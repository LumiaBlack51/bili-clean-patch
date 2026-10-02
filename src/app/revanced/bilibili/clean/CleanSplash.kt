package app.revanced.bilibili.clean

import android.util.Log
import androidx.annotation.Keep
import app.revanced.bilibili.settings.Settings

/** Applied before the host reads cached orders or starts either hot-splash implementation. */
@Keep
object CleanSplash {
    @JvmStatic
    fun block(): Boolean = Settings.CleanAds().also {
        if (it) Log.i("BiliClean", "splash-entry blocked")
    }
}
