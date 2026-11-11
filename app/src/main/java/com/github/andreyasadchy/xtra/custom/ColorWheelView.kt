package com.github.andreyasadchy.xtra.custom

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Xtra5 custom: hue/saturation color wheel. Hue is the angle, saturation the distance from the center.
 * Brightness is shown by darkening the wheel; it is set from outside (slider).
 */
class ColorWheelView(context: Context) : View(context) {

    var onChanged: ((hue: Float, saturation: Float) -> Unit)? = null

    /** Largest size of the wheel in pixels (it is always square). */
    var maxSizePx = (240 * resources.displayMetrics.density).toInt()

    private var hue = 0f
    private var saturation = 0f
    private var brightness = 1f

    private val wheelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectorFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectorRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
        color = Color.WHITE
    }
    private val selectorOuter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * resources.displayMetrics.density
        color = 0x66000000
    }
    private var cx = 0f
    private var cy = 0f
    private var radius = 0f

    fun setColor(hue: Float, saturation: Float, brightness: Float) {
        this.hue = hue
        this.saturation = saturation.coerceIn(0f, 1f)
        this.brightness = brightness.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val size = if (w > 0) min(w, maxSizePx) else maxSizePx
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cx = w / 2f
        cy = h / 2f
        radius = min(w, h) / 2f - 12 * resources.displayMetrics.density
        val colors = IntArray(13) { Color.HSVToColor(floatArrayOf((it * 30f) % 360f, 1f, 1f)) }
        // Angles go clockwise in SweepGradient; draw hue counter-clockwise to match atan2 below.
        val sweep = SweepGradient(cx, cy, colors.reversedArray(), null)
        val radial = RadialGradient(cx, cy, radius, Color.WHITE, 0x00FFFFFF, Shader.TileMode.CLAMP)
        wheelPaint.shader = ComposeShader(sweep, radial, PorterDuff.Mode.SRC_OVER)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawCircle(cx, cy, radius, wheelPaint)
        shadePaint.color = Color.argb(((1f - brightness) * 255).toInt(), 0, 0, 0)
        canvas.drawCircle(cx, cy, radius, shadePaint)
        val angle = Math.toRadians(hue.toDouble())
        val x = cx + (cos(angle) * saturation * radius).toFloat()
        val y = cy - (sin(angle) * saturation * radius).toFloat()
        val r = 11 * resources.displayMetrics.density
        selectorFill.color = Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
        canvas.drawCircle(x, y, r, selectorFill)
        canvas.drawCircle(x, y, r, selectorRing)
        canvas.drawCircle(x, y, r + selectorRing.strokeWidth / 2, selectorOuter)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true) // don't let the dialog scroll while picking
                val dx = event.x - cx
                val dy = cy - event.y
                var angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                if (angle < 0) angle += 360f
                hue = angle
                saturation = (hypot(dx, dy) / radius).coerceIn(0f, 1f)
                invalidate()
                onChanged?.invoke(hue, saturation)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
