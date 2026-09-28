package app.revanced.bilibili.clean

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Environment
import android.widget.Toast
import androidx.annotation.Keep
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Keep
object CleanUpdate {
    const val VERSION = "0.4.0"
    const val REPOSITORY = "LumiaBlack51/bili-clean-patch"
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private var scheduled = false
    private var downloadPollScheduled = false
    @JvmStatic fun automatic() = CleanPlayback.prefs().getBoolean("auto_update", true)
    @JvmStatic fun setAutomatic(value: Boolean) { CleanPlayback.prefs().edit().putBoolean("auto_update", value).apply() }
    @JvmStatic fun newer(tag: String, current: String): Boolean {
        fun parts(value: String): List<Int>? = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)$").matchEntire(value)?.groupValues?.drop(1)?.map { it.toIntOrNull() ?: return null }
        val a = parts(tag) ?: return false; val b = parts(current) ?: return false
        for (i in 0..2) { if (a[i] != b[i]) return a[i] > b[i] }; return false
    }
    @JvmStatic fun resumed(activity: Activity) {
        if (activity.isFinishing) return
        pollDownload(activity)
        if (!automatic() || scheduled || System.currentTimeMillis() - CleanPlayback.prefs().getLong("update_checked", 0) < 86_400_000L) return
        scheduled = true
        val ref = WeakReference(activity)
        main.postDelayed({ scheduled = false; ref.get()?.takeUnless { it.isDestroyed || it.isFinishing }?.let { check(it, false) } }, 8000)
    }
    @JvmStatic fun check(activity: Activity, manual: Boolean) {
        if (!busy.compareAndSet(false, true)) return
        if (manual) Toast.makeText(activity, "正在检查 GitHub 更新…", Toast.LENGTH_SHORT).show()
        val ref = WeakReference(activity)
        worker.execute {
            val result = runCatching {
                val conn = URL("https://api.github.com/repos/$REPOSITORY/releases/latest").openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = 15000; conn.readTimeout = 20000
                    conn.setRequestProperty("Accept", "application/vnd.github+json")
                    conn.setRequestProperty("User-Agent", "BiliClean/$VERSION")
                    if (conn.responseCode == 404) return@runCatching null
                    check(conn.responseCode == 200) { "GitHub HTTP ${conn.responseCode}" }
                    JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                } finally { conn.disconnect() }
            }
            main.post {
                busy.set(false)
                val host = ref.get()?.takeUnless { it.isDestroyed || it.isFinishing } ?: return@post
                if (result.isFailure) {
                    if (manual) Toast.makeText(host, "检查失败，请确认能连接 GitHub 后重试", Toast.LENGTH_LONG).show()
                    return@post
                }
                CleanPlayback.prefs().edit().putLong("update_checked", System.currentTimeMillis()).apply()
                val release = result.getOrNull()
                val tag = release?.optString("tag_name").orEmpty()
                if (release == null || release.optBoolean("draft") || release.optBoolean("prerelease") || !newer(tag, VERSION)) {
                    if (manual) Toast.makeText(host, "当前已是最新补丁 $VERSION", Toast.LENGTH_LONG).show()
                    return@post
                }
                val assets = release.optJSONArray("assets") ?: return@post
                val asset = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull {
                    it.optString("name").startsWith("BiliClean-") && it.optString("name").endsWith(".apk") &&
                        it.optString("browser_download_url").startsWith("https://github.com/$REPOSITORY/releases/download/")
                }
                if (asset == null) { if (manual) Toast.makeText(host, "新版尚未上传安装包", Toast.LENGTH_LONG).show(); return@post }
                AlertDialog.Builder(host).setTitle("发现补丁更新 $tag")
                    .setMessage(release.optString("body").take(1800).ifBlank { "下载新版后由系统确认安装，保留应用数据。" })
                    .setNegativeButton("稍后", null).setPositiveButton("下载更新") { _, _ -> download(host, asset) }.show()
            }
        }
    }
    private fun download(host: Activity, asset: JSONObject) {
        runCatching {
            val name = asset.getString("name").replace(Regex("[^A-Za-z0-9._-]"), "_")
            val manager = host.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(asset.getString("browser_download_url")))
                .setTitle("哔哩哔哩补丁更新").setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(host, Environment.DIRECTORY_DOWNLOADS, "${System.currentTimeMillis()}-$name")
            val id = manager.enqueue(request)
            CleanPlayback.prefs().edit().putLong("update_download", id).apply()
            Toast.makeText(host, "正在下载，完成后将打开系统安装器", Toast.LENGTH_LONG).show()
            val ref = WeakReference(host)
            main.postDelayed({ ref.get()?.let { pollDownload(it) } }, 2000)
        }.onFailure { Toast.makeText(host, "下载未能开始，请稍后重试", Toast.LENGTH_LONG).show() }
    }
    private fun pollDownload(host: Activity) {
        if (downloadPollScheduled) return
        val id = CleanPlayback.prefs().getLong("update_download", -1); if (id < 0 || host.isDestroyed) return
        runCatching {
            val manager = host.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val status = manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                if (!cursor.moveToFirst()) DownloadManager.STATUS_FAILED else cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            }
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val uri = manager.getUriForDownloadedFile(id) ?: return
                    host.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
                    CleanPlayback.prefs().edit().remove("update_download").apply()
                }
                DownloadManager.STATUS_FAILED -> {
                    CleanPlayback.prefs().edit().remove("update_download").apply()
                    Toast.makeText(host, "更新下载失败，可在设置中重新检查", Toast.LENGTH_LONG).show()
                }
                else -> {
                    val ref = WeakReference(host); downloadPollScheduled = true
                    main.postDelayed({ downloadPollScheduled = false; ref.get()?.let { pollDownload(it) } }, 3000)
                }
            }
        }.onFailure { CleanPlayback.prefs().edit().remove("update_download").apply(); Toast.makeText(host, "请从下载通知打开安装包", Toast.LENGTH_LONG).show() }
    }
}
