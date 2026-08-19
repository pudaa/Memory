package com.deepsleep.memory.ui.auth_view;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.deepsleep.memory.R;
import com.deepsleep.memory.network.ApiBridge;
import com.deepsleep.memory.network.MemoryApiClient;
import com.google.android.material.textfield.TextInputLayout;
import org.json.JSONObject;

import java.util.Locale;
import java.util.Random;

public class RegisterActivity extends AppCompatActivity {
    private TextInputLayout tilRegisterPhone, tilRegisterPassword, tilRegisterConfirmPassword;
    private EditText etRegisterPhone, etRegisterPassword, etRegisterConfirmPassword;
    private Button btnRegister;
    private TextView toLogin;
    static final int msg_success = 1;
    static final int msg_failed = -1;
    private final MyHandler myHandler = new MyHandler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.register_layout);
        EdgeToEdge.enable(this);
        tilRegisterPhone = findViewById(R.id.til_register_phone);
        tilRegisterPassword = findViewById(R.id.til_register_password);
        tilRegisterConfirmPassword = findViewById(R.id.til_register_confirm_password);
        etRegisterPhone = tilRegisterPhone.findViewById(R.id.text_input_edit_register_phone);
        etRegisterPassword = tilRegisterPassword.findViewById(R.id.text_input_edit_register_password);
        etRegisterConfirmPassword = tilRegisterConfirmPassword
                .findViewById(R.id.text_input_edit_register_confirm_password);
        btnRegister = findViewById(R.id.btn_register);
        toLogin = findViewById(R.id.tv_login);

        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("RegisterActivity", "Register button clicked");
                performRegister();
            }
        });
        toLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                startActivity(intent);
                finish();
            }
        });

    }

    private void performRegister() {
        String phone = etRegisterPhone.getText().toString().trim();
        String password = etRegisterPassword.getText().toString().trim();
        String confirmPassword = etRegisterConfirmPassword.getText().toString().trim();

        if (phone.isEmpty()) {
            tilRegisterPhone.setError("请输入手机号码");
            return;
        }

        if (password.isEmpty()) {
            tilRegisterPassword.setError("请输入密码");
            return;
        }

        if (confirmPassword.isEmpty()) {
            tilRegisterConfirmPassword.setError("请确认密码");
            return;
        }

        if (!password.equals(confirmPassword)) {
            tilRegisterConfirmPassword.setError("两次输入的密码不一致");
            return;
        }

        tilRegisterPhone.setError(null);
        tilRegisterPassword.setError(null);
        tilRegisterConfirmPassword.setError(null);

        String nickname = String.format(Locale.getDefault(), "%05d", generateRandomNumber(5));
        String avatarUrl = "default_avatar_url";
        Log.d("RegisterActivity", nickname);
        ApiBridge.enqueue(MemoryApiClient.auth().register(phone, password, nickname, avatarUrl), myHandler, msg_success,
                msg_failed, "Register");
    }

    private int generateRandomNumber(int length) {
        Random random = new Random();
        int min = (int) Math.pow(10, length - 1);
        int max = (int) Math.pow(10, length) - 1;
        return random.nextInt(max - min + 1) + min;
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
                try {
                    JSONObject responseJson = new JSONObject(result);
                    String code = responseJson.getString("code");
                    if ("200".equals(code)) {
                        Toast.makeText(RegisterActivity.this, "注册成功", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                        startActivity(intent);
                        finish();
                    } else if ("409".equals(code)) {
                        Toast.makeText(RegisterActivity.this, "该手机号已被注册", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(RegisterActivity.this, "注册失败", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(RegisterActivity.this, "注册结果解析失败", Toast.LENGTH_SHORT).show();
                }
                break;
            case msg_failed:
                Toast.makeText(RegisterActivity.this, "获取失败", Toast.LENGTH_LONG).show();
                break;
            }
        }
    }
}
