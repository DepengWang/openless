# OpenLess iOS · 原生工程


> 历史 Build 11：默认尺寸按新参考收小，可自定义高度；保存润色选择，恢复中文候选与两步英文确认，@ 常驻，改善录音波形、翻译提示和 thinking 文案。[目标与实现](docs/ios-build11-goals.md) · [交付与验证](docs/ios-build11-release.md) · [未签名 IPA](Artifacts/release-11/OpenLess-0.1.0-11-unsigned.ipa)。

> 历史 Build 10：隐藏 VideoCall PiP 与共存适配、锁屏/前后台恢复、空闲音频释放、拼音原文提交和按键尺寸已落盘。[目标与实现](docs/ios-build10-goals.md) · [交付与验证](docs/ios-build10-release.md) · [未签名 IPA](Artifacts/release-10/OpenLess-0.1.0-10-unsigned.ipa)。真机隐藏、共存和扬声器效果仍待本包验收。

> 历史 Build 7：修复后台音频准入过期误判、中英文切换后未重新准备、旧输入目标交付阻塞新录音；加入离线英文下一词和中文续词，扩大发送按钮、更新按键音与原生触感，消除风格／词典切换的整页淡入闪烁。[下载未签名 IPA](Artifacts/interaction-build7/OpenLess-0.1.0-7-unsigned.ipa)；[交付与验证记录](Artifacts/interaction-build7/README.md)。

> 最新产品范围以 [完整需求与 To-do](docs/requirements-and-todo.md) 为准；2026-10-17 的实现、安装产物及验证范围见 [验收记录](Artifacts/overnight-usability/acceptance-summary.json)。风格库／市场、翻译／帮写、词典桥接及账户页已接入；远程加密同步和完整真机运行验收仍有缺口；键盘跨应用录音的最新状态见 Build 10 目标与交付记录。

原生 iPhone App + Keyboard Extension，最低部署目标 iOS 16；目前主要运行证据来自 iOS 27，旧系统还需实际设备验收。主 App 用 SwiftUI；UIKit 键盘扩展嵌入 KeyboardKit 9.9.0 的原生 SwiftUI 键盘，中文组合使用 Rime。没有 WebView、Hermes、React Native 或 Flutter；Rust Core 和模型只在主 App 中运行。

> 当前录音实现包括系统控制中心、键盘首次自动唤醒／返回，以及有效会话内的后台重新开麦。具体测试范围见上方 Build 7 记录；旧系统和用户真机仍需验收。
> Build 10 已移除空闲输出引擎，待机只保留 PiP；系统内部接口不兼容时降级为前台激活。设备验证名单仍为空，详见 [后台会话与 PiP 研究](docs/keepalive.md)。

## 已写入的功能

- 官方 OpenLess 图标与品牌；统一“语音／EN／拼”模式栏，语音页和错位排布的文字键盘共用输入和会话。
- 26 键英文、简体全拼、真实 Rime 候选、数字符号、大小写、连续删除、光标操作、Return 类型与系统输入法切换。
- 中文采用雾凇常用字与 20 万高频基础词，英文采用离线词频／上下文词对 + Apple 拼写纠错；空格或中文上屏后仍可选续词，候选左侧返回工具栏。见[离线候选与续词](docs/input-predictions.md)。
- 大、小麦克风按钮共用点按／按住手势；空格长按可选移动光标或录音，进入光标模式时显示明确提示。
- 模式滑块与页面过渡可打断；语音空白区可横滑切页，不抢按住麦克风、空格或候选的手势。Shift／Caps Lock 有可见状态，轻触反馈开关接入实际键位。
- 普通模式固定 SenseVoice Small int8 本地 ASR，模型随 App 打包，不预设 LLM、不经过 LLM 后处理。Apple Speech 是开发者模式下可选的系统识别器，不作为静默回退。
- 设置中连点版本号三次，经确认安全关闭任务并重载界面与 Core，进入开发者模式；不是终止／重启 iOS 进程。可配置自己的 ASR／LLM／全模态 API，传统管线和全模态管线复用桌面 Core。
- 密钥使用主 App 的 Keychain；提供商／模型列表、验证复用 Core，只在用户主动请求时执行。iOS 选区改写 UI 已移除，共享 Core 接口仍保留。
- 真实 PCM、音量与计时，停止先释放采集再处理结果；30 分钟上限、键盘断联自动停止。
- Core 的会话、取消、输入结果和历史规则，通过独立 iOS Host 接线。
- App Group SQLite + Darwin 通知。首次唤醒录音的结果自动写入返回后的当前输入框；重复通知、已尝试的写入与旧消息不会重放。
- 独立原生试用编辑页，键盘出现时不再与 Tab 栏重叠；浅／深色界面、大字号纵向设置布局；授权被拒绝时提供实际的系统设置入口。
- 历史详情与原文／结果复制、管理菜单和确认删除；重新润色为 Core 生成预览，不覆盖原记录。失败录音可在真实资产存在时播放／导出，完成转写无录音导出。
- 真实概览和 7／30 天活动趋势、本地 Core 词典管理；应用主题、帮助与版本说明。已确认并启用的词条可通过 App Group 共享到 Rime；远程加密同步仍未开放。
- 待确认文字、安装引导、隐私和许可；实验诊断保持二级入口。
- 修复后台授权回调的 Swift actor 断言、键盘断连时空文档 UUID 强制桥接；新增真实系统回调及反复重开键盘回归。

首次授权由主 App 显示系统权限请求。键盘点击语音后先确认后台服务；仅在无法服务时前台唤醒，自动开始并返回原宿主。结果就绪后自动写入当前可用输入框；没有输入框时等待键盘重新连接。控制中心录音入口仍可使用。

## 构建

环境：Xcode 27、Swift 6、CMake、Rustup、Git。构建辅助工具和开源源码保存在忽略的 `.build-tools/`、`.vendor/`；不修改系统签名配置，不安装竞品。

```sh
cd openless-all/ios
./scripts/bootstrap.sh
./scripts/bootstrap-local-speech.sh
./scripts/build-rime.sh
./scripts/build-core.sh
./scripts/build.sh simulator Debug
./scripts/build.sh device Personal
```

- Xcode 工程：`OpenLess.xcodeproj`，由 `project.yml` 生成，两个均保留。
- 普通 Scheme：`OpenLess`；个人诊断 Scheme：`OpenLessPersonal`。
- 默认设备构建不签名，不能直接安装到 iPhone；默认模拟器构建使用 ad-hoc 签名并检查有效的模拟 App Group 声明。
- XcodeGen 按 `OPENLESS_XCODEGEN_BIN`、仓库缓存、PATH 中的已安装版本顺序解析。可用 `OPENLESS_DERIVED_DATA` 指定构建缓存目录；不会因缓存被清理而重复下载工具。
- 无签名设备包使用显式 `validate-product.py --unsigned-device` 检查结构和资源，输出明确不包含签名／安装就绪证明。签名包与模拟器的正常校验仍严格核对实际产品 entitlement。
- 二进制依赖和编译后拼音资源不入库；新检出必须先运行构建脚本。
- `swift scripts/sync-brand-assets.swift` 同步仓库 `app/src-tauri/icons/` 的官方素材；只为 iOS App Icon 去除 alpha，不重画 Logo。`Resources/Brand.xcassets` 同时供 App／键盘使用。

### 真机签名与安装

1. 复制 `Config/Local.xcconfig.example` 为 `Config/Local.xcconfig`。
2. 填写自己的 Team ID、唯一 Bundle ID 和 App Group；主 App、键盘和录音控制扩展必须使用同一团队和 App Group。
3. 在 Xcode 账户／开发者后台配置可用的 App Group 签名。免费个人团队若不能配置该能力，不能据此验证跨进程语音。
4. 解锁并连接 iPhone，启用开发者模式，在 Xcode 选择真机和 `OpenLessPersonal` 运行。也可显式执行 `./scripts/build.sh device Personal signed`，该命令会请求 Xcode 的自动配置服务。
5. 系统设置 → 通用 → 键盘 → 键盘 → 添加新键盘 → OpenLess；语音控制需要开启“允许完全访问”。基本打字不依赖完整访问。
6. 在主 App 点击“启用语音”授权并准备本地模型；准备不会录音。普通模式不请求 Apple Speech 权限／下载。再从首页进入“试用键盘”独立编辑页。键盘中的打开 App 请求可能被系统拒绝，会提示手动打开，不调用未验证的打开应用技巧。
7. 关闭完全访问时，中英文仍可在键盘的模式栏中切换；主 App 的默认面板、空格长按等偏好需要完全访问才能同步。

尚未配置新的签名身份或安装到手机。已有模拟器录音与服务验证的准确范围见验收记录；这不是对所有系统、服务或真机状态的保证。

## 启动本地预览

```sh
IOS_SIMULATOR_ID=<设备标识> ./scripts/preview.sh
```

这条命令只构建并安装产品，不运行 XCTest；检查的是构建包与实际安装包中的可执行文件、编译图标、Assets.car 和键盘资源。桌面占位图、旧安装记录不算成功。界面可通过 Orca 的 iOS 预览打开；没有独立 Simulator.app 时也不需要伪造预览页。

UI 测试会临时安装 `OpenLessUITests-Runner`。`test.sh` 在正常结束或失败退出时移除它；`preview.sh` 也会清理残留 Runner，只保留一个 OpenLess 入口，不删除产品数据。

## 测试

```sh
./scripts/test.sh unit
# 只跑 App 原生 + UI，或加上上述共享／Rust／Rime／音频检查：
IOS_SIMULATOR_ID=<设备标识> ./scripts/test.sh ui
IOS_SIMULATOR_ID=<设备标识> ./scripts/test.sh all
# 使用专门的 QA 模拟器；测试会切换页面、安装键盘和关闭完全访问。
# 导出有来源索引的截图：
python3 scripts/export-ui-evidence.py Artifacts/<测试结果>.xcresult --tag light
```

无需麦克风的测试包括：共享协议／SQLite／手势／活跃键盘租约；真实 Core 管线配合明确标识的原生测试适配器；真实 Rime 拼音与候选；AVFoundation 重采样和尾帧排空。Core 测试只使用独立临时目录。

UI 测试会在指定模拟器的系统设置里添加 OpenLess 键盘并关闭完全访问，验证“nihao → 你好”、English、符号／换行／删除、横屏、模式切换、编辑页避让、授权拒绝、只读诊断以及文字裁切。空历史测试要求专用 QA 数据。

当前完整套件 `keyboardkit-full-current.xcresult`：**12 项 UI + 4 项 App 原生通过，1 项授权回调因未授权跳过，0 失败**。包括真实键盘、默认无 LLM 的开发者重载、实际本地模型公共音频 smoke 与不可用按钮安全。另有 27 项共享 Swift、14 项 Rust Host 通过；最新带模型的无签名设备构建通过。真实授权回调只复用既有决定，不偷偷授权。另有 3 项深色检查和 1 项最大辅助字号首页／设置裁切审计通过。旧的 11 UI／3 原生是历史版本，不混算。证据及准确边界见[验证记录](docs/verification.md)。模拟器使用 ad-hoc 签名，不配置真实开发团队。

结果写入忽略的 `Artifacts/`，不会因 Orca 重启丢失；`UI_RESULT_BUNDLE` 可指定结果路径。UI 测试与真机录音、隐藏 PiP、输入目标行为、资源及能耗验收分别记录，不能互相替代。

## 结构与数据边界

| 目录 | 职责 |
| --- | --- |
| `App/` | 主 App、原生 ASR 调度、音频、Core、开发者配置与显式选区预览 |
| `Keyboard/` | 键位、候选、录音控制、输入目标与最终上屏 |
| `PlatformShared/` | 两个目标共用的 UIKit 说话按钮、共享容器和通知 |
| `Packages/OpenLessShared/` | 无 UI 的 v2 协议、持久化、手势策略和测试 |
| `Packages/LocalSpeech/` | 仅主 App 链接的 sherpa-onnx／ONNX Runtime 与 SenseVoice actor |
| `CoreHost/` | 独立 Rust workspace，窄 C ABI，依赖现有 `openless-core` |
| `RimeBridge/` | 基于公开 Rime C API 的 Objective-C++ 桥 |
| `Resources/` | 官方 OpenLess 图标、许可、隐私声明、输入方案与空兼容白名单 |
| `AppTests/`／`UITests/` | 真实授权回调／说话按钮测试；App 与实际扩展的 UI 回归 |

- Core 与 Rime 的静态库只分别链接主 App／键盘，不把 Core 放进扩展。
- 主 App 的历史在自己的 Application Support；拼音学习数据在扩展私有容器。
- App Group 不保存原始音频。提交日志保留至多一天供错误恢复；已提交内容随清空历史清理，待确认项可单独移除。
- 只有用户点“复制”才写剪贴板。无账户、云同步、广告、埋点或竞品 API。
- 识别预览跨进程限长，音频使用有界队列，录音之外不维持音频采集。
- 空闲没有键盘心跳；只有活跃的键盘录音每秒更新租约，超过 4 秒失联则停止。

## 依赖与许可

全部输入引擎／词库来自合法上游，没有取用竞品包中的二进制、字体、Logo 或商业词典。

- KeyboardKit **9.9.0 MIT 源码版**：`a7e33ac2e6f47e16288ce09ec8d765cd329f918c`；不是当前闭源／二进制主版本。
- SenseVoice Small int8、sherpa-onnx 1.13.8 和 ONNX Runtime 1.28.2：固定上游与 SHA-256，见[模型来源](docs/local-speech-provenance.md)。模型约 228 MiB，不进入键盘扩展。
- librime 1.13.1：`1c23358157934bd6e6d6981f0c0164f05393b497`，BSD-3-Clause。
- rime-ice：`da1fbe602e38f26db846fa10120ee64c2b0324c0`，GPL-3.0-only，使用常用字表及 20 万高频基础词。方案 ID 沿用 `pinyin_simp` 以保留用户学习。
- SymSpell 频率数据：`c239062ae02961df18ab7da1671d01b4388204e0`，MIT，英语单词与上下文词对；其上游数据归属见应用内 `prediction-data` 说明。
- Boost 1.85.0：源码压缩包校验值固定在 bootstrap 中，BSL-1.0。
- LevelDB、yaml-cpp、marisa、OpenCC 使用上述 librime 固定的子模块提交；禁用外部插件、glog 和原生依赖测试工具。
- OpenLess/Core 沿用项目 AGPL-3.0-only。许可证位于 `Resources/Licenses/`；Rust 依赖由独立 `CoreHost/Cargo.lock` 固定。

不包含 CarPlay、耳机硬件键、九宫格、其他键盘输入语言或账户／订阅／同步体系。自定义云端服务只在开发者模式下由用户配置；没有预置第三方账号或竞品服务。

普通模式原文输出；开发者模式可主动启用 LLM 或全模态模型。分段合并不是改写，提供商接线／假服务测试不等于真实云端兼容性。与豆包／Typeless 的进程分工、后台缺口和处理链路见[架构与后处理对照](docs/architecture-and-processing.md)。
