package com.deepsleep.memory.ui.treasure_view.dictation_view;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.deepsleep.memory.R;
import com.deepsleep.memory.settings.InnerSettingsManager;

import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 听写练习 - 任务生成与预览页
 * 包含：生成任务 → 展示预览清单（冷却倒计时）→ 开始听写
 */
public class DictationGenerateActivity extends AppCompatActivity {

    private static final int MSG_GENERATE_SUCCESS = 1;
    private static final int MSG_GENERATE_FAILED = 2;
    private static final int MSG_POLL_SUCCESS = 3;
    private static final int MSG_POLL_FAILED = 4;

    /** 缓存的任务 JSON（用于从结果页跳转重练时预填充） */
    private static String cachedTaskJson = null;

    public static void setCachedTaskJson(String json) {
        cachedTaskJson = json;
    }

    private ImageButton btnBack;
    private TextView tvTitle, tvCooldown, tvStatus, tvWordCount;
    private Button btnStart, btnRetry, btnPrint;
    private ProgressBar progressBar;
    private RecyclerView previewRecyclerView;
    private PreviewAdapter previewAdapter;
    private LinearLayout layoutPreview, layoutLoading;

    private int userId;
    private int wordCount;
    private int lexiconId;
    private String taskId;
    private String userNickname;
    private DictationModels.DictationTask currentTask;

    private Handler handler;
    private Runnable countdownRunnable;
    private Runnable pollRunnable;
    private long cooldownEndTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dictation_generate_layout);

        userId = InnerSettingsManager.getInstance(this).getUserId();
        userNickname = InnerSettingsManager.getInstance(this).getNickName();
        wordCount = getIntent().getIntExtra("count", 15);
        lexiconId = getIntent().getIntExtra("lexiconId", 2);
        taskId = getIntent().getStringExtra("taskId");

        initViews();
        initHandler();

        // 优先级：缓存的生成结果 > 已有的 taskId > 新建任务
        if (cachedTaskJson != null) {
            String json = cachedTaskJson;
            cachedTaskJson = null;
            parseGenerateResult(json);
        } else if (taskId != null && !taskId.isEmpty()) {
            loadExistingTask();
        } else {
            generateTask();
        }
    }

    private void initViews() {
        btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        tvTitle = findViewById(R.id.tv_title);
        tvCooldown = findViewById(R.id.tv_cooldown);
        tvStatus = findViewById(R.id.tv_status);
        tvWordCount = findViewById(R.id.tv_word_count);
        btnStart = findViewById(R.id.btn_start);
        btnRetry = findViewById(R.id.btn_retry);
        btnPrint = findViewById(R.id.btn_print);
        progressBar = findViewById(R.id.progress_bar);
        previewRecyclerView = findViewById(R.id.preview_recycler);
        layoutPreview = findViewById(R.id.layout_preview);
        layoutLoading = findViewById(R.id.layout_loading);

        previewAdapter = new PreviewAdapter();
        previewRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        previewRecyclerView.setAdapter(previewAdapter);

        btnStart.setOnClickListener(v -> startDictation());
        btnRetry.setOnClickListener(v -> generateTask());
        btnPrint.setOnClickListener(v -> printToPdf());

        // 初始状态：隐藏预览，显示加载
        layoutPreview.setVisibility(View.GONE);
        layoutLoading.setVisibility(View.VISIBLE);
        btnStart.setEnabled(false);
        btnRetry.setVisibility(View.GONE);
    }

    private void initHandler() {
        handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(android.os.Message msg) {
                if (msg.what == MSG_GENERATE_SUCCESS) {
                    String result = (String) msg.obj;
                    parseGenerateResult(result);
                } else if (msg.what == MSG_GENERATE_FAILED) {
                    onGenerateFailed();
                } else if (msg.what == MSG_POLL_SUCCESS) {
                    String result = (String) msg.obj;
                    parsePollResult(result);
                } else if (msg.what == MSG_POLL_FAILED) {
                    // 轮询失败，继续轮询
                    schedulePoll();
                }
            }
        };
    }

    private void generateTask() {
        layoutLoading.setVisibility(View.VISIBLE);
        layoutPreview.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        layoutLoading.findViewById(R.id.tv_loading_text).setVisibility(View.VISIBLE);
        ((TextView) layoutLoading.findViewById(R.id.tv_loading_text)).setText("正在生成听写任务...");
        btnStart.setEnabled(false);
        btnRetry.setVisibility(View.GONE);

        DictationApiHelper.generateTask(handler, MSG_GENERATE_SUCCESS, MSG_GENERATE_FAILED,
                userId, wordCount, lexiconId);
    }

    /** 从历史记录进入：加载已有任务 */
    private void loadExistingTask() {
        layoutLoading.setVisibility(View.VISIBLE);
        layoutPreview.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        ((TextView) layoutLoading.findViewById(R.id.tv_loading_text)).setText("正在加载听写任务...");
        btnStart.setEnabled(false);
        btnRetry.setVisibility(View.GONE);

        DictationApiHelper.getTaskDetail(handler, MSG_GENERATE_SUCCESS, MSG_GENERATE_FAILED, taskId);
    }

    private void parseGenerateResult(String result) {
        try {
            JSONObject json = new JSONObject(result);
            String code = json.optString("code");

            if (code.equals("200")) {
                JSONObject data = json.optJSONObject("data");
                if (data != null) {
                    currentTask = DictationModels.DictationTask.fromJson(data);
                    taskId = currentTask.taskId;
                    enrichLocalInfo();
                    showPreview();
                    parseCooldownTime(currentTask.cooldownUntil);
                    schedulePoll();
                }
            } else if (code.equals("409")) {
                // 已有进行中的任务：加载它
                String conflictTaskId = json.optJSONObject("data") != null
                        ? json.optJSONObject("data").optString("taskId", "")
                        : "";
                if (!conflictTaskId.isEmpty()) {
                    taskId = conflictTaskId;
                    loadExistingTask();
                } else {
                    String message = json.optString("message", "已有进行中的听写任务");
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    onGenerateFailed();
                }
            } else {
                String message = json.optString("message", "任务生成失败");
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                onGenerateFailed();
            }
        } catch (Exception e) {
            e.printStackTrace();
            onGenerateFailed();
        }
    }

    /**
     * 从本地词书补全词性和释义
     * 当前使用模拟数据，实际项目中应从本地 JSON/DB 查询
     */
    private void enrichLocalInfo() {
        if (currentTask == null || currentTask.items == null) return;
        // TODO: 接入实际的本地词书数据
        // 目前：使用服务端返回的 posHint（可能为null），或留空让UI自行处理
        for (DictationModels.DictationItem item : currentTask.items) {
            if (item.posHint != null && !item.posHint.isEmpty()) {
                item.localPos = item.posHint;
            }
        }
    }

    private void showPreview() {
        layoutLoading.setVisibility(View.GONE);
        layoutPreview.setVisibility(View.VISIBLE);

        tvWordCount.setText("共 " + currentTask.totalWords + " 词");
        previewAdapter.setData(currentTask.items);

        // 检查是否已经过了冷却期
        checkCooldownStatus();
    }

    private void parseCooldownTime(String cooldownUntil) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            Date date = sdf.parse(cooldownUntil);
            if (date != null) {
                cooldownEndTime = date.getTime();
            }
        } catch (ParseException e) {
            // 尝试另一种格式
            try {
                SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                Date date = sdf2.parse(cooldownUntil);
                if (date != null) {
                    cooldownEndTime = date.getTime();
                }
            } catch (ParseException e2) {
                e2.printStackTrace();
                cooldownEndTime = 0;
            }
        }
    }

    private void checkCooldownStatus() {
        long now = System.currentTimeMillis();

        if (cooldownEndTime <= 0 || now >= cooldownEndTime) {
            // 冷却已结束，检查音频是否全部就绪
            tvCooldown.setText("准备就绪");
            tvCooldown.setTextColor(0xFF4CAF50);
            checkAudioReady();
        } else {
            // 正在冷却中
            startCooldownCountdown();
        }
    }

    private void startCooldownCountdown() {
        stopCountdown();
        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                long remaining = cooldownEndTime - now;

                if (remaining <= 0) {
                    tvCooldown.setText("准备就绪");
                    tvCooldown.setTextColor(0xFF4CAF50);
                    checkAudioReady();
                    return;
                }

                long minutes = remaining / 60000;
                long seconds = (remaining % 60000) / 1000;
                String text = String.format(Locale.getDefault(), "冷却中 %02d:%02d", minutes, seconds);
                tvCooldown.setText(text);
                tvCooldown.setTextColor(0xFFFF9800);

                handler.postDelayed(this, 1000);
            }
        };
        handler.post(countdownRunnable);
    }

    private void stopCountdown() {
        if (countdownRunnable != null) {
            handler.removeCallbacks(countdownRunnable);
            countdownRunnable = null;
        }
    }

    /**
     * 检查所有音频是否已就绪
     */
    private boolean allAudioReady() {
        if (currentTask == null || currentTask.items == null) return false;
        for (DictationModels.DictationItem item : currentTask.items) {
            if (!item.audioReady) return false;
        }
        return true;
    }

    private void checkAudioReady() {
        if (allAudioReady()) {
            tvStatus.setText("全部音频已就绪");
            enableStartButton();
        } else {
            tvStatus.setText("正在生成音频...");
            // 音频尚未全部就绪，等待轮询
        }
    }

    private void enableStartButton() {
        btnStart.setEnabled(true);
        btnStart.setAlpha(1.0f);
        btnStart.setText("开始听写");
        progressBar.setVisibility(View.GONE);
    }

    /**
     * 轮询获取任务详情（音频就绪状态）
     */
    private void schedulePoll() {
        stopPoll();
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (taskId == null) return;

                // 如果已经可以开始，停止轮询
                if (btnStart.isEnabled()) return;

                DictationApiHelper.getTaskDetail(handler, MSG_POLL_SUCCESS, MSG_POLL_FAILED, taskId);
            }
        };
        // 每 5 秒轮询一次
        handler.postDelayed(pollRunnable, 5000);
    }

    private void parsePollResult(String result) {
        try {
            JSONObject json = new JSONObject(result);
            if (json.optString("code").equals("200")) {
                JSONObject data = json.optJSONObject("data");
                if (data != null) {
                    DictationModels.DictationTask updated = DictationModels.DictationTask.fromJson(data);
                    // 更新音频就绪状态
                    if (currentTask != null && updated.items != null) {
                        for (DictationModels.DictationItem updatedItem : updated.items) {
                            for (DictationModels.DictationItem currentItem : currentTask.items) {
                                if (currentItem.wordId == updatedItem.wordId) {
                                    currentItem.audioReady = updatedItem.audioReady;
                                    currentItem.audioUrl = updatedItem.audioUrl;
                                    break;
                                }
                            }
                        }
                        currentTask.status = updated.status;
                        previewAdapter.notifyDataSetChanged();
                    }

                    // 重新检查状态
                    long now = System.currentTimeMillis();
                    if (now >= cooldownEndTime && cooldownEndTime > 0) {
                        checkAudioReady();
                    } else {
                        checkCooldownStatus();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 继续轮询
        schedulePoll();
    }

    private void stopPoll() {
        if (pollRunnable != null) {
            handler.removeCallbacks(pollRunnable);
            pollRunnable = null;
        }
    }

    private void onGenerateFailed() {
        layoutLoading.setVisibility(View.VISIBLE);
        layoutPreview.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        ((TextView) layoutLoading.findViewById(R.id.tv_loading_text)).setText("任务生成失败，请重试");
        btnRetry.setVisibility(View.VISIBLE);
    }

    private void startDictation() {
        if (taskId == null) return;
        Intent intent = new Intent(this, DictationExecutionActivity.class);
        intent.putExtra("taskId", taskId);
        intent.putExtra("totalWords", currentTask != null ? currentTask.totalWords : 0);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCountdown();
        stopPoll();
    }

    private void printToPdf() {
        if (currentTask == null || currentTask.items == null || currentTask.items.isEmpty()) {
            Toast.makeText(this, "暂无听写清单", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            PdfDocument document = new PdfDocument();
            int pw = 595, ph = 842;          // A4
            int ml = 40, mr = 40;            // margins
            int cw = pw - ml - mr;           // content width
            // 书写方格区：右侧固定 160pt 宽
            int boxLeft = pw - mr - 160;
            int boxW = 160;

            // ── Paints ──
            Paint titleP = paint(20, Typeface.DEFAULT_BOLD, 0xFF1a1a2e);
            Paint headP  = paint(9, Typeface.DEFAULT, 0xFF888888);
            Paint textP  = paint(14, Typeface.DEFAULT, 0xFF333333);
            Paint lineP  = paint(0.6f, 0xFFDDDDDD);
            Paint boxP   = paint(1.2f, 0xFFBBBBBB);
            boxP.setStyle(Paint.Style.STROKE);
            Paint fillP  = paint(0, 0xFFF7F9FC);
            fillP.setStyle(Paint.Style.FILL);

            // ── Page 1 ──
            PdfDocument.PageInfo pi = new PdfDocument.PageInfo.Builder(pw, ph, 1).create();
            PdfDocument.Page page = document.startPage(pi);
            Canvas c = page.getCanvas();
            int y = 45;

            // ═══ TOP SECTION ═══
            String nickname = (userNickname != null && !userNickname.isEmpty())
                    ? userNickname : "同学";
            String title = nickname + " 的听写单";
            c.drawText(title, ml, y, titleP);
            y += 20;
            String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            c.drawText(dateStr, ml, y, headP);
            c.drawText("Task " + (taskId != null ? taskId : ""), ml + 120, y, headP);
            y += 14;
            // 操作指引
            c.drawText("听音频  →  在方格中书写  →  拍照提交", ml, y, headP);
            y += 22;
            c.drawLine(ml, y, pw - mr, y, lineP);
            y += 12;

            // ═══ ITEMS ═══
            int seq = 1;
            for (DictationModels.DictationItem item : currentTask.items) {
                if (y > ph - 110) { page = newPage(document, page, pw, ph, ml, mr, titleP, lineP); c = page.getCanvas(); y = 48; }

                int rowH = 48;
                int rowTop = y;

                // Row background
                c.drawRect(ml, rowTop, pw - mr, rowTop + rowH, fillP);

                // Prompt text
                String prompt = buildPrintPrompt(item, seq);
                int promptMaxW = boxLeft - ml - 14;
                android.text.TextPaint tp = new android.text.TextPaint(textP);
                android.text.StaticLayout sl = android.text.StaticLayout.Builder
                        .obtain(prompt, 0, prompt.length(), tp, promptMaxW)
                        .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                        .build();

                // Adjust row height for multi-line prompts
                int needH = Math.max(sl.getHeight() + 14, rowH);
                if (y + needH > ph - 110) { page = newPage(document, page, pw, ph, ml, mr, titleP, lineP); c = page.getCanvas(); y = 48; rowTop = y; }

                c.drawRect(ml, rowTop, pw - mr, rowTop + needH, fillP);
                c.save();
                c.translate(ml + 4, rowTop + 7);
                sl.draw(c);
                c.restore();

                // Writing box
                c.drawRect(boxLeft, rowTop + 4, boxLeft + boxW, rowTop + needH - 4, boxP);
                // Guide line inside box
                int midY = rowTop + needH / 2;
                c.drawLine(boxLeft + 4, midY, boxLeft + boxW - 4, midY, lineP);

                y = rowTop + needH + 2;
                seq++;
            }

            // ═══ BOTTOM ═══
            y += 8;
            if (y > ph - 100) { page = newPage(document, page, pw, ph, ml, mr, titleP, lineP); c = page.getCanvas(); y = 48; }

            // Self-evaluation area
            Paint evalP = paint(12, Typeface.DEFAULT, 0xFF666666);
            c.drawText("自我评价：不确定的单词请打  ✓", ml, y, evalP);
            y += 18;
            // Checkbox squares
            for (int i = 0; i < 5; i++) {
                int cx = ml + i * 48;
                c.drawRect(cx, y, cx + 14, y + 14, boxP);
            }
            y += 24;

            // Footer
            c.drawLine(ml, y, pw - mr, y, lineP);
            y += 12;
            c.drawText("Powered by MemoryApp  ·  个人专属听写单", ml, y, headP);

            document.finishPage(page);

            // ── Save & Open ──
            File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Dictation");
            if (!dir.exists() && !dir.mkdirs()) {
                dir = new File(getCacheDir(), "Dictation");
                if (!dir.exists()) dir.mkdirs();
            }
            String fn = "Dictation_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".pdf";
            File file = new File(dir, fn);
            FileOutputStream fos = new FileOutputStream(file);
            document.writeTo(fos);
            fos.close();
            document.close();

            Uri uri = FileProvider.getUriForFile(this, "com.deepsleep.memory.fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/pdf");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
            Toast.makeText(this, "PDF 已生成", Toast.LENGTH_SHORT).show();

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "PDF 生成失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private PdfDocument.Page newPage(PdfDocument doc, PdfDocument.Page currentPage,
            int pw, int ph, int ml, int mr, Paint titleP, Paint lineP) {
        doc.finishPage(currentPage);
        int pages = doc.getPages().size();
        PdfDocument.PageInfo pi = new PdfDocument.PageInfo.Builder(pw, ph, pages + 1).create();
        PdfDocument.Page page = doc.startPage(pi);
        Canvas c = page.getCanvas();
        c.drawText("听写练习（续）", ml, 38, titleP);
        c.drawLine(ml, 52, pw - mr, 52, lineP);
        return page;
    }

    private Paint paint(float size, int color) {
        Paint p = new Paint();
        if (size > 0) { p.setTextSize(size); p.setAntiAlias(true); }
        if (color != 0) p.setColor(color);
        return p;
    }

    private Paint paint(float size, Typeface tf, int color) {
        Paint p = paint(size, color);
        if (tf != null) p.setTypeface(tf);
        return p;
    }

    private String buildPrintPrompt(DictationModels.DictationItem item, int seq) {
        String pos = (item.localPos != null && !item.localPos.isEmpty()) ? item.localPos : "";
        String posStr = pos.isEmpty() ? "" : "  " + pos;

        switch (item.level) {
            case 1:
                return seq + ". " + posStr;
            case 2:
            case 3:
                if (item.contextText != null && item.headWord != null) {
                    String masked = item.contextText.replaceAll(
                            "(?i)" + java.util.regex.Pattern.quote(item.headWord), "___________");
                    if (masked.equals(item.contextText) && !item.targetForm.equals(item.headWord)) {
                        masked = item.contextText.replaceAll(
                                "(?i)" + java.util.regex.Pattern.quote(item.targetForm), "___________");
                    }
                    return seq + ". " + masked + posStr;
                }
                return seq + ". " + (item.contextText != null ? item.contextText : "") + posStr;
            default:
                return seq + ". " + posStr;
        }
    }

    // --- 预览清单适配器 ---
    private class PreviewAdapter extends RecyclerView.Adapter<PreviewAdapter.ViewHolder> {

        private List<DictationModels.DictationItem> data = new ArrayList<>();

        void setData(List<DictationModels.DictationItem> data) {
            this.data = data;
            notifyDataSetChanged();
        }

        @Override
        public ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.item_dictation_preview, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            DictationModels.DictationItem item = data.get(position);
            holder.tvIndex.setText(String.valueOf(item.index));
            holder.tvLevel.setText(getLevelLabel(item.level));
            holder.tvAudioStatus.setText(item.audioReady ? "✓" : "⏳");
            holder.tvAudioStatus.setTextColor(item.audioReady ? 0xFF4CAF50 : 0xFFFF9800);

            String displayText = buildDisplayText(item);
            holder.tvContent.setText(displayText);
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        private String getLevelLabel(int level) {
            switch (level) {
                case 1: return "单词";
                case 2: return "短语";
                case 3: return "句子";
                default: return "L" + level;
            }
        }

        /**
         * 构建预览文本（隐藏目标单词拼写）
         */
        private String buildDisplayText(DictationModels.DictationItem item) {
            String pos = (item.localPos != null && !item.localPos.isEmpty())
                    ? item.localPos : "";
            String posStr = pos.isEmpty() ? "" : " (" + pos + ")";

            switch (item.level) {
                case 1:
                    // 单词模式：只显示词性
                    return posStr;
                case 2:
                case 3:
                    // 短语/句子模式：将目标词替换为 _________
                    if (item.contextText != null && item.headWord != null) {
                        String masked = item.contextText.replaceAll(
                                "(?i)" + java.util.regex.Pattern.quote(item.headWord), "___________");
                        // 如果目标词可能有变形，再尝试替换 targetForm
                        if (masked.equals(item.contextText) && !item.targetForm.equals(item.headWord)) {
                            masked = item.contextText.replaceAll(
                                    "(?i)" + java.util.regex.Pattern.quote(item.targetForm), "___________");
                        }
                        return masked + posStr;
                    }
                    return (item.contextText != null ? item.contextText : "") + posStr;
                default:
                    return posStr;
            }
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvIndex, tvLevel, tvContent, tvAudioStatus;

            ViewHolder(View v) {
                super(v);
                tvIndex = v.findViewById(R.id.tv_preview_index);
                tvLevel = v.findViewById(R.id.tv_preview_level);
                tvContent = v.findViewById(R.id.tv_preview_content);
                tvAudioStatus = v.findViewById(R.id.tv_preview_audio);
            }
        }
    }
}
