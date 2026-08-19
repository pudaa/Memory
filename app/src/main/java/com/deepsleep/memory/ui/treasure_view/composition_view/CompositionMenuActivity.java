package com.deepsleep.memory.ui.treasure_view.composition_view;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.deepsleep.memory.R;
import com.deepsleep.memory.network.ApiBridge;
import com.deepsleep.memory.network.MemoryApiClient;
import com.deepsleep.memory.settings.InnerSettingsManager;
import com.deepsleep.memory.ui.components.CameraCaptureActivity;
import com.deepsleep.memory.ui.components.ThemeCropActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CompositionMenuActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private String currentPhotoPath;

    private ImageButton btnTakePhoto;
    private ImageButton btnTypeComposition;
    private LinearLayout compositionBooksLayout;
    private ListView historyCompositionList;
    private TextView noHistoryText;
    private CompositionRecordAdapter recordAdapter;
    private List<CompositionRecord> compositionRecords;

    static final int msg_success = 1;
    static final int msg_failed = -1;
    static final int msg_records_success = 2;
    static final int msg_records_failed = -2;
    private Uri croppedImageUri;

    /** OCR 识别进行中标志：防止重复上传、离开页面浪费资源 */
    private boolean isOcrInProgress = false;
    /** OCR 处理进度对话框（分阶段文案：上传 → 识别） */
    private Dialog ocrProgressDialog;
    private TextView ocrProgressText;
    /** 用于切换进度文案的 Handler（上传 → 识别阶段） */
    private final Handler ocrStageHandler = new Handler(Looper.getMainLooper());
    private final Runnable ocrStageRunnable = () -> {
        if (isOcrInProgress && ocrProgressText != null) {
            ocrProgressText.setText("图片已上传,正在识别文字,请稍候…");
        }
    };

    /** 拍照回调（自定义相机，拍照后返回照片路径） */
    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    // 从自定义相机返回的照片路径
                    String photoPath = result.getData().getStringExtra(CameraCaptureActivity.EXTRA_PHOTO_PATH);
                    if (photoPath != null) {
                        currentPhotoPath = photoPath;
                    }
                    startUCropActivity();
                }
            });

    /** 裁剪回调（裁剪完成后进入 OCR） */
    private final ActivityResultLauncher<Intent> cropLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    // 裁剪完成，进行OCR识别
                    String outputUri = result.getData().getStringExtra(ThemeCropActivity.EXTRA_OUTPUT_URI);
                    if (outputUri != null) {
                        croppedImageUri = Uri.parse(outputUri);
                        uploadImageForOCR();
                    }
                } else {
                    // 用户在裁剪界面点击取消 → 返回相机重新拍摄
                    dispatchTakePictureIntent();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.composition_menu_layout);

        initViews();
        setListeners();
    }

    @SuppressLint("HandlerLeak")
    private void initViews() {
        btnTakePhoto = findViewById(R.id.btn_take_photo);
        btnTypeComposition = findViewById(R.id.btn_type_composition);
        compositionBooksLayout = findViewById(R.id.composition_books_layout);
        historyCompositionList = findViewById(R.id.history_composition_list);
        noHistoryText = findViewById(R.id.no_history_text);

        // 设置返回按钮（OCR 进行中时提示确认，避免误触浪费已上传的识别）
        findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (isOcrInProgress) {
                new MaterialAlertDialogBuilder(this).setTitle("正在识别").setMessage("图片正在识别中,退出将丢弃本次识别结果。确定退出吗?")
                        .setPositiveButton("确定退出", (d, w) -> finish()).setNegativeButton("继续等待", null).show();
            } else {
                finish();
            }
        });

        // 初始化历史作文记录列表
        compositionRecords = new ArrayList<>();
        recordAdapter = new CompositionRecordAdapter(this, compositionRecords);
        historyCompositionList.setAdapter(recordAdapter);

        // 设置列表项点击事件
        historyCompositionList.setOnItemClickListener((parent, view, position, id) -> {
            if (isOcrInProgress) {
                Toast.makeText(this, "正在识别,请稍候…", Toast.LENGTH_SHORT).show();
                return;
            }
            CompositionRecord record = compositionRecords.get(position);
            Intent intent = new Intent(CompositionMenuActivity.this, CompositionResultActivity.class);
            intent.putExtra("result_json", record.getCorrectionResult());
            startActivity(intent);
        });

        // 获取历史作文记录

        int userId = InnerSettingsManager.getInstance(this).getUserId();

        ApiBridge.enqueue(MemoryApiClient.composition().records(String.valueOf(userId)), new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                case msg_records_success:
                    String recordsJson = (String) msg.obj;
                    parseAndDisplayRecords(recordsJson);
                    break;

                case msg_records_failed:
                    break;
                }
            }
        }, msg_records_success, msg_records_failed, null);
    }

    private void setListeners() {
        btnTakePhoto.setOnClickListener(v -> {
            // OCR 进行中禁止重复拍照，避免并发请求浪费
            if (isOcrInProgress) {
                Toast.makeText(this, "正在识别,请稍候…", Toast.LENGTH_SHORT).show();
                return;
            }
            // 检查相机权限
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, // 请求权限
                        new String[] { Manifest.permission.CAMERA }, REQUEST_CAMERA_PERMISSION);
            } else {
                dispatchTakePictureIntent();
            }
        });

        btnTypeComposition.setOnClickListener(v -> {
            if (isOcrInProgress) {
                Toast.makeText(this, "正在识别,请稍候…", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(CompositionMenuActivity.this, CompositionPreviewActivity.class);
            startActivity(intent);
        });
    }

    private void dispatchTakePictureIntent() {
        // 不预创建 0 字节临时文件：文件由相机拍照时在内部缓存目录创建并从结果返回，
        // 避免未拍照即退出时残留空文件被系统相册扫描（部分 ROM 会扫描 Android/data）
        Intent intent = new Intent(this, CameraCaptureActivity.class);
        cameraLauncher.launch(intent);
    }

    private void startUCropActivity() {
        Uri sourceUri = Uri.fromFile(new File(currentPhotoPath));

        // 启动自建裁剪页（预设比例已内置：自由/原始/1:1/3:2/4:3/16:9/16:10/A4，最大输出 1280）
        Intent intent = new Intent(this, ThemeCropActivity.class);
        intent.putExtra(ThemeCropActivity.EXTRA_SOURCE_URI, sourceUri);
        cropLauncher.launch(intent);
    }

    private void uploadImageForOCR() {
        if (isOcrInProgress) {
            Toast.makeText(this, "正在识别,请稍候…", Toast.LENGTH_SHORT).show();
            return;
        }
        if (croppedImageUri == null) {
            Toast.makeText(this, "图片数据不存在", Toast.LENGTH_SHORT).show();
            return;
        }

        isOcrInProgress = true;
        showOcrProgress();

        // 使用 MemoryApiClient 进行OCR识别
        ApiBridge.enqueue(MemoryApiClient.composition().extractText(
                ApiBridge.filePart(this, croppedImageUri, "image", "cropped_image.jpg", "image/jpeg")),
                new OCRHandler(), msg_success, msg_failed, "ExtractText");
    }

    /** 显示 OCR 处理进度对话框（先“上传中”，2 秒后切到“识别中”） */
    private void showOcrProgress() {
        if (isFinishing() || isDestroyed())
            return;
        try {
            View v = getLayoutInflater().inflate(R.layout.dialog_ocr_progress, null);
            ocrProgressText = v.findViewById(R.id.ocr_progress_text);
            ocrProgressText.setText("正在上传图片…");
            ocrProgressDialog = new MaterialAlertDialogBuilder(this).setCancelable(false).setView(v).create();
            ocrProgressDialog.show();
            ocrStageHandler.removeCallbacks(ocrStageRunnable);
            ocrStageHandler.postDelayed(ocrStageRunnable, 2000);
        } catch (Exception e) {
            ocrProgressDialog = null;
            ocrProgressText = null;
        }
    }

    /** 关闭 OCR 进度对话框并清理状态 */
    private void dismissOcrProgress() {
        ocrStageHandler.removeCallbacks(ocrStageRunnable);
        if (ocrProgressDialog != null && ocrProgressDialog.isShowing()) {
            try {
                ocrProgressDialog.dismiss();
            } catch (Exception ignored) {
            }
        }
        ocrProgressDialog = null;
        ocrProgressText = null;
    }

    /** 展示 OCR 失败原因并引导用户自检 */
    private void showOcrError() {
        if (isFinishing() || isDestroyed())
            return;
        String err = MemoryApiClient.getLastImageUploadError();
        String msg;
        if (err == null || err.isEmpty()) {
            msg = "未能识别图片中的文字。\n\n建议:\n• 调整拍摄角度与距离,保证文字清晰\n• 避免反光/阴影遮挡文字\n• 稍后重试";
        } else if (err.contains("超时")) {
            msg = err + "\n\n请检查:\n• 手机网络是否正常\n• 服务器是否已启动\n确认后稍后重试";
        } else if (err.contains("无法连接")) {
            msg = err + "\n\n请检查:\n• 手机网络/数据流量是否开启\n• 服务器(含内网穿透)是否在线\n确认后重试";
        } else if (err.contains("服务器返回错误码")) {
            msg = err + "\n\n请稍后重试;若持续失败,可联系管理员查看服务端日志。";
        } else {
            msg = err + "\n\n建议:\n• 调整拍摄后重试\n• 检查网络后重试";
        }
        new MaterialAlertDialogBuilder(this).setTitle("识别失败").setMessage(msg).setPositiveButton("知道了", null).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isOcrInProgress = false;
        dismissOcrProgress();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NotNull String[] permissions,
            @NotNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                dispatchTakePictureIntent();
            } else {
                Toast.makeText(this, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void deleteTempImageFile() {
        if (currentPhotoPath != null) {
            File tempFile = new File(currentPhotoPath);
            if (tempFile.exists()) {
                tempFile.delete();
            }
            currentPhotoPath = null;
        }
    }

    @SuppressLint("HandlerLeak")
    class OCRHandler extends Handler {
        OCRHandler() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            // 用户已离开页面：丢弃结果、清理状态，不再跳转/弹窗（避免误跳与资源浪费）
            if (isFinishing() || isDestroyed()) {
                isOcrInProgress = false;
                dismissOcrProgress();
                return;
            }
            switch (msg.what) {
            case msg_success: // OCR成功
                String result = (String) msg.obj;
                Log.d("OCR", "识别结果：" + result);
                isOcrInProgress = false;
                dismissOcrProgress();
                // 删除临时文件
                deleteTempImageFile();
                // 跳转到预览界面并传递识别结果
                Intent intent = new Intent(CompositionMenuActivity.this, CompositionPreviewActivity.class);
                intent.putExtra("ocr_text", result);
                startActivity(intent);
                break;
            case msg_failed: // OCR失败
                isOcrInProgress = false;
                dismissOcrProgress();
                // 删除临时文件
                deleteTempImageFile();
                // 区分失败原因并引导用户自检
                showOcrError();
                break;
            }
        }
    }

    private void parseAndDisplayRecords(String recordsJson) { // 解析并显示记录列表
        try {
            JSONArray jsonArray = new JSONArray(recordsJson);
            compositionRecords.clear();

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject recordObj = jsonArray.getJSONObject(i);
                String compositionId = recordObj.getString("compositionId");
                String compositionContent = recordObj.getString("compositionContent");
                String correctionResult = recordObj.getString("correctionResult");
                String createdTime = recordObj.getString("createdTime");

                CompositionRecord record = new CompositionRecord(compositionId, compositionContent, correctionResult,
                        createdTime);
                compositionRecords.add(record);
            }

            recordAdapter.notifyDataSetChanged();

            if (compositionRecords.isEmpty()) {
                noHistoryText.setVisibility(View.VISIBLE);
                historyCompositionList.setVisibility(View.GONE);
            } else {
                noHistoryText.setVisibility(View.GONE);
                historyCompositionList.setVisibility(View.VISIBLE);
            }
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "解析历史记录失败", Toast.LENGTH_SHORT).show();
        }
    }
}
