package app.revanced.bilibili.clean

import android.app.Activity
import androidx.annotation.Keep
import app.revanced.bilibili.utils.av2bv
import app.revanced.bilibili.utils.bv2av
import app.revanced.bilibili.utils.bvPattern
import org.json.JSONObject
import java.util.WeakHashMap

/** An explicit Story entry is protected only while its owning screen exists. */
@Keep
object CleanStoryEntry {
    private data class Selection(val aid: Long, val bvid: String?)
    private val entries = WeakHashMap<Activity, Selection>()
    private val bv = Regex(bvPattern)

    @JvmStatic fun entered(activity: Activity) {
        // Run before native onCreate can request or consume the first Story response.
        val selection = runCatching {
            val extras = activity.intent?.extras
            fun extra(key: String) = extras?.get(key)?.toString()?.trim().orEmpty()
            val id = extra("id").ifEmpty { extra("avid").ifEmpty { extra("aid") } }
            var aid = id.removePrefix("av").toLongOrNull()?.takeIf { it > 0 } ?: 0L
            var bvid = id.takeIf { bv.matches(it) }
                ?: if (aid == 0L) extra("bvid").takeIf { bv.matches(it) } else null
            // Cached entry cards can carry the identity without separate route arguments.
            if (aid == 0L && bvid == null) {
                val item = extra("story_item").takeIf { it.isNotEmpty() }?.let { JSONObject(it) }
                aid = item?.optJSONObject("player_args")?.optLong("aid")?.takeIf { it > 0 } ?: 0L
                bvid = if (aid == 0L) item?.optString("bvid")?.takeIf { bv.matches(it) } else null
            }
            if (aid == 0L && bvid != null) aid = bv2av(bvid)
            if (aid <= 0L) return@runCatching null
            val canonicalBv = av2bv(aid)
            if (bvid != null && canonicalBv != bvid) return@runCatching null
            Selection(aid, canonicalBv)
        }.getOrNull()
        synchronized(entries) {
            entries.remove(activity)
            if (selection != null) entries[activity] = selection
        }
    }

    @JvmStatic fun leaving(activity: Activity) {
        synchronized(entries) { entries.remove(activity) }
    }

    internal fun selections(): List<Pair<Long, String?>> = synchronized(entries) {
        entries.entries.filter { !it.key.isFinishing && !it.key.isDestroyed }
            .map { it.value.aid to it.value.bvid }
    }
}
