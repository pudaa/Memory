package com.deepsleep.memory.ui.main_view;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.deepsleep.memory.R;
import com.deepsleep.memory.ui.extra_view.word_search_view.SearchingActivity;
import com.deepsleep.memory.ui.treasure_view.composition_view.CompositionMenuActivity;
import com.deepsleep.memory.ui.treasure_view.dictation_view.DictationMenuActivity;
import com.deepsleep.memory.ui.treasure_view.pronunciation_view.PronunciationMenuActivity;
import com.deepsleep.memory.ui.treasure_view.evaluation_view.EvaluationDashboardActivity;

public class TreasureBoxFragment extends Fragment {
    ImageButton btnSearch;
    LinearLayout compositionSection, pronunciationSection, evaluationSection, dictationSection;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_treasure_box, container, false);
        btnSearch = view.findViewById(R.id.btn_search);
        btnSearch.setOnClickListener(v -> openSearch());

        // 添加作文评分功能的点击事件
        compositionSection = view.findViewById(R.id.composition_section);
        compositionSection.setOnClickListener(v -> openCompositionMenu());

        // 添加发音功能的点击事件
        pronunciationSection = view.findViewById(R.id.pronunciation_section);
        pronunciationSection.setOnClickListener(v -> openPronunciationMenu());

        // 添加学情分析功能的点击事件 (第二行第一个卡片 "学情分析")
        evaluationSection = view.findViewById(R.id.evaluation_section);
        if (evaluationSection != null) {
            evaluationSection.setOnClickListener(v -> openEvaluationDashboard());
        }

        // 添加单词听写功能的点击事件 (第一行第二个卡片 "单词听写")
        dictationSection = view.findViewById(R.id.dictation_section);
        if (dictationSection != null) {
            dictationSection.setOnClickListener(v -> openDictation());
        }

        return view;
    }

    private void openDictation() {
        Intent intent = new Intent(requireContext(), DictationMenuActivity.class);
        startActivity(intent);
    }

    private void openEvaluationDashboard() {
        Intent intent = new Intent(requireContext(), EvaluationDashboardActivity.class);
        startActivity(intent);
    }

    private void openPronunciationMenu() {
        Intent intent = new Intent(requireContext(), PronunciationMenuActivity.class);
        startActivity(intent);
    }

    private void openSearch() {
        Intent intent = new Intent(requireContext(), SearchingActivity.class);
        startActivity(intent);
    }

    private void openCompositionMenu() {
        Intent intent = new Intent(requireContext(), CompositionMenuActivity.class);
        startActivity(intent);
    }
}
