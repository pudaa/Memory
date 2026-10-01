package com.deepsleep.memory.ui.extra_view.my_word_view;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.deepsleep.memory.R;
import com.deepsleep.memory.handle_utils.lexicon.LexiconResourceMap;
import com.deepsleep.memory.handle_utils.lexicon.WordEntry;
import com.deepsleep.memory.network.ApiBridge;
import com.deepsleep.memory.network.MemoryApiClient;
import com.deepsleep.memory.settings.InnerSettingsManager;
import com.deepsleep.memory.sync.CacheKind;
import com.deepsleep.memory.sync.WordListCacheStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 薄弱词列表（P3：读缓存）。
 *
 * <p>先渲染本地缓存（离线可用），再请求服务端覆盖；请求失败保留缓存并提示
 * 「离线数据 · 更新于 X」。</p>
 */
public class WeakWordsFragment extends Fragment {

    private RecyclerView recyclerView;
    private WordListAdapter adapter;
    int userId;
    private String planId = "";
    private String lexiconId = "";
    private boolean renderedFromCache = false;

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
        planId = WordListCacheStore.currentPlanId(requireContext(), userId);
        lexiconId = InnerSettingsManager.getInstance(requireContext()).getCurrentLexiconId(userId);

        renderFromCache();
        getSampleData();
        return view;
    }

    private void renderFromCache() {
        JSONArray cached = WordListCacheStore.loadItemsAsJson(requireContext(), userId, planId, CacheKind.WEAK);
        if (cached.length() == 0) {
            return;
        }
        renderedFromCache = true;
        renderWords(entriesFromJson(cached));
    }

    private List<WordEntry> entriesFromJson(@NonNull JSONArray array) {
        List<WordEntry> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject wordObject = array.optJSONObject(i);
            if (wordObject == null) {
                continue;
            }
            String headWord = wordObject.optString("headWord", "");
            if (headWord.isEmpty()) {
                continue;
            }
            WordEntry entry = LexiconResourceMap.findWordInAllLexicons(headWord);
            if (entry != null) {
                list.add(entry);
            }
        }
        return list;
    }

    private void renderWords(@NonNull List<WordEntry> list) {
        adapter = new WordListAdapter(list);
        recyclerView.setAdapter(adapter);
    }

    private void getSampleData() {
        ApiBridge.enqueue(MemoryApiClient.learning().getWeakWords(String.valueOf(userId)),
                new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                if (msg.what == msg_success) {
                    try {
                        JSONObject jsonObject = new JSONObject((String) msg.obj);
                        if (!"200".equals(jsonObject.getString("code"))) {
                            return;
                        }
                        JSONArray weakWordsArray = jsonObject.optJSONArray("weakWords");
                        if (weakWordsArray == null || weakWordsArray.length() == 0) {
                            WordListCacheStore.replaceFromJsonArray(requireContext(), userId, planId, CacheKind.WEAK,
                                    lexiconId, new JSONArray(), "headWord", "retrievability");
                            renderWords(new ArrayList<>());
                            renderedFromCache = false;
                            return;
                        }
                        WordListCacheStore.replaceFromJsonArray(requireContext(), userId, planId, CacheKind.WEAK,
                                lexiconId, weakWordsArray, "headWord", "retrievability");
                        renderWords(entriesFromJson(weakWordsArray));
                        renderedFromCache = false;
                    } catch (Exception e) {
                        Log.e("weakWords", "解析薄弱词列表失败", e);
                    }
                } else {
                    Log.i("weakWords", "获取失败（离线）");
                    if (renderedFromCache) {
                        Toast.makeText(getContext(),
                                WordListCacheStore.offlineHint(requireContext(), userId, planId, CacheKind.WEAK),
                                Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }, msg_success, msg_failed, null);
    }
}
