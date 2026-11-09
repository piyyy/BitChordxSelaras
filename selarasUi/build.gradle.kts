import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * The Selaras equalizer page, drawn once for the phone, the tablet and the desktop.
 *
 * Its engine (state, presets, AutoEq, the DSP itself) is in :shared; this is only the page. The
 * phone draws its UI from :sharedUi and the desktop from :desktopUi, and those two may never meet in
 * one graph, so the page lives in a module of its own that both apps can depend on. It shares no
 * packages with either.
 *
 * Versions follow :sharedUi: the CMP release whose Android artifacts are the phone's androidx ones,
 * and on the JVM whatever the desktop resolves for the platform it is built for.
 */
val cmp = "1.10.3"

val selarasTarget: String = (providers.gradleProperty("bitchord.target").orNull
    ?: System.getProperty("os.name")).lowercase()

/** Windows stays on 1.10.3 (see :sharedUi and :desktopUi); everywhere else the desktop is on 1.12. */
val CMP_DESKTOP = if (selarasTarget.contains("windows")) "1.10.3" else "1.12.0"

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    applyDefaultHierarchyTemplate()

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        // Both targets are JVMs, so the page is written once against the JDK.
        val jvmSharedMain by creating {
            dependsOn(commonMain.get())
        }
        androidMain.get().dependsOn(jvmSharedMain)
        jvmMain.get().dependsOn(jvmSharedMain)

        commonMain.dependencies {
            api(project(":shared"))
            implementation("org.jetbrains.compose.runtime:runtime:$cmp")
            implementation("org.jetbrains.compose.foundation:foundation:$cmp")
            implementation("org.jetbrains.compose.ui:ui:$cmp")
            implementation("org.jetbrains.compose.material3:material3:1.7.3")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
        }
        jvmMain.dependencies {
            implementation("org.jetbrains.compose.ui:ui-desktop:$CMP_DESKTOP")
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.9.3")
        }
    }
}

android {
    namespace = "com.music.bitchord.selaras.ui"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
    }
}
