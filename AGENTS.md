# AGENTS.md - AI Coding Assistant Guide for Memory App

## Project Overview
Memory is an Android language learning application with features for vocabulary acquisition, composition writing/correction, pronunciation practice, and daily reading. The app integrates with a backend API and Coze AI for content generation.

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

## Key Files & Directories
- `app/build.gradle` - Dependencies (Glide, Material Dialogs, uCrop, Markwon)
- `app/src/main/AndroidManifest.xml` - 15+ activities, file provider config
- `network/ApiConstants.java` - Environment switching
- `network/HttpManager.java` - 20+ HTTP methods for different request types
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
