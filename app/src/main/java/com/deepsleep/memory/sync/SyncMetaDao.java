package com.deepsleep.memory.sync;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

/** 同步元信息 DAO */
@Dao
public interface SyncMetaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(SyncMetaEntity meta);

    @Query("SELECT * FROM sync_meta WHERE userId = :userId AND planId = :planId LIMIT 1")
    SyncMetaEntity find(int userId, String planId);

    @Query("UPDATE sync_meta SET lastSyncAtEpochMs = :at WHERE userId = :userId AND planId = :planId")
    void touchLastSync(int userId, String planId, long at);

    @Query("UPDATE sync_meta SET lastTaskPullAtEpochMs = :at WHERE userId = :userId AND planId = :planId")
    void touchLastTaskPull(int userId, String planId, long at);

    @Query("DELETE FROM sync_meta WHERE userId = :userId")
    void deleteForUser(int userId);
}
