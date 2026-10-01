package com.deepsleep.memory.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 出站队列门面：入队 / 取任务 / 状态流转 / 背压 / 旧队列迁移。
 *
 * <p>UI 层只与本类交互，不直接碰 Room；这也是"所有写操作统一走 Outbox"的落点。</p>
 */
public final class OutboxStore {

    private static final String TAG = "OutboxStore";

    /** 条目存活上限：30 天（超过则视为不再重要，随清理删除） */
    public static final long TTL_MS = 30L * 24 * 3600 * 1000;

    /** 每账号队列上限（背压：超限丢最旧，不阻塞新作答） */
    public static final int MAX_ENTRIES_PER_USER = 5000;

    /** 单条最大重试次数，超过转 dead（保留可查） */
    public static final int MAX_ATTEMPTS = 8;

    /** 历史上待上传队列所在的 SharedPreferences 文件与键后缀（见 DailyStateManager 旧实现） */
    private static final String LEGACY_PREF_NAME = "UserPrefs";
    private static final String LEGACY_KEY_SUFFIX = "_pendingUploads";
    private static final String LEGACY_MIGRATED_SUFFIX = "_pendingUploadsMigrated";

    private OutboxStore() {
    }

    // ==================== 入队 ====================

    /** 入队一条（submitId 非空时按唯一索引覆盖同键旧条目，避免重复推进 FSRS） */
    public static long enqueue(@NonNull Context context, @NonNull OutboxEntity entry) {
        if (entry.createdAtEpochMs <= 0) {
            entry.createdAtEpochMs = System.currentTimeMillis();
        }
        long id = MemoryLocalDatabase.getInstance(context).outboxDao().insert(entry);
        enforceCap(context, entry.userId);
        return id;
    }

    /** 背压：超限丢最旧（并记录日志，便于排查"为什么少了记录"） */
    private static void enforceCap(@NonNull Context context, int userId) {
        OutboxDao dao = MemoryLocalDatabase.getInstance(context).outboxDao();
        int total = dao.pendingCount(userId) + dao.deadCount(userId);
        int overflow = total - MAX_ENTRIES_PER_USER;
        if (overflow > 0) {
            int removed = dao.deleteOldest(userId, overflow);
            Log.w(TAG, "出站队列超限（" + total + " > " + MAX_ENTRIES_PER_USER + "），丢弃最旧 " + removed + " 条");
        }
    }

    // ==================== 取任务与状态流转 ====================

    @Nullable
    public static OutboxEntity nextReady(@NonNull Context context, int userId) {
        return MemoryLocalDatabase.getInstance(context).outboxDao().nextReady(userId, System.currentTimeMillis());
    }

    /**
     * 标记为"发送中"。
     *
     * <p>保留条目已有的 {@code attempts}：此前写 0 会让**发送中窗口**内 DB 显示 0 次尝试，
     * 且进程在发送中被杀会重置重试计数（8 次转死信的保障被削弱）。</p>
     */
    public static void markSyncing(@NonNull Context context, @NonNull OutboxEntity entry) {
        MemoryLocalDatabase.getInstance(context).outboxDao().updateStatus(entry.id, OutboxState.SYNCING,
                entry.attempts, System.currentTimeMillis() + 60_000, "");
    }

    /** 网络类失败：退避后重试 */
    public static void markRetry(@NonNull Context context, @NonNull OutboxEntity entry, @Nullable String error) {
        int attempts = entry.attempts + 1;
        long delay = backoffMs(attempts);
        OutboxDao dao = MemoryLocalDatabase.getInstance(context).outboxDao();
        if (attempts >= MAX_ATTEMPTS) {
            dao.updateStatus(entry.id, OutboxState.DEAD, attempts, Long.MAX_VALUE, safe(error));
            Log.w(TAG, "条目重试超限转 dead: id=" + entry.id + ", kind=" + entry.kind + ", error=" + error);
            // 记录到同步元信息，便于排查"为什么这条一直失败"（角标/诊断用）
            SyncMetaStore.setError(context, entry.userId, entry.planId == null ? "" : entry.planId,
                    "retry exhausted(" + attempts + "): " + safe(error));
            return;
        }
        dao.updateStatus(entry.id, OutboxState.PENDING, attempts, System.currentTimeMillis() + delay, safe(error));
        Log.i(TAG, "条目重试排队: id=" + entry.id + ", attempts=" + attempts + ", delayMs=" + delay);
    }

    /** 业务性永久失败：直接 dead，不再重试 */
    public static void markDead(@NonNull Context context, long id, int attempts, @Nullable String error) {
        MemoryLocalDatabase.getInstance(context).outboxDao().updateStatus(id, OutboxState.DEAD, attempts,
                Long.MAX_VALUE, safe(error));
    }

    public static void delete(@NonNull Context context, long id) {
        MemoryLocalDatabase.getInstance(context).outboxDao().deleteById(id);
    }

    public static void deleteBySubmitId(@NonNull Context context, @Nullable String submitId) {
        if (submitId == null || submitId.isEmpty()) {
            return;
        }
        MemoryLocalDatabase.getInstance(context).outboxDao().deleteBySubmitId(submitId);
    }

    /** 指数退避 + 抖动：1s、2s、4s…上限 1h */
    public static long backoffMs(int attempts) {
        long base = Math.min(1000L << Math.min(attempts, 11), 3600_000L);
        double jitter = 0.8 + Math.random() * 0.4;
        return (long) (base * jitter);
    }

    // ==================== 统计与清理 ====================

    public static int pendingCount(@NonNull Context context, int userId) {
        return MemoryLocalDatabase.getInstance(context).outboxDao().pendingCount(userId);
    }

    public static int deadCount(@NonNull Context context, int userId) {
        return MemoryLocalDatabase.getInstance(context).outboxDao().deadCount(userId);
    }

    /** 死信重试：全部重新排队（用户手动触发） */
    public static int retryAllDead(@NonNull Context context, int userId) {
        int retried = MemoryLocalDatabase.getInstance(context).outboxDao().retryAllDead(userId);
        if (retried > 0) {
            Log.i(TAG, "死信重新入队 " + retried + " 条");
        }
        return retried;
    }

    /** 清除死信 */
    public static int clearAllDead(@NonNull Context context, int userId) {
        return MemoryLocalDatabase.getInstance(context).outboxDao().deleteAllDead(userId);
    }

    /** 启动补传前调用：复位残留 syncing + 清理死信与超期条目 */
    public static void prepare(@NonNull Context context, int userId) {
        OutboxDao dao = MemoryLocalDatabase.getInstance(context).outboxDao();
        dao.resetStaleSyncing(userId, System.currentTimeMillis());
        int purged = dao.purgeExpired(userId, System.currentTimeMillis() - TTL_MS);
        if (purged > 0) {
            Log.i(TAG, "清理超期/死信条目 " + purged + " 条");
        }
    }

    /** 清除本机某账号的离线数据（登出时**不**调用，仅由用户显式触发的清理入口调用） */
    public static void clearForUser(@NonNull Context context, int userId) {
        MemoryLocalDatabase.getInstance(context).outboxDao().deleteAllForUser(userId);
        MemoryLocalDatabase.getInstance(context).taskSnapshotDao().deleteForUser(userId);
        MemoryLocalDatabase.getInstance(context).wordListCacheDao().deleteForUser(userId);
        MemoryLocalDatabase.getInstance(context).syncMetaDao().deleteForUser(userId);
    }

    // ==================== 请求体锚定字段 ====================

    /**
     * 把「作答时间锚定」字段写入 submitAnswer 请求体（在线提交与补传共用）。
     *
     * <p>服务端据此把 FSRS 复习时刻锚定到真实作答时间：断网补传通常发生在次日，
     * 不锚定会导致 due 后移与间隔通胀（服务端实测 S=10、3 天间隔场景 18 → 21 天）。</p>
     */
    public static void putAnchorFields(@NonNull JSONObject body, @Nullable String answeredAt,
            @Nullable String planId, @Nullable String planDate, int studyDay) throws org.json.JSONException {
        if (answeredAt != null && !answeredAt.isEmpty()) {
            body.put("answeredAt", answeredAt);
        }
        if (planId != null && !planId.isEmpty()) {
            body.put("planId", planId);
        }
        if (planDate != null && !planDate.isEmpty()) {
            body.put("planDate", planDate);
        }
        if (studyDay > 0) {
            body.put("studyDay", studyDay);
        }
    }

    // ==================== 旧队列迁移（SharedPreferences → Room） ====================

    /**
     * 把历史上存放在 {@code UserPrefs} 的待上传队列导入 outbox（每账号只做一次）。
     *
     * <p>迁移成功后删除旧键，且**不再回写**——旧实现每次入队/出队都要全量重写该键，
     * 正是本次要消除的 O(n) 主线程开销。迁移失败不影响新队列（旧键保留，下次再试）。</p>
     */
    public static void migrateLegacyQueueIfNeeded(@NonNull Context context, int userId) {
        SharedPreferences sp = context.getSharedPreferences(LEGACY_PREF_NAME, Context.MODE_PRIVATE);
        String migratedKey = userId + LEGACY_MIGRATED_SUFFIX;
        if (sp.getBoolean(migratedKey, false)) {
            return;
        }
        String legacyKey = userId + LEGACY_KEY_SUFFIX;
        String raw = sp.getString(legacyKey, "");
        int migrated = 0;
        if (raw != null && !raw.isEmpty()) {
            try {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    OutboxEntity entry = fromLegacyJson(userId, o);
                    if (entry != null) {
                        MemoryLocalDatabase.getInstance(context).outboxDao().insert(entry);
                        migrated++;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "旧待上传队列迁移失败，保留原数据待下次重试", e);
                return;
            }
        }
        sp.edit().remove(legacyKey).putBoolean(migratedKey, true).apply();
        if (migrated > 0) {
            Log.i(TAG, "旧待上传队列已迁移到 outbox: userId=" + userId + ", 条数=" + migrated);
        }
    }

    /** 旧 SP 条目（键：id/sid/ans_at/plan_id/plan_date/day/lex/word/correct/score/fb/rt/mode/ans/ref/pos）→ OutboxEntity */
    @Nullable
    private static OutboxEntity fromLegacyJson(int userId, @NonNull JSONObject o) {
        try {
            int wordId = o.optInt("id");
            String lexiconId = o.optString("lex", "");
            String word = o.optString("word", "");
            boolean isCorrect = o.optBoolean("correct", false);
            long rt = o.optLong("rt", 0);
            String studyMode = o.optString("mode", "choice");
            String submitId = o.optString("sid", "");
            String answeredAtIso = o.optString("ans_at", "");

            JSONObject payload = new JSONObject();
            payload.put("userId", userId);
            payload.put("wordId", wordId);
            payload.put("lexiconId", lexiconId);
            payload.put("headWord", word);
            payload.put("isCorrect", isCorrect);
            payload.put("responseTimeMs", rt);
            payload.put("studyMode", studyMode);
            if ("input".equals(studyMode)) {
                payload.put("userAnswer", o.optString("ans", ""));
                payload.put("referenceDefinition", o.optString("ref", ""));
                payload.put("word", word);
                payload.put("pos", o.optString("pos", ""));
            }
            if (!submitId.isEmpty()) {
                payload.put("submitId", submitId);
            }
            if (!answeredAtIso.isEmpty()) {
                payload.put("answeredAt", answeredAtIso);
            }

            OutboxEntity entry = new OutboxEntity();
            entry.kind = OutboxKind.SUBMIT_ANSWER;
            entry.userId = userId;
            entry.submitId = submitId.isEmpty() ? null : submitId;
            entry.planId = o.optString("plan_id", "");
            entry.planDate = o.optString("plan_date", "");
            entry.lexiconId = lexiconId;
            entry.studyDay = o.optInt("day", 0);
            entry.answeredAtIso = answeredAtIso;
            // 旧条目可能没有作答时刻（本字段是 2026-09-30 才加的）：用当前时间保证排序稳定，
            // 且不伪造 ISO 时间（服务端会退化为接收时刻，不会算错历史间隔）
            entry.answeredAtEpochMs = System.currentTimeMillis();
            entry.payloadJson = payload.toString();
            entry.createdAtEpochMs = System.currentTimeMillis();
            return entry;
        } catch (Exception e) {
            Log.w(TAG, "解析旧待上传条目失败，跳过", e);
            return null;
        }
    }

    @NonNull
    private static String safe(@Nullable String error) {
        return error == null ? "" : error;
    }
}
