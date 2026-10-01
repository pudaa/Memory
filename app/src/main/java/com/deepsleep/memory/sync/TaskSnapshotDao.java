package com.deepsleep.memory.sync;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

/** 每日任务快照 DAO（P2 使用，表随 P1 一次建好，避免后续再迁移） */
@Dao
public interface TaskSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(TaskSnapshotEntity snapshot);

    @Query("SELECT * FROM task_snapshot WHERE userId = :userId AND planId = :planId LIMIT 1")
    TaskSnapshotEntity find(int userId, String planId);

    /** 该账号最近一次快照（离线冷启动时 planId 未知的兜底路径） */
    @Query("SELECT * FROM task_snapshot WHERE userId = :userId ORDER BY fetchedAtEpochMs DESC LIMIT 1")
    TaskSnapshotEntity findLatest(int userId);

    @Query("DELETE FROM task_snapshot WHERE userId = :userId")
    void deleteForUser(int userId);
}
