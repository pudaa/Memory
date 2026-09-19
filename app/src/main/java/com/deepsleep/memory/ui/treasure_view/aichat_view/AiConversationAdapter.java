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
import com.deepsleep.memory.handle_utils.AudioPlaybackManager;
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

    /**
     * 朗读按钮的动作回调（"点击才生成"的流式朗读由 Activity 负责驱动）。
     *
     * 适配器只负责 UI 状态，不持有音频通道——因为流式播放需要 AudioTrack 与
     * 网络请求的生命周期管理，放在 Activity 里与页面生命周期一致。
     */
    public interface AudioActionListener {
        /** 用户点击朗读（该消息尚无已生成音频）→ 触发流式生成并播放 */
        void onPlayAudio(AiMessage message);

        /** 用户再次点击（正在播放）→ 立即停止 */
        void onStopAudio(AiMessage message);
    }

    private AudioActionListener audioActionListener;

    public void setAudioActionListener(AudioActionListener l) {
        this.audioActionListener = l;
    }

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
                holder.layoutUserVoice.setOnClickListener(v ->
                        // 用户录音是可复听的用户数据，播完**不删除**（与 TTS 缓存不同）
                        playLocalAudio(message.getLocalAudioPath(), false));
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

            // 音频按钮状态：等首片(转圈) / 播放中(可停止) / 可点击朗读 / 隐藏
            //
            // 对话朗读采用"点击才生成"的方案：AI 回复文本到达时**不预生成音频**，
            // 用户点播放按钮才向后端流式请求（首声约 0.6s），再点一次即停止。
            // 因此这里不再依赖 hasAudio()（那需要先落盘/轮询），而是只要正文非空
            // 就展示播放按钮。
            final boolean hasText = message.getContent() != null
                    && !message.getContent().trim().isEmpty();
            if (message.isAudioPlaying()) {
                holder.btnPlayAudio.setVisibility(View.VISIBLE);
                holder.btnPlayAudio.setEnabled(true);
                holder.btnPlayAudio.setAlpha(1.0f);
                holder.btnPlayAudio.setImageResource(R.drawable.ic_stop_24);
                holder.btnPlayAudio.setOnClickListener(v -> {
                    if (audioActionListener != null) {
                        audioActionListener.onStopAudio(message);
                    }
                });
            } else if (message.isAudioPending()) {
                holder.btnPlayAudio.setVisibility(View.VISIBLE);
                holder.btnPlayAudio.setEnabled(false);
                holder.btnPlayAudio.setAlpha(0.35f);
                holder.btnPlayAudio.setImageResource(R.drawable.ic_volume_up_24);
                holder.btnPlayAudio.setOnClickListener(null);
                // 历史消息可能残留"生成中"状态（例如上次预生成失败/中断，audio_url 永远为空），
                // 那样按钮会一直是灰的、点不动。既然本方案是"点击才生成"，
                // 只要有正文就让按钮可点（点击会走流式合成）。
                if (hasText && !message.isAudioPlaying()) {
                    holder.btnPlayAudio.setEnabled(true);
                    holder.btnPlayAudio.setAlpha(1.0f);
                    holder.btnPlayAudio.setOnClickListener(v -> {
                        if (audioActionListener != null) {
                            audioActionListener.onPlayAudio(message);
                        }
                    });
                }
            } else if (hasText || message.hasAudio()) {
                holder.btnPlayAudio.setVisibility(View.VISIBLE);
                holder.btnPlayAudio.setEnabled(true);
                holder.btnPlayAudio.setAlpha(1.0f);
                holder.btnPlayAudio.setImageResource(R.drawable.ic_volume_up_24);
                holder.btnPlayAudio.setOnClickListener(v -> {
                    // 优先走"点击才生成"的流式朗读：服务端按文本派生固定 seed，
                    // 同一条回复每次听到的都一致，且不落盘。
                    // 仅当流式不可用（Activity 未接管）时，才退回旧的直连/下载播放。
                    if (audioActionListener != null) {
                        audioActionListener.onPlayAudio(message);
                    } else if (message.hasAudio()) {
                        playAudio(message.getAudioUrl());
                    }
                });
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
     * 播放本地音频文件。
     *
     * 统一交给 {@link AudioPlaybackManager}：它会先打断任何正在播放的音频
     * （含另一条回复的流式朗读），并负责"播完即删"的缓存清理。
     * 本适配器**不再自己持有 MediaPlayer**——播放的唯一入口收敛到管理器，
     * 从结构上排除"两路声音同时响"的可能。
     *
     * @param deleteAfter 播完是否删除文件（TTS 缓存"用完即弃"；用户录音则保留）
     */
    private void playLocalAudio(String filePath) {
        playLocalAudio(filePath, true);
    }

    private void playLocalAudio(String filePath, boolean deleteAfter) {
        if (filePath == null || filePath.isEmpty()) {
            return;
        }
        AudioPlaybackManager.playSpeaker(context, filePath, null, deleteAfter, null);
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
     * 释放正在播放的音频。由宿主 Activity/Fragment 在销毁时调用。
     *
     * 现在只是转发到统一管理器——适配器自身不再持有任何播放器
     * （原先的 MediaPlayer 已移除，见 {@link AudioPlaybackManager}）。
     */
    public void releaseMediaPlayer() {
        AudioPlaybackManager.stop();
    }

    /**
     * 播放一条"已生成好的"音频（本地文件或远程 URL）。
     *
     * 直连播放 + token 过期重试 + 失败回退下载的整条链路都在
     * {@link SpeakerAudioSource} 里，本方法只做分发。
     */
    private void playAudio(String audioUrl) {
        if (audioUrl == null || audioUrl.isEmpty()) {
            return;
        }
        boolean isLocal = !audioUrl.startsWith("http://") && !audioUrl.startsWith("https://");
        // 统一入口：管理器会先打断流式朗读，避免两路声音重叠
        AudioPlaybackManager.playSpeaker(context,
                isLocal ? audioUrl : null,
                isLocal ? null : audioUrl,
                isLocal,          // 本地缓存文件播完即删；远程直连不产生文件
                null);
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
