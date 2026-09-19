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
     * 流式 PCM 合成入口：POST JSON，返回可**边读边播**的 Response（调用方负责 close）。
     *
     * 对应后端的 `/tts/synthesize-stream`（MemoryServer 透传 MemoryServerTTS 的
     * chunked 裸 PCM，int16LE / 单声道 / 24kHz）。与 {@link #downloadMediaFile}
     * 的关键区别：**不落盘、不缓冲整个响应**，读到多少就能播多少，
     * 因此首声延迟约 0.6s 而不是等整段下载完。
     *
     * @param urlString 完整 URL
     * @param text      待合成文本
     * @param onResponse 拿到 200 响应后回调（可从中读 Content-Type / X-Audio-Sample-Rate
     *                   并消费 body 流）；回调抛出的异常会被包装成 IOException
     */
    public static void streamPcm(String urlString, String text, PcmResponseHandler onResponse)
            throws IOException {
        JSONObject json = new JSONObject();
        try {
            json.put("text", text);
        } catch (Exception e) {
            throw new IOException("构造请求体失败", e);
        }
        Request.Builder b = new Request.Builder().url(urlString).post(jsonBody(json));
        try (Response r = client().newCall(auth(b).build()).execute()) {
            if (r.code() != 200) {
                Log.e("MemoryApiClient", "streamPcm 失败: HTTP " + r.code() + " " + urlString);
                throw new IOException("HTTP " + r.code());
            }
            if (onResponse != null) {
                onResponse.onResponse(r);
            }
        }
    }

    /** 流式 PCM 响应回调 */
    public interface PcmResponseHandler {
        void onResponse(Response response) throws IOException;
    }

    /**
     * 刷新用的单飞锁：与 Retrofit 栈的 Authenticator 共用同一把，
     * 避免两条路径同时用同一个旧 refresh_token 刷新而触发服务端轮换竞态。
     */
    static final Object REFRESH_LOCK = new Object();

    /**
     * 阻塞式刷新 access token（与 Authenticator 共用单飞锁）。
     *
     * <p>为什么需要它：OkHttp 的 Authenticator 只在「OkHttp 发出的请求」收到 401 时触发，
     * 而 {@code MediaPlayer} 有自己的 HTTP 栈、**不在 OkHttp 体系内**，
     * 直连播放遇到 token 过期不会自动刷新，需要调用方显式刷新后重试。
     *
     * @return true 表示刷新成功且新 token 已保存
     */
    public static boolean refreshTokenBlocking() {
        synchronized (REFRESH_LOCK) {
            if (sAppContext == null) {
                Log.e("MemoryApiClient", "refreshTokenBlocking: 网络层未初始化");
                return false;
            }
            TokenStore store = tokenStore();
            String refreshToken = store.refreshToken();
            if (refreshToken == null || refreshToken.isEmpty()) {
                return false;
            }
            try (Response refreshResponse = client().newCall(
                    new Request.Builder().url(ApiConstants.getFullUrl("/auth/refresh"))
                            .header("refreshToken", refreshToken)
                            .post(RequestBody.create(new byte[0]))
                            .build())
                    .execute()) {
                if (!refreshResponse.isSuccessful() || refreshResponse.body() == null) {
                    Log.e("MemoryApiClient", "refreshTokenBlocking 失败: HTTP " + refreshResponse.code());
                    return false;
                }
                JSONObject json = new JSONObject(refreshResponse.body().string());
                if (!"200".equals(json.optString("code"))) {
                    return false;
                }
                String access = json.optString("access_token");
                if (access == null || access.isEmpty()) {
                    return false;
                }
                store.save(access, json.optString("refresh_token", refreshToken));
                Log.i("MemoryApiClient", "refreshTokenBlocking 成功");
                return true;
            } catch (Exception e) {
                Log.e("MemoryApiClient", "refreshTokenBlocking 异常", e);
                return false;
            }
        }
    }

    /**
     * SSE / 流式响应入口：基于共享 OkHttpClient 发起 POST（application/x-www-form-urlencoded），
     * 返回可流式读取的 Response（调用方负责 close）。失败抛 IOException。
     * 服务端全面强制 JWT 后附带 Bearer access token（过期时服务端返回 401，
     * 调用方按失败流处理；正常刷新由 Retrofit 栈 Authenticator 承担）。
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
        return client().newCall(auth(b).build()).execute();
    }

    /**
     * POST JSON → 下载 WAV 音频到本地文件，返回文件路径（附带 Bearer access token）。
     */
    public static String downloadWav(String urlString, JSONObject jsonParam, Context context) {
        try {
            Request.Builder b = new Request.Builder().url(urlString).post(jsonBody(jsonParam));
            try (Response r = client().newCall(auth(b).build()).execute()) {
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

    /**
     * GET + Bearer → 下载远程媒体（听写音频 / 对话音频等）到本地 Audio 目录，返回文件路径。
     * 服务端对 /tts-audio/** 等资源强制 JWT 后，MediaPlayer 无法直接带头播放，
     * 统一走"先鉴权下载到本地、再播本地文件"的模式。失败返回 null。
     */
    public static String downloadMediaFile(String urlString, Context context) {
        try {
            Request.Builder b = new Request.Builder().url(urlString).get();
            try (Response r = client().newCall(auth(b).build()).execute()) {
                if (r.code() == 200 && r.body() != null) {
                    File dir = new File(context.getExternalFilesDir(null), "Audio");
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }
                    String name = Uri.parse(urlString).getLastPathSegment();
                    if (name == null || name.isEmpty()) {
                        name = "media_" + System.currentTimeMillis() + ".wav";
                    }
                    File outFile = new File(dir, System.currentTimeMillis() + "_" + name);
                    FileOutputStream fos = new FileOutputStream(outFile);
                    try {
                        try (InputStream in = r.body().byteStream()) {
                            byte[] buf = new byte[16 * 1024];
                            int n;
                            while ((n = in.read(buf)) != -1) {
                                fos.write(buf, 0, n);
                            }
                        }
                    } finally {
                        fos.close();
                    }
                    return outFile.getAbsolutePath();
                } else {
                    Log.e("MemoryApiClient", "downloadMediaFile 失败: HTTP " + r.code() + " " + urlString);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // ── Retrofit 构建（环境感知） ──

    /** 取得（必要时创建）共享 TokenStore */
    private static TokenStore tokenStore() {
        if (tokenStore == null) {
            synchronized (MemoryApiClient.class) {
                if (tokenStore == null) {
                    tokenStore = new TokenStore(sAppContext);
                }
            }
        }
        return tokenStore;
    }

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

        // 单飞刷新锁：App 启动时多个请求并行 401，若各自触发刷新，同一旧 refreshToken
        // 并发到达服务端会触发轮换竞态（输家被判定复用→整链吊销→本地令牌被清→强制重新登录）。
        // 加锁 + 双检后同一时刻只有一个线程真正刷新，其余线程直接复用刚刷新出的新 token 重放。
        // 注意：用类级 REFRESH_LOCK 而非局部锁 —— MediaPlayer 直连播放走不到 Authenticator，
        // 需要用 refreshTokenBlocking() 手动刷新，两条路径必须共用同一把锁。
        final Object refreshLock = REFRESH_LOCK;

        Authenticator authenticator = (Route route, Response response) -> {
            if (responseCount(response) >= 2) {
                return null;
            }
            synchronized (refreshLock) {
                // 双检：并发场景下其他线程可能已完成刷新——直接用最新 token 重放原请求
                String latestAccess = tokenStore.accessToken();
                String failedToken = bearerTokenOf(response.request().header("Authorization"));
                if (!latestAccess.isEmpty() && !latestAccess.equals(failedToken)) {
                    return response.request().newBuilder()
                            .header("Authorization", "Bearer " + latestAccess).build();
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

    /** 从 Authorization 头提取 Bearer token（无头或格式不符返回空串），供刷新双检比对 */
    private static String bearerTokenOf(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return "";
        }
        return authorization.substring(7).trim();
    }
}
