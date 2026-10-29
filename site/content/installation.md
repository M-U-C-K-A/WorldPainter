---
title: Installation
description: Download WorldPainter Modernised for macOS, Windows or Linux, or build it from source.
---

# Installation

WorldPainter Modernised is a desktop application. The downloads include their own Java runtime, so there is nothing else to install.

## Download

Download the latest version from the [Releases page](https://github.com/M-U-C-K-A/WorldPainter/releases) on GitHub. Under **Assets**, pick the file for your system:

| System | File |
|---|---|
| macOS, Apple Silicon | `WorldPainter-Modernised-…-macos-arm64.dmg` |
| macOS, Intel | `WorldPainter-Modernised-…-macos-x64.dmg` |
| Windows (x64) | `WorldPainter-Modernised-…-windows-x64.zip`: unzip it and run `WorldPainter.exe` |
| Linux (x64, Debian and Ubuntu) | `WorldPainter-Modernised-…-linux-amd64.deb` |

On macOS, open the disk image and drag **WorldPainter** to **Applications**. On Linux, install the package with `sudo apt install ./WorldPainter-Modernised-…-linux-amd64.deb`.

> [!WARNING] The application is not signed yet. On macOS, open it the first time with right-click › **Open**, then confirm; if macOS says it is damaged, run `xattr -dr com.apple.quarantine /Applications/WorldPainter.app` in Terminal. On Windows, SmartScreen may ask you to confirm with **More info** › **Run anyway**.

> [!NOTE] WorldPainter Modernised keeps its settings apart from the original WorldPainter, so both can be installed side by side. Worlds saved with it may not open in the original WorldPainter: keep backups of the worlds you care about.

The latest, untested build of every change is also available from the [Build workflow](https://github.com/M-U-C-K-A/WorldPainter/actions/workflows/build.yml): open its latest successful run and download the file for your system under **Artifacts**.

## Requirements

| | |
|---|---|
| Systems | macOS (Apple Silicon or Intel), Windows, Linux (x64) |
| Memory | Generating a 5000 × 5000 map with the node graph uses about 5 GB; 16 GB of memory is comfortable for maps of that size |
| Minecraft | Java Edition 1.20.5 to 26.x |

The application may use up to **75 % of your memory** (Java's default is 25 %), which lets you work on maps about three times as large.

## Build from source

You need **JDK 21** and **Maven**; everything else is downloaded automatically.

```bash
git clone https://github.com/M-U-C-K-A/WorldPainter.git
cd WorldPainter/WorldPainter
mvn -DskipTests install       # build
mvn -pl WPGUI exec:exec       # run
```

To build the native application for your system, in `dist/`:

```bash
packaging/package.sh          # macOS: dist/WorldPainter.app
packaging/package.sh dmg      # a disk image to share
```

See [BUILDING.md](https://github.com/M-U-C-K-A/WorldPainter/blob/master/BUILDING.md) for the details.

## Next

[Your first archipelago](tutorial-first-archipelago.html) shows the whole workflow, from an empty world to a map you can play.
