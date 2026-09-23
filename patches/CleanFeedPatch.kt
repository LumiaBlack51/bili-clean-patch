package app.revanced.patches.bilibili.clean

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patches.bilibili.misc.json.fingerprints.PegasusParserFingerprint
import app.revanced.patches.bilibili.utils.cloneMutable

@Patch(name = "Clean feed", compatiblePackages = [CompatiblePackage("tv.danmaku.bili", ["9.12.0"])])
object CleanFeedPatch : BytecodePatch(setOf(PegasusParserFingerprint)) {
    override fun execute(context: BytecodeContext) {
        val result = PegasusParserFingerprint.result ?: throw PatchException("Unsupported feed parser")
        val original = result.mutableClass.methods.single { it.returnType == "Lcom/bilibili/okretro/GeneralResponse;" }
        if (original.parameterTypes.size != 1) throw PatchException("Unsupported feed parser signature")
        val wrapper = original.cloneMutable(registerCount = 2, clearImplementation = true)
        original.name += "_BiliCleanOriginal"
        wrapper.addInstructions("""
            invoke-virtual {p0, p1}, $original
            move-result-object p1
            invoke-static {p1}, Lapp/revanced/bilibili/clean/CleanFeed;->filter(Ljava/lang/Object;)V
            return-object p1
        """.trimIndent())
        result.mutableClass.methods.add(wrapper)
    }
}
