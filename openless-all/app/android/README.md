# OpenLess Android 平台代码

Android 相关 Rust、Kotlin 与前端代码的统一入口。桌面端通过 `#[cfg(not(mobile))]` 分层，不受影响。

## 目录结构

```text
android/
├── kotlin/              # Kotlin 模板（CI 复制到 gen/android/）
├── manifests/           # AndroidManifest snippet + res/xml
└── frontend/            # React 模块（Vite 别名 @android）

src-tauri/src/android/   # Rust 运行时模块（crate::android）
```

## Rust（`src-tauri/src/android/`）

| 模块 | 职责 |
|------|------|
| `jni.rs` | JNI 工具（clipboard、overlay service、accessibility） |
| `native_bridge.rs` | Kotlin ↔ Coordinator JNI 入口 |
| `overlay.rs` | 悬浮窗权限与 show/hide |
| `accessibility.rs` | 无障碍服务状态与 paste |
| `shizuku.rs` | Shizuku 状态诊断与受控无障碍恢复 |
| `insert.rs` | 跨 App 文本插入策略 |
| `updater.rs` | 应用内更新（manifest 拉取、minisign 校验、系统安装器） |
| `updater_logic.rs` | 更新 URL / 版本比较纯函数（全平台可测） |
| `types.rs` | Android 偏好与状态类型 |

主 crate 通过 `mod android;` 引入，常用 API 经 `crate::android::` 扁平 re-export。

## Kotlin（`android/kotlin/`）

`tauri android init` 后由 [`scripts/copy-android-scaffolding.mjs`](../scripts/copy-android-scaffolding.mjs) 复制到 `src-tauri/gen/android/app/src/main/java/com/openless/app/`。

Manifest 合并脚本：

- [`scripts/merge-android-v1-manifest.mjs`](../scripts/merge-android-v1-manifest.mjs) — 麦克风权限（`android/manifests/AndroidManifest.v1.snippet.xml`）
- [`scripts/merge-android-overlay-manifest.mjs`](../scripts/merge-android-overlay-manifest.mjs) — 悬浮窗 / 无障碍
- [`scripts/merge-android-shizuku-manifest.mjs`](../scripts/merge-android-shizuku-manifest.mjs) — Shizuku Provider / 授权 Activity
- [`scripts/patch-android-shizuku-deps.mjs`](../scripts/patch-android-shizuku-deps.mjs) — Shizuku Gradle 依赖

## 前端（`android/frontend/`，别名 `@android`）

| 路径 | 职责 |
|------|------|
| `lib/androidTypes.ts` | Android 偏好与状态 TS 类型 |
| `lib/androidIpc.ts` | overlay / accessibility / Shizuku Tauri invoke |
| `lib/androidMicrophonePermission.ts` | WebView 麦克风权限辅助 |
| `components/AndroidPermissionsPanel.tsx` | 设置页 Android 权限与 overlay 配置 |

`src/lib/types.ts` 与 `src/lib/ipc.ts` 保留 re-export，现有 import 路径仍可用。

## Kotlin 类架构总览

`OpenLessImeService.kt` 曾经是一个 5300+ 行的单体类，承载所有四个输入面板（语音/笔画/剪贴板/英文）的构建、查询、渲染、提交逻辑。2026-09-24 把笔画面板整体拆到了独立文件（详见下方"2026-09 英文键盘增强 + 笔画性能优化 + 架构拆分"一节），现在的模块边界如下：

### IME 主体与面板控制器

| 文件 | 角色 |
|------|------|
| `OpenLessImeService.kt`（约 4857 行） | `InputMethodService` 实现本体。承载：四面板切换（`selectInputMode()`/`swipeInputMode()`）、语音听写生命周期（录音/整理/撤销重做/编辑）、英文键盘（`buildKeyboardView()` 及其 `EnglishLayer{LETTERS,NUMBERS,SYMBOLS}`）、剪贴板面板与历史浏览器、跨面板共用的 UI 构建工具（`keyboardKey()`、`dp()`/`tone()`/`ui()`、`roundedButton()`、`candidateItemView()`/`styleCandidateFirstState()`/`showCandidateOverlay()`）、后端心跳与运行时守护、Activity 生命周期对接。四个面板里只有笔画面板的专属逻辑已经拆出去；语音/剪贴板/英文面板的构建+查询+提交逻辑仍在这个类里，是后续可以按同样模式继续拆分的部分。 |
| `StrokeInputController.kt`（约 650 行，新增） | 笔画面板专属：编码输入（`appendStroke`/`deleteStroke`/`segmentStroke`/`clearStrokes`）、字候选/联想候选查询+渲染+提交（`refreshStrokeCandidates`/`refreshAssociations`/`renderCandidateRow`/`populateCandidateRow`/`commitWord`/`commitStrokeCandidate`/`commitAssociation`）、数字符号子面板（`buildStrokeNumberView`）。持有 `StrokeInputRepository`/`StrokePhraseRepository` 两个仓库实例及其生命周期。通过构造函数持有 `service: OpenLessImeService` 引用，回调共用基础设施；真正跨面板共用的部分（候选气泡、`keyboardKey()`、主题/布局工具）留在 `OpenLessImeService` 上，可见性从 `private` 放宽到 `internal` 供本类调用。 |

### 笔画/英文各自的离线词典与个人频率层

| 文件 | 角色 |
|------|------|
| `StrokeInput.kt` | `StrokeInputRepository`：离线五笔画字典查询，按笔画编码前 4 位分桶索引（`stroke.dict.tsv` + `stroke-frequency.tsv`），后台单线程执行器 + `Handler.post` 回主线程。 |
| `StrokePhraseRepository.kt` | 联想词（下文预测）查询：约 22 万条词组（`phrases.dict.tsv`）建成字符 Trie，按词组自身前缀索引；`confirmedText`（已上屏文字的滚动窗口）作为后缀去匹配 Trie 里的前缀。 |
| `StrokeUserFrequency.kt` | 个人用词频率存储与打分（`ln(1+次数)*80 + exp(-天数/30)*24`），SharedPreferences 持久化，笔画个人纠偏与联想使用记录共用同一张表（3000 条上限）。 |
| `EnglishCandidateProvider.kt` | 英文候选词查询：2 万词基础词典（`hermitdave/FrequencyWords`，OpenSubtitles-2018 语料）建成前缀 Trie，架构与 `StrokePhraseRepository` 同构。 |
| `EnglishUserFrequency.kt` | 英文个人用词频率存储/打分，与自定义词（用户打过但不在基础词典里的词）的增删查询，独立一张表（不与笔画共享）。 |

### 面板内共用的自绘 View（均为 `OpenLessImeService.kt` 内的嵌套类）

`KeyPreviewOverlay`/`KeyPreviewBubbleView`（全面板共用的单例按键气泡）、`ModeToggle`（顶部四段模式切换，Canvas 绘制）、`SwipeModeContainer`/`SwipeRail`（手势容器）、`VoiceButton`/`MicrophoneKeyView`/`StrokeKeyView`/`StrokeGlyphView`/`StrokeActionView`/`ActionSymbolView`/`ShiftKeyView`/`MidDividerTextView`（各类自绘按键）。这些类多数原本是 `private`，笔画面板拆分时把其中 `SwipeModeContainer`/`SwipeRail`/`StrokeActionView`/`InputMode` 枚举放宽到了 `internal` 供 `StrokeInputController` 引用。

### 支撑性 Android 组件（生命周期/权限/持久化）

| 文件 | 角色 |
|------|------|
| `OpenLessApplication.kt` | 全局 `Application`，`ActivityLifecycleCallbacks` 驱动 JNI Activity Context 注册/注销，重启统计分类白名单（`ALL_RESTART_CATEGORIES`）。 |
| `OpenLessRuntimeService.kt` | `START_STICKY` 前台常驻服务，JNI Context 真正的挂载点（不依赖任何 Activity 的存活）。 |
| `OpenLessBackendWarmupActivity.kt` | Tauri host Activity，兼启动器入口与设置页宿主；静默唤醒、`webViewCreationWatchdog`、卡死兜底自重启。 |
| `OpenLessOverlayService.kt` / `OpenLessOverlayBridge.kt` | 悬浮窗显示/隐藏与 IME 侧桥接。 |
| `OpenLessAccessibilityService.kt` 及 `OpenLessAccessibility*.kt` 系列 | 无障碍跨 App 文本插入（策略、目标定位、结果类型）。 |
| `OpenLessShizuku*.kt` | Shizuku 受控无障碍恢复通道。 |
| `OpenLessKeyboardSettingsActivity.kt` | 键盘专属原生设置页（震动强度/时长、重启诊断表）。 |
| `OpenLessClipboardHistory.kt` | 剪贴板历史持久化（收藏/分类/过滤）。 |
| `OpenLessAndroidPreferences.kt` | 偏好读取的统一入口（笔画个人化开关、联想开关、英文候选开关等）。 |
| `OpenLessProcessRestartStats.kt` / `OpenLessBuildInfo.kt` | 重启原因诊断计数、调试构建版本号。 |
| `OpenLessCredentialCipher.kt` / `OpenLessCredentialVault.kt` | Android Keystore 支持的凭证加密存储。 |
| `OpenLessNative.kt` | JNI 原生方法声明（对应 `native_bridge.rs` 的导出）。 |

对应的 Rust 侧模块划分见本文件顶部"Rust（`src-tauri/src/android/`）"表格；两侧通过 `OpenLessNative.kt` 声明 ↔ `native_bridge.rs` 导出一一对应。

## 笔画输入法 IME 最近更新（`OpenLessImeService.kt`）

面板高度固定为 300dp（`SwipeModeContainer.onMeasure()` 强制），笔画面板内编码区 24dp + 候选区 36dp（合计 60dp）与下方按键区共同瓜分剩余高度，任何输入状态下都不重新布局。

| 功能 | 说明 |
|------|------|
| 手势 | 左右切换面板滑动阈值 72dp→100dp；新增下滑 ≥120dp 收起键盘（`hideKeyboardPanel()`）；面板切换带滑入动画（`refreshInputView(slideDirection)`） |
| 编码区/候选区 | 固定高度、扁平背景 + 分隔线（`buildEncodeAreaBackground()`），选中/首选候选字改为编码文字同款浅蓝 + 加粗（`strokeEncodeAccentColor`），不再用红色；候选区超出部分用 `showCandidateOverlay()` 悬浮层展开，不推挤按键 |
| 中间 3×4 笔画键 | 间距收紧到 ~2dp（键位 `setMargins(dp(1)...)`），并与右侧红色功能键列、左侧标点列的行边距对齐一致 |
| 键盘设置 | 长按 Logo 打开全屏原生设置页 `OpenLessKeyboardSettingsActivity`（先实现震动强度/时长，后续可继续加项） |
| 语言同步修复 | `OpenLessApplication` 原来按精确类型判断 `MainActivity`，实际设置页跑在子类 `OpenLessBackendWarmupActivity` 上从未触发，改成 `is` 判断 |
| 剪贴板 | 新增历史持久化 `OpenLessClipboardHistory.kt`，按钮配色与笔画面板统一 |
| 语音纠错联动（已改为写入词典，见下方新行） | `native_bridge.rs` 新增 `nativeAddCorrectionRule`，手动改过的听写结果自动写入纠错词典——此后该入口已改为写入全局 Dictionary，纠错规则改为纯手工维护 |
| Activity Context 生命周期 | JNI 侧改用显式 `GlobalRef` 注册表（`nativeRegisterActivityContext`/`nativeUnregisterActivityContext`），由 `OpenLessApplication` 的 `ActivityLifecycleCallbacks`（`is MainActivity` 匹配，覆盖子类 `OpenLessBackendWarmupActivity`）驱动注册/注销；之前先后用过 `ndk_context::android_context()`（Activity 重建后失效）和 `tao::main_android_context()`（仅追踪前台 Activity，后台时为空）都出过问题 |
| 启动图标黑屏修复 | 直接点应用图标会启动裸 `MainActivity`，触发第二次、未被追踪的 Tauri host 初始化（WebView 拿不到内容）；改用 `merge-android-overlay-manifest.mjs` 把 LAUNCHER `intent-filter` 挪到 `OpenLessBackendWarmupActivity` 上解决 |
| 构建版本追踪 | 新增 `OpenLessBuildInfo.VERSION`（每次调试构建手动 +0.01），键盘设置页底部显示；版本变化时 `OpenLessApplication.resetRestartStatsOnVersionBump()` 把全部重启计数清零，避免跨构建对比无意义的历史值 |
| 进程重启统计改为"仅今天" | `OpenLessProcessRestartStats` 去掉原来的 3 天滚动窗口，只保留 `todayKey()`；设置页对应表格去掉多日列，改成单行 `key + 计数 + 中文说明`（如"sticky 系统杀后恢复"） |
| 键盘设置页主题跟随 | `OpenLessKeyboardSettingsActivity` 原来背景/文字颜色是写死的深色，从未跟随应用自己的浅色/深色设置；改用与 `OpenLessImeService.isDarkTheme` 相同的 `theme_mode` 读取逻辑 |
| 震动滑块 UX | 滑块标签实时显示 `Max n Set:当前值`；时长上限从 500ms 逐步减半到 125ms，便于精细调节 |
| 听写生命周期触觉反馈 | `onCapsuleStateChanged()` 在开始录音、录音结束进入整理、整理完成三个节点各触发一次 `performKeyHaptic()` |
| 悬浮窗跟随输入法面板（已回退） | 曾尝试给悬浮窗加"跟随面板显示/隐藏 + 固定在 Logo 旁"的开关，目的是保活；后确认 `OpenLessOverlayService` 的显示/隐藏与保活完全无关（保活由 `OpenLessRuntimeService` 独立的常驻前台服务负责，文档注释原话是"without showing an overlay"），且该指示器与面板自身的话筒动画信息重复，价值有限，遂整批回退（含设置页开关） |
| 设置页黑屏问题（两层原因，均已修复） | 见下方独立章节《设置页黑屏排查记录》 |
| 撤销/重做/编辑控件退格保留 | `OpenLessImeService` 通过输入法自己的退格键（含全选后退格，统一走 `deleteBackward()`）删空听写内容后，不再让 `lastDictationText` 失效——新增 `selfInitiatedTextChange` 标记，由 `deleteBackward()` 置位、`invalidateDictationResultIfTextChanged()`（`onUpdateSelection()` 触发）消费并跳过失效判断；只有非退格触发的清空（如宿主 App 发送后自动清空）才会让控件消失；另外开始新一轮听写（非编辑/纠正分支）时主动隐藏 |
| 编辑弹窗 checkbox 调整 | "同时加入纠错规则"checkbox 从紧跟文字预览下方挪到面板下部（贴底部分隔线上方），字体和勾选框都放大 1.5 倍；全选/未选中（回退到编辑整句）时默认不勾选，只有选中部分内容时才默认勾选（`editingOriginalText != lastDictationText` 判断） |
| 安装时间显示 | `OpenLessApplication.resetRestartStatsOnVersionBump()` 每次清零重启计数时，同时把 `System.currentTimeMillis()` 写入 `openless_runtime` 的 `build_first_seen_wall_time`；键盘设置页版本号行后面追加"安装于 yyyy-MM-dd HH:mm"，方便截图时知道这些计数是从什么时候开始累计的 |
| 后端语音链路指示灯 + 心跳自愈 | Logo 右侧新增 12dp 圆形指示灯（`backendLinkIndicator`，仅语音面板显示，带呼吸闪烁），就绪=淡绿、录音中=红、整理中=蓝、心跳检测到未就绪=黄；`runBackendHeartbeatCheck()` 每 6 秒跑一次 `isBackendReady()`，未就绪时主动 `ensureBackendReady()` 并记一次 `heartbeat` 重启统计——解决"点麦克风没反应、录音指示没有波动，过一会又自动恢复"这种静默断链，让用户不用先点一次才能发现链路已经断了 |
| 面板切换动画：头部固定 | `refreshInputView(slideDirection)` 原来把整个面板（含 Logo 行）一起滑入，现在改成只对 `childAt(1..)`（头部之后的内容）做滑动动画，`childAt(0)`（每个面板 builder 都第一个 addView 的头部行）全程不动，避免 Logo 跟着"跳一下"；四段模式开关点击也接入了同一套动画（按 `InputMode.entries` 顺序算左右方向），不再只有划动切换才有动画 |
| 划动切换阈值改为宽度百分比 | `SwipeModeContainer` 的 `commitThreshold` 从固定 dp 改成面板宽度的固定比例（目前 1/3），随屏幕尺寸自适应，不再是写死的 dp 值 |
| 英文键盘 iOS 17 布局 + 候选词 | `buildKeyboardView()` 重写为 `EnglishLayer{LETTERS,NUMBERS,SYMBOLS}` 三层结构，键位排列/切换逻辑对齐 iOS 17 的 ABC/123/#+=；新增按键按下预览气泡（`KeyPreviewBubbleView`，Canvas 绘制 + `PopupWindow.showAsDropDown()` 锚点定位，支持拖动到相邻键改选）；新增 `EnglishCandidateProvider`/`EnglishUserFrequency`（架构照抄 `StrokePhraseRepository`/`StrokeUserFrequency`：前缀 Trie + 后台线程 + LRU 缓存 + 用户词频衰减），候选栏复用笔画面板的 `candidateItemView()`/`HorizontalScrollView`/展开按钮/`showCandidateOverlay()`，视觉与交互完全一致；基础词典来自 `hermitdave/FrequencyWords`（MIT License，OpenSubtitles-2018 语料，见 `english-frequency.LICENSE.txt`），构建脚本 `scripts/generate-english-dictionary.mjs` 生成 20000 词、约 250KB 的 `english-frequency.tsv`；设置页新增"英文单词提示"开关（`english_suggestions_enabled`）。未改动任何其它输入模式的按键组件或输入连接协议 |
| 编辑/纠正结果改为写入全局词典，不再自动生成纠错规则 | 之前"编辑弹窗 checkbox"/剪贴板划动"加入纠错"两处入口，都是自动调用 `nativeAddCorrectionRule()` 往 `CorrectionRuleStore` 写一条 pattern→replacement 规则；现在改为调用新增的 `nativeAddVocabularyWord(phrase)`（`native_bridge.rs::spawn_add_vocabulary_word()`，走 `OpenLessBackend::add_vocabulary_if_absent()`，与桌面端 `add_vocab` 命令同一个全局 Dictionary 存储，用于提升 ASR/LLM 转换准确率），重复纠正同一个词不会堆积重复条目；纠错规则（`CorrectionRule`）今后改为纯手工维护，不再由这些 Android 入口自动生成。配套新增 `nativeVocabularyPhrases()`/`nativeRemoveVocabularyWord()`（对应旧的 `nativeCorrectionRulePatterns()`/`nativeRemoveCorrectionRule()`），剪贴板划动区的高亮判断和"加入/移出"文案随之改为"加入词典/移出词典" |
| 剪贴板划动"加入"改为直接写词典，键盘"纠正"文案统一改"字典" | 剪贴板划动左滑"加入"原来会打开语音更正面板（`openCorrectionRuleViaVoice()`，让用户说一遍"正确的词"再写入），这套流程是从 CorrectionRule 的 pattern→replacement 语义搬过来的，现在词典只是"记住这个词"，不再需要额外说一遍；改为直接调用 `nativeAddVocabularyWord(entry.text)` + `refreshInputView()`，与"移出"一侧的直接调用对称。连带删除了因此变成死代码的 `openCorrectionRuleViaVoice()`/`editingForClipboardCorrection` 及编辑面板里因它而生的"隐藏勾选框"特殊分支。键盘上"历史\纠正"按键改名"历史\字典"（`History\Dict`），语音编辑面板的词典 checkbox 文案改为"加入到字典中"，且默认改为不勾选（两个入口——编辑听写结果、长按"历史/字典"更正选中文字——都统一从未勾选开始），只有用户主动勾选才会在完成时写入 |
| 修复：语音/笔画输入后切到相机、拨号盘甚至桌面会误弹出面板 | 根因不是某个 App 的输入框类型判断错误，而是"后端保活/唤醒"链路的副作用：`OpenLessImeService.onStartInput()`（任意 App 里任何输入框获得焦点都会触发，含 Camera/Dialer 内部不需要软键盘的隐藏 EditText）、`onCreate()`、`runBackendHeartbeatCheck()`（每 6 秒，与当前前台 App 完全无关）等多处都会调用 `ensureBackendReady()`；一旦判定后端未就绪，`OpenLessBackendWarmupActivity.launchWarmup()` 会用 `FLAG_ACTIVITY_NEW_TASK` 抢占前台弹出 Tauri host 窗口，180ms 后自我隐藏，再过 260ms 无条件调用 `OpenLessImeService.requestInputPanelAfterWarmup()` 强制 `requestShowSelf()`——全程不检查用户此刻是否还在原来的输入场景，前后跨度小半秒足够用户切到完全不相关的 App 或桌面。修复：新增 `OpenLessImeService.isInputPanelCurrentlyShown()`，`launchWarmup()` 在抢占前台前的最后一刻记录面板是否可见（`restoreInputPanelAfterWarmup` 标记），`sendToBackground` 只有该标记为真时才调用 `requestInputPanelAfterWarmup()`；该函数自身在真正 `requestShowSelf()` 之前又补了一次即时校验（`currentInputEditorInfo?.inputType != TYPE_NULL`），双重保证只有"用户本来就在打字、且当前确实还有个想要键盘的输入框"时才会强制拉出面板。已构建安装（1.31），效果待真机测试确认 |
| 统一按键预览气泡：单例 `KeyPreviewOverlay` 取代逐键 `PopupWindow` | 每个面板通过 `wrapWithKeyPreviewOverlay()` 包一层 `FrameLayout`，顶层加一个不拦截触摸的 `KeyPreviewOverlay`，全面板所有键共用同一个 `KeyPreviewBubbleView` 实例，拖动到别的键只是重新定位而不是拆一个弹窗建一个新的；`showTapPreview()`/`showSwipePreview()` 两个入口分别服务英文/数字点击气泡与笔画划动气泡。踩过的坑：show/hide 原来带 80ms/70ms 淡入淡出动画，手速快的点击会在动画播完前就被打断，导致气泡"半透明一闪"甚至完全看不见——改成瞬间显示/隐藏（不再 animate）后解决。气泡外框四角改成统一圆角（`CORNER_RADIUS_DP`，目前 4dp，原来只有上面两角是圆的，下面是硬编的直线转角）|
| 笔画上划：支持划动中途改选相邻/跨行键，同时收紧了"纯上划"误判 | `keyboardKey()` 的 `swipeUpAction`/`swipePreview` 现在挂在 `tag`（`Pair<action, preview>`）上，`findSwipeTargetAt()` 用手指当前真实屏幕坐标逐键命中测试（跨行需要先爬到 `row.parent` 再枚举全部含 swipe 标记的兄弟行）。命中测试的竖直容差从最早抄英文键盘那份的 24dp 收紧到 2dp——笔画 4 行键几乎贴边（行距约 1dp、单行高约 47dp），24dp 容差会让相邻行判定区域大面积重叠，纯粹垂直上划（如从"0"直上划）会被误判成上面一行的键（实测复现过 0→8、8→2、4→1）。即便竖直容差收紧后，又加了一道独立的横向位移门槛（`horizontalDragArmed`，10dp，一次性锁存）：手指没有真正横移超过这个阈值之前，就算已经真实划进了别的键的物理范围，也不切换 `trackedView`；跨过门槛后才允许持续跟手跨键，松手即提交当前停留键的数字，不要求松手瞬间仍处于上划状态。切换命中键时额外触发一次 `performKeyHaptic()`（与普通按键同参数）。气泡视觉：`showSwipePreview()` 改成始终贴着目标键的真实屏幕位置定位（不再按手指抬起高度算，避免跨键/跨行时坐标基准不一致而"飘移"），竖直间距比英文点击气泡多 20dp（`SWIPE_GAP_DP`）；字号改成固定 22dp（不再照抄被划到那个键自身的字号——"0"对应的麦克风键本身字号只有 10sp，之前会导致"0"的气泡明显比其它数字小）；气泡背景暗色主题下从原来偏亮的浅灰改成 `rgb(52,52,54)`（比候选/编码区卡片背景 `rgb(58,58,58)` 略深，又比面板底色 `rgb(48,48,48)` 略亮，边框 `rgb(92,92,96)`），浅色主题维持原样。字色最终改成右侧动作栏（←/↵/清除/123）同款红 `Color.rgb(153, 26, 40)`、粗体叠加细描边——中途经过候选字蓝（`strokeEncodeAccentColor`）又调整回红，`candidateItemView()` 里高亮首个候选字的颜色同步从蓝改回这个红，两处统一 |
| "0"（麦克风）键长按进入语音模式改用可调延时 | `keyboardKey()` 新增 `longPressAction`/`longPressDelayMs` 参数，用自建 `Handler.postDelayed` 取代系统 `setOnLongClickListener`（系统长按判定固定约 500ms，无法按键单独调整）；"0"键延时设为 900ms，且一旦识别到上划手势就立刻 `removeCallbacks` 取消这次长按——起因是原来"0"键长按 500ms 直接进入语音模式，手指按下后稍作停顿再开始上划，长按会先于上划阈值触发，导致"0"键上划实际从未生效过 |
| 笔画气泡颜色多轮反馈微调（在上面几行提交之后又调了几轮） | 背景：暗色主题从 `rgb(52,52,54)`（刻意比候选/编码区卡片背景 `58,58,58` 更深）改成方向相反的"比候选行更浅"——`rgb(80,80,84)` 再到最终 `rgb(96,96,100)`，边框同步调亮到 `rgb(128,128,133)`；浅色主题也从原来比候选行背景更深的 `C8C8CC` 改成更浅的 `rgb(246,246,249)`（边框 `rgb(210,210,215)`）。字色：候选字高亮与气泡文字暗色主题下从 `Color.rgb(153, 26, 40)`（动作栏同款红）加亮到 `Color.rgb(190, 45, 60)`，同一色相只是更亮，浅色主题维持动作栏原色不变 |
| 话筒键划动手势重做：两个方向都改成"松手才生效"+ 实时视觉反馈 | 原来上划进 RAW 模式（`rawModeArmed`）和下划取消录音（`cancelDictation()`）都是划过 24dp 阈值那一刻在 `ACTION_MOVE` 里立刻生效，容易划太快/抖一下就误触。两个手势统一改成：阈值最终定为 30dp（上划中途试过 40dp）；判定不再是一次性锁存，而是每次 `ACTION_MOVE` 重新计算的实时布尔值（`swipeUpActive`/`swipeDownActive`），手指缩回阈值以内会跟着退出"待触发"状态；真正生效（进 RAW / 取消）只在 `ACTION_UP` 时看当前布尔值是否仍为真。配套实时视觉：`VoiceButton` 新增 `armedForRawSwipe`（待机胶囊背景过渡成浅绿 `rgb(200,230,201)`）和 `armedForCancel`（录音时波形颜色过渡成浅红 `rgb(255,150,150)`），都是 120ms `ValueAnimator` 缓动，松手（不管有没有真的触发）统一再缓动回原色，不是瞬间跳变；每次越过阈值（不论方向）触发一次 `performKeyHaptic()`，真正触发 RAW/取消那一刻仍是原有的 `performDoubleKeyHaptic()`。中途还试过给待机话筒图标本身加同步的变黄+上移几 dp 效果，用户反馈后整批回退，只保留胶囊/波形背景色这一种反馈方式 |
| 修复：`actkill` 细分重启统计跟总数脱节 | 用户从键盘设置页截图发现 `actkill_finishing`（59）比 `actkill` 总数（16）还大，逻辑上不该发生——两者本该在 `OpenLessRuntimeService.kt` 里原子地一起 +1。根因是 `OpenLessApplication.ALL_RESTART_CATEGORIES`（版本号变化时清零哪些 key 的白名单，注释里写明"要手动同步"）没跟上后来新增的 `actkill_self`/`actkill_config`/`actkill_finishing`/`actkill_os` 四个细分 key，导致 `actkill` 总数每次调试构建升版本号都清零，四个细分计数却完全不清零，在同一天多次构建之间持续累加、跟总数脱节。已在 1.68 把四个 key 补进白名单 |
| 英文候选栏长按删词 | `EnglishCandidateProvider` 新增 `excludedWords`/`isCustomWord()`/`forgetCustomWord()`，`EnglishUserFrequency` 的 `addCustomWord`/`removeCustomWord`/`hasCustomWord` 本来就实现了但一直没接 UI 入口；现在长按一个英文候选（`candidateItemView()` 新增 `onLongPress` 参数）即可从个人词库移除并当场隐藏，Toast 提示 |
| 英文键盘顶行数字上划 + 第二/三排符号上划 | `buildEnglishCharKey()` 新增 `swipeSymbol` 参数，复用笔画面板同款上划手势（dp(10) 触发阈值、实时可回退的 arm 状态、跨键重定位、`KeyPreviewOverlay` 气泡）；映射：顶行 q~p → 1~0，第二排 a~l → `@ # $ % & - + ( )`，第三排 z~m → `: ; ' . , ! ?`。小号提示标签最终定为 11sp、灰色（`SWIPE_SYMBOL_HINT_TEXT_SIZE_SP`/`TOP_PADDING_DP`，试过跟随气泡的红色但在这个尺寸下更不清楚，改回灰）；字母与提示分成两个独立 View（`FrameLayout` 包一个真正居中的字母 `TextView` + 一个贴顶的小号 `TextView`），取代最初"两行文字挤在一个 TextView 里"的方案（那种做法会把字母整体往下挤）；字母另加 `translationY = 2dp` 的纯渲染层微调 |
| 英文字母 + 底排 123/Return 统一 `sans-serif-medium` 字重 | `buildEnglishCharKey()` 的字母 `TextView` 与底排 `123`/`Return` 按键都加了 `Typeface.create("sans-serif-medium", NORMAL)`，字号不变；语音面板状态文字/麦克风无声音提示/Return 按钮、剪贴板历史分类标签/空态提示的英文分支也统一跟进（按 `englishUi` 判断，中文文案保持原样不受影响，避免动了不该动的视觉） |
| 切换面板"笔画"字号微调 | `ModeToggle.onDraw()` 的 `drawLabel()` inkHeight 从 `h*0.34f` 调到 `h*0.38f`（"EN" 保持 `h*0.28f` 不变） |

开发流程：每次改动后用 `npm run copy:android-scaffolding` 同步 → `gradlew app:assembleArm64Debug -x app:rustBuildArm64Debug`（Kotlin-only 改动跳过 Rust 重编译）→ `adb install -r` 装机 → 通过 `adb exec-out screencap` 或用户反馈截图核对真机效果；涉及尺寸争议时用 `adb shell wm density` + 实测 px 反推 dp，避免凭空猜测布局问题。

## 2026-09-24：笔画候选/联想性能优化 + `StrokeInputController` 架构拆分

起因：用户反馈笔画输入的候选词/联想词在低配机上显示有点慢。排查+修复分两批，第二批顺带完成了"拆分 `OpenLessImeService.kt`"这项一直挂着的优化项（笔画面板是其中最大的一块自成体系的代码）。

### 第一批：候选栏渲染

`renderCandidateRow()`/`refreshAssociations()` 原来每次按键都 `removeAllViews()` 再重新 `addView()` 全部候选——每个候选都走 `candidateItemView()` → `keyboardKey()`，这是给完整按键用的通用构造器（分配 `roundedButton()` drawable、挂一整套长按/上划重定位触摸监听闭包），`candidateItemView()` 拿到手后又把 background/elevation 清空重置成 0，相当于每次按键都把最多 `MAX_CANDIDATES`（36）个候选的"完整按键"白造一遍再丢弃大半。改为 `populateCandidateRow()`：候选内容抽成 `CandidateSpec`（文字/首选态/宽度/点击动作），对候选栏里已有的 View 按位置原地复用（改文字、改宽度、重新绑点击目标、重设首选态颜色/粗体），只有候选数量变化时才真正新建/删除 View。候选点击靠 `keyboardKey()` 本来就走的标准 `OnClickListener`（点按→`performClick()`）触发，复用时直接重新 `setOnClickListener` 即可换目标，不用碰长按/上划那套触摸监听逻辑。

### 第二批：数据层三个真实热点

1. **联想词典没有预热**：`StrokePhraseRepository`（约 22 万条词组）此前没有像 `StrokeInputRepository` 那样在 `onCreate()` 里预热，建 Trie 的开销（下面第 3 点）就会砸在"这个会话里第一次真正需要联想的那一下"——用户正等着候选栏更新的时刻。补了 `preloadAsync()`，随 `strokeController.preloadAsync()` 在键盘启动时一起后台预热。配套给 `ensureLoaded()`/`searchAsync()` 加了 `Log.i`/`Log.d`（tag `OpenLessPhrase`，耗时 + 条目/结果数），供后续排查用 logcat 直接看数据而不是猜。
2. **`trimIfNeeded()` 惰性化**：`StrokeUserFrequency`/`EnglishUserFrequency` 的 `record()` 原来每次都调 `trimIfNeeded()`，而 `preferences.all` 是整表拷贝，哪怕紧接着的 size 检查什么都不做也会先拷贝一次。两个类都加了 `approxSize` 缓存：重复记录一个已存在的键（最常见情况）现在完全不碰 `preferences.all`；只有真正插入新键才检查大小，只有真超过 `MAX_ENTRIES`（3000）才付真正整表排序清理的代价。
3. **联想缓存 key 从"完整滚动上下文"改成"实际尝试过的后缀子串"**：`StrokePhraseRepository` 的 LRU 缓存原来按完整 `confirmedText` 滚动窗口做 key，连续打字场景下几乎每次上下文都不一样，缓存基本没有命中过。挪到 `find()` 内部，按 `findLongestSuffix()` 实际尝试的每一个后缀长度做 key——搜索顺序和结果完全不变（只是把本来就会做的每次 `find()` 调用记住了），但短的常见结尾子串会在不同上下文之间反复命中。`CACHE_SIZE` 从 64 提到 256 配合更高但更有效的 key 基数。
4. **`insert()` 建索引改成先收集再一次性排序**：原来每插入一条词组，沿途最多 8 层节点都要做"判重（线性扫）+ 对最多 `NODE_TOP_N`（12）个候选重新排序"，22 万条词组累计是百万级别的小排序操作。改成 `insert()` 只管往节点列表里追加，全部文件读完后跑一遍 `finalizeNode()` 对每个节点一次性去重（`distinctBy { it.text }`，保留先出现的，等价于原来的判重逻辑）+ 排序 + 裁剪到 top-N。

### 架构拆分：新增 `StrokeInputController.kt`

`OpenLessImeService.kt` 一直被列为"该拆分"的优化项（见根 `README.md` Roadmap），笔画面板是其中最大的一块自成体系的代码。新建 `StrokeInputController.kt`（约 650 行），把笔画面板专属的状态和逻辑整体搬过去：编码输入、字候选/联想候选的查询+渲染+提交、数字符号子面板，以及笔画/词组两个仓库的生命周期。纯搬运，不改逻辑——只是把 `this` 换成 `service`，把裸调用换成 `service.xxx()`。真正跨面板共用的部分（`candidateItemView()`——英文候选栏也在用、`showCandidateOverlay()`、`keyboardKey()`、主题/布局工具函数）留在 `OpenLessImeService` 上，可见性从 `private` 放宽到 `internal` 供新 controller 调用。两处原来混在一起的重置逻辑（`selectInputMode()`、`onStartInput()`）拆成了 `resetForModeSwitch()`/`resetForNewInputSession()` 两个方法而不是合并成一个——因为两处原来重置的字段集合并不完全一样（切换面板还会重置数字符号子面板和标点翻页，新开一个输入会话还会额外清掉未完成的分词但不动标点翻页），合并会悄悄改变行为。`OpenLessImeService.kt`：约 5300 → 4857 行。完整类架构见本文件上方"Kotlin 类架构总览"一节。

### 现状

以上改动均已编译、完整构建（`npm run tauri:android:build:debug -- --target aarch64`）、`adb install -r` 装到测试设备（含一台小米设备，MIUI 首次安装需要在设备上手动确认安装弹窗，ADB 无法代为点击）。**尚未做完整的真机手势回归测试**——候选栏 View 复用、`StrokeInputController` 拆分这两处改动量较大，涉及大量调用点迁移，只能靠编译器捕获结构性错误，无法验证手势细节层面的行为是否完全一致。建议下一步至少覆盖：笔画正常打字选字、分词多字词、退格逐笔删除、联想候选点选、数字符号面板切换、繁简切换、语音长按跳转、英文顶行数字上划 + 第二/三排符号上划（含跨键滑动切换）、低配机上连续打字的候选栏响应速度主观对比。

## 2026-09-24（续）：语音面板 Raw 模式提示 + 状态文字空白 bug 修复

### 功能：话筒下方的 Raw 模式提示（`voiceRawHint`）

语音面板一直有"上划进 Raw 模式"（跳过 AI 润色，原样转写）这个手势，但没有任何 UI 提示，新用户发现不了。新增一行小字，位置和样式经过几轮调整：

- **文字与颜色随状态切换**（`rawModeHintText()`/`rawModeHintColor()`）：空闲态显示"上滑开启 Raw 模式"/"Swipe up for Raw"（浅灰）；一旦当前录音真的进了 Raw 模式（`rawModeArmed`），录音中和"正在思考"整理阶段都改显示"原样转写"/"Raw Mode"，颜色也换成和状态行、话筒胶囊/波形同款的橙色 `LINK_COLOR_RECORDING_RAW`；录音或整理中但**不是** Raw 模式时，这行字完全隐藏（`rawModeHintVisible()`）——此时它既不是在教一个还有用的手势，也不是在确认什么，留着只是干扰。
- **点击/长按弹 Toast** 解释 Raw 模式含义（"Raw模式，语音原样转写，不做AI润色整理"），没有做独立的 tooltip 气泡控件。
- **位置几经调整**：最初放在状态文字下方，字号/间距按设计稿走了几轮（14sp→12sp→11sp，间距 8dp→4dp→2dp→1dp）；后来改成放到话筒图标下方——直接把它塞进 `buttonHolder`（原来是竖直 `LinearLayout`，`gravity=CENTER`）会导致一个新问题：LinearLayout 会把"图标+小字"整体当一个块居中，图标为了给下面的小字腾地方被顶了上去，跟上方状态文字的间距变大了。最终把 `buttonHolder` 从 `LinearLayout` 换成 `FrameLayout`：话筒图标用自己独立的 `Gravity.CENTER` 定位（不受其他子 View 影响，位置和加小字之前完全一致），小字和麦克风无声音警告都用同一个 CENTER 基准点再加固定 `topMargin`（44dp / 70dp）挂在图标下方。

### Bug 修复：上屏后状态文字变成空白

`onCapsuleStateChanged()` 处理原生端（Rust）发来的 `"done"`/`"cancelled"`/`"error"`/`"idle"` 胶囊状态时，用的是 `message ?: "默认文案"`——这个写法只在 `message` 严格为 `null` 时才生效。如果原生端某次传回的是**空字符串**（非 null，只是内容为空），会原样透传下去，状态文字就真的变成空白，肉眼看就是"点击开始说话不见了"。

更麻烦的是它和"完成后自动回落到准备态"这个新逻辑（见下一节）叠加：如果原生自己在那个等待窗口内又发了一次带空消息的 `"idle"` 事件，会立刻把状态改成 `"idle"`（空白文字）；等自动回落的延迟回调触发时，一检查"当前状态是不是还是 done"已经不成立（提前被原生的空消息 idle 事件改过了），直接跳过不做任何修正——空白就这样卡住，没有任何机制能把它修回来。

修复：新增 `String?.orDefault(fallback)` 扩展函数，null **或**空白字符串都归一到默认文案，`onCapsuleStateChanged()` 里全部四处 `message ?: "..."` 换成这个。

### 新增：完成后自动回落到"点击开始说话"（`scheduleRevertToIdle()`）

以前"已上屏"/"已完成"这类确认文案会一直停在那，直到用户开始下一轮录音才会变。现在统一抽出 `scheduleRevertToIdle()`：任何一次转瞬即逝的确认状态（`setState()` 的 `"done"` 分支——覆盖正常提交、编辑流程完成、原生 `"done"` 事件；以及 `toggleUndoRedoDictation()` 里直接调 `updateStatus()` 的"已撤销"/"已上屏"，这两个不走 `setState()`，之前完全没接入这套回落逻辑）触发后 `DONE_TO_IDLE_DELAY_MS`（2000ms）都会自动变回"点击开始说话"，让用户清楚知道可以开始下一轮输入了。

防抖用的是一个单调递增的 `statusRevertToken`，不是比较 `state` 字符串——每次调用 `scheduleRevertToIdle()` 都会让 token 前进一位，只有最后一次调度的回落会真正生效。这样"刚上屏、用户立刻点了撤销"这种连续操作会正确地从撤销那一刻重新数 2 秒，不会出现"上屏那次的计时先到，把撤销的提示提前冲掉"的竞态。

## 设置页黑屏排查记录（供交叉验证）

**现象**：长按 Logo 或点击应用图标打开设置页，`OpenLessBackendWarmupActivity` 窗口本身能弹出、获得焦点，但内容区域是纯黑，没有任何 UI、没有加载动画。期间语音听写、笔画输入完全正常，说明 Rust 后端和 IME 进程本身健康，问题局限在这一个 Activity 的 WebView 内容渲染上。

排查过程中确认了两层互相独立的原因，都已修复：

**原因一：`settingsRequested` 时序竞争**（commit 待提交，见 `OpenLessBackendWarmupActivity.kt`）。这个 Activity 平时绝大多数时候是"静默唤醒"用途——`onCreate()` 里 `warmupHandler.postDelayed(sendToBackground, 180L)` 会在 180ms 后自动 `moveTaskToBack()`，除非 `settingsRequested` 为 true。`ensureBackendReady()` 的自动静默唤醒和用户主动打开设置（图标/Logo）有概率在几毫秒内先后对同一个 `singleTask` Activity 发起 `startActivity()`；`settingsRequested` 原来完全依赖 `onCreate()`/`onNewIntent()` 收到的 Intent 内容判断，如果静默唤醒先创建了实例（`settingsRequested=false`）并挂上 180ms 定时器，用户的真实打开请求的 `onNewIntent()` 没能足够快地取消这个定时器，窗口就会"一闪而过"被收回后台——真机 logcat 里能看到 `onSurfaceShowChange show=true` 之后约 200ms 出现 `show=false`，与 180ms 定时器耗时吻合。

修复：新增 `settingsOpenPending`（`companion object` 里的 `@Volatile` 标记），由 `openSettings()`/`openSettingsIfRunning()` 在调用 `startActivity()` **之前**同步置位；`openSettingsIfRunning()` 还直接对已存活的 `activity` 实例同步设置 `settingsRequested = true` 并 `removeCallbacks(sendToBackground)`，不依赖 Intent 投递时序。同时给 `launchWarmup()` 内部 120ms 延迟后的 `startActivity()` 调用加了 `isRunning()` 复查，避免延迟期间已经有别的入口把 Activity 启动起来后仍旧重复发起静默唤醒。修复后 logcat 确认窗口能稳定停在 `RESUMED`/`isVisibleRequested=true`，不再自动隐藏。

**原因二：WebView 渲染进程在后台被系统冻结，恢复前台后没有重新合成画面**。上面这层修复只解决了"窗口是否留在前台"，窗口留住之后用户反馈仍然是黑屏，用 `adb exec-out screencap` 截图确认是纯黑（不是白屏/异常颜色/局部渲染），排除了布局或主题配色问题。由于这个 Activity 设计上几乎全部时间都处于 `moveTaskToBack()` 之后的后台状态，其宿主的 WebView 渲染子进程（`com.google.android.webview:sandboxed_process0`，运行在本应用 UID 下）是安卓"后台进程冻结"省电机制（Android 12+ App Freezer，各 OEM 定制系统通常更激进）的典型目标；真机 logcat 里能看到该子进程被 `Async freezing`/`received async transactions while frozen` 又 `sync unfroze`。冻结解除时 Chromium 合成器没有必然重新提交一帧画面，窗口本身可以正常获得焦点、绘制，但内部 WebView 表面停留在最后一次（或从未）合成的空白/黑色状态。

修复：`OpenLessBackendWarmupActivity` 新增 `onWebViewCreate(webView)` 覆写（`WryActivity` 已有的钩子，此前未使用）保存 WebView 引用，新增 `reloadWebViewForSettings()`，在 `onCreate()`/`onNewIntent()` 两处 `settingsRequested` 变为 true（即真正为用户打开设置，而非静默唤醒）时调用 `webViewRef?.post { webViewRef?.reload() }`，强制 Chromium 从头重新加载并合成一次画面，不依赖冻结/解冻这套系统机制自己恢复。

**注意**：排查早期还发现过一次 `adb install -r` 重装导致的一次性黑屏（`ActivityThread: Package [com.openless.app] reported as REPLACED, but missing application info. Assuming REMOVED.`），这是重装时旧 WebView 渲染进程还没被系统完全回收造成的开发流程副作用，`adb shell am force-stop com.openless.app` 可以清掉，不是代码问题，真实用户走应用商店/App 内更新不会遇到。

**2026-09-20 更新：黑屏在真机上又出现了，尚未排查**。现象描述是"过一段时间之后长按 Logo 打开设置页又是黑屏"——跟上面两个原因的表面症状（Activity 能弹出、内容区纯黑）一致，但触发条件里"过一段时间"这个说法目前还没有对应到具体机制，需要重新确认：

1. 是上面原因一/原因二的**回归**（比如某次改动意外绕过了 `settingsOpenPending` 或 `reloadWebViewForSettings()`），还是一个**没被覆盖到的新场景**（比如两次修复都是在"静默唤醒 vs 用户主动打开"这条路径上验证的，"过一段时间"具体指多久、期间设备是否息屏/App 是否被切到过后台、是否发生过 Runtime Service 重启，都还没有对照过）。
2. 重新走一遍上面的排查方法：`adb logcat` 抓 `onSurfaceShowChange`/`settingsRequested`/`Async freezing`/`sync unfroze` 这几个关键字，`adb exec-out screencap` 截图确认确实是纯黑（不是别的黑屏/白屏原因），再看这次是卡在原因一那种"窗口被收回后台"，还是卡在原因二那种"窗口在前台但 WebView 没重新合成"，或者是两个都排除后的第三种原因。
3. 在得出结论前不要假设是"同一个 bug 又犯了"——`reloadWebViewForSettings()`/`settingsOpenPending` 这两个修复本身有没有被后续改动动过、或者这次复现的具体操作路径是否真的会经过它们，都需要先用证据确认。

**2026-09-22 更新：找到了这次复发的真正根因（跟上面两个原因都无关），另外还发现了一个残留的、未彻底解决的深层问题。**

背景：`OpenLessBackendWarmupActivity` 平时靠 `moveTaskToBack()` 常驻后台，但系统的最近任务清理机制会不定期真的把它销毁掉（`onDestroy reason=finishing`，跟切换 App、熄屏时间较长强相关）。销毁之后再点 Logo，走的是"冷启动重建一个新 Activity 实例"这条路径——这条路径此前从未被验证过，因为历史上只测过"复用已存活实例"这条路径。

根因链（均已用真机 logcat + 直接阅读 Wry/Tao/Tauri 源码确认，不是猜测）：

1. **`activity_id` 恒为 0**：Wry 的 `WryActivity.kt` 用 `intent.extras?.getInt(ACTIVITY_ID_KEY) ?: hashCode()` 决定每个 Activity 实例的唯一 id，但 `Bundle.getInt()` 在 key 不存在时返回 0（不是 null），只要 Intent 带了任何 extra（我们的 `EXTRA_SHOW_SETTINGS` 就是），`?:` 就永远不会走到 `hashCode()`。结果：每次"冷启动重建"出来的新 Activity，Wry 内部用来做 `WEBVIEW_ATTRIBUTES`/`ACTIVITY_PROXY`/`CONTEXTS` 索引的 `activity_id` 全都是 0，跟前一个（可能正在被异步清理的）实例撞车。修复：`openSettings()` 的"starting fresh"分支现在显式带上随机的 `__wryActivityId` extra（`OpenLessBackendWarmupActivity.kt`）。
2. **`ensure_main_webview_window()`（新增的 Rust 命令，`android/native_bridge.rs`）**：既然 Wry 自己的 `android_setup()` 只在 `WEBVIEW_ATTRIBUTES` 里已有当前 `activity_id` 记录时才会发 `CreateWebView`，而"冷启动重建"出来的新 id 必然没有记录，就需要主动调用 `WebviewWindowBuilder::new(app, "main", ...).build()` 重建——这个命令从 `OpenLessBackendWarmupActivity.onCreate()` 幂等调用。
3. **Wry 的 Android `CreateWebView` 分发路径原本完全静默**：`main_pipe.rs` 里找不到 `ACTIVITY_PROXY` 记录时只有一行 `#[cfg(debug_assertions)] eprintln!`（stderr，进不了 logcat），`mod.rs` 里对应的 `.expect("no available activity")` 一旦命中会直接 panic（跨 JNI 边界未定义行为）。本地 vendor 了一份 wry 0.55.1（`src-tauri/vendor/wry-0.55.1/`，通过 `Cargo.toml` 的 `[patch.crates-io]` 接入，做法跟已有的 `vendor/wayland-scanner` 一致），把这两处换成能进 logcat 的 `log::warn!`/`log::info!`，才让后续排查变得可能。
4. **Tauri 自己的窗口注册表里会残留"僵尸 main 记录"**：`ensure_main_webview_window()` 一开始信任"`get_webview_window("main")` 有返回值 = 不用重建"，但真机日志证明这条记录可能是上一个已死实例留下的，跟当前 Activity 完全对不上——于是改成"发现就先摘掉再重建"。摘除一开始用 `close()`，后来发现 `close()`/`destroy()` 都只是把消息扔进 tao 事件循环的 proxy、立刻返回（**读 `tauri-runtime-wry` 源码确认**），并不会同步生效；真正会把这条记录从 Tauri 顶层 `AppManager` 的注册表里摘除的，是收到一个真正的平台级 `WindowEvent::Destroyed` 事件（`AppManager::on_window_close`，`tauri` crate 内部私有，应用代码完全够不到）——而如果 WebView 从来就没真正建出来过，这个事件永远不会发生。也就是说：**一旦触发过一次"WebView 建不出来"，这条 "main" 记录就永久卡死，之后每次重建都会报 `a webview with label "main" already exists`，真机连续验证过好几次、间隔几十秒也没自愈，只有强杀整个进程才能解开。**
5. **仍未根治的深层问题**：`ensure_main_webview_window()` 的 `.build()` 返回 `Ok` **不能保证** WebView 真的建出来了——真机日志里出现过 `build ok=true` 之后 `InnerWebView::new`/`CreateWebView proceeding` 这两行诊断日志完全不出现的情况，说明 Wry/Tauri 在 Android 上 window 创建的实际执行是异步派发到 tao 事件循环线程的，`.build()` 的返回值只反映"消息排队成功"，不反映"真的执行完成"。这次没有继续往 `tauri-runtime-wry` 内部深挖（范围和风险都比 patch wry 大一截，而且这是横跨全平台的核心窗口管理代码，不是 Android 专属）。

已落地的兜底方案（`OpenLessBackendWarmupActivity.kt`）：

- `webViewCreationWatchdog`：`settingsRequested` 的冷启动如果 4 秒内 `onWebViewCreate()` 没来，判定为卡死，`finishAndRemoveTask()` 收尾（用户体感：黑一下自动退回，不用手动按返回）。
- 连续卡死达到 `STUCK_RESTART_THRESHOLD`（2 次）时，判定"main"标签已经永久卡死，触发 `restartProcessAsLastResort()`——用 `AlarmManager` 安排 500ms 后重新拉起 launcher Activity，再 `Process.killProcess()` 自杀。**注意**：`tauri::AppHandle::restart()` 在这里完全不能用——它桌面端的实现是 `Command::new(当前可执行文件路径).spawn()` 再 `exit(0)`，Android 应用没有"当前可执行文件路径"这个概念，spawn 必然失败，最后只会执行那句 `exit(0)`，把进程杀掉且没有任何东西把它拉回来（读 `tauri` 源码 `process.rs` 确认后放弃这条路，改成 Kotlin 自己实现）。加了 60 秒冷却防止重启死循环，新增 `stuckwindow` 重启统计分类。
- 顺带把 `onBackPressed()` 关闭设置页时的 `moveTaskToBack()` 换成了 `finishAndRemoveTask()`（之前不敢这么做是因为历史上 `finish()` 会跟 HWUI 渲染线程池的清理竞争、产生原生 "destroyed mutex" abort——但那个顾虑成立的前提"IME 依赖这个 Activity 常驻"已经在同一批改动里解除了，且真机反复测试确认关闭设置页时 `finish()` 没有再现那个原生崩溃）。

**待验证的假设**：用户提出黑屏可能跟"之前是否在某个用 WebView 渲染输入框的 App 里打过字"相关（IME 跟目标 App 自己的 WebView"打架"）。目前复现路径（切 App / 熄屏一段时间后点 Logo）看起来跟点 Logo 前具体在哪个输入框无关，但样本量还不够排除，下次复现时应记录触发前具体在哪个 App、哪种输入框操作。

对应提交：`ff4194c8`（activity_id 修复）、`fda57eaa`（同批次 wry 诊断 patch + watchdog）、`ecc6f7b0`（关闭设置页改 finish）、`38d43983`（destroy() 替换 close()）、`0b5921f4`（进程自重启兜底），均在 `feature/android-runtime-lifecycle` 分支。

## 重启原因统计代码位置一览（`OpenLessProcessRestartStats`，供交叉验证）

8 个分类，`recordStart()` 调用点：

| key | 文件:行 | 触发条件 |
|-----|---------|---------|
| `main` | `OpenLessApplication.kt:290`（`OpenLessProcessRestartStats.MAIN`，动态 processKey 判断） | 主进程 `Application.onCreate()` 或等价路径执行 |
| `accessibility` | `OpenLessApplication.kt:290`（同上，`processKey == ":accessibility"` 时） | `:accessibility` 子进程启动 |
| `unclean` | `OpenLessApplication.kt:306` | 上一次主进程会话没有正常走到 `OpenLessImeService.onDestroy()`（尽力而为的异常退出信号） |
| `warmup` | `OpenLessBackendWarmupActivity.kt:316`（`launchWarmup()`） | `ensureBackendReady()` 发现后端未就绪或 Activity Context 未注册，发起静默唤醒 |
| `mictap` | `OpenLessImeService.kt:2416`（`toggleDictation()`） | 用户点麦克风时 `isBackendReady()` 为 false（用户可见的"服务未就绪"症状） |
| `sticky` | `OpenLessRuntimeService.kt:24` | `onStartCommand()` 收到 null Intent —— `START_STICKY` 服务被系统杀死后自动重启的官方信号，是"进程真的被杀过"最强的证据 |
| `actkill` | `OpenLessRuntimeService.kt:28` | 收到 `ACTION_RUNTIME_ACTIVITY_DESTROYED`（`OpenLessBackendWarmupActivity.onDestroy()` 发出）——系统回收了宿主 Activity 的窗口，不一定代表进程本身也被杀。同一次调用还会带一个 `reason`（`config`/`finishing`/`os`，`OpenLessBackendWarmupActivity.kt` 的 `onDestroy()` 判断——`finishing` 现在既覆盖用户关闭设置页时的主动 `finishAndRemoveTask()`，也覆盖 `webViewCreationWatchdog` 判定卡死时的自动 `finishAndRemoveTask()`），一并记一条 `actkill_<reason>` 细分计数，跟 `actkill` 本身原子地一起 +1 |
| `rtexit` | `OpenLessRuntimeService.kt:32` | 收到 `ACTION_RUNTIME_EXITED`——Tauri 的 `RunEvent::Exit` 实际触发了，理论上应该始终为 0（`mobile_runtime.rs` 的 `RunEvent::ExitRequested` + `prevent_exit()` 修复如果还生效的话） |
| `stuckwindow` | `OpenLessBackendWarmupActivity.kt`（`restartProcessAsLastResort()`） | "main" WebView 窗口标签永久卡死（见上方 2026-09-22 排查记录），主动自杀重启整个进程 |

所有计数每次 `OpenLessBuildInfo.VERSION` 变化时清零（见上方"安装时间显示"），键盘设置页只展示"今天"的累计值——**但这依赖 `OpenLessApplication.ALL_RESTART_CATEGORIES` 手动维护的白名单**，四个 `actkill_<reason>` 细分 key 加入代码后一度漏掉没同步进这份白名单，导致 `actkill` 总数每次版本号变化都清零、四个细分计数却完全不清零，在同一天内多次调试构建之间越攒越多、跟总数脱节（用户截图实测 `actkill_finishing` 累计到 59，同期 `actkill` 总数只有 16）。已在 1.68 修复（把四个 key 补进白名单）。

## 构建与 CI

**CI（overlay / 无障碍 ADB 测试 APK）** — 合并 v1 麦克风权限 + overlay / 无障碍 manifest，用于真机 ADB 测试完整悬浮窗与无障碍能力（非仅应用内听写）：

```bash
cd openless-all/app
npm ci && npm run build
CI=true npm run tauri -- android init --ci
node scripts/copy-android-scaffolding.mjs
node scripts/merge-android-v1-manifest.mjs
node scripts/merge-android-overlay-manifest.mjs
node scripts/merge-android-shizuku-manifest.mjs
node scripts/patch-android-shizuku-deps.mjs
CI=true npm run tauri:android:build
```

Workflow： [`.github/workflows/android-apk.yml`](../../.github/workflows/android-apk.yml)

**本地 overlay / 无障碍开发（v3）** — 与 CI 相同的 manifest 合并链，使用本地 init / copy 脚本：

```bash
cd openless-all/app
npm run tauri:android:init
npm run copy:android-scaffolding
node scripts/merge-android-v1-manifest.mjs
node scripts/merge-android-overlay-manifest.mjs
node scripts/merge-android-shizuku-manifest.mjs
node scripts/patch-android-shizuku-deps.mjs
npm run tauri:android:build
```

## 相关文档

- [AGENTS.md](../../AGENTS.md) — 真机闪退排查
- [docs/android-mobile-apk-overlay-plan.md](../../docs/android-mobile-apk-overlay-plan.md) — 分阶段产品计划
