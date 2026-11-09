package io.github.luccoj.wakebridge

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.service.quicksettings.TileService
import android.util.Log
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * The one sleep session that can be active at a time: a nap started by the user, or the night's
 * sleep armed by the nightly schedule.
 *
 * With "count only time asleep", nothing happens until the watch reports falling asleep. Then a
 * watch alarm is set for when the remaining sleep time runs out. If the watch reports waking up
 * before that, the time slept is added up and the alarm is withdrawn until the next fell-asleep
 * event. Without it, the alarm is set right away for the whole duration.
 *
 * Watch alarms have minute resolution, so wake-up times are rounded up to the next full minute.
 */
object Session {
    private const val TAG = "WakeBridge"

    /** The time source for all session logic; tests replace it. */
    var clock: Clock = Clock.systemDefaultZone()

    fun now(): Instant = Instant.now(clock)

    enum class Kind { NAP, NIGHT }

    enum class Status {
        /** No session. */
        IDLE,
        /** Counting only sleep, and the user is awake: waiting for a fell-asleep event. */
        WAITING,
        /** A watch alarm is set and will go off at [State.wakeAt]. */
        ALARM_SET,
    }

    /** How long after an alarm's time it is removed from the watch, so snoozing still works. */
    private val CLEANUP_DELAY: Duration = Duration.ofMinutes(10)

    data class State(
        val kind: Kind,
        val target: Duration,
        val sleepOnly: Boolean,
        val startedAt: Instant,
        val slept: Duration,
        /** When the current stretch of sleep began, if the user is asleep. */
        val asleepSince: Instant?,
        /** When the watch alarm goes off, if one is set. */
        val wakeAt: Instant?,
        /** No alarm later than this (nightly "wake no later than"). */
        val latest: Instant?,
        /** The session is cancelled if its alarm hasn't gone off by then. */
        val deadline: Instant?,
    ) {
        val status: Status
            get() = if (wakeAt != null) Status.ALARM_SET else Status.WAITING

        /** Sleep counted so far, including the current stretch. */
        fun sleptAt(now: Instant): Duration =
            if (sleepOnly && asleepSince != null) slept + Duration.between(asleepSince, now) else slept
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences("session", Context.MODE_PRIVATE)

    fun current(context: Context): State? {
        val p = prefs(context)
        if (!p.getBoolean("active", false)) return null
        fun instant(key: String) = p.getLong(key, 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli)
        return State(
            kind = Kind.valueOf(p.getString("kind", Kind.NAP.name)!!),
            target = Duration.ofMillis(p.getLong("target", 0)),
            sleepOnly = p.getBoolean("sleep_only", true),
            startedAt = instant("started_at") ?: now(),
            slept = Duration.ofMillis(p.getLong("slept", 0)),
            asleepSince = instant("asleep_since"),
            wakeAt = instant("wake_at"),
            latest = instant("latest"),
            deadline = instant("deadline"),
        )
    }

    private fun save(context: Context, state: State?) {
        val editor = prefs(context).edit().clear()
        if (state != null) {
            editor.putBoolean("active", true)
                .putString("kind", state.kind.name)
                .putLong("target", state.target.toMillis())
                .putBoolean("sleep_only", state.sleepOnly)
                .putLong("started_at", state.startedAt.toEpochMilli())
                .putLong("slept", state.slept.toMillis())
                .putLong("asleep_since", state.asleepSince?.toEpochMilli() ?: 0)
                .putLong("wake_at", state.wakeAt?.toEpochMilli() ?: 0)
                .putLong("latest", state.latest?.toEpochMilli() ?: 0)
                .putLong("deadline", state.deadline?.toEpochMilli() ?: 0)
        }
        // A cleanup still pending from a finished session survives the clear()
        pendingCleanup(context)?.let { editor.putLong("cleanup_at", it.toEpochMilli()) }
        editor.apply()
        onChanged(context)
    }

    private fun pendingCleanup(context: Context): Instant? =
        prefs(context).getLong("cleanup_at", 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli)

    /**
     * Starts a session, replacing any active one. Returns false, changing nothing, when no watch
     * is configured.
     */
    fun start(
        context: Context,
        kind: Kind,
        target: Duration,
        sleepOnly: Boolean,
        latest: Instant? = null,
    ): Boolean {
        val address = Settings(context).watchAddress ?: return false
        val now = now()
        withdraw(context, address, current(context))
        cleanup(context)

        val expiry = Settings(context).expiryHours
        var state = State(
            kind = kind,
            target = target,
            sleepOnly = sleepOnly,
            startedAt = now,
            slept = Duration.ZERO,
            asleepSince = null,
            wakeAt = null,
            latest = latest,
            deadline = if (expiry > 0) now.plus(Duration.ofHours(expiry.toLong())) else null,
        )
        EventLog.add(context, context.getString(R.string.log_started, describe(context, state)))
        if (!sleepOnly) {
            state = state.copy(wakeAt = wakeTime(now, target, latest))
            Gadgetbridge.setAlarm(context, address, state.wakeAt!!)
        }
        save(context, state)
        Scheduler.scheduleSession(context, state)
        return true
    }

    /** Cancels the active session, if any, removing its alarm from the watch. */
    fun cancel(context: Context, reason: String) {
        val state = current(context) ?: return
        Settings(context).watchAddress?.let { withdraw(context, it, state) }
        EventLog.add(context, reason)
        save(context, null)
        Scheduler.scheduleSession(context, null)
    }

    fun onFellAsleep(context: Context) {
        val now = now()
        EventLog.add(context, context.getString(R.string.log_fell_asleep))
        prefs(context).edit().putLong("last_fell_asleep", now.toEpochMilli()).apply()
        val state = checkTimers(context, now) ?: startAutoNap(context, now) ?: return onChanged(context)
        markAsleep(context, state, now)
    }

    /** Starts counting a stretch of sleep from [now], if the session counts sleep and isn't already. */
    private fun markAsleep(context: Context, state: State, now: Instant) {
        if (!state.sleepOnly || state.asleepSince != null) return onChanged(context)

        val address = Settings(context).watchAddress ?: return
        val wakeAt = wakeTime(now, state.target - state.slept, state.latest)
        Gadgetbridge.setAlarm(context, address, wakeAt)
        val updated = state.copy(asleepSince = now, wakeAt = wakeAt)
        save(context, updated)
        Scheduler.scheduleSession(context, updated)
    }

    /** Starts an automatic nap when the user falls asleep outside night-time, if enabled. */
    private fun startAutoNap(context: Context, now: Instant): State? {
        val settings = Settings(context)
        if (!settings.autoNapEnabled) return null
        if (settings.isNightTime(now.atZone(clock.zone).toLocalTime())) return null
        EventLog.add(context, context.getString(R.string.log_auto_nap))
        if (!start(context, Kind.NAP, Duration.ofMinutes(settings.autoNapMinutes.toLong()), true)) return null
        return current(context)
    }

    /**
     * Night starts while a nap is active: the nap's alarm is withdrawn and the night session takes
     * over, already counting sleep if the user was asleep.
     */
    fun handOverToNight(context: Context, nap: State, night: () -> Boolean) {
        val wasAsleep = nap.sleepOnly && nap.asleepSince != null
        EventLog.add(context, context.getString(R.string.log_night_takeover))
        if (!night()) return
        val state = current(context) ?: return
        if (wasAsleep) markAsleep(context, state, now())
    }

    fun onWokeUp(context: Context) {
        val now = now()
        EventLog.add(context, context.getString(R.string.log_woke_up))
        prefs(context).edit().putLong("last_woke_up", now.toEpochMilli()).apply()
        val state = checkTimers(context, now) ?: return onChanged(context)
        if (!state.sleepOnly || state.asleepSince == null) return onChanged(context)

        val slept = state.sleptAt(now)
        if (slept >= state.target) {
            finish(context, state)
            return
        }
        Settings(context).watchAddress?.let { Gadgetbridge.dismissAlarms(context, it) }
        EventLog.add(context, context.getString(R.string.log_paused, slept.toMinutes(), state.target.toMinutes()))
        val updated = state.copy(slept = slept, asleepSince = null, wakeAt = null)
        save(context, updated)
        Scheduler.scheduleSession(context, updated)
    }

    /**
     * Applies whatever is due: the alarm having gone off, the session's deadline, or a pending
     * cleanup. Returns the session if it is still active.
     */
    fun checkTimers(context: Context, now: Instant = now()): State? {
        val cleanupAt = pendingCleanup(context)
        if (cleanupAt != null && now >= cleanupAt && current(context)?.wakeAt == null) {
            cleanup(context)
        }
        val state = current(context) ?: return null
        if (state.wakeAt != null && now >= state.wakeAt) {
            finish(context, state)
            return null
        }
        if (state.deadline != null && now >= state.deadline) {
            cancel(context, context.getString(R.string.log_expired, Settings(context).expiryHours))
            return null
        }
        if (state.latest != null && now >= state.latest) {
            // Awake past the latest wake-up time (an alarm would have finished the session above)
            cancel(context, context.getString(R.string.log_night_over))
            return null
        }
        return state
    }

    /** The alarm went off: the session is over, its watch alarm is removed a little later. */
    private fun finish(context: Context, state: State) {
        val wakeAt = state.wakeAt ?: now()
        EventLog.add(context, context.getString(R.string.log_finished))
        prefs(context).edit().putLong("cleanup_at", wakeAt.plus(CLEANUP_DELAY).toEpochMilli()).apply()
        save(context, null)
        Scheduler.scheduleSession(context, null)
    }

    /** Removes a finished session's alarm from the watch, freeing its slot. */
    private fun cleanup(context: Context) {
        if (pendingCleanup(context) == null) return
        prefs(context).edit().remove("cleanup_at").apply()
        Settings(context).watchAddress?.let { Gadgetbridge.dismissAlarms(context, it) }
    }

    private fun withdraw(context: Context, address: String, state: State?) {
        if (state?.wakeAt != null) Gadgetbridge.dismissAlarms(context, address)
    }

    fun cleanupDueAt(context: Context): Instant? = pendingCleanup(context)

    fun lastFellAsleep(context: Context): Instant? =
        prefs(context).getLong("last_fell_asleep", 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli)

    fun lastWokeUp(context: Context): Instant? =
        prefs(context).getLong("last_woke_up", 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli)

    private fun wakeTime(now: Instant, remaining: Duration, latest: Instant?): Instant {
        val wakeAt = now.plus(remaining).plus(59, ChronoUnit.SECONDS).truncatedTo(ChronoUnit.MINUTES)
        return if (latest != null && latest < wakeAt) latest else wakeAt
    }

    fun describe(context: Context, state: State): String {
        val kind = context.getString(if (state.kind == Kind.NAP) R.string.kind_nap else R.string.kind_night)
        val mode = context.getString(if (state.sleepOnly) R.string.mode_sleep_only else R.string.mode_countdown)
        return "$kind, ${Format.duration(context, state.target)}, $mode"
    }

    private val listeners = mutableSetOf<() -> Unit>()

    fun addListener(listener: () -> Unit) = listeners.add(listener)

    fun removeListener(listener: () -> Unit) = listeners.remove(listener)

    private fun onChanged(context: Context) {
        Log.d(TAG, "Session changed: ${current(context)}")
        TileService.requestListeningState(context, ComponentName(context, WakeTileService::class.java))
        listeners.toList().forEach { it() }
    }
}
