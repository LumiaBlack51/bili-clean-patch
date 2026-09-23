package app.revanced.bilibili.clean

import android.util.Log
import app.revanced.bilibili.settings.Settings

/** Exact 9.12 model names inspected in the input DEX; unknown objects pass through. */
object CleanJson {
    @JvmStatic fun filter(value: Any?): Any? {
        if (value == null || !Settings.CleanAds()) return value
        try {
            val data = if (value.javaClass.name == "com.bilibili.okretro.GeneralResponse")
                value.javaClass.getField("data").get(value) ?: return value else value
            val fields = when (data.javaClass.name) {
                "tv.danmaku.bili.splash.ad.model.SplashListResponse" -> listOf("splashList", "strategyList")
                "tv.danmaku.bili.splash.ad.model.SplashShowResponse" -> listOf("strategyList")
                "tv.danmaku.bili.ui.splash.brand.model.BrandSplashData" -> listOf("brandList", "preloadList", "queryList", "showList")
                "com.bilibili.ad.adview.videodetail.danmakuv2.model.DmAdvert" -> listOf("ads")
                else -> return value
            }
            var removed = 0
            for (name in fields) {
                val field = data.javaClass.getDeclaredField(name).apply { isAccessible = true }
                val list = field.get(data) as? List<*> ?: continue
                removed += list.size
                field.set(data, ArrayList<Any>())
            }
            Log.i("BiliClean", "json-ads model=${data.javaClass.simpleName} removed=$removed")
        } catch (failure: Throwable) {
            Log.w("BiliClean", "json-filter-unavailable type=${failure.javaClass.simpleName}")
        }
        return value
    }
}
