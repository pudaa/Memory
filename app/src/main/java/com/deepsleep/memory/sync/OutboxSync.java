package com.deepsleep.memory.sync;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.deepsleep.memory.network.ApiBridge;
import com.deepsleep.memory.network.MemoryApiClient;
import com.deepsleep.memory.settings.InnerSettingsManager;
import com.deepsleep.memory.ui.main_view.DailyStateManager;

import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.ResponseBody;
import retrofit2.Call;

/**
 * 出站队列补传器（离线优先改造 P1）。
 *
 * <p>替代历史上的 {@code PendingUploadSync}：队列从 SharedPreferences 迁到独立 Room 库，
 * 每条带真实作答时刻（{@code answeredAt}）与计划作用域（{@code planId/planDate}），
 * 按作答顺序回放，服务端据此锚定 FSRS 复习时刻。</p>
 *
 * <p>触发点：进程启动、页面 onResume、今日任务加载成功、**网络恢复回调**（新增）。</p>
 *
 * <p>并发模型：独占一个 {@link HandlerThread}，天然串行；全局 {@link AtomicBoolean} 防重入。
 * 一轮里遇到网络类失败立即中止本轮（fail-fast），避免离线时逐条各等 3 次重试。</p>
 */
public final class OutboxSync {

    private static final String TAG = "OutboxSync";
    private static final int MSG_OK = 1;
    private static final int MSG_FAIL = -1;
    private static final long STALE_SYNCING_RESET_MS = 60_000;

    /** 全局防重入：同一时刻只允许一轮补传 */
    private static final AtomicBoolean SYNCING = new AtomicBoolean(false);
    private static volatile boolean networkCallbackRegistered = false;
    /** 静态主线程 Handler（空队列短路时回调用，不依赖 Round 实例） */
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    /** 补传完成回调（主线程） */
    public interface SyncFinishedListener {
        void onSyncFinished(int syncedCount, int remainCount);
    }

    private OutboxSync() {
    }

    /**
     * 触发补传（幂等：已有补传进行中则直接忽略）。
     *
     * @param context  任意上下文（内部取 applicationContext）
     * @param listener 完成回调（主线程），可为 null
     */
    public static void sync(@Nullable Context context, @Nullable SyncFinishedListener listener) {
        if (context == null) {
            return;
        }
        if (!SYNCING.compareAndSet(false, true)) {
            Log.i(TAG, "已有补传轮次进行中，忽略本次触发");
            return;
        }
        final Context app = context.getApplicationContext();
        final int userId = InnerSettingsManager.getInstance(app).getUserId();
        if (userId <= 0) {
            Log.i(TAG, "未登录（userId=" + userId + "），跳过补传");
            SYNCING.set(false);
            return;
        }
        ensureNetworkTrigger(app);
        OutboxDao dao = MemoryLocalDatabase.getInstance(app).outboxDao();
        int pending = dao.pendingCount(userId);
        int dead = dao.deadCount(userId);
        Log.i(TAG, "补传触发: userId=" + userId + ", 待发送=" + pending + ", 死信=" + dead);
        // 空队列短路（P4 优化）：没有任何待发送/死信条目时不起 worker 线程、不做任何清理，
        // 只按原语义回调一次"成功 0 条"。角标显示依赖的正是这里统计的口径。
        if (pending + dead == 0) {
            Log.i(TAG, "无待同步条目，跳过本轮");
            SYNCING.set(false);
            if (listener != null) {
                MAIN_HANDLER.post(() -> listener.onSyncFinished(0, 0));
            }
            return;
        }
        new Round(app, userId, listener).start();
    }

    /** 网络恢复即自动补传（进程内只注册一次；用 applicationContext，无需注销） */
    public static void ensureNetworkTrigger(@NonNull Context context) {
        if (networkCallbackRegistered) {
            return;
        }
        final Context app = context.getApplicationContext();
        ConnectivityManager cm = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return;
        }
        try {
            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build();
            cm.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    Log.i(TAG, "检测到网络恢复，触发补传");
                    sync(app, null);
                }
            });
            networkCallbackRegistered = true;
            Log.i(TAG, "已注册网络恢复补传回调");
        } catch (Exception e) {
            Log.w(TAG, "注册网络回调失败（不影响手动触发补传）", e);
        }
    }

    /** 当前是否有可用网络（避免离线时发起注定失败的请求） */
    public static boolean isOnline(@NonNull Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        Network network = cm.getActiveNetwork();
        if (network == null) {
            return false;
        }
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    /** 当前待同步条数（角标 / 调试用） */
    public static int pendingCount(@NonNull Context context) {
        int userId = InnerSettingsManager.getInstance(context.getApplicationContext()).getUserId();
        return userId <= 0 ? 0 : OutboxStore.pendingCount(context, userId);
    }

    // ==================== 单轮补传 ====================

    private static final class Round implements Handler.Callback {
        private final Context app;
        private final int userId;
        private final SyncFinishedListener listener;
        private final HandlerThread thread;
        private final Handler handler;
        private final Handler mainHandler = new Handler(Looper.getMainLooper());
        private OutboxEntity current;
        private int syncedCount;
        /** 本轮处理过的条目所属计划（用于 sync_meta 记账） */
        private String lastPlanId;

        Round(Context app, int userId, SyncFinishedListener listener) {
            this.app = app;
            this.userId = userId;
            this.listener = listener;
            this.thread = new HandlerThread("OutboxSync-Worker");
            this.thread.start();
            this.handler = new Handler(thread.getLooper(), this);
        }

        void start() {
            handler.post(() -> {
                // 起轮前清理（B）：复位进程被杀残留的 syncing、清理超期与死信条目。
                // 放在 worker 线程：这两步都是写库操作，不应占用主线程。
                OutboxStore.prepare(app, userId);
                step();
            });
        }

        @Override
        public boolean handleMessage(@NonNull Message msg) {
            OutboxEntity entry = current;
            if (entry == null) {
                handler.post(this::step);
                return true;
            }
            if (msg.what == MSG_OK && msg.obj instanceof String) {
                handleResponse(entry, (String) msg.obj);
            } else {
                // 网络层失败（ApiBridge 已内部退避重试 1s/2s）：退避后整体中止本轮
                Log.w(TAG, "补传网络失败，中止本轮: id=" + entry.id + ", kind=" + entry.kind);
                OutboxStore.markRetry(app, entry, "network failure");
                finish();
                return true;
            }
            handler.post(this::step);
            return true;
        }

        private void step() {
            current = null;
            if (!isOnline(app)) {
                Log.i(TAG, "当前离线，暂停补传（剩余 " + OutboxStore.pendingCount(app, userId) + " 条）");
                finish();
                return;
            }
            OutboxEntity entry = OutboxStore.nextReady(app, userId);
            if (entry == null) {
                finish();
                return;
            }
            current = entry;
            OutboxStore.markSyncing(app, entry);
            Call<ResponseBody> call = buildCall(entry);
            if (call == null) {
                OutboxStore.markDead(app, entry.id, entry.attempts, "unsupported kind: " + entry.kind);
                handler.post(this::step);
                return;
            }
            // 单轮内只重试 1 次：条目本身常驻队列，失败会退避后再来（还有网络回调与手动重试触发），
            // 没有必要在一轮里等满 3 次连接超时
            ApiBridge.enqueue(call, handler, MSG_OK, MSG_FAIL, "Outbox:" + entry.kind, 1);
        }

        /** 按条目类型选择服务端端点（payload 在入队时已构建完成，两条路径共用） */
        @Nullable
        private Call<ResponseBody> buildCall(@NonNull OutboxEntity entry) {
            switch (entry.kind) {
            case OutboxKind.SUBMIT_ANSWER:
                return MemoryApiClient.learningFastFail().submitAnswer(ApiBridge.jsonBody(entry.payloadJson));
            case OutboxKind.LEARNING_LIST_COMPLETION:
                return MemoryApiClient.learningFastFail()
                        .updateLearningListCompletion(ApiBridge.jsonBody(entry.payloadJson));
            case OutboxKind.STUDY_LOG:
                return MemoryApiClient.learningFastFail().uploadWordStudyLog(ApiBridge.jsonBody(entry.payloadJson));
            case OutboxKind.SET_FAVORITE:
                return buildSetFavoriteCall(entry);
            default:
                return null;
            }
        }

        /**
         * 收藏 / 取消收藏：服务端该端点用 header 传参（而非 body），
         * 因此入队时把字段放在 payload JSON 里，这里再还原成 header 调用。
         */
        @Nullable
        private Call<ResponseBody> buildSetFavoriteCall(@NonNull OutboxEntity entry) {
            try {
                JSONObject payload = new JSONObject(entry.payloadJson);
                return MemoryApiClient.learningFastFail().setFavorite(
                        String.valueOf(payload.optInt("userId", entry.userId)),
                        String.valueOf(payload.optInt("wordId", 0)),
                        payload.optString("lexiconId", entry.lexiconId),
                        payload.optString("headWord", ""),
                        payload.optString("isFavorite", "false"));
            } catch (Exception e) {
                Log.w(TAG, "构造收藏请求失败: id=" + entry.id, e);
                return null;
            }
        }

        private void handleResponse(@NonNull OutboxEntity entry, @NonNull String body) {
            String code = "";
            try {
                JSONObject json = new JSONObject(body);
                code = json.optString("code", "");
                if ("200".equals(code)) {
                    applyServerVerdict(entry, json);
                    OutboxStore.delete(app, entry.id);
                    syncedCount++;
                    lastPlanId = entry.planId;
                    Log.i(TAG, "补传成功: id=" + entry.id + ", kind=" + entry.kind + ", 剩余 "
                            + OutboxStore.pendingCount(app, userId));
                    return;
                }
                if ("500".equals(code)) {
                    // 服务端内部错误（如 FSRS 处理失败）：可重试
                    OutboxStore.markRetry(app, entry, "server code 500: " + json.optString("message", ""));
                    return;
                }
                // 其他业务码（无效词书 / 词条不存在等）：永久失败，转 dead 保留可查
                OutboxStore.markDead(app, entry.id, entry.attempts, "server code " + code);
                SyncMetaStore.setError(app, userId, entry.planId, "code " + code);
                Log.w(TAG, "补传永久失败: id=" + entry.id + ", code=" + code);
            } catch (Exception e) {
                OutboxStore.markRetry(app, entry, "parse response failed: " + e.getMessage());
            }
        }

        /** 输入模式：服务端 AI 判定覆盖本地记录（与在线提交后的行为一致） */
        private void applyServerVerdict(@NonNull OutboxEntity entry, @NonNull JSONObject json) {
            if (!OutboxKind.SUBMIT_ANSWER.equals(entry.kind)) {
                return;
            }
            if (!json.has("isCorrect") && !json.has("fsrsScore")) {
                return;
            }
            try {
                DailyStateManager dailyState = new DailyStateManager(app, userId);
                dailyState.loadFromPrefs();
                boolean isCorrect = json.optBoolean("isCorrect", true);
                int fsrsScore = json.optInt("fsrsScore", 0);
                String aiFeedback = json.optString("aiFeedback", "");
                dailyState.markCompletedWithFullResult(wordIdOf(entry), isCorrect, fsrsScore, aiFeedback);
                Log.i(TAG, "服务端判定已回写本地: wordId=" + wordIdOf(entry) + ", score=" + fsrsScore);
            } catch (Exception e) {
                Log.w(TAG, "回写服务端判定失败（不影响补传结果）", e);
            }
        }

        private int wordIdOf(@NonNull OutboxEntity entry) {
            try {
                return new JSONObject(entry.payloadJson).optInt("wordId", 0);
            } catch (Exception e) {
                return 0;
            }
        }

        private void finish() {
            final int remain = OutboxStore.pendingCount(app, userId);
            Log.i(TAG, "本轮补传结束：成功 " + syncedCount + " 条，剩余 " + remain + " 条");
            // 记录同步元信息（角标 / 排查用）：有成功发送才算一次同步
            if (syncedCount > 0) {
                SyncMetaStore.touchSync(app, userId, lastPlanId == null ? "" : lastPlanId);
            }
            SYNCING.set(false);
            if (listener != null) {
                mainHandler.post(() -> listener.onSyncFinished(syncedCount, remain));
            }
            thread.quitSafely();
        }
    }
}
