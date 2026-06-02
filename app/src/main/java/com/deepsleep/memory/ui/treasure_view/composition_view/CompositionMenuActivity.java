package com.deepsleep.memory.ui.treasure_view.composition_view;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.deepsleep.memory.R;
import com.deepsleep.memory.network.GetDataByThread;
import com.yalantis.ucrop.UCrop;
import com.yalantis.ucrop.model.AspectRatio;
import com.yalantis.ucrop.view.CropImageView;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CompositionMenuActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private static final int REQUEST_IMAGE_CROP = 2;
    private String currentPhotoPath;

    private ImageButton btnTakePhoto;
    private ImageButton btnTypeComposition;
    private LinearLayout compositionBooksLayout;
    private ListView historyCompositionList;
    private TextView noHistoryText;
    private CompositionRecordAdapter recordAdapter;
    private List<CompositionRecord> compositionRecords;

    static final  int msg_success = 1;
    static final  int msg_failed = -1;
    static final int msg_records_success = 2;
    static final int msg_records_failed = -2;
    private static final int REQUEST_IMAGE_EDIT = 2;  // 添加编辑请求码
    private boolean isImageEdited = false;
    private Uri croppedImageUri;

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

        // 设置返回按钮
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // 初始化历史作文记录列表
        compositionRecords = new ArrayList<>();
        recordAdapter = new CompositionRecordAdapter(this, compositionRecords);
        historyCompositionList.setAdapter(recordAdapter);

        // 设置列表项点击事件
        historyCompositionList.setOnItemClickListener((parent, view, position, id) -> {
            CompositionRecord record = compositionRecords.get(position);
            Intent intent = new Intent(CompositionMenuActivity.this, CompositionResultActivity.class);
            intent.putExtra("result_json", record.getCorrectionResult());
            startActivity(intent);
        });

        // 获取历史作文记录

        SharedPreferences sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        int userId = sharedPreferences.getInt("userId", 0);

        GetDataByThread getRecords = new GetDataByThread("/composition/records");
        getRecords.fetchHistoryRecords(new Handler() {
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
        }
        , msg_records_success
        , msg_records_failed
        , userId);
    }

    private void setListeners() {
        btnTakePhoto.setOnClickListener(v -> {
            // 检查相机权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,// 请求权限
                        new String[]{Manifest.permission.CAMERA},
                        REQUEST_CAMERA_PERMISSION);
            } else {
                dispatchTakePictureIntent();
            }
        });

        btnTypeComposition.setOnClickListener(v -> {
            Intent intent = new Intent(CompositionMenuActivity.this, CompositionPreviewActivity.class);
            startActivity(intent);
        });
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // 确保有相机应用可以处理intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // 创建文件用于保存照片
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Toast.makeText(this, "创建图片文件失败", Toast.LENGTH_SHORT).show();
                return;
            }
            // 继续只有文件成功创建
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "com.deepsleep.memory.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);

                // 禁用拍照确认界面
                takePictureIntent.putExtra("noConfirmation", true);
                takePictureIntent.putExtra("android.intent.extra.quickCapture", true);
                takePictureIntent.putExtra(MediaStore.EXTRA_FINISH_ON_COMPLETION, true);

                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    private File createImageFile() throws IOException {
        // 创建一个临时文件名
        String imageFileName = "JPEG_Composition_" + System.currentTimeMillis() + "_";
        File storageDir = getExternalFilesDir(null);
        File image = File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );

        // 保存文件路径
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            startUCropActivity();
        } else if (requestCode == REQUEST_IMAGE_CROP && resultCode == RESULT_OK) {
            // uCrop编辑完成，进行OCR识别
            if (data != null) {
                croppedImageUri = UCrop.getOutput(data);
                if (croppedImageUri != null) {
                    uploadImageForOCR();
                }
            }
        }
    }

    private void startUCropActivity() {
        Uri sourceUri = Uri.fromFile(new File(currentPhotoPath));

        // 创建裁剪后保存的文件
        String destinationFileName = "cropped_image_" + System.currentTimeMillis() + ".jpg";
        File destinationFile = new File(getCacheDir(), destinationFileName);
        Uri destinationUri = Uri.fromFile(destinationFile);

        // 配置uCrop选项
        UCrop.Options options = new UCrop.Options();

        // 界面美化选项
        options.setCompressionQuality(90);

        // 隐藏标题栏文字（通过设置为空字符串）
        options.setToolbarTitle("");

        // 启用自由裁剪模式
        options.setFreeStyleCropEnabled(true);

        // 设置更多预设的裁剪比例
        options.setAspectRatioOptions(0,
                new AspectRatio("自由", 0, 0),
                new AspectRatio("原始", CropImageView.SOURCE_IMAGE_ASPECT_RATIO, CropImageView.SOURCE_IMAGE_ASPECT_RATIO),
                new AspectRatio("1:1", 1, 1),
                new AspectRatio("3:2", 3, 2),
                new AspectRatio("4:3", 4, 3),
                new AspectRatio("16:9", 16, 9),
                new AspectRatio("16:10", 16, 10),
                new AspectRatio("A4", 210, 297));

        // 设置网格线颜色
        options.setCropGridColor(ContextCompat.getColor(this, R.color.theme_color));
        options.setCropGridStrokeWidth(1);

        // 设置裁剪框颜色
        options.setCropFrameColor(ContextCompat.getColor(this, R.color.theme_color));
        options.setCropFrameStrokeWidth(1);

        // 设置其他UI选项
        options.setShowCropGrid(true);
        options.setHideBottomControls(false);

        // 启动uCrop
        UCrop uCrop = UCrop.of(sourceUri, destinationUri)
                .withMaxResultSize(2048, 2048)
                .withOptions(options);

        uCrop.start(this, REQUEST_IMAGE_CROP);
    }

    private void uploadImageForOCR() {
        if (croppedImageUri == null) {
            Toast.makeText(this, "图片数据不存在", Toast.LENGTH_SHORT).show();
            return;
        }

        // 显示正在处理提示
        Toast.makeText(this, "正在识别图片中的文字...", Toast.LENGTH_SHORT).show();

        // 使用GetDataByThread进行OCR识别
        GetDataByThread getDataByThread = new GetDataByThread("/composition/extractText");
        getDataByThread.extractTextFromImageUri(new OCRHandler() , msg_success, msg_failed, croppedImageUri, this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NotNull String[] permissions, @NotNull int[] grantResults) {
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
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case msg_success: // OCR成功
                    String result = (String) msg.obj;
                    Log.d("OCR", "识别结果：" + result);
                    // 删除临时文件
                    deleteTempImageFile();
                    // 跳转到预览界面并传递识别结果
                    Intent intent = new Intent(CompositionMenuActivity.this, CompositionPreviewActivity.class);
                    intent.putExtra("ocr_text", result);
                    startActivity(intent);
                    break;
                case msg_failed: // OCR失败
                    // 删除临时文件
                    deleteTempImageFile();
                    Toast.makeText(CompositionMenuActivity.this, "文字识别失败", Toast.LENGTH_SHORT).show();
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

                CompositionRecord record = new CompositionRecord(
                        compositionId,
                        compositionContent,
                        correctionResult,
                        createdTime
                );
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
