package com.deepsleep.memory.ui.components;

import android.content.Context;

import androidx.core.content.ContextCompat;

import com.deepsleep.memory.R;
import com.yalantis.ucrop.UCrop;

/**
 * UCrop 裁剪组件主题化工具。
 * <p>
 * 将 UCrop 默认的黑白橙配色替换为应用主题色（蓝色系）， 确保裁剪界面与应用整体视觉风格一致。
 */
public class UcropHelper {

    /**
     * 创建预配置的应用主题 UCrop.Options。
     * <p>
     * 已设置：压缩质量 90、自由裁剪、工具栏/状态栏/控件颜色。 调用方仍需自行设置 {@code setAspectRatioOptions}
     * 等业务相关选项。
     */
    public static UCrop.Options createThemedOptions(Context context) {
        UCrop.Options options = new UCrop.Options();

        // ── 压缩质量 ──
        options.setCompressionQuality(90);

        // ── 隐藏标题栏文字 ──
        options.setToolbarTitle("");

        // ── 裁剪功能 ──
        options.setFreeStyleCropEnabled(true);
        options.setShowCropGrid(true);
        options.setHideBottomControls(false);

        // ── 主题色（蓝色系） ──
        int themeColor = ContextCompat.getColor(context, R.color.theme_color);
        int themeStress = ContextCompat.getColor(context, R.color.theme_stress);
        int themePrimary = ContextCompat.getColor(context, R.color.theme_primary);
        int white = ContextCompat.getColor(context, android.R.color.white);

        // 工具栏背景色
        options.setToolbarColor(themePrimary);
        // 状态栏颜色
        options.setStatusBarColor(themePrimary);
        // 工具栏图标/文字颜色
        options.setToolbarWidgetColor(white);
        // 底部控件激活态颜色（选中的裁剪比例按钮等）
        options.setActiveControlsWidgetColor(themeStress);

        // 裁剪网格线
        options.setCropGridColor(themeColor);
        options.setCropGridStrokeWidth(1);

        // 裁剪框
        options.setCropFrameColor(themeColor);
        options.setCropFrameStrokeWidth(1);

        // 日志友好的提示
        options.setToolbarTitle(" ");

        return options;
    }
}
