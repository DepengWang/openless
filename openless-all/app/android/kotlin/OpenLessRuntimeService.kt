package com.openless.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log

/** Keeps the OpenLess process alive while the system IME is active, without showing an overlay. */
class OpenLessRuntimeService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RUNTIME_ACTIVITY_DESTROYED) {
            Log.w(TAG, "runtime host activity was destroyed by the system; re-checking backend")
        }
        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    } else {
                        0
                    },
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (error: Throwable) {
            Log.e(TAG, "failed to promote IME runtime service", error)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        // START_STICKY means the system can restart this service on its own —
        // e.g. after killing it under memory pressure — with no IME
        // interaction involved at all. Piggyback the backend warmup check on
        // every start (including those system-triggered restarts) so a cold
        // backend has a chance to finish warming up (and the foreground-
        // stealing Activity that requires goes away) before the user is next
        // looking at some other app's text field, instead of only ever
        // reacting to that tap.
        OpenLessBackendWarmupActivity.ensureBackendReady(this)
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val channelId = "openless_ime_runtime"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "OpenLess 输入法服务",
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentTitle("OpenLess 输入法")
                .setContentText("输入法面板已就绪")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("OpenLess 输入法")
                .setContentText("输入法面板已就绪")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .build()
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 42002
        private const val TAG = "OpenLessRuntimeService"
        private const val ACTION_RUNTIME_ACTIVITY_DESTROYED = "com.openless.app.action.RUNTIME_ACTIVITY_DESTROYED"

        /**
         * Called from OpenLessBackendWarmupActivity.onDestroy() — this
         * service is the supervisor that decides whether/when to relaunch
         * the runtime host Activity (via ensureBackendReady(), already run
         * unconditionally at the end of onStartCommand()), not the dying
         * Activity deciding for itself. The action is only for logging here
         * today; onStartCommand() already re-checks on every start
         * regardless of why it was started.
         */
        fun notifyRuntimeActivityDestroyed(context: android.content.Context) {
            context.startService(
                Intent(context, OpenLessRuntimeService::class.java).setAction(ACTION_RUNTIME_ACTIVITY_DESTROYED),
            )
        }
    }
}
