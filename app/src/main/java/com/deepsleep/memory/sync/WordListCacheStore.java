package com.deepsleep.memory.sync;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.deepsleep.memory.settings.InnerSettingsManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 读缓存门面（P3）：收藏词 / 薄弱词 / 计划列表 / 每日一读收藏。
 *
 * <p>两种形态共存于同一张 {@code word_list_cache} 表：</p>
 * <ul>
 * <li><b>逐条缓存</b>（{@link #replaceFromJsonArray}）：适合"能单独增删一项"的清单
 * （收藏词、薄弱词）—— 取消收藏时可精确删除一行；</li>
 * <li><b>整块缓存</b>（{@link #saveBlob}）：适合"整体刷新"的响应
 * （计划列表、每日一读收藏）—— 直接存原始 JSON，页面按原逻辑解析。</li>
 * </ul>
 *
 * <p>作用域：{@code (userId, planId, kind)}。切账号 / 切计划天然隔离；失效只需按 kind 覆盖。</p>
 */
public final class WordListCacheStore {

    private static final String TAG = "WordListCacheStore";

    /** 整块缓存的固定 itemKey */
    private static final String BLOB_KEY = "__blob__";

    private WordListCacheStore() {
    }

    /** 当前计划 ID（离线时也要能定位缓存分桶；为空表示"未知计划"，退化为账号级缓存） */
    @NonNull
    public static String currentPlanId(@NonNull Context context, int userId) {
        String planId = InnerSettingsManager.getInstance(context.getApplicationContext()).getCurrentPlanId(userId);
        return planId == null ? "" : planId;
    }

    // ==================== 逐条缓存 ====================

    /**
     * 用服务端响应整体替换某类清单的逐条缓存。
     *
     * @param array     服务端返回的数组（如 {@code favoriteWords} / {@code weakWords}）
     * @param keyField  作为 itemKey 的字段（如 {@code headWord}）
     * @param sortField 排序字段（如 {@code retrievability}），缺失时按数组顺序递增
     */
    public static void replaceFromJsonArray(@NonNull Context context, int userId, @NonNull String planId,
            @NonNull String kind, @NonNull String lexiconId, @Nullable JSONArray array, @NonNull String keyField,
            @Nullable String sortField) {
        WordListCacheDao dao = MemoryLocalDatabase.getInstance(context).wordListCacheDao();
        dao.clearKind(userId, planId, kind);
        if (array == null || array.length() == 0) {
            return;
        }
        List<WordListCacheEntity> items = new ArrayList<>(array.length());
        long now = System.currentTimeMillis();
        for (int i = 0; i < array.length(); i++) {
            JSONObject raw = array.optJSONObject(i);
            if (raw == null) {
                continue;
            }
            String key = raw.optString(keyField, "");
            if (key.isEmpty()) {
                continue;
            }
            WordListCacheEntity entity = new WordListCacheEntity();
            entity.userId = userId;
            entity.planId = planId;
            entity.kind = kind;
            entity.itemKey = key;
            entity.lexiconId = lexiconId;
            entity.payloadJson = raw.toString();
            entity.sortValue = sortField == null ? i : raw.optDouble(sortField, i);
            entity.updatedAtEpochMs = now;
            items.add(entity);
        }
        if (!items.isEmpty()) {
            dao.upsertAll(items);
        }
        Log.i(TAG, "读缓存已更新: kind=" + kind + ", planId=" + planId + ", 条数=" + items.size());
    }

    /** 读取某类清单的逐条缓存（按服务端排序字段升序，与页面排序一致） */
    @NonNull
    public static List<WordListCacheEntity> loadItems(@NonNull Context context, int userId, @NonNull String planId,
            @NonNull String kind) {
        return MemoryLocalDatabase.getInstance(context).wordListCacheDao().list(userId, planId, kind);
    }

    /** 逐条缓存重建为 JSONArray（页面可直接复用原有解析逻辑） */
    @NonNull
    public static JSONArray loadItemsAsJson(@NonNull Context context, int userId, @NonNull String planId,
            @NonNull String kind) {
        JSONArray array = new JSONArray();
        for (WordListCacheEntity entity : loadItems(context, userId, planId, kind)) {
            try {
                array.put(new JSONObject(entity.payloadJson));
            } catch (JSONException ignored) {
                // 单条损坏不影响整体
            }
        }
        return array;
    }

    /** 精确删除一条（取消收藏的乐观更新用；失败后可通过 {@link #upsertItem} 回滚） */
    public static void removeItem(@NonNull Context context, int userId, @NonNull String planId, @NonNull String kind,
            @NonNull String itemKey) {
        MemoryLocalDatabase.getInstance(context).wordListCacheDao().removeItem(userId, planId, kind, itemKey);
    }

    /** 单条写入（回滚被误删/失败的乐观更新） */
    public static void upsertItem(@NonNull Context context, int userId, @NonNull String planId, @NonNull String kind,
            @NonNull String lexiconId, @NonNull JSONObject payload, @NonNull String keyField,
            double sortValue) {
        WordListCacheEntity entity = new WordListCacheEntity();
        entity.userId = userId;
        entity.planId = planId;
        entity.kind = kind;
        entity.itemKey = payload.optString(keyField, "");
        if (entity.itemKey.isEmpty()) {
            return;
        }
        entity.lexiconId = lexiconId;
        entity.payloadJson = payload.toString();
        entity.sortValue = sortValue;
        entity.updatedAtEpochMs = System.currentTimeMillis();
        List<WordListCacheEntity> items = new ArrayList<>(1);
        items.add(entity);
        MemoryLocalDatabase.getInstance(context).wordListCacheDao().upsertAll(items);
    }

    // ==================== 整块缓存 ====================

    public static void saveBlob(@NonNull Context context, int userId, @NonNull String planId, @NonNull String kind,
            @NonNull String lexiconId, @NonNull JSONObject blob) {
        WordListCacheEntity entity = new WordListCacheEntity();
        entity.userId = userId;
        entity.planId = planId;
        entity.kind = kind;
        entity.itemKey = BLOB_KEY;
        entity.lexiconId = lexiconId;
        entity.payloadJson = blob.toString();
        entity.sortValue = 0;
        entity.updatedAtEpochMs = System.currentTimeMillis();
        List<WordListCacheEntity> items = new ArrayList<>(1);
        items.add(entity);
        MemoryLocalDatabase.getInstance(context).wordListCacheDao().upsertAll(items);
    }

    @Nullable
    public static JSONObject loadBlob(@NonNull Context context, int userId, @NonNull String planId,
            @NonNull String kind) {
        for (WordListCacheEntity entity : loadItems(context, userId, planId, kind)) {
            if (BLOB_KEY.equals(entity.itemKey)) {
                try {
                    return new JSONObject(entity.payloadJson);
                } catch (JSONException e) {
                    Log.w(TAG, "整块缓存解析失败: kind=" + kind, e);
                    return null;
                }
            }
        }
        return null;
    }

    // ==================== 元信息 ====================

    /** 最近更新时间（0 表示无缓存），用于「离线数据 · 更新于 X」提示 */
    public static long lastUpdatedAt(@NonNull Context context, int userId, @NonNull String planId,
            @NonNull String kind) {
        long latest = 0;
        for (WordListCacheEntity entity : loadItems(context, userId, planId, kind)) {
            latest = Math.max(latest, entity.updatedAtEpochMs);
        }
        return latest;
    }

    /** 把缓存时间格式化为「MM-dd HH:mm」，供离线提示统一使用 */
    @NonNull
    public static String formatUpdatedAt(long epochMs) {
        if (epochMs <= 0) {
            return "";
        }
        return new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                .format(new java.util.Date(epochMs));
    }

    /** 统一的离线提示文案 */
    @NonNull
    public static String offlineHint(@NonNull Context context, int userId, @NonNull String planId,
            @NonNull String kind) {
        long updatedAt = lastUpdatedAt(context, userId, planId, kind);
        String when = formatUpdatedAt(updatedAt);
        return when.isEmpty() ? "离线数据" : "离线数据 · 更新于 " + when;
    }
}
