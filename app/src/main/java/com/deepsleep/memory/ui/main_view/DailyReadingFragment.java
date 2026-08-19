package com.deepsleep.memory.ui.main_view;

import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.deepsleep.memory.R;
import com.deepsleep.memory.handle_utils.AudioPlayer;
import com.deepsleep.memory.network.ApiBridge;
import com.deepsleep.memory.network.ApiBridge;
import com.deepsleep.memory.network.MemoryApiClient;
import com.deepsleep.memory.network.MemoryApiClient;
import com.deepsleep.memory.settings.InnerSettingsManager;
import com.deepsleep.memory.settings.UserSettingsManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import io.noties.markwon.*;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DailyReadingFragment extends Fragment {
    private TextView markdownTitleView;
    private TextView markdownContentView;
    private TextView tvTitleBar;

    private LinearLayout sentenceAnalysisContainer;
    private LinearLayout highFrequencyWordsContainer;
    private ImageButton btnRefresh;
    private ProgressBar loadingProgressBar;
    private ScrollView scrollView;
    private ProgressBar readingProgressBar;
    private TextView tvReadingTime;
    private ImageButton btnFontDecrease;
    private ImageButton btnFontIncrease;
    private ImageButton btnFavorite;
    private ImageButton btnHistory;
    private ImageButton btnBackToday;
    private boolean isFavorited = false;
    private boolean isViewingFavorite = false;
    private long currentFavoriteId = -1;
    private String currentArticleTitle = "";
    private String currentArticleContent = "";
    private JSONArray currentSentenceAnalysis = new JSONArray();
    private JSONArray currentHighFrequencyWords = new JSONArray();

    // 侧边抽屉
    private View favoritesDrawer;
    private View drawerOverlay;
    private ListView favoritesListView;
    private TextView favoritesEmptyView;
    private ProgressBar favoritesLoading;
    private ImageButton btnCloseDrawer;
    private boolean isDrawerOpen = false;
    private List<Long> favoriteIds = new ArrayList<>();
    private List<String> favoriteTitles = new ArrayList<>();

    private int userId;
    private int currentFontSize = 19;
    private static final int FONT_SIZE_MIN = 15;
    private static final int FONT_SIZE_MAX = 25;
    private static final int FONT_SIZE_STEP = 2;

    /** 字号同步防抖（跨设备） */
    private final Handler fontSyncHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingFontSync;
    // 线程处理
    static final int msg_success = 1;
    static final int msg_failed = -1;
    private Map<TextView, ObjectAnimator> animatorMap = new HashMap<>();

    // 重试机制
    private boolean isRetrying = false;
    private Handler retryHandler = new Handler(Looper.getMainLooper());
    private Runnable retryRunnable;
    // 20 次 x 3s = 60s 轮询窗口，覆盖 AI 生成文章耗时（通常 20-60s）
    private static final int MAX_RETRY_COUNT = 20;
    private static final long RETRY_DELAY_MS = 3000;
    private int retryCount = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_daily_reading, container, false);
        markdownTitleView = view.findViewById(R.id.markdown_title_view);
        markdownContentView = view.findViewById(R.id.markdown_content_view);
        tvTitleBar = view.findViewById(R.id.tv_day_count);
        sentenceAnalysisContainer = view.findViewById(R.id.markdown_sentenceAnalysis_container);
        highFrequencyWordsContainer = view.findViewById(R.id.markdown_highFrequencyWords_container);

        loadingProgressBar = view.findViewById(R.id.loading_progress_bar);
        scrollView = view.findViewById(R.id.scroll_view);
        readingProgressBar = view.findViewById(R.id.reading_progress_bar);
        tvReadingTime = view.findViewById(R.id.tv_reading_time);
        btnFontDecrease = view.findViewById(R.id.btn_font_decrease);
        btnFontIncrease = view.findViewById(R.id.btn_font_increase);
        btnFavorite = view.findViewById(R.id.btn_favorite);
        btnHistory = view.findViewById(R.id.btn_history);
        btnBackToday = view.findViewById(R.id.btn_back_today);

        // 侧边抽屉
        favoritesDrawer = view.findViewById(R.id.favorites_drawer);
        drawerOverlay = view.findViewById(R.id.drawer_overlay);
        favoritesListView = view.findViewById(R.id.favorites_list_view);
        favoritesEmptyView = view.findViewById(R.id.favorites_empty_view);
        favoritesLoading = view.findViewById(R.id.favorites_loading);
        btnCloseDrawer = view.findViewById(R.id.btn_close_drawer);

        // 收藏夹入口 → 打开侧边抽屉
        btnHistory.setOnClickListener(v -> openFavoritesDrawer());

        // 关闭抽屉
        btnCloseDrawer.setOnClickListener(v -> closeFavoritesDrawer());
        drawerOverlay.setOnClickListener(v -> closeFavoritesDrawer());
        btnBackToday.setOnClickListener(v -> returnToTodayReading());

        // 收藏按钮 - 根据模式行为不同
        btnFavorite.setOnClickListener(v -> {
            if (isViewingFavorite) {
                deleteFavoriteAndReturn();
            } else {
                toggleFavorite();
            }
        });

        currentFontSize = UserSettingsManager.getInstance(requireContext()).getReaderFontSize();
        applyFontSizeToContent();

        btnFontDecrease.setOnClickListener(v -> {
            if (currentFontSize > FONT_SIZE_MIN) {
                currentFontSize -= FONT_SIZE_STEP;
                applyFontSizeToContent();
                saveFontSize();
            }
        });

        btnFontIncrease.setOnClickListener(v -> {
            if (currentFontSize < FONT_SIZE_MAX) {
                currentFontSize += FONT_SIZE_STEP;
                applyFontSizeToContent();
                saveFontSize();
            }
        });

        scrollView.getViewTreeObserver().addOnScrollChangedListener(() -> {
            updateReadingProgress();
        });

        btnRefresh = view.findViewById(R.id.btn_refresh);
        btnRefresh.setOnClickListener(v -> {
            btnRefresh.animate().rotationBy(360).setDuration(500).start();
            onArticleGenerate("generateArticle");
        });
        userId = InnerSettingsManager.getInstance(requireContext()).getUserId();
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        onArticleGenerate("dailyReading");
    }

    public void onArticleGenerate(String mode) {
        onArticleGenerate(mode, false);
    }

    public void onArticleGenerate(String mode, boolean isRetry) {
        if (!isRetry) {
            markdownTitleView.setText("请等待，文章生成中");
            markdownContentView.setText("正在梳理薄弱单词……");
            sentenceAnalysisContainer.removeAllViews();
            highFrequencyWordsContainer.removeAllViews();

            TextView loadingSentence = new TextView(requireContext());
            loadingSentence.setText("正在生成长难句分析……");
            loadingSentence.setTextSize(17);
            loadingSentence.setTextColor(ContextCompat.getColor(requireContext(), R.color.reader_text_secondary));
            loadingSentence.setPadding(0, 8, 0, 8);
            sentenceAnalysisContainer.addView(loadingSentence);

            TextView loadingWords = new TextView(requireContext());
            loadingWords.setText("正在生成高频易错单词……");
            loadingWords.setTextSize(17);
            loadingWords.setTextColor(ContextCompat.getColor(requireContext(), R.color.reader_text_secondary));
            loadingWords.setPadding(0, 8, 0, 8);

            if (loadingProgressBar != null) {
                loadingProgressBar.setVisibility(View.GONE);
                startWaveAnimation(markdownContentView);
                startWaveAnimation(loadingSentence);
                startWaveAnimation(loadingWords);
            }
        }

        if ("dailyReading".equals(mode)) { // 每日一读模式
            ApiBridge.enqueue(MemoryApiClient.composition().dailyReading(String.valueOf(userId)), buildArticleHandler(),
                    msg_success, msg_failed, "DailyReading");
        } else { // 生成文章模式
            ApiBridge.enqueue(MemoryApiClient.composition().generateArticle(String.valueOf(userId)), buildArticleHandler(),
                    msg_success, msg_failed, "GenerateArticle");
        }
    }

    /** 文章加载共用 Handler（每日一读 / 生成文章两种模式：成功/失败行为一致） */
    private Handler buildArticleHandler() {
        return new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {

                if (msg.what == msg_success) {
                    if (loadingProgressBar != null) {
                        loadingProgressBar.setVisibility(View.GONE);
                        stopWaveAnimation(markdownContentView);
                    }

                    String article = (String) msg.obj;
                    Log.i("article", "文章获取成功");
                    article = article.trim();
                    try {
                        JSONObject articleJson = new JSONObject(article);

                        String title = articleJson.getString("title");
                        String content = articleJson.getString("content");

                        // 解析并显示文章（sentenceAnalysis/highFrequencyWords 后端可能为 null，显示时降级为空数组）
                        JSONArray sentenceAnalysisArray = articleJson.optJSONArray("sentenceAnalysis");
                        JSONArray highFrequencyWordsArray = articleJson.optJSONArray("highFrequencyWords");
                        if (sentenceAnalysisArray == null) {
                            sentenceAnalysisArray = new JSONArray();
                        }
                        if (highFrequencyWordsArray == null) {
                            highFrequencyWordsArray = new JSONArray();
                        }

                        displayArticle(title, content, sentenceAnalysisArray, highFrequencyWordsArray);

                        // 恢复今日文章收藏状态
                        if (!isRetrying) {
                            restoreDailyFavoriteState(title);
                        }

                        // 复原设计：后端返回模板文章（isFallback=true）时，前端显示后继续轮询，
                        // 等待后端真正的“每日一读”生成完成并同步（命中缓存后 isFallback=false 即停止）
                        boolean isFallback = articleJson.optBoolean("isFallback", false);
                        if (isFallback) {
                            Log.i("article", "检测到通用文章，启动轮询");
                            // 本轮请求已返回，重置标志以允许安排下一轮轮询
                            // （修复：此前 isRetrying 从未在 fallback 响应后重置，导致第一次重试后就停止轮询）
                            isRetrying = false;
                            if (retryCount < MAX_RETRY_COUNT) {
                                scheduleRetry();
                            } else {
                                Log.i("article", "已达最大重试次数，停止轮询");
                            }
                        } else {
                            Log.i("article", "已获取个性化文章，取消轮询");
                            cancelRetry();
                            retryCount = 0;
                        }

                    } catch (JSONException e) {
                        Log.e("article", "JSON解析失败", e);
                        isRetrying = false;
                        if (retryCount < MAX_RETRY_COUNT) {
                            scheduleRetry();
                        }
                    }

                } else if (msg.what == msg_failed) {
                    Log.i("article", "文章获取失败");
                    isRetrying = false;
                    if (retryCount < MAX_RETRY_COUNT) {
                        scheduleRetry();
                    }
                }
            }
        };
    }

    private void startWaveAnimation(TextView textView) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(textView, "translationY", 0, -15, 0, 15, 0);
        animator.setDuration(1000);
        animator.setRepeatCount(ObjectAnimator.INFINITE);
        animator.setRepeatMode(ObjectAnimator.RESTART);
        animator.start();

        animatorMap.put(textView, animator);
    }

    private void stopWaveAnimation(TextView textView) {
        ObjectAnimator animator = animatorMap.get(textView);
        if (animator != null) {
            animator.cancel();
            animatorMap.remove(textView);
        }
        textView.setTranslationY(0);
    }

    private void applyFontSizeToContent() {
        if (markdownContentView != null) {
            markdownContentView.setTextSize(currentFontSize);
            float lineSpacingExtra = currentFontSize * 0.5f;
            markdownContentView.setLineSpacing(lineSpacingExtra, 1.0f);
        }
    }

    private void saveFontSize() {
        UserSettingsManager.getInstance(requireContext()).setReaderFontSize(currentFontSize);
        syncFontSizeToServer();
    }

    /** 防抖推送字号到服务端用户设置（跨设备同步） */
    private void syncFontSizeToServer() {
        if (pendingFontSync != null) {
            fontSyncHandler.removeCallbacks(pendingFontSync);
        }
        pendingFontSync = () -> {
            if (userId <= 0) {
                pendingFontSync = null;
                return;
            }
            JSONObject settings = new JSONObject();
            try {
                settings.put("readerFontSize", currentFontSize);
            } catch (JSONException ignored) {
            }
            JSONObject body = new JSONObject();
            try {
                body.put("userId", userId);
                body.put("settings", settings);
            } catch (JSONException e) {
                pendingFontSync = null;
                return;
            }
            ApiBridge.enqueue(MemoryApiClient.auth().updateUserSettings(ApiBridge.jsonBody(body)),
                    new Handler(Looper.getMainLooper()) {
                @Override
                public void handleMessage(@NonNull Message msg) {
                    if (msg.what != msg_success && isAdded()) {
                        Toast.makeText(getContext(), "字号同步失败", Toast.LENGTH_SHORT).show();
                    }
                    pendingFontSync = null;
                }
            }, msg_success, msg_failed, "UpdateUserSettings");
        };
        fontSyncHandler.postDelayed(pendingFontSync, 800);
    }

    private void updateReadingProgress() {
        if (scrollView == null || readingProgressBar == null)
            return;
        View child = scrollView.getChildAt(0);
        if (child == null)
            return;
        int scrollY = scrollView.getScrollY();
        int childHeight = child.getHeight();
        int scrollViewHeight = scrollView.getHeight();
        int maxScrollY = childHeight - scrollViewHeight;
        if (maxScrollY <= 0) {
            readingProgressBar.setProgress(0);
            return;
        }
        int progress = (int) ((scrollY / (float) maxScrollY) * 100);
        readingProgressBar.setProgress(Math.min(progress, 100));
    }

    private void updateReadingTime(String content) {
        if (tvReadingTime == null || content == null)
            return;
        String[] words = content.split("\\s+");
        int wordCount = words.length;
        int minutes = Math.max(1, (int) Math.ceil(wordCount / 200.0));
        tvReadingTime.setText("预计阅读 " + minutes + " 分钟 · " + wordCount + " 词");
    }

    // ==================== 收藏功能 ====================

    private void toggleFavorite() {
        if (currentArticleTitle.isEmpty() || currentArticleContent.isEmpty())
            return;

        isFavorited = !isFavorited;
        updateFavoriteIcon();

        if (isFavorited) {
            // 收藏（新栈：CompositionApi.favoriteArticle 经 ApiBridge）
            try {
                JSONObject body = new JSONObject();
                body.put("title", currentArticleTitle);
                body.put("content", currentArticleContent);
                body.put("sentenceAnalysis", currentSentenceAnalysis);
                body.put("highFrequencyWords", currentHighFrequencyWords);
                body.put("wordList", "");
                body.put("note", "");
                ApiBridge.enqueue(MemoryApiClient.composition().favoriteArticle(String.valueOf(userId),
                        ApiBridge.jsonBody(body)), new Handler(Looper.getMainLooper()) {
                    @Override
                    public void handleMessage(@NonNull Message msg) {
                        if (msg.what == msg_success) {
                            try {
                                JSONObject result = new JSONObject((String) msg.obj);
                                if (result.has("favoriteId")) {
                                    currentFavoriteId = result.getLong("favoriteId");
                                    saveDailyFavoriteState(currentFavoriteId);
                                }
                            } catch (Exception e) {
                                Log.e("article", "收藏响应解析失败", e);
                            }
                        } else {
                            Log.e("article", "收藏请求失败");
                        }
                    }
                }, msg_success, msg_failed, "FavoriteArticle");
            } catch (Exception e) {
                Log.e("article", "收藏请求构造失败", e);
            }
        } else {
            // 取消收藏（新栈：CompositionApi.deleteFavorite 经 ApiBridge，静默）
            if (currentFavoriteId > 0) {
                ApiBridge.enqueue(MemoryApiClient.composition().deleteFavorite(currentFavoriteId, String.valueOf(userId)),
                        new Handler(Looper.getMainLooper()) {
                            @Override
                            public void handleMessage(@NonNull Message msg) {
                                // 静默：乐观更新在前
                            }
                        }, msg_success, msg_failed, "UnfavoriteArticle");
            }
            clearDailyFavoriteState();
            currentFavoriteId = -1;
        }
    }

    private void updateFavoriteIcon() {
        if (btnFavorite == null)
            return;
        btnFavorite.setImageResource(isFavorited ? R.drawable.baseline_star_24 : R.drawable.baseline_star_border_24);
    }

    // ==================== 收藏夹侧边抽屉 ====================

    private void openFavoritesDrawer() {
        if (isDrawerOpen)
            return;
        isDrawerOpen = true;

        drawerOverlay.setVisibility(View.VISIBLE);
        favoritesDrawer.setVisibility(View.VISIBLE);
        favoritesLoading.setVisibility(View.VISIBLE);
        favoritesEmptyView.setVisibility(View.GONE);
        favoritesListView.setVisibility(View.GONE);

        drawerOverlay.animate().alpha(1f).setDuration(250).start();
        favoritesDrawer.animate().translationX(0).setDuration(300).setInterpolator(new DecelerateInterpolator())
                .start();

        loadFavoritesList();
    }

    private void closeFavoritesDrawer() {
        if (!isDrawerOpen)
            return;
        isDrawerOpen = false;

        drawerOverlay.animate().alpha(0f).setDuration(200).start();
        favoritesDrawer.animate().translationX(-favoritesDrawer.getWidth()).setDuration(250)
                .setInterpolator(new DecelerateInterpolator()).withEndAction(() -> {
                    drawerOverlay.setVisibility(View.GONE);
                    favoritesDrawer.setVisibility(View.GONE);
                }).start();
    }

    private void loadFavoritesList() {
        ApiBridge.enqueue(MemoryApiClient.composition().favorites(String.valueOf(userId)),
                new Handler(Looper.getMainLooper()) {
                    @Override
                    public void handleMessage(@NonNull Message msg) {
                        if (msg.what == msg_success) {
                            try {
                                JSONArray favorites = new JSONArray((String) msg.obj);

                                favoriteIds.clear();
                                favoriteTitles.clear();
                                List<String> displayItems = new ArrayList<>();
                                for (int i = 0; i < favorites.length(); i++) {
                                    JSONObject fav = favorites.getJSONObject(i);
                                    favoriteIds.add(fav.getLong("id"));
                                    String title = fav.getString("articleTitle");
                                    favoriteTitles.add(title);
                                    displayItems.add(title);
                                }

                                favoritesLoading.setVisibility(View.GONE);

                                if (favoriteTitles.isEmpty()) {
                                    favoritesEmptyView.setVisibility(View.VISIBLE);
                                    return;
                                }

                                favoritesListView.setVisibility(View.VISIBLE);
                                ArrayAdapter<String> adapter = new ArrayAdapter<String>(requireContext(),
                                        android.R.layout.simple_list_item_1, android.R.id.text1, displayItems) {
                                    @Override
                                    public View getView(int pos, View convertView, ViewGroup parent) {
                                        View view = super.getView(pos, convertView, parent);
                                        TextView text = view.findViewById(android.R.id.text1);
                                        text.setText(favoriteTitles.get(pos));
                                        text.setTextSize(15);
                                        text.setTextColor(ContextCompat.getColor(requireContext(), R.color.reader_text));
                                        text.setPadding(24, 16, 24, 16);
                                        return view;
                                    }
                                };
                                favoritesListView.setAdapter(adapter);
                                favoritesListView.setOnItemClickListener((parent, v, pos, id) -> {
                                    closeFavoritesDrawer();
                                    loadFavoriteArticle(favoriteIds.get(pos));
                                });
                                favoritesListView.setOnItemLongClickListener((parent, v, pos, id) -> {
                                    closeFavoritesDrawer();
                                    showDeleteConfirmDialog(favoriteIds.get(pos), favoriteTitles.get(pos));
                                    return true;
                                });
                            } catch (Exception e) {
                                Log.e("article", "加载收藏列表失败", e);
                                favoritesLoading.setVisibility(View.GONE);
                                favoritesEmptyView.setText("加载失败");
                                favoritesEmptyView.setVisibility(View.VISIBLE);
                            }
                        }
                    }
                }, msg_success, msg_failed, "FavList");
    }

    // ==================== 加载收藏文章 ====================

    private void loadFavoriteArticle(long favoriteId) {
        showLoadingState();

        ApiBridge.enqueue(MemoryApiClient.composition().favoriteDetail(favoriteId, String.valueOf(userId)),
                new Handler(Looper.getMainLooper()) {
                    @Override
                    public void handleMessage(@NonNull Message msg) {
                        if (msg.what == msg_success) {
                            try {
                                JSONObject fav = new JSONObject((String) msg.obj);

                                String title = fav.getString("articleTitle");
                                String content = fav.getString("articleContent");

                                // 解析 sentenceAnalysis 和 highFrequencyWords (JSON 字符串)
                                JSONArray sentenceAnalysis = new JSONArray();
                                JSONArray highFrequencyWords = new JSONArray();
                                if (fav.has("sentenceAnalysis") && !fav.isNull("sentenceAnalysis")) {
                                    String saStr = fav.getString("sentenceAnalysis");
                                    if (saStr != null && !saStr.isEmpty()) {
                                        sentenceAnalysis = new JSONArray(saStr);
                                    }
                                }
                                if (fav.has("highFrequencyWords") && !fav.isNull("highFrequencyWords")) {
                                    String hfwStr = fav.getString("highFrequencyWords");
                                    if (hfwStr != null && !hfwStr.isEmpty()) {
                                        highFrequencyWords = new JSONArray(hfwStr);
                                    }
                                }

                                // 阅读计数（fire-and-forget，新栈经 ApiBridge，静默）
                                ApiBridge.enqueue(MemoryApiClient.composition().favoriteView(favoriteId),
                                        new Handler(Looper.getMainLooper()) {
                                            @Override
                                            public void handleMessage(@NonNull Message msg) {
                                                // 静默
                                            }
                                        }, msg_success, msg_failed, "FavoriteView");

                                displayArticle(title, content, sentenceAnalysis, highFrequencyWords);
                                currentFavoriteId = favoriteId;
                                isViewingFavorite = true;
                                isFavorited = true;
                                updateModeUI();
                            } catch (Exception e) {
                                Log.e("article", "加载收藏文章失败", e);
                                Toast.makeText(requireContext(), "加载失败", Toast.LENGTH_SHORT).show();
                                returnToTodayReading();
                            }
                        } else {
                            Log.e("article", "加载收藏文章失败");
                            Toast.makeText(requireContext(), "加载失败", Toast.LENGTH_SHORT).show();
                            returnToTodayReading();
                        }
                    }
                }, msg_success, msg_failed, "FavDetail");
    }

    // ==================== 删除收藏 ====================

    private void showDeleteConfirmDialog(long favoriteId, String title) {
        new MaterialAlertDialogBuilder(requireContext()).setTitle("取消收藏").setMessage("确定要取消收藏「" + title + "」吗？")
                .setPositiveButton("确定", (d, w) -> {
                    ApiBridge.enqueue(MemoryApiClient.composition().deleteFavorite(favoriteId, String.valueOf(userId)),
                            new Handler(Looper.getMainLooper()) {
                                @Override
                                public void handleMessage(@NonNull Message msg) {
                                    // 静默：本地状态已在下方同步清理
                                }
                            }, msg_success, msg_failed, "DeleteFavorite");
                    // 如果删除的是今日收藏的文章，同步清理本地状态
                    long dailyFavId = InnerSettingsManager.getInstance(requireContext()).getDailyFavoriteId(userId);
                    if (favoriteId == dailyFavId) {
                        clearDailyFavoriteState();
                    }
                    if (isViewingFavorite && currentFavoriteId == favoriteId) {
                        returnToTodayReading();
                    }
                }).setNegativeButton("取消", null).show();
    }

    private void deleteFavoriteAndReturn() {
        if (currentFavoriteId < 0)
            return;
        showDeleteConfirmDialog(currentFavoriteId, currentArticleTitle);
    }

    // ==================== 模式切换 ====================

    private void returnToTodayReading() {
        isViewingFavorite = false;
        currentFavoriteId = -1;
        isFavorited = false;
        updateModeUI();
        onArticleGenerate("dailyReading");
    }

    // ==================== 本地收藏状态持久化 ====================

    private void saveDailyFavoriteState(long favoriteId) {
        InnerSettingsManager.getInstance(requireContext()).saveDailyFavorite(userId, favoriteId, getToday());
    }

    private void clearDailyFavoriteState() {
        InnerSettingsManager.getInstance(requireContext()).clearDailyFavorite(userId);
    }

    private void restoreDailyFavoriteState(String currentTitle) {
        InnerSettingsManager settings = InnerSettingsManager.getInstance(requireContext());
        String savedDate = settings.getDailyFavoriteDate(userId);
        long savedId = settings.getDailyFavoriteId(userId);

        if (getToday().equals(savedDate) && savedId > 0) {
            isFavorited = true;
            currentFavoriteId = savedId;
        } else {
            isFavorited = false;
            currentFavoriteId = -1;
        }
        updateFavoriteIcon();
    }

    private String getToday() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    private void updateModeUI() {
        if (isViewingFavorite) {
            btnHistory.setVisibility(View.GONE);
            btnBackToday.setVisibility(View.VISIBLE);
            // tvTitleBar.setText(currentArticleTitle);
            updateFavoriteIcon();
        } else {
            btnHistory.setVisibility(View.VISIBLE);
            btnBackToday.setVisibility(View.GONE);
            // tvTitleBar.setText("每日一读");
            updateFavoriteIcon();
        }
    }

    // ==================== 辅助方法 ====================

    private void showLoadingState() {
        markdownTitleView.setText("加载中...");
        markdownContentView.setText("");
        sentenceAnalysisContainer.removeAllViews();
        highFrequencyWordsContainer.removeAllViews();
        if (loadingProgressBar != null) {
            loadingProgressBar.setVisibility(View.GONE);
        }
    }

    private void displayArticle(String title, String content, JSONArray sentenceAnalysis,
            JSONArray highFrequencyWords) {
        markdownTitleView.setText(title);
        currentArticleTitle = title;
        currentArticleContent = content;
        currentSentenceAnalysis = sentenceAnalysis;
        currentHighFrequencyWords = highFrequencyWords;

        Markwon markwon = Markwon.builder(requireContext()).build();
        markwon.setMarkdown(markdownContentView, content);

        applyFontSizeToContent();
        updateReadingTime(content);

        // 渲染长难句分析
        sentenceAnalysisContainer.removeAllViews();
        for (int i = 0; i < sentenceAnalysis.length(); i++) {
            try {
                JSONObject item = sentenceAnalysis.getJSONObject(i);
                String sentence = item.getString("sentence");
                String translation = item.getString("translation");

                TextView sentenceView = new TextView(requireContext());
                markwon.setMarkdown(sentenceView, sentence);
                sentenceView.setTextSize(17);
                sentenceView.setTextColor(ContextCompat.getColor(requireContext(), R.color.reader_text));
                sentenceView.setLineSpacing(0, 1.5f);
                sentenceView.setPadding(0, 0, 0, 6);
                sentenceAnalysisContainer.addView(sentenceView);

                TextView translationView = new TextView(requireContext());
                markwon.setMarkdown(translationView, translation);
                translationView.setTextSize(16);
                translationView.setTextColor(ContextCompat.getColor(requireContext(), R.color.reader_text_secondary));
                translationView.setLineSpacing(0, 1.4f);
                translationView.setPadding(0, 0, 0, 16);
                sentenceAnalysisContainer.addView(translationView);
            } catch (JSONException e) {
                Log.e("article", "解析长难句失败", e);
            }
        }

        // 渲染高频单词
        highFrequencyWordsContainer.removeAllViews();
        for (int i = 0; i < highFrequencyWords.length(); i++) {
            try {
                String word;
                String explanation;
                Object raw = highFrequencyWords.get(i);
                if (raw instanceof JSONObject) {
                    JSONObject item = (JSONObject) raw;
                    word = item.optString("word", "");
                    explanation = item.optString("explanation", "");
                } else {
                    // 容错：LLM 可能把整项输出成 "word: explanation" 字符串，提取单词与释义使其仍可交互
                    String[] parts = splitWordExplanation(String.valueOf(raw));
                    word = parts[0];
                    explanation = parts[1];
                }
                if (word.isEmpty()) {
                    continue;
                }
                // 去重：explanation 开头可能重复单词本身（如 "word: word：释义"）
                explanation = stripLeadingWord(word, explanation);
                if (explanation.isEmpty()) {
                    continue;
                }

                TextView wordView = new TextView(requireContext());
                markwon.setMarkdown(wordView, "**" + word + "**" + ": " + explanation);
                wordView.setTextSize(17);
                wordView.setTextColor(ContextCompat.getColor(requireContext(), R.color.reader_text));
                wordView.setLineSpacing(0, 1.4f);
                wordView.setPadding(0, 10, 0, 10);

                final String wordText = word;
                wordView.setOnClickListener(v -> {
                    boolean playType = AudioPlayer.getPlayType(wordText);
                    AudioPlayer.playAudio(v.getContext(), wordText, playType);
                });
                wordView.setClickable(true);
                wordView.setFocusable(true);
                highFrequencyWordsContainer.addView(wordView);
            } catch (JSONException e) {
                Log.e("article", "解析高频单词失败", e);
            }
        }
    }

    /**
     * 拆分 "word: explanation" / "word：explanation" 形式的字符串项。
     */
    private String[] splitWordExplanation(String text) {
        if (text == null) {
            text = "";
        }
        text = text.trim();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ':' || c == '：') {
                String w = text.substring(0, i).trim();
                if (w.isEmpty()) {
                    return new String[] { text, "" };
                }
                return new String[] { w, text.substring(i + 1).trim() };
            }
        }
        return new String[] { text, "" };
    }

    /**
     * 去除释义开头重复的单词本身（如 "conscientious：有责任心的…" → "有责任心的…"）。
     */
    private String stripLeadingWord(String word, String explanation) {
        if (word == null || word.isEmpty() || explanation == null) {
            return explanation;
        }
        String e = explanation.trim();
        String lowerE = e.toLowerCase();
        String lowerW = word.toLowerCase();
        while (!e.isEmpty() && lowerE.startsWith(lowerW)) {
            String rest = e.substring(word.length()).trim();
            if (rest.isEmpty()) {
                break;
            }
            char c = rest.charAt(0);
            if (c == ':' || c == '：' || c == '-' || c == '—' || c == ' ') {
                e = rest.substring(1).trim();
                lowerE = e.toLowerCase();
            } else {
                break;
            }
        }
        return e;
    }

    // ==================== 重试逻辑 ====================
    private void scheduleRetry() {
        if (!isAdded())
            return;
        if (retryCount >= MAX_RETRY_COUNT) {
            Log.i("article", "已达最大重试次数，停止轮询");
            return;
        }

        retryRunnable = () -> {
            if (isAdded() && !isRetrying) {
                isRetrying = true;
                retryCount++;
                Log.i("article", "通用文章已显示，正在尝试获取个性化文章... (第 " + retryCount + " 次)");
                onArticleGenerate("dailyReading", true); // 使用重载方法
            }
        };
        retryHandler.postDelayed(retryRunnable, RETRY_DELAY_MS);
    }

    private void cancelRetry() {
        if (retryHandler != null && retryRunnable != null) {
            retryHandler.removeCallbacks(retryRunnable);
        }
        isRetrying = false;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelRetry();
    }
}