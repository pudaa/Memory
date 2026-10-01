package com.deepsleep.memory.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * 本地应用数据库（离线优先改造）—— 与词书库 {@code lexicon.db} **必须分开**。
 *
 * <p>
 * ⚠️ {@code lexicon.db} 由 {@code LexiconDatabase.ASSET_DATA_VERSION} 的「删库重拷」机制随词书更新重建，
 * 任何用户数据放进去都会被清空；而这里存的是**不能丢的未同步作答**。
 * </p>
 *
 * <p>
 * 表随 P1 一次性建好（outbox / task_snapshot / word_list_cache / sync_meta），
 * 后续阶段只加逻辑不加表，因此 v1 不需要 Migration；**刻意不使用
 * fallbackToDestructiveMigration** —— 破坏性迁移会丢掉 outbox 里尚未上传的作答。
 * </p>
 */
@Database(entities = { OutboxEntity.class, TaskSnapshotEntity.class, WordListCacheEntity.class,
        SyncMetaEntity.class }, version = 1, exportSchema = false)
public abstract class MemoryLocalDatabase extends RoomDatabase {

    private static final String TAG = "MemoryLocalDatabase";
    private static final String DATABASE_NAME = "memory_local.db";

    @SuppressWarnings("VolatileLongOrDoubleField")
    private static volatile MemoryLocalDatabase INSTANCE;

    public abstract OutboxDao outboxDao();

    public abstract TaskSnapshotDao taskSnapshotDao();

    public abstract WordListCacheDao wordListCacheDao();

    public abstract SyncMetaDao syncMetaDao();

    @NonNull
    public static MemoryLocalDatabase getInstance(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (MemoryLocalDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(), MemoryLocalDatabase.class,
                            DATABASE_NAME)
                            // 答题路径上的入队是单行 insert，成本极低；重活（补传循环）在后台线程
                            .allowMainThreadQueries()
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    public static void destroyInstance() {
        INSTANCE = null;
    }

    @NonNull
    static String tag() {
        return TAG;
    }
}
