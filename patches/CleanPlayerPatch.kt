package app.revanced.patches.bilibili.clean

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patches.bilibili.video.player.fingerprints.PlayerOnPreparedFingerprint

@Patch(name = "Clean player", compatiblePackages = [CompatiblePackage("tv.danmaku.bili", ["9.12.0"])])
object CleanPlayerPatch : BytecodePatch(setOf(PlayerOnPreparedFingerprint)) {
    override fun execute(context: BytecodeContext) {
        val method = PlayerOnPreparedFingerprint.result?.mutableMethod
            ?: throw PatchException("Unsupported player prepared callback")
        if (method.parameterTypes.firstOrNull()?.toString() != "Ltv/danmaku/ijk/media/player/IMediaPlayer;")
            throw PatchException("Unsupported prepared signature")
        method.addInstructions(0, "invoke-static/range {p1 .. p1}, Lapp/revanced/bilibili/clean/CleanRuntime;->onPrepared(Ltv/danmaku/ijk/media/player/IMediaPlayer;)V")
    }
}
