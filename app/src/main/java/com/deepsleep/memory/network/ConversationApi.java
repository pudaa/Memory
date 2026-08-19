package com.deepsleep.memory.network;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * AI 对话域 Retrofit 接口（SSE 流式 /conversation/stream 除外，仍走 HttpManager.postStream）。
 * 与历史 GetDataByThread 的 wire 协议一一对应。
 */
public interface ConversationApi {

    /** POST /conversation/start  Header: userId（无 body） */
    @POST("conversation/start")
    Call<ResponseBody> start(@Header("userId") String userId);

    /** POST /conversation/message  multipart：Header userId + 文本字段 sessionId、text */
    @Multipart
    @POST("conversation/message")
    Call<ResponseBody> sendText(@Header("userId") String userId, @Part("sessionId") RequestBody sessionId,
            @Part("text") RequestBody text);

    /** POST /conversation/message  multipart：Header userId + 字段 sessionId + 音频文件 audio */
    @Multipart
    @POST("conversation/message")
    Call<ResponseBody> sendAudio(@Header("userId") String userId, @Part("sessionId") RequestBody sessionId,
            @Part MultipartBody.Part audio);

    /** GET /conversation/history  Header: userId, Query: sessionId */
    @GET("conversation/history")
    Call<ResponseBody> history(@Header("userId") String userId, @Query("sessionId") String sessionId);

    /** GET /conversation/last  Header: userId */
    @GET("conversation/last")
    Call<ResponseBody> last(@Header("userId") String userId);

    /** GET /conversation/sessions  Header: userId */
    @GET("conversation/sessions")
    Call<ResponseBody> sessions(@Header("userId") String userId);

    /** POST /conversation/delete  Header: userId, Query: sessionId */
    @POST("conversation/delete")
    Call<ResponseBody> delete(@Header("userId") String userId, @Query("sessionId") String sessionId);

    /** GET /conversation/scenarios  Header: userId */
    @GET("conversation/scenarios")
    Call<ResponseBody> scenarios(@Header("userId") String userId);

    /** GET /conversation/scenarios/recommended  Header: userId */
    @GET("conversation/scenarios/recommended")
    Call<ResponseBody> recommendedScenarios(@Header("userId") String userId);

    /** GET /conversation/topics/recommended  Header: userId, Query: count */
    @GET("conversation/topics/recommended")
    Call<ResponseBody> recommendedTopics(@Header("userId") String userId, @Query("count") int count);

    /** POST /conversation/start-scenario  Header: userId, Query: sessionId, scenarioId */
    @POST("conversation/start-scenario")
    Call<ResponseBody> startScenario(@Header("userId") String userId, @Query("sessionId") String sessionId,
            @Query("scenarioId") String scenarioId);

    /** POST /conversation/start-topic  Header: userId, Query: sessionId, topicId */
    @POST("conversation/start-topic")
    Call<ResponseBody> startTopic(@Header("userId") String userId, @Query("sessionId") String sessionId,
            @Query("topicId") String topicId);

    /** POST /conversation/{sessionId}/mode  Header: userId, Path: sessionId, Query: mode */
    @POST("conversation/{sessionId}/mode")
    Call<ResponseBody> switchMode(@Header("userId") String userId, @Path("sessionId") String sessionId,
            @Query("mode") String mode);
}