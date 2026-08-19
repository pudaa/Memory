package com.deepsleep.memory.network;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Query;

/**
 * 学情评估域 Retrofit 接口。
 * 与历史 GetDataByThread 的 wire 协议一一对应。
 */
public interface EvaluationApi {

    /** GET /evaluation/dashboard  Header: userId */
    @GET("evaluation/dashboard")
    Call<ResponseBody> dashboard(@Header("userId") String userId);

    /** GET /evaluation/trend  Header: userId, Query: days */
    @GET("evaluation/trend")
    Call<ResponseBody> trend(@Header("userId") String userId, @Query("days") int days);

    /** GET /evaluation/weeklyReport  Header: userId */
    @GET("evaluation/weeklyReport")
    Call<ResponseBody> weeklyReport(@Header("userId") String userId);

    /** GET /evaluation/aiSuggestion  Header: userId */
    @GET("evaluation/aiSuggestion")
    Call<ResponseBody> aiSuggestion(@Header("userId") String userId);

    /** GET /evaluation/aiSuggestion?refresh=  Header: userId */
    @GET("evaluation/aiSuggestion")
    Call<ResponseBody> aiSuggestionRefresh(@Header("userId") String userId, @Query("refresh") boolean refresh);

    /** GET /evaluation/deepAnalysis  Header: userId */
    @GET("evaluation/deepAnalysis")
    Call<ResponseBody> deepAnalysis(@Header("userId") String userId);

    /** GET /evaluation/weakWords  Header: userId, Query: topN */
    @GET("evaluation/weakWords")
    Call<ResponseBody> weakWords(@Header("userId") String userId, @Query("topN") int topN);

    /** GET /evaluation/criticalWords  Header: userId */
    @GET("evaluation/criticalWords")
    Call<ResponseBody> criticalWords(@Header("userId") String userId);

    /** GET /evaluation/masteryDistribution  Header: userId */
    @GET("evaluation/masteryDistribution")
    Call<ResponseBody> masteryDistribution(@Header("userId") String userId);

    /** GET /evaluation/fsrsTrend  Header: userId, Query: days */
    @GET("evaluation/fsrsTrend")
    Call<ResponseBody> fsrsTrend(@Header("userId") String userId, @Query("days") int days);
}