package io.github.luccoj.wakebridge

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.util.Date

object Format {
    fun time(context: Context, instant: Instant): String =
        DateFormat.getTimeFormat(context).format(Date.from(instant))

    fun time(context: Context, time: LocalTime): String =
        DateFormat.getTimeFormat(context).format(
            Date.from(time.atDate(java.time.LocalDate.now()).atZone(java.time.ZoneId.systemDefault()).toInstant())
        )

    fun dateTime(context: Context, instant: Instant): String =
        DateUtils.formatDateTime(
            context, instant.toEpochMilli(),
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
        )

    /** "45 min", "7 h", "7 h 30 min". */
    fun duration(context: Context, duration: Duration): String = minutes(context, duration.toMinutes().toInt())

    fun minutes(context: Context, total: Int): String {
        val hours = total / 60
        val minutes = total % 60
        return when {
            hours == 0 -> context.getString(R.string.format_minutes, minutes)
            minutes == 0 -> context.getString(R.string.format_hours, hours)
            else -> context.getString(R.string.format_hours_minutes, hours, minutes)
        }
    }

    /** A short status line, shared by the main screen and the tile's subtitle. */
    fun status(context: Context, state: Session.State?, now: Instant = Session.now()): String {
        if (state == null) return context.getString(R.string.status_idle)
        val slept = state.sleptAt(now).toMinutes().toInt()
        val alarm = state.wakeAt?.let { time(context, it) }
        return when {
            !state.sleepOnly -> context.getString(R.string.status_alarm_at, alarm)
            state.asleepSince != null -> context.getString(R.string.status_asleep, alarm)
            slept == 0 && alarm != null -> context.getString(R.string.status_waiting_alarm, alarm)
            slept == 0 -> context.getString(R.string.status_waiting)
            alarm != null -> context.getString(
                R.string.status_waiting_slept_alarm, minutes(context, slept), duration(context, state.target), alarm
            )
            else -> context.getString(
                R.string.status_waiting_slept, minutes(context, slept), duration(context, state.target)
            )
        }
    }
}
