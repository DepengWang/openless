# 键盘免跳转修复方案 · 2026-10-19

## 根因与边界

本次审计基于 de062611。已有 KeyboardStartHandshake 使用请求 ID、原始目标、20 秒 uptime 期限、快照 freshness、host epoch 和完成闩锁；Host 忙碌时等待，只有探针失联或空闲 Host 缺少后台资格才请求前台。自动上屏通过 Mailbox 持久化 claim、同步 insertText 和 uncertain 终态防止重复提交，扩展不初始化麦克风、Core 或模型。

发现一个目标冻结缺口：beginVoice 在 DispatchQueue.main.async 中才读取目标；用户点击后、回调前换输入框时，会把原手势绑定到新目标。修复为在提交已有输入法组合后立即捕获目标，并在异步回调及发送 start 前重新核对目标和可见性。失配终止该手势，不转移目标、不打开主应用。

## 状态机与改动点

- 手势提交后冻结 target → 异步检查可见性与 target → 创建握手。
- stale snapshot → 最多三次 ping → 超时才 openHost；fresh busy → 等待并保留绝对期限；fresh idle 有资格 → start；epoch、目标、期限变化 → 终止。
- sendStart 作为最后一道边界，再次检查原目标与键盘可见性。
- deferred start 保持原命令 requestID、epoch、deadline、KeyboardLease、后台资格和配置能力复核；不延长请求有效期。
- 视觉、动画和文案由设计 worker 负责，本 worker 不保留相关修改；canStart=false 的展示原因交其区分 busy、准备、失联和缺少后台资格。

## 测试与验收

补共享握手测试：等待期间丢失后台资格、过期快照不能覆盖 epoch、deadline 边界、配置变更终止，保证每个手势只有一次终态。运行共享 swift test；集成阶段执行 scripts/build.sh simulator Debug，重装独占 QA 模拟器后运行 App 单测、真实 Safari 键盘冷启动返回、收起重开及自动上屏去重回归，xcodebuild 使用 -collect-test-diagnostics never。日志和截图保存在 Artifacts/ui-redesign-20261019/，按方案、实现及验证结果分阶段提交。

真机仍需验证：同一轮 run loop 换输入框与收起键盘、App 重启 epoch、连续录音清理与配置切换、锁屏/中断/路由变化、后台输出租约真实性与空闲 input I/O 关闭，以及主 App 返回原宿主后一次上屏。PiP 仍未实现，不能宣称隐藏式免跳转已验收。

## 追加发现：取消前台准备与延迟解析

requestForegroundVoice 的宿主解析异步返回后，原代码只核对 foregroundHandoffID；voiceAction 的取消路径却未清除此 ID，因此取消准备后仍可能被延迟回调带到主应用。修复为取消/结束首先作废前台 handoff ID 与输入 anchor，解析回调另受 20 秒 uptime 绝对期限约束；收起键盘原有 stopForDestinationChange 会清除此 ID。所有提示复用既有文案，无新增资源字符串。
