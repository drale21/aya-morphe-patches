package app.morphe.patches.megabook.subscription

import app.morphe.patcher.Fingerprint

/**
 * B4A generates one process-globals holder per app, named `main` after the
 * BASIC module it is compiled from, and initialises every global in
 * `_process_globals`.
 *
 * The class is stable across releases (it is `app.lcs.megabook.main`), while
 * B4A's obfuscator renames the globals themselves on every build - which is why
 * the entitlement flag is looked up by usage instead of by name, see
 * [UnlockSubscriptionPatch].
 */
object GlobalsFingerprint : Fingerprint(
    definingClass = "Lapp/lcs/megabook/main;",
    name = "_process_globals",
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
)
