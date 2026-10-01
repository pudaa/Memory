package com.deepsleep.memory.sync;

import androidx.annotation.NonNull;
import androidx.room.Entity;

/**
 * 同步元信息（可观测性 + 节流）：每个 {@code (userId, planId)} 一行。
 *
 * <p>用于回答"上次同步是什么时候、还有多少条没发出去、上次为什么失败"，也是后续
 * 角标（待同步 N 条）的数据来源。</p>
 */
@Entity(tableName = "sync_meta", primaryKeys = { "userId", "planId" })
public class SyncMetaEntity {

    public int userId;

    @NonNull
    public String planId = "";

    /** 最近一次出站队列成功发送的时间 */
    public long lastSyncAtEpochMs;

    /** 最近一次拉取今日任务的时间（节流用） */
    public long lastTaskPullAtEpochMs;

    /** 最近一次失败原因（便于排查，不做 UI 依赖） */
    @NonNull
    public String lastError = "";
}
