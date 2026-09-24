package app.revanced.bilibili.clean

import android.app.Activity
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.util.WeakHashMap

/** Draw in the host view's overlay: native seek gestures, thumb and layout remain intact. */
class AirborneUi {
    private val bars = WeakHashMap<View, MarkerDrawable>()
    private val titles = WeakHashMap<TextView, Pair<CharSequence, String>>()
    private val barIds = setOf("bbplayer_halfscreen_seekbar", "bbplayer_fullscreen_seekbar",
        "gemini_halfscreen_seekbar", "player_widget_progressbar",
        "ctrl_seekbar")

    fun clear() {
        bars.forEach { (view, drawable) -> view.overlay.remove(drawable) }
        bars.clear()
        titles.forEach { (view, value) -> if (view.text.toString() == value.second) view.text = value.first }
        titles.clear()
    }

    /** Only the expanded native controls expose the optional details entry. */
    fun update(host: Activity, marks: List<SkipEngine.Segment>, duration: Long): Boolean {
        val root = host.findViewById<ViewGroup>(android.R.id.content) ?: return false
        val enabled = marks.filter { AirborneConfig.mode(host, it.category) != SkipEngine.Mode.DISABLED }
        val categories = AirborneConfig.categories.filter { category -> enabled.any { it.category == category.key } }
        val title = categories.firstOrNull()?.title.orEmpty() + if (categories.size > 1) " +${categories.size - 1}" else ""
        var foundTitle = false
        var controlsVisible = false
        fun actuallyVisible(view: View): Boolean {
            if (!view.isShown || view.width == 0 || view.height == 0) return false
            var current: View? = view
            while (current != null) {
                if (current.alpha <= 0.05f) return false
                current = current.parent as? View
            }
            return true
        }
        fun visit(view: View) {
            val name = if (view.id == View.NO_ID) "" else runCatching { host.resources.getResourceEntryName(view.id) }.getOrDefault("")
            // et1.a is the 9.12.0 mini timeline (no resource ID), verified in the pinned host.
            if (name in barIds || view.javaClass.name == "et1.a") {
                if (name in barIds && actuallyVisible(view)) controlsVisible = true
                val drawable = bars.getOrPut(view) {
                    MarkerDrawable(view).also { view.overlay.add(it) }
                }
                drawable.marks = enabled
                drawable.duration = duration
                drawable.setBounds(0, 0, view.width, view.height)
                drawable.invalidateSelf()
            }
            // In the pinned detail RecyclerView, the first title belongs to the current video.
            if (!foundTitle && name == "title" && view is TextView && view.isShown && view.parent is ViewGroup &&
                (view.parent as ViewGroup).parent is androidx.recyclerview.widget.RecyclerView) {
                foundTitle = true
                val previous = titles[view]
                val original = if (previous != null && view.text.toString() == previous.second) previous.first else view.text
                if (title.isEmpty()) {
                    if (previous != null) view.text = original
                    titles.remove(view)
                } else {
                    val prefix = " $title "
                    val text = SpannableStringBuilder(prefix).apply {
                        setSpan(BackgroundColorSpan(0xff245d37.toInt()), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        setSpan(ForegroundColorSpan(0xffffffff.toInt()), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        setSpan(RelativeSizeSpan(0.75f), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        append(" ").append(original)
                    }
                    if (view.text.toString() != text.toString()) view.text = text
                    titles[view] = original to text.toString()
                }
            }
            if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
        }
        visit(root)
        return controlsVisible
    }

    private class MarkerDrawable(private val host: View) : Drawable() {
        var marks: List<SkipEngine.Segment> = emptyList()
        var duration = 0L
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun draw(canvas: Canvas) {
            if (duration <= 0 || host.width == 0) return
            val left = host.paddingLeft.toFloat()
            val width = host.width - host.paddingLeft - host.paddingRight
            val y = host.height / 2f
            val thickness = (3 * host.resources.displayMetrics.density).coerceAtMost(host.height.toFloat())
            for (s in marks) {
                if (s.action == "full") continue
                val x1 = left + width * (s.start.toFloat() / duration).coerceIn(0f, 1f)
                val x2 = left + width * (s.end.toFloat() / duration).coerceIn(0f, 1f)
                paint.color = AirborneConfig.color(s.category)
                canvas.drawRect(x1, y - thickness / 2, (x2.coerceAtLeast(x1 + thickness)).coerceAtMost(left + width), y + thickness / 2, paint)
            }
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter }
        @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
