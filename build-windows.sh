#!/usr/bin/env bash
# Build BitChord (with Selaras) for Windows 10/11 x64, from Linux.
#
# Produces dist/BitChord-<version>-windows-x64.zip: unzip anywhere on Windows and run BitChord.bat.
# Java is bundled (Temurin 21 JRE), so nothing else needs installing there.
#
# Optional: Automix needs bitchord_analysis.dll, which is cross-compiled when MinGW and CMake are
# installed (Fedora: sudo dnf install mingw64-gcc-c++ cmake). Without them Automix is off on Windows.
set -euo pipefail
cd "$(dirname "$0")"

VERSION="${BITCHORD_VERSION:-1.8-beta1}"
JAVA_HOME="${JAVA_HOME:-$HOME/.local/share/jdk-21}"
JRE_CACHE="${JRE_CACHE:-$HOME/.cache/bitchord-build/jre-windows-x64}"
if [[ ! -x "$JAVA_HOME/bin/javac" ]]; then
    echo "JDK 21 not found at $JAVA_HOME (set JAVA_HOME)" >&2
    exit 1
fi
export JAVA_HOME PATH="$JAVA_HOME/bin:$PATH"

if [[ ! -f "$JRE_CACHE/bin/javaw.exe" ]]; then
    echo "Downloading the Windows Java 21 runtime…"
    tmp="$(mktemp -d)"
    curl -fL -o "$tmp/jre.zip" "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jre/hotspot/normal/eclipse"
    unzip -q "$tmp/jre.zip" -d "$tmp"
    mkdir -p "$(dirname "$JRE_CACHE")"
    rm -rf "$JRE_CACHE"
    mv "$tmp"/jdk-*-jre "$JRE_CACHE"
    rm -rf "$tmp"
fi

./gradlew --no-daemon \
    -Dorg.gradle.java.installations.paths="$JAVA_HOME" \
    -Porg.gradle.java.installations.auto-detect=false \
    -Porg.gradle.java.installations.auto-download=false \
    -Pbitchord.version="$VERSION" \
    -Pbitchord.target=windows \
    :desktopApp:windowsPortableAssemble

stage=desktopApp/build/windows-portable/BitChord
rm -rf "$stage/runtime"
cp -a "$JRE_CACHE" "$stage/runtime"

# The bundled runtime, without a console window. Falls back to a Java on PATH if it was removed.
cat > "$stage/BitChord.bat" <<EOF
@echo off
setlocal
set "HERE=%~dp0"
set "RUNNER=%HERE%runtime\\bin\\javaw.exe"
if not exist "%RUNNER%" set "RUNNER=javaw"
start "" "%RUNNER%" -Xmx512m -XX:+UseG1GC -Dbitchord.version=$VERSION -cp "%HERE%lib\\*" com.music.bitchord.desktop.MainKt %*
endlocal
EOF
# Same, with a console that stays open, for when something goes wrong.
cat > "$stage/BitChord (debug console).bat" <<EOF
@echo off
setlocal
set "HERE=%~dp0"
set "RUNNER=%HERE%runtime\\bin\\java.exe"
if not exist "%RUNNER%" set "RUNNER=java"
"%RUNNER%" -Xmx512m -XX:+UseG1GC -Dbitchord.version=$VERSION -cp "%HERE%lib\\*" com.music.bitchord.desktop.MainKt %*
pause
endlocal
EOF
sed -i 's/$/\r/' "$stage"/*.bat

mkdir -p dist
out="dist/BitChord-$VERSION-windows-x64.zip"
rm -f "$out"
(cd "$(dirname "$stage")" && zip -qr -9 "$OLDPWD/$out" BitChord)
echo "Windows build: $out ($(du -h "$out" | cut -f1))"
