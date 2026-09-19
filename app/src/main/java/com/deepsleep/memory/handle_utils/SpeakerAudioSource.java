package com.deepsleep.memory.handle_utils;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.deepsleep.memory.network.MemoryApiClient;
import com.deepsleep.memory.settings.InnerSettingsManager;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.Nullable;

/**
 * 片段音频源：播放**已存在**的音频（本地文件，或带鉴权的远程 wav）。
 *
 * <p>与 {@link StreamingAudioSource} 的分工：
 * 本类用于**短小、可复用**的片段——服务端已缓存好的 wav（单词听写、词典发音），
 * 点击即响且**不占用 GPU 生成**；而长文本朗读应走流式（见
 * {@link AudioPlaybackManager#playStreaming}），否则要等整段生成+下载。
 *
 * <p>本类把此前散落在 {@code AiConversationAdapter} 里的三段逻辑收敛到一处，
 * 并修掉了三个"单例保证靠人工调用维持"的隐患：
 * <ol>
 *   <li>回退下载后播放用的 MediaPlayer 原先存在局部变量里、不受管理
 *       → 现在由本对象统一持有，{@link #stop()} 一定释放；</li>
 *   <li>远程直连 token 过期时"刷新一次并重试"的链路原先散在适配器里
 *       → 收敛到这里；</li>
 *   <li>"播完即删"的缓存清理原先只挂在一条路径上
 *       → 现在所有片段播放都统一处理（受 {@link AudioCacheCleaner#deleteFile} 保护，
 *       只删本应用 Audio 目录内的文件）。</li>
 * </ol>
 */
final class SpeakerAudioSource implements AudioPlaybackManager.Source {

    private static final String TAG = "SpeakerAudio";

    private final Context context;
    @Nullable
    private final String localPath;
    @Nullable
    private final String remoteUrl;
    private final boolean deleteAfter;
    @Nullable
    private final AudioPlaybackManager.Listener listener;

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile MediaPlayer player;
    private volatile int token = -1;
    private int retried = 0;
    private long startedAt = 0;

    SpeakerAudioSource(Context context, @Nullable String localPath, @Nullable String remoteUrl,
                       boolean deleteAfter, @Nullable AudioPlaybackManager.Listener listener) {
        this.context = context.getApplicationContext();
        this.localPath = localPath;
        this.remoteUrl = remoteUrl;
        this.deleteAfter = deleteAfter;
        this.listener = listener;
    }

    @Override
    public void start(int token) {
        this.token = token;
        this.startedAt = System.currentTimeMillis();
        // MediaPlayer 在**主线程**创建与操作最稳妥
        runOnMain(() -> {
            if (cancelled.get()) {
                return;
            }
            if (localPath != null && !localPath.isEmpty()) {
                playLocal(localPath);
            } else {
                playRemote(remoteUrl);
            }
        });
    }

    @Override
    public void stop() {
        cancelled.set(true);
        runOnMain(this::release);
    }

    // ────────────────────────────────────────────────

    private void playLocal(String path) {
        MediaPlayer p = new MediaPlayer();
        player = p;
        try {
            applyAttributes(p);
            p.setDataSource(path);
            p.prepareAsync();
            p.setOnPreparedListener(mp -> {
                if (cancelled.get()) {
                    release();
                    return;
                }
                mp.start();
                notifySafe(() -> listener.onStarted((int) (System.currentTimeMillis() - startedAt)));
            });
            p.setOnCompletionListener(mp -> {
                boolean stillCurrent = !cancelled.get();
                release();
                if (stillCurrent) {
                    notifySafe(() -> listener.onCompleted((int) (System.currentTimeMillis() - startedAt)));
                    if (deleteAfter) {
                        deleteCacheFile(path);
                    }
                }
            });
            p.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "本地播放失败 what=" + what + " extra=" + extra + " path=" + path);
                release();
                notifySafe(() -> listener.onError("播放失败"));
                return true;
            });
        } catch (Exception e) {
            Log.e(TAG, "setDataSource(本地) 失败", e);
            release();
            notifySafe(() -> listener.onError("无法播放音频"));
        }
    }

    /**
     * 直连播放远程音频（附带 Authorization 头）。
     *
     * <p>注意：MediaPlayer 的 HTTP 栈不在 OkHttp 体系内，**不享受 401 自动刷新**，
     * 因此失败时刷新一次 token 并重试一次；仍失败才回退到"下载后播放"。
     */
    private void playRemote(String url) {
        if (url == null || url.isEmpty()) {
            notifySafe(() -> listener.onError("音频地址为空"));
            return;
        }
        MediaPlayer p = new MediaPlayer();
        player = p;
        try {
            applyAttributes(p);
            p.setDataSource(context, Uri.parse(url), authHeaders());
            p.prepareAsync();
            p.setOnPreparedListener(mp -> {
                if (cancelled.get()) {
                    release();
                    return;
                }
                mp.start();
                notifySafe(() -> listener.onStarted((int) (System.currentTimeMillis() - startedAt)));
            });
            p.setOnCompletionListener(mp -> {
                boolean stillCurrent = !cancelled.get();
                release();
                if (stillCurrent) {
                    notifySafe(() -> listener.onCompleted((int) (System.currentTimeMillis() - startedAt)));
                }
            });
            p.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "直连播放失败 what=" + what + " extra=" + extra + " url=" + url);
                release();
                onRemoteFailure(url);
                return true;
            });
        } catch (Exception e) {
            Log.e(TAG, "setDataSource(远程) 失败", e);
            release();
            onRemoteFailure(url);
        }
    }

    /** 远程失败的处理链：刷新 token 重试一次 → 仍失败则下载后播放 */
    private void onRemoteFailure(String url) {
        if (cancelled.get()) {
            return;
        }
        if (retried == 0) {
            retried = 1;
            new Thread(() -> {
                boolean refreshed = MemoryApiClient.refreshTokenBlocking();
                if (cancelled.get()) {
                    return;
                }
                Log.i(TAG, "token 刷新" + (refreshed ? "成功" : "失败") + "，重试直连");
                runOnMain(() -> {
                    if (!cancelled.get()) {
                        playRemote(url);
                    }
                });
            }, "audio-token-refresh").start();
            return;
        }
        // 直连两次都失败 → 回退到"下载后播放"
        new Thread(() -> {
            String downloaded = MemoryApiClient.downloadMediaFile(url, context);
            if (cancelled.get()) {
                return;
            }
            if (downloaded == null) {
                notifySafe(() -> listener.onError("音频加载失败"));
                return;
            }
            Log.i(TAG, "回退下载成功: " + downloaded);
            runOnMain(() -> {
                if (!cancelled.get()) {
                    playLocal(downloaded);
                }
            });
        }, "audio-fallback-download").start();
    }

    private void applyAttributes(MediaPlayer p) {
        p.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());
    }

    private Map<String, String> authHeaders() {
        Map<String, String> headers = new HashMap<>();
        String token = InnerSettingsManager.getStoredAccessToken();
        if (token != null && !token.isEmpty()) {
            headers.put("Authorization", "Bearer " + token);
        }
        return headers;
    }

    private void release() {
        MediaPlayer p = player;
        player = null;
        if (p != null) {
            try {
                if (p.isPlaying()) {
                    p.stop();
                }
            } catch (Exception ignored) {
                // 忽略状态异常
            }
            try {
                p.reset();
            } catch (Exception ignored) {
                // 忽略
            }
            try {
                p.release();
            } catch (Exception ignored) {
                // 忽略
            }
        }
    }

    /** 播完即删（仅本应用缓存目录内的文件；删除失败不影响播放） */
    private void deleteCacheFile(String path) {
        if (path == null) {
            return;
        }
        File f = new File(path);
        // 只处理应用私有缓存目录下的文件，避免误删用户数据
        if (f.getAbsolutePath().contains("Audio")) {
            new Thread(() -> AudioCacheCleaner.deleteFile(path), "audio-cache-del").start();
        }
    }

    private void notifySafe(Runnable r) {
        if (listener == null || !AudioPlaybackManager.isCurrent(token)) {
            return;
        }
        runOnMain(() -> {
            if (AudioPlaybackManager.isCurrent(token)) {
                r.run();
            }
        });
    }

    private void runOnMain(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            new Handler(Looper.getMainLooper()).post(r);
        }
    }
}
