package com.deepsleep.memory.ui.treasure_view.dictation_view;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.deepsleep.memory.network.ApiConstants;
import com.deepsleep.memory.network.HttpManager;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 听写练习模块 - API 请求辅助类
 * 遵循项目现有的 GetDataByThread + Handler 异步模式
 */
public class DictationApiHelper {

    private static final String TAG = "DictationAPI";
    private static final String BASE_PATH = "/learning/dictation";

    // --- 生成听写任务 ---
    public static void generateTask(final Handler handler, final int msgSuccess, final int msgFailed,
            final int userId, final int count, final int lexiconId) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("userId", userId);
                body.put("count", count);
                body.put("lexiconId", lexiconId);

                String url = ApiConstants.getBaseUrl() + BASE_PATH + "/generate";
                Log.i(TAG, "generateTask -> " + url + " body=" + body);
                String result = HttpManager.doHttpPost(url, body);
                Log.i(TAG, "generateTask <- " + result);

                sendResult(handler, msgSuccess, msgFailed, result);
            } catch (Exception e) {
                e.printStackTrace();
                handler.sendEmptyMessage(msgFailed);
            }
        }).start();
    }

    // --- 查询任务详情（含音频就绪状态） ---
    public static void getTaskDetail(final Handler handler, final int msgSuccess, final int msgFailed,
            final String taskId) {
        new Thread(() -> {
            try {
                String url = ApiConstants.getBaseUrl() + BASE_PATH + "/" + taskId;
                Log.i(TAG, "getTaskDetail -> " + url);
                String result = HttpManager.doHttpGetNoPara(url);
                Log.i(TAG, "getTaskDetail <- " + result);

                sendResult(handler, msgSuccess, msgFailed, result);
            } catch (Exception e) {
                e.printStackTrace();
                handler.sendEmptyMessage(msgFailed);
            }
        }).start();
    }

    // --- 提交听写答案 ---
    public static void submitAnswers(final Handler handler, final int msgSuccess, final int msgFailed,
            final String taskId, final JSONArray answers) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("taskId", taskId);
                body.put("answers", answers);

                String url = ApiConstants.getBaseUrl() + BASE_PATH + "/submit";
                Log.i(TAG, "submitAnswers -> " + url + " body=" + body);
                String result = HttpManager.doHttpPost(url, body);
                Log.i(TAG, "submitAnswers <- " + result);

                sendResult(handler, msgSuccess, msgFailed, result);
            } catch (Exception e) {
                e.printStackTrace();
                handler.sendEmptyMessage(msgFailed);
            }
        }).start();
    }

    // --- 查询历史记录 ---
    public static void getHistory(final Handler handler, final int msgSuccess, final int msgFailed,
            final int userId, final int page, final int size) {
        new Thread(() -> {
            try {
                String url = ApiConstants.getBaseUrl() + BASE_PATH + "/history?page=" + page + "&size=" + size;
                Log.i(TAG, "getHistory -> " + url);
                String result = HttpManager.doHttpGetOneHeader(url, "userId", String.valueOf(userId));
                Log.i(TAG, "getHistory <- " + result);

                sendResult(handler, msgSuccess, msgFailed, result);
            } catch (Exception e) {
                e.printStackTrace();
                handler.sendEmptyMessage(msgFailed);
            }
        }).start();
    }

    // --- 删除听写任务 ---
    public static void deleteTask(final Handler handler, final int msgSuccess, final int msgFailed,
            final int userId, final String taskId) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("userId", userId);
                body.put("taskId", taskId);

                String url = ApiConstants.getBaseUrl() + BASE_PATH + "/delete";
                Log.i(TAG, "deleteTask -> " + url + " body=" + body);
                String result = HttpManager.doHttpPost(url, body);
                Log.i(TAG, "deleteTask <- " + result);

                sendResult(handler, msgSuccess, msgFailed, result);
            } catch (Exception e) {
                e.printStackTrace();
                handler.sendEmptyMessage(msgFailed);
            }
        }).start();
    }

    // --- 错词重练 ---
    public static void retryWrongWords(final Handler handler, final int msgSuccess, final int msgFailed,
            final int userId, final String taskId, final int lexiconId) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("userId", userId);
                body.put("taskId", taskId);
                body.put("lexiconId", lexiconId);

                String url = ApiConstants.getBaseUrl() + BASE_PATH + "/retry-wrong";
                Log.i(TAG, "retryWrongWords -> " + url + " body=" + body);
                String result = HttpManager.doHttpPost(url, body);
                Log.i(TAG, "retryWrongWords <- " + result);

                sendResult(handler, msgSuccess, msgFailed, result);
            } catch (Exception e) {
                e.printStackTrace();
                handler.sendEmptyMessage(msgFailed);
            }
        }).start();
    }

    /**
     * 统一发送结果到 Handler
     */
    private static void sendResult(Handler handler, int msgSuccess, int msgFailed, String result) {
        if (result != null) {
            Message message = Message.obtain();
            message.what = msgSuccess;
            message.obj = result;
            handler.sendMessage(message);
        } else {
            handler.sendEmptyMessage(msgFailed);
        }
    }
}
