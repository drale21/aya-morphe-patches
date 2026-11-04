package app.morphe.patches.megabook.subscription

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.megabook.shared.Constants.COMPATIBILITY_MEGA_KARAOKE
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private data class WriteSite(
    val classType: String,
    val methodName: String,
    val parameters: List<String>,
    val index: Int,
    val register: Int,
)

/**
 * Unlock the app's subscription.
 *
 * Mega Karaoke Songbook is a free B4A app with one subscription that carries
 * everything premium. The app advertises four benefits and all four are gated by
 * a single static boolean in the B4A globals holder
 * (`Lapp/lcs/megabook/main;`):
 *
 *  - no ads - the ad view is only loaded and added to the layout when the flag is
 *    false (`pprint`, `pmanager`, `psplitart`, main page)
 *  - unlimited PDF pages - a free install stops at 20 pages (`pprint`, three
 *    export paths)
 *  - unlimited songbooks - a free install gets the promo dialog when creating one
 *    (`sbmanager`)
 *  - hidden functions - the extra list-menu entries (Title case and Category
 *    buttons, table view, PDF export) are only added when the flag is true
 *    (main page)
 *
 * The app writes that flag from ten places: the purchases-updated callback, the
 * restore-purchases callback, the trial (`ad_free_realtime`) check, the
 * subscription expiry check, the rewarded-ad path and the globals initialiser.
 * Each write is a `sput-boolean` of a freshly computed value.
 *
 * The patch inserts `const/4 v, 0x1` in front of every one of those stores, so
 * the flag is always stored as `true` - the same state a paying subscriber ends
 * up in. Nothing else changes: the billing flow, the acknowledge call, the token
 * verification and the trial bookkeeping keep their original behaviour.
 *
 * The flag is found by usage rather than by name: B4A renames every global on
 * each build, but the entitlement flag is the static boolean the app reads from
 * the most places (50 reads across 44 methods in 2.59, versus 7 for the next
 * one).
 */
@Suppress("unused")
val unlockSubscriptionPatch =
    bytecodePatch(
        name = "Unlock subscription",
        description =
            "Unlocks the subscription: no ads, unlimited PDF pages, unlimited songbooks " +
                "and the hidden functions.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_MEGA_KARAOKE)

        execute {
            val globalsType = GlobalsFingerprint.method.definingClass

            val classes = mutableListOf<ClassDef>()
            classDefForEach { classes += it }

            // The entitlement flag is the static boolean of the globals holder that the
            // rest of the app reads and writes the most.
            val references = mutableMapOf<String, Int>()
            for (classDef in classes) {
                for (method in classDef.methods) {
                    val implementation = method.implementation ?: continue
                    for (instruction in implementation.instructions) {
                        if (instruction.opcode != Opcode.SGET_BOOLEAN &&
                            instruction.opcode != Opcode.SPUT_BOOLEAN
                        ) {
                            continue
                        }
                        val reference =
                            (instruction as? ReferenceInstruction)?.reference as? FieldReference ?: continue
                        if (reference.definingClass != globalsType || reference.type != "Z") continue
                        references.merge(reference.name, 1, Int::plus)
                    }
                }
            }

            val (flagName, flagReferences) =
                references.maxByOrNull { it.value }
                    ?: error("no static boolean field in $globalsType")
            check(flagReferences >= 10) {
                "expected the entitlement flag to be referenced widely, but the most referenced " +
                    "boolean of $globalsType is $flagName ($flagReferences references)"
            }
            val flag = "$globalsType->$flagName:Z"

            val sites = mutableListOf<WriteSite>()
            for (classDef in classes) {
                for (method in classDef.methods) {
                    val implementation = method.implementation ?: continue
                    implementation.instructions.forEachIndexed { index, instruction ->
                        if (instruction.opcode != Opcode.SPUT_BOOLEAN) return@forEachIndexed
                        val reference =
                            (instruction as? ReferenceInstruction)?.reference as? FieldReference
                                ?: return@forEachIndexed
                        if ("$reference" != flag) return@forEachIndexed
                        val register =
                            (instruction as? OneRegisterInstruction)?.registerA
                                ?: return@forEachIndexed
                        sites +=
                            WriteSite(
                                classType = classDef.type,
                                methodName = method.name,
                                parameters = method.parameterTypes.map { it.toString() },
                                index = index,
                                register = register,
                            )
                    }
                }
            }
            check(sites.isNotEmpty()) { "no write of $flag found" }

            sites
                .groupBy { Triple(it.classType, it.methodName, it.parameters) }
                .forEach { (key, group) ->
                    val method =
                        mutableClassDefBy(key.first).methods.first {
                            it.name == key.second && it.parameterTypes.map { type -> type.toString() } == key.third
                        }
                    // Highest index first: every insertion shifts the instructions after it.
                    group.sortedByDescending { it.index }.forEach { site ->
                        method.addInstruction(site.index, "const/4 v${site.register}, 0x1")
                    }
                }
        }
    }
