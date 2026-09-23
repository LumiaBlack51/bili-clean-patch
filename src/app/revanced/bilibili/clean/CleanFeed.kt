package app.revanced.bilibili.clean

import android.util.Log
import androidx.annotation.Keep
import app.revanced.bilibili.settings.Settings

object CleanFeed {
    private fun field(value: Any?, name: String): Any? {
        if (value == null) return null
        var type: Class<*>? = value.javaClass
        while (type != null) {
            try { return type.getDeclaredField(name).apply { isAccessible = true }.get(value) }
            catch (_: NoSuchFieldException) { type = type.superclass }
        }
        return null
    }
    @Keep @JvmStatic
    fun filter(response: Any?) {
        if (!Settings.CleanAds()) return
        try {
            val data = field(response, "data") ?: return
            val list = field(data, "items") as? MutableList<*> ?: return
            val iterator = list.iterator()
            var removed = 0
            while (iterator.hasNext()) {
                val item = iterator.next() ?: continue
                val type = (field(item, "cardGoto") ?: field(item, "card_goto")) as? String
                if (field(item, "adInfo") != null || type in setOf("ad", "cm", "special_s") ||
                    type?.startsWith("cm_") == true || type?.startsWith("ad_") == true) {
                    iterator.remove(); removed++
                }
            }
            Log.i("BiliClean", "feed ads-removed=$removed")
        } catch (e: Exception) { Log.w("BiliClean", "feed unchanged: ${e.javaClass.simpleName}") }
    }
}
