package io.github.luccoj.wakebridge

import android.annotation.SuppressLint
import android.app.Dialog
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import java.time.Duration

/**
 * Tap while idle: a dialog over the Quick Settings panel to start a nap, or, with "One tap starts
 * the nap", start one right away with the current nap settings.
 * Tap during a session: confirm cancelling it.
 * Long press: Android opens [MainActivity], which handles QS_TILE_PREFERENCES.
 */
class WakeTileService : TileService() {
    private val listener = { refresh() }

    override fun onStartListening() {
        super.onStartListening()
        Session.addListener(listener)
        refresh()
    }

    override fun onStopListening() {
        Session.removeListener(listener)
        super.onStopListening()
    }

    override fun onClick() {
        val settings = Settings(this)
        val state = Session.checkTimers(this)
        when {
            settings.watchAddress == null -> openApp(Intent(this, SetupActivity::class.java))
            state != null -> showWhenUnlocked { confirmCancel(state) }
            settings.tileOneTap -> startNap()
            else -> showWhenUnlocked { napDialog() }
        }
        refresh()
    }

    private fun startNap() {
        val settings = Settings(this)
        Session.start(this, Session.Kind.NAP, Duration.ofMinutes(settings.napMinutes.toLong()), settings.napSleepOnly)
    }

    /** Dialogs over the panel can't be shown on a locked screen, so unlock first. */
    private fun showWhenUnlocked(create: () -> Dialog) {
        if (isLocked) unlockAndRun { showDialog(create()) } else showDialog(create())
    }

    private fun dialogBuilder() = MaterialAlertDialogBuilder(ContextThemeWrapper(this, R.style.Theme_WakeBridge))

    private fun napDialog(): Dialog {
        val settings = Settings(this)
        val builder = dialogBuilder()
        val view = LayoutInflater.from(builder.context).inflate(R.layout.dialog_nap, null)
        val length = view.findViewById<TextView>(R.id.nap_length)
        val showLength = { length.text = getString(R.string.nap_length, Format.minutes(this, settings.napMinutes)) }
        view.findViewById<Slider>(R.id.nap_slider).apply {
            value = settings.napMinutes.coerceIn(5, 180).toFloat()
            addOnChangeListener { _, value, _ ->
                settings.napMinutes = value.toInt()
                showLength()
            }
        }
        view.findViewById<MaterialSwitch>(R.id.nap_sleep_only).apply {
            isChecked = settings.napSleepOnly
            setOnCheckedChangeListener { _, checked -> settings.napSleepOnly = checked }
        }
        showLength()
        return builder
            .setTitle(R.string.kind_nap)
            .setView(view)
            .setPositiveButton(R.string.start_nap) { _, _ ->
                startNap()
                refresh()
            }
            .setNeutralButton(R.string.open_app) { _, _ -> openApp(Intent(this, MainActivity::class.java)) }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    private fun confirmCancel(state: Session.State): Dialog =
        dialogBuilder()
            .setTitle(Format.status(this, state))
            .setMessage(getString(R.string.tile_cancel_question, Session.describe(this, state)))
            .setPositiveButton(R.string.cancel) { _, _ ->
                Session.cancel(this, getString(R.string.log_cancelled))
                refresh()
            }
            .setNegativeButton(R.string.keep, null)
            .create()

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val state = Session.checkTimers(this)
        val settings = Settings(this)
        tile.state = if (state != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(
            if (state?.kind == Session.Kind.NIGHT) R.string.tile_label_night else R.string.tile_label
        )
        val subtitle = when {
            settings.watchAddress == null -> getString(R.string.tile_setup_needed)
            state != null -> Format.status(this, state)
            else -> getString(
                if (settings.napSleepOnly) R.string.tile_idle_sleep_only else R.string.tile_idle_countdown,
                Format.minutes(this, settings.napMinutes)
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle
        } else {
            tile.label = "${tile.label}: $subtitle"
        }
        tile.updateTile()
    }
}
