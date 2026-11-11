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
 * The one sleep session that can be active at a time: a nap, or the night's sleep.
 *
 * How the watch alarm moves depends on the [WakeMode]:
 * - [WakeMode.SLEEP_ONLY]: the alarm is set for when the remaining sleep runs out after falling
 *   asleep; waking up adds the time awake. With the fail-safe on, a nap holds an alarm for the
 *   earliest time the sleep could be complete from the start, so a missed event or a
 *   disconnection can only wake the user early.
 * - [WakeMode.FROM_FIRST_SLEEP]: the alarm is set once, from the first fell-asleep event.
 * - [WakeMode.FIXED]: a nap's alarm is set right away; a night's at the night's end.
 *
 * A night always holds an alarm at the night's end until sleep is detected, and is never woken
 * later than that ([State.latest]). Watch alarms have minute resolution, so wake-up times are
 * rounded up to the next full minute.
 */
object Session {
    private const val TAG = "WakeBridge"

    /** The time source for all session logic; tests replace it. */
    var clock: Clock = Clock.systemDefaultZone()

    fun now(): Instant = Instant.now(clock)

    enum class Kind { NAP, NIGHT }

    enum class StartResult { STARTED, NO_WATCH, DISCONNECTED }

    /** How long after an alarm's time it is removed from the watch, so snoozing still works. */
    private val CLEANUP_DELAY: Duration = Duration.ofMinutes(10)

    /** How long a cancelled session can be brought back with "Tap to undo". */
    val UNDO_TIME: Duration = Duration.ofSeconds(8)

    data class State(
        val kind: Kind,
        val target: Duration,
        val mode: WakeMode,
        /** Fail-safe: keep an alarm for the earliest possible time, also while awake. */
        val early: Boolean,
        val startedAt: Instant,
        /** Sleep counted before the current stretch ([WakeMode.SLEEP_ONLY]). */
        val slept: Duration,
        /** When the current stretch of sleep began, if the user is asleep. */
        val asleepSince: Instant?,
        /** When sleep was first detected in this session. */
        val firstSleepAt: Instant?,
        /** When the watch alarm goes off, if one is set. */
        val wakeAt: Instant?,
        /** No alarm later than this: the night's end. */
        val latest: Instant?,
        /** The session is cancelled if its alarm hasn't gone off by then. */
        val deadline: Instant?,
        /** The watch was out of reach at the start: only the phone can ring. */
        val phoneOnly: Boolean,
    ) {
        val counting: Boolean
            get() = mode != WakeMode.FIXED

        val asleep: Boolean
            get() = counting && asleepSince != null

        /** Sleep counted so far, including the current stretch. */
        fun sleptAt(now: Instant): Duration = when (mode) {
            WakeMode.FIXED -> Duration.ZERO
            WakeMode.FROM_FIRST_SLEEP -> firstSleepAt?.let { Duration.between(it, now) } ?: Duration.ZERO
            WakeMode.SLEEP_ONLY -> if (asleepSince != null) slept + Duration.between(asleepSince, now) else slept
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences("session", Context.MODE_PRIVATE)

    private fun undoPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences("session_undo", Context.MODE_PRIVATE)

    fun current(context: Context): State? = read(prefs(context))

    private fun read(p: SharedPreferences): State? {
        if (!p.getBoolean("active", false)) return null
        fun instant(key: String) = p.getLong(key, 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli)
        return State(
            kind = runCatching { Kind.valueOf(p.getString("kind", null)!!) }.getOrDefault(Kind.NAP),
            target = Duration.ofMillis(p.getLong("target", 0)),
            mode = runCatching { WakeMode.valueOf(p.getString("mode", null)!!) }.getOrDefault(WakeMode.SLEEP_ONLY),
            early = p.getBoolean("early", false),
            startedAt = instant("started_at") ?: now(),
            slept = Duration.ofMillis(p.getLong("slept", 0)),
            asleepSince = instant("asleep_since"),
            firstSleepAt = instant("first_sleep_at"),
            wakeAt = instant("wake_at"),
            latest = instant("latest"),
            deadline = instant("deadline"),
            phoneOnly = p.getBoolean("phone_only", false),
        )
    }

    private fun write(editor: SharedPreferences.Editor, state: State) {
        editor.putBoolean("active", true)
            .putString("kind", state.kind.name)
            .putLong("target", state.target.toMillis())
            .putString("mode", state.mode.name)
            .putBoolean("early", state.early)
            .putLong("started_at", state.startedAt.toEpochMilli())
            .putLong("slept", state.slept.toMillis())
            .putLong("asleep_since", state.asleepSince?.toEpochMilli() ?: 0)
            .putLong("first_sleep_at", state.firstSleepAt?.toEpochMilli() ?: 0)
            .putLong("wake_at", state.wakeAt?.toEpochMilli() ?: 0)
            .putLong("latest", state.latest?.toEpochMilli() ?: 0)
            .putLong("deadline", state.deadline?.toEpochMilli() ?: 0)
            .putBoolean("phone_only", state.phoneOnly)
    }

    private fun save(context: Context, state: State?) {
        val p = prefs(context)
        val cleanup = p.getLong("cleanup_at", 0)
        val lastFell = p.getLong("last_fell_asleep", 0)
        val lastWoke = p.getLong("last_woke_up", 0)
        val editor = p.edit().clear()
        if (state != null) write(editor, state)
        // These outlive sessions
        if (cleanup > 0) editor.putLong("cleanup_at", cleanup)
        if (lastFell > 0) editor.putLong("last_fell_asleep", lastFell)
        if (lastWoke > 0) editor.putLong("last_woke_up", lastWoke)
        editor.apply()
        onChanged(context)
    }

    private fun pendingCleanup(context: Context): Instant? =
        prefs(context).getLong("cleanup_at", 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli)

    /** Whether a session in this mode can start now; the watch matters unless it's fixed. */
    fun canStart(context: Context, mode: WakeMode): StartResult {
        if (Settings(context).watchAddress == null) return StartResult.NO_WATCH
        if (mode != WakeMode.FIXED && WatchConnection.state(context) == WatchConnection.State.DISCONNECTED) {
            return StartResult.DISCONNECTED
        }
        return StartResult.STARTED
    }

    /** Starts a session, replacing any active one. Changes nothing unless the result is STARTED. */
    fun start(
        context: Context,
        kind: Kind,
        target: Duration,
        mode: WakeMode,
        latest: Instant? = null,
    ): StartResult {
        val check = canStart(context, mode)
        if (check != StartResult.STARTED) return check
        val settings = Settings(context)
        val address = settings.watchAddress!!
        val now = now()
        withdraw(context, address, current(context))
        cleanup(context)
        PhoneAlarm.cancel(context)
        undoPrefs(context).edit().clear().apply()

        val expiry = settings.expiryHours
        val disconnected = WatchConnection.state(context) == WatchConnection.State.DISCONNECTED
        val phoneOnly = settings.phoneOnly || disconnected
        var state = State(
            kind = kind,
            target = target,
            mode = mode,
            early = mode != WakeMode.FIXED && settings.failSafe,
            startedAt = now,
            slept = Duration.ZERO,
            asleepSince = null,
            firstSleepAt = null,
            wakeAt = null,
            latest = latest,
            deadline = if (expiry > 0) now.plus(Duration.ofHours(expiry.toLong())) else null,
            phoneOnly = phoneOnly,
        )
        val wakeAt = when {
            // A night waits for sleep with an alarm at its end, whatever the mode
            kind == Kind.NIGHT && latest != null -> latest
            mode == WakeMode.FIXED || state.early -> wakeTime(now, target, latest)
            else -> null
        }
        state = state.copy(wakeAt = wakeAt)
        EventLog.add(context, context.getString(R.string.log_started, describe(context, state)))
        if (settings.phoneOnly) EventLog.add(context, context.getString(R.string.log_phone_only_setting))
        else if (disconnected) EventLog.add(context, context.getString(R.string.log_phone_only))
        if (wakeAt != null && !phoneOnly) Gadgetbridge.setAlarm(context, address, wakeAt)
        save(context, state)
        Scheduler.scheduleSession(context, state)
        PhoneAlarm.sync(context, state)
        return StartResult.STARTED
    }

    /** Cancels the active session, if any, removing its alarm from the watch. */
    fun cancel(context: Context, reason: String, undoable: Boolean = false) {
        val state = current(context) ?: return
        val now = now()
        Settings(context).watchAddress?.let { withdraw(context, it, state) }
        PhoneAlarm.cancel(context)
        if (state.kind == Kind.NAP) NapLedger.add(context, state.sleptAt(now))
        if (undoable) {
            val editor = undoPrefs(context).edit().clear()
            write(editor, state)
            editor.putLong("until", now.plus(UNDO_TIME).toEpochMilli()).apply()
        }
        EventLog.add(context, reason)
        save(context, null)
        Scheduler.scheduleSession(context, null)
    }

    /** Until when the last cancelled session can be restored, if it can. */
    fun undoUntil(context: Context): Instant? {
        val until = undoPrefs(context).getLong("until", 0)
        return if (until > 0 && now().toEpochMilli() < until) Instant.ofEpochMilli(until) else null
    }

    /** Restores the session cancelled moments ago. */
    fun undo(context: Context): Boolean {
        if (undoUntil(context) == null || current(context) != null) return false
        val state = read(undoPrefs(context)) ?: return false
        undoPrefs(context).edit().clear().apply()
        val address = Settings(context).watchAddress ?: return false
        if (state.wakeAt != null && !state.phoneOnly) Gadgetbridge.setAlarm(context, address, state.wakeAt)
        if (state.kind == Kind.NAP) NapLedger.add(context, state.sleptAt(now()).negated())
        EventLog.add(context, context.getString(R.string.log_undone))
        save(context, state)
        Scheduler.scheduleSession(context, state)
        PhoneAlarm.sync(context, state)
        return true
    }

    fun onFellAsleep(context: Context) {
        val now = now()
        EventLog.add(context, context.getString(R.string.log_fell_asleep))
        prefs(context).edit().putLong("last_fell_asleep", now.toEpochMilli()).apply()
        val state = checkTimers(context, now) ?: startAutoNap(context, now) ?: return onChanged(context)
        markAsleep(context, state, now)
    }

    /** Counts sleep from [now], as the session's mode says. */
    private fun markAsleep(context: Context, state: State, now: Instant) {
        val address = Settings(context).watchAddress ?: return
        val updated = when (state.mode) {
            WakeMode.FIXED -> return onChanged(context)
            WakeMode.FROM_FIRST_SLEEP -> {
                if (state.firstSleepAt != null) return onChanged(context)
                state.copy(asleepSince = now, firstSleepAt = now, wakeAt = wakeTime(now, state.target, state.latest))
            }
            WakeMode.SLEEP_ONLY -> {
                if (state.asleepSince != null) return onChanged(context)
                state.copy(
                    asleepSince = now,
                    firstSleepAt = state.firstSleepAt ?: now,
                    wakeAt = wakeTime(now, state.target - state.slept, state.latest),
                )
            }
        }
        if (!state.phoneOnly) moveAlarm(context, address, state.wakeAt, updated.wakeAt)
        save(context, updated)
        Scheduler.scheduleSession(context, updated)
        PhoneAlarm.sync(context, updated)
    }

    /** Starts an automatic nap when the user falls asleep outside night-time, if "Every nap" is on. */
    private fun startAutoNap(context: Context, now: Instant): State? {
        val settings = Settings(context)
        if (!settings.dayEvery) return null
        if (settings.isNightTime(now.atZone(clock.zone).toLocalTime())) return null
        EventLog.add(context, context.getString(R.string.log_auto_nap))
        val result = start(context, Kind.NAP, Duration.ofMinutes(settings.napMinutes.toLong()), settings.dayWakeMode)
        return if (result == StartResult.STARTED) current(context) else null
    }

    /**
     * Night starts while a nap is active: the night session takes over, already counting sleep
     * if the user was asleep.
     */
    fun handOverToNight(context: Context, nap: State, night: () -> StartResult) {
        val wasAsleep = nap.asleep
        EventLog.add(context, context.getString(R.string.log_night_takeover))
        if (night() != StartResult.STARTED) return
        val state = current(context) ?: return
        if (wasAsleep) markAsleep(context, state, now())
    }

    fun onWokeUp(context: Context) {
        val now = now()
        EventLog.add(context, context.getString(R.string.log_woke_up))
        prefs(context).edit().putLong("last_woke_up", now.toEpochMilli()).apply()
        val state = checkTimers(context, now)
        if (state == null) {
            // Awake after the alarm: no need for the phone to ring
            PhoneAlarm.onWokeUp(context)
            return onChanged(context)
        }
        if (state.mode != WakeMode.SLEEP_ONLY || state.asleepSince == null) return onChanged(context)

        val slept = state.sleptAt(now)
        if (slept >= state.target) {
            finish(context, state)
            PhoneAlarm.onWokeUp(context)
            return
        }
        // Fail-safe: the earliest the rest of the sleep could be done is if it started right now.
        // Without it a nap has no alarm while awake, and a night falls back to its end.
        val wakeAt = when {
            state.early -> wakeTime(now, state.target - slept, state.latest)
            state.kind == Kind.NIGHT -> state.latest
            else -> null
        }
        Settings(context).watchAddress?.let { if (!state.phoneOnly) moveAlarm(context, it, state.wakeAt, wakeAt) }
        EventLog.add(
            context,
            if (wakeAt != null) {
                context.getString(R.string.log_paused_alarm, slept.toMinutes(), state.target.toMinutes(), Format.time(context, wakeAt))
            } else {
                context.getString(R.string.log_paused, slept.toMinutes(), state.target.toMinutes())
            }
        )
        val updated = state.copy(slept = slept, asleepSince = null, wakeAt = wakeAt)
        save(context, updated)
        Scheduler.scheduleSession(context, updated)
        PhoneAlarm.sync(context, updated)
    }

    /**
     * Applies whatever is due: the alarm having gone off, the session's deadline, the night
     * being over, or a pending cleanup. Returns the session if it is still active.
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
            cancel(context, context.getString(R.string.log_night_over))
            return null
        }
        return state
    }

    /** The alarm went off: the session is over, its watch alarm is removed a little later. */
    private fun finish(context: Context, state: State) {
        val wakeAt = state.wakeAt ?: now()
        EventLog.add(context, context.getString(R.string.log_finished))
        if (state.kind == Kind.NAP) NapLedger.add(context, state.sleptAt(wakeAt))
        if (!state.phoneOnly) {
            prefs(context).edit().putLong("cleanup_at", wakeAt.plus(CLEANUP_DELAY).toEpochMilli()).apply()
        }
        save(context, null)
        Scheduler.scheduleSession(context, null)
        // The phone backup alarm stays scheduled: a woke-up event will cancel it
    }

    /** Removes a finished session's alarm from the watch, freeing its slot. */
    private fun cleanup(context: Context) {
        if (pendingCleanup(context) == null) return
        prefs(context).edit().remove("cleanup_at").apply()
        Settings(context).watchAddress?.let { Gadgetbridge.dismissAlarms(context, it) }
    }

    /** Gadgetbridge can't move an alarm, so a move is a dismissal followed by a new alarm. */
    private fun moveAlarm(context: Context, address: String, from: Instant?, to: Instant?) {
        if (from == to) return
        if (from != null) Gadgetbridge.dismissAlarms(context, address)
        if (to != null) Gadgetbridge.setAlarm(context, address, to)
    }

    private fun withdraw(context: Context, address: String, state: State?) {
        if (state?.wakeAt != null && !state.phoneOnly) Gadgetbridge.dismissAlarms(context, address)
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
        val mode = context.getString(
            when (state.mode) {
                WakeMode.SLEEP_ONLY -> R.string.mode_sleep_only
                WakeMode.FROM_FIRST_SLEEP -> R.string.mode_from_first_sleep
                WakeMode.FIXED -> if (state.kind == Kind.NAP) R.string.mode_countdown else R.string.mode_night_end
            }
        )
        return "$kind, ${Format.duration(context, state.target)}, $mode"
    }

    private val listeners = mutableSetOf<() -> Unit>()

    fun addListener(listener: () -> Unit) = listeners.add(listener)

    fun removeListener(listener: () -> Unit) = listeners.remove(listener)

    fun onChanged(context: Context) {
        Log.d(TAG, "Session changed: ${current(context)}")
        TileService.requestListeningState(context, ComponentName(context, WakeTileService::class.java))
        listeners.toList().forEach { it() }
    }
}
