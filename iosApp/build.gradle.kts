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

val material3 = "1.10.0-alpha05" // forced for every module on iOS, see the root build file

/*
 * The phone app's own UI files (app/src/main/java), compiled unchanged for iOS: copied into the
 * build with `import com.music.bitchord.ios.port.*` added (java.lang stand-ins) and the
 * Android-only lint annotations dropped. What they reach that is Android-only has an iOS
 * counterpart in src/iosMain: R + stringResource (strings from app/src/main/res, see
 * ios/kmp/gen-android-strings.py), AppSettings, android.os.Build, android.view.Window, and the
 * four backdrop files that wrap android.graphics (here on Skia).
 */
val androidPortFiles = listOf(
    "ui/components/FrostedTopBar.kt",
    "ui/components/TopFadeBlur.kt",
    "ui/components/FloatingBottomBar.kt",
    "ui/components/LiquidGlass.kt",
    "ui/components/GlassNavBar.kt",
    "ui/components/MiniPlayer.kt",
    "ui/components/BottomFadeScrim.kt",
    "ui/components/QueueActionNotice.kt",
    "ui/player/PlayerSheetMotion.kt",
    "ui/components/floatingtabbar/FloatingTabBar.kt",
    "ui/components/floatingtabbar/GlassSelectionPill.kt",
    "ui/components/backdrop/Backdrop.kt",
    "ui/components/backdrop/DrawBackdropModifier.kt",
    "ui/components/backdrop/RuntimeShaderCache.kt",
    "ui/components/backdrop/BackdropEffectScope.kt",
    "ui/components/backdrop/backdrops/Backdrop.kt",
    "ui/components/backdrop/backdrops/EmptyBackdrop.kt",
    "ui/components/backdrop/backdrops/LayerBackdropModifier.kt",
    "ui/components/backdrop/backdrops/CanvasBackdrop.kt",
    "ui/components/backdrop/backdrops/LayerBackdrop.kt",
    "ui/components/backdrop/backdrops/CombinedBackdrop.kt",
    "ui/components/backdrop/shadow/InnerShadow.kt",
    "ui/components/backdrop/shadow/Shadow.kt",
    "ui/components/backdrop/shadow/InnerShadowModifier.kt",
    "ui/components/backdrop/shadow/ShadowModifier.kt",
    "ui/components/backdrop/internal/LayerRecorder.kt",
    "ui/components/backdrop/internal/ShapeProvider.kt",
    "ui/components/backdrop/internal/InverseLayerScope.kt",
    "ui/components/backdrop/internal/Outline.kt",
    "ui/components/backdrop/internal/Shaders.kt",
    "ui/components/backdrop/effects/RenderEffect.kt",
    "ui/components/backdrop/effects/Lens.kt",
    "ui/components/backdrop/effects/ColorFilter.kt",
    "ui/components/backdrop/effects/Blur.kt",
    "ui/components/backdrop/highlight/Highlight.kt",
    "ui/components/backdrop/highlight/HighlightModifier.kt",
    "ui/components/backdrop/highlight/HighlightStyle.kt",
    // Album / playlist / artist pages.
    "ui/screens/DetailScreen.kt",
    "ui/screens/LocalFolder.kt",
    "data/LocalGroups.kt",
    "ui/components/CoverPlaceholder.kt",
    "data/canvas/AppleArtistArt.kt",
    "data/canvas/ArtistSignatures.kt",
    // The song menu.
    "ui/components/SongActionsSheet.kt",
    "playback/SleepTimer.kt",
)

val androidPort by tasks.registering(Sync::class) {
    from(rootProject.file("app/src/main/java/com/music/bitchord")) { include(androidPortFiles) }
    into(layout.buildDirectory.dir("generated/androidPort/kotlin/com/music/bitchord"))
    val lint = Regex("""@(param:)?(FloatRange|RequiresApi|ChecksSdkIntAtLeast|Language)\([^)]*\)\s*""")
    filter { line: String ->
        when {
            line.startsWith("package ") -> line + "\n\nimport com.music.bitchord.ios.port.*"
            line.startsWith("import androidx.annotation.") || line.startsWith("import org.intellij.lang.annotations.") -> ""
            else -> line.replace(lint, "")
        }
    }
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "BitChordKit"
            isStatic = true
        }
    }
    compilerOptions.freeCompilerArgs.add("-Xnested-type-aliases")

    sourceSets {
        iosMain {
            kotlin.srcDir(androidPort.map { layout.buildDirectory.dir("generated/androidPort/kotlin").get() })
        }
        iosMain.dependencies {
            implementation(project(":shared"))
            implementation(project(":sharedUi"))
            implementation("org.jetbrains.compose.runtime:runtime:$cmp")
            implementation("org.jetbrains.compose.foundation:foundation:$cmp")
            implementation("org.jetbrains.compose.ui:ui:$cmp")
            implementation("org.jetbrains.compose.material3:material3:$material3")
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
