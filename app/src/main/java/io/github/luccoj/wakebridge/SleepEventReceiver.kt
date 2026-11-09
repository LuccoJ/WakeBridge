package io.github.luccoj.wakebridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receives Gadgetbridge's "On Fall Asleep" / "On Wake Up" broadcasts. */
class SleepEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Gadgetbridge.ACTION_FELL_ASLEEP -> Session.onFellAsleep(context)
            Gadgetbridge.ACTION_WOKE_UP -> Session.onWokeUp(context)
        }
    }
}
