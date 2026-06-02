package com.deepsleep.memory.ui.treasure_view.aichat_view;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
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
import com.deepsleep.memory.handle_utils.MemAudioRecord;
import com.deepsleep.memory.network.ApiConstants;
import com.deepsleep.memory.network.GetDataByThread;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import com.deepsleep.memory.network.HttpManager;

public class AiConversationActivity extends AppCompatActivity {
    private static final String TAG = "AiConversation";
    private static final String PREF_NAME = "UserPrefs";
    private static final String KEY_USER_ID = "userId";
    private static final int MSG_SUCCESS = 1;
    private static final int MSG_FAIL = 2;
    private static final int MSG_SESSION_SUCCESS = 3;
    private static final int MSG_HISTORY_SUCCESS = 4;
    private static final int MSG_LAST_SUCCESS = 5;
    private static final int MSG_TTS_SUCCESS = 6;
    private static final int MSG_AUDIO_POLL_READY = 7;
    private static final int REQUEST_RECORD_AUDIO = 123;

    private static final String WELCOME_TEXT = "Hello! Welcome to Memory English Learning App 🎉\n\n"
            + "I'm your English speaking partner. Let's chat in English only — "
            + "no matter what you say, I'll always reply in English to help you practice!\n\n"
            + "Feel free to talk about anything: your day, hobbies, studies, or any topic you like. "
            + "I'll keep the conversation going and gently correct any mistakes. Ready? Let's start! 😊";

    private View coordinatorLayout;
    private RecyclerView rvConversation;
    private ProgressBar progressBar;
    private LinearLayout layoutInput;
    private ImageButton btnInputMode;
    private FloatingActionButton btnSend;
    private TextInputEditText etMessage;
    private LinearLayout layoutVoiceRecord;
    private TextView tvVoiceHint;
    private DrawerLayout drawerLayout;

    private AiConversationAdapter adapter;
    private List<AiMessage> messageList = new ArrayList<>();
    private boolean isVoiceMode = false;

    private String mSessionId = null;
    private int mUserId = 0;
    private MemAudioRecord mAudioRecord;
    private boolean mIsRecording = false;

    // 侧边栏会话列表
    private RecyclerView rvSessionList;
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
            View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent,
                    false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            SessionInfo s = sessionList.get(pos);
            h.text1.setText(s.title != null && !s.title.isEmpty() ? s.title : "会话 " + (pos + 1));
            h.text2.setText(s.updatedTime);
            h.itemView.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.END);
                switchToSession(s.sessionId);
            });
            h.itemView.setOnLongClickListener(v -> {
                new MaterialAlertDialogBuilder(AiConversationActivity.this).setTitle("删除会话")
                        .setMessage("确定要删除「" + h.text1.getText() + "」吗？此操作不可撤销。")
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
            TextView text1, text2;

            VH(View v) {
                super(v);
                text1 = v.findViewById(android.R.id.text1);
                text2 = v.findViewById(android.R.id.text2);
            }
        }
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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mAudioRecord != null) {
            mAudioRecord.cleanup();
        }
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
        btnSend = findViewById(R.id.btnSend);
        etMessage = findViewById(R.id.etMessage);
        layoutVoiceRecord = findViewById(R.id.layoutVoiceRecord);
        tvVoiceHint = findViewById(R.id.tvVoiceHint);
        rvSessionList = findViewById(R.id.rvSessionList);
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
        GetDataByThread api = new GetDataByThread("/conversation/last");
        api.getLastConversation(mainHandler, MSG_LAST_SUCCESS, MSG_FAIL, String.valueOf(mUserId));
    }

    private void startNewSession() {
        messageList.clear();
        adapter.notifyDataSetChanged();
        mSessionId = null;
        progressBar.setVisibility(View.VISIBLE);
        GetDataByThread api = new GetDataByThread("/conversation/start");
        api.startConversation(mainHandler, MSG_SESSION_SUCCESS, MSG_FAIL, String.valueOf(mUserId));
    }

    private void loadSessions() {
        GetDataByThread api = new GetDataByThread("/conversation/sessions");
        api.getConversationSessions(new Handler(Looper.getMainLooper()) {
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
                        }
                    } catch (JSONException ignored) {
                    }
                }
            }
        }, 1, -1, String.valueOf(mUserId));
    }

    private void switchToSession(String sessionId) {
        if (sessionId.equals(mSessionId))
            return;
        mSessionId = sessionId;
        messageList.clear();
        adapter.notifyDataSetChanged();
        progressBar.setVisibility(View.VISIBLE);
        GetDataByThread api = new GetDataByThread("/conversation/history");
        api.getConversationHistory(mainHandler, MSG_HISTORY_SUCCESS, MSG_FAIL, String.valueOf(mUserId), mSessionId);
    }

    private void deleteSession(String sessionId, int position) {
        GetDataByThread api = new GetDataByThread("/conversation/delete");
        api.deleteConversation(new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                if (msg.what == 1) {
                    // 用 sessionId 查找避免 position 过时导致 IndexOutOfBounds
                    for (int i = 0; i < sessionList.size(); i++) {
                        if (sessionList.get(i).sessionId.equals(sessionId)) {
                            sessionList.remove(i);
                            sessionAdapter.notifyItemRemoved(i);
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
        }, 1, 2, String.valueOf(mUserId), sessionId);
    }

    /** 新会话：显示 AI 欢迎语 + 请求 TTS 欢迎音频 */
    private void showWelcomeAndTts() {
        AiMessage welcome = AiMessage.assistant(WELCOME_TEXT, null, -1);
        messageList.add(welcome);
        adapter.notifyItemInserted(0);
        rvConversation.scrollToPosition(0);
        // 请求 TTS 音频
        GetDataByThread tts = new GetDataByThread("/tts/synthesize");
        tts.synthesizeTts(mainHandler, MSG_TTS_SUCCESS, MSG_FAIL, WELCOME_TEXT, this);
    }

    private void setupRecyclerView() {
        adapter = new AiConversationAdapter(messageList);
        rvConversation.setLayoutManager(new LinearLayoutManager(this));
        rvConversation.setAdapter(adapter);
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
        SharedPreferences sp = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        mUserId = sp.getInt(KEY_USER_ID, 0);
    }

    // ==================== 会话管理 ====================

    private void startConversationSession() {
        progressBar.setVisibility(View.VISIBLE);
        GetDataByThread api = new GetDataByThread("/conversation/start");
        api.startConversation(mainHandler, MSG_SESSION_SUCCESS, MSG_FAIL, String.valueOf(mUserId));
    }

    private void loadConversationHistory() {
        if (mSessionId == null)
            return;
        GetDataByThread api = new GetDataByThread("/conversation/history");
        api.getConversationHistory(mainHandler, MSG_HISTORY_SUCCESS, MSG_FAIL, String.valueOf(mUserId), mSessionId);
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

        // 添加到消息列表
        AiMessage userMsg = AiMessage.user(content);
        messageList.add(userMsg);
        adapter.notifyItemInserted(messageList.size() - 1);
        rvConversation.scrollToPosition(messageList.size() - 1);
        etMessage.setText("");
        hideKeyboard();

        // 发送文字消息
        progressBar.setVisibility(View.VISIBLE);
        GetDataByThread api = new GetDataByThread("/conversation/message");
        api.sendConversationText(mainHandler, MSG_SUCCESS, MSG_FAIL, String.valueOf(mUserId), mSessionId, content);
    }

    // ==================== 语音录制 ====================

    private void switchInputMode() {
        isVoiceMode = !isVoiceMode;
        layoutVoiceRecord.setVisibility(isVoiceMode ? View.VISIBLE : View.GONE);
        etMessage.setVisibility(isVoiceMode ? View.GONE : View.VISIBLE);
        btnSend.setVisibility(isVoiceMode ? View.GONE : View.VISIBLE);
        btnInputMode.setImageResource(isVoiceMode ? R.drawable.ic_keyboard_24dp : R.drawable.ic_mic_24dp);
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
                    layoutVoiceRecord.setBackgroundColor(
                            ContextCompat.getColor(AiConversationActivity.this, R.color.theme_error));
                    if (tvVoiceHint != null) {
                        tvVoiceHint.setText("录音中…点击停止");
                        tvVoiceHint.setTextColor(ContextCompat.getColor(AiConversationActivity.this, R.color.white));
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

        layoutVoiceRecord.setBackgroundColor(ContextCompat.getColor(this, R.color.white));
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
        GetDataByThread api = new GetDataByThread("/conversation/message");
        api.sendConversationAudio(mainHandler, MSG_SUCCESS, MSG_FAIL, String.valueOf(mUserId), mSessionId, audioUri,
                this);
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
            // http://192.168.102.14:8080/tts-audio/xxx.wav
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

            // v3.0：evaluation 已移除，不再解析

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

                // v3.0：evaluation 已移除，不再解析

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

    // ==================== 音频轮询 ====================

    private static final int MAX_POLL_ATTEMPTS = 80;  // 2 分钟（80 × 1.5s = 120s）
    private static final int POLL_INTERVAL_MS = 1500;

    /**
     * 启动音频轮询，异步获取 TTS 音频 URL
     */
    private void startAudioPolling(long messageId, int listPosition) {
        new Thread(() -> {
            int attempts = 0;
            while (attempts < MAX_POLL_ATTEMPTS) {
                try {
                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException e) {
                    return;
                }
                attempts++;

                String url = ApiConstants.getBaseUrl() + "/conversation/audio/" + messageId;
                String result = HttpManager.doHttpGetNoPara(url);
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
        }).start();
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

}