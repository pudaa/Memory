package com.deepsleep.memory.handle_utils.lexicon.db;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Room 数据库 —— 词书数据存储
 *
 * <p>
 * 首次启动时通过 {@link #createFromAsset} 从预置的 assets/databases/lexicon.db 复制数据库。
 * </p>
 */
@Database(entities = { LexiconBookEntity.class,
        com.deepsleep.memory.handle_utils.lexicon.WordEntry.class }, version = 2, exportSchema = false)
public abstract class LexiconDatabase extends RoomDatabase {

    private static final String TAG = "LexiconDatabase";
    private static final String DATABASE_NAME = "lexicon.db";
    private static final String ASSET_DB_PATH = "databases/lexicon.db";

    @SuppressWarnings("VolatileLongOrDoubleField")
    private static volatile LexiconDatabase INSTANCE;

    public abstract LexiconBookDao bookDao();

    public abstract LexiconWordDao wordDao();

    @NonNull
    public static LexiconDatabase getInstance(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (LexiconDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room
                            .databaseBuilder(context.getApplicationContext(), LexiconDatabase.class, DATABASE_NAME)
                            .createFromAsset(ASSET_DB_PATH).allowMainThreadQueries().fallbackToDestructiveMigration()
                            .addCallback(new Callback() {
                                @Override
                                public void onOpen(@NonNull SupportSQLiteDatabase db) {
                                    super.onOpen(db);
                                    Log.d(TAG, "词书数据库已打开");
                                }
                            }).build();
                }
            }
        }
        return INSTANCE;
    }

    public static void destroyInstance() {
        INSTANCE = null;
    }

    /** 预热是否已启动（进程内仅触发一次） */
    private static volatile boolean warmUpStarted = false;

    /**
     * 预热词库数据库：在后台线程提前打开底层 SQLite 文件。
     *
     * <p>
     * Room 为懒打开：首次查询才会 open 数据库文件（首装时还包括从 assets 拷贝 lexicon.db），
     * 若发生在主线程首次查询（单词清单响应后）会造成卡顿。此处把 open 成本与网络请求并行消化， 使「响应到达 → 出卡片」路径更快。已打开时幂等返回。
     * </p>
     */
    public static void warmUpAsync(@NonNull Context context) {
        if (warmUpStarted)
            return;
        warmUpStarted = true;
        new Thread(() -> {
            try {
                LexiconDatabase db = getInstance(context);
                // 触发底层 SQLite 文件打开 / 首装拷贝；已打开则立即返回
                db.getOpenHelper().getWritableDatabase();
            } catch (Exception e) {
                Log.w(TAG, "词库预热失败", e);
            }
        }, "LexiconDbWarmUp").start();
    }
}
