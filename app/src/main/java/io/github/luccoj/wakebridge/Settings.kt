package io.github.luccoj.wakebridge

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import java.time.Duration
import java.time.LocalTime

/** When the alarm goes off, relative to the sleep amount. */
enum class WakeMode {
    /** After the amount of actual sleep: awakenings pause the count. */
    SLEEP_ONLY,
    /** The amount after first falling asleep: later awakenings are ignored. */
    FROM_FIRST_SLEEP,
    /** Naps: the amount from now. Nights: at the night's end. */
    FIXED,
}

/** User settings, stored in the default preferences so the settings pane edits them directly. */
class Settings(context: Context) {
    val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    private fun edit(block: SharedPreferences.Editor.() -> Unit) = prefs.edit().apply(block).apply()

    val watchAddress: String?
        get() = prefs.getString(KEY_WATCH_ADDRESS, null)?.takeIf { it.isNotBlank() }

    val watchName: String?
        get() = prefs.getString(KEY_WATCH_NAME, null)

    fun setWatch(address: String, name: String?) = edit {
        putString(KEY_WATCH_ADDRESS, address)
        putString(KEY_WATCH_NAME, name)
    }

    // Day

    var napMinutes: Int
        get() = prefs.getInt(KEY_NAP_MINUTES, 20)
        set(value) = edit { putInt(KEY_NAP_MINUTES, value) }

    /** "Every nap": falling asleep during the day starts a nap by itself. */
    var dayEvery: Boolean
        get() = prefs.getBoolean(KEY_DAY_EVERY, false)
        set(value) = edit { putBoolean(KEY_DAY_EVERY, value) }

    var dayWakeMode: WakeMode
        get() = mode(KEY_DAY_WAKE_MODE)
        set(value) = edit { putString(KEY_DAY_WAKE_MODE, value.name) }

    // Night

    var nightStart: LocalTime
        get() = parseTime(prefs.getString(KEY_NIGHT_START, null), LocalTime.of(21, 0))
        set(value) = edit { putString(KEY_NIGHT_START, value.toString()) }

    var nightEnd: LocalTime
        get() = parseTime(prefs.getString(KEY_NIGHT_END, null), LocalTime.of(8, 0))
        set(value) = edit { putString(KEY_NIGHT_END, value.toString()) }

    /** The night window's length, start to end, across midnight. */
    val nightWindow: Duration
        get() {
            val minutes = Math.floorMod(
                nightEnd.toSecondOfDay() / 60 - nightStart.toSecondOfDay() / 60, 24 * 60
            )
            return Duration.ofMinutes(minutes.toLong())
        }

    /** The night's sleep, never longer than the window. */
    var nightMinutes: Int
        get() = prefs.getInt(KEY_NIGHT_MINUTES, 450).coerceAtMost(nightWindow.toMinutes().toInt())
        set(value) = edit { putInt(KEY_NIGHT_MINUTES, value) }

    /** "Every night": the night's session is armed by itself at the night's start. */
    var nightEvery: Boolean
        get() = prefs.getBoolean(KEY_NIGHT_EVERY, false)
        set(value) = edit { putBoolean(KEY_NIGHT_EVERY, value) }

    /** "Tonight": armed once, at the next night start. */
    var nightTonight: Boolean
        get() = prefs.getBoolean(KEY_NIGHT_TONIGHT, false)
        set(value) = edit { putBoolean(KEY_NIGHT_TONIGHT, value) }

    var nightWakeMode: WakeMode
        get() = mode(KEY_NIGHT_WAKE_MODE)
        set(value) = edit { putString(KEY_NIGHT_WAKE_MODE, value.name) }

    var nightIncludeNaps: Boolean
        get() = prefs.getBoolean(KEY_NIGHT_INCLUDE_NAPS, false)
        set(value) = edit { putBoolean(KEY_NIGHT_INCLUDE_NAPS, value) }

    /** Whether [time] falls in the night window. */
    fun isNightTime(time: LocalTime): Boolean {
        val start = nightStart
        val end = nightEnd
        return if (start <= end) time >= start && time < end else time >= start || time < end
    }

    // Alarms

    /** Keeps an alarm on the watch even before sleep is detected. */
    val failSafe: Boolean
        get() = prefs.getBoolean(KEY_FAIL_SAFE, true)

    val phoneBackup: Boolean
        get() = prefs.getBoolean(KEY_PHONE_BACKUP, true)

    val backupDelayMinutes: Int
        get() = prefs.getInt(KEY_BACKUP_DELAY, 10)

    /** Hours after which an alarm that never went off is cancelled; 0 never cancels. */
    val expiryHours: Int
        get() = prefs.getInt(KEY_EXPIRY_HOURS, 12)

    // Appearance

    val timelineOnLeft: Boolean
        get() = prefs.getString(KEY_TIMELINE_SIDE, "right") == "left"

    private fun mode(key: String): WakeMode =
        prefs.getString(key, null)?.let { runCatching { WakeMode.valueOf(it) }.getOrNull() } ?: WakeMode.SLEEP_ONLY

    companion object {
        const val KEY_WATCH = "watch"
        const val KEY_WATCH_ADDRESS = "watch_address"
        const val KEY_WATCH_NAME = "watch_name"
        const val KEY_SETUP = "setup"
        const val KEY_NAP_MINUTES = "nap_minutes"
        const val KEY_DAY_EVERY = "day_every"
        const val KEY_DAY_WAKE_MODE = "day_wake_mode"
        const val KEY_NIGHT_START = "night_start"
        const val KEY_NIGHT_END = "night_end"
        const val KEY_NIGHT_MINUTES = "night_sleep_minutes"
        const val KEY_NIGHT_EVERY = "night_every"
        const val KEY_NIGHT_TONIGHT = "night_tonight"
        const val KEY_NIGHT_WAKE_MODE = "night_wake_mode"
        const val KEY_NIGHT_INCLUDE_NAPS = "night_include_naps"
        const val KEY_FAIL_SAFE = "fail_safe"
        const val KEY_PHONE_BACKUP = "phone_backup"
        const val KEY_BACKUP_DELAY = "backup_delay"
        const val KEY_EXPIRY_HOURS = "expiry_hours"
        const val KEY_TIMELINE_SIDE = "timeline_side"

        fun parseTime(value: String?, default: LocalTime): LocalTime =
            try {
                if (value == null) default else LocalTime.parse(value)
            } catch (e: Exception) {
                default
            }
    }
}
