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
| **Annotations** | Coloured markings for planning the map, not exported unless you choose to |

Panels can be resized, grouped in tabs, detached into a window of their own, and shown again with **View › Panels**.

## Tools

The tools are grouped by what they do: **Sculpt terrain** (raise, lower, flatten, smooth, mountains, pyramids, paths), **Paint** (pencil, spray, fill, eyedropper, global operations, text), **Water** (flood with water or lava, sponge) and **Selection & more**. Hover over any tool to see its name, its shortcut and what it does.

## Brushes

A single **Brushes** panel with three basic shapes (soft round, plateau round, square) and nine **terrain stamps** generated procedurally: a peak, a range, a massif, a volcano, a crater rim, a mesa, dunes, hills and an eroded dome. Custom brushes (in the `brushes` folder of the configuration) are still supported.

## Canvas

The map sits on a dotted canvas instead of a dark blue background. On macOS the toolbar doubles as the title bar.
