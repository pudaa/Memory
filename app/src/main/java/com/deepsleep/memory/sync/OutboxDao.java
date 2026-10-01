package com.deepsleep.memory.sync;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/** 出站队列 DAO —— 单行增删改，避免历史上"全量重写 SharedPreferences"的 O(n) 开销 */
@Dao
public interface OutboxDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(OutboxEntity entry);

    /** 取该账号最早的一条待发送（按作答时刻，其次入队顺序） */
    @Query("SELECT * FROM outbox WHERE userId = :userId AND state != 'dead'"
            + " AND nextAttemptAtEpochMs <= :now ORDER BY answeredAtEpochMs ASC, id ASC LIMIT 1")
    OutboxEntity nextReady(int userId, long now);

    @Query("SELECT COUNT(*) FROM outbox WHERE userId = :userId AND state != 'dead'")
    int pendingCount(int userId);

    @Query("SELECT COUNT(*) FROM outbox WHERE userId = :userId AND state = 'dead'")
    int deadCount(int userId);

    @Query("SELECT * FROM outbox WHERE userId = :userId ORDER BY answeredAtEpochMs ASC, id ASC")
    List<OutboxEntity> listForUser(int userId);

    @Query("SELECT * FROM outbox WHERE userId = :userId AND kind = :kind ORDER BY id ASC")
    List<OutboxEntity> listForUserByKind(int userId, String kind);

    @Query("SELECT * FROM outbox WHERE submitId = :submitId LIMIT 1")
    OutboxEntity findBySubmitId(String submitId);

    @Query("DELETE FROM outbox WHERE submitId = :submitId")
    void deleteBySubmitId(String submitId);

    @Query("UPDATE outbox SET state = :state, attempts = :attempts, nextAttemptAtEpochMs = :nextAttemptAt,"
            + " lastError = :error WHERE id = :id")
    void updateStatus(long id, String state, int attempts, long nextAttemptAt, String error);

    @Query("DELETE FROM outbox WHERE id = :id")
    void deleteById(long id);

    /** 清理死信与超期条目（TTL 由调用方给出截止时间） */
    /**
     * 清理超过存活期的条目（待发送与死信同用 TTL）。
     *
     * <p>注意：**不要**无条件删除 {@code state='dead'} —— 死信要保留给用户查看/重试，
     * 由 TTL 与队列上限兜底即可；否则每轮补传都会把死信抹掉，"同步失败"面板将形同虚设。</p>
     */
    @Query("DELETE FROM outbox WHERE userId = :userId AND createdAtEpochMs < :expireBefore")
    int purgeExpired(int userId, long expireBefore);

    /** 超出上限时丢弃最旧的若干条（背压：不阻塞新作答） */
    @Query("DELETE FROM outbox WHERE id IN (SELECT id FROM outbox WHERE userId = :userId ORDER BY id ASC LIMIT :count)")
    int deleteOldest(int userId, int count);

    /** 进程被杀导致残留的 syncing 复位为 pending（超时才复位，避免打断正在进行的发送） */
    @Query("UPDATE outbox SET state = 'pending' WHERE userId = :userId AND state = 'syncing'"
            + " AND nextAttemptAtEpochMs < :before")
    int resetStaleSyncing(int userId, long before);

    /** 死信重试：清空退避与尝试次数，重新排队 */
    @Query("UPDATE outbox SET state = 'pending', attempts = 0, nextAttemptAtEpochMs = 0, lastError = NULL"
            + " WHERE userId = :userId AND state = 'dead'")
    int retryAllDead(int userId);

    /** 清除死信 */
    @Query("DELETE FROM outbox WHERE userId = :userId AND state = 'dead'")
    int deleteAllDead(int userId);

    /** 仅供"清除本机离线数据"使用 */
    @Query("DELETE FROM outbox WHERE userId = :userId")
    void deleteAllForUser(int userId);

    @Query("SELECT COUNT(*) FROM outbox")
    int countAll();
}
