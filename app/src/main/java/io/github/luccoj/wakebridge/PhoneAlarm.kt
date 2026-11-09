package io.github.luccoj.wakebridge

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.time.Duration
import java.time.Instant

/**
 * The phone backup alarm: it rings on the phone a few minutes after the watch alarm, unless the
 * watch reports waking up first. When the watch was out of reach at the start, the phone is the
 * only alarm and rings at the alarm time itself.
 */
object PhoneAlarm {
    private const val ACTION_RING = "io.github.luccoj.wakebridge.RING"
    const val CHANNEL = "alarm"
    const val NOTIFICATION_ID = 1
    val SNOOZE: Duration = Duration.ofMinutes(5)

    private fun prefs(context: Context) = context.getSharedPreferences("phone_alarm", Context.MODE_PRIVATE)

    fun pendingAt(context: Context): Instant? =
        prefs(context).getLong("at", 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli)

    /** Keeps the phone alarm in step with the session's watch alarm. */
    fun sync(context: Context, state: Session.State?) {
        val settings = Settings(context)
        val wakeAt = state?.wakeAt
        if (wakeAt == null || !(settings.phoneBackup || state.phoneOnly)) {
            cancel(context)
            return
        }
        val delay = if (state.phoneOnly) Duration.ZERO else Duration.ofMinutes(settings.backupDelayMinutes.toLong())
        schedule(context, wakeAt.plus(delay))
    }

    fun schedule(context: Context, at: Instant) {
        prefs(context).edit().putLong("at", at.toEpochMilli()).apply()
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val operation = ringIntent(context)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            val show = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(at.toEpochMilli(), show), operation)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), operation)
        }
    }

    fun cancel(context: Context) {
        if (pendingAt(context) == null) return
        prefs(context).edit().clear().apply()
        context.getSystemService(AlarmManager::class.java).cancel(ringIntent(context))
    }

    /** The watch reported waking up: the phone needn't ring. */
    fun onWokeUp(context: Context) {
        if (pendingAt(context) == null) return
        cancel(context)
        EventLog.add(context, context.getString(R.string.log_backup_cancelled))
    }

    private fun ringIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 1, Intent(context, Receiver::class.java).setAction(ACTION_RING),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_alarm), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null) // the ringing screen plays the alarm sound
                enableVibration(false)
            }
        )
    }

    /** Rings: a full-screen notification opens [RingActivity], which plays the sound. */
    fun ring(context: Context) {
        prefs(context).edit().clear().apply()
        EventLog.add(context, context.getString(R.string.log_backup_ringing))
        ensureChannel(context)
        val full = PendingIntent.getActivity(
            context, 2, Intent(context, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(context.getString(R.string.ring_title))
            .setContentText(context.getString(R.string.ring_text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .setOngoing(true)
            .build()
        try {
            context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // No notification permission: try opening the ringing screen directly
            context.startActivity(Intent(context, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_RING) ring(context)
        }
    }
}
