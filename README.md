<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/assets/hero-dark.svg">
  <img alt="WorldPainter, Minecraft map editor, modernised edition" src="docs/assets/hero-light.svg" width="100%">
</picture>

<p align="center">
  <b>Paint complete Minecraft worlds, faster.</b><br>
  An integrated 3D view, a preview of the land around the map, seed search, soft map edges and a light and dark interface, as a native application for macOS, Windows and Linux.
</p>

<p align="center">
  <a href="https://github.com/M-U-C-K-A/WorldPainter/actions/workflows/build.yml"><img alt="Build" src="https://github.com/M-U-C-K-A/WorldPainter/actions/workflows/build.yml/badge.svg"></a>
  <img alt="Minecraft 26.x" src="https://img.shields.io/badge/Minecraft-1.20.5_%E2%86%92_26.x-3d9a50">
  <img alt="Java 21" src="https://img.shields.io/badge/Java-21-f97316">
  <img alt="Platforms" src="https://img.shields.io/badge/macOS_%C2%B7_Windows_%C2%B7_Linux-native-18181b">
  <a href="LICENSE"><img alt="GPL v3 licence" src="https://img.shields.io/badge/licence-GPL_v3-blue"></a>
</p>

<p align="center">
  <b><a href="https://m-u-c-k-a.github.io/WorldPainter/">Documentation</a></b> ·
  <a href="https://m-u-c-k-a.github.io/WorldPainter/tutorial-first-archipelago.html">First tutorial</a> ·
  <a href="#overview">Overview</a> ·
  <a href="#integrated-3d-view">3D view</a> ·
  <a href="#around-the-map">Around the map</a> ·
  <a href="#seeds">Seeds</a> ·
  <a href="#interface">Interface</a> ·
  <a href="#brushes-and-terrain-stamps">Brushes</a> ·
  <a href="#edge-blending">Edges</a> ·
  <a href="#procedural-generation">Procedural</a> ·
  <a href="#install">Install</a>
</p>

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/assets/divider-dark.svg">
  <img alt="" src="docs/assets/divider-light.svg" width="100%">
</picture>

## Overview

> The full documentation (features, nodes, step by step tutorials, roadmap) is at **[m-u-c-k-a.github.io/WorldPainter](https://m-u-c-k-a.github.io/WorldPainter/)**; its sources are in [`site/`](site/).

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/images/screenshot-dark.png">
  <img alt="The WorldPainter editor, with the biomes Minecraft will generate around the map shown in transparency" src="docs/images/screenshot-light.png" width="100%">
</picture>

WorldPainter lets you "paint" Minecraft landscapes with tools much like those of a drawing program: sculpt the relief, paint the materials, the trees, the snow, the biomes… then export a playable map. This fork starts from the latest version of the original project, [Captain-Chaos/WorldPainter](https://github.com/Captain-Chaos/WorldPainter) 2.27, compatible with **Minecraft 1.20.5 to 26.x**. It makes it easier to pick up and adds what was missing to create a complete map quickly: seeing the result in 3D, knowing what will lie around it, and choosing the right seed.

This is an unofficial fork, not affiliated with or supported by the original project: please report problems [here](https://github.com/M-U-C-K-A/WorldPainter/issues).

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/assets/divider-dark.svg">
  <img alt="" src="docs/assets/divider-light.svg" width="100%">
</picture>

## Integrated 3D view

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/assets/view3d-dark.svg">
  <img alt="Block terrain with the display modes and the details of the block under the cursor" src="docs/assets/view3d-light.svg" width="560">
</picture>

The **Map / 3D** buttons at the top right switch the workspace between the map and a 3D view of the dimension, in the same window.

- **Details of the block under the cursor**: X / Z position, Y height, slope, terrain, biome (painted or automatic), water depth and the layers present.
- **5 display modes**: *Terrain* (block colours), *Height* (altitude gradient), *Slope* (from blue for flat ground to red for slopes of 60° and more), *Biome* and *Relief* (shading and contour lines), each with its legend.
- **Water** and **contour lines** can be switched on, and the **relief exaggeration** adjusted.
- True **perspective** or **orthographic** projection (no distortion, so distances stay comparable), low, high or **top down** view facing north.
- Navigation: drag to rotate, <kbd>⇧</kbd> + drag to pan, scroll to zoom, double-click to centre on a point; buttons to rotate and to fit the view.
- The map is shown as a model on a base, rendered as a 3D mesh on all cores (about 10 ms per frame); very large maps are simplified to stay smooth.

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/images/view3d-dark.png">
  <img alt="The 3D view in Terrain mode" src="docs/images/view3d-light.png" width="100%">
</picture>

<img alt="The 3D view in Slope mode: steep slopes in red" src="docs/images/view3d-slope.png" width="100%">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/assets/divider-dark.svg">
  <img alt="" src="docs/assets/divider-light.svg" width="100%">
</picture>

## Around the map

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/assets/surroundings-dark.svg">
  <img alt="The map surrounded, in transparency, by the biomes Minecraft will generate" src="docs/assets/surroundings-light.svg" width="560">
</picture>

**Surroundings** panel, **Show what lies beyond the map** checkbox. Around the map, the biomes Minecraft will generate for the world seed are shown in transparency: a desert on the left, plains at the top, an ocean on the right… You know in advance what the edges of the map will run into.

- Adjustable **margin** of 64 to 1024 blocks around the map (300 by default: a 200 × 200 map is shown up to 800 × 800).
- The borders between biomes are drawn, and the status bar shows the biome under the cursor ("Minecraft will generate: River").
- The biomes are computed by a Java port of the generation of [Cubiomes](https://github.com/Cubitect/cubiomes), checked to be identical on thousands of points. Available for the surface of Minecraft 1.18 and later maps, with the *Default* or *Large Biomes* world type.

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/assets/divider-dark.svg">
  <img alt="" src="docs/assets/divider-light.svg" width="100%">
</picture>

## Seeds

The world seed can be changed directly in the **Surroundings** panel (a number, or any text as in Minecraft), with two buttons:

- **Random seed**: picks a new seed; the preview around the map updates straight away.
- **Find a matching seed…**: looks for a seed for which the land generated around the map matches what is painted along its edges. A spruce forest at the bottom left and water on the right? The search aims for a taiga at the bottom left and an ocean on the right. Up to a million seeds are tested in parallel; the best 8 are shown with their match percentage and a preview, and if there is no perfect match you simply pick the closest.

<img alt="The seed search: the best candidates and a preview of the biomes around the map" src="docs/images/seed-search.png" width="680">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/assets/divider-dark.svg">
  <img alt="" src="docs/assets/divider-light.svg" width="100%">
</picture>

## Interface

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/assets/interface-dark.svg">
  <img alt="Window with a layer panel, a dotted canvas and a tooltip" src="docs/assets/interface-light.svg" width="560">
</picture>

- **Self-explaining panels**: *Terrain* (the block type of the ground), *Layers* (what goes on top: trees, snow, ores, caves…), *Biomes* and *Annotations* (coloured markings to plan the map, not exported unless enabled on export) each have a header saying what they are for.
- **Light and dark themes** inspired by shadcn/ui (zinc palette, orange accent, thin separators), the **Inter** font, and an **automatic mode which follows the system**. Switch on the fly with the sun/moon button in the toolbar.
- **[Lucide](https://lucide.dev) icons** for the toolbar, the tools, the layers and the panels, and a new application icon.
- **Explanatory cards** when hovering over each tool (name, shortcut, explanation) instead of the old yellow tooltips.
- A **dotted grid canvas** around the map.
- **Dockable panels**, resizable in both directions, grouped in compact tabs, which can be detached into a window, folded away on either side, and shown again with **View › Panels**.
- On macOS, the toolbar doubles as a **unified title bar**.

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/assets/divider-dark.svg">
  <img alt="" src="docs/assets/divider-light.svg" width="100%">
</picture>

## Brushes and terrain stamps

A single **Brushes** panel, with no hidden menu:

- **3 basic shapes**: soft round, flat round and square.
- **Procedurally generated terrain stamps**, to sculpt in one stroke with the height tools: peak, mountain range, massif, volcano, crater rim, mesa, dunes, hills, eroded dome…
- Custom brushes (the `brushes` folder of the configuration) are still supported.

### Node editor

**Generate › Node graph terrain…** (<kbd>⇧</kbd> <kbd>⌘</kbd> <kbd>N</kbd>), in the spirit of Gaia or World Machine: connect nodes on a canvas and get a complete map, relief, terrain, biomes and vegetation included.

- **Generators**: Perlin noise, ridged noise, billow noise, dunes, cells (Voronoi), gradient (radial, square or linear, fitted to the map), constant, imported greyscale image and **current map** (to rework a hand-painted relief).
- **Combinations**: add, subtract, multiply, maximum, minimum, blend with a mask. **Filters**: editable curve, terraces, clamp, blur, erosion (branching gullies running down the slopes) and thermal erosion.
- **Water**: winding **rivers** (constant width, carved bed, sloping banks, water at the level of the valley floor, river or frozen river biome, fading out above a chosen altitude) and **lakes** with irregular shores, filled to just below the lowest point of their rim. The bottom (gravel, sand…) is painted automatically.
- **Masks**: height range, slope range and selection. When not connected, masks read the final terrain.
- **Surface**: paint a terrain (rock, sand, snow, terracotta…), a Minecraft **biome** (43 biomes) or **vegetation** (deciduous, pine and jungle forests, swamp, snow, grass and flowers, ferns, desert plants, mushrooms). The strongest mask wins, with natural speckled borders.
- Each node shows a **thumbnail**; preview the **relief**, the **surface**, the **biomes** or the selected node.
- Comfortable editing: **undo / redo**, **multiple selection** (<kbd>⇧</kbd> click, <kbd>⇧</kbd> drag, <kbd>⌘</kbd> <kbd>A</kbd>), copy, paste and duplicate, **opening and saving graphs** (`.wpgraph`) to reuse them from one world to the next.
- Apply to the whole map or to the **selection with soft edges**, with undo; the graph is **saved with the world**.
- **Examples** of complete maps: hills, eroded mountains, tropical archipelago, mesa canyons, desert dunes, volcanic island, lakes and forests, valleys and rivers, and "erode the current map". Evaluated in parallel tiles, identical to the bit to a computation in one piece: no seams.

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/assets/divider-dark.svg">
  <img alt="" src="docs/assets/divider-light.svg" width="100%">
</picture>

## Edge blending

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/assets/edges-dark.svg">
  <img alt="The edge of the map slopes down gently instead of forming a cliff" src="docs/assets/edges-light.svg" width="560">
</picture>

**Generate › Blend map edges…**. A 200 × 200 map no longer ends in a sheer cliff: its relief goes over gradually into the land around it.

- **Transition zone** of 16 to 4096 blocks, outside the map (which is then enlarged), inside it (the edge is softened) or across the edge. Round maps are blended too.
- **Target**: a procedural terrain (*Minecraft-like* by default, close to what Minecraft generates beyond the map) or a flat level.
- Adjustable curve shape and **irregularity**; Minecraft then joins up the last few blocks itself with its *chunk blending*.

<img alt="Before and after blending the edges" src="docs/images/edge-blending.png" width="100%">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/assets/divider-dark.svg">
  <img alt="" src="docs/assets/divider-light.svg" width="100%">
</picture>

## Procedural generation

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/assets/procedural-dark.svg">
  <img alt="Stacked layers of noise forming a terrain" src="docs/assets/procedural-light.svg" width="460">
</picture>

**Generate › Procedural terrain…** (<kbd>⇧</kbd> <kbd>⌘</kbd> <kbd>G</kbd>) to lay down a base relief before painting:

- a stack of noise layers to combine (gentle relief, ridges, hills, cells, dunes), with terraces, sea level and surface rules (snow up high, rock on the slopes, beaches);
- 8 presets, a live preview, and applying to the whole map or to the selection with soft edges;
- computed in parallel on all cores, with no seams between tiles, suited to very large maps.

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/assets/divider-dark.svg">
  <img alt="" src="docs/assets/divider-light.svg" width="100%">
</picture>

## Install

Download the latest version from the [Releases](https://github.com/M-U-C-K-A/WorldPainter/releases) page: a native application per platform (macOS Apple Silicon or Intel, Windows, Linux), with Java included and nothing else to install. The untested build of each change is also available in the [Actions](https://github.com/M-U-C-K-A/WorldPainter/actions/workflows/build.yml) tab.

The application is not signed: on macOS, open it the first time with right-click › Open. It keeps its settings apart from those of the original WorldPainter, so both can be installed side by side.

The application can use **up to 75% of the memory** of the machine (instead of Java's default of 25%), which allows working on maps about three times larger.

## Build

All you need is **JDK 21** and **Maven**; all dependencies are downloaded automatically. Details in [BUILDING.md](BUILDING.md).

```bash
cd WorldPainter
mvn -DskipTests install       # build
mvn -pl WPGUI exec:exec       # run
```

Create the native application for the current platform (in `dist/`):

```bash
packaging/package.sh          # macOS: dist/WorldPainter.app
packaging/package.sh dmg      # disk image to distribute
```

The illustrations in this README are generated by [`docs/assets/make_svgs.py`](docs/assets/make_svgs.py).

## Licence and credits

WorldPainter is free software under the **GNU General Public License version 3**. If you distribute it, as is or modified, you must also distribute the source code (with your changes) under the GPL version 3 or later. See [LICENSE](LICENSE).

- WorldPainter was created by [Pepijn Schmitz (Captain-Chaos)](https://github.com/Captain-Chaos) and its contributors.
- Minecraft biome generation ported from [Cubiomes](https://github.com/Cubitect/cubiomes) (MIT licence).
- [Lucide](https://lucide.dev) icons (ISC licence), [FlatLaf](https://www.formdev.com/flatlaf/) themes (Apache 2.0), [Inter](https://rsms.me/inter/) font (SIL OFL).

[![Contributors](https://contrib.rocks/image?repo=Captain-Chaos/WorldPainter)](https://github.com/Captain-Chaos/WorldPainter/graphs/contributors)
