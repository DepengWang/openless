# iOS 16 起的系统兼容

最低部署版本统一为 **iOS 16.0**，主 App、键盘扩展、OpenLessShared 和 LocalSpeech 使用同一个下限。使用 Xcode 27 SDK 构建，不意味着最低系统必须是 iOS 27。

## 系统路径

| 系统 | 普通模式识别 | 音频采集／权限 | 可选 Apple Speech |
| --- | --- | --- | --- |
| iOS 16 | 打包 SenseVoice，本机 CPU，不预设 LLM | AVAudioEngine 传统 tap；回调先复制值数据，工作 actor 重采样；AVAudioSession 权限 API | SFSpeechRecognizer，仅在支持本机识别时启用 |
| iOS 17／18 | 同上 | 传统 tap；AVAudioApplication 权限 API | SFSpeechRecognizer，本机识别 |
| iOS 26 | 同上 | 传统 tap；AVAudioApplication 权限 API | SpeechAnalyzer／SpeechTranscriber 或 DictationTranscriber |
| iOS 27 | 同上 | immutable installAudioTap；在工作 actor 内复制／转换，保留现有新系统路径 | SpeechAnalyzer／系统转写模块 |

较新的类型隔离在标记了 `@available` 的实现中，调用处使用 `#available`。不支持所选 Apple 本机识别的旧设备会明确报错，**不会偷偷转云端，也不会改变普通模式的 SenseVoice**。旧的 Apple 系统识别可能受系统语言资产、设备能力和会话时长限制；它不是 iOS 16 基础语音的唯一入口。

界面使用 iOS 16 可用的 NavigationStack、ObservableObject／Published、UIKit 文本视图。移除对 iOS 17 Observation 运行时和新版本专属 Button／动画重载的硬依赖。LocalSpeech 的取消标记使用有明确锁保护的 NSLock，不依赖 iOS 18 才提供的 Mutex。

## 不只是修改版本号

- Core 和 Rime 的 device／simulator 静态库都重建为 iOS 16 下限。
- Core 使用 `.build/core-ios16` 独立 Cargo 目录，避免复用之前按 iOS 27 编译的依赖对象。
- `scripts/validate-deployment.py` 检查库中每个 Mach-O 对象，以及最终 App／键盘／debug dylib 的最低版本。旧缓存里混入 iOS 27 对象时会拒绝通过。
- 已下载的 sherpa-onnx／ONNX Runtime 原生二进制最低为 iOS 13，低于本工程的 iOS 16 下限，无需重新下载模型或替换识别引擎。
- iOS 27 的 readonly tap 路径保留；iOS 16–26 的旧 tap 会在回调结束前独立复制，不能跨线程共享系统随后会复用的可变音频缓冲。

## 验证边界

16 组无麦克风合成音频检查覆盖新旧缓冲路径、4 种采样率、单／双声道、尾帧和幅度；旧路径在转换前覆盖原始缓冲，验证复制不依赖原始内存。

SDK 可用性编译、Mach-O 最低版本检查、iOS 27 运行回归是不同证据。**目前没有 iOS 16／18 真机或对应旧运行时的实测，不能宣称每个系统都已运行验收。**旧系统实际安装、权限、音频路由和资源占用仍应在对应设备验证。具体本轮结果见 [verification.md](verification.md)。

## 免跳转不在此兼容承诺内

隐藏式后台免跳转仍未实现；`ExperimentalPiPKeepAlive.isImplemented`／`isReady` 保持 false。降低部署版本不代表任何系统的后台重新开麦、隐藏小窗或免跳转已经完成。没有通过长期占麦或可见 PiP 代替。
