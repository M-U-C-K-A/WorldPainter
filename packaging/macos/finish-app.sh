#!/usr/bin/env bash
#
# Finishes WorldPainter.app after jpackage has made it: declares the .world file type, adds the Quick Look extension
# which shows the map of a world as the icon of its file in the Finder (as Blender does for .blend files), and signs the
# application again, since adding to it breaks its signature.
#
# Usage: packaging/macos/finish-app.sh PATH/TO/WorldPainter.app VERSION
#
# Requirements: the Xcode command line tools (swiftc, lipo, codesign).

set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
APP="$1"
VERSION="$2"

# The .world file type, with a document icon which macOS makes from the application icon
/usr/libexec/PlistBuddy -c "Merge $HERE/document-types.plist" "$APP/Contents/Info.plist" >/dev/null

# The Quick Look extension, for both kinds of processor
EXTENSION="$APP/Contents/PlugIns/WorldPainterThumbnails.appex"
rm -rf "$EXTENSION"
mkdir -p "$EXTENSION/Contents/MacOS"
sed "s/@VERSION@/$VERSION/g" "$HERE/thumbnails/Info.plist" > "$EXTENSION/Contents/Info.plist"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT
SDK="$(xcrun --sdk macosx --show-sdk-path)"
for ARCH in arm64 x86_64; do
    # An extension has no main function of its own: NSExtensionMain starts it
    xcrun swiftc -O -module-name WorldPainterThumbnails -parse-as-library -application-extension \
        -target "$ARCH-apple-macos11" -sdk "$SDK" -Xlinker -e -Xlinker _NSExtensionMain \
        "$HERE/thumbnails/ThumbnailProvider.swift" -o "$BUILD/$ARCH"
done
lipo -create "$BUILD/arm64" "$BUILD/x86_64" -output "$EXTENSION/Contents/MacOS/WorldPainterThumbnails"

# Extensions must run in the sandbox. Signed ad hoc, as jpackage signs the application
codesign --force --sign - --entitlements "$HERE/thumbnails/entitlements.plist" "$EXTENSION"
codesign --force --sign - "$APP"
