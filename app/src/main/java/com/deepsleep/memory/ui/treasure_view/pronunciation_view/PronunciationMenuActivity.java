package com.deepsleep.memory.ui.treasure_view.pronunciation_view;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;

import com.deepsleep.memory.R;
import com.deepsleep.memory.ui.treasure_view.aichat_view.AiConversationActivity;
import com.google.android.material.card.MaterialCardView;

public class PronunciationMenuActivity extends AppCompatActivity {

    // 默认词书ID（kaoyan_3）
    private static final int DEFAULT_WORD_BOOK_ID = 2;
    private static final int DEFAULT_PHRASE_COUNT = 7;
    private static final int DEFAULT_SENTENCE_COUNT = 7;

    private MaterialCardView cardDailyMinute, cardAiChat;
    private ImageButton backButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pronunciation_menu_layout);

        backButton = findViewById(R.id.btn_back);
        backButton.setOnClickListener(v -> finish());

        cardDailyMinute = findViewById(R.id.card_daily_minute);
        cardAiChat = findViewById(R.id.card_ai_chat);

        cardDailyMinute.setOnClickListener(v -> {
            Intent intent = new Intent(PronunciationMenuActivity.this, PronunciationMinuteFollowActivity.class);
            intent.putExtra("topicName", "每日一分钟");
            intent.putExtra("wordBookId", DEFAULT_WORD_BOOK_ID);
            intent.putExtra("phraseCount", DEFAULT_PHRASE_COUNT);
            intent.putExtra("sentenceCount", DEFAULT_SENTENCE_COUNT);
            intent.putExtra("hasIntro", 0);
            startActivity(intent);
        });

        cardAiChat.setOnClickListener(v -> {
            Intent intent = new Intent(PronunciationMenuActivity.this, AiConversationActivity.class);
            startActivity(intent);
        });
    }
}