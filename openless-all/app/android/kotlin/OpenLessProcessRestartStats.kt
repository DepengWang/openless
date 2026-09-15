package com.openless.app

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Per-day count of how many times one specific OS process has (re)started,
 * kept for the last [RETAIN_DAYS] days. A cold process start looks the same
 * here whether it's the first launch of the day or the OS/OEM force-killing
 * and restarting it — that ambiguity doesn't matter for what this is: a
 * quick, adb-free signal the user can check in Settings for whether the
 * background-kill mitigations (Phase 1/2/3) are actually helping.
 *
 * [MAIN] (default process, hosts OpenLessImeService + the Tauri backend) and
 * [ACCESSIBILITY] (the `:accessibility` process declared in the manifest)
 * are tracked separately, since the crash/kill debugging that motivated this
 * counter treated them as two distinct, independently-restartable processes
 * sharing one SharedPreferences file — recordStart()/recentDays() only ever
 * touch keys under their own [processKey] prefix.
 */
class OpenLessProcessRestartStats(context: Context, private val processKey: String) {
    private val preferences = context.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    fun recordStart() {
        val today = dayKey(0)
        preferences.edit().putInt(today, preferences.getInt(today, 0) + 1).apply()
        prune()
    }

    /** Oldest-to-newest (date, count) for the retained window; days with no recorded start show 0. */
    fun recentDays(): List<Pair<String, Int>> =
        (RETAIN_DAYS - 1 downTo 0).map { offset -> dateOnly(offset) to preferences.getInt(dayKey(offset), 0) }

    private fun prune() {
        val keepDates = (RETAIN_DAYS - 1 downTo 0).map { dateOnly(it) }.toSet()
        val prefix = "$processKey|"
        val edit = preferences.edit()
        var changed = false
        for (key in preferences.all.keys) {
            if (!key.startsWith(prefix)) continue // never touch the other process's keys
            if (key.removePrefix(prefix) !in keepDates) {
                edit.remove(key)
                changed = true
            }
        }
        if (changed) edit.apply()
    }

    private fun dateOnly(daysAgo: Int): String {
        val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
        return FORMAT.format(calendar.time)
    }

    private fun dayKey(daysAgo: Int): String = "$processKey|${dateOnly(daysAgo)}"

    companion object {
        const val MAIN = "main"
        const val ACCESSIBILITY = "accessibility"
        private const val STORE = "openless_process_restart_stats"
        private const val RETAIN_DAYS = 3
        private val FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
}
