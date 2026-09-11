package com.deepsleep.memory.ui.extra_view.my_word_view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

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
    private View progressBar;
    private TextView tvLexiconTitle, tvWordCount, tvOfflineHint;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_lexicon_browse, container, false);

        recyclerView = view.findViewById(R.id.recycler_view);
        progressBar = view.findViewById(R.id.progress_bar);
        tvLexiconTitle = view.findViewById(R.id.tv_lexicon_title);
        tvWordCount = view.findViewById(R.id.tv_word_count);
        tvOfflineHint = view.findViewById(R.id.tv_offline_hint);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        // 信息条点击 → 自由切换浏览的词书（在线 / 离线均可用）
        View infoBar = view.findViewById(R.id.lexicon_info_bar);
        infoBar.setOnClickListener(v -> openBookPicker());
        loadLexiconData();

        return view;
    }

    /**
     * 加载词书数据（三级回退，词书内容与本文逻辑均不依赖网络）：
     * ① 本次会话已加载的词书；
     * ② 持久化缓存（App 重启 / 断网时恢复）；
     * ③ 本地词书表兜底——仅一本直接采用，多本交用户选择。
     */
    @SuppressWarnings("unchecked")
    private void loadLexiconData() {
        progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            String lexiconId = LexiconResourceMap.getLoadedLexiconName();
            boolean offline = false;

            // ② 离线恢复：从持久化缓存取当前词书并在本地库校验
            if (lexiconId == null) {
                String cached = LexiconResourceMap.restoreCachedLexicon(requireContext());
                if (!cached.isEmpty()) {
                    lexiconId = cached;
                    offline = true;
                }
            }

            // ③ 仍无词书：用本地词书表兜底
            if (lexiconId == null) {
                List<JSONObject> localBooks = LexiconResourceMap.loadBooksFromJson(requireContext());
                if (localBooks.size() == 1) {
                    String only = localBooks.get(0).optString("id", "");
                    if (!only.isEmpty()) {
                        LexiconResourceMap.loadLexicon(requireContext(), only);
                        lexiconId = only;
                        offline = true;
                    }
                } else if (localBooks.size() > 1) {
                    showBookPicker(localBooks, true);
                    return;
                }
            }

            if (lexiconId == null || lexiconId.isEmpty()) {
                requireActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvLexiconTitle.setText("本地暂无词书");
                });
                return;
            }

            renderLexicon(lexiconId, offline);
        }).start();
    }

    /** 渲染词书内容（后台线程调用） */
    private void renderLexicon(String lexiconId, boolean offline) {
        List<JSONObject> bookList = LexiconResourceMap.loadBooksFromJson(requireContext());
        String title = LexiconResourceMap.getLexiconName(lexiconId, bookList);

        List<WordEntry> entries = LexiconResourceMap.getAllEntries(lexiconId);
        if (entries == null) {
            entries = new ArrayList<>();
        }

        List<WordEntry> finalEntries = entries;
        String finalTitle = title;
        requireActivity().runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            tvLexiconTitle.setText(finalTitle);
            tvWordCount.setText(finalEntries.size() + "词");
            if (tvOfflineHint != null) {
                tvOfflineHint.setVisibility(offline ? View.VISIBLE : View.GONE);
            }
            recyclerView.setAdapter(new LexiconBrowseAdapter(finalEntries));
        });
    }

    /** 手动切换浏览的词书（在线 / 离线均可用；不改变离线默认词书） */
    private void openBookPicker() {
        new Thread(() -> {
            List<JSONObject> books = LexiconResourceMap.loadBooksFromJson(requireContext());
            if (books.isEmpty()) {
                requireActivity().runOnUiThread(() -> Toast
                        .makeText(requireContext(), "本地暂无词书", Toast.LENGTH_SHORT).show());
                return;
            }
            showBookPicker(books, false);
        }).start();
    }

    /**
     * 弹出词书选择底部弹窗（拖拽指示条 + 标题 + 首字色块卡片列表 + 当前词书标记）。
     *
     * @param persistAsOfflineDefault true = 作为离线默认词书落盘（离线兜底场景）；
     *                                false = 仅切换本次浏览（用户自由切换场景）
     */
    private void showBookPicker(List<JSONObject> books, boolean persistAsOfflineDefault) {
        requireActivity().runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);

            com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                    new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());
            View content = LayoutInflater.from(requireContext())
                    .inflate(R.layout.dialog_lexicon_picker, null);
            sheet.setContentView(content);

            TextView subtitle = content.findViewById(R.id.tv_picker_subtitle);
            subtitle.setText("共 " + books.size() + " 本 · 本地词库，离线可浏览");

            String currentId = LexiconResourceMap.getLoadedLexiconName();
            LinearLayout list = content.findViewById(R.id.lexicon_list);

            for (JSONObject book : books) {
                String id = book.optString("id", "");
                String title = book.optString("title", id);
                int count = book.optInt("wordNum", 0);

                View row = LayoutInflater.from(requireContext())
                        .inflate(R.layout.item_lexicon_pick, list, false);

                TextView initial = row.findViewById(R.id.tv_initial);
                initial.setText(title.isEmpty() ? "词" : title.substring(0, 1));
                ((TextView) row.findViewById(R.id.tv_book_title)).setText(title);
                ((TextView) row.findViewById(R.id.tv_book_count)).setText(count + " 词");
                row.findViewById(R.id.tv_current_tag)
                        .setVisibility(id.equals(currentId) ? View.VISIBLE : View.GONE);

                row.setOnClickListener(v -> {
                    sheet.dismiss();
                    progressBar.setVisibility(View.VISIBLE);
                    new Thread(() -> {
                        if (persistAsOfflineDefault) {
                            // 离线兜底：记为离线默认词书（落盘）
                            LexiconResourceMap.loadLexicon(requireContext(), id);
                            renderLexicon(id, true);
                        } else {
                            // 自由切换：仅影响本次会话浏览，不写缓存、不显示离线提示
                            LexiconResourceMap.switchLexiconForBrowsing(requireContext(), id);
                            renderLexicon(id, false);
                        }
                    }).start();
                });
                list.addView(row);
            }
            sheet.show();
        });
    }
}
