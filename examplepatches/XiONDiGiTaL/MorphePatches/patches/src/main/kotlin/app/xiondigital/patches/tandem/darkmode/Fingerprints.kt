package app.xiondigital.patches.tandem.darkmode

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * `net.tandem.TandemApp.onCreate()` in Tandem 6.38.0.
 *
 * Tandem's real `Application` subclass is not obfuscated: the manifest declares
 * `com.pairip.application.Application`, which extends `net.tandem.TandemApp` and only overrides
 * `attachBaseContext` (PairIP integrity/licence checks). `onCreate` is therefore inherited
 * unchanged from `TandemApp`, which makes it a stable, readable anchor that survives the
 * PairIP wrapper.
 *
 * `onCreate()` begins with `invoke-virtual {p0}, Lnet/tandem/TandemApp;->a()V` and then uses the
 * `"coroutineScope"` and `"ioDispatcher"` delegate error messages; the string filter below pins
 * the method to that body. The method's `.locals 11` leaves v0/v1 free at entry, which is what
 * the injected code uses.
 */
internal object TandemAppOnCreateFingerprint : Fingerprint(
    definingClass = "Lnet/tandem/TandemApp;",
    name = "onCreate",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        string("coroutineScope"),
    ),
)
