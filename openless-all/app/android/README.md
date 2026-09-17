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
| 编辑/纠正结果改为写入全局词典，不再自动生成纠错规则 | 之前"编辑弹窗 checkbox"/剪贴板划动"加入纠错"两处入口，都是自动调用 `nativeAddCorrectionRule()` 往 `CorrectionRuleStore` 写一条 pattern→replacement 规则；现在改为调用新增的 `nativeAddVocabularyWord(phrase)`（`native_bridge.rs::spawn_add_vocabulary_word()`，走 `OpenLessBackend::add_vocabulary_if_absent()`，与桌面端 `add_vocab` 命令同一个全局 Dictionary 存储，用于提升 ASR/LLM 转换准确率），重复纠正同一个词不会堆积重复条目；纠错规则（`CorrectionRule`）今后改为纯手工维护，不再由这些 Android 入口自动生成。配套新增 `nativeVocabularyPhrases()`/`nativeRemoveVocabularyWord()`（对应旧的 `nativeCorrectionRulePatterns()`/`nativeRemoveCorrectionRule()`），剪贴板划动区的高亮判断和"加入/移出"文案随之改为"加入词典/移出词典"；`OpenLessImeService.kt` 内部字段 `addCorrectionRuleForEdit` 相应改名为 `addToDictionaryForEdit`，勾选框文案改为"同时加入词典（提升识别准确率）" |

开发流程：每次改动后用 `npm run copy:android-scaffolding` 同步 → `gradlew app:assembleArm64Debug -x app:rustBuildArm64Debug`（Kotlin-only 改动跳过 Rust 重编译）→ `adb install -r` 装机 → 通过 `adb exec-out screencap` 或用户反馈截图核对真机效果；涉及尺寸争议时用 `adb shell wm density` + 实测 px 反推 dp，避免凭空猜测布局问题。

## 设置页黑屏排查记录（供交叉验证）

**现象**：长按 Logo 或点击应用图标打开设置页，`OpenLessBackendWarmupActivity` 窗口本身能弹出、获得焦点，但内容区域是纯黑，没有任何 UI、没有加载动画。期间语音听写、笔画输入完全正常，说明 Rust 后端和 IME 进程本身健康，问题局限在这一个 Activity 的 WebView 内容渲染上。

排查过程中确认了两层互相独立的原因，都已修复：

**原因一：`settingsRequested` 时序竞争**（commit 待提交，见 `OpenLessBackendWarmupActivity.kt`）。这个 Activity 平时绝大多数时候是"静默唤醒"用途——`onCreate()` 里 `warmupHandler.postDelayed(sendToBackground, 180L)` 会在 180ms 后自动 `moveTaskToBack()`，除非 `settingsRequested` 为 true。`ensureBackendReady()` 的自动静默唤醒和用户主动打开设置（图标/Logo）有概率在几毫秒内先后对同一个 `singleTask` Activity 发起 `startActivity()`；`settingsRequested` 原来完全依赖 `onCreate()`/`onNewIntent()` 收到的 Intent 内容判断，如果静默唤醒先创建了实例（`settingsRequested=false`）并挂上 180ms 定时器，用户的真实打开请求的 `onNewIntent()` 没能足够快地取消这个定时器，窗口就会"一闪而过"被收回后台——真机 logcat 里能看到 `onSurfaceShowChange show=true` 之后约 200ms 出现 `show=false`，与 180ms 定时器耗时吻合。

修复：新增 `settingsOpenPending`（`companion object` 里的 `@Volatile` 标记），由 `openSettings()`/`openSettingsIfRunning()` 在调用 `startActivity()` **之前**同步置位；`openSettingsIfRunning()` 还直接对已存活的 `activity` 实例同步设置 `settingsRequested = true` 并 `removeCallbacks(sendToBackground)`，不依赖 Intent 投递时序。同时给 `launchWarmup()` 内部 120ms 延迟后的 `startActivity()` 调用加了 `isRunning()` 复查，避免延迟期间已经有别的入口把 Activity 启动起来后仍旧重复发起静默唤醒。修复后 logcat 确认窗口能稳定停在 `RESUMED`/`isVisibleRequested=true`，不再自动隐藏。

**原因二：WebView 渲染进程在后台被系统冻结，恢复前台后没有重新合成画面**。上面这层修复只解决了"窗口是否留在前台"，窗口留住之后用户反馈仍然是黑屏，用 `adb exec-out screencap` 截图确认是纯黑（不是白屏/异常颜色/局部渲染），排除了布局或主题配色问题。由于这个 Activity 设计上几乎全部时间都处于 `moveTaskToBack()` 之后的后台状态，其宿主的 WebView 渲染子进程（`com.google.android.webview:sandboxed_process0`，运行在本应用 UID 下）是安卓"后台进程冻结"省电机制（Android 12+ App Freezer，各 OEM 定制系统通常更激进）的典型目标；真机 logcat 里能看到该子进程被 `Async freezing`/`received async transactions while frozen` 又 `sync unfroze`。冻结解除时 Chromium 合成器没有必然重新提交一帧画面，窗口本身可以正常获得焦点、绘制，但内部 WebView 表面停留在最后一次（或从未）合成的空白/黑色状态。

修复：`OpenLessBackendWarmupActivity` 新增 `onWebViewCreate(webView)` 覆写（`WryActivity` 已有的钩子，此前未使用）保存 WebView 引用，新增 `reloadWebViewForSettings()`，在 `onCreate()`/`onNewIntent()` 两处 `settingsRequested` 变为 true（即真正为用户打开设置，而非静默唤醒）时调用 `webViewRef?.post { webViewRef?.reload() }`，强制 Chromium 从头重新加载并合成一次画面，不依赖冻结/解冻这套系统机制自己恢复。

**注意**：排查早期还发现过一次 `adb install -r` 重装导致的一次性黑屏（`ActivityThread: Package [com.openless.app] reported as REPLACED, but missing application info. Assuming REMOVED.`），这是重装时旧 WebView 渲染进程还没被系统完全回收造成的开发流程副作用，`adb shell am force-stop com.openless.app` 可以清掉，不是代码问题，真实用户走应用商店/App 内更新不会遇到。

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
| `actkill` | `OpenLessRuntimeService.kt:28` | 收到 `ACTION_RUNTIME_ACTIVITY_DESTROYED`（`OpenLessBackendWarmupActivity.onDestroy()` 发出）——系统回收了宿主 Activity 的窗口，不一定代表进程本身也被杀 |
| `rtexit` | `OpenLessRuntimeService.kt:32` | 收到 `ACTION_RUNTIME_EXITED`——Tauri 的 `RunEvent::Exit` 实际触发了，理论上应该始终为 0（`mobile_runtime.rs` 的 `RunEvent::ExitRequested` + `prevent_exit()` 修复如果还生效的话） |

所有计数每次 `OpenLessBuildInfo.VERSION` 变化时清零（见上方"安装时间显示"），键盘设置页只展示"今天"的累计值。

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
