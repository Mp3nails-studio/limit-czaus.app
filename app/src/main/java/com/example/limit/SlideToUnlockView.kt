package com.example.limit

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

/** Suwak "przeciągnij w bok": po przeciągnięciu uchwytu do końca wywołuje [onSlid]. */
class SlideToUnlockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onSlid: (() -> Unit)? = null
    var label: String = ""

    private val density = resources.displayMetrics.density
    private val pad = 6 * density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3 * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 16 * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }

    private val trackRect = RectF()
    private val arrow = Path()

    /** Przesunięcie uchwytu od lewej krawędzi (0..maxOffset) w pikselach. */
    private var offset = 0f
    private var dragging = false
    private var grabDelta = 0f
    private var returnAnim: ValueAnimator? = null

    fun setColors(track: Int, thumb: Int, arrowColor: Int, text: Int) {
        trackPaint.color = track
        thumbPaint.color = thumb
        arrowPaint.color = arrowColor
        textPaint.color = text
        invalidate()
    }

    private fun thumbSize() = height - 2 * pad
    private fun maxOffset() = max(0f, width - 2 * pad - thumbSize())

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        val w = width.toFloat()
        trackRect.set(0f, 0f, w, h)
        canvas.drawRoundRect(trackRect, h / 2, h / 2, trackPaint)

        val size = thumbSize()
        val progress = if (maxOffset() > 0f) offset / maxOffset() else 0f

        // Napis blednie w miarę przesuwania uchwytu.
        textPaint.alpha = (255 * (1f - progress).coerceIn(0f, 1f)).toInt()
        val textX = (pad + size + w - pad) / 2f
        val textY = h / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, textX, textY, textPaint)

        val cx = pad + offset + size / 2f
        val cy = h / 2f
        canvas.drawCircle(cx, cy, size / 2f, thumbPaint)

        // Strzałka ">" na uchwycie.
        val a = size * 0.14f
        arrow.reset()
        arrow.moveTo(cx - a * 0.6f, cy - a * 1.4f)
        arrow.lineTo(cx + a * 0.9f, cy)
        arrow.lineTo(cx - a * 0.6f, cy + a * 1.4f)
        canvas.drawPath(arrow, arrowPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val thumbLeft = pad + offset
                val slop = 12 * density
                val onThumb = event.x >= thumbLeft - slop &&
                        event.x <= thumbLeft + thumbSize() + slop
                if (!onThumb) return false
                returnAnim?.cancel()
                dragging = true
                grabDelta = event.x - thumbLeft
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    offset = min(max(event.x - grabDelta - pad, 0f), maxOffset())
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) return false
                dragging = false
                val done = event.actionMasked == MotionEvent.ACTION_UP &&
                        maxOffset() > 0f && offset >= maxOffset() * 0.85f
                animateBack()
                if (done) onSlid?.invoke()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun animateBack() {
        returnAnim?.cancel()
        returnAnim = ValueAnimator.ofFloat(offset, 0f).apply {
            duration = 200
            addUpdateListener {
                offset = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        returnAnim?.cancel()
        super.onDetachedFromWindow()
    }
}
