package com.deepsleep.memory.handle_utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

/**
 * 统一音频播放仲裁器 —— App 内**所有**音频播放的唯一入口。
 *
 * <h3>为什么需要它</h3>
 * 此前音频播放散落在几处各自为政：流式 PCM 播放器、{@code AiConversationAdapter}
 * 里的 MediaPlayer（本地文件/远程直连）、以及词典发音播放器。它们靠"记得互相调用
 * stop()"来避免叠音，属于约定而非保证——漏一处就会两路声音同时响。
 * 本类把这条不变量变成**强制的**：任何时刻至多一个活跃播放，新的播放请求
 * 一律先打断旧的。
 *
 * <h3>两种模式都保留（各有不可替代的场景）</h3>
 * <ul>
 *   <li>{@link #playStreaming} —— 读长文本（AI 回复、每日一读）。
 *       服务端边生成边下发，首声约 0.5–0.8s，不必等整段生成完。</li>
 *   <li>{@link #playSpeaker} —— 短小、可复用的片段（单词听写、词典发音）。
 *       走服务端缓存好的 wav，**不占用 GPU 生成**，点击即响。</li>
 * </ul>
 * 注意：长文本**不要**用 {@link #playSpeaker}（要等整段生成+下载，首声要十几秒），
 * 短词**不要**用 {@link #playStreaming}（每次都现生成，浪费 GPU）。
 *
 * <h3>实现方式</h3>
 * 每次播放创建一个独立的 {@link Source} 对象并持有它；{@link Source} 自己拥有
 * 底层播放器（AudioTrack / MediaPlayer），相互之间零共享。
 * 打断即对该对象调用 {@link Source#stop()}，并用自增 token 让**过期回调不再上报**，
 * 避免"旧播放器的回调误刷新新播放器的 UI"这类竞态。
 */
public final class AudioPlaybackManager {

    private static final String TAG = "AudioPlayback";

    /** 一次播放会话的通用状态回调（保证只在主线程触发，且过期会话不再回调） */
    public interface Listener {
        /** 已开始出声（流式为首片到达，文件为准备完成） */
        void onStarted(int elapsedMs);

        /** 正常播放完毕 */
        void onCompleted(int totalMs);

        /** 失败（网络/解码/资源异常） */
        void onError(String message);
    }

    /** 可播放音频源：每个实现自己拥有并管理底层播放器 */
    public interface Source {
        /**
         * 开始播放。
         *
         * @param token 本次会话标识；回调前应核对 {@link #isCurrent(int)} 以免过期上报
         */
        void start(int token);

        /** 立即停止并释放资源（可重复调用） */
        void stop();
    }

    private static volatile int activeToken = 0;

    @Nullable
    private static volatile Source activeSource;

    private AudioPlaybackManager() {
    }

    // ────────────────────────────────────────────────
    // 对外播放入口
    // ────────────────────────────────────────────────

    /**
     * 流式朗读：把文本交给后端边生成边播（首声约 0.5–0.8s）。
     * 适合长文本；不落盘、不产生音频文件。
     */
    public static void playStreaming(Context context, String url, String text,
                                     @Nullable Listener listener) {
        if (text == null || text.trim().isEmpty()) {
            Log.w(TAG, "playStreaming: 文本为空，忽略");
            return;
        }
        startSource(new StreamingAudioSource(context, url, text, listener), listener);
    }

    /**
     * 播放"片段音频"：本地文件，或带鉴权的远程 wav（服务端已缓存，不占 GPU）。
     * 适合单词听写、词典发音等短小可复用的音频。
     *
     * @param localPath   本地文件路径；非空时优先使用
     * @param remoteUrl   远程 URL（本地路径为空时使用）
     * @param deleteAfter 播放完成后是否删除本地文件（听写/会话缓存为"用完即弃"）
     */
    public static void playSpeaker(Context context, @Nullable String localPath,
                                   @Nullable String remoteUrl, boolean deleteAfter,
                                   @Nullable Listener listener) {
        if ((localPath == null || localPath.isEmpty())
                && (remoteUrl == null || remoteUrl.isEmpty())) {
            Log.w(TAG, "playSpeaker: 未提供可播放地址，忽略");
            return;
        }
        startSource(new SpeakerAudioSource(context, localPath, remoteUrl, deleteAfter, listener),
                listener);
    }

    /** 打断当前播放（可在任意线程调用） */
    public static void stop() {
        Source s = activeSource;
        activeSource = null;
        activeToken++;                    // 使旧会话的回调失效
        if (s != null) {
            try {
                s.stop();
            } catch (Exception e) {
                Log.w(TAG, "停止播放异常: " + e.getMessage());
            }
        }
    }

    /** 是否有正在播放的音频 */
    public static boolean isPlaying() {
        return activeSource != null;
    }

    /**
     * 当前会话 token 是否仍然有效。
     * 供各 Source 在触发回调前核对，避免"被打断后仍上报状态"。
     */
    static boolean isCurrent(int token) {
        return token == activeToken && activeSource != null;
    }

    // ────────────────────────────────────────────────

    private static void startSource(Source source, @Nullable Listener listener) {
        // 先打断旧的（含 AudioTrack 释放与 MediaPlayer release）
        stop();
        int token = ++activeToken;
        activeSource = source;
        Log.i(TAG, "开始播放: " + source.getClass().getSimpleName() + " token=" + token);
        try {
            source.start(token);
        } catch (Exception e) {
            Log.e(TAG, "启动播放失败", e);
            if (isCurrent(token)) {
                activeSource = null;
                activeToken++;
                if (listener != null) {
                    listener.onError(e.getMessage() != null ? e.getMessage() : "播放失败");
                }
            }
        }
    }
}
