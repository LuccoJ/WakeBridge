package io.github.luccoj.wakebridge

import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** The phone backup alarm, ringing: sound and vibration until stopped, snoozed or timed out. */
class RingActivity : AppCompatActivity() {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_ring)

        findViewById<TextView>(R.id.ring_time).text = Format.time(this, Session.now())
        findViewById<Button>(R.id.ring_stop).setOnClickListener {
            EventLog.add(this, getString(R.string.log_backup_stopped))
            finish()
        }
        findViewById<Button>(R.id.ring_snooze).setOnClickListener {
            PhoneAlarm.schedule(this, Session.now().plus(PhoneAlarm.SNOOZE))
            EventLog.add(this, getString(R.string.log_backup_snoozed))
            finish()
        }
        startRinging()
        // Don't ring forever
        handler.postDelayed({ finish() }, 10 * 60 * 1000L)
    }

    private fun startRinging() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@RingActivity, uri)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
        @Suppress("DEPRECATION")
        vibrator = getSystemService(Vibrator::class.java)?.also {
            it.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0))
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.run { runCatching { stop() }; release() }
        vibrator?.cancel()
        getSystemService(NotificationManager::class.java).cancel(PhoneAlarm.NOTIFICATION_ID)
        super.onDestroy()
    }
}
