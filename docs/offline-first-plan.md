# 离线优先改造方案（Offline-First Plan）

> 状态：**方案已定稿，分期实施中**。服务端前置改造见 MemoryServer `docs/project-documentation.md` §「FSRS 作答时间锚定」。
> 本文是客户端侧的唯一方案口径；实施进度以文末「分期与验收」勾选为准。

---

## 1. 目标与非目标

**目标**

1. 断网也能完成当日学习任务，作答先落本地、联网后自动补传；
2. 断网时「我的词书」等页面能渲染上次数据，联网后自动刷新；
3. 补传时 **FSRS 计算与联网时一致**（按真实作答时刻调度，不因补传滞后而错位）；
4. 账号 / 计划切换不串数据、不丢未同步数据。

**非目标（本阶段不做）**

- 离线生成"新词的当日任务"（新词预算仍由服务端 `getTodayTask` 决定）；
- 离线跨天继续学（明确禁止，见 §4）；
- 本地复制 FSRS 调度算法（服务端仍是唯一调度权威）；
- 离线使用听写生成 / 作文批改 / AI 对话等强依赖服务端的能力（仅做明确提示）。

---

## 2. 现状与结论（证据）

| 现状 | 位置 | 结论 |
|---|---|---|
| 每日任务只在内存，冷启动断网 = Toast「网络请求失败」 | `WordLearningFragment:41, 891-894` | **必须新增任务快照** |
| 待上传队列存在且持久化，按 userId 隔离，带去重与幂等键 | `DailyStateManager:33, 170-184`；`WordLearningFragment:623` | 方向正确，可复用为 Outbox 前身 |
| 队列存于 `UserPrefs`（与登录信息同文件） | `DailyStateManager:25`；`InnerSettingsManager:12` | **登出 `clear()` 会连队列一起抹掉**，必修 |
| 每次答题全量 load/parse/save SP，且在主线程 | `DailyStateManager:170-277, 341-354` | 队列规模化后必然卡顿，改 Room |
| 补传只有 App 启动 / 页面 onResume 两个触发点，无连通性感知 | `MainActivity:48`；`WordLearningFragment:261` | 需新增 `NetworkCallback` |
| 收藏 / 薄弱词每次进页面才拉，失败只 log | `FavoriteWordsFragment:104`；`WeakWordsFragment:85` | 需读缓存 |
| 输入模式已有本地判定，服务端事后覆盖 | `ExerciseCardFactory:313-336`；`WordLearningFragment:674-682` | 离线输入模式**无需新算法** |

**服务端已确认的关键点**（详见服务端文档）：

- `submitAnswer` 已按 `submitId` 幂等去重；
- java-fsrs 的 `reviewCard(card, rating, reviewDatetime)` 支持显式复习时刻，服务端此前未使用 → 补传会导致 FSRS 按"入库时刻"推进（due 后移、学习步长卡被当成隔天复习、R/elapsed 通胀）；
- `user_word_state.due_date` 此前按"now + interval"写入，锚定改造必须同步修，否则 `due_date <= NOW()` 的取词会与 card_json 漂移。

---

## 3. 已确认的决策（不再讨论）

| # | 决策 | 说明 |
|---|---|---|
| D1 | **禁止离线跨天** | 快照以服务端 `planDate` 为唯一"哪一天"权威；设备日期 > 快照 planDate 时进入"离线锁定"态：可回看，不可产生新作答 |
| D2 | **Outbox 按 userId 保留** | 登出只清 token / UI 态，不清未同步作答；提供"清除本机离线数据"入口与提示 |
| D3 | 队列上限 / TTL | 每账号上限 5000 条、TTL 30 天，超限丢最旧并记日志（不阻塞新作答） |
| D4 | 读缓存清单 | 收藏词、薄弱词、计划列表、每日一读收藏 |
| D5 | `answeredAt` 信任边界 | 客户端上报 ISO-8601 带时区；服务端 clamp：超前 > 60s 丢弃、滞后 > 36h 退化为接收时刻；同时保存"客户端声明"与"服务端接收"两个时间 |
| D6 | FSRS 幂等响应可重放 | 重复 `submitId` 时服务端回放首次的 `fsrsScore / isCorrect / aiFeedback` |

---

## 4. 客户端架构

### 4.1 新增独立 Room 库 `memory_local.db`

> ⚠️ **绝不能放进 `lexicon.db`**：词书库由 `LexiconDatabase.ASSET_DATA_VERSION` 版本机制「删库重拷」（见 `LexiconDatabase`），放进去会被词书更新清空。

| 表 | 关键列 | 说明 |
|---|---|---|
| `outbox` | `id, userId, planId, planDate, lexiconId, kind, submitId(nullable), answeredAt(epoch ms), payloadJson, attempts, nextAttemptAt, state, lastError, createdAt` | 唯一出站通道；`kind ∈ {submit_answer, learning_list_completion, set_favorite, study_log}`；`submitId` 唯一索引（SQLite 允许多个 NULL） |
| `task_snapshot` | `(userId, planId)` 主键, `lexiconId, planDate, studyDay, wordListJson, newWordCount, reviewLimit, reviewsDoneToday, fetchedAt` | 每日任务快照；**只保留最新一份**，用 `planDate` 判定是否过期 |
| `word_list_cache` | `(userId, planId, kind, itemKey)` 主键, `lexiconId, payloadJson, updatedAt` | 读缓存（收藏/薄弱词/计划列表/每日一读收藏通用一表） |
| `sync_meta` | `(userId, planId)` 主键, `lastSyncAt, lastTaskPullAt, lastError` | 节流 + 可观测（"待同步 N 条 / 上次同步时间"） |

迁移策略：`MemoryLocalDatabase` version 1（首版），`fallbackToDestructiveMigration()`（本库只存可重建的同步中间态；**唯一的例外是 outbox 里的未同步作答**，因此 destructive 前必须先把 outbox 导出到文件或禁止 destructive —— 实现时采用"手写 `Migration` 兜底 + 禁止破坏性迁移"，见 §7 风险）。

### 4.2 Outbox 状态机

```
        enqueue(本地立即生效)
             │
             ▼
   ┌─── pending ──(worker 取任务, 置 syncing)──► syncing
   │        ▲                                      │
   │        │                            200 & code=200
   │        │                                      ▼
   │        │                                   removed
   │        │
   │  失败且可重试 (网络/5xx/超时)  ◄── attempts++ / nextAttemptAt = now + backoff(attempts)
   │        │
   │        └── attempts ≥ 8 ──► dead（保留可查，不阻塞队列；UI 可提示"待人工重试"）
   │
   └── 4xx 业务错（除 401）──► dead（永久失败，不再重试）
```

- **顺序**：同一 `(userId, planId)` 内按 `answeredAt ASC, id ASC` 串行消费（保证 FSRS 按作答顺序回放）；
- **退避**：`min(2^attempts, 3600) * (1 + rand*0.2)` 秒；**attempts 只增不清**（`markSyncing` 保留原值，进程被杀不会重置计数）；
- **401**：交给 OkHttp Authenticator 静默刷新后继续，**不计失败**；
- **触发点**：进程启动、`NetworkCallback.onAvailable`（注册在 `NetworkInitializer`，任何页面都生效）、单词页 onResume、Tab 切回、今日任务加载成功、**点状态横幅"立即重试"**、死信面板"重试全部"；
- **一轮内的重试**：最多 2 次尝试（1s 退避）＋连接超时 5s → 最坏约 11s；一轮遇到网络失败即 fail-fast 中止（不让 N 条各等一轮超时）；
- **空队列短路**：无待发送也无死信时不起 worker 线程、不做清理，只回调一次"成功 0 条"；
- **起轮前清理**：`OutboxStore.prepare()` 复位残留 `syncing`、按 TTL(30 天) 清理超期条目 —— 注意**死信不无条件删除**（保留供用户查看/重试，由 TTL 与 5000 条上限兜底）；
- **重入保护**：全局 `AtomicBoolean` 单飞，重复触发只记一行"已有补传轮次进行中，忽略本次触发"。

### 4.3 每日任务快照与"禁止跨天"（D1）

```
打开单词页
 ├─ 读 task_snapshot(userId, planId)
 │    ├─ snapshot.planDate == serverPlanDate? → 直接渲染可学（离线亦可学）
 │    └─ snapshot.planDate <  设备今天     → 离线锁定：只读回看 + 顶部提示"请联网获取今日任务"
 └─ 并行 GET /learning/getTodayTask（节流：距 lastTaskPullAt < 5min 且同日则跳过）
      ├─ 成功 → 覆盖快照（含服务端 planId/planDate/studyDay/wordList/counters）
      └─ 失败 → 保留快照，UI 显示「离线模式 · 数据为 {planDate}」
```

规则细节：

1. **判定基准是服务端 `planDate`**（`getTodayTask` 已返回），不是设备日期；设备日期仅用于"是否已经过了快照那天"的保守判断（设备日期 > planDate → 判定跨天）；
2. 跨天且**尚未取到新任务**时：**卡片区允许为空**——昨天的任务已经过期，继续摆出来只会误导；
   此时只显示引导横幅「已跨天（离线数据为 X）· 联网后获取今日学习任务」，并在 `offlineLocked` 兜底拦截任何提交；
3. 同日离线：正常渲染快照卡片，横幅显示「离线模式 · 数据为 X（待同步 N 条）」；
4. 服务端返回的 `wordList` 永远覆盖本地快照与页面（服务端是调度权威），并隐藏离线横幅；
5. 补传完成后若累计同步 ≥ `dailyNewWordCount` 条，忽略节流强制重拉一次任务（重新锚定今日列表）；
6. 触发重试的入口：`onResume`、Tab 切回（`onHiddenChanged`）、网络恢复回调；**离线态（含跨天留空）下这些入口都会再试一次服务端**，取到新任务即自动切回在线态。

### 4.4 补传协议（与 D5/D6 配套）

`submit_answer` 的 payload 在现有字段基础上新增：

```json
{
  "submitId": "123_1759234567890",
  "answeredAt": "2026-09-30T23:50:12+08:00",
  "planDate": "2026-09-30",
  "planId": "42",
  "studyDay": 7,
  "...": "原有字段不变（userId/wordId/lexiconId/headWord/isCorrect/responseTimeMs/studyMode/…）"
}
```

- `answeredAt` 取**作答瞬间**（点击提交时）的时间戳，而不是入队/补传时间；
- `learning_list_completion` 新增 `planDate`（服务端据此定位当日行，避免 `studyDate` 序号撞行）；
- 服务端幂等短路会回放首次响应，客户端在输入模式下据此补写 `aiFeedback`（修复"AI 分析中…"不收敛）。

### 4.5 读缓存（D4）

- **读穿**：先渲染缓存 → 请求成功替换 + 更新缓存 → 失败保留缓存 + 顶部提示「离线数据 · 更新于 X」；
- **写穿 + 乐观更新**：取消收藏等操作本地立刻生效并入 Outbox，失败回滚并提示（现状只 `Log.e`）；
- **失效**：`userId`/`planId` 变化天然隔离（缓存按二者分键）；手动下拉刷新；写操作成功；
- **缓存内容**：收藏词与薄弱词缓存 `headWord` 列表即可（正文由本地词书库按需补全，见 `FavoriteWordsFragment:76`）；计划列表缓存原始 JSON；每日一读收藏复用现有 `daily_favorite_*` 键。

---

## 5. 分期与验收

| 阶段 | 内容 | 验收标准 | 状态 |
|---|---|---|---|
| **P1** Outbox 加固 | 独立 Room 库 + outbox 表；`answeredAt/planId/planDate` 入队；登出不清队列；`NetworkCallback` 触发；清死信可视化 | 飞行模式答 20 题 → 恢复网络 ≤ 30s 内全部补传；登出再登录队列仍在；主线程无 O(n) 序列化（答题不卡） | ✅ 已完成（真机验证见 §8） |
| **P2** 离线学习 | `task_snapshot` + 本地优先渲染 + 禁止跨天（跨天留空 + 引导）+ 离线模式标识 + 离线态重试服务端 | 冷启动飞行模式可完整学完当日任务；跨天离线卡片区留空且无法提交；联网后服务端 `learnedWords/streak` 与本地一致、无重复计分 | ✅ 已完成（真机验证见 §8） |
| **P3** 读缓存 | 收藏 / 薄弱词 / 计划列表 / 每日一读收藏读缓存 + `set_favorite` 入队 | 离线进「我的词书」能看到上次清单并提示离线；联网自动刷新；取消收藏失败能回滚 | ✅ 已完成（真机验证见 §8） |
| **P4** 收尾 | 完成上报 / 学习日志入队；`sync_meta` 角标（待同步 N 条）；死信面板；文档同步 | 死信可查可重试；角标与实际队列一致 | ✅ 已完成（真机验证见 §8） |
| **P5** 队列自愈加固 | 点横幅立即重试；TTL/死信清理真正接线；重试计数不被清零；空队列短路；快速失败客户端（连接超时 5s、单轮最多 2 次尝试） | 空闲触发不再起轮；服务端不可达时等待从最坏 ~48s 降到 ~11s；死信可保留可重试 | ✅ 已完成（真机验证见 §8.7） |

**回归高风险点**：① 重复推进 FSRS（靠 `submitId` + 服务端幂等，双端都要验证）；② 跨天判定误伤（时区/planDate 不一致）；③ 输入模式 AI 反馈丢失（靠幂等响应回放）。

---

## 6. 服务端依赖（前置）

| 依赖 | 服务端现状 | 需要 |
|---|---|---|
| FSRS 复习时刻锚定 | 未锚定（`Instant.now()`） | 接受 `answeredAt` 并透传 `reviewCard(card, rating, Instant)` |
| `due_date` 写入 | `now + interval` | 改为由 `card.getDue()` 换算 |
| 作答时间落库 | 只有 `study_time`（接收时刻） | 新增 `answered_at` + `is_correct` + `ai_feedback` |
| 日统计口径 | 全部用 `study_time` | 改用 `COALESCE(answered_at, study_time)` |
| 完成上报定位 | 按 `studyDate` 序号（可撞行） | 支持并优先按 `planDate` 定位 |
| `planId` 下发 | `getTodayTask` 不返回 | 响应补 `planId` |
| 幂等响应 | 只回 `duplicate:true` | 回放首次响应（含 `aiFeedback`） |
| `elapsed_days` | 被临时用于存"对错" | 存真实间隔；对错改 `is_correct` |

---

## 7. 风险与回滚

| 风险 | 应对 |
|---|---|
| 客户端时钟不可信 | 服务端 clamp（D5）；客户端也做一次本地 clamp（不早于上次作答、不晚于当前） |
| Room 迁移破坏未同步作答 | `memory_local.db` **禁止 `fallbackToDestructiveMigration`**；新增字段走手写 `Migration`，只允许加列/加表 |
| 跨天判定误伤用户 | 以服务端 `planDate` 为准 + 设备日期仅作保守判断；锁定态提供"立即联网同步"按钮 |
| 补传与服务端状态冲突 | 服务端为准（服务端重新计算 FSRS）；客户端只信服务端返回的 `fsrsScore/isCorrect`，本地判定仅作即时反馈 |
| 队列膨胀 | 上限 5000 / TTL 30 天 + 丢最旧日志 + 角标可见 |

---

## 8. 验证记录

### 8.1 服务端锚定（2026-10-01，本机 8081 + MySQL）

| 用例 | 结果 |
|---|---|
| `submitAnswer` 带 `answeredAt = now-25h` | 返回 `dueDate = 作答时刻 + 19 天`；`user_word_study_log` 落两列：`study_time`=接收时刻、`answered_at`=作答时刻（lag 1500 分钟），`is_correct=1`、`elapsed_days=0`（真实间隔）、`scheduled_days=19` |
| `user_word_state.due_date` 与 `card_json.due` | 完全一致（同为 `2026-10-19T10:15:25+08:00` 的同一瞬间）—— 证明 `due_date` 修复生效，取词列与卡片不再漂移 |
| 相同 `submitId` 重复提交 | `duplicate:true` 且**回放** `fsrsScore/stability/difficulty/retrievability/dueDate`（旧实现只回 `duplicate`） |
| `answeredAt = now-40h`（超 36h clamp） | `answered_at == study_time`（退化为接收时刻），未污染 FSRS 档期 |
| `FSRSAnchorTest`（3 用例） | 全绿；其中实测「未锚定 → 间隔被通胀」：`S=10、3 天间隔、GOOD` 由 18 天变成 21 天 |

### 8.2 客户端 P1（2026-10-01，真机 HLK-AL00 + LOCAL 环境 + adb reverse）

1. **离线入队**：断开 `adb reverse`（服务端不可达）后确认一道选择题 →
   `memory_local.db` 建表成功（`outbox / task_snapshot / word_list_cache / sync_meta`），
   outbox 内出现 1 条 `pending` 记录，字段齐全：
   `userId=10, planId=9, planDate=2026-10-01, studyDay=1, submitId=101_…, answeredAtIso=2026-10-01T11:27:20.123+08:00`，
   payload 为完整 `submitAnswer` 请求体（含锚定字段）。
2. **恢复补传**：恢复 `adb reverse` → 应用回前台触发 `onResume → flushPendingUploads` →
   日志 `补传成功: id=1, kind=submit_answer, 剩余 0`，outbox 清零。
3. **服务端最终落库**（客户端 → 服务端的完整链路）：`study_time=11:28:48`、`answered_at=11:27:20`（滞后 88 秒），
   `user_word_state.due_date = 11:37:20 = 作答时刻 + 10 分钟`（新词 GOOD 的学习步长），
   若未锚定则会是 `11:38:48` —— 说明作答时刻锚定已端到端生效。
4. **网络回调**：注册 `ConnectivityManager.NetworkCallback` 后日志出现
   `已注册网络恢复补传回调` / `检测到网络恢复，触发补传`（注册即回调当前网络）。
5. **旧队列迁移**：设备上原 SharedPreferences 队列为空，迁移路径未触发（代码幂等，`_pendingUploadsMigrated` 标记）。

> 真机测试期间的临时改动均已还原：`local.properties` 恢复 `DEFAULT_ENVIRONMENT=TEST`，测试账号与作答记录已从数据库清除。

### 8.3 P2 离线学习（2026-10-01，同机同法）

| 用例 | 结果 |
|---|---|
| 在线加载任务后落快照 | `task_snapshot` 出现 1 行：`userId=11, planId=10, lexiconId=kaoyanluan_1, planDate=2026-10-01, studyDay=1, newWordCount=3, wordListJson=[[201,…],[202,…],[203,…]]` |
| **离线冷启动（同日）** | 服务端 3 次重试失败 → 日志 `已用本地任务快照进入学习: planDate=2026-10-01`；页面渲染 `Day 1 / generation` + 横幅「离线模式 · 数据为 2026-10-01（本地作答，联网后自动同步）」 |
| 离线作答 | 快照卡片可直接作答，作答入 outbox（`id=2, pending, submitId=201_…, answeredAt=11:44:18`） |
| **跨天 + 未取到新任务** | 把快照 `planDate` 改为昨天后离线启动：日志 `快照已跨天…卡片区留空等待联网获取今日任务`；**卡片区为空**（无 `tv_word`/`option_a`/`btn_confirm`），横幅「已跨天（离线数据为 2026-09-30）· 联网后获取今日学习任务」；尝试提交不产生任何请求、outbox 保持 0 条 |
| 恢复在线 | `onResume` 触发重拉 → `GetPlan` 200（planId=10, planDate=2026-10-01）→ outbox 自动补传成功（`剩余 0`，服务端 `answered_at` 比 `study_time` 早 16 秒）→ **离线横幅消失**、快照被服务端数据覆盖 |
| 横幅文案过期缺陷 | 首次实现漏了"在线响应到达后隐藏横幅"，实测残留「离线模式…（待同步 1 条）」；已在 `parseAndCreateCards` 与补传回调中补 `updateOfflineBanner()` 并复验通过 |

### 8.4 P3 读缓存（2026-10-01，同机同法）

| 用例 | 结果 |
|---|---|
| 在线渲染后落缓存 | `word_list_cache` 出现 `favorite`×3（generation/pardon/presence）+ `weak`×2，作用域 `planId=11` ✓ |
| **离线进「我的词书」** | 断网冷启动后收藏词仍列出 3 条、薄弱词仍列出 2 条（来自缓存） |
| 计划列表离线 | 断网打开「计划更换」页仍显示「考研必考词汇 3/40」（来自整块缓存 `{"plans":[…],"onPlanId":11}`） |
| 每日一读收藏离线 | 断网打开收藏抽屉仍列出「A New Day」（来自整块缓存） |
| **离线取消收藏** | 长按 → 确定：列表与缓存立即移除（乐观更新），出站队列新增 `set_favorite`（payload 含 userId/wordId/lexiconId/headWord/isFavorite）；联网后自动补传成功、服务端 `is_favorite` 置 0 |
| 顺带修掉的解析缺陷 | 原实现用「跨词书查词」得到的 (wordId, lexiconId) 去取消收藏，服务端是按**当前词书**分桶的 → 可能命中 0 行静默失败。现优先在当前计划词书内解析（`findWordInBook`），实测服务端目标行 `is_favorite` 由 1 变 0 ✓ |
| 全局补传触发 | 新增在 `NetworkInitializer`（ContentProvider，早于 Application）注册网络回调：实测停在「个人词书」页时网络恢复也能补传，不再依赖学习页 resume |

### 8.5 P4 收尾（2026-10-01，同机同法）

| 用例 | 结果 |
|---|---|
| **离线完成当日学习** | 断网（关 wifi + 断 `adb reverse` + 冷启动，此时连接池已无可用长连接）答完最后一张卡：日志 `完成上报失败，已留在出站队列等待补传`；outbox 新增 `learning_list_completion`（幂等键 `complete_13_12_2026-10-01`，payload 含 `planDate`）+ 该题 `submit_answer` |
| 恢复网络补传 | `补传成功: id=9, kind=learning_list_completion`；服务端 `user_learning_list.is_completed=1, completion_rate=1`（**离线产生的完成上报没有丢**） |
| 锚定仍然生效 | 该离线作答 `study_time=12:16:30` / `answered_at=12:15:30`（滞后 60 秒），due 依旧按作答时刻计算 |
| `sync_meta` 记账 | `lastSyncAt=12:16:51`、`lastTaskPullAt=12:16:51`、`lastError=''` |
| **角标与实际队列一致** | 横幅显示「待同步 0 条作答（上次同步 10-01 12:18）　·　1 条同步失败，点击处理」；入队/补传后立即刷新（不再等下次 resume） |
| **死信可见可处理** | 点击横幅弹出「有 1 条学习记录同步失败」→「清除」后 outbox 清空、横幅消失；注入第二条后「重试全部」使其回到 `pending, attempts=1`（重新排队并立即尝试） |
| 客户端时钟偏差实测 | 在线作答出现 `answered_at` 比 `study_time` 早 **-5 秒**（设备时钟略快）→ 落在 clamp 的 60 秒前瞻容忍区间内，未影响调度（若偏差更大则退化为接收时刻） |

### 8.6 真机测试中发现的既有缺陷（已修）

1. **任务响应被丢弃后不再重试**（已修）：`WordLearningFragment.MyHandler` 原先在 `!isResumed()` 时直接 `return`，而 `onResume` 只在"无卡片"时才重载 —— 实测冷启动时响应若落在未 resumed 窗口，页面会一直空白。现改为置 `reloadPendingForResume`，`onResume` 必查。
2. **切换 Tab 不会触发补传**（已修）：`MainActivity` 用 `hide()/show()` 而非 `replace()`，Tab 切换不产生 `onResume`。已补 `onHiddenChanged` 触发「补传 + 无卡则加载」。

### 8.7 P5 队列自愈加固（2026-10-01，同机同法）

> 测试方法说明：涉及"注入指定状态条目"的用例是**先 force-stop、拉库改库再推回**；注意只拉 `memory_local.db` 会丢掉仍在 `-wal` 里未 checkpoint 的行（本轮的注入就丢过一条），断言时以推送后的实际行为准。

| 用例 | 结果 |
|---|---|
| **空队列短路** | 队列为空时 resume/切 Tab：日志中**完全没有** `OutboxSync` 输出（改造前每次都会打「本轮补传结束：成功 0 条，剩余 0 条」） |
| **点横幅立即重试** | 离线态点横幅 → `补传触发: userId=14, 待发送=1, 死信=0` → `当前离线，暂停补传（剩余 1 条）`，同一次点击还触发了 `GetTodayTask` 重拉 |
| **TTL 真正生效** | 注入一条 31 天前的待发送条目 → 起轮后待发送 2→1，日志/库内该行被清除 ✓（改造前 `prepare()` 无调用点，永远不会清） |
| **死信不再被误删** | 注入一条当日 `state='dead'` → 起轮后**仍然保留**，横幅显示「1 条同步失败，点击处理」✓（删掉了 `purgeExpired` 里无条件的 `state='dead'` 分支） |
| **重试计数不被清零（C）** | 注入"发送中被杀"状态（`state='syncing', attempts=7`）→ `prepare()` 复位为 pending 且 attempts 仍为 7 → 发送失败后达 8 → 转 `dead`，`lastError='network failure'` ✓；正常路径同样可见 `条目重试排队: id=15, attempts=4, delayMs=12842`（3→4，未归零） |
| **单轮 2 次尝试 + 连接超时 5s（E）** | 前台提交离线失败：只有「第 1 次尝试失败，1000ms 后重试」+ 第二次失败，**没有**第二次重试（改造前 3 次）；补传轮次同样只有一次重试。按 `5s connect × 2 + 1s` 推算最坏约 11s（改造前 `15s × 3 + 3s ≈ 48s`）；**注意：连接被立刻拒绝时实测仍是毫秒级失败，"11s 上界"是推算值，未构造丢包环境实测** |
| **单飞仍然有效** | 冷启动时两个触发点并发：第二条打印 `已有补传轮次进行中，忽略本次触发`，未重复建轮 |
| 诊断可观测性 | 早退分支补了日志（未登录 / 空队列 / 已有轮次），此前"为什么没补传"在日志里是空白 |

### 8.8 仍待办（可选项）

- `study_log`（`updateWordStudyLog`）入队：worker 已支持该 kind，但客户端目前无调用点（该端点服务端已标记弃用）；
- 队列「丢最旧」背压（5000 条上限）已有代码但未在真机触发过（需要构造超限数据）；
- 断网时「个人词书」等页面目前只提示「离线数据 · 更新于 X」（Toast），如需常驻横幅可后续增强；
- "服务端已恢复但没有连通性事件"的场景现在靠用户点横幅，若想更自动可加轻量前台定时探测（当前刻意不做，避免引入后台轮询）。
