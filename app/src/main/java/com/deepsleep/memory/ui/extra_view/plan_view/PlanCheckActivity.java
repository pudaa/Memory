package com.deepsleep.memory.ui.extra_view.plan_view;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import com.deepsleep.memory.R;
import com.deepsleep.memory.network.ApiBridge;
import com.deepsleep.memory.network.MemoryApiClient;
import com.deepsleep.memory.settings.InnerSettingsManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.deepsleep.memory.handle_utils.lexicon.LexiconResourceMap.loadBooksFromJson;

public class PlanCheckActivity extends AppCompatActivity {
    private List<JSONObject> allBooks;
    ImageButton btnBack;
    TextView onLearningBook, progressMain, needNew, needReview;
    TextView tvDailyWords, tvAvgStability, tvAvgDifficulty, tvAvgRetrievability;
    TextView tvMasteredWords, tvNote;
    ProgressBar planProgressBar;
    LinearLayout onPlanShow, toExchangePlan;
    int userId;
    static final int MSG_SUCCESS = 1;
    static final int MSG_FAILED = -1;

    // 双接口并行追踪
    private boolean statsLoaded = false;
    private boolean previewLoaded = false;
    private JSONObject statsData = null;
    private JSONArray upcomingData = null;
    private String noteText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.plan_check_layout);
        initView();
        allBooks = loadBooksFromJson(this);
        btnBack.setOnClickListener(v -> finish());
        toExchangePlan.setOnClickListener(v -> {
            Intent intent = new Intent(PlanCheckActivity.this, PlanListActivity.class);
            startActivity(intent);
        });

        // 并行调用两个接口
        loadPlanData();
    }

    private void initView() {
        userId = InnerSettingsManager.getInstance(this).getUserId();
        btnBack = findViewById(R.id.btn_back);
        planProgressBar = findViewById(R.id.plan_progress);
        onLearningBook = findViewById(R.id.on_learning_book);
        progressMain = findViewById(R.id.progress_main);
        needNew = findViewById(R.id.need_new);
        needReview = findViewById(R.id.need_review);
        tvDailyWords = findViewById(R.id.tv_daily_words);
        tvAvgStability = findViewById(R.id.tv_avg_stability);
        tvAvgDifficulty = findViewById(R.id.tv_avg_difficulty);
        tvAvgRetrievability = findViewById(R.id.tv_avg_retrievability);
        tvMasteredWords = findViewById(R.id.tv_mastered_words);
        tvNote = findViewById(R.id.tv_note);
        onPlanShow = findViewById(R.id.on_plan_show);
        toExchangePlan = findViewById(R.id.to_exchange_plan);
    }

    private void loadPlanData() {
        // 接口1：统计卡片
        ApiBridge.enqueue(MemoryApiClient.learning().getLearningPlanDetails(String.valueOf(userId)), new StatsHandler(),
                MSG_SUCCESS, MSG_FAILED, null);

        // 接口2：预测列表
        ApiBridge.enqueue(MemoryApiClient.learning().getSchedulePreview(String.valueOf(userId)), new PreviewHandler(),
                MSG_SUCCESS, MSG_FAILED, "SchedulePreview");
    }

    private String getLexiconName(String lexiconId) {
        for (JSONObject book : allBooks) {
            try {
                if (book.getString("id").equals(lexiconId)) {
                    return book.getString("title");
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return "未知词书";
    }

    /**
     * 两个接口都返回后统一渲染
     */
    private void tryRenderUI() {
        if (!statsLoaded || !previewLoaded)
            return;

        try {
            JSONObject progressInfo = statsData.getJSONObject("progressInfo");

            int totalWords = progressInfo.getInt("totalWords");
            int learnedWords = progressInfo.getInt("learnedWords");
            int masteredWords = progressInfo.optInt("masteredWords", 0);
            String lexiconId = progressInfo.getString("lexiconId");
            int dailyNewWords = progressInfo.optInt("dailyNewWords", 10);
            double avgStability = progressInfo.optDouble("avgStability", 0);
            double avgDifficulty = progressInfo.optDouble("avgDifficulty", 5.0);
            double avgRetrievability = progressInfo.optDouble("avgRetrievability", 1.0);

            JSONObject todayPlan = progressInfo.getJSONObject("todayPlan");
            int dueReviewCount = todayPlan.optInt("dueReviewCount", 0);
            int remainingNewWords = todayPlan.optInt("remainingNewWords", 0);

            // ---- 统计区 ----
            onLearningBook.setText(getLexiconName(lexiconId));
            progressMain.setText(String.format(Locale.getDefault(), "已学 %d / 总 %d 词", learnedWords, totalWords));
            tvDailyWords.setText(String.format(Locale.getDefault(), "每日新词 %d", dailyNewWords));
            needReview.setText(String.format(Locale.getDefault(), "待复习 %d 词", dueReviewCount));
            needNew.setText(String.format(Locale.getDefault(), "可新学 %d 词", remainingNewWords));
            tvAvgStability.setText(String.format(Locale.getDefault(), "%.1f天", avgStability));
            tvAvgDifficulty.setText(String.format(Locale.getDefault(), "%.1f/10", avgDifficulty));
            tvAvgRetrievability.setText(String.format(Locale.getDefault(), "%.0f%%", avgRetrievability * 100));
            tvMasteredWords.setText(String.format(Locale.getDefault(), "已掌握 %d 词", masteredWords));

            if (totalWords > 0) {
                planProgressBar.setProgress((learnedWords * 100) / totalWords);
            }

            // ---- 提示（不显示） ----
            // tvNote.setText(noteText);

            // ---- 预测列表 ----
            buildPredictionList(upcomingData);

        } catch (JSONException e) {
            Log.e("PlanCheckActivity", "Render error", e);
            planProgressBar.setProgress(0);
        }
    }

    /**
     * 构建预测复习列表（替代旧的 generatePlanUi）
     */
    private void buildPredictionList(JSONArray upcoming) {
        if (upcoming == null || upcoming.length() == 0) {
            TextView emptyView = new TextView(this);
            emptyView.setText("暂无预测数据，完成今日学习后可见");
            emptyView.setTextSize(14);
            emptyView.setTextColor(ContextCompat.getColor(this, R.color.middle_gray));
            emptyView.setGravity(Gravity.CENTER);
            emptyView.setPadding(0, 40, 0, 40);
            onPlanShow.addView(emptyView);
            return;
        }

        try {
            for (int i = 0; i < upcoming.length(); i++) {
                JSONObject dayObj = upcoming.getJSONObject(i);
                String date = dayObj.getString("date");
                int estimatedCount = dayObj.getInt("estimatedReviewCount");
                JSONArray previewWords = dayObj.optJSONArray("previewWords");

                // 日期卡片容器
                CardView card = new CardView(this);
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                cardParams.setMargins(0, 6, 0, 6);
                card.setLayoutParams(cardParams);
                card.setRadius(12);
                card.setCardElevation(1);
                card.setContentPadding(50, 30, 16, 30);
                card.setCardBackgroundColor(i == 0 ? ContextCompat.getColor(this, R.color.item_background_stress)
                        : ContextCompat.getColor(this, R.color.card_background));

                LinearLayout inner = new LinearLayout(this);
                inner.setOrientation(LinearLayout.VERTICAL);

                // 日期 + 预计词数
                LinearLayout headerRow = new LinearLayout(this);
                headerRow.setOrientation(LinearLayout.HORIZONTAL);
                headerRow.setGravity(Gravity.CENTER_VERTICAL);

                TextView tvDate = new TextView(this);
                tvDate.setText(formatDate(date));
                tvDate.setTextSize(16);
                tvDate.setTypeface(null, Typeface.BOLD);
                tvDate.setTextColor(ContextCompat.getColor(this, R.color.theme_text_primary));

                TextView tvCount = new TextView(this);
                tvCount.setText(String.format(Locale.getDefault(), "预计 %d 词", estimatedCount));
                tvCount.setTextSize(14);
                tvCount.setTextColor(ContextCompat.getColor(this, R.color.theme_stress));
                LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                countParams.setMarginStart(16);
                tvCount.setLayoutParams(countParams);

                headerRow.addView(tvDate);
                headerRow.addView(tvCount);
                inner.addView(headerRow);

                // 预览词列表
                if (previewWords != null && previewWords.length() > 0) {
                    int showCount = Math.min(previewWords.length(), 5);
                    for (int j = 0; j < showCount; j++) {
                        JSONObject pw = previewWords.getJSONObject(j);
                        String headWord = pw.getString("headWord");
                        double difficulty = pw.optDouble("difficulty", 0);
                        double stability = pw.optDouble("stability", 0);

                        TextView tvWord = new TextView(this);
                        tvWord.setText(String.format(Locale.getDefault(), "%s  ·  难度%.1f  ·  稳定%.1fd", headWord,
                                difficulty, stability));
                        tvWord.setTextSize(14);
                        tvWord.setTextColor(ContextCompat.getColor(this, R.color.dark_gray));
                        tvWord.setPadding(0, 6, 0, 4);
                        inner.addView(tvWord);
                    }
                    if (previewWords.length() > showCount) {
                        TextView tvMore = new TextView(this);
                        tvMore.setText(
                                String.format(Locale.getDefault(), "... 还有 %d 词", previewWords.length() - showCount));
                        tvMore.setTextSize(12);
                        tvMore.setTextColor(ContextCompat.getColor(this, R.color.middle_gray));
                        inner.addView(tvMore);
                    }
                }

                card.addView(inner);
                onPlanShow.addView(card);
            }
        } catch (JSONException e) {
            Log.e("PlanCheckActivity", "buildPredictionList error", e);
        }
    }

    /**
     * 格式化日期：2026-05-21 → 5月21日
     */
    private String formatDate(String dateStr) {
        try {
            String[] parts = dateStr.split("-");
            if (parts.length == 3) {
                return String.format(Locale.getDefault(), "%s月%s日", parts[1], parts[2]);
            }
        } catch (Exception ignored) {
        }
        return dateStr;
    }

    // ==================== Handlers ====================

    @SuppressLint("HandlerLeak")
    private class StatsHandler extends Handler {
        StatsHandler() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            if (msg.what == MSG_SUCCESS) {
                try {
                    statsData = new JSONObject((String) msg.obj);
                    Log.d("ProgressInfo", statsData.toString(4));
                    statsLoaded = true;
                    tryRenderUI();
                } catch (JSONException e) {
                    Log.e("PlanCheckActivity", "Stats parse error", e);
                }
            } else {
                Toast.makeText(PlanCheckActivity.this, "加载统计失败", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @SuppressLint("HandlerLeak")
    private class PreviewHandler extends Handler {
        PreviewHandler() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            if (msg.what == MSG_SUCCESS) {
                try {
                    JSONObject resp = new JSONObject((String) msg.obj);
                    if ("200".equals(resp.getString("code"))) {
                        upcomingData = resp.getJSONArray("upcoming");
                        noteText = resp.optString("note", "");
                    }
                    previewLoaded = true;
                    tryRenderUI();
                } catch (JSONException e) {
                    Log.e("PlanCheckActivity", "Preview parse error", e);
                }
            } else {
                Toast.makeText(PlanCheckActivity.this, "加载预测失败", Toast.LENGTH_SHORT).show();
            }
        }
    }
}