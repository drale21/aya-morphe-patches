package gtade.patches

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.ResourcePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.rawResourcePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rc
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

val GTASA_DE = Compatibility(
    name = "GTA: SA",
    packageName = "com.rockstargames.gtasa.de",
    apkFileType = ApkFileType.APK,
    appIconColor = 0x1B1B1B,
    // The mods find the game's code by symbol names of this build.
    targets = listOf(AppTarget(version = "1.87.0")),
)

private const val ACTIVITY = "Lcom/epicgames/ue4/GameActivity;"
private const val BOOT_START = "Lgtade/boot/Boot;->start()V"

/** Bytes of a file bundled in this patches file under src/main/resources/gtade/. */
internal fun bundled(name: String): ByteArray =
    object {}.javaClass.getResourceAsStream("/gtade/$name")?.use { it.readBytes() }
        ?: throw PatchException("$name is missing from the patches file")

private val amlLibraryPatch = rawResourcePatch {
    execute { get("lib/arm64-v8a/libAML.so", false).writeBytes(bundled("libAML.so")) }
}

/**
 * Not listed on its own: every mod patch depends on it. GameActivity's static init loads UE4; right after that
 * gtade.boot.Boot.start() copies the bundled mods to Android/data/<game> and loads AML (Android Mod Loader).
 */
internal val modLoaderPatch = bytecodePatch {
    compatibleWith(GTASA_DE)
    dependsOn(amlLibraryPatch)
    extendWith("extensions/extension.mpe")

    execute {
        val clinit = mutableClassDefBy(ACTIVITY).methods.first { it.name == "<clinit>" }
        val insns = clinit.instructions
        // A loadLibrary("AML") from the PC patcher (loader/patch_apk_aml.sh) would load AML before the mods sync.
        for (i in insns.indices.reversed()) if (loadLibraryArg(insns, i) == "AML") clinit.removeInstruction(i)
        val ue4 = insns.indices.filter { loadLibraryArg(insns, it) == "UE4" }
        if (ue4.size != 1) throw PatchException("expected one loadLibrary(\"UE4\") in GameActivity.<clinit>, found ${ue4.size}")
        clinit.addInstruction(ue4[0] + 1, "invoke-static {}, $BOOT_START")
    }
}

/** The literal passed to System.loadLibrary at [i] (the last const-string into its register), or null. */
private fun loadLibraryArg(insns: List<BuilderInstruction>, i: Int): String? {
    val ins = insns[i]
    val reg = when {
        ins.opcode == Opcode.INVOKE_STATIC && ins is Instruction35c -> ins.registerC
        ins.opcode == Opcode.INVOKE_STATIC_RANGE && ins is Instruction3rc -> ins.startRegister
        else -> return null
    }
    val ref = (ins as ReferenceInstruction).reference as MethodReference
    if (ref.definingClass != "Ljava/lang/System;" || ref.name != "loadLibrary") return null
    // shortcut: straight-line scan back, fine for this static initializer (no branches before the call).
    for (j in i - 1 downTo 0) {
        val prev = insns[j]
        if (prev !is OneRegisterInstruction || !prev.opcode.setsRegister() || prev.registerA != reg) continue
        if (prev.opcode != Opcode.CONST_STRING && prev.opcode != Opcode.CONST_STRING_JUMBO) return null
        return ((prev as ReferenceInstruction).reference as StringReference).string
    }
    return null
}

/** Adds a mod's library (loaded by AML) and its config/data files; Boot copies them at the next game start. */
internal fun ResourcePatchContext.addMod(library: String, vararg configs: String) {
    get("assets/gtade/mods/$library", false).apply { parentFile.mkdirs() }.writeBytes(bundled(library))
    for (c in configs) get("assets/gtade/configs/$c", false).apply { parentFile.mkdirs() }.writeBytes(bundled(c))
}
