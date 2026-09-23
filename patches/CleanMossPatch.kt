package app.revanced.patches.bilibili.clean

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patches.bilibili.utils.cloneMutable

@Patch(name = "Clean metadata", compatiblePackages = [CompatiblePackage("tv.danmaku.bili", ["9.12.0"])])
object CleanMossPatch : BytecodePatch(emptySet()) {
    override fun execute(context: BytecodeContext) {
        val target = context.findClass("Lcom/bilibili/lib/moss/api/KMossServiceImp;")?.mutableClass
            ?: throw PatchException("Unsupported metadata transport")
        val methods = target.methods.filter { it.name == "asyncUnaryCall" && it.implementation != null }
        if (methods.size != 1) throw PatchException("Ambiguous metadata callback")
        val method = methods.single()
        if (method.parameterTypes.map { it.toString() } != listOf("Lio/grpc/MethodDescriptor;",
                "Lcom/google/protobuf/GeneratedMessageLite;", "Lcom/bilibili/lib/moss/api/MossResponseHandler;",
                "Lcom/bilibili/lib/moss/api/MossHttpRule;")) throw PatchException("Unsupported metadata signature")
        val wrapper = method.cloneMutable(registerCount = 7, clearImplementation = true)
        method.name += "_BiliCleanOriginal"
        wrapper.addInstructions("""
            invoke-static {p2, p3}, Lapp/revanced/bilibili/clean/CleanMetadata;->wrap(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
            move-result-object v0
            if-eqz v0, :delegate
            check-cast v0, Lcom/bilibili/lib/moss/api/MossResponseHandler;
            move-object p3, v0
            :delegate
            invoke-virtual/range {p0 .. p4}, $method
            :done
            return-void
        """.trimIndent())
        target.methods.add(wrapper)
    }
}
