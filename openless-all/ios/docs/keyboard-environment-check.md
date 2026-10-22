# 软件键盘退化：当前环境对照

2026-10-16。ENV-02 尚未关闭，本记录只说明本次观察，不宣称已经确定根因或修复。

## 操作与观察

主 Agent 通过 Orca 自带模拟器连接唯一设备 `A4B5F07C-C6C9-498F-BEEC-AC7AC98B8A6A`，先读取共享 voice snapshot：inactive、level 0；没有调用准备语音／开始录音。

1. 打开当前已安装 OpenLess，Orca AX 返回真实页面；通过 normalized tap 点击词典 Tab 后复查 AX，词典 radio value 从 0 变为 1。
2. 点击旧版词典添加，再点词条输入框。截图中没有软件键盘。未保存词条，点击取消离开。
3. 打开系统 Settings，通过 Orca tap 从键盘设置退回根页，再点击系统搜索输入框。
4. 系统搜索框出现光标，但截图没有软件键盘；Orca type `openless` 后，AX TextField 的 value 真实变为 `openless`，屏幕键盘仍无。没有修改任何系统设置。

证据目录 `Artifacts/execution-wave1/`：

- `orca-after-tab-ax.json`：Tab 实际切换，不以 tap 返回 ok 当成功。
- `orca-focused-word-input.png`：当前旧词典 Form，无软件键盘；不是新词典设计验收。
- `orca-system-search-focused.png`／`orca-system-search-focused-ax.json`：系统输入框获得光标也没有屏幕键盘。
- `orca-system-search-typed.png`／`orca-system-search-typed-ax.json`：系统输入框接收 ASCII，仍无软件键盘。

## 可下的结论

- 当前问题**不只发生在 OpenLess 编辑器**；系统输入框同样可复现。
- `ConnectHardwareKeyboard=false` 的全局／设备偏好不是屏幕键盘真实显示的充分证据。
- 输入服务／扩展进程存在不证明 UI 可见；AX 有焦点不证明屏幕键盘可见。
- 模拟器 keyboard preferences 当时存在 `HardwareKeyboardLastSeen=1`，只是线索，不能单凭这个历史字段定根因，更不直接改用户偏好清库。
- Orca 当前 serve-sim runtime 是 `1.4.227`，旧检查点的 `1.4.224` 已不存在。当前 normalized tap／type 有 AX 变化证据；不能继续引用旧版本 tap 无变化的结论。

## 后续恢复证据（同日）

在当前 Orca serve-sim 1.4.227 中，通过其已实现的 `softwareKeyboard` HID toggle（helper WebSocket type 12，对应工具条软件键盘开关）切换一次后，出现底部系统地球键／听写栏，但键盘内容暂时仍未出现。随后实际点击地球键切到系统简体拼音，**系统26键显示**；再切回 OpenLess，**OpenLess 语音键盘也完整显示**。

- `Artifacts/execution-wave3/keyboard-toggle.png`：先出现底部系统栏，无键盘内容。
- `system-keyboard-switched.png`：系统拼音键盘真正显示，输入框／弹窗随键盘避让。
- `openless-keyboard-after-switch.png`：切回 OpenLess 后键盘真正显示（还是视频改版前版本）。
- `keyboard-popup-restored.xcresult/.log`：原 `testTrialEditorShowsSoftwareKeyboardAfterRefocus` **通过，exit 0**；实际键盘存在、编辑器缩小、收起／再次点击三轮原断言未改。

没有重置数据、改 Full Access、重启系统服务或加产品强制聚焦补丁。恢复步骤是软件键盘显示切换＋输入法切换；不能单凭此确定最初由哪个版本／服务导致隐藏状态，也不能保证所有重启后永不复发。

重要修正：系统应用输入框也可能选中 OpenLess 扩展；所以“系统输入框也不弹”单独并不能排除第三方扩展问题。上述地球键切到系统拼音再切回才是实际输入法对照。

当前恢复可继续验收新键盘。最终视频改版后仍需重跑相关输入回归；旧失败日志保留，不改写为历史通过。
