package com.deepsleep.memory.ui.extra_view.plan_view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.deepsleep.memory.ui.MainActivity;
import com.deepsleep.memory.R;
import com.deepsleep.memory.ui.init_view.BookSelectActivity;
import com.deepsleep.memory.network.GetDataByThread;
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
    private static final String PREF_NAME = "UserPrefs";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_USER_ID = "userId";
    int userId;
    static final  int msg_success = 1;
    static final  int msg_failed = -1;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.plan_list_layout);
        initView();
        allBooks = loadBooksFromJson(this);
        filteredBooks = new ArrayList<>(allBooks);
        btnBack.setOnClickListener(v -> finish());
        btnAdd.setOnClickListener(v -> {
            Intent intent = new Intent(PlanListActivity.this, BookSelectActivity.class);
            startActivity(intent);
        });
        GetDataByThread getDataByThread = new GetDataByThread("/learning/getUserAllLearningPlans");
        getDataByThread.getPlanDetails(new PlanHandler(), msg_success, msg_failed, userId);
    }
    private void initView() {
        SharedPreferences sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        userId = sharedPreferences.getInt(KEY_USER_ID, 0);
        btnBack = findViewById(R.id.btn_back);
        btnAdd = findViewById(R.id.btn_add);
        planListView = findViewById(R.id.plan_list);
    }

    private void startMainActivity() {
        SharedPreferences sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(KEY_IS_LOGGED_IN, 2);
        editor.apply();
        //  跳转到主页
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();

    }

    @SuppressLint("HandlerLeak")
    private class PlanHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case msg_success:
                    try {
                        String result = (String) msg.obj;
                        JSONArray responseJson = new JSONObject(result).getJSONArray("plans");
                        int onPlanId = new JSONObject(result).optInt("onPlanId");
                        filteredBooks.clear();
                        for (int i = 0; i < responseJson.length(); i++) {
                            JSONObject plan = responseJson.getJSONObject(i);
                            filteredBooks.add(plan);
                        }
                        planListAdapter = new PlanListAdapter(PlanListActivity.this, filteredBooks, onPlanId);
                        planListView.setAdapter(planListAdapter);
                        planListView.setOnItemClickListener((parent, view, position, id) -> {
                            JSONObject plan = filteredBooks.get(position);
                            int planId = plan.optInt("planId");
                            if(planId != onPlanId){
                                GetDataByThread getDataByThread = new GetDataByThread("/auth/setPlan");
                                getDataByThread.updateCurrentPlan(new Handler(Looper.getMainLooper()){
                                    @Override
                                    public void handleMessage(Message msg) {
                                        super.handleMessage(msg);
                                        if(msg.what == msg_success){
                                            startMainActivity();
                                        }else if(msg.what == msg_failed){
                                            Toast.makeText(PlanListActivity.this, "更新失败", Toast.LENGTH_SHORT).show();
                                        }
                                        planListAdapter.notifyDataSetChanged();
                                    }
                                }, msg_success, msg_failed, userId, planId);
                            }
                        });
                    } catch (JSONException e) {
                        Log.e("PlanListActivity", "JSON parsing error", e);
                    }
                    break;
                case msg_failed:
                    break;
            }
        }
    }

}