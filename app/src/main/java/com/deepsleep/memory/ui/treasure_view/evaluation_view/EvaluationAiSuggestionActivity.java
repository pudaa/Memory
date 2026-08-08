package com.deepsleep.memory.ui.treasure_view.evaluation_view;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import androidx.core.content.ContextCompat;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.deepsleep.memory.R;
import com.deepsleep.memory.network.GetDataByThread;
import com.deepsleep.memory.settings.InnerSettingsManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * @deprecated 已弃用，AI建议功能已整合到 {@link EvaluationActivity} 的 Tab 页中。
 * 保留此类仅为兼容性参考。
 */
@Deprecated
public class EvaluationAiSuggestionActivity extends AppCompatActivity {

    private static final int MSG_SUCCESS = 1;
    private static final int MSG_FAILED = -1;

    private int userId;
    private ProgressBar progressBar;
    private View contentLayout;

    private TextView tvOverallAssessment;
    private TextView tvIntensityLevel;
    private TextView tvTrend;
    private TextView tvWeaknessAnalysis;
    private LinearLayout suggestionsLayout;
    private TextView tvRecommendedMode, tvSuggestedDailyNewWords;
    private View btnApplySettings;

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
                        populateAiSuggestion(data);
                        progressBar.setVisibility(View.GONE);
                        contentLayout.setVisibility(View.VISIBLE);
                        fadeIn(contentLayout);
                    } else {
                        showError("数据加载失败");
                    }
                } catch (JSONException e) {
                    Log.e("EvalAiSug", "JSON parse error", e);
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
        setContentView(R.layout.evaluation_ai_suggestion_layout);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        progressBar = findViewById(R.id.progress_bar);
        contentLayout = findViewById(R.id.content_layout);

        tvOverallAssessment = findViewById(R.id.tv_overall_assessment);
        tvIntensityLevel = findViewById(R.id.tv_intensity_level);
        tvTrend = findViewById(R.id.tv_trend);
        tvWeaknessAnalysis = findViewById(R.id.tv_weakness_analysis);
        suggestionsLayout = findViewById(R.id.suggestions_layout);
        tvRecommendedMode = findViewById(R.id.tv_recommended_mode);
        tvSuggestedDailyNewWords = findViewById(R.id.tv_suggested_daily_new_words);
        btnApplySettings = findViewById(R.id.btn_apply_settings);

        btnApplySettings.setOnClickListener(v -> applyRecommendedSettings());

        loadData();
    }

    private void loadData() {
        userId = InnerSettingsManager.getInstance(this).getUserId();

        progressBar.setVisibility(View.VISIBLE);
        contentLayout.setVisibility(View.GONE);

        GetDataByThread api = new GetDataByThread("/evaluation/aiSuggestion");
        api.getEvaluationAiSuggestion(handler, MSG_SUCCESS, MSG_FAILED, String.valueOf(userId));
    }

    @SuppressLint("SetTextI18n")
    private void populateAiSuggestion(JSONObject data) throws JSONException {
        tvOverallAssessment.setText(data.optString("overallAssessment", "暂无评估"));

        // 强度级别
        String intensity = data.optString("intensityLevel", "appropriate");
        switch (intensity) {
        case "too_light":
            tvIntensityLevel.setText("强度偏低，可加大新词量");
            break;
        case "too_heavy":
            tvIntensityLevel.setText("强度偏高，建议放缓");
            break;
        default:
            tvIntensityLevel.setText("强度适中");
            break;
        }

        // 趋势
        String trend = data.optString("trend", "stable");
        switch (trend) {
        case "improving":
            tvTrend.setText("上升中");
            break;
        case "declining":
            tvTrend.setText("下降中，需关注");
            break;
        default:
            tvTrend.setText("平稳");
            break;
        }

        tvWeaknessAnalysis.setText(data.optString("weaknessAnalysis", "暂无薄弱分析"));

        // 建议列表
        suggestionsLayout.removeAllViews();
        JSONArray suggestions = data.optJSONArray("suggestions");
        if (suggestions != null && suggestions.length() > 0) {
            for (int i = 0; i < suggestions.length(); i++) {
                TextView tv = new TextView(this);
                tv.setText(suggestions.optString(i));
                tv.setTextSize(14);
                tv.setTextColor(ContextCompat.getColor(this, R.color.theme_text_primary));
                tv.setPadding(16, 8, 16, 8);
                tv.setLineSpacing(4f, 1.2f);
                suggestionsLayout.addView(tv);
            }
        }

        // 推荐设置
        String mode = data.optString("recommendedMode", "choice");
        tvRecommendedMode.setText("choice".equals(mode) ? "选择题模式" : "输入题模式");

        int dailyNew = data.optInt("suggestedDailyNewWords", 10);
        tvSuggestedDailyNewWords.setText("每日 " + dailyNew + " 个新词");
    }

    private void applyRecommendedSettings() {
        // 重新解析最新数据
        Toast.makeText(this, "正在应用推荐设置...", Toast.LENGTH_SHORT).show();

        // 重新加载数据获取推荐值
        userId = InnerSettingsManager.getInstance(this).getUserId();

        Handler applyHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                if (msg.what == MSG_SUCCESS) {
                    String result = (String) msg.obj;
                    try {
                        JSONObject response = new JSONObject(result);
                        if ("200".equals(response.getString("code"))) {
                            JSONObject data = response.getJSONObject("data");
                            int dailyNew = data.optInt("suggestedDailyNewWords", 10);
                            String mode = data.optString("recommendedMode", "choice");

                            GetDataByThread api = new GetDataByThread("/learning/updatePreference");
                            api.updatePreference(new Handler(Looper.getMainLooper()) {
                                @Override
                                public void handleMessage(@NonNull Message msg) {
                                    if (msg.what == MSG_SUCCESS) {
                                        Toast.makeText(EvaluationAiSuggestionActivity.this, "设置已应用", Toast.LENGTH_SHORT)
                                                .show();
                                    } else {
                                        Toast.makeText(EvaluationAiSuggestionActivity.this, "应用设置失败",
                                                Toast.LENGTH_SHORT).show();
                                    }
                                }
                            }, 1, -1, userId, dailyNew, mode, null, null);
                        }
                    } catch (JSONException e) {
                        Toast.makeText(EvaluationAiSuggestionActivity.this, "解析失败", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(EvaluationAiSuggestionActivity.this, "请求失败", Toast.LENGTH_SHORT).show();
                }
            }
        };

        GetDataByThread api = new GetDataByThread("/evaluation/aiSuggestion");
        api.getEvaluationAiSuggestion(applyHandler, MSG_SUCCESS, MSG_FAILED, String.valueOf(userId));
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
