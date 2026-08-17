package com.deepsleep.memory.ui.extra_view.setting_view;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.deepsleep.memory.R;
import com.deepsleep.memory.network.MemoryApiClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AiProviderSettingsActivity extends AppCompatActivity {
    private EditText name;
    private EditText protocol;
    private EditText baseUrl;
    private EditText customModel;
    private EditText apiKey;
    private CheckBox consent;
    private Spinner modelSpinner;
    private TextView status;
    private final List<String> models = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ai_provider_settings_layout);
        name = findViewById(R.id.ai_provider_name);
        protocol = findViewById(R.id.ai_provider_protocol);
        baseUrl = findViewById(R.id.ai_provider_base_url);
        customModel = findViewById(R.id.ai_provider_model_custom);
        apiKey = findViewById(R.id.ai_provider_key);
        consent = findViewById(R.id.ai_provider_consent);
        modelSpinner = findViewById(R.id.ai_provider_model);
        status = findViewById(R.id.ai_provider_status);

        name.setText("OpenCode Zen");
        protocol.setText("OPENAI_COMPATIBLE");
        baseUrl.setText("https://opencode.ai/zen/v1");
        findViewById(R.id.ai_provider_save).setOnClickListener(v -> saveProvider());
        loadCatalog();
    }

    private void loadCatalog() {
        MemoryApiClient.get(this).getAiProviderCatalog().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    status.setText("无法加载模型目录，可手动填写模型名");
                    return;
                }
                try {
                    JSONObject root = new JSONObject(response.body().string());
                    JSONArray providers = root.optJSONArray("providers");
                    if (providers != null && providers.length() > 0) {
                        JSONArray providerModels = providers.getJSONObject(0).optJSONArray("models");
                        if (providerModels != null) {
                            for (int i = 0; i < providerModels.length(); i++) {
                                models.add(providerModels.getJSONObject(i).optString("modelCode"));
                            }
                        }
                    }
                    modelSpinner.setAdapter(new ArrayAdapter<>(AiProviderSettingsActivity.this,
                            android.R.layout.simple_spinner_dropdown_item, models));
                } catch (Exception e) {
                    status.setText("模型目录解析失败，可手动填写模型名");
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                status.setText("无法连接模型目录，可手动填写模型名");
            }
        });
    }

    private void saveProvider() {
        String model = customModel.getText().toString().trim();
        if (model.isEmpty() && modelSpinner.getSelectedItem() != null) {
            model = String.valueOf(modelSpinner.getSelectedItem());
        }
        try {
            JSONObject body = new JSONObject();
            body.put("name", name.getText().toString().trim());
            body.put("protocol", protocol.getText().toString().trim());
            body.put("baseUrl", baseUrl.getText().toString().trim());
            body.put("modelCode", model);
            body.put("apiKey", apiKey.getText().toString());
            body.put("privacyConsent", consent.isChecked());
            RequestBody requestBody = RequestBody.create(MediaType.parse("application/json"), body.toString());
            status.setText("正在验证 Provider...");
            MemoryApiClient.get(this).saveMyAiProvider(requestBody).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    try {
                        String result = response.body() == null ? "" : response.body().string();
                        JSONObject json = new JSONObject(result);
                        status.setText(json.optString("message", "保存完成"));
                        if ("200".equals(json.optString("code"))) {
                            Toast.makeText(AiProviderSettingsActivity.this, "Provider 保存成功", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        status.setText("保存响应解析失败");
                    }
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    status.setText("Provider 保存失败：" + t.getMessage());
                }
            });
        } catch (Exception e) {
            status.setText("请填写完整配置");
        }
    }
}
