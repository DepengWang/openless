# OpenLess iOS Build 11 交付

日期：2026-10-21。版本 `0.1.0 (11)`，arm64，未签名，由用户自行签名安装。实现范围见 [目标与实现](ios-build11-goals.md)。本轮沿用 Build 10 的音频/PiP 逻辑。

## 安装包

[下载 IPA](../Artifacts/release-11/OpenLess-0.1.0-11-unsigned.ipa)

- 大小：198,107,772 字节，约 188.9 MiB。
- SHA-256：`7cfe27ef52cd19c6f32b1af6da2f844d313408a9e3fbf702231a95a538ccf790`。
- 主 App、键盘扩展及录音控件均为 Build 11。
- ZIP CRC、解压产品结构及 532 个文件与原始构建产物的 SHA-256 比对通过。

## 已实现

默认键盘内容区缩为 208pt；设置增加 90%–120% 高度与恢复推荐大小。润色选择独立持久保存，键盘重新出现会读取最近选择。候选栏恢复仅由 Rime 提供候选，第一次确认原始字母、第二次执行宿主发送/换行。@ 不再被撤销按钮替换，撤销改为长按 @ 的菜单项，删除移至右上、翻译移至左上，底部主按钮居中。

录音圆扩大至 96pt，波形改用真实 RMS 的语音范围映射。翻译白条改为完成提示下方的灰色文字；语音 processing 状态统一为 `thinking`。顶部模式控制和空闲麦克风外观参考用户截图整理。

输入法后端：Rime 负责中文组合与候选，KeyboardKit 9.9.0 负责键位布局和手势；英文候选结合本地预测词库与系统 UITextChecker；语音默认是 SenseVoice Small 本地识别。本次没有替换这些引擎。

## 验证记录

- 设备 Release 构建、项目静态校验、IPA 解压产品校验通过。
- `KeyboardPresentationTests` 4 项通过：旧偏好兼容、尺寸范围/异常值、风格持久保存/原文清除、真实音量显示映射。
- 界面资源检查 5 项通过，并补齐上轮新增 PiP 静态提示的翻译资源。
- `SpeakButtonTests` 两项通过：未就绪时不偷偷录音，以及录音/准备状态的取消与停止。
- 录音视觉测试通过：模拟 RMS 驱动录音组件并转到 thinking，截图及组件录像保留。没有使用麦克风，不能把它当作实际采集测试。
- 高度偏好在主 App 调整、重启、恢复推荐的 UI 用例已通过。
- 拼音两步确认、中文候选和 @ 输入 UI 用例通过；最终主按钮居中、语音/拼音布局截图检查通过。中文主操作键在库内缺少文案时也有图标兜底。

证据目录：`Artifacts/release-11/`。设备日志 `build-device-release.log`；偏好测试 `preferences-tests.log`；资源检查 `interface-resources-tests.log`；最终模拟器测试 `final-focused-tests.log/.xcresult`；视觉组件录像 `recording-component.mov`；包校验 `package-verification.json`。首轮并行编译受同机资源压力影响，主动中断后改为串行完成，未将中断记录标为测试通过。

未运行完整测试套件。真机输入手感、微信等宿主的实际发送动作、低音量实际录音视觉以及自定义高度的舒适范围，仍需自签安装后验收。没有把模拟器测试扩大为新包的 iPhone 真机结论。

最终 UI 证据：`keyboard-confirmed-tests.log/.xcresult`（输入行为）、`layout-tests.log/.xcresult`（最终布局）。冷启动设置导航及跨进程文字等待的中间失败记录保留，重跑后上述用例通过；没有重跑完整测试套件。

- [最终语音键盘](../Artifacts/release-11/final-layout-evidence/59A456C6-30E2-4E66-8438-A73452CE43DD.png)
- [最终拼音键盘](../Artifacts/release-11/final-layout-evidence/01E96C2E-35D4-4E47-85F6-A1B415925750.png)
- [录音组件动态演示（模拟音量）](../Artifacts/release-11/recording-component.mov)
