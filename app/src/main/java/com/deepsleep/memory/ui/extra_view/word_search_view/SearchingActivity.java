package com.deepsleep.memory.ui.extra_view.word_search_view;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;
import com.deepsleep.memory.R;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class SearchingActivity extends AppCompatActivity {

    private EditText etSearchWord;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.searching_word_layout);

        etSearchWord = findViewById(R.id.et_search_word);
        TabLayout tabLayout = findViewById(R.id.tab_layout);
        ViewPager2 viewPager = findViewById(R.id.view_pager);

        // 设置适配器
        WordSearchPagerAdapter adapter = new WordSearchPagerAdapter(this);
        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> {
                    switch (position) {
                        case 0: tab.setText("本地词书"); break;
                        case 1: tab.setText("必应词典"); break;
                        case 2: tab.setText("牛津词典"); break;
                        case 3: tab.setText("剑桥词典"); break;
                    }
                }).attach();


        findViewById(R.id.btn_cancel).setOnClickListener(v -> finish());


        etSearchWord.setOnEditorActionListener((v, actionId, event) -> { // 监听搜索按钮点击事件
            if (actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String word = etSearchWord.getText().toString().trim();
                if (!TextUtils.isEmpty(word)) {
                    ((WordSearchLocalFragment) adapter.getFragment(0)).searchWordLocally(word);
                    ((WordSearchWebFragment) adapter.getFragment(1)).searchWordOnline(word);
                    ((WordSearchOxfordFragment) adapter.getFragment(2)).searchWordOnline(word);
                    ((WordSearchCambridgeFragment) adapter.getFragment(3)).searchWordOnline(word);
                    hideKeyboard();
                }
                return true;
            }
            return false;
        });

        // 检查intent中是否有传入的单词
        String searchWord = getIntent().getStringExtra("search_word");
        if (!TextUtils.isEmpty(searchWord)) {
            etSearchWord.setText(searchWord);
            performSearch(searchWord, adapter);
            hideKeyboard();
        }
    }

    private void performSearch(String word, WordSearchPagerAdapter adapter) {
        ((WordSearchLocalFragment) adapter.getFragment(0)).searchWordLocally(word);
        ((WordSearchWebFragment) adapter.getFragment(1)).searchWordOnline(word);
        ((WordSearchOxfordFragment) adapter.getFragment(2)).searchWordOnline(word);
        ((WordSearchCambridgeFragment) adapter.getFragment(3)).searchWordOnline(word);
        hideKeyboard();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }
}
