package com.deepsleep.memory.ui.extra_view.my_word_view;

import android.content.Context;
import android.content.SharedPreferences;
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
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class WeakWordsFragment extends Fragment {

    private RecyclerView recyclerView;
    private WordListAdapter adapter;
    private static final String PREF_NAME = "UserPrefs";
    private static final String KEY_USER_ID = "userId";
    int userId;
    // 线程处理
    static final  int msg_success = 1;
    static final  int msg_failed = -1;
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_word_list, container, false);
        recyclerView = view.findViewById(R.id.word_list_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        SharedPreferences sharedPreferences = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        userId = sharedPreferences.getInt(KEY_USER_ID, 0);
        getSampleData();
        return view;
    }

    private void getSampleData() {
        GetDataByThread getDataByThread = new GetDataByThread("/learning/getWeakWords");
        getDataByThread.fetchWeakWords(new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                if (msg.what == msg_success) {
                    List<WordEntry> list = new ArrayList<>();
                    String weakWords = (String) msg.obj;
                    // weakWords的值为：{"code":"200","weakWords":[{"headWord":"care","masteryLevel":0},{"headWord":"consider","masteryLevel":0}]}
                    try {
                        JSONObject jsonObject = new JSONObject(weakWords);
                        if (jsonObject.getString("code").equals("200")) {
                            List<String> weakWordList = new ArrayList<>();
                            StringBuilder weakWordsStr = new StringBuilder();
                            try {
                                JSONArray weakWordsArray = jsonObject.getJSONArray("weakWords");
                                if (weakWordsArray.length() == 0) {
                                    //  没有薄弱词
                                    return;
                                }

                                for (int i = 0; i < weakWordsArray.length(); i++) {
                                    JSONObject wordObject = weakWordsArray.getJSONObject(i);
                                    String headWord = wordObject.getString("headWord");
                                    weakWordList.add(headWord);
                                    weakWordsStr.append(headWord).append(", ");
                                    list.add(LexiconResourceMap.findWordInAllLexicons(headWord));
                                }

                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            adapter = new WordListAdapter(list);
                            recyclerView.setAdapter(adapter);
                        }
                    }catch (Exception e){
                        e.printStackTrace();
                    }

                }else {
                    Log.i("weakWords", "获取失败");
                }
            }
        },msg_success,msg_failed,String.valueOf(userId));
    }
}
