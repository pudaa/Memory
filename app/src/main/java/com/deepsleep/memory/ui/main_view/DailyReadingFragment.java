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
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.deepsleep.memory.R;
import com.deepsleep.memory.handle_utils.AudioPlayer;
import com.deepsleep.memory.network.CozeAPI;
import com.deepsleep.memory.network.GetDataByThread;
import io.noties.markwon.*;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class DailyReadingFragment extends Fragment {
    String ACCESS_TOKEN = "pat_IIANC6ApULu0iK2AkEj8IxcZSEyROShxpOWUP0pHXRv4EnpSqKHnY9WuCDvAnHHa";
    String BOT_ID = "7486395931509178405";

    CozeAPI cozeAPI = new CozeAPI(ACCESS_TOKEN, BOT_ID);
    private TextView markdownTitleView;
    private TextView markdownContentView;

    private LinearLayout sentenceAnalysisContainer;
    private LinearLayout highFrequencyWordsContainer;
    private ImageButton btnRefresh;
    private ProgressBar loadingProgressBar;
    private static final String PREF_NAME = "UserPrefs";
    private static final String KEY_USER_ID = "userId";
    private int userId;
    // 线程处理
    static final  int msg_success = 1;
    static final  int msg_failed = -1;
    private Map<TextView, ObjectAnimator> animatorMap = new HashMap<>();


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_daily_reading, container, false);
        markdownTitleView = view.findViewById(R.id.markdown_title_view);
        markdownContentView = view.findViewById(R.id.markdown_content_view);
        sentenceAnalysisContainer = view.findViewById(R.id.markdown_sentenceAnalysis_container);
        highFrequencyWordsContainer = view.findViewById(R.id.markdown_highFrequencyWords_container);

        loadingProgressBar = view.findViewById(R.id.loading_progress_bar);

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
        loadingSentence.setTextSize(22);
        loadingSentence.setTextColor(getResources().getColor(R.color.dark_gray));
        sentenceAnalysisContainer.addView(loadingSentence);

        TextView loadingWords = new TextView(requireContext());
        loadingWords.setText("正在生成高频易错单词……");
        loadingWords.setTextSize(22);
        loadingWords.setTextColor(getResources().getColor(R.color.dark_gray));
        highFrequencyWordsContainer.addView(loadingWords);

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

                        Markwon markwon = Markwon.builder(requireContext()).build();
                        markwon.setMarkdown(markdownContentView, content);

                        // 解析长难句分析部分并动态添加TextView
                        sentenceAnalysisContainer.removeAllViews();
                        JSONArray sentenceAnalysisArray = articleJson.getJSONArray("sentenceAnalysis");
                        for (int i = 0; i < sentenceAnalysisArray.length(); i++) {
                            JSONObject sentenceObj = sentenceAnalysisArray.getJSONObject(i);
                            String sentence = sentenceObj.getString("sentence");
                            String translation = sentenceObj.getString("translation");

                            // 添加原句
                            TextView sentenceView = new TextView(requireContext());
                            markwon.setMarkdown(sentenceView, sentence);
                            sentenceView.setTextSize(22);
                            sentenceView.setTextColor(getResources().getColor(R.color.dark_gray));
                            sentenceView.setLineSpacing(0, 1.2f);
                            sentenceView.setPadding(0, 0, 0, 8);
                            sentenceAnalysisContainer.addView(sentenceView);

                            // 添加翻译
                            TextView translationView = new TextView(requireContext());
                            markwon.setMarkdown(translationView, translation);
                            translationView.setTextSize(22);
                            translationView.setTextColor(getResources().getColor(R.color.dark_gray));
                            translationView.setLineSpacing(0, 1.2f);
                            translationView.setPadding(0, 0, 0, 20);
                            sentenceAnalysisContainer.addView(translationView);
                        }

                        // 解析高频易错单词部分并动态添加TextView
                        highFrequencyWordsContainer.removeAllViews();
                        JSONArray highFrequencyWordsArray = articleJson.getJSONArray("highFrequencyWords");
                        for (int i = 0; i < highFrequencyWordsArray.length(); i++) {
                            JSONObject wordObj = highFrequencyWordsArray.getJSONObject(i);
                            String word = wordObj.getString("word");
                            String explanation = wordObj.getString("explanation");

                            // 添加单词和解释
                            TextView wordView = new TextView(requireContext());
                            markwon.setMarkdown(wordView, "**" + word + "**" + ": " + explanation);
                            wordView.setTextSize(22);
                            wordView.setTextColor(getResources().getColor(R.color.dark_gray));
                            wordView.setLineSpacing(0, 1.2f);
                            wordView.setPadding(0, 0, 0, 16);

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


                }else if (msg.what == msg_failed) {
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

}