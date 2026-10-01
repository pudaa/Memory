package com.deepsleep.memory.sync;

import androidx.annotation.NonNull;
import androidx.room.Entity;

/**
 * 读缓存（P3）：收藏词 / 薄弱词 / 计划列表 / 每日一读收藏 统一一张表。
 *
 * <p>按 {@code (userId, planId, kind, itemKey)} 分键，切账号 / 切计划天然隔离，
 * 失效只需按 kind 覆盖或删除，不需要额外的清理逻辑。</p>
 */
@Entity(tableName = "word_list_cache", primaryKeys = { "userId", "planId", "kind", "itemKey" })
public class WordListCacheEntity {

    public int userId;

    @NonNull
    public String planId = "";

    /** 见 {@link CacheKind} */
    @NonNull
    public String kind = "";

    /** 条目键：单词类为 headWord，计划列表类为 planId 等 */
    @NonNull
    public String itemKey = "";

    @NonNull
    public String lexiconId = "";

    /** 原始 JSON（保留服务端字段，便于后续增删字段而不改表） */
    @NonNull
    public String payloadJson = "{}";

    /** 展示用排序值（如薄弱词的 R、收藏词的 difficulty） */
    public double sortValue;

    public long updatedAtEpochMs;
}
