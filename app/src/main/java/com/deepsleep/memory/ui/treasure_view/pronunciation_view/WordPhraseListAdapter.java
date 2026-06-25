package com.deepsleep.memory.ui.treasure_view.pronunciation_view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.deepsleep.memory.R;
import com.deepsleep.memory.handle_utils.AudioPlayer;
import com.deepsleep.memory.handle_utils.MemAudioRecord;
import com.deepsleep.memory.network.GetDataByThread;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class WordPhraseListAdapter extends BaseAdapter {

    /**
     * 评分结果回调——通知 Activity 在 BottomSheet 中展示详细结果
     */
    public interface OnScoreResultListener {
        void onScoreResult(String word, double overallScore, String level, String feedback, String asrTranscript,
                String referenceText, JSONArray words);
    }

    private List<WordPhraseItem> wordPhraseList;
    private Context context;
    private int expandedPosition = -1;
    private MemAudioRecord audioRecord;
    private SparseArray<Integer> scoreMap = new SparseArray<>();
    private OnScoreResultListener scoreResultListener;

    public void setOnScoreResultListener(OnScoreResultListener listener) {
        this.scoreResultListener = listener;
    }

    private void handleRecordStop(String filePath, String word, Integer score) {
        if (score != null) {
            int position = findPositionByWord(word);
            if (position >= 0) {
                scoreMap.put(position, score);
                ((Activity) context).runOnUiThread(() -> notifyDataSetChanged());
            }
        }
    }

    private int findPositionByWord(String word) {
        for (int i = 0; i < wordPhraseList.size(); i++) {
            WordPhraseItem item = wordPhraseList.get(i);
            if (item.getWord().equals(word)) {
                return i;
            }
        }
        return -1;
    }

    /** 从持久化成绩恢复评分显示（外部调用） */
    public void restoreScore(String word, int score) {
        int position = findPositionByWord(word);
        if (position >= 0 && score > 0) {
            scoreMap.put(position, score);
        }
    }

    public WordPhraseListAdapter(Context context, List<WordPhraseItem> wordPhraseList) {
        this.context = context;
        this.wordPhraseList = wordPhraseList;
        this.audioRecord = new MemAudioRecord();
    }

    @Override
    public int getCount() {
        return wordPhraseList.size();
    }

    @Override
    public Object getItem(int position) {
        return wordPhraseList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        ViewHolder holder;

        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.fragment_word_phrase, parent, false);
            holder = new ViewHolder(view);
            view.setTag(holder);
        } else {
            holder = (ViewHolder) view.getTag();
        }

        final WordPhraseItem item = wordPhraseList.get(position);
        Integer score = scoreMap.get(position);
        holder.bind(item, position == expandedPosition, position, score);

        // 设置点击事件
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (expandedPosition == position) {
                    // 不响应点击
                    return;
                } else {
                    expandedPosition = position;
                }
                notifyDataSetChanged();
            }
        });

        holder.playButton.setOnClickListener(v -> {
            boolean playType = AudioPlayer.getPlayType(item.getWord());
            AudioPlayer.playAudio(context, item.getWord(), playType);
        });

        holder.recordButton.setOnClickListener(v -> {
            if (context.checkSelfPermission(
                    android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
                builder.setTitle("需要录音权限");
                builder.setMessage("此功能需要录音权限来录制您的发音，请授予录音权限。");
                builder.setPositiveButton("授予权限", (dialog, which) -> {
                    // 请求权限
                    ((Activity) context).requestPermissions(new String[] { android.Manifest.permission.RECORD_AUDIO },
                            123);
                });
                builder.setNegativeButton("取消", (dialog, which) -> {
                    dialog.dismiss();
                    Toast.makeText(context, "需要录音权限才能使用此功能", Toast.LENGTH_SHORT).show();
                });
                builder.setCancelable(false);
                builder.show();
                return;
            }

            if (audioRecord.isRecording()) {
                // 停止录音
                audioRecord.stopRecording(new MemAudioRecord.OnRecordListener() {
                    @Override
                    public void onRecordStart() {
                    }

                    @Override
                    public void onRecordStop(String filePath) {
                        holder.stopRecordingAnimation();
                        holder.replayButton.setEnabled(true);
                        holder.replayButton.setAlpha(1.0f);
                        byte[] audioData = audioRecord.getPCMData();
                        uploadPronunciationForCorrection(filePath, item.getWord());
                    }

                    @Override
                    public void onError(String error) {
                        holder.stopRecordingAnimation();
                    }
                });
            } else {
                // 开始录音
                String fileName = "pronunciation_" + item.getWord() + "_" + System.currentTimeMillis() + ".m4a";
                audioRecord.startRecording(fileName, new MemAudioRecord.OnRecordListener() {
                    @Override
                    public void onRecordStart() {
                        holder.recordIcon.setImageResource(R.drawable.ic_follow_stop);
                        holder.startRecordingAnimation();
                    }

                    @Override
                    public void onRecordStop(String filePath) {
                    }

                    @Override
                    public void onError(String error) {
                        holder.recordIcon.setImageResource(R.drawable.ic_mic_24dp);
                        holder.stopRecordingAnimation();
                    }
                }, context);
            }

        });

        holder.replayButton.setOnClickListener(v -> {
            if (audioRecord.isPlaying()) {
                audioRecord.stopPlaying();
                holder.replayButton.setAlpha(1.0f);
            } else {
                audioRecord.playRecording(new MemAudioRecord.OnPlayListener() {
                    @Override
                    public void onPlayStart() {
                        holder.replayButton.setAlpha(0.4f);
                    }

                    @Override
                    public void onPlayComplete() {
                        holder.replayButton.setAlpha(1.0f);
                    }

                    @Override
                    public void onError(String error) {
                        holder.replayButton.setAlpha(1.0f);
                    }
                });
            }
        });

        return view;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        audioRecord.cleanup();
    }

    private void uploadPronunciationForCorrection(String filePath, String referenceText) {
        try {
            Uri fileUri = Uri.parse("file://" + filePath);

            // 创建Handler处理网络请求结果
            Handler pronunciationHandler = new Handler(Looper.getMainLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                    case 1: // 成功
                        String response = (String) msg.obj;
                        try {
                            JSONObject jsonResponse = new JSONObject(response);
                            /*
                             * 示例响应体 PhonemeScoreResult(overallScore=100.0, phonemeAccuracy=1.0,
                             * wordCountReference=1, wordCountSpoken=1, asrTranscript=Institution,
                             * referenceText=institution, level=excellent, feedback=发音非常标准！,
                             * words=[PhonemeScoreResult.WordPhonemeScore(word=institution,
                             * spokenWord=institution, startTime=0.0, endTime=1.32, score=100.0,
                             * expectedPhonemes=[IH, N, S, T, IH, T, UW, SH, AH, N], actualPhonemes=[IH, N,
                             * S, T, IH, T, UW, SH, AH, N], phonemeAccuracy=1.0, errors=[],
                             * status=correct)])
                             */
                            if ("200".equals(String.valueOf(jsonResponse.optInt("code", -1)))) {
                                JSONObject data = jsonResponse.getJSONObject("data");

                                double overallScore = data.optDouble("overallScore", -1);
                                String level = data.optString("level", "");
                                String feedback = data.optString("feedback", "");
                                String asrTranscript = data.optString("asrTranscript", "");
                                JSONArray words = data.optJSONArray("words");

                                final int displayScore = (overallScore >= 0) ? (int) overallScore : -1;

                                ((Activity) context).runOnUiThread(() -> {
                                    // 更新按钮分数
                                    handleRecordStop(filePath, referenceText, displayScore);
                                    // 通知 Activity 弹出 BottomSheet 展示详细结果
                                    if (scoreResultListener != null) {
                                        scoreResultListener.onScoreResult(referenceText, overallScore, level, feedback,
                                                asrTranscript, data.optString("referenceText", referenceText), words);
                                    }
                                });
                            } else {
                                String errMsg = jsonResponse.optString("message", "发音纠正失败");
                                Toast.makeText(context, errMsg, Toast.LENGTH_SHORT).show();
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                            try {
                                JSONObject data = new JSONObject(response);
                                int overallScore = data.getInt("overallScore");
                                ((Activity) context).runOnUiThread(() -> {
                                    handleRecordStop(filePath, referenceText, overallScore);
                                    if (scoreResultListener != null) {
                                        scoreResultListener.onScoreResult(referenceText, overallScore, "", "", "",
                                                referenceText, null);
                                    }
                                });
                            } catch (JSONException e2) {
                                Toast.makeText(context, "解析评分数据失败", Toast.LENGTH_SHORT).show();
                            }
                        }
                        break;
                    case 0: // 失败
                        Toast.makeText(context, "发音纠正失败", Toast.LENGTH_SHORT).show();
                        break;
                    }
                }
            };

            GetDataByThread getDataTask = new GetDataByThread("/pronunciation/correct");
            getDataTask.correctPronunciation(pronunciationHandler, 1, 0, fileUri, referenceText, context);
        } catch (Exception e) {
            e.printStackTrace();
            // Toast.makeText(context, "无法读取录音文件", Toast.LENGTH_SHORT).show();
        }
    }

    static class ViewHolder {
        TextView wordText;
        TextView meaningText;
        TextView wordIndex;
        TextView scoreBadge;
        LinearLayout expandedSection;
        ImageButton playButton;
        FrameLayout recordButton;
        ImageView recordIcon;
        TextView recordScore;
        ImageButton replayButton;
        View recordRipple;
        ValueAnimator pulseAnimator;
        ValueAnimator rippleAnimator;

        ViewHolder(View view) {
            wordText = view.findViewById(R.id.word_text);
            meaningText = view.findViewById(R.id.meaning_text);
            wordIndex = view.findViewById(R.id.word_index);
            scoreBadge = view.findViewById(R.id.score_badge);
            expandedSection = view.findViewById(R.id.expanded_section);
            playButton = view.findViewById(R.id.play_button);
            recordButton = view.findViewById(R.id.record_button);
            recordIcon = view.findViewById(R.id.record_icon);
            recordScore = view.findViewById(R.id.record_score);
            replayButton = view.findViewById(R.id.replay_button);
            recordRipple = view.findViewById(R.id.record_ripple);

            initView();
        }

        void initView() {
            playButton.setEnabled(true);
            recordButton.setEnabled(true);
            replayButton.setEnabled(false);
        }

        /** 开始录音动画：按钮脉冲 + 外层涟漪扩散 */
        void startRecordingAnimation() {
            // 按钮脉冲动画
            pulseAnimator = ValueAnimator.ofFloat(1.0f, 1.08f);
            pulseAnimator.setDuration(600);
            pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
            pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
            pulseAnimator.addUpdateListener(animation -> {
                float scale = (float) animation.getAnimatedValue();
                recordButton.setScaleX(scale);
                recordButton.setScaleY(scale);
            });
            pulseAnimator.start();

            // 涟漪扩散动画
            recordRipple.setVisibility(View.VISIBLE);
            recordRipple.setAlpha(0.35f);
            recordRipple.setScaleX(1.0f);
            recordRipple.setScaleY(1.0f);
            rippleAnimator = ValueAnimator.ofFloat(1.0f, 1.9f);
            rippleAnimator.setDuration(1000);
            rippleAnimator.setRepeatMode(ValueAnimator.RESTART);
            rippleAnimator.setRepeatCount(ValueAnimator.INFINITE);
            rippleAnimator.addUpdateListener(animation -> {
                float scale = (float) animation.getAnimatedValue();
                float alpha = 0.35f * (1.0f - (scale - 1.0f) / 0.9f);
                recordRipple.setScaleX(scale);
                recordRipple.setScaleY(scale);
                recordRipple.setAlpha(Math.max(0.0f, alpha));
            });
            rippleAnimator.start();
        }

        /** 停止录音动画，恢复初始状态 */
        void stopRecordingAnimation() {
            if (pulseAnimator != null) {
                pulseAnimator.cancel();
                pulseAnimator = null;
            }
            if (rippleAnimator != null) {
                rippleAnimator.cancel();
                rippleAnimator = null;
            }
            recordButton.setScaleX(1.0f);
            recordButton.setScaleY(1.0f);
            recordRipple.setVisibility(View.GONE);
            recordRipple.setAlpha(0.0f);
            recordRipple.setScaleX(1.0f);
            recordRipple.setScaleY(1.0f);
        }

        void bind(WordPhraseItem item, boolean isExpanded, int position, Integer score) {
            wordText.setText(item.getWord());
            meaningText.setText(item.getMeaning());
            wordIndex.setText(String.valueOf(position + 1));
            expandedSection.setVisibility(isExpanded ? View.VISIBLE : View.GONE);

            // 得分徽章 + 录音按钮状态
            if (score != null && score >= 0) {
                scoreBadge.setText(String.valueOf(score));
                scoreBadge.setVisibility(View.VISIBLE);
                recordIcon.setVisibility(View.GONE);
                recordScore.setText(String.valueOf(score));
                recordScore.setVisibility(View.VISIBLE);
            } else if (score != null && score < 0) {
                scoreBadge.setText("?");
                scoreBadge.setVisibility(View.VISIBLE);
                recordIcon.setVisibility(View.GONE);
                recordScore.setText("?");
                recordScore.setVisibility(View.VISIBLE);
            } else {
                scoreBadge.setVisibility(View.GONE);
                recordIcon.setVisibility(View.VISIBLE);
                recordIcon.setImageResource(R.drawable.ic_mic_24dp);
                recordScore.setVisibility(View.GONE);
            }
        }
    }
}