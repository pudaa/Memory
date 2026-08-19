package com.deepsleep.memory.network;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Retrofit 新栈 → 项目统一 Handler+Message 约定的桥接器。
 *
 * 语义与历史 GetDataByThread 完全一致：
 * - 响应成功（HTTP 2xx 且响应体非空，body 按 UTF-8 解码）→ sendMessage(ok, body)，业务方用 org.json 解析；
 * - 网络层失败（onFailure / 非 2xx / 空体）→ 指数退避重试（1s、2s，共 1+2 次尝试），
 *   全部失败才 sendEmptyMessage(fail)；业务错误（非空响应）不重试。
 *
 * 同时提供 JSON / 文本 / multipart 文件 RequestBody 构造，供各业务域 Retrofit 接口调用。
 */
public final class ApiBridge {
    /** 网络层失败后的额外重试次数，共 1 + MAX_RETRIES 次尝试 */
    private static final int MAX_RETRIES = 2;
    /** 首次重试等待时间（指数退避基数：1s、2s） */
    private static final long RETRY_BASE_DELAY_MS = 1000L;

    private ApiBridge() {
    }

    /** 发起 Retrofit 调用，结果经 Handler 回传（含自动重试） */
    public static void enqueue(Call<okhttp3.ResponseBody> call, Handler h, int ok, int fail, String tag) {
        enqueueRetrying(call, h, ok, fail, tag, 0);
    }

    private static void enqueueRetrying(Call<okhttp3.ResponseBody> call, Handler h, int ok, int fail, String tag,
            int attempt) {
        call.enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        String result = new String(response.body().bytes(), StandardCharsets.UTF_8);
                        if (tag != null) {
                            Log.i(tag, "--------" + result);
                        }
                        Message m = Message.obtain();
                        m.what = ok;
                        m.obj = result;
                        h.sendMessage(m);
                        return;
                    }
                    // 非 2xx / 空体：连接层失败语义，走重试
                } catch (Exception e) {
                    if (tag != null)
                        Log.e(tag, "Error: " + e.getMessage());
                    else
                        e.printStackTrace();
                }
                retryOrFail(call, h, ok, fail, tag, attempt);
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                if (tag != null)
                    Log.e(tag, "Error: " + t.getMessage());
                else
                    Log.e("ApiBridge", "request failed", t);
                retryOrFail(call, h, ok, fail, tag, attempt);
            }
        });
    }

    private static void retryOrFail(Call<okhttp3.ResponseBody> call, Handler h, int ok, int fail, String tag,
            int attempt) {
        if (attempt < MAX_RETRIES) {
            long delay = RETRY_BASE_DELAY_MS << attempt; // 1s、2s
            if (tag != null) {
                Log.w(tag, "第 " + (attempt + 1) + " 次尝试失败，" + delay + "ms 后重试");
            }
            ApiConstants.execute(() -> {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                enqueueRetrying(call.clone(), h, ok, fail, tag, attempt + 1);
            });
        } else {
            h.sendEmptyMessage(fail);
        }
    }

    // ── RequestBody 工厂 ──

    /** JSON 字符串 → JSON body */
    public static RequestBody jsonBody(String json) {
        return RequestBody.create(json, MediaType.get("application/json; charset=UTF-8"));
    }

    /** JSONObject → JSON body */
    public static RequestBody jsonBody(JSONObject json) {
        return jsonBody(json.toString());
    }

    /** 纯文本 body（text/plain，作文批改/改昵称用） */
    public static RequestBody textBody(String text) {
        return RequestBody.create(text, MediaType.get("text/plain; charset=UTF-8"));
    }

    /** 表单文本字段（multipart 内嵌字段，如 sessionId/text） */
    public static RequestBody formPart(String value) {
        return RequestBody.create(value != null ? value : "", MediaType.get("text/plain; charset=UTF-8"));
    }

    /**
     * content:// Uri → multipart 文件 Part（流式：contentLength 预读 + 边读边写，避免整图驻留内存）。
     * 用于上传图片 / 音频，字段名与历史一致。
     */
    public static MultipartBody.Part filePart(Context context, Uri uri, String fieldName, String fileName,
            String mime) {
        String realMime = mime != null ? mime : "application/octet-stream";
        RequestBody body = MemoryApiClient.streamingPart(context, uri, realMime);
        return MultipartBody.Part.createFormData(fieldName, fileName != null ? fileName : "file", body);
    }
}