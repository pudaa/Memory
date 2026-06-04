
package com.deepsleep.memory.handle_utils.lexicon;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class WordEntry {
    private String headWord;         // 单词拼写
    private int wordRank;            // 单词序号
    private String bookId;           // 书籍ID

    // 音标相关
    private String usPhone;          // 美式音标
    private String ukPhone;          // 英式音标
    private String usSpeechUrl;      // 美音发音URL参数
    private String ukSpeechUrl;      // 英音发音URL参数

    // 中文释义与英英释义
    private List<String> chineseTranslations = new ArrayList<>();
    private List<String> englishDefinitions = new ArrayList<>();

    // 词性（取第一个释义的词性，如 "v", "n"）
    private String pos = "";

    private List<ExampleSentence> exampleSentences = new ArrayList<>();

    public WordEntry(JSONObject jsonObject) throws JSONException {
        this.headWord = jsonObject.getString("headWord");
        this.wordRank = jsonObject.getInt("wordRank");
        this.bookId = jsonObject.optString("bookId", "");

        JSONObject contentObj = jsonObject.getJSONObject("content").getJSONObject("word").getJSONObject("content");

        this.usPhone = contentObj.optString("usphone", "");
        this.ukPhone = contentObj.optString("ukphone", "");
        this.usSpeechUrl = contentObj.optString("usspeech", "");
        this.ukSpeechUrl = contentObj.optString("ukspeech", "");

        // 提取翻译释义
        JSONArray transArray = contentObj.getJSONArray("trans");
        for (int i = 0; i < transArray.length(); i++) {
            JSONObject transObj = transArray.getJSONObject(i);
            if (transObj.has("tranCn")) {
                chineseTranslations.add(transObj.getString("tranCn"));
            }
            if (transObj.has("tranOther")) {
                englishDefinitions.add(transObj.getString("tranOther"));
            }
            // 取第一个 trans 的词性
            if (pos.isEmpty() && transObj.has("pos")) {
                pos = transObj.getString("pos");
            }
        }

        // 提取例句
        if (contentObj.has("sentence") && contentObj.getJSONObject("sentence").has("sentences")) {
            JSONArray sentencesArray = contentObj.getJSONObject("sentence").getJSONArray("sentences");
            for (int i = 0; i < sentencesArray.length(); i++) {
                JSONObject sentenceObj = sentencesArray.getJSONObject(i);
                String enSentence = sentenceObj.getString("sContent");
                String cnSentence = sentenceObj.getString("sCn");
                exampleSentences.add(new ExampleSentence(enSentence, cnSentence));
            }
        }
    }

    // 获取中文释义（合并多个）
    public String getChineseTranslation() {
        return String.join("; ", chineseTranslations);
    }

    // 获取英文释义（合并多个）
    public String getEnglishDefinition() {
        return String.join("; ", englishDefinitions);
    }

    // 获取词性
    public String getPos() {
        return pos;
    }

    // 获取例句列表
    public List<ExampleSentence> getExampleSentences() {
        return exampleSentences;
    }

    // Getter 方法
    public String getHeadWord() {
        return headWord;
    }

    public int getWordRank() {
        return wordRank;
    }

    public String getBookId() {
        return bookId;
    }

    public String getUsPhone() {
        return usPhone;
    }

    public String getUkPhone() {
        return ukPhone;
    }

    public String getUsSpeechUrl() {
        return usSpeechUrl;
    }

    public String getUkSpeechUrl() {
        return ukSpeechUrl;
    }

    // 内部类：例句
    public static class ExampleSentence {
        private final String en;
        private final String cn;

        public ExampleSentence(String en, String cn) {
            this.en = en;
            this.cn = cn;
        }

        public String getEn() {
            return en;
        }

        public String getCn() {
            return cn;
        }
    }
}
