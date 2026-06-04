package com.deepsleep.memory.ui.main_view;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.SharedPreferences;
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
import androidx.fragment.app.Fragment;
import com.deepsleep.memory.R;
import com.deepsleep.memory.handle_utils.AudioPlayer;
import com.deepsleep.memory.network.CozeAPI;
import com.deepsleep.memory.network.ApiConstants;
import com.deepsleep.memory.network.GetDataByThread;
import com.deepsleep.memory.network.HttpManager;
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
    String ACCESS_TOKEN = "pat_IIANC6ApULu0iK2AkEj8IxcZSEyROShxpOWUP0pHXRv4EnpSqKHnY9WuCDvAnHHa";
    String BOT_ID = "7486395931509178405";

    CozeAPI cozeAPI = new CozeAPI(ACCESS_TOKEN, BOT_ID);
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
    private static final String PREF_NAME = "UserPrefs";
    private static final String KEY_USER_ID = "userId";
    private static final String PREF_READER = "ReaderPrefs";
    private static final String KEY_FONT_SIZE = "reader_font_size";
    private static final String PREF_DAILY = "DailyFavoritePrefs";
    private static final String KEY_DAILY_FAV_ID = "daily_favorite_id";
    private static final String KEY_DAILY_FAV_DATE = "daily_favorite_date";
    private int userId;
    private int currentFontSize = 19;
    private static final int FONT_SIZE_MIN = 15;
    private static final int FONT_SIZE_MAX = 25;
    private static final int FONT_SIZE_STEP = 2;
    // 线程处理
    static final int msg_success = 1;
    static final int msg_failed = -1;
    private Map<TextView, ObjectAnimator> animatorMap = new HashMap<>();

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

        SharedPreferences readerPrefs = requireContext().getSharedPreferences(PREF_READER, Context.MODE_PRIVATE);
        currentFontSize = readerPrefs.getInt(KEY_FONT_SIZE, 19);
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
        SharedPreferences sharedPreferences = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        userId = sharedPreferences.getInt(KEY_USER_ID, 0);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        onArticleGenerate("dailyReading");
    }

    public void onArticleGenerate(String mode) {
        markdownTitleView.setText("请等待，文章生成中");
        markdownContentView.setText("正在梳理薄弱单词……");
        sentenceAnalysisContainer.removeAllViews();
        highFrequencyWordsContainer.removeAllViews();

        TextView loadingSentence = new TextView(requireContext());
        loadingSentence.setText("正在生成长难句分析……");
        loadingSentence.setTextSize(17);
        loadingSentence.setTextColor(getResources().getColor(R.color.reader_text_secondary));
        loadingSentence.setPadding(0, 8, 0, 8);
        sentenceAnalysisContainer.addView(loadingSentence);

        TextView loadingWords = new TextView(requireContext());
        loadingWords.setText("正在生成高频易错单词……");
        loadingWords.setTextSize(17);
        loadingWords.setTextColor(getResources().getColor(R.color.reader_text_secondary));
        loadingWords.setPadding(0, 8, 0, 8);

        if (loadingProgressBar != null) {
            loadingProgressBar.setVisibility(View.GONE);
            startWaveAnimation(markdownContentView);
            startWaveAnimation(loadingSentence);
            startWaveAnimation(loadingWords);
        }

        GetDataByThread getDataByThread;
        if ("dailyReading".equals(mode)) {
            getDataByThread = new GetDataByThread("/composition/dailyReading");
        } else {
            getDataByThread = new GetDataByThread("/composition/generateArticle");
        }
        getDataByThread.getDailyReading(new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {

                if (msg.what == msg_success) {
                    if (loadingProgressBar != null) {
                        loadingProgressBar.setVisibility(View.GONE);
                        stopWaveAnimation(markdownContentView);
                    }

                    String article = (String) msg.obj;
                    Log.i("article", "文章生成成功");
                    // 对字符串处理，去掉首尾空格以保证解析正确
                    article = article.trim();
                    try {
                        // 解析JSON格式的文章内容
                        JSONObject articleJson = new JSONObject(article);

                        String title = articleJson.getString("title");
                        String content = articleJson.getString("content");

                        markdownTitleView.setText(title);
                        currentArticleTitle = title;
                        currentArticleContent = content;

                        Markwon markwon = Markwon.builder(requireContext()).build();
                        markwon.setMarkdown(markdownContentView, content);

                        applyFontSizeToContent();
                        updateReadingTime(content);

                        // 恢复今日文章收藏状态
                        restoreDailyFavoriteState(title);

                        // 解析长难句分析部分并动态添加TextView
                        sentenceAnalysisContainer.removeAllViews();
                        JSONArray sentenceAnalysisArray = articleJson.getJSONArray("sentenceAnalysis");
                        currentSentenceAnalysis = sentenceAnalysisArray;
                        for (int i = 0; i < sentenceAnalysisArray.length(); i++) {
                            JSONObject sentenceObj = sentenceAnalysisArray.getJSONObject(i);
                            String sentence = sentenceObj.getString("sentence");
                            String translation = sentenceObj.getString("translation");

                            // 添加原句
                            TextView sentenceView = new TextView(requireContext());
                            markwon.setMarkdown(sentenceView, sentence);
                            sentenceView.setTextSize(17);
                            sentenceView.setTextColor(getResources().getColor(R.color.reader_text));
                            sentenceView.setLineSpacing(0, 1.5f);
                            sentenceView.setPadding(0, 0, 0, 6);
                            sentenceAnalysisContainer.addView(sentenceView);

                            // 添加翻译
                            TextView translationView = new TextView(requireContext());
                            markwon.setMarkdown(translationView, translation);
                            translationView.setTextSize(16);
                            translationView.setTextColor(getResources().getColor(R.color.reader_text_secondary));
                            translationView.setLineSpacing(0, 1.4f);
                            translationView.setPadding(0, 0, 0, 16);
                            sentenceAnalysisContainer.addView(translationView);
                        }

                        // 解析高频易错单词部分并动态添加TextView
                        highFrequencyWordsContainer.removeAllViews();
                        JSONArray highFrequencyWordsArray = articleJson.getJSONArray("highFrequencyWords");
                        currentHighFrequencyWords = highFrequencyWordsArray;
                        for (int i = 0; i < highFrequencyWordsArray.length(); i++) {
                            JSONObject wordObj = highFrequencyWordsArray.getJSONObject(i);
                            String word = wordObj.getString("word");
                            String explanation = wordObj.getString("explanation");

                            // 添加单词和解释 - 带卡片样式的单词条目
                            TextView wordView = new TextView(requireContext());
                            markwon.setMarkdown(wordView, "**" + word + "**" + ": " + explanation);
                            wordView.setTextSize(17);
                            wordView.setTextColor(getResources().getColor(R.color.reader_text));
                            wordView.setLineSpacing(0, 1.4f);
                            wordView.setPadding(0, 10, 0, 10);

                            // 为单词添加点击事件（用于播放音频）
                            final String wordText = word;
                            wordView.setOnClickListener(v -> {
                                boolean playType = AudioPlayer.getPlayType(wordText);
                                AudioPlayer.playAudio(v.getContext(), wordText, playType);
                            });

                            wordView.setClickable(true);
                            wordView.setFocusable(true);

                            highFrequencyWordsContainer.addView(wordView);
                        }

                    } catch (JSONException e) {
                        // 如果解析JSON失败，重新发送请求
                        onArticleGenerate("generateArticle");
                    }

                } else if (msg.what == msg_failed) {
                    Log.i("article", "文章生成失败");
                }
            }
        }, msg_success, msg_failed, String.valueOf(userId));
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
        SharedPreferences readerPrefs = requireContext().getSharedPreferences(PREF_READER, Context.MODE_PRIVATE);
        readerPrefs.edit().putInt(KEY_FONT_SIZE, currentFontSize).apply();
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
            // 收藏
            new Thread(() -> {
                try {
                    JSONObject body = new JSONObject();
                    body.put("title", currentArticleTitle);
                    body.put("content", currentArticleContent);
                    body.put("sentenceAnalysis", currentSentenceAnalysis);
                    body.put("highFrequencyWords", currentHighFrequencyWords);
                    body.put("wordList", "");
                    body.put("note", "");
                    String resp = HttpManager.doHttpPostWithJson(
                            ApiConstants.getFullUrl("/composition/dailyReading/favorite"), body, "userId",
                            String.valueOf(userId));
                    if (resp != null) {
                        JSONObject result = new JSONObject(resp);
                        if (result.has("favoriteId")) {
                            currentFavoriteId = result.getLong("favoriteId");
                            saveDailyFavoriteState(currentFavoriteId);
                        }
                    }
                } catch (Exception e) {
                    Log.e("article", "收藏请求失败", e);
                }
            }).start();
        } else {
            // 取消收藏
            if (currentFavoriteId > 0) {
                new Thread(() -> {
                    try {
                        HttpManager.doHttpDelete(ApiConstants.getFullUrl("/composition/favorites/" + currentFavoriteId),
                                "userId", String.valueOf(userId));
                    } catch (Exception e) {
                        Log.e("article", "取消收藏失败", e);
                    }
                }).start();
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
        new Thread(() -> {
            try {
                String resp = HttpManager.doHttpGetOneHeader(ApiConstants.getFullUrl("/composition/favorites"),
                        "userId", String.valueOf(userId));
                JSONArray favorites = new JSONArray(resp);

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

                requireActivity().runOnUiThread(() -> {
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
                            text.setTextColor(getResources().getColor(R.color.reader_text));
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
                });
            } catch (Exception e) {
                Log.e("article", "加载收藏列表失败", e);
                requireActivity().runOnUiThread(() -> {
                    favoritesLoading.setVisibility(View.GONE);
                    favoritesEmptyView.setText("加载失败");
                    favoritesEmptyView.setVisibility(View.VISIBLE);
                });
            }
        }).start();
    }

    // ==================== 加载收藏文章 ====================

    private void loadFavoriteArticle(long favoriteId) {
        showLoadingState();

        new Thread(() -> {
            try {
                String resp = HttpManager.doHttpGetOneHeader(
                        ApiConstants.getFullUrl("/composition/favorites/" + favoriteId), "userId",
                        String.valueOf(userId));
                JSONObject fav = new JSONObject(resp);

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

                HttpManager.doHttpPost(ApiConstants.getFullUrl("/composition/favorites/" + favoriteId + "/view"));

                final JSONArray saFinal = sentenceAnalysis;
                final JSONArray hfwFinal = highFrequencyWords;
                requireActivity().runOnUiThread(() -> { // 回到主线程更新UI
                    displayArticle(title, content, saFinal, hfwFinal);
                    currentFavoriteId = favoriteId;
                    isViewingFavorite = true;
                    isFavorited = true;
                    updateModeUI();
                });
            } catch (Exception e) {
                Log.e("article", "加载收藏文章失败", e);
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "加载失败", Toast.LENGTH_SHORT).show();
                    returnToTodayReading();
                });
            }
        }).start();
    }

    // ==================== 删除收藏 ====================

    private void showDeleteConfirmDialog(long favoriteId, String title) {
        new MaterialAlertDialogBuilder(requireContext()).setTitle("取消收藏").setMessage("确定要取消收藏「" + title + "」吗？")
                .setPositiveButton("确定", (d, w) -> {
                    new Thread(() -> {
                        try {
                            HttpManager.doHttpDelete(ApiConstants.getFullUrl("/composition/favorites/" + favoriteId),
                                    "userId", String.valueOf(userId));
                        } catch (Exception e) {
                            Log.e("article", "删除收藏失败", e);
                        }
                    }).start();
                    // 如果删除的是今日收藏的文章，同步清理本地状态
                    SharedPreferences dailyPrefs = requireContext().getSharedPreferences(PREF_DAILY,
                            Context.MODE_PRIVATE);
                    long dailyFavId = dailyPrefs.getLong(KEY_DAILY_FAV_ID + "_" + userId, -1);
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
        SharedPreferences prefs = requireContext().getSharedPreferences(PREF_DAILY, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_DAILY_FAV_ID + "_" + userId, favoriteId)
                .putString(KEY_DAILY_FAV_DATE + "_" + userId, getToday()).apply();
    }

    private void clearDailyFavoriteState() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREF_DAILY, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_DAILY_FAV_ID + "_" + userId).remove(KEY_DAILY_FAV_DATE + "_" + userId).apply();
    }

    private void restoreDailyFavoriteState(String currentTitle) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREF_DAILY, Context.MODE_PRIVATE);
        String savedDate = prefs.getString(KEY_DAILY_FAV_DATE + "_" + userId, "");
        long savedId = prefs.getLong(KEY_DAILY_FAV_ID + "_" + userId, -1);

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
                sentenceView.setTextColor(getResources().getColor(R.color.reader_text));
                sentenceView.setLineSpacing(0, 1.5f);
                sentenceView.setPadding(0, 0, 0, 6);
                sentenceAnalysisContainer.addView(sentenceView);

                TextView translationView = new TextView(requireContext());
                markwon.setMarkdown(translationView, translation);
                translationView.setTextSize(16);
                translationView.setTextColor(getResources().getColor(R.color.reader_text_secondary));
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
                JSONObject item = highFrequencyWords.getJSONObject(i);
                String word = item.getString("word");
                String explanation = item.getString("explanation");

                TextView wordView = new TextView(requireContext());
                markwon.setMarkdown(wordView, "**" + word + "**" + ": " + explanation);
                wordView.setTextSize(17);
                wordView.setTextColor(getResources().getColor(R.color.reader_text));
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

}