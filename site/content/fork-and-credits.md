---
title: Fork and credits
description: WorldPainter Modernised is a fork of WorldPainter. Its relation to the original, its licence and its credits.
---

# Fork and credits

## A fork of WorldPainter

[WorldPainter](https://www.worldpainter.net) is the map editor created by Pepijn Schmitz (Captain-Chaos), developed since 2011 with many contributors. WorldPainter Modernised is a fork of it: it starts from [WorldPainter 2.27](https://github.com/Captain-Chaos/WorldPainter) and keeps everything it does (the painting tools, layers, custom objects, the export to every Minecraft version it supports) while adding the features described in this documentation.

It is not an official release of WorldPainter, and it is not supported by its author. Please report problems with this fork [here](https://github.com/M-U-C-K-A/WorldPainter/issues), not to the original project.

## Under the hood

- Java 21, and native applications built with jpackage for macOS, Windows and Linux
- The interface uses FlatLaf, with custom light and dark themes, Inter and Lucide icons
- The biomes Minecraft generates are computed by a Java port of Cubiomes
- The node graph, the 3D view, the paths and the floating islands are new code, in the `nodes`, `view3d`, `paths` and `vanilla` packages

## Licence

WorldPainter Modernised is free software, under the [GNU General Public Licence, version 3](https://github.com/M-U-C-K-A/WorldPainter/blob/master/LICENSE), like WorldPainter. If you distribute it, modified or not, you must also distribute its source code under the same licence.

## Credits

- **WorldPainter**: Pepijn Schmitz and [its contributors](https://github.com/Captain-Chaos/WorldPainter/graphs/contributors)
- **Cubiomes**: [Cubitect](https://github.com/Cubitect/cubiomes) (MIT), for the biome generation
- **FlatLaf**: [FormDev](https://www.formdev.com/flatlaf/) (Apache 2.0)
- **Lucide** icons ([ISC](https://lucide.dev/license))
- **Inter** and **Geist** typefaces (SIL Open Font Licence)

Minecraft is a trademark of Mojang. WorldPainter Modernised is not affiliated with Mojang or Microsoft.
