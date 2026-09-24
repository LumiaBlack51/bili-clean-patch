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
import app.revanced.bilibili.settings.Settings
import app.revanced.bilibili.utils.av2bv
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
    private val ui = AirborneUi()
    private var manual: TextView? = null
    private var pendingSeekUntil = 0L

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
        (manual?.parent as? ViewGroup)?.removeView(manual)
        manual = null
        ui.clear()
        pendingSeekUntil = 0L
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
                manual?.visibility = android.view.View.GONE
                ui.clear()
                main.postDelayed(this, 500)
                return
            }
            try {
                if (!Settings.CleanAirborne()) {
                    marker?.visibility = android.view.View.GONE
                    manual?.visibility = android.view.View.GONE
                    ui.clear()
                    main.postDelayed(this, 500)
                    return
                }
                val id = identity()
                if (id == null) {
                    if (key.isNotEmpty()) { key = ""; engine.reset(); epoch++; ui.clear() }
                    marker?.visibility = android.view.View.GONE
                    manual?.visibility = android.view.View.GONE
                    main.postDelayed(this, 250)
                    return
                }
                val selected = "${id.first}:${id.second}"
                if (key != selected && media.duration > 0) {
                    key = selected
                    failedSeek = false
                    val generation = engine.select(selected)
                    val requestEpoch = ++epoch
                    ui.clear()
                    status = "读取片段标记…"
                    fetch(id.first, id.second, generation, requestEpoch, media.duration)
                }
                showMarker(host)
                ui.update(host, engine.markers(), media.duration)
                val segment = engine.at(media.currentPosition, media.isPlaying,
                    !failedSeek && android.os.SystemClock.uptimeMillis() >= pendingSeekUntil) {
                    AirborneConfig.mode(host, it.category)
                }
                if (segment != null) seek(host, media, segment, true)
                showManual(host, media, media.currentPosition)
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
                connection = URL("https://www.bsbsb.top/api/skipSegments?videoID=$bvid&cid=$cid&categories=" +
                    java.net.URLEncoder.encode(JSONArray(AirborneConfig.categories.map { it.key }).toString(), "UTF-8"))
                    .openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", "BiliCleanPatch/0.2")
                connection.setRequestProperty("origin", "BiliCleanPatch")
                connection.setRequestProperty("x-ext-version", "0.2.0")
                val code = connection.responseCode
                if (code == 404) message = "此视频暂无片段标记"
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
                            AirborneConfig.category(item.optString("category")) == null ||
                            item.optString("actionType") !in setOf("skip", "full", "poi", "mute") || range.length() != 2)
                            return@mapNotNull null
                        val start = range.optDouble(0)
                        val end = range.optDouble(1)
                        val videoDuration = item.optDouble("videoDuration")
                        val action = item.optString("actionType")
                        if (!start.isFinite() || !end.isFinite() || start < 0 || end < start ||
                            (end == start && action !in setOf("full", "poi")) ||
                            end * 1000 > duration || !videoDuration.isFinite() || videoDuration < 0 ||
                            (videoDuration != 0.0 && kotlin.math.abs(videoDuration * 1000 - duration) > 3000)) return@mapNotNull null
                        val uuid = item.optString("UUID")
                        if (uuid.isEmpty()) null else SkipEngine.Segment(uuid,
                            (start * 1000).toLong(), (end * 1000).toLong(), item.optString("category"), action)
                    }
                    message = if (result.isEmpty()) "此视频暂无匹配的片段标记" else "${result.size} 个片段标记"
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
            text.setOnClickListener { showDetails(host) }
            marker = text
        }
        marker?.visibility = android.view.View.VISIBLE
        val visible = engine.markers().filter { AirborneConfig.mode(host, it.category) != SkipEngine.Mode.DISABLED }
        marker?.text = if (visible.isEmpty()) {
            if (engine.markers().isEmpty()) "空降助手 · $status" else "空降助手 · 分类已禁用 ›"
        } else "空降助手 · ${visible.size}处标记 ›"
    }

    private fun time(ms: Long): String = "%02d:%02d".format(ms / 60000, ms / 1000 % 60)

    private fun seek(host: Activity, media: IMediaPlayer, segment: SkipEngine.Segment, automatic: Boolean) {
        try {
            val from = media.currentPosition
            val target = if (segment.action == "poi") segment.start else segment.end
            media.seekTo(target)
            engine.acknowledge(segment)
            pendingSeekUntil = android.os.SystemClock.uptimeMillis() + 1500
            Log.i("BiliClean", "seek-request video=$key from=$from to=$target category=${segment.category} automatic=$automatic uuid=${segment.id}")
            if (Settings.CleanNotice()) Toast.makeText(host,
                "空降助手：${if (segment.action == "poi") "跳至" else "跳过"}${AirborneConfig.title(segment.category)} ${time(target)}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            failedSeek = true
            status = "跳转失败，自动跳过已暂停"
            Log.w("BiliClean", "seek-failed type=${e.javaClass.simpleName}")
        }
    }

    private fun showManual(host: Activity, media: IMediaPlayer, position: Long) {
        val current = engine.markers().firstOrNull {
            it.action == "skip" && it.start <= position && position < it.end &&
                AirborneConfig.mode(host, it.category) == SkipEngine.Mode.MANUAL
        }
        if (current == null) { manual?.visibility = android.view.View.GONE; return }
        if (manual == null) {
            val root = host.findViewById<ViewGroup>(android.R.id.content) ?: return
            manual = TextView(host).apply {
                textSize = 15f; setTextColor(0xffffffff.toInt()); setBackgroundColor(0xee303030.toInt())
                setPadding(24, 16, 24, 16)
                root.addView(this, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
                    topMargin = (96 * host.resources.displayMetrics.density).toInt()
                })
            }
        }
        manual?.apply {
            visibility = android.view.View.VISIBLE
            text = "跳过${AirborneConfig.title(current.category)} → ${time(current.end)}"
            setOnClickListener { seek(host, media, current, false) }
        }
    }

    private fun showDetails(host: Activity) {
        val selectedKey = key
        val marks = engine.markers().filter { AirborneConfig.mode(host, it.category) != SkipEngine.Mode.DISABLED }
        val labels = marks.map {
            val range = if (it.action == "full") "全片标签" else "${time(it.start)}–${time(it.end)}"
            val mode = if (it.action == "full") "全片标签（仅显示）" else if (it.action == "mute") "静音标记（仅显示）" else if (it.action == "poi") "点击跳至精彩时刻" else
                AirborneConfig.labels[AirborneConfig.mode(host, it.category).ordinal]
            "● ${AirborneConfig.title(it.category)}  $range\n$mode"
        }.toTypedArray()
        val dialog = AlertDialog.Builder(host).setTitle("空降助手 · 片段详情")
            .setNegativeButton("关闭", null)
            .setNeutralButton("分类行为设置") { _, _ -> AirborneConfigDialog.show(host) }
        if (marks.isEmpty()) dialog.setMessage(status)
        else dialog.setItems(labels) { _, index ->
            val s = marks[index]
            if (s.action == "full" || s.action == "mute") return@setItems
            AlertDialog.Builder(host).setTitle(AirborneConfig.title(s.category))
                .setMessage("${time(s.start)}–${time(s.end)}")
                .setPositiveButton(if (s.action == "poi") "跳至此处" else "跳过此片段") { _, _ ->
                    if (key == selectedKey && Settings.CleanAirborne()) player.get()?.let { seek(host, it, s, false) }
                }.setNegativeButton("取消", null).show()
        }
        dialog.show()
    }
}
