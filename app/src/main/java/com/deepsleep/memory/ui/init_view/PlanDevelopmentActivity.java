package com.deepsleep.memory.ui.init_view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import android.os.Handler;
import android.os.Message;
import android.widget.*;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.deepsleep.memory.ui.MainActivity;
import com.deepsleep.memory.R;
import com.deepsleep.memory.network.GetDataByThread;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;

public class PlanDevelopmentActivity extends AppCompatActivity {
    private static final String PREF_NAME = "UserPrefs";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_USER_ID = "userId";
    TextView bookNameTextView, learningPlanNum, bookWordNumText, maximumPressureNumText;
    NumberPicker dailyNewWordsPicker, completionDaysPicker;
    RadioGroup radioGroup;
    Button startLearnButton;
    ImageButton backButton;
    String bookTitle, bookId;
    int wordCount;
    List<Integer> wordListIds;
    static final int msg_success = 1;
    static final int msg_failed = -1;
    private final MyHandler myHandler = new MyHandler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.plan_development_layout);
        bookNameTextView = findViewById(R.id.book_name);
        learningPlanNum = findViewById(R.id.learning_plan_num);
        bookWordNumText = findViewById(R.id.book_word_num);
        maximumPressureNumText = findViewById(R.id.maximum_pressure_num);
        dailyNewWordsPicker = findViewById(R.id.daily_new_words_picker);
        completionDaysPicker = findViewById(R.id.completion_days_picker);
        startLearnButton = findViewById(R.id.start_word_learning_button);
        backButton = findViewById(R.id.back_button);

        EdgeToEdge.enable(this);
        try {
            bookTitle = getIntent().getStringExtra("bookTitle");
            wordCount = getIntent().getIntExtra("bookWordCount", 0); // 词书的单词总数
            bookId = getIntent().getStringExtra("bookId");
            // 根据词书的单词总数来生成所有的单词的列表id，使用列表生成式
            wordListIds = new ArrayList<>();
            for (int i = 1; i <= wordCount; i++) {
                wordListIds.add(i);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        bookNameTextView.setText(bookTitle);
        learningPlanNum.setText("学习计划");
        bookWordNumText.setText(getString(R.string.word_count, wordCount));
        maximumPressureNumText.setText(getString(R.string.maximum_pressure_example));
        radioGroup = findViewById(R.id.radio_group);
        radioGroup.check(R.id.radio_button1);
        initPickers();

        backButton.setOnClickListener(v -> {

            Intent intent = new Intent(this, BookSelectActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
            finish();
        });
        startLearnButton.setOnClickListener(v -> { // 生成学习计划，开始学习
            int dailyNewWords = 5 + dailyNewWordsPicker.getValue() * 5;
            int totalDays = completionDaysPicker.getValue();
            int learningDays = totalDays - 15;
            boolean isSequential = radioGroup.getCheckedRadioButtonId() == R.id.radio_button1; // 是否顺序
            // 根据是否顺序来决定是否打乱wordListIds
            if (!isSequential) {
                Collections.shuffle(wordListIds);
            }
            JSONArray wordListIdsArray = new JSONArray(wordListIds);
            List<List<Integer>> planStructure = generatePlanStructure(learningDays, totalDays); // 生成学习计划结构

            JSONArray jsonArray = new JSONArray();
            for (List<Integer> dayPlan : planStructure) {
                JSONArray dayArray = new JSONArray();
                for (Integer listId : dayPlan) {
                    dayArray.put(listId);
                }
                jsonArray.put(dayArray);
            }
            SharedPreferences sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            int userId = sharedPreferences.getInt(KEY_USER_ID, 0);
            try {
                JSONObject planData = new JSONObject();
                planData.put("dailyNewWords", dailyNewWords);
                planData.put("totalDays", totalDays);
                planData.put("learningDays", learningDays);
                planData.put("isSequential", isSequential);
                planData.put("lexiconId", bookId);
                planData.put("userId", userId);
                planData.put("planStructure", jsonArray);
                planData.put("wordListIds", wordListIdsArray);

                // 同步每日新词数到本地设置
                com.deepsleep.memory.settings.UserSettingsManager.getInstance(PlanDevelopmentActivity.this)
                        .setDailyNewWords(dailyNewWords);

                GetDataByThread getDataByThread = new GetDataByThread("/learning/planUpload");
                getDataByThread.planUpload(myHandler, msg_success, msg_failed, planData);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void initPickers() {
        int maxNewWordsPerDay = Math.min(wordCount, 500);
        int[] newWordsValues = new int[(maxNewWordsPerDay - 5) / 5 + 1];
        for (int i = 0; i < newWordsValues.length; i++) {
            newWordsValues[i] = 5 + i * 5;
        }
        dailyNewWordsPicker.setMinValue(0);
        dailyNewWordsPicker.setMaxValue(newWordsValues.length - 1);
        dailyNewWordsPicker.setDisplayedValues(convertIntArrayToStringArray(newWordsValues));
        dailyNewWordsPicker.setValue(0);

        int minDays = calculateCompletionDays(maxNewWordsPerDay); // 最大新词量对应的最小天数
        int maxDays = calculateCompletionDays(5); // 最小新词量对应的最大天数

        completionDaysPicker.setMinValue(minDays);
        completionDaysPicker.setMaxValue(maxDays);
        completionDaysPicker.setValue(maxDays);

        dailyNewWordsPicker.setOnValueChangedListener((picker, oldVal, newVal) -> {
            int newWordsPerDay = newWordsValues[newVal];
            int days = calculateCompletionDays(newWordsPerDay);
            learningPlanNum.setText(getString(R.string.learning_plan_num, days - 15));
            maximumPressureNumText.setText(getString(R.string.maximum_pressure_num, newWordsPerDay * 6));
            completionDaysPicker.setValue(days);
        });

        completionDaysPicker.setOnValueChangedListener((picker, oldVal, newVal) -> {
            int learningDays = Math.max(newVal - 15, 1); // 保证至少1天学习时间
            int newWordsPerDay = (int) Math.ceil((double) wordCount / learningDays);
            newWordsPerDay = Math.max(5, Math.min(newWordsPerDay, maxNewWordsPerDay));
            int index = (newWordsPerDay - 5) / 5;
            learningPlanNum.setText(getString(R.string.learning_plan_num, learningDays));
            maximumPressureNumText.setText(getString(R.string.maximum_pressure_num, newWordsPerDay * 6));
            dailyNewWordsPicker.setValue(index);
        });
    }

    private int calculateLearningDays(int dailyNewWords) {
        return (int) Math.ceil((double) wordCount / dailyNewWords);
    }

    private int calculateCompletionDays(int dailyNewWords) {
        return calculateLearningDays(dailyNewWords) + 15; // 学习天数 + 复习周期
    }

    private List<List<Integer>> generatePlanStructure(int learningDays, int totalDays) {
        List<List<Integer>> plan = new ArrayList<>();
        Queue<Integer> reviewQueue = new LinkedList<>(); // 复习队列

        for (int day = 1; day <= totalDays; day++) {
            List<Integer> dailyTask = new ArrayList<>();
            if (day <= learningDays) {
                dailyTask.add(day);

                List<Integer> reviewTasks = new ArrayList<>();
                Iterator<Integer> iterator = reviewQueue.iterator();
                while (iterator.hasNext()) {
                    int listNumber = iterator.next();
                    if (listNumber % 15 == day % 15 || listNumber + 1 == day || listNumber + 2 == day
                            || listNumber + 4 == day || listNumber + 7 == day) {
                        reviewTasks.add(listNumber);
                    }
                }

                // 逆序添加复习任务，让后加入的先出现
                for (int i = reviewTasks.size() - 1; i >= 0; i--) {
                    dailyTask.add(reviewTasks.get(i));
                }

                reviewQueue.offer(day);// 添加新的复习任务
            } else {
                dailyTask.add(-1);
                int[] reviewDays = { 1, 2, 4, 7, 15 };
                for (int reviewDay : reviewDays) {
                    // 查找 reviewQueue 中与day-reviewDay 的值相同的元素并填充到dailyTask。如果没有责插入-1
                    if (reviewQueue.contains(day - reviewDay)) {
                        dailyTask.add(day - reviewDay);
                    } else {
                        dailyTask.add(-1);
                    }
                }
            }

            while (!reviewQueue.isEmpty() && reviewQueue.peek() < day - 15) {// 移除过时的复习任务
                reviewQueue.poll();
            }

            plan.add(dailyTask);
        }
        return plan;
    }

    private List<int[]> generateWordRanges(int dailyNewWords, int totalWords) {
        List<int[]> ranges = new ArrayList<>();
        int current = 1;
        while (current <= totalWords) {
            int end = Math.min(current + dailyNewWords - 1, totalWords);
            ranges.add(new int[] { current, end });
            current = end + 1;
        }
        return ranges;
    }

    private String[] convertIntArrayToStringArray(int[] intArray) {
        String[] stringArray = new String[intArray.length];
        for (int i = 0; i < intArray.length; i++) {
            stringArray[i] = String.valueOf(intArray[i]);
        }
        return stringArray;
    }

    private void startMainActivity() {
        SharedPreferences sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(KEY_IS_LOGGED_IN, 2);
        editor.apply();
        // 跳转到主页
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();

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

}