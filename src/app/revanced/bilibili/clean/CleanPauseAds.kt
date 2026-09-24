package app.revanced.bilibili.clean

import android.util.Log
import androidx.annotation.Keep
import app.revanced.bilibili.settings.Settings

/** The pinned host treats a null paused-page result as no advertisement. */
object CleanPauseAds {
    @Keep @JvmStatic
    fun block(): Boolean = Settings.CleanAds().also {
        if (it) Log.i("BiliClean", "pause-ad blocked")
    }
}
