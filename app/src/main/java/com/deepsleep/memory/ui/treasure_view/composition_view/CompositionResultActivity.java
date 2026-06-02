package com.deepsleep.memory.ui.treasure_view.composition_view;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.deepsleep.memory.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class CompositionResultActivity extends AppCompatActivity {

    private TextView tvScore;
    private TextView tvErrorAnalysis;
    private TextView tvHighlightAnalysis;
    private TextView tvWritingSuggestion;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.composition_result_layout);

        initViews();
        handleResultData();
    }

    private void initViews() {
        tvScore = findViewById(R.id.tv_score);
        tvErrorAnalysis = findViewById(R.id.tv_error_analysis);
        tvHighlightAnalysis = findViewById(R.id.tv_highlight_analysis);
        tvWritingSuggestion = findViewById(R.id.tv_writing_suggestion);

        // 设置返回按钮
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }

    private void handleResultData() {
        String resultJson = getIntent().getStringExtra("result_json");
        Log.d("CompositionResultActivity", "resultJson: " + resultJson);
        if (resultJson != null && !resultJson.isEmpty()) {
            parseAndDisplayResult(resultJson);
        }
    }

    private void parseAndDisplayResult(String resultJson) {
        try {
            JSONObject jsonObject = new JSONObject(resultJson);

            // 解析评分
            JSONObject scoreObj = jsonObject.getJSONObject("评分");
            String score = scoreObj.getString("分数");
            tvScore.setText(score);

            // 解析错误分析
            JSONObject errorAnalysisObj = jsonObject.getJSONObject("错误分析");
            StringBuilder errorAnalysis = new StringBuilder();

            // 语法错误
            JSONArray grammarErrors = errorAnalysisObj.getJSONArray("语法错误");
            if (grammarErrors.length() > 0) {
                errorAnalysis.append("语法错误:\n");
                for (int i = 0; i < grammarErrors.length(); i++) {
                    JSONObject error = grammarErrors.getJSONObject(i);
                    errorAnalysis.append((i + 1)).append(". ")
                            .append(error.getString("错误文本")).append("\n")
                            .append("   说明: ").append(error.getString("错误说明")).append("\n")
                            .append("   建议: ").append(error.getString("修改建议")).append("\n\n");
                }
            }

            // 拼写错误
            JSONArray spellingErrors = errorAnalysisObj.getJSONArray("拼写错误");
            if (spellingErrors.length() > 0) {
                errorAnalysis.append("拼写错误:\n");
                for (int i = 0; i < spellingErrors.length(); i++) {
                    JSONObject error = spellingErrors.getJSONObject(i);
                    errorAnalysis.append((i + 1)).append(". ")
                            .append(error.getString("错误文本")).append("\n")
                            .append("   说明: ").append(error.getString("错误说明")).append("\n")
                            .append("   建议: ").append(error.getString("修改建议")).append("\n\n");
                }
            }

            // 标点错误
            JSONArray punctuationErrors = errorAnalysisObj.getJSONArray("标点错误");
            if (punctuationErrors.length() > 0) {
                errorAnalysis.append("标点错误:\n");
                for (int i = 0; i < punctuationErrors.length(); i++) {
                    JSONObject error = punctuationErrors.getJSONObject(i);
                    errorAnalysis.append((i + 1)).append(". ")
                            .append(error.getString("错误文本")).append("\n")
                            .append("   说明: ").append(error.getString("错误说明")).append("\n")
                            .append("   建议: ").append(error.getString("修改建议")).append("\n\n");
                }
            }

            tvErrorAnalysis.setText(errorAnalysis.toString());

            // 解析亮点分析
            JSONObject highlightAnalysisObj = jsonObject.getJSONObject("亮点分析");
            StringBuilder highlightAnalysis = new StringBuilder();

            // 高级词汇
            JSONArray advancedWords = highlightAnalysisObj.getJSONArray("高级词汇");
            if (advancedWords.length() > 0) {
                highlightAnalysis.append("高级词汇: ");
                for (int i = 0; i < advancedWords.length(); i++) {
                    if (i > 0) highlightAnalysis.append(", ");
                    highlightAnalysis.append(advancedWords.getString(i));
                }
                highlightAnalysis.append("\n\n");
            }

            // 亮点表达
            JSONArray highlightExpressions = highlightAnalysisObj.getJSONArray("亮点表达");
            if (highlightExpressions.length() > 0) {
                highlightAnalysis.append("亮点表达:\n");
                for (int i = 0; i < highlightExpressions.length(); i++) {
                    highlightAnalysis.append((i + 1)).append(". ")
                            .append(highlightExpressions.getString(i)).append("\n");
                }
            }

            tvHighlightAnalysis.setText(highlightAnalysis.toString());

            // 解析写作建议
            String writingSuggestion = jsonObject.getString("写作建议");
            tvWritingSuggestion.setText(writingSuggestion);

        } catch (JSONException e) {
            e.printStackTrace();
            tvErrorAnalysis.setText("解析结果失败");
        }
    }
}