package com.deepsleep.memory.ui.treasure_view.evaluation_view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.DecelerateInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.deepsleep.memory.R;
import com.deepsleep.memory.network.GetDataByThread;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.PercentFormatter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EvaluationDashboardActivity extends AppCompatActivity {

    private static final String PREF_NAME = "UserPrefs";
    private static final String KEY_USER_ID = "userId";
    private static final int MSG_SUCCESS = 1;
    private static final int MSG_FAILED = -1;

    private int userId;
    private ProgressBar progressBar;
    private View contentLayout;

    // 概览卡片（包含 tv_value + tv_label 的 item_eval_card）
    private View cardStudyDays, cardStreak, cardWords;
    private View cardMastery, cardTodayDone, cardTodayDue;
    // FSRS 指标
    private TextView tvAvgRetrievability, tvAvgStability, tvAvgDifficulty;
    private TextView tvTotalReviews, tvAvgScore, tvWeakWordCount;
    // 图表
    private PieChart masteryPieChart;
    private LineChart recent7DaysChart;
    // 按钮
    private View btnTrend, btnWeekly, btnAiSuggestion, btnDeepAnalysis;

    @SuppressLint("HandlerLeak")
    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            if (msg.what == MSG_SUCCESS) {
                String result = (String) msg.obj;
                try {
                    JSONObject response = new JSONObject(result);
                    if ("200".equals(response.getString("code"))) {
                        JSONObject data = response.getJSONObject("data");
                        populateDashboard(data);
                        progressBar.setVisibility(View.GONE);
                        contentLayout.setVisibility(View.VISIBLE);
                        fadeIn(contentLayout);
                    } else {
                        showError("数据加载失败");
                    }
                } catch (JSONException e) {
                    Log.e("EvalDashboard", "JSON parse error", e);
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
        setContentView(R.layout.evaluation_dashboard_layout);

        // 返回按钮
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        progressBar = findViewById(R.id.progress_bar);
        contentLayout = findViewById(R.id.content_layout);

        // 概览卡片
        cardStudyDays = findViewById(R.id.card_days);
        cardStreak = findViewById(R.id.card_streak);
        cardWords = findViewById(R.id.card_words);
        cardMastery = findViewById(R.id.card_mastery);
        cardTodayDone = findViewById(R.id.card_today_done);
        cardTodayDue = findViewById(R.id.card_today_due);

        // FSRS 指标
        tvAvgRetrievability = findViewById(R.id.tv_avg_retrievability);
        tvAvgStability = findViewById(R.id.tv_avg_stability);
        tvAvgDifficulty = findViewById(R.id.tv_avg_difficulty);
        tvTotalReviews = findViewById(R.id.tv_total_reviews);
        tvAvgScore = findViewById(R.id.tv_avg_score);
        tvWeakWordCount = findViewById(R.id.tv_weak_word_count);

        // 图表
        masteryPieChart = findViewById(R.id.mastery_pie_chart);
        recent7DaysChart = findViewById(R.id.recent_7days_chart);

        // 导航按钮
        btnTrend = findViewById(R.id.btn_trend);
        btnWeekly = findViewById(R.id.btn_weekly);
        btnAiSuggestion = findViewById(R.id.btn_ai_suggestion);
        btnDeepAnalysis = findViewById(R.id.btn_deep_analysis);

        btnTrend.setOnClickListener(v -> startActivity(new Intent(this, EvaluationTrendActivity.class)));
        btnWeekly.setOnClickListener(v -> startActivity(new Intent(this, EvaluationWeeklyReportActivity.class)));
        btnAiSuggestion.setOnClickListener(v -> startActivity(new Intent(this, EvaluationAiSuggestionActivity.class)));
        if (btnDeepAnalysis != null)
            btnDeepAnalysis.setOnClickListener(v -> startActivity(new Intent(this, EvaluationDeepAnalysisActivity.class)));

        loadData();
    }

    private void loadData() {
        SharedPreferences sp = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        userId = sp.getInt(KEY_USER_ID, 0);

        progressBar.setVisibility(View.VISIBLE);
        contentLayout.setVisibility(View.GONE);

        GetDataByThread api = new GetDataByThread("/evaluation/dashboard");
        api.getEvaluationDashboard(handler, MSG_SUCCESS, MSG_FAILED, String.valueOf(userId));
    }

    @SuppressLint("SetTextI18n")
    private void populateDashboard(JSONObject data) throws JSONException {
        // 概览卡片：通过 card view 找到内部的 tv_value 和 tv_label
        populateCard(cardStudyDays, String.valueOf(data.optInt("totalStudyDays", 0)), "已学天数");
        populateCard(cardStreak, String.valueOf(data.optInt("consecutiveDays", 0)), "连续天数");
        populateCard(cardWords, data.optInt("learnedWords", 0) + "/" + data.optInt("totalWords", 0), "已学单词");
        populateCard(cardMastery, String.format(Locale.getDefault(), "%.0f%%", data.optDouble("masteryRate", 0) * 100),
                "掌握率");
        populateCard(cardTodayDone, String.valueOf(data.optInt("todayStudied", 0)), "今日已学");
        populateCard(cardTodayDue, String.valueOf(data.optInt("todayDueCount", 0)), "待复习");

        // FSRS 指标
        tvAvgRetrievability
                .setText(String.format(Locale.getDefault(), "%.0f%%", data.optDouble("avgRetrievability", 0) * 100));
        tvAvgStability.setText(String.format(Locale.getDefault(), "%.1f天", data.optDouble("avgStability", 0)));
        tvAvgDifficulty.setText(String.format(Locale.getDefault(), "%.1f", data.optDouble("avgDifficulty", 0)));
        tvTotalReviews.setText(String.valueOf(data.optInt("totalReviews", 0)));
        tvAvgScore.setText(String.format(Locale.getDefault(), "%.1f/4.0", data.optDouble("avgScore", 0)));
        tvWeakWordCount.setText(String.valueOf(data.optInt("weakWordCount", 0)));

        // 掌握度分布
        populateMasteryDistribution(data);

        // 近7日趋势
        populateRecent7Days(data.optJSONArray("recent7Days"));
    }

    @SuppressLint("SetTextI18n")
    private void populateMasteryDistribution(JSONObject data) throws JSONException {
        // 兼容两种格式：JSONObject { buckets: [...] } 或直接的 JSONArray
        JSONObject distObj = data.optJSONObject("masteryDistribution");
        JSONArray buckets = null;

        if (distObj != null) {
            buckets = distObj.optJSONArray("buckets");
        }
        if (buckets == null)
            buckets = data.optJSONArray("masteryDistribution");
        if (buckets == null || buckets.length() == 0) {
            masteryPieChart.setNoDataText("暂无数据");
            masteryPieChart.invalidate();
            return;
        }

        List<PieEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        for (int i = 0; i < buckets.length(); i++) {
            JSONObject item = buckets.getJSONObject(i);
            int count = item.optInt("count", 0);
            String label = item.optString("label", "");
            String colorStr = item.optString("color", "#888888");

            if (count > 0) {
                entries.add(new PieEntry(count, label));
                try {
                    colors.add(Color.parseColor(colorStr));
                } catch (Exception e) {
                    colors.add(Color.GRAY);
                }
            }
        }

        if (entries.isEmpty()) {
            masteryPieChart.setNoDataText("暂无数据");
            masteryPieChart.invalidate();
            return;
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setValueTextSize(12f);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setSliceSpace(3f);
        dataSet.setValueFormatter(new PercentFormatter(masteryPieChart));

        PieData pieData = new PieData(dataSet);

        masteryPieChart.setData(pieData);
        masteryPieChart.setUsePercentValues(true);
        masteryPieChart.getDescription().setEnabled(false);
        masteryPieChart.setDrawHoleEnabled(true);
        masteryPieChart.setHoleRadius(45f);
        masteryPieChart.setTransparentCircleRadius(50f);
        masteryPieChart.setHoleColor(Color.WHITE);
        masteryPieChart.setCenterText("掌握度\n分布");
        masteryPieChart.setCenterTextSize(14f);
        masteryPieChart.setCenterTextColor(Color.parseColor("#666666"));
        masteryPieChart.setRotationEnabled(false);
        masteryPieChart.setDrawEntryLabels(false);

        Legend legend = masteryPieChart.getLegend();
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setTextSize(11f);
        legend.setXEntrySpace(12f);
        legend.setFormSize(10f);

        masteryPieChart.animateY(800);
        masteryPieChart.invalidate();
    }

    private void populateRecent7Days(JSONArray days) throws JSONException {
        if (days == null || days.length() == 0) {
            recent7DaysChart.setNoDataText("暂无近7日数据");
            recent7DaysChart.invalidate();
            return;
        }

        List<Entry> newEntries = new ArrayList<>();
        List<Entry> reviewEntries = new ArrayList<>();
        List<Entry> totalEntries = new ArrayList<>();
        List<String> xLabels = new ArrayList<>();

        for (int i = 0; i < days.length(); i++) {
            JSONObject day = days.getJSONObject(i);
            String date = day.optString("date", "");
            String shortDate = date.length() >= 5 ? date.substring(date.length() - 5) : date;
            xLabels.add(shortDate);

            newEntries.add(new Entry(i, day.optInt("newWords", 0)));
            reviewEntries.add(new Entry(i, day.optInt("reviewWords", 0)));
            totalEntries.add(new Entry(i, day.optInt("totalWords", 0)));
        }

        LineDataSet newSet = new LineDataSet(newEntries, "新词");
        newSet.setColor(Color.parseColor("#42A5F5"));
        newSet.setCircleColor(Color.parseColor("#42A5F5"));
        newSet.setLineWidth(2f);
        newSet.setCircleRadius(4f);
        newSet.setValueTextSize(10f);

        LineDataSet reviewSet = new LineDataSet(reviewEntries, "复习");
        reviewSet.setColor(Color.parseColor("#FFB74D"));
        reviewSet.setCircleColor(Color.parseColor("#FFB74D"));
        reviewSet.setLineWidth(2f);
        reviewSet.setCircleRadius(4f);
        reviewSet.setValueTextSize(10f);

        LineDataSet totalSet = new LineDataSet(totalEntries, "总量");
        totalSet.setColor(Color.parseColor("#66BB6A"));
        totalSet.setCircleColor(Color.parseColor("#66BB6A"));
        totalSet.setLineWidth(2.5f);
        totalSet.setCircleRadius(4f);
        totalSet.setValueTextSize(10f);
        totalSet.enableDashedLine(10f, 5f, 0f);

        LineData lineData = new LineData(newSet, reviewSet, totalSet);

        recent7DaysChart.setData(lineData);
        recent7DaysChart.getDescription().setEnabled(false);
        recent7DaysChart.setTouchEnabled(true);
        recent7DaysChart.setDragEnabled(true);
        recent7DaysChart.setScaleEnabled(false);
        recent7DaysChart.setPinchZoom(false);
        recent7DaysChart.setDrawGridBackground(false);

        XAxis xAxis = recent7DaysChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(xLabels));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setTextSize(10f);
        xAxis.setDrawGridLines(false);

        recent7DaysChart.getAxisLeft().setTextSize(10f);
        recent7DaysChart.getAxisLeft().setDrawGridLines(true);
        recent7DaysChart.getAxisLeft().setGridLineWidth(0.5f);
        recent7DaysChart.getAxisRight().setEnabled(false);

        Legend legend = recent7DaysChart.getLegend();
        legend.setTextSize(11f);
        legend.setFormSize(10f);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);

        recent7DaysChart.animateX(800);
        recent7DaysChart.invalidate();
    }

    private void populateCard(View cardView, String value, String label) {
        TextView tvValue = cardView.findViewById(R.id.tv_value);
        TextView tvLabel = cardView.findViewById(R.id.tv_label);
        if (tvValue != null)
            tvValue.setText(value);
        if (tvLabel != null)
            tvLabel.setText(label);
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
