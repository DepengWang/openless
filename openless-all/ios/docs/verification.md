# iOS 预览与验证记录

更新：2026-10-16。证据必须对应实现版本；与参考输入法的实现差异见 [architecture-and-processing.md](architecture-and-processing.md)。

## 最新状态：历史、概览、词典与设置基础

本批证据见 [product-library.md](product-library.md)：Rust Host 21 项通过；新增历史模型 4 项通过；另外 SenseVoice／SpeakButton 4 项和不依赖软件键盘的 UI 4 项通过；最大辅助字号首页／设置裁切审计 1 项通过。模拟器与无签名设备构建、App／扩展／debug dylib iOS 16 下限检查通过。没有清空用户历史／词典、调整 Full Access、录制人声或调用真实云服务。

STYLE-06 的 iOS 选区润色 UI 已移除。风格市场、翻译／帮写、Rime 用户词典、账户／加密同步、隐藏式免跳转仍未完成；新播放器／导出及 LLM 预览还需要相应真实操作验收，不能以构建代替。

软件键盘弹出按用户最新指示是模拟器环境问题，不再排查或作为产品阻塞。原 `Artifacts/compatibility/runtime-regression.xcresult`、`keyboard-stable-navigation.xcresult` 仍保留失败事实；没有改断言或伪造绿色。下文完整 KeyboardKit 绿状态只属于兼容改造前基线。

当前完整要求见 [requirements-and-todo.md](requirements-and-todo.md)，继续执行先读 [continuation-checkpoint.md](continuation-checkpoint.md)。

## 兼容改造前基线：KeyboardKit／SenseVoice／开发者模式

当前文字键盘使用 KeyboardKit 9.9.0 MIT 源码和 Rime。**完整浅色回归已通过：12 项 UI、4 项 App 原生通过，1 项授权回调因没有既有授权而跳过，0 失败。**没有用旧自制键盘的次数代替此次验收。

### 已完成的验证

| 层级 | 最新证据 |
| --- | --- |
| 完整 App／键盘回归 | `keyboardkit-full-current.xcresult`／`.log`，脚本 exit 0；xcresult 总计 17，passed 16、skipped 1、failed 0 |
| 键盘交互 | 完全访问关闭下真实“nihao → 你好”和 English；五轮收起／重开；符号／换行／删除／组合切换；Shift／Caps Lock；空格长按实际移动光标且不误插空格；语音空白区横滑；横屏和反复模式切换 |
| 系统底部区域 | 控制器初始化及重新连接时设置公开 `hasDictationKey = true`。实际完整屏幕图中右下角系统听写键消失、地球键保留；没有遮挡层、负 inset 或私有 Hook |
| 开发者真实 UI／Core | 完整套件包含三击版本、确认重载、真实快照读取、默认 LLM 关闭、保存传统管线、退出并重载普通模式；不发云端请求 |
| 深色／最大辅助字号 | `keyboardkit-current-dark.xcresult` 3 项通过；`keyboardkit-current-large-type.xcresult` 1 项首页／设置裁切审计通过。独立结果，不计成另一轮完整 UI |
| 原生 ASR／按钮 | 两项 SenseVoice 通过：打包模型解码公共 PCM、预先取消。两项 SpeakButton 通过：不可用只解释、不意外开始，准备／录音阶段仍可取消或停止 |
| 授权回调 | `NativeAuthorizationTests` 显式跳过未决定的系统授权，不自动授予权限。完整 UI 的拒绝权限分支通过；旧版本已有系统回调证据不能当作本次实际执行 |
| 共享 v2 协议 | `shared-v2-tests.log`：27 项／6 套通过；prepare 与 begin 分离，prepare 专用行为仍可补充分支覆盖 |
| Rust Host | `provider-host-verified.log`：14 项通过；首次开发者传统／原文且无 LLM、频道／凭据／激活持久化、选择改写和提供商错误脱敏；测试显式使用 memory vault，不读取用户 Mac Keychain |
| 无签名设备构建 | `keyboardkit-device-final.log`／`.exit`：build 和实际模型哈希验证通过，exit 0；不能直接安装到 iPhone，不代表真机性能 |
| 产品完整性 | `validate-product.py` 检查构建／安装包内可执行文件、编译图标、Assets.car、键盘、Rime、实际 SenseVoice 模型与 tokens 的固定 SHA-256；模型不进入键盘 |

本轮没有授予麦克风权限或开始人声录音。模型下载是之前明确授权的资源准备，不是 UI 测试暗中触发。公开音频是集成 smoke，没有人工 gold transcript，不能宣称准确率。

浅色完整截图：`Artifacts/Review/*-keyboardkit-current-light.png`；深色和最大字号分别为 `*-keyboardkit-current-dark.png`、`*-keyboardkit-current-large-type.png`。来源索引在 `Review/index.json`；原始包对应上述三份当前结果，不能把旧图当成新键盘。

预览已恢复到 `OpenLess iOS 27 QA`（A4B5F07C-C6C9-498F-BEEC-AC7AC98B8A6A），实际安装的主 App／键盘二进制与通过完整回归的构建产物哈希一致；安装包内模型与 tokens 也通过固定哈希验证。系统注册只保留 `app.openless.ios` 一个 OpenLess 启动入口，Runner 已移除。Orca 已附加实际设备画面，没有启动麦克风。当前 CLI tap 虽返回 ok，但复核画面未观察到导航，不将它算作交互成功；上述交互验收来自真实 XCTest，而非这个返回值。

### 定位与兼容处理

- 收起键盘原有的跨进程“清焦点”通知会干扰再次编辑，现移除该通知，走 UIKit 原生生命周期。额外重开手势、强制反复 resign／become 及笼统的 keyboardDidHide 补丁均已撤掉。旧的硬件键盘日志不能直接当作全部根因。
- 听写键需在控制器初始化、重新连接时声明能力，而非只在 viewDidLoad 设置。Apple [完整文档](https://developer.apple.com/documentation/uikit/configuring-a-custom-keyboard-interface)说明会抑制系统按钮；不能只凭头文件的 disabled 字样推断不支持隐藏。iOS AX 可保留 disabled 的隐藏元素，因此检查不可点击并核对屏幕，不错误要求 AX 个数为零。
- iOS 27 下 9.9 的 Shift 适配收到的双击只有一次 release；仅给 Shift 增加显式双击识别，仍调用 KeyboardKit 自己的 caps-lock action，不重写布局、键位命中或大小写状态机。Caps Lock／解除／实际输入已整例通过。
- 空格长按沿用库的 0.5 秒门槛；测试改为 0.65 秒，不沿用旧自制键盘的 0.35 秒。数字／字母／Return 用稳定标识；等待 SwiftUI 状态渲染完成，而非用旧文案和同步读取断言。

`keyboardkit-typing-*`、`keyboardkit-current-clean`、`keyboardkit-completed-hide`、`keyboardkit-public-capabilities`、Shift 诊断等失败保留，不计入通过。探针代码和临时日志已从产品代码移除。早期 `Review/*-keyboardkit-current-incomplete.png` 不是最终版本。

### 仍没有完成或未验证的部分

- 人声麦克风 → Core → 本地识别 → 结果／上屏，以及真机内存、耗时、热量与能耗。
- 用户真实云端 API、额外认证、模型列表／验证；当前是接线、Core 测试和无网络配置 UI 证据，不是所有实际服务兼容证明。
- 这份旧基线曾接入 Swift 选区改写；最新需求已移除 iOS UI，不再补第三方选区集成。共享 Core／桌面接口保留。
- Team、App Group provisioning 和真机 Keychain。本机检查为 0 个有效 codesigning identity；开发者模式不代替签名。
- 隐藏式后台免跳转没有实现，不是只缺测试。新模型／运行时／夹具的分发 notices 和少数 Rust 许可正文仍须核对。

## 历史版本：实际交付与旧自制键盘

以下为 KeyboardKit 替换前的验收，保留故障来源，不作为当前版本的 pass。

### 当时先修的是实际交付，不是只改源图

用户看到默认占位图、两个入口、点开退出。本机检查发现：

- LaunchServices 中仍有 `app.openless.ios` 安装记录，但记录对应的 `.app` 目录、可执行文件、Info.plist 和 Assets.car 已不存在。启动日志为 `FBSOpenApplicationErrorDomain / NotFound`，没有启动出产品进程。文件被移除的来源没有证据，不能归因给某个清理工具。
- 第二个入口是 `app.openless.ios.uitests.xctrunner`，即 UI 测试宿主，不是键盘扩展或第二个产品。
- 重建原生依赖并重新安装后，SpringBoard 实际显示官方图标；安装列表只剩一个 OpenLess。完成三次独立冷启动和从桌面点图标启动，进程持续运行、首页控件可用。截图：`Artifacts/restored-springboard.png`、`restored-open-from-icon.png`。

新增 `scripts/validate-product.py` 检查**构建后的产品和实际安装后的产品**：主可执行文件、编译图标声明及小尺寸图标、Assets.car、嵌入键盘与 Rime 数据。源图存在或残留桌面图标不再算交付成功。

`scripts/preview.sh` 只构建／安装产品，不运行 XCTest。`scripts/test.sh` 在 UI 测试结束或失败退出时移除 Runner；再次打开预览也会清理残留 Runner，不删除产品数据。

### 旧版本测试

环境：Xcode 27.0（27A266a）、Swift 6、Rust 1.98.0。主 QA 设备为 **OpenLess iOS 27 QA**，iPhone 16 Pro，iOS 27.0（24A434），UUID `A4B5F07C-C6C9-498F-BEEC-AC7AC98B8A6A`。

| 层级 | 本轮结果／来源 |
| --- | --- |
| 共享 Swift | 27 项／6 套通过。`Artifacts/current-polish-light.log` |
| iOS Rust Host | 3 项通过。真实 Core 管线配明确标识的原生测试适配器；同一日志 |
| Rime | 桥接引擎的拼音、选词、提交、删除／取消检查通过；实际扩展中文／英文输入也通过 |
| 合成音频 | 8k／16k／44.1k／48kHz × 单／双声道共 8 组；一秒各输出 16,000 样本，幅度及尾帧通过；不打开麦克风 |
| App 原生 | 3 项／2 套通过。真实 Apple 授权回调返回 MainActor；不可用说话入口只解释；准备与录音阶段仍可取消／停止 |
| 最终完整 UI | **11 项 UI + 3 项 App 原生测试通过，0 失败**；安装产品检查通过。`Artifacts/current-polish-final.xcresult`／`.log`，`current-polish-final.exit` 为 0 |
| 定点复查 | 首页／历史／滚动不启动准备／权限或资源分支，4 项通过；`current-polish-targeted.xcresult` |
| 最大辅助字号 | 首页、设置 `.textClipped` 审计通过；`current-polish-large-type.xcresult`。保留系统字号和滚动，不强行缩字 |
| 新装权限拒绝 | 在临时全新模拟器实际点击“不允许”，主 App 存活、无录音，出现“前往系统设置”；`current-permission-recovered.xcresult`。没有修改主 QA 原有的授权；临时设备已删除 |
| 静态交付 | App Group／隐私／键盘无采集代码、空免跳转白名单、Rime 数据、官方品牌逐字节一致、1024px 无 alpha 图标检查通过 |

本轮无人值守测试没有授予麦克风、没有启动录音，也没有下载语音模型。主 QA 原有系统决定已为授权，不能把它当“新装拒绝权限”证据，因此后者使用单独的全新实例。真正的 Speech 授权回调原生测试复用已有决定；无决定时会跳过。

### UI 覆盖

- 首页、设置、空历史、原生试用编辑页、键盘避让与无重叠 Tab 栏。
- 完全访问关闭状态下，实际 OpenLess 扩展输入“nihao → 你好”和 English。
- 符号、删除、Return、语言切换提交组合文本、横屏和连续切换模式。
- Shift 单次大写、Caps Lock 锁定／解除、空格长按实际移动插入点且不误插空格。
- 在语音页空白区横滑进入文字页；不以点击麦克风模拟横滑。
- 滚动首页不触发准备／录音；授权回调、资源提示和只读实验诊断。

### 保留的失败，不算通过

`current-polish-light.xcresult` 初次运行中 7 项 UI 通过、3 项失败。首页／历史受准备资源提示干扰；原测试还用固定的 115pt 底部禁区反复滚动已经可见的内容，并错误假设已有授权的 QA 一定走拒绝分支。修正为实际 Tab 栏可见边界、区分既有授权与新弹窗，并增加滚动回归；4 项定点复查通过。

`current-polish-dark.xcresult` 与 `current-permission-fresh.xcresult` 的第一次并行执行因 120 秒命令上限中断，不算整轮通过：前者日志只确认深色裁切审计通过；后者新设备首次迁移及编译耗时，不能当 App 崩溃。随后复用已编译产品执行新装权限测试成功，临时设备／重复编译目录已清理。

## UI 与交互改动

| Before | After | Why |
| --- | --- | --- |
| 只核对源图，实际安装包缺失 | 构建包和安装包都检查图标、可执行文件与键盘数据 | 默认占位图和安装残影不能算成功 |
| UI 测试宿主留在桌面 | 测试结束清理 Runner，产品预览不运行测试 | 产品只保留一个入口 |
| 首页多个大盒子，视觉重点分散 | 独立 HomeView、统一冷浅色／深色底、官方品牌、集中语音入口与试用路径 | 减少诊断面板感，让主要操作更明确 |
| 模式选中背景直接跳变 | 一个滑块在“语音／EN／拼”间移动 | 让位置和状态连续，可在过渡中改选 |
| 只能从窄顶栏横滑 | 顶栏和语音空白区都可滑；排除 UIControl、候选和键位 | 手势可发现，不抢按住说话／移出取消／空格光标 |
| Shift／Caps Lock 无视觉区别 | 空心／实心 Shift 与 Caps Lock 图标、对应辅助功能值 | 明确下一字和持续大写状态 |
| 长按空格进入光标模式但没有反馈 | 提示“移动光标”，弱化字母，松手恢复 | 解释当前手势，不误插空格 |
| 轻触反馈偏好没覆盖实际普通键位 | 字母、Shift、删除、空格、Return 接入轻触反馈 | 偏好与实际行为一致，不加逐键弹跳延迟 |
| 拒绝授权只显示错误 | 提供可执行的“前往系统设置”入口 | 不让用户反复点一个无法重新弹权限的按钮 |

### 动效参数与边界

- 主 App 的偶发按钮：按下缩放 0.98／透明度 0.8，140ms ease-out；减少动态效果时只变透明度。
- 模式滑块：200ms、dampingRatio 1，可打断；切页：250ms、dampingRatio 1，拖动按真实触点，释放使用速度投影。
- 说话控件沿用参考的 147×60／109×109／129×60 状态几何、200ms 形态变化；七根真实音量条 80ms 跟随，不播放伪波形。
- 字母输入立即响应，不逐键等待弹簧或页面动画；减少动态效果不会关闭必要的状态反馈。
- 本轮未主动录音，因此录音中形态和音量驱动有代码／原生单元及合成音频证据，**没有新的人声录音观感验收**。
- 浅／深色的实际模式按钮、拼音候选及官方品牌在当前安装包中再次目视核对，Orca 预览保持开启。

## 复跑与截图

```sh
cd openless-all/ios
./scripts/test.sh unit
IOS_SIMULATOR_ID=<专用设备标识> ./scripts/test.sh ui
IOS_SIMULATOR_ID=<专用设备标识> ./scripts/test.sh all
IOS_SIMULATOR_ID=<设备标识> ./scripts/preview.sh
python3 scripts/export-ui-evidence.py Artifacts/current-polish-final.xcresult --tag current
```

`unit` 跑共享／Rust／Rime／合成音频；`ui` 跑 App 原生与实际 UI；`all` 跑两者。`UI_RESULT_BUNDLE` 可指定新结果路径。UI 测试会操作指定模拟器的设置、键盘和页面，应使用无个人历史的 QA 实例。

`Artifacts/Review/index.json` 关联截图、结果包、测试与原附件。完整 11 项 UI 回归后，视觉复核又发现系统 UIButton 选中样式叠出黑块、键盘品牌被缩成 24 个物理像素；现改用自定义文字按钮和显示倍率渲染，并补足辅助键按下色。它们经过增量构建及实际点击核对，未因此重复整套逻辑测试。

**当前安装外观**看 `current-pinyin-candidates-installed.png`、`current-english-dark-installed.png`、`current-voice-dark-installed.png` 和 `keyboard-interactions.mov`。录像是实际模式过渡与 Rime 按键，不是录音演示；没有点击麦克风。`*-current.png` 的自动附件保留为测试时的版本，不覆盖成改样式后的图。

横屏完整屏幕图可能保留设备原生方向，旋转查看副本必须标明，不能裁图掩盖布局问题。未封口的中断结果包不支持导出。

`Artifacts/` 是本机忽略目录，不是永久发布材料。之前的 9 UI／3 原生和桌面 1,163 Core 等是历史结果；旧结果包当前已不在磁盘，不冒充本次复跑证据。

## 已定位的原生崩溃

- 主 App 旧报告 `OpenLess-2026-10-14-191901.ips`：系统 Speech 授权回调不在 MainActor，隐式隔离闭包触发 `_dispatch_assert_queue_fail`。显式 `@Sendable` continuation 桥接和真实系统回调回归通过。
- 键盘旧报告 `OpenLessKeyboard-2026-10-14-191900.ips`：远程代理断连时非空声明的 `documentIdentifier` 实际可能为 nil，UUID 强桥接崩溃。现经 nullable Objective-C 边界读取；无目标就停止，不生成假 UUID。
- 重排键位时的 `OpenLessKeyboard-2026-10-14-211048.ips`：跨行等宽约束在没有共同父视图时激活。现先建层级再激活，实际扩展重建／横屏回归通过。

这些历史代码崩溃与本轮“安装记录在、包目录不在”的启动失败不是同一原因。

## 当前能力边界

以本文开头的当前版本表为准。开发者 ASR／LLM／Omni 和主应用选区预览已有接线；真实云端调用尚未验证。隐藏式后台免跳转和完整用户纠正规则／简繁设置仍未实现。

真机签名与 App Group、实际麦克风／识别、不同宿主上屏、系统中断、后台重新开麦、热量与能耗仍没有真机证据。开发者模式并不代替签名。公开分发前仍需补齐缺少的许可正文与新资源 notices。


## 2026-10-18：Build 6 键盘重开

88 项共享协议测试（新增 9 项握手状态测试）、5 项资源检查、工程检查、Release 设备结构与 IPA 校验通过。实际键盘用例 `testKeyboardVoiceSurvivesDismissalAndReopening` 通过：输出会话准备后，收起 5 秒／30 秒重开录音，录音中收起后停止采集并再次重开录音，全程 Safari 前台、主 App 后台。详见 [本轮验收](../Artifacts/keyboard-reopen-fix/acceptance-summary.json)。这不是隐藏 PiP 或用户 iPhone 运行通过证明；本轮独立冷启动返回前置未取得来源 bundle，没有计作通过。
