package com.deepsleep.memory.ui.main_view;

import android.annotation.SuppressLint;
import android.content.Intent;

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
import com.deepsleep.memory.settings.InnerSettingsManager;

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

    /** 学习模式切换时若正在加载任务，标记待重载（当前加载完成后补执行） */
    private boolean reloadPendingForMode = false;

    /** 服务端回写学习模式时置位，避免触发重复重载（防循环） */
    private boolean isApplyingStudyModeFromServer = false;

    /** 后台（被其他页面覆盖）时收到学习模式变更，置位后在 onResume 补重载 */
    private boolean pendingStudyModeReload = false;

    /** 当前总结卡片视图引用（用于避免重复创建，更新时移除旧卡片再添加新卡片） */
    private View summaryCardView = null;

    // ── 卡片视图渐进式构建（避免 40 张卡片一次性 inflate 阻塞主线程约 2s） ──
    /** 卡片视图构建专用 Handler：分帧批量构建，避免长时间占用主线程 */
    private final Handler cardBuildHandler = new Handler(Looper.getMainLooper());
    /** 首批同步构建的卡片数：保证响应到达后马上有卡可学 */
    private static final int CARD_BUILD_INITIAL_COUNT = 2;
    /** 每帧追加构建的卡片数（把 inflate 与选项生成成本分摊到多帧） */
    private static final int CARD_BUILD_CHUNK_SIZE = 3;
    /** 下一个待构建视图的 wordCards 下标 */
    private int pendingViewBuildIndex = 0;

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
            // 应用在后台（被其他页面覆盖）时不加载任务，仅保持轮询
            if (!isResumed()) {
                midnightCheckHandler.postDelayed(this, 60_000);
                return;
            }
            String todayStr = java.time.LocalDate.now().toString();
            if (lastKnownDate != null && !lastKnownDate.equals(todayStr)) {
                Log.i("WordLearning", "[跨夜检测] 前台跨天! " + lastKnownDate + " → " + todayStr);
                lastKnownDate = todayStr;
                boolean dayChanged = dailyState.checkAndResetDailyState();
                if (dayChanged) {
                    isLoadingTask = false;
                    clearCardsAndCancelBuilds();
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
        // 学习模式变化 → 立即重载今日任务，让新模式马上生效（无需重新进入应用）
        if (UserSettingsManager.KEY_STUDY_MODE.equals(key)) {
            studyMode = (String) value;
            // 服务端回写不触发重载（防循环）；仅用户手动切换时重载
            if (isApplyingStudyModeFromServer || !isAdded()) {
                return;
            }
            // 应用在后台（如被拍照/裁剪页面覆盖）时不立即请求，回到前台再补重载，避免后台刷请求
            if (!isResumed()) {
                pendingStudyModeReload = true;
                return;
            }
            if (isLoadingTask) {
                reloadPendingForMode = true;
            } else {
                reloadTodayTaskForSettings();
            }
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

        userId = InnerSettingsManager.getInstance(requireContext()).getUserId();

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
            isLoadingTask = false;
            clearCardsAndCancelBuilds();
        }
        boolean containerEmpty = cardContainer != null && cardContainer.getAllCards().isEmpty();
        if ((dayChanged || wordCards.isEmpty() || containerEmpty) && !isLoadingTask) {
            loadTodayTask();
        }
        // 后台挂起的学习模式重载，回到前台补执行（保证切换模式后回到单词页立即生效）
        if (pendingStudyModeReload) {
            pendingStudyModeReload = false;
            reloadTodayTaskForSettings();
        }
        // 重置当前卡片的计时器：用户可能从其他 Tab 切回，或 App 从后台恢复
        resetCurrentCardTimer();
        // 启动/刷新前台跨夜检测
        lastKnownDate = LocalDate.now().toString();
        startMidnightCheck();
        // 网络恢复信号：静默补传断网期间未同步的答题记录
        flushPendingUploads();
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

    /** 学习模式等设置变化后重载今日任务，使新配置立即生效 */
    private void reloadTodayTaskForSettings() {
        if (!isAdded() || isLoadingTask)
            return;
        clearCardsAndCancelBuilds();
        loadTodayTask();
    }

    /** 清空卡片数据与视图，并取消挂起的分帧构建任务（重载/日切时调用） */
    private void clearCardsAndCancelBuilds() {
        cardBuildHandler.removeCallbacksAndMessages(null);
        pendingViewBuildIndex = 0;
        wordCards.clear();
        summaryCardView = null;
        if (cardContainer != null)
            cardContainer.removeAllCards();
    }

    /**
     * 解析新的 getTodayTask 响应并创建卡片 响应格式: {code, lexiconId, wordList: [[wordId,
     * headWord, R, D, S, lastScore], ...], dailyNewWordCount, studyDay?}
     */
    private void parseAndCreateCards(JSONObject responseJson) throws JSONException {
        isLoadingTask = false;
        // 能拉到今日任务说明网络已通：静默补传断网期间未同步的答题记录
        flushPendingUploads();
        lexiconId = responseJson.getString("lexiconId");
        // 兼容旧字段 dailyNewWordCount，优先读取服务端 newWordCount（修复固定为 10 的问题）
        dailyNewWordCount = responseJson.has("newWordCount") ? responseJson.optInt("newWordCount", 10)
                : responseJson.optInt("dailyNewWordCount", 10);
        studyDay = responseJson.optInt("studyDay", 0); // 0=未返回时回退
        // 今日复习预算信息（方案B：服务端按"今日累计已复习"封顶）
        reviewsDoneToday = responseJson.optInt("reviewsDoneToday", 0);
        reviewLimit = responseJson.optInt("reviewLimit", 0);
        updateTitleBar();

        // 同步服务端每日新词数到本地设置（保持设置页一致）
        userSettingsManager.setDailyNewWords(dailyNewWordCount);
        // 同步服务端每日最大复习词数到本地设置（设置页展示/修改用）
        if (reviewLimit > 0) {
            userSettingsManager.setMaxReviewWords(reviewLimit);
        }
        // 服务端学习模式回写（跨设备/跨账号恢复本人偏好，防循环）
        String serverStudyMode = responseJson.optString("studyModePreference", "");
        if ("choice".equals(serverStudyMode) || "input".equals(serverStudyMode)) {
            // 与本地一致时跳过写回，避免无谓的设置变更通知
            if (!serverStudyMode.equals(userSettingsManager.getStudyMode())) {
                isApplyingStudyModeFromServer = true;
                try {
                    userSettingsManager.setStudyMode(serverStudyMode);
                } finally {
                    isApplyingStudyModeFromServer = false;
                }
            }
        }
        studyMode = userSettingsManager.getStudyMode();

        // 预加载词库
        LexiconResourceMap.loadLexicon(requireContext(), lexiconId);

        JSONArray wordList = responseJson.getJSONArray("wordList");
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
        }

        if (filteredCount > 0)
            Log.i("StudyLog", "客户端过滤掉 " + filteredCount + " 个今日已完成单词");

        if (wordCards.isEmpty()) {
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
        // 取消上一轮可能仍挂起的分帧构建任务，避免旧批次继续追加卡片
        cardBuildHandler.removeCallbacksAndMessages(null);
        pendingViewBuildIndex = 0;
        // 首批卡片同步构建，保证首帧即有卡片可学；剩余卡片分帧构建，避免一次 inflate 40 张卡阻塞主线程
        if (buildNextCardViewBatch(CARD_BUILD_INITIAL_COUNT)) {
            cardBuildHandler.post(this::buildRemainingCardViews);
        }
    }

    /**
     * 构建下一批卡片视图（每批最多 maxCount 张），返回是否仍有剩余待构建。
     */
    private boolean isBuildingCardBatch = false;

    private boolean buildNextCardViewBatch(int maxCount) {
        // 防重入：addCard 首张卡会触发 showCard→onCurrentCardChanged→ensureNextCardsBuilt，
        // 若无保护会嵌套构建同一张卡导致重复视图
        if (isBuildingCardBatch || cardFactory == null || cardContainer == null)
            return false;
        isBuildingCardBatch = true;
        try {
            int built = 0;
            while (pendingViewBuildIndex < wordCards.size() && built < maxCount) {
                WordCard wc = wordCards.get(pendingViewBuildIndex);
                // 先推进下标再 addCard，双重保险防止重入时重复构建同一张卡
                pendingViewBuildIndex++;
                cardContainer.addCard(cardFactory.createExerciseCardView(wc));
                built++;
            }
            return pendingViewBuildIndex < wordCards.size();
        } finally {
            isBuildingCardBatch = false;
        }
    }

    /** 分帧构建剩余卡片视图：每帧一批，把 inflate 成本分摊到多个空闲帧 */
    private void buildRemainingCardViews() {
        if (!isAdded() || cardFactory == null || cardContainer == null)
            return;
        if (buildNextCardViewBatch(CARD_BUILD_CHUNK_SIZE)) {
            cardBuildHandler.post(this::buildRemainingCardViews);
        }
    }

    /** 兜底补建：用户快翻到已构建卡片末尾时，立即补建下一批，避免无卡可翻 */
    private void ensureNextCardsBuilt() {
        if (pendingViewBuildIndex >= wordCards.size() || cardContainer == null)
            return;
        int builtCount = cardContainer.getAllCards().size();
        if (summaryCardView != null)
            builtCount--; // 排除总结卡片
        if (currentCardIndex >= builtCount - 1) {
            buildNextCardViewBatch(CARD_BUILD_CHUNK_SIZE);
        }
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
        // 仅首次提交时计入标题栏进度（防止重复触发导致多计）
        boolean firstSubmit = !wordCard.isOperated;
        wordCard.isOperated = true;
        // 乐观更新：立即标记完成（含正确性），确保总结卡片同步出现在卡片堆中
        dailyState.markCompletedWithResult(wordCard.word_id, wordCard.isCorrect);
        operatedCount++;
        // 标题栏实时刷新：复习卡计入今日复习数（新词不占复习预算，与服务端口径一致）
        if (firstSubmit && wordCard.type == WordCard.TYPE_REVIEW && reviewLimit > 0) {
            reviewsDoneToday++;
            updateTitleBar();
        }
        checkAllCompleted();

        // 生成客户端提交幂等键：正常提交与补传共用，服务端据此去重（防止重复推进 FSRS）
        final String submitId = wordCard.word_id + "_" + System.currentTimeMillis();

        // 入队待上传记录：断网/提交失败时联网后自动补传，服务端确认成功后移除
        DailyStateManager.PendingUpload pendingUpload = new DailyStateManager.PendingUpload();
        pendingUpload.wordId = wordCard.word_id;
        pendingUpload.submitId = submitId;
        pendingUpload.lexiconId = lexiconId;
        pendingUpload.word = wordCard.word;
        pendingUpload.isCorrect = wordCard.isCorrect;
        pendingUpload.fsrsScore = wordCard.fsrsScore;
        pendingUpload.aiFeedback = wordCard.aiFeedback != null ? wordCard.aiFeedback : "";
        pendingUpload.responseTimeMs = responseTimeMs;
        pendingUpload.studyMode = studyMode;
        pendingUpload.userAnswer = wordCard.userAnswer != null ? wordCard.userAnswer : "";
        pendingUpload.referenceDefinition = wordCard.referenceDefinition != null ? wordCard.referenceDefinition : "";
        pendingUpload.pos = wordCard.pos != null ? wordCard.pos : "";
        dailyState.enqueuePendingUpload(pendingUpload);

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
                                // 服务端确认成功：移出待上传队列
                                dailyState.removePendingUpload(wordCard.word_id);
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
                    wordCard.pos != null ? wordCard.pos : "", submitId);
        } else {
            // 选择题模式：保持原有行为
            submit.submitAnswer(new Handler(Looper.getMainLooper()) {
                @Override
                public void handleMessage(@NonNull Message msg) {
                    if (msg.what == msg_success) {
                        // 服务端确认成功：移出待上传队列
                        dailyState.removePendingUpload(wordCard.word_id);
                    } else {
                        Toast.makeText(getContext(), R.string.submit_failed_retry, Toast.LENGTH_SHORT).show();
                        // 提交失败：记录保留在待上传队列，联网后自动补传
                    }
                }
            }, msg_success, msg_failed, userId, wordCard.word_id, lexiconId, wordCard.word, wordCard.isCorrect,
                    responseTimeMs, studyMode, submitId);
        }
    }

    // ==================== 断网补传（待上传队列） ====================

    /**
     * 网络恢复信号（onResume / 今日任务加载成功）后触发：
     * 由 PendingUploadSync 静默逐条补传本地未同步的答题记录（App 启动时也会全局触发）。
     */
    private void flushPendingUploads() {
        PendingUploadSync.sync(requireContext(), (syncedCount, remainCount) -> {
            // 补传完成后兜底：今日已全部完成时重报学习列表完成状态（幂等）
            if (!isAdded() || remainCount != 0) {
                return;
            }
            if (operatedCount >= totalWords && totalWords > 0) {
                resendLearningListCompletion();
            }
        });
    }

    private void checkAllCompleted() {
        if (operatedCount >= totalWords && totalWords > 0) {
            Log.i("StudyLog", "--------完成了今天的学习");
            // 添加总结卡片（放在卡片堆末尾）
            addSummaryCard();
            resendLearningListCompletion();
        }
    }

    /** 上报学习列表完成状态（幂等操作，补传完成后兜底重发） */
    private void resendLearningListCompletion() {
        GetDataByThread updateLearningList = new GetDataByThread("/learning/updateLearningListCompletion");
        int actualStudyDay = studyDay > 0 ? studyDay : 1;
        updateLearningList.updateLearningListCompletion(new UpdateHandler(), msg_success, msg_failed, userId,
                lexiconId, actualStudyDay, true);
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
        // 兜底：先确保下一张卡片视图已构建，避免翻到已构建末尾时无卡可翻
        ensureNextCardsBuilt();
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
            // 渐进式构建兜底：快翻到已构建末尾时补建下一批
            ensureNextCardsBuilt();
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
            // 应用在后台（被拍照/裁剪等页面覆盖）时丢弃响应并复位加载标记，由 onResume 兜底重载
            if (!isResumed()) {
                isLoadingTask = false;
                reloadPendingForMode = false;
                return;
            }
            switch (msg.what) {
            case msg_success:
                String result = (String) msg.obj;
                try {
                    JSONObject responseJson = new JSONObject(result);
                    Log.i("GetPlan", "--------" + result);
                    String code = responseJson.getString("code");
                    if ("200".equals(code)) {
                        parseAndCreateCards(responseJson);
                        // 学习模式切换时若有挂起的重载请求，在此补执行（当前加载已完成）
                        if (reloadPendingForMode) {
                            reloadPendingForMode = false;
                            reloadTodayTaskForSettings();
                        }
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
            // 应用在后台时不处理学习完成回调，避免后台触发 Toast 等
            if (!isResumed()) {
                return;
            }
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
