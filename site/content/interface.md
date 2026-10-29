---
title: Interface
description: The redesigned interface of WorldPainter Modernised.
---

# Interface

![The editor in the light theme](assets/images/screenshot-light.png)

## Themes

Light and dark themes in the style of shadcn/ui (zinc greys, an orange accent, rounded corners, hairline separators), with the Inter typeface. The automatic mode follows the system; the sun and moon button in the toolbar switches theme at once.

## Panels

The panels on the left explain themselves in their header:

| Panel | Contains |
|---|---|
| **Terrain** | The block type of the ground surface: grass, sand, stone, snow… |
| **Layers** | What is added on top of or under the terrain: trees, caves, ores, frost… with an eye to show or hide each one |
| **Biomes** | The Minecraft biome of each area, chosen automatically from the terrain unless you paint one |
| **Packs** | Ready made trees, flowers, plants and mixes of blocks: click one to add it and paint with it (see [Trees and materials](trees-and-materials.html#the-packs-panel)) |
| **Annotations** | Coloured markings for planning the map, not exported unless you choose to |

The palettes of terrain types, biomes and colours use the whole width of their panel, however wide or narrow it is, and the terrain types are grouped by family (grass and soil, stone, sand and desert, terracotta…) with a search field. The Terrain panel also lists the [material mixes](trees-and-materials.html#material-mixes) (grass, dirt, stone, sand, cliff mixes…): click one to add it as a custom terrain type and paint with it.

Hover over a mix of blocks or a plant layer to see what it is made of: each block or plant with its icon and its share.

Panels can be resized, grouped in tabs, detached into a window of their own, and shown again with **View › Panels**.

To make room for the map, the buttons at either end of the toolbar (two panel icons) fold away all the panels on the left or on the right, and bring them back. Showing a panel from **View › Panels** unfolds its side again.

## Tools

The tools are grouped by what they do: **Sculpt terrain** (raise, lower, flatten, smooth, mountains, pyramids, paths), **Paint** (pencil, spray, fill, eyedropper, global operations, text), **Water** (flood with water or lava, sponge) and **Selection & more**. Hover over any tool to see a card with a short looping animation of what it does, its shortcut and how to use it.

To select an area, **Select rectangle** works as in an image editor: drag across the map to add a rectangle to the selection, with the right mouse button to remove one; hold <kbd>Shift</kbd> for a square and <kbd>Ctrl</kbd> for an ellipse. **Edit selection** paints the selection with the brush instead. Tick *inside selection* in the options of a tool to limit it to the selection.

## Brushes

A single **Brushes** panel with six basic shapes and twenty-two **terrain stamps** generated procedurally, by family:

| Family | Stamps |
|---|---|
| Mountains | Peak, alpine peak, twin peaks, crag, mountain range, mountain chain, eroded ridge, massif, glacial cirque |
| Hills & plateaus | Hills, eroded dome, terraced hill, plateau, mesa, butte, escarpment, karst towers, dunes |
| Volcanoes & craters | Volcano, shield volcano, crater rim |
| Valleys | Valley, to carve with the lower tool (hold <kbd>Ctrl</kbd> with the height tool) |

Hover over a brush to see its shape in 3D, turning slowly on a block of ground. On the map the outline of a brush shows its actual shape, rotation included. Rotate it with <kbd>[</kbd> and <kbd>]</kbd> (or <kbd>Alt</kbd>+scroll), and zoom the map with <kbd>Ctrl</kbd>+scroll or <kbd>⌘</kbd>+scroll. Custom brushes (in the `brushes` folder of the configuration) are still supported.

## Canvas

The map sits on a dotted canvas instead of a dark blue background. On macOS the toolbar doubles as the title bar.
