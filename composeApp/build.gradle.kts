// KMP : Desktop only

import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val appGroup = providers.gradleProperty("app.group").get()
val appRootName = providers.gradleProperty("app.rootName").get()
val appDisplayName = providers.gradleProperty("app.displayName").get()
val appVersion = providers.gradleProperty("app.version").get()

// Compose jpackage rejects DMG versions whose MAJOR is 0. About still shows
// app.version; only the installer bundle version is rewritten.
fun dmgPackageVersion(version: String): String {
    val parts = version.split('.')
    val major = parts[0].toInt()
    if (major > 0) return version
    val minor = parts.getOrElse(1) { "0" }
    val patch = parts.getOrElse(2) { "0" }
    return "1.$minor.$patch"
}

group = appGroup
version = appVersion

kotlin {
    jvm("desktop")
    jvmToolchain(25)

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "$appGroup.MainKt"

        // The macOS menu bar name is fixed when the JVM starts. Gradle `run` does not
        // package an app, so the dock name has to be passed as a launcher argument.
        if (System.getProperty("os.name").orEmpty().contains("mac", ignoreCase = true)) {
            jvmArgs += listOf(
                "-Dapple.awt.application.name=$appDisplayName",
                "-Xdock:name=$appDisplayName",
                "-Xdock:icon=${project.file("packaging/macos/foldmerge-icon-1024.png").absolutePath}",
            )
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = appRootName
            packageVersion = dmgPackageVersion(appVersion)
            description = "Compare text files from two folders and save the chosen side."
            vendor = "neojou"
            includeAllModules = true
            macOS {
                iconFile.set(project.file("packaging/macos/Foldmerge.icns"))
                dockName = appDisplayName
                bundleID = appGroup
            }
        }
    }
}

compose.resources {
    packageOfResClass = appGroup
}
