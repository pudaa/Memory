package com.deepsleep.memory.handle_utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.*;
import android.util.Log;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MemAudioRecord {
    private static final String TAG = "AudioRecord";

    private AudioRecord audioRecord;
    private Thread recordThread;
    private Thread playThread;
    private String audioFilePath;
    private boolean isRecording = false;
    private boolean isPlaying = false;

    private static final int SAMPLE_RATE = 16000; // 设置采样率为44100Hz
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO; // 设置单声道
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT; // 设置编码格式
    private int bufferSize;

    public interface OnRecordListener {
        void onRecordStart();
        void onRecordStop(String filePath);
        void onError(String error);
    }

    public interface OnPlayListener {
        void onPlayStart();
        void onPlayComplete();
        void onError(String error);
    }

    public void cleanup() {
        // 停止录音和播放，并删除录音文件
        try {
            if (isRecording) {
                stopRecording(null);
            }
            if (isPlaying) {
                stopPlaying();
            }
            deleteRecordingFile();
        } catch (Exception e) {
            Log.w(TAG, "Cleanup failed", e);
        }
    }

    public void startRecording(String fileName, OnRecordListener listener, Context context) {
        if (isRecording) {
            Log.w(TAG, "Already recording");
            return;
        }

        try {
            // 创建保存PCM文件的目录
            File audioDir = new File(context.getExternalFilesDir(null), "Audio");
            if (!audioDir.exists()) {
                if (!audioDir.mkdirs()) {
                    Log.e(TAG, "Failed to create directory: " + audioDir.getAbsolutePath());
                    if (listener != null) {
                        listener.onError("创建录音目录失败");
                    }
                    return;
                }
            }

            audioFilePath = new File(audioDir, fileName).getAbsolutePath();

            // 初始化AudioRecord
            bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            );

            audioRecord.startRecording();
            isRecording = true;

            // 启动录音线程
            recordThread = new Thread(new AudioRecordRunnable());
            recordThread.start();

            if (listener != null) {
                listener.onRecordStart();
            }

            Log.d(TAG, "Started recording to: " + audioFilePath);

        } catch (Exception e) {
            Log.e(TAG, "Failed to start recording", e);
            if (listener != null) {
                listener.onError("录音启动失败: " + e.getMessage());
            }
        }
    }

    private class AudioRecordRunnable implements Runnable {
        @Override
        public void run() {
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(audioFilePath);
                byte[] buffer = new byte[bufferSize];

                while (isRecording) {
                    int read = audioRecord.read(buffer, 0, bufferSize);
                    if (read > 0) {
                        fos.write(buffer, 0, read);
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Error writing audio data to file", e);
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Error closing file output stream", e);
                    }
                }
            }
        }
    }

    public void stopRecording(OnRecordListener listener) {
        if (!isRecording || audioRecord == null) {
            Log.w(TAG, "Not recording currently");
            return;
        }

        try {
            isRecording = false;
            if (recordThread != null) {
                recordThread.join();
            }

            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;

            // 将原始 PCM 转换为标准 WAV（添加 44 字节文件头）
            String wavFilePath = pcmToWav(audioFilePath, SAMPLE_RATE, 1, 16);
            if (wavFilePath != null) {
                // 删除原始 PCM 文件，更新路径为 WAV
                new File(audioFilePath).delete();
                audioFilePath = wavFilePath;
            }

            if (listener != null) {
                listener.onRecordStop(audioFilePath);
            }

            Log.d(TAG, "Stopped recording → WAV: " + audioFilePath);

        } catch (Exception e) {
            Log.e(TAG, "Error stopping recording", e);
            if (listener != null) {
                listener.onError("停止录音失败: " + e.getMessage());
            }
        }
    }

    /**
     * 将原始 PCM 数据转换为标准 WAV 格式（添加 44 字节文件头）
     * @param pcmFilePath  原始 PCM 文件路径
     * @param sampleRate   采样率（如 16000）
     * @param channels     声道数（1=单声道）
     * @param bitsPerSample 位深度（通常 16）
     * @return WAV 文件路径，失败返回 null
     */
    private static String pcmToWav(String pcmFilePath, int sampleRate, int channels, int bitsPerSample) {
        File pcmFile = new File(pcmFilePath);
        if (!pcmFile.exists()) return null;

        String wavPath = pcmFilePath.replaceFirst("\\.[^.]+$", "") + ".wav";
        File wavFile = new File(wavPath);

        try (FileInputStream fis = new FileInputStream(pcmFile);
             FileOutputStream fos = new FileOutputStream(wavFile)) {

            long totalAudioLen = pcmFile.length();
            long totalDataLen = totalAudioLen + 36;
            int byteRate = sampleRate * channels * bitsPerSample / 8;

            byte[] header = new byte[44];
            // RIFF chunk
            header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
            writeIntLE(header, 4, (int) totalDataLen);
            header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
            // fmt subchunk
            header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
            writeIntLE(header, 16, 16);              // Subchunk1Size (PCM = 16)
            writeShortLE(header, 20, (short) 1);      // AudioFormat (1 = PCM)
            writeShortLE(header, 22, (short) channels);
            writeIntLE(header, 24, sampleRate);
            writeIntLE(header, 28, byteRate);
            writeShortLE(header, 32, (short) (channels * bitsPerSample / 8)); // BlockAlign
            writeShortLE(header, 34, (short) bitsPerSample);
            // data subchunk
            header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
            writeIntLE(header, 40, (int) totalAudioLen);

            fos.write(header);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }

            return wavPath;

        } catch (IOException e) {
            Log.e(TAG, "PCM→WAV conversion failed", e);
            return null;
        }
    }

    private static void writeIntLE(byte[] buf, int offset, int value) {
        buf[offset]     = (byte) (value & 0xFF);
        buf[offset + 1] = (byte) ((value >> 8) & 0xFF);
        buf[offset + 2] = (byte) ((value >> 16) & 0xFF);
        buf[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }

    private static void writeShortLE(byte[] buf, int offset, short value) {
        buf[offset]     = (byte) (value & 0xFF);
        buf[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }

    public void playRecording(OnPlayListener listener) {
        if (isPlaying || audioFilePath == null) {
            Log.w(TAG, "Cannot play recording");
            return;
        }

        try {
            // 使用AudioTrack播放PCM数据
            int bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AUDIO_FORMAT
            );

            AudioTrack audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                .setAudioFormat(new AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AUDIO_FORMAT)
                    .build())
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();

            // 开启播放线程并保存引用
            playThread = new Thread(() -> {
                isPlaying = true;
                if (listener != null) {
                    listener.onPlayStart();
                }

                try {
                    audioTrack.play();
                    FileInputStream fis = new FileInputStream(audioFilePath);
                    byte[] buffer = new byte[bufferSize];
                    int bytesRead;

                    while (isPlaying && (bytesRead = fis.read(buffer)) != -1) {
                        audioTrack.write(buffer, 0, bytesRead);
                    }

                    fis.close();
                    audioTrack.stop();

                    if (listener != null) {
                        listener.onPlayComplete();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error playing PCM data", e);
                    if (listener != null) {
                        listener.onError("播放失败: " + e.getMessage());
                    }
                } finally {
                    audioTrack.release();
                    isPlaying = false;
                    playThread = null; // 清理线程引用
                }
            });

            playThread.start();
            Log.d(TAG, "Started playing recording with AudioTrack");

        } catch (Exception e) {
            Log.e(TAG, "Failed to play recording", e);
            isPlaying = false;
            playThread = null; // 清理线程引用
            if (listener != null) {
                listener.onError("播放失败: " + e.getMessage());
            }
        }
    }

    public void stopPlaying() {
        // 停止AudioTrack播放
        if (isPlaying) {
            isPlaying = false; // 设置标志位停止播放循环
            if (playThread != null) {
                try {
                    playThread.join(); // 等待播放线程结束
                } catch (InterruptedException e) {
                    Log.e(TAG, "Interrupted while waiting for play thread to finish", e);
                    Thread.currentThread().interrupt();
                }
            }
            Log.d(TAG, "Stopped playing");
        }
    }


    public boolean deleteRecordingFile() { // 删除录音文件
        if (audioFilePath != null) {
            File file = new File(audioFilePath);
            boolean deleted = file.delete();
            if (deleted) {
                audioFilePath = null;
            }
            return deleted;
        }
        return false;
    }

    public String getAudioFilePath() {
        return audioFilePath;
    }

    public boolean isRecording() {
        return isRecording;
    }

    public boolean isPlaying() {
        return isPlaying;
    }
    public byte[] getPCMData() {
        if (audioFilePath == null) {
            return null;
        }

        try {
            File file = new File(audioFilePath);
            FileInputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();
            return data;
        } catch (IOException e) {
            Log.e(TAG, "Error reading PCM data", e);
            return null;
        }
    }
}