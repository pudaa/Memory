package com.deepsleep.memory.ui.treasure_view.dictation_view;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.deepsleep.memory.R;
import com.deepsleep.memory.network.ApiConstants;
import com.deepsleep.memory.network.HttpManager;
import com.deepsleep.memory.settings.InnerSettingsManager;
import com.deepsleep.memory.ui.components.CameraCaptureActivity;
import com.deepsleep.memory.ui.components.ThemeCropActivity;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 听写练习 - 执行听写页 逐词播放音频 → 用户输入 → 提交
 */
public class DictationExecutionActivity extends AppCompatActivity {

    private static final int MSG_TASK_SUCCESS = 1;
    private static final int MSG_TASK_FAILED = 2;
    private static final int MSG_SUBMIT_SUCCESS = 3;
    private static final int MSG_SUBMIT_FAILED = 4;
    private static final int MSG_OCR_SUCCESS = 5;
    private static final int MSG_OCR_FAILED = 6;

    private static final int MAX_AUTO_PLAY = 2;
    private static final int MAX_MANUAL_REPLAY = 1;
    private static final int REPLAY_INTERVAL_MS = 3000;

    /** 拍照回调（自定义相机，拍照后返回照片路径） */
    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    // 从自定义相机返回的照片路径
                    String photoPath = result.getData().getStringExtra(CameraCaptureActivity.EXTRA_PHOTO_PATH);
                    if (photoPath != null) {
                        currentPhotoPath = photoPath;
                        cameraImageUri = Uri.fromFile(new File(photoPath));
                    }
                    startUCropActivity();
                }
            });

    /** 裁剪回调（裁剪完成后进入 OCR） */
    private final ActivityResultLauncher<Intent> cropLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                if (result.getResultCode() == RESULT_OK && data != null) {
                    // 裁剪完成 → 获取裁剪后的图片进行 OCR
                    String outputUri = data.getStringExtra(ThemeCropActivity.EXTRA_OUTPUT_URI);
                    if (outputUri != null) {
                        uploadForOcr(Uri.parse(outputUri));
                    } else {
                        Toast.makeText(this, "裁剪结果获取失败", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    // 用户在裁剪界面点击取消/失败 → 返回相机重新拍摄
                    openCamera();
                }
            });

    private ImageButton btnBack;
    private TextView tvProgress, tvContext, tvLevel;
    private EditText etAnswer;
    private Button btnReplay, btnSubmit, btnNext, btnScan;
    private ProgressBar progressAudio;
    private LinearLayout answerInputLayout, submitLayout;
    private Uri cameraImageUri;
    private String currentPhotoPath;

    /** 听写清单中的原始单词集合（用于过滤 OCR 结果中的提示词） */
    private Set<String> filterWordSet = new HashSet<>();

    private String taskId;
    private int totalWords;
    private int currentIndex = 0;

    private DictationModels.DictationTask currentTask;
    private List<DictationModels.DictationItem> items = new ArrayList<>();

    /** 用户答案 { index → answer } */
    private List<AnswerEntry> userAnswers = new ArrayList<>();

    private MediaPlayer mediaPlayer;
    private int autoPlayCount = 0;
    private int manualReplayCount = 0;
    private boolean isPlaying = false;

    private Handler handler;
    private Handler audioDelayHandler = new Handler(Looper.getMainLooper());

    // 当前播放音频何时结束的 Runnable
    private Runnable delayedPlayRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dictation_execution_layout);

        taskId = getIntent().getStringExtra("taskId");
        totalWords = getIntent().getIntExtra("totalWords", 0);

        initViews();
        initHandler();
        loadTask();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> confirmExit());

        tvProgress = findViewById(R.id.tv_progress);
        tvContext = findViewById(R.id.tv_context);
        tvLevel = findViewById(R.id.tv_level);
        etAnswer = findViewById(R.id.et_answer);
        btnReplay = findViewById(R.id.btn_replay);
        btnSubmit = findViewById(R.id.btn_submit);
        btnNext = findViewById(R.id.btn_next);
        btnScan = findViewById(R.id.btn_scan);
        progressAudio = findViewById(R.id.progress_audio);
        answerInputLayout = findViewById(R.id.answer_input_layout);
        submitLayout = findViewById(R.id.submit_layout);

        btnReplay.setOnClickListener(v -> requestManualReplay());

        btnNext.setOnClickListener(v -> onNextWord());

        btnScan.setOnClickListener(v -> openCamera());

        btnSubmit.setOnClickListener(v -> {
            // 保存当前答案后提交
            saveCurrentAnswer();
            submitAnswers();
        });

        // 键盘回车键：确认当前单词，进入下一个
        etAnswer.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                onNextWord();
                return true;
            }
            return false;
        });

        // 初始状态
        answerInputLayout.setVisibility(View.GONE);
        submitLayout.setVisibility(View.GONE);
    }

    private void initHandler() {
        handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(android.os.Message msg) {
                if (msg.what == MSG_TASK_SUCCESS) {
                    String result = (String) msg.obj;
                    parseTaskResult(result);
                } else if (msg.what == MSG_TASK_FAILED) {
                    Toast.makeText(DictationExecutionActivity.this, "加载任务失败", Toast.LENGTH_SHORT).show();
                    finish();
                } else if (msg.what == MSG_SUBMIT_SUCCESS) {
                    String result = (String) msg.obj;
                    navigateToResult(result);
                } else if (msg.what == MSG_SUBMIT_FAILED) {
                    Toast.makeText(DictationExecutionActivity.this, "提交失败，请重试", Toast.LENGTH_SHORT).show();
                } else if (msg.what == MSG_OCR_SUCCESS) {
                    String result = (String) msg.obj;
                    parseOcrResult(result);
                } else if (msg.what == MSG_OCR_FAILED) {
                    Toast.makeText(DictationExecutionActivity.this, "OCR 识别失败，请手动输入", Toast.LENGTH_SHORT).show();
                }
            }
        };
    }

    private void loadTask() {
        DictationApiHelper.getTaskDetail(handler, MSG_TASK_SUCCESS, MSG_TASK_FAILED, taskId);
    }

    private void parseTaskResult(String result) {
        try {
            JSONObject json = new JSONObject(result);
            if (json.optString("code").equals("200")) {
                JSONObject data = json.optJSONObject("data");
                if (data != null) {
                    currentTask = DictationModels.DictationTask.fromJson(data);
                    items = currentTask.items;
                    totalWords = items.size();

                    // 初始化答案数组
                    userAnswers.clear();
                    for (DictationModels.DictationItem item : items) {
                        AnswerEntry entry = new AnswerEntry();
                        entry.index = item.index;
                        entry.wordId = item.wordId;
                        entry.answer = "";
                        userAnswers.add(entry);
                    }

                    // 构建 OCR 过滤词集合
                    buildFilterWordSet();

                    // 显示第一个词
                    showCurrentWord();
                }
            } else {
                Toast.makeText(this, json.optString("message", "加载失败"), Toast.LENGTH_SHORT).show();
                finish();
            }
        } catch (Exception e) {
            e.printStackTrace();
            finish();
        }
    }

    private void showCurrentWord() {
        if (currentIndex >= items.size()) {
            // 全部作答完毕，显示提交
            showSubmitState();
            return;
        }

        DictationModels.DictationItem item = items.get(currentIndex);

        // 更新进度
        tvProgress.setText((currentIndex + 1) + " / " + totalWords);

        // 更新语境标签
        tvLevel.setText(getLevelLabel(item.level));

        // 显示语境文本（L2/L3）或词性提示（L1）
        String contextDisplay = buildContextDisplay(item);
        tvContext.setText(contextDisplay);

        // 显示输入区域
        answerInputLayout.setVisibility(View.VISIBLE);
        submitLayout.setVisibility(View.GONE);

        // 恢复之前的答案（如果有）
        etAnswer.setText("");
        if (currentIndex < userAnswers.size()) {
            String savedAnswer = userAnswers.get(currentIndex).answer;
            if (savedAnswer != null && !savedAnswer.isEmpty()) {
                etAnswer.setText(savedAnswer);
                etAnswer.setSelection(savedAnswer.length());
            }
        }
        etAnswer.requestFocus();

        // 重置播放状态
        autoPlayCount = 0;
        manualReplayCount = 0;
        btnReplay.setEnabled(true);
        btnReplay.setAlpha(1.0f);

        // 开始自动播放
        playAudio();
    }

    private String buildContextDisplay(DictationModels.DictationItem item) {
        String pos = getPosHint(item);
        switch (item.level) {
        case 1:
            return pos.isEmpty() ? "请听写以下单词" : "请听写以下 " + pos + " 单词";
        case 2:
        case 3:
            if (item.contextText != null) {
                // 用下划线替换目标词
                String masked = item.contextText.replaceAll("(?i)" + java.util.regex.Pattern.quote(item.headWord),
                        "____");
                if (masked.equals(item.contextText) && !item.targetForm.equals(item.headWord)) {
                    masked = item.contextText.replaceAll("(?i)" + java.util.regex.Pattern.quote(item.targetForm),
                            "____");
                }
                String posStr = pos.isEmpty() ? "" : " (" + pos + ")";
                return masked + posStr;
            }
            return "";
        default:
            return "";
        }
    }

    private String getPosHint(DictationModels.DictationItem item) {
        if (item.localPos != null && !item.localPos.isEmpty())
            return item.localPos;
        if (item.posHint != null && !item.posHint.isEmpty())
            return item.posHint;
        return "";
    }

    private String getLevelLabel(int level) {
        switch (level) {
        case 1:
            return "单词听写";
        case 2:
            return "短语听写";
        case 3:
            return "句子听写";
        default:
            return "L" + level;
        }
    }

    private void playAudio() {
        DictationModels.DictationItem item = items.get(currentIndex);
        String audioUrl = item.audioUrl;

        if (audioUrl == null || audioUrl.isEmpty()) {
            // 音频未就绪，降级处理
            Toast.makeText(this, "音频尚未生成，请稍候", Toast.LENGTH_SHORT).show();
            progressAudio.setVisibility(View.VISIBLE);
            return;
        }

        progressAudio.setVisibility(View.VISIBLE);
        isPlaying = true;

        try {
            releaseMediaPlayer();
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(audioUrl);
            mediaPlayer.prepareAsync();

            mediaPlayer.setOnPreparedListener(mp -> {
                progressAudio.setVisibility(View.GONE);
                mp.start();
            });

            mediaPlayer.setOnCompletionListener(mp -> {
                isPlaying = false;
                autoPlayCount++;

                if (autoPlayCount < MAX_AUTO_PLAY) {
                    // 延迟 3 秒后自动再播一遍
                    delayedPlayRunnable = () -> playAudio();
                    audioDelayHandler.postDelayed(delayedPlayRunnable, REPLAY_INTERVAL_MS);
                }
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                isPlaying = false;
                progressAudio.setVisibility(View.GONE);
                Toast.makeText(this, "音频播放失败", Toast.LENGTH_SHORT).show();
                return true;
            });

        } catch (IOException e) {
            e.printStackTrace();
            progressAudio.setVisibility(View.GONE);
        }
    }

    private void requestManualReplay() {
        if (manualReplayCount >= MAX_MANUAL_REPLAY) {
            Toast.makeText(this, "已达到最大重听次数", Toast.LENGTH_SHORT).show();
            btnReplay.setEnabled(false);
            btnReplay.setAlpha(0.5f);
            return;
        }
        manualReplayCount++;

        // 取消自动延迟播放
        if (delayedPlayRunnable != null) {
            audioDelayHandler.removeCallbacks(delayedPlayRunnable);
            delayedPlayRunnable = null;
        }

        playAudio();
    }

    private void saveCurrentAnswer() {
        if (currentIndex < userAnswers.size()) {
            userAnswers.get(currentIndex).answer = etAnswer.getText().toString().trim();
        }
    }

    private void onNextWord() {
        saveCurrentAnswer();

        // 取消延迟播放
        if (delayedPlayRunnable != null) {
            audioDelayHandler.removeCallbacks(delayedPlayRunnable);
            delayedPlayRunnable = null;
        }

        releaseMediaPlayer();

        currentIndex++;
        if (currentIndex >= items.size()) {
            showSubmitState();
        } else {
            showCurrentWord();
        }
    }

    private void showSubmitState() {
        answerInputLayout.setVisibility(View.GONE);
        submitLayout.setVisibility(View.VISIBLE);

        // 统计已作答数量
        int answered = 0;
        for (AnswerEntry entry : userAnswers) {
            if (entry.answer != null && !entry.answer.isEmpty()) {
                answered++;
            }
        }
        tvProgress.setText("已作答 " + answered + " / " + totalWords);
        tvContext.setText("所有单词已作答完毕");
    }

    private void submitAnswers() {
        try {
            JSONArray answersArr = new JSONArray();
            for (AnswerEntry entry : userAnswers) {
                JSONObject answerObj = new JSONObject();
                answerObj.put("index", entry.index);
                answerObj.put("wordId", entry.wordId);
                answerObj.put("answer", entry.answer != null ? entry.answer : "");
                answersArr.put(answerObj);
            }

            btnSubmit.setEnabled(false);
            btnSubmit.setText("提交中...");

            DictationApiHelper.submitAnswers(handler, MSG_SUBMIT_SUCCESS, MSG_SUBMIT_FAILED, taskId, answersArr);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "提交出错", Toast.LENGTH_SHORT).show();
            btnSubmit.setEnabled(true);
            btnSubmit.setText("提交");
        }
    }

    private void navigateToResult(String result) {
        Intent intent = new Intent(this, DictationResultActivity.class);
        intent.putExtra("taskId", taskId);
        intent.putExtra("resultJson", result);
        intent.putExtra("lexiconId", currentTask != null ? currentTask.lexiconId : 0);
        startActivity(intent);
        finish();
    }

    private void confirmExit() {
        // 检查是否有已作答的单词
        boolean hasAnswers = false;
        for (AnswerEntry entry : userAnswers) {
            if (entry.answer != null && !entry.answer.isEmpty()) {
                hasAnswers = true;
                break;
            }
        }

        if (hasAnswers) {
            new MaterialAlertDialogBuilder(this).setTitle("确认退出").setMessage("已作答的答案将保留，下次可从中断处继续。确定退出吗？")
                    .setPositiveButton("退出", (dialog, which) -> {
                        releaseMediaPlayer();
                        if (delayedPlayRunnable != null) {
                            audioDelayHandler.removeCallbacks(delayedPlayRunnable);
                            delayedPlayRunnable = null;
                        }
                        finish();
                    }).setNegativeButton("继续听写", null).show();
        } else {
            finish();
        }
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mediaPlayer = null;
        }
    }

    // ── 拍照 OCR ──

    private void openCamera() {
        // 不预创建 0 字节临时文件：文件由相机拍照时在内部缓存目录创建并从结果返回，
        // 避免未拍照即退出时残留空文件被系统相册扫描（部分 ROM 会扫描 Android/data）
        Intent intent = new Intent(this, CameraCaptureActivity.class);
        cameraLauncher.launch(intent);
    }

    private void startUCropActivity() {
        if (currentPhotoPath == null)
            return;

        Uri sourceUri = Uri.fromFile(new File(currentPhotoPath));

        // 启动自建裁剪页（听写默认自由比例，最大输出 1280）
        Intent intent = new Intent(this, ThemeCropActivity.class);
        intent.putExtra(ThemeCropActivity.EXTRA_SOURCE_URI, sourceUri);
        cropLauncher.launch(intent);
    }

    private void uploadForOcr(Uri imageUri) {
        if (imageUri == null)
            return;
        Toast.makeText(this, "正在识别...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                // 直接上传裁剪页输出（与作文端一致：JPEG 质量 85 / 最长边 1280），避免二次解码重编码
                String url = ApiConstants.getBaseUrl() + "/composition/extractText";
                long fileSize = 0;
                InputStream sizeStream = getContentResolver().openInputStream(imageUri);
                if (sizeStream != null) {
                    try {
                        long avail = sizeStream.available();
                        fileSize = avail > 0 ? avail : 0;
                    } finally {
                        sizeStream.close();
                    }
                }
                Log.i("DictationOCR", "上传 OCR 图片 → " + url + "  文件大小: " + fileSize + " bytes");
                String result = HttpManager.doHttpPostWithImageUri(url, imageUri,
                        DictationExecutionActivity.this);
                Log.i("DictationOCR", "OCR 返回: " + (result != null ? result : "null（请求失败）"));

                if (result != null) {
                    android.os.Message msg = handler.obtainMessage(MSG_OCR_SUCCESS, result);
                    handler.sendMessage(msg);
                } else {
                    handler.sendEmptyMessage(MSG_OCR_FAILED);
                }
            } catch (Exception e) {
                e.printStackTrace();
                handler.sendEmptyMessage(MSG_OCR_FAILED);
            }
        }).start();
    }

    /**
     * 构建过滤词集合：收集听写清单中打印在提示区的单词（headWord） 这些词是听写单上的提示/语境词，OCR 识别到时应过滤掉。
     * 注意：targetForm 是用户应写出的正确答案，绝不能加入黑名单！
     */
    private void buildFilterWordSet() {
        filterWordSet.clear();
        for (DictationModels.DictationItem item : items) {
            if (item.headWord != null) {
                filterWordSet.add(item.headWord.toLowerCase());
            }
        }
    }

    private void parseOcrResult(String result) {
        try {
            JSONObject json = new JSONObject(result);

            // 兼容两种响应格式：
            // 格式1（标准）: {"code":"200","data":{"text":"..."}}
            // 格式2（扁平）: {"text":"..."}
            String text = null;

            String code = json.optString("code", "");
            if ("200".equals(code)) {
                // 标准格式
                JSONObject data = json.optJSONObject("data");
                text = data != null ? data.optString("text", "") : "";
            } else if (json.has("text")) {
                // 扁平格式
                text = json.optString("text", "");
                Log.i("DictationOCR", "使用扁平格式解析，text=" + text);
            } else {
                Log.e("DictationOCR", "无法识别的响应格式: " + result);
                Toast.makeText(this, "OCR 响应格式异常", Toast.LENGTH_SHORT).show();
                return;
            }

            if (text == null || text.isEmpty()) {
                Log.w("DictationOCR", "服务端返回空文本，图片可能不含可识别文字");
                Toast.makeText(this, "未能识别到文字，请确保拍摄内容清晰", Toast.LENGTH_SHORT).show();
                return;
            }

            // 确保过滤词集合已构建
            if (filterWordSet.isEmpty()) {
                buildFilterWordSet();
            }

            // ── 策略：从 OCR 文本中提取所有候选单词，逐层过滤 ──
            List<String> candidates = new ArrayList<>();

            // 第一层：尝试 "序号. word" 模式
            Pattern p = Pattern.compile("(\\d+)\\s*\\.?\\s*([a-zA-Z]+)");
            Matcher m = p.matcher(text);
            while (m.find()) {
                String word = m.group(2).trim().toLowerCase();
                candidates.add(word);
            }

            // 第二层：如果模式匹配不足，兜底按行提取所有单词
            if (candidates.isEmpty()) {
                String[] lines = text.split("[\\n\\r]+");
                for (String line : lines) {
                    String[] words = line.replaceAll("[^a-zA-Z]", " ").trim().split("\\s+");
                    for (String w : words) {
                        if (w.length() >= 2) {
                            candidates.add(w.toLowerCase());
                        }
                    }
                }
            }

            // ── 过滤：排除听写清单中的原始单词（提示词） ──
            List<String> filtered = new ArrayList<>();
            for (String word : candidates) {
                if (!filterWordSet.contains(word)) {
                    filtered.add(word);
                }
            }

            if (filtered.isEmpty()) {
                Toast.makeText(this, "未能识别到有效答案（已自动过滤提示词），请手动输入", Toast.LENGTH_LONG).show();
                return;
            }

            // ── 预览：取第一个有效单词填入当前输入框 ──
            String bestAnswer = filtered.get(0);
            etAnswer.setText(bestAnswer);
            etAnswer.setSelection(bestAnswer.length());

            // 同时保存到当前答案
            if (currentIndex < userAnswers.size()) {
                userAnswers.get(currentIndex).answer = bestAnswer;
            }

            String tip = filtered.size() > 1 ? "已识别 " + filtered.size() + " 个候选词，已填入最佳匹配，请核对" : "已识别并填入，请核对";
            Toast.makeText(this, tip, Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "OCR 解析失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseMediaPlayer();
        if (delayedPlayRunnable != null) {
            audioDelayHandler.removeCallbacks(delayedPlayRunnable);
        }
    }

    // --- 答案存储 ---
    static class AnswerEntry {
        int index;
        long wordId;
        String answer;
    }
}
