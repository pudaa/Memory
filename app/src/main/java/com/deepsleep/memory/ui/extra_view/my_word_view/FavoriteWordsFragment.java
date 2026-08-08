package com.deepsleep.memory.ui.extra_view.my_word_view;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.deepsleep.memory.R;
import com.deepsleep.memory.handle_utils.lexicon.LexiconResourceMap;
import com.deepsleep.memory.handle_utils.lexicon.WordEntry;
import com.deepsleep.memory.network.GetDataByThread;
import com.deepsleep.memory.settings.InnerSettingsManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class FavoriteWordsFragment extends Fragment {

    private RecyclerView recyclerView;
    private WordListAdapter adapter;
    int userId;
    // 线程处理
    static final int msg_success = 1;
    static final int msg_failed = -1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_word_list, container, false);
        recyclerView = view.findViewById(R.id.word_list_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        userId = InnerSettingsManager.getInstance(requireContext()).getUserId();

        getSampleData();
        return view;
    }

    GetDataByThread getDataByThread = new GetDataByThread("/learning/getFavoriteWords");

    private void getSampleData() {// 获取收藏单词
        getDataByThread.fetchFavoriteWords(new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                if (msg.what == msg_success) {
                    List<WordEntry> list = new ArrayList<>();
                    String FavWords = (String) msg.obj;
                    try {
                        JSONObject jsonObject = new JSONObject(FavWords);
                        if (jsonObject.getString("code").equals("200")) {
                            List<String> favWordList = new ArrayList<>();
                            StringBuilder favWordsStr = new StringBuilder();
                            try {
                                JSONArray favoriteWordsArray = jsonObject.getJSONArray("favoriteWords");
                                if (favoriteWordsArray.length() == 0) {
                                    // 没有收藏单词
                                    return;
                                }

                                for (int i = 0; i < favoriteWordsArray.length(); i++) {
                                    JSONObject wordObject = favoriteWordsArray.getJSONObject(i);
                                    String headWord = wordObject.getString("headWord");
                                    favWordList.add(headWord);
                                    favWordsStr.append(headWord).append(", ");
                                    list.add(LexiconResourceMap.findWordInAllLexicons(headWord));
                                }

                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            Log.i("weakWords", favWordsStr.toString());
                            adapter = new WordListAdapter(list);
                            adapter.setOnItemLongClickListener(word -> {
                                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
                                builder.setTitle("取消收藏");
                                builder.setMessage("是否取消收藏？");

                                builder.setPositiveButton("确定", (dialog, which) -> {
                                    unFavoriteWord(word.getHeadWord());
                                });

                                builder.setNegativeButton("取消", (dialog, which) -> dialog.dismiss());

                                builder.show();
                            });
                            recyclerView.setAdapter(adapter);

                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                } else {
                    Log.i("weakWords", "获取失败");
                }
            }
        }, msg_success, msg_failed, String.valueOf(userId));
    }

    private void unFavoriteWord(String headWord) {
        WordEntry wordEntry = LexiconResourceMap.findWordInAllLexicons(headWord);
        if (wordEntry == null) {
            Log.e("unFavoriteWord", "无法找到单词: " + headWord);
            return;
        }

        // 从WordEntry获取单词ID（wordRank）和词书ID（bookId）
        int wordId = wordEntry.getWordRank();
        String lexiconId = wordEntry.getBookId();
        GetDataByThread thread = new GetDataByThread("/learning/setFavorite");
        thread.updateFavorite(new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                if (msg.what == msg_success) {
                    adapter.removeItem(headWord);
                } else {
                    Log.e("unFavoriteWord", "取消收藏失败");
                }
            }
        }, msg_success, msg_failed, String.valueOf(userId), wordId, lexiconId, headWord, false);
    }

}
