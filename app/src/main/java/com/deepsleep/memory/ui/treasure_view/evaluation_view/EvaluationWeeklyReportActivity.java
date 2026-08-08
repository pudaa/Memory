package com.deepsleep.memory.ui.treasure_view.evaluation_view;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.deepsleep.memory.R;
import com.deepsleep.memory.network.GetDataByThread;
import com.deepsleep.memory.settings.InnerSettingsManager;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EvaluationWeeklyReportActivity extends AppCompatActivity {

    private static final int MSG_SUCCESS = 1;
    private static final int MSG_FAILED = -1;
    private static final int MSG_DEEP_SUCCESS = 2;

    private int userId;
    private ProgressBar progressBar;
    private View contentLayout;
    private boolean weeklyLoaded = false, deepLoaded = false;

    private TextView tvWeekRange, tvWeekStudyDays, tvWeekNewWords, tvWeekReviews, tvWeekAvgScore, tvWeekStudyTime;
    private TextView tvNewWordsChange, tvReviewsChange, tvAvgScoreChange;
    private TextView tvAiWeeklySummary;
    private LinearLayout weakWordsLayout;
    private TextView tvNewlyMastered;
    // 新增：FSRS 趋势图 + 危急单词
    private LineChart weeklyTrendChart;
    private LinearLayout criticalWordsContainer;

    @SuppressLint("HandlerLeak")
    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            if (msg.what == MSG_SUCCESS || msg.what == MSG_DEEP_SUCCESS) {
                String result = (String) msg.obj;
                try {
                    JSONObject response = new JSONObject(result);
                    if ("200".equals(response.getString("code"))) {
                        JSONObject data = response.getJSONObject("data");
                        if (msg.what == MSG_SUCCESS) {
                            populateReport(data);
                            weeklyLoaded = true;
                        } else {
                            populateDeepData(data);
                            deepLoaded = true;
                        }
                        if (weeklyLoaded && deepLoaded) {
                            progressBar.setVisibility(View.GONE);
                            contentLayout.setVisibility(View.VISIBLE);
                            fadeIn(contentLayout);
                        }
                    } else {
                        showError("数据加载失败");
                    }
                } catch (JSONException e) {
                    Log.e("EvalWeekly", "JSON parse error", e);
                    showError("数据解析失败");
                }
            } else {
                showError("网络请求失败");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.evaluation_weekly_report_layout);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        progressBar = findViewById(R.id.progress_bar);
        contentLayout = findViewById(R.id.content_layout);

        tvWeekRange = findViewById(R.id.tv_week_range);
        tvWeekStudyDays = findViewById(R.id.tv_week_study_days);
        tvWeekNewWords = findViewById(R.id.tv_week_new_words);
        tvWeekReviews = findViewById(R.id.tv_week_reviews);
        tvWeekAvgScore = findViewById(R.id.tv_week_avg_score);
        tvWeekStudyTime = findViewById(R.id.tv_week_study_time);
        tvNewWordsChange = findViewById(R.id.tv_new_words_change);
        tvReviewsChange = findViewById(R.id.tv_reviews_change);
        tvAvgScoreChange = findViewById(R.id.tv_avg_score_change);
        tvAiWeeklySummary = findViewById(R.id.tv_ai_weekly_summary);
        weakWordsLayout = findViewById(R.id.weak_words_layout);
        tvNewlyMastered = findViewById(R.id.tv_newly_mastered);
        weeklyTrendChart = findViewById(R.id.weekly_trend_chart);
        criticalWordsContainer = findViewById(R.id.critical_words_container);

        loadData();
    }

    private void loadData() {
        userId = InnerSettingsManager.getInstance(this).getUserId();

        progressBar.setVisibility(View.VISIBLE);
        contentLayout.setVisibility(View.GONE);
        weeklyLoaded = false;
        deepLoaded = false;

        // 同时请求周报和深度分析
        GetDataByThread weeklyApi = new GetDataByThread("/evaluation/weeklyReport");
        weeklyApi.getEvaluationWeeklyReport(handler, MSG_SUCCESS, MSG_FAILED, String.valueOf(userId));

        GetDataByThread deepApi = new GetDataByThread("/evaluation/deepAnalysis");
        deepApi.getEvaluationDeepAnalysis(handler, MSG_DEEP_SUCCESS, MSG_FAILED, String.valueOf(userId));
    }

    @SuppressLint("SetTextI18n")
    private void populateReport(JSONObject data) throws JSONException {
        String weekStart = data.optString("weekStart", "");
        String weekEnd = data.optString("weekEnd", "");
        tvWeekRange.setText(weekStart + " ~ " + weekEnd);

        tvWeekStudyDays.setText(data.optInt("weekStudyDays", 0) + " 天");
        tvWeekNewWords.setText(data.optInt("weekNewWords", 0) + " 词");
        tvWeekReviews.setText(data.optInt("weekReviews", 0) + " 次");
        tvWeekAvgScore.setText(String.format(Locale.getDefault(), "%.1f", data.optDouble("weekAvgScore", 0)));
        tvWeekStudyTime.setText("约 " + data.optInt("weekTotalStudyTimeMinutes", 0) + " 分钟");

        int newChange = data.optInt("newWordsChange", 0);
        int reviewsChange = data.optInt("reviewsChange", 0);
        double scoreChange = data.optDouble("avgScoreChange", 0);

        tvNewWordsChange.setText(formatChange(newChange));
        tvNewWordsChange.setTextColor(newChange >= 0 ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336"));
        tvReviewsChange.setText(formatChange(reviewsChange));
        tvReviewsChange.setTextColor(reviewsChange >= 0 ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336"));
        tvAvgScoreChange.setText(formatChangeDouble(scoreChange));
        tvAvgScoreChange.setTextColor(scoreChange >= 0 ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336"));

        String aiSummary = data.optString("aiWeeklySummary", "");
        tvAiWeeklySummary.setText(aiSummary.isEmpty() ? "暂无AI总结" : aiSummary);

        // 薄弱单词
        weakWordsLayout.removeAllViews();
        JSONArray weakWords = data.optJSONArray("topWeakWords");
        if (weakWords != null && weakWords.length() > 0) {
            for (int i = 0; i < Math.min(weakWords.length(), 5); i++) {
                JSONObject w = weakWords.getJSONObject(i);
                View wordView = getLayoutInflater().inflate(R.layout.item_weak_word, weakWordsLayout, false);
                TextView tvWord = wordView.findViewById(R.id.tv_word);
                TextView tvDifficulty = wordView.findViewById(R.id.tv_difficulty);
                TextView tvLapses = wordView.findViewById(R.id.tv_lapses);

                tvWord.setText(w.optString("headWord", ""));
                tvDifficulty.setText("难度 " + String.format(Locale.getDefault(), "%.1f", w.optDouble("difficulty", 0)));
                tvLapses.setText("遗忘 " + w.optInt("lapses", 0) + " 次");

                weakWordsLayout.addView(wordView);
            }
        } else {
            TextView empty = new TextView(this);
            empty.setText("暂无薄弱单词");
            empty.setPadding(8, 8, 8, 8);
            weakWordsLayout.addView(empty);
        }

        // 成就已移除 - 当前版本未实现成就系统
        tvNewlyMastered.setText("本周新掌握 " + data.optInt("newlyMasteredWords", 0) + " 个单词");
    }

    @SuppressLint("SetTextI18n")
    private void populateDeepData(JSONObject data) throws JSONException {
        // FSRS 7日趋势图
        JSONObject trend = data.optJSONObject("recent7DaysTrend");
        if (trend != null && weeklyTrendChart != null) {
            JSONArray points = trend.optJSONArray("points");
            if (points != null && points.length() > 0) {
                List<Entry> dEntries = new ArrayList<>();
                List<Entry> rEntries = new ArrayList<>();
                List<String> labels = new ArrayList<>();

                for (int i = 0; i < points.length(); i++) {
                    JSONObject p = points.getJSONObject(i);
                    String date = p.optString("date", "");
                    labels.add(date.length() >= 5 ? date.substring(date.length() - 5) : date);
                    dEntries.add(new Entry(i, (float) p.optDouble("avgDifficulty", 0)));
                    rEntries.add(new Entry(i, (float) (p.optDouble("avgRetrievability", 0) * 100)));
                }

                LineDataSet dSet = new LineDataSet(dEntries, "难度");
                dSet.setColor(0xFFFF9800);
                dSet.setCircleColor(0xFFFF9800);
                dSet.setLineWidth(2f);
                dSet.setCircleRadius(3f);
                dSet.setDrawValues(false);

                LineDataSet rSet = new LineDataSet(rEntries, "提取率%");
                rSet.setColor(0xFF42A5F5);
                rSet.setCircleColor(0xFF42A5F5);
                rSet.setLineWidth(2f);
                rSet.setCircleRadius(3f);
                rSet.setDrawValues(false);

                weeklyTrendChart.setData(new LineData(dSet, rSet));
                weeklyTrendChart.getDescription().setEnabled(false);
                weeklyTrendChart.setTouchEnabled(false);
                weeklyTrendChart.setScaleEnabled(false);

                XAxis xAxis = weeklyTrendChart.getXAxis();
                xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
                xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
                xAxis.setGranularity(1f);
                xAxis.setTextSize(10f);
                xAxis.setDrawGridLines(false);
                weeklyTrendChart.getAxisLeft().setTextSize(10f);
                weeklyTrendChart.getAxisRight().setEnabled(false);

                Legend legend = weeklyTrendChart.getLegend();
                legend.setTextSize(10f);
                legend.setFormSize(8f);

                weeklyTrendChart.animateX(500);
                weeklyTrendChart.invalidate();
            }
        }

        // 危急单词
        criticalWordsContainer.removeAllViews();
        JSONArray criticalWords = data.optJSONArray("criticalWords");
        if (criticalWords != null && criticalWords.length() > 0) {
            for (int i = 0; i < Math.min(criticalWords.length(), 3); i++) {
                JSONObject w = criticalWords.getJSONObject(i);
                View v = getLayoutInflater().inflate(R.layout.item_critical_word, criticalWordsContainer, false);
                TextView tvWord = v.findViewById(R.id.tv_word);
                TextView tvIntervention = v.findViewById(R.id.tv_intervention);
                tvWord.setText(w.optString("headWord", ""));
                tvIntervention.setText(w.optString("intervention", "建议强化复习"));
                criticalWordsContainer.addView(v);
            }
        } else {
            TextView empty = new TextView(this);
            empty.setText("暂无危急单词，继续保持");
            empty.setPadding(8, 16, 8, 8);
            empty.setTextSize(14);
            empty.setTextColor(0xFF888888);
            criticalWordsContainer.addView(empty);
        }
    }

    private String formatChange(int change) {
        if (change > 0)
            return "↑" + change;
        if (change < 0)
            return "↓" + Math.abs(change);
        return "→0";
    }

    private String formatChangeDouble(double change) {
        if (change > 0)
            return "↑" + String.format(Locale.getDefault(), "%.1f", change);
        if (change < 0)
            return "↓" + String.format(Locale.getDefault(), "%.1f", Math.abs(change));
        return "→0.0";
    }

    private void showError(String msg) {
        progressBar.setVisibility(View.GONE);
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private static void fadeIn(View view) {
        AlphaAnimation anim = new AlphaAnimation(0f, 1f);
        anim.setDuration(400);
        anim.setInterpolator(new DecelerateInterpolator());
        view.startAnimation(anim);
    }
}
