package com.deepsleep.memory.ui.components;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;
import android.animation.ObjectAnimator;
import android.animation.AnimatorSet;
import android.animation.PropertyValuesHolder;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.deepsleep.memory.R;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

/**
 * 自定义相机拍照 Activity。
 * <p>
 * 调用方式：
 * 
 * <pre>{@code
 * Intent intent = new Intent(context, CameraCaptureActivity.class);
 * intent.putExtra(CameraCaptureActivity.EXTRA_OUTPUT_PATH, photoFile.getAbsolutePath());
 * startActivityForResult(intent, REQUEST_CODE);
 * }</pre>
 * <p>
 * 返回结果：若拍照成功，resultCode=RESULT_OK，Intent 中携带
 * {@link #EXTRA_PHOTO_PATH}（照片文件绝对路径）。
 */
public class CameraCaptureActivity extends AppCompatActivity {

    private static final String TAG = "CameraCapture";
    private static final int REQUEST_GALLERY = 1001;

    /** 输入：指定照片保存路径（可选，不传则自动生成） */
    public static final String EXTRA_OUTPUT_PATH = "output_path";
    /** 输出：照片文件绝对路径 */
    public static final String EXTRA_PHOTO_PATH = "photo_path";

    private PreviewView viewFinder;
    private ImageButton btnCapture;
    private ImageButton btnClose;
    private ImageButton btnFlash;
    private ImageButton btnGallery;

    private ImageCapture imageCapture;
    private int lensFacing = CameraSelector.LENS_FACING_BACK;
    private boolean flashEnabled = false;
    private String outputPath;
    private boolean isCapturing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_capture);

        viewFinder = findViewById(R.id.view_finder);
        btnCapture = findViewById(R.id.btn_capture);
        btnClose = findViewById(R.id.btn_close);
        btnFlash = findViewById(R.id.btn_flash);
        btnGallery = findViewById(R.id.btn_gallery);

        outputPath = getIntent().getStringExtra(EXTRA_OUTPUT_PATH);

        btnClose.setOnClickListener(v -> finish());
        btnFlash.setOnClickListener(v -> toggleFlash());
        btnCapture.setOnClickListener(v -> takePhoto());
        btnGallery.setOnClickListener(v -> openGallery());

        // 只有后置摄像头才显示闪光灯按钮
        btnFlash.setVisibility(lensFacing == CameraSelector.LENS_FACING_BACK ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        startCamera();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "相机初始化失败", e);
                Toast.makeText(this, "相机初始化失败", Toast.LENGTH_SHORT).show();
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        cameraProvider.unbindAll();

        // ── 统一 Preview 和 ImageCapture 的宽高比 ──
        // 使用 4:3 宽高比（常见标准），确保预览与拍照一致

        // 预览
        Preview preview = new Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build();
        preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

        // 拍照用例 - 应用相同的宽高比，确保一致性
        imageCapture = new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setFlashMode(flashEnabled ? ImageCapture.FLASH_MODE_ON : ImageCapture.FLASH_MODE_OFF)
                .setTargetAspectRatio(AspectRatio.RATIO_4_3).build();

        // 镜头选择
        CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(lensFacing).build();

        try {
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
        } catch (Exception e) {
            Log.e(TAG, "相机绑定失败", e);
            Toast.makeText(this, "无法启动相机", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void takePhoto() {
        if (isCapturing || imageCapture == null)
            return;
        isCapturing = true;
        btnCapture.setEnabled(false);

        // ── 按钮按压动画反馈 ──
        playShutterFeedback();

        // 确定输出文件
        File photoFile;
        if (outputPath != null) {
            photoFile = new File(outputPath);
        } else {
            photoFile = createTempFile();
        }

        // 确保父目录存在
        File parent = photoFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                        isCapturing = false;
                        Log.i(TAG, "照片已保存: " + photoFile.getAbsolutePath());
                        // 修复横拍旋转问题：根据 EXIF 方向自动旋转回正
                        fixRotation(photoFile);
                        returnResult(photoFile);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        isCapturing = false;
                        btnCapture.setEnabled(true);
                        Log.e(TAG, "拍照失败", exception);
                        Toast.makeText(CameraCaptureActivity.this, "拍照失败，请重试", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void returnResult(File photoFile) {
        // 扫描媒体库（可选，方便图库看到）
        MediaScannerConnection.scanFile(this, new String[] { photoFile.getAbsolutePath() }, null, null);

        Intent result = new Intent();
        result.putExtra(EXTRA_PHOTO_PATH, photoFile.getAbsolutePath());
        setResult(RESULT_OK, result);
        finish();
    }

    private File createTempFile() {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        try {
            return File.createTempFile("camera_" + ts, ".jpg", dir);
        } catch (IOException e) {
            // fallback
            return new File(dir, "camera_" + ts + ".jpg");
        }
    }

    // ── 相册选择 ──

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_GALLERY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_GALLERY && resultCode == RESULT_OK && data != null) {
            Uri selectedUri = data.getData();
            if (selectedUri != null) {
                copyGalleryImage(selectedUri);
            }
        }
    }

    private void copyGalleryImage(Uri sourceUri) {
        File destFile;
        if (outputPath != null) {
            destFile = new File(outputPath);
        } else {
            destFile = createTempFile();
        }

        File parent = destFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (InputStream in = getContentResolver().openInputStream(sourceUri);
                FileOutputStream out = new FileOutputStream(destFile)) {
            if (in == null) {
                Toast.makeText(this, "无法读取图片", Toast.LENGTH_SHORT).show();
                return;
            }
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            out.flush();
            Log.i(TAG, "相册图片已复制: " + destFile.getAbsolutePath());
            // 确保方向正确
            fixRotation(destFile);
            returnResult(destFile);
        } catch (IOException e) {
            Log.e(TAG, "复制相册图片失败", e);
            Toast.makeText(this, "读取图片失败", Toast.LENGTH_SHORT).show();
        }
    }

    // ── 快门视觉反馈 ──

    /**
     * 播放拍照按钮的快门反馈动画。 包括按钮缩放效果和预览层的快门闪烁。
     */
    private void playShutterFeedback() {
        // 1. 按钮按压反馈：快速缩放
        PropertyValuesHolder scaleXDown = PropertyValuesHolder.ofFloat("scaleX", 1f, 0.85f);
        PropertyValuesHolder scaleYDown = PropertyValuesHolder.ofFloat("scaleY", 1f, 0.85f);
        ObjectAnimator scaleDown = ObjectAnimator.ofPropertyValuesHolder(btnCapture, scaleXDown, scaleYDown);
        scaleDown.setDuration(100);

        PropertyValuesHolder scaleXUp = PropertyValuesHolder.ofFloat("scaleX", 0.85f, 1f);
        PropertyValuesHolder scaleYUp = PropertyValuesHolder.ofFloat("scaleY", 0.85f, 1f);
        ObjectAnimator scaleUp = ObjectAnimator.ofPropertyValuesHolder(btnCapture, scaleXUp, scaleYUp);
        scaleUp.setDuration(100);

        AnimatorSet buttonFeedback = new AnimatorSet();
        buttonFeedback.playSequentially(scaleDown, scaleUp);
        buttonFeedback.setInterpolator(new AccelerateDecelerateInterpolator());
        buttonFeedback.start();

        // 2. 快门闪烁效果：PreviewView 白色闪光
        viewFinder.setAlpha(1f);
        ObjectAnimator flash = ObjectAnimator.ofFloat(viewFinder, View.ALPHA, 0.3f);
        flash.setDuration(80);
        ObjectAnimator unflash = ObjectAnimator.ofFloat(viewFinder, View.ALPHA, 1f);
        unflash.setDuration(120);

        AnimatorSet shutterFlash = new AnimatorSet();
        shutterFlash.playSequentially(flash, unflash);
        shutterFlash.start();
    }

    // ── 旋转修复 ──

    /**
     * 根据 EXIF 方向信息旋转照片，解决横拍后图片方向错误的问题。 读取角度后旋转 Bitmap 并覆盖写入，确保后续裁剪组件拿到正确方向。
     */
    private void fixRotation(File photoFile) {
        try {
            ExifInterface exif = new ExifInterface(photoFile.getAbsolutePath());
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            int rotation = 0;
            switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                rotation = 90;
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                rotation = 180;
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                rotation = 270;
                break;
            default:
                return; // 无需旋转
            }
            Log.i(TAG, "检测到 EXIF 方向 " + orientation + "，旋转 " + rotation + "°");

            // 加载、旋转、保存
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap src = BitmapFactory.decodeFile(photoFile.getAbsolutePath(), opts);
            if (src == null)
                return;

            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            Bitmap rotated = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), matrix, true);
            src.recycle();

            try (FileOutputStream fos = new FileOutputStream(photoFile)) {
                rotated.compress(Bitmap.CompressFormat.JPEG, 92, fos);
                fos.flush();
            }
            rotated.recycle();

            // 写回 EXIF 方向为正常
            ExifInterface outExif = new ExifInterface(photoFile.getAbsolutePath());
            outExif.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(ExifInterface.ORIENTATION_NORMAL));
            outExif.saveAttributes();

            Log.i(TAG, "旋转修复完成: " + photoFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "旋转修复失败", e);
        }
    }

    // ── 闪光灯 ──

    private void toggleFlash() {
        flashEnabled = !flashEnabled;
        btnFlash.setImageResource(flashEnabled ? android.R.drawable.ic_lock_idle_low_battery
                : android.R.drawable.ic_lock_idle_low_battery);
        // 重新绑定以应用闪光灯设置
        rebindCamera();
    }

    private void rebindCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                bindPreview(cameraProviderFuture.get());
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "重新绑定相机失败", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }
}
