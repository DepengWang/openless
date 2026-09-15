package com.openless.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.Context
import android.content.Intent

/**
 * Starts the Tauri/Rust runtime — usually invisibly (warmup/recovery), but
 * also the app's actual launcher target (the manifest's LAUNCHER
 * intent-filter lives here, not on bare MainActivity — see
 * merge-android-overlay-manifest.mjs's moveLauncherIntentFilterToWarmupActivity()
 * for why: a direct launcher tap used to open untracked plain MainActivity,
 * a second Tauri host whose WebView never got anything attached).
 */
class OpenLessBackendWarmupActivity : MainActivity() {
    // Activity 实例化阶段尚未 attach Context，不能访问 Activity.mainLooper。
    private val warmupHandler = Handler(Looper.getMainLooper())
    private val sendToBackground = Runnable {
        if (!settingsRequested && !isFinishing && !isDestroyed) {
            // Tauri/Rust runtime is owned by this Activity. Keep it alive as the
            // single UI/runtime host, but never relaunch the editor's package here:
            // a package launch intent only knows that app's launcher Activity, which
            // for apps like Settings or WeChat mini programs is not the screen the
            // user was actually typing in, and replacing it destroys their context.
            // Moving this task behind the current one preserves the exact
            // Activity/window that requested the IME.
            overridePendingTransition(0, 0)
            moveTaskToBack(true)
            OpenLessImeService.requestInputPanelAfterWarmup(260L)
        }
    }
    private var settingsRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activeInstance = java.lang.ref.WeakReference(this)
        // with_android_env()'s Activity Context registration (see
        // OpenLessNative.nativeRegisterActivityContext()'s doc comment)
        // happens from OpenLessApplication's app-wide ActivityLifecycleCallbacks
        // (matched via `is MainActivity`, which this class extends) rather
        // than here, so it also covers plain MainActivity if anything ever
        // instantiates that directly again.
        //
        // A direct launcher-icon tap arrives here as a plain ACTION_MAIN/
        // CATEGORY_LAUNCHER intent (no EXTRA_SHOW_SETTINGS) — treated the
        // same as an explicit settings request: the user tapped the icon
        // expecting to see the app, not an invisible warmup that vanishes
        // 180ms later.
        val launchedFromLauncher = intent.action == Intent.ACTION_MAIN &&
            intent.hasCategory(Intent.CATEGORY_LAUNCHER)
        // settingsOpenPending covers a cold-start race: openSettings()/
        // openSettingsIfRunning() set it synchronously before ever calling
        // startActivity(), so it is already true here even if this onCreate()
        // actually happened to be triggered by a concurrent, unrelated
        // ensureBackendReady() warmup racing to create the same singleTask
        // instance first (observed on-device: a silent warmup and a
        // launcher-icon tap landing within ~10ms of each other after the
        // OS killed the process in the background) — the alternative,
        // reading only this Intent's own extras, depends on onNewIntent()
        // winning that race, which is not guaranteed.
        settingsRequested = launchedFromLauncher || intent.getBooleanExtra(EXTRA_SHOW_SETTINGS, false) || settingsOpenPending
        settingsOpenPending = false
        // Only when visibly opened for settings: a permission dialog here
        // during the invisible warmup path would get dragged to the
        // background along with this Activity by sendToBackground() 180ms
        // later, before the user could ever answer it. Android never
        // auto-requests POST_NOTIFICATIONS (API 33+) — without asking
        // explicitly at least once, OpenLessRuntimeService's foreground
        // notification stays silently blocked and "Manage notifications"
        // shows as a fixed, non-interactive "don't allow" in Settings,
        // since there is nothing granted to manage.
        if (settingsRequested) {
            requestNotificationPermissionIfNeeded()
        }

        // 不再修改窗口透明度或触摸属性。主 Activity 必须以正常窗口完成
        // Tauri/WebView 初始化，完成后仅退到后台，避免留下黑色/空白窗口状态。
        // Tauri/WebView keeps initializing natively after super.onCreate() returns.
        // Backgrounding this window while that is still in flight has produced a
        // native "destroyed mutex" abort in HWUI's worker pool; suppressing the
        // enter transition avoids extra render work racing with that teardown.
        overridePendingTransition(0, 0)
        warmupHandler.postDelayed(sendToBackground, 180L)
    }

    @Suppress("DEPRECATION", "MissingSuperCall")
    override fun onBackPressed() {
        // This Activity is the single, process-lifetime Tauri/Rust host and must
        // never actually finish() while the process is alive: finishing destroys
        // the window Surface (unlike moveTaskToBack, which only hides it), and
        // that race with HWUI's worker-pool teardown is what produces the native
        // "destroyed mutex" abort. The default back behavior would finish() this
        // Activity once there is no more back-stack, so always background it
        // instead — skipping super.onBackPressed() is intentional here.
        overridePendingTransition(0, 0)
        moveTaskToBack(true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val launchedFromLauncher = intent.action == Intent.ACTION_MAIN &&
            intent.hasCategory(Intent.CATEGORY_LAUNCHER)
        if (launchedFromLauncher || intent.getBooleanExtra(EXTRA_SHOW_SETTINGS, false) || settingsOpenPending) {
            settingsRequested = true
            settingsOpenPending = false
            warmupHandler.removeCallbacks(sendToBackground)
            // Covers openSettingsIfRunning() bringing an already-alive
            // instance forward, and the launcher icon being tapped again
            // while this Activity is already alive (singleTask redelivers
            // via onNewIntent instead of a fresh onCreate) — either way,
            // onCreate()'s own call to this never runs again for those
            // cases, so this is the only other place a visible moment happens.
            requestNotificationPermissionIfNeeded()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_POST_NOTIFICATIONS)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_POST_NOTIFICATIONS) return
        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        android.util.Log.i("OpenLessBackendWarmupActivity", "POST_NOTIFICATIONS result granted=$granted")
    }

    override fun onDestroy() {
        warmupHandler.removeCallbacks(sendToBackground)
        if (activeInstance?.get() === this) {
            activeInstance = null
        }
        // Only reachable here, not from onPause()/onStop(): this class never
        // calls finish() on itself (see onBackPressed()), so onDestroy()
        // firing means the *system* reclaimed this task — e.g. memory
        // pressure, or "don't keep activities" — while the process (and
        // OpenLessRuntimeService, its supervisor) is still alive. Routed
        // through the service rather than calling ensureBackendReady()
        // directly here: the service is what decides whether/when to
        // relaunch, this Activity dying is just one input to that decision.
        OpenLessRuntimeService.notifyRuntimeActivityDestroyed(applicationContext)
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var activeInstance: java.lang.ref.WeakReference<OpenLessBackendWarmupActivity>? = null

        private const val EXTRA_SHOW_SETTINGS = "com.openless.app.extra.SHOW_SETTINGS"
        private const val REQUEST_POST_NOTIFICATIONS = 9102

        // Set synchronously by openSettings()/openSettingsIfRunning() BEFORE
        // startActivity() is ever called, and consumed by onCreate()/
        // onNewIntent() — a settings request is "in flight" the instant one
        // of those functions is called, not only once its Intent happens to
        // be delivered. Closes a race observed on-device: a concurrent,
        // unrelated ensureBackendReady() warmup can create/reuse this same
        // singleTask instance a few milliseconds earlier (e.g. right after
        // the OS killed the process in the background and the user's tap
        // triggers a cold start), and depending purely on whose Intent
        // reaches onCreate()/onNewIntent() first left the 180ms
        // sendToBackground() timer free to fire before the real settings
        // request ever got a chance to cancel it — the window would flash
        // and vanish instead of staying open.
        @Volatile
        private var settingsOpenPending = false

        /** The single Tauri host is still alive even while its task is in the background. */
        fun isRunning(): Boolean {
            val activity = activeInstance?.get() ?: return false
            return !activity.isFinishing && !activity.isDestroyed
        }

        /** Bring the existing Tauri host forward instead of creating a black second host. */
        fun openSettingsIfRunning(context: Context): Boolean {
            val activity = activeInstance?.get() ?: return false
            if (activity.isFinishing || activity.isDestroyed) return false
            settingsOpenPending = true
            // Also applied directly to the live instance right here, not
            // left to onNewIntent() delivery timing: this is the common
            // case (host already running) and the one most exposed to the
            // race described above, since the instance — and its pending
            // sendToBackground() timer — already exist by the time this runs.
            activity.settingsRequested = true
            activity.warmupHandler.removeCallbacks(activity.sendToBackground)
            context.startActivity(Intent(context, OpenLessBackendWarmupActivity::class.java).apply {
                putExtra(EXTRA_SHOW_SETTINGS, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            })
            return true
        }

        /**
         * Show settings, reusing the existing Tauri host if one is alive. Only
         * starts a fresh Activity when none exists yet. Always targets this
         * class (never the bare MainActivity) so there is ever only one
         * tracked Tauri host, regardless of whether it was created for warmup
         * or for settings — starting MainActivity directly here would spin up
         * an untracked second host and re-run Tauri/Rust setup from scratch.
         */
        fun openSettings(context: Context) {
            settingsOpenPending = true
            if (openSettingsIfRunning(context)) return
            context.startActivity(Intent(context, OpenLessBackendWarmupActivity::class.java).apply {
                putExtra(EXTRA_SHOW_SETTINGS, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            })
        }

        private const val BACKEND_WARMUP_ATTEMPT_KEY = "backend_warmup_attempt_wall_time"
        private const val BACKEND_WARMUP_RETRY_DELAY_MS = 30_000L
        @Volatile
        private var lastWarmupAttemptElapsed = 0L

        /**
         * Warms the Tauri/Rust backend if it isn't ready yet, from whatever
         * Context happens to notice first — not just the IME service reacting
         * to a focused text field. Called from OpenLessImeService.onCreate()
         * (the original path) and now also from OpenLessRuntimeService's
         * START_STICKY restart, so a system-triggered service restart (which
         * can happen before the user ever taps a field again) gets a chance to
         * finish this warmup — and the disruptive foreground-stealing
         * Activity launch it requires — before that tap happens, instead of
         * only ever reacting to it.
         *
         * The 30s persisted cooldown (SharedPreferences, survives a process
         * crash) is shared across every caller, so calling this from more
         * places never launches the warmup Activity more often than before —
         * it only widens the chance that one of those launches lands before
         * the user is looking at some other app's text field.
         */
        fun ensureBackendReady(context: Context) {
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastWarmupAttemptElapsed < 5_000L) return
            if (isRunning()) return
            val backendError = try {
                OpenLessNative.requireBackendContract()
                null
            } catch (error: Throwable) {
                error
            }
            if (backendError != null) {
                launchWarmup(context, now, backendError)
                return
            }
            // The Rust backend can stay perfectly healthy for a long time
            // after its last registered Activity is destroyed — that
            // Activity dying is meant to be survivable (Phase 1/2's whole
            // point). But nothing else ever notices that gap and relaunches
            // one, since requireBackendContract() only checks whether the
            // backend itself is running: every notify_capsule_state() call
            // (dictation/waveform status updates) is left permanently
            // failing until something does. Treated the same as "backend
            // not ready" here so it goes through the same relaunch + cooldown.
            if (!OpenLessNative.nativeHasRegisteredActivityContext()) {
                launchWarmup(context, now, IllegalStateException("backend healthy but no Activity registered for JNI notifications"))
            }
        }

        private fun launchWarmup(context: Context, nowElapsed: Long, cause: Throwable) {
            val runtimePrefs = context.getSharedPreferences("openless_runtime", Context.MODE_PRIVATE)
            val wallNow = System.currentTimeMillis()
            val lastAttempt = runtimePrefs.getLong(BACKEND_WARMUP_ATTEMPT_KEY, 0L)
            if (wallNow >= lastAttempt && wallNow - lastAttempt < BACKEND_WARMUP_RETRY_DELAY_MS) return
            lastWarmupAttemptElapsed = nowElapsed
            runtimePrefs.edit().putLong(BACKEND_WARMUP_ATTEMPT_KEY, wallNow).apply()
            android.util.Log.i("OpenLessBackendWarmupActivity", "backend is not ready; launching warmup", cause)
            OpenLessProcessRestartStats(context, "warmup").recordStart()
            Handler(Looper.getMainLooper()).postDelayed({
                // Re-checked here, not just by the caller 120ms ago: something
                // else (a launcher-icon tap, a Logo tap) can have already
                // created/resumed the host in the meantime. Without this,
                // this launch still fires and delivers a no-extras Intent to
                // that same singleTask instance — harmless by itself, but it
                // was one of the two ingredients (together with imprecise
                // settingsRequested timing) behind an on-device black-screen
                // race: a genuine settings-open request and this silent
                // warmup landing within milliseconds of each other right
                // after the OS killed the process in the background.
                if (isRunning()) return@postDelayed
                runCatching {
                    context.startActivity(Intent(context, OpenLessBackendWarmupActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                        addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    })
                }.onFailure { launchError ->
                    android.util.Log.w("OpenLessBackendWarmupActivity", "failed to launch warmup", launchError)
                }
            }, 120L)
        }
    }
}
