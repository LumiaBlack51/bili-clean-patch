package app.revanced.bilibili.clean

import android.util.Log
import androidx.annotation.Keep
import app.revanced.bilibili.settings.Settings

object CleanFeed {
    private fun markedAd(info: Any?): Boolean {
        if (info == null) return false
        return try { info.javaClass.getMethod("isAd").invoke(info) == true }
        catch (_: NoSuchMethodException) { field(info, "isAd") == true }
    }
    @Keep @JvmStatic
    fun filterModern(response: Any?) {
        if (response == null || response.javaClass.name != "com.bilibili.pegasus.data.base.PegasusResponse") return
        try {
            val itemsField = response.javaClass.getDeclaredField("a").apply { isAccessible = true }
            val items = itemsField.get(response) as? List<*> ?: return
            val base = Class.forName("com.bilibili.pegasus.data.base.BasePegasusData")
            val adGetter = base.getMethod("getAdInfo")
            val gotoGetter = base.getMethod("getCardGoto")
            val filtered = ArrayList<Any?>()
            var ads = 0
            var placeholders = 0
            for (item in items) {
                val ad = if (item != null && base.isInstance(item)) {
                    val type = gotoGetter.invoke(item) as? String
                    val info = adGetter.invoke(item)
                    val realAd = markedAd(info)
                    if (info != null && !realAd) placeholders++
                    realAd || type in setOf("ad", "cm", "special_s") ||
                        type?.startsWith("ad_") == true || type?.startsWith("cm_") == true
                } else false
                if (ad) ads++
                if (!ad || !Settings.CleanAds()) filtered.add(item)
            }
            if (Settings.CleanAds() && ads > 0) itemsField.set(response, filtered)
            Log.i("BiliClean", "modern-feed items=${items.size} ads=$ads placeholders=$placeholders removed=${items.size - filtered.size} enabled=${Settings.CleanAds()}")
        } catch (e: Exception) { Log.w("BiliClean", "modern-feed unchanged: ${e.javaClass.simpleName}") }
    }
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
        try {
            val data = field(response, "data")
            if (data?.javaClass?.name == "com.bilibili.pegasus.data.base.PegasusResponse") {
                filterModern(data)
                return
            }
            if (!Settings.CleanAds()) return
            val list = field(data, "items") as? MutableList<*>
            Log.i("BiliClean", "feed-response type=${data?.javaClass?.name} items=${list?.size ?: -1}")
            if (list == null) return
            val iterator = list.iterator()
            var removed = 0
            while (iterator.hasNext()) {
                val item = iterator.next() ?: continue
                val type = (field(item, "cardGoto") ?: field(item, "card_goto")) as? String
                if (markedAd(field(item, "adInfo")) || type in setOf("ad", "cm", "special_s") ||
                    type?.startsWith("cm_") == true || type?.startsWith("ad_") == true) {
                    iterator.remove(); removed++
                }
            }
            Log.i("BiliClean", "feed ads-removed=$removed")
        } catch (e: Exception) { Log.w("BiliClean", "feed unchanged: ${e.javaClass.simpleName}") }
    }
}
