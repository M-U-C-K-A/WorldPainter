---
title: Interface
description: The redesigned interface of WorldPainter Modernised.
---

# Interface

![The editor in the light theme](assets/images/screenshot-light.png)

## Themes

Two designs, each light, dark or following the system, with the Geist typeface:

| Design | Looks like |
|---|---|
| **Modern** | A window of soft grey with raised surfaces, a blue accent, softly rounded controls, tabs as a segmented control, line icons, and settings grouped on rounded surfaces with their controls on the right, as in the settings of macOS |
| **Classic** | White, in the style of shadcn/ui: zinc greys, an orange accent, hairline separators, and settings in boxes |

Choose them in **Preferences › General › Theme**: the change shows at once, without restarting, and **Cancel** takes it back. The sun and moon button in the toolbar switches between light and dark.

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

Panels can be resized, grouped in tabs, detached into a window of their own, and shown again with **View › Panels**. Right click on the title or the tab of a panel to move it to the other side or to the bottom, below the map.

To make room for the map, the panel buttons in the toolbar fold away all the panels on the left, at the bottom or on the right, and bring them back. Showing a panel from **View › Panels** unfolds its side again.

## Workspaces

The workspace button in the toolbar, next to *Map* and *3D*, arranges the panels for a kind of work, and keeps their sizes:

| Workspace | Shows |
|---|---|
| **Default** | All the panels, as when WorldPainter starts |
| **Terraforming** | The tools and the brushes, to shape the land, without the palettes |
| **Painting** | Terrain, layers and biomes next to the tools and the brushes |
| **Node graph** | Opens the [node graph](node-graph.html), with the layers, the surroundings and the info next to the map |
| **Minimal** | Only the map, with all the panels folded away |

The same workspaces are in **View › Workspace layout**, where the arrangement of your own can also be saved and loaded again.

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

## Where a brush applies

The brush settings (intensity, rotation, brush and options) stay as they are when you switch between tools, and when you paint with a different material.

The **Options** can limit a brush in several independent ways:

- **Where on the map** limits it to what you selected on the map with a selection tool, or to everything else. With *Inside selection* and nothing selected, the brush paints nowhere, and the panel says so.
- The **height limits** limit it to certain heights, shown on a scale with a little landscape: the land in the accent colour is the land the brush will change.

- **What is on the map** limits it to certain terrain types or layers (*only on*, *except on*), or to steep or gentle slopes. You can choose as many terrain types and layers as you like, with **Add another**: to turn grass and stone into dirt, paint dirt *only on* grass and stone.

For the height limits:

- Drag the bars to set the limits, which also switches them on, or type them in the *at or above* and *at or below* fields. A value you type counts as soon as you click elsewhere, without pressing Enter. *Fade at the limits* lets the brush fade over the 4 blocks beyond them, instead of stopping abruptly.
- **Between** applies between the two bars; **Beyond** applies below the lower one and above the upper one. Click the highlighted button again to switch the limits off. 
- Ctrl+Z (⌘Z on macOS) takes back changes to these options, and Ctrl+Y brings them back, as long as you have not changed the map since. After that they undo and redo changes to the map as usual.

## Canvas

The map sits on a dotted canvas instead of a dark blue background. On macOS the toolbar doubles as the title bar.

## Settings windows

In the Classic design the settings windows (Preferences, Edit Surface Properties, Export, New World…) open with a banner: a picture of what they are about, a sentence explaining them and, for windows with tabs, what the selected tab contains. The Modern design leaves the banner out and starts with the settings, and replaces the old coloured icons with line icons in the colours of the theme. **Preferences** also has an **Apply** button, which puts the changes into effect without closing the window.

Their settings are grouped in cards, each with a title and a sentence about what it does. Badges point out the settings which only apply after a restart, after reloading the map, on the next export or to new worlds, and those which are experimental or can be slow.

![Edit Surface Properties in the Modern design](assets/images/settings-windows.png)
