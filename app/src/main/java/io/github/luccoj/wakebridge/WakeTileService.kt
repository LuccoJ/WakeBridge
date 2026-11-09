package io.github.luccoj.wakebridge

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import java.time.Duration

/**
 * Tap: start a nap with the last used settings, or cancel the active session.
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
        Session.checkTimers(this)
        if (Session.current(this) != null) {
            Session.cancel(this, getString(R.string.log_cancelled))
        } else {
            val settings = Settings(this)
            val started = Session.start(
                this, Session.Kind.NAP, Duration.ofMinutes(settings.napMinutes.toLong()), settings.napSleepOnly
            )
            if (!started) openApp(Intent(this, SetupActivity::class.java))
        }
        refresh()
    }

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
