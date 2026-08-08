package com.deepsleep.memory.ui.extra_view.setting_view;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.deepsleep.memory.R;
import com.deepsleep.memory.network.GetDataByThread;
import com.deepsleep.memory.settings.InnerSettingsManager;
import com.deepsleep.memory.settings.ThemeHelper;
import com.deepsleep.memory.settings.UserSettingsManager;

public class SettingActivity extends AppCompatActivity {
    private UserSettingsManager userSettingsManager;

    // 学习模式
    private LinearLayout optionModeChoice;
    private LinearLayout optionModeInput;

    // 每日新词数
    private TextView tvNewWordsCount;
    private ImageButton btnNewWordsMinus;
    private ImageButton btnNewWordsPlus;
    private int dailyNewWords;

    // 卡片操作
    private Switch switchSwipeBack;

    // 主题模式
    private TextView tvThemeMode;

    private static final int MIN_NEW_WORDS = 5;
    private static final int MAX_NEW_WORDS = 100;
    private static final int STEP_NEW_WORDS = 5;

    // 网络请求
    private int userId;
    private static final int MSG_SUCCESS = 1;
    private static final int MSG_FAILED = -1;

    // 防抖：延迟发送 API 请求，避免快速点击导致多次请求
    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingDailyWordsUpdate;
    private Runnable pendingStudyModeUpdate;
    private static final long DEBOUNCE_DELAY_MS = 800;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.setting_layout);
        userSettingsManager = UserSettingsManager.getInstance(this);

        userId = InnerSettingsManager.getInstance(this).getUserId();

        // 返回按钮
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        // === 学习模式 ===
        optionModeChoice = findViewById(R.id.option_mode_choice);
        optionModeInput = findViewById(R.id.option_mode_input);

        optionModeChoice.setOnClickListener(v -> selectStudyMode("choice"));
        optionModeInput.setOnClickListener(v -> selectStudyMode("input"));

        // === 每日新词数 ===
        tvNewWordsCount = findViewById(R.id.tv_new_words_count);
        btnNewWordsMinus = findViewById(R.id.btn_new_words_minus);
        btnNewWordsPlus = findViewById(R.id.btn_new_words_plus);

        dailyNewWords = userSettingsManager.getDailyNewWords();
        updateNewWordsDisplay();

        btnNewWordsMinus.setOnClickListener(v -> {
            if (dailyNewWords > MIN_NEW_WORDS) {
                dailyNewWords -= STEP_NEW_WORDS;
                updateNewWordsDisplay();
                userSettingsManager.setDailyNewWords(dailyNewWords);
                scheduleDailyWordsUpdate();
            }
        });

        btnNewWordsPlus.setOnClickListener(v -> {
            if (dailyNewWords < MAX_NEW_WORDS) {
                dailyNewWords += STEP_NEW_WORDS;
                updateNewWordsDisplay();
                userSettingsManager.setDailyNewWords(dailyNewWords);
                scheduleDailyWordsUpdate();
            }
        });

        // === 卡片操作 ===
        switchSwipeBack = findViewById(R.id.switch_swipe_back);
        switchSwipeBack.setOnCheckedChangeListener((buttonView, isChecked) -> {
            userSettingsManager.setIsSlideBack(isChecked);
        });

        // === 主题模式 ===
        tvThemeMode = findViewById(R.id.tv_theme_mode);
        updateThemeModeDisplay();
        tvThemeMode.setOnClickListener(v -> cycleThemeMode());

        initView();
    }

    private void initView() {
        String currentMode = userSettingsManager.getStudyMode();
        selectStudyModeUI(currentMode);
        switchSwipeBack.setChecked(userSettingsManager.isSlideBackEnabled());
        dailyNewWords = userSettingsManager.getDailyNewWords();
        updateNewWordsDisplay();
    }

    // ==================== 学习模式 ====================

    private void selectStudyMode(String mode) {
        userSettingsManager.setStudyMode(mode);
        selectStudyModeUI(mode);
        String modeName = "choice".equals(mode) ? "选择题模式" : "输入题模式";
        Toast.makeText(this, "已切换为" + modeName, Toast.LENGTH_SHORT).show();

        // 防抖同步到服务端
        scheduleStudyModeUpdate(mode);
    }

    private void selectStudyModeUI(String mode) {
        if ("input".equals(mode)) {
            optionModeChoice.setBackgroundResource(R.drawable.option_background_default);
            optionModeInput.setBackgroundResource(R.drawable.option_background_selected);
        } else {
            optionModeChoice.setBackgroundResource(R.drawable.option_background_selected);
            optionModeInput.setBackgroundResource(R.drawable.option_background_default);
        }
    }

    // ==================== 每日新词数 ====================

    private void updateNewWordsDisplay() {
        tvNewWordsCount.setText(String.valueOf(dailyNewWords));
        btnNewWordsMinus.setAlpha(dailyNewWords <= MIN_NEW_WORDS ? 0.3f : 1.0f);
        btnNewWordsPlus.setAlpha(dailyNewWords >= MAX_NEW_WORDS ? 0.3f : 1.0f);
    }

    // ==================== 防抖 + API 同步 ====================

    private void scheduleDailyWordsUpdate() {
        if (pendingDailyWordsUpdate != null) {
            debounceHandler.removeCallbacks(pendingDailyWordsUpdate);
        }
        pendingDailyWordsUpdate = () -> {
            callUpdatePreference(dailyNewWords, null, null);
            pendingDailyWordsUpdate = null;
        };
        debounceHandler.postDelayed(pendingDailyWordsUpdate, DEBOUNCE_DELAY_MS);
    }

    private void scheduleStudyModeUpdate(String mode) {
        if (pendingStudyModeUpdate != null) {
            debounceHandler.removeCallbacks(pendingStudyModeUpdate);
        }
        pendingStudyModeUpdate = () -> {
            callUpdatePreference(null, mode, null);
            pendingStudyModeUpdate = null;
        };
        debounceHandler.postDelayed(pendingStudyModeUpdate, DEBOUNCE_DELAY_MS);
    }

    private void callUpdatePreference(Integer newWords, String mode, Double retentionTarget) {
        if (userId <= 0)
            return;

        GetDataByThread api = new GetDataByThread("/learning/updatePreference");
        api.updatePreference(new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                if (msg.what == MSG_SUCCESS) {
                    Log.i("SettingActivity", "偏好更新成功");
                } else {
                    Toast.makeText(SettingActivity.this, "同步到服务器失败，已保存到本地", Toast.LENGTH_SHORT).show();
                }
            }
        }, MSG_SUCCESS, MSG_FAILED, userId, newWords, mode, retentionTarget);
    }

    // ==================== 主题模式 ====================

    private void cycleThemeMode() {
        int current = ThemeHelper.getThemeMode(this);
        int next = (current + 1) % 3; // 循环：系统→浅色→深色→系统
        ThemeHelper.setThemeMode(this, next);
        updateThemeModeDisplay();
        // 重建 Activity 以应用新主题
        recreate();
    }

    private void updateThemeModeDisplay() {
        tvThemeMode.setText(ThemeHelper.getThemeModeName(this));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 清理防抖回调
        if (pendingDailyWordsUpdate != null) {
            debounceHandler.removeCallbacks(pendingDailyWordsUpdate);
        }
        if (pendingStudyModeUpdate != null) {
            debounceHandler.removeCallbacks(pendingStudyModeUpdate);
        }
    }
}