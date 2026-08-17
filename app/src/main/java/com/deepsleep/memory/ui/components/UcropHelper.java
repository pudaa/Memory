package com.deepsleep.memory.ui.components;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.TypedValue;

import androidx.core.content.ContextCompat;

import com.canhub.cropper.CropImageOptions;
import com.canhub.cropper.CropImageView;
import com.deepsleep.memory.R;

/**
 * 图片裁剪组件主题化工具（CanHub Android-Image-Cropper 版，替代原 UCrop）。
 * <p>
 * 将裁剪组件默认配色替换为应用主题色（蓝色系），确保裁剪界面与应用整体视觉风格一致。
 */
public class UcropHelper {

    /**
     * 裁剪输出最大边长。
     * <p>
     * 1280 依据 PaddleOCR 实测基准：服务端检测阶段内部缩放到 limit_side_len=960
     * （MemoryServerTTS config/ocr.yaml），客户端 1280 已覆盖该上限并保留 33% 余量；
     * 体积对比实测（手写/简单/复杂三场景）2048→1280 文件大小约减半，OCR 精度无损。
     */
    public static final int MAX_CROP_RESULT_SIZE = 1280;

    /**
     * 创建预配置的应用主题 CropImageOptions。
     * <p>
     * 已设置：压缩质量 85、裁剪网格/边框/圆角为主题色、输出上限 1280、允许旋转/翻转。 调用方（自建裁剪 Activity）通过
     * {@link CropImageView#setImageCropOptions(CropImageOptions)} 应用。
     */
    public static CropImageOptions createThemedCropOptions(Context context) {
        CropImageOptions options = new CropImageOptions();

        int themeColor = ContextCompat.getColor(context, R.color.theme_color);
        int themePrimary = ContextCompat.getColor(context, R.color.theme_primary);

        // ── 输出与压缩 ──
        options.outputCompressFormat = Bitmap.CompressFormat.JPEG;
        // JPEG 质量 85：近无损、对 OCR 影响极小，减小上传体积（与 ThemeCropActivity.doCrop 一致）
        options.outputCompressQuality = 85;
        // 注意：不再设置 maxCropResult（保持默认 99999）。该值会按“屏幕缩放比例”反算并钳制裁剪框的
        // 屏幕像素尺寸——当图片显示较小时，会被换算成很小的裁剪框，导致无法选择较大区域。
        // 输出上限已由 ThemeCropActivity.doCrop() 的 RESIZE_INSIDE 保证。

        // ── 裁剪 UI（主题色） ──
        options.guidelines = CropImageView.Guidelines.ON;
        options.borderLineThickness = 2f;
        options.borderLineColor = themeColor;
        options.borderCornerThickness = 4f;
        options.borderCornerLength = 20f;
        options.borderCornerColor = themeColor;
        options.guidelinesThickness = 1f;
        options.guidelinesColor = themeColor;
        // 裁剪框外暗色遮罩（iOS 相机裁剪页风格）：60% 黑，清晰区分裁剪区与被裁剪区，
        // 优于此前“模糊背景图”方案（双图叠加视觉混乱且低分辨率放大模糊效果差）
        options.backgroundColor = android.graphics.Color.argb(153, 0, 0, 0);

        // ── 交互 ──
        options.allowRotation = true;
        options.allowCounterRotation = true;
        // 裁剪框边缘触摸容差：默认 24dp 偏大，在裁剪框外（尤其下方/侧边）拖动易误触
        // handle 导致裁剪框意外变化，收窄到 12dp 平衡"可点中"与"少误触"
        options.touchRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f,
                context.getResources().getDisplayMetrics());
        // 【定制】关闭自动缩放：裁剪页图片始终完整可见（contain），捏合自由调整裁剪框
        options.autoZoomEnabled = false;
        options.multiTouchEnabled = true;
        // 初始裁剪框与图片边缘保持 8% 留白（覆盖绝大部分图片，可再手动缩小）
        options.initialCropWindowPaddingRatio = 0.08f;
        options.showProgressBar = true;
        options.activityTitle = "";

        return options;
    }
}
