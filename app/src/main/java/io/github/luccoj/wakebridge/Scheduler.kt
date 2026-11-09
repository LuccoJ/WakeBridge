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
 * Phone-side timers for the app's own state. None needs to be exact (the alarm that wakes the
 * user lives on the watch, and the phone backup alarm has its own exact alarm), so inexact alarms
 * are used.
 */
object Scheduler {
    private const val ACTION_CHECK = "io.github.luccoj.wakebridge.CHECK"
    private const val ACTION_NIGHT = "io.github.luccoj.wakebridge.NIGHT"

    /** Schedules the next moment something is due for the session: alarm time, deadline or cleanup. */
    fun scheduleSession(context: Context, state: Session.State?) {
        val due = listOfNotNull(state?.wakeAt, state?.deadline, state?.latest, Session.cleanupDueAt(context)).minOrNull()
        set(context, ACTION_CHECK, due)
    }

    /**
     * Schedules the next night start: it arms the night's session ("Every night", or "Tonight"
     * once) and ends naps running into the night.
     */
    fun scheduleNight(context: Context) {
        val settings = Settings(context)
        val needed = settings.nightEvery || settings.nightTonight || settings.dayEvery
        set(context, ACTION_NIGHT, if (needed) nextOccurrence(settings.nightStart) else null)
    }

    /** "Arm tonight": now if the night has started, else at its start. */
    fun armTonight(context: Context) {
        val settings = Settings(context)
        settings.nightTonight = true
        if (settings.isNightTime(Session.now().atZone(Session.clock.zone).toLocalTime())) armNight(context)
        scheduleNight(context)
        Session.onChanged(context)
    }

    fun disarmTonight(context: Context) {
        Settings(context).nightTonight = false
        scheduleNight(context)
        Session.onChanged(context)
    }

    /** The first moment after [after] when the clock shows [time]. */
    fun nextOccurrence(time: LocalTime, after: Instant = Session.now()): Instant {
        val zone = Session.clock.zone
        val date = after.atZone(zone).toLocalDate()
        val sameDay = date.atTime(time).atZone(zone).toInstant()
        return if (sameDay > after) sameDay else date.plusDays(1).atTime(time).atZone(zone).toInstant()
    }

    /** The last moment, up to [before], when the clock showed [time]. */
    fun previousOccurrence(time: LocalTime, before: Instant = Session.now()): Instant {
        val zone = Session.clock.zone
        val date = before.atZone(zone).toLocalDate()
        val sameDay = date.atTime(time).atZone(zone).toInstant()
        return if (sameDay <= before) sameDay else date.minusDays(1).atTime(time).atZone(zone).toInstant()
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
     * Night starts. A nap still running hands over to the night session, or is just cancelled
     * when no night is armed; a night session already running is left alone.
     */
    fun armNight(context: Context) {
        val settings = Settings(context)
        val current = Session.checkTimers(context)
        if (!settings.nightEvery && !settings.nightTonight) {
            if (current?.kind == Session.Kind.NAP) {
                Session.cancel(context, context.getString(R.string.log_nap_ended_by_night))
            }
            return
        }
        settings.nightTonight = false
        if (current?.kind == Session.Kind.NIGHT) {
            EventLog.add(context, context.getString(R.string.log_night_skipped))
            return
        }

        var target = Duration.ofMinutes(settings.nightMinutes.toLong())
        if (settings.nightIncludeNaps) {
            val naps = NapLedger.today(context)
            if (!naps.isZero) EventLog.add(context, context.getString(R.string.log_naps_counted, naps.toMinutes()))
            target -= naps
        }
        if (target <= Duration.ZERO) {
            EventLog.add(context, context.getString(R.string.log_night_covered))
            if (current?.kind == Session.Kind.NAP) Session.cancel(context, context.getString(R.string.log_nap_ended_by_night))
            return
        }
        // The watch away at night start: rather than no alarm at all, ring the phone at the night's end
        var mode = settings.nightWakeMode
        if (Session.canStart(context, mode) == Session.StartResult.DISCONNECTED) {
            EventLog.add(context, context.getString(R.string.log_night_disconnected))
            mode = WakeMode.FIXED
        }
        val latest = nextOccurrence(settings.nightEnd)
        val night = { Session.start(context, Session.Kind.NIGHT, target, mode, latest) }
        if (current?.kind == Session.Kind.NAP) Session.handOverToNight(context, current, night) else night()
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
                    val state = Session.checkTimers(context)
                    scheduleSession(context, state)
                    scheduleNight(context)
                    // Alarm-clock alarms don't survive a reboot
                    PhoneAlarm.pendingAt(context)?.let { PhoneAlarm.schedule(context, it) }
                }
            }
        }
    }
}
