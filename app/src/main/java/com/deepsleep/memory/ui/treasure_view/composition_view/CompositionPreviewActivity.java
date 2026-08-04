package com.deepsleep.memory.ui.treasure_view.composition_view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.deepsleep.memory.R;
import com.deepsleep.memory.network.GetDataByThread;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.json.JSONException;
import org.json.JSONObject;

public class CompositionPreviewActivity extends AppCompatActivity {

    private EditText etCompositionText;
    private Button btnSaveTemp;
    private Button btnSubmit;
    private View loadingOverlay;
    private ProgressBar loadingProgressBar;
    private String ocrText;
    private int userId;
    private static final String PREF_NAME = "UserPrefs";
    private static final String KEY_USER_ID = "userId";
    static final int msg_success = 1;
    static final int msg_failed = -1;
    private int correctTimes = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.composition_preview_layout);

        initViews();
        handleIntentData();
        setListeners();
    }

    private void initViews() {
        etCompositionText = findViewById(R.id.et_composition_text);
        btnSaveTemp = findViewById(R.id.btn_save_temp);
        btnSubmit = findViewById(R.id.btn_submit);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        SharedPreferences sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        userId = sharedPreferences.getInt(KEY_USER_ID, 0);

        // 初始化加载视图
        loadingOverlay = findViewById(R.id.loading_overlay);
        loadingProgressBar = findViewById(R.id.loading_progress_bar);

        // 默认隐藏加载视图
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.GONE);
        }
    }

    private void handleIntentData() {
        // 检查是否有从OCR识别传来的文本
        ocrText = getIntent().getStringExtra("ocr_text");

        if (ocrText != null && !ocrText.isEmpty()) {
            // 转成JSON，获取ocrText中“text”对应的内容
            try {
                JSONObject jsonObject = new JSONObject(ocrText);
                // 兼容两种响应格式：
                // 包裹格式: {"code":200,"data":{"text":"..."}}
                // 扁平格式: {"text":"..."}
                if (jsonObject.has("data")) {
                    JSONObject data = jsonObject.getJSONObject("data");
                    ocrText = data.getString("text");
                } else {
                    ocrText = jsonObject.getString("text");
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            // 将OCR识别的文本显示在编辑框中
            etCompositionText.setText(ocrText);
            Toast.makeText(this, "已转化为文本", Toast.LENGTH_LONG).show();
        } else {
            // 检查是否有暂存的作文
            SharedPreferences sharedPreferences = getSharedPreferences("CompositionPrefs", Context.MODE_PRIVATE);
            String savedComposition = sharedPreferences.getString("saved_composition_" + userId, "");
            if (!savedComposition.isEmpty()) {
                etCompositionText.setText(savedComposition);
                long saveTime = sharedPreferences.getLong("save_time_" + userId, 0);
                String timeStr = android.text.format.DateFormat
                        .format("yyyy-MM-dd HH:mm:ss", new java.util.Date(saveTime)).toString();
                Toast.makeText(this, "已恢复暂存作文", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void setListeners() {
        btnSaveTemp.setOnClickListener(v -> {
            // 保存当前作文到本地SharedPreferences
            String compositionText = etCompositionText.getText().toString();
            SharedPreferences sharedPreferences = getSharedPreferences("CompositionPrefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("saved_composition_" + userId, compositionText);
            editor.putLong("save_time_" + userId, System.currentTimeMillis());
            editor.apply();
            Toast.makeText(this, "作文已暂存", Toast.LENGTH_SHORT).show();
        });

        btnSubmit.setOnClickListener(v -> {
            submitCompositionForCorrection();
        });
    }

    private void submitCompositionForCorrection() {
        String compositionText = etCompositionText.getText().toString().trim();
        if (compositionText.isEmpty()) {
            Toast.makeText(this, "请输入作文", Toast.LENGTH_SHORT).show();
            return;
        }

        // 显示加载动画并阻止用户操作
        showLoadingOverlay();

        // 显示正在批改提示
        // Toast.makeText(this, "正在批改作文，请稍候...", Toast.LENGTH_SHORT).show();

        // 这里可以添加作文批改的实现
        GetDataByThread getDataByThread = new GetDataByThread("/composition/correctText");
        getDataByThread.correctText(new CorrectHandler(), msg_success, msg_failed, compositionText, userId);
    }

    // 显示加载遮罩
    private void showLoadingOverlay() {
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.VISIBLE);
        }
        // 禁用按钮防止重复提交
        btnSubmit.setEnabled(false);
        btnSaveTemp.setEnabled(false);
    }

    // 隐藏加载遮罩
    private void hideLoadingOverlay() {
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.GONE);
        }
        // 恢复按钮功能
        btnSubmit.setEnabled(true);
        btnSaveTemp.setEnabled(true);
    }

    @SuppressLint("HandlerLeak")
    class CorrectHandler extends Handler {
        CorrectHandler() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            // 隐藏加载动画
            hideLoadingOverlay();
            switch (msg.what) {
            case msg_success: // 批改成功
                correctTimes = 0;
                String resultJson = (String) msg.obj;
                Log.d("correct", "批改结果：" + resultJson);
                Intent intent = new Intent(CompositionPreviewActivity.this, CompositionResultActivity.class);
                intent.putExtra("result_json", resultJson);
                startActivity(intent);
                finish();
                break;
            case msg_failed: // 批改失败
                correctTimes++;
                if (correctTimes >= 3) {
                    Toast.makeText(CompositionPreviewActivity.this, "批改失败", Toast.LENGTH_SHORT).show();
                    return;
                }
                submitCompositionForCorrection();
                break;
            }
        }
    }

    // 处理返回键事件，防止在加载过程中意外退出
    @Override
    public void onBackPressed() {
        if (loadingOverlay != null && loadingOverlay.getVisibility() == View.VISIBLE) {
            // 显示确认对话框
            new MaterialAlertDialogBuilder(this).setTitle("正在批改作文").setMessage("作文正在批改中，确定要离开吗？")
                    .setPositiveButton("确定离开", (dialog, which) -> {
                        super.onBackPressed();
                    }).setNegativeButton("继续等待", null).show();
        } else {
            super.onBackPressed();
        }
    }

}
