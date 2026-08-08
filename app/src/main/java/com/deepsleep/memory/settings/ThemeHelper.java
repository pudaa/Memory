package com.deepsleep.memory.settings;

import android.content.Context;
import androidx.appcompat.app.AppCompatDelegate;

/**
 * 主题管理工具类
 * 支持：跟随系统 / 浅色 / 深色 三种模式
 */
public class ThemeHelper {

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    /**
     * 应用主题（应在 Activity.onCreate() 中 super.onCreate() 之前调用）
     */
    public static void applyTheme(Context context) {
        int mode = getThemeMode(context);
        switch (mode) {
            case THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case THEME_SYSTEM:
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    /**
     * 设置并立即应用主题模式（持久化统一由 UserSettingsManager 管理）
     */
    public static void setThemeMode(Context context, int mode) {
        UserSettingsManager.getInstance(context).setThemeMode(mode);
        applyTheme(context);
    }

    /**
     * 获取当前主题模式
     */
    public static int getThemeMode(Context context) {
        return UserSettingsManager.getInstance(context).getThemeMode();
    }

    /**
     * 获取主题模式的可读名称
     */
    public static String getThemeModeName(Context context) {
        switch (getThemeMode(context)) {
            case THEME_LIGHT:  return "浅色";
            case THEME_DARK:   return "深色";
            default:           return "跟随系统";
        }
    }
}
