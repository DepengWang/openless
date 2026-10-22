# 键盘语音首次唤醒与返回

## 当前接线

键盘点击录音先通过 App Group 和 Darwin 通知确认主进程可服务。共享快照过期不会直接触发跳转；后台会话可用时沿用原进程启动采集。无法连接或后台会话失效时，保存包含输入目标与语音意图的请求，再打开主应用。

主应用只自动消费与本次 URL 精确匹配、仍有效且由新版键盘明确标记的请求。冷启动等待 Core 和前台状态就绪；系统麦克风授权照常显示。无需再次点击开始。恢复旧请求、重复打开 URL、取消及过期请求不会自动重开录音。主进程重建时将遗留录音请求标为失败；键盘在 IPC 无响应且存活快照过期后可以替换旧请求。替换与快照检查在同一 SQLite 事务内，新的有效心跳会阻止替换。

返回发生在收到实际音频帧之后。SwiftUI 通过 UIApplicationDelegateAdaptor／UISceneDelegate 接收 URL context；公开 sourceApplication 只对同一开发团队提供来源，跨团队为空。键盘因此在本次语音点击后读取当前宿主通知，不以旧 bundle 缓存推断去向。iOS 27 的键盘通知实际走 _didHandleKeyboardClientChange:shouldConsiderSnapshottingKeyboard:isLocalEvent:，返回 BOOL，代码先校验 ABI 再接收。重新建立 arbiter 连接、要求两个新 generation 的同一宿主结果，且 window 和 documentIdentifier 必须仍匹配。返回桥优先使用匹配来源的系统导航 action，必要时使用本次查得的精确来源 bundle 打开应用。返回 token 每次只消费一次；切换期间允许有界重试，只有离开前台才停止返回流程。每个 token 只消费一次，不枚举已安装应用，不猜 URL scheme。iOS 27 模拟器的独立双应用验证表明，旧导航 action 可为空，系统 URL context 的来源与精确 bundle 返回仍可工作。

这里使用了非公开的应用返回接口；桥在每次调用前检查实际方法签名。接口或来源不可用时保留录音界面并提示返回，不把调用失败伪装为成功。模拟器兼容性不代表全部真机系统版本已经验证。

## 录音与待机分开

NoJumpAudioSession 使用独立的输出音频引擎维持已经前台激活的音频会话。AudioCapture 只在录音期间创建输入引擎，停止时移除输入 tap、停止引擎并结束 PCM 流。待机路径没有输入 node、识别请求或音频上传。原有空闲十分钟主动销毁会话已删除。

播放历史录音可共用输出会话，不再销毁键盘语音的 generation。播放期间拒绝同时开始录音，播放结束后恢复原 generation。真实音频中断、失去渲染、权限变化或进程被回收仍会使会话失效；下一次显式录音动作才恢复，不自行重新采集。

本地 SenseVoice 的 PCM 在独立、加锁且有容量上限的缓冲区中收集，不再逐帧经过界面线程。切换应用时的界面停顿不会填满原来的 16 帧主线程队列。finish 在采集 drain 之后原子取出完整数据；cancel 和迟到数据按 sessionID 隔离，旧会话的错误也不能取消新会话。开发者选择 Apple Speech 时保留其现有主线程接线。

这个实现是输出音频会话方案。之前的 Typeless 报告证明了“PiP 保活与停止采集分离”的分支，并未恢复隐藏 PiP 的原生实现；不能把那份静态证据当成本项目实现同一套 PiP 的证明。

## 输入位置与自动写入（Build 5）

返回结果就绪后，键盘等待可见且已连接的 textDocumentProxy，直接将结果绑定当前输入框。跳转前后的 keyboardID、documentID 或 revision 不同，不再触发预览和二次确认。若用户在返回后选择了新的可用输入框，则按当前输入框写入。

领取结果和写入仍串行执行：先提交当前拼音组合，再读取当前目标；在 SQLite 事务中创建唯一 insertionID，在 UIKit 写入前标记 attempting，完成后标记 submitted。重复通知、重新挂接或进程重建不会重放已经尝试的写入。没有输入框时保留结果等待连接，不静默复制到剪贴板。

## 证据位置

Build 5 的编译、共享协议测试和实际键盘验证见 [交付记录](../Artifacts/auto-insert-fix/README.md)。Build 4 的真实音频生命周期证据保留在 [上版交付记录](../Artifacts/voice-handoff-fix/README.md)。单独双应用探针已验证返回后原 UITextField 仍为 first responder；实际键盘路径需以相应 UI 测试结果为准。
