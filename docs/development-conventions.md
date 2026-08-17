# Memory App — 开发约束与规范（Development Conventions）

> **版本**：1.1 | **生效日期**：2026-08-14
>
> **更新记录**：
> - 1.1（2026-08-14）：新增 §3.8 布局文件命名规范（Layout 命名铁律）
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

- 自定义拍照走 `CameraCaptureActivity`（`ResolutionSelector` 支持 4:3 / 16:9 切换，默认 16:9，选择持久化于 `UserSettingsManager`）；
- 遮罩/预览跟随比例动态切换（`applyRatioVisual()`，借鉴系统相机）：**16:9 → `FILL_CENTER` 铺满全屏 + 渐变遮罩；4:3 → `FIT_CENTER` 完整画面居中（四周天然黑边）+ 遮罩置为透明**（黑边本身即黑，若用不透明遮罩会盖住 FIT 画面底部/侧边并遮挡网格线）；
- 横竖屏布局分列 `layout/camera_capture_layout.xml` 与 `layout-land/camera_capture_layout.xml`，控件 id 必须一致；渐变遮罩为独立全边缘 View（`top_scrim` / `bottom_scrim`），**禁止**在控制栏上内嵌背景 + margin 制造渐变（会留出底部未覆盖缺口）；
- **相机控件朝向不做任何代码旋转**（重要结论）：横竖屏切换由 Activity 重建天然处理，横屏布局（layout-land）中控件保持相对窗口 0° 即用户在横持视角的正立方向；此前传感器（OrientationEventListener/主动读取加速度计）+ Display rotation 驱动的旋转机制因 ROM 报告不可靠（华为重建后回调缺失/报告旧值）反复出错，且用户实测确认"横屏应逆时针再转 90°"即回归 0°，**已整体移除**（`rotation="-90"` 预设、监听器、主动采样、DisplayListener 全部删除）；
- **系统栏隐藏需在 `onWindowFocusChanged` 中重复执行**（`hideSystemBars()`）：部分 ROM（华为）横屏时可能覆盖沉浸模式或重建时序导致隐藏失效（表现为竖屏隐藏正常、横屏状态栏复现）；
- 预览层手势：`onSingleTapUp` 必须返回 `false`（否则 GestureDetector 禁用双击检测），单击对焦走 `onSingleTapConfirmed`，双击缩放走 `onDoubleTap`，捏合缩放灵敏度按 CameraView 做法 ×2，单指上下滑动调节曝光补偿（`onScroll`，每 80px 一个档位，`setExposureCompensationIndex`）；
- 自定义叠加层（如网格线）若覆盖预览层，**必须设置 `setOnTouchListener` 转发触摸到 focus_overlay**，否则 clickable=false 的 View 会截胡事件（hit-test 命中后向上冒泡，不会穿透到下层）；
- 网格线开关持久化于 `UserSettingsManager`（`isCameraGridEnabled`），控件 id 约定：`btn_grid` / `btn_switch_camera` / `grid_overlay` / `ev_label`；
- **网格线三分位置必须基于画面实际渲染区域**（`GridOverlayView.setRenderRect`）：渲染矩形由 `updateGridRenderRect()` 计算，优先使用 `Preview.getResolutionInfo()` 返回的**真实分辨率 + 旋转角**（`ResolutionInfo.getResolution()/getRotationDegrees()`，绑定后 ~250ms 异步就绪，勿用 `Preview.PreviewResolution`——该嵌套类在 CameraX 1.3.x 不存在）；未就绪时回退 4:3 假设；渲染矩形需夹取到 PreviewView 范围内，避免网格线画到遮罩/黑边区；并监听 `viewFinder` 布局变化（`OnGlobalLayoutListener`）同步更新；
- 捏合缩放使用 **16ms 节流合并**（`zoomPendingTarget` + `postDelayed`）：CameraX `setZoomRatio` 内部带平滑动画，每次 onScale 都直接调用会让动画不断重启、画面追不上手势（感知延迟），节流后每帧最多提交一次；
- **SVG 转 Android vector 必须等比**：vector 默认将 viewport 拉伸填满容器（SVG 默认 preserveAspectRatio 等比），viewport 宽高比 ≠ 容器宽高比时图形会被压扁/拉长（如 1303:1024 放进 24dp×24dp 会横向压缩成瘦长相机 + 椭圆镜头）；容器高度应按 `height = width * viewportHeight / viewportWidth` 计算；
- **相册按钮圆角用 `ViewOutlineProvider` 绘制层裁剪**（`setClipToOutline(true)` + `outline.setRoundRect`，**固定 dp 尺寸而非 `view.getWidth()`**——onCreate 阶段未布局宽高为 0，0 尺寸 outline 会把整个按钮裁剪不可见）；不依赖 Glide 变换/alpha 通道（RGB_565 解码下 `RoundedCorners` 透明角变黑，黑色图片上无圆角观感）；配合半透明白圆角背景（`bg_camera_gallery`）保证深色图片圆角轮廓可见；Glide 仅 `override(按钮尺寸).centerCrop()`；
- 横屏下缩放/EV 徽章（`zoom_label`/`ev_label`）约束为**顶部居中**，禁止约束屏幕右缘（会与右侧快门控制栏重叠被遮挡）；
- 裁剪页的 `crop_auto_fit` 持久化于 `UserSettingsManager`（UI 文案使用“自动适配”）：开启时旋转/缩放通过放大图片保证裁剪框内切，不缩小裁剪框；关闭时裁剪框可在屏幕内超出图片，结果超出部分填黑；
- 裁剪页缩放必须使用独立图片矩阵（`keepCropWindow`），禁止缩放时通过图片平移或重设裁剪框来补偿；旋转/微调后的 cover 倍率必须基于旋转图片四边形计算，不能只用外接矩形；
- 裁剪框拖动期间禁止 `applyImageMatrix(center=false)` 平移图片，避免拖动边缘时图片与裁剪框一起失控；边缘句柄触摸容差控制在 12dp 左右，裁剪视图左右保留至少 24dp 系统返回手势安全区；
- 图片裁剪走 uCrop（`UcropHelper.createThemedOptions()`），裁剪返回用 Activity Result API；
- **裁剪输出统一 JPEG 质量 85 / 最长边 1280**（`UcropHelper.MAX_CROP_RESULT_SIZE`）：依据 PaddleOCR 实测基准——服务端检测阶段内部缩放到 `limit_side_len=960`（MemoryServerTTS `config/ocr.yaml`），1280 覆盖上限并保留余量；三场景（手写/简单/复杂作文）A/B 实测 2048→1280 体积约减半、识别精度无损（conf ≥ 0.99）；
- **OCR 上传禁止二次解码重编码**：作文端（`CompositionMenuActivity`）与听写端（`DictationExecutionActivity.uploadForOcr`）均直接上传裁剪页输出 URI（`HttpManager.doHttpPostWithImageUri` 流式原样上传），不得再 `Bitmap.compress` 一次（曾存在听写端二次压缩 q80，已移除）；
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

### 3.8 布局文件命名规范（Layout 命名铁律）

**目的**：布局文件名必须"一眼可辨组件类型 + 所属功能"。前期手动编写与 AI 生成的命名混用（`activity_` 前缀、`_item` 后缀、裸名等）导致对接时经常改错页面。**所有新增 / 修改布局文件必须遵守本节。**

**命名总则**：全小写 + 下划线（`snake_case`），格式 = `[类型前缀] + 功能语义名`。功能名用模块英文名（`composition` / `dictation` / `evaluation` 等），禁止中文、拼音、无意义缩写。

#### 3.8.1 核心规则（强制）

| 布局类型 | 命名格式 | 示例 | 判定依据 |
|---------|---------|------|---------|
| **Activity 页面** | `<功能>_layout` | `composition_menu_layout`（作文批改菜单页） | `setContentView(R.layout.xxx)` 的完整页面，以 `_layout` 结尾 |
| **Fragment 页面** | `fragment_<功能>` | `fragment_daily_reading`（每日阅读页） | Fragment `onCreateView()` inflate 的布局，`fragment_` 开头 |
| **列表项 / 内嵌小组件** | `item_<实体>` | `item_weak_word`（薄弱词列表项） | RecyclerView / ListView 等列表的每个 item，或页内复用小部件，`item_` 开头 |

#### 3.8.2 补充规则（强制）

| 布局类型 | 命名格式 | 示例 | 判定依据 |
|---------|---------|------|---------|
| **对话框** | `dialog_<功能>` | `dialog_ocr_progress`（OCR 进度对话框） | `AlertDialog` / `DialogFragment` 的内容布局 |
| **底部弹层** | `sheet_<功能>` | `sheet_scenario_picker`（场景选择弹层） | `BottomSheetDialog` / `BottomSheetDialogFragment` 内容布局 |
| **Tab 子页面** | `<模块>_page_<名称>` | `evaluation_page_overview`（学情分析概览 Tab） | ViewPager2 / TabLayout 内嵌的子页面（非独立 Activity） |
| **自定义 View / 可复用组件** | `view_<名称>` | `view_bottom_nav` | 自定义控件类 inflate 到自己；或被 `<include>` / 工厂类复用的组件 |

#### 3.8.3 禁止写法（红线）

- ❌ `activity_` 前缀（旧写法，如 `activity_main`）→ 统一为 `<功能>_layout`；
- ❌ `_item` 后缀（如 `xxx_item`）→ 统一为 `item_<实体>`；
- ❌ 类型前缀与 `_layout` 后缀混用（如 `dialog_xxx_layout`）→ 只保留类型前缀，去掉 `_layout`；
- ❌ 无类型前缀的裸名（如 `bottom_layout`）→ 无法判断组件类型，必须加前缀；
- ❌ 中文 / 拼音 / 无意义缩写命名。

#### 3.8.4 存量不合规迁移记录（2026-08-14 已完成）

> ✅ **迁移已于 2026-08-14 执行完毕**（`git mv` 保留历史 + 同步更新全部 `R.layout.xxx` 与 `<include>` 引用，`assembleDebug` 构建通过）。后续所有布局必须直接符合 §3.8.1 / §3.8.2，不再存在例外存量。

**已完成重命名（`activity_` 前缀 / `_item` 后缀）：**

| 原文件名 | 新文件名 | 引用位置 |
|------|--------|---------|
| `activity_main.xml` | `main_layout.xml` | `MainActivity` |
| `activity_camera_capture.xml` | `camera_capture_layout.xml` | `CameraCaptureActivity` |
| `activity_theme_crop.xml` | `theme_crop_layout.xml` | `ThemeCropActivity` |
| `composition_records_item.xml` | `item_composition_record.xml` | `CompositionRecordAdapter` |
| `book_select_layout_item.xml` | `item_book_select.xml` | `BookAdapter` |
| `fragment_word_list_item.xml` | `item_word_list.xml` | `WordListAdapter` |
| `plan_list_layout_item.xml` | `item_plan_list.xml` | `PlanListAdapter` |

**已完成重命名（可复用组件 / 对话框，统一加类型前缀）：**

| 原文件名 | 新文件名 | 引用位置 |
|------|--------|---------|
| `bottom_layout.xml` | `view_bottom_nav.xml` | `main_layout.xml`（`<include>`） |
| `card_container_layout.xml` | `view_word_card_container.xml` | `WordCardContainer` |
| `card_summary_layout.xml` | `view_card_summary.xml` | `SummaryCardBuilder` |
| `word_card_choice_layout.xml` | `view_word_card_choice.xml` | `ExerciseCardFactory` |
| `word_card_input_layout.xml` | `view_word_card_input.xml` | `ExerciseCardFactory` |
| `dialog_manual_layout.xml` | `dialog_manual.xml` | `ManualDialogFragment` |

**已删除孤儿文件（原全库零引用）：**

| 文件 | 来源 |
|------|------|
| `item_trend_day.xml` / `item_trend_legend.xml` / `item_trend_row.xml` | 已删除的旧 `EvaluationTrendActivity` 遗留 |
| `item_mastery_bar.xml` | 旧学情分析遗留 |
| `item_section_header.xml` | 历史遗留 |
| `word_card_layout.xml` | 旧单词卡片基础布局遗留 |

> ⚠️ `crop_image_view.xml` 为裁剪库 vendor 代码（`com.canhub.cropper.CropImageView`）私有布局，**保持不动**，不纳入本项目命名规范。

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
