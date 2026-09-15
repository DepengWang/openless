package com.openless.app

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.net.Uri
import android.util.Log

/** Registers activity lifecycle hooks for overlay background trigger mode. */
class OpenLessApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        OpenLessAppContext.initialize(this)
        if (isMainProcess()) {
            OpenLessShizukuBridge.initialize()
        }
        recordProcessRestart()
        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) =
                    Unit

                override fun onActivityStarted(activity: Activity) {
                    if (activity.javaClass.name.endsWith("MainActivity")) {
                        maybeRequestBatteryOptimizationExemption(activity)
                        maybeRequestNotificationPermission(activity)
                        maybeHideOverlayOnForeground()
                    }
                }

                override fun onActivityResumed(activity: Activity) {
                    // Settings are always opened through
                    // OpenLessBackendWarmupActivity (a MainActivity subclass),
                    // never MainActivity itself directly — an exact class
                    // check here meant this never matched in practice, so the
                    // interface-language poll never actually ran and the
                    // mirrored locale pref went stale at whatever it was on
                    // first install.
                    if (activity is MainActivity) {
                        watchWebViewMirroredState(activity)
                    }
                }

                override fun onActivityPaused(activity: Activity) {
                    if (activity is MainActivity) {
                        readInterfaceLanguage(activity)
                        readInterfaceTheme(activity)
                        localeHandler.removeCallbacksAndMessages(null)
                    }
                }

                override fun onActivityStopped(activity: Activity) {
                    if (activity.javaClass.name.endsWith("MainActivity")) {
                        maybeShowOverlayOnBackground()
                    }
                }

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) =
                    Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            }
        )
    }

    private fun maybeShowOverlayOnBackground() {
        val configured = configuredOverlayTriggerMode()
        val shouldShow = configured == "background" || configured == "always"
        if (!shouldShow) {
            return
        }
        if (!canDrawOverlays()) {
            return
        }
        sendOverlayAction(OpenLessOverlayService.ACTION_SHOW)
    }

    private fun maybeHideOverlayOnForeground() {
        if (configuredOverlayTriggerMode() == "always") {
            if (canDrawOverlays()) {
                sendOverlayAction(OpenLessOverlayService.ACTION_SHOW)
            }
            return
        }
        sendOverlayAction(OpenLessOverlayService.ACTION_HIDE)
    }

    private fun configuredOverlayTriggerMode(): String {
        return OpenLessAndroidPreferences.overlayTriggerMode(this) ?: "background"
    }

    private fun canDrawOverlays(): Boolean {
        return OpenLessPermissionBridge.canDrawOverlaysSafely(this)
    }

    private fun sendOverlayAction(action: String) {
        try {
            startService(
                Intent(this, OpenLessOverlayService::class.java).apply {
                    this.action = action
                }
            )
        } catch (error: Throwable) {
            Log.w(TAG, "overlay action failed: $action", error)
        }
    }

    // The interface language lives in the Tauri WebView, not preferences.json.
    // Mirror only that setting for the native IME; never read editor content.
    private val localeHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun watchWebViewMirroredState(activity: Activity) {
        localeHandler.removeCallbacksAndMessages(null)
        val poll = object : Runnable {
            override fun run() {
                if (activity.isFinishing || activity.isDestroyed) return
                readInterfaceLanguage(activity)
                readInterfaceTheme(activity)
                localeHandler.postDelayed(this, 500L)
            }
        }
        localeHandler.post(poll)
    }

    private fun readInterfaceLanguage(activity: Activity) {
        fun findWebView(view: android.view.View): android.webkit.WebView? {
            if (view is android.webkit.WebView) return view
            if (view is android.view.ViewGroup) {
                for (index in 0 until view.childCount) {
                    findWebView(view.getChildAt(index))?.let { return it }
                }
            }
            return null
        }
        val webView = findWebView(activity.window.decorView) ?: return
        webView.evaluateJavascript(
            "(function(){return localStorage.getItem('ol.locale') || document.documentElement.lang || navigator.language;})()",
        ) { result ->
            val locale = result.trim('"').lowercase(java.util.Locale.ROOT)
            if (!locale.matches(Regex("[a-z]{2,3}(-[a-z0-9]{2,8})*"))) return@evaluateJavascript
            val prefs = getSharedPreferences("openless_ime_ui", MODE_PRIVATE)
            if (prefs.getString("locale", null) != locale) {
                prefs.edit().putString("locale", locale).apply()
            }
        }
    }

    // The keyboard's light/dark palette follows the app's own theme setting
    // (Settings > Appearance), not the raw OS setting. Read directly from
    // the same two sources themeMode.ts's resolveTheme() uses (the stored
    // 'ol.theme' preference, falling back to prefers-color-scheme for
    // "system") instead of the `data-ol-theme` DOM attribute it derives
    // from: the attribute only reflects reality once applyThemeMode() has
    // actually run in this tick, and a poll landing mid-render/mid-navigation
    // could read it before that — which showed up as the keyboard flashing
    // the wrong theme until the next rebuild "self-corrected" it. localStorage
    // and matchMedia are queryable immediately regardless of render timing.
    private fun readInterfaceTheme(activity: Activity) {
        fun findWebView(view: android.view.View): android.webkit.WebView? {
            if (view is android.webkit.WebView) return view
            if (view is android.view.ViewGroup) {
                for (index in 0 until view.childCount) {
                    findWebView(view.getChildAt(index))?.let { return it }
                }
            }
            return null
        }
        val webView = findWebView(activity.window.decorView) ?: return
        webView.evaluateJavascript(
            """
            (function(){
                try {
                    var pref = window.localStorage.getItem('ol.theme');
                    if (pref !== 'light' && pref !== 'dark' && pref !== 'system') pref = 'system';
                    if (pref === 'light') return 'light';
                    if (pref === 'dark') return 'dark';
                    return (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) ? 'dark' : 'light';
                } catch (e) {
                    return 'unknown';
                }
            })()
            """.trimIndent(),
        ) { result ->
            val theme = result.trim('"')
            if (theme != "dark" && theme != "light") return@evaluateJavascript
            val prefs = getSharedPreferences("openless_ime_ui", MODE_PRIVATE)
            if (prefs.getString("theme_mode", null) != theme) {
                prefs.edit().putString("theme_mode", theme).apply()
            }
        }
    }

    private fun maybeRequestBatteryOptimizationExemption(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val power = getSystemService(POWER_SERVICE) as? PowerManager ?: return
        // Re-checked against live system state every time, not gated by a
        // one-shot "already prompted" flag: some OEMs (OnePlus/ColorOS
        // observed) silently revert this exemption back to "optimized" on
        // their own, and a one-time flag would then never prompt again even
        // though the app is no longer actually exempt.
        if (power.isIgnoringBatteryOptimizations(packageName)) return
        val prefs = getSharedPreferences("openless_runtime", MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastPrompt = prefs.getLong("battery_optimization_prompted_at", 0L)
        if (now >= lastPrompt && now - lastPrompt < BATTERY_PROMPT_COOLDOWN_MS) return
        prefs.edit().putLong("battery_optimization_prompted_at", now).apply()
        runCatching {
            activity.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName"),
                )
            )
        }.onFailure { error ->
            Log.w(TAG, "battery optimization exemption request failed", error)
        }
    }

    // POST_NOTIFICATIONS (API 33+) is never auto-granted, and
    // OpenLessBackendWarmupActivity only asks for it when the settings UI is
    // opened explicitly — a user who only ever invokes the IME via the
    // keyboard switcher, without ever tapping the launcher icon, could go
    // through that path forever without the request ever firing. Asking
    // here too, on any real (launcher) app open, catches that case.
    private fun maybeRequestNotificationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching {
            activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_POST_NOTIFICATIONS)
        }.onFailure { error ->
            Log.w(TAG, "notification permission request failed", error)
        }
    }

    private fun isMainProcess(): Boolean {
        val processName = currentProcessName() ?: return true
        return processName == packageName
    }

    // OpenLessApplication.onCreate() runs once per OS process this
    // application object is forked into — not just the main one — so this
    // is the one place both the main process (IME + Tauri backend) and the
    // separate ":accessibility" process (OpenLessAccessibilityService) can
    // each be counted under their own key. Any other/unexpected process
    // name is intentionally left unrecorded rather than guessed at.
    private fun recordProcessRestart() {
        val processKey = when (currentProcessName() ?: packageName) {
            packageName -> OpenLessProcessRestartStats.MAIN
            "$packageName:accessibility" -> OpenLessProcessRestartStats.ACCESSIBILITY
            else -> return
        }
        OpenLessProcessRestartStats(this, processKey).recordStart()
        if (processKey == OpenLessProcessRestartStats.MAIN) {
            recordUncleanShutdownIfAny()
        }
    }

    // Best-effort "did the previous main-process session end cleanly"
    // check: OpenLessImeService.onDestroy() clears "session_alive" on any
    // ordinary teardown (keyboard switched away from, app force-stopped).
    // An abrupt process kill — native crash, OOM — skips onDestroy()
    // entirely and leaves it set, so finding it still set here means the
    // previous run did not end cleanly. Can't tell a crash apart from a
    // deliberate force-stop this way, but both are worth surfacing.
    private fun recordUncleanShutdownIfAny() {
        val prefs = getSharedPreferences("openless_runtime", MODE_PRIVATE)
        if (prefs.getBoolean("session_alive", false)) {
            OpenLessProcessRestartStats(this, "unclean").recordStart()
        }
        prefs.edit().putBoolean("session_alive", true).apply()
    }

    private fun currentProcessName(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName()
        }
        val pid = android.os.Process.myPid()
        val activityManager = getSystemService(ACTIVITY_SERVICE) as? ActivityManager ?: return null
        return activityManager.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName
    }

    companion object {
        private const val TAG = "OpenLessApplication"
        private const val BATTERY_PROMPT_COOLDOWN_MS = 3L * 24 * 60 * 60 * 1000
        private const val REQUEST_POST_NOTIFICATIONS = 9103
    }
}
