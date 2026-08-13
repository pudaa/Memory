package com.deepsleep.memory.ui.components;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.canhub.cropper.CropImageView;
import com.deepsleep.memory.R;

import java.io.File;
import java.util.Locale;

/**
 * 自建裁剪 Activity（基于 CanHub Android-Image-Cropper 的 CropImageView，替代原
 * UCropActivity）。
 * <p>
 * 提供：主题化配色、预设裁剪比例（自由/原始/1:1/3:2/4:3/16:9/16:10/A4）、旋转、水平翻转。 相比 UCrop 的 cover
 * 回弹行为，可在此页面进行后续深度定制（如切换比例时允许图片完整可见）。
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

    private CropImageView cropImageView;
    private TextView[] ratioChips;
    private int selectedRatioIndex = 0;
    private SeekBar fineRotateSeek;
    private TextView fineRotateAngle;
    private SeekBar zoomSeek;
    private TextView zoomText;
    /** 微调旋转滑块是否正在拖动（拖拽中不同步滑块位置，避免抖动） */
    private boolean isFineRotateTouching = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 隐藏系统状态栏/导航栏，提供沉浸式裁剪体验（与拍摄页一致）
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(getWindow(),
                getWindow().getDecorView());
        insetsController.hide(WindowInsetsCompat.Type.systemBars());
        insetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        setContentView(R.layout.activity_theme_crop);

        cropImageView = findViewById(R.id.crop_image_view);
        Uri sourceUri = getIntent().getParcelableExtra(EXTRA_SOURCE_URI);

        // 应用主题配置（网格/边框/圆角颜色、压缩质量、输出上限等）
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

        findViewById(R.id.btn_rotate_left).setOnClickListener(v -> {
            cropImageView.rotateImage(-90);
            syncFineRotateAngle();
        });
        findViewById(R.id.btn_rotate_right).setOnClickListener(v -> {
            cropImageView.rotateImage(90);
            syncFineRotateAngle();
        });
        findViewById(R.id.btn_flip).setOnClickListener(v -> cropImageView.flipImageHorizontally());

        // 微调旋转滑块：-45° ~ +45°，小幅度校正拍摄倾斜（拖动中仅显示角度，松手后应用）
        fineRotateSeek = findViewById(R.id.fine_rotate_seek);
        fineRotateAngle = findViewById(R.id.fine_rotate_angle);
        fineRotateSeek.setMax(900); // 每 10 单位 = 1°，450 为 0°
        fineRotateSeek.setProgress(450);
        fineRotateSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fineRotateAngle != null) {
                    int deg = (progress - 450) / 10;
                    fineRotateAngle.setText(deg == 0 ? "0°" : (deg > 0 ? "+" + deg + "°" : deg + "°"));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isFineRotateTouching = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isFineRotateTouching = false;
                applyFineRotate();
            }
        });

        // 缩放滚轮：1.0x ~ 4.0x，放大查看细节（图片始终完整可见，缩放后拖动裁剪框可平移选区）
        zoomSeek = findViewById(R.id.zoom_seek);
        zoomText = findViewById(R.id.zoom_text);
        zoomSeek.setMax(300); // 对应 1.0x ~ 4.0x
        zoomSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                cropImageView.setZoom(1f + progress / 100f);
                if (zoomText != null) {
                    zoomText.setText(String.format(Locale.US, "%.1fx", 1f + progress / 100f));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        // 重置：恢复旋转角度、缩放、裁剪框与比例
        findViewById(R.id.btn_crop_reset).setOnClickListener(v -> resetAll());

        // 初始化比例 chips
        ratioChips = new TextView[] { findViewById(R.id.chip_free), findViewById(R.id.chip_original),
                findViewById(R.id.chip_1_1), findViewById(R.id.chip_3_2), findViewById(R.id.chip_4_3),
                findViewById(R.id.chip_16_9), findViewById(R.id.chip_16_10), findViewById(R.id.chip_a4) };
        for (int i = 0; i < ratioChips.length; i++) {
            final int idx = i;
            ratioChips[i].setOnClickListener(v -> applyRatio(idx));
        }
        applyRatio(0);
    }

    /** 执行裁剪：输出到应用缓存目录（结果限制在 2048 内），完成后回调 onCropImageComplete */
    private void doCrop() {
        File outFile = new File(getCacheDir(), "cropped_" + System.currentTimeMillis() + ".jpg");
        // JPEG 质量 85：近无损、对 OCR 影响极小，但可显著减小上传体积（384KB→~250KB）
        cropImageView.croppedImageAsync(Bitmap.CompressFormat.JPEG, 85, UcropHelper.MAX_CROP_RESULT_SIZE,
                UcropHelper.MAX_CROP_RESULT_SIZE, CropImageView.RequestSizeOptions.RESIZE_INSIDE,
                Uri.fromFile(outFile));
    }

    /** 应用微调旋转滑块的角度（-45° ~ +45°，取模 0~359 后设置） */
    private void applyFineRotate() {
        if (fineRotateSeek == null || cropImageView == null)
            return;
        int deg = (fineRotateSeek.getProgress() - 450) / 10;
        int norm = ((deg % 360) + 360) % 360;
        // Kotlin var 属性在 Java 中必须用 setter 访问（不能直接字段赋值）
        cropImageView.setRotatedDegrees(norm);
        syncFineRotateAngle();
    }

    /** 将当前旋转角度同步到微调滑块与角度文本（90° 按钮操作后调用） */
    private void syncFineRotateAngle() {
        if (fineRotateSeek == null || fineRotateAngle == null || isFineRotateTouching || cropImageView == null)
            return;
        int cur = ((cropImageView.getRotatedDegrees() % 360) + 360) % 360;
        int display = cur;
        if (display > 180)
            display -= 360; // 映射到 -180° ~ 180°
        // 仅在 -45° ~ 45° 范围内同步滑块（超出视为已旋转 90°，回中并显示实际角度）
        if (display >= -45 && display <= 45) {
            fineRotateSeek.setProgress(450 + display * 10);
        } else {
            fineRotateSeek.setProgress(450);
        }
        fineRotateAngle.setText(display == 0 ? "0°" : (display > 0 ? "+" + display + "°" : display + "°"));
    }

    /** 重置：旋转角度、缩放、裁剪框与比例全部恢复初始状态 */
    private void resetAll() {
        // 重置图片变换（缩放/旋转/翻转）与裁剪框
        cropImageView.resetCropRect();
        // 同步滑块到初始值
        if (fineRotateSeek != null) {
            fineRotateSeek.setProgress(450);
            fineRotateAngle.setText("0°");
        }
        if (zoomSeek != null) {
            zoomSeek.setProgress(0);
            if (zoomText != null)
                zoomText.setText("1.0x");
        }
        // 重置为自由比例
        applyRatio(0);
    }

    /** 应用裁剪比例预设 */
    private void applyRatio(int index) {
        selectedRatioIndex = index;
        updateChipUi();
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

    private void updateChipUi() {
        for (int i = 0; i < ratioChips.length; i++) {
            ratioChips[i].setBackgroundResource(
                    i == selectedRatioIndex ? R.drawable.bg_crop_chip_selected : R.drawable.bg_crop_chip_normal);
        }
    }
}
