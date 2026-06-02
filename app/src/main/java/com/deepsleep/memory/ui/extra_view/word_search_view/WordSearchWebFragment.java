package com.deepsleep.memory.ui.extra_view.word_search_view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.deepsleep.memory.R;

public class WordSearchWebFragment extends Fragment {

    private WebView webView;
    private String currentWord;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_word_web_result, container, false);

        webView = view.findViewById(R.id.web_view);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        webView.setWebViewClient(new WebViewClient());
        // 如果已经有单词传入，立即搜索
        if (currentWord != null && !currentWord.isEmpty()) {
            searchWordOnline(currentWord);
        }
        return view;
    }

    public void searchWordOnline(String word) {
        this.currentWord = word;
        if (webView != null) {
            String url = "https://www.bing.com/dict/search?q=" + word + "&filters=sitecontent%3A%22Dictionary%22";
            webView.loadUrl(url);
        }
    }

    public Fragment getFragment() {
        return this;
    }
}
