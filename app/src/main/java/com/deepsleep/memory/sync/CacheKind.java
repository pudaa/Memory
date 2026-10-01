package com.deepsleep.memory.sync;

import androidx.annotation.NonNull;

/** 读缓存类型（{@link WordListCacheEntity#kind}） */
public final class CacheKind {

    private CacheKind() {
    }

    /** 收藏词（/learning/getFavoriteWords） */
    @NonNull
    public static final String FAVORITE = "favorite";

    /** 薄弱词（/learning/getWeakWords） */
    @NonNull
    public static final String WEAK = "weak";

    /** 计划列表（/learning/getUserAllLearningPlans） */
    @NonNull
    public static final String PLAN_LIST = "plan_list";

    /** 每日一读文章收藏（本地键已存在，这里只做统一封装） */
    @NonNull
    public static final String DAILY_READING_FAVORITE = "daily_reading_favorite";
}
