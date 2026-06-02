package com.deepsleep.memory.network;

public class ApiConstants {
    public enum Environment { DEV, TEST, PROD }

    private static Environment currentEnv = Environment.DEV;

    private static final String DEV_BASE_URL = "http://192.168.102.14:8080";  // 开发环境IP
    private static final String TEST_BASE_URL = "http://frp-fit.com:60966";  // 测试环境
    private static final String PROD_BASE_URL = "http://116.62.6.15:8080"; // 生产环境


    public static String getBaseUrl() {
        switch (currentEnv) {
            case TEST:  return TEST_BASE_URL;
            case PROD:  return PROD_BASE_URL;
            default:    return DEV_BASE_URL;
        }
    }


    public static void setEnvironment(Environment env) {
        currentEnv = env;
    }

    public static String getFullUrl(String relativePath) {
        return getBaseUrl() + relativePath;
    }
}