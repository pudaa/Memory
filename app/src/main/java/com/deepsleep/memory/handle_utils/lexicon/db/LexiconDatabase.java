package com.deepsleep.memory.handle_utils.lexicon.db;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.deepsleep.memory.settings.InnerSettingsManager;

/**
 * Room 数据库 —— 词书数据存储
 *
 * <p>
 * 首次启动时通过 {@link #createFromAsset} 从预置的 assets/databases/lexicon.db 复制数据库；
 * 资源库内容更新后（见 {@link #ASSET_DATA_VERSION}）会删库重拷，保证存量安装也拿到新数据。
 * </p>
 */
@Database(entities = { LexiconBookEntity.class,
        com.deepsleep.memory.handle_utils.lexicon.WordEntry.class }, version = 2, exportSchema = false)
public abstract class LexiconDatabase extends RoomDatabase {

    private static final String TAG = "LexiconDatabase";
    private static final String DATABASE_NAME = "lexicon.db";
    private static final String ASSET_DB_PATH = "databases/lexicon.db";

    /**
     * 词书资源数据版本 —— <b>每次更新 assets/databases/lexicon.db 内容后必须 +1</b>。
     *
     * <p>
     * Room 的 {@code createFromAsset} 只在数据库文件不存在时拷贝资源库，
     * 因此仅替换 assets 里的 db 对存量安装无效（用户仍读旧数据）。
     * 这里用版本号判断并做一次「删库重拷」，保证老用户也拿到修正后的词书数据。
     * </p>
     *
     * <p>版本 2：修复例句/真题例句中因早期剥离 HTML 标签而丢失空格的粘连词
     * （见 {@code scripts/repair_glued_sentences.py}）。</p>
     */
    public static final int ASSET_DATA_VERSION = 2;

    @SuppressWarnings("VolatileLongOrDoubleField")
    private static volatile LexiconDatabase INSTANCE;

    public abstract LexiconBookDao bookDao();

    public abstract LexiconWordDao wordDao();

    @NonNull
    public static LexiconDatabase getInstance(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (LexiconDatabase.class) {
                if (INSTANCE == null) {
                    final Context appContext = context.getApplicationContext();
                    refreshAssetIfOutdated(appContext);
                    INSTANCE = Room
                            .databaseBuilder(appContext, LexiconDatabase.class, DATABASE_NAME)
                            .createFromAsset(ASSET_DB_PATH).allowMainThreadQueries().fallbackToDestructiveMigration()
                            .addCallback(new Callback() {
                                @Override
                                public void onOpen(@NonNull SupportSQLiteDatabase db) {
                                    super.onOpen(db);
                                    // 打开成功才记录数据版本：拷贝失败时下次启动会重试
                                    InnerSettingsManager.getInstance(appContext)
                                            .setLexiconAssetVersion(ASSET_DATA_VERSION);
                                    Log.d(TAG, "词书数据库已打开（数据版本 " + ASSET_DATA_VERSION + "）");
                                }
                            }).build();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 词书资源版本落后时删除本地库文件，让 Room 重新从 assets 拷贝。
     *
     * <p>本地库是只读数据（DAO 写入方法无运行期调用点），删库不会丢失用户数据。</p>
     */
    private static void refreshAssetIfOutdated(@NonNull Context context) {
        try {
            InnerSettingsManager settings = InnerSettingsManager.getInstance(context);
            if (settings.getLexiconAssetVersion() == ASSET_DATA_VERSION) {
                return;
            }
            boolean deleted = context.deleteDatabase(DATABASE_NAME);
            Log.i(TAG, "词书资源版本 " + settings.getLexiconAssetVersion() + " → " + ASSET_DATA_VERSION
                    + "，重新拷贝资源库（旧库已删除: " + deleted + "）");
        } catch (Exception e) {
            Log.w(TAG, "刷新词书资源失败", e);
        }
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
