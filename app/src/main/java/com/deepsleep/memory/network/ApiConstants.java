package com.deepsleep.memory.network;

import com.deepsleep.memory.BuildConfig;

public class ApiConstants {
    public enum Environment { DEV, TEST, PROD }

    private static Environment currentEnv = Environment.DEV;

    // Base URLs are injected at build time from local.properties (see app/build.gradle).
    // The public repo only ships placeholder values; real endpoints stay local.
    private static final String DEV_BASE_URL = BuildConfig.DEV_BASE_URL;
    private static final String TEST_BASE_URL = BuildConfig.TEST_BASE_URL;
    private static final String PROD_BASE_URL = BuildConfig.PROD_BASE_URL;


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