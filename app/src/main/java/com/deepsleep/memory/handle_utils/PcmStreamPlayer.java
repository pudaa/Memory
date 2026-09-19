package com.deepsleep.memory.handle_utils;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.util.Log;

import com.deepsleep.memory.network.MemoryApiClient;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 流式 PCM 播放器 —— 边接收边播放，用于对话朗读。
 *
 * <p>为什么不用 {@link android.media.MediaPlayer}：后端流式接口返回的是**裸 PCM**
 * （int16LE / 单声道 / 24kHz），没有容器头，MediaPlayer 无法识别；而且
 * {@code /tts-audio/**} 已强制 JWT，MediaPlayer 也无法直接带头播放。
 * AudioTrack 正是为"PCM 字节流"设计的，天然支持边收边播。
 *
 * <p>工作方式：在**后台线程**上顺序完成「读网络流 → 写 AudioTrack」。
 * AudioTrack 的缓冲区写满时会阻塞，这个背压正好避免把整段音频读进内存，
 * 因此内存占用恒定，首声延迟只取决于服务端首片到达时间（约 0.6s）。
 *
 * <p>线程模型：同一时刻只允许一个播放会话（新播放会打断旧的），
 * 与 {@link AudioPlayer} 的既有约定一致。
 */
public class PcmStreamPlayer {

    private static final String TAG = "PcmStreamPlayer";

    /** 服务端采样率（与 MemoryServer / MemoryServerTTS 一致） */
    private static final int DEFAULT_SAMPLE_RATE = 24000;

    /** AudioTrack 缓冲区时长（毫秒）。过小易断续，过大增延迟；500ms 较稳妥 */
    private static final int BUFFER_MS = 500;

    /** 当前会话（新播放会打断旧的） */
    private static volatile Session activeSession;

    /**
     * 播放指定文本的流式 PCM。返回后立即开始后台下载+播放。
     *
     * @param urlString 后端流式端点完整 URL（如 .../tts/synthesize-stream）
     * @param text      待合成文本
     * @param listener  状态回调，可为 null
     */
    public static void playStream(String urlString, String text, Listener listener) {
        stop(); // 打断上一次播放
        Session s = new Session(urlString, text, listener);
        activeSession = s;
        s.start();
    }

    /** 停止当前播放并释放资源（可在主线程调用） */
    public static void stop() {
        Session s = activeSession;
        activeSession = null;
        if (s != null) {
            s.cancel();
        }
    }

    /** 是否有正在播放的会话 */
    public static boolean isPlaying() {
        Session s = activeSession;
        return s != null && !s.cancelled.get();
    }

    /** 播放状态回调（均在后台线程触发，UI 更新请自行切主线程） */
    public interface Listener {
        /** 首字节到达，即将出声（可用于隐藏"加载中"） */
        void onFirstAudio(int elapsedMs);

        /** 正常播放完毕 */
        void onCompleted(int totalMs);

        /** 失败（网络错误 / 服务端非 200 / 音频轨道异常） */
        void onError(String message);
    }

    // ────────────────────────────────────────────────

    private static class Session extends Thread {
        private final String url;
        private final String text;
        private final Listener listener;
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        private volatile AudioTrack track;

        Session(String url, String text, Listener listener) {
            super("PcmStreamPlayer");
            setDaemon(true);
            this.url = url;
            this.text = text;
            this.listener = listener;
        }

        void cancel() {
            cancelled.set(true);
            AudioTrack t = track;
            if (t != null) {
                try {
                    // 唤醒可能在 write() 上阻塞的线程
                    t.pause();
                    t.flush();
                } catch (Exception ignored) {
                    // 已释放等情况，忽略
                }
            }
            interrupt();
        }

        @Override
        public void run() {
            long t0 = System.currentTimeMillis();
            try {
                final long[] firstAt = {-1};
                MemoryApiClient.streamPcm(url, text, response -> {
                    int sr = parseSampleRate(response.header("X-Audio-Sample-Rate"));
                    try {
                        readInto(response.body().byteStream(), sr, firstAt, t0);
                    } catch (RuntimeException re) {
                        throw re;
                    } catch (Exception e) {
                        // 回调只允许抛 IOException，统一转换
                        throw new java.io.IOException("读取音频流失败: " + e.getMessage(), e);
                    }
                });
            } catch (Exception e) {
                if (!cancelled.get()) {
                    Log.e(TAG, "流式播放失败", e);
                    if (listener != null) {
                        listener.onError(e.getMessage() != null ? e.getMessage() : "播放失败");
                    }
                }
            } finally {
                releaseTrack();
                if (!cancelled.get() && listener != null) {
                    listener.onCompleted((int) (System.currentTimeMillis() - t0));
                }
            }
        }

        /** 从网络流读 PCM 并写进 AudioTrack（写满自动阻塞，形成背压） */
        private void readInto(InputStream in, int sampleRate, long[] firstAt, long t0) throws Exception {
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
                    firstAt[0] = System.currentTimeMillis() - t0;
                    if (listener != null) {
                        listener.onFirstAudio((int) firstAt[0]);
                    }
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
            // 排空尚未播出的缓冲
            if (!cancelled.get()) {
                try {
                    t.stop();   // stop() 会播放完已写入的数据再停
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

        private static int parseSampleRate(String header) {
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
}
