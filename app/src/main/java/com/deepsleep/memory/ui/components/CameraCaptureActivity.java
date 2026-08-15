package com.deepsleep.memory.ui.components;

import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.ViewOutlineProvider;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.AnimatorSet;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExposureState;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.FocusMeteringResult;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.core.ResolutionInfo;
import androidx.camera.core.ZoomState;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.bumptech.glide.Glide;
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
    private View topBar;
    private ImageButton btnCapture;
    private ImageButton btnClose;
    private ImageButton btnFlash;
    private ImageButton btnGrid;
    private ImageButton btnSwitchCamera;
    private ImageButton btnGallery;
    private ImageView focusIndicator;
    private TextView btnRatio;
    private GridOverlayView gridOverlay;
    private TextView evLabel;

    /** 当前预览真实分辨率（surface 建立后由 getResolutionInfo 提供，用于网格线渲染矩形计算） */
    private android.util.Size previewSize;
    /** 预览流旋转角度（getResolutionInfo 提供） */
    private int previewRotationDegrees = 0;

    private ImageCapture imageCapture;
    /** 当前绑定的 Camera（用于点击对焦 startFocusAndMetering） */
    private Camera camera;
    private int lensFacing = CameraSelector.LENS_FACING_BACK;
    /** 闪光灯三态：0=关 / 1=开 / 2=自动（借鉴系统相机） */
    private int flashMode = FLASH_OFF;
    private static final int FLASH_OFF = 0;
    private static final int FLASH_ON = 1;
    private static final int FLASH_AUTO = 2;
    private static final int[] FLASH_MODES = { ImageCapture.FLASH_MODE_OFF, ImageCapture.FLASH_MODE_ON,
            ImageCapture.FLASH_MODE_AUTO };
    private String outputPath;
    private boolean isCapturing = false;
    /** 切换镜头进行中标志（防止快速连点导致多次异步重绑竞态） */
    private boolean isSwitchingCamera = false;
    /** 相机权限兜底（调用方可能未提前请求） */
    private boolean cameraPermissionGranted = false;

    /** 缩放倍率徽章（如 "2.5x"，缩放时短暂显示） */
    private TextView zoomLabel;
    private GestureDetector tapDetector;
    private ScaleGestureDetector scaleDetector;
    /** 当前缩放倍率（捏合/双击缩放跟踪用） */
    private float currentZoomRatio = 1f;
    /** 曝光补偿索引（单指上下滑动调节，0 = 默认） */
    private int exposureIndex = 0;
    /** 曝光滑动累积位移（每 EXPOSURE_SLIDE_PER_STEP px 触发一个 EV 档位） */
    private float exposureScrollAccum = 0f;
    private static final float EXPOSURE_SLIDE_PER_STEP = 80f;
    /** 网格线跟随预览层尺寸变化（旋转/比例切换后画面渲染区域改变） */
    private final ViewTreeObserver.OnGlobalLayoutListener gridLayoutListener = () -> updateGridRenderRect();
    /** 缩放节流：合并高频 onScale 增量，每帧最多设置一次 zoom（减少 CameraX 内部平滑动画重启造成的延迟） */
    private float zoomPendingTarget = 1f;
    private boolean zoomScheduled = false;
    private final Runnable applyZoomRunnable = new Runnable() {
        @Override
        public void run() {
            zoomScheduled = false;
            if (camera != null) {
                camera.getCameraControl().setZoomRatio(zoomPendingTarget);
                showZoomBadge();
            }
        }
    };
    /** 隐藏缩放徽章：显示 1 秒后淡出 */
    private final Runnable hideZoomBadgeRunnable = new Runnable() {
        @Override
        public void run() {
            if (zoomLabel != null) {
                zoomLabel.animate().alpha(0f).setDuration(200)
                        .withEndAction(() -> zoomLabel.setVisibility(View.INVISIBLE)).start();
            }
        }
    };
    /** 隐藏曝光徽章：显示 1 秒后淡出 */
    private final Runnable hideEvBadgeRunnable = new Runnable() {
        @Override
        public void run() {
            if (evLabel != null) {
                evLabel.animate().alpha(0f).setDuration(200)
                        .withEndAction(() -> evLabel.setVisibility(View.INVISIBLE)).start();
            }
        }
    };

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

    /** 相机权限兜底请求（调用方通常已请求，此处防御未授权直接进入的场景） */
    private final ActivityResultLauncher<String> cameraPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    cameraPermissionGranted = true;
                    startCamera();
                } else {
                    Toast.makeText(this, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show();
                    finish();
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
        hideSystemBars();

        setContentView(R.layout.camera_capture_layout);

        viewFinder = findViewById(R.id.view_finder);
        topBar = findViewById(R.id.top_bar);
        btnCapture = findViewById(R.id.btn_capture);
        btnClose = findViewById(R.id.btn_close);
        btnFlash = findViewById(R.id.btn_flash);
        btnGrid = findViewById(R.id.btn_grid);
        btnSwitchCamera = findViewById(R.id.btn_switch_camera);
        btnGallery = findViewById(R.id.btn_gallery);
        focusIndicator = findViewById(R.id.focus_indicator);
        btnRatio = findViewById(R.id.btn_ratio);
        gridOverlay = findViewById(R.id.grid_overlay);
        zoomLabel = findViewById(R.id.zoom_label);
        evLabel = findViewById(R.id.ev_label);

        outputPath = getIntent().getStringExtra(EXTRA_OUTPUT_PATH);

        // 读取上次选择的拍摄比例（持久化，重建/下次进入保持）
        currentRatioIndex = Math.max(0,
                Math.min(RATIO_LABELS.length - 1, UserSettingsManager.getInstance(this).getCameraAspectRatioIndex()));

        btnClose.setOnClickListener(v -> finish());
        btnFlash.setOnClickListener(v -> cycleFlashMode());
        btnGrid.setOnClickListener(v -> toggleGrid());
        btnSwitchCamera.setOnClickListener(v -> toggleCameraFacing());
        btnCapture.setOnClickListener(v -> takePhoto());
        btnGallery.setOnClickListener(v -> openGallery());
        btnRatio.setOnClickListener(v -> cycleRatio());
        updateRatioButton();

        // 相册按钮圆角：ViewOutlineProvider 在绘制层裁剪（RenderNode 裁剪，包含 src 图片与
        // foreground 涟漪），不依赖 Glide 变换与 alpha 通道——黑色/任意比例图片都呈现圆角矩形；
        // 背景 bg_camera_gallery（半透明白圆角底）保证深色图片的圆角轮廓可见。
        // 注意：必须用固定 dp 尺寸而非 view.getWidth()——onCreate 阶段按钮尚未布局
        // （宽高为 0），用 0 尺寸 outline 会把整个按钮裁剪成不可见
        final float gallerySizePx = 48f * getResources().getDisplayMetrics().density;
        final float galleryRadiusPx = 12f * getResources().getDisplayMetrics().density;
        btnGallery.setClipToOutline(true);
        btnGallery.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, (int) gallerySizePx, (int) gallerySizePx, galleryRadiusPx);
            }
        });
        // 按钮布局完成后同步 outline（getOutline 以最新尺寸为准，避免布局阶段时序问题）
        btnGallery.post(() -> btnGallery.invalidateOutline());

        // 预览层手势：单指点按对焦、双指捏合缩放、双击切换 1x↔最大倍率、单指上下滑动调曝光
        setupCameraGestures();

        // 闪光灯按钮仅后置摄像头显示，图标按三态模式更新
        updateFlashUi();

        // 恢复网格线持久化开关状态
        if (gridOverlay != null) {
            boolean gridOn = UserSettingsManager.getInstance(this).isCameraGridEnabled();
            gridOverlay.setVisibility(gridOn ? View.VISIBLE : View.GONE);
            if (btnGrid != null) {
                btnGrid.setImageAlpha(gridOn ? 255 : 102);
            }
        }

        // 监听预览层尺寸变化（旋转/重建后画面渲染区域改变，网格线需重新对齐）
        viewFinder.getViewTreeObserver().addOnGlobalLayoutListener(gridLayoutListener);

        // 加载最近照片作为相册按钮缩略图（无权限时保持默认图标）
        loadLatestPhotoThumbnail();
        requestGalleryPermissionIfNeeded();

        // 按拍摄比例应用预览裁切与遮罩方案（16:9 铺满+渐变 / 4:3 完整画面+纯黑遮罩）
        applyRatioVisual();

        // 挖孔屏/刘海屏安全区适配：顶栏避开挖孔区域，防止遮挡与误触
        setupCutoutInsets();

        // 相机权限兜底：未授权则请求，授权回调后才启动相机
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA);
        } else {
            cameraPermissionGranted = true;
        }
    }

    /**
     * 按当前拍摄比例应用预览与遮罩视觉方案（借鉴系统相机）：
     * <ul>
     * <li>16:9 —— 预览 FILL_CENTER 铺满全屏，遮罩用半透明渐变（渐隐自然，无硬边）；</li>
     * <li>4:3  —— 预览 FIT_CENTER 显示完整画面（居中），画面四周为天然黑边，
     * 遮罩设为<b>透明</b>：黑边本身即黑色，无需遮罩；若用不透明遮罩会盖住 FIT 画面
     * 底部/侧边（画面实际高于假设），且网格线会被遮罩遮挡。</li>
     * </ul>
     */
    private void applyRatioVisual() {
        boolean fullScreen = currentRatioIndex == 1; // 1 = 16:9
        viewFinder.setScaleType(fullScreen ? PreviewView.ScaleType.FILL_CENTER : PreviewView.ScaleType.FIT_CENTER);

        View topScrim = findViewById(R.id.top_scrim);
        View bottomScrim = findViewById(R.id.bottom_scrim);
        if (topScrim == null || bottomScrim == null)
            return;
        if (!fullScreen) {
            // 4:3：黑边天然为黑，遮罩透明，避免盖住画面与网格线
            topScrim.setBackgroundColor(Color.TRANSPARENT);
            bottomScrim.setBackgroundColor(Color.TRANSPARENT);
        } else {
            boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
            int topRes;
            int bottomRes;
            if (landscape) {
                // 横屏：左侧（顶栏区）/ 右侧（快门区）
                topRes = R.drawable.bg_scrim_start;
                bottomRes = R.drawable.bg_scrim_end;
            } else {
                // 竖屏：顶部 / 底部
                topRes = R.drawable.bg_scrim_top;
                bottomRes = R.drawable.bg_scrim_bottom;
            }
            topScrim.setBackgroundResource(topRes);
            bottomScrim.setBackgroundResource(bottomRes);
        }
        // 网格线需跟随画面渲染区域（4:3 FIT 居中时非全屏），随比例/布局更新
        updateGridRenderRect();
    }

    /**
     * 计算相机画面的实际渲染矩形并同步给网格线层。
     * <p>
     * 优先使用 {@link Preview#getResolutionInfo()} 返回的<b>真实预览分辨率与旋转角</b>计算画面
     * 宽高比（FIT_CENTER 时按该比例在 PreviewView 内居中适配），竖屏/横屏均正确；
     * 分辨率未就绪时回退到 4:3 横向的近似假设。
     * <ul>
     * <li>16:9（FILL_CENTER 铺满）→ 渲染区域即整个 PreviewView；</li>
     * <li>4:3（FIT_CENTER 完整显示）→ 画面等比缩放后居中，四周留边，
     * 三分线必须基于该矩形计算才与画面内容对齐。</li>
     * </ul>
     */
    private void updateGridRenderRect() {
        if (gridOverlay == null || viewFinder == null)
            return;
        int w = viewFinder.getWidth();
        int h = viewFinder.getHeight();
        if (w <= 0 || h <= 0)
            return;
        RectF rect;
        if (currentRatioIndex == 1) {
            // 16:9 铺满
            rect = null;
        } else if (previewSize != null) {
            // 基于真实预览分辨率与旋转角：旋转后画面宽高比（宽/高）
            float ratio;
            if (previewRotationDegrees == 90 || previewRotationDegrees == 270) {
                ratio = (float) previewSize.getHeight() / previewSize.getWidth();
            } else {
                ratio = (float) previewSize.getWidth() / previewSize.getHeight();
            }
            float viewRatio = (float) w / h;
            if (ratio > viewRatio) {
                // 画面更宽 → 宽度铺满，高度等比居中
                float renderHeight = w / ratio;
                rect = new RectF(0, (h - renderHeight) / 2f, w, (h + renderHeight) / 2f);
            } else {
                // 画面更高 → 高度铺满，宽度等比居中
                float renderWidth = h * ratio;
                rect = new RectF((w - renderWidth) / 2f, 0, (w + renderWidth) / 2f, h);
            }
            // 夹取到 PreviewView 范围内，避免网格线画到遮罩/黑边区
            rect.left = Math.max(0f, rect.left);
            rect.top = Math.max(0f, rect.top);
            rect.right = Math.min(w, rect.right);
            rect.bottom = Math.min(h, rect.bottom);
        } else {
            // 回退：按 4:3 横向假设（分辨率信息尚未就绪时）
            float renderHeight = w * 3f / 4f;
            float top = (h - renderHeight) / 2f;
            rect = new RectF(0, Math.max(0f, top), w, Math.min(h, top + renderHeight));
        }
        gridOverlay.setRenderRect(rect);
    }

    /**
     * 隐藏系统状态栏/导航栏（沉浸式全屏）。
     * <p>
     * 在 onCreate、onWindowFocusChanged 等多处调用：部分 ROM（如华为）横屏时可能覆盖
     * 沉浸模式或重建时序导致隐藏失效（竖屏隐藏正常、横屏状态栏又出现），
     * 窗口每次获得焦点时重新隐藏可保证横竖屏一致。
     */
    private void hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(getWindow(),
                getWindow().getDecorView());
        insetsController.hide(WindowInsetsCompat.Type.systemBars());
        insetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    /**
     * 窗口获得焦点后重新隐藏系统栏——修复横屏下状态栏复现的问题。
     * <p>
     * 注意：控件朝向不做任何旋转校正——横竖屏切换由 Activity 重建（布局随窗口旋转）
     * 天然处理，横屏布局中控件保持相对窗口 0° 即用户在横持视角下的正立方向；
     * 此前传感器/Display 驱动的旋转机制因 ROM 报告不可靠反复出错，已整体移除。
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
            // 部分 ROM（华为）横屏下状态栏会在聚焦后再次注入，延迟再隐藏一次兜底
            viewFinder.postDelayed(this::hideSystemBars, 500);
        }
    }

    // ── 手势：点按对焦 / 捏合缩放 / 双击缩放 ──

    /**
     * 绑定预览层手势。点按对焦沿用原有逻辑；双指捏合平滑缩放（按相机 ZoomState 的最小/最大倍率夹取）；
     * 双击在 1x 与最大倍率之间切换。
     */
    private void setupCameraGestures() {
        View focusOverlay = findViewById(R.id.focus_overlay);
        if (focusOverlay == null)
            return;

        tapDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(@NonNull MotionEvent e) {
                // 必须返回 false：返回 true 会让 GestureDetector 判定“事件已被消费”，
                // 从而禁用后续 double-tap 检测（双击缩放将永远无法触发）
                return false;
            }

            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                // 单击确认（系统判定非双击后才回调），此时执行点按对焦
                handleTapToFocus(e.getX(), e.getY());
                return true;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                toggleDoubleTapZoom();
                return true;
            }

            @Override
            public boolean onScroll(@NonNull MotionEvent e1, @NonNull MotionEvent e2, float distanceX,
                    float distanceY) {
                // 单指垂直滑动调节曝光补偿（上滑加亮 / 下滑减暗，CameraView 风格）
                // 位移超过 touch slop 后 GestureDetector 不再触发 tap/双击，互不干扰
                handleExposureScroll(distanceY);
                return true;
            }
        });

        scaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(@NonNull ScaleGestureDetector detector) {
                applyPinchZoom(detector.getScaleFactor());
                return true;
            }
        });

        focusOverlay.setOnTouchListener((v, event) -> {
            scaleDetector.onTouchEvent(event);
            // 捏合进行中不响应点按/双击，避免缩放手势误触对焦
            if (!scaleDetector.isInProgress()) {
                tapDetector.onTouchEvent(event);
            }
            return true;
        });

        // 网格线层必须透明转发触摸到对焦层：可见时它位于 hit-test 顶层，
        // clickable=false 的 View 会“截胡”事件（向上冒泡而非穿透），导致对焦/缩放/滑动全部失效
        if (gridOverlay != null) {
            gridOverlay.setOnTouchListener((v, event) -> focusOverlay.dispatchTouchEvent(event));
        }
    }

    /** 相机绑定后同步当前缩放倍率（重建/切换比例后恢复真实值） */
    private void syncZoomState() {
        if (camera == null)
            return;
        ZoomState zoomState = camera.getCameraInfo().getZoomState().getValue();
        if (zoomState != null) {
            currentZoomRatio = zoomState.getZoomRatio();
        }
    }

    /**
     * 双指捏合缩放：以手势缩放系数按比例缩放，并夹在相机支持的最小/最大倍率之间。
     * 借鉴 CameraView 的手感调校：放大缩小的灵敏度加倍（(scaleFactor-1)*2+1），
     * 让少量手势位移也能获得明显的倍率变化，避免“捏了没反应”。
     * <p>
     * CameraX 的 setZoomRatio 内部自带平滑过渡动画，若每次 onScale 都立即调用，
     * 动画会不断重启、画面追不上手势（感知为延迟）。这里做 16ms 节流合并：
     * 高频手势事件只保留最新目标值，每帧最多提交一次，减少动画重启次数。
     */
    private static final float ZOOM_SENSITIVITY = 2.5f;

    /** 双指捏合缩放：以手势缩放系数按比例缩放，并夹在相机支持的最小/最大倍率之间 */
    private void applyPinchZoom(float scaleFactor) {
        if (camera == null)
            return;
        ZoomState zoomState = camera.getCameraInfo().getZoomState().getValue();
        if (zoomState == null)
            return;
        float minZoom = zoomState.getMinZoomRatio();
        float maxZoom = zoomState.getMaxZoomRatio();
        float boosted = 1f + (scaleFactor - 1f) * ZOOM_SENSITIVITY;
        float target = Math.max(minZoom, Math.min(maxZoom, currentZoomRatio * boosted));
        if (Math.abs(target - currentZoomRatio) < 0.001f)
            return;
        currentZoomRatio = target;
        zoomPendingTarget = target;
        if (!zoomScheduled) {
            zoomScheduled = true;
            viewFinder.postDelayed(applyZoomRunnable, 16);
        }
    }

    /** 双击在最小倍率（通常 1x）与最大倍率之间切换（平滑动画过渡，避免倍率跳变） */
    private void toggleDoubleTapZoom() {
        if (camera == null)
            return;
        ZoomState zoomState = camera.getCameraInfo().getZoomState().getValue();
        if (zoomState == null)
            return;
        float minZoom = zoomState.getMinZoomRatio();
        float maxZoom = zoomState.getMaxZoomRatio();
        float target = currentZoomRatio > minZoom + 0.01f ? minZoom : maxZoom;
        animateZoomTo(target);
    }

    /** 平滑动画缩放至目标倍率（双击缩放用），提升手感 */
    private void animateZoomTo(float targetZoom) {
        if (camera == null)
            return;
        ZoomState zoomState = camera.getCameraInfo().getZoomState().getValue();
        if (zoomState == null)
            return;
        float minZoom = zoomState.getMinZoomRatio();
        float maxZoom = zoomState.getMaxZoomRatio();
        float target = Math.max(minZoom, Math.min(maxZoom, targetZoom));
        ValueAnimator animator = ValueAnimator.ofFloat(currentZoomRatio, target);
        animator.setDuration(300);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            currentZoomRatio = (float) animation.getAnimatedValue();
            camera.getCameraControl().setZoomRatio(currentZoomRatio);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                showZoomBadge();
            }
        });
        animator.start();
    }

    /** 短暂显示当前缩放倍率（如 1.0x / 2.5x），1 秒后自动淡出 */
    private void showZoomBadge() {
        if (zoomLabel == null)
            return;
        zoomLabel.removeCallbacks(hideZoomBadgeRunnable);
        zoomLabel.setText(String.format(Locale.getDefault(), "%.1fx", currentZoomRatio));
        zoomLabel.setVisibility(View.VISIBLE);
        zoomLabel.setAlpha(1f);
        zoomLabel.postDelayed(hideZoomBadgeRunnable, 1000);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // 相机权限兜底：授权回调中才会置 true 并启动相机
        if (cameraPermissionGranted) {
            startCamera();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (viewFinder != null) {
            viewFinder.getViewTreeObserver().removeOnGlobalLayoutListener(gridLayoutListener);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "相机初始化失败", e);
                isSwitchingCamera = false;
                Toast.makeText(this, "相机初始化失败", Toast.LENGTH_SHORT).show();
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        cameraProvider.unbindAll();

        // ── 预览与拍照使用当前选中的宽高比（4:3 / 16:9），保证两者一致 ──
        // （scaleType 由 applyRatioVisual() 按比例统一设置：16:9 铺满 / 4:3 完整显示）

        Preview preview = new Preview.Builder().setResolutionSelector(buildResolutionSelector()).build();
        preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

        // 真实预览分辨率/旋转角在 surface 建立后异步就绪，延迟读取用于网格线渲染矩形计算
        // （基于真实数据而非假设，竖屏/横屏下三分线都能与画面精确对齐）
        viewFinder.postDelayed(() -> {
            ResolutionInfo res = preview.getResolutionInfo();
            if (res != null) {
                previewSize = res.getResolution();
                previewRotationDegrees = res.getRotationDegrees();
                updateGridRenderRect();
            }
        }, 250);

        // 拍照用例 - 应用相同的宽高比与当前闪光灯模式，确保一致性
        imageCapture = new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setFlashMode(FLASH_MODES[flashMode])
                .setResolutionSelector(buildResolutionSelector()).build();

        // 镜头选择
        CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(lensFacing).build();

        try {
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
            // 绑定成功后同步缩放状态（保留设备默认倍率）
            syncZoomState();
        } catch (Exception e) {
            Log.e(TAG, "相机绑定失败", e);
            Toast.makeText(this, "无法启动相机", Toast.LENGTH_SHORT).show();
            finish();
        } finally {
            isSwitchingCamera = false;
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
                        // 拍照失败时清理刚创建的空临时文件，避免 0 字节残留被系统相册扫描
                        if (photoFile != null && photoFile.exists() && photoFile.length() == 0) {
                            photoFile.delete();
                        }
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

    /**
     * 创建临时照片文件。
     * <p>
     * 使用<b>内部缓存目录</b>（getCacheDir）：MediaScanner 绝不扫描内部缓存，
     * 拍照失败/中途取消时残留的 0 字节文件不会进入系统相册
     * （此前用 getExternalFilesDir 会被部分 ROM 扫描，出现空白临时图片）。
     */
    private File createTempFile() {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File dir = getCacheDir();
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
                    // 圆角由 ViewOutlineProvider 在绘制层裁剪（见 onCreate），不依赖 Glide 变换
                    // （Glide RoundedCorners 依赖 alpha 通道，RGB_565/黑色图片上不可见）。
                    // override 按钮尺寸保证图片铺满按钮且裁切中心区域
                    int size = btnGallery.getWidth() > 0 ? btnGallery.getWidth() : dp(48);
                    Glide.with(this).load(uri).override(size, size).centerCrop().into(btnGallery);
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

    // ── 闪光灯（三态：关 / 开 / 自动，借鉴系统相机） ──

    /** 循环切换闪光灯模式：关 → 开 → 自动 → 关 */
    private void cycleFlashMode() {
        flashMode = (flashMode + 1) % 3;
        if (imageCapture != null) {
            // 运行时切换拍照闪光模式，无需重建用例
            imageCapture.setFlashMode(FLASH_MODES[flashMode]);
            // “开”模式下预览手电筒常亮（拍照时同样闪光）；关/自动不常亮
            if (camera != null) {
                camera.getCameraControl().enableTorch(flashMode == FLASH_ON);
            }
        }
        updateFlashUi();
    }

    /** 按镜头朝向与闪光灯模式刷新按钮可见性与图标（前置镜头无闪光灯） */
    private void updateFlashUi() {
        if (btnFlash == null)
            return;
        if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            btnFlash.setVisibility(View.GONE);
        } else {
            btnFlash.setVisibility(View.VISIBLE);
            btnFlash.setImageResource(flashMode == FLASH_OFF ? R.drawable.ic_flash_off
                    : flashMode == FLASH_ON ? R.drawable.ic_flash_on : R.drawable.ic_flash_auto);
        }
    }

    // ── 前后摄像头切换 ──

    /** 切换前后摄像头：重新绑定相机，重置缩放/曝光/闪光灯（仿系统相机行为） */
    private void toggleCameraFacing() {
        if (isSwitchingCamera)
            return;
        isSwitchingCamera = true;
        // 按钮旋转 180° 提示镜头翻转（纯视觉反馈，与朝向校正无关）
        btnSwitchCamera.animate().rotationBy(180f).setDuration(300).start();
        lensFacing = lensFacing == CameraSelector.LENS_FACING_BACK ? CameraSelector.LENS_FACING_FRONT
                : CameraSelector.LENS_FACING_BACK;
        // 切换镜头后重置缩放倍率与曝光补偿（前后摄能力不同）
        currentZoomRatio = 1f;
        exposureIndex = 0;
        // 前置镜头无闪光灯，切回后置时从“关”开始
        flashMode = FLASH_OFF;
        updateFlashUi();
        // 重新绑定（unbindAll + rebind，避免运行中重绑竞态）
        startCamera();
    }

    // ── 网格线 ──

    /** 切换三分构图网格线显示（持久化，下次进入保持） */
    private void toggleGrid() {
        if (gridOverlay == null)
            return;
        boolean enabled = !UserSettingsManager.getInstance(this).isCameraGridEnabled();
        UserSettingsManager.getInstance(this).setCameraGridEnabled(enabled);
        gridOverlay.setVisibility(enabled ? View.VISIBLE : View.GONE);
        // 图标半透明提示“未开启”，亮白提示“已开启”
        if (btnGrid != null) {
            btnGrid.setImageAlpha(enabled ? 255 : 102);
        }
    }

    // ── 曝光补偿（EV） ──

    /**
     * 单指垂直滑动调节曝光补偿：上滑加亮、下滑减暗（惯例方向）。
     * 每 EXPOSURE_SLIDE_PER_STEP 像素触发一个档位，档位夹在设备支持范围内。
     * 注意：onScroll 的 distanceY = 上次事件位置 - 当前事件位置，手指上移（y 减小）
     * 时 distanceY 为正，故曝光指数取 +steps（上滑 → 曝光提高）。
     */
    private void handleExposureScroll(float distanceY) {
        if (camera == null)
            return;
        ExposureState state = camera.getCameraInfo().getExposureState();
        if (state == null || !state.isExposureCompensationSupported())
            return;
        exposureScrollAccum += distanceY;
        int steps = (int) (exposureScrollAccum / EXPOSURE_SLIDE_PER_STEP);
        if (steps == 0)
            return;
        exposureScrollAccum -= steps * EXPOSURE_SLIDE_PER_STEP;
        int min = state.getExposureCompensationRange().getLower();
        int max = state.getExposureCompensationRange().getUpper();
        int target = Math.max(min, Math.min(max, exposureIndex + steps)); // 上滑(dy>0) → 加亮
        if (target == exposureIndex)
            return;
        exposureIndex = target;
        camera.getCameraControl().setExposureCompensationIndex(target);
        showEvBadge();
    }

    /** 短暂显示当前曝光补偿值（如 EV +1.0），1 秒后自动淡出 */
    private void showEvBadge() {
        if (evLabel == null)
            return;
        evLabel.removeCallbacks(hideEvBadgeRunnable);
        float step = 0.33f;
        if (camera != null) {
            ExposureState state = camera.getCameraInfo().getExposureState();
            if (state != null && state.getExposureCompensationStep() != null) {
                step = state.getExposureCompensationStep().floatValue();
            }
        }
        evLabel.setText(String.format(Locale.getDefault(), "EV %+.1f", exposureIndex * step));
        evLabel.setVisibility(View.VISIBLE);
        evLabel.setAlpha(1f);
        evLabel.postDelayed(hideEvBadgeRunnable, 1000);
    }

    // ── 挖孔屏安全区 ──

    /** 挖孔屏/刘海屏适配：允许内容延伸到 cutout，顶栏按安全区高度动态下移 */
    private void setupCutoutInsets() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        ViewCompat.setOnApplyWindowInsetsListener(getWindow().getDecorView(), (v, insets) -> {
            int safeTop = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && insets.getDisplayCutout() != null) {
                safeTop = insets.getDisplayCutout().getSafeInsetTop();
            }
            if (safeTop > 0) {
                ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) topBar.getLayoutParams();
                lp.topMargin = safeTop + dp(8);
                topBar.setLayoutParams(lp);
            }
            return insets;
        });
        getWindow().getDecorView().requestApplyInsets();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
