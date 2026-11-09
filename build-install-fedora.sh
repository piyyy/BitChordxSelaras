#!/usr/bin/env bash
# Build BitChord desktop (with the Selaras equalizer page) and install it per-user on Fedora.
# Needs a JDK 21: JAVA_HOME=/path/to/jdk-21 ./build-install-fedora.sh
set -euo pipefail
cd "$(dirname "$0")"

JAVA_HOME="${JAVA_HOME:-$HOME/.local/share/jdk-21}"
if [[ ! -x "$JAVA_HOME/bin/javac" ]] || ! "$JAVA_HOME/bin/java" -version 2>&1 | grep -q '"21\.'; then
    echo "JDK 21 not found at $JAVA_HOME (set JAVA_HOME)" >&2
    exit 1
fi
export JAVA_HOME PATH="$JAVA_HOME/bin:$PATH"

./gradlew --no-daemon \
    -Dorg.gradle.java.installations.paths="$JAVA_HOME" \
    -Porg.gradle.java.installations.auto-detect=false \
    -Porg.gradle.java.installations.auto-download=false \
    -Pbitchord.version=1.8-beta1 \
    :desktopApp:createDistributable

out=desktopApp/build/compose/binaries/main/app/BitChord
dest="$HOME/.local/opt/bitchord"
if [[ -d "$dest" ]]; then
    rm -rf "$dest.bak"
    mv "$dest" "$dest.bak"
fi
cp -a "$out" "$dest"
ln -sf "$dest/bin/BitChord" "$HOME/.local/bin/bitchord"
echo "Installed to $dest (previous build kept in $dest.bak)"
