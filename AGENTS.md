# AGENTS.md - AI Coding Assistant Guide for Memory App

> **⚠️ 本文件是 AI 辅助开发的强制入口：动手前必须先建立全局观，再写代码。**

## 📚 文档导航（先读这些）

- **`docs/project-overview.md`** — 项目全景总览 + **已有能力清单**（动手前必读，防止重复造轮子）
- **`docs/development-conventions.md`** — 开发约束与规范（红线，必须遵守）
- **`docs/project-technical-documentation.md`** — 模块技术细节、架构、API 清单

## 🎯 核心准则：先看全局，再动局部

**本项目最大的历史教训**：开发者/AI 只看到单个模块，导致重复造轮子（如到处新建 SharedPreferences 调用点，结构破碎）与模块间冲突。请务必：

1. **动手前**先读 `docs/project-overview.md` 的「已有基础设施清单」，并 `grep` 代码库确认没有现成实现；
2. **持久化一律走 settings 管理器**（`UserSettingsManager` / `InnerSettingsManager` / `DailyStateManager`），禁止在 Activity/Fragment 中直接 `getSharedPreferences()`；
3. **网络一律走** `HttpManager` / `GetDataByThread`，禁止引入 OkHttp / Retrofit；
4. 改动某模块前评估对其他模块的影响（网络层、设置层、词库层、`MainActivity` 导航、工具类）；
5. 涉及跨模块能力时，**扩展已有管理器/工具类**，不要新建散落实现；
6. 改动核心架构 / 新增持久化键 / 新增 API 时，同步更新 `docs/` 相关文档。

## Project Overview
Memory is an Android language learning application with features for vocabulary acquisition, composition writing/correction, pronunciation practice, and daily reading. The app integrates with backend APIs (MemoryServer + MemoryServerTTS) and AI for content generation. 详见 `docs/project-overview.md`。

## Architecture & Key Components

### Core Structure
- **Main Activity**: `MainActivity.java` implements a 4-tab bottom navigation (Word Learning, Treasure Box, Daily Reading, User Home) using Fragments with slide animations
- **Package Organization**: 
  - `ui/` - Activities and Fragments organized by feature (auth_view, treasure_view, etc.)
  - `network/` - API communication (HttpManager, GetDataByThread, CozeAPI)
  - `handle_utils/` - Utilities for audio, images, and data processing
  - `settings/` - User preferences and configuration

### Data Flow
- **Networking**: Custom `HttpManager` class using Apache HttpClient (deprecated) for all HTTP requests
- **Async Operations**: `GetDataByThread` creates background threads with Handler callbacks for API responses
- **AI Integration**: `CozeAPI` handles chat-based AI interactions for content generation

## Critical Developer Workflows

### Build & Run
```bash
# Build APK
.\gradlew.bat assembleDebug

# Install and run on device
.\gradlew.bat installDebug

# Build release APK
.\gradlew.bat assembleRelease
```

### Environment Configuration
- **API Environments**: Controlled via `ApiConstants.setEnvironment()` (DEV/TEST/PROD)
- **Default Environment**: `GetDataByThread` constructor sets TEST environment
- **URLs**: `http://frp-pet.com:60966` (TEST), `http://116.62.6.15:8080` (PROD)

### Testing
- **Unit Tests**: `app/src/test/` (JUnit 4.13.2)
- **Instrumentation Tests**: `app/src/androidTest/` (Espresso 3.5.1)
- Run: `.\gradlew.bat test` or `.\gradlew.bat connectedAndroidTest`

## Project-Specific Patterns & Conventions

### Networking Pattern
**Always use the custom networking stack instead of modern libraries:**
```java
// WRONG - Don't use OkHttp or Retrofit
OkHttpClient client = new OkHttpClient();

// CORRECT - Use HttpManager via GetDataByThread
GetDataByThread api = new GetDataByThread("/user/login");
api.login(handler, SUCCESS_MSG, FAIL_MSG, phone, password);
```

**Handler-based async callbacks:**
```java
Handler handler = new Handler(Looper.getMainLooper()) {
    @Override
    public void handleMessage(@NonNull Message msg) {
        if (msg.what == SUCCESS_MSG) {
            String result = (String) msg.obj;
            // Process JSON response
        }
    }
};
```

### Fragment Navigation
**Bottom tab navigation with animations:**
- Use `FragmentTransaction.setCustomAnimations()` for slide transitions
- Hide/show fragments instead of replace for performance
- Reference: `MainActivity.java` lines 63-127

### Image & Media Handling
**Custom utilities for media processing:**
- `BitmapManager` for image decoding/loading
- `AudioPlayer` and `MemAudioRecord` for audio playback/recording
- Multipart uploads via `HttpManager.doHttpPostWithImageUri()`

### JSON Processing
**Manual JSONObject parsing (no Gson/Moshi):**
```java
JSONObject response = new JSONObject(result);
if (response.getString("code").equals("200")) {
    JSONArray data = response.getJSONArray("data");
    // Process array
}
```

### Dialog Pattern
**Always use MaterialAlertDialogBuilder for all dialogs:**
```java
// WRONG - Don't use AlertDialog.Builder
AlertDialog.Builder builder = new AlertDialog.Builder(context);
// or
new androidx.appcompat.app.AlertDialog.Builder(context)...

// CORRECT - Use MaterialAlertDialogBuilder for consistent Material Design styling
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

new MaterialAlertDialogBuilder(context)
        .setTitle("标题")
        .setMessage("消息内容")
        .setPositiveButton("确定", (dialog, which) -> { /* action */ })
        .setNegativeButton("取消", null)
        .show();
```
- Import: `com.google.android.material.dialog.MaterialAlertDialogBuilder`
- Always use lambda syntax for button click listeners
- This ensures consistent theming (light/dark mode, rounded corners, ripple effects) across all dialogs

### Permissions & Features
**Required permissions for core features:**
- `CAMERA` + `WRITE_EXTERNAL_STORAGE` for composition OCR
- `RECORD_AUDIO` for pronunciation practice
- `INTERNET` for all API communication

### Settings & Persistence (集中管理)
**所有持久化访问必须经过 settings 管理器，禁止直接操作 SharedPreferences：**
```java
// WRONG - Don't create new SharedPreferences in UI layer
SharedPreferences sp = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);

// CORRECT - 用户偏好走 UserSettingsManager
UserSettingsManager.getInstance(context).setReaderFontSize(size);

// CORRECT - 内部信息/收藏/草稿/成绩走 InnerSettingsManager
int userId = InnerSettingsManager.getInstance(context).getUserId();
```
- **用户偏好**（学习模式、滑动方向、每日新词、字号、主题）→ `UserSettingsManager`
- **应用内部信息**（登录态、userId、昵称、头像、每日收藏、作文草稿、发音成绩）→ `InnerSettingsManager`
- **学习进度**（今日已完成单词）→ `DailyStateManager`
- 新增持久化需求：先在对应管理器中**扩展方法**，不要新开 SharedPreferences 文件/调用点

## Key Files & Directories
- `docs/` - 项目文档（总览 / 约束 / 技术文档）
- `app/build.gradle` - Dependencies (Glide, Material, uCrop, Markwon, CameraX 1.3.4)
- `app/src/main/AndroidManifest.xml` - 15+ activities, file provider config
- `settings/UserSettingsManager.java` - 用户偏好单例 + 观察者
- `settings/InnerSettingsManager.java` - 应用内部信息记录器（集中持久化入口）
- `network/ApiConstants.java` - Environment switching
- `network/HttpManager.java` - 20+ HTTP methods
- `network/GetDataByThread.java` - 60+ business API methods + Handler callbacks
- `ui/MainActivity.java` - Tab navigation implementation
- `handle_utils/BitmapManager.java` - Image processing utilities

## Common Pitfalls
- **No text emojis in UI strings** - Never use unicode emoji characters (📊📈📝🔍 etc.) in `setText()`, layout XML `android:text`, or any user-facing strings. They render inconsistently across Android versions and break visual consistency. Use proper `ImageView` with drawable icons or Material icon fonts instead.
- **Don't update Apache HttpClient** - Despite deprecation warnings, maintain compatibility
- **Environment hardcoded** - Remember to switch between TEST/PROD for API calls
- **Threading model** - Use Handler/Message pattern, avoid AsyncTask (deprecated)
- **Dependencies** - Stick to specified versions, avoid auto-updating without testing

## AI Integration
**Coze API for content generation:**
- Bot-based chat interface for question answering
- Streaming responses with status polling
- Reference: `CozeAPI.java` for async AI request handling</content>
<parameter name="filePath">D:\Codes\Memory\AGENTS.md
