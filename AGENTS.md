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
3. **网络一律走 `network/` 包**（`MemoryApiClient` 统一入口 + `ApiBridge` 桥接 + 各域 Retrofit 接口；原 `HttpManager` / `GetDataByThread` 已于 2026-08 合并删除），底层统一 OkHttp 连接池（`MemoryApiClient.client()`）；禁止在 UI 层直接 new OkHttpClient / 裸 HttpURLConnection；
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
  - `network/` - API communication (MemoryApiClient 统一入口, ApiBridge 桥接, 域接口)
  - `handle_utils/` - Utilities for audio, images, and data processing
  - `settings/` - User preferences and configuration

### Data Flow
- **Networking**: `network/` 包统一基于 **OkHttp**（`MemoryApiClient` 为唯一入口，持有单一共享连接池 `client()`），HTTP 请求一律经 `ApiBridge.enqueue(MemoryApiClient.域().xxx(...), handler, ok, fail, tag)` 桥接
- **Async Operations**: 网络任务在共享线程池（`ApiConstants.execute`）上执行，Handler/Message 回调返回 API 响应（2xx 非空体 → `sendMessage(ok, body)`；网络失败 → 指数退避重试 1s/2s 后 `sendEmptyMessage(fail)`）
- **AI Integration**: AI 内容生成（作文批改、文章生成、听写/对话生成、评估分析）由后端 MemoryServer 提供，客户端经 `CompositionApi`/`ConversationApi`/`LearningApi`/`EvaluationApi` 域接口调用

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
- **API Environments**: Controlled via `ApiConstants.setEnvironment()` (DEV/TEST/PROD)；默认 **TEST**
- **URL 拼接**: 一律走 `ApiConstants.getFullUrl(path)`，禁止手写 `getBaseUrl() + "/xxx"`
- **运行期切换**: `setEnvironment()` 后旧栈按调用时解析 URL、新栈 `MemoryApiClient` 自动重建 Retrofit，立即生效
- **URLs**: 实际地址由 `local.properties` 注入 BuildConfig（`BACKEND_DEV_URL`/`BACKEND_TEST_URL`/`BACKEND_PROD_URL`），运行时以 `ApiConstants` 为准；当前 TEST=`frp-fit.com:60966`、PROD=`116.62.6.15:8080`

### Testing
- **Unit Tests**: `app/src/test/` (JUnit 4.13.2)
- **Instrumentation Tests**: `app/src/androidTest/` (Espresso 3.5.1)
- Run: `.\gradlew.bat test` or `.\gradlew.bat connectedAndroidTest`

## Project-Specific Patterns & Conventions

### Networking Pattern
**所有 HTTP 请求统一走 `network/` 包（底层 OkHttp，单一共享连接池），禁止在 UI 层自建客户端：**
```java
// WRONG - 禁止在 UI 层直接 new OkHttpClient / 裸 HttpURLConnection
OkHttpClient client = new OkHttpClient();

// CORRECT - 业务请求统一走 ApiBridge + MemoryApiClient（域接口），
//          Handler/Message 回调保持旧语义（2xx 非空体 → ok，网络失败重试后 → fail）
ApiBridge.enqueue(MemoryApiClient.auth().login(ApiBridge.formPart(phone), ApiBridge.formPart(password)),
        handler, SUCCESS_MSG, FAIL_MSG, "Login");

// CORRECT - 底层直连（SSE/TTS/下载/上传 等）走 MemoryApiClient 静态底层方法，URL 用 ApiConstants.getFullUrl(path)
String url = ApiConstants.getFullUrl("/conversation/stream");
MemoryApiClient.postStream(url, headers, form);
```
- **URL 拼接**：一律 `ApiConstants.getFullUrl(path)`；禁止 `getBaseUrl() + "/xxx"`
- **异步执行**：网络任务一律 `ApiConstants.execute(runnable)`（共享线程池），禁止 `new Thread`
- **环境切换**：`ApiConstants.setEnvironment()` 立即全局生效（默认 TEST）

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
- Multipart uploads via `ApiBridge.filePart()`（流式文件体，经 Retrofit 域接口上传）

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
- `network/ApiConstants.java` - 环境中间件: DEV/TEST/PROD + URL 拼接 + 共享网络线程池
- `network/MemoryApiClient.java` - 网络层唯一入口：持有单一共享 OkHttpClient `client()`（连接 15s/读写 120s），含 Retrofit 域接口工厂（auth/learning/composition/conversation/evaluation/pronunciation）+ 底层专用能力（postStream SSE / downloadWav TTS / doHttpGetNoPara / streamingPart 流式文件体），环境切换自动重建
- `network/ApiBridge.java` - Handler/Message 桥接层：enqueue 语义与历史一致，multipart 上传经 filePart 流式文件体
- `network/MemoryApi.java` / 域接口（AuthApi 等）- Retrofit 声明式接口
- `ui/MainActivity.java` - Tab navigation implementation
- `handle_utils/BitmapManager.java` - Image processing utilities

## Common Pitfalls
- **No text emojis in UI strings** - Never use unicode emoji characters (📊📈📝🔍 etc.) in `setText()`, layout XML `android:text`, or any user-facing strings. They render inconsistently across Android versions and break visual consistency. Use proper `ImageView` with drawable icons or Material icon fonts instead.
- **网络层只认 OkHttp** - 已统一为 OkHttp 单一连接池（`MemoryApiClient.client()` 唯一持有），不要再引入 Apache HttpClient / HttpURLConnection 直连
- **URL 拼接统一走 ApiConstants.getFullUrl()** - 禁止 `getBaseUrl() + "/xxx"` 手拼，维护时混淆
- **不要 new Thread 做网络** - 统一 `ApiConstants.execute()`（共享网络线程池）
- **Environment hardcoded** - Remember to switch between TEST/PROD for API calls (default TEST; 运行时 setEnvironment 立即生效)
- **Threading model** - Use Handler/Message pattern, avoid AsyncTask (deprecated)
- **Dependencies** - Stick to specified versions, avoid auto-updating without testing

## AI Integration
AI 能力（内容生成、批改、评估）统一由后端 MemoryServer 提供，客户端经 Retrofit 域接口（`CompositionApi`/`ConversationApi`/`LearningApi`/`EvaluationApi`）调用，底层复用共享 OkHttp 连接池；客户端不再直连第三方 AI 平台（`CozeAPI` 已删除，勿再引入直连方案）。</content>
<parameter name="filePath">D:\Codes\Memory\AGENTS.md
