package io.github.luccoj.wakebridge

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.time.temporal.ChronoUnit

/** How to set up Gadgetbridge and the phone, plus checks that events and alarms get through. */
class SetupActivity : AppCompatActivity() {
    private lateinit var settings: Settings
    private val listener = { runOnUiThread { refresh() } }
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

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
            startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_SETTINGS, true))
        }
        findViewById<Button>(R.id.test_alarm).setOnClickListener { testAlarm() }
        findViewById<Button>(R.id.remove_test_alarm).setOnClickListener {
            val address = settings.watchAddress ?: return@setOnClickListener
            Gadgetbridge.dismissAlarms(this, address, Gadgetbridge.TEST_ALARM_TITLE)
            EventLog.add(this, getString(R.string.log_test_alarm_removed))
            refresh()
        }
        // Act as if Gadgetbridge had sent its broadcast, through the same receiver
        findViewById<Button>(R.id.simulate_asleep).setOnClickListener { simulate(Gadgetbridge.ACTION_FELL_ASLEEP) }
        findViewById<Button>(R.id.simulate_awake).setOnClickListener { simulate(Gadgetbridge.ACTION_WOKE_UP) }
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

    private fun simulate(action: String) {
        EventLog.add(this, getString(R.string.log_simulated))
        SleepEventReceiver().onReceive(this, Intent(action))
        refresh()
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
        refreshPermissions()
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    /** What the phone has to allow, each with its state and a way to allow it. */
    @SuppressLint("BatteryLife")
    private fun refreshPermissions() {
        val rows = findViewById<LinearLayout>(R.id.permissions)
        rows.removeAllViews()

        fun row(title: Int, ok: Boolean, optional: Boolean = false, action: () -> Unit) {
            val view = LayoutInflater.from(this).inflate(R.layout.permission_row, rows, false)
            view.findViewById<TextView>(R.id.permission_title).setText(title)
            view.findViewById<TextView>(R.id.permission_status).setText(
                when {
                    ok -> R.string.permission_ok
                    optional -> R.string.permission_optional
                    else -> R.string.permission_missing
                }
            )
            view.findViewById<Button>(R.id.permission_button).apply {
                isEnabled = !ok
                setOnClickListener { action() }
            }
            rows.addView(view)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            row(R.string.permission_notifications, granted(Manifest.permission.POST_NOTIFICATIONS)) {
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            row(R.string.permission_exact_alarms, getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
                startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            }
        }
        if (Build.VERSION.SDK_INT >= 34) {
            row(R.string.permission_full_screen, getSystemService(NotificationManager::class.java).canUseFullScreenIntent()) {
                startActivity(Intent(AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            row(R.string.permission_nearby, granted(Manifest.permission.BLUETOOTH_CONNECT)) {
                permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        val power = getSystemService(PowerManager::class.java)
        row(R.string.permission_battery, power.isIgnoringBatteryOptimizations(packageName), optional = true) {
            startActivity(Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }
    }
}
