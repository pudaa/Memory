package com.deepsleep.memory.handle_utils.lexicon;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import androidx.annotation.NonNull;
import com.deepsleep.memory.R;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.*;

public class LexiconResourceMap {
    private static final Map<String, Integer> lexiconMap = new HashMap<>();
    private static String specifiedLexiconId = null; // 保存指定加载的词书ID

    private static final Map<String, List<WordEntry>> lexiconCache = new HashMap<>();
    // wordRank → WordEntry 索引，O(1) 按序号查词
    private static final Map<String, Map<Integer, WordEntry>> rankIndex = new HashMap<>();
    // headWord(小写) → WordEntry 全局索引，O(1) 跨词书搜词
    private static final Map<String, WordEntry> globalWordIndex = new HashMap<>();

    static {
        lexiconMap.put("cet4luan_1", R.raw.cet4luan_1);
        lexiconMap.put("cet6luan_1", R.raw.cet6luan_1);
        lexiconMap.put("kaoyanluan_1", R.raw.kaoyanluan_1);
        lexiconMap.put("level4luan_1", R.raw.level4luan_1);
        lexiconMap.put("level8_1", R.raw.level8_1);
        lexiconMap.put("cet4luan_2", R.raw.cet4luan_2);
        lexiconMap.put("cet6_2", R.raw.cet6_2);
        lexiconMap.put("kaoyan_2", R.raw.kaoyan_2);
        lexiconMap.put("level4luan_2", R.raw.level4luan_2);
        lexiconMap.put("level8luan_2", R.raw.level8luan_2);
        lexiconMap.put("cet4_3", R.raw.cet4_3);
        lexiconMap.put("cet6_3", R.raw.cet6_3);
        lexiconMap.put("kaoyan_3", R.raw.kaoyan_3);
        lexiconMap.put("cet4_1", R.raw.cet4_1);
        lexiconMap.put("cet6_1", R.raw.cet6_1);
        lexiconMap.put("kaoyan_1", R.raw.kaoyan_1);
        lexiconMap.put("level4_1", R.raw.level4_1);
        lexiconMap.put("cet4_2", R.raw.cet4_2);
        lexiconMap.put("level4_2", R.raw.level4_2);
        lexiconMap.put("level8_2", R.raw.level8_2);
        lexiconMap.put("ieltsluan_2", R.raw.ieltsluan_2);
        lexiconMap.put("toefl_2", R.raw.toefl_2);
        lexiconMap.put("gre_2", R.raw.gre_2);
        lexiconMap.put("sat_2", R.raw.sat_2);
        lexiconMap.put("gmatluan_2", R.raw.gmatluan_2);
        lexiconMap.put("ielts_3", R.raw.ielts_3);
        lexiconMap.put("toefl_3", R.raw.toefl_3);
        lexiconMap.put("gre_3", R.raw.gre_3);
        lexiconMap.put("sat_3", R.raw.sat_3);
        lexiconMap.put("gmat_3", R.raw.gmat_3);
        lexiconMap.put("ielts_2", R.raw.ielts_2);
        lexiconMap.put("gmat_2", R.raw.gmat_2);
        lexiconMap.put("chuzhongluan_2", R.raw.chuzhongluan_2);
        lexiconMap.put("gaozhongluan_2", R.raw.gaozhongluan_2);
        lexiconMap.put("chuzhong_3", R.raw.chuzhong_3);
        lexiconMap.put("gaozhong_3", R.raw.gaozhong_3);
        lexiconMap.put("pepxiaoxue3_1", R.raw.pepxiaoxue3_1);
        lexiconMap.put("pepxiaoxue3_2", R.raw.pepxiaoxue3_2);
        lexiconMap.put("pepxiaoxue4_1", R.raw.pepxiaoxue4_1);
        lexiconMap.put("pepxiaoxue4_2", R.raw.pepxiaoxue4_2);
        lexiconMap.put("pepxiaoxue5_1", R.raw.pepxiaoxue5_1);
        lexiconMap.put("pepxiaoxue5_2", R.raw.pepxiaoxue5_2);
        lexiconMap.put("pepxiaoxue6_1", R.raw.pepxiaoxue6_1);
        lexiconMap.put("pepxiaoxue6_2", R.raw.pepxiaoxue6_2);
        lexiconMap.put("pepchuzhong7_1", R.raw.pepchuzhong7_1);
        lexiconMap.put("pepchuzhong7_2", R.raw.pepchuzhong7_2);
        lexiconMap.put("pepchuzhong8_1", R.raw.pepchuzhong8_1);
        lexiconMap.put("pepchuzhong8_2", R.raw.pepchuzhong8_2);
        lexiconMap.put("pepchuzhong9_1", R.raw.pepchuzhong9_1);
        lexiconMap.put("waiyanshechuzhong_1", R.raw.waiyanshechuzhong_1);
        lexiconMap.put("waiyanshechuzhong_2", R.raw.waiyanshechuzhong_2);
        lexiconMap.put("waiyanshechuzhong_3", R.raw.waiyanshechuzhong_3);
        lexiconMap.put("waiyanshechuzhong_4", R.raw.waiyanshechuzhong_4);
        lexiconMap.put("waiyanshechuzhong_5", R.raw.waiyanshechuzhong_5);
        lexiconMap.put("waiyanshechuzhong_6", R.raw.waiyanshechuzhong_6);
        lexiconMap.put("pepgaozhong_1", R.raw.pepgaozhong_1);
        lexiconMap.put("pepgaozhong_2", R.raw.pepgaozhong_2);
        lexiconMap.put("pepgaozhong_3", R.raw.pepgaozhong_3);
        lexiconMap.put("pepgaozhong_4", R.raw.pepgaozhong_4);
        lexiconMap.put("pepgaozhong_5", R.raw.pepgaozhong_5);
        lexiconMap.put("pepgaozhong_6", R.raw.pepgaozhong_6);
        lexiconMap.put("pepgaozhong_7", R.raw.pepgaozhong_7);
        lexiconMap.put("pepgaozhong_8", R.raw.pepgaozhong_8);
        lexiconMap.put("pepgaozhong_9", R.raw.pepgaozhong_9);
        lexiconMap.put("pepgaozhong_10", R.raw.pepgaozhong_10);
        lexiconMap.put("pepgaozhong_11", R.raw.pepgaozhong_11);
        lexiconMap.put("chuzhong_2", R.raw.chuzhong_2);
        lexiconMap.put("gaozhong_2", R.raw.gaozhong_2);
        lexiconMap.put("bec_2", R.raw.bec_2);
        lexiconMap.put("bec_3", R.raw.bec_3);
        lexiconMap.put("beishigaozhong_1", R.raw.beishigaozhong_1);
        lexiconMap.put("beishigaozhong_2", R.raw.beishigaozhong_2);
        lexiconMap.put("beishigaozhong_3", R.raw.beishigaozhong_3);
        lexiconMap.put("beishigaozhong_4", R.raw.beishigaozhong_4);
        lexiconMap.put("beishigaozhong_5", R.raw.beishigaozhong_5);
        lexiconMap.put("beishigaozhong_6", R.raw.beishigaozhong_6);
        lexiconMap.put("beishigaozhong_7", R.raw.beishigaozhong_7);
        lexiconMap.put("beishigaozhong_8", R.raw.beishigaozhong_8);
        lexiconMap.put("beishigaozhong_9", R.raw.beishigaozhong_9);
        lexiconMap.put("beishigaozhong_10", R.raw.beishigaozhong_10);
        lexiconMap.put("beishigaozhong_11", R.raw.beishigaozhong_11);

    }

    public static int getResourceId(String lexiconId) {
        return lexiconMap.getOrDefault(lexiconId, -1); // -1 表示未找到
    }

    /**
     * 加载词书内容到内存缓存
     */
    public static void loadLexicon(@NonNull Context context, @NonNull String lexiconId) {
        if (lexiconCache.containsKey(lexiconId)) {
            return; // 已加载过
        }

        specifiedLexiconId = lexiconId;

        int resourceId = getResourceId(lexiconId);
        if (resourceId == -1) {
            Log.e("LexiconResourceMap", "词书资源不存在: " + lexiconId);
            return;
        }

        // 加载指定的词书
        loadLexiconInternal(context, lexiconId, resourceId);

        // 启动新线程加载其他词书
        // new Thread(() -> {
        // Set<String> loadedLexicons = new HashSet<>();
        // loadedLexicons.add(lexiconId); // 不重复加载指定的词书
        //
        // for (Map.Entry<String, Integer> entry : lexiconMap.entrySet()) {
        // String id = entry.getKey();
        // int resId = entry.getValue();
        //
        // // 跳过已加载的词书
        // if (loadedLexicons.contains(id) || lexiconCache.containsKey(id)) {
        // continue;
        // }
        //
        // // 加载其他词书
        // loadLexiconInternal(context, id, resId);
        // }
        // }).start();
    }

    private static void loadLexiconInternal(@NonNull Context context, @NonNull String lexiconId, int resourceId) {
        try {
            InputStream is = context.getResources().openRawResource(resourceId);
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();

            String jsonStr = new String(buffer, "UTF-8");
            JSONArray jsonArray = new JSONArray(jsonStr);

            List<WordEntry> entries = new ArrayList<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                // 添加词书ID到WordEntry对象中
                obj.put("bookId", lexiconId);
                entries.add(new WordEntry(obj));
            }

            lexiconCache.put(lexiconId, entries);

            // 建立索引：rankIndex + globalWordIndex
            Map<Integer, WordEntry> rankMap = new HashMap<>();
            for (WordEntry entry : entries) {
                rankMap.put(entry.getWordRank(), entry);
                globalWordIndex.put(entry.getHeadWord().toLowerCase(), entry);
            }
            rankIndex.put(lexiconId, rankMap);

            Log.d("LexiconResourceMap", "成功加载词书: " + lexiconId + ", 共 " + entries.size() + " 个单词");

        } catch (Exception e) {
            e.printStackTrace();
            Log.e("LexiconResourceMap", "加载词书失败: " + lexiconId, e);
        }
    }

    public static List<JSONObject> loadBooksFromJson(@NonNull Context context) {
        List<JSONObject> books = new ArrayList<>();
        try {
            Resources res = context.getResources();
            InputStream inputStream = res.openRawResource(R.raw.book_list);
            Scanner scanner = new Scanner(inputStream).useDelimiter("\\A");
            String json = scanner.hasNext() ? scanner.next() : "";

            JSONObject root = new JSONObject(json);
            JSONObject data = root.getJSONObject("data");
            JSONArray normalBooksInfo = data.getJSONArray("normalBooksInfo");

            for (int i = 0; i < normalBooksInfo.length(); i++) {
                books.add(normalBooksInfo.getJSONObject(i));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return books;
    }

    public static String getLexiconName(String lexiconId, List<JSONObject> allBooks) {
        for (JSONObject book : allBooks) {
            try {
                if (book.getString("id").equals(lexiconId)) {
                    return book.getString("title");
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return "未知词书";
    }

    /**
     * 获取当前已加载的词书的名称
     * 
     * @return 当前已加载的词书的名称
     */
    public static String getLoadedLexiconName() {
        return specifiedLexiconId;
    }

    /**
     * 根据 wordRank 获取单词信息
     */
    public static WordEntry getWordByRank(@NonNull String lexiconId, int wordRank) {
        Map<Integer, WordEntry> rankMap = rankIndex.get(lexiconId);
        return rankMap != null ? rankMap.get(wordRank) : null;
    }

    /**
     * 获取词书的总单词数
     */
    public static int getTotalWords(@NonNull String lexiconId) {
        List<WordEntry> entries = lexiconCache.get(lexiconId);
        return entries != null ? entries.size() : 0;
    }

    /**
     * 在所有已加载的词书中查找单词
     * 
     * @param word 要查找的单词
     * @return 如果找到返回 WordEntry，否则返回 null
     */
    public static WordEntry findWordInAllLexicons(@NonNull String word) {
        return globalWordIndex.get(word.toLowerCase());
    }

    /*
     * 在所有已经加载的词书中随机抽取10个单词
     */
    public static String[] getRandomWords() {
        for (String lexiconId : lexiconCache.keySet()) {
            List<WordEntry> entries = lexiconCache.get(lexiconId);
            if (entries != null && !entries.isEmpty()) {
                int size = Math.min(10, entries.size());
                String[] randomWords = new String[size];
                for (int i = 0; i < size; i++) {
                    int index = (int) (Math.random() * entries.size());
                    randomWords[i] = entries.get(index).getHeadWord();
                }
                return randomWords;
            }
        }
        return new String[] { "not", "any", "words", "has", "been", "loaded", "yet", "or", "the", "lexicon", "is",
                "empty" };
    }

    /**
     * 获取指定词书的所有单词条目（用于生成选择题选项）
     */
    public static List<WordEntry> getAllEntries(@NonNull String lexiconId) {
        return lexiconCache.get(lexiconId);
    }

}