package com.deepsleep.memory.ui.extra_view.word_search_view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.deepsleep.memory.R;
import com.deepsleep.memory.handle_utils.AudioPlayer;
import com.deepsleep.memory.handle_utils.lexicon.LexiconResourceMap;
import com.deepsleep.memory.handle_utils.lexicon.WordEntry;

public class WordSearchLocalFragment extends Fragment {

    private TextView tvWord, tvPhoneticUS, tvPhoneticUK,tvPhoneticSep, tvMeaning, tvExample;
    private View line1 ,line2;
    private String currentWord;
    private boolean isViewCreated = false;
    private String pendingSearchWord = null;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_word_local_result, container, false);

        tvWord = view.findViewById(R.id.tv_word);
        tvPhoneticUS = view.findViewById(R.id.tv_phonetic_US);
        tvPhoneticUK = view.findViewById(R.id.tv_phonetic_UK);
        tvPhoneticSep = view.findViewById(R.id.tv_phonetic_separator);
        tvMeaning = view.findViewById(R.id.tv_meaning);
        tvExample = view.findViewById(R.id.tv_example);
        line1 = view.findViewById(R.id.line_1);
        line2 = view.findViewById(R.id.line_2);

        line1.setVisibility(View.INVISIBLE);
        line2.setVisibility(View.INVISIBLE);

        isViewCreated = true;

        // 如果有待处理的搜索请求，则执行搜索
        if (pendingSearchWord != null) {
            searchWordLocally(pendingSearchWord);
            pendingSearchWord = null;
        }

        return view;
    }

    public void searchWordLocally(String word) {
        // 如果视图还没有创建，则保存搜索词，稍后在onCreateView中执行搜索
        if (!isViewCreated) {
            pendingSearchWord = word;
            return;
        };

        this.currentWord = word;
        tvWord.setText(String.format("%s", word));
        line1.setVisibility(View.VISIBLE);
        line2.setVisibility(View.VISIBLE);

        WordEntry entry = LexiconResourceMap.findWordInAllLexicons(word);
        if (entry != null) {
            StringBuilder exampleSentence = new StringBuilder();
            int s_count = 0;
            for (WordEntry.ExampleSentence sentence : entry.getExampleSentences()) {
                s_count++;
                exampleSentence.append("例句").append(s_count).append(": \n").append(sentence.getEn()+"\n");
                exampleSentence.append("释义:\n").append(sentence.getCn()+"\n");
            }
            String exampleText = exampleSentence.toString();
            tvPhoneticUS.setText(String.format("%s", "美音:" + entry.getUsPhone()));
            tvPhoneticUS.setOnClickListener(v -> {
                AudioPlayer.playAudio(v.getContext(), entry.getHeadWord(), true);
            });
            tvPhoneticUK.setText(String.format("%s", "英音:"+entry.getUkPhone()));
            tvPhoneticUK.setOnClickListener(v -> {
                AudioPlayer.playAudio(v.getContext(), entry.getHeadWord(), false);
            });
            tvPhoneticSep.setText(" | ");
            tvMeaning.setText(String.format("释义：%s", entry.getChineseTranslation()));
            tvExample.setText(String.format("%s", exampleText));
        } else {
            tvPhoneticUK.setText("未找到该单词在本地词书中");
            tvMeaning.setText("");
            tvExample.setText("");
        }
    }

    public Fragment getFragment() {
        return this;
    }
}
