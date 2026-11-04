package app.morphe.patches.megabook.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    /**
     * Mega Karaoke Songbook (`app.lcs.megabook`), a B4A (Basic4Android) karaoke
     * songbook player.
     *
     * The analysed artifact is the single, non-split APK
     * `Mega Karaoke Songbook - v2.59.apk` (2 dex files, no native libraries).
     *
     * Target verified against version 2.59 (versionCode 259, minSdk 23,
     * targetSdk 36).
     */
    val COMPATIBILITY_MEGA_KARAOKE =
        Compatibility(
            packageName = "app.lcs.megabook",
            name = "Mega Karaoke Songbook",
            description = null,
            apkFileType = ApkFileType.APK,
            appIconColor = 0x004B8BE3.toInt(),
            signatures = null,
            targets =
                listOf(
                    AppTarget(
                        version = "2.59",
                        minSdk = 23,
                    ),
                ),
        )
}
