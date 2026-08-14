package com.deepsleep.memory.network;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import org.json.JSONObject;

public class GetDataByThread {
    /** 网络层失败（null 响应/异常）后的额外重试次数，共 1 + MAX_RETRIES 次尝试 */
    private static final int MAX_RETRIES = 2;
    /** 首次重试等待时间（指数退避基数：1s、2s） */
    private static final long RETRY_BASE_DELAY_MS = 1000L;

    private final String url_path;

    public GetDataByThread(String path) {
        ApiConstants.setEnvironment(ApiConstants.Environment.TEST);
        url_path = ApiConstants.getFullUrl(path);
    }

    public String getUrl_path() {
        return url_path;
    }

    /**
     * 统一异步调用入口：网络层失败（响应为 null 或抛异常）时静默自动重试，
     * 指数退避（1s、2s），全部失败后才回调 fail；业务错误（非 null 响应）不重试。
     */
    private void asyncCall(Handler h, int ok, int fail, String tag, Callable callable) {
        new Thread(() -> {
            String result = null;
            for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
                try {
                    if (tag != null && attempt == 0) {
                        Log.i(tag, "--------" + url_path);
                    }
                    result = callable.call(url_path);
                    if (tag != null) {
                        Log.i(tag, "--------" + result);
                    }
                    if (result != null) {
                        Message m = Message.obtain();
                        m.what = ok;
                        m.obj = result;
                        h.sendMessage(m);
                        return;
                    }
                    // result == null：连接层失败（如 Connection reset / 超时），可重试
                    if (tag != null) {
                        Log.w(tag, "空响应，attempt=" + (attempt + 1));
                    }
                } catch (Exception e) {
                    if (tag != null)
                        Log.e(tag, "Error: " + e.getMessage());
                    else
                        e.printStackTrace();
                }

                // 还有重试机会：指数退避后重试
                if (attempt < MAX_RETRIES) {
                    long delay = RETRY_BASE_DELAY_MS << attempt; // 1s、2s
                    if (tag != null) {
                        Log.w(tag, "第 " + (attempt + 1) + " 次尝试失败，" + delay + "ms 后重试");
                    }
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            // 全部尝试失败
            h.sendEmptyMessage(fail);
        }).start();
    }

    private void asyncGet1H(Handler h, int ok, int fail, String tag, String hKey, String hVal) {
        asyncCall(h, ok, fail, tag, url -> HttpManager.doHttpGetOneHeader(url, hKey, hVal));
    }

    private void asyncGet1HFull(Handler h, int ok, int fail, String tag, String fullUrl, String hKey, String hVal) {
        asyncCall(h, ok, fail, tag, u -> HttpManager.doHttpGetOneHeader(fullUrl, hKey, hVal));
    }

    private void asyncPostJson(Handler h, int ok, int fail, String tag, JSONObject json) {
        asyncCall(h, ok, fail, tag, url -> HttpManager.doHttpPost(url, json));
    }

    interface Callable {
        String call(String url) throws Exception;
    }

    // ── Auth ──
    public void login(Handler h, int ok, int fail, String phone, String pwd) {
        asyncCall(h, ok, fail, "Login", url -> HttpManager.doHttpGetTwoHeader(url, "phone", phone, "password", pwd));
    }

    public void register(Handler h, int ok, int fail, String phone, String pwd, String nick, String avatar) {
        asyncCall(h, ok, fail, "Register", url -> HttpManager.doHttpPostFourHeader(url, "phone", phone, "password", pwd,
                "nickname", nick, "avatarUrl", avatar));
    }

    public void getUserInfo(Handler h, int ok, int fail, String uid) {
        asyncGet1H(h, ok, fail, null, "userId", uid);
    }

    public void uploadUserAvatar(Handler h, int ok, int fail, int uid, Uri imageUri, Context ctx) {
        asyncCall(h, ok, fail, "UploadUserAvatar",
                url -> HttpManager.doHttpPostWithImageAndParams(url, ctx, imageUri, "userId", String.valueOf(uid)));
    }

    public void updateUserNickname(Handler h, int ok, int fail, String uid, String nick) {
        asyncCall(h, ok, fail, "UpdateUserNickname",
                url -> HttpManager.doHttpPostWithTextBody(url, "userId", uid, nick));
    }

    // ── Plan ──
    public void getPlan(Handler h, int ok, int fail, String uid) {
        asyncGet1H(h, ok, fail, "GetPlan", "userId", uid);
    }

    public void planUpload(Handler h, int ok, int fail, JSONObject planData) {
        asyncPostJson(h, ok, fail, "PlanUpload", planData);
    }

    public void getPlanDetails(Handler h, int ok, int fail, int uid) {
        asyncGet1H(h, ok, fail, null, "userId", String.valueOf(uid));
    }

    public void getPlanList(Handler h, int ok, int fail, String uid) {
        asyncGet1H(h, ok, fail, "GetPlanList", "userId", uid);
    }

    public void updateCurrentPlan(Handler h, int ok, int fail, int uid, int planId) {
        asyncCall(h, ok, fail, "UpdateCurrentPlan", url -> HttpManager.doHttpPostTwoHeader(url, "userId",
                String.valueOf(uid), "planId", String.valueOf(planId)));
    }

    public void getUserAllLearningPlans(Handler h, int ok, int fail, String uid) {
        asyncGet1H(h, ok, fail, "AllLearningPlans", "userId", uid);
    }

    // ── Learning ──
    public void uploadWordStudyLog(Handler h, int ok, int fail, int uid, int wid, String lexiconId, String headWord,
            boolean mastered) {
        try {
            JSONObject j = new JSONObject();
            j.put("userId", uid);
            j.put("wordId", wid);
            j.put("lexiconId", lexiconId);
            j.put("headWord", headWord);
            j.put("isMastered", mastered);
            asyncPostJson(h, ok, fail, "StudyLog", j);
        } catch (Exception e) {
            h.sendEmptyMessage(fail);
        }
    }

    public void updateLearningListCompletion(Handler h, int ok, int fail, int uid, String lexiconId, int studyDate,
            boolean completed) {
        try {
            JSONObject j = new JSONObject();
            j.put("userId", uid);
            j.put("lexiconId", lexiconId);
            j.put("studyDate", studyDate);
            j.put("isCompleted", completed);
            asyncPostJson(h, ok, fail, "UpdateCompletion", j);
        } catch (Exception e) {
            h.sendEmptyMessage(fail);
        }
    }

    public void submitAnswer(Handler h, int ok, int fail, int uid, int wid, String lexiconId, String headWord,
            boolean isCorrect, long rtMs, String mode, String submitId) {
        try {
            JSONObject j = new JSONObject();
            j.put("userId", uid);
            j.put("wordId", wid);
            j.put("lexiconId", lexiconId);
            j.put("headWord", headWord);
            j.put("isCorrect", isCorrect);
            j.put("responseTimeMs", rtMs);
            j.put("studyMode", mode);
            if (submitId != null && !submitId.isEmpty()) {
                j.put("submitId", submitId);
            }
            asyncPostJson(h, ok, fail, "SubmitAnswer", j);
        } catch (Exception e) {
            h.sendEmptyMessage(fail);
        }
    }

    public void submitAnswerInput(Handler h, int ok, int fail, int uid, int wid, String lexiconId, String headWord,
            boolean isCorrect, long rtMs, String userAnswer, String referenceDefinition, String pos, String submitId) {
        try {
            JSONObject j = new JSONObject();
            j.put("userId", uid);
            j.put("wordId", wid);
            j.put("lexiconId", lexiconId);
            j.put("headWord", headWord);
            j.put("isCorrect", isCorrect);
            j.put("responseTimeMs", rtMs);
            j.put("studyMode", "input");
            j.put("userAnswer", userAnswer);
            j.put("referenceDefinition", referenceDefinition);
            j.put("word", headWord);
            j.put("pos", pos);
            if (submitId != null && !submitId.isEmpty()) {
                j.put("submitId", submitId);
            }
            asyncPostJson(h, ok, fail, "SubmitAnswer", j);
        } catch (Exception e) {
            h.sendEmptyMessage(fail);
        }
    }

    public void getSchedulePreview(Handler h, int ok, int fail, String uid) {
        asyncGet1H(h, ok, fail, "SchedulePreview", "userId", uid);
    }

    public void updatePreference(Handler h, int ok, int fail, int uid, Integer dailyNew, String modePref,
            Double retentionTarget, Integer maxReviewWords) {
        try {
            JSONObject j = new JSONObject();
            j.put("userId", uid);
            if (dailyNew != null)
                j.put("dailyNewWords", dailyNew);
            if (modePref != null)
                j.put("studyModePreference", modePref);
            if (retentionTarget != null)
                j.put("fsrsRetentionTarget", retentionTarget);
            if (maxReviewWords != null)
                j.put("fsrsMaxReviewWords", maxReviewWords);
            asyncCall(h, ok, fail, "UpdatePreference", url -> HttpManager.doHttpPut(url, j));
        } catch (Exception e) {
            h.sendEmptyMessage(fail);
        }
    }

    // ── 用户级设置 ──
    /** 获取用户级设置（JSON 对象，无记录时返回空对象） */
    public void getUserSettings(Handler h, int ok, int fail, String uid) {
        asyncGet1H(h, ok, fail, "GetUserSettings", "userId", uid);
    }

    /** 更新用户级设置 {userId, settings:{...}}（服务端增量合并） */
    public void updateUserSettings(Handler h, int ok, int fail, int uid, JSONObject settings) {
        try {
            JSONObject j = new JSONObject();
            j.put("userId", uid);
            j.put("settings", settings);
            asyncCall(h, ok, fail, "UpdateUserSettings", url -> HttpManager.doHttpPut(url, j));
        } catch (Exception e) {
            h.sendEmptyMessage(fail);
        }
    }

    // ── Words ──
    public void fetchWeakWords(Handler h, int ok, int fail, String uid) {
        asyncGet1H(h, ok, fail, null, "userId", uid);
    }

    public void fetchFavoriteWords(Handler h, int ok, int fail, String uid) {
        asyncGet1H(h, ok, fail, null, "userId", uid);
    }

    public void updateFavorite(Handler h, int ok, int fail, String uid, int wid, String lexiconId, String headWord,
            boolean fav) {
        asyncCall(h, ok, fail, "UpdateFavorite", url -> HttpManager.doHttpPostFiveHeader(url, "userId", uid, "wordId",
                String.valueOf(wid), "lexiconId", lexiconId, "headWord", headWord, "isFavorite", String.valueOf(fav)));
    }

    // ── Composition ──
    public void extractTextFromImageUri(Handler h, int ok, int fail, Uri imageUri, Context ctx) {
        asyncCall(h, ok, fail, "ExtractText", url -> HttpManager.doHttpPostWithImageUri(url, imageUri, ctx));
    }

    public void correctText(Handler h, int ok, int fail, String text, int uid) {
        asyncCall(h, ok, fail, "CorrectText",
                url -> HttpManager.doHttpPostWithTextBody(url, "userId", String.valueOf(uid), text));
    }

    public void fetchHistoryRecords(Handler h, int ok, int fail, int uid) {
        if (uid <= 0) {
            h.sendEmptyMessage(fail);
            return;
        }
        asyncGet1H(h, ok, fail, null, "userId", String.valueOf(uid));
    }

    public void getDailyReading(Handler h, int ok, int fail, String uid) {
        asyncGet1H(h, ok, fail, null, "userId", uid);
    }

    // ── Pronunciation ──
    public void getPronunciationWords(Handler h, int ok, int fail, String uid, int bookId, int phraseCount,
            int sentenceCount) {
        String fu = url_path + "?wordBookId=" + bookId + "&phraseCount=" + phraseCount + "&sentenceCount="
                + sentenceCount;
        asyncGet1HFull(h, ok, fail, "PronunciationWords", fu, "userId", uid);
    }

    public void correctPronunciation(Handler h, int ok, int fail, Uri audioUri, String refText, Context ctx) {
        asyncCall(h, ok, fail, "CorrectPronunciation",
                url -> HttpManager.doHttpPostWithAudioAndText(url, audioUri, refText, ctx));
    }

    // ── Conversation ──
    public void startConversation(Handler h, int ok, int fail, String uid) {
        asyncCall(h, ok, fail, "ConversationStart", url -> HttpManager.doHttpPostOneHeader(url, "userId", uid));
    }

    public void sendConversationText(Handler h, int ok, int fail, String uid, String sid, String text) {
        asyncCall(h, ok, fail, "ConversationMsg", url -> {
            java.util.HashMap<String, String> f = new java.util.HashMap<>();
            f.put("sessionId", sid);
            f.put("text", text);
            return HttpManager.doHttpPostMultipart(url, f, null, null, null, null, "userId", uid, null);
        });
    }

    public void sendConversationAudio(Handler h, int ok, int fail, String uid, String sid, Uri audioUri, Context ctx) {
        asyncCall(h, ok, fail, "ConversationAudio", url -> {
            java.util.HashMap<String, String> f = new java.util.HashMap<>();
            f.put("sessionId", sid);
            return HttpManager.doHttpPostMultipart(url, f, "audio", audioUri, "recording.wav", "audio/wav", "userId",
                    uid, ctx);
        });
    }

    public void getConversationHistory(Handler h, int ok, int fail, String uid, String sid) {
        asyncGet1HFull(h, ok, fail, "ConversationHistory", url_path + "?sessionId=" + sid, "userId", uid);
    }

    public void deleteConversation(Handler h, int ok, int fail, String uid, String sid) {
        String fu = url_path + "?sessionId=" + sid;
        asyncCall(h, ok, fail, "ConvDelete", u -> HttpManager.doHttpPostOneHeader(fu, "userId", uid));
    }

    public void getConversationSessions(Handler h, int ok, int fail, String uid) {
        asyncGet1H(h, ok, fail, "ConvSessions", "userId", uid);
    }

    public void getLastConversation(Handler h, int ok, int fail, String uid) {
        asyncGet1H(h, ok, fail, "ConvLast", "userId", uid);
    }

    // ── Conversation Enhanced (AI 对话智能化升级) ──
    public void getScenarios(Handler h, int ok, int fail, String uid) {
        asyncGet1H(h, ok, fail, "Scenarios", "userId", uid);
    }

    public void getRecommendedScenarios(Handler h, int ok, int fail, String uid) {
        asyncGet1H(h, ok, fail, "ScnRecom", "userId", uid);
    }

    public void getRecommendedTopics(Handler h, int ok, int fail, String uid, int count) {
        asyncGet1HFull(h, ok, fail, "Topics", url_path + "?count=" + count, "userId", uid);
    }

    public void startScenario(Handler h, int ok, int fail, String uid, String sid, String scenarioId) {
        asyncCall(h, ok, fail, "StartScenario", url -> {
            String fu = url + "?sessionId=" + sid + "&scenarioId=" + scenarioId;
            return HttpManager.doHttpPostOneHeader(fu, "userId", uid);
        });
    }

    public void startTopic(Handler h, int ok, int fail, String uid, String sid, String topicId) {
        asyncCall(h, ok, fail, "StartTopic", url -> {
            String fu = url + "?sessionId=" + sid + "&topicId=" + topicId;
            return HttpManager.doHttpPostOneHeader(fu, "userId", uid);
        });
    }

    public void switchMode(Handler h, int ok, int fail, String uid, String sid, String mode) {
        asyncCall(h, ok, fail, "SwitchMode", url -> {
            String fu = url + "/" + sid + "/mode?mode=" + mode;
            return HttpManager.doHttpPostOneHeader(fu, "userId", uid);
        });
    }

    // ── TTS ──
    public void synthesizeTts(Handler h, int ok, int fail, String text, Context ctx) {
        new Thread(() -> {
            try {
                JSONObject j = new JSONObject();
                j.put("text", text);
                j.put("language", "en");
                String wav = HttpManager.doHttpPostDownloadWav(url_path, j, ctx);
                if (wav != null) {
                    Message m = Message.obtain();
                    m.what = ok;
                    m.obj = wav;
                    h.sendMessage(m);
                } else
                    h.sendEmptyMessage(fail);
            } catch (Exception e) {
                Log.e("TtsSynthesize", "Error: " + e.getMessage());
                h.sendEmptyMessage(fail);
            }
        }).start();
    }

    // ── Evaluation ──
    public void getEvaluationDashboard(Handler h, int ok, int fail, String uid) {
        asyncGet1H(h, ok, fail, "EvalDashboard", "userId", uid);
    }

    public void getEvaluationTrend(Handler h, int ok, int fail, String uid, int days) {
        asyncGet1HFull(h, ok, fail, "EvalTrend", url_path + "?days=" + days, "userId", uid);
    }

    public void getEvaluationWeeklyReport(Handler h, int ok, int fail, String uid) {
        asyncGet1H(h, ok, fail, "EvalWeekly", "userId", uid);
    }

    public void getEvaluationAiSuggestion(Handler h, int ok, int fail, String uid) {
        asyncGet1H(h, ok, fail, "EvalAiSug", "userId", uid);
    }

    /**
     * 获取 AI 建议（支持 refresh 参数强制刷新）
     */
    public void getEvaluationAiSuggestion(Handler h, int ok, int fail, String uid, boolean refresh) {
        asyncGet1HFull(h, ok, fail, "EvalAiSug", url_path + "?refresh=" + refresh, "userId", uid);
    }

    public void getEvaluationDeepAnalysis(Handler h, int ok, int fail, String uid) {
        asyncGet1H(h, ok, fail, "EvalDeep", "userId", uid);
    }

    public void getEvaluationWeakWords(Handler h, int ok, int fail, String uid, int topN) {
        asyncGet1HFull(h, ok, fail, null, url_path + "?topN=" + topN, "userId", uid);
    }

    public void getEvaluationCriticalWords(Handler h, int ok, int fail, String uid) {
        asyncGet1H(h, ok, fail, null, "userId", uid);
    }

    public void getEvaluationMasteryDistribution(Handler h, int ok, int fail, String uid) {
        asyncGet1H(h, ok, fail, null, "userId", uid);
    }

    public void getEvaluationFsrsTrend(Handler h, int ok, int fail, String uid, int days) {
        asyncGet1HFull(h, ok, fail, null, url_path + "?days=" + days, "userId", uid);
    }

    @Deprecated
    public void aiConversation(Handler h, int ok, int fail, String content) {
        try {
            JSONObject j = new JSONObject();
            j.put("content", content);
            asyncPostJson(h, ok, fail, "AiConversation", j);
        } catch (Exception e) {
            Log.e("AiConversation", "Error: " + e.getMessage());
            h.sendEmptyMessage(fail);
        }
    }
}
