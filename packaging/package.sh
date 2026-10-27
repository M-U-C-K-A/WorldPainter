#!/usr/bin/env bash
#
# Builds a native, self-contained WorldPainter package for the current operating system, with a bundled Java runtime,
# so that users do not need to install Java.
#
# Usage: packaging/package.sh [TYPE]
#
#   TYPE  app-image (default; a WorldPainter.app on macOS, a folder elsewhere), or one of the installer types supported
#         by jpackage on the current OS: dmg or pkg (macOS), exe or msi (Windows, requires the WiX toolset), deb or rpm
#         (Linux).
#
# Requirements: JDK 21 or later (with jpackage) and Maven. JAVA_HOME is used if set, otherwise the JDK on the PATH.
# The result is written to the dist directory at the root of the repository.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TYPE="${1:-app-image}"

case "$(uname -s)" in
    Darwin) OS=mac ;;
    Linux) OS=linux ;;
    MINGW*|MSYS*|CYGWIN*) OS=windows ;;
    *) echo "Unsupported operating system: $(uname -s)" >&2; exit 1 ;;
esac

if [[ -n "${JAVA_HOME:-}" ]] && [[ "$OS" != windows ]]; then
    JPACKAGE="$JAVA_HOME/bin/jpackage"
else
    # On Windows JAVA_HOME contains backslashes, so rely on the PATH
    JPACKAGE="$(command -v jpackage || true)"
fi
if [[ -z "$JPACKAGE" ]] || ! "$JPACKAGE" --version >/dev/null 2>&1; then
    echo "jpackage not found: install JDK 21 or later and set JAVA_HOME (or put its bin directory on the PATH)" >&2
    exit 1
fi

cd "$ROOT/WorldPainter"
echo "Building WorldPainter..."
mvn -B -q -DskipTests install
VERSION="$(sed -n 's:.*<version>\(.*\)</version>.*:\1:p' pom.xml | head -1)"
# Native packages need a purely numeric version
APP_VERSION="${VERSION%%-*}"

INPUT="WPGUI/target/jpackage-input"
rm -rf "$INPUT"
mvn -B -q -pl WPGUI dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/jpackage-input
cp "WPGUI/target/WPGUI-$VERSION.jar" "$INPUT/"
# WPDynmapPreviewer replaces some classes of DynmapCore, so it must come before it on the class path. jpackage sorts the
# class path by file name, so give it a name which sorts first
mv "$INPUT/WPDynmapPreviewer-$VERSION.jar" "$INPUT/0-WPDynmapPreviewer-$VERSION.jar"

DEST="$ROOT/dist"
mkdir -p "$DEST"
rm -rf "$DEST/WorldPainter" "$DEST/WorldPainter.app"

ARGS=(
    --type "$TYPE"
    --name WorldPainter
    --app-version "$APP_VERSION"
    --vendor "WorldPainter contributors"
    --description "Interactive map generator for Minecraft"
    --copyright "Copyright (C) pepsoft.org and contributors, licensed under the GPL v3"
    --input "$INPUT"
    --main-jar "WPGUI-$VERSION.jar"
    --main-class org.pepsoft.worldpainter.Main
    --dest "$DEST"
    # Allow large worlds: use up to three quarters of the physical memory (the JVM default is one quarter)
    --java-options "-XX:MaxRAMPercentage=75"
    --java-options "-XX:+UseStringDeduplication"
    --java-options "-Dfile.encoding=UTF-8"
)
if [[ "$TYPE" != "app-image" ]]; then
    ARGS+=(--file-associations "$ROOT/packaging/world-file.properties")
fi

case "$OS" in
    mac)
        ARGS+=(--icon "$ROOT/packaging/WorldPainter.icns"
               --mac-package-identifier org.pepsoft.worldpainter
               --mac-package-name WorldPainter
               --java-options "-Dapple.awt.application.name=WorldPainter")
        ;;
    windows)
        ARGS+=(--icon "$ROOT/packaging/WorldPainter.ico")
        if [[ "$TYPE" != "app-image" ]]; then
            ARGS+=(--win-menu --win-shortcut --win-dir-chooser --win-upgrade-uuid 6c1b3a64-6f0e-4c39-9a51-7c4f4d4a7e21)
        fi
        ;;
    linux)
        ARGS+=(--icon "$ROOT/packaging/icon.png")
        if [[ "$TYPE" != "app-image" ]]; then
            ARGS+=(--linux-shortcut --linux-menu-group Graphics --linux-app-category graphics)
        fi
        ;;
esac

echo "Packaging WorldPainter $APP_VERSION ($TYPE) for $OS..."
"$JPACKAGE" "${ARGS[@]}"
echo "Done: $(ls -d "$DEST"/WorldPainter* | tr '\n' ' ')"
