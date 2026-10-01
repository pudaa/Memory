package com.deepsleep.memory.sync;

import androidx.annotation.NonNull;

/**
 * 出站队列条目状态。
 *
 * <pre>
 *   pending  ──(worker 取走)──► syncing ──(服务端 200)──► 删除
 *      ▲                           │
 *      └──(网络失败：attempts++ / nextAttemptAt 退避)──┘
 *      └──(业务失败 4xx / attempts≥MAX)──► dead（保留可查，不阻塞队列）
 * </pre>
 */
public final class OutboxState {

    private OutboxState() {
    }

    /** 待发送（含等待退避时间的情况，见 {@code nextAttemptAtEpochMs}） */
    @NonNull
    public static final String PENDING = "pending";

    /** 正在发送（进程被杀后残留的 syncing 会被重新视为 pending 过期重试） */
    @NonNull
    public static final String SYNCING = "syncing";

    /** 永久失败（业务错误或重试超限），保留供排查与人工重试 */
    @NonNull
    public static final String DEAD = "dead";
}
