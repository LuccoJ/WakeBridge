package io.github.luccoj.wakebridge

import android.app.TimePickerDialog
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import java.time.Duration
import java.time.LocalTime

/** The Day or the Night pane: they mirror each other. */
class PaneFragment : Fragment() {
    private val night: Boolean
        get() = requireArguments().getBoolean(ARG_NIGHT)

    private lateinit var settings: Settings
    private lateinit var root: View
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 30_000)
        }
    }
    private val listener = { activity?.runOnUiThread { refresh() }; Unit }

    /** The connection chip follows the watch connecting and disconnecting. */
    private val bluetooth = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refresh()
            // The connected-devices list can lag the broadcast a little
            handler.postDelayed({ refresh() }, 1500)
        }
    }
    private var binding = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_pane, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        root = view
        settings = Settings(requireContext())
        view.findViewById<TextView>(R.id.title).setText(if (night) R.string.tab_night else R.string.tab_day)
        view.findViewById<RoundButton>(R.id.round_button).icon =
            ContextCompat.getDrawable(requireContext(), R.drawable.ic_night)

        view.findViewById<MaterialButtonToggleGroup>(R.id.every_toggle).addOnButtonCheckedListener { _, id, checked ->
            if (binding || !checked) return@addOnButtonCheckedListener
            val every = id == R.id.every
            if (night) {
                settings.nightEvery = every
                if (every) settings.nightTonight = false
            } else {
                settings.dayEvery = every
            }
            Scheduler.scheduleNight(requireContext())
            refresh()
        }
        val slider = view.findViewById<Slider>(R.id.amount)
        slider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            if (night) settings.nightMinutes = value.toInt() else settings.napMinutes = value.toInt()
            // While dragging only the amount and the preview change; the rest on release
            view.findViewById<TextView>(R.id.amount_value).text = Format.minutes(requireContext(), value.toInt())
            view.findViewById<TimelineView>(R.id.timeline).previewMinutes = value.toInt()
        }
        slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) = Unit

            override fun onStopTrackingTouch(slider: Slider) {
                if (night && slider.value >= slider.valueTo) {
                    Toast.makeText(requireContext(), R.string.toast_widen_night, Toast.LENGTH_SHORT).show()
                }
                refresh()
            }
        })
        view.findViewById<RadioGroup>(R.id.wake_mode).setOnCheckedChangeListener { _, id ->
            if (binding) return@setOnCheckedChangeListener
            val mode = when (id) {
                R.id.mode_from_first -> WakeMode.FROM_FIRST_SLEEP
                R.id.mode_fixed -> WakeMode.FIXED
                else -> WakeMode.SLEEP_ONLY
            }
            if (night) settings.nightWakeMode = mode else settings.dayWakeMode = mode
            refresh()
        }
        view.findViewById<MaterialSwitch>(R.id.include_naps).setOnCheckedChangeListener { _, checked ->
            if (!binding) settings.nightIncludeNaps = checked
        }
        view.findViewById<RoundButton>(R.id.round_button).setOnClickListener { onRoundButton() }

        val timeline = view.findViewById<TimelineView>(R.id.timeline)
        timeline.listener = object : TimelineView.Listener {
            override fun onWindowChanged(start: LocalTime, end: LocalTime, done: Boolean) {
                settings.nightStart = start
                settings.nightEnd = end
                if (done) windowChanged()
                refresh()
            }

            override fun onThresholdTapped(start: Boolean) = pickThreshold(start)

            override fun onWindowLimit() {
                Toast.makeText(requireContext(), R.string.toast_shorten_night, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        arrangeTimeline()
        Session.addListener(listener)
        handler.post(ticker)
        ContextCompat.registerReceiver(
            requireContext(), bluetooth,
            IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            arrangeTimeline()
            refresh()
        }
    }

    override fun onPause() {
        Session.removeListener(listener)
        handler.removeCallbacksAndMessages(null)
        requireContext().unregisterReceiver(bluetooth)
        super.onPause()
    }

    /** The timeline goes on the side chosen in Settings. */
    private fun arrangeTimeline() {
        val row = root.findViewById<LinearLayout>(R.id.row)
        val timeline = root.findViewById<TimelineView>(R.id.timeline)
        val left = settings.timelineOnLeft
        row.removeView(timeline)
        row.addView(timeline, if (left) 0 else 1)
        val params = timeline.layoutParams as LinearLayout.LayoutParams
        val gap = resources.getDimensionPixelSize(R.dimen.timeline_gap)
        params.marginStart = if (left) 0 else gap
        params.marginEnd = if (left) gap else 0
        timeline.layoutParams = params
        val side = resources.getDimensionPixelSize(R.dimen.pane_side)
        val edge = resources.getDimensionPixelSize(R.dimen.pane_edge)
        row.setPaddingRelative(if (left) edge else side, 0, if (left) side else edge, row.paddingBottom)
        timeline.innerOnLeft = !left
    }

    private fun windowChanged() {
        // The sleep can't be longer than the night
        settings.nightMinutes = settings.nightMinutes
        Scheduler.scheduleNight(requireContext())
    }

    private fun pickThreshold(start: Boolean) {
        val current = if (start) settings.nightStart else settings.nightEnd
        TimePickerDialog(requireContext(), { _, hour, minute ->
            val time = LocalTime.of(hour, minute)
            val newStart = if (start) time else settings.nightStart
            val newEnd = if (start) settings.nightEnd else time
            val length = Math.floorMod(newEnd.toSecondOfDay() / 60 - newStart.toSecondOfDay() / 60, 24 * 60)
            val startAxis = Math.floorMod(newStart.toSecondOfDay() / 60 - 12 * 60, 24 * 60)
            val endAxis = Math.floorMod(newEnd.toSecondOfDay() / 60 - 12 * 60, 24 * 60).let { if (it == 0) 1440 else it }
            if (length < 60 || startAxis >= endAxis) {
                Toast.makeText(requireContext(), R.string.night_window_invalid, Toast.LENGTH_LONG).show()
                return@TimePickerDialog
            }
            if (length < settings.nightMinutes) {
                Toast.makeText(requireContext(), R.string.toast_shorten_night, Toast.LENGTH_LONG).show()
                return@TimePickerDialog
            }
            settings.nightStart = newStart
            settings.nightEnd = newEnd
            windowChanged()
            refresh()
        }, current.hour, current.minute, DateFormat.is24HourFormat(requireContext())).show()
    }

    private fun onRoundButton() {
        val context = requireContext()
        val state = Session.checkTimers(context)
        if (night) {
            when {
                state?.kind == Session.Kind.NIGHT -> {
                    Session.cancel(context, getString(R.string.log_cancelled))
                    if (settings.nightEvery) {
                        toast(getString(R.string.toast_skipped, Format.time(context, settings.nightStart)))
                    }
                }
                settings.nightEvery -> toast(getString(R.string.night_every_hint, Format.time(context, settings.nightStart)))
                settings.nightTonight -> Scheduler.disarmTonight(context)
                else -> Scheduler.armTonight(context)
            }
        } else {
            if (state?.kind == Session.Kind.NAP) {
                Session.cancel(context, getString(R.string.log_cancelled))
            } else if (state?.kind == Session.Kind.NIGHT) {
                toast(getString(R.string.toast_night_running))
            } else if (settings.dayEvery) {
                toast(getString(R.string.toast_auto_nap))
            } else {
                val result = Session.start(
                    context, Session.Kind.NAP, Duration.ofMinutes(settings.napMinutes.toLong()), settings.dayWakeMode
                )
                when (result) {
                    Session.StartResult.NO_WATCH -> startActivity(Intent(context, SetupActivity::class.java))
                    Session.StartResult.DISCONNECTED ->
                        Toast.makeText(context, R.string.disconnected_hint, Toast.LENGTH_LONG).show()
                    Session.StartResult.STARTED -> Unit
                }
            }
        }
        refresh()
    }

    private fun refresh() {
        if (!isAdded) return
        val context = requireContext()
        val now = Session.now()
        val state = Session.checkTimers(context, now)
        val connection = WatchConnection.state(context)
        binding = true

        // Connection chip
        // Connection chip: Bluetooth icon and the watch's name; "connected" only for screen readers
        root.findViewById<TextView>(R.id.connection).apply {
            val name = settings.watchName ?: getString(R.string.watch)
            when {
                settings.watchAddress == null -> {
                    text = getString(R.string.chip_no_watch)
                    contentDescription = text
                    chip(R.drawable.ic_bluetooth_off, R.color.chip_neutral_bg, R.color.chip_neutral_fg)
                }
                connection == WatchConnection.State.CONNECTED -> {
                    text = name
                    contentDescription = getString(R.string.chip_connected, name)
                    chip(R.drawable.ic_bluetooth, R.color.chip_ok_bg, R.color.chip_ok_fg)
                }
                connection == WatchConnection.State.DISCONNECTED -> {
                    text = name
                    contentDescription = getString(R.string.chip_disconnected, name)
                    chip(R.drawable.ic_bluetooth_off, R.color.chip_error_bg, R.color.chip_error_fg)
                }
                else -> {
                    text = name
                    contentDescription = name
                    chip(R.drawable.ic_bluetooth, R.color.chip_neutral_bg, R.color.chip_neutral_fg)
                }
            }
        }

        // Status card
        val card = root.findViewById<MaterialCardView>(R.id.status_card)
        val mine = state?.kind == (if (night) Session.Kind.NIGHT else Session.Kind.NAP)
        val dark = night && (mine || settings.nightEvery || settings.nightTonight)
        card.setCardBackgroundColor(context.getColor(if (dark) R.color.night else R.color.card))
        val fg = context.getColor(if (dark) R.color.on_night else R.color.on_surface)
        val fgSoft = context.getColor(if (dark) R.color.on_night_soft else R.color.on_surface_soft)
        root.findViewById<TextView>(R.id.status).setTextColor(fg)
        root.findViewById<TextView>(R.id.status_details).setTextColor(fgSoft)
        val (title, details) = statusTexts(state, mine, connection, now)
        root.findViewById<TextView>(R.id.status).text = title
        // Always two lines, so nothing below moves
        root.findViewById<TextView>(R.id.status_details).text = details.ifEmpty { " " }

        // Once / every
        root.findViewById<TextView>(R.id.once).setText(R.string.mode_manual)
        root.findViewById<TextView>(R.id.every).setText(R.string.mode_auto)
        val every = if (night) settings.nightEvery else settings.dayEvery
        root.findViewById<MaterialButtonToggleGroup>(R.id.every_toggle).check(if (every) R.id.every else R.id.once)
        root.findViewById<TextView>(R.id.mode_caption).text = when {
            night && every -> getString(R.string.caption_every_night, Format.time(context, settings.nightStart))
            night -> getString(R.string.caption_tonight)
            every -> getString(R.string.caption_every_nap)
            else -> getString(R.string.caption_next_nap)
        }

        // Sleep amount
        val minutes = if (night) settings.nightMinutes else settings.napMinutes
        root.findViewById<Slider>(R.id.amount).apply {
            if (night) {
                val max = (settings.nightWindow.toMinutes().toInt() / 5 * 5).coerceAtLeast(35)
                valueFrom = 30f
                valueTo = max.toFloat()
            } else {
                valueFrom = 5f
                valueTo = 180f
            }
            value = (minutes / 5 * 5).toFloat().coerceIn(valueFrom, valueTo)
        }
        root.findViewById<TextView>(R.id.amount_value).text = Format.minutes(context, minutes)

        // Wake me
        val amount = Format.minutes(context, minutes)
        root.findViewById<RadioButton>(R.id.mode_sleep_only).text = getString(R.string.wake_sleep_only, amount)
        root.findViewById<RadioButton>(R.id.mode_from_first).text = getString(R.string.wake_from_first, amount)
        root.findViewById<RadioButton>(R.id.mode_fixed).text = if (night) {
            getString(R.string.wake_night_end, Format.time(context, settings.nightEnd))
        } else {
            getString(R.string.wake_countdown, amount)
        }
        val mode = if (night) settings.nightWakeMode else settings.dayWakeMode
        root.findViewById<RadioGroup>(R.id.wake_mode).check(
            when (mode) {
                WakeMode.SLEEP_ONLY -> R.id.mode_sleep_only
                WakeMode.FROM_FIRST_SLEEP -> R.id.mode_from_first
                WakeMode.FIXED -> R.id.mode_fixed
            }
        )
        // Extra row, the same height in both panes
        root.findViewById<TextView>(R.id.extra_label).setText(if (night) R.string.include_naps else R.string.napped_today)
        root.findViewById<MaterialSwitch>(R.id.include_naps).apply {
            visibility = if (night) View.VISIBLE else View.GONE
            isChecked = settings.nightIncludeNaps
        }
        root.findViewById<TextView>(R.id.extra_value).apply {
            visibility = if (night) View.GONE else View.VISIBLE
            text = Format.duration(context, NapLedger.today(context))
        }

        // Round button
        root.findViewById<RoundButton>(R.id.round_button).apply {
            val disconnectedBlocks = Session.canStart(context, mode) == Session.StartResult.DISCONNECTED
            isClickable = true
            if (night) {
                when {
                    mine -> {
                        look = RoundButton.Look.RUNNING
                        label = getString(if (settings.nightEvery) R.string.skip_tonight else R.string.stop)
                    }
                    // Automatic: greyed until the night starts, then it pulses
                    settings.nightEvery -> {
                        look = RoundButton.Look.DISABLED
                        label = getString(R.string.from_time, Format.time(context, settings.nightStart))
                    }
                    settings.nightTonight -> { look = RoundButton.Look.RUNNING; label = getString(R.string.disarm) }
                    else -> { look = RoundButton.Look.IDLE; label = getString(R.string.arm_tonight) }
                }
            } else {
                when {
                    mine -> { look = RoundButton.Look.RUNNING; label = getString(R.string.stop_nap) }
                    // Automatic: greyed while waiting for sleep, pulsing once a nap starts by itself
                    settings.dayEvery && state?.kind != Session.Kind.NIGHT -> {
                        look = RoundButton.Look.DISABLED; label = getString(R.string.detecting)
                    }
                    state?.kind == Session.Kind.NIGHT || disconnectedBlocks -> {
                        look = RoundButton.Look.DISABLED; label = getString(R.string.start_nap)
                    }
                    else -> { look = RoundButton.Look.IDLE; label = getString(R.string.start_nap) }
                }
            }
        }

        // Timeline
        root.findViewById<TimelineView>(R.id.timeline).apply {
            nightStart = settings.nightStart
            nightEnd = settings.nightEnd
            focusNight = night
            previewMinutes = minutes
            minWindowMinutes = settings.nightMinutes
            this.now = now.atZone(Session.clock.zone).toLocalTime()
        }
        binding = false
    }

    private fun toast(text: String) = Toast.makeText(requireContext(), text, Toast.LENGTH_LONG).show()

    private fun TextView.chip(icon: Int, bg: Int, fg: Int) {
        backgroundTintList = ColorStateList.valueOf(context.getColor(bg))
        setTextColor(context.getColor(fg))
        val drawable = ContextCompat.getDrawable(context, icon)?.mutate()
        val size = (textSize * 1.2f).toInt()
        drawable?.setBounds(0, 0, size, size)
        setCompoundDrawablesRelative(drawable, null, null, null)
        TextViewCompat.setCompoundDrawableTintList(this, ColorStateList.valueOf(context.getColor(fg)))
    }

    /** The status card: a short state, then the numbers with the alarm time first. */
    private fun statusTexts(
        state: Session.State?, mine: Boolean, connection: WatchConnection.State, now: java.time.Instant,
    ): Pair<String, String> {
        val context = requireContext()
        if (mine && state != null) return sessionTexts(state, now)
        val window = getString(
            R.string.detail_night_window, Format.time(context, settings.nightStart),
            Format.time(context, settings.nightEnd), Format.duration(context, settings.nightWindow)
        )
        if (night) {
            return when {
                settings.nightEvery -> getString(R.string.status_every_night) to window
                settings.nightTonight -> getString(R.string.status_armed_tonight) to window
                else -> getString(R.string.status_no_night) to window
            }
        }
        if (state?.kind == Session.Kind.NIGHT) {
            return getString(R.string.status_night_active) to
                getString(R.string.detail_naps_after_night, Format.time(context, settings.nightEnd))
        }
        return when {
            settings.watchAddress == null -> getString(R.string.title_setup) to getString(R.string.detail_setup)
            connection == WatchConnection.State.DISCONNECTED && settings.dayWakeMode != WakeMode.FIXED ->
                getString(R.string.status_no_nap) to getString(R.string.detail_disconnected)
            settings.dayEvery -> getString(R.string.title_watching_naps) to getString(
                R.string.detail_nap_hours, Format.time(context, settings.nightEnd), Format.time(context, settings.nightStart)
            )
            else -> getString(R.string.status_no_nap) to getString(R.string.detail_manual_naps)
        }
    }

    private fun sessionTexts(state: Session.State, now: java.time.Instant): Pair<String, String> {
        val context = requireContext()
        val alarm = state.wakeAt?.let { Format.time(context, it) }
        val target = Format.duration(context, state.target)
        val slept = state.sleptAt(now).toMinutes().toInt()
        val title = getString(
            when {
                state.mode == WakeMode.FIXED -> if (state.kind == Session.Kind.NAP) R.string.title_countdown else R.string.title_night_on
                state.asleep -> R.string.title_asleep
                state.firstSleepAt == null && slept == 0 -> R.string.status_waiting
                else -> R.string.title_awake
            }
        )
        val details = when {
            state.phoneOnly && alarm != null -> getString(R.string.detail_phone, alarm)
            alarm == null && slept == 0 -> getString(R.string.detail_nap_no_alarm, target)
            alarm == null -> getString(R.string.detail_slept, Format.minutes(context, slept), target)
            state.counting && slept > 0 ->
                getString(R.string.detail_slept_alarm, alarm, Format.minutes(context, slept), target)
            state.kind == Session.Kind.NAP -> getString(R.string.detail_nap_alarm, alarm, target)
            else -> getString(R.string.detail_night_alarm, alarm, target)
        }
        return title to details
    }

    companion object {
        private const val ARG_NIGHT = "night"

        fun create(night: Boolean) = PaneFragment().apply {
            arguments = Bundle().apply { putBoolean(ARG_NIGHT, night) }
        }
    }
}
