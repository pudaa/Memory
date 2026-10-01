package com.deepsleep.memory.ui.extra_view.plan_view;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.*;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.deepsleep.memory.ui.MainActivity;
import com.deepsleep.memory.R;
import com.deepsleep.memory.settings.InnerSettingsManager;
import com.deepsleep.memory.sync.CacheKind;
import com.deepsleep.memory.sync.OutboxSync;
import com.deepsleep.memory.sync.WordListCacheStore;
import com.deepsleep.memory.ui.init_view.BookSelectActivity;
import com.deepsleep.memory.network.ApiBridge;
import com.deepsleep.memory.network.MemoryApiClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import static com.deepsleep.memory.handle_utils.lexicon.LexiconResourceMap.loadBooksFromJson;

public class PlanListActivity extends AppCompatActivity {
    private List<JSONObject> allBooks;
    private List<JSONObject> filteredBooks;
    private ImageButton btnBack, btnAdd;
    private ListView planListView;
    private PlanListAdapter planListAdapter;
    int userId;
    /** 当前列表是否由缓存渲染（离线提示用） */
    private boolean renderedFromCache = false;
    static final int msg_success = 1;
    static final int msg_failed = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.plan_list_layout);
        initView();
        allBooks = loadBooksFromJson(this);
        filteredBooks = new ArrayList<>(allBooks);
        // 离线兜底：先渲染上次的计划列表（P3 读缓存），再请求服务端覆盖
        renderPlansFromCache();
        btnBack.setOnClickListener(v -> finish());
        btnAdd.setOnClickListener(v -> {
            Intent intent = new Intent(PlanListActivity.this, BookSelectActivity.class);
            startActivity(intent);
        });
        ApiBridge.enqueue(MemoryApiClient.learning().getUserAllLearningPlans(String.valueOf(userId)), new PlanHandler(),
                msg_success, msg_failed, "AllLearningPlans");
    }

    /**
     * 计划列表属于账号级数据（列表里含全部计划，与"当前计划"无关），
     * 因此缓存作用域的 planId 固定为空串，避免切计划后缓存失效。
     */
    private static final String PLAN_LIST_CACHE_SCOPE = "";

    /** 离线兜底：先渲染上次的计划列表（P3 读缓存） */
    private void renderPlansFromCache() {
        JSONObject blob = WordListCacheStore.loadBlob(this, userId, PLAN_LIST_CACHE_SCOPE, CacheKind.PLAN_LIST);
        if (blob == null) {
            return;
        }
        renderPlans(blob.optJSONArray("plans"), blob.optInt("onPlanId"), true);
    }

    /** 渲染计划列表（缓存与在线响应共用） */
    private void renderPlans(@Nullable JSONArray plans, int onPlanId, boolean fromCache) {
        if (plans == null) {
            return;
        }
        filteredBooks.clear();
        for (int i = 0; i < plans.length(); i++) {
            JSONObject plan = plans.optJSONObject(i);
            if (plan != null) {
                filteredBooks.add(plan);
            }
        }
        renderedFromCache = fromCache;
        planListAdapter = new PlanListAdapter(PlanListActivity.this, filteredBooks, onPlanId);
        planListView.setAdapter(planListAdapter);
        planListView.setOnItemClickListener((parent, view, position, id) -> switchToPlan(position, onPlanId));
    }

    /** 切换 on-plan 计划（离线时给出明确提示，不做本地假定） */
    private void switchToPlan(int position, int onPlanId) {
        JSONObject plan = filteredBooks.get(position);
        int planId = plan.optInt("planId");
        if (planId == onPlanId) {
            return;
        }
        if (!OutboxSync.isOnline(this)) {
            Toast.makeText(this, "离线状态无法切换计划，请联网后重试", Toast.LENGTH_SHORT).show();
            return;
        }
        ApiBridge.enqueue(MemoryApiClient.auth().setPlan(String.valueOf(userId), String.valueOf(planId)),
                new Handler(Looper.getMainLooper()) {
                    @Override
                    public void handleMessage(Message msg) {
                        super.handleMessage(msg);
                        if (msg.what == msg_success) {
                            startMainActivity();
                        } else if (msg.what == msg_failed) {
                            Toast.makeText(PlanListActivity.this, "更新失败", Toast.LENGTH_SHORT).show();
                        }
                        if (planListAdapter != null) {
                            planListAdapter.notifyDataSetChanged();
                        }
                    }
                }, msg_success, msg_failed, "UpdateCurrentPlan");
    }

    private void initView() {
        userId = InnerSettingsManager.getInstance(this).getUserId();
        btnBack = findViewById(R.id.btn_back);
        btnAdd = findViewById(R.id.btn_add);
        planListView = findViewById(R.id.plan_list);
    }

    private void startMainActivity() {
        InnerSettingsManager.getInstance(this).setLoggedIn(2);
        // 跳转到主页
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();

    }

    @SuppressLint("HandlerLeak")
    private class PlanHandler extends Handler {
        PlanHandler() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case msg_success:
                try {
                    String result = (String) msg.obj;
                    JSONObject root = new JSONObject(result);
                    JSONArray responseJson = root.getJSONArray("plans");
                    int onPlanId = root.optInt("onPlanId");
                    // 更新读缓存（整块存原始响应），供离线打开时渲染
                    JSONObject blob = new JSONObject();
                    blob.put("plans", responseJson);
                    blob.put("onPlanId", onPlanId);
                    WordListCacheStore.saveBlob(PlanListActivity.this, userId, PLAN_LIST_CACHE_SCOPE,
                            CacheKind.PLAN_LIST, "", blob);
                    renderPlans(responseJson, onPlanId, false);
                } catch (JSONException e) {
                    Log.e("PlanListActivity", "JSON parsing error", e);
                }
                break;
            case msg_failed:
                // 离线：保留缓存渲染结果并提示数据时间
                if (renderedFromCache) {
                    Toast.makeText(PlanListActivity.this,
                            WordListCacheStore.offlineHint(PlanListActivity.this, userId, PLAN_LIST_CACHE_SCOPE,
                                    CacheKind.PLAN_LIST),
                            Toast.LENGTH_SHORT).show();
                }
                break;
            }
        }
    }

}