package io.github.luccoj.wakebridge

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.text.format.DateFormat
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.graphics.ColorUtils
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A vertical day: noon at the top to noon at the bottom, or midnight to midnight when the night
 * crosses noon (shift work), so the night never wraps around the ends. Its two thresholds, night
 * start and night end, can be dragged (in 15-minute steps) or tapped for an exact time. It shows
 * the focused sleep to scale; with [showBoth], the nap and the night's sleep together (for the
 * legend, [TimelineLegendView], which also explains the marks).
 */
class TimelineView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    interface Listener {
        /** A threshold moved; [done] when the drag ended. */
        fun onWindowChanged(start: LocalTime, end: LocalTime, done: Boolean)

        fun onThresholdTapped(start: Boolean)

        /** A threshold was dragged against [minWindowMinutes]: the window can't get shorter. */
        fun onWindowLimit()

        /** The timeline was touched: time to explain it. */
        fun onTouched()
    }

    /** The night can't get shorter than this (the night's sleep amount). */
    var minWindowMinutes: Int = 60

    var listener: Listener? = null

    /** Called whenever what the timeline shows changes, so the legend can follow. */
    var onChanged: (() -> Unit)? = null

    var nightStart: LocalTime = LocalTime.of(21, 0)
        set(value) { field = value; updateDescription(); changed() }
    var nightEnd: LocalTime = LocalTime.of(8, 0)
        set(value) { field = value; updateDescription(); changed() }

    /** Night pane: the night is the strong colour; Day pane: the day is. */
    var focusNight: Boolean = false
        set(value) { field = value; changed() }

    /** The nap's and the night's sleep length in minutes, drawn centred in their windows. */
    var napMinutes: Int = 20
        set(value) { field = value; changed() }
    var sleepMinutes: Int = 450
        set(value) { field = value; changed() }

    /** Both sleeps, not just the focused one: while the legend is shown. */
    var showBoth: Boolean = false
        set(value) { field = value; changed() }

    /** Which side the pane content is on (the legend's labels go there). */
    var innerOnLeft: Boolean = true
        set(value) { field = value; changed() }

    var now: LocalTime = LocalTime.now()
        set(value) { field = value; changed() }

    /** The threshold being dragged: true for night start, false for night end, null for none. */
    var dragging: Boolean? = null
        private set

    private fun changed() {
        invalidate()
        onChanged?.invoke()
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private val accent = context.getColor(R.color.accent_deep)
    private val sunColor = context.getColor(R.color.sun_glow)
    private val moonColor = context.getColor(R.color.moon)
    private val moon = Path()
    private val moonCut = Path()
    private val track = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(12f)
    }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(9f)
        typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
    }
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var limitShown = false

    private var downY = 0f
    private var moved = false

    /** The axis doesn't flip while a threshold is being dragged. */
    private var frozenAxis: Int? = null

    init {
        isFocusable = true
        updateDescription()
    }

    private fun updateDescription() {
        contentDescription = context.getString(
            R.string.timeline_description, Format.time(context, nightStart), Format.time(context, nightEnd)
        )
    }

    /** Minute of the day at the top of the track: noon, or midnight for a night crossing noon. */
    val axisStart: Int
        get() = frozenAxis ?: axisFor(nightStart, nightEnd)

    /** Minutes from the top of the track, 0..1440. */
    private fun axis(time: LocalTime): Int = axisMinutes(time.toSecondOfDay() / 60)

    private fun axisMinutes(minuteOfDay: Int): Int = Math.floorMod(minuteOfDay - axisStart, 24 * 60)

    private fun timeAt(axisMinutes: Int): LocalTime = LocalTime.MIDNIGHT.plusMinutes((axisStart + axisMinutes).toLong())

    private fun y(axisMinutes: Float) = track.top + track.height() * axisMinutes / (24 * 60)

    private val startAxis get() = axis(nightStart)
    private val endAxis get() = axis(nightEnd).let { if (it == 0) 1440 else it }

    /** Where [time] is, in this view's coordinates. */
    fun yAt(time: LocalTime): Float = y(axis(time).toFloat())

    fun yOfStart(): Float = y(startAxis.toFloat())

    fun yOfEnd(): Float = y(endAxis.toFloat())

    /**
     * The nap's block, top to bottom: centred in the longer part of the day the track shows (the
     * day is split by the track's ends, above and below the night).
     */
    fun napRange(): ClosedFloatingPointRange<Float> {
        val above = startAxis
        val below = 1440 - endAxis
        return block(if (above >= below) above / 2f else endAxis + below / 2f, napMinutes)
    }

    /** The night's sleep block, centred in the night. */
    fun sleepRange(): ClosedFloatingPointRange<Float> = block((startAxis + endAxis) / 2f, sleepMinutes)

    private fun block(center: Float, minutes: Int): ClosedFloatingPointRange<Float> {
        val top = (center - minutes / 2f).coerceIn(0f, (1440f - minutes).coerceAtLeast(0f))
        val y0 = y(top)
        return y0..y(top + minutes).coerceAtLeast(y0 + dp(3f))
    }

    /** The track's left and right edges, in this view's coordinates. */
    val trackLeft: Float get() = track.left
    val trackRight: Float get() = track.right

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val pad = dp(6f)
        // The noon suns stick out of the track's ends, the top one level with the status card
        track.set(pad, dp(5f), w - pad, h - dp(5f))
        onChanged?.invoke()
    }

    override fun onDraw(canvas: Canvas) {
        val dayColor = context.getColor(if (focusNight) R.color.day_soft else R.color.day_focus)
        val nightColor = context.getColor(if (focusNight) R.color.night else R.color.night_soft)
        val s = startAxis / 1440f
        val e = endAxis / 1440f
        val blend = 60f / 1440f
        paint.shader = LinearGradient(
            0f, track.top, 0f, track.bottom,
            intArrayOf(dayColor, dayColor, nightColor, nightColor, dayColor, dayColor),
            floatArrayOf(0f, (s - blend).coerceAtLeast(0f), (s + blend).coerceAtMost(e), (e - blend).coerceAtLeast(s), (e + blend).coerceAtMost(1f), 1f),
            Shader.TileMode.CLAMP
        )
        val radius = track.width() / 2
        canvas.drawRoundRect(track, radius, radius, paint)
        paint.shader = null

        // The sleeps, to scale: the focused one, or both while the legend is shown
        val showNap = showBoth || !focusNight
        val showSleep = showBoth || focusNight
        val nap = napRange()
        val sleep = sleepRange()
        if (showNap && napMinutes > 0) {
            paint.color = accent
            canvas.drawRoundRect(track.left + dp(4f), nap.start, track.right - dp(4f), nap.endInclusive, dp(3f), dp(3f), paint)
        }
        if (showSleep && sleepMinutes > 0) {
            paint.color = context.getColor(if (focusNight) R.color.accent_pastel else R.color.accent_deep)
            canvas.drawRoundRect(track.left + dp(4f), sleep.start, track.right - dp(4f), sleep.endInclusive, dp(3f), dp(3f), paint)
        }

        // Hour numbers every 3 hours inside the track, readable on whatever is under them; on a
        // 12-hour clock AM/PM goes on a second, smaller line to keep the track narrow
        val twelve = !DateFormat.is24HourFormat(context)
        val lineHeight = text.descent() - text.ascent()
        val block = if (twelve) lineHeight * 1.7f else lineHeight
        fun labelCenter(i: Int) = when (i) {
            0 -> track.top + dp(3f) + block / 2
            8 -> track.bottom - dp(3f) - block / 2
            else -> y(i / 8f * 1440)
        }
        val noonAxis = axisStart == 12 * 60

        // Landmarks behind the numbers: a soft sun behind noon, a crescent behind midnight
        for (i in if (noonAxis) listOf(0, 8) else listOf(4)) drawSun(canvas, labelCenter(i))
        for (i in if (noonAxis) listOf(4) else listOf(0, 8)) drawMoon(canvas, labelCenter(i))

        for (i in 0..8) {
            val fraction = i / 8f
            val hour = (axisStart / 60 + i * 3) % 24
            val number = if (twelve) (if (hour % 12 == 0) 12 else hour % 12).toString() else "%02d".format(hour)
            val center = labelCenter(i)
            val inNight = fraction > s && fraction < e
            val onSleep = showSleep && center in sleep
            val dark = inNight && focusNight && !onSleep
            text.color = if (dark) Color.WHITE else context.getColor(R.color.on_surface)
            val top = center - block / 2
            canvas.drawText(number, track.centerX(), top - text.ascent(), text)
            if (twelve) {
                small.color = text.color
                canvas.drawText(if (hour < 12) "AM" else "PM", track.centerX(), top + lineHeight - small.ascent() * 0.9f, small)
            }
        }

        // Thresholds
        for ((threshold, isStart) in listOf(s to true, e to false)) {
            val ty = track.top + track.height() * threshold
            val handle = RectF(track.left - dp(4f), ty - dp(6f), track.right + dp(4f), ty + dp(6f))
            if (dragging == isStart) {
                paint.color = ColorUtils.setAlphaComponent(accent, 64)
                canvas.drawRoundRect(RectF(handle).apply { inset(-dp(6f), -dp(6f)) }, dp(12f), dp(12f), paint)
            }
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            canvas.drawRoundRect(handle, dp(6f), dp(6f), paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(2f)
            paint.color = accent
            canvas.drawRoundRect(handle, dp(6f), dp(6f), paint)
            paint.style = Paint.Style.FILL
        }
    }

    private fun drawSun(canvas: Canvas, cy: Float) {
        // As CSS's radial-gradient(circle) on the mockup's 30 dp square: sized to its corner
        val r = dp(15f) * 1.414f
        paint.shader = RadialGradient(
            track.centerX(), cy, r,
            intArrayOf(
                ColorUtils.setAlphaComponent(sunColor, 242), ColorUtils.setAlphaComponent(sunColor, 153),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.42f, 0.72f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(track.centerX(), cy, r, paint)
        paint.shader = null
    }

    private fun drawMoon(canvas: Canvas, cy: Float) {
        val r = dp(9.3f)
        moon.reset()
        moon.addCircle(track.centerX(), cy, r, Path.Direction.CW)
        moonCut.reset()
        moonCut.addCircle(track.centerX() + r * 0.53f, cy - r * 0.53f, r, Path.Direction.CW)
        moon.op(moonCut, Path.Op.DIFFERENCE)
        paint.color = ColorUtils.setAlphaComponent(moonColor, 204)
        canvas.drawPath(moon, paint)
    }

    // Dragging, from this view or from the legend's labels (y in this view's coordinates)

    /** The threshold near [y], if any: true for night start, false for night end. */
    fun thresholdNear(y: Float): Boolean? {
        val startY = yOfStart()
        val endY = yOfEnd()
        val reach = dp(24f)
        return when {
            abs(y - startY) <= reach && abs(y - startY) <= abs(y - endY) -> true
            abs(y - endY) <= reach -> false
            else -> null
        }
    }

    fun beginDrag(start: Boolean, y: Float) {
        frozenAxis = axisFor(nightStart, nightEnd)
        dragging = start
        downY = y
        moved = false
        limitShown = false
        changed()
    }

    /** Moves the dragged threshold so that it follows [y], moved by [offset] from its own position. */
    fun dragTo(y: Float, offset: Float = 0f) {
        val start = dragging ?: return
        if (abs(y - downY) > touchSlop) moved = true
        if (!moved) return
        val raw = ((y - offset - track.top) / track.height() * 1440).roundToInt()
        val snapped = (raw / 15.0).roundToInt() * 15
        val minimum = minWindowMinutes.coerceAtLeast(60)
        val s = startAxis
        val e = endAxis
        if (start) nightStart = timeAt(snapped.coerceIn(0, e - minimum))
        else nightEnd = timeAt(snapped.coerceIn(s + minimum, 1440))
        val againstSleep = (start && snapped > e - minimum) || (!start && snapped < s + minimum)
        if (againstSleep && minimum > 60 && !limitShown) {
            limitShown = true
            listener?.onWindowLimit()
        }
        listener?.onWindowChanged(nightStart, nightEnd, false)
    }

    /** Ends a drag; a drag that never moved is a tap, which asks for an exact time. */
    fun endDrag() {
        val start = dragging ?: return
        dragging = null
        frozenAxis = null
        if (moved) listener?.onWindowChanged(nightStart, nightEnd, true) else listener?.onThresholdTapped(start)
        changed()
    }

    fun cancelDrag() {
        dragging = null
        frozenAxis = null
        changed()
    }

    private var grabOffset = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                listener?.onTouched()
                val start = thresholdNear(event.y)
                if (start == null) {
                    performClick()
                    return true
                }
                grabOffset = event.y - (if (start) yOfStart() else yOfEnd())
                beginDrag(start, event.y)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                dragTo(event.y, grabOffset)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (dragging != null) {
                    if (!moved) performClick()
                    endDrag()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelDrag()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()

    companion object {
        private fun crosses(minuteOfDay: Int, start: LocalTime, end: LocalTime): Boolean {
            val s = start.toSecondOfDay() / 60
            val length = Math.floorMod(end.toSecondOfDay() / 60 - s, 24 * 60)
            val at = Math.floorMod(minuteOfDay - s, 24 * 60)
            return at in 1 until length
        }

        /** Noon to noon, or midnight to midnight when the night crosses noon. */
        fun axisFor(start: LocalTime, end: LocalTime): Int = if (crosses(12 * 60, start, end)) 0 else 12 * 60

        /** At least an hour, and not across both noon and midnight (the timeline can't show that). */
        fun isValidWindow(start: LocalTime, end: LocalTime): Boolean {
            val length = Math.floorMod(end.toSecondOfDay() / 60 - start.toSecondOfDay() / 60, 24 * 60)
            return length >= 60 && !(crosses(12 * 60, start, end) && crosses(0, start, end))
        }
    }
}
