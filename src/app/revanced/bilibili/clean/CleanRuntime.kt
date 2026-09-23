package app.revanced.bilibili.clean

import android.app.Activity
import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.Keep
import app.revanced.bilibili.patches.main.ApplicationDelegate
import app.revanced.bilibili.patches.main.VideoInfoHolder
import app.revanced.bilibili.settings.Settings
import app.revanced.bilibili.utils.av2bv
import com.bapis.bilibili.playershared.BizType
import org.json.JSONArray
import tv.danmaku.ijk.media.player.IMediaPlayer
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** No cookies, account ID, membership lookup, write API, or telemetry. */
object CleanRuntime {
    private val main = Handler(Looper.getMainLooper())
    private val network = Executors.newSingleThreadExecutor()
    private val engine = SkipEngine()
    private var player = WeakReference<IMediaPlayer>(null)
    private var activity = WeakReference<Activity>(null)
    private var marker: TextView? = null
    private var key = ""
    private var epoch = 0L
    private var status = "等待视频信息"
    private var failedSeek = false

    @Keep @JvmStatic
    fun onPrepared(value: IMediaPlayer) {
        Log.i("BiliClean", "player-prepared type=${value.javaClass.name}")
        main.post {
            detach()
            player = WeakReference(value)
            activity = WeakReference(ApplicationDelegate.getTopActivity())
            main.postDelayed(tick, 500)
        }
    }

    private fun detach() {
        main.removeCallbacks(tick)
        (marker?.parent as? ViewGroup)?.removeView(marker)
        marker = null
        engine.reset()
        key = ""
        epoch++
        failedSeek = false
    }

    private fun identity(): Pair<String, Long>? {
        val owner = activity.get() ?: return null
        val (aid, cid) = CleanMetadata.current(owner) ?: return null
        return av2bv(aid) to cid
    }

    private val tick: Runnable = object : Runnable {
        override fun run() {
            val host = activity.get()
            val media = player.get()
            if (host == null || host.isDestroyed || media == null) { detach(); return }
            if (ApplicationDelegate.getTopActivity() !== host) {
                marker?.visibility = android.view.View.GONE
                main.postDelayed(this, 500)
                return
            }
            try {
                if (!Settings.CleanAirborne()) {
                    marker?.visibility = android.view.View.GONE
                    main.postDelayed(this, 500)
                    return
                }
                val id = identity()
                if (id == null) {
                    if (key.isNotEmpty()) { key = ""; engine.reset(); epoch++ }
                    marker?.visibility = android.view.View.GONE
                    main.postDelayed(this, 250)
                    return
                }
                val selected = "${id.first}:${id.second}"
                if (key != selected && media.duration > 0) {
                    key = selected
                    failedSeek = false
                    val generation = engine.select(selected)
                    val requestEpoch = ++epoch
                    status = "读取广告标记…"
                    fetch(id.first, id.second, generation, requestEpoch, media.duration)
                }
                showMarker(host)
                val segment = engine.at(media.currentPosition, media.isPlaying,
                    Settings.CleanAutoSkip() && !failedSeek)
                if (segment != null) {
                    try {
                        val from = media.currentPosition
                        media.seekTo(segment.end)
                        engine.acknowledge(segment)
                        // This records a request, never claims actual playback arrived there.
                        Log.i("BiliClean", "seek-request video=$key from=$from to=${segment.end} uuid=${segment.id}")
                        if (Settings.CleanNotice()) Toast.makeText(host,
                            "空降助手：跳过广告至 ${segment.end / 1000} 秒", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        failedSeek = true
                        status = "跳转失败，自动跳过已暂停"
                        Log.w("BiliClean", "seek-failed type=${e.javaClass.simpleName}")
                    }
                }
            } catch (e: Exception) {
                status = "播放器暂不可用"
                Log.w("BiliClean", "player-state type=${e.javaClass.simpleName}")
            }
            main.postDelayed(this, 200)
        }
    }

    private fun fetch(bvid: String, cid: Long, generation: Long, requestEpoch: Long, duration: Long) {
        network.execute {
            var result: List<SkipEngine.Segment> = emptyList()
            var message: String
            var connection: HttpURLConnection? = null
            try {
                connection = URL("https://www.bsbsb.top/api/skipSegments?videoID=$bvid&cid=$cid")
                    .openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", "BiliCleanPatch/0.1")
                val code = connection.responseCode
                if (code == 404) message = "此视频暂无广告标记"
                else if (code != 200) message = "标记服务请求失败（HTTP $code）"
                else {
                    val bytes = connection.inputStream.use { stream ->
                        val buffer = java.io.ByteArrayOutputStream()
                        val chunk = ByteArray(8192)
                        while (true) {
                            val n = stream.read(chunk)
                            if (n < 0) break
                            require(buffer.size() + n <= 1024 * 1024) { "response too large" }
                            buffer.write(chunk, 0, n)
                        }
                        buffer.toByteArray()
                    }
                    val data = JSONArray(String(bytes, Charsets.UTF_8))
                    result = (0 until data.length()).mapNotNull { i ->
                        val item = data.getJSONObject(i)
                        val range = item.optJSONArray("segment") ?: return@mapNotNull null
                        if (item.optString("cid") != cid.toString() ||
                            item.optString("category") != "sponsor" ||
                            item.optString("actionType") != "skip" || range.length() != 2)
                            return@mapNotNull null
                        val start = range.optDouble(0)
                        val end = range.optDouble(1)
                        val videoDuration = item.optDouble("videoDuration")
                        if (!start.isFinite() || !end.isFinite() || start < 0 || end <= start ||
                            end * 1000 > duration || !videoDuration.isFinite() ||
                            kotlin.math.abs(videoDuration * 1000 - duration) > 3000) return@mapNotNull null
                        val uuid = item.optString("UUID")
                        if (uuid.isEmpty()) null else SkipEngine.Segment(uuid,
                            (start * 1000).toLong(), (end * 1000).toLong())
                    }
                    message = if (result.isEmpty()) "此视频暂无匹配的广告标记" else "${result.size} 段广告标记"
                }
            } catch (e: Exception) {
                message = "标记读取失败，正常播放不受影响"
                Log.w("BiliClean", "fetch-failed type=${e.javaClass.simpleName}")
            } finally { connection?.disconnect() }
            main.post {
                if (requestEpoch == epoch && engine.load(generation, result, duration)) {
                    status = message
                    Log.i("BiliClean", "markers video=$bvid:$cid count=${result.size} status=$message")
                }
            }
        }
    }

    private fun showMarker(host: Activity) {
        if (marker == null) {
            val root = host.findViewById<ViewGroup>(android.R.id.content) ?: return
            val text = TextView(host)
            text.textSize = 12f
            text.setTextColor(0xffffffff.toInt())
            text.setBackgroundColor(0xcc303030.toInt())
            text.setPadding(16, 8, 16, 8)
            val layout = FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END)
            layout.topMargin = (56 * host.resources.displayMetrics.density).toInt()
            root.addView(text, layout)
            text.setOnClickListener {
                val marks = engine.markers().joinToString("\n") { "广告：${it.start / 1000.0}–${it.end / 1000.0} 秒" }
                AlertDialog.Builder(host).setTitle("空降助手")
                    .setMessage("$status\n$marks\n设置入口：我的 → 设置 → 去广告与空降助手")
                    .setPositiveButton("确定", null).show()
            }
            marker = text
        }
        marker?.visibility = android.view.View.VISIBLE
        marker?.text = "空降助手 · $status"
    }
}
