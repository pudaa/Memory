package com.deepsleep.memory.ui.main_view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.*;
import android.util.Log;
import android.view.*;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.deepsleep.memory.R;
import com.deepsleep.memory.settings.UserSettingsManager;
import com.deepsleep.memory.ui.extra_view.plan_view.PlanCheckActivity;
import com.deepsleep.memory.ui.extra_view.word_search_view.SearchingActivity;
import com.deepsleep.memory.handle_utils.lexicon.LexiconResourceMap;
import com.deepsleep.memory.handle_utils.lexicon.WordEntry;
import com.deepsleep.memory.network.GetDataByThread;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class WordLearningFragment extends Fragment implements WordCardContainer.OnCardSwipedListener,
        WordCardContainer.OnCardLongPressedListener, UserSettingsManager.OnSettingsChangedListener {

    private WordCardContainer cardContainer;
    private final List<WordCard> wordCards = new ArrayList<>();

    private TextView tvDayCount;
    private ImageButton btnPlan;
    private ImageButton btnSearch;

    String lexiconId;
    private String studyMode;
    private int dailyNewWordCount = 0;
    private int studyDay = 0;

    /** 今日已完成复习数（方案B：服务端按"今日累计"封顶，用于标题栏展示进度） */
    private int reviewsDoneToday = 0;
    /** 每日复习上限（fsrsMaxReviewWords），0 表示服务端未返回 */
    private int reviewLimit = 0;

    private int totalWords = 0;
    private int operatedCount = 0;
    private int currentCardIndex = 0;

    private static final String PREF_NAME = "UserPrefs";
    private static final String KEY_USER_ID = "userId";
    private UserSettingsManager userSettingsManager;
    private int slideFlag = -1;
    private int userId;

    static final int msg_success = 1;
    static final int msg_failed = -1;
    private final MyHandler myHandler = new MyHandler();

    // ── 提取的模块 ──
    private DailyStateManager dailyState;
    private ExerciseCardFactory cardFactory;
    private SummaryCardBuilder summaryBuilder;

    /** 防止 onCreateView 和 onResume 重复触发 loadTodayTask */
    private boolean isLoadingTask = false;

    /** 当前总结卡片视图引用（用于避免重复创建，更新时移除旧卡片再添加新卡片） */
    private View summaryCardView = null;

    // ── 前台跨夜检测 ──
    /** 上一次检测到的日期（用于前台跨天检测），null 表示尚未记录 */
    private String lastKnownDate = null;
    /** 专用于跨夜检测的 Handler，确保 postDelayed/removeCallbacks 使用同一实例 */
    private final Handler midnightCheckHandler = new Handler(Looper.getMainLooper());
    /** 跨夜检测定时任务 */
    private final Runnable midnightCheckRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isAdded())
                return;
            String todayStr = java.time.LocalDate.now().toString();
            if (lastKnownDate != null && !lastKnownDate.equals(todayStr)) {
                Log.i("WordLearning", "[跨夜检测] 前台跨天! " + lastKnownDate + " → " + todayStr);
                lastKnownDate = todayStr;
                boolean dayChanged = dailyState.checkAndResetDailyState();
                if (dayChanged) {
                    wordCards.clear();
                    isLoadingTask = false;
                    summaryCardView = null;
                    if (cardContainer != null)
                        cardContainer.removeAllCards();
                    loadTodayTask();
                }
            }
            // 每 60 秒检测一次
            midnightCheckHandler.postDelayed(this, 60_000);
        }
    };

    @Override
    public void onSettingChanged(String key, Object value) {
        if (UserSettingsManager.KEY_IS_SLIDE_BACK.equals(key)) {
            slideFlag = (Boolean) value ? 1 : -1;
        }
        // 学习模式变化时重新加载
        if (UserSettingsManager.KEY_STUDY_MODE.equals(key)) {
            studyMode = (String) value;
        }
    }

    @Override
    public void onSettingsReset() {
        slideFlag = userSettingsManager.isSlideBackEnabled() ? 1 : -1;
        studyMode = userSettingsManager.getStudyMode();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_word_learning, container, false);
        userSettingsManager = UserSettingsManager.getInstance(requireContext());
        userSettingsManager.addSettingsChangeListener(this);
        slideFlag = userSettingsManager.isSlideBackEnabled() ? 1 : -1;
        studyMode = userSettingsManager.getStudyMode();

        SharedPreferences sp = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        userId = sp.getInt(KEY_USER_ID, 0);

        // 初始化提取的模块
        dailyState = new DailyStateManager(requireContext(), userId);
        summaryBuilder = new SummaryCardBuilder(requireContext());

        tvDayCount = view.findViewById(R.id.tv_day_count);
        btnPlan = view.findViewById(R.id.btn_plan);
        btnSearch = view.findViewById(R.id.btn_search);

        btnPlan.setOnClickListener(v -> showLearningPlan());
        btnSearch.setOnClickListener(v -> openSearch());

        cardContainer = view.findViewById(R.id.word_card_container);
        if (cardContainer != null) {
            cardContainer.setInteractiveMode(true);
            cardContainer.setOnCardSwipedListener(this);
            dailyState.loadFromPrefs();
            loadTodayTask();
        }

        // 初始化前台跨夜检测
        lastKnownDate = LocalDate.now().toString();

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 停止前台跨夜检测
        stopMidnightCheck();
        if (userSettingsManager != null) {
            userSettingsManager.removeSettingsChangeListener(this);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        boolean dayChanged = dailyState.checkAndResetDailyState();
        if (dayChanged) {
            // 日切时强制清空内存中的旧卡片，避免昨日残留数据
            wordCards.clear();
            isLoadingTask = false;
            summaryCardView = null;
            if (cardContainer != null)
                cardContainer.removeAllCards();
        }
        boolean containerEmpty = cardContainer != null && cardContainer.getAllCards().isEmpty();
        if ((dayChanged || wordCards.isEmpty() || containerEmpty) && !isLoadingTask) {
            loadTodayTask();
        }
        // 重置当前卡片的计时器：用户可能从其他 Tab 切回，或 App 从后台恢复
        resetCurrentCardTimer();
        // 启动/刷新前台跨夜检测
        lastKnownDate = LocalDate.now().toString();
        startMidnightCheck();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopMidnightCheck();
    }

    // ── 前台跨夜检测控制 ──

    private void startMidnightCheck() {
        // 先移除旧的，避免重复调度
        midnightCheckHandler.removeCallbacks(midnightCheckRunnable);
        midnightCheckHandler.postDelayed(midnightCheckRunnable, 60_000);
    }

    private void stopMidnightCheck() {
        midnightCheckHandler.removeCallbacks(midnightCheckRunnable);
    }

    /**
     * 重置当前展示卡片的 displayStartTime。 用户从其他 Tab / 模块返回或 App 从后台恢复时，不应将离开时间计入答题耗时。
     */
    private void resetCurrentCardTimer() {
        if (cardContainer == null)
            return;
        View currentCardView = cardContainer.getCurrentCardView();
        if (currentCardView == null)
            return;
        WordCard wc = getWordCardFromView(currentCardView);
        if (wc != null && !wc.isOperated) {
            wc.resetExerciseState();
        }
    }

    // ==================== 标题栏 ====================

    private void updateTitleBar() {
        if (tvDayCount != null) {
            if (studyDay > 0) {
                if (reviewLimit > 0) {
                    // 方案B：展示每日复习进度（今日已完成/上限），直观体现"每日累计封顶"
                    tvDayCount.setText("Day " + studyDay + " · 复习 " + reviewsDoneToday + "/" + reviewLimit);
                } else {
                    tvDayCount.setText("Day " + studyDay);
                }
            } else {
                tvDayCount.setText("水滴记忆");
            }
        }
    }

    private void showLearningPlan() {
        Intent intent = new Intent(requireContext(), PlanCheckActivity.class);
        startActivity(intent);
    }

    private void openSearch() {
        Intent intent = new Intent(requireContext(), SearchingActivity.class);
        startActivity(intent);
    }

    // ==================== 数据加载 ====================

    private void loadTodayTask() {
        isLoadingTask = true;
        GetDataByThread getDataByThread = new GetDataByThread("/learning/getTodayTask");
        getDataByThread.getPlan(myHandler, msg_success, msg_failed, String.valueOf(userId));
    }

    /**
     * 解析新的 getTodayTask 响应并创建卡片 响应格式: {code, lexiconId, wordList: [[wordId,
     * headWord, R, D, S, lastScore], ...], dailyNewWordCount, studyDay?}
     */
    private void parseAndCreateCards(JSONObject responseJson) throws JSONException {
        isLoadingTask = false;
        lexiconId = responseJson.getString("lexiconId");
        // 兼容旧字段 dailyNewWordCount，优先读取服务端 newWordCount（修复固定为 10 的问题）
        dailyNewWordCount = responseJson.has("newWordCount")
                ? responseJson.optInt("newWordCount", 10)
                : responseJson.optInt("dailyNewWordCount", 10);
        studyDay = responseJson.optInt("studyDay", 0); // 0=未返回时回退
        // 今日复习预算信息（方案B：服务端按"今日累计已复习"封顶）
        reviewsDoneToday = responseJson.optInt("reviewsDoneToday", 0);
        reviewLimit = responseJson.optInt("reviewLimit", 0);
        updateTitleBar();

        // 同步服务端每日新词数到本地设置（保持设置页一致）
        userSettingsManager.setDailyNewWords(dailyNewWordCount);

        // 预加载词库
        LexiconResourceMap.loadLexicon(requireContext(), lexiconId);

        JSONArray wordList = responseJson.getJSONArray("wordList");
        List<View> cardViews = new ArrayList<>();
        wordCards.clear();
        dailyState.clearFilteredSnapshot();

        // cardFactory 依赖 lexiconId，延迟初始化
        cardFactory = new ExerciseCardFactory(requireContext(), lexiconId, studyMode, userId,
                new ExerciseCardFactory.Callback() {
                    @Override
                    public void onSubmitAnswer(WordCard wc, long rt) {
                        submitAnswerForCard(wc, rt);
                    }

                    @Override
                    public void onMoveToNext() {
                        moveToNextCard();
                    }
                });

        int filteredCount = 0;
        for (int i = 0; i < wordList.length(); i++) {
            JSONArray item = wordList.getJSONArray(i);
            int wordId = item.getInt(0);
            String headWord = item.optString(1, "");
            double r = item.optDouble(2, 0), d = item.optDouble(3, 0);
            double s = item.optDouble(4, 0);
            int lastScore = item.optInt(5, 0);

            if (headWord.isEmpty()) {
                WordEntry entry = LexiconResourceMap.getWordByRank(lexiconId, wordId);
                if (entry != null)
                    headWord = entry.getHeadWord();
            }

            if (dailyState.isCompleted(wordId)) {
                filteredCount++;
                WordCard snap = buildWordCard(wordId, headWord, r, d, s, lastScore);
                snap.isOperated = true;
                // 从持久化详情中恢复 isCorrect（上次会话已记录的正确/错误状态）
                for (DailyStateManager.CompletedWordEntry entry : dailyState.getCompletedDetails()) {
                    if (entry.wordId == wordId) {
                        snap.isCorrect = entry.isCorrect;
                        break;
                    }
                }
                dailyState.addFilteredCard(snap);
                Log.d("WordLearning", "[防重复] 过滤已完成的 wordId=" + wordId);
                continue;
            }

            WordCard wc = buildWordCard(wordId, headWord, r, d, s, lastScore);
            wordCards.add(wc);
            cardViews.add(cardFactory.createExerciseCardView(wc));
        }

        if (filteredCount > 0)
            Log.i("StudyLog", "客户端过滤掉 " + filteredCount + " 个今日已完成单词");

        if (cardViews.isEmpty()) {
            Log.i("StudyLog", "今日所有单词已完成，展示总结卡片");
            addSummaryCard();
            totalWords = 0;
            operatedCount = 0;
            return;
        }

        // 有新练习卡片时，移除旧的总结卡片（如果有）
        if (summaryCardView != null && cardContainer != null) {
            cardContainer.removeCard(summaryCardView);
            summaryCardView = null;
        }

        totalWords = wordCards.size();
        operatedCount = 0;
        currentCardIndex = 0;
        for (View cv : cardViews)
            cardContainer.addCard(cv);
    }

    /**
     * 从词库构建 WordCard
     */
    private WordCard buildWordCard(int wordId, String headWord, double retrievability, double difficulty,
            double stability, int lastScore) {
        WordEntry entry = LexiconResourceMap.getWordByRank(lexiconId, wordId);
        String definition = "";
        String exampleText = "";
        String usPhone = "";
        String ukPhone = "";

        if (entry != null) {
            definition = "中文释义:\n" + entry.getChineseTranslation() + "\n\n英文释义:\n" + entry.getEnglishDefinition();

            StringBuilder sb = new StringBuilder();
            int sCount = 0;
            for (WordEntry.ExampleSentence sentence : entry.getExampleSentences()) {
                if (sCount >= 2)
                    break;
                sCount++;
                sb.append("例句").append(sCount).append(": \n").append(sentence.getEn()).append("\n");
                sb.append("释义:\n").append(sentence.getCn()).append("\n");
            }
            exampleText = sb.toString();
            usPhone = entry.getUsPhone();
            ukPhone = entry.getUkPhone();
        }

        WordCard card = new WordCard(wordId, headWord, "美音:" + usPhone + " | 英音:" + ukPhone, definition, exampleText);
        card.setPhone(usPhone, ukPhone);
        card.retrievability = retrievability;
        card.difficulty = difficulty;
        card.stability = stability;
        card.lastScore = lastScore;
        // 判断新词/复习：新词 stability=0 且 lastScore=0
        card.type = (stability == 0 && lastScore == 0) ? WordCard.TYPE_NEW : WordCard.TYPE_REVIEW;
        card.isOperated = false;

        return card;
    }

    // ==================== 练习卡片视图创建 ====================

    /**
     * 根据学习模式创建对应的练习卡片视图
     */
    // 卡片视图创建 → ExerciseCardFactory；总结卡片 → SummaryCardBuilder

    // ==================== 答案提交 ====================

    private void submitAnswerForCard(WordCard wordCard, long responseTimeMs) {
        wordCard.isOperated = true;
        // 乐观更新：立即标记完成（含正确性），确保总结卡片同步出现在卡片堆中
        dailyState.markCompletedWithResult(wordCard.word_id, wordCard.isCorrect);
        operatedCount++;
        checkAllCompleted();

        GetDataByThread submit = new GetDataByThread("/learning/submitAnswer");
        if (WordCard.MODE_INPUT.equals(studyMode)) {
            // 输入模式：发送扩展字段，服务端进行 AI 评判
            submit.submitAnswerInput(new Handler(Looper.getMainLooper()) {
                @Override
                public void handleMessage(@NonNull Message msg) {
                    if (!isAdded())
                        return;
                    if (msg.what == msg_success) {
                        try {
                            JSONObject responseJson = new JSONObject((String) msg.obj);
                            if ("200".equals(responseJson.getString("code"))) {
                                String aiFeedback = responseJson.optString("aiFeedback", "");
                                boolean serverIsCorrect = responseJson.optBoolean("isCorrect", wordCard.isCorrect);
                                int fsrsScore = responseJson.optInt("fsrsScore", 0);
                                // 用服务端判定覆盖客户端判定
                                wordCard.isCorrect = serverIsCorrect;
                                wordCard.fsrsScore = fsrsScore;
                                wordCard.aiFeedback = aiFeedback;
                                // 持久化完整的 AI 评判结果
                                dailyState.markCompletedWithFullResult(wordCard.word_id, serverIsCorrect, fsrsScore,
                                        aiFeedback);
                                // 找到对应的卡片视图并更新 AI 评判结果
                                if (cardContainer != null) {
                                    for (View cv : cardContainer.getAllCards()) {
                                        WordCard wc = getWordCardFromView(cv);
                                        if (wc != null && wc.word_id == wordCard.word_id) {
                                            ExerciseCardFactory.updateInputFeedbackResult(cv, fsrsScore,
                                                    serverIsCorrect, aiFeedback);
                                            break;
                                        }
                                    }
                                }
                                // 如果所有单词已完成，重建总结卡片以反映服务端的修正
                                if (operatedCount >= totalWords && totalWords > 0) {
                                    addSummaryCard();
                                }
                            }
                        } catch (JSONException e) {
                            Log.e("submitAnswer", "解析响应失败", e);
                        }
                    } else {
                        Toast.makeText(getContext(), R.string.submit_failed_retry, Toast.LENGTH_SHORT).show();
                    }
                }
            }, msg_success, msg_failed, userId, wordCard.word_id, lexiconId, wordCard.word, wordCard.isCorrect,
                    responseTimeMs, wordCard.userAnswer != null ? wordCard.userAnswer : "",
                    wordCard.referenceDefinition != null ? wordCard.referenceDefinition : "",
                    wordCard.pos != null ? wordCard.pos : "");
        } else {
            // 选择题模式：保持原有行为
            submit.submitAnswer(new Handler(Looper.getMainLooper()) {
                @Override
                public void handleMessage(@NonNull Message msg) {
                    if (msg.what != msg_success) {
                        Toast.makeText(getContext(), R.string.submit_failed_retry, Toast.LENGTH_SHORT).show();
                        // 提交失败时回滚：但保留已完成标记，避免重复练习
                    }
                }
            }, msg_success, msg_failed, userId, wordCard.word_id, lexiconId, wordCard.word, wordCard.isCorrect,
                    responseTimeMs, studyMode);
        }
    }

    private void checkAllCompleted() {
        if (operatedCount >= totalWords && totalWords > 0) {
            Log.i("StudyLog", "--------完成了今天的学习");
            // 添加总结卡片（放在卡片堆末尾）
            addSummaryCard();
            GetDataByThread updateLearningList = new GetDataByThread("/learning/updateLearningListCompletion");
            int actualStudyDay = studyDay > 0 ? studyDay : 1;
            updateLearningList.updateLearningListCompletion(new UpdateHandler(), msg_success, msg_failed, userId,
                    lexiconId, actualStudyDay, true);
        }
    }

    /** 添加今日学习总结卡片（幂等：先移除旧总结卡片，再添加新的，避免重复堆积） */
    private void addSummaryCard() {
        // 移除旧的总结卡片（如果存在），确保容器中始终只有一张总结卡片
        if (summaryCardView != null && cardContainer != null) {
            cardContainer.removeCard(summaryCardView);
            Log.i("StudyLog", "[总结卡片] 已移除旧卡片");
        }
        View summaryView = summaryBuilder.buildTodaySummary(wordCards, dailyState.getFilteredSnapshot(),
                dailyState.getCompletedCount(), dailyState.getCompletedDetails(), lexiconId);
        summaryCardView = summaryView;
        if (cardContainer != null) {
            cardContainer.addCard(summaryView);
        }
        Log.i("StudyLog", "[总结卡片] 已添加 (词书=" + lexiconId + ")");
    }

    private void moveToNextCard() {
        // 使用 -slideFlag 确保始终前进到下一个卡片
        cardContainer.animateCardOut(-slideFlag);
    }

    // ==================== 卡片容器回调 ====================

    @Override
    public void onCurrentCardChanged(View newCard) {
        WordCard wordCard = getWordCardFromView(newCard);
        if (wordCard != null) {
            currentCardIndex = wordCards.indexOf(wordCard);
            // 记录卡片展示时间
            wordCard.resetExerciseState();
        }
    }

    @Override
    public void onCardLongPressed(View cardView) {
        String word = ((TextView) cardView.findViewById(R.id.tv_word)).getText().toString();
        if (!word.isEmpty()) {
            Intent intent = new Intent(requireContext(), SearchingActivity.class);
            intent.putExtra("search_word", word);
            startActivity(intent);
        }
    }

    public static WordCard getWordCardFromView(View view) {
        Object tag = view.getTag();
        if (tag instanceof WordCard) {
            return (WordCard) tag;
        }
        return null;
    }

    // ==================== Handler 类 ====================

    @SuppressLint("HandlerLeak")
    class MyHandler extends Handler {
        MyHandler() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            if (!isAdded())
                return;
            switch (msg.what) {
            case msg_success:
                String result = (String) msg.obj;
                try {
                    JSONObject responseJson = new JSONObject(result);
                    Log.i("GetPlan", "--------" + result);
                    String code = responseJson.getString("code");
                    if ("200".equals(code)) {
                        parseAndCreateCards(responseJson);
                    } else {
                        String message = responseJson.optString("message", "加载失败");
                        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                    }
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                break;
            case msg_failed:
                isLoadingTask = false;
                Toast.makeText(getContext(), "网络请求失败", Toast.LENGTH_SHORT).show();
                break;
            }
        }
    }

    @SuppressLint("HandlerLeak")
    class UpdateHandler extends Handler {
        UpdateHandler() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            if (!isAdded())
                return;
            switch (msg.what) {
            case msg_success:
                String result = (String) msg.obj;
                try {
                    JSONObject responseJson = new JSONObject(result);
                    String code = responseJson.getString("code");
                    if ("500".equals(code)) {
                        Toast.makeText(getContext(), "更新学习计划失败", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String isCompleted = responseJson.optString("isCompleted", "false");
                    if ("true".equals(isCompleted)) {
                        Toast.makeText(getContext(), "🎉 恭喜！你已完成本词书全部单词的学习！", Toast.LENGTH_LONG).show();
                        Log.i("StudyLog", "词书已完成 — 全部 " + totalWords + " 个单词已学习");
                    } else {
                        Toast.makeText(getContext(), R.string.today_learning_complete, Toast.LENGTH_LONG).show();
                        Log.i("StudyLog", "今日学习已完成 — 已操作 " + operatedCount + " 个单词");
                    }
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                break;
            case msg_failed:
                break;
            }
        }
    }
}
