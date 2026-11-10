/*
 * BitChord's phone UI on iOS: the shared Compose pages (:sharedUi) and data layer (:shared),
 * wired to an iOS host. Built as the static framework `BitChordKit`, which the Xcode project in
 * ios/ links; audio, stream resolution, Selaras and sign-in stay native Swift behind PlaybackBridge.
 *
 *   ./gradlew :iosApp:linkReleaseFrameworkIosArm64 -Pbitchord.ios=true
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val cmp = "1.10.3"

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "BitChordKit"
            isStatic = true
        }
    }
    compilerOptions.freeCompilerArgs.add("-Xnested-type-aliases")

    sourceSets {
        iosMain.dependencies {
            implementation(project(":shared"))
            implementation(project(":sharedUi"))
            implementation("org.jetbrains.compose.runtime:runtime:$cmp")
            implementation("org.jetbrains.compose.foundation:foundation:$cmp")
            implementation("org.jetbrains.compose.ui:ui:$cmp")
            implementation("org.jetbrains.compose.material3:material3:1.7.3")
            implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
            implementation("org.jetbrains.compose.components:components-resources:$cmp")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
            implementation("io.coil-kt.coil3:coil-compose:3.0.4")
            implementation("io.coil-kt.coil3:coil-network-ktor3:3.0.4")
            implementation("io.ktor:ktor-client-darwin:3.5.2")
            implementation("dev.chrisbanes.haze:haze:1.3.1")
            implementation("dev.chrisbanes.haze:haze-materials:1.3.1")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
        }
    }
}
