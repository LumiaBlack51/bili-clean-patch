package app.revanced.bilibili.clean

import android.net.Uri
import androidx.annotation.Keep
import app.revanced.bilibili.settings.Settings
import app.revanced.bilibili.utils.Utils
import android.content.Context
import android.content.Intent
import android.view.View

/** Narrow rules for the pinned 9.12.0 models. Never search video titles/descriptions. */
object CleanContent {
    private const val SETTINGS_TAB = "bilibili://pegasus/promo?biliclean_settings=1"
    internal fun field(value: Any?, name: String): Any? {
        if (value == null) return null
        var type: Class<*>? = value.javaClass
        while (type != null) {
            try { return type.getDeclaredField(name).apply { isAccessible = true }.get(value) }
            catch (_: NoSuchFieldException) { type = type.superclass }
        }
        return null
    }
    private fun call(value: Any?, name: String): Any? =
        if (value == null) null else runCatching { value.javaClass.getMethod(name).invoke(value) }.getOrNull()

    internal fun adType(type: String?): Boolean = type in setOf("ad", "cm", "special_s") ||
        type?.startsWith("ad_") == true || type?.startsWith("cm_") == true

    internal fun mallUri(value: Any?): Boolean {
        val uri = (value as? String)?.let { Uri.parse(it) } ?: return false
        return (uri.scheme == "bilibili" && uri.host in setOf("mall", "show", "shopping")) ||
            (uri.scheme in setOf("http", "https") && uri.host in setOf("mall.bilibili.com", "show.bilibili.com"))
    }

    /** Commercial metadata can accompany an ordinary av with is_ad=false. */
    internal fun paidPromotion(item: Any): Boolean {
        val info = call(item, "getAdInfo") ?: field(item, "adInfo") ?: return false
        if (info.javaClass.name !in setOf("com.bilibili.adcommon.data.AdInfo",
                "com.bilibili.adcommon.data.model.FeedAdInfo")) return false
        // These are the pinned host's commercial badge codes, not arbitrary nonzero values.
        val mark = (call(info, "getCmMark") as? Number)?.toInt()
        if (mark in setOf(1, 3, 5, 6, 7, 8) ||
            (call(info, "getNatureAd") as? Number)?.toInt() == 1) return true
        val extra = call(info, "getExtra") ?: field(info, "feedExtra") ?: return false
        val card = field(extra, "card") ?: return false
        // Story renders its rocket as a server-supplied image in ad_tag_style.
        // Use the host's validity check; an empty ad slot/card is not a promotion.
        return listOf(call(card, "getMarker"), call(card, "getAdTagStyleFullScreen")).any { style ->
            style != null && runCatching {
                Class.forName("com.bilibili.ad.adview.widget.marker.MarkLabelUtil")
                    .getMethod("isShowMark", style.javaClass).invoke(null, style) == true
            }.getOrDefault(false)
        }
    }

    internal fun homeExtra(item: Any, cardType: String?, goto: String?): Boolean {
        // Banner is a separate switch even when a banner contains an ad.
        if (cardType == "banner" || cardType?.startsWith("banner_v") == true || goto == "banner")
            return Settings.CleanHomeBanner()
        if (Settings.CleanMall() && mallUri(call(item, "getUri") ?: field(item, "uri"))) return true
        if (Settings.CleanPaidPromotion() && paidPromotion(item)) return true
        if (!Settings.CleanPromotion()) return false
        val reason = runCatching {
            val base = Class.forName("com.bilibili.pegasus.data.base.BasePegasusData")
            Class.forName("com.bilibili.pegasus.data.base.BasePegasusDataKt")
                .getMethod("getGetRecommendReason", base).invoke(null, item)
        }.getOrNull()
        return reason == "创作推广" || field(item, "rcmdReason") == "创作推广"
    }

    @Keep @JvmStatic fun filterStory(items: List<*>?): List<*>? {
        if (items == null || (!Settings.CleanPromotion() && !Settings.CleanMall() && !Settings.CleanPaidPromotion())) return items
        return items.filter { item ->
            if (item == null || item.javaClass.name != "com.bilibili.video.story.StoryDetail") true
            else {
                val promoted = Settings.CleanPromotion() &&
                    (call(field(item, "adInfo"), "isAd") == true || call(item, "getGotoIsAd") == true ||
                        adType(field(item, "goto") as? String) || adType(field(item, "cardGoto") as? String) ||
                        field(item, "rcmdReason") == "创作推广")
                val mall = Settings.CleanMall() && mallUri(field(item, "uri"))
                val paid = Settings.CleanPaidPromotion() && paidPromotion(item)
                !promoted && !mall && !paid
            }
        }
    }

    @Keep @JvmStatic fun filterTabs(items: List<*>?): List<*>? {
        if (items == null || !Settings.CleanMall()) return items
        return items.mapNotNull { item ->
            if (item?.javaClass?.name == "tv.danmaku.bili.ui.main2.resource.x" &&
                (field(item, "b") == "会员购" || mallUri(field(item, "d")))) {
                // Copy cached models: disabling the switch must be able to restore the original.
                val copy = item.javaClass.getConstructor().newInstance()
                item.javaClass.declaredFields.filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }.forEach {
                    it.isAccessible = true; it.set(copy, it.get(item))
                }
                fun set(name: String, value: Any?) { copy.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(copy, value) }
                set("a", "biliclean_settings"); set("b", "设置"); set("d", SETTINGS_TAB)
                set("e", ""); set("f", ""); set("g", "biliclean_settings"); set("j", null); set("k", false)
                val context = Utils.getContext()
                val iconId = context.resources.getIdentifier("bcg_sidebar_settings", "drawable", context.packageName)
                    .takeIf { it != 0 } ?: android.R.drawable.ic_menu_preferences
                set("c", Class.forName("g90.d").getConstructor(Context::class.java, Int::class.javaPrimitiveType)
                    .newInstance(context, iconId))
                return@mapNotNull copy
            }
            val fields = when (item?.javaClass?.name) {
                "tv.danmaku.bili.ui.main2.resource.MainResourceManager\$Tab" -> "name" to "uri"
                "tv.danmaku.bili.ui.main2.resource.x", "tv.danmaku.bili.ui.main2.resource.t" -> "b" to "d"
                "tv.danmaku.bili.ui.main2.resource.y" -> "b" to "c"
                else -> null
            }
            if (fields == null || (field(item, fields.first) != "会员购" && !mallUri(field(item, fields.second)))) item else null
        }
    }

    @Keep @JvmStatic fun openSettings(view: View): Boolean {
        // Intercept before TabHost changes selection, leaving the current page intact on Back.
        if (view.id != SETTINGS_TAB.hashCode()) return false
        return runCatching {
            view.context.startActivity(Intent().setClassName(view.context.packageName,
                "com.bilibili.app.preferences.BiliPreferencesActivity"))
            true
        }.getOrDefault(true) // Never fall through to an unrelated page for this synthetic button.
    }

    internal fun filterJson(data: Any) {
        if (!Settings.CleanMall()) return
        val tabs = when (data.javaClass.name) {
            "tv.danmaku.bili.ui.main2.resource.MainResourceManager\$TabResponse" -> field(data, "tabData")
            "tv.danmaku.bili.ui.main2.resource.MainResourceManager\$TabData" -> data
            else -> null
        } ?: return
        // Bottom is transformed after native model construction, preserving its position.
        for (name in listOf("top", "tab")) {
            val list = field(tabs, name) as? List<*> ?: continue
            tabs.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(tabs, filterTabs(list))
        }
    }
}
