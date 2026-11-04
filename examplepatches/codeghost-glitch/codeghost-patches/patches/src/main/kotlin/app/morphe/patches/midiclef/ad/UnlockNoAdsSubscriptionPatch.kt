package app.morphe.patches.midiclef.ad

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.midiclef.shared.Constants.COMPATIBILITY_MIDI_CLEF
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

private fun List<Instruction>.referenceAt(index: Int): String? = (this[index] as? ReferenceInstruction)?.reference?.toString()

private fun List<Instruction>.indexOfReference(reference: String): Int =
    indices.firstOrNull { referenceAt(it) == reference }
        ?: error("instruction referencing $reference not found")

private fun List<Instruction>.indexOfOpcode(
    opcode: Opcode,
    from: Int,
): Int =
    (from until size).firstOrNull { this[it].opcode == opcode }
        ?: error("instruction $opcode not found")

/**
 * Unlock the "remove ads" in-app purchase (`it.giccisw.midi.noads`) of MIDI Clef.
 *
 * MIDI Clef is a free app whose only product is a one-off, non-consumable ad
 * removal. The patch makes the app believe that purchase is owned, so every ad
 * surface it has - AdMob banner / medium / native banner fragments, the
 * `InterstitialCustom` interstitial, and the ad-partner part of the GDPR consent
 * screen - is disabled exactly as it is for a real buyer.
 *
 * Entitlement map (R8 renames all of this per release, hence the pinned version):
 *
 *  - product id `it.giccisw.midi.noads`, type `inapp`, fetched with
 *    `queryPurchases("inapp")`
 *  - `Lst;->m:Lel3;` - the `noAds` preference (default `false`), read by the ad
 *    gate; `Lst;->n:Lil3;` - the purchase token, only written when a real
 *    purchase object exists and left untouched here
 *  - `BillingNoAdsStatus.STARTED_NO_ADS` - the status a real buyer reaches
 *
 * Two single instructions are rewritten:
 *
 *  1. `Ltt;->b(ILjava/util/ArrayList;)V` at `+0x0096` - the purchases-updated
 *     callback. `move v2, v8` becomes `const/4 v2, 0x1`, pinning the boolean
 *     computed from the purchase list, so any successful query (which returns an
 *     empty list for a non-buyer) leaves the app entitled. The unchanged code
 *     below stores it into `Lst;->m` and publishes `STARTED_NO_ADS`, so the state
 *     is persisted and survives restarts.
 *
 *  2. `MidiActivityMain.onCreate` at `+0x02ee` - the ad gate that reads that
 *     preference. `move-result v2` becomes `const/4 v2, 0x1`, so `if-eqz v2`
 *     falls through: `o0 = false` (no ad fragment is ever added by `show()`)
 *     and the interstitial loader is skipped. This also covers devices where
 *     Play Billing never answers at all, because on a connection failure the app
 *     drops its queued purchase query and edit 1 never runs.
 *
 * Nothing else is touched: the purchase flow, the restore path, the GDPR
 * manager, and the audio engine keep their original behaviour.
 *
 * Verified statically: the harness applies the patch to the 9.2.6 `base.apk`
 * with `exception=null`, and a dex dump shows both methods byte-identical to the
 * original apart from those two instructions. Not observed on a device - there
 * is no Play account here and the shipped APK has arm64 native libraries only.
 *
 * Caveats: the release ships as a split bundle (base + 13 `config.*` splits),
 * and a patched APK is re-signed, so Play will not install it as an update and
 * Play Integrity fails.
 */
@Suppress("unused")
val unlockNoAdsSubscriptionPatch =
    bytecodePatch(
        name = "Unlock remove-ads subscription",
        description =
            "Removes all ads: banners, native ads and interstitials.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_MIDI_CLEF)

        execute {
            // 1. Entitlement: always compute "no ads purchase present".
            val purchasesUpdated = NoAdsPurchaseUpdateFingerprint.method
            val billing = purchasesUpdated.implementation!!.instructions.toList()
            // z2 is assigned right before the preference write, which is the first
            // use of Boolean.TRUE in the method (this.c.l.e(Boolean.TRUE)).
            val entitledWrite = billing.indexOfReference("Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;")
            val entitlementAssignment =
                (entitledWrite - 1 downTo 0)
                    .first { billing[it].opcode == Opcode.MOVE }
            val entitlementRegister = (billing[entitlementAssignment] as OneRegisterInstruction).registerA
            purchasesUpdated.replaceInstruction(entitlementAssignment, "const/4 v$entitlementRegister, 0x1")

            // 2. Ad gate: take the "owned" branch of the main activity ad decision.
            val onCreate = MidiActivityAdGateFingerprint.method
            val gate = onCreate.implementation!!.instructions.toList()
            val preferenceRead = gate.indexOfReference("Lst;->m:Lel3;")
            val booleanResult = gate.indexOfOpcode(Opcode.MOVE_RESULT, preferenceRead + 1)
            val gateRegister = (gate[booleanResult] as OneRegisterInstruction).registerA
            onCreate.replaceInstruction(booleanResult, "const/4 v$gateRegister, 0x1")
        }
    }
