package com.deepsleep.memory.ui.main_view;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 每日学习状态管理：已完成单词追踪、SharedPreferences 持久化、跨天重置。
 *
 * 重要：所有持久化 key 均按 userId 隔离，防止切换账户后交叉污染。
 *       同时持久化单词详情（wordId + isCorrect），确保离开页面后总结卡片仍可重建完整单词列表。
 */
public class DailyStateManager {
    private static final String PREF_NAME = "UserPrefs";

    /** key 后缀模板：{userId} 在运行时替换 */
    private static final String SUFFIX_COMPLETED_IDS = "_completedWordIds";
    private static final String SUFFIX_LAST_DATE = "_completedLastDate";
    /** 持久化完成的单词详情 JSON: [{"id":123,"correct":true}, ...] */
    private static final String SUFFIX_COMPLETED_DETAILS = "_completedWordDetails";

    private final Set<Integer> completedWordIds = new HashSet<>();
    private final List<CompletedWordEntry> completedWordDetails = new ArrayList<>();
    private LocalDate lastDate = LocalDate.now();
    private final Context context;
    private final int userId;

    /** 被过滤掉的已完成单词快照（供总结卡片使用，仅内存） */
    private final List<WordCard> filteredCardSnapshot = new ArrayList<>();

    // ── 动态 key ──
    private String keyCompletedIds() { return userId + SUFFIX_COMPLETED_IDS; }
    private String keyLastDate()    { return userId + SUFFIX_LAST_DATE; }
    private String keyDetails()     { return userId + SUFFIX_COMPLETED_DETAILS; }

    public DailyStateManager(Context context, int userId) {
        this.context = context;
        this.userId = userId;
    }

    // ── 日期检查 ──

    /** 直接从 SharedPreferences 读取持久化日期来判断跨天，避免内存 lastDate 同步问题 */
    public boolean checkAndResetDailyState() {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String savedDate = sp.getString(keyLastDate(), "");
        LocalDate today = LocalDate.now();

        if (!savedDate.isEmpty() && !today.toString().equals(savedDate)) {
            Log.i("WordLearning", "[防重复] 跨天重置 userId=" + userId + ": " + savedDate + " → " + today);
            completedWordIds.clear();
            completedWordDetails.clear();
            filteredCardSnapshot.clear();
            clearPersisted();
            lastDate = today;
            return true;
        }
        lastDate = today;
        Log.i("WordLearning", "[防重复] userId=" + userId + " 同日，已完成 " + completedWordIds.size()
                + " 个单词, savedDate=" + savedDate);
        return false;
    }

    // ── 完成标记 ──

    public boolean isCompleted(int wordId) {
        return completedWordIds.contains(wordId);
    }

    /**
     * 标记单词已完成（仅持久化 wordId）
     */
    public void markCompleted(int wordId) {
        completedWordIds.add(wordId);
        saveToPrefs();
    }

    /**
     * 标记单词已完成并记录是否正确（推荐使用，用于总结卡片重建）
     */
    public void markCompletedWithResult(int wordId, boolean isCorrect) {
        completedWordIds.add(wordId);
        // 避免重复记录
        boolean alreadyRecorded = false;
        for (CompletedWordEntry entry : completedWordDetails) {
            if (entry.wordId == wordId) {
                entry.isCorrect = isCorrect;
                alreadyRecorded = true;
                break;
            }
        }
        if (!alreadyRecorded) {
            completedWordDetails.add(new CompletedWordEntry(wordId, isCorrect));
        }
        saveToPrefs();
    }

    public int getCompletedCount() {
        return completedWordIds.size();
    }

    /** 获取持久化的单词详情列表（用于总结卡片重建） */
    public List<CompletedWordEntry> getCompletedDetails() {
        return completedWordDetails;
    }

    // ── 过滤快照（仅内存，不持久化） ──

    public void addFilteredCard(WordCard card) {
        filteredCardSnapshot.add(card);
        // 同步更新详情（从过滤的已操作卡片中提取 isCorrect）
        boolean alreadyRecorded = false;
        for (CompletedWordEntry entry : completedWordDetails) {
            if (entry.wordId == card.word_id) {
                entry.isCorrect = card.isCorrect;
                alreadyRecorded = true;
                break;
            }
        }
        if (!alreadyRecorded) {
            completedWordDetails.add(new CompletedWordEntry(card.word_id, card.isCorrect));
        }
        saveToPrefs();
    }

    public void clearFilteredSnapshot() {
        filteredCardSnapshot.clear();
    }

    public List<WordCard> getFilteredSnapshot() {
        return filteredCardSnapshot;
    }

    // ── 持久化 ──

    public void loadFromPrefs() {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String saved = sp.getString(keyCompletedIds(), "");
        String savedDate = sp.getString(keyLastDate(), "");
        String savedDetails = sp.getString(keyDetails(), "");
        LocalDate today = LocalDate.now();

        completedWordIds.clear();
        completedWordDetails.clear();
        if (!saved.isEmpty() && today.toString().equals(savedDate)) {
            // 恢复 wordId 集合
            for (String idStr : saved.split(",")) {
                try {
                    completedWordIds.add(Integer.parseInt(idStr.trim()));
                } catch (NumberFormatException ignored) {
                }
            }
            // 恢复单词详情
            parseDetailsFromJson(savedDetails);
            lastDate = today;
            Log.i("WordLearning", "[防重复] userId=" + userId + " 从本地恢复了 "
                    + completedWordIds.size() + " 个已完成单词, " + completedWordDetails.size() + " 条详情");
        } else {
            // 跨天：只清内存+磁盘，保留旧的 lastDate 让 checkAndResetDailyState() 能检测到变更
            if (!savedDate.isEmpty()) {
                try { lastDate = LocalDate.parse(savedDate); } catch (Exception ignored) {}
            }
            clearPersisted();
            Log.i("WordLearning", "[防重复] userId=" + userId + " 无有效本地记录，lastDate="
                    + lastDate + "，从零开始");
        }
    }

    private void saveToPrefs() {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        StringBuilder sb = new StringBuilder();
        for (Integer id : completedWordIds) {
            if (sb.length() > 0) sb.append(",");
            sb.append(id);
        }
        String detailsJson = buildDetailsJson();
        sp.edit()
                .putString(keyCompletedIds(), sb.toString())
                .putString(keyLastDate(), lastDate.toString())
                .putString(keyDetails(), detailsJson)
                .apply();
        Log.i("WordLearning", "[防重复] userId=" + userId + " 持久化 "
                + completedWordIds.size() + " 个已完成单词, " + completedWordDetails.size() + " 条详情");
    }

    private void clearPersisted() {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                .remove(keyCompletedIds())
                .remove(keyLastDate())
                .remove(keyDetails())
                .apply();
    }

    // ── 单词详情 JSON 序列化 ──

    private String buildDetailsJson() {
        if (completedWordDetails.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (CompletedWordEntry entry : completedWordDetails) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{\"id\":").append(entry.wordId)
                    .append(",\"correct\":").append(entry.isCorrect).append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private void parseDetailsFromJson(String json) {
        completedWordDetails.clear();
        if (json == null || json.isEmpty()) return;
        try {
            // 简易 JSON 数组解析（避免引入 Gson 增加依赖）
            int pos = 0;
            while ((pos = json.indexOf("{\"id\":", pos)) >= 0) {
                int idStart = pos + 6;
                int idEnd = json.indexOf(",", idStart);
                if (idEnd < 0) break;
                int wordId = Integer.parseInt(json.substring(idStart, idEnd).trim());

                int correctStart = json.indexOf("\"correct\":", idEnd) + 10;
                int correctEnd = json.indexOf("}", correctStart);
                if (correctEnd < 0) break;
                boolean isCorrect = Boolean.parseBoolean(json.substring(correctStart, correctEnd).trim());

                completedWordDetails.add(new CompletedWordEntry(wordId, isCorrect));
                pos = correctEnd + 1;
            }
        } catch (Exception e) {
            Log.w("WordLearning", "[防重复] 解析单词详情 JSON 失败", e);
        }
    }

    // ── 内部类 ──

    /**
     * 持久化的已完成单词条目（轻量级，仅含 wordId + 是否答对）
     */
    public static class CompletedWordEntry {
        public final int wordId;
        public boolean isCorrect;

        public CompletedWordEntry(int wordId, boolean isCorrect) {
            this.wordId = wordId;
            this.isCorrect = isCorrect;
        }
    }
}
