package com.deepsleep.memory.network;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * 学习 / FSRS / 听写域 Retrofit 接口（含 dictation 子模块）。
 * 与历史 GetDataByThread + DictationApiHelper 的 wire 协议一一对应。
 */
public interface LearningApi {

    /** GET /learning/getTodayTask  Header: userId */
    @GET("learning/getTodayTask")
    Call<ResponseBody> getTodayTask(@Header("userId") String userId);

    /** GET /learning/getLearningPlanDetails  Header: userId */
    @GET("learning/getLearningPlanDetails")
    Call<ResponseBody> getLearningPlanDetails(@Header("userId") String userId);

    /** GET /learning/getUserAllLearningPlans  Header: userId */
    @GET("learning/getUserAllLearningPlans")
    Call<ResponseBody> getUserAllLearningPlans(@Header("userId") String userId);

    /** GET /learning/getSchedulePreview  Header: userId */
    @GET("learning/getSchedulePreview")
    Call<ResponseBody> getSchedulePreview(@Header("userId") String userId);

    /** POST /learning/planUpload  Body: 计划 JSON */
    @POST("learning/planUpload")
    Call<ResponseBody> planUpload(@Body RequestBody body);

    /** POST /learning/submitAnswer  Body: 作答 JSON（选择/输入共用） */
    @POST("learning/submitAnswer")
    Call<ResponseBody> submitAnswer(@Body RequestBody body);

    /** POST /learning/updateLearningListCompletion  Body: JSON */
    @POST("learning/updateLearningListCompletion")
    Call<ResponseBody> updateLearningListCompletion(@Body RequestBody body);

    /** POST /learning/updateWordStudyLog  Body: {userId, wordId, lexiconId, headWord, isMastered}
     *  注意：历史 wire 为 updateWordStudyLog（后端 /learning/updateWordStudyLog），勿写成 uploadWordStudyLog */
    @POST("learning/updateWordStudyLog")
    Call<ResponseBody> uploadWordStudyLog(@Body RequestBody body);

    /** GET /learning/getWeakWords  Header: userId */
    @GET("learning/getWeakWords")
    Call<ResponseBody> getWeakWords(@Header("userId") String userId);

    /** GET /learning/getFavoriteWords  Header: userId */
    @GET("learning/getFavoriteWords")
    Call<ResponseBody> getFavoriteWords(@Header("userId") String userId);

    /** POST /learning/setFavorite  Header: userId, wordId, lexiconId, headWord, isFavorite */
    @POST("learning/setFavorite")
    Call<ResponseBody> setFavorite(@Header("userId") String userId, @Header("wordId") String wordId,
            @Header("lexiconId") String lexiconId, @Header("headWord") String headWord,
            @Header("isFavorite") String isFavorite);

    /** PUT /learning/updatePreference  Body: 偏好 JSON */
    @PUT("learning/updatePreference")
    Call<ResponseBody> updatePreference(@Body RequestBody body);

    // ── 听写 (Dictation) ──

    /** POST /learning/dictation/generate  Body: {userId, count, lexiconId} */
    @POST("learning/dictation/generate")
    Call<ResponseBody> dictationGenerate(@Body RequestBody body);

    /** GET /learning/dictation/{taskId}  Path: taskId */
    @GET("learning/dictation/{taskId}")
    Call<ResponseBody> dictationDetail(@Path("taskId") String taskId);

    /** POST /learning/dictation/submit  Body: {taskId, answers} */
    @POST("learning/dictation/submit")
    Call<ResponseBody> dictationSubmit(@Body RequestBody body);

    /** GET /learning/dictation/history  Header: userId, Query: page, size */
    @GET("learning/dictation/history")
    Call<ResponseBody> dictationHistory(@Header("userId") String userId, @Query("page") int page,
            @Query("size") int size);

    /** POST /learning/dictation/delete  Body: {userId, taskId} */
    @POST("learning/dictation/delete")
    Call<ResponseBody> dictationDelete(@Body RequestBody body);

    /** POST /learning/dictation/retry-wrong  Body: {userId, taskId, lexiconId} */
    @POST("learning/dictation/retry-wrong")
    Call<ResponseBody> dictationRetryWrong(@Body RequestBody body);
}