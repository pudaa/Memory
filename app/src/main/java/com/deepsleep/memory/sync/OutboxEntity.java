package com.deepsleep.memory.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 出站队列条目（Room 实体）。
 *
 * <p>替代历史上存放在 {@code UserPrefs} 里的 JSON 字符串队列：那里每次入队/出队都要全量
 * load→parse→save（O(n)，且在答题主线程上），且与登录信息同文件、登出会被一并清除。</p>
 *
 * <p>作用域键是 {@code (userId, planId)}：同一账号切换 on-plan 计划后任务不同，必须分桶；
 * {@code planDate} 是服务端"哪一天"的权威口径（禁止离线跨天的判定依据）。</p>
 */
@Entity(tableName = "outbox", indices = {
        @Index(value = "submitId", unique = true),
        @Index(value = { "userId", "state", "nextAttemptAtEpochMs", "answeredAtEpochMs" })
})
public class OutboxEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** 见 {@link OutboxKind} */
    @NonNull
    public String kind = OutboxKind.SUBMIT_ANSWER;

    public int userId;

    /**
     * 幂等键（服务端据此去重并回放首次结果）。
     *
     * <p>可空：SQLite 的唯一索引允许多个 NULL，因此非作答类条目（如完成上报）不占用幂等键。</p>
     */
    @Nullable
    public String submitId;

    @NonNull
    public String planId = "";

    /** 服务端 {code getTodayTask} 返回的 planDate（yyyy-MM-dd） */
    @NonNull
    public String planDate = "";

    @NonNull
    public String lexiconId = "";

    /** 计划内第几天（完成上报用） */
    public int studyDay;

    /** 作答时刻（epoch ms）：**回放顺序**按它升序，保证 FSRS 状态机按真实作答顺序推进 */
    public long answeredAtEpochMs;

    /** 作答时刻（ISO-8601 带时区），直接作为请求体字段上送，保留作答时的时区偏移 */
    @NonNull
    public String answeredAtIso = "";

    /** 请求体 JSON（入队时即构建完成，补传与正常提交共用同一份，避免两条路径字段漂移） */
    @NonNull
    public String payloadJson = "{}";

    public int attempts;

    /** 下次可尝试时间（epoch ms）：指数退避 */
    public long nextAttemptAtEpochMs;

    /** 见 {@link OutboxState} */
    @NonNull
    public String state = OutboxState.PENDING;

    @Nullable
    public String lastError;

    public long createdAtEpochMs;
}
