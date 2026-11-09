package io.github.luccoj.wakebridge

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
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
    }

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
    private val track = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(10f)
        isFakeBoldText = true
    }
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

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

        // Hour numbers inside the track, readable on whatever is under them
        val labels = listOf("12", "18", "00", "06", "12")
        labels.forEachIndexed { i, label ->
            val fraction = i / 4f
            val baseline = when (i) {
                0 -> track.top + dp(4f) - text.ascent()
                4 -> track.bottom - dp(4f) - text.descent()
                else -> y(fraction * 1440) - (text.ascent() + text.descent()) / 2
            }
            val inNight = fraction > s && fraction < e
            val onPreview = baseline in previewTop..previewBottom + text.textSize
            val dark = inNight && focusNight && !onPreview
            text.color = if (dark) Color.WHITE else context.getColor(R.color.on_surface)
            canvas.drawText(label, track.centerX(), baseline, text)
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
                if (drag == Drag.START) {
                    nightStart = timeAt(snapped.coerceIn(0, endAxis - 60))
                } else {
                    nightEnd = timeAt(snapped.coerceIn(startAxis + 60, 1440))
                }
                listener?.onWindowChanged(nightStart, nightEnd, false)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (drag == Drag.NONE) return false
                if (moved) listener?.onWindowChanged(nightStart, nightEnd, true) else {
                    performClick()
                    listener?.onThresholdTapped(drag == Drag.START)
                }
                drag = Drag.NONE
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                drag = Drag.NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()
}
