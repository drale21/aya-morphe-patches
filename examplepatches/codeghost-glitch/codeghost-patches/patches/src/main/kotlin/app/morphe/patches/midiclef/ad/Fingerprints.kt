package app.morphe.patches.midiclef.ad

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.newInstance
import com.android.tools.smali.dexlib2.Opcode

/**
 * MIDI Clef sells exactly one Play Billing product, the non-consumable
 * "remove ads" purchase `it.giccisw.midi.noads` (verified in
 * `BillingInAppActivity`, `vt` and `tt`).
 *
 * Entitlement is computed in one place: `Ltt;->b(ILjava/util/ArrayList;)V`,
 * the purchases-updated callback of the app's own billing wrapper. It walks the
 * purchase list, looks for a product id containing `it.giccisw.midi.noads`, and
 * stores the result in the "noAds" boolean preference (`Lst;->m:Lel3;`)
 * before publishing a [BillingNoAdsStatus][it.giccisw.util.googleplay.billing.BillingNoAdsStatus].
 *
 * Every ad decision in the app reads that preference (or the published status):
 * banner/native/medium fragments in `MidiActivityMain`, the interstitial
 * loader, and the GDPR manager.
 */
object NoAdsPurchaseUpdateFingerprint : Fingerprint(
    definingClass = "Ltt;",
    name = "b",
    returnType = "V",
    parameters = listOf("I", "Ljava/util/ArrayList;"),
    filters =
        listOf(
            // Reads its own preference holder: this.c
            fieldAccess(
                opcode = Opcode.IGET_OBJECT,
                definingClass = "Ltt;",
                name = "c",
                type = "Lst;",
            ),
            // Writes the "noAds" preference: this.c.m
            fieldAccess(
                opcode = Opcode.IGET_OBJECT,
                definingClass = "Lst;",
                name = "m",
                type = "Lel3;",
            ),
            // ... and stores the computed boolean into it.
            methodCall(
                definingClass = "Ljava/lang/Boolean;",
                name = "valueOf",
                parameters = listOf("Z"),
                returnType = "Ljava/lang/Boolean;",
            ),
        ),
)

/**
 * `MidiActivityMain.onCreate` holds the only ad decision of the main screen.
 *
 * It reads the same "noAds" preference and picks one of two branches:
 *
 *  - owned   -> `this.o0 = false` (no banner, native or medium ad fragment is
 *               ever added by `show()`) and the interstitial loader is skipped
 *  - not owned -> `nav_noads` menu item hidden and the AdMob interstitial
 *               (`InterstitialCustom`) is registered
 *
 * Forcing the owned branch keeps the app ad-free even when Play Billing never
 * answers (no Play account, no network, microG): in that case
 * [NoAdsPurchaseUpdateFingerprint] never runs, because the billing connection
 * failure drops the queued purchase query.
 */
object MidiActivityAdGateFingerprint : Fingerprint(
    definingClass = "Lit/giccisw/midi/MidiActivityMain;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    filters =
        listOf(
            // this.k0.c.c.m -> the "noAds" preference
            fieldAccess(
                opcode = Opcode.IGET_OBJECT,
                definingClass = "Lst;",
                name = "m",
                type = "Lel3;",
            ),
            methodCall(
                definingClass = "Ljava/lang/Boolean;",
                name = "booleanValue",
                returnType = "Z",
            ),
            // The interstitial loader created on the "not owned" branch.
            newInstance("Lt5;"),
        ),
)
