package app.revanced.bilibili.clean

import android.os.Bundle
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.annotation.Keep
import androidx.fragment.app.Fragment
import app.revanced.bilibili.settings.BooleanSetting
import app.revanced.bilibili.settings.Settings

/** Native controls inside the host settings activity; no obfuscated PreferenceManager dependency. */
@Keep
class CleanSettingsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val context = requireContext()
        val padding = (20 * resources.displayMetrics.density).toInt()
        val rows = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        fun text(value: String) = TextView(context).apply {
            text = value; textSize = 14f; setPadding(0, padding / 2, 0, padding)
        }
        rows.addView(TextView(context).apply { text = "去广告与空降助手"; textSize = 22f })
        fun toggle(title: String, summary: String, setting: BooleanSetting): Switch {
            val control = Switch(context).apply {
                text = title; textSize = 17f; minHeight = padding * 3
                // Host dark mode updates TextView defaults but not the platform Switch style.
                setTextColor(TextView(context).textColors)
                val states = arrayOf(intArrayOf(-android.R.attr.state_enabled),
                    intArrayOf(android.R.attr.state_checked), intArrayOf())
                thumbTintList = ColorStateList(states, intArrayOf(Color.GRAY, Color.rgb(251, 91, 145), Color.LTGRAY))
                trackTintList = ColorStateList(states, intArrayOf(Color.DKGRAY, Color.rgb(154, 72, 103), Color.GRAY))
                isChecked = setting.get()
                setOnCheckedChangeListener { _, enabled -> setting.save(enabled) }
            }
            rows.addView(control, LinearLayout.LayoutParams(-1, -2))
            rows.addView(text(summary))
            return control
        }
        toggle("过滤界面广告", "过滤已适配的开屏、推荐流、视频页和暂停广告。", Settings.CleanAds)
        toggle("空降助手", "显示详情页分类标签和彩色进度条。轻点视频呼出控件时显示片段入口，正常观看时隐藏；手动跳过按钮只在对应时段出现。", Settings.CleanAirborne)
        toggle("显示跳过提示", "跳过时显示目标时间。", Settings.CleanNotice)
        rows.addView(AirborneConfigDialog.rows(context))
        rows.addView(text("没有标记时正常播放；服务故障会单独提示。社区标记可能不准确，可随时关闭自动跳过。"))
        return ScrollView(context).apply { addView(rows) }
    }
}
