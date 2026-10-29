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

The palettes of terrain types, biomes and colours use the whole width of their panel, however wide or narrow it is, and the terrain types are grouped by family (grass and soil, stone, sand and desert, terracotta…) with a search field.

Panels can be resized, grouped in tabs, detached into a window of their own, and shown again with **View › Panels**.

## Tools

The tools are grouped by what they do: **Sculpt terrain** (raise, lower, flatten, smooth, mountains, pyramids, paths), **Paint** (pencil, spray, fill, eyedropper, global operations, text), **Water** (flood with water or lava, sponge) and **Selection & more**. Hover over any tool to see a card with a short looping animation of what it does, its shortcut and how to use it.

## Brushes

A single **Brushes** panel with six basic shapes and twenty-two **terrain stamps** generated procedurally, by family:

| Family | Stamps |
|---|---|
| Mountains | Peak, alpine peak, twin peaks, crag, mountain range, mountain chain, eroded ridge, massif, glacial cirque |
| Hills & plateaus | Hills, eroded dome, terraced hill, plateau, mesa, butte, escarpment, karst towers, dunes |
| Volcanoes & craters | Volcano, shield volcano, crater rim |
| Valleys | Valley, to carve with the lower tool (hold <kbd>Ctrl</kbd> with the height tool) |

Custom brushes (in the `brushes` folder of the configuration) are still supported.

## Canvas

The map sits on a dotted canvas instead of a dark blue background. On macOS the toolbar doubles as the title bar.
