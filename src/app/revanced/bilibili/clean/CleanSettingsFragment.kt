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
        rows.addView(Switch(context).apply {
            text = "YouTube 风格手势"; textSize = 17f; minHeight = padding * 3
            setTextColor(TextView(context).textColors); isChecked = CleanPlayback.enabled()
            setOnCheckedChangeListener { _, enabled -> CleanPlayback.setEnabled(enabled) }
        })
        rows.addView(text("可选开启：双击左侧后退 10 秒，双击右侧快进 10 秒；关闭双击暂停。单击视频显示中间的上一集、播放/暂停、下一集。"))
        rows.addView(text("定时关闭：在视频的定时关闭菜单选择「播放到当前视频结束」，结束后关闭播放页，不再自动连播。"))
        rows.addView(Switch(context).apply {
            text = "自动检查更新"; textSize = 17f; minHeight = padding * 3
            setTextColor(TextView(context).textColors); isChecked = CleanUpdate.automatic()
            setOnCheckedChangeListener { _, enabled -> CleanUpdate.setAutomatic(enabled) }
        })
        rows.addView(text("启动时每天检查 GitHub Release，发现新版后提示下载。当前补丁 ${CleanUpdate.VERSION}"))
        rows.addView(android.widget.Button(context).apply {
            text = "检查更新"; setOnClickListener { CleanUpdate.check(requireActivity(), true) }
        })
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
        toggle("隐藏首页横幅", "默认开启，移除首页推荐顶部的大横幅。修改后刷新首页。", Settings.CleanHomeBanner)
        toggle("屏蔽创作推广", "默认开启，过滤推荐中的创作推广，以及竖屏流中的广告推广视频。修改后重新进入推荐流。", Settings.CleanPromotion)
        toggle("屏蔽小火箭 / 付费推广", "默认开启，过滤首页推荐和竖屏流中带商业推广标记的视频。修改后刷新首页或重新进入竖屏流；其他广告开关仍可过滤同一视频。", Settings.CleanPaidPromotion)
        toggle("隐藏会员购", "默认开启，将底部会员购替换为设置，并过滤会员购推荐卡片。关闭后重启客户端恢复原入口。", Settings.CleanMall)
        toggle("空降助手", "显示详情页分类标签和彩色进度条。轻点视频呼出控件时显示片段入口，正常观看时隐藏；手动跳过按钮只在对应时段出现。", Settings.CleanAirborne)
        toggle("显示跳过提示", "跳过时显示目标时间。", Settings.CleanNotice)
        rows.addView(AirborneConfigDialog.rows(context))
        rows.addView(text("没有标记时正常播放；服务故障会单独提示。社区标记可能不准确，可随时关闭自动跳过。"))
        return ScrollView(context).apply { addView(rows) }
    }
}
