# ASR 入口与文件导航

## 当前怎么用

普通模式使用已打包的 **SenseVoice Small int8**，在主 App 本机识别，不预配 LLM，不经过 LLM 后处理。准备只做权限与模型初始化；用户主动开始后才采集声音。

### 语言语义

`KeyboardPreferences.language` 只表示默认打字布局（中文全拼或 English），不再决定语音识别语言。旧偏好仍按原有 `Codable` 解码规则读取，缺失字段继续使用默认中文布局。

- 键盘能力快照的 `sourceLanguage` 固定为 `auto`，普通听写默认支持中英文混合。
- SenseVoice 的语言参数固定传 `auto`，不会因为用户切换中文全拼／English 而重建识别器。
- Core 管理的云端 ASR、LLM 和 Omni 管线不接收键盘布局字段；翻译语言仍只由一次录音的上滑选择和已配置目标控制。
- 开发者模式的 Apple Speech 仍使用系统可用的中文 locale 作为模块选择参数，但它与打字布局解耦；系统 Speech framework 的具体混合语言能力由设备和 provider 决定。

开发者模式在设置中连点版本三次后进入，安全重载界面与 Core，而不是退出／重启 iOS 进程。可选择原生 Apple Speech、Core 已支持的云端 ASR、自定义服务、LLM 后处理或全模态管线。云端模型列表／验证只在主动操作时请求，密钥保存在主 App Keychain。

2026-10-16 用户反馈应用内语音已经能正常聆听、感知，正在手工测试。这与自动化公共音频 smoke 分开记录；不据此宣称跨 App 后台免跳转完成。

## 原生实现统一放在 `App/Speech/`

| 文件 | 职责 |
| --- | --- |
| `NativeSpeechService.swift` | 原生识别入口；选择 SenseVoice 或 Apple，控制输入、完成、取消和释放 |
| `AudioCapture.swift` | AVAudioSession／AVAudioEngine；按系统版本选择 tap，先停硬件再排空处理 |
| `AudioPCM.swift` | 可发送的音频值快照、16 kHz mono S16LE 重采样、有界尾帧 |
| `MicrophoneAccess.swift` | iOS 16 与 17+ 的权限 API；不从键盘请求麦克风 |
| `AppleSpeechService.swift` | Apple provider 门面与授权桥接；按系统版本选择后端 |
| `ModernAppleSpeechService.swift` | iOS 26+ 的 SpeechAnalyzer／系统转写和资源准备 |
| `LegacyAppleSpeechService.swift` | iOS 16–18 的 SFSpeechRecognizer，本机模式、取消、结束超时和结果回调隔离 |

其他相关入口：

- `Packages/LocalSpeech/Sources/LocalSpeech/SenseVoiceRuntime.swift`：离线 C API actor、两个 CPU 线程、20 秒分块、取消与长度上限。模型不进入键盘。
- `CoreHost/src/lib.rs`、`control.rs`、`credentials.rs`：普通／开发者模式约束、复用桌面 Core provider、控制请求和 Keychain。
- `App/AppController.swift`：业务状态、音频会话生命周期与 Core 回调；不实现另一套 HTTP ASR。
- `PlatformShared/SharedContainer.swift` 与 `Packages/OpenLessShared/`：v2 准备／开始命令、输入目标身份和持久上屏回执。

桌面的实验性本地 ASR 仍以仓库根目录的 [experimental-local-asr.md](../../../docs/experimental-local-asr.md) 为入口。之前研究的 TextStream／Qwen3-ASR Mac MLX 子进程不是 iOS 实现，不在手机上包装桌面 subprocess。

## 资源与验证

- [固定模型、运行时和哈希](local-speech-provenance.md)
- [iOS 16 起的版本路径与兼容边界](platform-support.md)
- [完整处理管线、显式选区与后台缺口](architecture-and-processing.md)
- [测试／安装包／截图证据](verification.md)

不重新下载已校验资源，不删除已有桌面实验 ASR 工作，也不把 ASR 分段累计、识别器标点或 `finalText` 字段冒充 LLM 改写。
