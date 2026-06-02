# 服务端问题报告：每日单词学习模块

> 生成日期：2026-05-24  
> 关联客户端：Android Memory App (`WordLearningFragment.java`)

---

## 一、当前客户端-服务端交互流程

| 步骤 | 接口 | 方法 | 触发时机 |
|------|------|------|----------|
| 1 | `/learning/getTodayTask` | GET | 用户进入每日学习页面 |
| 2 | `/learning/submitAnswer` | POST | 用户每完成一个单词的答题 |
| 3 | `/learning/updateLearningListCompletion` | POST | 用户完成当天所有单词 |

### 1.1 `/learning/getTodayTask` (GET)

**请求参数：** `userId`

**期望响应格式：**
```json
{
    "code": "200",
    "lexiconId": "CET4",
    "dailyNewWordCount": 10,
    "studyDay": 3,
    "wordList": [
        [wordId, headWord, R, D, S, lastScore],
        ...
    ]
}
```

**客户端行为：**
- 解析 `wordList`，对每个单词用本地 `SharedPreferences` 中的已完成列表做客户端过滤
- 如果某 `wordId` 已被标记为今日完成，跳过该单词（只用于总结统计）

### 1.2 `/learning/submitAnswer` (POST)

**请求参数：**
```json
{
    "userId": 1,
    "wordId": 42,
    "lexiconId": "CET4",
    "headWord": "abandon",
    "isCorrect": true,
    "responseTimeMs": 3200,
    "studyMode": "choice"
}
```

### 1.3 `/learning/updateLearningListCompletion` (POST)

**请求参数：**
```json
{
    "userId": 1,
    "lexiconId": "CET4",
    "studyDate": 3,
    "isCompleted": true
}
```

> ⚠️ **注意：** `studyDate` 字段表示用户当前学习的第几天（即 `getTodayTask` 返回的 `studyDay`），客户端之前一直硬编码为 `1`，现已修复为传递实际值。
> 如果服务端用 `studyDate` 来判断"是否完成词书"，请确认逻辑是否依赖此字段。

**响应格式：**
```json
{
    "code": "200",
    "isCompleted": "true"   // "true" = 词书全部完成, "false" = 仅今日完成
}
```

---

## 二、需要服务端排查的问题

### 问题 A：`getTodayTask` 返回的单词列表不完整

**现象：** 用户进入学习页面时，部分今日应学习的单词未显示。退出应用重新进入后可以看到。

**可能原因：**
1. **服务端返回的 `wordList` 本身不完整** —— 请检查今日任务生成逻辑是否有遗漏。例如：当日新词 + 复习词的合并是否完整。
2. **服务端对同一用户的并发请求处理不当** —— 客户端之前存在重复调用 `getTodayTask` 的问题（`onCreateView` + `onResume` 各触发一次，间隔几十毫秒）。如果服务端在处理第一次请求时修改了内部状态（如标记"今日任务已下发"），第二次请求可能返回空或不同的结果。
3. **`studyDay` 计算异常** —— 如果 `getTodayTask` 返回的 `studyDay` 跳跃（如用户第一天进入返回 Day 5），词书可能被认为快要结束，导致单词数量不对。

**建议排查方向：**
- 检查服务端 `/learning/getTodayTask` 对于同一用户连续两次快速请求的响应是否一致
- 确认 `wordList` 的生成逻辑是否有状态污染

---

### 问题 B：`updateLearningListCompletion` 错误返回 `isCompleted=true`

**现象：** 用户才完成第三天学习，客户端弹出"🎉 恭喜！你已完成本词书全部单词的学习！"

**客户端代码逻辑：**
```java
// UpdateHandler 中判断
String isCompleted = responseJson.optString("isCompleted", "false");
if ("true".equals(isCompleted)) {
    Toast.makeText(getContext(), "🎉 恭喜！你已完成本词书全部单词的学习！", Toast.LENGTH_LONG).show();
}
```

客户端完全依赖服务端返回的 `isCompleted` 字段来展示 toast，自身不做词书完成度的计算。

**可能原因：**
1. **服务端接收到 `studyDate=1`（之前客户端硬编码）** —— 如果词书设计只有 1 天（如"每日一句"类的微型词书），`studyDate=1` + `isCompleted=true` 会被判定为词书完成。**但普通词书（如 CET4 有 30+ 天）不应出现此问题，请检查词书完成判断逻辑。**
2. **`isCompleted` 判断逻辑使用了错误的字段** —— 可能是 `studyDate` 被当作"已完成天数"，当 `studyDate >= totalDays` 时错误返回 `true`。
3. **词书 `totalDays` 配置错误** —— 数据库中该词书的总天数被错误设置为很小的值。

**建议排查方向：**
- 检查 `/learning/updateLearningListCompletion` 服务端实现中 `isCompleted` 的计算逻辑
- 确认传入的 `studyDate` 参数在服务端的具体用途
- 验证该用户对应词书的 `totalDays` / `totalWords` 配置

---

### 问题 C：单词重复出现

**现象：** 学习过程中部分单词在当天重复出现。

**可能原因：**
1. **（客户端已修复）** 客户端之前的双重 `getTodayTask` 调用导致卡片视图叠加。此问题已在客户端修复。
2. **服务端 `wordList` 包含重复 `wordId`** —— 请检查当日任务生成逻辑是否会重复添加同一单词。如果 FSRS 复习调度与新词选取逻辑没有去重，同一 `wordId` 可能同时出现在"新词"和"复习"列表中。
3. **服务端跨天状态未正确重置** —— 如果服务端在前一天结束时保留了部分未完成单词，第二天又将其重新下发，可能造成重复。

**建议排查方向：**
- 检查服务端日志中同一用户同一日期的 `getTodayTask` 响应，确认 `wordList` 中 `wordId` 是否有重复
- 确认新词选取和 FSRS 复习调度的去重逻辑

---

## 三、建议服务端增加/修改的字段

| 接口 | 字段 | 建议 |
|------|------|------|
| `getTodayTask` | `studyDay` | ✅ 已有但建议确保返回值正确 |
| `getTodayTask` | `totalStudyDays` | 🆕 建议新增，返回词书总天数，客户端可用于进度展示 |
| `getTodayTask` | `totalWordsInBook` | 🆕 建议新增，返回词书总单词数 |
| `getTodayTask` | `completedWordsCount` | 🆕 建议新增，返回已学单词总数 |
| `updateLearningListCompletion` | `isCompleted` | ⚠️ 需排查判断逻辑 |

---

## 四、客户端已完成的修复

| 问题 | 修复内容 |
|------|----------|
| 重复 `loadTodayTask` | 添加 `isLoadingTask` 标志位，防止 `onCreateView` + `onResume` 同时触发请求 |
| 卡片视图叠加 | `parseAndCreateCards()` 开头调用 `cardContainer.removeAllViews()` 清理旧视图 |
| `studyDate` 硬编码 | 改为 `int actualStudyDay = studyDay > 0 ? studyDay : 1`，使用服务端返回的实际天数 |
| 网络失败状态 | `msg_failed` 分支也重置 `isLoadingTask = false`，确保重试可用 |
