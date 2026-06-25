package com.deepsleep.memory.ui.treasure_view.dictation_view;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.deepsleep.memory.R;
import com.deepsleep.memory.settings.InnerSettingsManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

/**
 * 听写练习 - 成绩结果页 展示评分结果、错词列表，支持错词重练
 */
public class DictationResultActivity extends AppCompatActivity {

    private static final int MSG_RETRY_SUCCESS = 1;
    private static final int MSG_RETRY_FAILED = 2;

    private ImageButton btnBack;
    private TextView tvScore, tvCorrectCount, tvTotalWords, tvGrade;
    private LinearLayout summaryContainer;
    private Button btnRetryWrong, btnBackToList;
    private ScrollView scrollView;

    private String taskId;
    private DictationModels.DictationSubmitResult submitResult;
    private int userId;
    private int lexiconId = 2;

    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dictation_result_layout);

        userId = InnerSettingsManager.getInstance(this).getUserId();
        taskId = getIntent().getStringExtra("taskId");
        String resultJson = getIntent().getStringExtra("resultJson");
        lexiconId = getIntent().getIntExtra("lexiconId", 2);

        initViews();
        initHandler();
        parseResult(resultJson);
    }

    private void initViews() {
        btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> showExitDialog());

        tvScore = findViewById(R.id.tv_score);
        tvCorrectCount = findViewById(R.id.tv_correct_count);
        tvTotalWords = findViewById(R.id.tv_total_words);
        tvGrade = findViewById(R.id.tv_grade);
        summaryContainer = findViewById(R.id.summary_container);
        btnRetryWrong = findViewById(R.id.btn_retry_wrong);
        btnBackToList = findViewById(R.id.btn_back_to_list);
        scrollView = findViewById(R.id.scroll_result);

        btnRetryWrong.setOnClickListener(v -> retryWrongWords());
        btnBackToList.setOnClickListener(v -> showExitDialog());
    }

    private void initHandler() {
        handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(android.os.Message msg) {
                if (msg.what == MSG_RETRY_SUCCESS) {
                    String result = (String) msg.obj;
                    try {
                        JSONObject json = new JSONObject(result);
                        if (json.optString("code").equals("200")) {
                            JSONObject data = json.optJSONObject("data");
                            if (data != null) {
                                String newTaskId = data.optString("taskId", "");
                                Intent intent = new Intent(DictationResultActivity.this,
                                        DictationGenerateActivity.class);
                                // 直接传递已有的 task，跳转到生成页用已有数据渲染
                                intent.putExtra("count", data.optInt("totalWords", 0));
                                intent.putExtra("lexiconId", lexiconId);
                                // 将生成结果传递给生成页（通过静态变量或 Intent 标记）
                                DictationGenerateActivity.setCachedTaskJson(result);
                                startActivity(intent);
                                finish();
                            }
                        } else {
                            Toast.makeText(DictationResultActivity.this, json.optString("message", "重练失败"),
                                    Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else if (msg.what == MSG_RETRY_FAILED) {
                    Toast.makeText(DictationResultActivity.this, "网络请求失败", Toast.LENGTH_SHORT).show();
                }
            }
        };
    }

    private void parseResult(String resultJson) {
        try {
            JSONObject json = new JSONObject(resultJson);
            if (json.optString("code").equals("200")) {
                JSONObject data = json.optJSONObject("data");
                if (data != null) {
                    submitResult = DictationModels.DictationSubmitResult.fromJson(data);
                    displayResult();
                }
            } else {
                Toast.makeText(this, "结果解析失败", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "数据格式错误", Toast.LENGTH_SHORT).show();
        }
    }

    private void displayResult() {
        if (submitResult == null)
            return;

        int total = submitResult.totalWords;
        int correct = submitResult.correctCount;
        int accuracy = total > 0 ? (correct * 100 / total) : 0;

        tvScore.setText(String.valueOf(accuracy));
        tvCorrectCount.setText("正确 " + correct + " 个");
        tvTotalWords.setText("共 " + total + " 个单词");

        // 评级
        String grade;
        int gradeColor;
        if (accuracy >= 90) {
            grade = "优秀";
            gradeColor = 0xFF4CAF50;
        } else if (accuracy >= 75) {
            grade = "良好";
            gradeColor = 0xFF2196F3;
        } else if (accuracy >= 60) {
            grade = "一般";
            gradeColor = 0xFFFF9800;
        } else {
            grade = "需努力";
            gradeColor = 0xFFF44336;
        }
        tvGrade.setText(grade);
        tvGrade.setTextColor(gradeColor);

        // 逐词展示
        summaryContainer.removeAllViews();
        for (DictationModels.DictationSummary summary : submitResult.summary) {
            View itemView = createSummaryItem(summary);
            summaryContainer.addView(itemView);
        }

        // 错词重练按钮
        if (submitResult.wrongWordIds != null && !submitResult.wrongWordIds.isEmpty()) {
            btnRetryWrong.setVisibility(View.VISIBLE);
            btnRetryWrong.setText("重练错词 (" + submitResult.wrongWordIds.size() + ")");
        } else {
            btnRetryWrong.setVisibility(View.GONE);
        }
    }

    private View createSummaryItem(DictationModels.DictationSummary summary) {
        View itemView = getLayoutInflater().inflate(R.layout.item_dictation_summary, summaryContainer, false);

        TextView tvIndex = itemView.findViewById(R.id.tv_summary_index);
        TextView tvTarget = itemView.findViewById(R.id.tv_summary_target);
        TextView tvUserAnswer = itemView.findViewById(R.id.tv_summary_user);
        TextView tvScoreText = itemView.findViewById(R.id.tv_summary_score);
        View colorBar = itemView.findViewById(R.id.color_bar);

        tvIndex.setText(String.valueOf(summary.index));

        if (summary.correct) {
            // 正确：绿色
            tvTarget.setText(summary.targetForm);
            tvUserAnswer.setText(summary.userAnswer);
            tvUserAnswer.setTextColor(0xFF4CAF50);
            tvScoreText.setText("✓ " + getScoreLabel(summary.score));
            tvScoreText.setTextColor(0xFF4CAF50);
            colorBar.setBackgroundColor(0xFF4CAF50);
        } else {
            // 错误：红色，显示正确答案
            tvTarget.setText(summary.targetForm);
            tvUserAnswer.setText(summary.userAnswer.isEmpty() ? "(未作答)" : summary.userAnswer);
            tvUserAnswer.setTextColor(0xFFF44336);
            tvScoreText.setText("✗ " + getScoreLabel(summary.score));
            tvScoreText.setTextColor(0xFFF44336);
            colorBar.setBackgroundColor(0xFFF44336);
        }

        return itemView;
    }

    private String getScoreLabel(int score) {
        switch (score) {
        case 4:
            return "完全掌握";
        case 3:
            return "基本掌握";
        case 2:
            return "模糊";
        case 1:
            return "未掌握";
        default:
            return String.valueOf(score);
        }
    }

    private void showExitDialog() {
        new MaterialAlertDialogBuilder(this).setTitle("离开成绩页").setMessage("确定要离开成绩查看吗？\n你可以通过「错题重练」按钮对错词进行针对性练习。")
                .setPositiveButton("确定离开", (dialog, which) -> {
                    Intent intent = new Intent(DictationResultActivity.this, DictationMenuActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                }).setNegativeButton("继续查看", null).show();
    }

    @Override
    public void onBackPressed() {
        showExitDialog();
    }

    private void retryWrongWords() {
        btnRetryWrong.setEnabled(false);
        btnRetryWrong.setText("正在创建...");
        DictationApiHelper.retryWrongWords(handler, MSG_RETRY_SUCCESS, MSG_RETRY_FAILED, userId, taskId, lexiconId);
    }
}
