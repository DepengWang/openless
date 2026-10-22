# 跨应用录音：Build 10 当前实现

2026-10-21：用户已确认 Build 9 基础 PiP 免跳转可用。本轮改为隐藏 VideoCall PiP 保活、空闲释放音频，目标与验收见 [Build 10 目标](ios-build10-goals.md)，隐藏/共存方案见 [PiP 设计](pip-hidden-coexistence.md)。

当前准入依赖用户激活代际、实际 PiP active/non-suspended、麦克风权限、解锁和系统中断状态；不再依赖静音输出引擎或 render timestamp。停止一次录音只关闭输入与音频会话，保留 PiP 和激活意图；关闭保活才显式销毁。运行时接口不兼容时降级为前台激活，不报告隐藏或共存成功。

下文全部为 Build 9 及以前的历史记录，其中“当前实现”“下一步”等措辞仅对应历史时点，不代表 Build 10 的代码状态。Build 10 真机效果以新包验收记录为准。

---

# 跨应用录音：输出会话与 PiP 研究

## 2026-10-20：再次收起键盘后的交付与准入修复

本次重新检查了“停止录音 → 收起键盘 → 切到其他 App → 再录”的代码路径，发现 Build 6 仍有三处缺口：

- 输出音频在独立渲染线程上继续运行，但 `isReady` 使用主线程健康计时器缓存的时间。主线程延迟超过三秒后，IPC 回调可能先于计时器执行，把仍在输出的会话误判为失效。现在每次准入直接读取 `AVAudioPlayerNode.lastRenderTime`；健康计时器继续负责清理真实停止的会话。
- 切换中英文现在只改变键盘布局，不会清零 `speechPrepared`，也不会重新准备识别器；能力快照始终使用 `sourceLanguage=auto`。布局切换不会创建采集引擎或重新激活音频。
- 录音结束时键盘可能已经消失，识别结果仍向旧 `keyboardID/documentID/revision` 发送插入请求。旧扩展不再回执，Core 等待三十秒，新录音握手却在二十秒超时。现在键盘重新接入时，不携带 session ID 的有效空闲 ping 会撤销旧交付并立即回复 Core；即使 UIKit 重用同一个输入目标也适用。仍持有录音/结果的键盘发送带 session ID 的心跳，不会误撤销自己的结果。文字仍留在 SQLite 的待确认记录；尚未完成的识别结果随后到达也遵守该撤销。迟到的旧回执不能消费新会话，旧 native `insert` 必须匹配当前 Core 会话 ID。

键盘文案也区分了等待录音处理、识别配置准备、需要激活和主进程失联；空闲键盘不再一直显示“正在连接”，失去后台资格也不再被统称为“OpenLess 正忙”。

本次新增共享交付消息序列测试，覆盖真实 Mailbox 的旧插入、目标更换、保留待确认、新会话和迟到回执。原生音频探针增加了阻塞主线程 4.2 秒后直接检查实时准入的场景。最终运行结果由本轮构建/测试记录提供；代码修复本身不构成用户 iPhone 上长期免跳转已通过的证据。

Build 7 产物、测试记录和截图见[本轮交付记录](../Artifacts/interaction-build7/README.md)。

### 竞品与平台依据

[Typeless 官方说明](https://www.typeless.com/help/release-notes/ios/when-does-app-switching-happen)（2026-07-28，检索于 2026-10-20）明确区分两种状态：可直接听写，以及需要短暂打开主 App 取得麦克风后返回；同时说明待机时麦克风关闭。它没有承诺任何系统状态下都无需激活。仓库已有的 [Typeless 字节码复核](../Artifacts/keyboard-reopen-fix/competitor-evidence.md) 则显示，键盘隐藏、停一轮录音和关闭 PiP 保活是独立入口；本次沿用这种生命周期分离，没有声称恢复了其隐藏 PiP 原生实现。

Apple 的[自定义键盘文档](https://developer.apple.com/documentation/uikit/creating-a-custom-keyboard)说明键盘在独立且受内存限制的进程中运行，收起键盘也不意味着扩展必然销毁。因此后台语音能力与结果交付都不能以键盘视图是否还存在作为唯一判断。Apple 的[音频引擎配置变更文档](https://developer.apple.com/documentation/avfaudio/avaudioengineconfigurationchangenotification)还说明硬件通道数/采样率变化会停止引擎；本项目继续在真实输出停止、中断或权限失效时撤销准入，不凭缓存布尔值宣称可以后台开麦。

## 当前实现：输出音频会话 + PiP

键盘仍通过 `NoJumpAudioSession` 的独立输出引擎保持后台服务，录音期间才运行输入引擎；首次在主 App 前台准备语音后，`ExperimentalPiPKeepAlive` 使用公开 `AVSampleBufferDisplayLayer`／`ContentSource` 建立 PiP 保活。只有输出租约和 PiP 都真实有效时，后台新录音才直接准入；待机不创建 `inputNode`，也不采集麦克风。Build 9 的代码根因与准入边界见 [免跳转录音根因](ios-nojump-root-cause.md)。

Build 6 补充了键盘收起重开的真实录音验证与两个准入竞态修复；Build 9 新增 PiP 保活和后台录音准入。公开 API 不提供完全隐藏 PiP 窗口的开关，且本机当前没有签名身份，因此 Build 9 仍不能宣称通过 iPhone 真机长期免跳转验收。

下面保留的是此前的 PiP 研究与 2026-10-17 历史状态；它不覆盖 Build 9 的公开 API 实现。系统中断、锁屏、其他 PiP、来电／Siri、进程回收和能耗仍需真机验证。

## 2026-10-17 历史验证路径

- 系统控制中心录音已实现：iOS 27 模拟器中，主 App 不在进程列表时点击控制可后台启动主进程，收到真实音频帧，持续 79.7 秒后主动停止。实时活动只覆盖录音期间，结束时释放采集并结束活动。
- 键盘前台往返已实现：键盘按钮打开主 App 的对应请求，用户点按开始，返回 Safari 后继续录音，由键盘停止、预览并确认插入。输入位置变化时再次确认，交付不自动重放。
- 键盘直接后台启动仍未实现。此前真实键盘的 `Button(intent:)` 路由证明只执行在扩展进程；控制中心的成功不能替代这项证据。
- 最新界面与 Release 产物见 `../Artifacts/consumer-redesign/README.md`；语音运行证据见 `../Artifacts/overnight-usability/acceptance-summary.json`。模拟器证据不能代替真机运行。

继续检查发现一台已连接且开发者模式已开启的 iPhone 18 Pro（iOS 27.0.1 / 24A446）。本机当前可用代码签名身份为 0，项目未配置 `Config/Local.xcconfig`，该设备也未安装本项目默认 bundle。真机安装仍需有效开发证书、Team 和 App Group 配置。

## 硬要求

在明确激活过的会话内，同时做到：

1. 键盘请求录音时不切主 App 前台。
2. 两次录音之间没有持续音频采集。
3. 没有长期可见的小窗、横幅、边缘拉手。
4. 仍显示系统真实录音隐私指示；不越狱、不绕过麦克风权限。

## 已有证据与缺口

- 用户提供的 `ios-no-switch-report.md`：竞品将键盘、主进程采集和保活分离，存在 PiP／hidden／stash 模块线索。
- Typeless 2.7.0 字节码：停录时关闭音频服务但保留 PiP 的分支可读；原生隐藏实现和设备行为未恢复。
- 本机 iOS 27 SDK：公开 `AVPictureInPictureController` API 没有完全隐藏窗口的开关。
- 历史 [AVKit](https://github.com/nst/iOS-Runtime-Headers/blob/master/Frameworks/AVKit.framework/AVPictureInPictureController.h)／[Pegasus](https://github.com/nst/iOS-Runtime-Headers/blob/master/PrivateFrameworks/Pegasus.framework/PGPictureInPictureController.h) 头文件只作研究导航，不能证明 iOS 27 的接口、ABI 或行为。
- 已安装 iOS 27.0 模拟器运行时，App、实际键盘扩展与只读诊断测试已运行。真机连接与签名状态见上方更新；模拟器 UI、macOS PiP 行为均不能替代 iPhone 后台重新开麦和隐藏窗口证据。

## 代码实际状态

`ExperimentalPiPKeepAlive.isImplemented` 只表示当前系统具备公开 PiP API；`isReady` 只有在支持、已启用且系统报告 PiP active 时才为 true。它不写死成功状态，也不会在待机时打开麦克风。关闭设置中的“免跳转后台保活”会停止 PiP 并释放输出会话。

`Resources/KeepAliveCompatibility.json` 为空；即便人为修改 JSON，也不会把不存在的隐藏实现变成就绪。没有“只差打开开关”的承诺。

当前系统控制中心后台录音与键盘前台往返可独立使用；Build 9 的键盘直接免跳转仍需要签名包和 iPhone 18 Pro 运行证据。公开 PiP 实现不等于 Typeless 的私有隐藏表面。

## 下一步必须在真机完成

- 配置签名及 App Group，登记机型和完整系统 build。
- 收集目标系统的实际接口，检查方法签名及对象所有权；明确恢复隐藏方法后才在隔离实验模块实现。不能猜 selector、复用历史 ABI 或盲目调用 `perform`。
- 先验证隐藏动作是否维持真正后台执行及重新开麦权限，而不只是看不到视频内容。
- 激活后连续 20 次听写，以及空闲 1／5／30 分钟后恢复。
- 核对真实采集停止、系统麦克风指示、外部录像中的所有悬浮 UI。
- 来电、Siri、其他媒体／PiP、蓝牙、锁屏、键盘进程回收、强退主 App：退出就绪且不得自动重开麦。
- 记录 CPU、内存、发热和能耗，再登记可用 build。只出现一个成功截图不算通过。

没有通过就保留“不支持／未验证”，不得切换成持续占麦、透明内容的小窗、自动收边残留或循环跳转。
