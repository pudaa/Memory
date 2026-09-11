# AI 对话页美术改造方案

> 状态：**待确认**（确认后分阶段实施）
> 调研日期：2026-09-11
> 调研对象：`AiConversationActivity` 及其关联布局、资源

---

## 0. 一句话结论

聊天页之所以"粗糙"，根因不是缺装饰，而是**没有统一的资源体系**：
图标来自三套互不兼容的网格，颜色有硬编码与 `@color/white` 语义反转两重陷阱，导致风格割裂 + 暗色模式失效。
因此本方案的主体是「**先立规约，再改画面**」，而不是堆视觉元素。

---

## 1. 现状调研

### 1.1 涉及文件

| 类型 | 文件 |
|---|---|
| 页面 | `ui/treasure_view/aichat_view/AiConversationActivity.java` |
| 列表 | `ui/treasure_view/aichat_view/AiConversationAdapter.java` |
| 场景 | `ui/treasure_view/aichat_view/ScenarioPickerSheet.java` |
| 主布局 | `res/layout/aichat_main_layout.xml` |
| 消息项 | `res/layout/item_ai_message.xml`、`item_conversation_summary.xml` |
| 场景项 | `res/layout/item_scenario_card.xml`、`sheet_scenario_picker.xml` |

进入路径：百宝箱 → 发音纠正 → AI 对话（`PronunciationMenuActivity:46`）。

### 1.2 六项问题

**① 图标三套体系混用（高）**

| 资源 | 网格 | 风格 |
|---|---|---|
| `ic_mic_24dp.xml` | viewport **1024×1024** | iconfont 粗实心 |
| `ic_send_24dp.xml` | viewport 24×24 | Material 老版实心 |
| `ic_scenarios_24dp.xml` | viewport 24×24 | Material 线性 |
| `back.png` | 位图 | 非矢量，高分屏发虚 |

混用后果：同一行内图标线条粗细、视觉重量不一致。

**② 暗色下用户气泡不可读（高）**

`bg_chat_bubble_user.xml` 取 `@color/theme_primary` 作底，
`item_ai_message.xml:31` 取 `@color/white` 作文字色。
而 `values-night/colors.xml:5` 把 `white` 反转成 `#FF252538`（深色）——
暗色模式下变成 **深蓝底 + 深灰字**，几乎不可读。

**③ 图标语义错配（中）**

`ic_scenarios_24dp.xml:6` 注释写「场景/戏剧面具图标」，实际 pathData 绘制的是一张**笑脸**。
该资源被用在「场景选择」按钮上（`aichat_main_layout.xml:177`），功能与图形不符。

**④ 硬编码颜色阻断明暗切换（中）**

| 位置 | 硬编码值 | 暗色下的后果 |
|---|---|---|
| `aichat_main_layout.xml:193` | `#F2F2F7` | 输入框始终浅灰，成亮块 |
| `bg_chat_bubble_ai.xml:8` | `#1A82B0DA` | 描边不随主题调整 |
| `bg_scenario_card.xml:8` / `bg_summary_card.xml:8` | `#1A82B0DA` | 同上 |

**⑤ 图标着色来源混乱（中）**

三种方式并存且互相覆盖：

- `baseline_chat_24.xml:1` 内联 `android:tint="#FFFFFF"` + `fillColor` 白色
- `ic_play_24dp.xml:8` 写死 `fillColor="@color/theme_primary"`
- `aichat_main_layout.xml:55` 布局层再 `android:tint`

**⑥ 缺空状态与身份标识（低）**

无消息时整屏留白；AI 消息侧无头像或标识，全靠气泡颜色区分；
语音播放按钮是裸三角（`ic_play_24dp`），无可点击容器暗示。

### 1.3 美术基调（改造不得偏离）

| 项 | 值 |
|---|---|
| 主题色 | `theme_primary` 浅色 `#82B0DA` / 暗色 `#4A6FA5` |
| 强调色 | `theme_stress` 浅色 `#4E9BF8` / 暗色 `#6EB4F0` |
| 表面色 | `theme_surface` 浅色 `#F8F9FB` / 暗色 `#1A1A24` |
| 卡片语言 | 12dp 圆角 + 1dp 淡蓝描边 + 扁平填充 |
| 组件库 | Material 3（`Theme.Material3.DayNight.NoActionBar`） |

**参照基准**：`pronunciation_menu_layout`（发音纠正菜单）已是 App 内精致度最高的页面——
卡片 + 圆形图标底 + 主副标题 + 右侧 chevron。聊天页应向该语言对齐。

---

## 2. 图标库选型

### 2.1 结论

**主选：Material Symbols Rounded**

理由：项目已用 Material 3 组件库，现有 `baseline_*` 本就是 Material 体系，
延续成本最低；Rounded 变体的圆润笔画与 App 现有的圆角卡片语言一致。

**备选：Lucide** — 24 网格纯线性、风格极统一、ISC 许可；
但线宽偏细，与 App 现有图标气质差异较大，仅在个别位置按需取用。

> **实施修订（2026-09-11，真机对比后）**
>
> 实际落地使用 **filled（实心）变体**，而非默认的 outlined 线性变体。
> 原因：线性变体在 24dp 下视觉重量明显偏轻（波形、键盘两处尤其明显），
> 与 App 原有「实心饱满」的图标基调（iconfont 粗实心麦克风、手绘 PNG、
> 百宝箱立体卡片图标）不在一个语汇里。
> 下载路径相应为 `rounded/{name}-fill.svg`。

### 2.2 下载源与许可

| 渠道 | 地址 | 说明 |
|---|---|---|
| 官方站点 | `https://fonts.google.com/icons` | 逐个下载 SVG，可调字重/填充 |
| GitHub 仓库 | `https://github.com/google/material-design-icons` | 批量，含 symbols 目录 |
| npm 包 | `@material-symbols/svg-400` | 含 rounded / outlined / sharp 三风格 |

**许可：Apache License 2.0** — 可商用、可修改，需保留许可声明。
（对比：Lucide 为 ISC，同样宽松。）

### 2.3 落地流程（SVG → Android VectorDrawable）

```
下载 SVG → 转 VectorDrawable（viewport 统一 24×24）→ 放入 res/drawable/
```

转换两条路：
1. Android Studio：`New → Vector Asset → Local file`（逐个，适合少量）
2. 脚本批量转换（适合一次导入十几个，可复用）

### 2.4 命名与着色规约（新增约束）

- **命名**：统一 `ic_<语义>_24.xml`；废弃 `baseline_*` 与裸 png 新增
- **网格**：viewport 一律 24×24
- **着色**：vector 内 `fillColor` 统一写 `#FF000000` 占位，
  实际颜色**只由布局层 `android:tint` 决定**
- **禁止**：vector 内写 `@color/*` 或 `#FFFFFF`；布局层写十六进制字面量

### 2.5 图标映射表

| 位置 | 现有资源 | 问题 | 目标（Material Symbols） |
|---|---|---|---|
| 标题栏返回 | `back.png` | **手绘资产，不替换**（见 2.6） | 保持 `back.png` |
| 标题栏右侧 | `baseline_chat_24` | 写死白色 | `history`（历史会话） |
| 输入模式切换 | `ic_mic_24dp` | 1024 网格 | `mic` / `keyboard` |
| 场景选择 | `ic_scenarios_24dp` | **笑脸，语义错** | `theater_comedy` |
| 发送 | `ic_send_24dp` | 老版实心 | `send` |
| AI 朗读播放 | `ic_play_24dp` | 裸三角 | `volume_up` |
| 用户录音播放 | `ic_play_24dp` | 裸三角 | `play_arrow` |

### 2.6 手绘资产保护（硬约束）

项目内部分 PNG 图标为**早期手绘资产**，是美术基调的组成部分，**不得转为矢量替换**：

- `back.png` — 全 App 共 18 个布局引用的返回图标
- 百宝箱卡片图标：`treasure_box.png`、`word_learning.png`、`daily_reading.png`、`user_home.png` 等
- 其余手绘风格位图（`custom_*.png` 等）

处置方式：**原样保留**。待原始工程文件找回后，由美术侧导出 SVG，
再按 2.4 的命名与着色规约统一接入。

改造过程中若某处手绘位图恰好落在改动区域内，只调整其外部容器的间距与对齐，
**不触碰图标本身**。

> 已按本约束回退一处误改：聊天页 `btn_back` 曾一度被替换为
> Material 矢量 `arrow_back_ios_new`，现已恢复为 `back.png`。

**受此约束，第 2.5 节映射表中仅以下条目成立**（其余均为项目自有或 Material 素材，
可以替换）：`baseline_chat_24`、`ic_scenarios_24dp`、`ic_mic_24dp`、`ic_send_24dp`、
`ic_play_24dp`、`ic_keyboard_24dp`。

---

## 3. 明暗主题适配

### 3.1 新增语义色 token

`values/colors.xml` 与 `values-night/colors.xml` 同步新增：

| token | 浅色 | 暗色 | 用途 |
|---|---|---|---|
| `chat_toolbar_bg` | `#FFFFFF` | `#252538` | 标题栏（替代 `@color/white`） |
| `chat_input_bg` | `#F2F2F7` | `#2A2A3C` | 输入框填充 |
| `chat_input_bar_bg` | `#FFFFFF` | `#252538` | 输入栏背景 |
| `chat_bubble_ai_bg` | `#FFFFFF` | `#252538` | AI 气泡底 |
| `chat_bubble_ai_stroke` | `#1A82B0DA` | `#337FB6E0` | AI 气泡描边 |
| `chat_bubble_user_bg` | `#82B0DA` | `#4A6FA5` | 用户气泡底 |
| `chat_bubble_user_text` | `#FFFFFF` | `#FFFFFF` | **用户气泡文字（修复②）** |
| `chat_divider` | `#E6EAF0` | `#3A3A4C` | 输入栏分隔线 |

### 3.2 硬编码清理清单

改造时逐一替换（见 1.2 ④）：

- `aichat_main_layout.xml:21` `@color/white` → `@color/chat_toolbar_bg`
- `aichat_main_layout.xml:193` `#F2F2F7` → `@color/chat_input_bg`
- `aichat_main_layout.xml:245` `@color/white` → `@color/chat_toolbar_bg`
- `bg_chat_bubble_ai.xml:8` `#1A82B0DA` → `@color/chat_bubble_ai_stroke`
- `item_ai_message.xml:31,58,76` `@color/white` → `@color/chat_bubble_user_text`
- `AiConversationActivity.java:602` `R.color.white` → 语义色

### 3.3 同步修复的 Java 侧着色

- `AiConversationActivity.java:577` `R.color.white`（录音提示文字）
- `ScenarioPickerSheet.java:171,186` `R.color.white`（分类 chip 选中文字）

---

## 4. 布局改造清单

### 4.1 标题栏

- 高度 56dp → 保持；背景走 `chat_toolbar_bg`
- 返回图标换矢量，右侧图标 tint 由 `theme_text_primary` 改 `theme_primary`，
  与左侧返回图标统一为同色系
- 底部加 0.5dp 分隔线（替代目前 `elevation=4dp` 的生硬投影）

### 4.2 消息气泡

- AI 侧新增 26dp 圆形头像（`theme_primary` 底 + 白色 AI 标识），提升归属辨识
- 圆角由对称 16dp 改为**不对称**：AI 左下 4dp，用户右下 4dp
- 气泡内外边距微调，长文本 `lineSpacingExtra` 保持 2dp

### 4.3 语音播放按钮

- 裸三角 `ic_play_24dp` → `volume_up`（AI）+ `play_arrow`（用户）
- 增加 32dp 圆形浅底容器（`bg_chat_bubble_user` 内用白色 20% 透明度圆底）
- 点按区域 28dp → 32dp（符合最小可点区域规范）

### 4.4 输入区

- 顶部加 `chat_divider` 分隔线，解决当前与消息区无边界的问题
- 输入框填充走 `chat_input_bg`；圆角保持 20dp
- 麦克风 / 场景 / 发送三个图标统一 24 网格，统一 tint
- 场景按钮图标换 `theater_comedy`

### 4.5 空状态

- 消息列表为空时显示引导：一个矢量线稿图标 + 一行引导文案 + 场景入口按钮
- 目前该状态是整屏留白，是"画面粗糙"观感的重要来源

### 4.6 语音发送模式（补充，2026-09-11 追加）

语音模式由 `btnInputMode` 切换：显示 `layoutVoiceRecord`，隐藏 `etMessage` 与 `btnSend`，
`btnInputMode` 图标换成键盘。**该状态目前是整页最粗糙的一块**，实测截图见
`.workbuddy/screenshots/s5_voice_recording.png`。

现状问题：

| # | 现象 | 根因 |
|---|---|---|
| 1 | 波形位是一条 4dp 圆角的纯灰长条，像「被禁用的输入框」 | `bg_voice_wave_placeholder.xml` 仅一个 `colorOutlineVariant` 实心矩形 |
| 2 | 灰条与「点击录音」的关系含糊，看不出哪块可点 | 二者水平并排，无容器区分 |
| 3 | 发送按钮隐藏后，键盘与场景图标挤在左侧，右半屏全空 | `btnSend` 置 `GONE`，无替代元素占位 |
| 4 | 录音区与输入栏同为白底，两块粘在一起 | 缺分隔线，`layoutVoiceRecord` 用 `card_background` |
| 5 | 「录音中」整条录音区刷成 `theme_error` 高饱和红 | `AiConversationActivity.java:573` 整容器改色 |

改造要点：

- **波形位**：改为真正的波形图形（细竖条矢量，中段高两端低），
  静止态走 `chat_wave_idle`，录音态走 `chat_wave_active`
- **录音卡片化**：录音区改为独立圆角卡片（12dp 圆角 + 描边 + `chat_voice_bar_bg`），
  与下方输入栏明确分层
- **录音态降噪**：不再整条铺满红底。改为「圆形红点指示 + 文字转红 + 波形转红」，
  容器底色保持浅底，避免大面积高饱和
- **布局平衡（已按更克制的方案实施）**：语音模式下把外层 `TextInputLayout` 整块收起，
  并让输入栏内容居中。**不新增录音按钮** —— 上方录音条本身已是录音入口，
  再加一个会形成重复入口。
  根因说明：原实现只隐藏内层 `EditText`，外层 `TextInputLayout` 仍以 `weight=1`
  占满整段宽度，这才是右侧大片空白的真正成因。
- 录音区仍保持整条可点，交互完全不变

> 说明：该处最终**未引入任何新增交互**，只调整可见性与对齐方式，
> 改造完全落在视觉范围内。
>
> 另附实测限制：点击录音区无法进入「录音中」态 —— 设备当前无网络，
> `mSessionId` 为空，`startVoiceRecording()` 在会话校验处即早退。
> 该分支属既有逻辑，非本次改造引入。

### 4.7 场景面板（ScenarioPickerSheet）

- `ScenarioPickerSheet.java:113` 已读取后端 `icon` 字段但**未渲染**，建议启用：
  卡片左侧加 40dp 圆形图标位
- 难度徽章文字色 `@color/white` 改为语义 token，修复暗色对比度
- 分类 chip 选中态颜色改走 token

### 4.8 会话侧边栏（2026-09-11 追加，已实施）

现状问题（实测见 `.workbuddy/screenshots/s9_drawer_before.png`）：

| # | 现象 | 根因 |
|---|---|---|
| 1 | 时间戳直接显示 `2026-08-19T03:11:04` | 后端 ISO 字符串未做任何格式化 |
| 2 | 列表为纯文本，无图标、无分隔、无层级 | 使用系统 `android.R.layout.simple_list_item_2` |
| 3 | 暗色下标题与时间不可读 | 系统布局的文字色不跟随主题 |
| 4 | 列表为空时没有任何提示 | 无空状态兜底 |

改造要点（均已实施）：

- 新增 `layout/item_session.xml`：圆形浅底图标 + 标题（加粗、单行省略）+ 时间
- 时间格式化为紧凑展示：今天 → `HH:mm`，今年 → `MM-dd HH:mm`，更早 → `yyyy-MM-dd`；
  解析失败时**原样返回**，避免后端改格式后列表整片变空
- 新增 `bg_session_item.xml`（圆角涟漪）与 `bg_session_icon.xml`（图标圆底）
- 侧边栏补空状态「暂无历史会话」

---

## 5. 实施阶段与提交策略

| 阶段 | 内容 | 产出 |
|---|---|---|
| Phase 1 | 图标库导入 + 语义色 token + 着色规约 | 资源层，不动画面 |
| Phase 2 | 聊天页布局改造（标题栏 / 气泡 / 输入栏 / 空状态） | 主画面 |
| Phase 3 | 场景面板 + 总结卡 | 次级画面 |
| Phase 4 | 真机截图审阅 + 修正 | 验收 |

每阶段结束独立 git 提交；Phase 2 起每阶段在真机（华为 HLK-AL00）截图核对。

---

## 6. 决策记录与遗留项

### 6.1 已确认并执行

| 决策点 | 结论 |
|---|---|
| 图标库 | Material Symbols Rounded（**filled 变体**） |
| Emoji 范围 | UI 图标位一律矢量；**App 自己生成**的文案去掉 emoji；**模型产出的对话正文不动** |
| 空状态 | 矢量插图 + 引导文案 |
| 手绘资产 | `back.png`、百宝箱图标等**不替换**（见 2.6） |

Emoji 的实际处置：

- 已清除：`WELCOME_TEXT` 的 🎉😊、退出自由聊天提示的 😊
- 保留不动：用户与 AI 的对话正文（属语言内容，且由模型产出，无法根治）
- **本次未纳入**：`strings.xml:54,55,59`（`✅ 正确！` / `❌ 错误` / `恭喜🎉`），
  以及 `ExerciseCardFactory`、`SummaryCardBuilder`、`DictationResultActivity`、
  `view_card_summary.xml` 等处的 emoji 反馈图标。
  这些属学习/听写流程，建议作为独立一批处理（见 6.2）。

### 6.2 遗留待办

1. **场景面板（Phase 3）未实施** —— 设备当前无网络，`mSessionId` 为空，
   `showScenarioPicker()` 会直接 Snackbar 返回，面板打不开、**改动无法验证**，
   故未动代码。待可联网环境验证后再做 4.7 所列三项。
2. **词卡 / 听写页 emoji 反馈图标** —— 可替换为矢量对勾、叉号；
   涉及多处 Java 与布局，建议单独一批。
3. **孤儿资源** —— `ic_scenarios_24dp`、`ic_play_24dp`、`ic_send_24dp`、
   `bg_input_bar`、`bg_voice_wave_placeholder`、`ic_keyboard_24dp`、
   `bg_voice_record_bar` 现已无引用，可清理（本次保留以便随时回退）。
4. **抽屉会话标题** —— 后端返回的 title 多为 "New Conversation"，属数据侧问题，
   不在美术范围。
5. **另外 17 个布局仍在用 `back.png`** —— 位图在高分屏下的清晰度问题全 App 存在，
   待手绘原工程导出 SVG 后统一替换（见 2.6）。

---

## 7. 实测记录

真机：华为 HLK-AL00（Android 10 / 1080×2340 / 480dpi）

| 场景 | 截图 | 结论 |
|---|---|---|
| 改造前 · 文本模式 | `s4_chat_before.png` | 基线 |
| 改造前 · 语音模式 | `s5_voice_recording.png` | 波形为纯灰长条，右侧空白 |
| 改造前 · 侧边栏 | `s9_drawer_before.png` | 裸 ISO 时间戳 |
| 改造后 · 文本模式 | `s10_text_filled.png` | 实心图标、空状态、输入栏分层 |
| 改造后 · 语音模式 | `s11_voice_filled.png` | 录音条卡片化、输入栏居中 |
| 改造后 · 侧边栏 | `s12_drawer_after.png` | 图标 + 格式化时间 |
| 改造后 · 暗色模式 | `s15_dark_chat.png` | **明暗适配通过** |

未能验证的部分（均为环境限制，非实现缺失）：

- **消息气泡的实际渲染** —— 需联网建立会话才会产生消息，设备当前「无服务」；
  只能通过代码审查确认（气泡配色已全部收口到 token）。
- **录音中态** —— 同上，`startVoiceRecording()` 在 `mSessionId` 为空时提前返回。
- **场景面板** —— 同上。

> 截图存放于 `.workbuddy/screenshots/`，受 `.gitignore` 忽略，不入库。

---

## 8. 词卡模块去 emoji 化（2026-09-11 第二批）

老大反馈总结页可用真实数据检查后，将原「遗留待办 2」提前执行。
范围：单词学习做题页 + 总结页 + 完成提示；**听写页不在本批**。

### 8.1 改动清单

| 位置 | 原状 | 现状 |
|---|---|---|
| 总结页顶部插图 | 48sp 的 `🎉` 文本 emoji | 圆形浅底 + 组合矢量插图（见 8.2） |
| 总结页统计行 | Java 拼 `✅ 27 正确` / `❌ 13 错误` | 布局固定图标位（check/cancel）+ 纯文字数字 |
| 总结页列表项 | 每项前缀 `✅` / `❌` | 16dp 矢量对勾/叉号，尺寸统一、配色走 token |
| 总结页结尾 | `明天继续加油！💪` | 移除 💪 |
| 做题页反馈行 | `✅ 正确！` / `❌ 错误` | 纯文字 + `iv_feedback_icon` 图标位，`setFeedbackIcon()` 切换 |
| 做题页中性态 | 无图标逻辑 | 「已提交」/数字评分时自动隐藏图标 |
| 词书完成 Toast | `🎉 恭喜！…` | 去除 🎉 |

### 8.2 完成插图的来源与升级

- 第一版：Material Symbols `celebration`（filled）单色图标 —— **图标库素材，非手绘**。
  老大反馈喜庆感不如原 emoji，判断属实：其定位是「图标」而非「插图」。
- 第二版（现行）：`ic_celebrate_burst.xml` 组合矢量 ——
  彩笛主体（theme_primary）+ 右上主星（theme_stress）+ 半透明星点装饰，
  主题色三阶拉开层次；新增 `summary_decor_strong` / `summary_decor_soft` token。
  **该文件为多色插图，fillColor 内嵌主题色引用，不适用「占位黑 + tint」规约。**

### 8.3 不动现有资源的理由

`ic_check_circle.xml` / `ic_celebration.xml`（实为星形，命名误导）已被
`EvaluationActivity`（学习报告页）引用且 fillColor 写死 —— 保持原样，
另建 `_24` 后缀新图标，避免牵连报告页。

### 8.4 实测

浅色 `s16_summary_after.png`（第一版图标）、`s17_summary_burst.png`（组合插图）、
暗色 `s18_summary_burst_dark.png`（组合插图）—— 双模式均通过。

### 8.5 本批仍未覆盖

- 听写页 emoji（`item_dictation_summary.xml` 的 `✓ 完全掌握`、
  `DictationGenerateActivity` 的 `✓`/`⏳`、`DictationResultActivity` 的 `✓`/`✗`）
- `ExerciseCardFactory` / `SummaryCardBuilder` 中输入模式评分的
  4 个硬编码色（`#4CAF50` 等）—— 属配色 token 化，与 emoji 无关，另行处理
- `strings.xml` 的 3 条死字符串（`feedback_correct` / `feedback_wrong` /
  `all_learning_complete`，无任何引用）—— 可清理
