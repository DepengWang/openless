# OpenLess iOS Build 10 交付

日期：2026-10-21（America/Los_Angeles）。版本 `0.1.0 (10)`，arm64，未签名，由用户自行签名安装；未 push。Build 9 基础 PiP 免跳转已获用户确认，本轮接续处理其后的隐藏、共存、生命周期、扬声器及键盘反馈。

## 产物与目标

[下载未签名 IPA](../Artifacts/release-10/OpenLess-0.1.0-10-unsigned.ipa)。文件大小 198,093,424 字节（188.9 MiB），SHA-256：

```text
24554fe1f24e50a2112d11b8ff64c478f2cab381e45a26ebb2c14a8f3e42de95
```

目标、每项实现位置和真机验收步骤见 [Build 10 目标](ios-build10-goals.md)。专门设计见 [PiP 隐藏/共存](pip-hidden-coexistence.md)、[拼音原文与按键](keyboard-raw-input.md)。当前 README、keepalive 和待办已同步，历史 Build 7/9 记录保留并标识时点。

本轮移除待机音频引擎与静音循环。录音结束先停止采集，再 deactivate 音频并退出录音类别；释放失败做三次有界重试，旧重试不能关闭新录音。后台准入使用激活代际、实时 PiP 状态和权限/中断状态。锁屏保留意图，只对锁屏打断的 PiP 授予一次解锁恢复，不在后台反复争抢其他视频。

PiP 改为透明 VideoCall 内容，并集成独立、按运行时签名检查的隐藏/共存适配。该部分使用系统内部接口；不兼容时不会假报就绪。不是对豆包或 Typeless 完整原生源码的恢复，也不能用本地 type 6 提交成功证明系统已接受多 PiP。

## 验证

- 设备 Release 构建通过；主 App、键盘和控件版本均为 10。项目静态校验通过。
- IPA ZIP CRC、三个 executable 权限、解压产品校验通过；532 个文件逐一 SHA-256 与原始构建产物一致。打包不包含 AppleDouble/xattr 旁文件。
- 12 项模拟器音频所有权/准入测试通过：闲置不激活音频、PiP 独立准入、录音结束释放、锁屏解锁、前后台、系统中断、音频服务重置、路由变化、历史播放、旧代际、释放失败重试与新录音隔离。
- PiP runtime fixture 六组通过：构造范围、签名拒绝、代理身份、type4→6 延迟一次提交、回调/取消顺序、100 次释放，以及锁屏一次恢复状态机。用生产适配文件编译执行，不是手抄实现测试。
- 真实 AVKit 构造检查在当前 iOS 27 模拟器明确 skipped：该运行时 `isPictureInPictureSupported()` 返回 false。因此没有将 fixture 结果冒充实际 iOS PiP 运行证明。
- 真实 Rime smoke 通过，包括中文候选/空格、四种原文、删除与清空；Swift 语法与差异检查通过。
- 聚焦键盘 UI 用例 `testSymbolsReturnAndCompositionSwitch` 最终通过：原文/中文提交、两次 Return、44pt 目标、符号、大写与语音页换行。初次安装等待、跨进程更新同步及符号切换预期已更正，旧失败日志保留；未宣称整套 UI 或横屏验收通过。最终结果见 `keyboard-confirmed-tests.log/.xcresult`。

日志和 xcresult 位于 `Artifacts/release-10/`，包括 `build-device-release.log`、`validate-project.log`、`pip-runtime-tests.log`、`final-focused-tests.log`、`keyboard-confirmed-tests.log`、`validate-extracted-product.log`、`package-verification.json`。未运行完整测试套件。

设备构建复用原有 `Frameworks/device` 静态库，没有构建桌面端既有未提交修改。签名、安装、目标 iPhone 的实际隐藏、多 PiP、后台重新开麦、B 站扬声器音量和长期驻留尚未在本包实测；`KeepAliveCompatibility.json` 保持空，不伪造通过记录。

## 验收入口

自签安装后按目标文档的六步序列检查。最关键的三项：停止录音后直接播放 B 站无需切输出；B 站开启自己的 PiP 后 OpenLess 仍能录音；锁屏解锁和回主应用再离开不重新激活。失败时保留对应录屏及 OPENLESS_AUDIO/OPENLESS_PIP 日志，区分系统结束 PiP、录音准入失败和音频释放失败。

本轮没有修改原 provider 会话或归档，也未操作用户 Chrome。用户提供的 macOS DMG 路径在本机不存在，未安装或分析该文件；本次工作和交付范围为 iOS。
