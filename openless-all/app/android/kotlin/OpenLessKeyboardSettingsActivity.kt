package com.openless.app

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

/**
 * Full-screen native settings window opened by long-pressing the OpenLess
 * logo in the keyboard panels. Keyboard-only preferences that don't need the
 * full WebView app live here. Framework only for now (vibration intensity
 * and duration) — more rows get appended to `content` in buildContent() as
 * they're added.
 */
class OpenLessKeyboardSettingsActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("openless_ime_ui", Context.MODE_PRIVATE) }
    private val englishUi by lazy {
        val locale = prefs.getString("locale", null) ?: resources.configuration.locales[0].toLanguageTag()
        !locale.startsWith("zh", ignoreCase = true)
    }

    private fun ui(zh: String, en: String) = if (englishUi) en else zh
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /** Same "which theme is the keyboard actually showing" logic as OpenLessImeService.isDarkTheme, so this screen matches whatever the user is looking at when they long-press the Logo to get here. */
    private val isDarkTheme: Boolean
        get() = when (prefs.getString("theme_mode", null)) {
            "light" -> false
            "dark" -> true
            else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_NO
        }

    private fun tone(dark: Int, light: Int): Int = if (isDarkTheme) dark else light

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
    }

    private fun buildContent(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(tone(Color.rgb(30, 30, 30), Color.rgb(245, 245, 247)))
        }

        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), dp(16), dp(8))
        }
        header.addView(
            TextView(this).apply {
                text = "←"
                textSize = 22f
                setTextColor(tone(Color.WHITE, Color.rgb(30, 30, 34)))
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(8), dp(16), dp(8))
                isClickable = true
                setOnClickListener { finish() }
            },
        )
        header.addView(
            TextView(this).apply {
                text = ui("键盘设置", "Keyboard settings")
                textSize = 18f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(tone(Color.WHITE, Color.rgb(30, 30, 34)))
            },
        )
        root.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(20))
        }
        scroll.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        content.addView(sectionLabel(ui("震动反馈", "Haptic feedback")))

        val enabledRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        enabledRow.addView(
            TextView(this).apply {
                text = ui("按键震动", "Key vibration")
                textSize = 15f
                setTextColor(tone(Color.rgb(220, 220, 220), Color.rgb(40, 40, 44)))
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        enabledRow.addView(
            Switch(this).apply {
                isChecked = prefs.getBoolean("key_haptic_enabled", true)
                setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("key_haptic_enabled", checked).apply() }
            },
        )
        content.addView(
            enabledRow,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(14)
            },
        )

        content.addView(sectionLabel(ui("英文键盘", "English keyboard")))
        val englishSuggestionsRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        englishSuggestionsRow.addView(
            TextView(this).apply {
                text = ui("英文单词提示", "English word suggestions")
                textSize = 15f
                setTextColor(tone(Color.rgb(220, 220, 220), Color.rgb(40, 40, 44)))
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        englishSuggestionsRow.addView(
            Switch(this).apply {
                isChecked = prefs.getBoolean("english_suggestions_enabled", true)
                setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("english_suggestions_enabled", checked).apply() }
            },
        )
        content.addView(
            englishSuggestionsRow,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(14)
            },
        )

        // Amplitude's 255 ceiling is Android's own VibrationEffect max, not a
        // choice made here — the hardware/API can't go any stronger than
        // that regardless of what this slider allows. Duration's ceiling
        // started at 500ms, halved to 250, then halved again to 125 so the
        // same slider width covers a quarter of the original range, for
        // finer-grained adjustment.
        var currentAmplitude = prefs.getInt("key_haptic_amplitude", 55).coerceIn(1, 255)
        var currentDurationMs = prefs.getLong("key_haptic_duration_ms", 12L).toInt().coerceIn(1, 125)
        // No separate test button — letting go of either slider fires one
        // vibration with the values as they now stand, so adjusting and
        // feeling the result is a single motion.
        content.addView(
            sliderRow(
                label = ui("震动强度（系统上限）", "Intensity (platform ceiling)"),
                min = 1,
                max = 255,
                current = currentAmplitude,
                onChange = { value ->
                    currentAmplitude = value
                    prefs.edit().putInt("key_haptic_amplitude", value).apply()
                },
                onRelease = { fireTestVibration(currentAmplitude, currentDurationMs) },
            ),
        )
        content.addView(
            sliderRow(
                label = ui("震动时长", "Duration"),
                min = 1,
                max = 125,
                current = currentDurationMs,
                onChange = { value ->
                    currentDurationMs = value
                    prefs.edit().putLong("key_haptic_duration_ms", value.toLong()).apply()
                },
                onRelease = { fireTestVibration(currentAmplitude, currentDurationMs) },
            ),
        )

        content.addView(sectionLabel(ui("进程重启统计（今天）", "Process restarts (today)")))
        // Short, purposefully un-translated keys (not meant to be pretty —
        // meant to be pasted into a screenshot and read back verbatim).
        // All reset to 0 whenever OpenLessBuildInfo.VERSION changes (see
        // OpenLessApplication.resetRestartStatsOnVersionBump()), so these
        // are always "since this build was installed", not lifetime totals:
        //   main/access  - raw restarts of the main / :accessibility process
        //   sticky       - OpenLessRuntimeService.onStartCommand() got a
        //                  null Intent: Android's own restart-after-death
        //                  signal for a START_STICKY service, the strongest
        //                  evidence the whole process was actually killed
        //   warmup       - OpenLessBackendWarmupActivity.ensureBackendReady()
        //                  found the backend not registered and launched
        //                  the warmup Activity
        //   mictap       - user tapped the mic and toggleDictation() found
        //                  the backend not ready (the user-visible symptom)
        //   actkill      - OpenLessBackendWarmupActivity.onDestroy() fired
        //                  (system reclaimed the host Activity's window;
        //                  doesn't necessarily mean the process itself died)
        //   rtexit       - Tauri's RunEvent::Exit actually fired despite
        //                  ExitRequested being prevented (see
        //                  mobile_runtime.rs) — should stay at 0 if that fix
        //                  is holding
        //   unclean      - previous main-process session never reached
        //                  OpenLessImeService.onDestroy() (best-effort
        //                  crash/force-stop signal, can't tell those apart)
        // Chinese gloss for each key — just enough to read at a glance
        // without cross-referencing the doc comment above.
        val restartCategories = listOf(
            Triple(OpenLessProcessRestartStats.MAIN, "main", "主进程"),
            Triple(OpenLessProcessRestartStats.ACCESSIBILITY, "access", "无障碍进程"),
            Triple("sticky", "sticky", "系统杀后恢复"),
            Triple("warmup", "warmup", "后端唤醒"),
            Triple("mictap", "mictap", "点击时未就绪"),
            Triple("actkill", "actkill", "界面被系统回收"),
            Triple("rtexit", "rtexit", "后端异常退出"),
            Triple("unclean", "unclean", "上次异常退出"),
            Triple("heartbeat", "heartbeat", "心跳自愈"),
        )
        val monospace = android.graphics.Typeface.MONOSPACE
        for ((key, label, gloss) in restartCategories) {
            content.addView(
                TextView(this).apply {
                    val count = OpenLessProcessRestartStats(this@OpenLessKeyboardSettingsActivity, key).today()
                    text = label.padEnd(10) + count.toString().padEnd(4) + gloss
                    textSize = 13f
                    typeface = monospace
                    setTextColor(tone(Color.rgb(200, 200, 200), Color.rgb(70, 70, 75)))
                },
            )
        }
        content.addView(View(this), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(14)))

        content.addView(sectionLabel(ui("个人偏好数据", "Personal preference data")))
        val personalFrequency = StrokeUserFrequency(this)
        content.addView(
            TextView(this).apply {
                text = ui(
                    "已记录 ${personalFrequency.size()} / ${personalFrequency.capacity()} 条",
                    "${personalFrequency.size()} / ${personalFrequency.capacity()} entries recorded",
                )
                textSize = 14f
                setTextColor(tone(Color.rgb(200, 200, 200), Color.rgb(70, 70, 75)))
            },
        )

        // build_first_seen_wall_time is written by OpenLessApplication's
        // resetRestartStatsOnVersionBump() at the exact moment it last
        // zeroed the restart-cause counters below — i.e. "counting since
        // when" for whatever counts are on screen right now, so a
        // screenshot of this page carries both together.
        val buildFirstSeenAt = getSharedPreferences("openless_runtime", Context.MODE_PRIVATE)
            .getLong("build_first_seen_wall_time", 0L)
        val installedAtText = if (buildFirstSeenAt > 0L) {
            android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", buildFirstSeenAt)
        } else {
            "?"
        }
        content.addView(
            TextView(this).apply {
                text = "${ui("构建版本", "Build")} ${OpenLessBuildInfo.VERSION}  ${ui("安装于", "installed")} $installedAtText"
                textSize = 11f
                typeface = monospace
                setTextColor(tone(Color.rgb(120, 120, 120), Color.rgb(150, 150, 155)))
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(16)
            },
        )

        return root
    }

    /** Fires a one-shot vibration with the sliders' current (already-saved) values, so a change is felt immediately. */
    private fun fireTestVibration(amplitude: Int, durationMs: Int) {
        runCatching {
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(durationMs.toLong(), amplitude))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs.toLong())
            }
        }
    }

    private fun sectionLabel(text: String): View = TextView(this).apply {
        this.text = text
        textSize = 12f
        setTextColor(tone(Color.rgb(150, 150, 150), Color.rgb(110, 110, 115)))
        setPadding(0, 0, 0, dp(8))
    }

    /** One labeled slider row. Reusable as more settings rows get added here. */
    private fun sliderRow(label: String, min: Int, max: Int, current: Int, onChange: (Int) -> Unit, onRelease: (() -> Unit)? = null): View {
        // Computed before building the SeekBar itself, since inside that
        // view's own apply{} block an unqualified "max" would resolve to
        // SeekBar's own max property (shadowing this function's max: Int
        // parameter), not the value intended here.
        val range = (max - min).coerceAtLeast(1)
        val initialProgress = (current - min).coerceIn(0, range)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(14)
            }
        }
        val labelView = TextView(this).apply {
            text = "$label · Max $max Set:$current"
            textSize = 14f
            setTextColor(tone(Color.rgb(200, 200, 200), Color.rgb(70, 70, 75)))
        }
        row.addView(labelView)
        row.addView(
            SeekBar(this).apply {
                this.max = range
                this.progress = initialProgress
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            val value = progress + min
                            labelView.text = "$label · Max $max Set:$value"
                            onChange(value)
                        }
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        onRelease?.invoke()
                    }
                })
            },
        )
        return row
    }
}
