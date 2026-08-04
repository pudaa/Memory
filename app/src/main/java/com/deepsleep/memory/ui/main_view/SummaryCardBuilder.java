package com.deepsleep.memory.ui.main_view;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.deepsleep.memory.R;
import com.deepsleep.memory.handle_utils.AudioPlayer;
import com.deepsleep.memory.handle_utils.lexicon.LexiconResourceMap;
import com.deepsleep.memory.handle_utils.lexicon.WordEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 总结卡片构建器：今日完成 / 词书完成
 */
public class SummaryCardBuilder {

    private final Context context;
    private final LayoutInflater inflater;

    public SummaryCardBuilder(Context context) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
    }

    /**
     * 构建今日学习总结卡片
     * 
     * @param wordCards        今日所有卡片（含已操作标记，当前加载时有效）
     * @param filteredSnapshot 被过滤的快照（服务端仍返回已完成单词时有效）
     * @param completedCount   已完成单词总数
     * @param persistedDetails 持久化的已完成单词详情（wordId + isCorrect，跨页面重建用）
     * @param lexiconId        词书ID（用于从本地词书查询 headWord）
     */
    public View buildTodaySummary(List<WordCard> wordCards, List<WordCard> filteredSnapshot, int completedCount,
            List<DailyStateManager.CompletedWordEntry> persistedDetails, String lexiconId) {
        View view = inflater.inflate(R.layout.card_summary_layout, null);
        LinearLayout wordContainer = view.findViewById(R.id.summary_word_container);
        TextView tvCorrect = view.findViewById(R.id.tv_summary_correct);
        TextView tvWrong = view.findViewById(R.id.tv_summary_wrong);
        LinearLayout statsRow = view.findViewById(R.id.summary_stats_row);

        int correct = 0, wrong = 0;
        List<WordCard> displayCards = new ArrayList<>();

        // ── 核心策略：以 persistedDetails 为今日完整单词清单的主数据源 ──
        // persistedDetails 已在每次 markCompletedWithResult / addFilteredCard 时同步更新，
        // 包含了今天所有完成过的单词 + 其 isCorrect 状态。即使跨页面/杀进程也能完整恢复。
        if (persistedDetails != null && !persistedDetails.isEmpty()) {
            // 用 wordId 建立当前会话中的数据索引（用于获取富文本：definition 等）
            java.util.Map<Integer, WordCard> currentCardMap = new java.util.HashMap<>();
            for (WordCard wc : wordCards) {
                if (wc.isOperated)
                    currentCardMap.put(wc.word_id, wc);
            }
            if (filteredSnapshot != null) {
                for (WordCard wc : filteredSnapshot) {
                    if (!currentCardMap.containsKey(wc.word_id)) {
                        currentCardMap.put(wc.word_id, wc);
                    }
                }
            }

            for (DailyStateManager.CompletedWordEntry entry : persistedDetails) {
                WordCard card;
                // 优先用当前会话中已存在的 WordCard（带完整 definition）
                WordCard existing = currentCardMap.get(entry.wordId);
                if (existing != null) {
                    card = existing;
                    // 当前会话的 isCorrect 权威更高（用户当场作答的结果）
                    if (card.isOperated) {
                        entry.isCorrect = card.isCorrect;
                    }
                } else {
                    // 从本地词书重建
                    card = rebuildCardFromId(entry.wordId, entry.isCorrect, lexiconId);
                }
                if (card != null) {
                    card.isOperated = true;
                    card.isCorrect = entry.isCorrect;
                    card.fsrsScore = entry.fsrsScore;
                    card.aiFeedback = entry.aiFeedback;
                    displayCards.add(card);
                    if (entry.isCorrect)
                        correct++;
                    else
                        wrong++;
                }
            }
        }
        // ── 降级：persistedDetails 为空时（跨天重置、首日首次等），用内存数据 ──
        else {
            for (WordCard wc : wordCards) {
                if (wc.isOperated) {
                    displayCards.add(wc);
                    if (wc.isCorrect)
                        correct++;
                    else
                        wrong++;
                }
            }
            if (displayCards.isEmpty() && filteredSnapshot != null && !filteredSnapshot.isEmpty()) {
                displayCards = filteredSnapshot;
            }
        }

        boolean hasScore = !displayCards.isEmpty();

        if (hasScore && statsRow != null) {
            if (tvCorrect != null)
                tvCorrect.setText("✅ " + correct + " 正确");
            if (tvWrong != null)
                tvWrong.setText("❌ " + wrong + " 错误");
        } else if (statsRow != null) {
            statsRow.setVisibility(View.GONE);
        }

        if (wordContainer != null) {
            for (WordCard wc : displayCards) {
                addWordRow(wordContainer, wc, hasScore);
            }
        }

        if (displayCards.isEmpty() && completedCount > 0 && wordContainer != null) {
            TextView tv = new TextView(context);
            tv.setText("今日已完成 " + completedCount + " 个单词");
            tv.setTextSize(15);
            tv.setTextColor(ContextCompat.getColor(context, R.color.theme_text_primary));
            tv.setGravity(Gravity.CENTER);
            wordContainer.addView(tv);
        }

        return view;
    }

    /**
     * 根据 wordId 从本地词书重建轻量 WordCard（用于离开页面后的总结卡片重建）
     */
    private WordCard rebuildCardFromId(int wordId, boolean isCorrect, String lexiconId) {
        if (lexiconId == null || lexiconId.isEmpty())
            return null;
        WordEntry entry = LexiconResourceMap.getWordByRank(lexiconId, wordId);
        if (entry == null)
            return null;

        String headWord = entry.getHeadWord();
        if (headWord == null || headWord.isEmpty())
            return null;

        String definition = "中文释义:\n" + entry.getChineseTranslation() + "\n\n英文释义:\n" + entry.getEnglishDefinition();
        String phonetic = "美音:" + entry.getUsPhone() + " | 英音:" + entry.getUkPhone();
        WordCard card = new WordCard(wordId, headWord, phonetic, definition, "");
        card.isCorrect = isCorrect;
        card.isOperated = true;
        return card;
    }

    private void addWordRow(LinearLayout container, WordCard wc, boolean showScore) {
        // 单词行
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 8, 0, 2);

        if (showScore) {
            boolean isInputMode = (wc.fsrsScore > 0);
            TextView tvIcon = new TextView(context);
            if (isInputMode) {
                // 输入模式：显示 AI 评分 1-4
                tvIcon.setText(String.valueOf(wc.fsrsScore));
                tvIcon.setTextSize(16);
                tvIcon.setTypeface(null, android.graphics.Typeface.BOLD);
                // 按分数着色
                int scoreColor;
                switch (wc.fsrsScore) {
                case 4:
                    scoreColor = android.graphics.Color.parseColor("#4CAF50");
                    break; // 绿色-完全掌握
                case 3:
                    scoreColor = android.graphics.Color.parseColor("#2196F3");
                    break; // 蓝色-基本掌握
                case 2:
                    scoreColor = android.graphics.Color.parseColor("#FF9800");
                    break; // 橙色-部分理解
                default:
                    scoreColor = android.graphics.Color.parseColor("#F44336");
                    break; // 红色-不理解
                }
                tvIcon.setTextColor(scoreColor);
            } else {
                // 选择题模式：保持 ✅/❌
                tvIcon.setText(wc.isCorrect ? "✅" : "❌");
                tvIcon.setTextSize(14);
            }
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            ip.setMarginEnd(8);
            tvIcon.setLayoutParams(ip);
            row.addView(tvIcon);
        }

        TextView tvWord = new TextView(context);
        tvWord.setText(wc.word);
        tvWord.setTextSize(16);
        tvWord.setTypeface(null, android.graphics.Typeface.BOLD);
        tvWord.setTextColor(ContextCompat.getColor(context, R.color.theme_text_primary));
        tvWord.setOnClickListener(v -> AudioPlayer.playAudio(context, wc.word, true));
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvWord.setLayoutParams(wp);
        row.addView(tvWord);

        TextView tvStats = new TextView(context);
        tvStats.setText(String.format(Locale.getDefault(), "D%.1f  S%.1fd", wc.difficulty, wc.stability));
        tvStats.setTextSize(12);
        tvStats.setTextColor(ContextCompat.getColor(context, R.color.middle_gray));
        row.addView(tvStats);

        container.addView(row);

        // 释义行
        String meaning = extractShortMeaning(wc.definition);
        if (!meaning.isEmpty()) {
            TextView tvMeaning = new TextView(context);
            tvMeaning.setText(meaning);
            tvMeaning.setTextSize(12);
            tvMeaning.setTextColor(ContextCompat.getColor(context, R.color.middle_gray));
            tvMeaning.setPadding(0, 2, 0, 4);
            container.addView(tvMeaning);
        }

        // AI 反馈行（仅输入模式）
        if (wc.fsrsScore > 0 && wc.aiFeedback != null && !wc.aiFeedback.isEmpty()) {
            TextView tvAi = new TextView(context);
            tvAi.setText("AI: " + wc.aiFeedback);
            tvAi.setTextSize(12);
            tvAi.setTextColor(ContextCompat.getColor(context, R.color.middle_gray));
            tvAi.setPadding(0, 0, 0, 4);
            container.addView(tvAi);
        }

        // 分隔线
        View divider = new View(context);
        divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        divider.setBackgroundColor(ContextCompat.getColor(context, R.color.light_gray));
        container.addView(divider);
    }

    static String extractShortMeaning(String definition) {
        if (definition == null || definition.isEmpty())
            return "";
        int start = definition.indexOf("中文释义:\n");
        if (start < 0)
            return "";
        start += "中文释义:\n".length();
        int end = definition.indexOf("\n\n英文释义", start);
        if (end < 0)
            end = definition.length();
        String cn = definition.substring(start, end).trim();
        if (cn.length() > 30)
            cn = cn.substring(0, 28) + "…";
        return cn;
    }
}
