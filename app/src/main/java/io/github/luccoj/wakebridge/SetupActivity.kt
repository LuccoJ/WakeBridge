package io.github.luccoj.wakebridge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.time.Instant
import java.time.temporal.ChronoUnit

/** How to set up Gadgetbridge, plus checks that its events and alarms get through. */
class SetupActivity : AppCompatActivity() {
    private lateinit var settings: Settings
    private val listener = { runOnUiThread { refresh() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        settings = Settings(this)

        findViewById<TextView>(R.id.package_name).text = packageName
        findViewById<Button>(R.id.copy_package).setOnClickListener {
            getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), packageName))
            Toast.makeText(this, R.string.setup_copied, Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.choose_watch).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.test_alarm).setOnClickListener { testAlarm() }
        findViewById<Button>(R.id.remove_test_alarm).setOnClickListener {
            val address = settings.watchAddress ?: return@setOnClickListener
            Gadgetbridge.dismissAlarms(this, address, Gadgetbridge.TEST_ALARM_TITLE)
            EventLog.add(this, getString(R.string.log_test_alarm_removed))
            refresh()
        }
        findViewById<Button>(R.id.clear_log).setOnClickListener {
            EventLog.clear(this)
            refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        Session.addListener(listener)
        refresh()
    }

    override fun onPause() {
        Session.removeListener(listener)
        super.onPause()
    }

    private fun testAlarm() {
        val address = settings.watchAddress
        if (address == null) {
            Toast.makeText(this, R.string.setup_watch_none, Toast.LENGTH_LONG).show()
            return
        }
        val at = Session.now().plus(2, ChronoUnit.MINUTES).plus(59, ChronoUnit.SECONDS).truncatedTo(ChronoUnit.MINUTES)
        Gadgetbridge.setAlarm(this, address, at, Gadgetbridge.TEST_ALARM_TITLE)
        EventLog.add(this, getString(R.string.log_test_alarm, Format.time(this, at)))
        refresh()
    }

    private fun refresh() {
        findViewById<TextView>(R.id.watch_status).text = settings.watchAddress?.let { address ->
            getString(R.string.setup_watch_chosen, settings.watchName?.let { "$it ($address)" } ?: address)
        } ?: getString(R.string.setup_watch_none)
        val never = getString(R.string.setup_never)
        findViewById<TextView>(R.id.events_status).text = getString(
            R.string.setup_events_status,
            Session.lastFellAsleep(this)?.let { Format.dateTime(this, it) } ?: never,
            Session.lastWokeUp(this)?.let { Format.dateTime(this, it) } ?: never,
        )
        findViewById<TextView>(R.id.event_log).text =
            EventLog.format(this).ifEmpty { getString(R.string.setup_log_empty) }
    }
}
