package app.revanced.bilibili.clean

import android.app.AlertDialog
import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

object AirborneConfigDialog {
    fun rows(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val pad = (12 * resources.displayMetrics.density).toInt()
        addView(TextView(context).apply {
            text = "分类与进度条颜色\n点选每一类可设置行为，返回视频立即生效。全片标签、精彩时刻及静音标记不会自动跳过。"
            textSize = 14f; setPadding(0, pad, 0, pad)
        })
        for (category in AirborneConfig.categories) {
            val row = TextView(context).apply {
                textSize = 16f; minHeight = pad * 5
                setPadding(0, pad, 0, pad)
                isFocusable = true
            }
            fun refresh() {
                row.text = SpannableString("● ${category.title}\n${AirborneConfig.labels[AirborneConfig.mode(context, category.key).ordinal]}  ›").apply {
                    setSpan(ForegroundColorSpan(category.color), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            refresh()
            row.setOnClickListener {
                val modes = if (category.key in setOf("exclusive_access", "poi_highlight"))
                    arrayOf(SkipEngine.Mode.SHOW, SkipEngine.Mode.DISABLED) else SkipEngine.Mode.values()
                AlertDialog.Builder(context).setTitle(category.title)
                    .setSingleChoiceItems(modes.map { AirborneConfig.labels[it.ordinal] }.toTypedArray(),
                        modes.indexOf(AirborneConfig.mode(context, category.key))) { dialog, which ->
                        AirborneConfig.save(context, category.key, modes[which]); refresh(); dialog.dismiss()
                    }.setNegativeButton("取消", null).show()
            }
            addView(row, LinearLayout.LayoutParams(-1, -2))
        }
    }
    fun show(context: Context) {
        val padding = (20 * context.resources.displayMetrics.density).toInt()
        val content = rows(context).apply { setPadding(padding, 0, padding, 0) }
        AlertDialog.Builder(context).setTitle("空降助手设置")
            .setView(ScrollView(context).apply { addView(content) }).setPositiveButton("完成", null).show()
    }
}
