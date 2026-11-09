#!/usr/bin/env bash
# Build the BitChord Android app (with Selaras) as installable, signed release APKs.
#
# Produces dist/BitChord-<version>-android-universal.apk (any phone) and an arm64 one (smaller,
# for nearly every phone since 2017).
#
# Needs the Android SDK (ANDROID_HOME, default ~/Android/Sdk) with platform 37, build-tools,
# CMake 3.22.1 and the NDK; see README-selaras.md.
#
# Signing: the first run creates bitchord-release.jks and keystore.properties (both gitignored).
# BACK THEM UP. Every later update must be signed with the same key, or Android refuses to install
# it over the old one.
set -euo pipefail
cd "$(dirname "$0")"

JAVA_HOME="${JAVA_HOME:-$HOME/.local/share/jdk-21}"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
if [[ ! -x "$JAVA_HOME/bin/javac" ]]; then
    echo "JDK 21 not found at $JAVA_HOME (set JAVA_HOME)" >&2
    exit 1
fi
if [[ ! -d "$ANDROID_HOME/platforms" ]]; then
    echo "Android SDK not found at $ANDROID_HOME (set ANDROID_HOME)" >&2
    exit 1
fi
export JAVA_HOME ANDROID_HOME PATH="$JAVA_HOME/bin:$PATH"

grep -q '^sdk.dir=' local.properties 2>/dev/null || echo "sdk.dir=$ANDROID_HOME" >> local.properties

if [[ ! -f keystore.properties ]]; then
    echo "Creating a release signing key (bitchord-release.jks) — back it up together with keystore.properties."
    pass="$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 24)"
    keytool -genkeypair -keystore bitchord-release.jks -storetype PKCS12 \
        -keyalg RSA -keysize 4096 -validity 10000 -alias bitchord \
        -storepass "$pass" -keypass "$pass" \
        -dname "CN=BitChord Selaras, O=Personal build" >/dev/null
    printf 'storeFile=bitchord-release.jks\nstorePassword=%s\nkeyAlias=bitchord\nkeyPassword=%s\n' "$pass" "$pass" > keystore.properties
    chmod 600 keystore.properties bitchord-release.jks
fi

./gradlew --no-daemon \
    -Dorg.gradle.java.installations.paths="$JAVA_HOME" \
    -Porg.gradle.java.installations.auto-detect=false \
    -Porg.gradle.java.installations.auto-download=false \
    :app:assembleProdRelease

apks=app/build/outputs/apk/prod/release
version="$(sed -n 's/.*bitchordVersionName"\] = "\(.*\)"/\1/p' build.gradle.kts | head -1)"
mkdir -p dist
for abi in universal arm64-v8a; do
    src="$(ls "$apks"/*"$abi"*.apk 2>/dev/null | head -1 || true)"
    [[ -n "$src" ]] || continue
    cp "$src" "dist/BitChord-$version-android-$abi.apk"
    echo "Android build: dist/BitChord-$version-android-$abi.apk ($(du -h "$src" | cut -f1))"
done
