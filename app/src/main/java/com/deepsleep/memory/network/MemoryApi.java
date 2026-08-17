package com.deepsleep.memory.network;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;

public interface MemoryApi {
    @GET("api/ai/providers/catalog")
    Call<ResponseBody> getAiProviderCatalog();

    @GET("api/ai/providers/mine")
    Call<ResponseBody> getMyAiProviders();

    @POST("api/ai/providers/mine")
    Call<ResponseBody> saveMyAiProvider(@Body RequestBody body);

    @POST("api/ai/providers/routes")
    Call<ResponseBody> saveTaskRoute(@Body RequestBody body);

    @POST("auth/refresh")
    Call<ResponseBody> refresh(@Header("refreshToken") String refreshToken);
}
