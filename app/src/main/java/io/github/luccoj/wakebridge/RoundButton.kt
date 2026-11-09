package io.github.luccoj.wakebridge

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Button
import androidx.core.graphics.ColorUtils
import kotlin.math.min

/**
 * The big round button of the Day and Night panes. Idle: hollow, a soft halo of the accent
 * around an empty middle. Running: filled, with rings slowly pulsing outward ("breathing")
 * while the screen is on.
 */
class RoundButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    enum class Look { IDLE, RUNNING, DISABLED }

    var look: Look = Look.IDLE
        set(value) {
            field = value
            // Stays clickable when greyed out, so a tap can explain why it can't start
            updateAnimation()
            invalidate()
        }

    var label: String = ""
        set(value) {
            field = value
            contentDescription = value
            invalidate()
        }

    var icon: Drawable? = null
        set(value) {
            field = value?.mutate()
            invalidate()
        }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private val pastel = context.getColor(R.color.accent_pastel)
    private val deep = context.getColor(R.color.accent_deep)
    private val grey = context.getColor(R.color.disabled)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 15f, resources.displayMetrics)
        isFakeBoldText = true
    }
    private var phase = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2800
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        isClickable = true
        isFocusable = true
    }

    override fun getAccessibilityClassName(): CharSequence = Button::class.java.name

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wanted = dp(176f).toInt()
        val w = resolveSize(wanted, widthMeasureSpec)
        val h = resolveSize(wanted, heightMeasureSpec)
        val size = min(w, h)
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val outer = min(width, height) / 2f

        when (look) {
            Look.IDLE, Look.DISABLED -> {
                val c = if (look == Look.IDLE) pastel else grey
                paint.shader = RadialGradient(
                    cx, cy, outer,
                    intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, ColorUtils.setAlphaComponent(c, 242), ColorUtils.setAlphaComponent(c, 110), Color.TRANSPARENT),
                    floatArrayOf(0f, 0.6f, 0.68f, 0.82f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(cx, cy, outer, paint)
                paint.shader = null
                text.color = if (look == Look.IDLE) deep else grey
            }
            Look.RUNNING -> {
                // The idle halo mirrored: strongest at the ring, fading inward, gently breathing
                val breathe = (1 - kotlin.math.cos(phase * 2 * Math.PI).toFloat()) / 2
                val fadeStart = 0.22f + 0.12f * breathe
                paint.shader = RadialGradient(
                    cx, cy, outer,
                    intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, ColorUtils.setAlphaComponent(pastel, 120), ColorUtils.setAlphaComponent(deep, 235), Color.TRANSPARENT, Color.TRANSPARENT),
                    floatArrayOf(0f, fadeStart, (fadeStart + 0.68f) / 2, 0.68f, 0.72f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(cx, cy, outer, paint)
                paint.shader = null
                text.color = deep
            }
        }

        val iconSize = dp(28f).toInt()
        val textY = cy + dp(22f)
        icon?.let {
            it.setTint(text.color)
            val left = (cx - iconSize / 2f).toInt()
            val top = (cy - iconSize + dp(2f)).toInt()
            it.setBounds(left, top, left + iconSize, top + iconSize)
            it.draw(canvas)
        }
        canvas.drawText(label, cx, textY, text)
    }

    private fun updateAnimation() {
        val run = look == Look.RUNNING && isAttachedToWindow && isShown
        if (run && !animator.isStarted) animator.start()
        if (!run && animator.isStarted) animator.cancel()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimation()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        // Also covers the screen turning off: the window becomes invisible
        if (isVisible) updateAnimation() else animator.cancel()
    }
}
