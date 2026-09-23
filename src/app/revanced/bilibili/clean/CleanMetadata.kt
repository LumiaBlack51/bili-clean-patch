package app.revanced.bilibili.clean

import android.app.Activity
import android.util.Log
import androidx.annotation.Keep
import app.revanced.bilibili.patches.main.ApplicationDelegate
import com.bapis.bilibili.app.playerunite.v1.PlayViewUniteReply
import com.bapis.bilibili.playershared.BizType
import com.bilibili.lib.moss.api.MossResponseHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

/** Observe the same playback reply that provides the active UGC media URL. */
object CleanMetadata {
    private val identities = java.util.Collections.synchronizedMap(
        java.util.WeakHashMap<Activity, Pair<Long, Long>>())
    fun current(owner: Activity): Pair<Long, Long>? = identities[owner]

    @Keep @JvmStatic fun wrap(request: Any, handler: Any?): Any? {
        if (handler == null || request.javaClass.name !=
            "com.bapis.bilibili.app.playerunite.v1.PlayViewUniteReq") return null
        val owner = ApplicationDelegate.getTopActivity() ?: return null
        identities.remove(owner)
        return Proxy.newProxyInstance(handler.javaClass.classLoader,
            arrayOf(MossResponseHandler::class.java)) { _, method, args ->
            if ((method.name == "onNext" || method.name == "onNextForAck") &&
                ApplicationDelegate.getTopActivity() === owner) {
                try {
                    val reply = args?.firstOrNull() as? PlayViewUniteReply
                    if (reply != null) {
                        val arc = reply.playArc
                        if (arc.videoType == BizType.BIZ_TYPE_UGC && arc.aid > 0 && arc.cid > 0)
                            identities[owner] = arc.aid to arc.cid
                        Log.i("BiliClean", "play-metadata aid=${arc.aid} cid=${arc.cid} type=${arc.videoType}")
                    }
                } catch (failure: Throwable) {
                    Log.w("BiliClean", "metadata-unavailable type=${failure.javaClass.simpleName}")
                }
            }
            try { method.invoke(handler, *(args ?: emptyArray())) }
            catch (error: InvocationTargetException) { throw error.targetException }
        }
    }
}
