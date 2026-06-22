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
}
