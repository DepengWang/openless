# Build 11 更新

用户通过新录屏澄清：候选栏保留中文，不添加独立英文原文项。Build 11 已移除 Build 10 的原文候选按钮，保留第一次 Return 确认 rawInput、第二次执行宿主动作的两步行为。默认尺寸与自定义高度见 [Build 11 目标](ios-build11-goals.md)。以下为 Build 10 历史记录。

---

# 拼音原文提交与按键尺寸

目标：拼音模式输入 `openless` 时，蓝色确认键应提交实际键入的英文；用户也能直接点击候选栏中的原文。中文候选、空格选词及符号布局切换保持各自明确的行为。

## 输入行为

- 有组合输入时，第一次按 Return 提交原始字母，不换行、搜索或发送；第二次 Return 才交给宿主执行原本的动作。
- 候选栏第一项显示实际输入，如 `openless`、`nihao`、`nvse` 或 `xi'an`。点击后提交原文，保留 `v` 和用户自己输入的音节分隔符。
- 点击中文候选或按空格仍提交中文；切换符号布局时沿用 Rime 的组合提交规则，不隐式选择第一个中文候选。提交后原文候选消失，恢复后续词预测或工具栏。

`OLRimeSnapshot.rawInput` 直接读取 Rime `get_input`。`commitRawInput` 先复制 Rime 的输入缓冲，再清除组合并返回提交文本；不从会自动插入空格、显示 `ü` 的 `preedit` 反推原文。键盘沿用原有 marked-text 更新路径，替换当前组合，避免重复插入。

## 尺寸

语音、英文和拼音共用 232pt 内容高度。竖屏的四行文字键盘每行 58pt，保留 KeyboardKit 的错位、宽度与手势；模式切换文字为 18pt、44pt 高度，语音页 Return 为 48pt 高度。原文候选至少为 44 × 44pt。横屏继续采用 KeyboardKit 布局并适配共同视口。

## 验证范围

2026-10-21：`bash ios/scripts/test-rime.sh` 已通过，覆盖真实中文候选、空格中文提交、四种原文输入、删除、重复原文提交、清空及提交后原文状态清理；相关 Swift 文件通过语法解析，改动通过 `git diff --check`。

`OpenLessUITests.testSymbolsReturnAndCompositionSwitch` 在 iOS 27 专用模拟器最终通过，覆盖原文候选、两次 Return、中文选词、单字候选 44pt 点击尺寸、符号切换、大写及语音页换行。日志和结果为 `Artifacts/release-10/keyboard-confirmed-tests.log/.xcresult`。测试通过逐键确认真实原文、等待宿主实际文字更新来处理跨进程异步交付，不补打丢失的按键；符号切换沿用 Rime 当前提交规则，不假定隐式选中文。初次设置安装流程失败和中间断言失败记录保留。横屏用例初次停在安装辅助步骤，本轮未重新跑横屏视觉验收。
