package com.deepsleep.memory.handle_utils.lexicon;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.Ignore;

import com.deepsleep.memory.handle_utils.lexicon.db.LexiconBookEntity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 单词条目 —— 同时也是 Room 实体
 *
 * <p>
 * 涵盖 JSON 词书中<b>所有</b>字段，确保后续功能拓展无需重构数据库。 嵌套集合序列化为 JSON 列存储。
 * </p>
 */
@Entity(tableName = "word_entry", foreignKeys = @ForeignKey(entity = LexiconBookEntity.class, parentColumns = "book_id", childColumns = "book_id", onDelete = ForeignKey.CASCADE), indices = {
        @Index("book_id"), @Index(value = { "book_id", "word_rank" }, unique = true), @Index("head_word_lower") })
public class WordEntry {

    // ==================== Room 列 ====================

    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull
    @ColumnInfo(name = "book_id")
    private String bookId = "";

    @ColumnInfo(name = "word_rank")
    private int wordRank;

    @NonNull
    @ColumnInfo(name = "head_word")
    private String headWord = "";

    @NonNull
    @ColumnInfo(name = "head_word_lower")
    private String headWordLower = "";

    /** 全局唯一 ID，如 "CET4_1_1" */
    @NonNull
    @ColumnInfo(name = "word_id")
    private String wordId = "";

    // ---- 音标 & 发音 ----
    @NonNull
    @ColumnInfo(name = "us_phone")
    private String usPhone = "";
    @NonNull
    @ColumnInfo(name = "uk_phone")
    private String ukPhone = "";
    @NonNull
    @ColumnInfo(name = "phone")
    private String phone = "";
    @NonNull
    @ColumnInfo(name = "us_speech")
    private String usSpeech = "";
    @NonNull
    @ColumnInfo(name = "uk_speech")
    private String ukSpeech = "";
    @NonNull
    @ColumnInfo(name = "speech")
    private String speech = "";
    @ColumnInfo(name = "star")
    private int star;

    // ---- 释义 ----
    @NonNull
    @ColumnInfo(name = "pos")
    private String pos = "";

    @NonNull
    @ColumnInfo(name = "chinese_translations_json")
    private String chineseTranslationsJson = "[]";

    @NonNull
    @ColumnInfo(name = "english_definitions_json")
    private String englishDefinitionsJson = "[]";

    // ---- 例句 ----
    @NonNull
    @ColumnInfo(name = "example_sentences_json")
    private String exampleSentencesJson = "[]";

    // ---- 真题例句 ----
    @NonNull
    @ColumnInfo(name = "real_exam_sentences_json")
    private String realExamSentencesJson = "[]";

    // ---- 同近义词 ----
    @NonNull
    @ColumnInfo(name = "synonyms_json")
    private String synonymsJson = "[]";

    // ---- 同根词 ----
    @NonNull
    @ColumnInfo(name = "related_words_json")
    private String relatedWordsJson = "[]";

    // ==================== 非 Room 字段（懒加载缓存） ====================

    @Ignore
    private List<String> chineseTranslations;
    @Ignore
    private List<String> englishDefinitions;
    @Ignore
    private List<ExampleSentence> exampleSentences;
    @Ignore
    private List<RealExamSentence> realExamSentences;
    @Ignore
    private List<Synonym> synonyms;
    @Ignore
    private List<RelatedWord> relatedWords;

    // ==================== 构造方法 ====================

    /** Room 使用的无参构造 */
    public WordEntry() {
    }

    /**
     * 从 JSONObject 构造（Python 导入时的数据来源 & 兼容旧代码）
     */
    @Ignore
    public WordEntry(@NonNull JSONObject jsonObject) throws JSONException {
        this.headWord = jsonObject.getString("headWord");
        this.wordRank = jsonObject.getInt("wordRank");
        this.bookId = jsonObject.optString("bookId", "");
        this.headWordLower = headWord.toLowerCase();

        JSONObject contentObj = jsonObject.getJSONObject("content").getJSONObject("word").getJSONObject("content");

        this.wordId = jsonObject.getJSONObject("content").getJSONObject("word").optString("wordId", "");

        this.usPhone = contentObj.optString("usphone", "");
        this.ukPhone = contentObj.optString("ukphone", "");
        this.phone = contentObj.optString("phone", "");
        this.usSpeech = contentObj.optString("usspeech", "");
        this.ukSpeech = contentObj.optString("ukspeech", "");
        this.speech = contentObj.optString("speech", "");
        this.star = contentObj.optInt("star", 0);

        // 翻译
        JSONArray transArray = contentObj.getJSONArray("trans");
        this.chineseTranslations = new ArrayList<>();
        this.englishDefinitions = new ArrayList<>();
        JSONArray cnList = new JSONArray(), enList = new JSONArray();
        for (int i = 0; i < transArray.length(); i++) {
            JSONObject t = transArray.getJSONObject(i);
            if (t.has("tranCn")) {
                String cn = t.getString("tranCn");
                chineseTranslations.add(cn);
                JSONObject ci = new JSONObject();
                ci.put("tranCn", cn);
                ci.put("pos", t.optString("pos", ""));
                ci.put("descCn", t.optString("descCn", ""));
                cnList.put(ci);
            }
            if (t.has("tranOther")) {
                String en = t.getString("tranOther");
                englishDefinitions.add(en);
                JSONObject ei = new JSONObject();
                ei.put("tranOther", en);
                ei.put("pos", t.optString("pos", ""));
                enList.put(ei);
            }
            if (pos.isEmpty() && t.has("pos"))
                pos = t.getString("pos");
        }
        this.chineseTranslationsJson = cnList.toString();
        this.englishDefinitionsJson = enList.toString();

        // 例句
        this.exampleSentences = new ArrayList<>();
        JSONArray exArr = new JSONArray();
        if (contentObj.has("sentence")) {
            JSONArray sa = contentObj.getJSONObject("sentence").optJSONArray("sentences");
            if (sa != null) {
                for (int i = 0; i < sa.length(); i++) {
                    JSONObject s = sa.getJSONObject(i);
                    exampleSentences.add(new ExampleSentence(s.getString("sContent"), s.optString("sCn", ""),
                            s.optString("sContent_eng", ""), s.optString("sSpeech", "")));
                    JSONObject ei = new JSONObject();
                    ei.put("sContent", s.getString("sContent"));
                    ei.put("sCn", s.optString("sCn", ""));
                    ei.put("sContent_eng", s.optString("sContent_eng", ""));
                    ei.put("sSpeech", s.optString("sSpeech", ""));
                    exArr.put(ei);
                }
            }
        }
        this.exampleSentencesJson = exArr.toString();

        // 真题例句
        this.realExamSentences = new ArrayList<>();
        JSONArray reArr = new JSONArray();
        if (contentObj.has("realExamSentence")) {
            JSONArray ra = contentObj.getJSONObject("realExamSentence").optJSONArray("sentences");
            if (ra != null) {
                for (int i = 0; i < ra.length(); i++) {
                    JSONObject r = ra.getJSONObject(i);
                    JSONObject si = r.optJSONObject("sourceInfo");
                    String paper = "", level = "", year = "", type = "";
                    if (si != null) {
                        paper = si.optString("paper", "");
                        level = si.optString("level", "");
                        year = si.optString("year", "");
                        type = si.optString("type", "");
                    }
                    realExamSentences.add(new RealExamSentence(r.getString("sContent"), paper, level, year, type));
                    JSONObject ri = new JSONObject();
                    ri.put("sContent", r.getString("sContent"));
                    ri.put("paper", paper);
                    ri.put("level", level);
                    ri.put("year", year);
                    ri.put("type", type);
                    reArr.put(ri);
                }
            }
        }
        this.realExamSentencesJson = reArr.toString();

        // 同近义词
        this.synonyms = new ArrayList<>();
        JSONArray synArr = new JSONArray();
        if (contentObj.has("syno")) {
            JSONArray sy = contentObj.getJSONObject("syno").optJSONArray("synos");
            if (sy != null) {
                for (int i = 0; i < sy.length(); i++) {
                    JSONObject s = sy.getJSONObject(i);
                    List<String> hwds = new ArrayList<>();
                    JSONArray hArr = s.optJSONArray("hwds");
                    if (hArr != null)
                        for (int j = 0; j < hArr.length(); j++)
                            hwds.add(hArr.getJSONObject(j).optString("w", ""));
                    synonyms.add(new Synonym(s.optString("pos", ""), s.optString("tran", ""), hwds));
                    JSONObject si = new JSONObject();
                    si.put("pos", s.optString("pos", ""));
                    si.put("tran", s.optString("tran", ""));
                    JSONArray wArr = new JSONArray();
                    for (String w : hwds) {
                        JSONObject wo = new JSONObject();
                        wo.put("w", w);
                        wArr.put(wo);
                    }
                    si.put("hwds", wArr);
                    synArr.put(si);
                }
            }
        }
        this.synonymsJson = synArr.toString();

        // 同根词
        this.relatedWords = new ArrayList<>();
        JSONArray relArr = new JSONArray();
        if (contentObj.has("relWord")) {
            JSONArray rl = contentObj.getJSONObject("relWord").optJSONArray("rels");
            if (rl != null) {
                for (int i = 0; i < rl.length(); i++) {
                    JSONObject r = rl.getJSONObject(i);
                    List<RelWordItem> items = new ArrayList<>();
                    JSONArray wArr = r.optJSONArray("words");
                    if (wArr != null)
                        for (int j = 0; j < wArr.length(); j++) {
                            JSONObject w = wArr.getJSONObject(j);
                            items.add(new RelWordItem(w.optString("hwd", ""), w.optString("tran", "")));
                        }
                    relatedWords.add(new RelatedWord(r.optString("pos", ""), items));
                    JSONObject ri = new JSONObject();
                    ri.put("pos", r.optString("pos", ""));
                    JSONArray wiArr = new JSONArray();
                    for (RelWordItem rwi : items) {
                        JSONObject wi = new JSONObject();
                        wi.put("hwd", rwi.hwd);
                        wi.put("tran", rwi.tran);
                        wiArr.put(wi);
                    }
                    ri.put("words", wiArr);
                    relArr.put(ri);
                }
            }
        }
        this.relatedWordsJson = relArr.toString();
    }

    // ==================== Getter / Setter（Room 需要） ====================

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @NonNull
    public String getBookId() {
        return bookId;
    }

    public void setBookId(@NonNull String v) {
        this.bookId = v;
    }

    public int getWordRank() {
        return wordRank;
    }

    public void setWordRank(int v) {
        this.wordRank = v;
    }

    @NonNull
    public String getHeadWord() {
        return headWord;
    }

    public void setHeadWord(@NonNull String v) {
        this.headWord = v;
        this.headWordLower = v.toLowerCase();
    }

    @NonNull
    public String getHeadWordLower() {
        return headWordLower;
    }

    public void setHeadWordLower(@NonNull String v) {
        this.headWordLower = v;
    }

    @NonNull
    public String getWordId() {
        return wordId;
    }

    public void setWordId(@NonNull String v) {
        this.wordId = v;
    }

    @NonNull
    public String getUsPhone() {
        return usPhone;
    }

    public void setUsPhone(@NonNull String v) {
        this.usPhone = v;
    }

    @NonNull
    public String getUkPhone() {
        return ukPhone;
    }

    public void setUkPhone(@NonNull String v) {
        this.ukPhone = v;
    }

    @NonNull
    public String getPhone() {
        return phone;
    }

    public void setPhone(@NonNull String v) {
        this.phone = v;
    }

    @NonNull
    public String getUsSpeech() {
        return usSpeech;
    }

    public void setUsSpeech(@NonNull String v) {
        this.usSpeech = v;
    }

    @NonNull
    public String getUkSpeech() {
        return ukSpeech;
    }

    public void setUkSpeech(@NonNull String v) {
        this.ukSpeech = v;
    }

    @NonNull
    public String getSpeech() {
        return speech;
    }

    public void setSpeech(@NonNull String v) {
        this.speech = v;
    }

    public int getStar() {
        return star;
    }

    public void setStar(int v) {
        this.star = v;
    }

    @NonNull
    public String getPos() {
        return pos;
    }

    public void setPos(@NonNull String v) {
        this.pos = v;
    }

    @NonNull
    public String getChineseTranslationsJson() {
        return chineseTranslationsJson;
    }

    public void setChineseTranslationsJson(@NonNull String v) {
        this.chineseTranslationsJson = v;
    }

    @NonNull
    public String getEnglishDefinitionsJson() {
        return englishDefinitionsJson;
    }

    public void setEnglishDefinitionsJson(@NonNull String v) {
        this.englishDefinitionsJson = v;
    }

    @NonNull
    public String getExampleSentencesJson() {
        return exampleSentencesJson;
    }

    public void setExampleSentencesJson(@NonNull String v) {
        this.exampleSentencesJson = v;
    }

    @NonNull
    public String getRealExamSentencesJson() {
        return realExamSentencesJson;
    }

    public void setRealExamSentencesJson(@NonNull String v) {
        this.realExamSentencesJson = v;
    }

    @NonNull
    public String getSynonymsJson() {
        return synonymsJson;
    }

    public void setSynonymsJson(@NonNull String v) {
        this.synonymsJson = v;
    }

    @NonNull
    public String getRelatedWordsJson() {
        return relatedWordsJson;
    }

    public void setRelatedWordsJson(@NonNull String v) {
        this.relatedWordsJson = v;
    }

    // ==================== 公开业务方法（兼容旧接口） ====================

    public String getChineseTranslation() {
        ensureChineseLoaded();
        return String.join("; ", chineseTranslations);
    }

    public String getEnglishDefinition() {
        ensureEnglishLoaded();
        return String.join("; ", englishDefinitions);
    }

    public List<ExampleSentence> getExampleSentences() {
        ensureExampleSentencesLoaded();
        return exampleSentences;
    }

    public List<RealExamSentence> getRealExamSentences() {
        ensureRealExamLoaded();
        return realExamSentences;
    }

    public List<Synonym> getSynonyms() {
        ensureSynonymsLoaded();
        return synonyms;
    }

    public List<RelatedWord> getRelatedWords() {
        ensureRelatedWordsLoaded();
        return relatedWords;
    }

    @Deprecated
    public String getUsSpeechUrl() {
        return usSpeech;
    }

    @Deprecated
    public String getUkSpeechUrl() {
        return ukSpeech;
    }

    // ---- 懒加载辅助 ----

    private void ensureChineseLoaded() {
        if (chineseTranslations == null) {
            chineseTranslations = new ArrayList<>();
            try {
                JSONArray a = new JSONArray(chineseTranslationsJson);
                for (int i = 0; i < a.length(); i++)
                    chineseTranslations.add(a.getJSONObject(i).getString("tranCn"));
            } catch (JSONException ignored) {
            }
        }
    }

    private void ensureEnglishLoaded() {
        if (englishDefinitions == null) {
            englishDefinitions = new ArrayList<>();
            try {
                JSONArray a = new JSONArray(englishDefinitionsJson);
                for (int i = 0; i < a.length(); i++)
                    englishDefinitions.add(a.getJSONObject(i).getString("tranOther"));
            } catch (JSONException ignored) {
            }
        }
    }

    private void ensureExampleSentencesLoaded() {
        if (exampleSentences == null) {
            exampleSentences = new ArrayList<>();
            try {
                JSONArray a = new JSONArray(exampleSentencesJson);
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    exampleSentences.add(new ExampleSentence(o.getString("sContent"), o.optString("sCn", ""),
                            o.optString("sContent_eng", ""), o.optString("sSpeech", "")));
                }
            } catch (JSONException ignored) {
            }
        }
    }

    private void ensureRealExamLoaded() {
        if (realExamSentences == null) {
            realExamSentences = new ArrayList<>();
            try {
                JSONArray a = new JSONArray(realExamSentencesJson);
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    realExamSentences.add(new RealExamSentence(o.getString("sContent"), o.optString("paper", ""),
                            o.optString("level", ""), o.optString("year", ""), o.optString("type", "")));
                }
            } catch (JSONException ignored) {
            }
        }
    }

    private void ensureSynonymsLoaded() {
        if (synonyms == null) {
            synonyms = new ArrayList<>();
            try {
                JSONArray a = new JSONArray(synonymsJson);
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    List<String> h = new ArrayList<>();
                    JSONArray ha = o.optJSONArray("hwds");
                    if (ha != null)
                        for (int j = 0; j < ha.length(); j++)
                            h.add(ha.getJSONObject(j).optString("w", ""));
                    synonyms.add(new Synonym(o.optString("pos", ""), o.optString("tran", ""), h));
                }
            } catch (JSONException ignored) {
            }
        }
    }

    private void ensureRelatedWordsLoaded() {
        if (relatedWords == null) {
            relatedWords = new ArrayList<>();
            try {
                JSONArray a = new JSONArray(relatedWordsJson);
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    List<RelWordItem> items = new ArrayList<>();
                    JSONArray wa = o.optJSONArray("words");
                    if (wa != null)
                        for (int j = 0; j < wa.length(); j++) {
                            JSONObject w = wa.getJSONObject(j);
                            items.add(new RelWordItem(w.optString("hwd", ""), w.optString("tran", "")));
                        }
                    relatedWords.add(new RelatedWord(o.optString("pos", ""), items));
                }
            } catch (JSONException ignored) {
            }
        }
    }

    // ==================== 内部类 ====================

    /** 例句 */
    public static class ExampleSentence {
        private final String en, cn, enEng, speech;

        public ExampleSentence(String en, String cn) {
            this(en, cn, "", "");
        }

        public ExampleSentence(String en, String cn, String enEng, String speech) {
            this.en = en;
            this.cn = cn;
            this.enEng = enEng;
            this.speech = speech;
        }

        public String getEn() {
            return en;
        }

        public String getCn() {
            return cn;
        }

        public String getEnEng() {
            return enEng;
        }

        public String getSpeech() {
            return speech;
        }
    }

    /** 真题例句 */
    public static class RealExamSentence {
        private final String content, paper, level, year, type;

        public RealExamSentence(String content, String paper, String level, String year, String type) {
            this.content = content;
            this.paper = paper;
            this.level = level;
            this.year = year;
            this.type = type;
        }

        public String getContent() {
            return content;
        }

        public String getPaper() {
            return paper;
        }

        public String getLevel() {
            return level;
        }

        public String getYear() {
            return year;
        }

        public String getType() {
            return type;
        }

        public String getSourceLabel() {
            StringBuilder sb = new StringBuilder();
            if (!level.isEmpty())
                sb.append(level).append(" | ");
            if (!year.isEmpty())
                sb.append(year).append(" | ");
            if (!type.isEmpty())
                sb.append(type);
            return sb.toString();
        }
    }

    /** 同近义词 */
    public static class Synonym {
        private final String pos, tran;
        private final List<String> hwds;

        public Synonym(String pos, String tran, List<String> hwds) {
            this.pos = pos;
            this.tran = tran;
            this.hwds = hwds;
        }

        public String getPos() {
            return pos;
        }

        public String getTran() {
            return tran;
        }

        public List<String> getHwds() {
            return hwds;
        }
    }

    /** 同根词 */
    public static class RelatedWord {
        private final String pos;
        private final List<RelWordItem> words;

        public RelatedWord(String pos, List<RelWordItem> words) {
            this.pos = pos;
            this.words = words;
        }

        public String getPos() {
            return pos;
        }

        public List<RelWordItem> getWords() {
            return words;
        }
    }

    /** 同根词条目 */
    public static class RelWordItem {
        private final String hwd, tran;

        public RelWordItem(String hwd, String tran) {
            this.hwd = hwd;
            this.tran = tran;
        }

        public String getHwd() {
            return hwd;
        }

        public String getTran() {
            return tran;
        }
    }
}
