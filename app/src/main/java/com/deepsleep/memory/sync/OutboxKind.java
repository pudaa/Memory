package com.deepsleep.memory.sync;

import androidx.annotation.NonNull;

/**
 * 出站队列（Outbox）条目类型。
 *
 * <p>统一承载所有"会产生服务端副作用"的写操作，离线时先落本地、联网后按顺序回放。</p>
 */
public final class OutboxKind {

    private OutboxKind() {
    }

    /** 提交作答（选择/输入共用），payload 即 {@code /learning/submitAnswer} 的请求体 */
    @NonNull
    public static final String SUBMIT_ANSWER = "submit_answer";

    /** 上报当日学习完成状态，payload 即 {@code /learning/updateLearningListCompletion} 的请求体 */
    @NonNull
    public static final String LEARNING_LIST_COMPLETION = "learning_list_completion";

    /** 收藏 / 取消收藏（payload 为 JSON，字段对应 setFavorite 的 header 语义） */
    @NonNull
    public static final String SET_FAVORITE = "set_favorite";

    /** 学习日志上报（payload 即 {@code /learning/updateWordStudyLog} 的请求体） */
    @NonNull
    public static final String STUDY_LOG = "study_log";
}
