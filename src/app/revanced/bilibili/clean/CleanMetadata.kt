package app.revanced.bilibili.clean

import android.util.Log
import androidx.annotation.Keep
import app.revanced.bilibili.meta.VideoInfo
import app.revanced.bilibili.patches.main.ApplicationDelegate
import app.revanced.bilibili.patches.main.VideoInfoHolder
import com.bapis.bilibili.app.playerunite.v1.PlayViewUniteReply
import com.bilibili.lib.moss.api.MossResponseHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

/** Observe only public-video metadata. Never rewrite playback requests or responses. */
object CleanMetadata {
    private val pending = java.util.Collections.synchronizedMap(
        java.util.WeakHashMap<android.app.Activity, Pair<Long, Long>>())
    private val requests = setOf(
        "com.bapis.bilibili.app.view.v1.ViewReq",
        "com.bapis.bilibili.app.viewunite.v1.ViewReq",
        "com.bapis.bilibili.app.playurl.v1.PlayViewReq",
        "com.bapis.bilibili.app.playerunite.v1.PlayViewUniteReq"
    )
    private fun aid(view: Any?): Long = when (view) {
        is com.bapis.bilibili.app.view.v1.ViewReply -> view.arc.aid
        is com.bapis.bilibili.app.viewunite.v1.ViewReply -> view.arc.aid
        else -> 0
    }
    @Keep @JvmStatic
    fun wrap(request: Any, handler: Any?): Any? {
        if (handler == null || request.javaClass.name !in requests) return null
        val owner = ApplicationDelegate.getTopActivity() ?: return null
        return Proxy.newProxyInstance(handler.javaClass.classLoader,
            arrayOf(MossResponseHandler::class.java)) { _, method, args ->
            if ((method.name == "onNext" || method.name == "onNextForAck") &&
                ApplicationDelegate.getTopActivity() === owner) {
                try {
                    val reply = args?.firstOrNull()
                    when (reply) {
                        is com.bapis.bilibili.app.view.v1.ViewReply,
                        is com.bapis.bilibili.app.viewunite.v1.ViewReply -> {
                            val newAid = aid(reply)
                            if (newAid > 0) VideoInfoHolder.updateCurrent { old ->
                                val known = pending[owner]?.takeIf { it.first == newAid }?.second
                                VideoInfo(known ?: if (aid(old?.view) == newAid) old?.cid ?: 0 else 0, reply)
                            }
                        }
                        is PlayViewUniteReply -> {
                            val newAid = reply.playArc.aid
                            val cid = reply.playArc.cid
                            if (newAid > 0 && cid > 0) pending[owner] = newAid to cid
                            if (newAid > 0 && cid > 0) VideoInfoHolder.updateCurrent { old ->
                                VideoInfo(cid, old?.view?.takeIf { aid(it) == newAid })
                            }
                            Log.i("BiliClean", "play-metadata aid=$newAid cid=$cid")
                        }
                    }
                } catch (failure: Throwable) {
                    // Metadata enhancement must not prevent the host callback from running.
                    Log.w("BiliClean", "metadata-unavailable type=${failure.javaClass.simpleName}")
                }
            }
            try { method.invoke(handler, *(args ?: emptyArray())) }
            catch (error: InvocationTargetException) { throw error.targetException }
        }
    }
}
