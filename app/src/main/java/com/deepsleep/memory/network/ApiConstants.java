package com.deepsleep.memory.network;

import com.deepsleep.memory.BuildConfig;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 网络中间件 —— 环境（DEV/TEST/PROD/LOCAL）唯一来源 + 网络异步的统一执行器。
 *
 * 2026-08 网络层统一改造（Apache HttpClient → OkHttp）后的职责：
 * 1. 环境唯一来源：所有栈（HttpManager / GetDataByThread / MemoryApiClient / 各 UI 直连点）
 *    都通过 {@link #getBaseUrl()} / {@link #getFullUrl(String)} 取地址，禁止调用方自行拼接；
 * 2. 环境与地址**全部来自 `local.properties`**（构建期注入 BuildConfig）：
 *    `BACKEND_*_URL` 提供四个环境的地址，`DEFAULT_ENVIRONMENT` 提供默认环境。
 *    中间件内**不硬编码任何地址或默认环境**；
 * 3. 运行期切换环境立即生效：旧栈在调用时解析 URL，新栈（MemoryApiClient）检测 baseUrl 变化自动重建；
 * 4. 异步统一走 {@link #execute(Runnable)}：基于单一共享线程池，替代散落在 UI 层的 new Thread。
 *
 * <h3>四个环境（与 DEV/TEST/PROD 同等对待）</h3>
 * <ul>
 *   <li>{@link Environment#DEV} —— 局域网直连开发机</li>
 *   <li>{@link Environment#TEST} —— 测试隧道（frp）</li>
 *   <li>{@link Environment#PROD} —— 生产</li>
 *   <li>{@link Environment#LOCAL} —— 真机 USB 调试：先执行
 *       {@code adb reverse tcp:8080 tcp:8080}，手机上的 localhost:8080 即转发到
 *       PC 本地后端，不要求同网段。端口需与 `BACKEND_LOCAL_URL` 一致。</li>
 * </ul>
 */
public final class ApiConstants {

    public enum Environment { DEV, TEST, PROD, LOCAL }

    /**
     * 默认环境 —— 取自 `local.properties` 的 `DEFAULT_ENVIRONMENT`（构建期注入），
     * 不再硬编码在中间件里。未配置或写错时回退 TEST。
     */
    private static volatile Environment currentEnv =
            parseEnvironment(BuildConfig.DEFAULT_ENVIRONMENT);

    /** 把配置里的环境名解析成枚举；非法值回退 TEST（不抛异常，避免 App 起不来） */
    private static Environment parseEnvironment(String name) {
        if (name != null) {
            try {
                return Environment.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // 配置写错时回退 TEST，而不是崩溃
            }
        }
        return Environment.TEST;
    }

    /** 网络共享线程池：全部网络 IO（含 SSE 流式、轮询、重试）在此执行 */
    private static final ExecutorService NETWORK_EXECUTOR;

    static {
        final AtomicInteger seq = new AtomicInteger(1);
        NETWORK_EXECUTOR = Executors.newCachedThreadPool(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "memory-net-" + seq.getAndIncrement());
            }
        });
    }

    private ApiConstants() {
    }

    // Base URLs are injected at build time from local.properties (see app/build.gradle).
    // The public repo only ships placeholder values; real endpoints stay local.
    private static final String DEV_BASE_URL = BuildConfig.DEV_BASE_URL;
    private static final String TEST_BASE_URL = BuildConfig.TEST_BASE_URL;
    private static final String PROD_BASE_URL = BuildConfig.PROD_BASE_URL;

    /**
     * 真机 USB 调试地址 —— 同样来自 `local.properties`（`BACKEND_LOCAL_URL`），
     * 与 DEV/TEST/PROD 一套机制，不再硬编码。缺省 {@code http://localhost:8080}
     * （adb reverse 惯用端口）。
     */
    private static final String LOCAL_BASE_URL = BuildConfig.LOCAL_BASE_URL;

    public static String getBaseUrl() {
        switch (currentEnv) {
            case LOCAL:
                // 配置为空/占位时回退 DEV，避免拼出不可用地址
                return (LOCAL_BASE_URL != null && !LOCAL_BASE_URL.isEmpty())
                        ? LOCAL_BASE_URL : DEV_BASE_URL;
            case TEST:
                return TEST_BASE_URL;
            case PROD:
                return PROD_BASE_URL;
            default:
                return DEV_BASE_URL;
        }
    }

    /** 运行时切换环境；各网络路径会自动感知（见类注释第 3 条） */
    public static void setEnvironment(Environment env) {
        currentEnv = env;
    }

    public static Environment getEnvironment() {
        return currentEnv;
    }

    /**
     * 唯一的相对路径 → 完整 URL 拼接入口。
     * 所有调用方必须走这里，禁止手写 {@code getBaseUrl() + "/xxx"} 造成风格分裂。
     *
     * @param relativePath 以 "/" 开头的相对路径（未带 "/" 也会自动补）
     */
    public static String getFullUrl(String relativePath) {
        String path = relativePath;
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return getBaseUrl() + path;
    }

    /**
     * 在共享网络线程池上执行任务。
     * 网络 IO / 轮询 / 流式连接一律走这里，替代散落的 {@code new Thread(...).start()}，
     * 便于统一管控线程数与生命周期。
     */
    public static void execute(Runnable task) {
        NETWORK_EXECUTOR.execute(task);
    }

    /** 共享网络线程池（需要自管 Future / 生命周期时使用） */
    public static ExecutorService executor() {
        return NETWORK_EXECUTOR;
    }
}