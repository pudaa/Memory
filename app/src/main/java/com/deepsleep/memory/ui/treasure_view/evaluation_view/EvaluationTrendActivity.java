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
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EvaluationTrendActivity extends AppCompatActivity {

    private static final int MSG_SUCCESS = 1;
    private static final int MSG_FAILED = -1;

    private int userId;
    private int currentDays = 7;
    private ProgressBar progressBar;
    private View contentLayout;

    private MaterialButton btn7Days, btn14Days, btn30Days;
    private TextView tvDailyAvgWords, tvDailyAvgNew, tvDailyAvgReview;
    private TextView tvOverallAvgScore, tvAvgResponseTime, tvForgettingRate;
    private LineChart trendLineChart;
    private LineChart scoreLineChart;

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
                        populateTrend(data);
                        progressBar.setVisibility(View.GONE);
                        contentLayout.setVisibility(View.VISIBLE);
                        fadeIn(contentLayout);
                    } else {
                        showError("数据加载失败");
                    }
                } catch (JSONException e) {
                    Log.e("EvalTrend", "JSON parse error", e);
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
        setContentView(R.layout.evaluation_trend_layout);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        progressBar = findViewById(R.id.progress_bar);
        contentLayout = findViewById(R.id.content_layout);

        btn7Days = findViewById(R.id.btn_7days);
        btn14Days = findViewById(R.id.btn_14days);
        btn30Days = findViewById(R.id.btn_30days);

        tvDailyAvgWords = findViewById(R.id.tv_daily_avg_words);
        tvDailyAvgNew = findViewById(R.id.tv_daily_avg_new);
        tvDailyAvgReview = findViewById(R.id.tv_daily_avg_review);
        tvOverallAvgScore = findViewById(R.id.tv_overall_avg_score);
        tvAvgResponseTime = findViewById(R.id.tv_avg_response_time);
        tvForgettingRate = findViewById(R.id.tv_forgetting_rate);
        trendLineChart = findViewById(R.id.trend_line_chart);
        scoreLineChart = findViewById(R.id.score_line_chart);

        btn7Days.setOnClickListener(v -> switchDays(7));
        btn14Days.setOnClickListener(v -> switchDays(14));
        btn30Days.setOnClickListener(v -> switchDays(30));

        userId = InnerSettingsManager.getInstance(this).getUserId();
        switchDays(7);
    }

    private void switchDays(int days) {
        currentDays = days;
        updateSegmentButtons();
        loadData();
    }

    private void updateSegmentButtons() {
        btn7Days.setChecked(currentDays == 7);
        btn14Days.setChecked(currentDays == 14);
        btn30Days.setChecked(currentDays == 30);
    }

    private void loadData() {
        progressBar.setVisibility(View.VISIBLE);
        contentLayout.setVisibility(View.GONE);

        GetDataByThread api = new GetDataByThread("/evaluation/trend");
        api.getEvaluationTrend(handler, MSG_SUCCESS, MSG_FAILED, String.valueOf(userId), currentDays);
    }

    @SuppressLint("SetTextI18n")
    private void populateTrend(JSONObject data) throws JSONException {
        JSONObject summary = data.optJSONObject("summary");
        if (summary != null) {
            tvDailyAvgWords.setText(String.format(Locale.getDefault(), "%.1f词", summary.optDouble("dailyAvgWords", 0)));
            tvDailyAvgNew
                    .setText(String.format(Locale.getDefault(), "%.1f词", summary.optDouble("dailyAvgNewWords", 0)));
            tvDailyAvgReview
                    .setText(String.format(Locale.getDefault(), "%.1f词", summary.optDouble("dailyAvgReviewWords", 0)));
            tvOverallAvgScore
                    .setText(String.format(Locale.getDefault(), "%.1f", summary.optDouble("overallAvgScore", 0)));
            tvAvgResponseTime
                    .setText(String.format(Locale.getDefault(), "%.0fms", summary.optDouble("avgResponseTimeMs", 0)));
            tvForgettingRate.setText(
                    String.format(Locale.getDefault(), "%.0f%%", summary.optDouble("weeklyForgettingRate", 0) * 100));
        }

        JSONArray points = data.optJSONArray("points");
        populateChart(points);
    }

    @SuppressLint("SetTextI18n")
    private void populateChart(JSONArray points) throws JSONException {
        if (points == null || points.length() == 0) {
            trendLineChart.setNoDataText("暂无足够数据");
            trendLineChart.invalidate();
            scoreLineChart.setNoDataText("暂无足够数据");
            scoreLineChart.invalidate();
            return;
        }

        int len = points.length();
        List<Entry> newEntries = new ArrayList<>();
        List<Entry> reviewEntries = new ArrayList<>();
        List<Entry> totalEntries = new ArrayList<>();
        List<Entry> scoreEntries = new ArrayList<>();
        List<String> xLabels = new ArrayList<>();

        for (int i = 0; i < len; i++) {
            JSONObject p = points.getJSONObject(i);
            String date = p.optString("date", "");
            xLabels.add(date.length() >= 5 ? date.substring(date.length() - 5) : date);

            newEntries.add(new Entry(i, p.optInt("newWords", 0)));
            reviewEntries.add(new Entry(i, p.optInt("reviewWords", 0)));
            totalEntries.add(new Entry(i, p.optInt("totalWords", 0)));
            scoreEntries.add(new Entry(i, (float) p.optDouble("avgScore", 0)));
        }

        // === 学习量折线图 ===
        LineDataSet newSet = new LineDataSet(newEntries, "新词");
        newSet.setColor(Color.parseColor("#42A5F5"));
        newSet.setCircleColor(Color.parseColor("#42A5F5"));
        newSet.setLineWidth(2f);
        newSet.setCircleRadius(3f);
        newSet.setValueTextSize(9f);

        LineDataSet reviewSet = new LineDataSet(reviewEntries, "复习");
        reviewSet.setColor(Color.parseColor("#FFB74D"));
        reviewSet.setCircleColor(Color.parseColor("#FFB74D"));
        reviewSet.setLineWidth(2f);
        reviewSet.setCircleRadius(3f);
        reviewSet.setValueTextSize(9f);

        LineDataSet totalSet = new LineDataSet(totalEntries, "总量");
        totalSet.setColor(Color.parseColor("#66BB6A"));
        totalSet.setCircleColor(Color.parseColor("#66BB6A"));
        totalSet.setLineWidth(2.5f);
        totalSet.setCircleRadius(3f);
        totalSet.setValueTextSize(9f);
        totalSet.enableDashedLine(10f, 5f, 0f);

        configureLineChart(trendLineChart, new LineData(newSet, reviewSet, totalSet), xLabels);

        // === 评分折线图 ===
        LineDataSet scoreSet = new LineDataSet(scoreEntries, "评分");
        scoreSet.setColor(Color.parseColor("#E91E63"));
        scoreSet.setCircleColor(Color.parseColor("#E91E63"));
        scoreSet.setLineWidth(2.5f);
        scoreSet.setCircleRadius(4f);
        scoreSet.setValueTextSize(10f);
        scoreSet.setFillColor(Color.parseColor("#FCE4EC"));
        scoreSet.setDrawFilled(true);

        configureLineChart(scoreLineChart, new LineData(scoreSet), xLabels);
        scoreLineChart.getAxisLeft().setAxisMinimum(1f);
        scoreLineChart.getAxisLeft().setAxisMaximum(4f);

        trendLineChart.animateX(800);
        scoreLineChart.animateX(800);
    }

    private void configureLineChart(LineChart chart, LineData data, List<String> xLabels) {
        chart.setData(data);
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(false);
        chart.setPinchZoom(false);
        chart.setDrawGridBackground(false);

        XAxis xAxis = chart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(xLabels));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setTextSize(10f);
        xAxis.setDrawGridLines(false);

        chart.getAxisLeft().setTextSize(10f);
        chart.getAxisLeft().setDrawGridLines(true);
        chart.getAxisLeft().setGridLineWidth(0.5f);
        chart.getAxisRight().setEnabled(false);

        Legend legend = chart.getLegend();
        legend.setTextSize(11f);
        legend.setFormSize(10f);

        chart.invalidate();
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
