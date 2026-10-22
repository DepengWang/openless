# 恢复检查点：最终集成／审查／实际产品验收阶段

更新：2026-10-16。先读本文件、[requirements-and-todo.md](requirements-and-todo.md)、[execution-plan.md](execution-plan.md)；不要重做已完成worker／fixture或将部分功能冒充完整产品通过。

## 用户最新要求与资源

- 补齐剩余需求，所有子Agent **GPT-6.1-Sol high**，不许更高；实现后Codex独立review，主Agent再验收。
- 验收使用 **Orca自带唯一模拟器真实操作与截图**，不能只有编译／XCTest。
- 新词典参考已实现列表／来源筛选／浮动加号／紧凑弹窗。
- 新56.45秒键盘视频实际路径 `/Users/trip/Library/Application Support/com.netease.uuremote/Download/ScreenRecording_10-16-2026 15-35-41_1.mp4`。已抽帧，SHA `fe35d4f505d27fc98b7f5acc51341b0cf215466ea043d32c309ba85059c411c1`；`docs/keyboard-video-reference.md` 与 `Artifacts/video-keyboard/`。
- 用户还在 **Codex Test另一个会话**要求蓝色“启用语音”、去除opaque背景以衔接系统Liquid Glass、独立第五个“风格”Tab及我的风格／市场／翻译目标分区、真实raw风格翻译和失败不插原文。其发来的`[Codex coordination]`是协作消息，不是要重复创建worker。
- 主Agent不要每一步宣称完成；完成可验证工作后统一汇报，外部缺口／未实现项不得藏起来。

## 工作区与保护

主目录 `/Users/trip/orca/workspaces/openless/合并两个分支`，分支 `appergb/合并两个分支`，HEAD `e4727697ddf559c3f4d21dd1cd4ac94d5ea935ef`。大量原有desktop／Android／ASR dirty改动保留。不得commit／push／reset／批量格式化；iOS仍untracked，git diff不覆盖全部新增源。

普通模式bundled SenseVoice/local/raw；不预配云凭据、不静默Apple／cloud回退。Core负责领域／网络／crypto／prompt；键盘不采集、不运行Core／ASR、不存keys。自动测试不授权麦克风、不录制人声、不动真实账户／云服务。用户数据、Full Access、输入法列表和唯一模拟器不清空。

唯一VM：`A4B5F07C-C6C9-498F-BEEC-AC7AC98B8A6A`，OpenLess iOS27 QA。当前 **模拟器租约交给Codex Test做误收起恢复／最终表面检查**，是否归还以 `Artifacts/keyboard-codex-coordination.md` 最新节和其 `SIMULATOR_RETURNED=YES` 消息为准。主Agent在归还前不要安装／launch／XCTest／改外观，generic build和Core测试可以。

Orca固定CLI `/Applications/Orca.app/Contents/Resources/bin/orca`；实际serve-sim runtime **1.4.227**（旧1.4.224不存在）。已读orca-cli和orca-emulator版本指南。所有normalized tap要先AX／截图并核实效果。扩展AX有时是键盘局部坐标，不直接当全屏坐标。

## 已完成并集成的组件（不重跑worker）

### 第一波 A/B/D

Workflow `af5b23fc-7fa2-43f5-aa9f-75f105c1e13d`全部完成。
- A词典UI run `437b8215-7a49-4ae9-8f78-269ad70f0add`，五个Swift文件已基线SHA核验合入；主Host source filter all/learned/manual分页前过滤，保留Core真实分类。
- B Core风格／marketplace／ZIP staging／capability run `915eb1bf-56d9-4a20-ae1d-c7ef652e4213`，已集成。
- D真实device-flow账户／禁止错误全量同步 run `5a2ebe6c-f8ef-4a93-8b5c-cf972dc0433d`，已集成。
报告在 `Artifacts/execution-wave1/*-handoff.md`。

### 第二波 B-UI/F/T/E

原workflow `50c5fb79-bdc8-4479-b2d8-2564e50dd85e` 因extension reload显示stopped，**不能据此重启已完成children**。
- B-UI `abdb83a7-cd1d-4e08-b53e-d5e1268efa1f` 已完成并合入7新Swift文件，style ZIP bounded copy、no auto-network、同consumer缓存。
- T `e685c87f-f97e-4c13-bb63-d78380977334` 已完成并合入6Core文件：有序多目标／旧scalar迁移／presence marker／freeze会话目标。主Agent补了sync registry TextList分类和serde-skipped元数据测试，完整Core曾1191通过1忽略。
- F `f91fdc18...`曾超时，续run **`1db1e696-d75d-481a-9b1c-26ed13101894`已完成**，13文件已合入：有限本地scope／overlay／confirmed-ID／numeric identity facade。**没有remote有限sync，不可用singleton full vault上传**。服务端缺namespace/CAS/receipts/authenticated scope，详根docs/encrypted-cloud-sync-limited-scope.md。
- E录音维护 `a8f597f1...`等待决策超时，续run **`7d2d0cd4-95b7-42f9-b4cd-dfac286ca259`已完成**：4源文件＋lib/control hunks已合入；捕获journal、恢复unfinished、隔离孤儿、retention清扫；clear保留Quick Notes WAV。
报告在 `Artifacts/execution-wave2/*-handoff.md`。第二波独立Codex run `e4dc00f0-e320-480e-9cc7-ad4bca6d4d65`已完成，无证实P0/P1/P2，报告 `execution-wave2/codex-review.md`；明确不验收远程sync／后续键盘等。

### 第三波 C-UI／账户／Core voice／词典桥／本地化

全部实施worker已完成并合入，不再启动同任务：
- C-UI `5925582e-307b-4a65-a572-29dcee945cd0`（workflow `49ad043e-edb7-49ae-b67b-bf5b51bcb1aa`），10文件视频键盘／intent／capabilities／generatedPreview／confirm DTO。最终声明WRITER RELEASED，报告 `Artifacts/execution-wave3/keyboard-video-handoff.md`。
- 账户App `cf177465-899f-4b97-b459-048ba06a483b` 同workflow，4Swift文件已合入，status/deviceflow/poll/cancel/signout真实Core调用；未联网验收，remote sync禁用不算实现。
- Core voice `3696b18a-292f-4881-ab2c-34835ee5eac3`：voice_intent新模块＋api/auxiliary/ports/router/cloud provider最小seam已合入。普通与帮写录音强制unmodified raw/native ios-sensevoice，不改用户style；prepare compose冻结endpoint/key/model/options，SecretValue内存；生成专用Core compose preview，**不会自动insert**。
- 词典／Rime `06f4ba8f-8b91-4102-a8fa-903252a83d54`（workflow `f8bbe755-2d38-4888-85aa-09ce80f3be8a`），12文件已合入；主Agent接了control metadata invalidation/confirmation、App VocabularyBridge、Shared vocabularyDecision、键盘真实纠正建议menu和Rime快照import。纯BMP中文CFStringTransform pinyin生成独立script_translator，保留原personal userdb／活动composition后更新。**真实ASR自动新词抽取不存在；现有为Core手改纠正建议，不可冒充自动ASR候选**。
- 本地化 `2dc50399-47b7-4df7-a5cc-eb01de890bbf` 同workflow，11文件已合入；续run **`b4edeee4-9070-4aa6-9584-8307446c24cf`已完成**，68新词＋InfoPlist资源已合入，主Agent又加Undo/Space与styleUI31keys。资源现668keys每语言（按audit实际为准）。UI语言system/zh-Hans/en独立ASR／翻译；主题独立mailbox `interface.preferences.v1`，无FullAccess系统fallback。Root modifier保持runtimeID不以language重置录音。App动态文案迁移仍不完全；资源覆盖≠所有runtime String均已本地化。

## 主Agent已接的关键公共入口

- CoreBridge `onConfigurationMutation` + pending-controls状态，成功管理操作使键盘capabilityRevision失效再刷新；只读nested account status/vocab snapshot/proposals不触发循环。
- `KeyboardCapabilityProjection.swift` 投影无秘密，**最新 canAttemptTranslation＋Core effectiveTargetLanguages**，不再自行用canAttemptPolish推断；capability配置变化和源语言变化均轮换revision。
- AppController start验证command.intent/capabilityRevision/source/language、Core无待处理controls；ActiveSession保存KeyboardVoiceStart。Core独立验证意图，普通模式不外发。
- completed outputDisposition preview只接受matching core/client/intent/promptKind，发布有界generatedPreview（180s），保持App ActiveSession直到confirm/cancel/expiry。confirm只传ID/捕获元数据；Shared纯校验通过后一次性storeInsertion，清preview避免双份大文本；generatedInsertionID防重放。
- 新 `.vocabularyDecision` 独立于start/mic；请求claim-once＋epoch/deadline，只有真实proposal ID／generation可accept/reject/dismiss；不会替换当前输入。
- Mailbox `revokeInsertion` 将pending→targetChanged、attempting→uncertain，submitted不变，不删attempt证据；取消／确认失败调用。
- 键盘CUI-FIX02 enqueue false立即本地收尾＋成功发送后5s专属client回执deadline（全局fresh不能代替确认）。CUI-FIX03 insertText后ack失败也清UI，durable uncertain不重插。
- 确认storeInsertion失败：明确phase error、清preview／ActiveSession、保留主App结果，不锁死按钮到180s；不靠文案识别协议。
- 安全撤销：`KeyboardUndoRecord`内存仅本键盘已写入且未替换selection、≤128字、document/revision/before/after完全一致；每delete步骤重核上下文，不符合就停。按钮仅有可撤销record时显示。
- 新source根Scene语言／theme都已接；旧 `appThemePicker`测试入口换为 `openInterfacePreferences`。键盘UIInputView不替换，仅透明内容以跟随系统材质；确认动作／撤销／风格／翻译用公开API。

## Codex Test（外部会话）协作事实

同主cwd，但只在tmp产patch，主Agent唯一源集成者。以 `Artifacts/keyboard-codex-coordination.md`为协调入口，精确范围已授权。

已读并apply-check后合入：
1. `keyboard-codex-ui-fixes.patch` SHA `a236ba35655bcdc25316f9ef07e68d352c6a3f97c77c6535d9a3eebdc65fee55`：arc最终release(at:)／旋转命中／分页／取消区／辅助字号纵向滚动；真实helpwrite圆触区；processing-only sweep与reduceMotion/window/offpage生命周期。8UIKit fixture测试通过，**不重跑旧fixture**。证据完整保存在 `Artifacts/keyboard-codex-evidence/`（从tmp复制）。
2. surface patch最终SHA `e3d85f404846c6975d03debae142963e2d18749038eceb6a84c512919e8fd2a9`（最初749c废弃）：native背景clear、renderBackground:false、蓝色Enable、透明overlay隐藏背后控件且longpress视图只layer.opacity0保持gesture。
3. caption patch SHA `031f4091dfce5cd937476b8f9ca57176a9c73a3a878545d43c60506bb84dfd33`：bounded键盘hint字体随DynamicType增至22，防Return重叠。已合入。
4. key-legends patch SHA `025f56e58670c26d76ae60d85471084ed6c303febb6f18c9f8e83cd96b1fa533`：只在KeyboardKit standardButtonText为空时给Space/123/ABC/#+=fallback，不改action。已合入。
5. STYLE UI patch SHA `0c995bf1350e886a8dd73b554d8b717a8ca194d1b01c443bb174f2b1c71f501d`：第五风格Tab、MyStyles/Market/Targets、模型设置直接复用DeveloperSettings；selectedTab上移App @State跨runtime reload保留、不改重启默认。Destination自己的SceneStorage待恢复model sheet（等!restarting），配置关闭后refresh原consumer不丢缓存。已合入并更新UITest；**不再使用主Agent曾预留的requestProviderConfiguration，未使用方法/flags已删除**。
6. `/tmp/openless-translation-swift-tests/style-library-tests.patch` 已从openless-all apply，七新增Swift声明＋14旧，共21声明（含参数化）。已存 `Artifacts/execution-wave3/translation-model-tests.patch/report.md`；最终iOS执行仍待跑。

外部Codex已正式产品Orca验过blueEnable／native背景／EN／拼，`Artifacts/keyboard-native-surface-qa/README.md`。最大字号当时失败→caption修复，后续新包复查仍待其回报。当前租约延续用于用户误收起keyboard恢复/最终surface复查；不要抢设备。

## 最新新增翻译业务修复（已写源码／Host测试通过，尚未重建静态库）

用户要求raw风格可显式翻译，且翻译失败绝不能插原文假装成功：
- CoreHost capability使用developer＋traditional＋configured＋enabled/supported channel独立计算`providerConfiguredAndEnabled`；`canAttemptTranslation`看任一Core effective target，`canAttemptPolish`仍反映postprocess/style开关。helpWrite保持既有显式开关。
- voice_intent translation admission读canAttemptTranslation，不借canAttemptPolish；Core仍校验configured exact target。
- PipelineDictationEngine仅context.translation_active时强制PolishFailurePolicy::Fail并拒绝空结果。普通polish UseRawText保持；raw transcript在failure中保留，绝不调用insert。
- Host新测试raw＋多个目标（默认等working language、备用有效）、合法tagged翻译成功、空／malformed输出失败不插原文，通过。
- 初版新fixture给plain text而缺Core translation markers导致失败，是fixture错误已改为合法 `[[OPENLESS_POLISHED_SOURCE]]…[[OPENLESS_TRANSLATION]]…`；并保留malformed失败用例。

## 当前证据／下一验证

- `execution-wave3/translation-integrated-tests.log` **Host70通过**，包含metadata9／voice8＋新增raw translation等。
- `execution-wave3/core-full-final-bounded.log` **Core1193通过、1忽略**，未跳过新增测试；同目录最初 `core-full-final.log`进程SIGTERM，没有测试断言失败，原因未证实；以4线程有界重跑完整通过，保留失败事实。
- Shared最新曾 **57 Testing通过**（额外XCTest本地化模块结果分开看日志），随后新增Undo测试，仍需最终再跑。首次InsertionRevocation测试`#require`缺内部try已修复，不是产品失败。
- 当前所有App测试 `build-for-testing` **通过**，`execution-wave3/final-app-test-build.log`；修复两个worker XCTest class继承init actor冲突：改nonisolated class＋每test @MainActor，不改变断言。
- 旧第三波App native：词典6＋历史4，style19，account18已实际iOS测试宿主通过；产品导航／词典取消2UI通过，记录在对应日志。**新增多目标21、projection、locale、VocabularyBridge及最终keyboard仍待最终实际执行**。
- ENV02原键盘popup恢复：Orca softwareKeyboard helper toggle(type12)后globe切系统拼音再回OpenLess，原三轮refocus测试通过 `keyboard-popup-restored.xcresult`。系统输入框也可能选中OpenLess，不用“Settings也失败”排除扩展。恢复具体原因不可过度归因。
- 已安装产品最近 `caption-legends-product-build.log`（pid72755；后续Codex可能重启了App），包含caption/keylegends/blue/native背景/locale/69Host版本，**尚不含最新Core raw translation修复和第五StyleTab**。源 `final-app-test-build.log`包含新UI，但没有安装。最终必须重建Core双平台后link/install。
- 当前资源audit5测试通过（`localization-audit.log`）；636+31keys按实际文件数为准。infoPlist英文／中文2项随资源包，品牌不变。

## 活动review／最后行动顺序

仅活动独立final Codex review **`e581dd6e-a1d7-4a0a-a682-8c8254d7ca37`**（profile `.pi/agents/openless-codex-review-61.md`，explicit gpt-6.1-sol high/read-only）；收到完成读取 `review/final-ios-integration.md`。它启动后又有第五Tab／raw translation改动，必要时对这部分做有界follow-up review，不能把旧结果覆盖新改动。

1. 回答外部Codex协调／等其归还唯一模拟器；不重跑已过8fixture。
2. 处理final review具体findings，保留未实现项真实状态；查source改动和模型限制。
3. 更新静态Core双平台（显式Rust1.98、fresh `.build/core-ios16`，不可用旧CoreHost/target iOS27缓存），link最终App／extension。
4. 实际iOS native tests（新增projection／vocab／locale／style21）、定向keyboard与页面UI、device unsigned build／every Mach-O floor16。
5. 主Agent Orca最终实际操作／截图：第五Tab、三分区、目标添加/排序/删除可用专有测试目标后恢复；词典专有测试词后只删自己数据；语音／EN／拼／refocus／depth／大字号／dark／locale，防误录／误调用服务；导出真实fixture时标注fixture不能假装用户录音。
6. 收尾当前README／verification／requirements／execution状态，清Runner／测试条目／无用临时资源（不删除复用模型或任何user数据），保留有价值证据，恢复light/large/用户原locale等。
7. 不声称全需求已完成：remote limited sync需服务端namespace及Host vault/runtime hooks，hidden no-switch仍false需真机实现／签名，自动ASR新词抽取无真实接口，风格原位更新不支持，真实云/旧系统/真机无新证据。用户要求all不是伪造完成的授权；最终明确这些残余。

工具：XcodeGen绝对相对path `ios/.build-tools/XcodeGen/.build/release/xcodegen`；Rust显式RUSTC/RUSTDOC1.98；DerivedData-compatibility产品。codexhost不存在，未创建CodexHost thread；原生Pi和独立Codex CLI profile均已正常运行。所有active worker已交接，只有final review/外部Codex QA在继续。
