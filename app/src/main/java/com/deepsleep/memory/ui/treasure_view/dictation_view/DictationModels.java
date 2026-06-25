package com.deepsleep.memory.ui.treasure_view.dictation_view;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * 听写练习模块 - 数据模型
 */
public class DictationModels {

    /**
     * 听写任务
     */
    public static class DictationTask {
        public String taskId;
        public String status; // PENDING / READY / SUBMITTED
        public String cooldownUntil;
        public int totalWords;
        public int lexiconId;
        public List<DictationItem> items;

        public static DictationTask fromJson(JSONObject json) {
            DictationTask task = new DictationTask();
            try {
                task.taskId = json.getString("taskId");
                task.status = json.optString("status", "PENDING");
                task.cooldownUntil = json.optString("cooldownUntil", "");
                task.totalWords = json.optInt("totalWords", 0);
                task.lexiconId = json.optInt("lexiconId", 0);
                task.items = new ArrayList<>();
                JSONArray itemsArr = json.optJSONArray("items");
                if (itemsArr != null) {
                    for (int i = 0; i < itemsArr.length(); i++) {
                        task.items.add(DictationItem.fromJson(itemsArr.getJSONObject(i)));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return task;
        }
    }

    /**
     * 单个听写项
     */
    public static class DictationItem {
        public int index;
        public long wordId;
        public String headWord;
        public int level; // 1=单词, 2=短语, 3=句子
        public String contextText; // 语境文本 (L2/L3)
        public String targetForm; // 期望拼写
        public String posHint; // 词性提示 (服务端可能为null)
        public String audioUrl;
        public boolean audioReady;

        /** 本地补全字段 (由客户端从本地词书查询填充) */
        public String localPos; // 本地词性
        public String localMeaning; // 本地释义

        public static DictationItem fromJson(JSONObject json) {
            DictationItem item = new DictationItem();
            try {
                item.index = json.optInt("index", 0);
                item.wordId = json.optLong("wordId", 0);
                item.headWord = json.optString("headWord", "");
                item.level = json.optInt("level", 1);
                item.contextText = json.optString("contextText", null);
                if ("null".equals(item.contextText))
                    item.contextText = null;
                item.targetForm = json.optString("targetForm", "");
                item.posHint = json.optString("posHint", null);
                if ("null".equals(item.posHint))
                    item.posHint = null;
                item.audioUrl = json.optString("audioUrl", null);
                if ("null".equals(item.audioUrl))
                    item.audioUrl = null;
                item.audioReady = json.optBoolean("audioReady", false);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return item;
        }
    }

    /**
     * 提交结果
     */
    public static class DictationSubmitResult {
        public String taskId;
        public int totalWords;
        public int correctCount;
        public int score;
        public List<DictationSummary> summary;
        public List<Long> wrongWordIds;

        public static DictationSubmitResult fromJson(JSONObject json) {
            DictationSubmitResult result = new DictationSubmitResult();
            try {
                result.taskId = json.optString("taskId", "");
                result.totalWords = json.optInt("totalWords", 0);
                result.correctCount = json.optInt("correctCount", 0);
                result.score = json.optInt("score", 0);
                result.summary = new ArrayList<>();
                JSONArray summaryArr = json.optJSONArray("summary");
                if (summaryArr != null) {
                    for (int i = 0; i < summaryArr.length(); i++) {
                        result.summary.add(DictationSummary.fromJson(summaryArr.getJSONObject(i)));
                    }
                }
                result.wrongWordIds = new ArrayList<>();
                JSONArray wrongArr = json.optJSONArray("wrongWordIds");
                if (wrongArr != null) {
                    for (int i = 0; i < wrongArr.length(); i++) {
                        result.wrongWordIds.add(wrongArr.getLong(i));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return result;
        }
    }

    /**
     * 单题评分摘要
     */
    public static class DictationSummary {
        public int index;
        public long wordId;
        public String headWord;
        public String targetForm;
        public String userAnswer;
        public int score; // 1-4
        public boolean correct;

        public static DictationSummary fromJson(JSONObject json) {
            DictationSummary s = new DictationSummary();
            try {
                s.index = json.optInt("index", 0);
                s.wordId = json.optLong("wordId", 0);
                s.headWord = json.optString("headWord", "");
                s.targetForm = json.optString("targetForm", "");
                s.userAnswer = json.optString("userAnswer", "");
                s.score = json.optInt("score", 1);
                s.correct = json.optBoolean("correct", false);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return s;
        }
    }

    /**
     * 历史记录项
     */
    public static class DictationHistoryItem {
        public String taskId;
        public String createdAt;
        public int totalWords;
        public int correctCount;
        public int accuracy;
        public String status;
        public int lexiconId;

        public static DictationHistoryItem fromJson(JSONObject json) {
            DictationHistoryItem item = new DictationHistoryItem();
            try {
                item.taskId = json.optString("taskId", "");
                item.createdAt = json.optString("createdAt", "");
                item.totalWords = json.optInt("totalWords", 0);
                item.correctCount = json.optInt("correctCount", 0);
                item.accuracy = json.optInt("accuracy", 0);
                item.status = json.optString("status", "");
                item.lexiconId = json.optInt("lexiconId", 0);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return item;
        }
    }

    /**
     * 历史记录列表
     */
    public static class DictationHistoryResult {
        public List<DictationHistoryItem> list;
        public int total;
        public int page;
        public int size;

        public static DictationHistoryResult fromJson(JSONObject json) {
            DictationHistoryResult result = new DictationHistoryResult();
            try {
                result.list = new ArrayList<>();
                JSONArray listArr = json.optJSONArray("list");
                if (listArr != null) {
                    for (int i = 0; i < listArr.length(); i++) {
                        result.list.add(DictationHistoryItem.fromJson(listArr.getJSONObject(i)));
                    }
                }
                result.total = json.optInt("total", 0);
                result.page = json.optInt("page", 1);
                result.size = json.optInt("size", 10);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return result;
        }
    }
}
