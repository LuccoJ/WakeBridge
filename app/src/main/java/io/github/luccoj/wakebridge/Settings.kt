package io.github.luccoj.wakebridge

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import java.time.LocalTime

/** User settings, stored in the default preferences so the settings screen edits them directly. */
class Settings(context: Context) {
    val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    val watchAddress: String?
        get() = prefs.getString(KEY_WATCH_ADDRESS, null)?.takeIf { it.isNotBlank() }

    val watchName: String?
        get() = prefs.getString(KEY_WATCH_NAME, null)

    fun setWatch(address: String, name: String?) {
        prefs.edit().putString(KEY_WATCH_ADDRESS, address).putString(KEY_WATCH_NAME, name).apply()
    }

    var napMinutes: Int
        get() = prefs.getInt(KEY_NAP_MINUTES, 20)
        set(value) = prefs.edit().putInt(KEY_NAP_MINUTES, value).apply()

    var napSleepOnly: Boolean
        get() = prefs.getBoolean(KEY_NAP_SLEEP_ONLY, true)
        set(value) = prefs.edit().putBoolean(KEY_NAP_SLEEP_ONLY, value).apply()

    /** Hours after which an alarm that never went off is cancelled; 0 never cancels. */
    val expiryHours: Int
        get() = prefs.getInt(KEY_EXPIRY_HOURS, 12)

    val nightEnabled: Boolean
        get() = prefs.getBoolean(KEY_NIGHT_ENABLED, false)

    val nightArmTime: LocalTime
        get() = parseTime(prefs.getString(KEY_NIGHT_ARM_TIME, null), LocalTime.of(21, 0))

    val nightMinutes: Int
        get() = prefs.getString(KEY_NIGHT_MINUTES, null)?.toIntOrNull() ?: 450

    val nightSleepOnly: Boolean
        get() = prefs.getBoolean(KEY_NIGHT_SLEEP_ONLY, true)

    val nightLatestEnabled: Boolean
        get() = prefs.getBoolean(KEY_NIGHT_LATEST_ENABLED, true)

    val nightLatestTime: LocalTime
        get() = parseTime(prefs.getString(KEY_NIGHT_LATEST_TIME, null), LocalTime.of(8, 0))

    val autoNapEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_NAP_ENABLED, false)

    val autoNapMinutes: Int
        get() = prefs.getString(KEY_AUTO_NAP_MINUTES, null)?.toIntOrNull() ?: 20

    /**
     * Whether [time] falls between the night's start and its latest wake-up time. This window
     * applies even with the nightly alarm off, so that automatic naps never start at night.
     */
    fun isNightTime(time: LocalTime): Boolean {
        val start = nightArmTime
        val end = nightLatestTime
        return if (start <= end) time >= start && time < end else time >= start || time < end
    }

    companion object {
        const val KEY_WATCH = "watch"
        const val KEY_WATCH_ADDRESS = "watch_address"
        const val KEY_WATCH_NAME = "watch_name"
        const val KEY_NAP_MINUTES = "nap_minutes"
        const val KEY_NAP_SLEEP_ONLY = "nap_sleep_only"
        const val KEY_EXPIRY_HOURS = "expiry_hours"
        const val KEY_NIGHT_ENABLED = "night_enabled"
        const val KEY_NIGHT_ARM_TIME = "night_arm_time"
        const val KEY_NIGHT_MINUTES = "night_minutes"
        const val KEY_NIGHT_SLEEP_ONLY = "night_sleep_only"
        const val KEY_NIGHT_LATEST_ENABLED = "night_latest_enabled"
        const val KEY_NIGHT_LATEST_TIME = "night_latest_time"
        const val KEY_AUTO_NAP_ENABLED = "auto_nap_enabled"
        const val KEY_AUTO_NAP_MINUTES = "auto_nap_minutes"

        fun parseTime(value: String?, default: LocalTime): LocalTime =
            try {
                if (value == null) default else LocalTime.parse(value)
            } catch (e: Exception) {
                default
            }
    }
}
