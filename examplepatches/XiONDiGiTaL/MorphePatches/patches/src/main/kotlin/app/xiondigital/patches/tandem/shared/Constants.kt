package app.xiondigital.patches.tandem.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

/**
 * Tandem: Language exchange (`net.tandem`).
 *
 * Only 6.38.0 (versionCode 11568) has been inspected and used to develop the patches in this
 * package. The manifest of that build reports minSdk 32 / targetSdk 37, and it is distributed
 * on APKMirror as an APKM bundle (`base.apk` plus ABI and density splits), which is what the
 * reference file for this version is.
 *
 * No other version is claimed to work. Adding a version here is a claim that the fingerprints
 * and the injected code were verified against that exact build.
 */
object Constants {
    private const val VERSION_NAME = "6.38.0"

    /** Device requirement taken from the 6.38.0 manifest (`minSdkVersion`). */
    private const val MIN_SDK = 32

    private val TANDEM_TARGETS = listOf(
        AppTarget(
            version = VERSION_NAME,
            minSdk = MIN_SDK,
        ),
    )

    /** Tandem 6.38.0 as the APKM bundle it is distributed in (the inspected reference). */
    val COMPATIBILITY_TANDEM_APKM = Compatibility(
        name = "Tandem: Language exchange",
        packageName = "net.tandem",
        apkFileType = ApkFileType.APKM,
        // Accent colour of the app icon, from the 6.38.0 bundle metadata.
        appIconColor = 0x2CC0D8,
        targets = TANDEM_TARGETS,
    )

    /** The same 6.38.0 build split out into a single APK (universal or `base.apk`). */
    val COMPATIBILITY_TANDEM_APK = Compatibility(
        name = "Tandem: Language exchange",
        packageName = "net.tandem",
        apkFileType = ApkFileType.APK,
        appIconColor = 0x2CC0D8,
        targets = TANDEM_TARGETS,
    )
}
