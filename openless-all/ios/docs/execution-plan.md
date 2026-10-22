# 剩余工作执行清单与分工

更新：2026-10-16。主需求见 [requirements-and-todo.md](requirements-and-todo.md)。本清单先于新一轮实现落盘；勾选只表示对应阶段已经实际完成，不代表整个功能验收通过。

## 执行合同

- 所有子 Agent：`openai-codex/gpt-6.1-sol`，thinking **high**。不使用其他模型，不使用 xhigh／max，不允许子 Agent 再委派。
- 各组件在当前 `appergb/合并两个分支` 的隔离子工作区运行，复制当前未提交源码快照；不以旧 HEAD 代替 dirty iOS 源码。不 commit／push／reset，不覆盖其他平台工作。
- 多组件任务：独占实现 → 窄接口交接 → 主 Agent 集成 → Codex 独立审查 → 原作者或主 Agent 修复 → 主 Agent 最终验收。
- 模拟器由主 Agent 独占；worker 不启动、删除或重置设备，不改权限，不用用户数据做破坏性测试。Core 测试用临时目录／memory vault；语音验证优先公共音频 fixture。
- 最终使用 Orca 自带模拟器实际操作与截图，AX／截图证明结果；命令返回 ok 不算操作成功。保留唯一设备 `A4B5F07C-C6C9-498F-BEEC-AC7AC98B8A6A`。
- 发现环境／服务／签名缺口时，继续可独立完成的代码，但缺失验收不得勾选。隐藏免跳转不得用假按钮、空 allowlist、长期静默占麦或可见小窗冒充完成。

## 完整执行 To-do

### 0. 需求、环境与调度

- [x] 汇总既有 63 条需求及最新词典截图、键盘退化追问。
- [x] 新增 DICT-01～04、REF-05、EXEC-01～03 和本执行清单。
- [x] 核对当前模型注册名：`openai-codex/gpt-6.1-sol`；显式 high。
- [x] 建立隔离组件工作区、当前 dirty 源码快照与 183 个关键源文件 SHA-256 基线；`Artifacts/execution-wave1/*.json`。不提交工作区。
- [x] 首波 worker 运行记录：workflow `af5b23fc-7fa2-43f5-aa9f-75f105c1e13d`；A `437b8215-7a49-4ae9-8f78-269ad70f0add`、B `915eb1bf-56d9-4a20-ae1d-c7ef652e4213`、D `5a2ebe6c-f8ef-4a93-8b5c-cf972dc0433d`，全部 `openai-codex/gpt-6.1-sol:high`。报告为 runtime-managed `wave1/{dictionary,style-core,account-sync-core}.md`，当前运行中。

### 1. 词典截图实现（A，DICT-01～04／LANG-04）

- [x] 词典大标题、按需搜索、所有／自动／手动胶囊筛选代码由 A 交接并集成；Orca 当前空态截图已核对（非最终全部状态验收）。
- [ ] 按真实 Core 来源分页前筛选并接入 UI。主 Host 已支持 source all／learned／manual，遵循 Core exact learned marker 规则，查询与分类在 total／100 条分页之前应用；210 条混合测试通过（`Artifacts/execution-wave1/vocabulary-source-tests.log`），UI 待 A 交接。
- [ ] 简洁分隔列表、来源图标、必要停用状态；备注与使用次数按需查看。
- [x] 右下浮动加号／紧凑添加弹窗已集成；Orca 实际打开／取消后回到空词典且不写入，截图 `Artifacts/execution-wave1/dictionary-new-modal.png`。保存／失败运行验收仍在总验收门。
- [ ] 编辑／启停／删除放入可发现的行菜单；长词、中文、emoji、空态、加载、错误、深色与大字号。
- [ ] 模型／筛选／输入校验测试；模拟器实际打开、搜索、筛选、添加取消与截图。

### 2. 风格、能力与翻译配置（B，STYLE-01～05／SET-04／LANG-01～05）

- [ ] 复用 Core 市场服务、风格仓库／归档校验，确认本地归档实际格式，不发明 VIP 付费。
- [ ] 窄 Host API：市场列表／详情／下载，已安装包读取／启停／选择／更新／移除／导入。
- [ ] 能力快照区分普通原文、LLM 未启用、缺凭据、服务未验证、模型／权限／网络错误；不泄露密钥。
- [ ] App 只读风格管理与文件导入 UI，无包编辑器。
- [ ] 翻译语言目录和目标新增／删除／排序／持久化，对齐 Core 契约，不凭参考图硬写不兼容上限。
- [ ] 统一提供商／模型设置入口，保留初始本地原文和显式启用原则。
- [ ] 本地校验／假传输契约测试；真实市场／服务结果与网络缺口单独记录。

### 3. 键盘意图、词条确认与 Rime（C，KBD-01～08／SYNC-03／05／06）

- [x] VID-01：找到用户上传的56.45秒视频并实际抽帧，详见 [keyboard-video-reference.md](keyboard-video-reference.md)。
- [ ] VID-02～06：按视频实现语音／帮写／弧形翻译／处理态的UI和逻辑，保留EN／拼真实打字引擎；视频未展开的26键不臆造。

- [ ] 明确点按、按住、选择态、取消、越界、中断、释放的状态表。
- [ ] 语音会话开始固定源语言／目标／风格／提供商能力，durable IPC 兼容旧状态。
- [ ] 长按／滑动翻译目标，只在真实能力可用时开放，普通按住说话不改成松手一律取消。
- [ ] 提示词驱动帮写：描述要写什么 → ASR／LLM → 预览 → 用户受控插入；不读取选区充当帮写。
- [ ] 键盘内选择实际可用的润色模式／风格。
- [ ] 词条建议去重、确认／拒绝／关闭；确认前不写词典／不替换原文。
- [ ] Core 领域词条映射到 Rime 本机词典，不跨进程同步正在写的二进制学习文件。
- [ ] 保留 KeyboardKit 文字输入、无 Full Access 基础打字、系统地球键／听写键处理、目标变更保护和减少动态效果。
- [ ] 协议／状态机测试及主 Agent 的真实键盘操作验收。

### 4. 账户与加密同步（D，ACCOUNT-01／02／SYNC-01～08）

- [ ] 核对实际账户认证、回调 URI／移动平台约束、Keychain 与登出契约。
- [ ] 复用 Core E2EE 注册／初始化、密钥派生、恢复、同步、冲突和错误，不另写 Swift crypto／HTTP。
- [ ] 同步仅确认词条、模式、风格引用；缺失风格可补下载／提示，不默默改服务商。
- [ ] 登录／未登录／实际权限、恢复与错误界面；不编造 Free／Pro、升级、支付或订阅。
- [ ] 本地数据隔离与退出／切账户，不因错误密钥／网络／恢复失败覆盖数据。
- [ ] 离线／冲突／错密钥／重试／跨端契约测试；真实认证／服务验收单独记录。

### 5. P1 余项、兼容、键盘退化与免跳转（E）

- [ ] HIST-04／05：详情复制／删除／重新润色 UI，真实服务可用时验证；原文不可被预览覆盖。
- [ ] HIST-06～10：播放／停止／系统导出真实 WAV，完成态禁止导出；崩溃遗留恢复、历史过期资产清扫。
- [ ] SET-02／03：App／键盘主题一致性，UI 语言独立于识别／翻译；复用已有本地化资源。
- [ ] BASE-02：README、验证、ASR、许可／模型 notices 与实现一致。
- [ ] ENV-02：已用 Orca 实际点击对照，系统 Settings 搜索框有光标、能接收 ASCII，但同样无软件键盘；不只发生在 OpenLess。证据见 [keyboard-environment-check.md](keyboard-environment-check.md)。仍需定位具体根因，不能以系统同现象当作修复通过。
- [ ] VOICE-01：保持本地 SenseVoice、准备不录音、取消／超时／目标保护；不自动录制人声。
- [ ] VOICE-02：可行的真实隐藏免跳转实现与物理设备验证；没有实现／签名不能勾选。
- [ ] COMP-03：当前 device／simulator 构建、Mach-O 下限、原生测试；iOS 16／18 真实运行证据另列，不冒充已覆盖。

### 6. Codex 审查与主 Agent 验收（R／V）

- [ ] 完成组件交接、基线校验与集成，逐条回写主需求状态。
- [ ] Codex（GPT-6.1 high）独立审查集成代码，覆盖数据丢失、秘密泄露、IPC／会话意图、UI／iOS 16 和需求遗漏。
- [ ] 将 findings 分类为有效／已失效／无效／范围外，修复有效阻断并复审。
- [ ] 主 Agent 检查修改文件和测试结果，不以 worker 声称通过代替证据。
- [ ] Orca 自带模拟器实际页面／键盘操作、截图和 AX；深浅色、大字号、取消／离线错误和导航。
- [ ] 使用安全的专有测试条目验证 CRUD，清理仅本次测试条目；不清用户历史／词典／凭据／Full Access。
- [ ] 恢复单产品入口、无 Runner／多余探针，无后台录音；唯一模拟器与模型资源保留。
- [ ] 提交最终逐项结果：完成、未完成、证据、外部依赖，不把总体目标写成已完成。

## 拓扑与文件所有权

分类：**multi-seam**。词典呈现、Core 风格服务、Core 账户／同步、键盘状态机、App 集成、环境诊断可分别验证。采用分波执行；不是让一个 worker 包办整项产品。

| Lane | 决策／独占范围 | 隔离 | 依赖／下一门 | 输出 |
| --- | --- | --- | --- | --- |
| A | 词典呈现：`App/VocabularyView.swift`、词典 DTO／筛选模型、新词典测试；不改 Rust／根入口 | `/Users/trip/orca/workspaces/openless/ios-dict-ui-61` | Core 来源字段契约交给主 Agent；Swift 编译／窄测试 | 修改文件、界面值、校验、剩余缺口 |
| B | 风格服务：新 `CoreHost/src/style_library.rs`、对应 tests；本地可接线编译，但公共 lib／control 交主 Agent 按补丁集成 | `/Users/trip/orca/workspaces/openless/ios-style-core-61` | 先交 Host JSON 契约；后启动独占 App 风格 UI worker | Core 方法与 JSON 合同、测试 |
| D | 账户／E2EE：新 `CoreHost/src/account_sync.rs` 与测试；本地可接线编译，但公共入口交主 Agent 按补丁集成 | `/Users/trip/orca/workspaces/openless/ios-account-sync-61` | 先核对现有服务／密钥契约；后启动账户 UI worker | 可实现合同／实现、外部缺口与测试 |
| B-UI | 风格 App 消费者：新 StyleLibrary.swift／StyleLibraryView.swift，翻译设置消费者；不改 Root／Host | `/Users/trip/orca/workspaces/openless/ios-style-app-61`（创建后执行） | B Host JSON 已明确，31 项集成测试通过；仅实现真实支持能力，后续多目标合同由 T 提供 | 页面与 DTO／导入校验测试 |
| F | 有限范围 E2EE Core 策略：共享 Core cloud_sync_e2ee* 的 opt-in policy／document gate／迁移测试，默认桌面全量不变 | `/Users/trip/orca/workspaces/openless/ios-sync-policy-61`（创建后执行） | D 证实全量协议超范围；不得上传历史／凭据来冒充完成 | Core 范围合同、向后兼容及错密钥／冲突测试 |
| T | 多翻译目标／会话冻结：共享 Core config／settings／DictationContext／start-options，与既有单目标向后兼容 | `/Users/trip/orca/workspaces/openless/ios-translation-domain-61`（创建后执行） | B 证实只有单目标；先补领域合同，C 才能消费 | 目标迁移／排序／会话意图测试 |
| C-UI | 视频键盘呈现／手势：Keyboard／PlatformShared／Shared意图DTO与状态机，不改Host／AppController | `/Users/trip/orca/workspaces/openless/ios-keyboard-video-61` | VID参考、B能力和T会话合同；App／Host主Agent接线 | 状态表、协议、UI及定向测试 |
| D-UI | 账户App消费者：AccountSettings model/view/tests，不改Host／Root | `/Users/trip/orca/workspaces/openless/ios-account-app-61` | D已接通真实device-flow；F未就绪时清楚禁同步 | 账户页面／轮询取消／安全错误 |
| C-Core | 冻结语音意图／Core compose执行：CoreHost新voice_intent模块和最小Core compose facade | `/Users/trip/orca/workspaces/openless/ios-voice-intent-core-61` | T已集成；仅此worker写该契约，App接线主Agent | 取消／无凭据／冻结／preview不自动插入测试 |
| C-Rime | 词条桥接／确认建议独立模块：RimeBridge、新Shared词条DTO、App词条桥；不改C-UI控制器 | `/Users/trip/orca/workspaces/openless/ios-vocabulary-bridge-61` | C-UI挂接点交主Agent；只同步明确确认词条 | 词条映射／无FullAccess／确认测试 |
| E-audio | 历史音频恢复／过期清扫：新 recording_maintenance 模块，窄 recordings／library接线；主入口补丁交主Agent | `/Users/trip/orca/workspaces/openless/ios-history-maintenance-61` | A／B／D已集成；不得丢弃尚可恢复的孤立录音 | 临时目录崩溃／过期／幂等测试 |
| E-UI | UI语言独立设置及本地化资源／主题传递的新模块，不改Root／Settings／KeyboardController公共入口 | `/Users/trip/orca/workspaces/openless/ios-localization-61` | 复用中文／英文已有翻译；公共入口补丁交主Agent | 本地化覆盖／持久化／原生构建 |
| I | 主 Agent 唯一集成者：`lib.rs`／`control.rs`／AppController／Root／project／主需求文档 | 当前工作区 | 所有组件交接后集成；不得并发拷贝覆盖 | 集成记录、冲突处置 |
| R | Codex 独立只读审查 | 集成工作区只读 | 实现与定向测试完成后运行，high | 有证据的 findings／复审 |
| V | 主 Agent 独占 Orca 自带模拟器 | 当前工作区 | review 修复后 | 操作记录、截图、逐项验收 |

## 调度事实

- 当前可用 Pi registry：`openai-codex/gpt-6.1-sol`；worker 显式 `:high`。
- `codexhost delegate --help` 返回 `/bin/bash: codexhost: command not found`，未启动任何 CodexHost thread。已核对 Codex 本机模型缓存支持 `gpt-6.1-sol`／high；新增项目 profile `.pi/agents/openless-codex-review-61.md`，通过原生 external-cli runner 显式 `codex exec --model gpt-6.1-sol --config model_reasoning_effort="high" --sandbox read-only`。内置 adapter 不支持自定义 args 的预检已纠正；当前 profile 发现有效，尚未启动 review，不用模型默认 low。
- 首波 A／B／D 已交接。A 五个Swift文件经基线哈希检查集成，B／D 入口手工逐段组合，没有覆盖词典筛选或其他lane修改。主Host全部 **39测试通过**（`execution-wave1/integrated-account-tests.log`）；新词典6＋历史模型4在实际iOS测试宿主通过（`dictionary-model-current.xcresult`）。尚未Codex最终review。
- 第二波 `50c5fb79-bdc8-4479-b2d8-2564e50dd85e`：B-UI `abdb83a7-cd1d-4e08-b53e-d5e1268efa1f`，F `f91fdc18-5e25-4a9d-b888-f36cac6a005c`，T `e685c87f-f97e-4c13-bb63-d78380977334`，全部6.1/high，运行中。C／E和账户消费者按合同继续；最终review和验收未提前勾选。
- D 已证实当前 Core E2EE 使用 `encrypted-full-snapshot-v1`，包括历史／活动／频道／凭据，没有有限文档策略注入。已批准先做真实 device-flow 账户与 fail-closed 同步边界；D 必须交接精确 Core scope-policy／transaction seam，随后另开共享 Core 范围适配任务。**禁用同步只是中间保护，不算 SYNC-01～08 已实现。**
- A 的來源合同已批准：遵循 Core 当前 exact learned marker 分类，不按短语猜来源；unknown／未来 DTO 值仍不强行标自动。A 初次包解析缺失本地 speech Frameworks，已批准仅补只读依赖 symlink 后重跑，不重下／修改二进制。
