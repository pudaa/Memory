package com.deepsleep.memory.ui.treasure_view.pronunciation_view;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.deepsleep.memory.R;
import com.deepsleep.memory.handle_utils.lexicon.LexiconResourceMap;
import com.deepsleep.memory.handle_utils.lexicon.WordEntry;
import com.deepsleep.memory.network.GetDataByThread;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PronunciationMinuteFollowActivity extends AppCompatActivity
        implements WordPhraseListAdapter.OnScoreResultListener {
    private static final String TAG = "PronunciationMinute";
    private static final String PREF_NAME = "UserPrefs";
    private static final String KEY_USER_ID = "userId";
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
    private LinearLayout bsTextCompare, bsWordScores;

    private ImageButton backButton;

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
        if (topicName == null) topicName = "每日一分钟";

        wordsListView = findViewById(R.id.words_list_view);
        tvTitle = findViewById(R.id.tv_title);
        tvTitle.setText(topicName);

        bottomSheetBehavior = BottomSheetBehavior.from(findViewById(R.id.bottom_sheet));
        bsScore = findViewById(R.id.bs_score);
        bsLevel = findViewById(R.id.bs_level);
        bsAsrText = findViewById(R.id.bs_asr_text);
        bsPhoneme = findViewById(R.id.bs_phoneme);
        bsFeedback = findViewById(R.id.bs_feedback);
        bsTextCompare = findViewById(R.id.bs_text_compare);
        bsWordScores = findViewById(R.id.bs_word_scores);

        backButton = findViewById(R.id.btn_back);
        backButton.setOnClickListener(v -> finish());

        SharedPreferences sp = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        userId = sp.getInt(KEY_USER_ID, 0);

        wordPhraseList = new ArrayList<>();
        initListView();
        initBottomSheet();
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

        findViewById(R.id.bs_close_btn).setOnClickListener(v -> hideBottomSheet());
    }

    // ==================== 评分回调（来自 Adapter） ====================

    @Override
    public void onScoreResult(String word, double overallScore, String level, String feedback,
                              String asrTranscript, String referenceText, JSONArray words) {
        // 大分数
        if (overallScore >= 0) {
            bsScore.setText(String.format(Locale.getDefault(), "%.0f", overallScore));
            bsScore.setTextColor(ContextCompat.getColor(this, R.color.theme_primary));
        } else {
            bsScore.setText("?");
            bsScore.setTextColor(ContextCompat.getColor(this, R.color.theme_error));
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

        // 逐词评分
        bsWordScores.removeAllViews();
        bsWordScores.setVisibility(View.GONE);
        if (words != null && words.length() > 0) {
            bsWordScores.setVisibility(View.VISIBLE);
            for (int i = 0; i < words.length(); i++) {
                try {
                    JSONObject w = words.getJSONObject(i);
                    String wrd = w.getString("word");
                    double wScore = w.optDouble("score", -1);
                    String status = w.optString("status", "");

                    LinearLayout row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setPadding(0, 4, 0, 4);

                    TextView tvWord = new TextView(this);
                    tvWord.setText(wrd);
                    tvWord.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                    tvWord.setTextColor(ContextCompat.getColor(this, R.color.theme_text_primary));
                    tvWord.setLayoutParams(new LinearLayout.LayoutParams(0,
                            LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                    row.addView(tvWord);

                    TextView tvScore = new TextView(this);
                    tvScore.setGravity(Gravity.END);
                    if (wScore >= 0) {
                        tvScore.setText(String.format(Locale.getDefault(), "%.0f", wScore));
                        tvScore.setTextColor(ContextCompat.getColor(this,
                                "missing".equals(status) ? R.color.theme_error :
                                "mispronounced".equals(status) ? R.color.theme_stress :
                                R.color.teal_200));
                    } else {
                        tvScore.setText("?");
                        tvScore.setTextColor(ContextCompat.getColor(this, R.color.theme_text_secondary));
                    }
                    tvScore.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                    tvScore.setTypeface(null, Typeface.BOLD);
                    tvScore.setLayoutParams(new LinearLayout.LayoutParams(60,
                            LinearLayout.LayoutParams.WRAP_CONTENT));
                    row.addView(tvScore);

                    bsWordScores.addView(row);
                } catch (JSONException ignored) {}
            }
        }

        // 弹出 BottomSheet
        showBottomSheet();
    }

    private String toLevelText(String level) {
        switch (level) {
            case "excellent": return "🏆 优秀 Excellent";
            case "good":      return "👍 良好 Good";
            case "fair":      return "📝 一般 Fair";
            case "poor":      return "🔇 较弱 Poor";
            case "very_poor": return "⛔ 很弱 Very Poor";
            default:          return level;
        }
    }

    // ==================== BottomSheet 控制 ====================

    public void showBottomSheet() {
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    public void hideBottomSheet() {
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
    }

    // ==================== 单词列表加载 ====================

    private void fetchWordList() {
        // 加载本地词书（供释义查找）
        currentLexiconId = WORD_BOOK_TO_LEXICON.get(wordBookId);
        if (currentLexiconId != null) {
            LexiconResourceMap.loadLexicon(this, currentLexiconId);
        }

        GetDataByThread api = new GetDataByThread("/pronunciation/words");
        api.getPronunciationWords(wordHandler, MSG_WORDS_SUCCESS, MSG_WORDS_FAIL,
                String.valueOf(userId), wordBookId, phraseCount, sentenceCount);
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
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject item = data.getJSONObject(i);
                            String word = item.getString("word");
                            String wordId = item.optString("wordId", "");

                            // 从本地词书查找释义
                            String meaning = lookupMeaning(wordId);

                            wordPhraseList.add(new WordPhraseItem(word, meaning, false));
                        }
                        adapter.notifyDataSetChanged();
                        Log.i(TAG, "加载了 " + wordPhraseList.size() + " 个单词");
                    } else {
                        Toast.makeText(PronunciationMinuteFollowActivity.this,
                                root.optString("message", "获取单词列表失败"), Toast.LENGTH_SHORT).show();
                        useFallbackWords();
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "解析单词列表失败", e);
                    useFallbackWords();
                }
            } else {
                Toast.makeText(PronunciationMinuteFollowActivity.this,
                        "获取单词列表失败，使用测试数据", Toast.LENGTH_SHORT).show();
                useFallbackWords();
            }
        }
    };

    /** 用 wordId 从已加载的本地词书中查找中文释义 */
    private String lookupMeaning(String wordId) {
        if (currentLexiconId == null || wordId.isEmpty()) return "";
        try {
            int rank = Integer.parseInt(wordId);
            WordEntry entry = LexiconResourceMap.getWordByRank(currentLexiconId, rank);
            if (entry != null) {
                return entry.getChineseTranslation();
            }
        } catch (NumberFormatException ignored) {}
        return "";
    }

    private void useFallbackWords() {
        wordPhraseList.clear();
        wordPhraseList.add(new WordPhraseItem("Hello", "你好", false));
        wordPhraseList.add(new WordPhraseItem("Good morning", "早上好", false));
        wordPhraseList.add(new WordPhraseItem("Thank you", "谢谢", false));
        wordPhraseList.add(new WordPhraseItem("Nice to meet you", "很高兴认识你", false));
        adapter.notifyDataSetChanged();
    }
}