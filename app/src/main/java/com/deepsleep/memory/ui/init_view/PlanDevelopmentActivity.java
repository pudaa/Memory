package com.deepsleep.memory.ui.init_view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.*;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.deepsleep.memory.ui.MainActivity;
import com.deepsleep.memory.R;
import com.deepsleep.memory.network.GetDataByThread;
import com.deepsleep.memory.ui.components.TextCustomNumberPicker;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;

public class PlanDevelopmentActivity extends AppCompatActivity {
    private static final String PREF_NAME = "UserPrefs";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_USER_ID = "userId";

    // 视图
    TextView bookNameTextView;
    TextView tvNewWordsCount, tvMaxReviewCount;
    TextView tvBookWordCount, tvEstimatedDays, tvEstimatedTime;
    TextView tvAdvancedExpand;
    LinearLayout advancedSettingsHeader, advancedSettingsContent;
    TextCustomNumberPicker dailyNewWordsPicker;
    ImageButton btnMaxReviewMinus, btnMaxReviewPlus;
    RadioGroup radioGroup;
    SeekBar seekbarRetention;
    TextView tvRetentionValue;
    TextView preset80, preset90, preset95;
    LinearLayout optionModeChoice, optionModeInput;
    Button startLearnButton;
    ImageButton backButton;

    // 数据
    String bookTitle, bookId;
    int wordCount;
    List<Integer> wordListIds;
    int dailyNewWords = 10;
    int maxReviewWords = 30;
    double retentionTarget = 0.9;
    String studyMode = "choice";
    boolean isAdvancedExpanded = false;

    // 防止联动循环触发
    private boolean isSyncing = false;

    static final int msg_success = 1;
    static final int msg_failed = -1;
    private final MyHandler myHandler = new MyHandler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.plan_development_layout);

        EdgeToEdge.enable(this);

        try {
            bookTitle = getIntent().getStringExtra("bookTitle");
            wordCount = getIntent().getIntExtra("bookWordCount", 0);
            bookId = getIntent().getStringExtra("bookId");
            // 生成单词ID列表
            wordListIds = new ArrayList<>();
            for (int i = 1; i <= wordCount; i++) {
                wordListIds.add(i);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        initView();
        initListeners();
        updatePrediction();
    }

    private void initView() {
        bookNameTextView = findViewById(R.id.book_name);
        tvNewWordsCount = findViewById(R.id.tv_new_words_count);
        tvMaxReviewCount = findViewById(R.id.tv_max_review_count);
        tvBookWordCount = findViewById(R.id.tv_book_word_count);
        tvEstimatedDays = findViewById(R.id.tv_estimated_days);
        tvEstimatedTime = findViewById(R.id.tv_estimated_time);
        tvAdvancedExpand = findViewById(R.id.tv_advanced_expand);
        advancedSettingsHeader = findViewById(R.id.advanced_settings_header);
        advancedSettingsContent = findViewById(R.id.advanced_settings_content);
        dailyNewWordsPicker = findViewById(R.id.daily_new_words_picker);
        btnMaxReviewMinus = findViewById(R.id.btn_max_review_minus);
        btnMaxReviewPlus = findViewById(R.id.btn_max_review_plus);
        radioGroup = findViewById(R.id.radio_group);
        seekbarRetention = findViewById(R.id.seekbar_retention);
        tvRetentionValue = findViewById(R.id.tv_retention_value);
        preset80 = findViewById(R.id.preset_80);
        preset90 = findViewById(R.id.preset_90);
        preset95 = findViewById(R.id.preset_95);
        optionModeChoice = findViewById(R.id.option_mode_choice);
        optionModeInput = findViewById(R.id.option_mode_input);
        startLearnButton = findViewById(R.id.start_word_learning_button);
        backButton = findViewById(R.id.back_button);

        // 设置默认值
        bookNameTextView.setText(bookTitle);
        tvBookWordCount.setText(String.format(Locale.getDefault(), "%d 词", wordCount));
        // SeekBar范围0-27，映射到70%-97%，默认90% → progress=20
        seekbarRetention.setProgress(20);
        tvRetentionValue.setText("90%");
        radioGroup.check(R.id.radio_button1);

        // 初始化 NumberPicker：步长5，范围5~min(wordCount, 100)
        int maxPicker = Math.min(wordCount, 100);
        int pickerCount = (maxPicker - 5) / 5 + 1;
        String[] displayedValues = new String[pickerCount];
        for (int i = 0; i < pickerCount; i++) {
            displayedValues[i] = String.valueOf(5 + i * 5);
        }
        dailyNewWordsPicker.setMinValue(0);
        dailyNewWordsPicker.setMaxValue(pickerCount - 1);
        dailyNewWordsPicker.setDisplayedValues(displayedValues);
        // 默认10 → index = (10-5)/5 = 1
        dailyNewWordsPicker.setValue(1);
        dailyNewWordsPicker.setWrapSelectorWheel(false);

        // 同步初始值
        dailyNewWords = 10;
        maxReviewWords = Math.max(dailyNewWords * 3, 10);
        tvNewWordsCount.setText(String.valueOf(dailyNewWords));
        tvMaxReviewCount.setText(String.valueOf(maxReviewWords));
    }

    private void initListeners() {
        // 返回按钮
        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, BookSelectActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
            finish();
        });

        // 每日新词 NumberPicker 联动
        dailyNewWordsPicker.setOnValueChangedListener((picker, oldVal, newVal) -> {
            if (isSyncing)
                return;
            isSyncing = true;

            int newDaily = 5 + newVal * 5;
            dailyNewWords = newDaily;

            // 双向联动规则：
            // 1) maxReviewWords 不低于 dailyNewWords（至少1倍）
            // 2) maxReviewWords 不超过 dailyNewWords * 5（上限保护）
            if (maxReviewWords < dailyNewWords) {
                maxReviewWords = dailyNewWords;
            } else if (maxReviewWords > dailyNewWords * 5) {
                maxReviewWords = dailyNewWords * 5;
            }
            // 如果 maxReviewWords 在合理范围内（>= dailyNewWords 且 <= dailyNewWords*5），不做修改

            tvNewWordsCount.setText(String.valueOf(dailyNewWords));
            tvMaxReviewCount.setText(String.valueOf(maxReviewWords));
            updatePrediction();
            isSyncing = false;
        });

        // 每日最大复习词数 +/-
        btnMaxReviewMinus.setOnClickListener(v -> {
            if (isSyncing)
                return;
            isSyncing = true;

            if (maxReviewWords > dailyNewWords) {
                maxReviewWords -= 5;
                if (maxReviewWords < dailyNewWords) {
                    maxReviewWords = dailyNewWords;
                }
            }
            tvMaxReviewCount.setText(String.valueOf(maxReviewWords));
            updatePrediction();
            isSyncing = false;
        });

        btnMaxReviewPlus.setOnClickListener(v -> {
            if (isSyncing)
                return;
            isSyncing = true;

            maxReviewWords += 5;
            // 如果新词数小于最大复习词数的1/3，不联动调整新词数
            // 只在 maxReviewWords < dailyNewWords 时才降低新词数（已由上限保护）
            tvMaxReviewCount.setText(String.valueOf(maxReviewWords));
            updatePrediction();
            isSyncing = false;
        });

        // 学习模式选择
        optionModeChoice.setOnClickListener(v -> {
            studyMode = "choice";
            optionModeChoice.setBackgroundResource(R.drawable.option_background_selected);
            optionModeInput.setBackgroundResource(R.drawable.option_background_default);
        });

        optionModeInput.setOnClickListener(v -> {
            studyMode = "input";
            optionModeInput.setBackgroundResource(R.drawable.option_background_selected);
            optionModeChoice.setBackgroundResource(R.drawable.option_background_default);
        });

        // 高级设置展开/折叠
        advancedSettingsHeader.setOnClickListener(v -> {
            isAdvancedExpanded = !isAdvancedExpanded;
            advancedSettingsContent.setVisibility(isAdvancedExpanded ? View.VISIBLE : View.GONE);
            tvAdvancedExpand.setText(isAdvancedExpanded ? "收起 <" : "展开 >");
        });

        // 目标保留率 SeekBar（范围0-27，映射到70%-97%）
        seekbarRetention.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int percent = progress + 70;
                retentionTarget = percent / 100.0;
                tvRetentionValue.setText(String.format(Locale.getDefault(), "%d%%", percent));
                updatePresetHighlight(percent);
                updatePrediction();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        // 预设保留率选项
        preset80.setOnClickListener(v -> setRetentionPreset(80, 0.8));
        preset90.setOnClickListener(v -> setRetentionPreset(90, 0.9));
        preset95.setOnClickListener(v -> setRetentionPreset(95, 0.95));

        // 开始学习按钮
        startLearnButton.setOnClickListener(v -> startLearning());
    }

    private void setRetentionPreset(int presetPercent, double value) {
        retentionTarget = value;
        // SeekBar范围0-27，映射到70%-97%
        seekbarRetention.setProgress(presetPercent - 70);
        tvRetentionValue.setText(String.format(Locale.getDefault(), "%d%%", presetPercent));
        updatePresetHighlight(presetPercent);
        updatePrediction();
    }

    private void updatePresetHighlight(int percent) {
        // 选中：深色背景 + 白色文字；未选中：边框背景 + 深色文字
        int selectedBg = R.drawable.custom_tag_background_selected;
        int defaultBg = R.drawable.custom_tag_background;
        int white = getResources().getColor(R.color.white);
        int darkGray = getResources().getColor(R.color.dark_gray);

        preset80.setBackgroundResource(percent == 80 ? selectedBg : defaultBg);
        preset80.setTextColor(percent == 80 ? white : darkGray);

        preset90.setBackgroundResource(percent == 90 ? selectedBg : defaultBg);
        preset90.setTextColor(percent == 90 ? white : darkGray);

        preset95.setBackgroundResource(percent == 95 ? selectedBg : defaultBg);
        preset95.setTextColor(percent == 95 ? white : darkGray);
    }

    private void updatePrediction() {
        // 简化预测模型：假设每个单词平均需要学习1次 + 复习约3次
        int totalStudyUnits = wordCount * 4;
        int dailyTotal = dailyNewWords + maxReviewWords;
        int estimatedDays = (int) Math.ceil((double) totalStudyUnits / dailyTotal);
        estimatedDays = Math.max(estimatedDays, 1);

        tvEstimatedDays.setText(String.format(Locale.getDefault(), "约 %d 天", estimatedDays));

        // 预估每日学习时间：每个新词约8秒，每个复习词约5秒
        int estimatedMinutes = (int) Math.ceil((dailyNewWords * 8 + maxReviewWords * 5) / 60.0);
        if (estimatedMinutes < 1)
            estimatedMinutes = 1;
        tvEstimatedTime.setText(String.format(Locale.getDefault(), "约 %d 分钟", estimatedMinutes));
    }

    private void startLearning() {
        boolean isSequential = radioGroup.getCheckedRadioButtonId() == R.id.radio_button1;

        // 如果乱序，打乱单词列表
        if (!isSequential) {
            List<Integer> shuffled = new ArrayList<>(wordListIds);
            Collections.shuffle(shuffled);
            wordListIds = shuffled;
        }

        JSONArray wordListIdsArray = new JSONArray(wordListIds);
        SharedPreferences sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int userId = sharedPreferences.getInt(KEY_USER_ID, 0);

        try {
            JSONObject planData = new JSONObject();
            planData.put("dailyNewWords", dailyNewWords);
            planData.put("totalDays", 0); // FSRS 模式下不再预计算
            planData.put("learningDays", 0); // FSRS 模式下不再预计算
            planData.put("isSequential", isSequential);
            planData.put("lexiconId", bookId);
            planData.put("userId", userId);
            planData.put("planStructure", new JSONArray()); // FSRS 不再使用
            planData.put("wordListIds", wordListIdsArray);

            // FSRS 新增参数
            planData.put("fsrsRetentionTarget", retentionTarget);
            planData.put("fsrsMaxReviewWords", maxReviewWords);
            planData.put("studyModePreference", studyMode);

            // 同步每日新词数到本地设置
            com.deepsleep.memory.settings.UserSettingsManager.getInstance(PlanDevelopmentActivity.this)
                    .setDailyNewWords(dailyNewWords);
            com.deepsleep.memory.settings.UserSettingsManager.getInstance(PlanDevelopmentActivity.this)
                    .setStudyMode(studyMode);

            GetDataByThread getDataByThread = new GetDataByThread("/learning/planUpload");
            getDataByThread.planUpload(myHandler, msg_success, msg_failed, planData);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("HandlerLeak")
    class MyHandler extends Handler {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
            case msg_success:
                String result = (String) msg.obj;
                JSONObject responseJson = null;
                try {
                    responseJson = new JSONObject(result);
                    String code = responseJson.getString("code");
                    switch (code) {
                    case "200":
                        startMainActivity();
                        Toast.makeText(PlanDevelopmentActivity.this, "完成计划编制", Toast.LENGTH_SHORT).show();
                        break;
                    case "500":
                        Toast.makeText(PlanDevelopmentActivity.this, "已有相同计划", Toast.LENGTH_SHORT).show();
                        break;
                    }
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                break;
            case msg_failed:
                Toast.makeText(PlanDevelopmentActivity.this, "获取失败", Toast.LENGTH_LONG).show();
                break;
            }
        }
    }

    private void startMainActivity() {
        SharedPreferences sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(KEY_IS_LOGGED_IN, 2);
        editor.apply();
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}