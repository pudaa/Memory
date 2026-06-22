package com.deepsleep.memory.handle_utils.lexicon;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.deepsleep.memory.R;
import com.deepsleep.memory.handle_utils.lexicon.db.LexiconBookEntity;
import com.deepsleep.memory.handle_utils.lexicon.db.LexiconDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.*;

/**
 * 词书资源管理器 —— Room + SQLite 按需查询版
 *
 * <p>
 * 不再一次性加载全部单词到内存，改为每次按 wordRank 查询 Room 并懒缓存。 所有公开接口签名保持不变。
 * </p>
 */
public class LexiconResourceMap {

    private static Context appContext;
    private static String specifiedLexiconId;

    // 懒缓存：仅缓存已查询过的 wordRank → WordEntry
    private static final Map<String, Map<Integer, WordEntry>> rankCache = new HashMap<>();

    // ========== 公开接口 ==========

    /**
     * 初始化/切换当前词书（仅记录 ID，不再批量加载）
     */
    public static void loadLexicon(@NonNull Context context, @NonNull String lexiconId) {
        if (appContext == null) {
            appContext = context.getApplicationContext();
        }
        specifiedLexiconId = lexiconId;
        // 确保词书存在
        int count = LexiconDatabase.getInstance(appContext).wordDao().getWordCountByBookId(lexiconId);
        if (count == 0) {
            Log.e("LexiconResourceMap", "词书在数据库中不存在: " + lexiconId);
        } else {
            Log.d("LexiconResourceMap", "词书就绪(SQLite): " + lexiconId + ", 共 " + count + " 个单词");
        }
    }

    /** 加载书本列表 */
    public static List<JSONObject> loadBooksFromJson(@NonNull Context context) {
        if (appContext == null)
            appContext = context.getApplicationContext();
        List<JSONObject> books = new ArrayList<>();
        try {
            for (LexiconBookEntity entity : LexiconDatabase.getInstance(appContext).bookDao().getAllBooks()) {
                JSONObject book = new JSONObject();
                book.put("id", entity.getBookId());
                book.put("title", entity.getTitle());
                book.put("wordNum", entity.getWordCount());
                book.put("cover", entity.getCoverUrl() != null ? entity.getCoverUrl() : "");
                book.put("introduce", entity.getDescription() != null ? entity.getDescription() : "");
                if (entity.getTags() != null && !entity.getTags().isEmpty()) {
                    try {
                        book.put("tags", new JSONArray(entity.getTags()));
                    } catch (JSONException ignored) {
                    }
                } else {
                    book.put("tags", new JSONArray());
                }
                books.add(book);
            }
            if (!books.isEmpty())
                return books;
        } catch (Exception e) {
            Log.w("LexiconResourceMap", "Room 加载书本列表失败，回退 raw", e);
        }
        return loadBooksFromRawFallback(context);
    }

    public static String getLexiconName(String lexiconId, List<JSONObject> allBooks) {
        for (JSONObject book : allBooks) {
            try {
                if (book.getString("id").equals(lexiconId))
                    return book.getString("title");
            } catch (JSONException ignored) {
            }
        }
        return "未知词书";
    }

    public static String getLoadedLexiconName() {
        return specifiedLexiconId;
    }

    /**
     * 按 wordRank 获取单词 —— 懒查询 + 缓存
     */
    @Nullable
    public static WordEntry getWordByRank(@NonNull String lexiconId, int wordRank) {
        // 先查缓存
        Map<Integer, WordEntry> cache = rankCache.get(lexiconId);
        if (cache != null && cache.containsKey(wordRank)) {
            return cache.get(wordRank);
        }
        // Room 查询
        if (appContext == null)
            return null;
        WordEntry entry = LexiconDatabase.getInstance(appContext).wordDao().getWordByBookIdAndRank(lexiconId, wordRank);
        if (entry != null) {
            rankCache.computeIfAbsent(lexiconId, k -> new HashMap<>()).put(wordRank, entry);
        }
        return entry;
    }

    /** 获取词书总单词数 */
    public static int getTotalWords(@NonNull String lexiconId) {
        if (appContext == null)
            return 0;
        return LexiconDatabase.getInstance(appContext).wordDao().getWordCountByBookId(lexiconId);
    }

    /**
     * 跨所有词书搜索单词（直接走 Room 查询，不再依赖预加载）
     */
    @Nullable
    public static WordEntry findWordInAllLexicons(@NonNull String word) {
        if (appContext == null)
            return null;
        return LexiconDatabase.getInstance(appContext).wordDao().searchByHeadWord(word.toLowerCase());
    }

    /** 从当前词书中随机取 N 个单词 */
    public static String[] getRandomWords() {
        if (appContext == null || specifiedLexiconId == null) {
            return new String[] { "no", "lexicon", "loaded" };
        }
        List<String> words = LexiconDatabase.getInstance(appContext).wordDao().getRandomHeadWords(specifiedLexiconId,
                10);
        return words.toArray(new String[0]);
    }

    /** 获取词书全部单词 */
    public static List<WordEntry> getAllEntries(@NonNull String lexiconId) {
        if (appContext == null)
            return Collections.emptyList();
        return LexiconDatabase.getInstance(appContext).wordDao().getWordsByBookId(lexiconId);
    }

    // ========== 内部 ==========

    private static List<JSONObject> loadBooksFromRawFallback(@NonNull Context context) {
        List<JSONObject> books = new ArrayList<>();
        try {
            Resources res = context.getResources();
            InputStream is = res.openRawResource(R.raw.book_list);
            Scanner scanner = new Scanner(is).useDelimiter("\\A");
            String json = scanner.hasNext() ? scanner.next() : "";
            scanner.close();
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.getJSONObject("data").getJSONArray("normalBooksInfo");
            for (int i = 0; i < arr.length(); i++)
                books.add(arr.getJSONObject(i));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return books;
    }
}