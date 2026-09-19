package com.deepsleep.memory.handle_utils;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.deepsleep.memory.network.MemoryApiClient;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.Nullable;

/**
 * 流式音频源：把后端送来的**裸 PCM**（int16LE/单声道）边收边写进 AudioTrack。
 *
 * <p>为什么用 AudioTrack 而不是 MediaPlayer：流式端点返回的是裸 PCM，
 * 没有容器头，MediaPlayer/ExoPlayer 都需要容器或自定义 MediaSource；
 * AudioTrack 本身就是"PCM 字节流"接口，天然支持边收边播，
 * 且 {@code write()} 写满缓冲会阻塞 —— 这个背压把读取速度限制在播放速度上，
 * 因此**内存占用恒定**，不会把整段音频读进内存。
 *
 * <p>本类由 {@link AudioPlaybackManager} 创建并持有，属于其内部实现；
 * 业务代码请调用 {@code AudioPlaybackManager.playStreaming(...)}。
 */
final class StreamingAudioSource implements AudioPlaybackManager.Source {

    private static final String TAG = "StreamingAudio";

    /** 服务端采样率缺省值（与 MemoryServer / MemoryServerTTS 一致） */
    private static final int DEFAULT_SAMPLE_RATE = 24000;

    /** AudioTrack 缓冲时长（毫秒）。过小易断续，过大增延迟 */
    private static final int BUFFER_MS = 500;

    private final Context context;
    private final String url;
    private final String text;
    @Nullable
    private final AudioPlaybackManager.Listener listener;

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile AudioTrack track;
    private volatile int token = -1;
    private Thread worker;

    StreamingAudioSource(Context context, String url, String text,
                         @Nullable AudioPlaybackManager.Listener listener) {
        this.context = context.getApplicationContext();
        this.url = url;
        this.text = text;
        this.listener = listener;
    }

    @Override
    public void start(int token) {
        this.token = token;
        worker = new Thread(this::runPlayback, "audio-stream");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public void stop() {
        cancelled.set(true);
        AudioTrack t = track;
        if (t != null) {
            try {
                // 唤醒可能阻塞在 write() 上的线程
                t.pause();
                t.flush();
            } catch (Exception ignored) {
                // 已释放等情况
            }
        }
        if (worker != null) {
            worker.interrupt();
        }
    }

    // ────────────────────────────────────────────────

    private void runPlayback() {
        final long t0 = System.currentTimeMillis();
        try {
            MemoryApiClient.streamPcm(url, text, response -> {
                int sr = parseSampleRate(response.header("X-Audio-Sample-Rate"));
                try {
                    readInto(response.body().byteStream(), sr, t0);
                } catch (RuntimeException re) {
                    throw re;
                } catch (Exception e) {
                    // 回调只允许抛 IOException，统一转换
                    throw new java.io.IOException("读取音频流失败: " + e.getMessage(), e);
                }
            });
            if (!cancelled.get()) {
                notifySafe(() -> listener.onCompleted((int) (System.currentTimeMillis() - t0)));
            }
        } catch (Exception e) {
            if (!cancelled.get()) {
                Log.e(TAG, "流式播放失败", e);
                final String msg = e.getMessage() != null ? e.getMessage() : "播放失败";
                notifySafe(() -> listener.onError(msg));
            }
        } finally {
            releaseTrack();
        }
    }

    /** 从网络流读 PCM 并写进 AudioTrack（写满自动阻塞，形成背压） */
    private void readInto(InputStream in, int sampleRate, long t0) throws Exception {
        int minBuf = AudioTrack.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int bufBytes = Math.max(minBuf, sampleRate * 2 * BUFFER_MS / 1000);

        AudioTrack t;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            t = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(bufBytes)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
        } else {
            t = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    bufBytes, AudioTrack.MODE_STREAM);
        }
        track = t;
        if (t.getState() != AudioTrack.STATE_INITIALIZED) {
            throw new IllegalStateException("AudioTrack 初始化失败 (sr=" + sampleRate + ")");
        }
        t.play();

        byte[] buf = new byte[8 * 1024];
        int n;
        boolean first = true;
        while (!cancelled.get() && (n = in.read(buf)) != -1) {
            if (first && n > 0) {
                first = false;
                final int elapsed = (int) (System.currentTimeMillis() - t0);
                notifySafe(() -> listener.onStarted(elapsed));
            }
            int off = 0;
            while (off < n && !cancelled.get()) {
                int w = t.write(buf, off, n - off);
                if (w <= 0) {
                    break;
                }
                off += w;
            }
        }
        if (!cancelled.get()) {
            try {
                t.stop();   // 播放完已写入的数据
            } catch (Exception ignored) {
                // 忽略
            }
        }
    }

    private void releaseTrack() {
        AudioTrack t = track;
        track = null;
        if (t != null) {
            try {
                if (t.getPlayState() != AudioTrack.PLAYSTATE_STOPPED) {
                    t.stop();
                }
            } catch (Exception ignored) {
                // 忽略
            }
            try {
                t.release();
            } catch (Exception ignored) {
                // 忽略
            }
        }
    }

    /** 仅当本次会话仍有效时才回调（避免被打断后仍上报，刷新错 UI） */
    private void notifySafe(Runnable r) {
        if (listener == null || !AudioPlaybackManager.isCurrent(token)) {
            return;
        }
        new Handler(Looper.getMainLooper()).post(() -> {
            if (AudioPlaybackManager.isCurrent(token)) {
                r.run();
            }
        });
    }

    private static int parseSampleRate(@Nullable String header) {
        if (header == null) {
            return DEFAULT_SAMPLE_RATE;
        }
        try {
            return Integer.parseInt(header.trim());
        } catch (NumberFormatException e) {
            return DEFAULT_SAMPLE_RATE;
        }
    }
}
