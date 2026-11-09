package io.github.luccoj.wakebridge

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import java.time.Duration
import java.time.Instant

/** Status and nap controls. Also opened by long-pressing the Quick Settings tile. */
class MainActivity : AppCompatActivity() {
    private lateinit var settings: Settings
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 15_000)
        }
    }
    private val listener = { runOnUiThread { refresh() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        settings = Settings(this)

        val slider = findViewById<Slider>(R.id.nap_slider)
        slider.value = settings.napMinutes.coerceIn(5, 180).toFloat()
        slider.addOnChangeListener { _, value, _ ->
            settings.napMinutes = value.toInt()
            refresh()
        }
        val sleepOnly = findViewById<MaterialSwitch>(R.id.nap_sleep_only)
        sleepOnly.isChecked = settings.napSleepOnly
        sleepOnly.setOnCheckedChangeListener { _, checked ->
            settings.napSleepOnly = checked
            refresh()
        }
        findViewById<Button>(R.id.start_stop).setOnClickListener { startOrStop() }
        findViewById<Button>(R.id.setup_button).setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        Session.addListener(listener)
        handler.post(ticker)
    }

    override fun onPause() {
        Session.removeListener(listener)
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    private fun startOrStop() {
        if (Session.checkTimers(this) != null) {
            Session.cancel(this, getString(R.string.log_cancelled))
        } else if (!Session.start(
                this, Session.Kind.NAP, Duration.ofMinutes(settings.napMinutes.toLong()), settings.napSleepOnly
            )
        ) {
            startActivity(Intent(this, SetupActivity::class.java))
        }
        refresh()
    }

    private fun refresh() {
        val now = Session.now()
        val state = Session.checkTimers(this, now)
        findViewById<View>(R.id.setup_card).visibility =
            if (settings.watchAddress == null) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.status).text = Format.status(this, state, now)
        findViewById<TextView>(R.id.status_details).text = details(state, now)
        findViewById<Button>(R.id.start_stop).text = getString(
            if (state != null) R.string.cancel else R.string.start_nap
        )
        findViewById<TextView>(R.id.nap_length).text = getString(
            R.string.nap_length, Format.minutes(this, settings.napMinutes)
        )
    }

    private fun details(state: Session.State?, now: Instant): String {
        if (state == null) {
            return if (settings.nightEnabled) {
                getString(
                    R.string.details_night_scheduled,
                    Format.time(this, settings.nightArmTime),
                    Format.minutes(this, settings.nightMinutes)
                )
            } else {
                ""
            }
        }
        val lines = mutableListOf(Session.describe(this, state))
        if (state.sleepOnly) {
            lines += getString(
                R.string.details_slept,
                Format.minutes(this, state.sleptAt(now).toMinutes().toInt()),
                Format.duration(this, state.target)
            )
        }
        state.latest?.let { lines += getString(R.string.details_latest, Format.time(this, it)) }
        state.deadline?.let { lines += getString(R.string.details_deadline, Format.time(this, it)) }
        return lines.joinToString("\n")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_setup -> startActivity(Intent(this, SetupActivity::class.java))
            R.id.menu_settings -> startActivity(Intent(this, SettingsActivity::class.java))
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }
}
