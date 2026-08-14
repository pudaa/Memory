package com.deepsleep.memory.ui.components;

import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.animation.ObjectAnimator;
import android.animation.AnimatorSet;
import android.animation.PropertyValuesHolder;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.FocusMeteringResult;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.deepsleep.memory.R;
import com.deepsleep.memory.settings.UserSettingsManager;
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

    /** 输入：指定照片保存路径（可选，不传则自动生成） */
    public static final String EXTRA_OUTPUT_PATH = "output_path";
    /** 输出：照片文件绝对路径 */
    public static final String EXTRA_PHOTO_PATH = "photo_path";

    private PreviewView viewFinder;
    private ImageButton btnCapture;
    private ImageButton btnClose;
    private ImageButton btnFlash;
    private ImageButton btnGallery;
    private ImageView focusIndicator;
    private TextView btnRatio;

    private ImageCapture imageCapture;
    /** 当前绑定的 Camera（用于点击对焦 startFocusAndMetering） */
    private Camera camera;
    private int lensFacing = CameraSelector.LENS_FACING_BACK;
    private boolean flashEnabled = false;
    private String outputPath;
    private boolean isCapturing = false;

    /**
     * 拍摄比例预设：4:3 / 16:9 注意：CameraX 的 AspectRatioStrategy 官方仅支持 RATIO_4_3 与
     * RATIO_16_9 两种； 1:1 并非受支持的值，硬塞会导致相机绑定失败（无法启动相机），故移除。 需要 1:1 出图时可先按 4:3
     * 拍摄，在裁剪页选择 1:1 比例。
     */
    private static final String[] RATIO_LABELS = { "4:3", "16:9" };
    private static final AspectRatioStrategy[] RATIO_STRATEGIES = {
            AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY,
            AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY };
    private int currentRatioIndex = 1; // 默认 16:9，与屏幕利用率较匹配

    /** 相册读取权限请求回调（读取最近照片缩略图用） */
    private final ActivityResultLauncher<String> galleryPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    loadLatestPhotoThumbnail();
                }
            });

    /** 按当前选中的拍摄比例构建 ResolutionSelector（预览与拍照一致） */
    private ResolutionSelector buildResolutionSelector() {
        return new ResolutionSelector.Builder().setAspectRatioStrategy(RATIO_STRATEGIES[currentRatioIndex]).build();
    }

    /** 相册选择回调（Activity Result API） */
    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri selectedUri = result.getData().getData();
                    if (selectedUri != null) {
                        copyGalleryImage(selectedUri);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 隐藏系统状态栏/导航栏，提供沉浸式拍摄体验
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(getWindow(),
                getWindow().getDecorView());
        insetsController.hide(WindowInsetsCompat.Type.systemBars());
        insetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        setContentView(R.layout.camera_capture_layout);

        viewFinder = findViewById(R.id.view_finder);
        btnCapture = findViewById(R.id.btn_capture);
        btnClose = findViewById(R.id.btn_close);
        btnFlash = findViewById(R.id.btn_flash);
        btnGallery = findViewById(R.id.btn_gallery);
        focusIndicator = findViewById(R.id.focus_indicator);
        btnRatio = findViewById(R.id.btn_ratio);

        outputPath = getIntent().getStringExtra(EXTRA_OUTPUT_PATH);

        // 读取上次选择的拍摄比例（持久化，重建/下次进入保持）
        currentRatioIndex = Math.max(0,
                Math.min(RATIO_LABELS.length - 1, UserSettingsManager.getInstance(this).getCameraAspectRatioIndex()));

        btnClose.setOnClickListener(v -> finish());
        btnFlash.setOnClickListener(v -> toggleFlash());
        btnCapture.setOnClickListener(v -> takePhoto());
        btnGallery.setOnClickListener(v -> openGallery());
        btnRatio.setOnClickListener(v -> cycleRatio());
        updateRatioButton();

        // 点击预览区域 → 点按对焦 + 显示对焦圈动画
        View focusOverlay = findViewById(R.id.focus_overlay);
        focusOverlay.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                handleTapToFocus(event.getX(), event.getY());
            }
            return true;
        });

        // 只有后置摄像头才显示闪光灯按钮
        btnFlash.setVisibility(lensFacing == CameraSelector.LENS_FACING_BACK ? View.VISIBLE : View.GONE);
        btnFlash.setImageResource(R.drawable.ic_flash_off);

        // 加载最近照片作为相册按钮缩略图（无权限时保持默认图标）
        loadLatestPhotoThumbnail();
        requestGalleryPermissionIfNeeded();
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

        // ── 预览与拍照使用当前选中的宽高比（4:3 / 16:9 / 1:1），保证两者一致 ──

        // 预览（fillCenter 填充整个屏幕，提高屏幕利用率；对焦映射会按实际变换校正）
        viewFinder.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        Preview preview = new Preview.Builder().setResolutionSelector(buildResolutionSelector()).build();
        preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

        // 拍照用例 - 应用相同的宽高比，确保一致性
        imageCapture = new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setFlashMode(flashEnabled ? ImageCapture.FLASH_MODE_ON : ImageCapture.FLASH_MODE_OFF)
                .setResolutionSelector(buildResolutionSelector()).build();

        // 镜头选择
        CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(lensFacing).build();

        try {
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
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

        // 拍照前根据设备当前物理旋转设置目标旋转，确保横持拍摄时照片方向正确
        // （activity 允许跟随旋转，但以物理 Display 旋转为准，兼容旋转锁定场景）
        imageCapture.setTargetRotation(getWindowManager().getDefaultDisplay().getRotation());

        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                        isCapturing = false;
                        Log.i(TAG, "照片已保存: " + photoFile.getAbsolutePath());
                        // 修复横拍旋转问题：根据 EXIF 方向在后台线程旋转回正（避免主线程全尺寸解码卡顿）
                        fixRotationAsync(photoFile, () -> returnResult(photoFile));
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
        galleryLauncher.launch(intent);
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
            // 确保方向正确（后台线程处理，避免主线程解码大图卡顿）
            fixRotationAsync(destFile, () -> returnResult(destFile));
        } catch (IOException e) {
            Log.e(TAG, "复制相册图片失败", e);
            Toast.makeText(this, "读取图片失败", Toast.LENGTH_SHORT).show();
        }
    }

    // ── 点按对焦 ──

    /** 点击预览区域触发自动对焦 + 测光，并播放对焦圈动画 */
    private void handleTapToFocus(float x, float y) {
        if (camera == null)
            return;
        MeteringPointFactory factory = viewFinder.getMeteringPointFactory();
        MeteringPoint point = factory.createPoint(x, y);
        FocusMeteringAction action = new FocusMeteringAction.Builder(point,
                FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AE).build();

        showFocusIndicator(x, y);
        ListenableFuture<FocusMeteringResult> future = camera.getCameraControl().startFocusAndMetering(action);
        future.addListener(() -> {
            try {
                FocusMeteringResult result = future.get();
                hideFocusIndicator(result.isFocusSuccessful());
            } catch (ExecutionException e) {
                // 连续点按对焦时，前一次对焦会被新一次取消（OperationCanceledException 作为 cause），属正常现象
                if (e.getCause() instanceof CameraControl.OperationCanceledException) {
                    hideFocusIndicator(false);
                } else {
                    Log.e(TAG, "对焦失败", e);
                    hideFocusIndicator(false);
                }
            } catch (Exception e) {
                Log.e(TAG, "对焦失败", e);
                hideFocusIndicator(false);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /** 在点击位置显示对焦圈并播放缩放动画（1.3 → 1.0），提示用户已重新对焦 */
    private void showFocusIndicator(float x, float y) {
        if (focusIndicator == null)
            return;
        focusIndicator.setX(x - focusIndicator.getWidth() / 2f);
        focusIndicator.setY(y - focusIndicator.getHeight() / 2f);
        focusIndicator.setVisibility(View.VISIBLE);
        focusIndicator.setAlpha(1f);
        focusIndicator.setScaleX(1.3f);
        focusIndicator.setScaleY(1.3f);
        focusIndicator.animate().scaleX(1f).scaleY(1f).setDuration(200)
                .setInterpolator(new AccelerateDecelerateInterpolator()).start();
    }

    /** 对焦完成（成功/失败）后淡出对焦圈 */
    private void hideFocusIndicator(boolean success) {
        if (focusIndicator == null)
            return;
        focusIndicator.animate().alpha(0f).setDuration(300).withEndAction(() -> {
            focusIndicator.setVisibility(View.INVISIBLE);
        }).start();
    }

    // ── 相册最近照片缩略图 ──

    private String getGalleryPermission() {
        return Build.VERSION.SDK_INT >= 33 ? android.Manifest.permission.READ_MEDIA_IMAGES
                : android.Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    private boolean canReadGallery() {
        return ContextCompat.checkSelfPermission(this, getGalleryPermission()) == PackageManager.PERMISSION_GRANTED;
    }

    /** 无读取相册权限时请求（缩略图展示用；选择照片走系统选择器不依赖该权限） */
    private void requestGalleryPermissionIfNeeded() {
        if (!canReadGallery()) {
            galleryPermissionLauncher.launch(getGalleryPermission());
        }
    }

    /** 查询系统相册最近一张照片，加载为相册按钮缩略图；无权限/无照片时保持默认图标 */
    private void loadLatestPhotoThumbnail() {
        if (!canReadGallery())
            return;
        try {
            Uri collection = Build.VERSION.SDK_INT >= 29
                    ? MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            String[] projection = { MediaStore.Images.Media._ID };
            String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";
            try (Cursor cursor = getContentResolver().query(collection, projection, null, null, sortOrder)) {
                if (cursor != null && cursor.moveToFirst()) {
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID));
                    Uri uri = ContentUris.withAppendedId(collection, id);
                    Glide.with(this).load(uri).centerCrop().transform(new RoundedCorners(18)).into(btnGallery);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "加载相册缩略图失败", e);
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

    /** 后台线程执行 EXIF 旋转修复，完成后在主线程回调 onDone（避免主线程全尺寸解码卡顿/OOM） */
    private void fixRotationAsync(File photoFile, Runnable onDone) {
        new Thread(() -> {
            fixRotation(photoFile);
            runOnUiThread(() -> {
                if (onDone != null)
                    onDone.run();
            });
        }).start();
    }

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

    // ── 拍摄比例切换 ──

    /** 循环切换拍摄比例：4:3 → 16:9 → 1:1 */
    private void cycleRatio() {
        currentRatioIndex = (currentRatioIndex + 1) % RATIO_LABELS.length;
        updateRatioButton();
        // 持久化当前选择，重建/下次进入保持
        UserSettingsManager.getInstance(this).setCameraAspectRatioIndex(currentRatioIndex);
        // 重建 Activity，让相机在全新生命周期中按新比例干净地重新绑定
        // （运行中 unbindAll+rebind 存在竞态，部分设备会抛出“无法启动相机”并退出）
        recreate();
    }

    private void updateRatioButton() {
        if (btnRatio != null) {
            btnRatio.setText(RATIO_LABELS[currentRatioIndex]);
        }
    }

    // ── 音量键快门（仿系统相机，横屏拍摄更顺手） ──

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            // 仅首次按下触发，避免长按音量键连拍
            if (event.getRepeatCount() == 0) {
                takePhoto();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ── 闪光灯 ──

    private void toggleFlash() {
        flashEnabled = !flashEnabled;
        btnFlash.setImageResource(flashEnabled ? R.drawable.ic_flash_on : R.drawable.ic_flash_off);
        // 直接切换手电筒，无需重新绑定相机（避免重绑竞态崩溃）
        if (camera != null) {
            camera.getCameraControl().enableTorch(flashEnabled);
        }
    }
}
