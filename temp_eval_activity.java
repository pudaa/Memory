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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EvaluationActivity extends AppCompatActivity {

    private static final String PREF_NAME = "UserPrefs";
    private static final String KEY_USER_ID = "userId";

    private static final int MSG_DASHBOARD_OK = 1;
    private static final int MSG_DASHBOARD_FAIL = -1;
    private static final int MSG_DEEP_OK = 2;
    private static final int MSG_DEEP_FAIL = -2;
    private static final int MSG_AI_OK = 3;
    private static final int MSG_AI_FAIL = -3;
    private static final int MSG_WEEKLY_OK = 5;
    private static final int MSG_WEEKLY_FAIL = -5;

    private int userId;
    private ProgressBar progressBar;
    private TabLayout tabLayout;
    private ViewPager2 viewPager;

    private View overviewRoot, deepRoot, aiRoot;

    private View cardStudyDays, cardStreak, cardWords;
    private View cardMastery, cardTodayDone, cardTodayDue;
    private TextView tvAvgRetrievability, tvAvgStability, tvAvgDifficulty;
    private TextView tvTotalReviews, tvAvgScore, tvWeakWordCount;
    private PieChart masteryPieChart;
    private LineChart recent7DaysChart;
    private TextView tvAiWeeklySummary;

    private LineChart fsrsTrendChart;
    private TextView tvDTrend, tvRTrend, tvDSlope, tvRSlope;
    private LinearLayout weakWordsContainer, criticalWordsContainer;

    private TextView tvOverallAssessment, tvIntensityLevel, tvTrend;
    private TextView tvWeaknessAnalysis;
    private LinearLayout suggestionsLayout;
    private TextView tvRecommendedMode, tvSuggestedDailyNewWords;
    private View btnApplySettings;

    private JSONObject cachedAiData;

    @SuppressLint(""HandlerLeak"")
    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
            case MSG_DASHBOARD_OK: onDashboardLoaded((String) msg.obj); break;
            case MSG_DASHBOARD_FAIL: onLoadFailed(""概览""); break;
            case MSG_DEEP_OK:      onDeepLoaded((String) msg.obj); break;
            case MSG_DEEP_FAIL:    onLoadFailed(""深度分析""); break;
            case MSG_AI_OK:        onAiLoaded((String) msg.obj); break;
            case MSG_AI_FAIL:      onLoadFailed(""AI建议""); break;
            case MSG_WEEKLY_OK:    onWeeklyLoaded((String) msg.obj); break;
            case MSG_WEEKLY_FAIL:  Log.w(""Evaluation"", ""周报加载失败""); break;
            }
        }
    };
