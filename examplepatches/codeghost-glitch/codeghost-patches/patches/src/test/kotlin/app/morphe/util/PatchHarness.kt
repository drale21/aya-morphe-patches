package app.morphe.util

import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.patch.Patch
import app.morphe.patches.megabook.subscription.unlockSubscriptionPatch
import app.morphe.patches.midiclef.ad.unlockNoAdsSubscriptionPatch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Applies every patch listed in [patches] to [args]0 and writes the patched dex
 * files to [args]1 so they can be dumped and diffed against the originals.
 *
 * Patches are applied as listed, without the compatibility filter Manager uses, so a
 * patch for another app reports its fingerprint failure instead of being skipped.
 *
 * Run with `./gradlew :patches:runPatchHarness -Papk=/path/to/app.apk`.
 */
private val patches: List<Patch<*>> =
    listOf(
        unlockNoAdsSubscriptionPatch,
        unlockSubscriptionPatch,
    )

fun main(args: Array<String>) {
    val apk = File(args[0]).absoluteFile
    val outDir = File(args.getOrElse(1) { "build/patched-dex" }).absoluteFile
    outDir.mkdirs()

    println("apk: " + apk)
    println("patches: " + patches.map { it.name })

    Patcher(PatcherConfig(apk)).use { patcher ->
        patcher += patches.toSet()
        runBlocking {
            patcher().collect { result ->
                println("result: patch=" + result.patch.name + " exception=" + result.exception)
                result.exception?.printStackTrace()
            }
        }
        patcher.get().dexFiles.forEach { dex ->
            val target = File(outDir, dex.name)
            dex.stream.use { input -> target.outputStream().use { input.copyTo(it) } }
            println("wrote " + target + " (" + target.length() + " bytes)")
        }
    }
}
