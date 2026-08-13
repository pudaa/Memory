package com.deepsleep.memory.ui.auth_view;

import android.annotation.SuppressLint;
import android.os.Bundle;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.deepsleep.memory.settings.InnerSettingsManager;
import com.deepsleep.memory.settings.UserSettingsManager;
import com.deepsleep.memory.handle_utils.lexicon.db.LexiconDatabase;
import com.deepsleep.memory.ui.MainActivity;
import com.deepsleep.memory.R;

import android.content.Intent;
import com.deepsleep.memory.ui.init_view.BookSelectActivity;
import com.deepsleep.memory.network.GetDataByThread;
import com.google.android.material.textfield.TextInputLayout;
import org.json.JSONException;
import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {
    private InnerSettingsManager innerSettingsManager;
    private TextInputLayout tilPhone, tilPassword;
    private EditText etPhone, etPassword;
    private Button btnLogin;
    private TextView tvRegister, tvForgotPassword;
    static final int msg_success = 1;
    static final int msg_failed = -1;
    private final MyHandler myHandler = new MyHandler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_layout);
        EdgeToEdge.enable(this);
        // 预热词库数据库：后台打开（首装含 assets 拷贝），与后续网络请求并行，
        // 避免单词清单响应后主线程首次查询才触发 open 造成卡顿
        LexiconDatabase.warmUpAsync(getApplicationContext());
        innerSettingsManager = InnerSettingsManager.getInstance(this);

        tilPhone = findViewById(R.id.til_phone);
        tilPassword = findViewById(R.id.til_password);
        etPhone = tilPhone.findViewById(R.id.text_input_edit_phone);
        etPassword = tilPassword.findViewById(R.id.text_input_edit_password);
        btnLogin = findViewById(R.id.btn_login);
        tvRegister = findViewById(R.id.tv_register);
        tvForgotPassword = findViewById(R.id.tv_forgot_password);
        // try {// 删除SharedPreferences文件
        // this.deleteSharedPreferences(PREF_NAME);
        // } catch (Exception ignored) {}
        // 检查登录状态
        if (innerSettingsManager.isLoggedIn() == 1 || innerSettingsManager.isLoggedIn() == 2) {
            int userId = innerSettingsManager.getUserId();
            startMainActivity(userId);
        }

        btnLogin.setOnClickListener(v -> performLogin());

        tvRegister.setOnClickListener(v -> startRegisterActivity());

        tvForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());
    }

    private void performLogin() {
        String phone = etPhone.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        // 简单的表单验证
        if (phone.isEmpty()) {
            tilPhone.setError("请输入手机号码");
            return;
        }

        if (password.isEmpty()) {
            tilPassword.setError("请输入密码");
            return;
        }

        // 清除错误提示
        tilPhone.setError(null);
        tilPassword.setError(null);

        GetDataByThread getDataByThread = new GetDataByThread("/auth/login");
        getDataByThread.login(myHandler, msg_success, msg_failed, phone, password);
    }

    private void saveLoginStatus(int isLoggedIn, int userId, String nickName, String userName, String avatarUrl) {
        innerSettingsManager.setLoggedIn(isLoggedIn);
        innerSettingsManager.setUserId(userId);
        innerSettingsManager.setNickName(nickName);
        innerSettingsManager.setUserName(userName);
        innerSettingsManager.setAvatarUrl(avatarUrl);
    }

    private void startMainActivity(int userId) {// 我勒个屎山啊，这个地方逻辑咋变得这么复杂的
        // Toast.makeText(this, String.valueOf(innerSettingsManager.isLoggedIn()),
        // Toast.LENGTH_SHORT).show();
        // 异步拉取用户级设置并应用（跨设备/跨账号恢复本人偏好）
        syncUserSettingsFromServer(userId);
        if (innerSettingsManager.isLoggedIn() == 2) {// 为了减少请求带来的延迟设置的，正常流程走完后，每次进入软件都应该直接进入主界面
            Log.i("LoginActivity", "startMainActivity: " + userId);
            Intent intent1 = new Intent(LoginActivity.this, MainActivity.class);
            startActivity(intent1);
            finish();
        } else {
            // 有退出登录过后的话，会对用户信息进行一次检查
            GetDataByThread getDataByThread = new GetDataByThread("/auth/getCurrentPlan");
            getDataByThread.getPlan(new Handler(Looper.getMainLooper()) {
                @Override
                public void handleMessage(@NonNull Message msg) {
                    super.handleMessage(msg);
                    switch (msg.what) {
                    case msg_success:// 如果是数据获取成功的消息
                        String result = (String) msg.obj;
                        JSONObject responseJson = null;
                        try {
                            responseJson = new JSONObject(result);
                            String code = responseJson.getString("code");
                            Log.i("LoginActivity", "handleMessage: " + code);
                            switch (code) {
                            case "200":// 有找到学习计划说明直接进入主界面
                                innerSettingsManager.setLoggedIn(2);
                                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                                startActivity(intent);
                                finish();
                                break;
                            case "404":// 没有找到用户的学习计划
                                if (innerSettingsManager.isLoggedIn() == 2) {
                                    Intent intent1 = new Intent(LoginActivity.this, MainActivity.class);
                                    startActivity(intent1);
                                    finish();
                                } else if (innerSettingsManager.isLoggedIn() == 1) {
                                    Intent intent2 = new Intent(LoginActivity.this, BookSelectActivity.class);
                                    startActivity(intent2);
                                    finish();
                                }
                                break;
                            }
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                        break;
                    case msg_failed:
                        break;
                    }
                }
            }, msg_success, msg_failed, String.valueOf(userId));
        }
    }

    /** 从服务端拉取用户级设置并应用到本地（跨设备/跨账号恢复本人偏好） */
    private void syncUserSettingsFromServer(int userId) {
        GetDataByThread api = new GetDataByThread("/auth/getUserSettings");
        api.getUserSettings(new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                if (msg.what != msg_success)
                    return;
                try {
                    JSONObject response = new JSONObject((String) msg.obj);
                    if ("200".equals(response.getString("code"))) {
                        JSONObject settings = response.optJSONObject("settings");
                        if (settings != null) {
                            UserSettingsManager.getInstance(LoginActivity.this).applyUserSettings(settings);
                        }
                    }
                } catch (JSONException e) {
                    Log.e("LoginActivity", "解析用户设置失败", e);
                }
            }
        }, msg_success, msg_failed, String.valueOf(userId));
    }

    private void startRegisterActivity() {
        Intent intent = new Intent(this, RegisterActivity.class);
        startActivity(intent);
    }

    private void showForgotPasswordDialog() {
        Toast.makeText(this, "忘记密码功能", Toast.LENGTH_SHORT).show();
    }

    @SuppressLint("HandlerLeak")
    class MyHandler extends Handler {
        MyHandler() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
            case msg_success:
                String result = (String) msg.obj;
                JSONObject responseJson = null;
                try {
                    responseJson = new JSONObject(result);
                    String code = responseJson.getString("code");
                    switch (code) {
                    case "200":
                        int userId = responseJson.getInt("user_id");
                        String nickName = responseJson.getString("nickname");
                        String userName = responseJson.getString("username");
                        String avatarUrl = responseJson.getString("avatar_url");
                        saveLoginStatus(1, userId, nickName, userName, avatarUrl); // 保存登录状态和用户信息

                        startMainActivity(userId);
                        Toast.makeText(LoginActivity.this, "登录成功", Toast.LENGTH_SHORT).show();

                        break;
                    case "404":
                        Toast.makeText(LoginActivity.this, "用户名不存在", Toast.LENGTH_SHORT).show();
                        break;
                    case "500":
                        Toast.makeText(LoginActivity.this, "密码错误", Toast.LENGTH_SHORT).show();
                        break;
                    }
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                break;
            case msg_failed:
                Toast.makeText(LoginActivity.this, "获取失败", Toast.LENGTH_LONG).show();
                break;

            }
        }
    }

}