package com.deepsleep.memory.sync;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/** 读缓存 DAO（P3 使用） */
@Dao
public interface WordListCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<WordListCacheEntity> items);

    @Query("SELECT * FROM word_list_cache WHERE userId = :userId AND planId = :planId AND kind = :kind"
            + " ORDER BY sortValue ASC, itemKey ASC")
    List<WordListCacheEntity> list(int userId, String planId, String kind);

    @Query("SELECT COUNT(*) FROM word_list_cache WHERE userId = :userId AND planId = :planId AND kind = :kind")
    int count(int userId, String planId, String kind);

    @Query("DELETE FROM word_list_cache WHERE userId = :userId AND planId = :planId AND kind = :kind")
    void clearKind(int userId, String planId, String kind);

    @Query("DELETE FROM word_list_cache WHERE userId = :userId AND planId = :planId AND kind = :kind"
            + " AND itemKey = :itemKey")
    void removeItem(int userId, String planId, String kind, String itemKey);

    @Query("DELETE FROM word_list_cache WHERE userId = :userId")
    void deleteForUser(int userId);
}
