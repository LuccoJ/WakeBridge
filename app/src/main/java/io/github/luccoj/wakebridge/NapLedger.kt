package io.github.luccoj.wakebridge

import android.content.Context
import java.time.Duration
import java.time.Instant

/** Nap sleep since the last night's end, for "Include today's naps". */
object NapLedger {
    private fun prefs(context: Context) = context.getSharedPreferences("nap_ledger", Context.MODE_PRIVATE)

    /** The day naps are counted for: from the most recent night end. */
    private fun dayStart(context: Context, now: Instant = Session.now()): Instant =
        Scheduler.previousOccurrence(Settings(context).nightEnd, now)

    fun add(context: Context, sleep: Duration) {
        if (sleep.isZero) return
        val day = dayStart(context).toEpochMilli()
        val p = prefs(context)
        val total = if (p.getLong("day", 0) == day) p.getLong("total", 0) else 0
        p.edit().putLong("day", day).putLong("total", (total + sleep.toMillis()).coerceAtLeast(0)).apply()
    }

    fun today(context: Context): Duration {
        val p = prefs(context)
        return if (p.getLong("day", 0) == dayStart(context).toEpochMilli()) Duration.ofMillis(p.getLong("total", 0)) else Duration.ZERO
    }
}
