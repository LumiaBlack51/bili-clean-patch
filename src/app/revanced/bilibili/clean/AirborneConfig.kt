package app.revanced.bilibili.clean

import android.content.Context
import app.revanced.bilibili.settings.Settings

/** Stable string keys: changing category order must not change a saved policy. */
object AirborneConfig {
    data class Category(val key: String, val title: String, val color: Int)
    val categories = listOf(
        Category("sponsor", "赞助/恰饭", 0xff00d400.toInt()),
        Category("selfpromo", "无偿/自我推广", 0xffffff00.toInt()),
        Category("exclusive_access", "独家访问/抢先体验", 0xff008a5c.toInt()),
        Category("interaction", "三连/互动提醒", 0xffcc00ff.toInt()),
        Category("poi_highlight", "精彩时刻/重点", 0xffff1684.toInt()),
        Category("intro", "过场/开场动画", 0xff00ffff.toInt()),
        Category("outro", "鸣谢/结束画面", 0xff0202ed.toInt()),
        Category("preview", "回顾/概要", 0xff008fd6.toInt()),
        Category("padding", "填充内容/前黑/后黑", 0xff777777.toInt()),
        Category("filler", "离题闲聊/玩笑", 0xff7300ff.toInt()),
        Category("music_offtopic", "音乐：非音乐部分", 0xffff9900.toInt()))
    val labels = arrayOf("总是跳过", "跳过一次（允许回看）", "手动跳过", "仅显示", "禁用")
    fun category(key: String) = categories.firstOrNull { it.key == key }
    private fun prefs(context: Context) = context.getSharedPreferences("clean_airborne_categories", Context.MODE_PRIVATE)
    fun mode(context: Context, category: String): SkipEngine.Mode {
        val fallback = if (category == "sponsor" && Settings.CleanAutoSkip()) SkipEngine.Mode.ONCE else SkipEngine.Mode.SHOW
        return runCatching { SkipEngine.Mode.valueOf(prefs(context).getString(category, fallback.name)!!) }.getOrDefault(fallback)
    }
    fun save(context: Context, category: String, mode: SkipEngine.Mode) {
        prefs(context).edit().putString(category, mode.name).apply()
    }
    fun color(key: String) = category(key)?.color ?: 0xffaaaaaa.toInt()
    fun title(key: String) = category(key)?.title ?: key
}
