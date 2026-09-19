package com.deepsleep.memory.ui.treasure_view.aichat_view;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.deepsleep.memory.R;
import com.deepsleep.memory.handle_utils.AudioPlaybackManager;
import com.deepsleep.memory.handle_utils.MemAudioRecord;
import com.deepsleep.memory.network.ApiBridge;
import com.deepsleep.memory.network.ApiConstants;
import com.deepsleep.memory.network.MemoryApiClient;
import com.deepsleep.memory.settings.InnerSettingsManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AiConversationActivity extends AppCompatActivity {
    private static final String TAG = "AiConversation";
    private static final int MSG_SUCCESS = 1;
    private static final int MSG_FAIL = 2;
    private static final int MSG_SESSION_SUCCESS = 3;
    private static final int MSG_HISTORY_SUCCESS = 4;
    private static final int MSG_LAST_SUCCESS = 5;
    private static final int MSG_TTS_SUCCESS = 6;
    private static final int MSG_AUDIO_POLL_READY = 7;
    private static final int REQUEST_RECORD_AUDIO = 123;

    private static final String WELCOME_TEXT = "Hello! Welcome to Memory English Learning App.\n\n"
            + "I'm your English speaking partner. Let's chat in English only — "
            + "no matter what you say, I'll always reply in English to help you practice!\n\n"
            + "Feel free to talk about anything: your day, hobbies, studies, or any topic you like. "
            + "I'll keep the conversation going and gently correct any mistakes. Ready? Let's start!";

    private View coordinatorLayout;
    private RecyclerView rvConversation;
    private View progressBar;
    private LinearLayout layoutInput;
    private View layoutMessageInput;
    private View layoutEmptyState;
    private ImageButton btnInputMode;
    private ImageButton btnScenario;
    private FloatingActionButton btnSend;
    private TextInputEditText etMessage;
    private LinearLayout layoutVoiceRecord;
    private ImageView ivVoiceWave;
    private TextView tvVoiceHint;
    private DrawerLayout drawerLayout;

    // 模式标签
    private LinearLayout layoutModeLabel;
    private TextView tvModeLabel;

    // 对话状态
    private String mCurrentMode = "FREE_CHAT";
    private int mConversationTurnCount = 0;

    private AiConversationAdapter adapter;
    private List<AiMessage> messageList = new ArrayList<>();
    private boolean isVoiceMode = false;

    private String mSessionId = null;
    private int mUserId = 0;
    private MemAudioRecord mAudioRecord;
    private boolean mIsRecording = false;

    // 侧边栏会话列表
    private RecyclerView rvSessionList;
    private View tvSessionEmpty;
    private final List<SessionInfo> sessionList = new ArrayList<>();
    private SessionAdapter sessionAdapter;

    private static class SessionInfo {
        String sessionId, title, updatedTime;

        SessionInfo(String id, String t, String ut) {
            sessionId = id;
            title = t;
            updatedTime = ut;
        }
    }

    private class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // 改用自定义 item：系统 simple_list_item_2 的文字色不跟随明暗主题，暗色下不可读
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_session, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            SessionInfo s = sessionList.get(pos);
            String title = s.title != null && !s.title.isEmpty() ? s.title : "会话 " + (pos + 1);
            h.tvTitle.setText(title);
            h.tvTime.setText(formatSessionTime(s.updatedTime));
            h.itemView.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.END);
                switchToSession(s.sessionId);
            });
            h.itemView.setOnLongClickListener(v -> {
                new MaterialAlertDialogBuilder(AiConversationActivity.this).setTitle("删除会话")
                        .setMessage("确定要删除「" + title + "」吗？此操作不可撤销。")
                        .setPositiveButton("删除", (d, w) -> deleteSession(s.sessionId, pos))
                        .setNegativeButton("取消", null).show();
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return sessionList.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvTitle, tvTime;

            VH(View v) {
                super(v);
                tvTitle = v.findViewById(R.id.tvSessionTitle);
                tvTime = v.findViewById(R.id.tvSessionTime);
            }
        }
    }

    /**
     * 把后端返回的 ISO 时间（如 2026-08-19T03:11:04）转成紧凑展示：
     * 今天 → HH:mm，今年 → MM-dd HH:mm，更早 → yyyy-MM-dd。
     * 解析失败时原样返回，避免后端改格式后列表整片变空。
     */
    private String formatSessionTime(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        try {
            String normalized = raw.replace('T', ' ').trim();
            int dot = normalized.indexOf('.');
            if (dot > 0) {
                normalized = normalized.substring(0, dot);
            }
            if (normalized.length() > 19) {
                normalized = normalized.substring(0, 19);
            }
            Date date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(normalized);
            if (date == null) {
                return raw;
            }
            Calendar now = Calendar.getInstance();
            Calendar then = Calendar.getInstance();
            then.setTime(date);
            String pattern;
            if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
                    && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)) {
                pattern = "HH:mm";
            } else if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR)) {
                pattern = "MM-dd HH:mm";
            } else {
                pattern = "yyyy-MM-dd";
            }
            return new SimpleDateFormat(pattern, Locale.getDefault()).format(date);
        } catch (ParseException e) {
            return raw;
        }
    }

    /** 自定义 item 之后，空列表不再有系统占位提示，需要自行兜底 */
    private void updateSessionEmptyState() {
        if (tvSessionEmpty == null) {
            return;
        }
        tvSessionEmpty.setVisibility(sessionList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    public static void start(Context context) {
        Intent intent = new Intent(context, AiConversationActivity.class);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.aichat_main_layout);
        initViews();
        setupRecyclerView();
        setupInputArea();
        loadUserId();
        checkLastSession();
        // 兜底清理历史遗留的音频缓存：该目录原先无任何清理逻辑，
        // 文件名带时间戳，长期使用会无限堆积（后台线程做磁盘 IO）
        ApiConstants.execute(() ->
                com.deepsleep.memory.handle_utils.AudioCacheCleaner.cleanup(this));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mAudioRecord != null) {
            mAudioRecord.cleanup();
        }
        // 停掉流式朗读，释放 AudioTrack（否则 Activity 销毁后仍可能出声）
        stopStreamingTts(streamingMsg);
        if (adapter != null) {
            adapter.releaseMediaPlayer();
        }
    }

    private void initViews() {
        coordinatorLayout = findViewById(R.id.coordinatorLayout);
        drawerLayout = findViewById(R.id.drawerLayout);
        rvConversation = findViewById(R.id.rvConversation);
        progressBar = findViewById(R.id.progressBar);
        layoutInput = findViewById(R.id.layoutInput);
        btnInputMode = findViewById(R.id.btnInputMode);
        btnScenario = findViewById(R.id.btnScenario);
        btnSend = findViewById(R.id.btnSend);
        etMessage = findViewById(R.id.etMessage);
        layoutVoiceRecord = findViewById(R.id.layoutVoiceRecord);
        tvVoiceHint = findViewById(R.id.tvVoiceHint);
        ivVoiceWave = findViewById(R.id.ivVoiceWave);
        layoutMessageInput = findViewById(R.id.layoutMessageInput);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        layoutModeLabel = findViewById(R.id.layoutModeLabel);
        tvModeLabel = findViewById(R.id.tvModeLabel);
        rvSessionList = findViewById(R.id.rvSessionList);
        tvSessionEmpty = findViewById(R.id.tvSessionEmpty);
        mAudioRecord = new MemAudioRecord();

        // 返回按钮
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // 会话列表按钮
        findViewById(R.id.btn_sessions).setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.END));

        sessionAdapter = new SessionAdapter();
        rvSessionList.setLayoutManager(new LinearLayoutManager(this));
        rvSessionList.setAdapter(sessionAdapter);

        findViewById(R.id.btnNewSession).setOnClickListener(v -> {
            drawerLayout.closeDrawer(GravityCompat.END);
            startNewSession();
        });

        // 场景按钮
        btnScenario.setOnClickListener(v -> showScenarioPicker());

        // 空状态：「选择场景」入口
        findViewById(R.id.btnEmptyScenario).setOnClickListener(v -> showScenarioPicker());

        // 退出模式按钮
        View tvExitMode = findViewById(R.id.tvExitMode);
        if (tvExitMode != null) {
            tvExitMode.setOnClickListener(v -> exitCurrentMode());
        }

        // 侧边栏打开时刷新会话列表
        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(View drawerView) {
                loadSessions();
            }
        });
    }

    // ==================== 入口流程：先查最近会话 ====================

    private void checkLastSession() {
        progressBar.setVisibility(View.VISIBLE);
        ApiBridge.enqueue(MemoryApiClient.conversation().last(String.valueOf(mUserId)), mainHandler, MSG_LAST_SUCCESS,
                MSG_FAIL, "ConvLast");
    }

    private void startNewSession() {
        messageList.clear();
        adapter.notifyDataSetChanged();
        mSessionId = null;
        progressBar.setVisibility(View.VISIBLE);
        ApiBridge.enqueue(MemoryApiClient.conversation().start(String.valueOf(mUserId)), mainHandler,
                MSG_SESSION_SUCCESS, MSG_FAIL, "ConversationStart");
    }

    private void loadSessions() {
        ApiBridge.enqueue(MemoryApiClient.conversation().sessions(String.valueOf(mUserId)),
                new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                if (msg.what == 1) {
                    try {
                        JSONObject root = new JSONObject((String) msg.obj);
                        if ("200".equals(String.valueOf(root.optInt("code", -1)))) {
                            JSONArray arr = root.getJSONArray("data");
                            sessionList.clear();
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject s = arr.getJSONObject(i);
                                sessionList.add(new SessionInfo(s.getString("sessionId"), s.optString("title", ""),
                                        s.optString("updatedTime", "")));
                            }
                            sessionAdapter.notifyDataSetChanged();
                            updateSessionEmptyState();
                        }
                    } catch (JSONException ignored) {
                    }
                }
            }
        }, 1, -1, "ConvSessions");
    }

    private void switchToSession(String sessionId) {
        if (sessionId.equals(mSessionId))
            return;
        mSessionId = sessionId;
        messageList.clear();
        adapter.notifyDataSetChanged();
        progressBar.setVisibility(View.VISIBLE);
        ApiBridge.enqueue(MemoryApiClient.conversation().history(String.valueOf(mUserId), mSessionId), mainHandler,
                MSG_HISTORY_SUCCESS, MSG_FAIL, "ConversationHistory");
    }

    private void deleteSession(String sessionId, int position) {
        ApiBridge.enqueue(MemoryApiClient.conversation().delete(String.valueOf(mUserId), sessionId),
                new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                if (msg.what == 1) {
                    // 用 sessionId 查找避免 position 过时导致 IndexOutOfBounds
                    for (int i = 0; i < sessionList.size(); i++) {
                        if (sessionList.get(i).sessionId.equals(sessionId)) {
                            sessionList.remove(i);
                            sessionAdapter.notifyItemRemoved(i);
                            updateSessionEmptyState();
                            break;
                        }
                    }
                    if (sessionId.equals(mSessionId)) {
                        startNewSession();
                    }
                } else {
                    Toast.makeText(AiConversationActivity.this, "删除失败", Toast.LENGTH_SHORT).show();
                }
            }
        }, 1, 2, "ConvDelete");
    }

    /** 新会话：显示 AI 欢迎语 + 请求 TTS 欢迎音频 */
    private void showWelcomeAndTts() {
        AiMessage welcome = AiMessage.assistant(WELCOME_TEXT, null, -1);
        messageList.add(welcome);
        adapter.notifyItemInserted(0);
        rvConversation.scrollToPosition(0);
        // 请求 TTS 音频
        requestTts(WELCOME_TEXT);
    }

    private void requestTts(String text) {
        ApiConstants.execute(() -> {
            try {
                JSONObject j = new JSONObject();
                j.put("text", text);
                j.put("language", "en");
                String wav = MemoryApiClient.downloadWav(ApiConstants.getFullUrl("/tts/synthesize"), j,
                        AiConversationActivity.this);
                if (wav != null) {
                    Message m = Message.obtain();
                    m.what = MSG_TTS_SUCCESS;
                    m.obj = wav;
                    mainHandler.sendMessage(m);
                } else {
                    mainHandler.sendEmptyMessage(MSG_FAIL);
                }
            } catch (Exception e) {
                Log.e("TtsSynthesize", "Error: " + e.getMessage());
                mainHandler.sendEmptyMessage(MSG_FAIL);
            }
        });
    }

    private void setupRecyclerView() {
        adapter = new AiConversationAdapter(messageList);
        // 朗读按钮：点击才生成（流式边生成边播），再点一次即停止
        adapter.setAudioActionListener(new AiConversationAdapter.AudioActionListener() {
            @Override
            public void onPlayAudio(AiMessage message) {
                startStreamingTts(message);
            }

            @Override
            public void onStopAudio(AiMessage message) {
                stopStreamingTts(message);
            }
        });
        rvConversation.setLayoutManager(new LinearLayoutManager(this));
        rvConversation.setAdapter(adapter);

        // 空状态随消息列表联动：新增、删除、整体刷新都要重算
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                updateEmptyState();
            }

            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                updateEmptyState();
            }

            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
                updateEmptyState();
            }
        });
        updateEmptyState();
    }

    /**
     * 无消息且不在加载中时，显示空状态引导；否则隐藏。
     * progressBar 的可见性集中在 mainHandler 入口统一置为 GONE，刷新点见该处。
     */
    private void updateEmptyState() {
        if (layoutEmptyState == null || adapter == null || progressBar == null) {
            return;
        }
        boolean empty = adapter.getItemCount() == 0;
        boolean loading = progressBar.getVisibility() == View.VISIBLE;
        layoutEmptyState.setVisibility(empty && !loading ? View.VISIBLE : View.GONE);
    }

    private void setupInputArea() {
        btnInputMode.setOnClickListener(v -> switchInputMode());
        btnSend.setOnClickListener(v -> sendMessage());

        // 语音录制区域点击处理
        layoutVoiceRecord.setOnClickListener(v -> {
            if (mIsRecording) {
                stopVoiceRecording();
            } else {
                startVoiceRecording();
            }
        });
    }

    private void loadUserId() {
        mUserId = InnerSettingsManager.getInstance(this).getUserId();
    }

    // ==================== 会话管理 ====================

    private void startConversationSession() {
        progressBar.setVisibility(View.VISIBLE);
        ApiBridge.enqueue(MemoryApiClient.conversation().start(String.valueOf(mUserId)), mainHandler,
                MSG_SESSION_SUCCESS, MSG_FAIL, "ConversationStart");
    }

    private void loadConversationHistory() {
        if (mSessionId == null)
            return;
        ApiBridge.enqueue(MemoryApiClient.conversation().history(String.valueOf(mUserId), mSessionId), mainHandler,
                MSG_HISTORY_SUCCESS, MSG_FAIL, "ConversationHistory");
    }

    // ==================== 消息发送 ====================

    private void sendMessage() {
        String content = etMessage.getText() != null ? etMessage.getText().toString().trim() : "";
        if (TextUtils.isEmpty(content)) {
            Snackbar.make(coordinatorLayout, R.string.hint_type_message, Snackbar.LENGTH_SHORT).show();
            return;
        }
        if (mSessionId == null) {
            Snackbar.make(coordinatorLayout, "会话未建立，请稍后重试", Snackbar.LENGTH_SHORT).show();
            return;
        }

        // 添加用户消息到列表
        AiMessage userMsg = AiMessage.user(content);
        messageList.add(userMsg);
        adapter.notifyItemInserted(messageList.size() - 1);
        rvConversation.scrollToPosition(messageList.size() - 1);
        etMessage.setText("");
        hideKeyboard();

        // 添加流式 AI 消息占位符
        AiMessage aiMsg = AiMessage.streaming();
        messageList.add(aiMsg);
        adapter.notifyItemInserted(messageList.size() - 1);
        rvConversation.scrollToPosition(messageList.size() - 1);

        // 启动 SSE 流式连接
        sendStreamingMessage(content, aiMsg);
    }

    /**
     * 通过 SSE 流式发送消息并接收 AI 回复。
     * SSE 事件类型：chunk（文本块）、eval（评估）、done（完成）、error（错误）
     * 基于共享 OkHttp 连接池（MemoryApiClient.postStream），在共享网络线程池上执行。
     */
    private void sendStreamingMessage(String content, AiMessage aiMsg) {
        // 发新消息即打断上一条正在流式朗读的音频（避免新旧两段声音重叠）
        stopStreamingTts(streamingMsg);
        ApiConstants.execute(() -> {
            try {
                String urlStr = ApiConstants.getFullUrl("/conversation/stream");
                Map<String, String> headers = new HashMap<>();
                headers.put("userId", String.valueOf(mUserId));
                // 告知服务端本客户端的朗读方式，避免做完即弃的预生成：
                //   自动朗读开启 → 回复一到达就要播，服务端**预生成**是有用的，
                //                  因此不发此头（沿用旧行为，且 audioUrl 可作回退）
                //   自动朗读关闭 → 用户不点就不该生成，发此头让服务端跳过后台预生成
                // 说明：自动朗读时我们仍然走流式播放（首声更快），预生成只是服务端的兜底。
                boolean autoPlayAudio = com.deepsleep.memory.settings.UserSettingsManager
                        .getInstance(this).isAiAutoPlayAudioEnabled();
                if (!autoPlayAudio) {
                    headers.put("TTS-Stream", "1");
                }
                Map<String, String> form = new HashMap<>();
                form.put("sessionId", mSessionId);
                form.put("text", content);

                try (okhttp3.Response resp = MemoryApiClient.postStream(urlStr, headers, form)) {
                    if (resp.code() != 200) {
                        runOnUiThread(() -> {
                            aiMsg.setStreaming(false);
                            aiMsg.setContent("服务器错误: " + resp.code());
                            int pos = messageList.indexOf(aiMsg);
                            if (pos >= 0) adapter.notifyItemChanged(pos);
                        });
                        return;
                    }

                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(resp.body().byteStream(), "UTF-8"));
                    String line;
                    String eventType = "";
                    StringBuilder data = new StringBuilder();

                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("event:")) {
                            eventType = line.substring(6).trim();
                        } else if (line.startsWith("data:")) {
                            data.append(line.substring(5).trim());
                        } else if (line.isEmpty() && !eventType.isEmpty()) {
                            String finalEventType = eventType;
                            String finalData = data.toString();
                            runOnUiThread(() -> handleSseEvent(finalEventType, finalData, aiMsg));
                            eventType = "";
                            data.setLength(0);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "SSE 流式连接失败", e);
                runOnUiThread(() -> {
                    aiMsg.setStreaming(false);
                    if (aiMsg.getContent() == null || aiMsg.getContent().isEmpty()) {
                        aiMsg.setContent("连接失败，请检查网络后重试");
                    }
                    int pos = messageList.indexOf(aiMsg);
                    if (pos >= 0) adapter.notifyItemChanged(pos);
                });
            }
        });
    }

    /**
     * 处理 SSE 事件
     */
    private void handleSseEvent(String eventType, String data, AiMessage aiMsg) {
        try {
            switch (eventType) {
                case "chunk": {
                    JSONObject chunkData = new JSONObject(data);
                    String text = chunkData.optString("text", "");
                    aiMsg.setContent(text);
                    int pos = messageList.indexOf(aiMsg);
                    if (pos >= 0) {
                        adapter.notifyItemChanged(pos);
                    }
                    rvConversation.scrollToPosition(messageList.size() - 1);
                    break;
                }
                case "eval": {
                    JSONObject eval = new JSONObject(data);
                    if (eval.has("pronunciation"))
                        aiMsg.setPronunciationScore(eval.optDouble("pronunciation", -1));
                    if (eval.has("fluency"))
                        aiMsg.setFluencyScore(eval.optDouble("fluency", -1));
                    if (eval.has("grammar"))
                        aiMsg.setGrammarScore(eval.optDouble("grammar", -1));
                    if (eval.has("vocabulary"))
                        aiMsg.setVocabularyScore(eval.optDouble("vocabulary", -1));
                    if (eval.has("feedback"))
                        aiMsg.setFeedback(eval.optString("feedback", ""));
                    break;
                }
                case "done": {
                    JSONObject doneData = new JSONObject(data);
                    aiMsg.setStreaming(false);
                    long messageId = doneData.optLong("messageId", -1);
                    aiMsg.setMessageId(messageId);
                    aiMsg.setAudioPending(doneData.optBoolean("audioPending", false));

                    int pos = messageList.indexOf(aiMsg);
                    if (pos >= 0) {
                        adapter.notifyItemChanged(pos);
                    }

                    // 播放时机由设置项决定（设置 → AI 服务 → 朗读与音频）：
                    //   自动朗读开 → 文本一说完就流式合成并播放（更像日常对话）
                    //   自动朗读关 → 仅准备就绪，等用户点朗读按钮才生成（省 GPU、不打断阅读）
                    boolean autoPlay = com.deepsleep.memory.settings.UserSettingsManager
                            .getInstance(AiConversationActivity.this).isAiAutoPlayAudioEnabled();
                    if (autoPlay && aiMsg.getContent() != null
                            && !aiMsg.getContent().trim().isEmpty()) {
                        startStreamingTts(aiMsg);
                    } else if (doneData.optBoolean("audioPending", false) && messageId > 0) {
                        // 回退：老链路的"轮询音频 URL"（仅老客户端/未启用流式时才会走到）
                        startAudioPolling(messageId, messageList.indexOf(aiMsg));
                    }
                    break;
                }
                case "error": {
                    JSONObject errData = new JSONObject(data);
                    aiMsg.setStreaming(false);
                    String errorMsg = errData.optString("message", "未知错误");
                    if (aiMsg.getContent() == null || aiMsg.getContent().isEmpty()) {
                        aiMsg.setContent("Error: " + errorMsg);
                    }
                    int pos = messageList.indexOf(aiMsg);
                    if (pos >= 0) adapter.notifyItemChanged(pos);
                    Snackbar.make(coordinatorLayout, errorMsg, Snackbar.LENGTH_SHORT).show();
                    break;
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "SSE 事件解析失败: " + eventType, e);
        }
    }

    // ==================== 语音录制 ====================

    private void switchInputMode() {
        isVoiceMode = !isVoiceMode;
        layoutVoiceRecord.setVisibility(isVoiceMode ? View.VISIBLE : View.GONE);
        etMessage.setVisibility(isVoiceMode ? View.GONE : View.VISIBLE);
        btnSend.setVisibility(isVoiceMode ? View.GONE : View.VISIBLE);
        btnInputMode.setImageResource(isVoiceMode ? R.drawable.ic_keyboard_24 : R.drawable.ic_mic_24);

        // 语音模式下把输入框整块收起，并让输入栏内容居中。
        // 原实现只隐藏 EditText，外层 TextInputLayout 仍以 weight=1 占满整段宽度，
        // 结果右侧留出一大片空白、键盘与场景图标被挤在左侧，视觉失衡。
        layoutMessageInput.setVisibility(isVoiceMode ? View.GONE : View.VISIBLE);
        layoutInput.setGravity(isVoiceMode ? Gravity.CENTER : Gravity.CENTER_VERTICAL);
    }

    private void startVoiceRecording() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.RECORD_AUDIO },
                    REQUEST_RECORD_AUDIO);
            return;
        }
        if (mSessionId == null) {
            Snackbar.make(coordinatorLayout, "会话未建立，请稍后重试", Snackbar.LENGTH_SHORT).show();
            return;
        }

        String fileName = "conversation_" + System.currentTimeMillis() + ".m4a";
        mAudioRecord.startRecording(fileName, new MemAudioRecord.OnRecordListener() {
            @Override
            public void onRecordStart() {
                mIsRecording = true;
                runOnUiThread(() -> {
                    // 不再整条铺满高饱和红底：改为低饱和红底 + 红描边，
                    // 波形图标与提示文字同步转红，保持信息层级清晰。
                    layoutVoiceRecord.setBackgroundResource(R.drawable.bg_chat_voice_bar_recording);
                    if (ivVoiceWave != null) {
                        ivVoiceWave.setColorFilter(ContextCompat.getColor(
                                AiConversationActivity.this, R.color.theme_error));
                    }
                    if (tvVoiceHint != null) {
                        tvVoiceHint.setText("录音中…点击停止");
                        tvVoiceHint.setTextColor(ContextCompat.getColor(
                                AiConversationActivity.this, R.color.theme_error));
                    }
                });
            }

            @Override
            public void onRecordStop(String filePath) {
                mIsRecording = false;
            }

            @Override
            public void onError(String error) {
                mIsRecording = false;
            }
        }, this);
    }

    private void stopVoiceRecording() {
        if (!mIsRecording || !mAudioRecord.isRecording())
            return;
        mAudioRecord.stopRecording(null);
        mIsRecording = false;
        // stopRecording 内部已做 PCM→WAV 转换，直接用 getAudioFilePath()
        String filePath = mAudioRecord.getAudioFilePath();

        layoutVoiceRecord.setBackgroundResource(R.drawable.bg_chat_voice_bar);
        if (ivVoiceWave != null) {
            ivVoiceWave.setColorFilter(ContextCompat.getColor(this, R.color.chat_wave_idle));
        }
        if (tvVoiceHint != null) {
            tvVoiceHint.setText("点击录音");
            tvVoiceHint.setTextColor(ContextCompat.getColor(this, R.color.theme_primary));
        }
        if (filePath != null) {
            AiMessage voiceMsg = AiMessage.userVoice(filePath);
            messageList.add(voiceMsg);
            adapter.notifyItemInserted(messageList.size() - 1);
            rvConversation.scrollToPosition(messageList.size() - 1);
            sendAudioToServer(filePath);
        }
    }

    private void sendAudioToServer(String filePath) {
        progressBar.setVisibility(View.VISIBLE);
        Uri audioUri = Uri.parse("file://" + filePath);
        ApiBridge.enqueue(MemoryApiClient.conversation().sendAudio(String.valueOf(mUserId),
                ApiBridge.formPart(mSessionId), ApiBridge.filePart(this, audioUri, "audio", "recording.wav", "audio/wav")),
                mainHandler, MSG_SUCCESS, MSG_FAIL, "ConversationAudio");
    }

    // ==================== 响应解析 ====================

    /**
     * 修正服务端返回的 localhost URL 为实际可访问的地址
     */
    private String fixAudioUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isEmpty() || "null".equals(rawUrl)) {
            return null;
        }
        // 服务端 TTS 返回的 URL 可能使用 localhost，Android 设备无法访问
        // 替换为当前环境配置的实际 API 主机地址
        if (rawUrl.contains("localhost")) {
            String baseUrl = ApiConstants.getBaseUrl();
            // 从 http://localhost:8080/tts-audio/xxx.wav →
            // http://<当前环境主机>:8080/tts-audio/xxx.wav
            return rawUrl.replaceFirst("https?://localhost(:\\d+)?", baseUrl);
        }
        return rawUrl;
    }

    private void parseConversationResponse(String responseJson) {
        try {
            JSONObject root = new JSONObject(responseJson);
            if (!"200".equals(String.valueOf(root.optInt("code", -1)))) {
                String msg = root.optString("message", "AI服务异常");
                showError(msg);
                return;
            }

            JSONObject data = root.getJSONObject("data");

            String aiReply = data.getString("aiReply");
            String audioUrl = fixAudioUrl(data.optString("audioUrl", null));
            long messageId = data.optLong("messageId", -1);
            boolean audioPending = data.optBoolean("audioPending", false);

            AiMessage aiMsg = AiMessage.assistant(aiReply, audioUrl != null ? audioUrl : null, -1);
            aiMsg.setMessageId(messageId);
            aiMsg.setAudioPending(audioPending);

            // 解析五维评估结果
            if (!data.isNull("evaluation") && data.has("evaluation")) {
                try {
                    String evalStr = data.getString("evaluation");
                    JSONObject eval = new JSONObject(evalStr);
                    if (eval.has("pronunciation"))
                        aiMsg.setPronunciationScore(eval.optDouble("pronunciation", -1));
                    if (eval.has("fluency"))
                        aiMsg.setFluencyScore(eval.optDouble("fluency", -1));
                    if (eval.has("grammar"))
                        aiMsg.setGrammarScore(eval.optDouble("grammar", -1));
                    if (eval.has("vocabulary"))
                        aiMsg.setVocabularyScore(eval.optDouble("vocabulary", -1));
                    if (eval.has("feedback"))
                        aiMsg.setFeedback(eval.optString("feedback", ""));
                } catch (JSONException ignored) {}
            }

            messageList.add(aiMsg);
            adapter.notifyItemInserted(messageList.size() - 1);
            rvConversation.scrollToPosition(messageList.size() - 1);

            // 音频异步生成中，启动轮询
            if (audioPending && messageId > 0) {
                startAudioPolling(messageId, messageList.size() - 1);
            }

        } catch (JSONException e) {
            Log.e(TAG, "解析AI回复失败", e);
            showError("解析AI回复失败");
        }
    }

    private void parseHistoryResponse(String responseJson) {
        try {
            JSONObject root = new JSONObject(responseJson);
            if (!"200".equals(String.valueOf(root.optInt("code", -1)))) {
                return;
            }

            JSONArray history = root.getJSONArray("data");
            messageList.clear();
            for (int i = 0; i < history.length(); i++) {
                JSONObject item = history.getJSONObject(i);
                String role = item.getString("role");
                String content = item.optString("content", "");
                String audioUrl = fixAudioUrl(item.optString("audioUrl", null));
                long messageId = item.optLong("messageId", -1);

                int type = "user".equals(role) ? AiMessage.TYPE_USER : AiMessage.TYPE_ASSISTANT;
                AiMessage msg;
                if (type == AiMessage.TYPE_USER) {
                    msg = AiMessage.user(content);
                } else {
                    msg = AiMessage.assistant(content, audioUrl, -1);
                    boolean audioPending = item.optBoolean("audioPending", false);
                    msg.setAudioPending(audioPending);
                }
                msg.setMessageId(messageId);

                // 解析评估结果
                if (!item.isNull("evaluation") && item.has("evaluation")) {
                    try {
                        String evalStr = item.getString("evaluation");
                        JSONObject eval = new JSONObject(evalStr);
                        if (eval.has("pronunciation"))
                            msg.setPronunciationScore(eval.optDouble("pronunciation", -1));
                        if (eval.has("fluency"))
                            msg.setFluencyScore(eval.optDouble("fluency", -1));
                        if (eval.has("grammar"))
                            msg.setGrammarScore(eval.optDouble("grammar", -1));
                        if (eval.has("vocabulary"))
                            msg.setVocabularyScore(eval.optDouble("vocabulary", -1));
                        if (eval.has("feedback"))
                            msg.setFeedback(eval.optString("feedback", ""));
                    } catch (JSONException ignored) {}
                }

                messageList.add(msg);
            }
            adapter.notifyDataSetChanged();
            if (!messageList.isEmpty()) {
                rvConversation.scrollToPosition(messageList.size() - 1);
            }

        } catch (JSONException e) {
            Log.e(TAG, "解析历史记录失败", e);
        }
    }

    // ==================== Handler ====================

    private final Handler mainHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            progressBar.setVisibility(View.GONE);
            updateEmptyState();

            if (msg.what == MSG_LAST_SUCCESS) {
                String result = (String) msg.obj;
                try {
                    JSONObject root = new JSONObject(result);
                    if ("200".equals(String.valueOf(root.optInt("code", -1)))) {
                        JSONObject data = root.optJSONObject("data");
                        if (data != null && data.optString("sessionId", null) != null) {
                            mSessionId = data.getString("sessionId");
                            loadConversationHistory();
                            return;
                        }
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "解析最近会话失败", e);
                }
                // 无历史会话 → 新建
                startNewSession();
                return;
            }

            if (msg.what == MSG_SESSION_SUCCESS) {
                String result = (String) msg.obj;
                try {
                    JSONObject root = new JSONObject(result);
                    if ("200".equals(String.valueOf(root.optInt("code", -1)))) {
                        JSONObject data = root.getJSONObject("data");
                        mSessionId = data.getString("sessionId");
                        showWelcomeAndTts();
                    } else {
                        showError("创建会话失败");
                    }
                } catch (JSONException e) {
                    showError("解析会话数据失败");
                }
                return;
            }

            if (msg.what == MSG_HISTORY_SUCCESS) {
                String result = (String) msg.obj;
                parseHistoryResponse(result);
                return;
            }

            if (msg.what == MSG_TTS_SUCCESS) {
                // TTS 欢迎音频已下载，播放
                String wavPath = (String) msg.obj;
                if (messageList.size() > 0) {
                    messageList.get(0).setAudioUrl(wavPath);
                    adapter.notifyItemChanged(0);
                }
                return;
            }

            if (msg.what == MSG_SUCCESS) {
                parseConversationResponse((String) msg.obj);
            } else if (msg.what == MSG_FAIL) {
                showError("AI服务请求失败");
            }
        }
    };

    // ==================== 朗读（统一走 AudioPlaybackManager） ====================

    /** 流式朗读端点（与 MemoryServer 的 TtsController 对应） */
    private static final String STREAM_TTS_PATH = "/tts/synthesize-stream";

    /** 当前正在流式朗读的消息（用于"再点一次停止"与状态回滚） */
    private AiMessage streamingMsg;

    /**
     * 点击朗读：**此刻才**向后端请求流式合成，边生成边播（首声约 0.6s）。
     *
     * 与旧链路的区别：旧链路在 AI 回复到达时就异步生成整段音频、落盘、再轮询 URL，
     * 用户点击时才下载播放。现在改为"点击才生成"：
     * - 用户不点 → 完全不生成，不浪费 GPU；
     * - 同一条回复重复点击 → 服务端按文本派生固定 seed，音频可复现（相关性 0.99999）；
     * - 不落盘、不产生任何音频文件。
     *
     * 播放统一交给 {@link AudioPlaybackManager}：它会先打断任何正在播放的音频
     * （无论是另一条回复的流式朗读，还是片段音频），保证同一时刻只有一路声音。
     */
    private void startStreamingTts(AiMessage msg) {
        if (msg == null) {
            return;
        }
        final String text = msg.getContent();
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        // 已经在播这条 → 视为停止
        if (streamingMsg == msg) {
            stopStreamingTts(msg);
            return;
        }
        // 切换目标：交由管理器统一打断上一路（含 MediaPlayer 释放）
        if (streamingMsg != null) {
            stopStreamingTts(streamingMsg);
        }

        streamingMsg = msg;
        msg.setAudioPending(true);   // 等首片：按钮转圈
        msg.setAudioPlaying(false);
        refreshMessageRow(msg);

        final String url = ApiConstants.getFullUrl(STREAM_TTS_PATH);
        AudioPlaybackManager.playStreaming(this, url, text,
                new AudioPlaybackManager.Listener() {
                    @Override
                    public void onStarted(int elapsedMs) {
                        Log.i(TAG, "流式朗读首声: " + elapsedMs + "ms");
                        if (streamingMsg != msg) {
                            return;   // 等待期间已被停下
                        }
                        msg.setAudioPending(false);
                        msg.setAudioPlaying(true);   // 已出声：按钮变"停止"
                        refreshMessageRow(msg);
                    }

                    @Override
                    public void onCompleted(int totalMs) {
                        Log.i(TAG, "流式朗读完成: " + totalMs + "ms");
                        if (streamingMsg == msg) {
                            streamingMsg = null;
                        }
                        msg.setAudioPending(false);
                        msg.setAudioPlaying(false);
                        refreshMessageRow(msg);
                    }

                    @Override
                    public void onError(String message) {
                        Log.w(TAG, "流式朗读失败: " + message);
                        if (streamingMsg == msg) {
                            streamingMsg = null;
                        }
                        msg.setAudioPending(false);
                        msg.setAudioPlaying(false);
                        refreshMessageRow(msg);
                        Snackbar.make(coordinatorLayout, "朗读失败，请稍后再试",
                                Snackbar.LENGTH_SHORT).show();
                    }
                });
    }

    /** 停止流式朗读（用户再点一次 / 页面销毁 / 发新消息） */
    private void stopStreamingTts(AiMessage msg) {
        AudioPlaybackManager.stop();
        if (msg != null) {
            msg.setAudioPending(false);
            msg.setAudioPlaying(false);
            refreshMessageRow(msg);
        }
        if (streamingMsg == msg) {
            streamingMsg = null;
        }
    }

    /** 刷新某条消息所在行（仅在消息仍在列表中时） */
    private void refreshMessageRow(AiMessage msg) {
        int pos = messageList.indexOf(msg);
        if (pos >= 0 && adapter != null) {
            adapter.notifyItemChanged(pos);
        }
    }

    // ==================== 音频轮询 ====================

    private static final int MAX_POLL_ATTEMPTS = 80; // 2 分钟（80 × 1.5s = 120s）
    private static final int POLL_INTERVAL_MS = 1500;

    /**
     * 启动音频轮询，异步获取 TTS 音频 URL
     */
    private void startAudioPolling(long messageId, int listPosition) {
        ApiConstants.execute(() -> {
            int attempts = 0;
            while (attempts < MAX_POLL_ATTEMPTS) {
                try {
                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException e) {
                    return;
                }
                attempts++;

                String url = ApiConstants.getFullUrl("/conversation/audio/" + messageId);
                String result = MemoryApiClient.doHttpGetNoPara(url);
                if (result == null)
                    continue;

                try {
                    JSONObject root = new JSONObject(result);
                    if (root.optInt("code", -1) == 200) {
                        JSONObject data = root.optJSONObject("data");
                        if (data != null && data.optBoolean("ready", false)) {
                            String audioUrl = fixAudioUrl(data.optString("audioUrl", null));
                            if (audioUrl != null && listPosition < messageList.size()) {
                                AiMessage msg = messageList.get(listPosition);
                                msg.setAudioUrl(audioUrl);
                                msg.setAudioPending(false);
                                runOnUiThread(() -> adapter.notifyItemChanged(listPosition));
                            }
                            return; // 成功获取，停止轮询
                        }
                    }
                } catch (JSONException ignored) {
                }
            }
            // 超时：音频生成失败
            runOnUiThread(() -> {
                if (listPosition < messageList.size()) {
                    AiMessage msg = messageList.get(listPosition);
                    msg.setAudioPending(false);
                    adapter.notifyItemChanged(listPosition);
                }
            });
        });
    }

    // ==================== 辅助方法 ====================

    private void showError(String msg) {
        Snackbar.make(coordinatorLayout, msg, Snackbar.LENGTH_SHORT).show();
    }

    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceRecording();
            } else {
                Toast.makeText(this, "需要录音权限才能使用语音输入", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ==================== 场景选择 & 模式管理 ====================

    private void showScenarioPicker() {
        if (mSessionId == null) {
            Snackbar.make(coordinatorLayout, "会话未建立，请稍后重试", Snackbar.LENGTH_SHORT).show();
            return;
        }
        ScenarioPickerSheet sheet = ScenarioPickerSheet.newInstance(String.valueOf(mUserId));
        sheet.setOnScenarioSelectedListener(new ScenarioPickerSheet.OnScenarioSelectedListener() {
            @Override
            public void onScenarioSelected(String scenarioId, String title, String aiRole, String userRole,
                    String openingLine) {
                startScenarioMode(scenarioId, title, aiRole, userRole, openingLine);
            }

            @Override
            public void onCustomScenarioRequested() {
                // 自定义场景：引导用户输入场景描述
                showCustomScenarioDialog();
            }
        });
        sheet.show(getSupportFragmentManager(), "scenario_picker");
    }

    private void startScenarioMode(String scenarioId, String title, String aiRole, String userRole,
            String openingLine) {
        if (mSessionId == null)
            return;

        progressBar.setVisibility(View.VISIBLE);
        ApiBridge.enqueue(MemoryApiClient.conversation().startScenario(String.valueOf(mUserId), mSessionId, scenarioId),
                new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                progressBar.setVisibility(View.GONE);
                if (msg.what == 1) {
                    try {
                        JSONObject root = new JSONObject((String) msg.obj);
                        if (root.optInt("code", -1) == 200) {
                            mCurrentMode = "ROLE_PLAY";
                            updateModeLabel(title + " — 你是" + userRole);

                            // 显示 AI 角色的开场白
                            AiMessage openingMsg = AiMessage.assistant(openingLine, null, -1);
                            messageList.add(openingMsg);
                            adapter.notifyItemInserted(messageList.size() - 1);
                            rvConversation.scrollToPosition(messageList.size() - 1);

                            // 请求开场白 TTS
                            requestTts(openingLine);
                        } else {
                            showError(root.optString("message", "启动场景失败"));
                        }
                    } catch (Exception e) {
                        showError("启动场景失败");
                    }
                } else {
                    showError("启动场景失败");
                }
            }
        }, 1, -1, "StartScenario");
    }

    private void showCustomScenarioDialog() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("自定义场景")
                .setMessage("描述你想要的场景，AI 会根据你的描述进入角色。例如：\n\n" + "\"我在机场，需要办理登机手续\"\n" + "\"我想练习在餐厅点餐\"")
                .setPositiveButton("确定", (dialog, which) -> {
                    // 这里可以打开一个输入框让用户输入自定义场景
                    // 暂时用一个简单的对话框
                    android.widget.EditText input = new android.widget.EditText(this);
                    input.setHint("描述场景...");
                    input.setPadding(48, 32, 48, 16);

                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("描述场景")
                            .setView(input).setPositiveButton("开始", (d, w) -> {
                                String desc = input.getText().toString().trim();
                                if (!desc.isEmpty()) {
                                    startCustomScenario(desc);
                                }
                            }).setNegativeButton("取消", null).show();
                }).setNegativeButton("取消", null).show();
    }

    private void startCustomScenario(String description) {
        if (mSessionId == null)
            return;
        mCurrentMode = "ROLE_PLAY";
        updateModeLabel("自定义场景");

        // 发送场景描述作为第一条消息
        String prompt = "[SYSTEM: 进入角色扮演模式。场景描述: " + description + "。请用你的开场白开始这个场景，保持角色，用英语对话。]";
        AiMessage userMsg = AiMessage.user("我想练习这个场景：" + description);
        messageList.add(userMsg);
        adapter.notifyItemInserted(messageList.size() - 1);
        rvConversation.scrollToPosition(messageList.size() - 1);

        // 发送到服务器
        progressBar.setVisibility(View.VISIBLE);
        ApiBridge.enqueue(MemoryApiClient.conversation().sendText(String.valueOf(mUserId),
                ApiBridge.formPart(mSessionId), ApiBridge.formPart(prompt)), mainHandler, MSG_SUCCESS, MSG_FAIL,
                "ConversationMsg");
    }

    private void updateModeLabel(String text) {
        if (layoutModeLabel != null && tvModeLabel != null) {
            layoutModeLabel.setVisibility(View.VISIBLE);
            tvModeLabel.setText(text);
        }
    }

    private void exitCurrentMode() {
        if (mSessionId == null)
            return;

        progressBar.setVisibility(View.VISIBLE);
        ApiBridge.enqueue(MemoryApiClient.conversation().switchMode(String.valueOf(mUserId), mSessionId, "FREE_CHAT"),
                new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                progressBar.setVisibility(View.GONE);
                mCurrentMode = "FREE_CHAT";
                if (layoutModeLabel != null) {
                    layoutModeLabel.setVisibility(View.GONE);
                }
                // 发送系统消息提示模式已切换
                AiMessage sysMsg = AiMessage.assistant("Back to free chat mode! Feel free to talk about anything.",
                        null, -1);
                messageList.add(sysMsg);
                adapter.notifyItemInserted(messageList.size() - 1);
                rvConversation.scrollToPosition(messageList.size() - 1);
            }
        }, 1, -1, "SwitchMode");
    }

    // ==================== 对话总结 ====================

    private void showConversationSummary() {
        if (messageList.isEmpty())
            return;

        int turnCount = 0;
        int userMsgCount = 0;
        for (AiMessage msg : messageList) {
            if (msg.getType() == AiMessage.TYPE_USER)
                userMsgCount++;
        }
        turnCount = userMsgCount;

        if (turnCount < 2)
            return; // 至少2轮才显示总结

        // 在列表末尾插入总结卡片
        AiMessage summaryMsg = AiMessage.assistant("", null, -1);
        summaryMsg.setSummary(true);
        summaryMsg.setSummaryWordsUsed(0);
        summaryMsg.setSummaryCorrections(0);
        summaryMsg.setSummaryTurnCount(turnCount);
        messageList.add(summaryMsg);
        adapter.notifyItemInserted(messageList.size() - 1);
        rvConversation.scrollToPosition(messageList.size() - 1);
    }

}