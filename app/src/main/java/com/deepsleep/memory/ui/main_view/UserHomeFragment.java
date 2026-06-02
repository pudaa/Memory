package com.deepsleep.memory.ui.main_view;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.bumptech.glide.Glide;
import com.deepsleep.memory.R;
import com.deepsleep.memory.settings.InnerSettingsManager;
import com.deepsleep.memory.ui.auth_view.LoginActivity;
import com.deepsleep.memory.ui.extra_view.my_word_view.MyWordBookActivity;
import com.deepsleep.memory.network.GetDataByThread;
import com.deepsleep.memory.ui.extra_view.setting_view.SettingActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public class UserHomeFragment extends Fragment {
    private static final String PREF_NAME = "UserPrefs";
    private static final String KEY_USER_ID = "userId";
    private InnerSettingsManager innerSettingsManager;
    private static final int PICK_IMAGE_REQUEST = 1;

    private Button reLoginBtn;
    private ImageButton toMyWordBookBtn;
    private ImageView userAvatar;
    private TextView nickNameText;
    private RelativeLayout layoutMyFavorite;
    private RelativeLayout settingLayout;
    private RelativeLayout aboutUsLayout;
    // 基本信息
    private String nickName, userName, avatarUrl;
    private int userId;
    // 线程处理
    static final int msg_success = 1;
    static final int msg_failed = -1;
    private final MyHandler myHandler = new MyHandler();

    @SuppressLint("MissingInflatedId")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_user_home, container, false);
        innerSettingsManager = InnerSettingsManager.getInstance(requireContext());
        userAvatar = view.findViewById(R.id.avatar_image);
        userAvatar.setOnClickListener(v -> openImageChooser());

        nickNameText = view.findViewById(R.id.nick_name);
        nickNameText.setOnClickListener(v -> showNicknameInputDialog());

        layoutMyFavorite = view.findViewById(R.id.layout_my_favorite);
        layoutMyFavorite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getActivity(), MyWordBookActivity.class);
                startActivity(intent);
            }
        });

        settingLayout = view.findViewById(R.id.layout_setting);
        settingLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getActivity(), SettingActivity.class);
                startActivity(intent);
            }
        });

        aboutUsLayout = view.findViewById(R.id.layout_about_us);
        aboutUsLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ManualDialogFragment dialog = new ManualDialogFragment();
                getParentFragmentManager().beginTransaction().add(dialog, "ManualDialog").commit();
            }
        });

        reLoginBtn = view.findViewById(R.id.btn_relogin);
        reLoginBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                innerSettingsManager.clear();
                Intent intent = new Intent(getActivity(), LoginActivity.class);
                startActivity(intent);
                Objects.requireNonNull(getActivity()).finish();
            }
        });
        initView();
        return view;
    }

    private void initView() {
        SharedPreferences sharedPreferences = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        userId = sharedPreferences.getInt(KEY_USER_ID, 0);
        GetDataByThread getDataByThread = new GetDataByThread("/auth/getUserInfo");
        getDataByThread.getUserInfo(myHandler, msg_success, msg_failed, String.valueOf(userId));

    }

    private void openImageChooser() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "选择头像"), PICK_IMAGE_REQUEST);
    }

    private void showNicknameInputDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setTitle("修改昵称");

        final EditText input = new EditText(requireContext());
        input.setText(nickName);

        input.setTextSize(20);
        input.setBackground(getResources().getDrawable(R.drawable.custom_textinput_background));
        input.setPadding(30, 20, 30, 20);

        FrameLayout container = new FrameLayout(requireContext());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(50, 30, 50, 30);
        input.setLayoutParams(params);
        container.addView(input);

        builder.setView(container);

        builder.setPositiveButton("确定", (dialog, which) -> {
            String newNickname = input.getText().toString().trim();
            if (!newNickname.isEmpty() && !newNickname.equals(nickName)) {
                updateNickname(newNickname);
            }
        });
        builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void updateNickname(String newNickname) {
        GetDataByThread getDataByThread = new GetDataByThread("/auth/updateUserNickname");
        getDataByThread.updateUserNickname(new Handler(Looper.getMainLooper()), msg_success, msg_failed,
                String.valueOf(userId), newNickname);
        nickName = newNickname;
        nickNameText.setText(newNickname);

        innerSettingsManager.setNickName(newNickname);

        Toast.makeText(requireContext(), "昵称已更新", Toast.LENGTH_SHORT).show();
    }

    private Uri copyImageToInternalStorage(Uri uri) {
        try {
            InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
            if (inputStream == null)
                return null;

            String fileName = "temp_avatar_" + System.currentTimeMillis() + ".jpg";
            File outputFile = new File(requireContext().getCacheDir(), fileName);

            FileOutputStream outputStream = new FileOutputStream(outputFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            inputStream.close();
            outputStream.close();

            return Uri.fromFile(outputFile);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null
                && data.getData() != null) { // 获取图片的uri
            Uri imageUri = data.getData();
            Uri copiedImageUri = copyImageToInternalStorage(imageUri);

            if (copiedImageUri != null) {
                // 显示选中的新图片作为预览
                Glide.with(this).load(copiedImageUri).placeholder(R.drawable.default_avatar)
                        .error(R.drawable.default_avatar).circleCrop().into(userAvatar);
            } else {
                Glide.with(this).load(imageUri).placeholder(R.drawable.default_avatar).error(R.drawable.default_avatar)
                        .circleCrop().into(userAvatar);
            }

            // 优先使用复制到内部的URI，确保文件可读；复制失败则使用原始URI
            Uri uploadUri = (copiedImageUri != null) ? copiedImageUri : imageUri;

            GetDataByThread getDataByThread = new GetDataByThread("/auth/uploadUserAvatar");
            getDataByThread.uploadUserAvatar(new Handler(Looper.getMainLooper()) {
                @Override
                public void handleMessage(@NonNull Message msg) {
                    if (msg.what == msg_success) {
                        String avatarPath = "/auth/avatar/{userId}".replace("{userId}", String.valueOf(userId));
                        GetDataByThread avatarLoader = new GetDataByThread(avatarPath);
                        String avatarUrl = avatarLoader.getUrl_path();
                        Glide.get(requireContext()).clearMemory();
                        new Thread(() -> {
                            Glide.get(requireContext()).clearDiskCache();
                            // 在主线程中重新加载头像
                            requireActivity().runOnUiThread(() -> {
                                Glide.with(UserHomeFragment.this).load(avatarUrl).placeholder(R.drawable.default_avatar)
                                        .error(R.drawable.default_avatar).circleCrop().into(userAvatar);

                                Toast.makeText(requireContext(), "头像已更新", Toast.LENGTH_SHORT).show();
                            });
                        }).start();
                    } else {
                        Toast.makeText(requireContext(), "头像更新失败", Toast.LENGTH_SHORT).show();
                    }
                }
            }, msg_success, msg_failed, userId, uploadUri, requireContext());

        }
    }

    @Override
    public void onResume() { // 初始化视图
        super.onResume();
        // initView();
    }

    @SuppressLint("HandlerLeak")
    class MyHandler extends Handler {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
            case msg_success:
                String result = (String) msg.obj;
                JSONObject responseJson = null;
                try {
                    responseJson = new JSONObject(result);
                    Log.i("GetPlan", "--------" + result);
                    String code = responseJson.getString("code");
                    switch (code) {
                    case "200":
                        nickName = responseJson.getString("nickname");
                        userName = responseJson.getString("username");
                        avatarUrl = responseJson.getString("avatarUrl");
                        nickNameText.setText(nickName);
                        Log.i("avatarUrl", "--------" + avatarUrl);
                        if (!Objects.equals(avatarUrl, "default_avatar_url")) {
                            GetDataByThread getDataByThread = new GetDataByThread(
                                    "/auth/avatar/{userId}".replace("{userId}", String.valueOf(userId)));

                            // 加载用户头像
                            Glide.with(requireContext()).load(getDataByThread.getUrl_path())
                                    .placeholder(R.drawable.default_avatar).error(R.drawable.default_avatar)
                                    .circleCrop().into(userAvatar);
                        }
                        break;

                    case "500":
                        Toast.makeText(getContext(), "加载词书失败", Toast.LENGTH_SHORT).show();
                        break;
                    }
                } catch (JSONException e) {
                    Log.e("UserHomeFragment", "JSON parse error: " + result, e);
                }
                break;
            case msg_failed:
                Log.w("UserHomeFragment", "getUserInfo failed");
                break;

            }
        }
    }
}
