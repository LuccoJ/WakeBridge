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
import android.util.AttributeSet
import android.util.TypedValue
import android.graphics.drawable.ColorDrawable
import android.text.format.DateFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A vertical day, noon at the top to noon at the bottom, so the night never wraps around
 * midnight. Its two thresholds, night start and night end, can be dragged (in 15-minute steps)
 * or tapped for an exact time. It also shows "now" and the focused sleep, to scale.
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
    }

    /** The night can't get shorter than this (the night's sleep amount). */
    var minWindowMinutes: Int = 60

    var listener: Listener? = null

    var nightStart: LocalTime = LocalTime.of(21, 0)
        set(value) { field = value; updateDescription(); invalidate() }
    var nightEnd: LocalTime = LocalTime.of(8, 0)
        set(value) { field = value; updateDescription(); invalidate() }

    /** Night pane: the night is the strong colour; Day pane: the day is. */
    var focusNight: Boolean = false
        set(value) { field = value; invalidate() }

    /** The focused sleep's length in minutes, drawn centred in its window; 0 for none. */
    var previewMinutes: Int = 0
        set(value) { field = value; invalidate() }

    /** Which side the "now" dot goes: towards the pane content. */
    var innerOnLeft: Boolean = true
        set(value) { field = value; invalidate() }

    var now: LocalTime = LocalTime.now()
        set(value) { field = value; invalidate() }

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

    /** Shows the time a threshold is being dragged to, beside the finger. */
    private val tooltip: PopupWindow by lazy {
        val label = TextView(context).apply {
            setTextColor(context.getColor(R.color.on_night))
            textSize = 16f
            setPadding(dp(12f).toInt(), dp(6f).toInt(), dp(12f).toInt(), dp(6f).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(10f)
                setColor(context.getColor(R.color.night))
            }
        }
        PopupWindow(label, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isTouchable = false
            elevation = dp(4f)
        }
    }
    private var limitShown = false

    private fun showTooltip(time: LocalTime, atY: Float) {
        val label = tooltip.contentView as TextView
        label.text = Format.time(context, time)
        label.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        val location = IntArray(2)
        getLocationOnScreen(location)
        // Beside the track, towards the pane content
        val x = if (innerOnLeft) location[0] - label.measuredWidth - dp(8f).toInt() else location[0] + width + dp(8f).toInt()
        val y = location[1] + atY.toInt() - label.measuredHeight / 2
        if (tooltip.isShowing) tooltip.update(x, y, -1, -1) else tooltip.showAtLocation(this, Gravity.NO_GRAVITY, x, y)
    }

    private enum class Drag { NONE, START, END }

    private var drag = Drag.NONE
    private var downY = 0f
    private var moved = false

    init {
        isFocusable = true
        updateDescription()
    }

    private fun updateDescription() {
        contentDescription = context.getString(
            R.string.timeline_description, Format.time(context, nightStart), Format.time(context, nightEnd)
        )
    }

    /** Minutes from noon, 0..1440. */
    private fun axis(time: LocalTime): Int = Math.floorMod(time.toSecondOfDay() / 60 - 12 * 60, 24 * 60)

    private fun timeAt(axisMinutes: Int): LocalTime = LocalTime.NOON.plusMinutes(axisMinutes.toLong())

    private fun y(axisMinutes: Float) = track.top + track.height() * axisMinutes / (24 * 60)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val pad = dp(6f)
        track.set(pad, dp(8f), w - pad, h - dp(8f))
    }

    override fun onDraw(canvas: Canvas) {
        val dayColor = context.getColor(if (focusNight) R.color.day_soft else R.color.day_focus)
        val nightColor = context.getColor(if (focusNight) R.color.night else R.color.night_soft)
        val s = axis(nightStart) / 1440f
        val e = axis(nightEnd).let { if (it == 0) 1440 else it } / 1440f
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

        // The focused sleep, centred in its window
        val previewTop: Float
        val previewBottom: Float
        if (previewMinutes > 0) {
            val nightLength = ((e - s) * 1440).roundToInt()
            val center = if (focusNight) {
                axis(nightStart) + nightLength / 2f
            } else {
                val dayLength = 1440 - nightLength
                (axis(nightEnd) + dayLength / 2f) % 1440
            }
            val top = (center - previewMinutes / 2f).coerceIn(0f, 1440f - previewMinutes)
            previewTop = y(top)
            previewBottom = y(top + previewMinutes).coerceAtLeast(previewTop + dp(3f))
            paint.color = context.getColor(if (focusNight) R.color.accent_pastel else R.color.accent_deep)
            canvas.drawRoundRect(track.left + dp(4f), previewTop, track.right - dp(4f), previewBottom, dp(3f), dp(3f), paint)
        } else {
            previewTop = -1f
            previewBottom = -1f
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

        // Landmarks behind the numbers: a soft sun behind both noons, a crescent behind midnight
        for (i in listOf(0, 8)) {
            val cy = labelCenter(i)
            paint.shader = RadialGradient(
                track.centerX(), cy, dp(15f),
                intArrayOf(
                    ColorUtils.setAlphaComponent(sunColor, 242), ColorUtils.setAlphaComponent(sunColor, 153),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.42f, 0.72f), Shader.TileMode.CLAMP
            )
            canvas.drawCircle(track.centerX(), cy, dp(15f), paint)
            paint.shader = null
        }
        moon.reset()
        val moonY = labelCenter(4)
        val moonR = dp(9.3f)
        moon.addCircle(track.centerX(), moonY, moonR, Path.Direction.CW)
        moonCut.reset()
        moonCut.addCircle(track.centerX() + moonR * 0.53f, moonY - moonR * 0.53f, moonR, Path.Direction.CW)
        moon.op(moonCut, Path.Op.DIFFERENCE)
        paint.color = ColorUtils.setAlphaComponent(moonColor, 204)
        canvas.drawPath(moon, paint)

        for (i in 0..8) {
            val fraction = i / 8f
            val hour = (12 + i * 3) % 24
            val number = if (twelve) (if (hour % 12 == 0) 12 else hour % 12).toString() else "%02d".format(hour)
            val center = labelCenter(i)
            val inNight = fraction > s && fraction < e
            val onPreview = center in previewTop..previewBottom
            val dark = inNight && focusNight && !onPreview
            text.color = if (dark) Color.WHITE else context.getColor(R.color.on_surface)
            val top = center - block / 2
            canvas.drawText(number, track.centerX(), top - text.ascent(), text)
            if (twelve) {
                small.color = text.color
                canvas.drawText(if (hour < 12) "AM" else "PM", track.centerX(), top + lineHeight - small.ascent() * 0.9f, small)
            }
        }

        // Now
        val nowY = y(axis(now).toFloat())
        paint.color = accent
        canvas.drawRect(track.left - dp(4f), nowY - dp(1f), track.right + dp(4f), nowY + dp(1f), paint)
        val dotX = if (innerOnLeft) track.left - dp(3f) else track.right + dp(3f)
        canvas.drawCircle(dotX, nowY, dp(4f), paint)

        // Thresholds
        for (threshold in listOf(s, e)) {
            val ty = track.top + track.height() * threshold
            val handle = RectF(track.left - dp(4f), ty - dp(6f), track.right + dp(4f), ty + dp(6f))
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val startY = y(axis(nightStart).toFloat())
                val endY = y(axis(nightEnd).let { if (it == 0) 1440 else it }.toFloat())
                val reach = dp(24f)
                drag = when {
                    abs(event.y - startY) <= reach && abs(event.y - startY) <= abs(event.y - endY) -> Drag.START
                    abs(event.y - endY) <= reach -> Drag.END
                    else -> Drag.NONE
                }
                if (drag == Drag.NONE) return false
                downY = event.y
                moved = false
                limitShown = false
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (drag == Drag.NONE) return false
                if (abs(event.y - downY) > touchSlop) moved = true
                if (!moved) return true
                val raw = ((event.y - track.top) / track.height() * 1440).roundToInt()
                val snapped = (raw / 15.0).roundToInt() * 15
                val startAxis = axis(nightStart)
                val endAxis = axis(nightEnd).let { if (it == 0) 1440 else it }
                val minimum = minWindowMinutes.coerceAtLeast(60)
                val limited: Int
                if (drag == Drag.START) {
                    limited = snapped.coerceIn(0, endAxis - minimum)
                    nightStart = timeAt(limited)
                } else {
                    limited = snapped.coerceIn(startAxis + minimum, 1440)
                    nightEnd = timeAt(limited)
                }
                val againstSleep = (drag == Drag.START && snapped > endAxis - minimum) ||
                    (drag == Drag.END && snapped < startAxis + minimum)
                if (againstSleep && minimum > 60 && !limitShown) {
                    limitShown = true
                    listener?.onWindowLimit()
                }
                showTooltip(if (drag == Drag.START) nightStart else nightEnd, y(limited.toFloat()))
                listener?.onWindowChanged(nightStart, nightEnd, false)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (drag == Drag.NONE) return false
                if (tooltip.isShowing) tooltip.dismiss()
                if (moved) listener?.onWindowChanged(nightStart, nightEnd, true) else {
                    performClick()
                    listener?.onThresholdTapped(drag == Drag.START)
                }
                drag = Drag.NONE
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (tooltip.isShowing) tooltip.dismiss()
                drag = Drag.NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()
}
