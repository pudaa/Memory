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
import com.deepsleep.memory.sync.OutboxEntity;
import com.deepsleep.memory.sync.OutboxKind;
import com.deepsleep.memory.sync.OutboxStore;
import com.deepsleep.memory.sync.WordListCacheStore;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 收藏词列表（P3：读缓存 + 取消失败回滚）。
 *
 * <p>渲染顺序：先读本地缓存（离线也能看到上次清单）→ 再请求服务端覆盖；请求失败时
 * 保留缓存并提示「离线数据 · 更新于 X」。取消收藏走乐观更新 + 出站队列（离线时先本地生效、
 * 联网后自动同步），服务端明确拒绝时回滚。</p>
 */
public class FavoriteWordsFragment extends Fragment {

    private RecyclerView recyclerView;
    private WordListAdapter adapter;
    int userId;
    private String planId = "";
    private String lexiconId = "";
    /** 本次是否由缓存渲染（用于失败时提示离线数据，而不是提示"加载失败"） */
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

    /** 先渲染缓存（有则立即可见，离线可用） */
    private void renderFromCache() {
        JSONArray cached = WordListCacheStore.loadItemsAsJson(requireContext(), userId, planId, CacheKind.FAVORITE);
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
        adapter.setOnItemLongClickListener(word -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle("取消收藏")
                .setMessage("是否取消收藏？")
                .setPositiveButton("确定", (dialog, which) -> unFavoriteWord(word))
                .setNegativeButton("取消", (dialog, which) -> dialog.dismiss())
                .show());
        recyclerView.setAdapter(adapter);
    }

    private void getSampleData() {// 获取收藏单词
        ApiBridge.enqueue(MemoryApiClient.learning().getFavoriteWords(String.valueOf(userId)),
                new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                if (msg.what == msg_success) {
                    try {
                        JSONObject jsonObject = new JSONObject((String) msg.obj);
                        if (!"200".equals(jsonObject.getString("code"))) {
                            return;
                        }
                        JSONArray favoriteWordsArray = jsonObject.optJSONArray("favoriteWords");
                        if (favoriteWordsArray == null || favoriteWordsArray.length() == 0) {
                            // 服务端确认为空：清缓存并清空列表
                            WordListCacheStore.replaceFromJsonArray(requireContext(), userId, planId,
                                    CacheKind.FAVORITE, lexiconId, new JSONArray(), "headWord", "retrievability");
                            renderWords(new ArrayList<>());
                            renderedFromCache = false;
                            return;
                        }
                        // 更新缓存（服务端为权威）后再渲染
                        WordListCacheStore.replaceFromJsonArray(requireContext(), userId, planId, CacheKind.FAVORITE,
                                lexiconId, favoriteWordsArray, "headWord", "retrievability");
                        renderWords(entriesFromJson(favoriteWordsArray));
                        renderedFromCache = false;
                    } catch (Exception e) {
                        Log.e("favoriteWords", "解析收藏列表失败", e);
                    }
                } else {
                    // 网络失败：缓存已渲染则提示离线数据，否则保持空列表
                    Log.i("favoriteWords", "获取失败（离线）");
                    if (renderedFromCache) {
                        Toast.makeText(getContext(),
                                WordListCacheStore.offlineHint(requireContext(), userId, planId, CacheKind.FAVORITE),
                                Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }, msg_success, msg_failed, null);
    }

    /**
     * 取消收藏：乐观更新（UI + 缓存立即生效）→ 入出站队列 → 立即尝试提交。
     *
     * <p>网络失败保留队列（联网后自动同步）；服务端明确拒绝则回滚本地改动。</p>
     */
    private void unFavoriteWord(@NonNull WordEntry wordEntry) {
        String headWord = wordEntry.getHeadWord();
        // 解析「该词在当前计划词书中的 (wordId, lexiconId)」：
        // 服务端的收藏是按"用户当前词书"分桶的，若用跨词书取到的条目（findWordInAllLexicons
        // 会返回任意一本）去取消收藏，服务端可能命中不到那一行 —— 先用当前词书查，缺失再回退。
        String wordLexiconId = wordEntry.getBookId();
        int wordId = wordEntry.getWordRank();
        if (lexiconId != null && !lexiconId.isEmpty()) {
            WordEntry inPlanBook = LexiconResourceMap.findWordInBook(headWord, lexiconId);
            if (inPlanBook != null) {
                wordLexiconId = inPlanBook.getBookId();
                wordId = inPlanBook.getWordRank();
            }
        }

        // 乐观更新
        if (adapter != null) {
            adapter.removeItem(headWord);
        }
        WordListCacheStore.removeItem(requireContext(), userId, planId, CacheKind.FAVORITE, headWord);

        JSONObject payload = new JSONObject();
        long outboxId = -1;
        try {
            payload.put("userId", userId);
            payload.put("wordId", wordId);
            payload.put("lexiconId", wordLexiconId);
            payload.put("headWord", headWord);
            payload.put("isFavorite", "false");
            OutboxEntity entry = new OutboxEntity();
            entry.kind = OutboxKind.SET_FAVORITE;
            entry.userId = userId;
            entry.planId = planId;
            entry.lexiconId = wordLexiconId;
            entry.payloadJson = payload.toString();
            entry.answeredAtEpochMs = System.currentTimeMillis();
            outboxId = OutboxStore.enqueue(requireContext(), entry);
        } catch (Exception e) {
            Log.w("favoriteWords", "取消收藏入队失败，仅本地生效", e);
        }

        final long entryId = outboxId;
        // 快速失败路径：改动已入出站队列并落了本地缓存，前台只重试 1 次（连接超时 5s）
        ApiBridge.enqueue(MemoryApiClient.learningFastFail().setFavorite(String.valueOf(userId),
                String.valueOf(wordId), wordLexiconId, headWord, String.valueOf(false)),
                new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                if (msg.what == msg_success) {
                    String code = "";
                    try {
                        code = new JSONObject((String) msg.obj).optString("code", "");
                    } catch (Exception ignored) {
                    }
                    if ("200".equals(code)) {
                        // 服务端确认：出队
                        if (entryId > 0) {
                            OutboxStore.delete(requireContext(), entryId);
                        }
                        return;
                    }
                    // 业务失败：出队 + 回滚本地改动（避免队列里留下永远失败的记录）
                    if (entryId > 0) {
                        OutboxStore.delete(requireContext(), entryId);
                    }
                    rollbackUnFavorite(wordEntry, payload);
                } else {
                    // 网络失败：保留队列，联网后自动同步
                    Toast.makeText(getContext(), "已离线保存，联网后自动同步", Toast.LENGTH_SHORT).show();
                }
            }
        }, msg_success, msg_failed, "UpdateFavorite", 1);
    }

    /** 回滚乐观更新（服务端拒绝时） */
    private void rollbackUnFavorite(@NonNull WordEntry wordEntry, @NonNull JSONObject payload) {
        Log.w("favoriteWords", "取消收藏被服务端拒绝，回滚本地状态: " + wordEntry.getHeadWord());
        Toast.makeText(getContext(), "取消收藏失败，已恢复", Toast.LENGTH_SHORT).show();
        renderedFromCache = true;
        JSONArray cached = WordListCacheStore.loadItemsAsJson(requireContext(), userId, planId, CacheKind.FAVORITE);
        List<WordEntry> list = entriesFromJson(cached);
        boolean present = false;
        for (WordEntry entry : list) {
            if (entry.getHeadWord().equals(wordEntry.getHeadWord())) {
                present = true;
                break;
            }
        }
        if (!present) {
            list.add(wordEntry);
        }
        renderWords(list);
        // 缓存回写
        WordListCacheStore.upsertItem(requireContext(), userId, planId, CacheKind.FAVORITE,
                wordEntry.getBookId(), payload, "headWord", 0);
    }

}
