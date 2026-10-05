package app.revanced.bilibili.clean

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import androidx.annotation.Keep

/** Drawing-only player overlay: never participates in touch dispatch. */
@Keep
class CleanSeekFeedback : Drawable(), View.OnAttachStateChangeListener, View.OnLayoutChangeListener {
    private val main = Handler(Looper.getMainLooper())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val triangle = Path()
    private var surface: View? = null
    private var player: Any? = null
    private var forward = true
    private var seconds = 0
    private var lastStep = 0L
    private val frame = object : Runnable {
        override fun run() {
            if (surface?.isAttachedToWindow != true || SystemClock.uptimeMillis() - lastStep >= 1400) {
                clear(); return
            }
            invalidateSelf()
            main.postDelayed(this, 16)
        }
    }

    fun show(view: View, media: Any, delta: Long) {
        val now = SystemClock.uptimeMillis()
        val direction = delta > 0
        val continuing = surface === view && player === media && forward == direction && now - lastStep <= 1200
        val total = if (continuing) seconds + 10 else 10
        if (surface !== view) {
            clear()
            surface = view
            view.overlay.add(this)
            view.addOnAttachStateChangeListener(this)
            view.addOnLayoutChangeListener(this)
        }
        player = media; forward = direction; seconds = total; lastStep = now
        setBounds(0, 0, view.width, view.height)
        main.removeCallbacks(frame)
        frame.run()
    }

    fun clear() {
        main.removeCallbacks(frame)
        surface?.let {
            it.overlay.remove(this)
            it.removeOnAttachStateChangeListener(this)
            it.removeOnLayoutChangeListener(this)
        }
        surface = null; player = null; seconds = 0; lastStep = 0
    }

    override fun draw(canvas: Canvas) {
        val view = surface ?: return
        val age = (SystemClock.uptimeMillis() - lastStep).toFloat()
        val opacity = if (age < 1200) 1f else ((1400 - age) / 200).coerceIn(0f, 1f)
        val density = view.resources.displayMetrics.density
        val w = bounds.width().toFloat(); val h = bounds.height().toFloat()
        val centerX = if (forward) w * .80f else w * .20f
        val centerY = h * .5f
        val unit = minOf(density, h / 140f)
        val pulse = (age / 450).coerceIn(0f, 1f)
        val save = canvas.save()
        canvas.clipRect(bounds)
        // An oval cropped at the edge keeps the feedback inside its half of the video.
        paint.color = Color.BLACK; paint.alpha = (48 * opacity).toInt()
        val left = if (forward) w * .59f else -w * .43f
        canvas.drawOval(left, -h * .30f, left + w * .84f, h * 1.30f, paint)
        paint.color = Color.WHITE; paint.alpha = ((22 + 18 * (1 - pulse)) * opacity).toInt()
        canvas.drawOval(left, -h * .30f, left + w * .84f, h * 1.30f, paint)
        val arrowY = centerY - 14 * unit
        for (index in 0..2) {
            val slot = if (forward) index else 2 - index
            val x = centerX + (slot - 1) * 16 * unit
            val sign = if (forward) 1 else -1
            paint.alpha = ((.55f + .45f * ((age / 380 - index * .18f).coerceIn(0f, 1f))) * 255 * opacity).toInt()
            triangle.reset()
            triangle.moveTo(x - sign * 5 * unit, arrowY - 7 * unit)
            triangle.lineTo(x + sign * 6 * unit, arrowY)
            triangle.lineTo(x - sign * 5 * unit, arrowY + 7 * unit)
            triangle.close(); canvas.drawPath(triangle, paint)
        }
        paint.alpha = (255 * opacity).toInt(); paint.textAlign = Paint.Align.CENTER
        paint.textSize = 18 * unit; paint.isFakeBoldText = true
        paint.setShadowLayer(2 * unit, 0f, unit, 0x66000000)
        canvas.drawText("$seconds 秒", centerX, centerY + 24 * unit, paint)
        paint.clearShadowLayer(); paint.isFakeBoldText = false
        canvas.restoreToCount(save)
    }

    override fun onViewDetachedFromWindow(view: View) = clear()
    override fun onViewAttachedToWindow(view: View) = Unit
    override fun onLayoutChange(view: View, left: Int, top: Int, right: Int, bottom: Int,
        oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
        if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) clear()
    }
    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Drawable opacity is no longer used")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
