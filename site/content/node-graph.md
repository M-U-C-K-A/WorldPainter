---
title: Node graph
description: Generate complete Minecraft worlds by wiring nodes together, with a live preview.
---

# Node graph

**Generate › Node graph terrain…** (<kbd>⇧</kbd> <kbd>⌘</kbd> <kbd>N</kbd>) opens the node editor, in which you build a world by wiring nodes together: noise for the shape of the land, filters and erosion to sculpt it, rivers and lakes, masks to select parts of it, and painters for the terrain types, biomes and vegetation. Everything is generated together when you click **Apply to map**.

![The node editor with the Simple archipelago example](assets/images/editor-simple-archipelago.png)

## How it works

Every node produces a value for every block of the map, from 0 to 1, shown as a greyscale thumbnail: black is 0, white is 1. Nodes pass their values along the wires, from their output (on the right) to the inputs of other nodes (on the left).

- The **Output** node turns the value it receives into the height of the land: **Height at 0** for black, **Height at 1** for white. With 22 and 152, for instance, a value of 0.31 is Y 62, the sea level.
- **Masks** select parts of the land, for instance everything between Y 59 and Y 65, or everything steeper than 40°. They usually read the final terrain, so that what you paint follows what you generate.
- **Painters** have no output: wherever their mask is strong they paint a terrain type, a material mix, a biome or vegetation. Where painters overlap, the strongest wins.

The value of a node depends only on where it is on the map, so the whole map is generated tile by tile, in parallel, without seams. A 5000 × 5000 map takes about 20 seconds.

## The editor

| | |
|---|---|
| Right-click or double-click the canvas | Add a node |
| Drag from an output to an input | Connect two nodes; drag from an output into the empty canvas to add a node connected to it |
| Shift-drag | Select several nodes |
| Scroll, drag the canvas | Zoom and move around |
| <kbd>⌘</kbd> <kbd>Z</kbd> / <kbd>⇧</kbd> <kbd>⌘</kbd> <kbd>Z</kbd> | Undo and redo |
| <kbd>⌘</kbd> <kbd>C</kbd>, <kbd>⌘</kbd> <kbd>V</kbd>, <kbd>⌘</kbd> <kbd>D</kbd>, <kbd>Delete</kbd> | Copy, paste, duplicate and delete nodes |
| <kbd>F</kbd> | Fit the whole graph in view |

Select a node to change its settings on the right. The preview above shows the result as **Relief**, **Surface** (terrain types, water and vegetation), **Biomes**, or the output of the selected **Node**, for the whole map or a part of it.

The toolbar starts a **New empty graph**, opens and saves graphs as `.wpgraph` files (to reuse them in other worlds), loads the **Examples**, and sets the **Seed**: the same graph with another seed gives another world of the same kind.

At the bottom:

- **Mode** replaces the land, adds to it, or only raises or lowers it; **Strength** blends the result with the existing land.
- **Whole map** or **Selection, feathered**: apply to the selected area only, with soft edges.
- **Repaint from theme** paints the terrain types of the map's theme first, under the painters of the graph.
- **Paint surface** turns the painters on or off.

## Nodes

### Generators

| Node | Makes |
|---|---|
| Perlin noise | Smooth fractal noise: rolling hills, continents |
| Ridged noise | Sharp ridges: mountain ranges |
| Billow noise | Rounded, puffy hills |
| Cells (Voronoi) | Mounds, pits or ridges along the edges of cells |
| Dunes | Parallel wavy ridges |
| Rock towers | Tall rock pillars with steep, fluted walls (tower karst, sea stacks) |
| Volcano | A volcano with a crater and furrowed flanks, at a position on the map |
| Plateaus | Flat topped table mountains in levels, bounded by cliffs |
| Gradient | A radial, square or linear gradient, for instance to make an island |
| Constant | The same value everywhere |
| Image | An imported greyscale height map |
| Current map | The map as it is now, to build on terrain painted by hand |

Noise nodes have a size (the scale of their features), a number of octaves (detail), a warp (to make them less regular) and a **Variant**: change it to get a different pattern with the same settings.

### Combiners

**Add**, **Subtract**, **Multiply** (use B as a mask for A), **Maximum**, **Minimum**, and **Blend with mask** (A where the mask is black, B where it is white). Each has a strength, which scales the effect of input B.

### Filters

| Node | Does |
|---|---|
| Curve | Reshape the values with a curve: the most useful node of all, to turn noise into a sea floor, coasts and mountains |
| Terrace | Flat terraces separated by steep steps |
| Strata | Ledges and bands of rock of uneven thickness which dip across the map, as in canyon walls |
| Clamp | Limit the values to a range |
| Blur | Smooth the terrain |
| Erosion | Branching gullies and valleys carved down the slopes |
| Thermal erosion | Steep slopes crumble into scree |

### Water

**Rivers** carves a network of winding rivers, with banks, and fills them with water; **Lakes** carves scattered lakes with irregular shores. Both paint their bed and their biome, can be limited to a range of heights, and are filled with water only where they are part of the final terrain.

### Masks

| Node | Selects |
|---|---|
| Height range | The land between two heights, with a fade (beaches, snow lines, the sea floor) |
| Slope range | The land by steepness (rock on cliffs, trees on gentle slopes) |
| Regions | Large irregular regions of several types: use several Regions nodes with the same settings and a different type to divide the map into biome zones |
| Selection | The area selected on the map |

### Surface

**Paint terrain**, **Paint material mix** (see [material mixes](trees-and-materials.html#material-mixes)), **Paint biome** and **Paint flora** (forests, grass, flowers, snow, and the trees of the [tree pack](trees-and-materials.html#tree-pack)). Painters without a mask paint everywhere, at their strength.

### Structures

**Floating islands** builds islands in the sky: see the [tutorial](tutorial-floating-islands.html).

## Examples

The **Examples** menu has complete worlds to start from or learn from: rolling hills, eroded mountains, a tropical archipelago, mesa canyons, desert dunes, a volcanic island, lakes and forests, river valleys, tower karst jungle, red cliffs and spruces, an alpine valley, the continent and the coral archipelago shown on the [home page](index.html), the beginner's archipelago, floating islands, and one which erodes the current map.

## Procedural terrain

**Generate › Procedural terrain…** (<kbd>⇧</kbd> <kbd>⌘</kbd> <kbd>G</kbd>) is a simpler alternative to the node graph: a stack of noise layers (smooth relief, ridges, hills, cells, dunes) with terraces, a sea level and surface rules (snow up high, rock on steep slopes, beaches by the water), eight presets and a live preview.
