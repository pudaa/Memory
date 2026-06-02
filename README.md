# Memory — AI 英语学习助手

[![Platform](https://img.shields.io/badge/Platform-Android-brightgreen?logo=android)](https://android.com)
[![Language](https://img.shields.io/badge/Language-Java%2011-orange?logo=java)](https://java.com)
[![Min SDK](https://img.shields.io/badge/minSdk-26-blueviolet)](https://developer.android.com/about/versions/8.0)
[![Target SDK](https://img.shields.io/badge/targetSdk-34-blue)](https://developer.android.com/about/versions/14)
[![License](https://img.shields.io/badge/License-MIT-lightgrey)](LICENSE)

**Memory** 是一款面向英语学习者的 Android 应用，集成 **FSRS 间隔重复算法**、**AI 智能批改**、**语音评测**和 **AI 对话**等核心功能，帮助用户高效提升英语水平。

---

## 核心功能

| 模块 | 描述 | 核心能力 |
|------|------|----------|
| **每日单词学习** | FSRS 科学背词 | 选择题/填空题双模式、智能复习调度 |
| **听写练习** | 逐词播放+作答 | 单词/短语/句子三级听写、OCR 拍照识别 |
| **作文批改** | AI 语法纠错评分 | OCR 拍照提取、AI 自动批改 |
| **发音练习** | 跟读评测 | 音频录制上传、发音/流利度/语调评分 |
| **AI 对话** | 自由口语练习 | 文字/语音双模式、实时评分 |
| **每日阅读** | AI 生成阅读材料 | Markdown 渲染、生词标注、句子翻译 |
| **学习评估** | 数据仪表盘 | 掌握度分布、复习趋势、AI 建议 |
| **词书管理** | 60+ 词库支持 | CET4/6、IELTS、TOEFL、GRE 等 |

---

## 技术架构

### 架构概览

```
┌──────────────────────────────────────────┐
│           UI 层 (Activity/Fragment)              │
│  WordLearning  Dictation  Composition  ...    │
├──────────────────────────────────────────┤
│        GetDataByThread (异步请求封装)            │
│                    │                              │
│            HttpManager (Apache HttpClient)        │
├──────────────────────────────────────────┤
│              后端 API 服务                        │
└──────────────────────────────────────────┘
```

### 技术栈

| 层次 | 技术选型 |
|------|----------|
| **UI 框架** | AndroidX AppCompat, Material Design, ConstraintLayout |
| **网络请求** | Apache HttpClient (自定义 HttpManager 封装) |
| **图片加载** | Glide 4.12.0 |
| **图片裁剪** | uCrop 2.2.8 (Yalantis) |
| **Markdown** | Markwon 4.6.2 |
| **图表** | MPAndroidChart 3.1.0 |
| **对话框** | Material Dialogs 3.3.0 (afollestad) |
| **音频** | MediaPlayer + AudioRecord (PCM 16bit 16kHz) |
| **本地词库** | Raw JSON 资源文件 (60+ 词库) |
| **AI 功能** | 由后端服务统一处理 |
| **TTS** | 有道词典 API |

### 数据流

```
用户操作 → Activity/Fragment
              ↓
         GetDataByThread (后台线程)
              ↓
         HttpManager (Apache HttpClient)
              ↓
            后端 API 服务
              ↓
         JSON 响应 → Handler.handleMessage() → UI 更新
```

---

## 项目结构

```
Memory/
├── AGENTS.md                       # AI 编码助手指南
├── build.gradle                    # 根构建配置
├── settings.gradle                 # Gradle 设置
├── gradle.properties               # Gradle 属性
├── docs/                           # 文档目录
├── gradle/wrapper/                 # Gradle Wrapper
└── app/
    ├── build.gradle                # 应用构建配置
    ├── proguard-rules.pro          # 混淆规则
    ├── libs/                       # 本地 JAR 库
    └── src/
        ├── androidTest/            # 仪器化测试
        ├── test/                   # 单元测试
        └── main/
            ├── AndroidManifest.xml
            ├── res/
            │   ├── layout/         # 布局文件
            │   ├── drawable/       # 图片资源
            │   ├── anim/           # 动画
            │   ├── raw/            # 词库 JSON
            │   └── xml/            # XML 配置
            └── java/com/deepsleep/memory/
                ├── network/        # 网络通信层
                │   ├── ApiConstants.java    # 环境切换
                │   ├── HttpManager.java     # HTTP 客户端
                │   ├── GetDataByThread.java # API 封装
                │   └── CozeAPI.java        # AI 对接
                ├── settings/       # 设置管理
                │   ├── UserSettingsManager.java
                │   └── InnerSettingsManager.java
                ├── handle_utils/   # 工具类
                │   ├── BitmapManager.java
                │   ├── AudioPlayer.java
                │   ├── MemAudioRecord.java
                │   └── lexicon/    # 本地词库
                └── ui/             # 界面层
                    ├── MainActivity.java
                    ├── auth_view/          # 登录/注册
                    ├── init_view/          # 词书选择/计划
                    ├── main_view/          # 4 Tab 主界面
                    │   ├── WordLearningFragment.java
                    │   ├── TreasureBoxFragment.java
                    │   ├── DailyReadingFragment.java
                    │   └── UserHomeFragment.java
                    ├── treasure_view/      # 宝藏箱子模块
                    │   ├── dictation_view/
                    │   ├── composition_view/
                    │   ├── pronunciation_view/
                    │   ├── aichat_view/
                    │   └── evaluation_view/
                    └── extra_view/         # 扩展功能
                        ├── my_word_view/
                        ├── word_search_view/
                        ├── plan_view/
                        └── setting_view/
```

---

## 快速开始

### 环境要求

- **Android Studio** Hedgehog (2023.1.1) 或更高版本
- **JDK** 11+
- **Gradle** 8.10.2 (使用项目内置的 Gradle Wrapper)
- **Android SDK** API Level 34

### 构建与运行

```bash
# 克隆项目
git clone https://github.com/pudaa/Memory.git
cd Memory

# 编译 Debug APK
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug

# 编译 Release APK
./gradlew assembleRelease

# 运行单元测试
./gradlew test

# 运行仪器化测试
./gradlew connectedAndroidTest
```

### 打开项目

1. 打开 Android Studio
2. 选择 **File -> Open**
3. 导航到项目根目录并选中
4. 等待 Gradle 同步完成
5. 点击 **Run** 按钮运行

---

## 环境配置

### API 环境切换

项目支持 **DEV / TEST / PROD** 三套环境，默认使用 TEST 环境：

```java
// 在 GetDataByThread 构造函数中默认设置
ApiConstants.setEnvironment(ApiConstants.Environment.TEST);

// 切换到生产环境
ApiConstants.setEnvironment(ApiConstants.Environment.PROD);
```

| 环境 | 地址 |
|------|------|
| **DEV** | `http://192.168.102.14:8080` |
| **TEST** | `http://frp-fit.com:60966` |
| **PROD** | `http://116.62.6.15:8080` |

### 权限说明

应用需要以下权限：
- `INTERNET` — API 通信
- `CAMERA` — OCR 拍照识别
- `RECORD_AUDIO` — 发音练习录音
- `READ/WRITE_EXTERNAL_STORAGE` — 图片保存与裁剪

---

## 用户流程

```
注册/登录 -> 选择词书 -> 制定学习计划 -> 主界面
                                          |- Tab0: 每日单词学习 (FSRS)
                                          |- Tab1: 宝藏箱 (听写/作文/发音/AI对话/评估)
                                          |- Tab2: 每日阅读 (AI生成)
                                          |- Tab3: 用户中心 (设置/词书/计划)
```

---

## 设计亮点

- **FSRS 间隔重复**：服务端根据记忆状态（可提取性、难度、稳定性）动态调度复习
- **逐词听写**：支持键盘输入 + OCR 拍照识别，智能过滤提示词
- **卡片式学习**：自定义滑动卡片容器，支持选择题/填空题双模式
- **多源查词**：集成 Bing、牛津、剑桥词典及本地词库四源查询
- **Handler 异步模式**：统一的后台线程 + Handler 回调，保持代码风格一致

---

## 文档

- [项目技术文档](docs/project-technical-documentation.md) — 详细的技术架构说明

---

## 贡献

欢迎提出问题和改进建议！请提交 Issue 或 Pull Request。

---

## 许可证

[MIT License](LICENSE)

Copyright (c) 2026 pudaa
