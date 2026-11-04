rootProject.name = "codeghost-patches"

pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        google()
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/MorpheApp/registry")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
        // Obtain baksmali/smali from source builds - https://github.com/iBotPeaches/smali
        maven { url = uri("https://jitpack.io") }
    }
}

plugins {
    id("app.morphe.patches") version "1.3.4"
}

settings {
    extensions {
        defaultNamespace = "app.morphe.extension"

        proguardFiles(rootProject.projectDir.resolve("extensions/proguard-rules.pro").toString())
    }
}
