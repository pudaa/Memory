package com.deepsleep.memory.ui.treasure_view.dictation_view;

import android.os.Handler;
import android.util.Log;

import com.deepsleep.memory.network.ApiBridge;
import com.deepsleep.memory.network.MemoryApiClient;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 听写练习模块 - API 请求辅助类
 * 2026-08 改造后：全部方法委托 Retrofit 新栈（LearningApi 听写子域）经 {@link ApiBridge} 桥接，
 * 统一 Handler 语义（2xx 且非空体 → success；网络层失败/空体按 1s、2s 指数退避重试，全部失败才 fail）。
 * 公开静态方法签名不变，调用方零改动。
 */
public class DictationApiHelper {

    private static final String TAG = "DictationAPI";

    // --- 生成听写任务 ---
    public static void generateTask(final Handler handler, final int msgSuccess, final int msgFailed,
            final int userId, final int count, final int lexiconId) {
        try {
            JSONObject body = new JSONObject();
            body.put("userId", userId);
            body.put("count", count);
            body.put("lexiconId", lexiconId);
            ApiBridge.enqueue(MemoryApiClient.learning().dictationGenerate(ApiBridge.jsonBody(body)),
                    handler, msgSuccess, msgFailed, TAG);
        } catch (Exception e) {
            e.printStackTrace();
            handler.sendEmptyMessage(msgFailed);
        }
    }

    // --- 查询任务详情（含音频就绪状态） ---
    public static void getTaskDetail(final Handler handler, final int msgSuccess, final int msgFailed,
            final String taskId) {
        ApiBridge.enqueue(MemoryApiClient.learning().dictationDetail(taskId), handler, msgSuccess, msgFailed, TAG);
    }

    // --- 提交听写答案 ---
    public static void submitAnswers(final Handler handler, final int msgSuccess, final int msgFailed,
            final String taskId, final JSONArray answers) {
        try {
            JSONObject body = new JSONObject();
            body.put("taskId", taskId);
            body.put("answers", answers);
            ApiBridge.enqueue(MemoryApiClient.learning().dictationSubmit(ApiBridge.jsonBody(body)),
                    handler, msgSuccess, msgFailed, TAG);
        } catch (Exception e) {
            e.printStackTrace();
            handler.sendEmptyMessage(msgFailed);
        }
    }

    // --- 查询历史记录 ---
    public static void getHistory(final Handler handler, final int msgSuccess, final int msgFailed,
            final int userId, final int page, final int size) {
        ApiBridge.enqueue(MemoryApiClient.learning().dictationHistory(String.valueOf(userId), page, size),
                handler, msgSuccess, msgFailed, TAG);
    }

    // --- 删除听写任务 ---
    public static void deleteTask(final Handler handler, final int msgSuccess, final int msgFailed,
            final int userId, final String taskId) {
        try {
            JSONObject body = new JSONObject();
            body.put("userId", userId);
            body.put("taskId", taskId);
            ApiBridge.enqueue(MemoryApiClient.learning().dictationDelete(ApiBridge.jsonBody(body)),
                    handler, msgSuccess, msgFailed, TAG);
        } catch (Exception e) {
            e.printStackTrace();
            handler.sendEmptyMessage(msgFailed);
        }
    }

    // --- 错词重练 ---
    public static void retryWrongWords(final Handler handler, final int msgSuccess, final int msgFailed,
            final int userId, final String taskId, final int lexiconId) {
        try {
            JSONObject body = new JSONObject();
            body.put("userId", userId);
            body.put("taskId", taskId);
            body.put("lexiconId", lexiconId);
            ApiBridge.enqueue(MemoryApiClient.learning().dictationRetryWrong(ApiBridge.jsonBody(body)),
                    handler, msgSuccess, msgFailed, TAG);
        } catch (Exception e) {
            e.printStackTrace();
            handler.sendEmptyMessage(msgFailed);
        }
    }
}