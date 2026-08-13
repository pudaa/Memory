package com.deepsleep.memory.handle_utils;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

public class AudioPlayer {

    /** 当前正在播放（或准备中）的播放器。同一时间仅保留一个：新播放会打断旧的，避免多个音频同时叠加 */
    private static MediaPlayer activePlayer;

    public static void playAudio(Context context, String word, boolean isUS) {
        // 打断上一次播放：先停止再释放（点击美音→英音、或快速连点均只保留最后一次播放）
        stopActivePlayer();

        String audioUrl = "https://dict.youdao.com/dictvoice?audio=" + word + "&type=" + (isUS ? "2" : "1");

        MediaPlayer mediaPlayer = new MediaPlayer();
        activePlayer = mediaPlayer;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mediaPlayer.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            }
            mediaPlayer.setDataSource(context, Uri.parse(audioUrl));

            mediaPlayer.prepareAsync();

            mediaPlayer.setOnPreparedListener(mp -> {
                // 准备期间若已被新的点击打断，则不再开始播放
                if (isActivePlayer(mediaPlayer)) {
                    mediaPlayer.start();
                }
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                if (!isActivePlayer(mediaPlayer)) {
                    // 已被新播放打断（release 可能触发 onError），静默释放，不弹错误提示
                    releaseIfActive(mediaPlayer);
                    return true;
                }
                Log.e("AudioPlayer", "播放错误: what=" + what + ", extra=" + extra);
                Toast.makeText(context, "播放失败，请检查网络", Toast.LENGTH_SHORT).show();
                releaseIfActive(mediaPlayer);
                return true;
            });

            mediaPlayer.setOnCompletionListener(mp -> {
                releaseIfActive(mediaPlayer);
            });

        } catch (Exception e) {
            Toast.makeText(context, "无法播放音频", Toast.LENGTH_SHORT).show();
            releaseIfActive(mediaPlayer);
        }
    }

    /** 打断当前正在播放/准备中的音频并释放资源 */
    private static void stopActivePlayer() {
        if (activePlayer == null)
            return;
        try {
            activePlayer.stop();
        } catch (Exception ignored) {
            // 播放器可能处于 Idle/Preparing 等不允许 stop 的状态，忽略即可，随后直接 release
        }
        try {
            activePlayer.release();
        } catch (Exception ignored) {
        }
        activePlayer = null;
    }

    /** 仅当该播放器仍是当前活跃实例时才清空引用，防止旧播放器的回调误清新播放器 */
    private static void releaseIfActive(MediaPlayer mediaPlayer) {
        if (activePlayer == mediaPlayer) {
            activePlayer = null;
        }
        try {
            mediaPlayer.release();
        } catch (Exception ignored) {
        }
    }

    private static boolean isActivePlayer(MediaPlayer mediaPlayer) {
        return activePlayer == mediaPlayer;
    }

    public static boolean getPlayType(String wordText) {
        // 美音英音轮流播放，第一次播放英音，第二次播放美音，第三次播放英音，以此类推，所以返回值将会在true和false之间摇摆
        // 随机返回true 或 false
        return Math.random() < 0.5;
    }

    public static void releaseMediaPlayer(MediaPlayer mediaPlayer) {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

}
