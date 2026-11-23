package com.wakewords
import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.sin

/** Original animated background: drifting layered sound-wave ribbons over a gradient. */
class WaveBackground(c: Context, private val top: Int, private val bottom: Int, private val wave: Int,
                     private val speed: Float = 1f) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var t = 0f
    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        p.shader = LinearGradient(0f, 0f, w * 0.3f, h, top, bottom, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w, h, p); p.shader = null
        t += 0.012f * speed
        for (i in 0 until 5) {
            path.reset(); path.moveTo(0f, h)
            val base = h * (0.50f + i * 0.10f); val amp = h * 0.02f * (1 + i * 0.5f)
            var x = 0f
            while (x <= w + 12f) {
                path.lineTo(x, base + amp * sin((x / w * (5f + i * 2f) + t * (1f + i * 0.4f)).toDouble()).toFloat())
                x += 12f
            }
            path.lineTo(w, h); path.close()
            p.color = wave; p.alpha = 18 + i * 9
            cv.drawPath(path, p)
        }
        postInvalidateOnAnimation()
    }
}
