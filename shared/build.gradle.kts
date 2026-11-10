import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
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

    // Android and desktop are both JVM, but the default hierarchy gives them no source set in
    // common.
    applyDefaultHierarchyTemplate()

    sourceSets {
        val jvmSharedMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                // Compile-only: the two applications supply the runtime artifact themselves.
                compileOnly("com.microsoft.onnxruntime:onnxruntime:1.20.0")
                // The network stack the lyrics providers and the YouTube Music
                // client run on, at the phone's versions — both applications
                // now call the same code, so they resolve the same libraries.
                api("io.ktor:ktor-client-core:3.5.2")
                api("io.ktor:ktor-client-okhttp:3.5.2")
                api("io.ktor:ktor-client-websockets:3.5.2")
                api("io.ktor:ktor-client-content-negotiation:3.5.2")
                api("io.ktor:ktor-serialization-kotlinx-json:3.5.2")
                api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                api("org.jsoup:jsoup:1.22.2")
                // YouTube stream resolution, as the phone does it: InnerTubeX's
                // client catalog and cipher tiers, NewPipe as the fallback. NewPipe
                // is compile-only because the phone ships a trimmed jar of it.
                api("com.github.MetrolistGroup.innertubex:innertubex:v0.7.0")
                compileOnly("com.github.TeamNewPipe:NewPipeExtractor:v0.26.3")
            }
        }
        androidMain.get().dependsOn(jvmSharedMain)
        jvmMain.get().dependsOn(jvmSharedMain)

        commonMain.dependencies {
            // Models in this module intentionally have no Android or Compose dependency.
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation("com.squareup.okhttp3:mockwebserver:5.3.2")
        }
    }
}

android {
    namespace = "com.music.bitchord.shared"
    compileSdk = 36
}

// ---- iOS (BitChordxSelaras) -------------------------------------------------------------------
// Off unless -Pbitchord.ios=true, so the phone and desktop builds are untouched. The iOS target
// compiles the same jvmSharedMain sources as the JVM targets, from the same directory — nothing is
// moved, so upstream updates merge cleanly — minus the files that are JVM through and through
// (OkHttp, NewPipe/InnerTubeX, ONNX, ...). Those have iOS counterparts in src/iosMain.
/** commonMain files that only compile against the JVM; see the iosMain block above. */
val iosCommonOnJvm = listOf("com/music/bitchord/data/listentogether/ServerClock.kt")

val iosCommonCopy by tasks.registering(Sync::class) {
    from("src/commonMain/kotlin") { include(iosCommonOnJvm) }
    into(layout.buildDirectory.dir("generated/iosCommon/kotlin"))
}

val bitchordIos = providers.gradleProperty("bitchord.ios").orNull == "true"
if (bitchordIos) {
    kotlin {
        iosArm64()
        iosSimulatorArm64()
        // java.lang.Character.UnicodeBlock & co. are nested type aliases in the per-package stand-ins.
        compilerOptions.freeCompilerArgs.add("-Xnested-type-aliases")
        sourceSets {
            // commonMain files that quietly use the JVM (fine while every target was a JVM): moved
            // out of commonMain into iosMain, where the java.lang stand-ins are visible.
            commonMain { kotlin.exclude(iosCommonOnJvm) }
            iosMain {
                kotlin.srcDir("src/jvmSharedMain/kotlin")
                kotlin.srcDir(iosCommonCopy)
                kotlin.exclude(iosExcluded)
                dependencies {
                    api("io.ktor:ktor-client-core:3.5.2")
                    api("io.ktor:ktor-client-darwin:3.5.2")
                    api("io.ktor:ktor-client-websockets:3.5.2")
                    api("io.ktor:ktor-client-content-negotiation:3.5.2")
                    api("io.ktor:ktor-serialization-kotlinx-json:3.5.2")
                    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
                    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                }
            }
        }
    }
}

/** jvmSharedMain files the iOS build leaves out; see src/iosMain for what stands in. */
val iosExcluded: List<String>
    get() = listOf(
        "com/music/bitchord/data/Http.kt",
        "com/music/bitchord/data/lyrics/Genius.kt",
        "com/music/bitchord/data/lyrics/TtmlLyrics.kt",
        "com/music/bitchord/data/lyrics/LyricsTranslation.kt",
        "com/music/bitchord/data/innertube/InnerTubeXResolver.kt",
        "com/music/bitchord/data/innertube/PlayerClient.kt",
        "com/music/bitchord/data/innertube/StreamResolver.kt",
        "com/music/bitchord/data/innertube/potoken/**",
        "com/music/bitchord/playback/smart/**",
        "com/music/bitchord/data/presence/**",
        "com/my/kizzy/**",
        "com/music/bitchord/selaras/**",
    )
