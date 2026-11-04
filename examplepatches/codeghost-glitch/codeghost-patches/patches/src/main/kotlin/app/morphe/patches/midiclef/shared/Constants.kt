package app.morphe.patches.midiclef.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    /**
     * MIDI Clef (`it.giccisw.midi`), a Kotlin MIDI/karaoke player.
     *
     * The analysed artifact is the APKCube split bundle
     * `MIDI-Clef-Karaoke-Player_9.2.6_apkcube.apks` (base.apk + 13
     * `config.*` splits), so `APKS` is the accurate file type.
     *
     * Target verified against version 9.2.6 (versionCode 90206, minSdk 29,
     * targetSdk 36).
     */
    val COMPATIBILITY_MIDI_CLEF =
        Compatibility(
            packageName = "it.giccisw.midi",
            name = "MIDI Clef",
            description = null,
            apkFileType = ApkFileType.APKS,
            appIconColor = 0x00FF6B6B.toInt(),
            signatures = null,
            targets =
                listOf(
                    AppTarget(
                        version = "9.2.6",
                        minSdk = 29,
                    ),
                ),
        )
}
