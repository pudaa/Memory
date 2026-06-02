package com.deepsleep.memory.settings;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public class InnerSettingsManager { // 内部信息记录器
    private static final String PREF_NAME = "UserPrefs";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_USER_ID = "userId";

    private static final String KEY_NICK_NAME = "nickName";
    private static final String KEY_USER_NAME = "userName";
    private static final String KEY_AVATAR_URL = "avatarUrl";
    private static InnerSettingsManager instance;
    private final SharedPreferences sharedPreferences;
    private final List<UserSettingsManager.OnSettingsChangedListener> listeners = new ArrayList<>();

    private InnerSettingsManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
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