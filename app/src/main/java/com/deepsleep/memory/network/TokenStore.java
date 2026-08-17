package com.deepsleep.memory.network;

import android.content.Context;
import android.content.SharedPreferences;

import com.deepsleep.memory.settings.InnerSettingsManager;

/** Stores only access/refresh tokens locally; provider API keys never enter this class. */
public final class TokenStore {
    private static final String PREF_NAME = "AuthTokens";
    private static final String ACCESS = "accessToken";
    private static final String REFRESH = "refreshToken";

    private final SharedPreferences preferences;
    private final InnerSettingsManager legacySettings;

    public TokenStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        legacySettings = InnerSettingsManager.getInstance(context);
    }

    public synchronized String accessToken() {
        String token = preferences.getString(ACCESS, "");
        return token.isEmpty() ? legacySettings.getAccessToken() : token;
    }

    public synchronized String refreshToken() {
        String token = preferences.getString(REFRESH, "");
        return token.isEmpty() ? legacySettings.getRefreshToken() : token;
    }

    public synchronized void save(String accessToken, String refreshToken) {
        preferences.edit().putString(ACCESS, accessToken).putString(REFRESH, refreshToken).apply();
        legacySettings.setTokens(accessToken, refreshToken, "USER");
    }

    public synchronized void clear() {
        preferences.edit().clear().apply();
        legacySettings.clearTokens();
    }
}
