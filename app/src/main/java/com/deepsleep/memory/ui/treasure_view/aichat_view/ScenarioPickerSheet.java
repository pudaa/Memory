package com.deepsleep.memory.ui.treasure_view.aichat_view;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.deepsleep.memory.R;
import com.deepsleep.memory.network.GetDataByThread;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 场景选择器 BottomSheet。 展示可用的角色扮演场景，支持分类筛选。
 */
public class ScenarioPickerSheet extends BottomSheetDialogFragment {

    public interface OnScenarioSelectedListener {
        void onScenarioSelected(String scenarioId, String title, String aiRole, String userRole, String openingLine);

        void onCustomScenarioRequested();
    }

    private OnScenarioSelectedListener listener;
    private List<ScenarioItem> allScenarios = new ArrayList<>();
    private List<ScenarioItem> filteredScenarios = new ArrayList<>();
    private ScenarioAdapter adapter;
    private String userId;
    private String currentFilter = "all";

    public static ScenarioPickerSheet newInstance(String userId) {
        ScenarioPickerSheet sheet = new ScenarioPickerSheet();
        Bundle args = new Bundle();
        args.putString("userId", userId);
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            userId = getArguments().getString("userId", "0");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_scenario_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView rv = view.findViewById(R.id.rvScenarios);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ScenarioAdapter();
        rv.setAdapter(adapter);

        // 自定义场景
        view.findViewById(R.id.layoutCustomScenario).setOnClickListener(v -> {
            if (listener != null)
                listener.onCustomScenarioRequested();
            dismiss();
        });

        // 加载场景列表
        loadScenarios();
    }

    public void setOnScenarioSelectedListener(OnScenarioSelectedListener l) {
        this.listener = l;
    }

    private void loadScenarios() {
        GetDataByThread api = new GetDataByThread("/conversation/scenarios");
        api.getScenarios(new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                if (msg.what == 1) {
                    try {
                        JSONObject root = new JSONObject((String) msg.obj);
                        if (root.optInt("code", -1) == 200) {
                            JSONArray arr = root.getJSONArray("data");
                            allScenarios.clear();
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject s = arr.getJSONObject(i);
                                allScenarios.add(new ScenarioItem(s.getString("id"), s.getString("title"),
                                        s.optString("description", ""), s.optString("aiRole", ""),
                                        s.optString("userRole", ""), s.optString("difficultyLevel", "all"),
                                        s.optString("icon", ""), s.optString("openingLine", "")));
                            }
                            setupCategoryChips();
                            filterScenarios("all");
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }, 1, -1, userId);
    }

    private void setupCategoryChips() {
        if (getView() == null)
            return;
        ChipGroup chipGroup = getView().findViewById(R.id.chipGroupCategory);
        chipGroup.removeAllViews();

        // 全部
        addCategoryChip(chipGroup, "全部", "all");

        // 按难度分类
        boolean hasBeginner = false, hasIntermediate = false, hasAdvanced = false;
        for (ScenarioItem s : allScenarios) {
            if ("beginner".equals(s.difficultyLevel) || "elementary".equals(s.difficultyLevel))
                hasBeginner = true;
            if ("intermediate".equals(s.difficultyLevel))
                hasIntermediate = true;
            if ("advanced".equals(s.difficultyLevel))
                hasAdvanced = true;
        }
        if (hasBeginner)
            addCategoryChip(chipGroup, "初级", "beginner");
        if (hasIntermediate)
            addCategoryChip(chipGroup, "中级", "intermediate");
        if (hasAdvanced)
            addCategoryChip(chipGroup, "高级", "advanced");

        // 全等级
        boolean hasAll = false;
        for (ScenarioItem s : allScenarios) {
            if ("all".equals(s.difficultyLevel)) {
                hasAll = true;
                break;
            }
        }
        if (hasAll)
            addCategoryChip(chipGroup, "全等级", "all_level");
    }

    private void addCategoryChip(ChipGroup group, String label, String filter) {
        Chip chip = new Chip(requireContext());
        chip.setText(label);
        chip.setCheckable(true);
        chip.setChecked("all".equals(filter) && "all".equals(currentFilter));

        // 使用主题色样式：未选中时为浅灰填充，选中时为 theme_primary 填充
        chip.setChipBackgroundColorResource(chip.isChecked() ? R.color.theme_primary : R.color.theme_surface);
        chip.setTextColor(getResources().getColor(chip.isChecked() ? R.color.white : R.color.theme_text_primary, null));
        chip.setChipStrokeWidth(0f);
        chip.setShapeAppearanceModel(chip.getShapeAppearanceModel().toBuilder().setAllCornerSizes(20f).build());
        chip.setTextSize(13f);

        chip.setOnClickListener(v -> {
            currentFilter = filter;
            // 更新所有 chip 的选中态颜色
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child instanceof Chip) {
                    Chip c = (Chip) child;
                    boolean isSelected = c == chip;
                    c.setChipBackgroundColorResource(isSelected ? R.color.theme_primary : R.color.theme_surface);
                    c.setTextColor(
                            getResources().getColor(isSelected ? R.color.white : R.color.theme_text_primary, null));
                }
            }
            filterScenarios(filter);
        });
        group.addView(chip);
    }

    private void filterScenarios(String filter) {
        filteredScenarios.clear();
        for (ScenarioItem s : allScenarios) {
            if ("all".equals(filter)) {
                filteredScenarios.add(s);
            } else if ("beginner".equals(filter)) {
                if ("beginner".equals(s.difficultyLevel) || "elementary".equals(s.difficultyLevel))
                    filteredScenarios.add(s);
            } else if ("intermediate".equals(filter)) {
                if ("intermediate".equals(s.difficultyLevel))
                    filteredScenarios.add(s);
            } else if ("advanced".equals(filter)) {
                if ("advanced".equals(s.difficultyLevel))
                    filteredScenarios.add(s);
            } else if ("all_level".equals(filter)) {
                if ("all".equals(s.difficultyLevel))
                    filteredScenarios.add(s);
            }
        }
        adapter.notifyDataSetChanged();
    }

    // ==================== 数据模型 ====================

    static class ScenarioItem {
        String id, title, description, aiRole, userRole, difficultyLevel, icon, openingLine;

        ScenarioItem(String id, String title, String description, String aiRole, String userRole,
                String difficultyLevel, String icon, String openingLine) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.aiRole = aiRole;
            this.userRole = userRole;
            this.difficultyLevel = difficultyLevel;
            this.icon = icon;
            this.openingLine = openingLine;
        }
    }

    // ==================== Adapter ====================

    class ScenarioAdapter extends RecyclerView.Adapter<ScenarioAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_scenario_card, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            ScenarioItem item = filteredScenarios.get(position);
            h.tvTitle.setText(item.title);
            h.tvDesc.setText(item.description);
            h.tvAiRole.setText("AI: " + item.aiRole);
            h.tvUserRole.setText("You: " + item.userRole);

            // 难度标签
            String diffText;
            int diffColor;
            switch (item.difficultyLevel) {
            case "beginner":
            case "elementary":
                diffText = "初级";
                diffColor = R.color.teal_200;
                break;
            case "intermediate":
                diffText = "中级";
                diffColor = R.color.theme_stress;
                break;
            case "advanced":
                diffText = "高级";
                diffColor = R.color.theme_error;
                break;
            default:
                diffText = "全等级";
                diffColor = R.color.theme_primary;
                break;
            }
            h.tvDifficulty.setText(diffText);
            h.tvDifficulty.getBackground().setTint(getResources().getColor(diffColor, null));

            h.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onScenarioSelected(item.id, item.title, item.aiRole, item.userRole, item.openingLine);
                }
                dismiss();
            });
        }

        @Override
        public int getItemCount() {
            return filteredScenarios.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvTitle, tvDesc, tvDifficulty, tvAiRole, tvUserRole;

            VH(@NonNull View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tvScenarioTitle);
                tvDesc = itemView.findViewById(R.id.tvScenarioDesc);
                tvDifficulty = itemView.findViewById(R.id.tvScenarioDifficulty);
                tvAiRole = itemView.findViewById(R.id.tvAiRole);
                tvUserRole = itemView.findViewById(R.id.tvUserRole);
            }
        }
    }
}
