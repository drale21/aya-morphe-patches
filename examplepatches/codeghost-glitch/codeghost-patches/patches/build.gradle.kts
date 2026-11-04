group = "app.morphe"

patches {
    about {
        name = "CodeGhost Morphe Patches"
        description = "Morphe patches for Android apps"
        source = "git@github.com:codeghost-glitch/codeghost-patches.git"
        author = "codeghost-glitch"
        contact = "na"
        website = "na"
        license = "GNU General Public License v3.0"
    }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.core)
}

tasks {
    // Applies every patch in this bundle to an APK with app.morphe:morphe-patcher and
    // dumps the patched dex files, so fingerprints and instruction edits can be checked.
    register<JavaExec>("runPatchHarness") {
        description = "Applies the bundle to an APK and writes the patched dex files"
        group = "verification"

        dependsOn("testClasses")

        classpath = sourceSets["test"].runtimeClasspath
        mainClass.set("app.morphe.util.PatchHarnessKt")

        doFirst {
            val apk =
                providers.gradleProperty("apk").orNull
                    ?: throw GradleException("runPatchHarness requires -Papk=/absolute/path/to/app.apk")
            setArgs(listOf(apk, providers.gradleProperty("out").orNull ?: "build/patched-dex"))
        }
    }

    // buildAndroid, not build: only buildAndroid writes classes.dex into the .mpp.
    // A dex-less .mpp loads in Morphe Manager as an empty bundle.
    publish {
        dependsOn("buildAndroid")
    }
}
