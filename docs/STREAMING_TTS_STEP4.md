# 对话朗读流式化（Step 4：Android 播放层）

> 2026-09-19。记录 Memory 端接入流式朗读的改动、设计取舍与**已验证/未验证**的边界。
> 上游方案见 MemoryServerTTS 的 `docs/STREAMING_ARCHITECTURE_ANALYSIS.md`，
> 中间层见 MemoryServer 的 `docs/STREAMING_TTS_STEP2.md`。

## 1. 原来的播放链路为什么首声那么慢

```
done 事件到达
  → startAudioPolling(messageId)        每 1.5s 轮询，最多 80 次（2 分钟）
  → 拿到 audioUrl
  → downloadMediaFile()                 **整段**下载到本地文件
  → MediaPlayer.setDataSource(本地路径) → prepareAsync → start
```

三个串联的等待点：**整段生成** → **轮询到 URL** → **整段下载**。

其中"整段下载后才能播"有历史原因：服务端对 `/tts-audio/**` 强制 JWT 后，
`MediaPlayer.setDataSource(url)` 无法附带 `Authorization` 头，
所以只能"先鉴权下载到本地、再播本地文件"（`MemoryApiClient` 里有注释说明）。

## 2. 本次改动

| 文件 | 改动 |
|---|---|
| `network/MemoryApiClient.java` | 新增 `streamPcm(url, text, handler)`：POST JSON，返回可**边读边播**的 `Response`（带 Bearer），**不落盘、不缓冲整段** |
| `handle_utils/PcmStreamPlayer.java` | **新增**：用 `AudioTrack` 播裸 PCM，边收边写；自带后台线程、打断上一个会话、生命周期安全释放 |
| `ui/.../AiConversationActivity.java` | `done` 事件后优先走 `startStreamingTts()`；失败**自动回退**轮询；`onDestroy`/发新消息时停播 |
| `ui/.../AiConversationAdapter.java` | 手动点播放前先停流式播放，避免两路声音重叠 |

### 为什么用 AudioTrack 而不是 ExoPlayer / MediaPlayer

后端流式接口返回的是**裸 PCM**（int16LE / 单声道 / 24kHz），**没有容器头**：

- `MediaPlayer` 需要可 seek 的容器（wav/mp3…），裸 PCM 无法直接播；
- `ExoPlayer` 的 progressive 对"长度未知的 WAV"较好，但我们传的是更底层的裸 PCM，
  仍需自定义 `MediaSource`；
- `AudioTrack` 本身就是"PCM 字节流"接口，**天然支持边收边播**，
  且 `write()` 写满缓冲会阻塞——这个背压正好把"读取速度"限制在"播放速度"上，
  于是**内存占用恒定**，不会把整段音频读进内存。

所以这里**不引入新依赖**，用平台自带的 `AudioTrack`。

### 关键实现点

- **打断语义**：同一时刻只允许一个播放会话（与既有 `AudioPlayer` 约定一致）。
  新播放会 `stop()` 掉旧的，避免重叠。
- **背压**：`AudioTrack.write()` 阻塞即背压，无需额外限流。
- **缓冲**：`AudioTrack` 缓冲设为约 500ms——过小易断续，过大增加延迟。
- **采样率**：从响应头 `X-Audio-Sample-Rate` 读取，缺省 24000，不硬编码。
- **失败回退**：流式请求失败 / 非 200 / `AudioTrack` 初始化失败时，
  回退到原有的"轮询 → 整段下载 → MediaPlayer"链路，**功能不会因流式不可用而丢失**。
- **开关**：`AiConversationActivity.USE_STREAMING_TTS`（常量）可一键关掉流式回到原链路。

## 3. 已验证 / 未验证（重要）

| 项 | 状态 |
|---|---|
| Android 代码**编译通过**（`gradlew --offline compileDebugJavaWithJavac` → BUILD SUCCESSFUL） | ✅ 已验证 |
| Java↔Python 透传层逐块转发、首块 666ms、RTF 0.644× | ✅ 已验证（见 MemoryServer 文档） |
| **Android 真机/模拟器上的实际出声效果** | ❌ **未验证** |
| 首声延迟在真机上的表现（含网络 RTT） | ❌ **未验证** |
| 打断/回退逻辑的运行时行为 | ❌ **未验证** |
| 不同 Android 版本 / 机型上的 `AudioTrack` 缓冲表现 | ❌ **未验证** |

**没有条件在真机/模拟器上实跑**，因此上面这些都不能算已验证。
第一次真机测试时建议重点看：
1. 首声是否明显早于原来的轮询链路（预期 ~0.6s vs 十几秒）；
2. 长回复播放是否连续、有无断续（若断续，调大 `PcmStreamPlayer.BUFFER_MS`）；
3. 播放中点"发送"或退出页面，声音是否立即停止（`stop()` 是否生效）；
4. 流式失败时是否正确回退（可临时把 `STREAM_TTS_PATH` 改成错误路径来验证）。

## 4. 音频管理的影响

- 流式路径**不落盘**：不再为每条 AI 回复生成一个 wav 文件，
  因此**不再向 `externalFilesDir/Audio` 堆积文件**，也不需要为流式音频做清理。
- 历史消息的"回放"仍然依赖原来的轮询链路（会落盘），未改动。
  因此音频管理**现状不变**，只是新消息少了一份 wav。
- 若后续要彻底去掉落盘，需要给"回放历史消息"另找数据来源（例如按 messageId
  重新请求流式端点），这属于后续工作。
