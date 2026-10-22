# 历史、概览、词典与设置：本批实现与证据

更新：2026-10-16。对应主需求 P1 和 STYLE-06。**这是部分实现报告，不是 63 条需求全部完成。** 软件键盘弹出按用户最新指示归为模拟器环境问题，本批未再排查、重置虚拟机或修改其失败断言。

## 实现

- `CoreHost/src/library.rs` 复用 Core 的历史仓库、独立活动统计、风格与 auxiliary repolish。列表只传 160 字符预览及完整字符数；打开详情后才请求完整文本。预览不写回记录、不插入输入框。非原文风格须有 LLM 凭据，当前仍受开发者模式限制。
- `CoreHost/src/control.rs` 增加 library、详情、单条删除、清空、音频资产、重新润色及词典 CRUD／启停／搜索／分页。普通模式可管理本地产品数据，但不泄露开发者频道或密钥。录音／处理期间限制写操作。
- `CoreBridge.swift` 控制请求串行排队，上限 32，单请求超时 75 秒；关闭时失败返回。接收事件上限 2 MiB，不将音频或凭据放入键盘邮箱。
- `ProductLibrary.swift` 为主 App 管理共享数据。模式重载和搜索使用请求标识，旧请求不得污染新 Core／新查询。操作错误真实显示。
- `HistoryView.swift` 有详情、原文／结果独立复制、单条删除、刷新和清空菜单。不可逆动作使用有明确取消按钮的 alert；取消不改历史。没有音频时仍保留文字操作。
- `HistoryAudioPlayer.swift` 使用 AVAudioPlayer；录音开始或页面离开时停止播放。系统 ShareLink 只接受 Core 返回的真实音频 URL，并受未完成 ASR 状态限制。
- `OverviewMetricsView.swift`／`HistoryModels.swift` 使用真实数据。今日字数按 Unicode scalar，与 Rust `chars()`／桌面 `Array.from` 对齐；今日时间按本地日边界；“平均段落”是今日每段平均时长；累计记录表示当前保留的历史数量。
- 7／30 天趋势使用 Core 独立活动数据，缺失日期补零，未来日期不纳入当前周期。清空历史不清空趋势。空、加载、错误状态不放示例数字。
- `VocabularyView.swift` 接通 Core 本地新增／编辑词条、启停、删除、大小写不敏感搜索、分页及重复处理。**尚未桥接 Rime 学习词典、词条自动建议或加密同步。**
- `AppTheme` 只控制主 App 的浅／深／系统外观并持久化；帮助、版本说明和隐私内容有真实入口。UI 多语言、键盘独立主题与统一提供商中心尚未完成。
- STYLE-06：删除 `SelectionRewriteView.swift`、试用编辑器改写入口／草稿状态和无调用的 Swift rewrite 方法。保留 Core／桌面 rewrite API、历史重新润色与后续帮写。

## 音频保留契约

`CoreHost/src/recordings.rs` 实现 Core 的 `RecordingArchive`，不在 Swift 新造识别业务。

| 状态 | 处理 |
| --- | --- |
| 主动采集 | 可选归档标准 16 kHz／mono／S16LE WAV，最大 30 分钟；独占创建、Unix 0600，每次完整帧更新 WAV 头 |
| 归档失败 | 不允许覆盖已有文件；无效帧停用／丢弃该资产。归档是可选项，不能因此禁用普通 ASR |
| 停止 | 先按原流程停止硬件和排空 PCM，再完成文件头与同步，由 Core 判断保留／清理 |
| ASR 失败且有有效音频 | 保存实际文件，并关联 Core 记录；可播放与系统分享 |
| 成功得到识别文本 | 沿用 Core 的临时音频清理策略；完成转写无导出入口，即使以后有可播放资产也不因此开放导出 |
| 主动取消 | 沿用 Core 丢弃策略，不把取消假装成已完成识别 |
| 单条删除／清空历史 | 清理相应记录关联的音频，不清理词典、独立活动、凭据或另一模式的数据 |

目录是主 App 私有业务目录下的 `recordings/`，不是 App Group。资产只允许 UUID 文件名、普通文件及有界大小；不能用任意路径查询。录音没有加入本轮词典／模式／风格的云同步范围。

**尚缺：**崩溃遗留孤立录音的恢复、历史自动过期后的资产清扫，以及真机上的实际播放／导出、长录音磁盘与内存验证。不能据本批短 WAV 测试声称这些已经完成。

## 验证

证据位于 `Artifacts/product-library/`：

| 证据 | 实际结果 |
| --- | --- |
| `core-tests.log/.exit` | Rust Host **21 通过**；包括 3 项 WAV 安全／生命周期测试、4 项产品库领域集成。使用临时目录与 memory vault，不读写用户 Mac Keychain |
| `library_tests.rs` | 验证预览有界／详情完整、正常模式产品权限、词典重复／编辑／启停／跨 Host 重开、raw repolish 不覆盖原文、原生 ASR 失败保留真实 WAV 且删除时清理、历史清空不清活动 |
| `core-build.log/.exit` | device／simulator 原生库重建成功，各 984 个 Mach-O 对象最高 iOS 16 |
| `native-and-ui-current.log` | 新历史模型 **4 项通过**；同包当时 UI 失败，不能把整包计为成功。模型覆盖导出状态、本地跨日／完整字数／平均时长、独立 7／30 天／溢出保护、原文与结果区别 |
| `related-regression-current.xcresult/.log/.exit` | **4 原生 + 4 UI，整包通过**：公共音频本地 SenseVoice、取消／按钮保护；清空确认后取消、首页设置裁切、滚动不录音、概览／词典／设置／帮助导航 |
| `large-type-current.xcresult/.log/.exit` | 最大辅助字号首页／设置裁切审计 **1 通过**；检查后恢复原字号 large |
| `device-current-build.log/.exit`、`device-current-validation.log` | 最新无签名设备构建和实际模型／产品／iOS 16 下限检查通过；不是签名安装／旧系统运行证明 |
| `Review/*-product-library-current.png`、`*-product-library-large-type.png` | 本批通过用例导出的真实截图；来源见 `Review/index.json` |

本批发现并修复：父容器 accessibilityIdentifier 覆盖统计 picker 的标识；iOS 27 的 confirmationDialog popover 不展示 cancel，故不可逆操作改为显式 alert。原失败结果不算通过；包含用户历史的临时失败附件不作为交付材料。

本批未授予麦克风权限、未录制人声、未调用真实云服务、未清空用户历史／词典，也未改变 Full Access。公共中文 fixture 是集成 smoke，不是准确率测评。

## 仍然未完成

P1 的真实 LLM／播放／分享操作验收、历史详情全场景视觉与音频恢复清扫；P2 的市场／包管理／翻译目标／手势／帮写；P3 的账户与 Core E2EE 同步；Rime 用户词典、确认词条建议、UI 本地化和键盘主题一致性。隐藏式免跳转仍未实现；iOS 16／18 真实运行、签名与真机性能也没有新证据。
