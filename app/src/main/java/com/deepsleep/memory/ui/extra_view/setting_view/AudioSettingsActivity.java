package com.deepsleep.memory.ui.extra_view.setting_view;

import android.os.Bundle;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.deepsleep.memory.R;
import com.deepsleep.memory.settings.UserSettingsManager;

/**
 * 朗读与音频设置页。
 *
 * 目前只有一项：AI 回复是否**自动朗读**。
 * 之所以单独成页而不是塞进"AI 服务与模型"，是因为：
 *  - 那一页是 Provider/Key/模型路由配置，语义不同；
 *  - 音频相关设置后续还有扩展空间（语速、音量、自动朗读范围等）。
 */
public class AudioSettingsActivity extends AppCompatActivity {

    private UserSettingsManager settings;
    private Switch switchAutoPlay;
    private TextView tvDesc;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.audio_settings_layout);

        settings = UserSettingsManager.getInstance(this);

        findViewById(R.id.audio_settings_back).setOnClickListener(v -> finish());
        switchAutoPlay = findViewById(R.id.switch_auto_play_audio);
        tvDesc = findViewById(R.id.tv_auto_play_desc);

        boolean auto = settings.isAiAutoPlayAudioEnabled();
        switchAutoPlay.setChecked(auto);
        renderDesc(auto);

        switchAutoPlay.setOnCheckedChangeListener((btn, checked) -> {
            settings.setAiAutoPlayAudioEnabled(checked);
            renderDesc(checked);
        });
    }

    /** 副标题随开关状态变化，让用户一眼知道当前行为 */
    private void renderDesc(boolean auto) {
        tvDesc.setText(auto
                ? "AI 回复到达后自动开始朗读"
                : "点击每条回复下方的朗读按钮才播放");
    }
}
