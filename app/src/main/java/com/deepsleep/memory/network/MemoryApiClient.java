package com.deepsleep.memory.network;

import android.content.Context;

import java.util.concurrent.TimeUnit;

import okhttp3.Authenticator;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import okhttp3.logging.HttpLoggingInterceptor;
import org.json.JSONObject;

import retrofit2.Retrofit;
import retrofit2.converter.scalars.ScalarsConverterFactory;

/** Single Retrofit/OkHttp entry point for newly migrated API calls. */
public final class MemoryApiClient {
    private static volatile MemoryApi api;

    private MemoryApiClient() {
    }

    public static MemoryApi get(Context context) {
        if (api == null) {
            synchronized (MemoryApiClient.class) {
                if (api == null) {
                    api = build(context.getApplicationContext());
                }
            }
        }
        return api;
    }

    private static MemoryApi build(Context context) {
        TokenStore tokenStore = new TokenStore(context);
        OkHttpClient bareClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build();

        Interceptor authInterceptor = chain -> {
            Request original = chain.request();
            String token = tokenStore.accessToken();
            if (token.isEmpty() || original.url().encodedPath().endsWith("/auth/refresh")) {
                return chain.proceed(original);
            }
            return chain.proceed(original.newBuilder()
                    .header("Authorization", "Bearer " + token)
                    .build());
        };

        Authenticator authenticator = (Route route, Response response) -> {
            if (responseCount(response) >= 2) {
                return null;
            }
            String refreshToken = tokenStore.refreshToken();
            if (refreshToken.isEmpty()) {
                return null;
            }
            try (okhttp3.Response refreshResponse = bareClient.newCall(
                    new Request.Builder().url(ApiConstants.getFullUrl("/auth/refresh"))
                            .header("refreshToken", refreshToken).post(okhttp3.RequestBody.create(new byte[0])).build())
                    .execute()) {
                if (!refreshResponse.isSuccessful() || refreshResponse.body() == null) {
                    tokenStore.clear();
                    return null;
                }
                JSONObject json = new JSONObject(refreshResponse.body().string());
                if (!"200".equals(json.optString("code"))) {
                    tokenStore.clear();
                    return null;
                }
                String access = json.optString("access_token");
                String refresh = json.optString("refresh_token", refreshToken);
                tokenStore.save(access, refresh);
                return response.request().newBuilder().header("Authorization", "Bearer " + access).build();
            } catch (Exception e) {
                return null;
            }
        };

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BASIC);
        OkHttpClient client = bareClient.newBuilder()
                .addInterceptor(authInterceptor)
                .authenticator(authenticator)
                .addInterceptor(logging)
                .build();

        return new Retrofit.Builder()
                .baseUrl(ApiConstants.getBaseUrl() + "/")
                .client(client)
                .addConverterFactory(ScalarsConverterFactory.create())
                .build()
                .create(MemoryApi.class);
    }

    private static int responseCount(Response response) {
        int result = 1;
        while ((response = response.priorResponse()) != null) {
            result++;
        }
        return result;
    }
}
