# Memory App — 项目技术文档

> **版本**: 1.0  
> **生成日期**: 2026-05-29  
> **目标平台**: Android (minSdk 26, targetSdk 34)  
> **开发语言**: Java 11  

---

## 目录

1. [项目概述](#1-项目概述)
2. [技术架构总览](#2-技术架构总览)
3. [项目结构](#3-项目结构)
4. [认证模块](#4-认证模块)
5. [初始化设置模块](#5-初始化设置模块)
6. [主界面与导航系统](#6-主界面与导航系统)
7. [每日单词学习模块](#7-每日单词学习模块)
8. [宝藏箱 — 听写模块](#8-宝藏箱--听写模块)
9. [宝藏箱 — 作文批改模块](#9-宝藏箱--作文批改模块)
10. [宝藏箱 — 发音练习模块](#10-宝藏箱--发音练习模块)
11. [宝藏箱 — AI 对话模块](#11-宝藏箱--ai-对话模块)
12. [宝藏箱 — 学习评估模块](#12-宝藏箱--学习评估模块)
13. [每日阅读模块](#13-每日阅读模块)
14. [用户中心模块](#14-用户中心模块)
15. [扩展功能模块](#15-扩展功能模块)
16. [网络通信层](#16-网络通信层)
17. [工具与辅助模块](#17-工具与辅助模块)
18. [设置管理系统](#18-设置管理系统)
19. [第三方依赖](#19-第三方依赖)
20. [构建与部署](#20-构建与部署)

---

## 1. 项目概述

### 1.1 项目简介

**Memory** 是一款面向英语学习者的 Android 应用，集成了词汇学习、听写练习、作文批改、发音训练、AI 对话和每日阅读等多种学习功能。应用后端对接自建 API 服务，通过 FSRS（Free Spaced Repetition System）间隔重复算法科学安排复习计划。

### 1.2 核心功能

| 功能模块 | 描述 | 核心能力 |
|----------|------|----------|
| 每日单词学习 | FSRS 间隔重复背词 | 选择题/填空题双模式、智能复习调度 |
| 听写练习 | 音频播放 + 手写/键盘作答 | 多级听写（单词/短语/句子）、OCR 拍照识别、UCrop 裁剪 |
| 作文批改 | AI 语法纠错与评分 | OCR 拍照提取文字、AI 自动批改 |
| 发音练习 | 跟读评测 | 录音上传、发音/流利度/语调评分 |
| AI 对话 | 自由口语练习 | 文字/语音双模式、会话管理 |
| 每日阅读 | AI 生成阅读材料 | Markdown 渲染、生词标注、句子翻译 |
| 学习评估 | 数据仪表盘 | 掌握度分布、复习趋势、AI 建议 |
| 词书管理 | 60+ 词库支持 | CET4/6、IELTS、TOEFL、GRE 等 |

### 1.3 用户流程

```
注册/登录 → 选择词书 → 制定学习计划 → 主界面
                                          ├─ Tab0: 每日单词学习 (FSRS)
                                          ├─ Tab1: 宝藏箱 (听写/作文/发音/AI对话/评估)
                                          ├─ Tab2: 每日阅读 (AI生成)
                                          └─ Tab3: 用户中心 (设置/词书/计划)
```

---

## 2. 技术架构总览

### 2.1 架构模式

项目采用 **传统 MVC + Fragment 导航** 架构，未使用 MVVM/ViewModel 等现代架构模式。核心特点：

- **Activity 容器 + Fragment 内容**：`MainActivity` 承载 4 个底部 Tab，每个 Tab 对应一个 Fragment
- **Handler + Message 异步模式**：所有网络请求通过后台线程执行，通过 `Handler` 将结果回传主线程更新 UI
- **Singleton 管理器**：`UserSettingsManager`、`InnerSettingsManager` 使用单例模式管理全局状态
- **SharedPreferences 持久化**：用户偏好、登录态、学习进度均通过 SharedPreferences 存储

### 2.2 技术栈

| 层次 | 技术选型 |
|------|----------|
| UI 框架 | AndroidX AppCompat, Material Design, ConstraintLayout |
| 网络请求 | Apache HttpClient (已废弃，但项目保持兼容) |
| 图片加载 | Glide 4.12.0 |
| 图片裁剪 | uCrop 2.2.8 (Yalantis) |
| Markdown 渲染 | Markwon 4.6.2 |
| 图表展示 | MPAndroidChart 3.1.0 |
| 对话框 | Material Dialogs 3.3.0 |
| 音频播放 | Android MediaPlayer + 有道词典 TTS |
| 音频录制 | AudioRecord (PCM 16bit 16kHz) |
| 本地词库 | Raw JSON 资源文件 |
| AI 集成 | Coze API (流式对话) |

### 2.3 数据流

```
用户操作 → Activity/Fragment
              ↓
         GetDataByThread (后台线程)
              ↓
         HttpManager (Apache HttpClient)
              ↓
         后端 API 服务 / Coze AI
              ↓
         JSON 响应
              ↓
         Handler.handleMessage() → UI 更新
```

---

## 3. 项目结构

```
d:\Codes\Memory\
├── AGENTS.md                          # AI 编码助手指南
├── build.gradle                       # 根构建配置
├── settings.gradle                    # Gradle 设置
├── gradle.properties                  # Gradle 属性
├── local.properties                   # 本地 SDK 路径
├── docs/                              # 文档目录
│   └── server-issue-report-word-learning.md
├── gradle/wrapper/                    # Gradle Wrapper
└── app/
    ├── build.gradle                   # 应用构建配置（依赖管理）
    ├── proguard-rules.pro             # 混淆规则
    ├── libs/                          # 本地 JAR 库
    ├── sampledata/                    # 示例数据
    └── src/
        ├── androidTest/               # 仪器化测试 (Espresso)
        ├── test/                      # 单元测试 (JUnit)
        └── main/
            ├── AndroidManifest.xml    # 应用清单
            ├── res/                   # 资源文件
            │   ├── layout/            # 布局文件 (30+)
            │   ├── drawable/          # 图片/矢量图
            │   ├── anim/              # 动画资源
            │   ├── raw/               # 原始资源（词库JSON）
            │   └── xml/               # XML 配置 (file_paths)
            └── java/com/deepsleep/memory/
                ├── network/           # 网络通信层
                │   ├── ApiConstants.java
                │   ├── HttpManager.java
                │   ├── GetDataByThread.java
                │   └── CozeAPI.java
                ├── settings/          # 设置管理
                │   ├── UserSettingsManager.java
                │   └── InnerSettingsManager.java
                ├── handle_utils/      # 工具类
                │   ├── BitmapManager.java
                │   ├── AudioPlayer.java
                │   ├── MemAudioRecord.java
                │   ├── AdapterTool.java
                │   └── lexicon/       # 本地词库
                │       ├── LexiconResourceMap.java
                │       └── WordEntry.java
                └── ui/                # 界面层
                    ├── MainActivity.java
                    ├── components/    # 可复用组件
                    ├── auth_view/     # 登录/注册
                    ├── init_view/     # 词书选择/计划制定
                    ├── main_view/     # 主页Fragment
                    │   ├── WordLearningFragment.java
                    │   ├── TreasureBoxFragment.java
                    │   ├── DailyReadingFragment.java
                    │   └── UserHomeFragment.java
                    ├── treasure_view/ # 宝藏箱子模块
                    │   ├── dictation_view/    # 听写
                    │   ├── composition_view/  # 作文
                    │   ├── pronunciation_view/# 发音
                    │   ├── aichat_view/       # AI对话
                    │   └── evaluation_view/   # 学习评估
                    └── extra_view/    # 扩展功能
                        ├── my_word_view/     # 我的词书
                        ├── word_search_view/ # 查词
                        ├── plan_view/        # 计划管理
                        └── setting_view/     # 设置
```

---

## 4. 认证模块

### 4.1 设计思路

采用手机号+密码的传统认证方式，首次登录后检查是否有学习计划，无计划则引导用户选择词书并制定计划后再进入主界面。

### 4.2 涉及文件

| 文件 | 职责 |
|------|------|
| `LoginActivity.java` | 登录主界面，表单验证，密码找回 |
| `RegisterActivity.java` | 注册界面，自动生成昵称 |
| `GetDataByThread.java` | 封装 `/auth/login`、`/auth/register` API |
| `InnerSettingsManager.java` | 持久化登录态、用户信息 |

### 4.3 技术实现

#### 4.3.1 登录流程

```
用户输入手机号+密码
    ↓
performLogin() — 表单非空校验
    ↓
GetDataByThread.login(handler, SUCCESS, FAIL, phone, password)
    ↓ 后台线程
HttpManager.doHttpGetTwoHeader("/auth/login", "phone", phone, "password", password)
    ↓
解析 JSON 响应:
  code="200" → 提取 userId, nickName, avatarUrl
    → InnerSettingsManager 持久化用户信息
    → 设置登录标记: KEY_IS_LOGGED_IN = 2 (老用户)
    → 调用 /auth/getCurrentPlan 检查计划
        ├─ 有计划 → MainActivity
        └─ 无计划 → BookSelectActivity
  code≠"200" → Toast 提示错误
```

**登录状态管理** (`InnerSettingsManager.java`)：

```java
// 三种登录状态
KEY_IS_LOGGED_IN = 0  // 未登录
KEY_IS_LOGGED_IN = 1  // 首次注册后自动登录
KEY_IS_LOGGED_IN = 2  // 老用户（跳过引导）
```

每次进入 `MainActivity` 时检查 `KEY_IS_LOGGED_IN`，若为 0 则跳回 `LoginActivity`。

#### 4.3.2 注册流程

```
用户输入手机号+密码+确认密码
    ↓
表单校验: 密码一致性、非空
    ↓
自动生成 5 位随机昵称: String.format("%05d", random)
    ↓
GetDataByThread.register(handler, SUCCESS, FAIL, phone, password, nickname, avatarUrl)
    ↓
成功后返回 LoginActivity，设置 KEY_IS_LOGGED_IN = 1
```

#### 4.3.3 Handler 异步回调模式

所有网络请求采用统一的 `Handler + Message` 模式：

```java
// 定义消息码
private static final int msg_success = 1;
private static final int msg_failed = -1;

// Handler 处理
class MyHandler extends Handler {
    @Override
    public void handleMessage(@NonNull Message msg) {
        if (msg.what == msg_success) {
            String result = (String) msg.obj;
            JSONObject json = new JSONObject(result);
            // 解析并更新 UI
        } else {
            Toast.makeText(context, "请求失败", Toast.LENGTH_SHORT).show();
        }
    }
}
```

**设计考量**：项目刻意保持使用已废弃的 `android.os.Handler` + `Message` 模式，而非现代方案（如 Retrofit + LiveData），原因是保持与项目既有代码风格的一致性，同时避免引入新的依赖。

---

## 5. 初始化设置模块

### 5.1 设计思路

新用户首次登录后，需要完成两步初始化：(1) 选择一本词书；(2) 制定每日学习计划。这两步采用顺序 Activity 跳转，数据通过 Intent 传递。

### 5.2 涉及文件

| 文件 | 职责 |
|------|------|
| `BookSelectActivity.java` | 词书选择（60+ 词库筛选） |
| `PlanDevelopmentActivity.java` | 制定学习计划（每日新词数等） |
| `BookAdapter.java` | 词书列表适配器 |

### 5.3 技术实现

#### 5.3.1 词书选择

**数据源**：`BookSelectActivity.loadBooksFromJson()` 解析本地 JSON 配置，按标签（CET、IELTS、TOEFL 等）分类展示。

**UI 交互**：
- 顶部标签栏：动态生成 Tag Button，支持按标签筛选
- 列表项：显示书名、单词数量、描述
- 点击某一项 → Intent 携带 `bookTitle`, `bookWordCount`, `bookId` 到下一步

#### 5.3.2 学习计划制定

**可配置项**：
- 每日新学单词数（通过 NumberPicker 选择）
- 学习模式偏好（选择题/填空题）
- FSRS 保留率目标

**API 调用**：`POST /learning/createPlan`，请求体包含用户的完整计划 JSON。

**成功后**：设置 `KEY_IS_LOGGED_IN = 2`，跳转 `MainActivity`。

---

## 6. 主界面与导航系统

### 6.1 设计思路

采用 **4 Tab 底部导航** 的经典移动端布局，通过 Fragment 的 `show()/hide()` 而非 `replace()` 管理页面切换，实现以下优势：

- **内存高效**：Fragment 只实例化一次，切换时只需 show/hide，无需重建
- **状态保持**：切换 Tab 不会丢失当前页面的滚动位置和输入状态
- **切换动画**：根据切换方向（左/右）应用不同的滑入滑出动画

### 6.2 涉及文件

| 文件 | 职责 |
|------|------|
| `MainActivity.java` | 底部导航容器，Fragment 生命周期管理 |
| `activity_main.xml` | 主界面布局（4个 LinearLayout + FrameLayout） |
| `slide_in_left.xml` / `slide_out_right.xml` 等 | 切换动画资源 |

### 6.3 技术实现

#### 6.3.1 Tab 结构

```java
Tab 0 (linear1) → WordLearningFragment    // 每日单词学习
Tab 1 (linear2) → TreasureBoxFragment     // 宝藏箱（练习模块入口）
Tab 2 (linear3) → DailyReadingFragment    // 每日阅读
Tab 3 (linear4) → UserHomeFragment        // 用户中心
```

#### 6.3.2 Fragment 切换机制

```java
private void selectTab(int i) {
    FragmentManager manager = getSupportFragmentManager();
    FragmentTransaction transaction = manager.beginTransaction();

    // 根据方向选择动画
    if (i > currentTab) {
        transaction.setCustomAnimations(
            R.anim.slide_in_right,    // 新 Fragment 从右侧滑入
            R.anim.slide_out_left      // 旧 Fragment 向左滑出
        );
    } else if (i < currentTab) {
        transaction.setCustomAnimations(
            R.anim.slide_in_left,      // 新 Fragment 从左侧滑入
            R.anim.slide_out_right     // 旧 Fragment 向右滑出
        );
    }

    hideFragments(transaction);     // 隐藏所有
    currentTab = i;

    if (fragment == null) {
        transaction.add(R.id.id_content, fragment);  // 首次：添加
    } else {
        transaction.show(fragment);                   // 已存在：显示
    }
    transaction.commit();
}
```

---

## 7. 每日单词学习模块

### 7.1 设计思路

每日单词学习是应用的核心功能。基于 **FSRS（Free Spaced Repetition System）** 算法，服务端根据用户的记忆状态（可提取性、难度、稳定性）动态安排每日新词和复习词。客户端支持两种学习模式：
- **选择题模式**：4 选 1，降低认知负荷，适合初期学习
- **填空题模式**：手动输入拼写，加深记忆，适合巩固阶段

### 7.2 涉及文件

| 文件 | 职责 |
|------|------|
| `WordLearningFragment.java` | 每日学习主逻辑：任务加载、卡片管理、进度追踪 |
| `WordCard.java` | 单词数据模型（含 FSRS 字段） |
| `WordCardContainer.java` | 自定义可滑动卡片容器 |
| `ExerciseCardFactory.java` | 工厂模式生成选择题/填空题卡片视图 |
| `DailyStateManager.java` | 每日进度管理（跨天重置、断点恢复） |
| `SummaryCardBuilder.java` | 学习总结卡片构建 |

### 7.3 技术实现

#### 7.3.1 单词数据模型 (`WordCard.java`)

```java
class WordCard {
    long word_id;           // 单词 ID
    String word;            // 拼写
    String definition;      // 释义
    String example;         // 例句
    String usPhone/ukPhone; // 音标
    int type;               // 0=新词, 1=复习

    // FSRS 记忆状态
    float retrievability;   // 可提取性 (0-1)
    float difficulty;       // 难度 (1-10)
    float stability;        // 稳定性 (天数)
    int lastScore;          // 上次评分 (1-4: Again/Hard/Good/Easy)

    // 练习状态
    long displayStartTime;  // 卡片展示时间戳
    boolean isCorrect;      // 本次是否答对
}
```

#### 7.3.2 任务加载流程

```
WordLearningFragment.onResume()
    ↓
DailyStateManager.checkAndResetDailyState()
    ├─ 检测是否跨天 → 是: 重置完成列表
    └─ 否: 继续
    ↓
GetDataByThread 调用 GET /learning/getTodayTask?userId=X
    ↓
解析响应:
    wordList[wordId, headWord, R, D, S, lastScore]
    ↓
客户端过滤: DailyStateManager 排除已完成的单词
    ↓
构建 WordCard 列表: 新词(NEW) + 复习词(REVIEW)
    ↓
WordCardContainer 填充卡片
```

#### 7.3.3 卡片容器 (`WordCardContainer.java`)

**设计目标**：实现类似 Tinder 的滑动卡片效果，但保留子 View 的触摸交互。

**核心实现**：

- **触摸事件分发**：

```java
@Override
public boolean onInterceptTouchEvent(MotionEvent ev) {
    // 如果子 View 可交互（按钮/输入框），不拦截事件
    if (isInteractiveChild(targetChild)) {
        return false;
    }
    // 否则拦截，交由父容器处理滑动
    return true;
}
```

- **长按标记**：`Handler.postDelayed(runnable, 1000)` 实现 1 秒长按检测，用于标记单词。
- **卡片偏移**：`STACK_OFFSET_X = 60f`，卡片层叠偏移量，模拟卡片堆叠效果。
- **滑动动画**：`ValueAnimator` 驱动卡片平移 + 透明度变化，松手后根据阈值判断是否翻页。

#### 7.3.4 题目工厂 (`ExerciseCardFactory.java`)

**设计模式**：工厂方法模式，根据 `studyMode` 创建不同类型的题目视图。

**选择题模式**：

```
inflate(R.layout.word_card_choice_layout)
    ↓
生成 4 个选项 (1个正确 + 3个干扰项)
    ├─ 正确项: 当前单词的释义
    └─ 干扰项: 从词库随机选取 3 个其他单词的释义
    ↓
随机排列选项位置
    ↓
点击选项 → 高亮选中 → 点击"提交" → 判断对错 → 显示反馈
```

**填空题模式**：

```
inflate(R.layout.word_card_input_layout)
    ↓
EditText 输入框
    ├─ 用户输入拼写
    ├─ 按 Enter 键提交
    └─ 不区分大小写比较 (equalsIgnoreCase)
```

#### 7.3.5 每日状态管理 (`DailyStateManager.java`)

**设计目标**：跨天自动重置学习进度，进程被杀后恢复数据。

**存储策略**：

```java
// SharedPreferences 键名格式: "{userId}_{key}"
"123_completedWordIds"     → JSONArray 存储已完成的 wordId
"123_completedWordDetails" → JSONArray 存储完成详情(含正确性)
"123_lastStudyDate"        → String 存储最后学习日期
```

**跨天检测**：

```java
public boolean checkAndResetDailyState() {
    String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    String lastDate = sp.getString(userId + "_lastStudyDate", "");
    if (!today.equals(lastDate)) {
        // 新的一天：清除旧的完成列表
        sp.edit().putString(userId + "_completedWordIds", "[]").apply();
        sp.edit().putString(userId + "_lastStudyDate", today).apply();
        return true;
    }
    return false;
}
```

#### 7.3.6 答题提交流程

```
用户作答 → 判断对错 (客户端本地判断)
    ↓
计算答题耗时: System.currentTimeMillis() - displayStartTime
    ↓
POST /learning/submitAnswer
    请求体: {userId, wordId, lexiconId, headWord, isCorrect, responseTimeMs, studyMode}
    ↓
DailyStateManager.markCompletedWithResult(wordId, isCorrect)
    ├─ 更新 completedWordIds 列表
    └─ 追加 completedWordDetails (用于总结展示)
    ↓
下一张卡片 或 总结页（如果全部完成）
```

#### 7.3.7 总结卡片 (`SummaryCardBuilder.java`)

**数据恢复优先级**：
1. `persistedDetails`（SharedPreferences 持久化的，进程被杀后可恢复）
2. `currentCardMap`（内存中的，当前会话可用）
3. 词库重建（兜底方案）

**展示内容**：正确/错误统计、每个单词的拼写+释义、可点击播放发音。

---

## 8. 宝藏箱 — 听写模块

### 8.1 设计思路

听写模块模拟课堂听写场景：服务端生成听写任务（含音频），用户在手机端听音频后手写（纸上）或键盘输入答案。支持 **三级听写**（单词/短语/句子）和 **拍照 OCR 识别**手写答案。

核心设计目标：
- 逐词播放音频，自动重复 2 次，支持手动重听 1 次
- 支持键盘输入和 OCR 拍照识别两种作答方式
- 拍照后自动进入 UCrop 裁剪，用户框选答案区域
- OCR 结果智能过滤听写清单中的提示词后填入输入框作为预览

### 8.2 涉及文件

| 文件 | 职责 |
|------|------|
| `DictationMenuActivity.java` | 听写入口：历史记录 + 生成新任务 |
| `DictationGenerateActivity.java` | 任务生成：参数配置、音频轮询、PDF 打印 |
| `DictationExecutionActivity.java` | 核心执行：逐词播放、输入、OCR、提交 |
| `DictationResultActivity.java` | 成绩展示：评分、逐题详情、错词重练 |
| `DictationModels.java` | 数据模型：Task、Item、SubmitResult 等 |
| `DictationApiHelper.java` | API 封装：生成/查询/提交/历史 |

### 8.3 技术实现

#### 8.3.1 数据模型

```java
DictationTask {
    taskId: String         // 任务唯一标识
    status: String         // PENDING → READY → SUBMITTED
    cooldownUntil: String  // 冷却截止时间（ISO 8601）
    totalWords: int        // 单词总数
    items: List<DictationItem>
}

DictationItem {
    index: int             // 序号（1-based）
    wordId: long           // 词库中的单词 ID
    headWord: String       // 词条原形（提示/语境用）
    level: int             // 1=单词, 2=短语, 3=句子
    contextText: String    // 语境文本（L2/L3，含 ____ 占位符）
    targetForm: String     // 期望拼写（用户应写出的正确答案）
    posHint: String        // 词性提示
    audioUrl: String       // 音频 URL
    audioReady: boolean    // 音频是否生成完毕
}
```

**关键设计**：`headWord` ≠ `targetForm` 时（如 headWord="contain", targetForm="container"），`headWord` 是提示词，`targetForm` 是正确答案。

#### 8.3.2 任务生成流程

```
DictationGenerateActivity
    ↓
POST /learning/dictation/generate
    请求体: {userId, count, lexiconId}
    ↓
解析响应 → DictationTask
    ↓
enrichLocalInfo() — 从本地词书补全词性信息
    ↓
parseCooldownTime(cooldownUntil) — 解析冷却时间戳
    ↓
showPreview() — 显示任务预览列表
    ↓
轮询 (每 5 秒):
    GET /learning/dictation/{taskId}
    → 更新每个 item 的 audioReady 和 audioUrl
    → 检查冷却倒计时
    → 全部就绪 → 启用"开始听写"按钮
```

**冷却机制**：服务端控制听写任务的生成频率（防止滥用），客户端显示倒计时 `"冷却中 MM:SS"`。冷却结束后显示 `"准备就绪"`（绿色）。

**PDF 打印**：`printToPdf()` 使用 Android 原生 `PdfDocument` API 生成 A4 格式的听写单。布局为：
- **左侧提示区**：序号 + 词性/语境
- **右侧书写方格**：160pt 宽的空白框供手写
- **底部自评区**：打勾框

#### 8.3.3 听写执行流程

```
DictationExecutionActivity
    ↓
loadTask() → GET /learning/dictation/{taskId}
    ↓
parseTaskResult() → 构建 userAnswers 列表 (全空初始化)
                 → buildFilterWordSet() 构建 OCR 过滤黑名单
    ↓
showCurrentWord() — 逐词循环:
    ├─ 显示进度 "1 / 15"
    ├─ 显示语境/词性提示
    ├─ 自动播放音频 (MAX_AUTO_PLAY=2 次)
    │   ├─ MediaPlayer.setDataSource(audioUrl)
    │   ├─ prepareAsync() → onPrepared → start()
    │   ├─ onCompletion → autoPlayCount++
    │   │   └─ < 2 → postDelayed(playAudio, 3000ms)
    │   └─ onError → Toast 提示
    ├─ 用户输入/拍照
    │   ├─ EditText 手动输入
    │   ├─ Enter 键 → onNextWord()
    │   └─ btnScan → openCamera() → UCrop → OCR
    └─ saveCurrentAnswer() → currentIndex++ → 下一词
    ↓
全部完成 → showSubmitState()
    ↓
submitAnswers() → POST /learning/dictation/submit
    请求体: {taskId, answers: [{index, wordId, answer}, ...]}
    ↓
DictationResultActivity — 显示成绩
```

#### 8.3.4 拍照 OCR 完整流程

**设计演进背景**：最初 OCR 结果直接覆盖所有答案槽位，存在两个问题：
1. 听写单包含全部单词的提示词，OCR 会将提示词误认为答案
2. 多次拍照会互相覆盖

**最终方案**：逐词拍照 + UCrop 裁剪 + 黑名单过滤 + 单词预览

```
点击"拍当前词" (btnScan)
    ↓
openCamera()
    ├─ createImageFile() — 创建临时 JPG 文件
    ├─ FileProvider.getUriForFile() — 获取 content:// URI
    └─ startActivityForResult(ACTION_IMAGE_CAPTURE)
    ↓
拍照完成 → onActivityResult(REQUEST_CAMERA)
    ↓
startUCropActivity()
    ├─ 源 URI: 刚拍的照片
    ├─ 目标 URI: getCacheDir()/dict_cropped_xxx.jpg
    ├─ UCrop.Options: 自由裁剪, 90% 质量
    └─ UCrop.of(source, dest).start(REQUEST_CROP)
    ↓
用户手动框选答案区域 → 裁剪完成
    ↓
onActivityResult(REQUEST_CROP) → UCrop.getOutput(data)
    ↓
uploadForOcr(croppedUri)
    ├─ 后台线程:
    │   ├─ BitmapFactory.decodeStream() 解码
    │   ├─ JPEG 80% 压缩
    │   └─ HttpManager.doHttpPostWithImageUri() 上传
    └─ Handler → parseOcrResult()
    ↓
OCR 文本解析与过滤:
    ├─ 第一层: 正则提取 "序号. word" 模式 → candidates
    ├─ 第二层: 兜底按行提取 ≥2 字母的单词 → candidates
    ├─ 第三层: 过滤 filterWordSet (headWord 黑名单)
    │   └─ 注意: targetForm 不在黑名单中，正确答案不会被过滤
    └─ 取第一个有效候选词
    ↓
预览填入:
    ├─ etAnswer.setText(bestAnswer)  // 填入输入框供核对
    ├─ userAnswers[currentIndex].answer = bestAnswer  // 保存
    └─ Toast: "已识别并填入，请核对"
```

**过滤策略的核心考量**：

| 字段 | 是否加入黑名单 | 原因 |
|------|:--:|------|
| `headWord` | ✅ | 打印在听写单提示区，OCR 可能识别到，不应作为答案 |
| `targetForm` | ❌ | 这就是用户应写出的正确答案，绝不能过滤 |
| `contextText` 中的单词 | ❌ | 过滤范围过大可能误伤合法答案 |

**边界情况处理**：当 `headWord == targetForm` 时（如直接听写 "container"），输入框内的提示词和答案相同。此时依赖 **UCrop 裁剪**隔离——用户裁剪到右侧书写方格区域，左侧提示区不在裁剪范围内。

#### 8.3.5 音频播放控制

```java
// 状态机
autoPlayCount → 自动播放计数 (最大 2 次)
manualReplayCount → 手动重听计数 (最大 1 次)
isPlaying → 防止重叠播放

// 延迟播放
delayedPlayRunnable = () -> playAudio();
audioDelayHandler.postDelayed(delayedPlayRunnable, REPLAY_INTERVAL_MS); // 3 秒后重播

// 手动重听时取消自动延迟
if (delayedPlayRunnable != null) {
    audioDelayHandler.removeCallbacks(delayedPlayRunnable);
}
```

#### 8.3.6 错词重练

```
DictationResultActivity
    ↓
retryWrongWords()
    ↓
POST /learning/dictation/retry-wrong
    请求体: {userId, taskId, lexiconId}
    ↓
服务端返回新 DictationTask (仅包含错误单词)
    ↓
DictationGenerateActivity.setCachedTaskJson(json)
    ↓
重新打开 DictationGenerateActivity → 预填充缓存结果
```

---

## 9. 宝藏箱 — 作文批改模块

### 9.1 设计思路

作文批改模块提供两种文本输入方式：(1) **拍照 OCR** 识别手写作文；(2) **手动输入** 文字。提交后调用 AI 服务进行语法纠错和评分。

### 9.2 涉及文件

| 文件 | 职责 |
|------|------|
| `CompositionMenuActivity.java` | 入口：拍照/打字 + 历史记录 |
| `CompositionPreviewActivity.java` | 文字输入 + 提交批改 |
| `CompositionResultActivity.java` | AI 批改结果展示 |
| `CompositionRecord.java` | 作文记录数据模型 |
| `CompositionRecordAdapter.java` | 历史列表适配器 |

### 9.3 技术实现

#### 9.3.1 拍照 OCR 流程

```
CompositionMenuActivity
    ↓
dispatchTakePictureIntent()
    ├─ 检查 CAMERA 权限
    ├─ createImageFile() — JPEG_Composition_{timestamp}_.jpg
    ├─ FileProvider.getUriForFile()
    └─ startActivityForResult(REQUEST_IMAGE_CAPTURE)
    ↓
拍照完成 → startUCropActivity()
    ├─ UCrop.Options: 自由裁剪, 多个预设比例 (1:1, 3:2, 4:3, 16:9, A4)
    └─ withMaxResultSize(2048, 2048)
    ↓
裁剪完成 → uploadImageForOCR()
    ├─ GetDataByThread.extractTextFromImageUri()
    └─ Handler → 跳转 CompositionPreviewActivity(ocr_text)
```

#### 9.3.2 AI 批改

```
POST /composition/correctText
    请求体: {text, userId}
    ↓
AI 批改响应 → CompositionResultActivity
    展示: 总分、语法纠错、词汇建议、分段点评
```

**OCR 与听写模块的差异**：作文 OCR 直接传文本到下一个 Activity 供用户编辑，不涉及过滤逻辑；听写 OCR 需要过滤提示词并填入特定词槽。

---

## 10. 宝藏箱 — 发音练习模块

### 10.1 设计思路

提供两种发音练习：(1) **每日跟读**：7 个短语 + 7 个句子，播放示范音后用户跟读录音，AI 评测；(2) **AI 对话**：跳转到 AI 对话模块进行自由口语练习。

### 10.2 涉及文件

| 文件 | 职责 |
|------|------|
| `PronunciationMenuActivity.java` | 入口：选择练习模式 |
| `PronunciationMinuteFollowActivity.java` | 跟读练习主界面 |
| `PronunciationReportActivity.java` | 评分报告 |
| `WordPhraseItem.java` | 词条数据模型 |
| `WordPhraseListAdapter.java` | 词条列表适配器 |

### 10.3 技术实现

#### 10.3.1 跟读流程

```
GET /pronunciation/words?userId=X&bookId=2&phraseCount=7&sentenceCount=7
    ↓
RecyclerView 展示词条列表 (word + meaning)
    ↓
逐条练习:
    ├─ 点击播放示范音 (AudioPlayer 播放有道 TTS)
    ├─ 点击录音 (MemAudioRecord)
    │   ├─ startRecording(fileName) → 16kHz PCM
    │   └─ stopRecording() → WAV 文件
    └─ 提交评测:
        POST /pronunciation/correctPronunciation
        → 上传音频 + 参考文本
        → 返回评分: 发音准确度、流利度、语调
```

#### 10.3.2 MemAudioRecord

```java
// 录音配置
sampleRate = 16000 Hz
channelConfig = CHANNEL_IN_MONO
audioFormat = ENCODING_PCM_16BIT
bufferSize = AudioRecord.getMinBufferSize(sampleRate, channel, format)

// 录音线程: 循环读取 PCM 数据写入文件
while (isRecording) {
    audioRecord.read(buffer, 0, bufferSize);
    outputStream.write(buffer);
}
```

---

## 11. 宝藏箱 — AI 对话模块

### 11.1 设计思路

模拟真实英语对话场景。用户可与 AI 进行**文字**或**语音**聊天，AI 回复后附带动画语音播报，并对用户的语法、发音、流利度进行评分。

### 11.2 涉及文件

| 文件 | 职责 |
|------|------|
| `AiConversationActivity.java` | 对话主界面 |
| `AiMessage.java` | 消息数据模型 |
| `AiConversationAdapter.java` | 消息列表适配器 |
| `MemAudioRecord.java` | 录音与播放 |

### 11.3 技术实现

#### 11.3.1 会话管理

```
POST /conversation/session/create → sessionId
    ↓
文字消息: POST /conversation/message {sessionId, text}
语音消息: POST /conversation/audio {sessionId, audioFile}
    ↓
AI 回复 → AiMessage (type=ASSISTANT, content, audioUrl, scores)
    ↓
TTS 播报: POST /composition/synthesizeTts {text} → WAV → MediaPlayer 播放
```

#### 11.3.2 消息评分

```java
AiMessage {
    type: USER | ASSISTANT
    score: -1 (未评分) | 0-100
    pronunciationScore, fluencyScore, grammarScore, vocabularyScore
    level: excellent | good | fair | poor
}
```

---

## 12. 宝藏箱 — 学习评估模块

### 12.1 设计思路

提供数据驱动的学习分析仪表盘，帮助用户了解学习进度和薄弱环节。

### 12.2 涉及文件

| 文件 | 职责 |
|------|------|
| `EvaluationDashboardActivity.java` | 总览仪表盘 |
| `EvaluationTrendActivity.java` | 30/90/180 天趋势图 |
| `EvaluationWeeklyReportActivity.java` | 周报 |
| `EvaluationAiSuggestionActivity.java` | AI 学习建议 |
| `EvaluationDeepAnalysisActivity.java` | 深度分析 |

### 12.3 技术实现

**仪表盘数据**：

```
GET /evaluation/dashboard
    ↓
展示:
    ├─ 概览卡片: 学习天数、连续天数、总词数、掌握率
    ├─ FSRS 指标: 平均可提取性、难度、稳定性
    ├─ 饼图 (MPAndroidChart): 掌握度分布 (master/familiar/learning)
    └─ 折线图: 最近 7 天复习量
```

---

## 13. 每日阅读模块

### 13.1 设计思路

通过 **Coze AI** 生成适合用户水平的英语阅读材料，包含词汇分析和句子翻译，帮助用户在语境中学习单词。

### 13.2 涉及文件

| 文件 | 职责 |
|------|------|
| `DailyReadingFragment.java` | 阅读主界面 |
| `CozeAPI.java` | Coze AI 平台对接 |

### 13.3 技术实现

#### 13.3.1 Coze AI 集成

```java
CozeAPI coze = new CozeAPI(apiKey, botId);

coze.questionService(prompt, new QuestionCallback() {
    @Override
    public void onResult(String answer, String[] followUpQuestions) {
        // answer 是 Markdown 格式的文章
        // 使用 Markwon 渲染
    }

    @Override
    public void onError(String errorMessage) {
        // 错误处理
    }
});
```

**Coze API 调用流程**：

```
POST https://api.coze.cn/v3/chat
    Header: Authorization: Bearer {apiKey}
    Body: {bot_id, user_id, stream: false, additional_messages: [...]}
    ↓
获取 conversation_id + chat_id
    ↓
轮询 GET /v3/chat/retrieve?conversation_id=X&chat_id=Y
    状态: "in_progress" → 继续等待
    状态: "completed" → 下一步
    ↓
GET /v3/chat/message/list?conversation_id=X&chat_id=Y
    ↓
解析 AI 回复 → 通过 QuestionCallback 回调
```

#### 13.3.2 Markdown 渲染

使用 **Markwon** 库：
```java
Markwon markwon = Markwon.create(context);
markwon.setMarkdown(textView, markdownContent);
```

**Bot 配置**：API Key `pat_IIANC6ApULu0iK2AkEj8...`，Bot ID `7486395931509178405`。

---

## 14. 用户中心模块

### 14.1 设计思路

用户个人主页，展示头像和昵称，提供设置入口和次级功能导航。

### 14.2 涉及文件

| 文件 | 职责 |
|------|------|
| `UserHomeFragment.java` | 用户中心主界面 |
| `ManualDialogFragment.java` | 使用说明弹窗 |

### 14.3 技术实现

**头像上传**：使用 Glide 的 `circleCrop()` 显示圆形头像，通过 Image Picker 选择图片后 `POST /auth/updateUserAvatar`。

**昵称修改**：Material Dialogs 的输入弹窗，`POST /auth/updateUserNickname`。

**次级导航**：
- 我的词书 → `MyWordBookActivity`
- 设置 → `SettingActivity`
- 关于 → `ManualDialogFragment`
- 重新登录 → 清除 `InnerSettingsManager`，跳转 `LoginActivity`

---

## 15. 扩展功能模块

### 15.1 我的词书 (`my_word_view/`)

采用 **ViewPager2 + TabLayout** 实现两个子页面：
- **收藏单词**：`GET /learning/favoriteWords`
- **薄弱单词**：`GET /learning/weakWords`

### 15.2 查词功能 (`word_search_view/`)

**多源查词**：ViewPager2 承载 4 个 Fragment，分别从不同来源查询：
1. **本地词库**：`LexiconResourceMap.getWordByRank()`
2. **Bing 词典**：WebView 加载 `https://cn.bing.com/dict/search?q={word}`
3. **牛津词典**：WebView 加载牛津在线词典
4. **剑桥词典**：WebView 加载剑桥在线词典

### 15.3 计划管理 (`plan_view/`)

- **计划列表**：`PlanListActivity` 展示用户所有学习计划，支持切换
- **计划详情**：`PlanCheckActivity` 查看进度和预估完成日期

### 15.4 设置页 (`setting_view/`)

| 设置项 | 存储键 | 默认值 | 说明 |
|--------|--------|--------|------|
| 学习模式 | `study_mode` | `"choice"` | 选择题/填空题 |
| 每日新词数 | `daily_new_words` | `10` | 5-100, 步长 5 |
| 右滑返回 | `is_slide_back` | `true` | 卡片右滑回到上一张 |

**防抖策略**：设置变更后延迟 800ms 再调用 API（`/learning/updatePreference`），避免用户连续滑动时重复请求。

---

## 16. 网络通信层

### 16.1 设计思路

项目使用 **Apache HttpClient**（已废弃但保持兼容）+ 自定义 `HttpManager` + `GetDataByThread` 异步封装的三层网络架构。

### 16.2 涉及文件

| 文件 | 职责 |
|------|------|
| `ApiConstants.java` | 环境切换（DEV/TEST/PROD） |
| `HttpManager.java` | 底层 HTTP 客户端（20+ 方法） |
| `GetDataByThread.java` | 高层 API 封装（60+ 业务方法） |
| `CozeAPI.java` | Coze AI 平台对接 |

### 16.3 技术实现

#### 16.3.1 环境切换

```java
public enum Environment { DEV, TEST, PROD }

// DEV:  "http://192.168.102.14:8080"
// TEST: "http://frp-fit.com:60966"
// PROD: "http://116.62.6.15:8080"

ApiConstants.setEnvironment(Environment.TEST); // GetDataByThread 构造函数默认
```

#### 16.3.2 HttpManager 方法矩阵

**GET 请求**（无参到 5 参数）：
```java
doHttpGetNoPara(url)
doHttpGetOneHeader(url, key, value)
doHttpGetTwoHeader(url, k1, v1, k2, v2)
// ... 最多 5 个 header 参数
```

**POST 请求**（JSON Body）：
```java
doHttpPost(url, JSONObject)             // 纯 JSON 体
doHttpPost(url, key, value, JSONObject)  // JSON 体 + 1 Header
```

**Multipart 请求**（文件上传）：
```java
doHttpPostWithImageUri(url, uri, context)              // 图片上传 (OCR)
doHttpPostWithImageAndParams(url, uri, params, context) // 图片 + 表单
doHttpPostWithAudioAndText(url, audioUri, text, context) // 音频 + 文本
```

#### 16.3.3 GetDataByThread 统一模式

```java
public class GetDataByThread {
    private final String basePath;  // API 路径前缀

    public GetDataByThread(String basePath) {
        this.basePath = basePath;
        ApiConstants.setEnvironment(ApiConstants.Environment.TEST); // 默认测试环境
    }

    // 每个业务方法遵循统一模式:
    public void login(Handler handler, int successMsg, int failMsg,
                      String phone, String password) {
        new Thread(() -> {
            try {
                String url = ApiConstants.getBaseUrl() + "/auth/login";
                String result = HttpManager.doHttpGetTwoHeader(
                    url, "phone", phone, "password", password);
                sendResult(handler, successMsg, failMsg, result);
            } catch (Exception e) {
                handler.sendEmptyMessage(failMsg);
            }
        }).start();
    }
}
```

#### 16.3.4 API 端点汇总

| 模块 | 端点 | 方法 | 用途 |
|------|------|------|------|
| **Auth** | `/auth/login` | GET | 登录 |
| | `/auth/register` | POST | 注册 |
| | `/auth/getUserInfo` | GET | 获取用户信息 |
| | `/auth/updateUserAvatar` | POST | 上传头像 |
| | `/auth/updateUserNickname` | POST | 修改昵称 |
| | `/auth/getCurrentPlan` | GET | 获取当前计划 |
| | `/auth/setPlan` | POST | 切换计划 |
| **Learning** | `/learning/getTodayTask` | GET | 每日学习任务 |
| | `/learning/submitAnswer` | POST | 提交答案 |
| | `/learning/createPlan` | POST | 创建计划 |
| | `/learning/updatePreference` | PUT | 更新偏好 |
| | `/learning/updateLearningListCompletion` | POST | 标记完成 |
| | `/learning/getUserAllLearningPlans` | GET | 所有计划 |
| **Dictation** | `/learning/dictation/generate` | POST | 生成听写任务 |
| | `/learning/dictation/{id}` | GET | 任务详情 |
| | `/learning/dictation/submit` | POST | 提交听写 |
| | `/learning/dictation/history` | GET | 听写历史 |
| | `/learning/dictation/retry-wrong` | POST | 错词重练 |
| **Composition** | `/composition/extractText` | POST | OCR 提取文字 |
| | `/composition/correct` | POST | AI 批改 |
| | `/composition/dailyReading` | GET | 每日阅读 |
| **Pronunciation** | `/pronunciation/words` | GET | 获取练习词 |
| | `/pronunciation/correctPronunciation` | POST | 评测发音 |
| **Conversation** | `/conversation/session/create` | POST | 创建会话 |
| | `/conversation/message` | POST | 发送消息 |
| | `/conversation/audio` | POST | 发送语音 |
| | `/conversation/history` | GET | 会话历史 |
| **TTS** | `/composition/synthesizeTts` | POST | 文字转语音 |
| **Evaluation** | `/evaluation/dashboard` | GET | 评估仪表盘 |
| | `/evaluation/trend` | GET | 趋势数据 |
| | `/evaluation/weakWords` | GET | 薄弱词 |
| **Favorites** | `/learning/favoriteWords` | GET | 收藏词 |
| | `/learning/setFavorite` | POST | 标记收藏 |
| | `/learning/weakWords` | GET | 薄弱词 |

---

## 17. 工具与辅助模块

### 17.1 BitmapManager

图片加载与处理工具类：

```java
decodebitmap(byte[])     // 字节数组解码为 Bitmap
decodebitmapScale(byte[]) // 解码并自动缩放
readStream(InputStream)   // 流读取为字节数组
scaleByMatrix(Bitmap, w, h) // Matrix 缩放
```

### 17.2 AudioPlayer

有道词典 TTS 音频播放：

```java
// 英式发音: https://dict.youdao.com/dictvoice?audio={word}&type=1
// 美式发音: https://dict.youdao.com/dictvoice?audio={word}&type=2

AudioPlayer.playAudio(context, word, AudioPlayer.TYPE_UK);  // 英式
AudioPlayer.playAudio(context, word, AudioPlayer.TYPE_US);  // 美式
```

内部使用 `MediaPlayer` + `prepareAsync()` 异步准备，`OnPreparedListener` 回调后自动播放，播放完毕自动释放。

### 17.3 本地词库 (`LexiconResourceMap`)

**设计**：60+ 词库以 JSON 形式存储在 `res/raw/` 目录下，通过 `LexiconResourceMap` 按需加载到内存缓存。

```java
// 词库映射 (部分)
CET4_1 → R.raw.cet4_1    CET4_2 → R.raw.cet4_2    CET4_3 → R.raw.cet4_3
CET6_1 → R.raw.cet6_1    IELTS_1 → R.raw.ielts_1
TOEFL_1 → R.raw.toefl_1  GRE_1 → R.raw.gre_1
// ... 60+ 词库

// 缓存加载
private Map<Integer, List<WordEntry>> lexiconCache = new HashMap<>();

public List<WordEntry> loadLexicon(Context context, int lexiconId) {
    if (lexiconCache.containsKey(lexiconId)) {
        return lexiconCache.get(lexiconId);  // 命中缓存
    }
    int resId = getResourceId(lexiconId);
    // 读取 raw 资源 → 解析 JSON → 构建 WordEntry 列表
    lexiconCache.put(lexiconId, entries);
    return entries;
}
```

**WordEntry 结构**：

```java
class WordEntry {
    String headWord;                  // 拼写
    int wordRank;                     // 在词库中的序号
    String usPhone, ukPhone;          // 音标
    String usSpeechUrl, ukSpeechUrl;  // 发音参数
    String[] chineseTranslations;     // 中文释义
    String[] englishDefinitions;      // 英文释义
    ExampleSentence[] exampleSentences; // 例句 (EN + CN)
}
```

---

## 18. 设置管理系统

### 18.1 UserSettingsManager

**设计模式**：线程安全的单例模式 + 观察者模式。

```java
public class UserSettingsManager {
    private static UserSettingsManager instance;
    private final SharedPreferences sharedPreferences;
    private final List<OnSettingsChangedListener> listeners;

    public static synchronized UserSettingsManager getInstance(Context context) {
        if (instance == null) instance = new UserSettingsManager(context);
        return instance;
    }

    // 设置变更时通知所有监听器
    private void notifySettingsChanged(String key, Object value) {
        for (OnSettingsChangedListener listener : listeners) {
            listener.onSettingChanged(key, value);
        }
    }
}
```

**监听器应用场景**：`WordLearningFragment` 监听 `study_mode` 变更，实时切换题目类型而无需重启。

### 18.2 InnerSettingsManager

**设计**：管理账户级数据（userId、昵称、头像、登录状态），支持多账户（通过 userId 前缀隔离）。

```java
// 存储结构
KEY_IS_LOGGED_IN → 0/1/2
KEY_USER_ID → int
KEY_NICK_NAME → String
KEY_AVATAR_URL → String
```

---

## 19. 第三方依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| `androidx.appcompat` | 1.6.1 | 基础兼容库 |
| `material` | 1.10.0 | Material Design 组件 |
| `constraintlayout` | 2.1.4 | 约束布局 |
| `Glide` | 4.12.0 | 图片加载与缓存 |
| `uCrop` | 2.2.8 | 图片裁剪（Yalantis） |
| `Markwon` | 4.6.2 | Markdown 渲染 |
| `MPAndroidChart` | 3.1.0 | 图表绘制（PhilJay） |
| `Material Dialogs` | 3.3.0 | 对话框组件（afollestad） |
| `nice-spinner` | 1.3.1 | 下拉选择器（arcadefire） |
| `JUnit` | 4.13.2 | 单元测试 |
| `Espresso` | 3.5.1 | UI 测试 |

---

## 20. 构建与部署

### 20.1 构建命令

```bash
# 编译 Debug APK
.\gradlew.bat assembleDebug

# 编译 Release APK
.\gradlew.bat assembleRelease

# 安装到设备
.\gradlew.bat installDebug

# 运行单元测试
.\gradlew.bat test

# 运行仪器化测试
.\gradlew.bat connectedAndroidTest
```

### 20.2 环境配置

```java
// 默认: GetDataByThread 构造时设为 TEST
ApiConstants.setEnvironment(ApiConstants.Environment.TEST);

// 切换到生产环境
ApiConstants.setEnvironment(ApiConstants.Environment.PROD);
```

### 20.3 权限申明

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

### 20.4 项目约定

- **网络请求**：统一使用 `HttpManager` + `GetDataByThread` + `Handler` 模式，不使用 OkHttp/Retrofit
- **JSON 解析**：手动 `JSONObject`/`JSONArray` 解析，不使用 Gson/Moshi
- **线程模型**：`new Thread()` + `Handler`，不使用 AsyncTask/RxJava/Coroutines
- **Fragment 导航**：`show()/hide()` 而非 `replace()`，性能优先
- **数据持久化**：`SharedPreferences`，不引入 Room 等数据库框架

---

> **文档维护说明**：本文档随项目迭代持续更新。如有新增模块或架构变更，请同步更新对应章节。
