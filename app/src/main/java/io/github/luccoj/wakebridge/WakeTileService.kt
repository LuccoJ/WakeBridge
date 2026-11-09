package io.github.luccoj.wakebridge

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import java.time.Duration

/**
 * Tap: start the Day pane's nap at once. Tap during a session: cancel it, with a few seconds
 * of "Tap to undo" instead of a confirmation (dialogs from tiles are slow and close the panel).
 * Long press: Android opens [MainActivity], which handles QS_TILE_PREFERENCES.
 */
class WakeTileService : TileService() {
    private val handler = Handler(Looper.getMainLooper())
    private val listener = { refresh(); Unit }

    override fun onStartListening() {
        super.onStartListening()
        Session.addListener(listener)
        refresh()
    }

    override fun onStopListening() {
        Session.removeListener(listener)
        handler.removeCallbacksAndMessages(null)
        super.onStopListening()
    }

    override fun onClick() {
        val settings = Settings(this)
        when {
            Session.checkTimers(this) != null -> Session.cancel(this, getString(R.string.log_cancelled), undoable = true)
            Session.undoUntil(this) != null -> Session.undo(this)
            else -> {
                val result = Session.start(
                    this, Session.Kind.NAP, Duration.ofMinutes(settings.napMinutes.toLong()), settings.dayWakeMode
                )
                if (result == Session.StartResult.NO_WATCH) openApp(Intent(this, SetupActivity::class.java))
            }
        }
        refresh()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val settings = Settings(this)
        val state = Session.checkTimers(this)
        val undoUntil = Session.undoUntil(this)
        handler.removeCallbacksAndMessages(null)
        tile.label = getString(if (state?.kind == Session.Kind.NIGHT) R.string.tile_label_night else R.string.tile_label)
        val subtitle: String
        when {
            state != null -> {
                tile.state = Tile.STATE_ACTIVE
                subtitle = Format.status(this, state)
            }
            undoUntil != null -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = getString(R.string.tile_cancelled)
                subtitle = getString(R.string.tile_undo)
                // Back to normal when the undo time is over
                handler.postDelayed({ refresh() }, Duration.between(Session.now(), undoUntil).toMillis() + 200)
            }
            settings.watchAddress == null -> {
                tile.state = Tile.STATE_INACTIVE
                subtitle = getString(R.string.tile_setup_needed)
            }
            Session.canStart(this, settings.dayWakeMode) == Session.StartResult.DISCONNECTED -> {
                tile.state = Tile.STATE_UNAVAILABLE
                subtitle = getString(R.string.tile_disconnected)
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                subtitle = getString(
                    if (settings.dayWakeMode == WakeMode.FIXED) R.string.tile_idle_countdown else R.string.tile_idle_sleep_only,
                    Format.minutes(this, settings.napMinutes)
                )
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle
        } else {
            tile.label = "${tile.label}: $subtitle"
        }
        tile.updateTile()
    }
}
