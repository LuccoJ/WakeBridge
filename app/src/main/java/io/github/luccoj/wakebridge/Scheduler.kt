package io.github.luccoj.wakebridge

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

/**
 * Phone-side timers. None needs to be exact: they only update the app's own state (the alarm
 * that wakes the user lives on the watch), so inexact alarms are used, which need no permission.
 */
object Scheduler {
    private const val ACTION_CHECK = "io.github.luccoj.wakebridge.CHECK"
    private const val ACTION_NIGHT = "io.github.luccoj.wakebridge.NIGHT"

    /** Schedules the next moment something is due for the session: alarm time, deadline or cleanup. */
    fun scheduleSession(context: Context, state: Session.State?) {
        val due = listOfNotNull(state?.wakeAt, state?.deadline, Session.cleanupDueAt(context)).minOrNull()
        set(context, ACTION_CHECK, due)
    }

    /**
     * Schedules the next start of night: it arms the night's session, and ends naps running into
     * the night. Nothing is scheduled when neither nightly mode nor automatic naps are on.
     */
    fun scheduleNight(context: Context) {
        val settings = Settings(context)
        val needed = settings.nightEnabled || settings.autoNapEnabled
        set(context, ACTION_NIGHT, if (needed) nextOccurrence(settings.nightArmTime) else null)
    }

    /** The first moment after [after] when the clock shows [time]. */
    fun nextOccurrence(time: LocalTime, after: Instant = Session.now()): Instant {
        val zone = Session.clock.zone
        val date = after.atZone(zone).toLocalDate()
        val sameDay = date.atTime(time).atZone(zone).toInstant()
        return if (sameDay > after) sameDay else date.plusDays(1).atTime(time).atZone(zone).toInstant()
    }

    private fun set(context: Context, action: String, at: Instant?) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = PendingIntent.getBroadcast(
            context, action.hashCode(),
            Intent(context, TimerReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(intent)
        if (at != null) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), intent)
        }
    }

    /**
     * Arms tonight's session. A nap still running is replaced by it (or just cancelled when
     * nightly mode is off); another night session, still running from the night before, is
     * left alone.
     */
    fun armNight(context: Context) {
        val settings = Settings(context)
        if (!settings.nightEnabled) {
            if (Session.checkTimers(context)?.kind == Session.Kind.NAP) {
                Session.cancel(context, context.getString(R.string.log_nap_ended_by_night))
            }
            return
        }
        val latest = if (settings.nightLatestEnabled) nextOccurrence(settings.nightLatestTime) else null
        val night = {
            Session.start(
                context, Session.Kind.NIGHT, Duration.ofMinutes(settings.nightMinutes.toLong()),
                settings.nightSleepOnly, latest
            )
        }
        val current = Session.checkTimers(context)
        when (current?.kind) {
            null -> night()
            Session.Kind.NAP -> Session.handOverToNight(context, current, night)
            Session.Kind.NIGHT -> EventLog.add(context, context.getString(R.string.log_night_skipped))
        }
    }

    class TimerReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_CHECK -> {
                    Session.checkTimers(context)
                    scheduleSession(context, Session.current(context))
                }
                ACTION_NIGHT -> {
                    armNight(context)
                    scheduleNight(context)
                }
                Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                    Session.checkTimers(context)
                    scheduleSession(context, Session.current(context))
                    scheduleNight(context)
                }
            }
        }
    }
}
