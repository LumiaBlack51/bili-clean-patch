package app.revanced.bilibili.clean

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.Keep
import app.revanced.bilibili.patches.main.ApplicationDelegate
import app.revanced.bilibili.utils.Utils
import tv.danmaku.ijk.media.player.IMediaPlayer
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Adapter for the pinned 9.12.0 host. No touch overlay covers the progress/volume gestures. */
@Keep
object CleanPlayback {
    private val main = Handler(Looper.getMainLooper())
    private val containers = WeakHashMap<Any, WeakReference<Any>>()
    private var core = WeakReference<Any>(null)
    private var media = WeakReference<IMediaPlayer>(null)
    private var owner = WeakReference<Activity>(null)
    private var controls: LinearLayout? = null
    private var armed = false
    private var armedIdentity: Pair<Long, Long>? = null
    private var armedCore = WeakReference<Any>(null)
    private var armedMedia = WeakReference<IMediaPlayer>(null)
    private var armedOwner = WeakReference<Activity>(null)
    fun prefs() = Utils.getContext().getSharedPreferences("biliclean_playback", Context.MODE_PRIVATE)
    @JvmStatic fun enabled() = prefs().getBoolean("youtube_gestures", false)
    @JvmStatic fun setEnabled(value: Boolean) { prefs().edit().putBoolean("youtube_gestures", value).apply() }
    internal fun field(obj: Any, name: String): Any? = obj.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(obj)
    internal fun invoke(obj: Any, name: String, vararg args: Any?): Any? {
        val method = obj.javaClass.methods.first { it.name == name && it.parameterTypes.size == args.size }
        method.isAccessible = true
        return method.invoke(obj, *args)
    }
    @JvmStatic fun bind(container: Any) {
        runCatching { invoke(container, "getPlayerCoreService")?.let { containers[it] = WeakReference(container) } }
            .onFailure { Log.w("BiliClean", "Playback container unavailable", it) }
    }
    @JvmStatic fun prepared(callback: Any, player: IMediaPlayer) {
        runCatching {
            val activeCore = field(callback, "a") ?: return
            field(activeCore, "a")?.let { containers[activeCore] = WeakReference(it) }
            core = WeakReference(activeCore); media = WeakReference(player)
            owner = WeakReference(ApplicationDelegate.getTopActivity())
            // A timer belongs to the selected video, never a subsequently selected episode.
            val activity = owner.get()
            if (armed && (armedCore.get() !== activeCore || armedMedia.get() !== player ||
                    (armedIdentity != null && activity != null && CleanMetadata.current(activity) != armedIdentity))) cancelEndTimer()
        }.onFailure { Log.w("BiliClean", "Playback binding failed", it) }
    }
    private fun container() = core.get()?.let { containers[it]?.get() }
    private fun director() = container()?.let { container ->
        runCatching { invoke(container, "getPlayDirectorServiceV3")?.let { invoke(it, "c") } }.getOrNull()
            ?: runCatching { invoke(container, "getVideoPlayDirectorService") }.getOrNull()
    }
    private fun modernDirector(value: Any) = value.javaClass.methods.any { it.name == "switchToNext" }
    private fun navigate(previous: Boolean) {
        val nav = director() ?: return
        cancelEndTimer()
        invoke(nav, if (modernDirector(nav)) {
            if (previous) "switchToPrevious" else "switchToNext"
        } else { if (previous) "playPrevious" else "playNext" }, false)
    }
    private fun controller() = container()?.let { invoke(it, "getPlayerCoreService") }
    private fun activeOwner(): Activity? = owner.get()?.takeIf { !it.isFinishing && !it.isDestroyed }
    private fun id(view: View) = runCatching { view.resources.getResourceEntryName(view.id) }.getOrDefault("")
    private fun find(view: View, name: String): View? {
        if (id(view) == name) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i), name)?.let { return it }
        return null
    }
    private fun visible(view: View?): Boolean {
        var current = view ?: return false
        if (!current.isShown || current.width == 0 || current.height == 0) return false
        while (true) { if (current.alpha <= .05f) return false; current = current.parent as? View ?: return true }
    }
    @JvmStatic fun doubleTap(event: MotionEvent): Boolean {
        if (!enabled()) return false
        val host = activeOwner() ?: return false
        if (ApplicationDelegate.getTopActivity() !== host) return false
        val surface = find(host.window.decorView, "control_container") ?: return false
        val bounds = Rect(); if (!surface.getGlobalVisibleRect(bounds)) return false
        // Raw coordinates remain correct in portrait, landscape and inset video layouts.
        if (!bounds.contains(event.rawX.toInt(), event.rawY.toInt())) return false
        val fraction = (event.rawX - bounds.left) / bounds.width()
        val delta = when { fraction < .4f -> -10_000L; fraction > .6f -> 10_000L; else -> 0L }
        if (delta != 0L) runCatching {
            media.get()?.let { player ->
                if (player.duration > 0) {
                    val target = (player.currentPosition + delta).coerceIn(0, (player.duration - 1).coerceAtLeast(0))
                    player.seekTo(target)
                    Toast.makeText(host, if (delta > 0) "快进 10 秒" else "后退 10 秒", Toast.LENGTH_SHORT).show()
                }
            }
        }.onFailure { Log.w("BiliClean", "Gesture seek failed", it) }
        return true // The middle region also consumes double-tap: never pause in this mode.
    }
    @JvmStatic fun refresh(host: Activity, player: IMediaPlayer) {
        if (owner.get() !== host || media.get() !== player || !enabled() || ApplicationDelegate.getTopActivity() !== host) {
            controls?.visibility = View.GONE; return
        }
        val root = host.findViewById<ViewGroup>(android.R.id.content) ?: return
        val surface = find(root, "control_container") ?: return
        val show = arrayOf("gemini_halfscreen_seekbar", "bbplayer_halfscreen_seekbar", "bbplayer_fullscreen_seekbar", "player_widget_progressbar", "ctrl_seekbar")
            .any { visible(find(root, it)) }
        if (!show) { controls?.visibility = View.GONE; return }
        var row = controls
        if (row == null || row.parent !== root) {
            clear()
            row = LinearLayout(host).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
                tag = "biliclean_center_controls"
                fun button(symbol: Int, label: String, action: () -> Unit) = ImageButton(host).apply {
                    setImageResource(symbol); setColorFilter(Color.WHITE)
                    contentDescription = label; tag = "biliclean_$label"
                    background = GradientDrawable().apply { setColor(0x99000000.toInt()); cornerRadius = 100f }
                    setOnClickListener { runCatching { action() }.onFailure { Log.w("BiliClean", label, it) } }
                }
                val size = (52 * host.resources.displayMetrics.density).toInt()
                fun add(control: View) { addView(control, LinearLayout.LayoutParams(size, size).apply { setMargins(size / 5, 0, size / 5, 0) }) }
                add(button(android.R.drawable.ic_media_previous, "上一集") { navigate(true) })
                add(button(android.R.drawable.ic_media_pause, "暂停或播放") { controller()?.let { invoke(it, if (player.isPlaying) "pause" else "resume") } })
                add(button(android.R.drawable.ic_media_next, "下一集") { navigate(false) })
            }
            root.addView(row, FrameLayout.LayoutParams(-2, -2)); controls = row
        }
        val bounds = Rect(); surface.getGlobalVisibleRect(bounds)
        val origin = IntArray(2); root.getLocationOnScreen(origin)
        row.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        row.x = bounds.exactCenterX() - origin[0] - row.measuredWidth / 2f
        row.y = bounds.exactCenterY() - origin[1] - row.measuredHeight / 2f
        row.visibility = View.VISIBLE
        (row.getChildAt(1) as ImageButton).setImageResource(if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        val nav = runCatching { director() }.getOrNull()
        for ((index, method) in listOf(0 to "hasPrevious", 2 to "hasNext")) {
            val available = nav != null && runCatching {
                (if (modernDirector(nav)) invoke(nav, method, false) else invoke(nav, method)) == true
            }.getOrDefault(false)
            row.getChildAt(index).isEnabled = available; row.getChildAt(index).alpha = if (available) 1f else .35f
        }
    }
    fun clear() { (controls?.parent as? ViewGroup)?.removeView(controls); controls = null }
    @JvmStatic fun cancelEndTimer() { armed = false; armedIdentity = null; armedCore.clear(); armedMedia.clear(); armedOwner.clear() }
    @JvmStatic fun isEndTimerArmed() = armed
    @JvmStatic fun armEndTimer(): Boolean {
        val host = activeOwner() ?: return false
        val player = media.get() ?: return false
        if (player.duration <= 0 || core.get() == null) return false
        armedCore = WeakReference(core.get()); armedMedia = WeakReference(player); armedOwner = WeakReference(host)
        armedIdentity = CleanMetadata.current(host); armed = true
        Toast.makeText(host, "将在当前视频播放结束后关闭播放页", Toast.LENGTH_LONG).show()
        return true
    }
    @JvmStatic fun stateChanged(source: Any, state: Int): Boolean {
        if (state != 6 || !armed || source !== armedCore.get()) return false
        val host = armedOwner.get() ?: return false
        if (armedIdentity != null && CleanMetadata.current(host) != armedIdentity) { cancelEndTimer(); return false }
        val player = armedMedia.get(); cancelEndTimer()
        // Consume completion before native next-video observers run. Finish on the UI queue.
        main.post { runCatching { player?.pause() }; if (!host.isDestroyed) host.finish() }
        Log.i("BiliClean", "end-of-video timer completed; autoplay suppressed")
        return true
    }
    @JvmStatic fun timerItems(original: Any): Any = runCatching {
        val type = original.javaClass.componentType!!
        val length = java.lang.reflect.Array.getLength(original)
        val result = java.lang.reflect.Array.newInstance(type, length + 1)
        System.arraycopy(original, 0, result, 0, length)
        val item = type.getConstructor(java.lang.Long::class.java, String::class.java, Int::class.javaPrimitiveType)
            .newInstance(null, "播放到当前视频结束", 6)
        java.lang.reflect.Array.set(result, length, item); result
    }.getOrElse { Log.w("BiliClean", "Timer menu failed", it); original }
    @JvmStatic fun timerClick(listener: Any): Boolean = runCatching {
        val item = field(listener, "b") ?: return false
        if (field(item, "a") != 6) { cancelEndTimer(); return false }
        val adapter = field(listener, "a")!!
        val callback = field(adapter, "b")!!
        val dialog = field(callback, "d") as Dialog
        val client = field(dialog, "f")!!
        invoke(client, "getService")?.let { invoke(it, "startShutOffTiming", 0L, false) }
        if (armEndTimer()) dialog.dismiss()
        true
    }.getOrElse { Log.w("BiliClean", "Timer selection failed", it); false }
    @JvmStatic fun timerOpened(dialog: Any) {
        if (armed) runCatching {
            val adapter = field(dialog, "b") ?: return
            adapter.javaClass.getDeclaredField("c").apply { isAccessible = true }.setInt(adapter, 6)
            invoke(adapter, "notifyDataSetChanged")
        }.onFailure { Log.w("BiliClean", "Timer selection display failed", it) }
    }
    @JvmStatic fun timerPanel(widget: Any, original: View): View {
        val layout = LinearLayout(original.context).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(original, LinearLayout.LayoutParams(-1, 0, 1f))
        layout.addView(TextView(original.context).apply {
            text = "播放到当前视频结束"; textSize = 16f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            minHeight = (52 * resources.displayMetrics.density).toInt()
            setOnClickListener { runCatching {
                field(widget, "b")?.let { invoke(it, "getService") }?.let { invoke(it, "startShutOffTiming", 0L, false) }
                if (armEndTimer()) {
                    val c = field(widget, "a")!!
                    invoke(c, "getFunctionWidgetService")?.let { invoke(it, "hideWidget", invoke(widget, "getToken")) }
                }
            }.onFailure { Log.w("BiliClean", "Fullscreen timer failed", it) } }
        }, LinearLayout.LayoutParams(-1, -2))
        return layout
    }
}
