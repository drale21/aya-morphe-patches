package id.ikuradachi.patches.crunchyroll.telemetry

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21t
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import id.ikuradachi.patches.crunchyroll.shared.Constants.COMPATIBILITY_CRUNCHYROLL

@Suppress("unused")
val disableSegmentAnalyticsPatch = bytecodePatch(
    name = "Disable Segment analytics events",
    description = "Blocks new Segment events, identification and screen payloads. SDK initialization and previously queued events remain.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_CRUNCHYROLL)

    execute {
        // shortcut: SDK startup and existing queues remain, audit those paths before claiming full removal.
        SegmentPayloadAdmissionFingerprint.matchAll(1..1).single().method.addInstruction(0, "return-void")
    }
}

@Suppress("unused")
val disableMuxAnalyticsPatch = bytecodePatch(
    name = "Disable Mux playback analytics events",
    description = "Drops Mux playback analytics events while retaining player and ad initialization. Local SDK metadata remains.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_CRUNCHYROLL)

    execute {
        MuxEventAdmissionFingerprint.matchAll(1..1).single().method.addInstruction(0, "return-void")
    }
}

@Suppress("unused")
val disableFirebaseAnalyticsAfterConsentPatch = bytecodePatch(
    name = "Disable Firebase analytics after consent initialization",
    description = "Keeps Firebase Analytics collection disabled when consent updates arrive. Collection before the first update remains possible.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_CRUNCHYROLL)

    execute {
        val match = FirebaseAnalyticsCollectionObserverFingerprint.matchAll(1..1).single()
        val result = match.instructionMatches[1]
        val register = result.getInstruction<OneRegisterInstruction>().registerA
        val setter = match.instructionMatches.last().getInstruction<Instruction35c>()
        check(register in 0..15 && setter.registerCount == 2 && setter.registerD == register) {
            "Unexpected Firebase Analytics collection argument"
        }
        // shortcut: observer runs after Firebase startup, verify an earlier control before claiming startup suppression.
        match.method.replaceInstruction(result.index, "const/4 v$register, 0x0")
    }
}

@Suppress("unused")
val disableAutomaticCrashlyticsUploadsPatch = bytecodePatch(
    name = "Disable automatic Crashlytics uploads",
    description = "Denies automatic crash report uploads. Local crash recording and explicit report submission remain available.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_CRUNCHYROLL)

    execute {
        val match = CrashlyticsAutomaticPermissionFingerprint.matchAll(1..1).single()
        val method = match.method
        val implementation = requireNotNull(method.implementation)
        val instructions = implementation.instructions
        val log = match.instructionMatches.last()
        val call = log.getInstruction<Instruction35c>()
        check(
            implementation.registerCount == 2 && instructions.first().opcode == Opcode.MONITOR_ENTER &&
                call.registerCount == 2 && call.registerC == 1 && call.registerD == 0 &&
                instructions[log.index + 1].opcode == Opcode.MONITOR_EXIT &&
                instructions[log.index + 2].opcode == Opcode.RETURN &&
                (instructions[log.index + 2] as OneRegisterInstruction).registerA == 0,
        ) { "Unexpected Crashlytics automatic collection control flow" }
        // Replace the labelled slot so both preference/default branches execute the false assignment.
        method.replaceInstruction(log.index, "const/4 v0, 0x0")
        method.addInstructions(
            log.index + 1,
            "invoke-direct {p0, v0}, Lcom/google/firebase/crashlytics/internal/common/DataCollectionArbiter;->logDataCollectionState(Z)V",
        )
    }
}

@Suppress("unused")
val disableDatadogTelemetryPatch = bytecodePatch(
    name = "Disable Datadog telemetry setup",
    description = "Selects the app's disabled Datadog implementation, preserving shared initialization and consent observers.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_CRUNCHYROLL)

    execute {
        val match = DatadogSetupFingerprint.matchAll(1..1).single()
        val implementation = requireNotNull(match.method.implementation)
        val instructions = implementation.instructions
        val condition = match.instructionMatches[1]
        val register = condition.getInstruction<TwoRegisterInstruction>().registerA
        val branch = instructions[match.instructionMatches[3].index] as BuilderInstruction21t
        val target = branch.target.location.index
        check(
            implementation.registerCount == 38 && register in 0..15 && branch.registerA == register &&
                instructions.subList(target, target + 6).map { it.opcode } == listOf(
                    Opcode.CONST_4, Opcode.CONST_4, Opcode.NEW_INSTANCE,
                    Opcode.INVOKE_DIRECT, Opcode.MOVE_OBJECT, Opcode.SPUT_OBJECT,
                ),
        ) { "Unexpected Datadog disabled branch" }
        match.method.replaceInstruction(condition.index, "const/4 v$register, 0x0")
    }
}
