package com.deepsleep.memory.ui.extra_view.my_word_view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.deepsleep.memory.R;
import com.deepsleep.memory.handle_utils.lexicon.LexiconResourceMap;
import com.deepsleep.memory.handle_utils.lexicon.WordEntry;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 当前词书浏览页 —— 按序展示计划词书中每个单词的完整信息
 */
public class LexiconBrowseFragment extends Fragment {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvLexiconTitle, tvWordCount;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_lexicon_browse, container, false);

        recyclerView = view.findViewById(R.id.recycler_view);
        progressBar = view.findViewById(R.id.progress_bar);
        tvLexiconTitle = view.findViewById(R.id.tv_lexicon_title);
        tvWordCount = view.findViewById(R.id.tv_word_count);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        loadLexiconData();

        return view;
    }

    @SuppressWarnings("unchecked")
    private void loadLexiconData() {
        progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            String lexiconId = LexiconResourceMap.getLoadedLexiconName();
            if (lexiconId == null) {
                requireActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvLexiconTitle.setText("未选择词书");
                });
                return;
            }

            List<JSONObject> bookList = LexiconResourceMap.loadBooksFromJson(requireContext());
            String title = LexiconResourceMap.getLexiconName(lexiconId, bookList);

            List<WordEntry> entries = LexiconResourceMap.getAllEntries(lexiconId);
            if (entries == null)
                entries = new ArrayList<>();

            List<WordEntry> finalEntries = entries;
            String finalTitle = title;
            requireActivity().runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                tvLexiconTitle.setText(finalTitle);
                tvWordCount.setText(finalEntries.size() + "词");
                recyclerView.setAdapter(new LexiconBrowseAdapter(finalEntries));
            });
        }).start();
    }
}
