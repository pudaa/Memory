package com.deepsleep.memory.handle_utils;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

public class AudioPlayer {
    public static void playAudio(Context context, String word, boolean isUS) {
        String audioUrl = "https://dict.youdao.com/dictvoice?audio=" + word + "&type=" + (isUS ? "2" : "1");

        MediaPlayer mediaPlayer = new MediaPlayer();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build());
            }
            mediaPlayer.setDataSource(context, Uri.parse(audioUrl));

            mediaPlayer.prepareAsync();

            mediaPlayer.setOnPreparedListener(mp -> {
                mediaPlayer.start();
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e("AudioPlayer", "播放错误: what=" + what + ", extra=" + extra);
                Toast.makeText(context, "播放失败，请检查网络", Toast.LENGTH_SHORT).show();
                mediaPlayer.release();
                return true;
            });

            mediaPlayer.setOnCompletionListener(mp -> {
                mediaPlayer.release();
            });

        } catch (Exception e) {
            Toast.makeText(context, "无法播放音频", Toast.LENGTH_SHORT).show();
            mediaPlayer.release();
        }
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
