package com.deepsleep.memory.sync;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 同步元信息门面（P4）：记录上次同步成功时间、上次任务拉取时间与最近失败原因。
 *
 * <p>这些值只服务于可观测性（角标 / 排查），读失败不影响主流程，因此对外接口全部容错。</p>
 */
public final class SyncMetaStore {

    private static final String TAG = "SyncMetaStore";

    private SyncMetaStore() {
    }

    @Nullable
    public static SyncMetaEntity load(@NonNull Context context, int userId, @NonNull String planId) {
        if (userId <= 0) {
            return null;
        }
        try {
            return MemoryLocalDatabase.getInstance(context).syncMetaDao().find(userId, planId);
        } catch (Exception e) {
            Log.w(TAG, "读取同步元信息失败", e);
            return null;
        }
    }

    /** 出站队列本轮有成功发送后调用 */
    public static void touchSync(@NonNull Context context, int userId, @NonNull String planId) {
        if (userId <= 0) {
            return;
        }
        try {
            SyncMetaDao dao = MemoryLocalDatabase.getInstance(context).syncMetaDao();
            ensureRow(dao, userId, planId);
            dao.touchLastSync(userId, planId, System.currentTimeMillis());
        } catch (Exception e) {
            Log.w(TAG, "写入同步时间失败", e);
        }
    }

    /** 每次成功拉取今日任务后调用（节流与"数据新鲜度"展示用） */
    public static void touchTaskPull(@NonNull Context context, int userId, @NonNull String planId) {
        if (userId <= 0) {
            return;
        }
        try {
            SyncMetaDao dao = MemoryLocalDatabase.getInstance(context).syncMetaDao();
            ensureRow(dao, userId, planId);
            dao.touchLastTaskPull(userId, planId, System.currentTimeMillis());
        } catch (Exception e) {
            Log.w(TAG, "写入任务拉取时间失败", e);
        }
    }

    /** 记录最近一次失败原因（死信排查用） */
    public static void setError(@NonNull Context context, int userId, @NonNull String planId,
            @Nullable String error) {
        if (userId <= 0 || error == null || error.isEmpty()) {
            return;
        }
        try {
            SyncMetaDao dao = MemoryLocalDatabase.getInstance(context).syncMetaDao();
            SyncMetaEntity meta = dao.find(userId, planId);
            if (meta == null) {
                meta = new SyncMetaEntity();
                meta.userId = userId;
                meta.planId = planId;
            }
            meta.lastError = error;
            dao.upsert(meta);
        } catch (Exception e) {
            Log.w(TAG, "写入同步错误失败", e);
        }
    }

    /** 最近一次同步时间的可读文案（无记录返回 ""） */
    @NonNull
    public static String lastSyncText(@NonNull Context context, int userId, @NonNull String planId) {
        SyncMetaEntity meta = load(context, userId, planId);
        if (meta == null || meta.lastSyncAtEpochMs <= 0) {
            return "";
        }
        return WordListCacheStore.formatUpdatedAt(meta.lastSyncAtEpochMs);
    }

    private static void ensureRow(@NonNull SyncMetaDao dao, int userId, @NonNull String planId) {
        if (dao.find(userId, planId) == null) {
            SyncMetaEntity meta = new SyncMetaEntity();
            meta.userId = userId;
            meta.planId = planId;
            dao.upsert(meta);
        }
    }
}
