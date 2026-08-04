package com.deepsleep.memory.ui.treasure_view.aichat_view;

import android.content.Context;
import android.media.MediaPlayer;
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
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

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

    private void playLocalAudio(String filePath) {
        if (filePath == null)
            return;
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(filePath);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
            mediaPlayer.setOnCompletionListener(mp -> {
                mp.release();
                mediaPlayer = null;
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
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(audioUrl);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
            mediaPlayer.setOnCompletionListener(mp -> {
                mp.release();
                mediaPlayer = null;
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
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
