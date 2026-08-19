package com.deepsleep.memory.network;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

/**
 * Retrofit 新栈接口——AI Provider / 路由配置（AI 配置页专用）。
 *
 * 定位说明：新栈（MemoryApiClient + 各业务域 Retrofit 接口）是网络层现代化改造的目标形态，
 * 老栈（HttpManager/GetDataByThread）将随迁移逐步收敛至此；UI 层经 {@link ApiBridge} 以
 * Handler 回调接入，保持项目既有的 org.json 解析约定不变。
 *
 * 注意：token 刷新不走本接口（拦截器内部用裸 OkHttp Request 调用 /auth/refresh），
 * 因此不再声明 refresh 方法，避免误导。
 */
public interface MemoryApi {
    @GET("api/ai/providers/catalog")
    Call<ResponseBody> getAiProviderCatalog();

    @GET("api/ai/providers/mine")
    Call<ResponseBody> getMyAiProviders();

    @POST("api/ai/providers/mine")
    Call<ResponseBody> saveMyAiProvider(@Body RequestBody body);

    @POST("api/ai/providers/routes")
    Call<ResponseBody> saveTaskRoute(@Body RequestBody body);
}