package app.revanced.bilibili.clean

import android.os.Bundle
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
                isChecked = setting.get()
                setOnCheckedChangeListener { _, enabled -> setting.save(enabled) }
            }
            rows.addView(control, LinearLayout.LayoutParams(-1, -2))
            rows.addView(text(summary))
            return control
        }
        toggle("过滤界面广告", "过滤已适配的开屏、推荐流和视频页广告。", Settings.CleanAds)
        val airborne = toggle("空降助手", "读取社区广告标记，无账号等级或大会员门槛。", Settings.CleanAirborne)
        val auto = toggle("自动跳过广告片段", "仅跳过广告分类；已跳过的片段允许手动回看。", Settings.CleanAutoSkip)
        auto.isEnabled = airborne.isChecked
        airborne.setOnCheckedChangeListener { _, enabled -> Settings.CleanAirborne.save(enabled); auto.isEnabled = enabled }
        toggle("显示跳过提示", "跳过时显示目标时间。", Settings.CleanNotice)
        rows.addView(text("没有标记时正常播放；服务故障会单独提示。社区标记可能不准确，可随时关闭自动跳过。"))
        return ScrollView(context).apply { addView(rows) }
    }
}
