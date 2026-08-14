package com.deepsleep.memory.ui.main_view;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.deepsleep.memory.R;
import io.noties.markwon.Markwon;

import java.io.InputStream;
import java.util.Scanner;

public class ManualDialogFragment extends DialogFragment {
    private Markwon markwon;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public AlertDialog onCreateDialog(Bundle savedInstanceState) {
        // 创建自定义视图
        View view = getActivity().getLayoutInflater().inflate(R.layout.dialog_manual, null);
        TextView manualTextView = view.findViewById(R.id.manual_text_view);

        markwon = Markwon.create(requireContext());

        try (InputStream is = getResources().openRawResource(R.raw.manual);
             Scanner scanner = new Scanner(is)) {
            StringBuilder builder = new StringBuilder();
            while (scanner.hasNextLine()) {
                builder.append(scanner.nextLine()).append("\n");
            }
            markwon.setMarkdown(manualTextView, builder.toString());
        } catch (Exception e) {
            manualTextView.setText("无法加载使用手册。");
            e.printStackTrace();
        }

        // 创建Material风格的对话框
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setView(view)
               .setPositiveButton("关闭", (dialog, which) -> dialog.dismiss());

        return builder.create();
    }
}
