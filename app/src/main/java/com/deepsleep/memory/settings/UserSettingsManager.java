package com.deepsleep.memory.settings;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public class UserSettingsManager { // 用于用户设置信息获取和设置
    private static final String PREF_NAME = "AppSettings";
    // 键名
    public static final String KEY_IS_SLIDE_BACK = "is_slide_back"; // 用户右滑是否回到上一个卡片
    public static final String KEY_STUDY_MODE = "study_mode"; // 学习模式: "choice"(选择题) / "input"(输入题)
    public static final String KEY_DAILY_NEW_WORDS = "daily_new_words"; // 每日新学单词数
    public static final String KEY_MAX_REVIEW_WORDS = "max_review_words"; // 每日最大复习词数
    public static final String KEY_READER_FONT_SIZE = "reader_font_size"; // 阅读字号
    public static final String KEY_THEME_MODE = "theme_mode"; // 主题模式: 0跟随系统 / 1浅色 / 2深色
    // 默认值
    private static final boolean DEFAULT_IS_SLIDE_BACK = true;
    private static final String DEFAULT_STUDY_MODE = "choice"; // 默认选择题模式
    private static final int DEFAULT_DAILY_NEW_WORDS = 10; // 默认每日10个新词
    private static final int DEFAULT_READER_FONT_SIZE = 19; // 默认阅读字号
    private static final int DEFAULT_THEME_MODE = 0; // 默认跟随系统
    private static UserSettingsManager instance;
    private final SharedPreferences sharedPreferences;
    private final List<OnSettingsChangedListener> listeners = new ArrayList<>();

    private UserSettingsManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized UserSettingsManager getInstance(Context context) { // 单例模式，确保只有一个实例
        if (instance == null) {
            instance = new UserSettingsManager(context);
        }
        return instance;
    }

    // 设置相关方法
    public void setIsSlideBack(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_IS_SLIDE_BACK, enabled).apply();
        notifySettingsChanged(KEY_IS_SLIDE_BACK, enabled);
    }

    // 获取相关方法
    public boolean isSlideBackEnabled() {
        return sharedPreferences.getBoolean(KEY_IS_SLIDE_BACK, DEFAULT_IS_SLIDE_BACK);
    }

    /** 获取学习模式: "choice" 或 "input" */
    public String getStudyMode() {
        return sharedPreferences.getString(KEY_STUDY_MODE, DEFAULT_STUDY_MODE);
    }

    /** 设置学习模式: "choice" 或 "input" */
    public void setStudyMode(String mode) {
        sharedPreferences.edit().putString(KEY_STUDY_MODE, mode).apply();
        notifySettingsChanged(KEY_STUDY_MODE, mode);
    }

    /** 获取每日新词数 */
    public int getDailyNewWords() {
        return sharedPreferences.getInt(KEY_DAILY_NEW_WORDS, DEFAULT_DAILY_NEW_WORDS);
    }

    /** 设置每日新词数 */
    public void setDailyNewWords(int count) {
        sharedPreferences.edit().putInt(KEY_DAILY_NEW_WORDS, count).apply();
        notifySettingsChanged(KEY_DAILY_NEW_WORDS, count);
    }

    /** 获取每日最大复习词数（默认 = 每日新词数×3，至少10） */
    public int getMaxReviewWords() {
        int defaultMax = Math.max(getDailyNewWords() * 3, 10);
        return sharedPreferences.getInt(KEY_MAX_REVIEW_WORDS, defaultMax);
    }

    /** 设置每日最大复习词数 */
    public void setMaxReviewWords(int count) {
        sharedPreferences.edit().putInt(KEY_MAX_REVIEW_WORDS, count).apply();
        notifySettingsChanged(KEY_MAX_REVIEW_WORDS, count);
    }

    /** 获取阅读字号 */
    public int getReaderFontSize() {
        return sharedPreferences.getInt(KEY_READER_FONT_SIZE, DEFAULT_READER_FONT_SIZE);
    }

    /** 设置阅读字号 */
    public void setReaderFontSize(int size) {
        sharedPreferences.edit().putInt(KEY_READER_FONT_SIZE, size).apply();
        notifySettingsChanged(KEY_READER_FONT_SIZE, size);
    }

    /** 获取主题模式: 0跟随系统 / 1浅色 / 2深色 */
    public int getThemeMode() {
        return sharedPreferences.getInt(KEY_THEME_MODE, DEFAULT_THEME_MODE);
    }

    /** 设置主题模式 */
    public void setThemeMode(int mode) {
        sharedPreferences.edit().putInt(KEY_THEME_MODE, mode).apply();
        notifySettingsChanged(KEY_THEME_MODE, mode);
    }

    // 重置相关方法
    public void resetIsSlideBack() {
        setIsSlideBack(DEFAULT_IS_SLIDE_BACK);
        notifySettingsChanged(KEY_IS_SLIDE_BACK, DEFAULT_IS_SLIDE_BACK);
    }

    /** 重置学习模式为默认值 */
    public void resetStudyMode() {
        setStudyMode(DEFAULT_STUDY_MODE);
        notifySettingsChanged(KEY_STUDY_MODE, DEFAULT_STUDY_MODE);
    }

    /** 重置每日新词数为默认值 */
    public void resetDailyNewWords() {
        setDailyNewWords(DEFAULT_DAILY_NEW_WORDS);
        notifySettingsChanged(KEY_DAILY_NEW_WORDS, DEFAULT_DAILY_NEW_WORDS);
    }

    public void resetAllSettings() {
        sharedPreferences.edit().clear().apply();
        notifySettingsReset();
    }

    // 设置变化监听器接口
    public interface OnSettingsChangedListener {
        void onSettingChanged(String key, Object value);

        void onSettingsReset();
    }

    // 添加监听器
    public void addSettingsChangeListener(OnSettingsChangedListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    // 移除监听器
    public void removeSettingsChangeListener(OnSettingsChangedListener listener) {
        listeners.remove(listener);
    }

    // 通知设置变化
    private void notifySettingsChanged(String key, Object value) {
        for (OnSettingsChangedListener listener : listeners) {
            listener.onSettingChanged(key, value);
        }
    }

    // 通知设置重置
    private void notifySettingsReset() {
        for (OnSettingsChangedListener listener : listeners) {
            listener.onSettingsReset();
        }
    }
}