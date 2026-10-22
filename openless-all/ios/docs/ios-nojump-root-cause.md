# iOS 免跳转录音根因与证据

日期：2026-10-20

## 结论

Build 8 已确认的实现缺口是：OpenLess 只用 `NoJumpAudioSession` 保留了一个**输出**音频会话，真正录音时仍由 `AudioCapture` 新建 `AVAudioEngine` 并访问 `inputNode`。输出引擎存活不能证明下一次后台开麦会成功。若 `engine.start()` 失败，旧路径还会销毁输出 generation，于是键盘下一次只能重新激活主 App。后台开麦被系统拒绝是当前待验证的解释；缺少该次真机错误日志，不能把它写成已证实的系统根因。

这不是“换输入框必然清理会话”的证据。当前代码中，换输入框主要重建键盘侧 target/lease；没有找到它直接销毁 `NoJumpAudioSession` generation 的路径。录屏能证明发生了两次“准备 → 跳主 App → 返回”，但没有 `OPENLESS_` 真机日志，不能把某个具体的系统错误码伪装成已观测事实。

## 录屏逐帧

输入文件：`/Users/trip/Downloads/ScreenRecording_10-20-2026 22-36-26_1.MP4`，时长 39.623 秒，1206×2622，按 0.5 秒抽取 79 帧。帧号按第 1 帧约为 0 秒计；时间为近似值。

| 帧 / 时间 | 画面与状态文案 | 结论 |
| --- | --- | --- |
| 1–30 / 0–14.5s | 微信聊天中切换系统键盘、进入 OpenLess 键盘、输入英文和打开风格菜单；主 App 没有跳转。 | 普通键盘操作本身没有触发主 App 激活。 |
| 31–34 / 15–16.5s | OpenLess 键盘出现录音面板，随后白色按钮显示“准备中…”，下方显示“正在连接 OpenLess…”。 | 键盘先走主 App 握手，而不是直接开始后台采集。 |
| 35–40 / 17–19.5s | 画面切到 OpenLess 主 App，显示“正在准备语音”“准备中…”；之后回到微信。 | 第一次确实发生主 App 前台激活并自动返回。 |
| 41–50 / 20–24.5s | 返回后的键盘显示红色录音按钮和“再次点击我完成”；顶部出现正常的系统麦克风指示。 | 采集开始后系统隐私指示正常，没有把待机保活误当成持续录音。 |
| 51–55 / 25–27s | 识别结果插入为“试 open试 openless 输入”，随后切到 QQ/另一聊天。 | 第一轮结果已返回；切换聊天发生在下一轮之前。 |
| 56–60 / 27.5–29.5s | 新聊天中又显示录音面板；随后进入等待/准备状态。 | 第二轮没有沿用可直接后台录音的资格。 |
| 61–63 / 30–31s | 键盘显示“准备中…”或“正在等待上一段语音完成…”。 | 第二轮仍在等待主 App/上一段会话收敛。 |
| 64–66 / 31.5–32.5s | 再次跳到 OpenLess，主 App 先显示“可以开始了”，随后显示“正在准备语音”；系统键盘语音面板也短暂出现。 | 第二次同样需要前台激活，不是单纯的输入框切换动画。 |
| 67–70 / 33–34.5s | 返回 QQ，键盘进入录音态并显示红色按钮。 | 第二轮在主 App 前台取得采集资格后开始。 |
| 71–79 / 35–39s | 结果“C试 opened输入功”回到当前 QQ 输入框，随后录音面板收起。 | 第二轮也能自动返回并插入，但仍付出了跳转成本。 |

用户指定的上一份录屏 `/Users/trip/Downloads/ScreenRecording_10-20-2026 16-26-45_1.MP4` 在本机不存在，因此本报告不推断该文件内容。

## 代码证据

`App/NoJumpAudioSession.swift` 的待机引擎只创建 `AVAudioPlayerNode` 和低音量循环 buffer，类别是 `.playAndRecord`，但没有创建 `inputNode` 或安装采集 tap。它的 `outputInputIO` 诊断用于确认待机 output unit 的 input I/O 没有打开。

`App/Speech/AudioCapture.swift` 只有在一次真实录音开始时才执行 `AVAudioEngine()`、读取 `engine.inputNode`、安装 tap 并调用 `engine.start()`。因此后台失败边界发生在 capture start，而不是键盘 UI 或识别结果插入。

`App/AppController.swift` 的旧准入条件是前台，或 `keyboardAudio.isReady`；它没有 PiP 条件。`nativeRequest(captureStart)` 失败后旧代码还调用 `keyboardAudio.deactivate(generation:)`，会把本来可能仍然有效的输出租约一并撤销。现已改为：后台新录音必须同时满足输出租约和 PiP 实际 active；capture start 失败只记录错误并释放本轮 capture，不主动摧毁仍有效的输出/PiP 保活。

## Typeless 对照

仓库中的 `Artifacts/keyboard-reopen-fix/competitor-evidence.md` 以及 `docs/keepalive.md` 对 Typeless 2.7.0 的静态证据显示：键盘重开前会 ping 主 App；`isSelfApp`、麦克风非 IDLE、或 `mainAppState.isPIPActive` 任一成立即可走直接启动；一轮录音停止会停服务但保留 PiP；键盘隐退不清理 PiP；关闭保活、登出或模式切换才 cleanup PiP。其待机麦克风仍是关闭的。

因此本轮采用公开 API 等价边界：`AVSampleBufferDisplayLayer` + `AVPictureInPictureController.ContentSource` + 中性视频帧；Info.plist 使用 `UIBackgroundModes: [audio]`；`picture-in-picture` 不是该键的合法独立值。OpenLess 不调用私有 selector、不伪造隐藏窗口，也不在待机创建 `inputNode`。系统 PiP 窗口的大小和是否被用户收起不能由公开 API 任意隐藏；这部分必须以真机系统行为验收，不能由代码静态宣称与 Typeless 的私有隐藏表面完全相同。

## 需要的真机日志

当前录屏没有配套 `OPENLESS_` 日志。用户有签名包后，在 Mac 打开“控制台”并连接 iPhone：

1. 左侧选中 iPhone 18 Pro `00008160-00065DA114800036`。
2. 搜索框输入 `OPENLESS_`，开始录制日志。
3. 先前台打开 OpenLess、启用键盘语音，再回微信完成两轮录音；保留主 App 和键盘扩展的完整时间段。
4. 重点保留 `OPENLESS_PIP_CONFIGURED`、`OPENLESS_PIP_START`、`OPENLESS_PIP_STARTED`、`OPENLESS_PIP_FAILED`、`OPENLESS_PIP_STOPPED`、`OPENLESS_START_REQUEST`、`OPENLESS_START_DEFERRED`、`OPENLESS_AUDIO_ACTIVATE`、`OPENLESS_AUDIO_RENDER_READY`、`OPENLESS_AUDIO_ACTIVATE_FAILED`、`OPENLESS_AUDIO_EVENT`、`OPENLESS_AUDIO_INVALIDATED`、`OPENLESS_AUDIO_CAPTURE_RELEASE`、`OPENLESS_AUDIO_CAPTURE_START_FAILED` 和 `OPENLESS_VOICE_HANDOFF`。
5. 对每次失败，必须保留同一行里的 `error`、`localizedDescription`、`appState`、`generation`、`outputInputIO` 和 `captureStart` session ID；其中 `cannot start recording`、`required condition is false`、`AVAudioSession` interruption/route change 是判断系统拒绝后台新开麦的关键字段。

## 代码边界

PiP 保活只有在用户启用后台保活且主 App 曾经前台准备过语音后建立。PiP 活跃和输出租约都是真实状态，任一失效时键盘会退回“激活主 App，完成后自动返回”的路径。关闭“免跳转后台保活”会显式停止 PiP、释放输出会话；待机路径不会采集麦克风，只有真实录音请求才启动 `AudioCapture`。

本文件记录的是 Build 8 录屏的现象、代码缺口和 Build 9 的改动；在没有签名安装和 `OPENLESS_` 真机日志前，不把长期后台免跳转标记为真机通过。

## 重启后续接与构建修正

此前工作停在 PiP 提交 `26ae3691` 和自动识别语言的未提交改动，尚未交付 IPA。续接时确认两个设备构建已结束，后台 agent 不再运行，未恢复旧会话。

本次补全了 PiP 的 inline source：以实际 `UIView` 的 `AVSampleBufferDisplayLayer` 作为根界面右上角 48×27 的可见源，持续保留在页面切换之外。帧采用运行中的 host-clock timebase，按 layer 背压投递；待机只绘制中性波形，不打开输入节点。关闭保活同时取消自动启动和重试，迟到的 `didStart` 不会重新发布就绪；暂停的 PiP 不再算作后台就绪。已修正无效的后台模式值，项目静态校验和设备 Release 构建通过。

默认 SenseVoice 固定传入 `auto`；打字布局变化不再使语音准备失效。开发者模式 Apple Speech 使用固定中文 locale，与布局解耦，其混合识别效果仍由系统实现决定。

这不是隐藏 PiP：系统可以显示画中画浮窗，用户可按系统方式收起。没有本次真机运行证据，不能保证当前 iOS 版本允许 PiP 中重新开麦。最终验收与 IPA 见 `ios-build9-release.md`。
