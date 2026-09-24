package app.revanced.patches.bilibili.clean

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patcher.util.smali.ExternalLabel

@Patch(name = "Clean pause ads", compatiblePackages = [CompatiblePackage("tv.danmaku.bili", ["9.12.0"])])
object CleanPauseAdsPatch : BytecodePatch() {
    override fun execute(context: BytecodeContext) {
        val owner = context.findClass("Lcom/bilibili/ad/adview/videodetail/pausedpage/VDPausedPage;")?.mutableClass
            ?: throw PatchException("Unsupported paused-page service")
        val signatures = mapOf(
            "requestPausedPage" to ("JJLcom/bapis/bilibili/app/viewunite/v1/KTabType;Ljava/lang/String;IJJIILjava/util/List;ILjava/lang/String;Ljava/lang/String;Lkotlin/coroutines/Continuation;" to "Ljava/lang/Object;"),
            "decodeViewEndPagePausedPage" to ("[B" to "Lkntr/app/ad/base/protocol/biz/vd/pausedpage/IAdPausedPageData;")
        )
        for ((name, signature) in signatures) {
            val method = owner.methods.singleOrNull { it.name == name &&
                it.parameterTypes.joinToString("") == signature.first && it.returnType == signature.second }
                ?: throw PatchException("Unsupported paused-page signature: $name")
            val parameterRegisters = 1 + method.parameterTypes.size + method.parameterTypes.count { it.toString() in setOf("J", "D") }
            if ((method.implementation?.registerCount ?: 0) <= parameterRegisters)
                throw PatchException("No scratch register in paused-page method: $name")
            method.addInstructionsWithLabels(0, """
                invoke-static {}, Lapp/revanced/bilibili/clean/CleanPauseAds;->block()Z
                move-result v0
                if-eqz v0, :original
                const/4 v0, 0x0
                return-object v0
            """.trimIndent(), ExternalLabel("original", method.getInstruction(0)))
        }
    }
}
