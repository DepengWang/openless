# OpenLess iOS UI 规格（Sonnet 5.5，2026-10-19）

本规格基于 `Artifacts/ui-sonnet-20261019/before-*.png`（iPhone 17 / iOS 27 模拟器，浅色，简体中文）实测审查，不照搬 `ios-ui-redesign.md`（系统标签栏方案已被否决）。

## 0. 不可改动的约束

- 导航保持 `App/ProductDock.swift` 的 `NativeDockPair`：左侧 dock 放 概览/历史/词典/设置，右侧单独一列 dock 放“风格”，选中后展开为 我的风格/风格市场/翻译目标。严禁改成系统 TabView 标签栏或把风格移到页面顶部。本轮只在其内打磨。
- 不用假状态：账户同步、隐藏 PiP 未实现就保持“暂不可用”。结果自动上屏，界面不再要求用户二次确认。
- 尊重 `accessibilityReduceMotion`、动态字号、深浅色；键盘扩展里不加重型动画（不加模糊/粒子/持续动画）。
- 不碰免跳转逻辑（KeyboardStartHandshake、KeyboardVoiceLifecycle、NoJumpAudioSession、AppController 会话/握手、HostApplicationReturn.m、KeyboardViewController 的启动/握手/返回/状态机）。

## 1. 审查结论（问题清单）

| # | 位置 | 问题 |
|---|---|---|
| P1 | 全局 | 页面横向边距 16/20/24 混用；圆角 20/24/14/12 混用；各页标题三种做法（首页自绘、历史/词典自绘大标题、设置/风格/帮助为居中 inline 标题）。 |
| P2 | dock | 浮动玻璃 dock 下方内容（卡片文字、搜索栏、列表行）直接透出，文字与图标叠在一起（首页“平均段落”卡、设置“界面语言”行）；出现/隐藏无过渡；切换页面内容瞬切，没有过渡。 |
| P3 | 首页 | 口号 34pt 大标题 + 副标题 + 状态点 + 180pt 浅蓝光晕圆 + 提示 + 语言菜单 + “概览”标题 + 4 张全 0 统计卡 + 折线图（空数据时 140pt 空白）+ 试用键盘卡，首屏几乎只有营销语；主操作（启用语音/录音）不够突出，语言入口像孤立文字。无数据时统计占屏过大。 |
| P4 | 设置 | 账户欢迎卡 ≈ 260pt 占满首屏，而键盘配置被挤到下面；“账户”能力实际是登录 GitHub，同步暂不可用，不应是首屏主角。各行图标有无不一，pinned 搜索栏下方列表内容透过玻璃，造成脏乱。 |
| P5 | 风格库 | 每张风格卡 ≈ 220pt（图标、2 行描述、勾选圆、整行宽的“当前使用/使用此风格”灰色按钮），一屏仅 2 张；“当前使用”灰色禁用按钮看起来像失效；“勾选圆”与按钮重复表达同一状态；“设置文字模型”蓝色文字孤立。 |
| P6 | 翻译目标 | 空态文案与“配置翻译模型/尚未设置”像三个独立块；“在键盘中翻译”卡末尾有一个空 `Text("")` 导致底部空白和多余分割线。 |
| P7 | 详情页 | 底部主操作条用 `.regularMaterial` 矩形，贴 dock 上缘，和玻璃 dock 风格冲突；“当前使用”用灰色禁用 borderedProminent。 |
| P8 | 历史/词典 | 历史无日期分组，卡片 5 行文本过高；词典背景为 `systemBackground` 与其它页的 `canvas` 不同；空态图标/字号三页各异。 |
| P9 | 文案 | 帮助/系统录音说明仍写“停止录音后，回到 OpenLess 键盘确认插入”“键盘中的录音入口会打开 OpenLess”，与“结果自动上屏、免跳转”相矛盾。 |
| P10 | 键盘 | 见第 6 节（需实机/模拟器键盘截图；本轮模拟器键盘自动选择失败，依据代码审查）。 |

## 2. 设计 token（`App/AppDesign.swift` 新增/调整）

```swift
enum AppDesign {                     // 颜色沿用现有 canvas/surface/accent
  static let accentSoft = accent.opacity(0.10)          // 图标底、选中底
  static let success = Color(uiColor: .systemGreen)
  static let danger = Color(uiColor: .systemRed)
  enum Space { static let page: CGFloat = 20, section: CGFloat = 24, card: CGFloat = 16, row: CGFloat = 12 }
  enum Radius { static let card: CGFloat = 18, tile: CGFloat = 12, icon: CGFloat = 10 }
  static let dockClearance: CGFloat = 78                 // 62 dock + 8 top + 8 bottom
}
```

- 排版（全部 Dynamic Type 文本样式，不写死字号）：页面标题 `.largeTitle.weight(.semibold)` + `tracking(-0.6)`；卡片标题 `.headline`；行标题 `.body`；说明 `.footnote` + `.secondary`；统计数字 `.title2.weight(.semibold).monospacedDigit()`。
- 卡片：`productCard(padding: 16)`，圆角 18，描边 `Color.primary.opacity(0.06)` 0.5pt，浅色无阴影；深色加描边即可。
- 图标底 `IconTile(symbol:, tint:)`：28×28（设置行）/ 36×36（风格卡），圆角 10，底色 `accentSoft`，图标 `.system(size: 15/18, weight: .medium)`。
- 命中区域最小 44×44；行最小高度 52。
- 动画 token（`ProductMotion`，放 `AppDesign.swift`；`PlatformShared/ProductMotion.swift` 已被移除，不要恢复）：
  - `quick = .easeOut(duration: 0.16)`：按压、勾选、徽标。
  - `standard = .spring(response: 0.34, dampingFraction: 0.88)`：卡片展开、列表增删。
  - `page = .easeOut(duration: 0.22)`：页面出现（opacity 0→1 + y 偏移 8→0）。
  - `dock = .easeInOut(duration: 0.4)`（ProductDock 内 UIKit 动画保持 0.4s 临界阻尼）。
  - Reduce Motion：所有位移/缩放去掉，只保留 `.easeOut(duration: 0.12)` 的透明度。
  - 封装：`func motion(_ a: Animation, reduce: Bool) -> Animation`，视图用 `@Environment(\.accessibilityReduceMotion)`。

## 3. 双 dock 打磨（`App/ProductDock.swift`、`App/OpenLessApp.swift` RootView）

保持结构与 UIKit 实现（`DockPairHost` / `DockBarHost` / `DockMorphItem`）不动，只做：

1. **底部渐隐遮罩**（解决 P2）：在 `RootView` 的 dock overlay 之下加 `DockScrim`：`LinearGradient(colors:[canvas.opacity(0), canvas.opacity(0.94)], startPoint:.top, endPoint:.bottom)`，高度 `dockClearance + safeAreaInsets.bottom + 12`，`allowsHitTesting(false)`，`ignoresSafeArea(edges:.bottom)`。内容从 dock 后滚过时淡出，不再与图标重叠。
2. **出现/隐藏过渡**：`dockVisible` 改变时 dock + scrim 使用 `.transition(.move(edge:.bottom).combined(with:.opacity))`，包在 `.animation(motion(.standard), value: dockVisible)`（RootView body 的 overlay 上）；Reduce Motion 时只用 `.opacity`。键盘弹出时即不闪烁。
3. **触感**：`onPrimary`/`onStyle` 回调里触发 `UISelectionFeedbackGenerator().selectionChanged()`（仅当选中项变化时，且遵循 `controller.preferences.haptics`——`ProductDock` 通过 `@Environment` 传入布尔值；此项为可选，若无法干净取得偏好则省略，不加新状态）。
4. **页面切换过渡**：新增 `PageAppear` ViewModifier（`.onAppear` 将 `@State shown` 置 true，`opacity(shown ? 1 : 0)`，`offset(y: shown || reduceMotion ? 0 : 8)`，`animation(page)`），应用在各 tab 根视图内容上。TabView 切换时新页面淡入上移 8pt，0.22s。
5. 无障碍：dock 不变；scrim `accessibilityHidden(true)`。
6. 验收：dock 下方不再出现内容透字；风格展开/收起动画不回归（`testDockRoundTripsAndStyleCardsStayResponsive` 通过）。

## 4. 页面规格

### 4.1 首页 `App/HomeView.swift`、`OverviewMetricsView.swift`（P3）

- 顶部：保留品牌行（`OpenLessMark(34)` + 字标 + 键盘安装按钮，按钮改 `accentSoft` 圆底 44×44）。删除“少打字，多表达。/把你想说的，变成文字。”两行大字：品牌行即是首屏标识，口号放入空态副文案即可。
- 主卡 `VoiceHeroCard`（新 private struct，HomeView 内）：一张 `productCard`，内部垂直排列：状态行（状态点 + `stateTitle`，右侧语言菜单 `Picker` 改成 `Menu` 胶囊，44pt 高）→ 麦克风控件（`NativeSpeakControl` 230×132；去掉 180pt 光晕圆，改为录音时才出现的 `Circle().fill(accentSoft)`，随 `level` 缩放 1.0…1.12，Reduce Motion 下不缩放）→ 提示 `hint` → 次级动作“启用键盘语音”。结构不改业务调用。
- 概览：标题与刷新放在一行；4 张统计卡改为一张卡内 2×2 网格（`Grid`，分隔线 0.5pt），每格“标题 caption / 数值 title2 / 说明 caption2”。无数据（`library.history.isEmpty`）时不显示图表，改显示 1 行说明“开始录音后，这里会显示趋势”；有数据时图表高 112。
- “试用键盘”卡保留（identifier `openKeyboardTrial` 不变），底部 info 文案删除“启用键盘语音后…”重复提示或合并进主卡。
- 状态过渡：`stateTitle`/`hint` 使用 `.contentTransition(.opacity)` + `.animation(motion(.quick), value: phase)`；错误卡 `.transition(.opacity.combined(with: .move(edge:.top)))`；结果卡出现用 `.standard`。
- 页边距 `Space.page`。

### 4.2 设置 `App/SettingsView.swift`、`AccountProfileHeader.swift`（P4）

- 账户行改为紧凑行 `AccountSummaryRow`：头像 44pt + 名称 `.headline` + 副标题（未登录：“登录 GitHub；同步暂不可用”）+ chevron，整体一个 `Section` 行，高 ≈ 72pt，去掉渐变大卡。`AccountProfileHeader(compact:true)` 重做为该行；`compact:false`（账户详情页）保留居中布局但头像 88、`padding(.vertical,24)`。
- 区段顺序：键盘 → 风格与外观 → 语音识别 → 系统录音 → 账户 → 帮助与关于 → 版本。账户移到“键盘”之后（输入是核心任务，账户是辅助）。
- 每行加 28pt `IconTile` 前缀（Picker 用 `Label` + `.labelStyle(SettingsLabelStyle())`），行高 ≥ 52。
- 搜索栏 `PinnedSearchBar`：背景改 `AppDesign.canvas`（不透明）+ 底部 0.5pt 分隔线，消除内容透字。该改动在 `NativeSearchField.swift` 的 `PinnedSearchBar` 内，所有页面共享。
- 页面标题：设置、风格根页使用大标题风格——设置改 `.navigationBarTitleDisplayMode(.large)` 不可与 safeAreaInset 搜索栏共存时，保留 inline，但标题文本 `.headline`；本轮保持 inline（风险最低），仅统一 `PageAppear`。

### 4.3 风格库 `App/StyleLibraryView.swift`（P5、P7）

- `installedStyles` 的卡片重做为 `StyleCard`（≈ 92pt）：`HStack`：`IconTile(sparkles, 36)` | 名称 `.headline` 1 行 + 描述 `.footnote.secondary` 2 行 | 右侧 `StyleUseControl`。整卡其余区域是 `NavigationLink`（identifier `styleDetail-<id>` 不变）。
- `StyleUseControl`：`Button`，当前使用 → 填充 accent 胶囊 + `checkmark`，文字“使用中”；未使用 → 描边 accent 胶囊，文字“使用”；高 32、最小宽 64；`accessibilityLabel` 仍为“当前使用”/“使用此风格”，identifier `styleUse-<id>` 不变；当前项不再灰色禁用外观（disabled 仍保留语义，视觉用 accent 填充而不是 gray）。移除左下角重复的勾选圆。
- 当前使用的卡片：描边 `accent.opacity(0.35)` 1pt，其余 `outline`。选中动画 `.animation(motion(.quick), value: style.active)`。
- 空/可用性提示：`availability` 内“设置文字模型”改成 `ProductRow` 风格的小卡（symbol `slider.horizontal.3`），置于列表顶部提示区，不与风格卡抢层级。
- 详情页底部操作条 `StylePrimaryAction`：背景由 `.regularMaterial` 改为 `canvas` 渐隐（同 DockScrim），按钮 `.borderedProminent`；当前使用时显示 “当前使用 ✓”，使用 `.tint(AppDesign.success)`，`disabled` 保留；底部 padding 加 `dock` 之上的间距（detail 页 dock 仍可见，沿用 `additionalSafeAreaInsets`）。
- 风格市场行：保留 `List`，`StyleSummary` 的 `.padding(.vertical, 4)` 保持。

### 4.4 翻译目标 `App/StyleTranslationSettingsView.swift`（P6）

- 删除 `Section("在键盘中翻译")` 中的空 `Text("")`，改 footer 文案 `Text("按住键盘上的翻译按钮并向上滑动选择语言，也可点按按钮后选择。")`；该区段改为带 `hand.draw`（`character.bubble`）图标的单行说明。
- “翻译模型”区段：状态用 `Label`；未配置时按钮“配置翻译模型”保持 identifier；说明文案并入 footer。
- 目标语言行当前默认项使用 accent `checkmark` 并加粗。

### 4.5 历史 `App/HistoryView.swift`（P8）

- 条目卡文字 `lineLimit(3)`；时间与时长同一行，状态放在右下 `caption`，去掉整行 chevron 重复（整卡可点已足够，保留 `accessibilityHint`）。
- 空态统一为 `EmptyState(symbol:, title:, message:)`（新建于 `AppDesign.swift`：56pt 图标圆底 `accentSoft`，标题 `.headline`，说明 `.footnote.secondary`，垂直 padding 48），历史、词典共用。

### 4.6 词典 `App/VocabularyView.swift`（P8）

- 背景 `systemBackground` → `AppDesign.canvas`；空态改用 `EmptyState`；页边距 `Space.page`。

### 4.7 授权/激活 `App/HostVoiceActivationView.swift`

- 结构保留；各 phase 之间用 `.animation(motion(.standard), value: request.phase)` + `.transition(.opacity)`；ready 文案已是“结果会自动输入”，保持。

### 4.8 文案（P9）

- `SystemRecordingInstructions`、`HelpView`、安装页：删除“回到 OpenLess 键盘确认插入”；改为“停止录音后，文字会自动输入到当前输入框。”；“键盘中的录音入口会打开 OpenLess”改为“首次启用或后台会话过期时，键盘会短暂打开 OpenLess 并自动返回；其余情况直接在键盘内录音。”
- 同步 `Resources` 里对应的 zh-Hans/en `.lproj` 字符串（如存在），并检查 `AppTests`/`UITests` 中引用这些文案的断言。

## 5. 过渡动画清单

| 场景 | 动画 | 实现 |
|---|---|---|
| 页面切换（dock 切 tab） | 新页 opacity 0→1 + y 8→0，0.22s easeOut | `PageAppear` |
| dock 展开/收起 | 保持现有 0.4s 临界阻尼 morph | `DockPairHost.update`（不改） |
| dock 随键盘/二级页隐藏 | 下移 + 淡出，0.34 spring | RootView overlay `.transition` |
| 风格卡选中 | 描边/胶囊颜色 0.16s easeOut | `StyleUseControl` |
| 首页状态切换 | 文案 opacity 交叉淡化 0.16s | `contentTransition(.opacity)` |
| 错误/结果卡出现 | opacity + 8pt 上移，0.34 spring | `.transition` |
| 麦克风电平光晕 | scale 1.0–1.12，随 level，0.1s | HomeView 内 |
| sheet | 系统默认；详情 `presentationDetents([.medium,.large])` | 保持 |
| 键盘模式切换（语音/EN/拼） | 选中块平移 0.2s（已有）；面板内容 opacity 交叉 0.16s | 第 6 节 |
| 录音 准备→录音→处理→上屏/失败 | 见第 6.3 | SpeakButton / 键盘 |

Reduce Motion：位移、缩放全部关闭，仅 0.12s 透明度。

## 6. 键盘视觉（只改布局/样式/文案，不改状态机）

> 依据代码审查（`Keyboard/KeyboardStyle.swift`、`PlatformShared/SpeakButton.swift`、`KeyboardViewController.swift` 视图部分）；若运行截图显示不同，以截图为准并更新本节。

1. 模式条 `KeyboardModeControl`：保持 3 段；选中块圆角与外框同心（外 18 / 内 15 已满足）；选中文字 `.label` + `.semibold`，未选中 `.secondaryLabel`；触控高度 ≥ 36（键盘垂直空间有限，不再增高）。
2. 语音页 `KeyboardStyle.voicePanelHeight = 200`：提示/状态行放在按钮上方固定高度 20pt 的槽位，避免文案出现时按钮跳动；按钮居中，大面积留白用于“风格/翻译”芯片行而不是空着。
3. 状态与文案（遵循协调者规则，只改文案，不改状态机）：
   - `canStart == false` 且 snapshot 新鲜 + 主 App 忙/配置刷新中：显示“正在等待，可取消”，不提示打开 App。
   - snapshot 新鲜 + 空闲 + `backgroundReady == false`（首次启用或后台会话过期）：唯一显示“需要打开 OpenLess，将自动返回”的情形。
   - snapshot 过期：显示“正在连接…”，不用错误色。
   - 结果不再出现“确认插入”。
4. 录音状态视觉（`SpeakButton`）：准备中 = 按钮灰蓝 + 小菊花；录音中 = 红色实心 + 7 根电平条（现有）；处理中 = 渐变扫光（现有，`presentationIsVisible` 控制暂停）；上屏 = 0.16s 勾选淡入后回 idle；失败 = 0.16s 文案淡入，红色文字，不加抖动。均受 Reduce Motion 控制。
5. 不新增模糊/Metal/持续动画，保持扩展内存。

## 7. 验收截图清单（`Artifacts/ui-sonnet-20261019/`，before-*/after-* 成对）

首页、首页滚动、历史、词典、设置、设置滚动、账户、界面与外观、帮助、风格库、风格详情、风格市场、翻译目标、翻译语言选择、添加键盘、试用键盘；深色再做首页/设置/风格库；键盘三模式与录音各状态在可用时补充。

截图由 `UITests/UIAuditTests.swift`（只导航+截图，不改数据）生成：
`AUDIT_DIR` / `AUDIT_PREFIX` 通过 `TEST_RUNNER_` 环境变量传入，`xcrun simctl ui <udid> appearance dark|light` 切换外观。

## 8. 测试保护

保留所有 accessibilityIdentifier：`primaryDock`、`styleDock`、`dockTab-*`、`dockStyle-*`、`styleDetail-*`、`styleUse-*`（label 仍为“当前使用/使用此风格”）、`styleActivate-*`、`openAccountSettings`、`openStyleLibrary`、`openKeyboardTrial`、`prepareSpeech`、`historySearch`、`settingsSearch` 等。
