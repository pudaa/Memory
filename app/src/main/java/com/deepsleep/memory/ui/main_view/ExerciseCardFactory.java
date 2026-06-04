package com.deepsleep.memory.ui.main_view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import com.deepsleep.memory.R;
import com.deepsleep.memory.handle_utils.AudioPlayer;
import com.deepsleep.memory.handle_utils.lexicon.LexiconResourceMap;
import com.deepsleep.memory.handle_utils.lexicon.WordEntry;
import com.deepsleep.memory.network.GetDataByThread;

import java.util.ArrayList;
import java.util.List;

/**
 * 练习卡片视图工厂：选择题/输入题卡片创建与交互
 */
public class ExerciseCardFactory {

    public interface Callback {
        void onSubmitAnswer(WordCard wordCard, long responseTimeMs);

        void onMoveToNext();
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final String lexiconId;
    private final String studyMode;
    private final int userId;
    private final Callback callback;

    public ExerciseCardFactory(Context context, String lexiconId, String studyMode, int userId, Callback callback) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.lexiconId = lexiconId;
        this.studyMode = studyMode;
        this.userId = userId;
        this.callback = callback;
    }

    @SuppressLint("ClickableViewAccessibility")
    public View createExerciseCardView(WordCard wordCard) {
        View cardView;
        if (WordCard.MODE_INPUT.equals(studyMode)) {
            cardView = inflater.inflate(R.layout.word_card_input_layout, null);
            setupInputCardView(cardView, wordCard);
        } else {
            cardView = inflater.inflate(R.layout.word_card_choice_layout, null);
            setupChoiceCardView(cardView, wordCard);
        }
        setupCommonCardView(cardView, wordCard);
        return cardView;
    }

    // ── 公共部分 ──

    private void setupCommonCardView(View cardView, WordCard wordCard) {
        TextView tvWord = cardView.findViewById(R.id.tv_word);
        TextView tvPhoneticUS = cardView.findViewById(R.id.tv_phonetic_US);
        TextView tvPhoneticUK = cardView.findViewById(R.id.tv_phonetic_UK);
        ImageButton btnFavorite = cardView.findViewById(R.id.btn_favorite);

        String[] phoneticArray = wordCard.phonetic.split("\\|");
        tvWord.setText(wordCard.word);
        tvPhoneticUS.setText(phoneticArray[0]);
        tvPhoneticUK.setText(phoneticArray[1]);

        tvPhoneticUS.setOnClickListener(v -> AudioPlayer.playAudio(context, wordCard.word, true));
        tvPhoneticUK.setOnClickListener(v -> AudioPlayer.playAudio(context, wordCard.word, false));

        btnFavorite.setImageResource(
                wordCard.isFavorite ? R.drawable.baseline_star_24 : R.drawable.baseline_star_border_24);
        btnFavorite.setOnClickListener(v -> {
            boolean isFav = !wordCard.isFavorite;
            wordCard.isFavorite = isFav;
            btnFavorite.setImageResource(isFav ? R.drawable.baseline_star_24 : R.drawable.baseline_star_border_24);
            GetDataByThread setFav = new GetDataByThread("/learning/setFavorite");
            setFav.updateFavorite(new Handler(Looper.getMainLooper()) {
            }, 1, -1, String.valueOf(userId), wordCard.word_id, lexiconId, wordCard.word, isFav);
        });

        LinearLayout fsrsBar = cardView.findViewById(R.id.fsrs_info_bar);
        if (fsrsBar != null && wordCard.type == WordCard.TYPE_REVIEW) {
            fsrsBar.setVisibility(View.VISIBLE);
            TextView tvR = cardView.findViewById(R.id.tv_retrievability);
            TextView tvD = cardView.findViewById(R.id.tv_difficulty);
            TextView tvS = cardView.findViewById(R.id.tv_stability);
            if (tvR != null)
                tvR.setText(String.format("R:%.2f", wordCard.retrievability));
            if (tvD != null)
                tvD.setText(String.format("D:%.1f", wordCard.difficulty));
            if (tvS != null)
                tvS.setText(String.format("S:%.1fd", wordCard.stability));
        }
    }

    // ── 选择题模式 ──

    private int selectedOptionIndex = -1;

    private void setupChoiceCardView(View cardView, WordCard wordCard) {
        WordEntry entry = LexiconResourceMap.getWordByRank(lexiconId, wordCard.word_id);
        final String correctMeaning = entry != null ? entry.getChineseTranslation() : "";
        selectedOptionIndex = -1;

        String[] options = generateChoiceOptions(wordCard.word_id, correctMeaning);
        final int correctIdx = (int) (Math.random() * 4);

        int[] optionIds = { R.id.option_a, R.id.option_b, R.id.option_c, R.id.option_d };
        int[] optionTextIds = { R.id.option_a_text, R.id.option_b_text, R.id.option_c_text, R.id.option_d_text };
        int[] optionCardIds = { R.id.option_a_card, R.id.option_b_card, R.id.option_c_card, R.id.option_d_card };

        int distractorIdx = 0;
        for (int i = 0; i < 4; i++) {
            TextView optText = cardView.findViewById(optionTextIds[i]);
            String displayText = (i == correctIdx) ? correctMeaning
                    : (distractorIdx < options.length ? options[distractorIdx++] : "——");
            optText.setText(displayText != null ? displayText : "——");

            final int idx = i;
            cardView.findViewById(optionIds[i]).setOnClickListener(v -> {
                selectedOptionIndex = idx;
                highlightSelectedOption(cardView, optionCardIds, idx);
                View btnConfirm = cardView.findViewById(R.id.btn_confirm);
                if (btnConfirm != null)
                    btnConfirm.setEnabled(true);
            });
        }

        cardView.setTag(R.id.option_a, correctIdx);
        cardView.setTag(R.id.option_b, correctMeaning);
        cardView.setTag(wordCard);

        View btnConfirm = cardView.findViewById(R.id.btn_confirm);
        View btnNext = cardView.findViewById(R.id.btn_next);
        View feedbackContainer = cardView.findViewById(R.id.feedback_container);

        if (btnConfirm != null) {
            btnConfirm.setEnabled(false);
            btnConfirm.setOnClickListener(v -> {
                if (selectedOptionIndex < 0)
                    return;
                boolean isCorrect = (selectedOptionIndex == correctIdx);
                wordCard.isCorrect = isCorrect;
                long responseTimeMs = System.currentTimeMillis() - wordCard.displayStartTime;
                showChoiceFeedback(cardView, feedbackContainer, btnConfirm, btnNext, optionCardIds, correctIdx,
                        selectedOptionIndex, isCorrect, correctMeaning);
                callback.onSubmitAnswer(wordCard, responseTimeMs);
            });
        }
        if (btnNext != null) {
            btnNext.setOnClickListener(v -> callback.onMoveToNext());
        }
    }

    private String[] generateChoiceOptions(int correctWordId, String correctMeaning) {
        String[] options = new String[3];
        List<WordEntry> allEntries = LexiconResourceMap.getAllEntries(lexiconId);
        List<String> candidates = new ArrayList<>();
        if (allEntries != null) {
            for (WordEntry e : allEntries) {
                if (e.getWordRank() != correctWordId) {
                    String m = e.getChineseTranslation();
                    if (m != null && !m.isEmpty() && !m.equals(correctMeaning))
                        candidates.add(m);
                }
            }
        }
        java.util.Collections.shuffle(candidates);
        int idx = 0;
        for (int i = 0; i < 3 && i < candidates.size(); i++)
            options[idx++] = candidates.get(i);
        while (idx < 3)
            options[idx++] = "——";
        return options;
    }

    private void highlightSelectedOption(View cardView, int[] cardIds, int selectedIdx) {
        for (int i = 0; i < cardIds.length; i++) {
            View card = cardView.findViewById(cardIds[i]);
            if (card != null) {
                LinearLayout inner = (LinearLayout) ((CardView) card).getChildAt(0);
                inner.setBackgroundResource(i == selectedIdx ? R.drawable.option_background_selected
                        : R.drawable.option_background_default);
            }
        }
    }

    private void showChoiceFeedback(View cardView, View feedbackContainer, View btnConfirm, View btnNext,
            int[] optionCardIds, int correctIdx, int selectedIdx, boolean isCorrect, String correctMeaning) {
        for (int i = 0; i < optionCardIds.length; i++) {
            View card = cardView.findViewById(optionCardIds[i]);
            if (card != null) {
                LinearLayout inner = (LinearLayout) ((CardView) card).getChildAt(0);
                if (i == correctIdx)
                    inner.setBackgroundResource(R.drawable.option_background_correct);
                else if (i == selectedIdx && !isCorrect)
                    inner.setBackgroundResource(R.drawable.option_background_wrong);
                else
                    inner.setBackgroundResource(R.drawable.option_background_default);
            }
        }
        if (feedbackContainer != null) {
            feedbackContainer.setVisibility(View.VISIBLE);
            TextView tvResult = cardView.findViewById(R.id.tv_feedback_result);
            TextView tvMeaning = cardView.findViewById(R.id.tv_correct_meaning);
            if (tvResult != null) {
                tvResult.setText(isCorrect ? "✅ 正确！" : "❌ 错误");
                tvResult.setTextColor(isCorrect ? android.graphics.Color.parseColor("#4CAF50")
                        : android.graphics.Color.parseColor("#F44336"));
            }
            if (tvMeaning != null)
                tvMeaning.setText("正确释义：" + correctMeaning);
        }
        if (btnConfirm != null)
            btnConfirm.setVisibility(View.GONE);
        if (btnNext != null)
            btnNext.setVisibility(View.VISIBLE);
        disableOptionClicks(cardView);
    }

    private void disableOptionClicks(View cardView) {
        for (int id : new int[] { R.id.option_a, R.id.option_b, R.id.option_c, R.id.option_d }) {
            View opt = cardView.findViewById(id);
            if (opt != null)
                opt.setClickable(false);
        }
    }

    // ── 输入题模式 ──

    private void setupInputCardView(View cardView, WordCard wordCard) {
        WordEntry entry = LexiconResourceMap.getWordByRank(lexiconId, wordCard.word_id);
        final String correctMeaning = entry != null ? entry.getChineseTranslation() : "";
        final String pos = entry != null ? entry.getPos() : "";

        android.widget.EditText etInput = cardView.findViewById(R.id.et_user_input);
        View btnSubmit = cardView.findViewById(R.id.btn_submit);
        View btnSkip = cardView.findViewById(R.id.btn_skip);
        View btnNext = cardView.findViewById(R.id.btn_next);
        View feedbackContainer = cardView.findViewById(R.id.feedback_container);
        View btnContainer = cardView.findViewById(R.id.btn_container);
        TextView tvAiFeedback = cardView.findViewById(R.id.tv_ai_feedback);

        // 将 WordCard 绑定到 cardView，方便后续通过视图查找卡片数据
        cardView.setTag(wordCard);

        if (btnSubmit != null) {
            btnSubmit.setOnClickListener(v -> {
                String userInput = etInput != null ? etInput.getText().toString().trim() : "";
                if (userInput.isEmpty())
                    return;
                boolean isCorrect = checkInputCorrect(userInput, correctMeaning);
                wordCard.isCorrect = isCorrect;
                wordCard.userAnswer = userInput;
                wordCard.referenceDefinition = correctMeaning;
                wordCard.pos = pos;

                long rt = System.currentTimeMillis() - wordCard.displayStartTime;
                showInputFeedback(cardView, feedbackContainer, btnContainer, btnNext, isCorrect, correctMeaning);

                // 显示 AI 评判中提示
                if (tvAiFeedback != null) {
                    tvAiFeedback.setText("AI 分析中…");
                    tvAiFeedback.setVisibility(View.VISIBLE);
                }

                if (etInput != null)
                    etInput.setEnabled(false);
                callback.onSubmitAnswer(wordCard, rt);
            });
        }
        if (btnSkip != null) {
            btnSkip.setOnClickListener(v -> {
                wordCard.isCorrect = false;
                long rt = System.currentTimeMillis() - wordCard.displayStartTime;
                showInputFeedback(cardView, feedbackContainer, btnContainer, btnNext, false, correctMeaning);

                // 跳过的没有 AI 评判
                if (tvAiFeedback != null) {
                    tvAiFeedback.setVisibility(View.GONE);
                }

                if (etInput != null)
                    etInput.setEnabled(false);
                callback.onSubmitAnswer(wordCard, rt > 0 ? rt : 5000);
            });
        }
        if (btnNext != null)
            btnNext.setOnClickListener(v -> callback.onMoveToNext());
    }

    private boolean checkInputCorrect(String userInput, String correctMeaning) {
        if (userInput.isEmpty() || correctMeaning.isEmpty())
            return false;
        String u = userInput.replaceAll("\\s+", "").toLowerCase();
        String c = correctMeaning.replaceAll("\\s+", "").toLowerCase();
        return u.contains(c) || c.contains(u)
                || (u.length() >= 2 && c.contains(u.substring(0, Math.min(u.length(), 4))));
    }

    private void showInputFeedback(View cardView, View feedbackContainer, View btnContainer, View btnNext,
            boolean isCorrect, String correctMeaning) {
        if (feedbackContainer != null) {
            feedbackContainer.setVisibility(View.VISIBLE);
            TextView tvResult = cardView.findViewById(R.id.tv_feedback_result);
            TextView tvMeaning = cardView.findViewById(R.id.tv_correct_meaning);
            if (tvResult != null) {
                // 输入模式：不提前显示对错，只显示中性提交状态
                tvResult.setText("已提交");
                tvResult.setTextColor(android.graphics.Color.parseColor("#9E9E9E"));
            }
            if (tvMeaning != null)
                tvMeaning.setText("标准释义：" + correctMeaning);
        }
        if (btnContainer != null)
            btnContainer.setVisibility(View.GONE);
        if (btnNext != null)
            btnNext.setVisibility(View.VISIBLE);
    }

    /**
     * 外部更新输入模式的评判结果（API 响应到达后调用）
     *
     * @param cardView   卡片视图
     * @param fsrsScore  AI 评分 1-4
     * @param isCorrect  服务端判定的对错
     * @param aiFeedback AI 反馈文字
     */
    public static void updateInputFeedbackResult(View cardView, int fsrsScore, boolean isCorrect, String aiFeedback) {
        if (cardView == null) return;

        TextView tvResult = cardView.findViewById(R.id.tv_feedback_result);
        if (tvResult != null) {
            // 显示评分数字 + 等级描述
            String level;
            int color;
            switch (fsrsScore) {
                case 4:  level = "完全掌握";  color = 0xFF4CAF50; break;
                case 3:  level = "基本掌握";  color = 0xFF2196F3; break;
                case 2:  level = "部分理解";  color = 0xFFFF9800; break;
                default: level = "不理解";    color = 0xFFF44336; break;
            }
            if (fsrsScore > 0) {
                tvResult.setText(fsrsScore + "  " + level);
            } else {
                // 降级：服务端未评分，回退到二值
                tvResult.setText(isCorrect ? "✅ 正确" : "❌ 错误");
                color = isCorrect ? 0xFF4CAF50 : 0xFFF44336;
            }
            tvResult.setTextColor(color);
        }

        TextView tvAiFeedback = cardView.findViewById(R.id.tv_ai_feedback);
        if (tvAiFeedback != null) {
            if (aiFeedback != null && !aiFeedback.isEmpty()) {
                tvAiFeedback.setText(aiFeedback);
                tvAiFeedback.setVisibility(View.VISIBLE);
            } else {
                tvAiFeedback.setVisibility(View.GONE);
            }
        }
    }
}
