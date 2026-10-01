package com.deepsleep.memory.handle_utils.lexicon;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.deepsleep.memory.R;
import com.deepsleep.memory.handle_utils.lexicon.db.LexiconBookEntity;
import com.deepsleep.memory.handle_utils.lexicon.db.LexiconDatabase;
import com.deepsleep.memory.settings.InnerSettingsManager;

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
    /** 已完成就绪校验的词书 ID：同一词书重复进入时跳过 count 查询 */
    private static String validatedLexiconId;

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
        // 同一词书已校验过则直接返回，避免每次进单词页都执行 count 查询
        if (lexiconId.equals(specifiedLexiconId) && lexiconId.equals(validatedLexiconId)) {
            return;
        }
        specifiedLexiconId = lexiconId;
        // 确保词书存在
        int count = LexiconDatabase.getInstance(appContext).wordDao().getWordCountByBookId(lexiconId);
        if (count == 0) {
            Log.e("LexiconResourceMap", "词书在数据库中不存在: " + lexiconId);
        } else {
            validatedLexiconId = lexiconId;
            // 落盘当前词书 ID：词书内容已在本地 Room 库，仅 ID 需要持久化，
            // 以便离线时词书浏览仍可恢复（见 restoreCachedLexicon）
            int userId = InnerSettingsManager.getInstance(appContext).getUserId();
            if (userId > 0) {
                InnerSettingsManager.getInstance(appContext).saveCurrentLexiconId(userId, lexiconId);
            }
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
     * 仅切换当前会话的浏览词书（供用户自由切换浏览使用）。
     * 与 {@link #loadLexicon} 的区别：<b>不写入离线默认词书缓存</b>——
     * 自由浏览切换并不代表学习计划的词书发生变化。
     */
    public static void switchLexiconForBrowsing(@NonNull Context context, @NonNull String lexiconId) {
        if (appContext == null) {
            appContext = context.getApplicationContext();
        }
        specifiedLexiconId = lexiconId;
    }

    /**
     * 离线恢复当前词书：读取持久化的词书 ID 并在本地库校验。
     * 全程无网络请求——词书内容本就在本地 Room 库，断网时同样可阅览。
     *
     * @return 恢复成功的词书 ID；无缓存或本地不存在该书时返回 ""，
     *         由调用方决定后续回退策略（本地词书列表 / 提示）
     */
    @NonNull
    public static String restoreCachedLexicon(@NonNull Context context) {
        if (appContext == null) {
            appContext = context.getApplicationContext();
        }
        int userId = InnerSettingsManager.getInstance(appContext).getUserId();
        if (userId <= 0) {
            return "";
        }
        String cached = InnerSettingsManager.getInstance(appContext).getCurrentLexiconId(userId);
        if (cached == null || cached.isEmpty()) {
            return "";
        }
        int count = LexiconDatabase.getInstance(appContext).wordDao().getWordCountByBookId(cached);
        if (count == 0) {
            Log.w("LexiconResourceMap", "缓存词书在本地库不存在: " + cached);
            return "";
        }
        specifiedLexiconId = cached;
        validatedLexiconId = cached;
        Log.d("LexiconResourceMap", "离线恢复词书: " + cached + ", 共 " + count + " 个单词");
        return cached;
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

    // ========== 本地查词：计划词书优先 + 全字段呈现 ==========

    /**
     * 绑定应用上下文（幂等）。
     *
     * <p>
     * 查词页可能在用户从未进入学习页的会话里被打开，此时 {@link #appContext} 尚未初始化，
     * 所有只读查询会直接返回 null。查词入口先调用本方法即可独立可用。
     * </p>
     */
    public static void attachContext(@NonNull Context context) {
        if (appContext == null) {
            appContext = context.getApplicationContext();
        }
    }

    /** 前缀候选一次取回的行数（调用方会按 head_word 去重后截断） */
    private static final int PREFIX_FETCH_LIMIT = 60;

    /** 模糊搜索最多回退截断的字符数 */
    private static final int FUZZY_MAX_TRIM = 3;

    /**
     * 当前「学习计划词书」ID —— 本地查词优先呈现该词书中的条目。
     *
     * <p>
     * 取值顺序：
     * <ol>
     * <li>{@link InnerSettingsManager#getCurrentLexiconId(int)}：学习页拿到服务端任务
     * （{@code /learning/getTodayTask} 的 lexiconId）后落盘，即是计划词书，离线可用；</li>
     * <li>本会话已加载词书（{@link #getLoadedLexiconName()}，含词书浏览的自由切换）。</li>
     * </ol>
     * 两者都没有时返回 ""，调用方退化为「跨词书选最丰富条目」。
     * </p>
     */
    @NonNull
    public static String getPreferredLexiconId(@NonNull Context context) {
        attachContext(context);
        Context ctx = appContext;
        if (ctx == null)
            return specifiedLexiconId != null ? specifiedLexiconId : "";
        try {
            int userId = InnerSettingsManager.getInstance(ctx).getUserId();
            if (userId > 0) {
                String planLexicon = InnerSettingsManager.getInstance(ctx).getCurrentLexiconId(userId);
                if (planLexicon != null && !planLexicon.isEmpty()) {
                    return planLexicon;
                }
            }
        } catch (Exception e) {
            Log.w("LexiconResourceMap", "读取计划词书失败", e);
        }
        return specifiedLexiconId != null ? specifiedLexiconId : "";
    }

    /**
     * 本地查词取词：优先计划词书，其次跨词书取「信息最完整」的条目。
     *
     * <p>
     * 同一单词常出现在多本词书中（17k+ 个单词命中多本），各书例句/英文释义详略不同。
     * 计划词书没有收录时，不再任取一行，而是按 {@link #richness(WordEntry)} 取最丰富的一条，
     * 并以 book_id 字典序兜底，保证同一查询结果稳定可复现。
     * </p>
     */
    @Nullable
    public static WordEntry findWordForDisplay(@NonNull String word, @Nullable String preferredBookId) {
        if (appContext == null)
            return null;
        String key = word.toLowerCase();
        if (preferredBookId != null && !preferredBookId.isEmpty()) {
            WordEntry preferred = LexiconDatabase.getInstance(appContext).wordDao().searchInBook(key, preferredBookId);
            if (preferred != null) {
                return preferred;
            }
        }
        List<WordEntry> candidates = LexiconDatabase.getInstance(appContext).wordDao().searchInAllBooks(key);
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        WordEntry best = null;
        for (WordEntry candidate : candidates) {
            if (best == null || isRicher(candidate, best)) {
                best = candidate;
            }
        }
        return best;
    }

    /**
     * 收录该单词的全部词书条目，按「计划词书优先 → 信息量降序 → book_id 字典序」排序。
     *
     * <p>
     * 供查词页在「计划词书未收录、跨书回退展示」时提供切换入口：用户可以看到
     * 这个词分别被哪些词书收录、各书内容详略如何，再自行决定展示哪一本。
     * </p>
     */
    @NonNull
    public static List<WordEntry> findWordInBooks(@NonNull String word, @Nullable String preferredBookId) {
        if (appContext == null) {
            return Collections.emptyList();
        }
        String bookFilter = preferredBookId != null ? preferredBookId : "";
        List<WordEntry> rows = LexiconDatabase.getInstance(appContext).wordDao()
                .searchInBooksOrdered(word.toLowerCase(), bookFilter);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<WordEntry> result = new ArrayList<>(rows);
        result.sort((a, b) -> {
            boolean aPlan = a.getBookId().equals(bookFilter);
            boolean bPlan = b.getBookId().equals(bookFilter);
            if (aPlan != bPlan) {
                return aPlan ? -1 : 1;
            }
            int sa = richness(a), sb = richness(b);
            if (sa != sb) {
                return sb - sa;
            }
            return a.getBookId().compareTo(b.getBookId());
        });
        return result;
    }

    /** 指定词书内的精确条目（查词页「切换词书」后按用户选择取词） */
    @Nullable
    public static WordEntry findWordInBook(@NonNull String word, @NonNull String bookId) {
        if (appContext == null || bookId.isEmpty()) {
            return null;
        }
        return LexiconDatabase.getInstance(appContext).wordDao().searchInBook(word.toLowerCase(), bookId);
    }

    /**
     * 拼写相近 / 词形变化候选（前缀匹配）。
     *
     * <p>
     * 先按原查询做前缀匹配；无结果时逐字回退（最多 {@link #FUZZY_MAX_TRIM} 个字符，
     * 且保留至少 3 个字符），用于兜住末尾多打/打错的输入（acceleratedd → accelerated）。
     * 结果按 head_word_lower 去重并排除查询词本身。
     * </p>
     */
    @NonNull
    public static List<WordEntry> findSimilarWords(@NonNull String word, @Nullable String preferredBookId, int limit) {
        List<WordEntry> result = new ArrayList<>();
        if (appContext == null || limit <= 0) {
            return result;
        }
        String key = sanitizeGlob(word.toLowerCase());
        if (key.length() < 2) {
            return result;
        }
        String bookFilter = preferredBookId != null ? preferredBookId : "";
        for (int trim = 0; trim <= FUZZY_MAX_TRIM && key.length() - trim >= 3; trim++) {
            String prefix = key.substring(0, key.length() - trim);
            List<WordEntry> rows = LexiconDatabase.getInstance(appContext).wordDao().searchByPrefix(prefix + "*", key,
                    bookFilter, PREFIX_FETCH_LIMIT);
            if (rows == null || rows.isEmpty()) {
                continue;
            }
            Set<String> seen = new LinkedHashSet<>();
            seen.add(key);
            result.clear();
            for (WordEntry row : rows) {
                if (row == null || !seen.add(row.getHeadWordLower())) {
                    continue;
                }
                result.add(row);
                if (result.size() >= limit) {
                    break;
                }
            }
            if (!result.isEmpty()) {
                return result;
            }
        }
        return result;
    }

    /** 词书标题（用于标注查词结果的来源词书） */
    @NonNull
    public static String getBookTitle(@NonNull String bookId) {
        if (appContext == null || bookId.isEmpty()) {
            return "";
        }
        try {
            String title = LexiconDatabase.getInstance(appContext).bookDao().getBookTitleById(bookId);
            return title != null ? title : "";
        } catch (Exception e) {
            Log.w("LexiconResourceMap", "读取词书标题失败: " + bookId, e);
            return "";
        }
    }

    /** 去掉 GLOB 元字符，避免用户输入把前缀匹配变成通配查询 */
    @NonNull
    private static String sanitizeGlob(@NonNull String word) {
        StringBuilder sb = new StringBuilder(word.length());
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (c != '*' && c != '?' && c != '[' && c != ']') {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }

    /** 条目信息丰富度评分：例句/真题权重更高，其次释义与同近义词、同根词 */
    private static int richness(@NonNull WordEntry entry) {
        int score = 0;
        score += Math.min(entry.getExampleSentences().size(), 5) * 3;
        score += Math.min(entry.getRealExamSentences().size(), 3) * 3;
        score += Math.min(entry.getSynonyms().size(), 5);
        score += Math.min(entry.getRelatedWords().size(), 5);
        score += entry.getEnglishDefinition().isEmpty() ? 0 : 2;
        score += entry.getChineseTranslation().isEmpty() ? 0 : 1;
        score += entry.getPos().isEmpty() ? 0 : 1;
        return score;
    }

    /** 是否 a 比 b 更丰富；评分相同时用 book_id / id 字典序兜底，保证结果稳定 */
    private static boolean isRicher(@NonNull WordEntry a, @NonNull WordEntry b) {
        int sa = richness(a);
        int sb = richness(b);
        if (sa != sb) {
            return sa > sb;
        }
        int byBook = a.getBookId().compareTo(b.getBookId());
        if (byBook != 0) {
            return byBook < 0;
        }
        return a.getId() < b.getId();
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