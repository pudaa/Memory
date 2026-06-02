package com.deepsleep.memory.ui.treasure_view.pronunciation_view;

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
        void onScoreResult(String word, double overallScore, String level, String feedback,
                          String asrTranscript, String referenceText, JSONArray words);
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
        if (score != null && score >= 0) {
            holder.recordButton.setText(String.valueOf(score));
        } else if (score != null && score < 0) {
            holder.recordButton.setText("?");
        } else {
            holder.recordButton.setText("●");
        }
        holder.bind(item, position == expandedPosition);

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
                        // Toast.makeText(context, "录音已保存: " + item.getWord(), Toast.LENGTH_SHORT).show();
                        holder.replayButton.setEnabled(true);
                        byte[] audioData = audioRecord.getPCMData();

                        // holder.recordButton.setText("99");
                        uploadPronunciationForCorrection(filePath, item.getWord());

                    }

                    @Override
                    public void onError(String error) {
                        // Toast.makeText(context, "录音错误: " + error, Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                // 开始录音
                String fileName = "pronunciation_" + item.getWord() + "_" + System.currentTimeMillis() + ".m4a";
                audioRecord.startRecording(fileName, new MemAudioRecord.OnRecordListener() {
                    @Override
                    public void onRecordStart() {
                        // Toast.makeText(context, "开始录音: " + item.getWord(), Toast.LENGTH_SHORT).show();
                        holder.recordButton.setText("~");
                    }

                    @Override
                    public void onRecordStop(String filePath) {
                    }

                    @Override
                    public void onError(String error) {
                        // Toast.makeText(context, "录音错误: " + error, Toast.LENGTH_SHORT).show();
                        holder.recordButton.setText("●");
                    }
                }, context);
            }

        });

        holder.replayButton.setOnClickListener(v -> {
            if (audioRecord.isPlaying()) {
                audioRecord.stopPlaying();
                holder.replayButton.setText("↺");
            } else {
                audioRecord.playRecording(new MemAudioRecord.OnPlayListener() {
                    @Override
                    public void onPlayStart() {
                        holder.replayButton.setText("~");
                    }

                    @Override
                    public void onPlayComplete() {
                        holder.replayButton.setText("↺");
                    }

                    @Override
                    public void onError(String error) {
                        // Toast.makeText(context, "播放错误: " + error, Toast.LENGTH_SHORT).show();
                        holder.replayButton.setText("↺");
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
                            示例响应体
                            PhonemeScoreResult(overallScore=100.0, phonemeAccuracy=1.0, wordCountReference=1, wordCountSpoken=1, asrTranscript=Institution, referenceText=institution, level=excellent, feedback=发音非常标准！, words=[PhonemeScoreResult.WordPhonemeScore(word=institution, spokenWord=institution, startTime=0.0, endTime=1.32, score=100.0, expectedPhonemes=[IH, N, S, T, IH, T, UW, SH, AH, N], actualPhonemes=[IH, N, S, T, IH, T, UW, SH, AH, N], phonemeAccuracy=1.0, errors=[], status=correct)])
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
                                        scoreResultListener.onScoreResult(
                                                referenceText, overallScore, level, feedback,
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
                                        scoreResultListener.onScoreResult(
                                                referenceText, overallScore, "", "",
                                                "", referenceText, null);
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
        LinearLayout expandedSection;
        Button playButton, recordButton, replayButton; // 添加按钮引用

        ViewHolder(View view) {
            wordText = view.findViewById(R.id.word_text);
            meaningText = view.findViewById(R.id.meaning_text);
            expandedSection = view.findViewById(R.id.expanded_section);
            playButton = view.findViewById(R.id.play_button);
            recordButton = view.findViewById(R.id.record_button);
            replayButton = view.findViewById(R.id.replay_button);

            initView();
        }

        void initView() {
            playButton.setEnabled(true);
            recordButton.setEnabled(true);
            replayButton.setEnabled(false);
        }

        void bind(WordPhraseItem item, boolean isExpanded) {
            wordText.setText(item.getWord());
            meaningText.setText(item.getMeaning());
            expandedSection.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        }
    }
}