package com.deepsleep.memory.network;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;

/**
 * 认证 / 用户 / 计划域 Retrofit 接口。
 * 与历史 GetDataByThread 的 wire 协议一一对应（业务参数走 Header 的旧约定保持不变）。
 */
public interface AuthApi {

    /** GET /auth/login  Header: phone, password */
    @GET("auth/login")
    Call<ResponseBody> login(@Header("phone") String phone, @Header("password") String password);

    /** POST /auth/register  Header: phone, password, nickname, avatarUrl（无 body） */
    @POST("auth/register")
    Call<ResponseBody> register(@Header("phone") String phone, @Header("password") String password,
            @Header("nickname") String nickname, @Header("avatarUrl") String avatarUrl);

    /** GET /auth/getUserInfo  Header: userId */
    @GET("auth/getUserInfo")
    Call<ResponseBody> getUserInfo(@Header("userId") String userId);

    /** GET /auth/getCurrentPlan  Header: userId */
    @GET("auth/getCurrentPlan")
    Call<ResponseBody> getCurrentPlan(@Header("userId") String userId);

    /** GET /auth/getUserSettings  Header: userId */
    @GET("auth/getUserSettings")
    Call<ResponseBody> getUserSettings(@Header("userId") String userId);

    /** PUT /auth/updateUserSettings  Body: {userId, settings:{...}} */
    @PUT("auth/updateUserSettings")
    Call<ResponseBody> updateUserSettings(@Body RequestBody body);

    /** POST /auth/setPlan  Header: userId, planId（无 body） */
    @POST("auth/setPlan")
    Call<ResponseBody> setPlan(@Header("userId") String userId, @Header("planId") String planId);

    /** POST /auth/uploadUserAvatar  Header: userId + multipart 文件 image */
    @Multipart
    @POST("auth/uploadUserAvatar")
    Call<ResponseBody> uploadUserAvatar(@Header("userId") String userId, @Part MultipartBody.Part image);

    /** POST /auth/updateUserNickname  Header: userId + text/plain 昵称体 */
    @POST("auth/updateUserNickname")
    Call<ResponseBody> updateUserNickname(@Header("userId") String userId, @Body RequestBody nickname);
}