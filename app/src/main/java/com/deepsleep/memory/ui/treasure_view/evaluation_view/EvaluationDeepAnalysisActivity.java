package com.deepsleep.memory.ui.treasure_view.evaluation_view;

import android.annotation.SuppressLint;
import android.content.Context;
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
import android.widget.LinearLayout;
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
import com.google.android.material.tabs.TabLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 深度分析页 — 标签栏切换四个维度：
 * 掌握度分布 / FSRS趋势 / 薄弱单词 / 危急单词
 */
public class EvaluationDeepAnalysisActivity extends AppCompatActivity {

    private static final String PREF_NAME = "UserPrefs";
    private static final String KEY_USER_ID = "userId";
    private static final int MSG_SUCCESS = 1;
    private static final int MSG_FAILED = -1;

    private int userId;
    private ProgressBar progressBar;
    private View contentLayout;

    // 标签
    private TabLayout tabLayout;
    // 四个面板
    private View panelDistribution, panelTrend, panelWeakWords, panelCriticalWords;
    private TextView tvMasteryRate, tvMedianR, tvStdDev, tvQuartiles;
    private PieChart distributionPieChart;

    private LineChart fsrsTrendChart;
    private TextView tvDTrend, tvRTrend, tvDSlope, tvRSlope;

    private LinearLayout weakWordsContainer, criticalWordsContainer;

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
                        populateAll(data);
                    } else {
                        showError("数据加载失败");
                    }
                } catch (JSONException e) {
                    Log.e("EvalDeep", "JSON error", e);
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
        setContentView(R.layout.evaluation_deep_analysis_layout);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        progressBar = findViewById(R.id.progress_bar);
        contentLayout = findViewById(R.id.content_layout);
        tabLayout = findViewById(R.id.tab_layout);

        // 四个面板
        panelDistribution = findViewById(R.id.panel_distribution);
        panelTrend = findViewById(R.id.panel_trend);
        panelWeakWords = findViewById(R.id.panel_weak_words);
        panelCriticalWords = findViewById(R.id.panel_critical);

        // 掌握度分布
        tvMasteryRate = findViewById(R.id.tv_mastery_rate);
        tvMedianR = findViewById(R.id.tv_median_r);
        tvStdDev = findViewById(R.id.tv_std_dev);
        tvQuartiles = findViewById(R.id.tv_quartiles);
        distributionPieChart = findViewById(R.id.distribution_pie_chart);

        // FSRS 趋势
        fsrsTrendChart = findViewById(R.id.fsrs_trend_chart);
        tvDTrend = findViewById(R.id.tv_d_trend);
        tvRTrend = findViewById(R.id.tv_r_trend);
        tvDSlope = findViewById(R.id.tv_d_slope);
        tvRSlope = findViewById(R.id.tv_r_slope);

        // 列表
        weakWordsContainer = findViewById(R.id.weak_words_container);
        criticalWordsContainer = findViewById(R.id.critical_words_container);

        setupTabs();
        loadData();
    }

    private void setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText("掌握度分布"));
        tabLayout.addTab(tabLayout.newTab().setText("FSRS趋势"));
        tabLayout.addTab(tabLayout.newTab().setText("薄弱单词"));
        tabLayout.addTab(tabLayout.newTab().setText("危急单词"));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) { showPanel(tab.getPosition()); }
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}
            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        showPanel(0);
    }

    private void showPanel(int index) {
        panelDistribution.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        panelTrend.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        panelWeakWords.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        panelCriticalWords.setVisibility(index == 3 ? View.VISIBLE : View.GONE);
    }

    private void loadData() {
        SharedPreferences sp = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        userId = sp.getInt(KEY_USER_ID, 0);
        progressBar.setVisibility(View.VISIBLE);
        contentLayout.setVisibility(View.GONE);

        GetDataByThread api = new GetDataByThread("/evaluation/deepAnalysis");
        api.getEvaluationDeepAnalysis(handler, MSG_SUCCESS, MSG_FAILED, String.valueOf(userId));
    }

    @SuppressLint("SetTextI18n")
    private void populateAll(JSONObject data) throws JSONException {
        populateDistribution(data.optJSONObject("masteryDistribution"));
        populateFsrsTrend(data.optJSONObject("recent7DaysTrend"));
        populateWeakWords(data.optJSONArray("bottom10Words"));
        populateCriticalWords(data.optJSONArray("criticalWords"));

        progressBar.setVisibility(View.GONE);
        contentLayout.setVisibility(View.VISIBLE);
        fadeIn(contentLayout);
    }

    // ── 掌握度分布 ──

    private void populateDistribution(JSONObject dist) throws JSONException {
        if (dist == null) {
            tvMasteryRate.setText("暂无数据");
            return;
        }

        tvMasteryRate.setText(String.format(Locale.getDefault(), "掌握率 %.0f%%",
                dist.optDouble("masteryRate", 0) * 100));
        tvMedianR.setText(String.format(Locale.getDefault(), "中位数 %.0f%%",
                dist.optJSONObject("extraStats") != null
                        ? dist.getJSONObject("extraStats").optDouble("medianRetrievability", 0) * 100 : 0));

        JSONObject extra = dist.optJSONObject("extraStats");
        if (extra != null) {
            double stdDev = extra.optDouble("retrievabilityStdDev", 0);
            tvStdDev.setText(String.format(Locale.getDefault(), "标准差 %.2f", stdDev));

            JSONArray quartiles = extra.optJSONArray("stabilityQuartiles");
            if (quartiles != null && quartiles.length() >= 3) {
                tvQuartiles.setText(String.format(Locale.getDefault(), "稳定性 Q1=%.1f Q2=%.1f Q3=%.1f 天",
                        quartiles.optDouble(0), quartiles.optDouble(1), quartiles.optDouble(2)));
            }
        }

        // 饼图
        JSONArray buckets = dist.optJSONArray("buckets");
        if (buckets != null && buckets.length() > 0) {
            List<PieEntry> entries = new ArrayList<>();
            List<Integer> colors = new ArrayList<>();
            int[] palette = {0xFF4CAF50, 0xFF8BC34A, 0xFFCDDC39, 0xFFFFEB3B, 0xFFFFC107,
                    0xFFFF9800, 0xFFFF5722, 0xFFF44336, 0xFFE91E63, 0xFF9C27B0};

            for (int i = 0; i < buckets.length(); i++) {
                JSONObject b = buckets.getJSONObject(i);
                int count = b.optInt("count", 0);
                if (count > 0) {
                    entries.add(new PieEntry(count, b.optString("label", "")));
                    colors.add(palette[i % palette.length]);
                }
            }

            if (!entries.isEmpty()) {
                PieDataSet dataSet = new PieDataSet(entries, "");
                dataSet.setColors(colors);
                dataSet.setValueTextSize(11f);
                dataSet.setValueTextColor(Color.WHITE);
                dataSet.setSliceSpace(2f);
                dataSet.setValueFormatter(new PercentFormatter(distributionPieChart));

                distributionPieChart.setData(new PieData(dataSet));
                distributionPieChart.setUsePercentValues(true);
                distributionPieChart.getDescription().setEnabled(false);
                distributionPieChart.setDrawHoleEnabled(true);
                distributionPieChart.setHoleRadius(42f);
                distributionPieChart.setTransparentCircleRadius(47f);
                distributionPieChart.setHoleColor(Color.WHITE);
                distributionPieChart.setCenterText("掌握度分布");
                distributionPieChart.setCenterTextSize(13f);
                distributionPieChart.setDrawEntryLabels(false);
                distributionPieChart.getLegend().setEnabled(false);
                distributionPieChart.animateY(600);
                distributionPieChart.invalidate();
                return;
            }
        }
        distributionPieChart.setNoDataText("暂无分布数据");
        distributionPieChart.invalidate();
    }

    // ── FSRS 趋势 ──

    private void populateFsrsTrend(JSONObject trend) throws JSONException {
        if (trend == null) {
            fsrsTrendChart.setNoDataText("暂无趋势数据");
            fsrsTrendChart.invalidate();
            return;
        }

        tvDTrend.setText(trend.optJSONObject("summary") != null
                ? trend.getJSONObject("summary").optString("difficultyTrend", "—") : "—");
        tvRTrend.setText(trend.optJSONObject("summary") != null
                ? trend.getJSONObject("summary").optString("retrievabilityTrend", "—") : "—");

        JSONObject summary = trend.optJSONObject("summary");
        if (summary != null) {
            tvDSlope.setText(String.format(Locale.getDefault(), "难度斜率 %.2f", summary.optDouble("difficultySlope", 0)));
            tvRSlope.setText(String.format(Locale.getDefault(), "提取率斜率 %.2f", summary.optDouble("retrievabilitySlope", 0)));
        }

        JSONArray points = trend.optJSONArray("points");
        if (points == null || points.length() == 0) {
            fsrsTrendChart.setNoDataText("暂无数据点");
            fsrsTrendChart.invalidate();
            return;
        }

        List<Entry> dEntries = new ArrayList<>();
        List<Entry> rEntries = new ArrayList<>();
        List<Entry> sEntries = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        for (int i = 0; i < points.length(); i++) {
            JSONObject p = points.getJSONObject(i);
            String date = p.optString("date", "");
            labels.add(date.length() >= 5 ? date.substring(date.length() - 5) : date);
            dEntries.add(new Entry(i, (float) p.optDouble("avgDifficulty", 0)));
            rEntries.add(new Entry(i, (float) (p.optDouble("avgRetrievability", 0) * 100)));
            sEntries.add(new Entry(i, (float) p.optDouble("avgStability", 0)));
        }

        LineDataSet dSet = buildLineSet(dEntries, "难度", 0xFFFF9800);
        LineDataSet rSet = buildLineSet(rEntries, "提取率%", 0xFF42A5F5);
        LineDataSet sSet = buildLineSet(sEntries, "稳定性(天)", 0xFF66BB6A);
        sSet.enableDashedLine(8f, 4f, 0f);

        LineData lineData = new LineData(dSet, rSet, sSet);
        configureFsrsChart(labels);
        fsrsTrendChart.setData(lineData);
        fsrsTrendChart.animateX(600);
        fsrsTrendChart.invalidate();
    }

    private LineDataSet buildLineSet(List<Entry> entries, String label, int color) {
        LineDataSet set = new LineDataSet(entries, label);
        set.setColor(color);
        set.setCircleColor(color);
        set.setLineWidth(2f);
        set.setCircleRadius(3f);
        set.setValueTextSize(9f);
        set.setDrawValues(false);
        return set;
    }

    private void configureFsrsChart(List<String> labels) {
        fsrsTrendChart.getDescription().setEnabled(false);
        fsrsTrendChart.setTouchEnabled(true);
        fsrsTrendChart.setDragEnabled(true);
        fsrsTrendChart.setScaleEnabled(false);
        fsrsTrendChart.setPinchZoom(false);

        XAxis xAxis = fsrsTrendChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setTextSize(10f);
        xAxis.setDrawGridLines(false);

        fsrsTrendChart.getAxisLeft().setTextSize(10f);
        fsrsTrendChart.getAxisRight().setEnabled(false);

        Legend legend = fsrsTrendChart.getLegend();
        legend.setTextSize(11f);
        legend.setFormSize(8f);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
    }

    // ── 薄弱单词 ──

    private void populateWeakWords(JSONArray words) throws JSONException {
        weakWordsContainer.removeAllViews();
        if (words == null || words.length() == 0) {
            addEmptyText(weakWordsContainer, "暂无薄弱单词");
            return;
        }

        for (int i = 0; i < words.length(); i++) {
            JSONObject w = words.getJSONObject(i);
            View v = getLayoutInflater().inflate(R.layout.item_weak_word, weakWordsContainer, false);
            TextView tvWord = v.findViewById(R.id.tv_word);
            TextView tvDifficulty = v.findViewById(R.id.tv_difficulty);
            TextView tvLapses = v.findViewById(R.id.tv_lapses);

            String level = w.optString("weaknessLevel", "moderate");
            String prefix = level.equals("critical") ? "" : level.equals("severe") ? "" : "";
            tvWord.setText(prefix + w.optString("headWord", ""));
            // 根据 weaknessLevel 设置左边框颜色
            View indicator = v.findViewById(R.id.weakness_indicator);
            if (indicator != null) {
                int color = level.equals("critical") ? 0xFFF44336
                        : level.equals("severe") ? 0xFFFF9800 : 0xFFFFC107;
                indicator.setBackgroundColor(color);
            }
            tvDifficulty.setText(String.format(Locale.getDefault(),
                    "R %.0f%%  ·  D %.1f  ·  S %.1fd",
                    w.optDouble("retrievability", 0) * 100,
                    w.optDouble("difficulty", 0),
                    w.optDouble("stability", 0)));
            tvLapses.setText("遗忘 " + w.optInt("lapses", 0) + " 次 · 复习 " + w.optInt("reps", 0) + " 次");
            weakWordsContainer.addView(v);
        }
    }

    // ── 危急单词 ──

    private void populateCriticalWords(JSONArray words) throws JSONException {
        criticalWordsContainer.removeAllViews();
        if (words == null || words.length() == 0) {
            addEmptyText(criticalWordsContainer, "暂无危急单词，继续保持");
            return;
        }

        for (int i = 0; i < words.length(); i++) {
            JSONObject w = words.getJSONObject(i);
            View v = getLayoutInflater().inflate(R.layout.item_critical_word, criticalWordsContainer, false);
            TextView tvWord = v.findViewById(R.id.tv_word);
            TextView tvIntervention = v.findViewById(R.id.tv_intervention);

            tvWord.setText(w.optString("headWord", ""));
            tvIntervention.setText(w.optString("intervention", "建议强化复习"));
            criticalWordsContainer.addView(v);
        }
    }

    private void addEmptyText(LinearLayout container, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setPadding(16, 16, 16, 16);
        tv.setTextSize(15);
        tv.setTextColor(0xFF888888);
        container.addView(tv);
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
