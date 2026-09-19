package com.deepsleep.memory.ui.treasure_view.aichat_view;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.deepsleep.memory.R;
import com.deepsleep.memory.handle_utils.AudioCacheCleaner;
import com.deepsleep.memory.settings.InnerSettingsManager;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AiConversationAdapter extends RecyclerView.Adapter<AiConversationAdapter.MessageViewHolder> {
    private List<AiMessage> messages;
    private Context context;
    private MediaPlayer mediaPlayer;

    private static final int VIEW_TYPE_MESSAGE = 0;
    private static final int VIEW_TYPE_SUMMARY = 1;

    public AiConversationAdapter(List<AiMessage> messages) {
        this.messages = messages;
    }

    @Override
    public int getItemViewType(int position) {
        AiMessage msg = messages.get(position);
        if (msg.isSummary())
            return VIEW_TYPE_SUMMARY;
        return VIEW_TYPE_MESSAGE;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        if (viewType == VIEW_TYPE_SUMMARY) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_conversation_summary, parent, false);
            return new MessageViewHolder(view);
        }
        View view = LayoutInflater.from(context).inflate(R.layout.item_ai_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        AiMessage message = messages.get(position);

        if (message.isSummary()) {
            // 绑定总结卡片
            holder.tvWordsUsed.setText(String.valueOf(message.getSummaryWordsUsed()));
            holder.tvCorrections.setText(String.valueOf(message.getSummaryCorrections()));
            holder.tvTurnCount.setText(String.valueOf(message.getSummaryTurnCount()));
            return;
        }
        if (message.getType() == AiMessage.TYPE_USER) {
            holder.layoutAiMessage.setVisibility(View.GONE);
            holder.layoutEvaluation.setVisibility(View.GONE);

            if (message.isVoiceMessage()) {
                holder.layoutUserMessage.setVisibility(View.GONE);
                holder.layoutUserVoice.setVisibility(View.VISIBLE);
                holder.layoutUserVoice.setOnClickListener(v -> playLocalAudio(message.getLocalAudioPath()));
            } else {
                holder.layoutUserVoice.setVisibility(View.GONE);
                holder.layoutUserMessage.setVisibility(View.VISIBLE);
                holder.tvUserContent.setText(message.getContent());
            }
        } else {
            holder.layoutUserMessage.setVisibility(View.GONE);
            holder.layoutUserVoice.setVisibility(View.GONE);
            holder.layoutAiMessage.setVisibility(View.VISIBLE);

            // 流式状态：显示打字光标
            if (message.isStreaming()) {
                String displayText = message.getContent();
                if (displayText == null || displayText.isEmpty()) {
                    holder.tvAiContent.setText("typing...");
                    holder.tvAiContent.setAlpha(0.5f);
                } else {
                    holder.tvAiContent.setText(displayText + " |");
                    holder.tvAiContent.setAlpha(1.0f);
                }
                holder.btnPlayAudio.setVisibility(View.GONE);
                return;
            }

            holder.tvAiContent.setAlpha(1.0f);
            holder.tvAiContent.setText(message.getContent());

            // 音频按钮状态：加载中 / 可播放 / 隐藏
            if (message.isAudioPending()) {
                holder.btnPlayAudio.setVisibility(View.VISIBLE);
                holder.btnPlayAudio.setEnabled(false);
                holder.btnPlayAudio.setAlpha(0.35f);
                holder.btnPlayAudio.setOnClickListener(null);
            } else if (message.hasAudio()) {
                holder.btnPlayAudio.setVisibility(View.VISIBLE);
                holder.btnPlayAudio.setEnabled(true);
                holder.btnPlayAudio.setAlpha(1.0f);
                holder.btnPlayAudio.setOnClickListener(v -> playAudio(message.getAudioUrl()));
            } else {
                holder.btnPlayAudio.setVisibility(View.GONE);
            }

            // if (message.hasScore()) { // TODO: 暂时去掉chip评分，用户不需要看到关于评分的细节
            // holder.chipScore.setVisibility(View.VISIBLE);
            // holder.chipScore.setText(String.valueOf(message.getScore()));
            // } else {
            // holder.chipScore.setVisibility(View.GONE);
            // }

            // if (message.hasEvaluation()) {
            // holder.layoutEvaluation.setVisibility(View.VISIBLE);
            // holder.chipGroupEval.removeAllViews();
            // addEvalChip(holder, "发音", message.getPronunciationScore());
            // addEvalChip(holder, "流利", message.getFluencyScore());
            // addEvalChip(holder, "语法", message.getGrammarScore());
            // addEvalChip(holder, "词汇", message.getVocabularyScore());
            // if (message.getFeedback() != null && !message.getFeedback().isEmpty()) {
            // holder.tvFeedback.setVisibility(View.VISIBLE);
            // holder.tvFeedback.setText(message.getFeedback());
            // } else {
            // holder.tvFeedback.setVisibility(View.GONE);
            // }
            // } else {
            // holder.layoutEvaluation.setVisibility(View.GONE);
            // holder.tvFeedback.setVisibility(View.GONE);
            // }
        }
    }

    /**
     * 播放本地音频文件。播完即删——这些文件都是"用完即弃"的 TTS 缓存，
     * 留着只会让 Audio 目录无限增长（历史遗留隐患）。
     * 仅删除本应用 Audio 目录下的文件（{@link AudioCacheCleaner#deleteFile} 内有保护）。
     */
    private void playLocalAudio(String filePath) {
        if (filePath == null)
            return;
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        mediaPlayer = new MediaPlayer();
        final String path = filePath;
        try {
            mediaPlayer.setDataSource(filePath);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
            mediaPlayer.setOnCompletionListener(mp -> {
                mp.release();
                mediaPlayer = null;
                // 播完即删：后台线程做磁盘 IO
                new Thread(() -> AudioCacheCleaner.deleteFile(path), "audio-cache-del").start();
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void addEvalChip(MessageViewHolder holder, String label, double score) {
        if (score < 0)
            return;
        Chip chip = new Chip(context);
        chip.setText(String.format(Locale.getDefault(), "%s %.0f", label, score));
        chip.setClickable(false);
        chip.setCheckable(false);
        chip.setChipStrokeWidth(0f);
        chip.setTextAppearanceResource(R.style.ChipTextStyle);
        int chipColor = score >= 85 ? R.color.teal_200 : score >= 70 ? R.color.theme_stress : R.color.theme_error;
        chip.setChipBackgroundColorResource(chipColor);
        chip.setTextColor(ContextCompat.getColor(context, R.color.white));
        chip.setShapeAppearanceModel(chip.getShapeAppearanceModel().toBuilder().setAllCornerSizes(10f).build());
        chip.setPadding(0, 0, 0, 0);
        holder.chipGroupEval.addView(chip);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    /**
     * 释放 MediaPlayer，由宿主 Activity/Fragment 在销毁时调用
     */
    public void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mediaPlayer = null;
        }
    }

    private void playAudio(String audioUrl) {
        if (audioUrl == null || audioUrl.isEmpty())
            return;
        // 手动播放要先打断流式朗读，避免两路声音重叠
        com.deepsleep.memory.handle_utils.PcmStreamPlayer.stop();
        // 本地文件（历史遗留的已下载文件）直接播放
        if (!audioUrl.startsWith("http://") && !audioUrl.startsWith("https://")) {
            playLocalAudio(audioUrl);
            return;
        }
        // 远程 URL：**直连播放**，不再下载到本地。
        //
        // 这里曾经绕道"先带 Bearer 下载到本地再播"，理由是"MediaPlayer 无法带请求头"——
        // 该前提不成立：MediaPlayer 有 setDataSource(Context, Uri, Map<String,String>)
        // 重载，可以附带 Authorization。改为直连后：
        //   1. 客户端不再产生任何音频文件（Audio 目录堆积问题从根上消失）；
        //   2. 不必等整段下载完，播放器会渐进缓冲，点击后更快出声。
        playRemoteAudio(audioUrl);
    }

    /**
     * 直连播放远程音频（带 Authorization 头）。
     *
     * 注意：MediaPlayer 的 HTTP 栈不在 OkHttp 体系内，**不享受 App 的 401 自动刷新**。
     * token 过期时会直接失败，因此这里在失败时**刷新一次 token 并重试一次**。
     */
    private void playRemoteAudio(String audioUrl) {
        releaseMediaPlayer();
        MediaPlayer player = new MediaPlayer();
        mediaPlayer = player;
        try {
            player.setAudioAttributes(new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            Map<String, String> headers = authHeaders();
            player.setDataSource(context, Uri.parse(audioUrl), headers);
            player.prepareAsync();
            player.setOnPreparedListener(MediaPlayer::start);
            player.setOnCompletionListener(mp -> releaseMediaPlayer());
            player.setOnErrorListener((mp, what, extra) -> {
                Log.e("AiConversationAdapter", "直连播放失败 what=" + what + " extra=" + extra
                        + " url=" + audioUrl);
                // 失败兜底：刷新 token 重试一次；仍失败则回退到"下载后播放"
                retryAfterRefresh(audioUrl);
                return true;
            });
        } catch (IOException | IllegalArgumentException | SecurityException e) {
            Log.e("AiConversationAdapter", "setDataSource 失败，回退下载", e);
            retryAfterRefresh(audioUrl);
        }
    }

    /** 构造带 Bearer 的请求头（无 token 时返回空表，仍尝试匿名访问） */
    private Map<String, String> authHeaders() {
        Map<String, String> headers = new HashMap<>();
        String token = InnerSettingsManager.getStoredAccessToken();
        if (token != null && !token.isEmpty()) {
            headers.put("Authorization", "Bearer " + token);
        }
        return headers;
    }

    /**
     * 直连失败时的兜底链：刷新 token → 直连重试一次 → 仍失败则下载到本地播放（方案 C 兜底）。
     */
    private void retryAfterRefresh(String audioUrl) {
        releaseMediaPlayer();
        new Thread(() -> {
            boolean refreshed = com.deepsleep.memory.network.MemoryApiClient.refreshTokenBlocking();
            if (refreshed) {
                MediaPlayer p = new MediaPlayer();
                try {
                    p.setAudioAttributes(new android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build());
                    p.setDataSource(context, Uri.parse(audioUrl), authHeaders());
                    p.setOnPreparedListener(mp -> {
                        mediaPlayer = p;
                        mp.start();
                    });
                    p.setOnCompletionListener(mp -> releaseMediaPlayer());
                    p.prepare();
                    return; // 重试成功
                } catch (Exception e) {
                    Log.w("AiConversationAdapter", "刷新 token 后直连仍失败，回退下载", e);
                    try { p.release(); } catch (Exception ignored) {}
                }
            } else {
                Log.w("AiConversationAdapter", "token 刷新失败，回退下载");
            }
            // 最终兜底：下载到本地再播（仅在直连不可用时才产生磁盘文件）
            String localPath = com.deepsleep.memory.network.MemoryApiClient
                    .downloadMediaFile(audioUrl, context);
            if (localPath != null) {
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .post(() -> playLocalAudio(localPath));
            }
        }, "conversation-audio-fallback").start();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        LinearLayout layoutUserMessage, layoutUserVoice, layoutAiMessage;
        TextView tvUserContent, tvAiContent;
        ImageButton btnPlayAudio, btnUserVoicePlay;
        Chip chipScore;
        LinearLayout layoutEvaluation;
        ChipGroup chipGroupEval;
        TextView tvFeedback;

        // 总结卡片视图
        TextView tvWordsUsed, tvCorrections, tvTurnCount;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutUserMessage = itemView.findViewById(R.id.layoutUserMessage);
            layoutUserVoice = itemView.findViewById(R.id.layoutUserVoice);
            layoutAiMessage = itemView.findViewById(R.id.layoutAiMessage);
            tvUserContent = itemView.findViewById(R.id.tvUserContent);
            tvAiContent = itemView.findViewById(R.id.tvAiContent);
            btnPlayAudio = itemView.findViewById(R.id.btnPlayAudio);
            btnUserVoicePlay = itemView.findViewById(R.id.btnUserVoicePlay);
            chipScore = itemView.findViewById(R.id.chipScore);
            layoutEvaluation = itemView.findViewById(R.id.layoutEvaluation);
            chipGroupEval = itemView.findViewById(R.id.chipGroupEval);
            tvFeedback = itemView.findViewById(R.id.tvFeedback);

            // 总结卡片
            tvWordsUsed = itemView.findViewById(R.id.tvWordsUsed);
            tvCorrections = itemView.findViewById(R.id.tvCorrections);
            tvTurnCount = itemView.findViewById(R.id.tvTurnCount);
        }
    }
}
