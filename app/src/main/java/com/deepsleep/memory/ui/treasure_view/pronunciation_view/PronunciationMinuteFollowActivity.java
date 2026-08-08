package com.deepsleep.memory.ui.treasure_view.pronunciation_view;

import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.deepsleep.memory.R;
import com.deepsleep.memory.handle_utils.lexicon.LexiconResourceMap;
import com.deepsleep.memory.handle_utils.lexicon.WordEntry;
import com.deepsleep.memory.network.GetDataByThread;
import com.deepsleep.memory.settings.InnerSettingsManager;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class PronunciationMinuteFollowActivity extends AppCompatActivity
        implements WordPhraseListAdapter.OnScoreResultListener {
    private static final String TAG = "PronunciationMinute";
    private static final int MSG_WORDS_SUCCESS = 1;
    private static final int MSG_WORDS_FAIL = 2;

    // wordBookId → 本地词书 lexiconId 映射
    private static final Map<Integer, String> WORD_BOOK_TO_LEXICON = new HashMap<>();
    static {
        WORD_BOOK_TO_LEXICON.put(1, "kaoyanluan_1");
        WORD_BOOK_TO_LEXICON.put(2, "kaoyan_3");
        WORD_BOOK_TO_LEXICON.put(5, "kaoyanluan_1");
        WORD_BOOK_TO_LEXICON.put(6, "kaoyanluan_1");
        WORD_BOOK_TO_LEXICON.put(7, "kaoyan_3");
    }

    private BottomSheetBehavior<View> bottomSheetBehavior;
    private ListView wordsListView;
    private WordPhraseListAdapter adapter;
    private List<WordPhraseItem> wordPhraseList;
    private String currentLexiconId;
    private TextView tvTitle;

    // BottomSheet 视图
    private TextView bsScore, bsLevel, bsAsrText, bsPhoneme, bsFeedback;
    private MaterialCardView bsTextCompare, bsWordScoresContainer;
    private LinearLayout bsWordScores, bsWordDetail, bsSummarySection;
    private LinearLayout bsSummaryStats, bsSummaryWords, bsSummaryErrors;
    private FrameLayout bsScoreCircle;
    private ScrollView bsScroll;

    private ImageButton backButton;

    // 自动加载下一组：追踪已评分单词 & 已出现单词（去重）
    private boolean isLoadingMore = false;
    private final Set<String> scoredWordTexts = new HashSet<>();
    private final Set<String> seenWordTexts = new HashSet<>();

    // 成绩持久化 & 汇总
    private final List<ScoreRecord> todayScores = new ArrayList<>();
    private boolean isSummaryMode = false;

    private static class ScoreRecord {
        String word;
        int overallScore;
        String level;
        String feedback;
        String asrTranscript;
        final List<PhonemeError> errors = new ArrayList<>();
    }

    private static class PhonemeError {
        String type;
        String expected;
        String actual;
        String word;
    }

    private String topicName;
    private int wordBookId, phraseCount, sentenceCount, hasIntro, userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pronunciation_minute_follow_layout);

        topicName = getIntent().getStringExtra("topicName");
        wordBookId = getIntent().getIntExtra("wordBookId", 1);
        phraseCount = getIntent().getIntExtra("phraseCount", 0);
        sentenceCount = getIntent().getIntExtra("sentenceCount", 0);
        hasIntro = getIntent().getIntExtra("hasIntro", 0);
        if (topicName == null)
            topicName = "每日一分钟";

        wordsListView = findViewById(R.id.words_list_view);
        tvTitle = findViewById(R.id.tv_title);
        tvTitle.setText(topicName);

        bottomSheetBehavior = BottomSheetBehavior.from(findViewById(R.id.bottom_sheet));
        bsScore = findViewById(R.id.bs_score);
        bsScoreCircle = findViewById(R.id.bs_score_circle);
        bsLevel = findViewById(R.id.bs_level);
        bsAsrText = findViewById(R.id.bs_asr_text);
        bsPhoneme = findViewById(R.id.bs_phoneme);
        bsFeedback = findViewById(R.id.bs_feedback);
        bsTextCompare = findViewById(R.id.bs_text_compare);
        bsWordScores = findViewById(R.id.bs_word_scores);
        bsWordScoresContainer = findViewById(R.id.bs_word_scores_container);
        bsWordDetail = findViewById(R.id.bs_word_detail);
        bsSummarySection = findViewById(R.id.bs_summary_section);
        bsSummaryStats = findViewById(R.id.bs_summary_stats);
        bsSummaryWords = findViewById(R.id.bs_summary_words);
        bsSummaryErrors = findViewById(R.id.bs_summary_errors);
        bsScroll = findViewById(R.id.bs_scroll);

        backButton = findViewById(R.id.btn_back);
        backButton.setOnClickListener(v -> finish());

        userId = InnerSettingsManager.getInstance(this).getUserId();

        wordPhraseList = new ArrayList<>();
        initListView();
        initBottomSheet();
        loadTodayScores();
        fetchWordList();
    }

    private void initListView() {
        adapter = new WordPhraseListAdapter(this, wordPhraseList);
        adapter.setOnScoreResultListener(this);
        wordsListView.setAdapter(adapter);
    }

    private void initBottomSheet() {
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        bottomSheetBehavior.setHideable(true);
        bottomSheetBehavior.setSkipCollapsed(true);

        findViewById(R.id.bs_close_btn).setOnClickListener(v -> {
            if (isSummaryMode) {
                switchToWordDetailMode();
            }
            hideBottomSheet();
        });

        bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN && isSummaryMode) {
                    switchToWordDetailMode();
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
            }
        });
    }

    // ==================== 评分回调（来自 Adapter） ====================

    @Override
    public void onScoreResult(String word, double overallScore, String level, String feedback, String asrTranscript,
            String referenceText, JSONArray words) {
        // 大分数 - 用圆圈背景色表示状态，文字始终白色
        if (overallScore >= 0) {
            bsScore.setText(String.format(Locale.getDefault(), "%.0f", overallScore));
            bsScoreCircle.setBackgroundResource(R.drawable.bg_follow_score_circle);
        } else {
            bsScore.setText("?");
            bsScoreCircle.setBackgroundResource(R.drawable.bg_follow_score_circle_error);
        }

        // 等级（仅有效评分时显示）
        if (overallScore >= 0 && level != null && !level.isEmpty()) {
            bsLevel.setVisibility(View.VISIBLE);
            bsLevel.setText(toLevelText(level));
        } else {
            bsLevel.setVisibility(View.GONE);
        }

        // 识别文本对比
        if (asrTranscript != null && !asrTranscript.isEmpty()) {
            bsTextCompare.setVisibility(View.VISIBLE);
            bsAsrText.setText(asrTranscript);
        } else {
            bsTextCompare.setVisibility(View.GONE);
        }

        // 反馈
        if (feedback != null && !feedback.isEmpty()) {
            bsFeedback.setVisibility(View.VISIBLE);
            bsFeedback.setText(feedback);
        } else {
            bsFeedback.setVisibility(View.GONE);
        }

        // 逐词评分（含音素错误详情）
        bsWordScores.removeAllViews();
        bsWordScoresContainer.setVisibility(View.GONE);
        if (words != null && words.length() > 0) {
            bsWordScoresContainer.setVisibility(View.VISIBLE);
            for (int i = 0; i < words.length(); i++) {
                try {
                    JSONObject w = words.getJSONObject(i);
                    String wrd = w.getString("word");
                    double wScore = w.optDouble("score", -1);
                    String status = w.optString("status", "");
                    String spokenWord = w.optString("spokenWord", "");

                    // ── 单词行：单词名 + 分数 ──
                    LinearLayout row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setPadding(0, 6, 0, 2);

                    TextView tvWord = new TextView(this);
                    tvWord.setText(wrd);
                    tvWord.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                    tvWord.setTextColor(ContextCompat.getColor(this, R.color.theme_text_primary));
                    tvWord.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                    row.addView(tvWord);

                    TextView tvScore = new TextView(this);
                    tvScore.setGravity(Gravity.END);
                    tvScore.setMinWidth(dpToPx(48));
                    if (wScore >= 0) {
                        tvScore.setText(String.format(Locale.getDefault(), "%.0f", wScore));
                        tvScore.setTextColor(ContextCompat.getColor(this, "missing".equals(status) ? R.color.theme_error
                                : "mispronounced".equals(status) ? R.color.theme_stress : R.color.teal_200));
                    } else {
                        tvScore.setText("?");
                        tvScore.setTextColor(ContextCompat.getColor(this, R.color.theme_text_secondary));
                    }
                    tvScore.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                    tvScore.setTypeface(null, Typeface.BOLD);
                    tvScore.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT));
                    row.addView(tvScore);

                    bsWordScores.addView(row);

                    // ── 发音偏差提示 ──
                    if (!spokenWord.isEmpty() && !spokenWord.equals(wrd)) {
                        TextView tvSpoken = new TextView(this);
                        tvSpoken.setText("听到: \"" + spokenWord + "\"");
                        tvSpoken.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                        tvSpoken.setTextColor(ContextCompat.getColor(this, R.color.theme_stress));
                        tvSpoken.setPadding(dpToPx(8), 0, 0, 0);
                        bsWordScores.addView(tvSpoken);
                    }

                    // ── 音素错误详情 ──
                    JSONArray errs = w.optJSONArray("errors");
                    if (errs != null && errs.length() > 0) {
                        for (int j = 0; j < errs.length(); j++) {
                            JSONObject err = errs.getJSONObject(j);
                            String type = err.optString("type", "?");
                            String expected = err.optString("expected", "?");
                            String actual = err.optString("actual", "?");

                            TextView tvErr = new TextView(this);
                            String typeLabel;
                            int typeColor;
                            switch (type) {
                            case "substitution":
                                typeLabel = "替换";
                                typeColor = R.color.theme_stress;
                                break;
                            case "deletion":
                                typeLabel = "遗漏";
                                typeColor = R.color.theme_error;
                                break;
                            case "insertion":
                                typeLabel = "多余";
                                typeColor = R.color.theme_error;
                                break;
                            default:
                                typeLabel = type;
                                typeColor = R.color.theme_text_secondary;
                                break;
                            }
                            tvErr.setText("  ↳ " + typeLabel + ": " + expected + " → " + actual);
                            tvErr.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                            tvErr.setTextColor(ContextCompat.getColor(this, typeColor));
                            tvErr.setPadding(dpToPx(8), 0, 0, 2);
                            bsWordScores.addView(tvErr);
                        }
                    }
                } catch (JSONException ignored) {
                }
            }
        }

        // 弹出 BottomSheet（单词详情模式）
        switchToWordDetailMode();
        showBottomSheet();

        // ── 记录成绩 + 持久化 ──
        ScoreRecord record = new ScoreRecord();
        record.word = word;
        record.overallScore = (int) overallScore;
        record.level = level != null ? level : "";
        record.feedback = feedback != null ? feedback : "";
        record.asrTranscript = asrTranscript != null ? asrTranscript : "";
        if (words != null) {
            for (int i = 0; i < words.length(); i++) {
                try {
                    JSONObject w = words.getJSONObject(i);
                    JSONArray errs = w.optJSONArray("errors");
                    if (errs != null) {
                        for (int j = 0; j < errs.length(); j++) {
                            JSONObject err = errs.getJSONObject(j);
                            PhonemeError pe = new PhonemeError();
                            pe.type = err.optString("type", "");
                            pe.expected = err.optString("expected", "");
                            pe.actual = err.optString("actual", "");
                            pe.word = w.optString("word", word);
                            record.errors.add(pe);
                        }
                    }
                } catch (JSONException ignored) {
                }
            }
        }
        // 移除同词旧记录（允许重复练习覆盖成绩）
        for (int i = todayScores.size() - 1; i >= 0; i--) {
            if (todayScores.get(i).word.equals(word)) {
                todayScores.remove(i);
            }
        }
        todayScores.add(record);
        saveTodayScores();

        // ── 追踪完成状态，全部练完后自动加载下一组 ──
        if (word != null && !word.isEmpty() && scoredWordTexts.add(word)) {
            for (WordPhraseItem item : wordPhraseList) {
                if (word.equals(item.getWord())) {
                    item.setCorrectlyPronounced(true);
                    break;
                }
            }
        }
        if (!isLoadingMore && scoredWordTexts.size() >= wordPhraseList.size() && !wordPhraseList.isEmpty()) {
            fetchNextBatch();
        }
    }

    private String toLevelText(String level) {
        switch (level) {
        case "excellent":
            return "优秀 Excellent";
        case "good":
            return "良好 Good";
        case "fair":
            return "一般 Fair";
        case "poor":
            return "较弱 Poor";
        case "very_poor":
            return "很弱 Very Poor";
        default:
            return level;
        }
    }

    // ==================== BottomSheet 控制 ====================

    public void showBottomSheet() {
        bsScroll.scrollTo(0, 0);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    public void hideBottomSheet() {
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
    }

    private void switchToWordDetailMode() {
        isSummaryMode = false;
        bsWordDetail.setVisibility(View.VISIBLE);
        bsSummarySection.setVisibility(View.GONE);
    }

    private void switchToSummaryMode() {
        isSummaryMode = true;
        bsWordDetail.setVisibility(View.GONE);
        bsSummarySection.setVisibility(View.VISIBLE);
    }

    // ==================== 单词列表加载 ====================

    private void fetchWordList() {
        // 加载本地词书（供释义查找）
        currentLexiconId = WORD_BOOK_TO_LEXICON.get(wordBookId);
        if (currentLexiconId != null) {
            LexiconResourceMap.loadLexicon(this, currentLexiconId);
        }

        GetDataByThread api = new GetDataByThread("/pronunciation/words");
        api.getPronunciationWords(wordHandler, MSG_WORDS_SUCCESS, MSG_WORDS_FAIL, String.valueOf(userId), wordBookId,
                phraseCount, sentenceCount);
    }

    private final Handler wordHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            if (msg.what == MSG_WORDS_SUCCESS) {
                String result = (String) msg.obj;
                try {
                    JSONObject root = new JSONObject(result);
                    if ("200".equals(String.valueOf(root.optInt("code", -1)))) {
                        JSONArray data = root.getJSONArray("data");
                        wordPhraseList.clear();
                        seenWordTexts.clear();
                        scoredWordTexts.clear();
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject item = data.getJSONObject(i);
                            String word = item.getString("word");
                            String wordId = item.optString("wordId", "");
                            seenWordTexts.add(word);

                            // 从本地词书查找释义
                            String meaning = lookupMeaning(wordId);

                            wordPhraseList.add(new WordPhraseItem(word, meaning, false));
                        }
                        adapter.notifyDataSetChanged();

                        // 恢复今日已练习单词的评分显示
                        for (ScoreRecord r : todayScores) {
                            adapter.restoreScore(r.word, r.overallScore);
                            scoredWordTexts.add(r.word);
                            for (WordPhraseItem item : wordPhraseList) {
                                if (r.word.equals(item.getWord())) {
                                    item.setCorrectlyPronounced(true);
                                    break;
                                }
                            }
                        }
                        adapter.notifyDataSetChanged();

                        Log.i(TAG, "加载了 " + wordPhraseList.size() + " 个单词");
                    } else {
                        Toast.makeText(PronunciationMinuteFollowActivity.this, root.optString("message", "获取单词列表失败"),
                                Toast.LENGTH_SHORT).show();
                        useFallbackWords();
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "解析单词列表失败", e);
                    useFallbackWords();
                }
            } else {
                Toast.makeText(PronunciationMinuteFollowActivity.this, "获取单词列表失败，使用测试数据", Toast.LENGTH_SHORT).show();
                useFallbackWords();
            }
        }
    };

    /** 用 wordId 从已加载的本地词书中查找中文释义 */
    private String lookupMeaning(String wordId) {
        if (currentLexiconId == null || wordId.isEmpty())
            return "";
        try {
            int rank = Integer.parseInt(wordId);
            WordEntry entry = LexiconResourceMap.getWordByRank(currentLexiconId, rank);
            if (entry != null) {
                return entry.getChineseTranslation();
            }
        } catch (NumberFormatException ignored) {
        }
        return "";
    }

    // ==================== 自动加载下一组 ====================

    /** 当前组全部练习完毕后，自动向服务端请求下一组随机单词 */
    private void fetchNextBatch() {
        isLoadingMore = true;
        Toast.makeText(this, "正在加载更多单词...", Toast.LENGTH_SHORT).show();

        GetDataByThread api = new GetDataByThread("/pronunciation/words");
        api.getPronunciationWords(loadMoreHandler, MSG_WORDS_SUCCESS, MSG_WORDS_FAIL, String.valueOf(userId),
                wordBookId, phraseCount, sentenceCount);
    }

    private final Handler loadMoreHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            isLoadingMore = false;
            if (msg.what == MSG_WORDS_SUCCESS) {
                String result = (String) msg.obj;
                try {
                    JSONObject root = new JSONObject(result);
                    if ("200".equals(String.valueOf(root.optInt("code", -1)))) {
                        JSONArray data = root.getJSONArray("data");
                        int addedCount = 0;
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject item = data.getJSONObject(i);
                            String word = item.getString("word");
                            String wordId = item.optString("wordId", "");

                            // 跳过已出现过的单词（去重）
                            if (seenWordTexts.contains(word))
                                continue;
                            seenWordTexts.add(word);

                            String meaning = lookupMeaning(wordId);
                            wordPhraseList.add(new WordPhraseItem(word, meaning, false));
                            addedCount++;
                        }

                        if (addedCount > 0) {
                            scoredWordTexts.clear();
                            // 恢复新批次中已练习单词的评分显示
                            for (ScoreRecord r : todayScores) {
                                adapter.restoreScore(r.word, r.overallScore);
                                scoredWordTexts.add(r.word);
                                for (WordPhraseItem item : wordPhraseList) {
                                    if (r.word.equals(item.getWord())) {
                                        item.setCorrectlyPronounced(true);
                                        break;
                                    }
                                }
                            }
                            adapter.notifyDataSetChanged();
                            wordsListView.smoothScrollToPosition(wordPhraseList.size() - addedCount);
                            Toast.makeText(PronunciationMinuteFollowActivity.this, "已加载 " + addedCount + " 个新单词，继续加油！",
                                    Toast.LENGTH_SHORT).show();
                            Log.i(TAG, "自动加载了 " + addedCount + " 个新单词，当前总计 " + wordPhraseList.size() + " 个");
                        } else {
                            showSummarySheet();
                        }
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "加载更多单词解析失败", e);
                    Toast.makeText(PronunciationMinuteFollowActivity.this, "加载失败，请退出重试", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(PronunciationMinuteFollowActivity.this, "网络异常，加载更多失败", Toast.LENGTH_SHORT).show();
            }
        }
    };

    // ==================== 成绩总结弹窗 ====================

    /** 词书全部练完后，展示今日成绩总结 */
    private void showSummarySheet() {
        if (todayScores.isEmpty()) {
            switchToSummaryMode();
            bsSummaryStats.removeAllViews();
            bsSummaryWords.removeAllViews();
            bsSummaryErrors.removeAllViews();

            addSummaryTitle(bsSummaryStats, "暂无练习记录");
            addSummaryRow(bsSummaryStats, "提示", "完成一些练习后这里会显示你的成绩总结");
            showBottomSheet();
            return;
        }

        switchToSummaryMode();
        bsSummaryStats.removeAllViews();
        bsSummaryWords.removeAllViews();
        bsSummaryErrors.removeAllViews();

        // ── 统计 ──
        int total = todayScores.size();
        double avgScore = 0;
        int bestScore = 0, worstScore = 100;
        String bestWord = "", worstWord = "";
        int excellent = 0, good = 0, fair = 0, poor = 0, veryPoor = 0;
        int substitutionCount = 0, deletionCount = 0, insertionCount = 0;

        for (ScoreRecord r : todayScores) {
            avgScore += r.overallScore;
            if (r.overallScore > bestScore) {
                bestScore = r.overallScore;
                bestWord = r.word;
            }
            if (r.overallScore < worstScore) {
                worstScore = r.overallScore;
                worstWord = r.word;
            }
            switch (r.level) {
            case "excellent":
                excellent++;
                break;
            case "good":
                good++;
                break;
            case "fair":
                fair++;
                break;
            case "poor":
                poor++;
                break;
            case "very_poor":
                veryPoor++;
                break;
            }
            for (PhonemeError e : r.errors) {
                switch (e.type) {
                case "substitution":
                    substitutionCount++;
                    break;
                case "deletion":
                    deletionCount++;
                    break;
                case "insertion":
                    insertionCount++;
                    break;
                }
            }
        }
        avgScore /= total;

        // ── 总览卡片 ──
        addSummaryTitle(bsSummaryStats, "概览");
        addSummaryRow(bsSummaryStats, "练习单词数", String.valueOf(total));
        addSummaryRow(bsSummaryStats, "平均得分", String.format(Locale.getDefault(), "%.0f 分", avgScore));
        addSummaryRow(bsSummaryStats, "最佳发音", bestWord + " (" + bestScore + "分)");
        if (worstScore < 100) {
            addSummaryRow(bsSummaryStats, "需重点练习", worstWord + " (" + worstScore + "分)");
        }

        // ── 等级分布 ──
        StringBuilder levelDist = new StringBuilder();
        if (excellent > 0)
            levelDist.append("优秀 ").append(excellent).append("  ");
        if (good > 0)
            levelDist.append("良好 ").append(good).append("  ");
        if (fair > 0)
            levelDist.append("一般 ").append(fair).append("  ");
        if (poor > 0)
            levelDist.append("较弱 ").append(poor).append("  ");
        if (veryPoor > 0)
            levelDist.append("很弱 ").append(veryPoor);
        addSummaryRow(bsSummaryStats, "等级分布", levelDist.toString().trim());

        // ── 单词列表 ──
        addSummaryTitle(bsSummaryWords, "单词详情");
        for (ScoreRecord r : todayScores) {
            String statusIcon;
            int statusColor;
            if (r.overallScore >= 85) {
                statusIcon = "●";
                statusColor = R.color.teal_200;
            } else if (r.overallScore >= 70) {
                statusIcon = "●";
                statusColor = R.color.theme_primary;
            } else if (r.overallScore >= 50) {
                statusIcon = "●";
                statusColor = R.color.theme_stress;
            } else {
                statusIcon = "●";
                statusColor = R.color.theme_error;
            }

            TextView tv = new TextView(this);
            tv.setText(statusIcon + " " + r.word + " — " + r.overallScore + "分  " + toLevelText(r.level));
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            tv.setTextColor(ContextCompat.getColor(this, statusColor));
            tv.setPadding(0, 4, 0, 4);
            bsSummaryWords.addView(tv);
        }

        // ── 常见错误分析 ──
        addSummaryTitle(bsSummaryErrors, "常见发音问题");
        if (substitutionCount + deletionCount + insertionCount == 0) {
            addSummaryRow(bsSummaryErrors, "太棒了！", "未检测到明显的音素错误");
        } else {
            if (substitutionCount > 0)
                addSummaryRow(bsSummaryErrors, "音素替换", substitutionCount + " 处（如 /θ/ 发成 /s/）");
            if (deletionCount > 0)
                addSummaryRow(bsSummaryErrors, "音素遗漏", deletionCount + " 处（未读出某个音）");
            if (insertionCount > 0)
                addSummaryRow(bsSummaryErrors, "多余音素", insertionCount + " 处（多读了某个音）");
        }

        showBottomSheet();
    }

    private void addSummaryTitle(LinearLayout container, String title) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setTextColor(ContextCompat.getColor(this, R.color.theme_text_primary));
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(0, 8, 0, 8);
        container.addView(tv);
    }

    private void addSummaryRow(LinearLayout container, String label, String value) {
        TextView tv = new TextView(this);
        tv.setText(label + "：" + value);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTextColor(ContextCompat.getColor(this, R.color.theme_text_secondary));
        tv.setPadding(0, 3, 0, 3);
        container.addView(tv);
    }

    // ==================== 成绩持久化 ====================

    private void saveTodayScores() {
        try {
            JSONArray arr = new JSONArray();
            for (ScoreRecord r : todayScores) {
                JSONObject obj = new JSONObject();
                obj.put("word", r.word);
                obj.put("score", r.overallScore);
                obj.put("level", r.level);
                obj.put("feedback", r.feedback);
                obj.put("asr", r.asrTranscript);
                JSONArray errs = new JSONArray();
                for (PhonemeError e : r.errors) {
                    JSONObject eo = new JSONObject();
                    eo.put("type", e.type);
                    eo.put("expected", e.expected);
                    eo.put("actual", e.actual);
                    eo.put("word", e.word);
                    errs.put(eo);
                }
                obj.put("errors", errs);
                arr.put(obj);
            }
            InnerSettingsManager.getInstance(this).savePronunciationScores(getTodayDate(), arr.toString());
        } catch (JSONException e) {
            Log.e(TAG, "保存成绩失败", e);
        }
    }

    private void loadTodayScores() {
        // 先清理过期数据（保留最近 7 天）
        cleanupOldScores(7);

        String json = InnerSettingsManager.getInstance(this).getPronunciationScores(getTodayDate());
        if (json == null)
            return;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                ScoreRecord r = new ScoreRecord();
                r.word = obj.optString("word", "");
                r.overallScore = obj.optInt("score", 0);
                r.level = obj.optString("level", "");
                r.feedback = obj.optString("feedback", "");
                r.asrTranscript = obj.optString("asr", "");
                JSONArray errs = obj.optJSONArray("errors");
                if (errs != null) {
                    for (int j = 0; j < errs.length(); j++) {
                        JSONObject eo = errs.getJSONObject(j);
                        PhonemeError pe = new PhonemeError();
                        pe.type = eo.optString("type", "");
                        pe.expected = eo.optString("expected", "");
                        pe.actual = eo.optString("actual", "");
                        pe.word = eo.optString("word", "");
                        r.errors.add(pe);
                    }
                }
                todayScores.add(r);
            }
            Log.i(TAG, "加载了今日 " + todayScores.size() + " 条历史成绩");
        } catch (JSONException e) {
            // 解析失败则忽略旧数据
        }
    }

    /** 清理超过保留天数的旧成绩数据 */
    private void cleanupOldScores(int keepDays) {
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            java.util.Calendar cutoff = java.util.Calendar.getInstance();
            cutoff.add(java.util.Calendar.DAY_OF_YEAR, -keepDays);

            InnerSettingsManager settings = InnerSettingsManager.getInstance(this);
            for (String dateStr : settings.getPronunciationScoreDates()) {
                try {
                    java.util.Date keyDate = sdf.parse(dateStr);
                    if (keyDate != null && keyDate.before(cutoff.getTime())) {
                        settings.removePronunciationScores(dateStr);
                        Log.i(TAG, "清理过期成绩: " + dateStr);
                    }
                } catch (java.text.ParseException ignored) {
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "清理旧成绩失败", e);
        }
    }

    private String getTodayDate() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new java.util.Date());
    }

    private void useFallbackWords() {
        wordPhraseList.clear();
        seenWordTexts.clear();
        scoredWordTexts.clear();
        wordPhraseList.add(new WordPhraseItem("Hello", "你好", false));
        wordPhraseList.add(new WordPhraseItem("Good morning", "早上好", false));
        wordPhraseList.add(new WordPhraseItem("Thank you", "谢谢", false));
        wordPhraseList.add(new WordPhraseItem("Nice to meet you", "很高兴认识你", false));
        adapter.notifyDataSetChanged();
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}