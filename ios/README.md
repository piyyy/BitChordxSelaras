# BitChord for iOS

A native SwiftUI port of BitChord, with Selaras as its equaliser.

| Part | What it is |
|---|---|
| `BitChordCore/` | Swift package, no Apple UI/audio frameworks: the YouTube Music client and parser (ported from `shared/…/innertube`), Selaras (state in the same `state.json` format, presets, AutoEq, the DSP) and lyrics (LRCLIB, YouTube Music). Builds and tests on Linux too. |
| `App/` | The SwiftUI app: Home, Explore (new releases, charts, moods & genres), Search with filters and suggestions, album / playlist / artist pages, Library (signed in), the player with queue, AutoPlay radio, synced lyrics, lock-screen controls, and the Selaras page. |
| `project.yml` | XcodeGen definition of the Xcode project. |

Playback: [YouTubeKit](https://github.com/alexeichhorn/YouTubeKit) resolves the audio stream, `AVPlayer` plays
it, and an `MTAudioProcessingTap` runs every buffer through `SelarasProcessor` — the same DSP as the
desktop and Android apps, so presets sound the same everywhere. Each output (speaker, wired, AirPods,
other Bluetooth, AirPlay) keeps its own EQ.

## Build

**On GitHub (no Mac needed):** push this repository to your own GitHub account; the `iOS` workflow
(`.github/workflows/ios.yml`) runs the core tests and builds `BitChord-unsigned.ipa`, attached to the run
as an artifact. It can also be started by hand from the Actions tab.

**On a Mac:**

```sh
brew install xcodegen
cd ios && xcodegen generate && open BitChord.xcodeproj
```

**Core tests on Linux.** Swift from swift.org (e.g. in `~/.local/share/swift`), plus
`sudo dnf install gcc glibc-devel libstdc++-devel libcurl-devel libxml2-devel`. On Fedora 45 SwiftPM 6.4's
new build system misreads GNU ld and the `libstdc++.so` dev link is missing, hence the extra flags:

```sh
mkdir -p ~/.local/share/swift-linkfix && ln -sf /usr/lib64/libstdc++.so.6 ~/.local/share/swift-linkfix/libstdc++.so
swift test --build-system native -Xlinker -L$HOME/.local/share/swift-linkfix --package-path ios/BitChordCore
# against the real music.youtube.com too:
BITCHORD_LIVE=1 swift test --build-system native -Xlinker -L$HOME/.local/share/swift-linkfix --package-path ios/BitChordCore --filter LiveTests
```

## Install on an iPhone

The .ipa is unsigned; a sideloading tool signs it with your Apple ID:

- **Sideloadly** (Windows/macOS) or **AltStore**: open the .ipa, sign in with your Apple ID, install.
- With a free Apple ID the app has to be re-signed every 7 days; a paid developer account lasts a year.
- On the iPhone: Settings › General › VPN & Device Management › trust your Apple ID, and turn on
  Developer Mode (Settings › Privacy & Security) on iOS 16+.

## Not yet ported

Compared with Android: offline downloads, Automix/crossfade, Spotify import, Last.fm scrobbling,
Listen Together, Discord, lyrics translation, home-screen widgets and CarPlay.
