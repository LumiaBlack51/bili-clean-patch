package app.revanced.patches.bilibili.clean

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

@Patch(name = "Clean splash", compatiblePackages = [CompatiblePackage("tv.danmaku.bili", ["9.12.0"])])
object CleanSplashPatch : BytecodePatch() {
    override fun execute(context: BytecodeContext) {
        fun blockOrder(ownerName: String, name: String, parameters: String, result: String) {
            val owner = context.findClass(ownerName)?.mutableClass
                ?: throw PatchException("Unsupported splash owner: $ownerName")
            val method = owner.methods.singleOrNull { it.name == name &&
                it.parameterTypes.joinToString("") == parameters && it.returnType == result &&
                AccessFlags.STATIC.isSet(it.accessFlags) }
                ?: throw PatchException("Unsupported splash signature: $ownerName->$name")
            if ((method.implementation?.registerCount ?: 0) <= method.parameterTypes.size)
                throw PatchException("No local register in splash entry: $name")
            method.addInstructionsWithLabels(0, """
                invoke-static {}, Lapp/revanced/bilibili/clean/CleanSplash;->block()Z
                move-result v0
                if-eqz v0, :original
                const/4 v0, 0x0
                return-object v0
            """.trimIndent(), ExternalLabel("original", method.getInstruction(0)))
        }
        // The old implementation can bypass JSON entirely via preloaded/cached SplashOrder.
        val legacy = "Ltv/danmaku/bili/splash/ad/core/SplashManager;"
        blockOrder(legacy, "b", "ZLtv/danmaku/bili/splash/ad/model/SplashSource;",
            "Ltv/danmaku/bili/splash/ad/model/SplashOrder;")
        // Factory guard also covers already selected orders and restored HotSplashActivity.
        blockOrder(legacy, "a", "Ltv/danmaku/bili/splash/ad/model/SplashOrder;",
            "Ltv/danmaku/bili/splash/ad/page/BaseSplash;")
        // 9.12 also ships KSplash, which uses Kotlin serialization and a StateFlow cache.
        val modern = "Lkntr/srcs/app/splash/core/vm/BusinessSplashViewModel;"
        blockOrder(modern, "a", "Lkntr/srcs/app/splash/model/SplashSource;", "Loi1/q;")
        blockOrder(modern, "c", "", "Loi1/q;")

        // R8 shares this lambda class with unrelated features. Guard ONLY its splash branch,
        // after the packed-switch dispatch and before either legacy/KSplash hot-start path.
        val lambda = context.findClass("Lbf/d;")?.mutableClass
            ?: throw PatchException("Unsupported hot-splash lambda")
        val invoke = lambda.methods.singleOrNull { it.name == "invoke" &&
            it.parameterTypes.isEmpty() && it.returnType == "Ljava/lang/Object;" }
            ?: throw PatchException("Unsupported hot-splash callback")
        val instructions = invoke.implementation?.instructions?.toList()
            ?: throw PatchException("Missing hot-splash callback instructions")
        val entry = instructions.indices.singleOrNull {
            (instructions[it] as? ReferenceInstruction)?.reference?.toString() == "Lip1/a;->a()Z"
        } ?: throw PatchException("Unsupported hot-splash feature dispatch")
        if (instructions[entry].opcode != Opcode.INVOKE_STATIC ||
            instructions.getOrNull(entry + 1)?.opcode != Opcode.MOVE_RESULT ||
            instructions.take(entry).none { it.opcode == Opcode.PACKED_SWITCH })
            throw PatchException("Unsupported hot-splash branch layout")
        // The next original instruction overwrites this local with the feature flag;
        // it has no live value across the guard, and no parameter is clobbered.
        val scratch = (instructions[entry + 1] as OneRegisterInstruction).registerA
        if (scratch >= (invoke.implementation!!.registerCount - 1))
            throw PatchException("No scratch local in hot-splash branch")
        invoke.addInstructionsWithLabels(entry, """
            invoke-static {}, Lapp/revanced/bilibili/clean/CleanSplash;->block()Z
            move-result v$scratch
            if-eqz v$scratch, :original
            sget-object v$scratch, Lkotlin/Unit;->INSTANCE:Lkotlin/Unit;
            return-object v$scratch
        """.trimIndent(), ExternalLabel("original", invoke.getInstruction(entry)))
    }
}
