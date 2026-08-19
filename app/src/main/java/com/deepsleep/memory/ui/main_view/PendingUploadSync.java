package com.deepsleep.memory.ui.main_view;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.deepsleep.memory.network.ApiBridge;
import com.deepsleep.memory.network.MemoryApiClient;
import com.deepsleep.memory.settings.InnerSettingsManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 断网答题补传工具：串行逐条补传 DailyStateManager 中的待上传队列。
 *
 * <p>触发点（网络恢复信号）：
 * <ul>
 * <li>App 启动（MainActivity.onCreate）——进程被杀后重新打开即恢复补传</li>
 * <li>单词页 onResume / 今日任务加载成功（WordLearningFragment）</li>
 * </ul>
 * 队列持久化在 SharedPreferences（按 userId 隔离），进程被杀不丢失；
 * 每条记录携带 submitId 幂等键，服务端据此去重，重复提交不重复推进 FSRS。
 */
public class PendingUploadSync {

    private static final String TAG = "PendingUploadSync";
    private static final long RESPONSE_TIME_CAP_MS = 300_000L;
    private static final int MSG_SUCCESS = 1;
    private static final int MSG_FAIL = -1;
    /** 全局防重入：同一时刻只允许一个补传循环 */
    private static final AtomicBoolean SYNCING = new AtomicBoolean(false);

    /** 补传完成回调（主线程） */
    public interface SyncFinishedListener {
        void onSyncFinished(int syncedCount, int remainCount);
    }

    /**
     * 触发补传（幂等：已有补传进行中则直接忽略）
     *
     * @param context 上下文（自动使用 applicationContext）
     * @param listener 完成回调（主线程），可为 null
     */
    public static void sync(@Nullable Context context, @Nullable SyncFinishedListener listener) {
        if (context == null || !SYNCING.compareAndSet(false, true)) {
            return;
        }
        Context app = context.getApplicationContext();
        int userId = InnerSettingsManager.getInstance(app).getUserId();
        if (userId <= 0) {
            SYNCING.set(false);
            return;
        }
        DailyStateManager dailyState = new DailyStateManager(app, userId);
        dailyState.loadFromPrefs();
        if (dailyState.getPendingUploadCount() == 0) {
            SYNCING.set(false);
            return;
        }
        Log.i(TAG, "开始补传 " + dailyState.getPendingUploadCount() + " 条未同步记录 (userId=" + userId + ")");
        new SyncLoop(app, dailyState, userId, listener).start();
    }

    /** 单次补传循环：维护本轮已尝试集合，逐条串行提交 */
    private static class SyncLoop {
        private final Context context;
        private final DailyStateManager dailyState;
        private final int userId;
        private final SyncFinishedListener listener;
        private final Handler mainHandler = new Handler(Looper.getMainLooper());
        /** 本轮已尝试的 wordId（失败 requeue 后不再重试，防止死循环） */
        private final Set<Integer> triedWordIds = new HashSet<>();
        private int syncedCount = 0;

        SyncLoop(Context context, DailyStateManager dailyState, int userId, SyncFinishedListener listener) {
            this.context = context;
            this.dailyState = dailyState;
            this.userId = userId;
            this.listener = listener;
        }

        void start() {
            submitNext();
        }

        private void submitNext() {
            // 取第一条本轮未尝试过的记录
            final DailyStateManager.PendingUpload current = firstUntried();
            if (current == null) {
                finish();
                return;
            }
            triedWordIds.add(current.wordId);
            // 补传时答题已过去较久：耗时封顶，避免极端值干扰 FSRS 间隔计算
            long cappedRt = Math.min(current.responseTimeMs, RESPONSE_TIME_CAP_MS);

            final Handler handler = new Handler(Looper.getMainLooper()) {
                @Override
                public void handleMessage(@NonNull Message msg) {
                    boolean success = false;
                    if (msg.what == MSG_SUCCESS && msg.obj instanceof String) {
                        try {
                            JSONObject resp = new JSONObject((String) msg.obj);
                            success = "200".equals(resp.optString("code", ""));
                            if (success && WordCard.MODE_INPUT.equals(current.studyMode)) {
                                // 输入模式补传：用服务端判定覆盖本地记录并持久化完整结果
                                boolean serverIsCorrect = resp.optBoolean("isCorrect", current.isCorrect);
                                int fsrsScore = resp.optInt("fsrsScore", current.fsrsScore);
                                String aiFeedback = resp.optString("aiFeedback", current.aiFeedback);
                                dailyState.markCompletedWithFullResult(current.wordId, serverIsCorrect, fsrsScore,
                                        aiFeedback);
                            }
                        } catch (JSONException ignored) {
                        }
                    }
                    if (success) {
                        dailyState.removePendingUpload(current.wordId);
                        syncedCount++;
                        Log.i(TAG, "wordId=" + current.wordId + " 同步成功，剩余 "
                                + dailyState.getPendingUploadCount());
                    } else {
                        // 失败：移到队尾，本轮不再重试，等待下次网络恢复信号
                        dailyState.requeuePendingUpload(current);
                        Log.w(TAG, "wordId=" + current.wordId + " 同步失败，等待下次机会");
                    }
                    // 无论成败都继续下一条
                    submitNext();
                }
            };

            if (WordCard.MODE_INPUT.equals(current.studyMode)) {
                JSONObject j = new JSONObject();
                try {
                    j.put("userId", userId);
                    j.put("wordId", current.wordId);
                    j.put("lexiconId", current.lexiconId);
                    j.put("headWord", current.word);
                    j.put("isCorrect", current.isCorrect);
                    j.put("responseTimeMs", cappedRt);
                    j.put("studyMode", "input");
                    j.put("userAnswer", current.userAnswer);
                    j.put("referenceDefinition", current.referenceDefinition);
                    j.put("word", current.word);
                    j.put("pos", current.pos);
                    if (current.submitId != null && !current.submitId.isEmpty()) {
                        j.put("submitId", current.submitId);
                    }
                } catch (JSONException e) {
                    handler.sendEmptyMessage(MSG_FAIL);
                    return;
                }
                ApiBridge.enqueue(MemoryApiClient.learning().submitAnswer(ApiBridge.jsonBody(j)), handler, MSG_SUCCESS,
                        MSG_FAIL, "SubmitAnswer");
            } else {
                JSONObject j = new JSONObject();
                try {
                    j.put("userId", userId);
                    j.put("wordId", current.wordId);
                    j.put("lexiconId", current.lexiconId);
                    j.put("headWord", current.word);
                    j.put("isCorrect", current.isCorrect);
                    j.put("responseTimeMs", cappedRt);
                    j.put("studyMode", current.studyMode);
                    if (current.submitId != null && !current.submitId.isEmpty()) {
                        j.put("submitId", current.submitId);
                    }
                } catch (JSONException e) {
                    handler.sendEmptyMessage(MSG_FAIL);
                    return;
                }
                ApiBridge.enqueue(MemoryApiClient.learning().submitAnswer(ApiBridge.jsonBody(j)), handler, MSG_SUCCESS,
                        MSG_FAIL, "SubmitAnswer");
            }
        }

        /** 返回第一条本轮尚未尝试过的待上传记录，无则返回 null */
        private DailyStateManager.PendingUpload firstUntried() {
            for (DailyStateManager.PendingUpload u : dailyState.getPendingUploads()) {
                if (!triedWordIds.contains(u.wordId)) {
                    return u;
                }
            }
            return null;
        }

        private void finish() {
            SYNCING.set(false);
            Log.i(TAG, "补传完成：成功 " + syncedCount + " 条，剩余 " + dailyState.getPendingUploadCount() + " 条");
            if (listener != null) {
                mainHandler.post(() -> listener.onSyncFinished(syncedCount, dailyState.getPendingUploadCount()));
            }
        }
    }
}
