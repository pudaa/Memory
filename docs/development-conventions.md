# Memory App — 开发约束与规范（Development Conventions）

> **版本**：1.0 | **生效日期**：2026-08-04
>
> 本文档是项目开发的**强制性约束**。所有新代码、重构以及 AI 辅助开发都必须遵守。
> 关联文档：[项目全景总览](project-overview.md)（先读总览建立全局观）、[项目技术文档](project-technical-documentation.md)（技术细节）。

---

## 1. 核心准则：全局观（先看全局，再动局部）

> 本项目历史上最大的问题：**开发/重构时只看到单个模块，导致重复造轮子、模块间冲突、架构碎片化**（典型教训：早期每个模块都各自散落创建 `SharedPreferences` 调用点，结构既集中管理又游离分散）。

**强制流程（每次动手前）：**

1. 先阅读 [`docs/project-overview.md`](project-overview.md) 的「已有基础设施清单」，确认项目**已经存在**的能力；
2. 用代码检索（`grep` 关键词）确认无现成实现后，再决定新增；
3. 改动前评估对**其他模块**的影响，尤其是：网络层、设置/持久化层、词库层、`MainActivity` 底部导航、工具类；
4. 涉及跨模块能力时，**优先扩展已有管理器/工具类**，而不是新建一套散落实现。

**禁止（红线）：**

- ❌ 为了一个功能新建一个 `SharedPreferences` 调用点 —— 必须走 settings 管理器（见 §2.2）；
- ❌ 新写一套 HTTP 请求封装 —— 必须走 `HttpManager` / `GetDataByThread`；
- ❌ 新写 JSON 解析工具 —— 使用 `org.json` 手动解析（项目约定）；
- ❌ 新写图片 / 音频 / 日期等工具 —— 先查 `handle_utils/` 是否已有实现；
- ❌ 改动某模块时破坏其他模块依赖的接口或数据结构，除非同步更新所有调用方。

---

## 2. 分层架构约束

### 2.1 职责划分

| 层 | 职责 | 禁止 |
|----|------|------|
| **UI 层**（Activity / Fragment） | 界面渲染、用户交互、数据展示 | 直接访问 SharedPreferences；直接发起裸 HTTP |
| **逻辑/数据层**（Manager / Utils） | 业务状态、持久化、通用能力 | 持有 Activity 长引用（防泄漏）；耦合 UI |
| **网络层**（`HttpManager` / `GetDataByThread`） | 全部 HTTP 通信 | 被 UI 层绕过 |
| **设置层**（`settings/` 包） | 全部持久化配置的统一入口 | 被 UI 层绕过 |

### 2.2 持久化铁律（settings 管理器）

**所有 SharedPreferences 访问必须收敛到 `settings/` 包，禁止在 Activity / Fragment 中直接调用 `getSharedPreferences()`。**

| 存储内容 | 归属管理器 | 说明 |
|---------|-----------|------|
| 用户偏好：学习模式、滑动方向、每日新词数、**阅读字号**、**主题模式** | `UserSettingsManager` | 用户可配置项（`AppSettings` 文件） |
| 应用内部信息：登录态、userId、昵称、用户名、头像 | `InnerSettingsManager` | 内部状态（`UserPrefs` 文件） |
| 每日收藏状态（每日一读） | `InnerSettingsManager` | 按 userId 隔离（`DailyFavoritePrefs` 文件） |
| 作文草稿（作文批改） | `InnerSettingsManager` | 按 userId 隔离（`CompositionPrefs` 文件） |
| 发音每日成绩（发音练习） | `InnerSettingsManager` | 按日期隔离（`pronunciation_daily_scores` 文件） |
| 学习进度（今日已完成单词） | `DailyStateManager` | feature 内部已封装的独立管理器 |

**新增持久化需求时：**

1. 先检索 `InnerSettingsManager` / `UserSettingsManager` 是否已有对应方法；
2. 没有 → 在**对应管理器内新增方法**（保持集中管理），不要新开 `SharedPreferences`；
3. 涉及新的 prefs 文件时，把文件访问一并封装进管理器，UI 层只调用管理器方法。

> **持久化方案决策（2026-08-04）**：当前使用 SharedPreferences（全部 `.apply()` 异步写盘 + 已集中到 3 个管理器）。**暂不迁移 Jetpack DataStore**——项目为纯 Java，DataStore 基于 Kotlin 协程/Flow，迁移需引入 Kotlin 插件，成本高而当前数据量下收益不可感知。**若将来迁移**：优先 **Preferences DataStore**（非 Proto），用 Kotlin wrapper 保持管理器公开 API 不变、调用方零改动。详见项目总览/技术文档。

### 2.3 网络层铁律

- 所有 HTTP 请求必须走 `HttpManager`（底层）+ `GetDataByThread`（业务方法）；
- **禁止**引入 OkHttp / Retrofit / Volley；保持 Apache HttpClient 兼容（虽有弃用警告，但为项目约定）；
- 异步回调统一用 `Handler(Looper.getMainLooper())` + `Message`；
- 自定义 Handler 子类必须调用 `super(Looper.getMainLooper())`（无参构造在非 Looper 线程会崩溃）。

### 2.4 异步与线程

- 使用 `Handler` / `Message` 模式；**禁止** `AsyncTask`（已弃用）；
- 子线程操作完成后必须切回主线程再更新 UI（`runOnUiThread` / 主线程 Handler）；
- 后台线程注意 Activity 生命周期（`isAdded()` 判空、防止泄漏）。

### 2.5 相机 / 裁剪 / 相册链

- 自定义拍照走 `CameraCaptureActivity`（相机用例统一用 `ResolutionSelector` 4:3）；
- 图片裁剪走 uCrop（`UcropHelper.createThemedOptions()`），裁剪返回用 Activity Result API；
- **禁止**回退到旧的 `startActivityForResult` / `onActivityResult` 写法。

---

## 3. 代码风格与模式约定

### 3.1 JSON 解析

- 手动 `org.json` 解析（`JSONObject` / `JSONArray`），**禁止** Gson / Moshi。

### 3.2 对话框

- 一律使用 `MaterialAlertDialogBuilder`，**禁止** 原生 `AlertDialog.Builder`；
- 按钮监听使用 lambda。

### 3.3 UI 文案

- **禁止在 UI 字符串中使用 emoji 字符**（📊📈 等），渲染不一致。用 `ImageView` + drawable 图标或 Material 图标。

### 3.4 导航

- 底部 Tab 用 show/hide Fragment + `setCustomAnimations` 滑动切换，参考 `MainActivity.java`；
- 页面间跳转用显式 `Intent`。

### 3.5 命名与注释

- 类名/方法名/变量名使用清晰英文命名；
- 关键业务逻辑必须写注释（项目现状以中文注释为主）；
- 常量集中定义，避免魔法数字散落。

### 3.6 权限

- 核心权限：`INTERNET`（网络）、`CAMERA` + `WRITE_EXTERNAL_STORAGE`（作文拍照 OCR）、`RECORD_AUDIO`（发音）；
- 权限清单统一维护在 `AndroidManifest.xml`，避免重复声明。

### 3.7 UI / 主题：Material Design 3

- **页面开发一律遵循 Material Design 3（M3）标准**：主题基类 `Theme.Material3.DayNight.NoActionBar`，组件用 Material Components（`com.google.android.material`）；
- 项目为 **XML + View（Java）** 体系，M3 的配色角色 / 字体类型 / 形状 / 高度通过 **Material 属性与自定义样式**落地（参考 `.github/references/material3-theming.md` 的**设计原则**，其中 Compose 代码仅为概念参考，不直接照搬）；
- 优先复用已有的 Material 组件与既有样式（`themes.xml`、`colors.xml`、`drawable` shape），避免每页自造一套；
- 涉及界面精细打磨时可调用移动端设计技能（mobile-android-design / make-interfaces-feel-better）辅助评审。

---

## 4. 依赖管理约束

- 不随意升级依赖版本（需测试验证后升级），当前清单见技术文档 §20；
- 新增依赖必须说明理由并评估体积/影响；
- 禁止为了"方便"引入与现有能力重复的库。

---

## 5. 错误处理

- 网络失败必须给用户提示（`Toast`），必要时提供重试；
- JSON 解析必须 `try-catch`；
- 禁止静默吞异常——至少 `Log.e` 记录，重要路径要上报/回退。

---

## 6. 测试与验证要求

- 任何改动至少保证编译通过（`.\gradlew.bat assembleDebug`）；
- 涉及手势 / 相机 / 录音 / 网络 / 动画的改动，**必须真机验证**；
- 单元测试：`.\gradlew.bat test`；仪器化测试：`.\gradlew.bat connectedAndroidTest`。

---

## 7. 文档同步要求

- 改动核心架构 / 新增模块 / 新增持久化键 → 同步更新 `docs/` 相关文档；
- 新增或修改 API → 同步更新技术文档的 API 清单；
- 新增通用能力 → 同步更新 `docs/project-overview.md` 的「已有基础设施清单」。

---

## 8. 环境与安全

- API 环境切换统一走 `ApiConstants.setEnvironment()`（DEV / TEST / PROD）；
- 默认环境由 `GetDataByThread` 构造设置为 TEST，正式发布前确认切换；
- 密钥（如 Coze `ACCESS_TOKEN`）不要硬编码进文档 / 注释 / 提交，注意安全。
