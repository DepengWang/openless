# 隐藏 PiP 与生命周期集成（2026-10-21）

## 目标与当前实现

待机只保留 PiP 和激活意图，不打开麦克风、不持续占用音频输出；后台录音准入必须同时检查真正活跃且未暂停的 PiP、解锁状态及音频捕获条件。用户关闭保活立即撤销状态；系统停止 PiP 后及时降级，不能沿用缓存的“已就绪”。

`ExperimentalPiPKeepAlive` 使用透明 `AVPictureInPictureVideoCallViewController`，尺寸为 300 × 0.1pt，替代普通 sample-buffer 视频方块。附着在主窗口的透明 `PiPSourceView` 提供系统启动几何位置。没有 AVPlayer、sample-buffer 视频、静音播放或音频输出引擎。活跃且未暂停、未锁屏时，每秒一次极低透明度内容更新；停用、暂停或锁屏后停止更新。这保留参考的内容更新路径，但 1Hz 是本次节能集成选择，不是已证实的长期保活条件。

`PiPCoexistenceAdapter` 将私有接口隔离在一处：构造期间仅对本次捕获的 adapter 返回 `_proxyControlsStyle = 5`；构造结束立即恢复该实例的原生 getter 行为。获取 lazy proxy 前装入限定实例的 content-type provider，启动使用 type 4，真实 `didStart` 后等待一秒，只提交一次 type 6。其他 adapter、其他 proxy 保留自身行为；不改对象的 isa，不循环强制覆盖系统状态。卸载时清除关联并恢复本地 type 4，不发送额外远端状态。

调用前校验 selector、参数数量、返回类型、参数类型、proxy 类和 `proxy.delegate === adapter`；拒绝覆盖系统原本已有的未知 provider。检查失败会明确返回免跳转不可用，走现有返回主应用录音路径。类上已安装的 wrapper 会保留到进程结束，但未关联实例继续执行原有行为。

## 调用契约

- `arm()`：开启保活意图并准备控制器，重复调用幂等；不启动音频输入。
- `startIfPossible()`：有界启动。最多等待五秒 source/系统就绪；真正发起启动后再给系统最多五秒回调时间。
- `onPrepareStart`：由音频所有者准备启动所需的短时 playback 会话；PiP 本身不改音频 category、route 或 active 状态。回调抛错时，音频所有者负责清理其部分准备。
- `onSettled`：每次成功的 prepare 对应一次结束通知，涵盖 didStart、失败、超时、锁屏取消和 stop；音频所有者据此释放空闲会话，不得停掉正在进行的用户录音。
- `foregroundChanged(Bool)`：恢复已有保活意图。前台通知与 didStop 顺序交错时，每次前台访问最多补一次恢复；后台被另一个 PiP 挤走时不持续争抢。
- `onChange`：重新计算音频准入、发布真实 readiness。`isRecovering` 仅在等待或启动期间为 true。
- `stop()`：取消任务、控制器、KVO、内容更新、私有适配和音频启动租约。异步回调检查控制器 identity 或 generation，旧回调不能重新激活新状态。

锁屏通知立即撤销 readiness、取消未完成启动、停止内容更新。已经活跃的 PiP 保留给系统管理；解锁后若仍活跃则重新检查暂停状态并恢复内容更新。如果锁屏打断了启动、或锁屏期间该控制器停止，解锁时消耗一次恢复标记，在前台或后台均做一次有界恢复尝试；同时覆盖 AVKit active 先变、didStop 晚于解锁通知的顺序。普通视频 PiP 挤出不产生标记，用户关闭立即清除标记，恢复失败不循环重试。其余已结束 PiP 只有前台访问触发恢复。KVO 监听 active/suspended，实际状态变化会通知音频所有者。

## 证据边界与验证

参考源是工作区已保存的 GlobalRefresh-PiP commit `1b62341c1d62308962fb3656192d6783631aa00c`（`Artifacts/release-10/research/globalrefresh`），不是声称恢复了豆包或 Typeless 的完整源码。来源署名随包保留在 `Resources/Licenses/GlobalRefresh-PiP-NOTICE.txt`。集成去掉了参考的 60Hz 动画、刷新率策略、诊断全局 hook 和不相关 UI。

可复现检查：`bash ios/scripts/test-pip-runtime.sh`（从 openless-all 目录）。它用生产 `PiPCoexistenceAdapter.swift`、Swift 6 与 MainActor 默认隔离，在本机 ObjC runtime 验证构造 getter 的实例范围、签名拒绝、代理身份、type4→6 一秒延迟、一次提交、暂停后恢复、旧任务/延迟 block 失效、恢复原有行为及 100 次释放循环；另直接验证生产 `PiPLockRecovery` 的一次解锁恢复、两种回调顺序、启动被锁打断、视频 PiP 隔离及用户关闭清理。脚本删除全部临时编译文件。

这些测试不模拟 iOS 画中画服务。系统是否接受远端 type 6、0.1pt 实际视觉隐藏、B 站/其他视频 PiP 同时运行、音频释放后的长时驻留、自动熄屏及锁屏恢复，仍必须在目标 iPhone/系统上验证。诊断里的 `transitionSubmitted=true` 只表示本地 mutation 已执行，不表示远端已接受多 PiP。readiness 仍以 AVKit active/suspended 和锁屏状态为准；系统不兼容时会降级，不假报成功。

真机验收顺序：全退后重新激活 → 键盘连续录音两次 → 结束后 B 站外放音量/路由 → B 站开启自己的 PiP → 回主 App 再离开 → 锁屏/解锁 → 用户关闭保活。每一步确认 readiness、麦克风占用、声音和浮窗；特别检查 didStart 后释放音频不会导致 PiP 立即结束。
