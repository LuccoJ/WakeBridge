package io.github.luccoj.wakebridge

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Explains the timeline: a raised panel beside it, the pane veiled behind, with labelled arrows
 * for now, the nap, night start, the night's sleep and night end. Night start and end can be
 * dragged by their labels too; the dragged one shows its live time and the others dim. A tap
 * anywhere else closes it.
 */
class TimelineLegendView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** The timeline explained; a sibling drawn above this view, in the same parent. */
    var timeline: TimelineView? = null
        set(value) { field = value; invalidate() }

    var onDismiss: (() -> Unit)? = null

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private val veil = context.getColor(R.color.legend_veil)
    private val panel = context.getColor(R.color.legend_panel)
    private val accent = context.getColor(R.color.accent_deep)
    private val ink = context.getColor(R.color.on_surface)
    private val inkSoft = context.getColor(R.color.on_surface_soft)
    private val quiet = context.getColor(R.color.legend_quiet)
    private val dimmed = context.getColor(R.color.disabled)
    private val onAccent = context.getColor(R.color.legend_on_accent)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
    private val value = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
    private val hint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sp(12f) }
    private val path = Path()
    private val panelRect = RectF()

    /** Touch targets of the two night labels, in this view's coordinates. */
    private val startTarget = RectF()
    private val endTarget = RectF()

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var dragOffset = 0f
    private var draggingLabel = false
    private var tapped = false

    init {
        isClickable = true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) setLayerType(LAYER_TYPE_SOFTWARE, null)
        shadow.color = panel
    }

    private enum class Kind { NOW, NAP, START, SLEEP, END }

    private class Item(val kind: Kind, val y: Float, val label: String, val value: String, val span: ClosedFloatingPointRange<Float>?)

    private fun items(tl: TimelineView): List<Item> {
        val dy = tl.top.toFloat() - top
        fun shift(r: ClosedFloatingPointRange<Float>) = (r.start + dy)..(r.endInclusive + dy)
        val nap = shift(tl.napRange())
        val sleep = shift(tl.sleepRange())
        return listOf(
            Item(Kind.NOW, tl.yAt(tl.now) + dy, context.getString(R.string.legend_now), Format.time(context, tl.now), null),
            Item(Kind.NAP, (nap.start + nap.endInclusive) / 2, context.getString(R.string.legend_nap), Format.minutes(context, tl.napMinutes), nap),
            Item(Kind.START, tl.yOfStart() + dy, context.getString(R.string.legend_night_start), Format.time(context, tl.nightStart), null),
            Item(Kind.SLEEP, (sleep.start + sleep.endInclusive) / 2, context.getString(R.string.legend_night_sleep), Format.minutes(context, tl.sleepMinutes), sleep),
            Item(Kind.END, tl.yOfEnd() + dy, context.getString(R.string.legend_night_end), Format.time(context, tl.nightEnd), null),
        )
    }

    override fun onDraw(canvas: Canvas) {
        val tl = timeline ?: return
        canvas.drawColor(veil)

        // Which way the labels go: from the timeline towards the pane content
        val onLeft = tl.innerOnLeft.not()
        val dir = if (onLeft) 1f else -1f
        val edge = if (onLeft) tl.left + tl.trackRight else tl.left + tl.trackLeft
        val panelWidth = min(tl.width + dp(8f) + dp(250f), width - dp(40f))
        val radius = dp(24f)
        if (onLeft) panelRect.set(-radius, 0f, tl.left + panelWidth, height - dp(4f))
        else panelRect.set(tl.right - panelWidth, 0f, width + radius, height - dp(4f))
        shadow.setShadowLayer(dp(9f), dir * dp(2f), 0f, 0x29173A4F)
        canvas.drawRoundRect(panelRect, radius, radius, shadow)

        val dragging = tl.dragging
        val draggingKind = when (dragging) { true -> Kind.START; false -> Kind.END; null -> null }
        // "Now" first and quiet, so the other marks stay readable on top of it
        for (item in items(tl)) {
            val active = item.kind == draggingKind
            val dim = draggingKind != null && !active
            val color = when {
                item.kind == Kind.NOW -> quiet
                dim -> dimmed
                else -> accent
            }
            paint.color = color
            paint.strokeWidth = dp(2f)
            paint.style = Paint.Style.STROKE
            val tip = edge + dir * dp(3f)
            if (item.span != null) {
                // A bracket as long as the block, then a line to the label
                val x = tip + dir * dp(7f)
                path.reset()
                path.moveTo(tip, item.span.start)
                path.lineTo(x, item.span.start)
                path.lineTo(x, item.span.endInclusive)
                path.lineTo(tip, item.span.endInclusive)
                canvas.drawPath(path, paint)
                canvas.drawLine(x, item.y, tip + dir * dp(28f), item.y, paint)
            } else {
                canvas.drawLine(tip + dir * dp(6f), item.y, tip + dir * dp(28f), item.y, paint)
                paint.style = Paint.Style.FILL
                path.reset()
                path.moveTo(tip, item.y)
                path.lineTo(tip + dir * dp(7f), item.y - dp(5f))
                path.lineTo(tip + dir * dp(7f), item.y + dp(5f))
                path.close()
                canvas.drawPath(path, paint)
            }
            paint.style = Paint.Style.FILL

            // The label and its value, on the panel colour (or filled while dragged)
            val quietItem = item.kind == Kind.NOW
            label.textSize = sp(if (quietItem) 13f else 15f)
            label.typeface = Typeface.create(if (quietItem) "sans-serif" else "sans-serif-medium", Typeface.NORMAL)
            value.textSize = sp(if (active) 20f else if (quietItem) 13f else 15f)
            val gap = dp(8f)
            val padX = if (active) dp(10f) else dp(6f)
            val padY = if (active) dp(4f) else dp(1f)
            val labelWidth = label.measureText(item.label)
            val valueWidth = value.measureText(item.value)
            val boxWidth = labelWidth + gap + valueWidth + 2 * padX
            val textHeight = max(value.descent() - value.ascent(), label.descent() - label.ascent())
            val boxStart = tip + dir * dp(30f)
            val box = if (onLeft) RectF(boxStart, item.y - textHeight / 2 - padY, boxStart + boxWidth, item.y + textHeight / 2 + padY)
            else RectF(boxStart - boxWidth, item.y - textHeight / 2 - padY, boxStart, item.y + textHeight / 2 + padY)
            if (!quietItem) {
                paint.color = if (active) accent else panel
                canvas.drawRoundRect(box, dp(12f), dp(12f), paint)
            }
            val baseline = item.y - (value.ascent() + value.descent()) / 2
            label.color = when {
                active -> onAccent
                quietItem -> quiet
                dim -> dimmed
                else -> ink
            }
            value.color = when {
                active -> onAccent
                quietItem -> quiet
                dim -> dimmed
                else -> accent
            }
            canvas.drawText(item.label, box.left + padX, baseline, label)
            canvas.drawText(item.value, box.left + padX + labelWidth + gap, baseline, value)

            if (item.kind == Kind.START || item.kind == Kind.END) {
                var targetBottom = box.bottom
                if (draggingKind == null) {
                    hint.color = inkSoft
                    val text = context.getString(R.string.legend_drag_hint)
                    val hintWidth = hint.measureText(text)
                    val x = if (onLeft) box.left + padX else box.right - padX - hintWidth
                    canvas.drawText(text, x, box.bottom + dp(2f) - hint.ascent(), hint)
                    targetBottom = box.bottom + dp(4f) + hint.descent() - hint.ascent()
                }
                val target = if (item.kind == Kind.START) startTarget else endTarget
                target.set(min(edge, box.left), box.top - dp(10f), max(edge, box.right), targetBottom + dp(6f))
            }
        }

        // How to close it: at the panel's foot, or at its head if the marks are down there
        hint.color = inkSoft
        val closeWidth = (panelWidth - tl.width - dp(24f)).toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder.obtain(context.getString(R.string.legend_close), 0, context.getString(R.string.legend_close).length, hint, closeWidth)
            .setAlignment(if (onLeft) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_OPPOSITE)
            .build()
        canvas.save()
        val x = if (onLeft) edge + dp(30f) else edge - dp(30f) - closeWidth
        val ys = items(tl).map { it.y }
        val footY = height - dp(16f) - layout.height
        val headY = dp(16f)
        val clearOfFoot = ys.minOf { abs(it - (footY + layout.height / 2f)) }
        val clearOfHead = ys.minOf { abs(it - (headY + layout.height / 2f)) }
        canvas.translate(x, if (clearOfFoot >= dp(48f) || clearOfFoot >= clearOfHead) footY else headY)
        layout.draw(canvas)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val tl = timeline ?: return false
        val dy = tl.top.toFloat() - top
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                val start = when {
                    startTarget.contains(event.x, event.y) -> true
                    endTarget.contains(event.x, event.y) -> false
                    else -> null
                }
                draggingLabel = start != null
                tapped = start == null
                if (start != null) {
                    // The threshold follows the finger from where the label was grabbed
                    val handleY = (if (start) tl.yOfStart() else tl.yOfEnd()) + dy
                    dragOffset = event.y - handleY
                    tl.beginDrag(start, event.y - dy)
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggingLabel) tl.dragTo(event.y - dy, dragOffset)
                else if (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop) tapped = false
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (draggingLabel) tl.endDrag()
                else if (tapped) {
                    performClick()
                    onDismiss?.invoke()
                }
                draggingLabel = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (draggingLabel) tl.cancelDrag()
                draggingLabel = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()
}
