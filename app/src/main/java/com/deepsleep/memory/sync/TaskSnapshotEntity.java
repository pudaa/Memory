package com.deepsleep.memory.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 每日任务快照（P2）：把服务端 {@code /learning/getTodayTask} 的结果留在本地，
 * 使冷启动断网时也能进入学习（卡片内容来自本地词书库，这里只需要 wordList 与计数）。
 *
 * <p>只保留每个 {@code (userId, planId)} 的最新一份；是否过期由 {@code planDate} 判定 ——
 * **禁止离线跨天**：设备日期晚于 {@code planDate} 时进入只读锁定态，不再产生新作答。</p>
 */
@Entity(tableName = "task_snapshot", primaryKeys = { "userId", "planId" })
public class TaskSnapshotEntity {

    public int userId;

    @NonNull
    public String planId = "";

    @NonNull
    public String lexiconId = "";

    /** 服务端权威日期（yyyy-MM-dd） */
    @NonNull
    public String planDate = "";

    public int studyDay;

    public int newWordCount;

    public int reviewLimit;

    public int reviewsDoneToday;

    /** 服务端原始 wordList（[[wordId, headWord, R, D, S, lastScore], ...]） */
    @NonNull
    public String wordListJson = "[]";

    public long fetchedAtEpochMs;
}
