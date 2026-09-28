package app.revanced.patches.bilibili.clean

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patches.bilibili.video.player.fingerprints.PlayerOnPreparedFingerprint
import app.revanced.patches.bilibili.utils.cloneMutable

@Patch(name = "Clean player", compatiblePackages = [CompatiblePackage("tv.danmaku.bili", ["9.12.0"])])
object CleanPlayerPatch : BytecodePatch(setOf(PlayerOnPreparedFingerprint)) {
    override fun execute(context: BytecodeContext) {
        val method = PlayerOnPreparedFingerprint.result?.mutableMethod
            ?: throw PatchException("Unsupported player prepared callback")
        if (method.parameterTypes.firstOrNull()?.toString() != "Ltv/danmaku/ijk/media/player/IMediaPlayer;")
            throw PatchException("Unsupported prepared signature")
        method.addInstructions(0, "invoke-static/range {p1 .. p1}, Lapp/revanced/bilibili/clean/CleanRuntime;->onPrepared(Ltv/danmaku/ijk/media/player/IMediaPlayer;)V")
        method.addInstructions(0, "invoke-static/range {p0 .. p1}, Lapp/revanced/bilibili/clean/CleanPlayback;->prepared(Ljava/lang/Object;Ltv/danmaku/ijk/media/player/IMediaPlayer;)V")
        val runtime = "Lapp/revanced/bilibili/clean/CleanPlayback;"
        fun host(type: String, name: String) = context.findClass(type)?.mutableClass?.methods
            ?.singleOrNull { it.name == name } ?: throw PatchException("Missing pinned player API: $type $name")
        host("Lfs1/I;", "onPlayerClockChanged").addInstructions(0,
            "invoke-static/range {p0 .. p1}, $runtime->clock(Ljava/lang/Object;Ltv/danmaku/ijk/media/player/IMediaPlayer;)V")
        host("Lcom/bilibili/playerbizcommon/gesture/GestureService;", "bindPlayerContainer").addInstructions(0,
            "invoke-static/range {p1 .. p1}, $runtime->bind(Ljava/lang/Object;)V")
        host("Lcom/bilibili/playerbizcommon/gesture/GestureService\$j;", "onDoubleTap").addInstructions(0, """
            invoke-static/range {p1 .. p1}, $runtime->doubleTap(Landroid/view/MotionEvent;)Z
            move-result v0
            if-eqz v0, :native_gesture
            return v0
            :native_gesture
            nop
        """.trimIndent())
        host("Lfs1/K;", "q").addInstructions(0, """
            invoke-static {p0, p1}, $runtime->stateChanged(Ljava/lang/Object;I)Z
            move-result v0
            if-eqz v0, :native_state
            return-void
            :native_state
            nop
        """.trimIndent())
        host("LDu0/c\$c;", "<init>").addInstructions(0, """
            invoke-static/range {p2 .. p2}, $runtime->timerItems(Ljava/lang/Object;)Ljava/lang/Object;
            move-result-object p2
            check-cast p2, [LDu0/c${'$'}d;
        """.trimIndent())
        val clickClass = context.findClass("LDu0/d;")!!.mutableClass
        val click = host("LDu0/d;", "onClick")
        val wrapper = click.cloneMutable(registerCount = 3, clearImplementation = true)
        click.name += "_BiliCleanOriginal"
        wrapper.addInstructions("""
            invoke-static {p0}, $runtime->timerClick(Ljava/lang/Object;)Z
            move-result v0
            if-eqz v0, :native_timer
            return-void
            :native_timer
            invoke-virtual {p0, p1}, $click
            return-void
        """.trimIndent())
        clickClass.methods.add(wrapper)
        host("LDu0/c;", "onStart").addInstructions(0,
            "invoke-static/range {p0 .. p0}, $runtime->timerOpened(Ljava/lang/Object;)V")
        host("Ltv/danmaku/biliplayerv2/service/business/ShutOffTimingService;", "startShutOffTiming").addInstructions(0,
            "invoke-static {}, $runtime->cancelEndTimer()V")
        val panel = host("LMu0/f;", "createContentView")
        val panelClass = context.findClass("LMu0/f;")!!.mutableClass
        val panelWrapper = panel.cloneMutable(registerCount = 3, clearImplementation = true)
        panel.name += "_BiliCleanOriginal"
        panelWrapper.addInstructions("""
            invoke-virtual {p0, p1}, $panel
            move-result-object v0
            invoke-static {p0, v0}, $runtime->timerPanel(Ljava/lang/Object;Landroid/view/View;)Landroid/view/View;
            move-result-object v0
            return-object v0
        """.trimIndent())
        panelClass.methods.add(panelWrapper)
        host("Lcom/bilibili/app/comm/timing/ui/TimingReminderSelectDialog\$a;", "l0").addInstructions(0, """
            invoke-static {p0, p1}, $runtime->globalTimerItems(Ljava/lang/Object;Ljava/util/List;)Ljava/util/List;
            move-result-object p1
        """.trimIndent())
        host("LNk/e;", "l").addInstructions(0, "invoke-static {}, $runtime->cancelEndTimer()V")
        val globalClick = host("Lcom/bilibili/app/comm/timing/ui/c;", "invoke")
        val globalWrapper = globalClick.cloneMutable(registerCount = 3, clearImplementation = true)
        globalClick.name += "_BiliCleanOriginal"
        globalWrapper.addInstructions("""
            invoke-static {p0, p1}, $runtime->globalTimerClick(Ljava/lang/Object;Ljava/lang/Object;)Z
            move-result v0
            if-eqz v0, :native_global_timer
            sget-object v0, Lkotlin/Unit;->INSTANCE:Lkotlin/Unit;
            return-object v0
            :native_global_timer
            invoke-virtual {p0, p1}, $globalClick
            move-result-object v0
            return-object v0
        """.trimIndent())
        context.findClass("Lcom/bilibili/app/comm/timing/ui/c;")!!.mutableClass.methods.add(globalWrapper)
    }
}
