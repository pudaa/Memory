package com.deepsleep.memory.network;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;

/**
 * 作文批改 / 每日阅读 / 收藏域 Retrofit 接口。
 * 与历史 GetDataByThread + DailyReadingFragment 直连的 wire 协议一一对应。
 */
public interface CompositionApi {

    /** POST /composition/extractText  multipart 文件 image（OCR） */
    @Multipart
    @POST("composition/extractText")
    Call<ResponseBody> extractText(@Part MultipartBody.Part image);

    /** POST /composition/correctText  Header: userId + text/plain 作文文本 */
    @POST("composition/correctText")
    Call<ResponseBody> correctText(@Header("userId") String userId, @Body RequestBody text);

    /** GET /composition/records  Header: userId */
    @GET("composition/records")
    Call<ResponseBody> records(@Header("userId") String userId);

    /** GET /composition/dailyReading  Header: userId */
    @GET("composition/dailyReading")
    Call<ResponseBody> dailyReading(@Header("userId") String userId);

    /** GET /composition/generateArticle  Header: userId */
    @GET("composition/generateArticle")
    Call<ResponseBody> generateArticle(@Header("userId") String userId);

    /** POST /composition/dailyReading/favorite  Header: userId + 文章 JSON body */
    @POST("composition/dailyReading/favorite")
    Call<ResponseBody> favoriteArticle(@Header("userId") String userId, @Body RequestBody body);

    /** GET /composition/favorites  Header: userId */
    @GET("composition/favorites")
    Call<ResponseBody> favorites(@Header("userId") String userId);

    /** GET /composition/favorites/{id}  Path: id, Header: userId */
    @GET("composition/favorites/{id}")
    Call<ResponseBody> favoriteDetail(@Path("id") long id, @Header("userId") String userId);

    /** DELETE /composition/favorites/{id}  Path: id, Header: userId */
    @DELETE("composition/favorites/{id}")
    Call<ResponseBody> deleteFavorite(@Path("id") long id, @Header("userId") String userId);

    /** POST /composition/favorites/{id}/view  Path: id（无 header） */
    @POST("composition/favorites/{id}/view")
    Call<ResponseBody> favoriteView(@Path("id") long id);
}