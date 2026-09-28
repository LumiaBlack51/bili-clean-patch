package app.revanced.patches.bilibili.clean

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patches.bilibili.misc.json.fingerprints.PegasusParserFingerprint
import app.revanced.patches.bilibili.utils.cloneMutable
import com.android.tools.smali.dexlib2.AccessFlags

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
        // 9.12 switched the active home feed to Gson; the legacy parser above is fallback only.
        val modern = context.findClass("Lcom/bilibili/pegasus/request/PegasusResponseTypeAdapter;")?.mutableClass
            ?: throw PatchException("Unsupported modern feed parser")
        val read = modern.methods.singleOrNull { it.name == "e" &&
            it.parameterTypes.map { p -> p.toString() } == listOf("LOR0/a;") &&
            it.returnType == "Ljava/lang/Object;" }
            ?: throw PatchException("Unsupported modern feed signature")
        val modernWrapper = read.cloneMutable(registerCount = 2, clearImplementation = true)
        read.name += "_BiliCleanOriginal"
        modernWrapper.addInstructions("""
            invoke-virtual {p0, p1}, $read
            move-result-object p1
            invoke-static {p1}, Lapp/revanced/bilibili/clean/CleanFeed;->filterModern(Ljava/lang/Object;)V
            return-object p1
        """.trimIndent())
        modern.methods.add(modernWrapper)
        // The default Gson configuration reflects PegasusResponse directly instead of registering
        // PegasusResponseTypeAdapter. Filter the concrete transport converter as well.
        val transport = context.findClass("Lcom/bilibili/pegasus/request/PegasusGsonParser;")?.mutableClass
            ?: throw PatchException("Unsupported Gson feed transport")
        val convert = transport.methods.singleOrNull { it.name == "g" &&
            it.parameterTypes.map { p -> p.toString() } == listOf("Lokhttp3/ResponseBody;") &&
            it.returnType == "Lcom/bilibili/okretro/GeneralResponse;" }
            ?: throw PatchException("Unsupported Gson feed transport signature")
        val transportWrapper = convert.cloneMutable(registerCount = 2, clearImplementation = true)
        convert.name += "_BiliCleanOriginal"
        transportWrapper.addInstructions("""
            invoke-virtual {p0, p1}, $convert
            move-result-object p1
            invoke-static {p1}, Lapp/revanced/bilibili/clean/CleanFeed;->filter(Ljava/lang/Object;)V
            return-object p1
        """.trimIndent())
        transport.methods.add(transportWrapper)
        // Hook the model getter so both JSON parsers and cached Story responses are covered.
        val story = context.findClass("Lcom/bilibili/video/story/api/StoryFeedResponse\$Data;")?.mutableClass
            ?: throw PatchException("Unsupported Story model")
        val storyItems = story.methods.singleOrNull { it.name == "getItems" && it.parameterTypes.isEmpty() && it.returnType == "Ljava/util/List;" }
            ?: throw PatchException("Unsupported Story items getter")
        fun hookList(method: app.revanced.patcher.util.proxy.mutableTypes.MutableMethod, hook: String) {
            val static = AccessFlags.STATIC.isSet(method.accessFlags)
            val count = method.parameterTypes.size + if (static) 0 else 1
            val wrapper = method.cloneMutable(registerCount = count + 1, clearImplementation = true)
            method.name += "_BiliCleanOriginal"
            val invoke = if (static) "invoke-static" else if (AccessFlags.PRIVATE.isSet(method.accessFlags)) "invoke-direct" else "invoke-virtual"
            val registers = if (count == 0) "" else (0 until count).joinToString(", ") { "p$it" }
            wrapper.addInstructions("""
                $invoke {$registers}, $method
                move-result-object v0
                invoke-static {v0}, Lapp/revanced/bilibili/clean/CleanContent;->$hook(Ljava/util/List;)Ljava/util/List;
                move-result-object v0
                return-object v0
            """.trimIndent())
            context.findClass(method.definingClass)!!.mutableClass.methods.add(wrapper)
        }
        hookList(storyItems, "filterStory")
        // Filter the native navigation builder as well as JSON: local/default tabs use this path.
        val manager = context.findClass("Ltv/danmaku/bili/ui/main2/resource/MainResourceManager;")?.mutableClass
            ?: throw PatchException("Unsupported navigation manager")
        val lists = manager.methods.filter { it.returnType == "Ljava/util/List;" && it.implementation != null }
        if (lists.size != 5) throw PatchException("Unsupported navigation list methods")
        lists.forEach { hookList(it, "filterTabs") }
        val defaults = context.findClass("Ltv/danmaku/bili/ui/main2/resource/a;")?.mutableClass
            ?: throw PatchException("Unsupported default navigation")
        hookList(defaults.methods.single { it.name == "c" && it.parameterTypes.isEmpty() && it.returnType == "Ljava/util/List;" }, "filterTabs")
        val clickClass = context.findClass("Lcom/bilibili/lib/homepage/widget/TabHost\$a;")?.mutableClass
            ?: throw PatchException("Unsupported bottom tab click listener")
        val click = clickClass.methods.single { it.name == "onClick" && it.parameterTypes == listOf("Landroid/view/View;") }
        val clickWrapper = click.cloneMutable(registerCount = 3, clearImplementation = true)
        click.name += "_BiliCleanOriginal"
        clickWrapper.addInstructions("""
            invoke-static {p1}, Lapp/revanced/bilibili/clean/CleanContent;->openSettings(Landroid/view/View;)Z
            move-result v0
            if-eqz v0, :original
            return-void
            :original
            invoke-virtual {p0, p1}, $click
            return-void
        """.trimIndent())
        clickClass.methods.add(clickWrapper)
    }
}
