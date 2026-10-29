---
title: Installation
description: Download WorldPainter Modernised for macOS, Windows or Linux, or build it from source.
---

# Installation

WorldPainter Modernised is a desktop application. The downloads include their own Java runtime, so there is nothing else to install.

## Download

The application is built automatically for every change to the code:

1. Open the latest successful run of the [Build workflow](https://github.com/M-U-C-K-A/WorldPainter/actions/workflows/build.yml) on GitHub.
2. Under **Artifacts**, download the one for your system:
   - **macOS Apple Silicon** or **macOS Intel**: a `.dmg` disk image
   - **Windows**: a `.zip` with the application folder
   - **Linux**: a `.deb` package
3. Install it as usual for your system.

> [!WARNING] The application is not signed yet. On macOS, open it the first time with right-click › **Open**, then confirm. On Windows, SmartScreen may ask you to confirm with **More info** › **Run anyway**.

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
