# BitChord + Selaras

Selaras is BitChord's equaliser on every platform: graphic and parametric EQ, presets, AutoEq
headphone profiles, tone, preamp/balance, compressor, limiter, and a separate EQ per output device.

| Platform | Where the EQ runs | Settings file |
|---|---|---|
| Linux with the Selaras service | system-wide, PipeWire filter-chain (`selaras-daemon`) | `~/.config/selaras/state.json` |
| Linux without it, or `BITCHORD_SELARAS=app` | inside BitChord | `~/.config/bitchord/selaras/state.json` |
| Windows | inside BitChord | `%APPDATA%\BitChord\selaras\state.json` |
| macOS | inside BitChord | `~/Library/Application Support/BitChord/selaras/state.json` |
| Android | inside BitChord (no root) | app-private storage |

The file format is Selaras' own, so presets (.json) and AutoEq profiles move between devices.

## Code

- `shared/src/jvmSharedMain/.../selaras/` — state, presets, AutoEq, filter math (`Selaras`),
  the DSP (`SelarasDsp`), spectrum, and the `SelarasPlatform` interface. Tests in `shared/src/jvmTest`.
- `selarasUi/` — the Selaras page, used by both the desktop and the phone.
- `desktopApp/.../DesktopSelarasPlatform.kt` — Linux service / in-app selection; the DSP sits just
  before the device in `DesktopAudioEngine.play()`.
- `app/.../selaras/AndroidSelarasPlatform.kt` — Android outputs; the DSP sits in `DspChain`.

## Building

All from Linux, with a JDK 21 at `~/.local/share/jdk-21` (or `JAVA_HOME`):

- Linux (Fedora, per-user install): `./build-install-fedora.sh`
- Windows x64 zip with Java bundled: `./build-windows.sh` → `dist/`
  (Automix on Windows additionally needs `sudo dnf install mingw64-gcc-c++ cmake`)
- Android APKs: `./build-android.sh` → `dist/`. Needs the Android SDK in `~/Android/Sdk`:
  `sdkmanager "platforms;android-37.0" "platforms;android-36" "build-tools;35.0.0" "cmake;3.22.1" "ndk;27.0.12077973"`.
  The first run creates `bitchord-release.jks` + `keystore.properties` — back both up; every
  update must be signed with the same key.
- Following upstream: `./update-bitchord.sh`
