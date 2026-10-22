# Build 10：目标、实现与验收边界

日期：2026-10-21。接续 Build 9，用户已确认基础 PiP 免跳转可用；本轮解决其后续反馈，不重做该结论。交付未签名 IPA，由用户自行签名。桌面端既有改动不属于本轮。

## 目标与实现

| 用户目标 | 本轮实现 | 验证要求 |
|---|---|---|
| 免跳转没有可见黑块、控制条或侧边拉手 | 透明 VideoCall 内容、0.1pt 高度、构建时隐藏 controls 适配；停止普通 sample-buffer 视频路径 | 编译与内部接口 fixture；实际外观需真机 |
| B 站等其他视频可以使用 PiP | 独立兼容层：按签名检查内部接口，普通 VideoCall 启动后提交一次共存内容类型；不循环抢占系统 PiP | fixture 验证隔离/取消，双 PiP 需要真机 |
| 锁屏解锁后保留免跳转意图 | 锁屏不撤销激活代际、不销毁已活跃 PiP；解锁重新读取实时状态；不会自动重开麦克风 | 状态测试；锁屏期间系统是否保留 PiP 需真机 |
| 回到 OpenLess 再离开无需再次激活 | 激活意图与前后台、音频录制状态分离；前台同步 PiP 并有界恢复 | 状态测试及真机前后台序列 |
| B 站恢复正常扬声器，无需切两次 | 删除空闲输出引擎与静音循环；只在录音期间激活 playAndRecord；硬件停止后 deactivate 并切回非录音类别；失败做有界重试，空闲路由通知不重启会话 | driver 测试验证释放顺序；音量/路由需真机 |
| 拼音模式能提交 openless 原文 | Return/原文候选提交 rawInput，空格/中文候选保留中文提交语义 | 真实 Rime smoke + 聚焦 UI 用例 |
| 键盘按钮更易点击 | 模式切换、语音工具、发送等尺寸调整；原文候选至少 44×44pt | 语法/布局检查，操作体验待验收 |

## 拆包证据如何转成代码

Typeless 的 `stopRecording → stopService → cleanupKeepActive` 在 PiP 模式跳过 PiP cleanup。键盘先 ping 主 App，PiP active 时允许 microphone IDLE 直接启动。依据是 `Artifacts/keyboard-reopen-fix/competitor-evidence.md` 的 Hermes 字节码复核，不是原生隐藏方法体。

因此 `NoJumpAudioSession` 不再维持输出音频租约。`activate()` 只建立用户激活代际；`AppController` 将实际 PiP readiness 作为后台录音能力；`acquireCapture(.keyboard(generation))` 同时校验代际、PiP、权限和锁定/中断状态。录音服务仍只有收到显式请求才创建 inputNode。`AudioCapture.stopHardware()` 先拆掉输入引擎，再释放会话；转写和插入无需持有音频。

豆包的 hidden/stash/hook 导出以及 Typeless 的 pipHideEnabled 分支证明存在专用路径，不能恢复其全部原生实现。本轮隐藏/共存兼容代码参考已归档的 GlobalRefresh-PiP 固定版本，来源、运行时签名检查与未知边界见 `pip-hidden-coexistence.md`。不将参考项目方案冒称为豆包或 Typeless 原始源码。

PiP 启动允许临时 playback + mixWithOthers 会话，在启动完成、失败、取消、超时后释放；若此时录音或历史播放已接管音频，不得提前 deactivate。音频路由不调用强制 speaker override，不周期性改路由，不模拟点击控制中心。录音类别的 defaultToSpeaker 仅覆盖无外设时的内置默认值；空闲时退出录音类别才是本轮的主要修复。

## 验证与证据边界

- 自动测试能证明：状态转换、旧代际取消、录音结束释放、空闲不重开音频、兼容层实例隔离和 ABI 不符时降级，以及键盘原文提交。
- 构建能证明：目标设备架构、Swift/Objective-C 编译链接、包结构和资源完整性。
- 上述均不能证明：iPhone 上隐藏视觉、其他视频 PiP 共存、后台重新开麦、锁屏后的长期保活、实际扬声器音量。
- `KeepAliveCompatibility.json` 继续为空，不伪造系统/机型实测记录。若接口不兼容、PiP 停止或已被系统挂起，`isReady` 必须为 false，键盘使用既有前台激活路径。

## 真机验收序列

1. 自签安装 Build 10，首次激活后在微信连续录音两次，确认真实 PCM、识别结果和麦克风指示正常。
2. 停录后确认麦克风指示消失，检查完整屏幕和侧边无残留浮窗；开启 B 站播放，不切输出设备，记录音量与控制中心输出列表。
3. 打开 B 站视频 PiP，再返回微信录音；关闭视频 PiP 后再录音。
4. 锁屏 30 秒及 5 分钟，解锁直接在原应用录音；随后进入 OpenLess，再返回原应用录音。
5. 插拔耳机/切蓝牙，结束后播放视频；确认不会被后台会话持续改回内置输出。
6. 拼音输入 openless：Return 和原文候选应提交英文；中文候选和空格仍提交中文。

收集 `OPENLESS_PIP_*`、`OPENLESS_AUDIO_ARM`、`OPENLESS_AUDIO_IDLE_RELEASED`、`OPENLESS_AUDIO_EVENT`、`OPENLESS_AUDIO_CAPTURE_START_FAILED` 与诊断中的 routeIn/routeOut/idleAudio/provider/localType。若失败，按实时 PiP、系统开麦结果、会话释放结果定位，不用布尔开关替代真机证据。
