package com.deepsleep.memory.ui.treasure_view.dictation_view;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.deepsleep.memory.R;
import com.deepsleep.memory.settings.InnerSettingsManager;

import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 听写练习 - 菜单入口页
 */
public class DictationMenuActivity extends AppCompatActivity {

    private static final int DEFAULT_COUNT = 15;
    private static final int DEFAULT_LEXICON_ID = 2;

    private ImageButton btnBack;
    private LinearLayout btnGenerate;
    private RecyclerView historyRecyclerView;
    private TextView tvNoHistory;
    private HistoryAdapter historyAdapter;

    private int userId;
    private android.os.Handler handler;

    // Handler 消息常量
    private static final int MSG_HISTORY_SUCCESS = 1;
    private static final int MSG_HISTORY_FAILED = 2;
    private static final int MSG_DELETE_SUCCESS = 3;
    private static final int MSG_DELETE_FAILED = 4;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dictation_menu_layout);

        userId = InnerSettingsManager.getInstance(this).getUserId();

        initViews();
        loadHistory();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        btnGenerate = findViewById(R.id.btn_generate);
        btnGenerate.setOnClickListener(v -> startGenerate());

        historyRecyclerView = findViewById(R.id.history_recycler);
        tvNoHistory = findViewById(R.id.tv_no_history);

        historyAdapter = new HistoryAdapter();
        historyRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        historyRecyclerView.setAdapter(historyAdapter);
    }

    private void startGenerate() {
        Intent intent = new Intent(this, DictationGenerateActivity.class);
        intent.putExtra("count", DEFAULT_COUNT);
        intent.putExtra("lexiconId", DEFAULT_LEXICON_ID);
        startActivity(intent);
    }

    private void loadHistory() {
        tvNoHistory.setVisibility(View.GONE);
        historyRecyclerView.setVisibility(View.GONE);

        if (handler == null) {
            handler = new android.os.Handler(getMainLooper()) {
                @Override
                public void handleMessage(android.os.Message msg) {
                    if (msg.what == MSG_HISTORY_SUCCESS) {
                        String result = (String) msg.obj;
                        parseHistoryResult(result);
                    } else if (msg.what == MSG_HISTORY_FAILED) {
                        tvNoHistory.setText("加载历史记录失败");
                        tvNoHistory.setVisibility(View.VISIBLE);
                    } else if (msg.what == MSG_DELETE_SUCCESS) {
                        String result = (String) msg.obj;
                        handleDeleteResult(result);
                    } else if (msg.what == MSG_DELETE_FAILED) {
                        Toast.makeText(DictationMenuActivity.this, "删除失败，请重试", Toast.LENGTH_SHORT).show();
                    }
                }
            };

        }

        DictationApiHelper.getHistory(handler, MSG_HISTORY_SUCCESS, MSG_HISTORY_FAILED, userId, 1, 20);
    }

    private void parseHistoryResult(String result) {
        try {
            JSONObject json = new JSONObject(result);
            if (json.optString("code").equals("200")) {
                JSONObject data = json.optJSONObject("data");
                if (data != null) {
                    DictationModels.DictationHistoryResult historyResult = DictationModels.DictationHistoryResult
                            .fromJson(data);
                    if (historyResult.list.isEmpty()) {
                        tvNoHistory.setText("暂无听写记录，开始第一次听写吧！");
                        tvNoHistory.setVisibility(View.VISIBLE);
                    } else {
                        historyAdapter.setData(historyResult.list);
                        historyRecyclerView.setVisibility(View.VISIBLE);
                    }
                }
            } else {
                tvNoHistory.setText(json.optString("message", "加载失败"));
                tvNoHistory.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            e.printStackTrace();
            tvNoHistory.setText("数据解析失败");
            tvNoHistory.setVisibility(View.VISIBLE);
        }
    }

    private void handleDeleteResult(String result) {
        try {
            JSONObject json = new JSONObject(result);
            if (json.optString("code").equals("200")) {
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                // 重新加载列表
                loadHistory();
            } else {
                Toast.makeText(this, json.optString("message", "删除失败"), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHistory();
    }

    // --- 历史记录适配器 ---
    private class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

        private List<DictationModels.DictationHistoryItem> data = new ArrayList<>();

        void setData(List<DictationModels.DictationHistoryItem> data) {
            this.data = data;
            notifyDataSetChanged();
        }

        @Override
        public ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.item_dictation_history, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            DictationModels.DictationHistoryItem item = data.get(position);
            holder.tvDate.setText(formatDate(item.createdAt));
            holder.tvStatus.setText(getStatusText(item));
            holder.tvWords.setText(item.correctCount + "/" + item.totalWords);

            // 根据状态调整显示
            if ("SUBMITTED".equals(item.status)) {
                holder.tvAccuracy.setText(item.accuracy + "%");
                holder.tvAccuracy.setVisibility(View.VISIBLE);
                if (item.accuracy >= 80) {
                    holder.tvAccuracy.setTextColor(0xFF4CAF50);
                } else if (item.accuracy >= 60) {
                    holder.tvAccuracy.setTextColor(0xFFFF9800);
                } else {
                    holder.tvAccuracy.setTextColor(0xFFF44336);
                }
            } else {
                holder.tvAccuracy.setVisibility(View.GONE);
                holder.tvWords.setText("共 " + item.totalWords + " 词");
            }

            // 点击历史项：PENDING/READY → 继续任务；SUBMITTED → 提示已完成
            holder.itemView.setOnClickListener(v -> {
                if ("SUBMITTED".equals(item.status)) {
                    Toast.makeText(DictationMenuActivity.this, "该听写已完成", Toast.LENGTH_SHORT).show();
                } else {
                    Intent intent = new Intent(DictationMenuActivity.this, DictationGenerateActivity.class);
                    intent.putExtra("taskId", item.taskId);
                    intent.putExtra("count", item.totalWords);
                    intent.putExtra("lexiconId", item.lexiconId);
                    startActivity(intent);
                }
            });

            // 删除按钮：所有任务均可删除
            holder.btnDelete.setVisibility(View.VISIBLE);
            holder.btnDelete.setOnClickListener(v -> {
                String confirmMsg = "SUBMITTED".equals(item.status) ? "该听写已完成，确定要删除这条记录吗？此操作不可撤销。"
                        : "确定要删除该听写记录吗？此操作不可撤销。";
                new androidx.appcompat.app.AlertDialog.Builder(DictationMenuActivity.this).setTitle("删除听写记录")
                        .setMessage(confirmMsg).setPositiveButton("删除", (dialog, which) -> {
                            DictationApiHelper.deleteTask(handler, MSG_DELETE_SUCCESS, MSG_DELETE_FAILED, userId,
                                    item.taskId);
                        }).setNegativeButton("取消", null).show();
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        private String formatDate(String dateStr) {
            if (dateStr == null || dateStr.length() < 10)
                return dateStr;
            return dateStr.substring(0, 10);
        }

        private String getStatusText(DictationModels.DictationHistoryItem item) {
            String status = item.status;
            // PENDING 任务：检查 createdAt + 20分钟 是否已过
            if ("PENDING".equals(status) && item.createdAt != null && !item.createdAt.isEmpty()) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                    Date created = sdf.parse(item.createdAt);
                    if (created != null && System.currentTimeMillis() - created.getTime() > 20 * 60 * 1000) {
                        return "可开始";
                    }
                } catch (Exception ignored) {
                }
            }
            switch (status) {
            case "SUBMITTED":
                return "已完成";
            case "READY":
                return "可开始";
            case "PENDING":
                return "冷却中";
            default:
                return status;
            }
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvDate, tvAccuracy, tvStatus, tvWords, btnDelete;

            ViewHolder(View v) {
                super(v);
                tvDate = v.findViewById(R.id.tv_history_date);
                tvAccuracy = v.findViewById(R.id.tv_history_accuracy);
                tvStatus = v.findViewById(R.id.tv_history_status);
                tvWords = v.findViewById(R.id.tv_history_words);
                btnDelete = v.findViewById(R.id.btn_delete);
            }
        }
    }
}
