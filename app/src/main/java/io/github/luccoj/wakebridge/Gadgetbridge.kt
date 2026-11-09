package io.github.luccoj.wakebridge

import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.Instant
import java.time.ZoneId

/**
 * Gadgetbridge's public interfaces used by this app.
 *
 * In: the "Send Broadcast" device action for "On Fall Asleep" / "On Wake Up", with Gadgetbridge's
 * default broadcast messages and this app as the broadcast package.
 *
 * Out: Gadgetbridge's alarm Intent API (Device settings > Intent API > "Allow 3rd party apps to set
 * alarms"). SET_ALARM takes the first device alarm slot that is disabled and has no title;
 * DISMISS_ALARM by title disables matching alarms and clears their title, freeing the slot again.
 * Gadgetbridge gives no reply, so failures are only visible in its log.
 */
object Gadgetbridge {
    const val ACTION_FELL_ASLEEP = "nodomain.freeyourgadget.gadgetbridge.FellAsleep"
    const val ACTION_WOKE_UP = "nodomain.freeyourgadget.gadgetbridge.WokeUp"

    private const val COMMAND_SET_ALARM = "nodomain.freeyourgadget.gadgetbridge.command.SET_ALARM"
    private const val COMMAND_DISMISS_ALARM = "nodomain.freeyourgadget.gadgetbridge.command.DISMISS_ALARM"

    /** Every alarm this app sets carries this in its title, which is how it finds them again. */
    const val ALARM_TITLE = "WakeBridge"
    const val TEST_ALARM_TITLE = "WakeBridge test"

    private const val TAG = "WakeBridge"

    fun setAlarm(context: Context, address: String, at: Instant, title: String = ALARM_TITLE) {
        val time = at.atZone(ZoneId.systemDefault())
        Log.i(TAG, "Setting device alarm '$title' at $time on $address")
        context.sendBroadcast(Intent(COMMAND_SET_ALARM).apply {
            putExtra("device", address)
            putExtra("hour", time.hour)
            putExtra("minutes", time.minute)
            putExtra("title", title)
        })
    }

    /** Dismisses every device alarm whose title contains [title]. */
    fun dismissAlarms(context: Context, address: String, title: String = ALARM_TITLE) {
        Log.i(TAG, "Dismissing device alarms '$title' on $address")
        context.sendBroadcast(Intent(COMMAND_DISMISS_ALARM).apply {
            putExtra("device", address)
            putExtra("mode", "title")
            putExtra("title", title)
        })
    }
}
