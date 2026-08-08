package com.deepsleep.memory.settings;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class InnerSettingsManager { // 内部信息记录器
    private static final String PREF_NAME = "UserPrefs";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_USER_ID = "userId";

    private static final String KEY_NICK_NAME = "nickName";
    private static final String KEY_USER_NAME = "userName";
    private static final String KEY_AVATAR_URL = "avatarUrl";

    // ── 每日收藏（每日一读）──
    private static final String PREF_DAILY = "DailyFavoritePrefs";
    private static final String KEY_DAILY_FAV_ID = "daily_favorite_id";
    private static final String KEY_DAILY_FAV_DATE = "daily_favorite_date";

    // ── 作文草稿（作文批改）──
    private static final String PREF_COMPOSITION = "CompositionPrefs";
    private static final String KEY_DRAFT_TEXT = "saved_composition_";
    private static final String KEY_DRAFT_TIME = "save_time_";

    // ── 发音每日成绩（发音练习）──
    private static final String PREF_PRONUNCIATION = "pronunciation_daily_scores";
    private static final String KEY_SCORE_PREFIX = "scores_";

    private static InnerSettingsManager instance;
    private final SharedPreferences sharedPreferences;
    private final SharedPreferences dailyPrefs;
    private final SharedPreferences compositionPrefs;
    private final SharedPreferences pronunciationPrefs;
    private final List<UserSettingsManager.OnSettingsChangedListener> listeners = new ArrayList<>();

    private InnerSettingsManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        dailyPrefs = context.getSharedPreferences(PREF_DAILY, Context.MODE_PRIVATE);
        compositionPrefs = context.getSharedPreferences(PREF_COMPOSITION, Context.MODE_PRIVATE);
        pronunciationPrefs = context.getSharedPreferences(PREF_PRONUNCIATION, Context.MODE_PRIVATE);
    }

    public static synchronized InnerSettingsManager getInstance(Context context) {
        if (instance == null) {
            instance = new InnerSettingsManager(context);
        }
        return instance;
    }

    // 设置相关方法
    public void setLoggedIn(int isLoggedIn) {
        sharedPreferences.edit().putInt(KEY_IS_LOGGED_IN, isLoggedIn).apply();
    }
    public void setUserId(int userId) {
        sharedPreferences.edit().putInt(KEY_USER_ID, userId).apply();
    }
    public void setNickName(String nickName) {
        sharedPreferences.edit().putString(KEY_NICK_NAME, nickName).apply();
    }
    public void setUserName(String userName) {
        sharedPreferences.edit().putString(KEY_USER_NAME, userName).apply();
    }
    public void setAvatarUrl(String avatarUrl) {
        sharedPreferences.edit().putString(KEY_AVATAR_URL, avatarUrl).apply();
    }
    public void clear() {
        sharedPreferences.edit().clear().apply();
    }
    private void saveLoginStatus(int isLoggedIn, int userId, String nickName, String userName, String avatarUrl) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(KEY_IS_LOGGED_IN, isLoggedIn);
        editor.putInt(KEY_USER_ID, userId);
        editor.putString(KEY_NICK_NAME, nickName);
        editor.putString(KEY_USER_NAME, userName);
        editor.putString(KEY_AVATAR_URL, avatarUrl);
        editor.apply();
    }
    // 获取设置方法
    public int isLoggedIn() {
        return sharedPreferences.getInt(KEY_IS_LOGGED_IN, 0);
    }
    public int getUserId() {
        return sharedPreferences.getInt(KEY_USER_ID, 0);
    }
    public String getNickName() {
        return sharedPreferences.getString(KEY_NICK_NAME, "");
    }
    public String getUserName() {
        return sharedPreferences.getString(KEY_USER_NAME, "");
    }
    public String getAvatarUrl() {
        return sharedPreferences.getString(KEY_AVATAR_URL, "");
    }

    // ==================== 每日收藏（每日一读模块，按 userId 隔离） ====================

    /** 获取今日收藏的文章 ID（无则返回 -1） */
    public long getDailyFavoriteId(int userId) {
        return dailyPrefs.getLong(KEY_DAILY_FAV_ID + "_" + userId, -1);
    }

    /** 获取今日收藏的日期（yyyy-MM-dd，无则返回 ""） */
    public String getDailyFavoriteDate(int userId) {
        return dailyPrefs.getString(KEY_DAILY_FAV_DATE + "_" + userId, "");
    }

    /** 保存今日收藏状态 */
    public void saveDailyFavorite(int userId, long favoriteId, String date) {
        dailyPrefs.edit().putLong(KEY_DAILY_FAV_ID + "_" + userId, favoriteId)
                .putString(KEY_DAILY_FAV_DATE + "_" + userId, date).apply();
    }

    /** 清除今日收藏状态 */
    public void clearDailyFavorite(int userId) {
        dailyPrefs.edit().remove(KEY_DAILY_FAV_ID + "_" + userId)
                .remove(KEY_DAILY_FAV_DATE + "_" + userId).apply();
    }

    // ==================== 作文草稿（作文批改模块，按 userId 隔离） ====================

    /** 获取保存的作文草稿内容（无则返回 ""） */
    public String getCompositionDraft(int userId) {
        return compositionPrefs.getString(KEY_DRAFT_TEXT + userId, "");
    }

    /** 获取作文草稿的保存时间戳（无则返回 0） */
    public long getCompositionDraftSaveTime(int userId) {
        return compositionPrefs.getLong(KEY_DRAFT_TIME + userId, 0);
    }

    /** 保存作文草稿 */
    public void saveCompositionDraft(int userId, String text, long saveTime) {
        compositionPrefs.edit().putString(KEY_DRAFT_TEXT + userId, text)
                .putLong(KEY_DRAFT_TIME + userId, saveTime).apply();
    }

    // ==================== 发音每日成绩（发音练习模块，按日期存储） ====================

    /** 保存指定日期的发音成绩（内部 key 为 "scores_yyyy-MM-dd"） */
    public void savePronunciationScores(String date, String json) {
        pronunciationPrefs.edit().putString(KEY_SCORE_PREFIX + date, json).apply();
    }

    /** 读取指定日期的发音成绩（无则返回 null） */
    public String getPronunciationScores(String date) {
        return pronunciationPrefs.getString(KEY_SCORE_PREFIX + date, null);
    }

    /** 获取所有已保存发音成绩的日期集合（yyyy-MM-dd） */
    public Set<String> getPronunciationScoreDates() {
        Set<String> dates = new HashSet<>();
        for (String key : pronunciationPrefs.getAll().keySet()) {
            if (key.startsWith(KEY_SCORE_PREFIX)) {
                dates.add(key.substring(KEY_SCORE_PREFIX.length()));
            }
        }
        return dates;
    }

    /** 删除指定日期的发音成绩 */
    public void removePronunciationScores(String date) {
        pronunciationPrefs.edit().remove(KEY_SCORE_PREFIX + date).apply();
    }



    // 设置变化监听器接口
    public interface OnSettingsChangedListener {
        void onSettingChanged(String key, Object value);
        void onSettingsReset();
    }

    // 添加监听器
    public void addSettingsChangeListener(UserSettingsManager.OnSettingsChangedListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    // 移除监听器
    public void removeSettingsChangeListener(UserSettingsManager.OnSettingsChangedListener listener) {
        listeners.remove(listener);
    }

    // 通知设置变化
    private void notifySettingsChanged(String key, Object value) {
        for (UserSettingsManager.OnSettingsChangedListener listener : listeners) {
            listener.onSettingChanged(key, value);
        }
    }

    // 通知设置重置
    private void notifySettingsReset() {
        for (UserSettingsManager.OnSettingsChangedListener listener : listeners) {
            listener.onSettingsReset();
        }
    }

}