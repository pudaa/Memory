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
import retrofit2.http.Query;

/**
 * 发音评测域 Retrofit 接口。
 * 与历史 GetDataByThread 的 wire 协议一一对应。
 */
public interface PronunciationApi {

    /** GET /pronunciation/words  Header: userId, Query: wordBookId, phraseCount, sentenceCount */
    @GET("pronunciation/words")
    Call<ResponseBody> words(@Header("userId") String userId, @Query("wordBookId") int wordBookId,
            @Query("phraseCount") int phraseCount, @Query("sentenceCount") int sentenceCount);

    /** POST /pronunciation/correct  multipart：文本字段 referenceText + 音频文件 audio（无 Header） */
    @Multipart
    @POST("pronunciation/correct")
    Call<ResponseBody> correct(@Part("referenceText") RequestBody referenceText, @Part MultipartBody.Part audio);
}