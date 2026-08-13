package com.deepsleep.memory.ui.treasure_view.evaluation_view;

import android.annotation.SuppressLint;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.deepsleep.memory.R;
import com.deepsleep.memory.network.GetDataByThread;
import com.deepsleep.memory.settings.InnerSettingsManager;
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
import com.google.android.material.tabs.TabLayoutMediator;

import io.noties.markwon.Markwon;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 学情分析 — 统一入口页 三个 Tab：学习概览 | 深度分析 | AI建议 融合了原有的 Dashboard / Trend / Weekly /
 * DeepAnalysis / AiSuggestion
 */
public class EvaluationActivity extends AppCompatActivity {

    private static final int MSG_DASHBOARD_OK = 1;
    private static final int MSG_DASHBOARD_FAIL = -1;
    private static final int MSG_DEEP_OK = 2;
    private static final int MSG_DEEP_FAIL = -2;
    private static final int MSG_AI_OK = 3;
    private static final int MSG_AI_FAIL = -3;
    private static final int MSG_WEEKLY_OK = 5;
    private static final int MSG_WEEKLY_FAIL = -5;

    private int userId;

    // 全局
    private ProgressBar progressBar;
    private TabLayout tabLayout;
    private ViewPager2 viewPager;

    // 三个页面的根视图
    private View overviewRoot, deepRoot, aiRoot;

    // ── Tab1: 概览 ──
    private View cardStudyDays, cardStreak, cardWords;
    private View cardMastery, cardTodayDone, cardTodayDue;
    private TextView tvAvgRetrievability, tvAvgStability, tvAvgDifficulty;
    private TextView tvTotalReviews, tvAvgScore, tvWeakWordCount;
    private PieChart masteryPieChart;
    private LineChart recent7DaysChart;
    private TextView tvAiWeeklySummary;

    // ── Tab2: 深度分析 ──
    private LineChart fsrsTrendChart;
    private TextView tvDTrend, tvRTrend, tvDSlope, tvRSlope;
    private LinearLayout weakWordsContainer, criticalWordsContainer;

    // ── Tab3: AI建议 ──
    private TextView tvOverallAssessment, tvIntensityLevel, tvTrend;
    private TextView tvWeaknessAnalysis;
    private LinearLayout suggestionsLayout;
    private TextView tvRecommendedMode, tvSuggestedDailyNewWords;
    private View btnApplySettings;

    // ── Tab3: 第3层即时反馈 ──
    private View realtimeAlertCard;
    private android.widget.ImageView ivAlertIcon;
    private TextView tvRealtimeAlert;

    // ── Tab3: 第1层长期策略 ──
    private View longTermPlanCard;
    private TextView tvLtpPhase, tvLtpGoal;
    private LinearLayout milestonesLayout;
    private TextView tvLtpNextAdjust, tvLtpRationale;

    // ── Tab3: 元信息 ──
    private View metaCard;
    private TextView tvMetaSource, tvMetaStale;
    private LinearLayout metaChangesLayout;

    // 数据缓存（用于应用设置时重新获取）
    private JSONObject cachedAiData;

    @SuppressLint("HandlerLeak")
    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
            case MSG_DASHBOARD_OK:
                onDashboardLoaded((String) msg.obj);
                break;
            case MSG_DASHBOARD_FAIL:
                onLoadFailed("概览");
                break;
            case MSG_DEEP_OK:
                onDeepLoaded((String) msg.obj);
                break;
            case MSG_DEEP_FAIL:
                onLoadFailed("深度分析");
                break;
            case MSG_AI_OK:
                onAiLoaded((String) msg.obj);
                break;
            case MSG_AI_FAIL:
                onLoadFailed("AI建议");
                showAiError("AI建议加载失败，请稍后重试");
                break;
            case MSG_WEEKLY_OK:
                onWeeklyLoaded((String) msg.obj);
                break;
            case MSG_WEEKLY_FAIL:
                Log.w("Evaluation", "周报加载失败");
                break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.evaluation_main_layout);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        progressBar = findViewById(R.id.progress_bar);
        tabLayout = findViewById(R.id.tab_layout);
        viewPager = findViewById(R.id.view_pager);

        setupViewPager();
        loadAllData();
    }

    // ═══════════════════════════════════════════════
    // ViewPager2 + TabLayout
    // ═══════════════════════════════════════════════

    private void setupViewPager() {
        PageAdapter adapter = new PageAdapter();
        viewPager.setAdapter(adapter);
        viewPager.setOffscreenPageLimit(2);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
            case 0:
                tab.setText("学习概览");
                break;
            case 1:
                tab.setText("深度分析");
                break;
            case 2:
                tab.setText("AI建议");
                break;
            }
        }).attach();
    }

    private class PageAdapter extends RecyclerView.Adapter<PageAdapter.Holder> {
        private final int[] LAYOUTS = { R.layout.evaluation_page_overview, R.layout.evaluation_page_deep,
                R.layout.evaluation_page_ai };
        /** 缓存已创建的页面根视图，避免依赖 findViewHolderForAdapterPosition */
        final View[] pages = new View[3];

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(LAYOUTS[viewType], parent, false);
            pages[viewType] = v;
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int pos) {
        }

        @Override
        public int getItemCount() {
            return 3;
        }

        @Override
        public int getItemViewType(int pos) {
            return pos;
        }

        class Holder extends RecyclerView.ViewHolder {
            Holder(View v) {
                super(v);
            }
        }
    }

    /** 从适配器缓存的页面根视图中绑定子视图 */
    private void bindPageViews() {
        PageAdapter adapter = (PageAdapter) viewPager.getAdapter();
        if (adapter == null)
            return;

        if (adapter.pages[0] != null && overviewRoot == null) {
            overviewRoot = adapter.pages[0];
            bindOverviewViews(overviewRoot);
        }
        if (adapter.pages[1] != null && deepRoot == null) {
            deepRoot = adapter.pages[1];
            bindDeepViews(deepRoot);
        }
        if (adapter.pages[2] != null && aiRoot == null) {
            aiRoot = adapter.pages[2];
            bindAiViews(aiRoot);
        }
    }

    // ═══════════════════════════════════════════════
    // 视图绑定（接收页面根视图）
    // ═══════════════════════════════════════════════

    private void bindOverviewViews(View root) {
        cardStudyDays = root.findViewById(R.id.card_days);
        cardStreak = root.findViewById(R.id.card_streak);
        cardWords = root.findViewById(R.id.card_words);
        cardMastery = root.findViewById(R.id.card_mastery);
        cardTodayDone = root.findViewById(R.id.card_today_done);
        cardTodayDue = root.findViewById(R.id.card_today_due);
        tvAvgRetrievability = root.findViewById(R.id.tv_avg_retrievability);
        tvAvgStability = root.findViewById(R.id.tv_avg_stability);
        tvAvgDifficulty = root.findViewById(R.id.tv_avg_difficulty);
        tvTotalReviews = root.findViewById(R.id.tv_total_reviews);
        tvAvgScore = root.findViewById(R.id.tv_avg_score);
        tvWeakWordCount = root.findViewById(R.id.tv_weak_word_count);
        masteryPieChart = root.findViewById(R.id.mastery_pie_chart);
        recent7DaysChart = root.findViewById(R.id.recent_7days_chart);
        tvAiWeeklySummary = root.findViewById(R.id.tv_ai_weekly_summary);

        // 预设图表暗色主题适配
        int tc = chartTextColor();
        masteryPieChart.setNoDataTextColor(tc);
        masteryPieChart.setBackgroundColor(Color.TRANSPARENT);
        recent7DaysChart.setNoDataTextColor(tc);
        recent7DaysChart.setBackgroundColor(Color.TRANSPARENT);
    }

    private void bindDeepViews(View root) {
        fsrsTrendChart = root.findViewById(R.id.fsrs_trend_chart);
        tvDTrend = root.findViewById(R.id.tv_d_trend);
        tvRTrend = root.findViewById(R.id.tv_r_trend);
        tvDSlope = root.findViewById(R.id.tv_d_slope);
        tvRSlope = root.findViewById(R.id.tv_r_slope);
        weakWordsContainer = root.findViewById(R.id.weak_words_container);
        criticalWordsContainer = root.findViewById(R.id.critical_words_container);

        fsrsTrendChart.setNoDataTextColor(chartTextColor());
        fsrsTrendChart.setBackgroundColor(Color.TRANSPARENT);
    }

    private void bindAiViews(View root) {
        tvOverallAssessment = root.findViewById(R.id.tv_overall_assessment);
        tvIntensityLevel = root.findViewById(R.id.tv_intensity_level);
        tvTrend = root.findViewById(R.id.tv_trend);
        tvWeaknessAnalysis = root.findViewById(R.id.tv_weakness_analysis);
        suggestionsLayout = root.findViewById(R.id.suggestions_layout);
        tvRecommendedMode = root.findViewById(R.id.tv_recommended_mode);
        tvSuggestedDailyNewWords = root.findViewById(R.id.tv_suggested_daily_new_words);
        btnApplySettings = root.findViewById(R.id.btn_apply_settings);
        if (btnApplySettings != null) {
            btnApplySettings.setOnClickListener(v -> applyRecommendedSettings());
        }

        // 第3层：即时反馈
        realtimeAlertCard = root.findViewById(R.id.realtime_alert_card);
        ivAlertIcon = root.findViewById(R.id.iv_alert_icon);
        tvRealtimeAlert = root.findViewById(R.id.tv_realtime_alert);

        // 第1层：长期策略
        longTermPlanCard = root.findViewById(R.id.long_term_plan_card);
        tvLtpPhase = root.findViewById(R.id.tv_ltp_phase);
        tvLtpGoal = root.findViewById(R.id.tv_ltp_goal);
        milestonesLayout = root.findViewById(R.id.milestones_layout);
        tvLtpNextAdjust = root.findViewById(R.id.tv_ltp_next_adjust);
        tvLtpRationale = root.findViewById(R.id.tv_ltp_rationale);

        // 元信息
        metaCard = root.findViewById(R.id.meta_card);
        tvMetaSource = root.findViewById(R.id.tv_meta_source);
        tvMetaStale = root.findViewById(R.id.tv_meta_stale);
        metaChangesLayout = root.findViewById(R.id.meta_changes_layout);
    }

    // ═══════════════════════════════════════════════
    // 数据加载
    // ═══════════════════════════════════════════════

    private void loadAllData() {
        userId = InnerSettingsManager.getInstance(this).getUserId();
        progressBar.setVisibility(View.VISIBLE);

        new GetDataByThread("/evaluation/dashboard").getEvaluationDashboard(handler, MSG_DASHBOARD_OK,
                MSG_DASHBOARD_FAIL, String.valueOf(userId));
        new GetDataByThread("/evaluation/deepAnalysis").getEvaluationDeepAnalysis(handler, MSG_DEEP_OK, MSG_DEEP_FAIL,
                String.valueOf(userId));
        new GetDataByThread("/evaluation/aiSuggestion").getEvaluationAiSuggestion(handler, MSG_AI_OK, MSG_AI_FAIL,
                String.valueOf(userId));
        new GetDataByThread("/evaluation/weeklyReport").getEvaluationWeeklyReport(handler, MSG_WEEKLY_OK,
                MSG_WEEKLY_FAIL, String.valueOf(userId));
    }

    private void onLoadFailed(String section) {
        Log.w("Evaluation", section + " 加载失败");
    }

    private void showContentIfReady() {
        if (overviewRoot != null && progressBar.getVisibility() == View.VISIBLE) {
            progressBar.setVisibility(View.GONE);
            fadeIn(viewPager);
        }
    }

    // ═══════════════════════════════════════════════
    // Tab1: 学习概览
    // ═══════════════════════════════════════════════

    @SuppressLint("SetTextI18n")
    private void onDashboardLoaded(String result) {
        try {
            JSONObject response = new JSONObject(result);
            if (!"200".equals(response.getString("code"))) {
                showError("数据加载失败");
                return;
            }

            final JSONObject data = response.getJSONObject("data");

            // 延迟到 ViewPager2 布局完成后再绑定视图并填充数据
            viewPager.post(() -> {
                bindPageViews();
                if (overviewRoot == null)
                    return;

                // 概览卡片
                populateCard(cardStudyDays, String.valueOf(data.optInt("totalStudyDays", 0)), "已学天数");
                populateCard(cardStreak, String.valueOf(data.optInt("consecutiveDays", 0)), "连续天数");
                populateCard(cardWords, data.optInt("learnedWords", 0) + "/" + data.optInt("totalWords", 0), "已学单词");
                populateCard(cardMastery,
                        String.format(Locale.getDefault(), "%.0f%%", data.optDouble("masteryRate", 0) * 100), "掌握率");
                populateCard(cardTodayDone, String.valueOf(data.optInt("todayStudied", 0)), "今日已学");
                populateCard(cardTodayDue, String.valueOf(data.optInt("todayDueCount", 0)), "待复习");

                // FSRS 指标
                tvAvgRetrievability.setText(
                        String.format(Locale.getDefault(), "%.0f%%", data.optDouble("avgRetrievability", 0) * 100));
                tvAvgStability.setText(String.format(Locale.getDefault(), "%.1f天", data.optDouble("avgStability", 0)));
                tvAvgDifficulty.setText(String.format(Locale.getDefault(), "%.1f", data.optDouble("avgDifficulty", 0)));
                tvTotalReviews.setText(String.valueOf(data.optInt("totalReviews", 0)));
                tvAvgScore.setText(String.format(Locale.getDefault(), "%.1f/4.0", data.optDouble("avgScore", 0)));
                tvWeakWordCount.setText(String.valueOf(data.optInt("weakWordCount", 0)));

                // 掌握度分布饼图
                try {
                    populateMasteryPie(data);
                } catch (JSONException ignored) {
                }

                // 近7日趋势
                try {
                    populateRecent7Days(data.optJSONArray("recent7Days"));
                } catch (JSONException ignored) {
                }

                // 显示内容
                showContentIfReady();
            });

        } catch (JSONException e) {
            Log.e("Evaluation", "Dashboard JSON error", e);
            showError("数据解析失败");
        }
    }

    private void populateMasteryPie(JSONObject data) throws JSONException {
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
        masteryPieChart.setHoleColor(chartHoleColor());
        masteryPieChart.setCenterText("掌握度\n分布");
        masteryPieChart.setCenterTextSize(14f);
        masteryPieChart.setCenterTextColor(chartCenterTextColor());
        masteryPieChart.setRotationEnabled(false);
        masteryPieChart.setDrawEntryLabels(false);
        masteryPieChart.setNoDataTextColor(chartTextColor());

        Legend legend = masteryPieChart.getLegend();
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setTextSize(11f);
        legend.setTextColor(chartTextColor());
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

        LineDataSet newSet = makeLineSet(newEntries, "新词", "#42A5F5", false);
        LineDataSet reviewSet = makeLineSet(reviewEntries, "复习", "#FFB74D", false);
        LineDataSet totalSet = makeLineSet(totalEntries, "总量", "#66BB6A", true);
        totalSet.enableDashedLine(10f, 5f, 0f);

        configureLineChart(recent7DaysChart, new LineData(newSet, reviewSet, totalSet), xLabels);
        recent7DaysChart.animateX(800);
    }

    private void onWeeklyLoaded(String result) {
        try {
            JSONObject response = new JSONObject(result);
            if (!"200".equals(response.getString("code")))
                return;
            final JSONObject data = response.getJSONObject("data");

            viewPager.post(() -> {
                bindPageViews();
                if (tvAiWeeklySummary == null)
                    return;
                String summary = data.optString("aiWeeklySummary", "");
                Markwon.create(EvaluationActivity.this).setMarkdown(tvAiWeeklySummary,
                        summary.isEmpty() ? "暂无本周总结" : summary);
            });
        } catch (JSONException e) {
            Log.e("Evaluation", "Weekly JSON error", e);
        }
    }

    // ═══════════════════════════════════════════════
    // Tab2: 深度分析
    // ═══════════════════════════════════════════════

    @SuppressLint("SetTextI18n")
    private void onDeepLoaded(String result) {
        try {
            JSONObject response = new JSONObject(result);
            if (!"200".equals(response.getString("code")))
                return;

            final JSONObject data = response.getJSONObject("data");

            viewPager.post(() -> {
                bindPageViews();
                if (deepRoot == null)
                    return;

                try {
                    JSONObject trend = data.optJSONObject("recent7DaysTrend");
                    if (trend != null)
                        populateFsrsTrend(trend);
                    populateWeakWords(data.optJSONArray("bottom10Words"));
                    populateCriticalWords(data.optJSONArray("criticalWords"));
                } catch (JSONException e) {
                    Log.e("Evaluation", "Deep populate error", e);
                }
            });

        } catch (JSONException e) {
            Log.e("Evaluation", "Deep JSON error", e);
        }
    }

    private void populateFsrsTrend(JSONObject trend) throws JSONException {
        JSONObject summary = trend.optJSONObject("summary");
        if (summary != null) {
            tvDTrend.setText(summary.optString("difficultyTrend", "—"));
            tvRTrend.setText(summary.optString("retrievabilityTrend", "—"));
            tvDSlope.setText(String.format(Locale.getDefault(), "难度斜率 %.2f", summary.optDouble("difficultySlope", 0)));
            tvRSlope.setText(
                    String.format(Locale.getDefault(), "提取率斜率 %.2f", summary.optDouble("retrievabilitySlope", 0)));
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

        LineDataSet dSet = makeLineSet(dEntries, "难度", "#FF9800", false);
        dSet.setDrawValues(false);
        LineDataSet rSet = makeLineSet(rEntries, "提取率%", "#42A5F5", false);
        rSet.setDrawValues(false);
        LineDataSet sSet = makeLineSet(sEntries, "稳定性(天)", "#66BB6A", false);
        sSet.setDrawValues(false);
        sSet.enableDashedLine(8f, 4f, 0f);

        LineData lineData = new LineData(dSet, rSet, sSet);
        configureLineChart(fsrsTrendChart, lineData, labels);
        fsrsTrendChart.animateX(600);
    }

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
            tvWord.setText(w.optString("headWord", ""));

            View indicator = v.findViewById(R.id.weakness_indicator);
            if (indicator != null) {
                int color = level.equals("critical") ? 0xFFF44336 : level.equals("severe") ? 0xFFFF9800 : 0xFFFFC107;
                indicator.setBackgroundColor(color);
            }

            tvDifficulty.setText(String.format(Locale.getDefault(), "R %.0f%%  ·  D %.1f  ·  S %.1fd",
                    w.optDouble("retrievability", 0) * 100, w.optDouble("difficulty", 0), w.optDouble("stability", 0)));
            tvLapses.setText("遗忘 " + w.optInt("lapses", 0) + " 次 · 复习 " + w.optInt("reps", 0) + " 次");
            weakWordsContainer.addView(v);
        }
    }

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

    // ═══════════════════════════════════════════════
    // Tab3: AI建议
    // ═══════════════════════════════════════════════

    @SuppressLint("SetTextI18n")
    private void onAiLoaded(String result) {
        try {
            JSONObject response = new JSONObject(result);
            if (!"200".equals(response.getString("code"))) {
                showAiError("AI建议加载失败，请稍后重试");
                return;
            }

            final JSONObject data = response.getJSONObject("data");
            cachedAiData = data;

            viewPager.post(() -> {
                bindPageViews();
                if (aiRoot == null)
                    return;

                // ===== 第2层：中期建议 =====
                tvOverallAssessment.setText(data.optString("overallAssessment", "暂无评估"));

                String intensity = data.optString("intensityLevel", "appropriate");
                switch (intensity) {
                case "too_light":
                    tvIntensityLevel.setText("强度偏低，可加大新词量");
                    tvIntensityLevel.setTextColor(getErrorColor());
                    break;
                case "too_heavy":
                    tvIntensityLevel.setText("强度偏高，建议放缓");
                    tvIntensityLevel.setTextColor(getErrorColor());
                    break;
                default:
                    tvIntensityLevel.setText("强度适中");
                    tvIntensityLevel.setTextColor(getSuccessColor());
                    break;
                }

                String trendStr = data.optString("trend", "stable");
                switch (trendStr) {
                case "improving":
                    tvTrend.setText("上升中");
                    tvTrend.setTextColor(getSuccessColor());
                    break;
                case "declining":
                    tvTrend.setText("下降中，需关注");
                    tvTrend.setTextColor(getErrorColor());
                    break;
                default:
                    tvTrend.setText("平稳");
                    tvTrend.setTextColor(ContextCompat.getColor(this, R.color.theme_text_secondary));
                    break;
                }

                tvWeaknessAnalysis.setText(data.optString("weaknessAnalysis", "暂无薄弱分析"));

                suggestionsLayout.removeAllViews();
                JSONArray suggestions = data.optJSONArray("suggestions");
                if (suggestions != null && suggestions.length() > 0) {
                    for (int i = 0; i < suggestions.length(); i++) {
                        TextView tv = new TextView(EvaluationActivity.this);
                        tv.setText("  " + suggestions.optString(i));
                        tv.setTextSize(14);
                        tv.setTextColor(ContextCompat.getColor(EvaluationActivity.this, R.color.theme_text_primary));
                        tv.setPadding(16, 8, 16, 8);
                        tv.setLineSpacing(4f, 1.2f);
                        suggestionsLayout.addView(tv);
                    }
                }

                String mode = data.optString("recommendedMode", "choice");
                tvRecommendedMode.setText("choice".equals(mode) ? "选择题模式" : "输入题模式");

                int dailyNew = data.optInt("suggestedDailyNewWords", 10);
                tvSuggestedDailyNewWords.setText("每日 " + dailyNew + " 个新词");

                // ===== 第3层：即时反馈 =====
                String realtimeAlert = data.optString("realtimeAlert", null);
                String alertType = data.optString("realtimeAlertType", "none");
                if (realtimeAlert != null && !"null".equals(realtimeAlert)
                        && !"none".equals(alertType) && realtimeAlertCard != null) {
                    realtimeAlertCard.setVisibility(View.VISIBLE);
                    tvRealtimeAlert.setText(realtimeAlert);

                    if ("warning".equals(alertType)) {
                        ivAlertIcon.setImageResource(R.drawable.ic_warning);
                        tvRealtimeAlert.setTextColor(getErrorColor());
                    } else if ("encouragement".equals(alertType)) {
                        ivAlertIcon.setImageResource(R.drawable.ic_celebration);
                        tvRealtimeAlert.setTextColor(getSuccessColor());
                    }
                } else if (realtimeAlertCard != null) {
                    realtimeAlertCard.setVisibility(View.GONE);
                }

                // ===== 第1层：长期策略 =====
                JSONObject longTermPlan = data.optJSONObject("longTermPlan");
                if (longTermPlan != null && longTermPlanCard != null) {
                    longTermPlanCard.setVisibility(View.VISIBLE);

                    tvLtpPhase.setText(longTermPlan.optString("phase", "—"));
                    tvLtpGoal.setText(longTermPlan.optString("goal", "—"));

                    // 里程碑
                    milestonesLayout.removeAllViews();
                    JSONArray milestones = longTermPlan.optJSONArray("milestones");
                    if (milestones != null) {
                        for (int i = 0; i < milestones.length(); i++) {
                            JSONObject m = milestones.optJSONObject(i);
                            if (m == null) continue;
                            String name = m.optString("name", "");
                            boolean achieved = m.optBoolean("achieved", false);

                            LinearLayout row = new LinearLayout(EvaluationActivity.this);
                            row.setOrientation(LinearLayout.HORIZONTAL);
                            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                            row.setPadding(0, 4, 0, 4);

                            android.widget.ImageView icon = new android.widget.ImageView(EvaluationActivity.this);
                            icon.setImageResource(achieved ? R.drawable.ic_check_circle : R.drawable.ic_radio_unchecked);
                            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                                    dpToPx(20), dpToPx(20));
                            iconLp.setMarginEnd(dpToPx(8));
                            icon.setLayoutParams(iconLp);

                            TextView label = new TextView(EvaluationActivity.this);
                            label.setText(name);
                            label.setTextSize(13);
                            label.setTextColor(achieved
                                    ? ContextCompat.getColor(EvaluationActivity.this, R.color.theme_stress)
                                    : ContextCompat.getColor(EvaluationActivity.this, R.color.theme_text_secondary));

                            row.addView(icon);
                            row.addView(label);
                            milestonesLayout.addView(row);
                        }
                    }

                    tvLtpNextAdjust.setText(longTermPlan.optString("nextAdjustCondition", ""));
                    tvLtpRationale.setText(longTermPlan.optString("rationale", ""));
                } else if (longTermPlanCard != null) {
                    longTermPlanCard.setVisibility(View.GONE);
                }

                // ===== 元信息 =====
                JSONObject meta = data.optJSONObject("meta");
                if (meta != null && metaCard != null) {
                    metaCard.setVisibility(View.VISIBLE);

                    String dataSource = meta.optString("dataSource", "fresh");
                    if ("cached".equals(dataSource)) {
                        tvMetaSource.setText("使用缓存数据");
                    } else {
                        tvMetaSource.setText("本次新生成");
                    }

                    int staleDays = meta.optInt("staleDays", 0);
                    if (staleDays > 0) {
                        tvMetaStale.setVisibility(View.VISIBLE);
                        tvMetaStale.setText("（建议已过 " + staleDays + " 天）");
                    } else {
                        tvMetaStale.setVisibility(View.GONE);
                    }

                    // 变化列表
                    metaChangesLayout.removeAllViews();
                    JSONArray changes = meta.optJSONArray("changes");
                    if (changes != null && changes.length() > 0) {
                        metaChangesLayout.setVisibility(View.VISIBLE);
                        for (int i = 0; i < changes.length(); i++) {
                            TextView tv = new TextView(EvaluationActivity.this);
                            tv.setText("  " + changes.optString(i));
                            tv.setTextSize(12);
                            tv.setTextColor(ContextCompat.getColor(EvaluationActivity.this, R.color.theme_text_secondary));
                            tv.setPadding(16, 2, 16, 2);
                            metaChangesLayout.addView(tv);
                        }
                    } else {
                        metaChangesLayout.setVisibility(View.GONE);
                    }
                } else if (metaCard != null) {
                    metaCard.setVisibility(View.GONE);
                }
            });

        } catch (JSONException e) {
            Log.e("Evaluation", "AI JSON error", e);
        }
    }

    /** AI 建议加载失败时展示错误文案，避免停留在"加载中" */
    private void showAiError(String msg) {
        viewPager.post(() -> {
            bindPageViews();
            if (aiRoot == null)
                return;
            tvOverallAssessment.setText(msg);
            tvWeaknessAnalysis.setText(msg);
            if (btnApplySettings != null) {
                btnApplySettings.setEnabled(false);
            }
        });
    }

    private void applyRecommendedSettings() {
        if (cachedAiData == null) {
            Toast.makeText(this, "数据尚未加载", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "正在应用推荐设置...", Toast.LENGTH_SHORT).show();

        try {
            int dailyNew = cachedAiData.optInt("suggestedDailyNewWords", 10);
            String mode = cachedAiData.optString("recommendedMode", "choice");

            GetDataByThread api = new GetDataByThread("/learning/updatePreference");
            api.updatePreference(new Handler(Looper.getMainLooper()) {
                @Override
                public void handleMessage(@NonNull Message msg) {
                    if (msg.what == 1) {
                        Toast.makeText(EvaluationActivity.this, "设置已应用", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(EvaluationActivity.this, "应用设置失败", Toast.LENGTH_SHORT).show();
                    }
                }
            }, 1, -1, userId, dailyNew, mode, null, null);
        } catch (Exception e) {
            Toast.makeText(this, "解析失败", Toast.LENGTH_SHORT).show();
        }
    }

    // ═══════════════════════════════════════════════
    // 工具方法
    // ═══════════════════════════════════════════════

    private boolean isDarkMode() {
        return (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    /** 图表文字颜色：深色模式用浅色，浅色模式用深色 */
    private int chartTextColor() {
        return isDarkMode() ? Color.parseColor("#CCCCCC") : Color.parseColor("#333333");
    }

    /** 图表网格/轴线颜色 */
    private int chartGridColor() {
        return isDarkMode() ? Color.parseColor("#444444") : Color.parseColor("#DDDDDD");
    }

    /** 图表中心/孔洞颜色 */
    private int chartHoleColor() {
        return isDarkMode() ? Color.parseColor("#1E1E1E") : Color.WHITE;
    }

    /** 饼图中心文字颜色 */
    private int chartCenterTextColor() {
        return isDarkMode() ? Color.parseColor("#AAAAAA") : Color.parseColor("#666666");
    }

    private void populateCard(View cardView, String value, String label) {
        TextView tvValue = cardView.findViewById(R.id.tv_value);
        TextView tvLabel = cardView.findViewById(R.id.tv_label);
        if (tvValue != null)
            tvValue.setText(value);
        if (tvLabel != null)
            tvLabel.setText(label);
    }

    private LineDataSet makeLineSet(List<Entry> entries, String label, String colorHex, boolean bold) {
        int color = Color.parseColor(colorHex);
        LineDataSet set = new LineDataSet(entries, label);
        set.setColor(color);
        set.setCircleColor(color);
        set.setLineWidth(bold ? 2.5f : 2f);
        set.setCircleRadius(bold ? 4f : 3f);
        set.setValueTextSize(10f);
        return set;
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
        xAxis.setTextColor(chartTextColor());
        xAxis.setDrawGridLines(false);

        chart.getAxisLeft().setTextSize(10f);
        chart.getAxisLeft().setTextColor(chartTextColor());
        chart.getAxisLeft().setDrawGridLines(true);
        chart.getAxisLeft().setGridLineWidth(0.5f);
        chart.getAxisLeft().setGridColor(chartGridColor());
        chart.getAxisRight().setEnabled(false);

        Legend legend = chart.getLegend();
        legend.setTextSize(11f);
        legend.setTextColor(chartTextColor());
        legend.setFormSize(10f);

        chart.setNoDataTextColor(chartTextColor());
        chart.invalidate();
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

    /**
     * 解析主题属性颜色（支持明暗主题）
     * 使用标准 Material 属性：colorSecondary 作为正向色，colorError 作为警示色
     */
    private int resolveColor(int attrResId, int fallback) {
        android.util.TypedValue tv = new android.util.TypedValue();
        if (getTheme().resolveAttribute(attrResId, tv, true)) {
            return tv.data;
        }
        return fallback;
    }

    /**
     * 获取正向/成功色（使用 colorSecondary，即 theme_stress）
     */
    private int getSuccessColor() {
        return resolveColor(com.google.android.material.R.attr.colorSecondary,
                ContextCompat.getColor(this, R.color.theme_stress));
    }

    /**
     * 获取警示/错误色（使用 colorError）
     */
    private int getErrorColor() {
        return resolveColor(com.google.android.material.R.attr.colorError,
                ContextCompat.getColor(this, R.color.theme_error));
    }

    /**
     * dp 转 px
     */
    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static void fadeIn(View view) {
        AlphaAnimation anim = new AlphaAnimation(0f, 1f);
        anim.setDuration(400);
        anim.setInterpolator(new DecelerateInterpolator());
        view.startAnimation(anim);
    }
}
