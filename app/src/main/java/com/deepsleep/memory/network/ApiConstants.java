package com.deepsleep.memory.network;

import com.deepsleep.memory.BuildConfig;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 网络中间件 —— 环境（DEV/TEST/PROD）唯一来源 + 网络异步的统一执行器。
 *
 * 2026-08 网络层统一改造（Apache HttpClient → OkHttp）后的职责：
 * 1. 环境唯一来源：所有栈（HttpManager / GetDataByThread / MemoryApiClient / 各 UI 直连点）
 *    都通过 {@link #getBaseUrl()} / {@link #getFullUrl(String)} 取地址，禁止调用方自行拼接；
 * 2. 默认环境为 TEST —— 与历史"有效行为"一致：旧版 GetDataByThread 构造会静默 setEnvironment(TEST)，
 *    该全局副作用已移除，默认值保留以免改变任何调用方的实际指向；
 * 3. 运行期切换环境立即生效：旧栈在调用时解析 URL，新栈（MemoryApiClient）检测 baseUrl 变化自动重建；
 * 4. 异步统一走 {@link #execute(Runnable)}：基于单一共享线程池，替代散落在 UI 层的 new Thread。
 */
public final class ApiConstants {

    public enum Environment { DEV, TEST, PROD }

    /** 默认环境：DEV（局域网直连调试；正式回归可改回 TEST，见类注释） */
    private static volatile Environment currentEnv = Environment.DEV;

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

    public static String getBaseUrl() {
        switch (currentEnv) {
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