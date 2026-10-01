package com.deepsleep.memory.network;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.deepsleep.memory.sync.OutboxSync;

/**
 * 网络层静态初始化器：以 ContentProvider 形式注册，onCreate 在 Application.onCreate 之前执行，
 * 保证网络层（MemoryApiClient / TokenStore）任何时刻都能拿到应用级 Context，无需调用方传参。
 *
 * <p>同时在这里注册「网络恢复 → 补传」回调：兜底队列的触发不能只挂在学习页的生命周期上
 * （实测用户停在"个人词书/计划"等页面时网络恢复，学习页不 resume，补传就不会发生）。</p>
 */
public final class NetworkInitializer extends ContentProvider {

    private static final String TAG = "NetworkInitializer";

    @Override
    public boolean onCreate() {
        MemoryApiClient.setAppContext(getContext());
        try {
            OutboxSync.ensureNetworkTrigger(getContext());
        } catch (Exception e) {
            Log.w(TAG, "注册网络恢复补传回调失败（不影响手动触发）", e);
        }
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
            @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
            @Nullable String[] selectionArgs) {
        return 0;
    }
}