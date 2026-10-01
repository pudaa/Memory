package com.deepsleep.memory.sync;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * 每日任务快照（P2）：把 {@code /learning/getTodayTask} 的结果留在本地，
 * 使冷启动断网时也能进入学习（卡片正文来自本地词书库，快照只需 wordList + 计数）。
 *
 * <p><b>禁止离线跨天</b>：快照以服务端 {@code planDate} 为"哪一天"的唯一权威。
 * 设备日期晚于 {@code planDate} 时，快照进入"锁定态"——卡片仍可回看，但不再产生新作答，
 * 避免用户次日联网后同时涌入"离线补学的新词"与"当天的新任务"，也避免作答时间锚点跨天
 * 导致 FSRS 档期错位。</p>
 */
public final class TaskSnapshotStore {

    private static final String TAG = "TaskSnapshotStore";

    private TaskSnapshotStore() {
    }

    /** 保存/覆盖当前计划的今日任务快照 */
    public static void save(@NonNull Context context, @NonNull TaskSnapshotEntity snapshot) {
        if (snapshot.fetchedAtEpochMs <= 0) {
            snapshot.fetchedAtEpochMs = System.currentTimeMillis();
        }
        MemoryLocalDatabase.getInstance(context).taskSnapshotDao().upsert(snapshot);
        Log.i(TAG, "任务快照已保存: planId=" + snapshot.planId + ", planDate=" + snapshot.planDate
                + ", 单词数=" + snapshot.newWordCount + "+复习");
    }

    /**
     * 读取快照。
     *
     * @param preferredPlanId 已知的计划 ID（来自上次联网或本地持久化），为空时回退"该账号最近一次快照"
     */
    @Nullable
    public static TaskSnapshotEntity load(@NonNull Context context, int userId, @Nullable String preferredPlanId) {
        TaskSnapshotDao dao = MemoryLocalDatabase.getInstance(context).taskSnapshotDao();
        if (preferredPlanId != null && !preferredPlanId.isEmpty()) {
            TaskSnapshotEntity snapshot = dao.find(userId, preferredPlanId);
            if (snapshot != null) {
                return snapshot;
            }
        }
        return dao.findLatest(userId);
    }

    /**
     * 快照是否仍属于"今天"（可以离线学习）。
     *
     * <p>设备日期晚于快照日期 → false（锁定态）；设备日期早于或等于快照日期 → true
     * （早于的情况通常是客户端时钟/时区偏差，此时放行比误锁更友好）。</p>
     */
    public static boolean isUsableToday(@Nullable TaskSnapshotEntity snapshot) {
        if (snapshot == null || snapshot.planDate == null || snapshot.planDate.isEmpty()) {
            return false;
        }
        try {
            LocalDate snapshotDate = LocalDate.parse(snapshot.planDate);
            LocalDate deviceDate = LocalDate.now();
            return !deviceDate.isAfter(snapshotDate);
        } catch (DateTimeParseException e) {
            Log.w(TAG, "快照 planDate 无法解析: " + snapshot.planDate);
            return false;
        }
    }

    public static void clearForUser(@NonNull Context context, int userId) {
        MemoryLocalDatabase.getInstance(context).taskSnapshotDao().deleteForUser(userId);
    }
}
