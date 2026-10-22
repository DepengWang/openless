# A1 语音往返诊断（2026-10-20）

基线 `5780e153`；先记录诊断，再修改代码。本文区分代码可证明的缺口和仍需真机日志确认的原因，不把模拟器构建当作真机验收。

## 现象、路径与判断

| 现象 | 代码路径 | 根因判断与证据 |
| --- | --- | --- |
| 录屏 0–5 秒准备/连接 | `startVoice → beginVoice → KeyboardStartHandshake.advance`；过期快照连续 3 次 `.probe`，每次 1.2 秒，随后 `.openHost → requestForegroundVoice`；宿主查询最多再等 2 秒 | **确定存在串行等待放大**，两段等待可达约 5.6 秒。快照过期只说明没有新状态；录屏不能区分进程挂起、退出、通知失联或音频会话失效。 |
| 5–9 秒已录音却停在 OpenLess | `receiveHostActivation → scheduleAutomaticHostActivation → startHostActivation → prepareSpeech → begin`；首次真实 `level` 事件才触发 `returnToKeyboardAfterCaptureStarted`；`HostApplicationReturn.m` 使用系统导航 action，失败再用精确 bundle | **录音已经启动，等待首次音频不是持续滞留的充分解释**。键盘私有 arbiter 要求两次新通知，真实系统若只发送一次会丢弃已读到的来源；跨团队 `sourceApplication` 又可能为空。没有本次日志，不能断言是来源为空还是返回 API 被拒绝。导航 action 的“accepted”也不等于真的离开前台。 |
| 微信内换聊天后准备/请求未完成/再激活 | `changedInput → stopForDestinationChange`；新请求创建新的 `KeyboardStartHandshake`，重新使用共享快照 | **未发现换文档直接撤销后台资格**。清空的是旧录音的目标、握手和 `activeHostEpoch`；`NoJumpAudioSession.epoch` 属于主 App，不在此路径清空。旧快照的 `.error` 当前会在 `activeClient == nil` 时进入新输入框的状态，造成无关错误沿用；这与是否有真实后台资格是两件事。 |
| 返回宿主后下次还要激活 | `NoJumpAudioSession.activate/checkHealth/releaseCapture` 与通知观察者 | `prepareSpeech` 已尝试激活输出会话，`foregroundChanged(false)` 也不会主动关闭它；不存在“接管漏调用 activate”的证据。**确定缺口**：中断 began/部分路由变化立即 deactivate；interruption ended 被忽略；音频引擎配置变化无恢复处理。发生一次中断后，本次输出资格永久丢失，必须再次前台激活。低音量循环输出能否在用户真机长期维持后台执行仍未验证。 |

用 ffmpeg 每 3 秒抽帧核对了原录屏：约 6 秒主 App 两处都显示录音，约 9 秒多任务卡片已回微信，约 21 秒第二个聊天显示请求失败。录屏显示的是可见行为，不能替代 Console 的分支证据。

## 修复边界

保留真实音频渲染时间、权限、锁屏与 session generation 的准入校验；绝不把已停引擎标记为可录音。缩短失联握手的串行等待，仍必须先探测；处理可恢复的输出配置变化及系统明确允许的中断结束，只恢复输出，绝不自动恢复麦克风。输入框改变必须继续终止旧请求与旧目标交付，但不能污染下一次请求的状态。

自动返回仍须保留本次输入目标/来源，重试只使用当前请求的精确 bundle 或唯一系统导航目标；不猜微信 URL scheme、不用上一次宿主缓存。返回在真实 PCM 已到达后执行，以免切后台过早让采集无法启动。

## 真机与日志

`xcrun devicectl list devices` 发现 iPhone 18 Pro（`00008160-00065DA114800036`）；`device info details` 确认有线连接、Developer Mode 开启、iOS 27.0.1（24A446）。另一台 iPhone 16 Pro 不可用。本记录尚未宣称新包安装或真机验收通过。

Mac 打开 Console.app（控制台），左栏选连接的 iPhone，开始流式记录，启用 Info/Debug 消息，搜索 `OPENLESS_`。保留 OpenLess 主进程与键盘扩展两者；不要只按进程过滤主 App。先清空显示，再按以下顺序复现并保存 `.logarchive` 或导出的日志：

1. 前台打开 OpenLess 并启用键盘语音，返回微信原聊天；点击一次说话、停止，确认只上屏一次。
2. 同一微信 App 内切到另一聊天，再点一次；分别等待 5 秒、30 秒、5 分钟后重复；记录是否跳转及原光标位置。
3. 主 App 确实不响应时点一次，确认激活后自动开始和返回，无需第二次点击；记录 `source`、`target`、`accepted` 和应用前后台变化。
4. 接入/断开蓝牙、Siri/来电中断结束、锁屏解锁后重复；中断时应停止当前录音，不自动重开麦克风。检查恢复输出是否真正渲染、空闲 `outputInputIO=0`。
5. 启动准备中切输入框/取消，确认旧回调不会再跳转或向新输入框投递旧会话。

需要关联的日志：`OPENLESS_START_*`（请求/探针/原因）、`OPENLESS_AUDIO_*`（真实引擎/渲染/中断/恢复）、`OPENLESS_HOST_*`（宿主查询）、`OPENLESS_VOICE_HANDOFF`（来源/返回）、`OPENLESS_DESTINATION`（旧目标解除）。不记录文本、音频、凭据。

若真机日志证明音频循环被系统挂起，Darwin ping 无法唤醒挂起进程，缩短超时只能减少等待，不能解决免跳转。可落地后续方案是：在真机做独立 PiP/系统录音控制实验，验证后台重新开麦、停止后麦克风释放、能耗及用户可见界面；按系统/机型记录实测资格，再接入产品。当前 PiP 未实现，不能声称隐藏 PiP 或绝对免跳转已完成。

Apple 对引擎配置变化会停止引擎的说明见 [AVAudioEngineConfigurationChange](https://developer.apple.com/documentation/foundation/nsnotification/name-swift.struct/avaudioengineconfigurationchange)，中断结束的恢复提示见 [Responding to interruptions](https://developer.apple.com/library/archive/documentation/Audio/Conceptual/AudioSessionProgrammingGuide/HandlingAudioInterruptions/HandlingAudioInterruptions.html)。这些说明支持恢复处理的必要性，不证明本机输出保活有效。

## 已实现的修复与验证边界

- 探测改为一次 1.2 秒有界 ping，再决定前台接管；没有把旧快照当作真实 ready。单次请求仍有原目标、20 秒总期限和结束闩锁。
- `NoJumpAudioSession` 在输出引擎配置变化时有限重启输出图；系统中断结束且带 `shouldResume` 时，才尝试恢复之前已由用户激活的输出会话。iOS 27 的 didBecomeInactive 与 interruption-began 先后顺序均保留该恢复意图；锁屏、媒体服务丢失、用户关闭或不允许恢复仍撤销资格。通知在其原本的 main queue 中处理，避免额外异步 Task 让旧中断影响新一代会话。
- 新增可选 `backgroundRecovering` 表示真实的有界修复过程，键盘等待它结束；`backgroundReady` 仍只来自真实渲染/采集。恢复不创建输入节点、不复活被中断的录音，失败就撤销输出资格。
- 进一步确认来源查询的缺口：`stopConnection/checkConnection` 期间无 bundle 的中间通知原来会立即结束查询，已改为等到原超时或完整来源；仍要求两次新 generation 的一致来源及原 document/window 挂载，不采用单次来源回退或历史缓存。此为代码可证缺口，是否正是微信本次失败原因仍需新日志。
- 主 App 进入 active 时也检查已到达的 captureReady，避免首次音频事件早于 scene 激活时错过自动返回；详细记录导航 action/精确 bundle 打开/返回是否实际观察到。仍在收到真实音频后返回。
- 无当前请求时不把旧 `.error/.cancelled` 沿用到新输入框；换输入框继续只解除旧会话目标，保持主进程音频资格独立。

本轮模拟器构建与定向共享测试通过；它们不能证明真机后台输出会话可长期存活，也不能证明微信来源查询/自动返回实际成功。设备已确认连接，本通道未覆盖安装用户设备上的包，交协调者合并两个通道后统一签名安装并按上文抓日志。若最新日志仍显示无响应或返回失败，需依据具体 `OPENLESS_` 分支继续真机修复，不应把本轮标记为真机 A1 验收通过。
