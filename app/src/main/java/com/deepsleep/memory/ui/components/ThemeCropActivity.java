package com.deepsleep.memory.ui.components;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.canhub.cropper.CropImageView;
import com.deepsleep.memory.R;
import com.deepsleep.memory.settings.UserSettingsManager;

import java.io.File;
import java.util.Locale;

/**
 * 自建裁剪 Activity（基于 CanHub Android-Image-Cropper 的 CropImageView，替代原
 * UCropActivity）。
 * <p>
 * 提供：主题化配色、预设裁剪比例（自由/原始/1:1/3:2/4:3/16:9/16:10/A4）、旋转、水平翻转。
 * 相比 UCrop 的 cover 回弹行为，可在此页面进行后续深度定制（如切换比例时允许图片完整可见）。
 * <p>
 * 交互（iOS 相机裁剪页风格）：底部为圆形 Tab 行（滑动/点击切换 旋转/缩放/比例，点击当前圆
 * 触发重置）+ 滚轮式微调控件（横向刻度滚轮，特殊值亮色 + 经过震动 + 松手吸附回正）；
 * 裁剪框外以模糊背景图呈现，优雅区分裁剪区与被裁剪区。
 * <p>
 * 输入：{@link #EXTRA_SOURCE_URI}（待裁剪图片 Uri） 输出：RESULT_OK +
 * {@link #EXTRA_OUTPUT_URI}（裁剪结果文件 Uri）；取消则 RESULT_CANCELED。
 */
public class ThemeCropActivity extends AppCompatActivity {

    public static final String EXTRA_SOURCE_URI = "extra_source_uri";
    public static final String EXTRA_OUTPUT_URI = "extra_output_uri";

    /** 比例预设：{0,0}=自由 {-1,-1}=原始（按图片原始比例） */
    private static final int[][] RATIOS = { { 0, 0 }, { -1, -1 }, { 1, 1 }, { 3, 2 }, { 4, 3 }, { 16, 9 }, { 16, 10 },
            { 210, 297 } };
    private static final String[] RATIO_LABELS = { "自由", "原始", "1:1", "3:2", "4:3", "16:9", "16:10", "A4" };

    private CropImageView cropImageView;
    private CircularTabsView circularTabs;
    private WheelSliderView wheelSlider;

    /** 当前属性模型 */
    private int selectedTab = 0; // 0=旋转 1=缩放 2=比例
    private float rotateAngle = 0f; // -45° ~ +45° 微调
    private float zoomValue = 1f; // 1.0x ~ 4.0x
    private int ratioIndex = 0; // 比例预设索引
    private boolean wheelTouching = false; // 滚轮拖动中（旋转松手才应用）

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 隐藏系统状态栏/导航栏，提供沉浸式裁剪体验（与拍摄页一致）
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(getWindow(),
                getWindow().getDecorView());
        insetsController.hide(WindowInsetsCompat.Type.systemBars());
        insetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        setContentView(R.layout.theme_crop_layout);

        cropImageView = findViewById(R.id.crop_image_view);
        circularTabs = findViewById(R.id.circular_tabs);
        wheelSlider = findViewById(R.id.wheel_slider);
        Uri sourceUri = getIntent().getParcelableExtra(EXTRA_SOURCE_URI);

        // 应用主题配置（网格/边框/圆角颜色、压缩质量、输出上限等；框外为暗色遮罩）
        cropImageView.setImageCropOptions(UcropHelper.createThemedCropOptions(this));
        if (sourceUri != null) {
            cropImageView.setImageUriAsync(sourceUri);
        } else {
            Toast.makeText(this, "图片数据不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        cropImageView.setOnSetImageUriCompleteListener((view, uri, error) -> {
            if (error != null) {
                Toast.makeText(ThemeCropActivity.this, "图片加载失败", Toast.LENGTH_SHORT).show();
            }
        });

        cropImageView.setOnCropImageCompleteListener((view, result) -> {
            if (result.getError() == null && result.getUriContent() != null) {
                Intent data = new Intent();
                data.putExtra(EXTRA_OUTPUT_URI, result.getUriContent().toString());
                setResult(RESULT_OK, data);
            } else {
                setResult(RESULT_CANCELED);
                Toast.makeText(ThemeCropActivity.this, "裁剪失败", Toast.LENGTH_SHORT).show();
            }
            finish();
        });

        findViewById(R.id.btn_crop_back).setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        findViewById(R.id.btn_crop_done).setOnClickListener(v -> doCrop());

        // "自动适配"开关：开启后旋转/缩放自动放大图片保证裁剪框不超出图片（默认开，持久化）
        TextView btnAutoFit = findViewById(R.id.btn_crop_auto_fit);
        boolean autoFit = UserSettingsManager.getInstance(this).isCropAutoFitEnabled();
        cropImageView.setCropAutoFit(autoFit);
        btnAutoFit.setAlpha(autoFit ? 1f : 0.45f);
        btnAutoFit.setOnClickListener(v -> {
            boolean enabled = !UserSettingsManager.getInstance(this).isCropAutoFitEnabled();
            UserSettingsManager.getInstance(this).setCropAutoFitEnabled(enabled);
            cropImageView.setCropAutoFit(enabled);
            if (enabled) {
                // 开启后立即按当前倍率重新计算 cover 下限，避免要等下一次旋转/缩放才生效
                cropImageView.setZoom(cropImageView.getZoom());
            }
            syncZoomValue();
            btnAutoFit.setAlpha(enabled ? 1f : 0.45f);
        });

        // 90° 旋转 / 翻转（保留在顶部工具栏，滚轮负责微调）
        findViewById(R.id.btn_rotate_left).setOnClickListener(v -> {
            cropImageView.rotateImage(-90);
            syncRotateAngle();
        });
        findViewById(R.id.btn_rotate_right).setOnClickListener(v -> {
            cropImageView.rotateImage(90);
            syncRotateAngle();
        });
        findViewById(R.id.btn_flip).setOnClickListener(v -> cropImageView.flipImageHorizontally());

        // 圆形 Tab：滑动/点击切换属性；点击当前圆触发重置
        circularTabs.setListener(new CircularTabsView.OnTabListener() {
            @Override
            public void onTabSelected(int index) {
                selectedTab = index;
                configureWheelForTab(index);
                updateCircularTabs();
            }

            @Override
            public void onTabReselect(int index) {
                resetProperty(index);
            }
        });
        circularTabs.selectTab(0);
        initTabCircles();

        // 滚轮：拖动实时更新圆环；松手应用（旋转）或吸附（缩放/比例已实时）
        wheelSlider.setOnValueChangeListener(new WheelSliderView.OnValueChangeListener() {
            @Override
            public void onValueChanged(float value) {
                wheelTouching = true;
                switch (selectedTab) {
                case 0:
                    // 旋转：拖动中实时应用，用户即时看到旋转效果（松手吸附后再精确落位）
                    rotateAngle = value;
                    applyFineRotate();
                    updateCircularTabs();
                    break;
                case 1:
                    cropImageView.setZoom(value);
                    // 自动适配可能把请求值抬高到 cover 所需倍率，界面必须显示真实倍率
                    zoomValue = cropImageView.getZoom();
                    if (Math.abs(zoomValue - value) > 0.001f) {
                        wheelSlider.setValue(zoomValue);
                    }
                    updateCircularTabs();
                    break;
                case 2:
                    ratioIndex = Math.round(value);
                    updateCircularTabs();
                    break;
                }
            }

            @Override
            public void onValueChangeEnd(float value) {
                wheelTouching = false;
                switch (selectedTab) {
                case 0:
                    rotateAngle = value;
                    applyFineRotate();
                    break;
                case 2:
                    ratioIndex = Math.round(value);
                    applyRatio(ratioIndex);
                    break;
                }
            }
        });
        configureWheelForTab(0);
        applyRatio(0);
    }

    /** 初始化三个圆环组件（值文本初始为空，由 updateCircularTabs 填充） */
    private void initTabCircles() {
        updateCircularTabs();
    }

    /** 按当前 Tab 配置滚轮（范围/步进/特殊值/当前值） */
    private void configureWheelForTab(int tab) {
        switch (tab) {
        case 0: // 旋转微调 -45°~45°，特殊值 0°
            wheelSlider.setRange(-45f, 45f);
            wheelSlider.setSteps(1f, 5f);
            wheelSlider.setSpecialValue(0f);
            wheelSlider.setValue(rotateAngle);
            break;
        case 1: // 缩放 1.0x~4.0x，特殊值 1.0x
            wheelSlider.setRange(1f, 4f);
            wheelSlider.setSteps(0.05f, 0.25f);
            wheelSlider.setSpecialValue(1f);
            wheelSlider.setValue(zoomValue);
            break;
        case 2: // 比例 8 档离散，特殊值为当前档位
            wheelSlider.setRange(0f, RATIOS.length - 1f);
            wheelSlider.setSteps(1f, 1f);
            wheelSlider.setSpecialValue(ratioIndex);
            wheelSlider.setValue(ratioIndex);
            break;
        }
    }

    /** 更新三个圆环的数值与进度（旋转=角度占比/方向，缩放=倍率占比，比例=索引占比） */
    private void updateCircularTabs() {
        // 旋转
        CircularValueView rotateCircle = circularTabs.getTab(0);
        rotateCircle.setProgress(Math.abs(rotateAngle) / 45f);
        rotateCircle.setDirection(rotateAngle >= 0f);
        rotateCircle.setValueText(formatAngle(rotateAngle));
        // 缩放
        CircularValueView zoomCircle = circularTabs.getTab(1);
        zoomCircle.setProgress((zoomValue - 1f) / 3f);
        zoomCircle.setDirection(true);
        zoomCircle.setValueText(String.format(Locale.US, "%.1fx", zoomValue));
        // 比例
        CircularValueView ratioCircle = circularTabs.getTab(2);
        ratioCircle.setProgress(ratioIndex / (float) (RATIOS.length - 1));
        ratioCircle.setDirection(true);
        ratioCircle.setValueText(RATIO_LABELS[ratioIndex]);
    }

    private String formatAngle(float value) {
        int deg = Math.round(value);
        return deg == 0 ? "0°" : (deg > 0 ? "+" + deg + "°" : deg + "°");
    }

    /** 重置指定属性为初始值（点击正中圆触发） */
    private void resetProperty(int tab) {
        switch (tab) {
        case 0:
            rotateAngle = 0f;
            applyFineRotate();
            break;
        case 1:
            cropImageView.setZoom(1f);
            zoomValue = cropImageView.getZoom();
            break;
        case 2:
            ratioIndex = 0;
            applyRatio(0);
            break;
        }
        configureWheelForTab(tab);
        updateCircularTabs();
    }

    /** 执行裁剪：输出到应用缓存目录（结果限制在 1280 内），完成后回调 onCropImageComplete */
    private void doCrop() {
        File outFile = new File(getCacheDir(), "cropped_" + System.currentTimeMillis() + ".jpg");
        // JPEG 质量 85 + 最长边 1280：PaddleOCR 实测（三场景）精度无损，体积较 2048 约减半
        cropImageView.croppedImageAsync(Bitmap.CompressFormat.JPEG, 85, UcropHelper.MAX_CROP_RESULT_SIZE,
                UcropHelper.MAX_CROP_RESULT_SIZE, CropImageView.RequestSizeOptions.RESIZE_INSIDE,
                Uri.fromFile(outFile));
    }

    /** 应用微调旋转角度（-45° ~ +45°，直接设置，不触发 90° cover 放大逻辑） */
    private void applyFineRotate() {
        if (cropImageView == null)
            return;
        int deg = Math.round(rotateAngle);
        int norm = ((deg % 360) + 360) % 360;
        cropImageView.setFineRotation(norm);
        syncZoomValue();
    }

    /** 90° 旋转按钮后：保持当前微调角度（不受 90° 步进影响），同步圆环 */
    private void syncRotateAngle() {
        // 滚轮微调值不变（90° 旋转与微调独立叠加），仅确保圆环显示正确
        syncZoomValue();
        updateCircularTabs();
    }

    /** 同步 CropImageView 实际倍率到圆环/滚轮状态，覆盖自动适配产生的倍率提升。 */
    private void syncZoomValue() {
        if (cropImageView == null)
            return;
        zoomValue = cropImageView.getZoom();
        if (selectedTab == 1 && wheelSlider != null) {
            wheelSlider.setValue(zoomValue);
        }
        updateCircularTabs();
    }

    /** 应用裁剪比例预设 */
    private void applyRatio(int index) {
        ratioIndex = index;
        updateCircularTabs();
        int[] ratio = RATIOS[index];
        int x = ratio[0], y = ratio[1];
        if (x <= 0) {
            // 自由：不锁定比例
            cropImageView.setFixedAspectRatio(false);
        } else if (x < 0) {
            // 原始：按图片原始比例（优先取已加载的整图尺寸）
            Rect src = cropImageView.getWholeImageRect();
            if (src != null && src.width() > 0 && src.height() > 0) {
                cropImageView.setAspectRatio(src.width(), src.height());
            } else {
                cropImageView.setFixedAspectRatio(false);
            }
        } else {
            cropImageView.setAspectRatio(x, y);
        }
    }
}
