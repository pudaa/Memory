package com.deepsleep.memory.network;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Authenticator;
import okhttp3.FormBody;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.Route;
import okhttp3.logging.HttpLoggingInterceptor;

import retrofit2.Retrofit;
import retrofit2.converter.scalars.ScalarsConverterFactory;

/**
 * 网络层统一入口 —— Retrofit 新栈单例 + 各业务域接口工厂 + 底层 HTTP 专用能力。
 *
 * <p>2026-08 网络层统一改造的最终形态：已吸收原 {@link HttpManager}（底层直连）与
 * {@link GetDataByThread}（兼容转发层）的全部能力，UI 层网络请求一律经本类：
 * <ul>
 *   <li>业务请求：{@link #auth()} / {@link #learning()} / {@link #composition()} /
 *       {@link #conversation()} / {@link #evaluation()} / {@link #pronunciation()} /
 *       {@link #get(Context)}（AI 配置）等域接口 + {@link ApiBridge} 桥接统一 Handler 语义；</li>
 *   <li>底层专用能力（Retrofit 不便表达，原 HttpManager 迁移）：
 *       SSE 流式 {@link #postStream}、WAV 下载 {@link #downloadWav}、multipart 流式文件体
 *       {@link #streamingPart}、直连 {@link #doHttpGetNoPara} / {@link #doHttpPost}、
 *       上传失败原因 {@link #getLastImageUploadError()}；</li>
 *   <li>单一共享连接池 {@link #client()}（本类自持，不再依赖旧栈）。</li>
 * </ul>
 *
 * <p>环境切换感知：每次访问比对 {@link ApiConstants#getBaseUrl()}，变化时在锁内自动重建
 * Retrofit（新栈 MemoryApiClient 检测 baseUrl 变化自动重建，运行期切换立即生效）。
 *
 * <p>401 自动刷新：Authenticator 用 refresh_token 换新 access_token 后重放原请求。
 *
 * <p>应用级 Context 由 {@link NetworkInitializer}（ContentProvider）在进程启动最早阶段注入，
 * 因此各域入口无需调用方传参。
 */
public final class MemoryApiClient {
    private static volatile Retrofit retrofit;
    private static volatile MemoryApi api;
    /** 当前已构建实例对应的 baseUrl（用于环境切换检测） */
    private static volatile String apiBaseUrl;
    /** TokenStore 跨重建复用，避免重复持有 SharedPreferences 引用 */
    private static volatile TokenStore tokenStore;
    /** 应用级 Context（NetworkInitializer 注入；get(Context) 亦可补充设置） */
    private static volatile Context sAppContext;

    // ── 单一共享 OkHttpClient（连接池；原 HttpManager.client() 迁移） ──
    private static volatile OkHttpClient sClient;

    /** 最近一次图片上传失败的可读原因（供 UI 层区分并引导用户），成功时清空 */
    private static volatile String sLastImageUploadError;

    private static final RequestBody EMPTY_BODY = RequestBody.create(new byte[0], null);

    private MemoryApiClient() {
    }

    /** 进程启动最早阶段注入应用级 Context（NetworkInitializer 调用；可在 Application 中补充） */
    public static void setAppContext(Context context) {
        if (context != null && sAppContext == null) {
            sAppContext = context.getApplicationContext();
        }
    }

    /**
     * 共享 OkHttpClient：全应用单一连接池。
     * 超时取"最宽松"档（连接 15s / 读写 120s），覆盖 OCR 图片上传（原 90s）与 TTS 下载。
     */
    public static OkHttpClient client() {
        OkHttpClient c = sClient;
        if (c == null) {
            synchronized (MemoryApiClient.class) {
                c = sClient;
                if (c == null) {
                    c = new OkHttpClient.Builder()
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(120, TimeUnit.SECONDS)
                            .writeTimeout(120, TimeUnit.SECONDS)
                            .retryOnConnectionFailure(true)
                            .build();
                    sClient = c;
                }
            }
        }
        return c;
    }

    /** AI 配置域接口（兼容旧入口） */
    public static MemoryApi get(Context context) {
        setAppContext(context);
        ensureBuilt();
        return api;
    }

    /** 认证 / 用户 / 计划域 */
    public static AuthApi auth() {
        ensureBuilt();
        return retrofit.create(AuthApi.class);
    }

    /** 学习 / FSRS / 听写域 */
    public static LearningApi learning() {
        ensureBuilt();
        return retrofit.create(LearningApi.class);
    }

    /** 作文批改 / 每日阅读 / 收藏域 */
    public static CompositionApi composition() {
        ensureBuilt();
        return retrofit.create(CompositionApi.class);
    }

    /** AI 对话域（SSE 流式除外） */
    public static ConversationApi conversation() {
        ensureBuilt();
        return retrofit.create(ConversationApi.class);
    }

    /** 学情评估域 */
    public static EvaluationApi evaluation() {
        ensureBuilt();
        return retrofit.create(EvaluationApi.class);
    }

    /** 发音评测域 */
    public static PronunciationApi pronunciation() {
        ensureBuilt();
        return retrofit.create(PronunciationApi.class);
    }

    // ── 底层 HTTP 专用能力（原 HttpManager 迁移） ──

    /** 非 multipart 请求附带 Bearer token（与历史 executeWithAuth 语义一致） */
    private static Request.Builder auth(Request.Builder builder) {
        String token = com.deepsleep.memory.settings.InnerSettingsManager.getStoredAccessToken();
        if (token != null && !token.isEmpty()) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    /** 按 UTF-8 解码响应体（旧版 EntityUtils.toString(..., "UTF_8") 语义） */
    private static String readUtf8(Response response) throws IOException {
        ResponseBody body = response.body();
        if (body == null) {
            return null;
        }
        return new String(body.bytes(), StandardCharsets.UTF_8);
    }

    private static RequestBody jsonBody(JSONObject param) {
        return RequestBody.create(param.toString(), MediaType.get("application/json; charset=UTF-8"));
    }

    /**
     * 统一请求执行：方法 + URL + 头 + 体 → Response（调用方负责 close）。
     *
     * @param method          GET / POST / PUT / DELETE
     * @param headers         附加请求头（可为 null）
     * @param body            请求体（GET/DELETE 忽略；POST/PUT 为 null 时发送空体）
     * @param withAuth        是否附带 Authorization: Bearer
     * @param jsonContentType POST 空体时是否补 Content-Type: application/json（历史行为）
     */
    private static Response executeInternal(String method, String url, Map<String, String> headers, RequestBody body,
            boolean withAuth, boolean jsonContentType) throws IOException {
        Request.Builder b = new Request.Builder().url(url);
        if ("POST".equals(method)) {
            b.post(body != null ? body : EMPTY_BODY);
            if (jsonContentType) {
                b.header("Content-Type", "application/json");
            }
        } else if ("PUT".equals(method)) {
            b.put(body != null ? body : EMPTY_BODY);
        } else if ("DELETE".equals(method)) {
            b.delete();
        } else {
            b.get();
        }
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                b.header(e.getKey(), e.getValue());
            }
        }
        return client().newCall(withAuth ? auth(b).build() : b.build()).execute();
    }

    /** 统一文本请求：200 → UTF-8 字符串，否则 null（异常吞掉并打日志） */
    private static String executeText(String method, String url, Map<String, String> headers, RequestBody body,
            boolean withAuth, boolean jsonContentType) {
        try (Response r = executeInternal(method, url, headers, body, withAuth, jsonContentType)) {
            if (r.code() == 200) {
                return readUtf8(r);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /** 将上传异常归类为可读提示 */
    private static String describeUploadError(Exception e) {
        if (e instanceof java.net.SocketTimeoutException) {
            return "连接服务器超时,请检查网络后重试";
        }
        if (e instanceof java.net.ConnectException || e instanceof java.net.UnknownHostException) {
            return "无法连接服务器,请检查网络后重试";
        }
        String m = e.getMessage();
        return m != null && !m.isEmpty() ? "网络错误: " + m : "网络错误,请重试";
    }

    /** 获取最近一次图片上传失败的原因描述；无失败/已成功时为 null */
    public static String getLastImageUploadError() {
        return sLastImageUploadError;
    }

    private static void setLastImageUploadError(String error) {
        sLastImageUploadError = error;
    }

    /** GET 直连：不传参数，只根据 url 地址访问接口 */
    public static String doHttpGetNoPara(String url) {
        return executeText("GET", url, null, null, true, false);
    }

    /** multipart 流式文件体：contentLength 预读一次，writeTo 重新打开流边读边写（保持流式上传） */
    public static RequestBody streamingPart(Context context, Uri uri, String mime) {
        long size = peekSize(context, uri);
        return new RequestBody() {
            @Override
            public MediaType contentType() {
                return MediaType.get(mime);
            }

            @Override
            public long contentLength() {
                return size;
            }

            @Override
            public void writeTo(okio.BufferedSink sink) throws IOException {
                InputStream in = context.getContentResolver().openInputStream(uri);
                if (in == null) {
                    throw new IOException("无法打开文件: " + uri);
                }
                try (InputStream is = in) {
                    byte[] buf = new byte[16 * 1024];
                    int n;
                    while ((n = is.read(buf)) != -1) {
                        sink.write(buf, 0, n);
                    }
                }
            }
        };
    }

    private static long peekSize(Context context, Uri uri) {
        try {
            InputStream in = context.getContentResolver().openInputStream(uri);
            if (in == null) {
                return -1;
            }
            try (InputStream is = in) {
                long avail = is.available();
                if (avail > 0) {
                    return avail;
                }
                long size = 0;
                byte[] probe = new byte[8192];
                int n;
                while ((n = is.read(probe)) != -1) {
                    size += n;
                }
                return size;
            }
        } catch (Exception e) {
            Log.e("MemoryApiClient", "peekSize 失败: " + e.getMessage());
            return -1;
        }
    }

    /**
     * SSE / 流式响应入口：基于共享 OkHttpClient 发起 POST（application/x-www-form-urlencoded），
     * 返回可流式读取的 Response（调用方负责 close）。失败抛 IOException。
     * 与历史裸 HttpURLConnection SSE 一致，不附带 Bearer（身份经调用方 Header 传递）。
     */
    public static Response postStream(String url, Map<String, String> headers, Map<String, String> formParams)
            throws IOException {
        FormBody.Builder fb = new FormBody.Builder();
        if (formParams != null) {
            for (Map.Entry<String, String> e : formParams.entrySet()) {
                fb.add(e.getKey(), e.getValue() != null ? e.getValue() : "");
            }
        }
        Request.Builder b = new Request.Builder().url(url).post(fb.build());
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                b.header(e.getKey(), e.getValue());
            }
        }
        return client().newCall(b.build()).execute();
    }

    /**
     * POST JSON → 下载 WAV 音频到本地文件，返回文件路径（与历史一致：不附带 Bearer）。
     */
    public static String downloadWav(String urlString, JSONObject jsonParam, Context context) {
        try {
            Request.Builder b = new Request.Builder().url(urlString).post(jsonBody(jsonParam));
            try (Response r = client().newCall(b.build()).execute()) {
                if (r.code() == 200 && r.body() != null) {
                    File dir = new File(context.getExternalFilesDir(null), "Audio");
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }
                    File outFile = new File(dir, "tts_welcome_" + System.currentTimeMillis() + ".wav");
                    FileOutputStream fos = new FileOutputStream(outFile);
                    try {
                        fos.write(r.body().bytes());
                    } finally {
                        fos.close();
                    }
                    return outFile.getAbsolutePath();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // ── Retrofit 构建（环境感知） ──

    private static void ensureBuilt() {
        if (sAppContext == null) {
            throw new IllegalStateException("网络层未初始化：NetworkInitializer（ContentProvider）未生效或未调用 setAppContext()");
        }
        String base = ApiConstants.getBaseUrl();
        if (retrofit == null || !base.equals(apiBaseUrl)) {
            synchronized (MemoryApiClient.class) {
                if (retrofit == null || !base.equals(apiBaseUrl)) {
                    if (tokenStore == null) {
                        tokenStore = new TokenStore(sAppContext);
                    }
                    retrofit = build(tokenStore, base);
                    api = retrofit.create(MemoryApi.class);
                    apiBaseUrl = base;
                }
            }
        }
    }

    private static Retrofit build(TokenStore tokenStore, String baseUrl) {
        // 自持共享 OkHttpClient（同一连接池 / Dispatcher），
        // 仅叠加本栈需要的 auth 拦截器 + 401 自动刷新 + 日志
        OkHttpClient bareClient = client();

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
                            .header("refreshToken", refreshToken).post(okhttp3.RequestBody.create(new byte[0]))
                            .build())
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
                .baseUrl(baseUrl + "/")
                .client(client)
                .addConverterFactory(ScalarsConverterFactory.create())
                .build();
    }

    private static int responseCount(Response response) {
        int result = 1;
        while ((response = response.priorResponse()) != null) {
            result++;
        }
        return result;
    }
}
