# iOS 架构与实际处理管线

更新：2026-10-16。参考来自用户提供的豆包／Typeless 拆包报告，不是竞品真机实测，也没有恢复其服务端。当前 KeyboardKit 已通过中英文、连续五次重开、系统听写键隐藏、符号／换行、Caps Lock／空格光标／横滑及横屏回归；完整浅色套件为 12 UI、4 原生通过、1 授权回调跳过。模拟器回归与构建成功仍不代表真机语音或完整后台能力。

## 进程与依赖

主 App 使用 SwiftUI／UIKit、AVFoundation、原生识别器和 Rust Core。键盘是 `UIInputViewController`，嵌入 **KeyboardKit 9.9.0 MIT 源码版**的 `KeyboardView`；它负责普通键位布局与触摸、大小写、符号、删除和光标手势，OpenLess 适配 Rime 组合／候选、英文补全和语音控制。不是仿造开源键盘外观，也没有引入 KeyboardKit 当前的闭源二进制版。

保留 UIKit 控制器和 nullable Objective-C 文档标识边界，避免断连时把 nil 强桥接为 UUID。无有效文档就停止目标相关操作，不生成假 UUID。键盘收起走 UIKit 原生生命周期；移除试用页额外的跨进程清焦点通知，避免收起旧会话的通知干扰下一次编辑。没有靠额外点击手势或反复 resign／become 修补。

- Rime 与学习数据留在扩展私有容器；基础中文全拼、English QWERTY 不要求完全访问。
- SenseVoice、sherpa-onnx、ONNX Runtime、Rust Core 只链接主 App。键盘不加载模型、不打开麦克风、不持有提供商密钥。
- Full Access 只允许共享偏好与控制通信，不代表后台开麦能力已实现。
- App Group SQLite WAL 是持久命令／结果通道；Darwin 只通知重读。v2 协议明确区分 `.prepare` 和 `.begin`，准备不会录音。
- 按 Apple 的[公开文档](https://developer.apple.com/documentation/uikit/configuring-a-custom-keyboard-interface)，在控制器初始化和重新连接时设置 `hasDictationKey = true`。当前英语／语音完整屏幕截图中系统右下角麦克风已消失，系统地球键保留；没有遮挡层／负 inset 或私有 Hook。iOS 仍可能在 AX 树保留 disabled 的隐藏听写元素，测试检查不可点击并保留截图，而不是把 AX 的元素个数当作视觉隐藏证据。

## 普通模式：本地 ASR，不预设 LLM

```text
用户主动准备 → 麦克风权限与已打包模型就绪（不采集）
用户主动开始 → AVFoundation 采集 → 16 kHz mono PCM → Core 会话
主动停止 → 先关闭采集、排空尾帧 → 本机 SenseVoice 解码
→ Core builtin.raw → 应用内结果／带目标身份的插入请求 → 回执与历史
```

普通模式由 Rust Host 强制使用 `ios-sensevoice`、传统管线和 `builtin.raw`。不是仅在 Swift 界面隐藏云端开关；也不会静默回退 Apple Speech 或某个云端服务。

`NativeSpeechService` 选择 SenseVoice／Apple 原生适配器；本地模型由 `Packages/LocalSpeech` actor 管理，两个 CPU 线程、20 秒解码分块、最多 30 分钟音频，取消标记可在模型加载／分块之间检查。模型不进入键盘。公开音频夹具的非空中文结果证明运行了打包模型，不是准确率、实时性、真机资源或人声采集验收。

Core 保留空文本检查、已配置纠正规则和简繁偏好等本地步骤。iOS 尚无完整的用户纠正规则／简繁设置界面；识别器产生的标点、分段累计和裁剪空白不冒充 LLM 润色。`polishedText`／`finalText` 字段名本身也不证明调用过模型后处理。

## 开发者模式：显式配置，共用桌面 Core

设置中在 1.5 秒连续间隔内点击版本三次，确认后取消并关闭本机任务、关闭旧 Core，切换普通／开发者存储，重建 Core、宿主 epoch 与界面。**这是应用业务状态重载，不是 iOS 进程退出／重启。**首次开发者存储仍是传统／原文模式，没有预配 LLM；随后显式保存的配置可以保留。

```text
传统：主 App PCM → Native ASR 或共享 Core ASR provider
     → ASR 文本 → 可选、主动启用的共享 Core LLM provider → 最终文本
全模态：主 App PCM → 共享 Core Omni provider → 识别／处理结果
选区：冻结选区与原文 → 显式指令 → 共享 Core LLM → 可编辑预览 → 显式替换／复制
```

复用 `TranscriptionRouter`、`SharedCloudTranscriptionEngine`、`TextPolisherRouter`、`SharedCloudTextPolisher`、`DictationEngineRouter` 和 `SharedOmniDictationEngine`，没有另写一套 Swift HTTP 语音管线。Core 的 ASR／LLM 预览阶段分别标记 `asr`／`llm`，后处理预览不会反馈进识别累计器。

主 App 提供频道增删、激活、地址／模型／认证编辑、模型列表和显式验证。普通频道用 UUID；Omni 保持 Core 自己的 providerType 标识与命名空间，不伪造 LLM 频道映射。需要桌面 OAuth 会话的 `codex_oauth` 未开放。

密钥进入主 App 的 generic-password Keychain：服务 `app.openless.ios.corehost.credentials.v1`，稳定账号 `standard`／`developer`，不依赖每次安装会变化的沙盒路径。快照、App Group、普通日志不返回密钥；提供商错误体不会直通公开失败消息。空密钥编辑表示保留，不会在打开配置页时自动验证／请求服务。真实云端和真机 Keychain 仍需对应环境验证。

选区改写目前只接入应用内试用编辑器。预览后仅当全文仍等于冻结原文、UTF-16 范围有效时才能替换。没有声称已接入任意第三方 App 的选区，也没有失败后静默写剪贴板。

## 上屏与资源边界

停止先释放音频硬件，再做解码／后处理。采集有无帧超时、30 分钟上限、有界 PCM 队列和尾帧排空；音量来自真实 PCM。空闲无键盘心跳，活跃键盘录音每秒续租，4 秒失联停止。

上屏绑定 session、epoch、document、revision、deadline 和持久尝试／回执。目标变化或写入不确定时保留待确认文字，不写进新输入框、不重放、不静默复制。`submitted` 只代表发送，不能宣称宿主已验证接受。当前不支持流式上屏。

## 与参考输入法相近和不同的部分

资料显示豆包／Typeless 都分离主 App、键盘、原生音频和进程通信；Typeless 有 RN／Hermes 前端和识别／refine 协议，豆包含录音保持与 PiP／hide／hook 相关符号。符号不能证明其真实停麦、隐藏方法、能耗或服务端提示词，也不授权复制代码、词库、商业资产或凭据。

**历史 PiP 路线状态（当前输出会话方案见 [Build 4](voice-handoff-fix.md)）**：`ExperimentalPiPKeepAlive.isImplemented == false`、`isReady == false`，白名单为空。不会用长期静默占麦、可见 PiP／横幅／边缘把手或反复切 App 代替。App 退后台在没有有效保持能力时停止会话；跨 App 后台开始请求拒绝，而不是假装成功。

原始证据归档为 `openless/7-research/ios-input-methods/analysis/`；本机只读副本在忽略的 `Artifacts/references/`。蓝图明确是参考规范，不是已实现组件。当前的本机模型、开发者 provider 接线与后台缺口应分别验收。
