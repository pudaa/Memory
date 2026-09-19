package com.deepsleep.memory.handle_utils;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 音频缓存清理器 —— 兜底策略。
 *
 * <p><b>背景</b>：`externalFilesDir/Audio/` 里的 wav 原先**没有任何清理逻辑**，
 * 而下载文件名带时间戳（`<millis>_<原名>`），所以每次播放都会新增一个文件，
 * 长期使用必然无限堆积。这是历史遗留隐患。
 *
 * <p><b>根治</b>：对话朗读已改为「MediaPlayer 直连播放 + Authorization 头」，
 * 不再下载到本地，因此该目录不再新增文件。
 *
 * <p><b>本类的作用（兜底）</b>：处理三类"仍会产生文件"的情况——
 * <ol>
 *   <li>用户设备上**历史遗留**的下载文件；</li>
 *   <li>直连失败的最终兜底路径（{@code downloadMediaFile}）；</li>
 *   <li>欢迎语 TTS（{@code downloadWav}）仍然下载到该目录。</li>
 * </ol>
 * 策略：启动/播放时清理「超过 {@link #MAX_AGE_DAYS} 天」的文件；
 * 若仍超过 {@link #MAX_TOTAL_BYTES}，按最近修改时间从旧到新删到阈值以内。
 *
 * <p>只在后台线程调用（有磁盘 IO）。
 */
public final class AudioCacheCleaner {

    private static final String TAG = "AudioCacheCleaner";

    /** Audio 子目录名（与 MemoryApiClient / MemAudioRecord 保持一致） */
    private static final String AUDIO_DIR = "Audio";

    /** 保留天数：超过即删 */
    private static final int MAX_AGE_DAYS = 7;

    /** 目录总量上限：100 MB（24kHz 单声道 wav 约 2.8MB/分钟，够几百条） */
    private static final long MAX_TOTAL_BYTES = 100L * 1024 * 1024;

    private AudioCacheCleaner() {
    }

    /**
     * 执行一次清理。在后台线程调用。
     *
     * @return 删除的文件数
     */
    public static int cleanup(Context context) {
        if (context == null) {
            return 0;
        }
        File dir = new File(context.getExternalFilesDir(null), AUDIO_DIR);
        if (!dir.isDirectory()) {
            return 0;
        }
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            return 0;
        }

        int deleted = 0;
        long now = System.currentTimeMillis();
        long ageLimitMs = MAX_AGE_DAYS * 24L * 60 * 60 * 1000;

        List<File> survivors = new ArrayList<>();
        // 1) 按时间清理过期文件
        for (File f : files) {
            if (!f.isFile()) {
                continue;
            }
            if (now - f.lastModified() > ageLimitMs) {
                if (f.delete()) {
                    deleted++;
                }
            } else {
                survivors.add(f);
            }
        }

        // 2) 仍超总量则按 LRU（最久未修改）删到阈值以内
        long total = 0;
        for (File f : survivors) {
            total += f.length();
        }
        if (total > MAX_TOTAL_BYTES && !survivors.isEmpty()) {
            survivors.sort(Comparator.comparingLong(File::lastModified));
            for (File f : survivors) {
                if (total <= MAX_TOTAL_BYTES) {
                    break;
                }
                long len = f.length();
                if (f.delete()) {
                    total -= len;
                    deleted++;
                }
            }
        }

        if (deleted > 0) {
            Log.i(TAG, "音频缓存清理完成，删除 " + deleted + " 个文件（保留 "
                    + survivors.size() + " 个，约 " + (total / 1024) + " KB）");
        }
        return deleted;
    }

    /**
     * 删除单个音频文件（例如直连播放失败兜底下载后、播放结束即删）。
     *
     * @param path 本地文件路径；非本地路径或不存在时静默忽略
     */
    public static void deleteFile(String path) {
        if (path == null || path.isEmpty()) {
            return;
        }
        // 只删本应用 Audio 目录下的文件，避免误删用户数据
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return;
        }
        try {
            File f = new File(path);
            if (f.isFile() && f.delete()) {
                Log.i(TAG, "已删除播放完的音频: " + f.getName());
            }
        } catch (Exception e) {
            Log.w(TAG, "删除音频失败: " + path, e);
        }
    }

    /** 当前 Audio 目录占用（字节），用于诊断 */
    public static long currentSizeBytes(Context context) {
        if (context == null) {
            return 0;
        }
        File dir = new File(context.getExternalFilesDir(null), AUDIO_DIR);
        File[] files = dir.listFiles();
        if (files == null) {
            return 0;
        }
        long total = 0;
        for (File f : files) {
            if (f.isFile()) {
                total += f.length();
            }
        }
        return total;
    }
}
