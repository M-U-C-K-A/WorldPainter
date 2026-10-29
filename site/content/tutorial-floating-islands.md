---
title: Floating islands
description: Build islands floating in the sky with the Floating islands node.
---

# Floating islands

The **Floating islands** node builds islands in the sky: wherever its **Shape** input is above one half there is an island, with its top at the height of its **Top** input and an underside which deepens like an upside down cone towards the middle, with craggy rock. In this tutorial you put a sky full of them over a meadow, with 11 nodes.

![Floating islands over meadows, seen from above with their shadows](assets/images/floating-islands.jpg)

> [!TIP] The finished graph is under **Examples › Floating islands**, or [download it](assets/graphs/floating-islands.wpgraph).

## 1. The ground

Start a **New empty graph**. For gentle meadows: a **Perlin noise** (size 900) into the Output, with **Height at 0** at 64 and **Height at 1** at 102. Add a **Paint flora** node with *Grass and flowers* at density 0.4 and no mask: grass and flowers everywhere.

## 2. The outlines of the islands

Islands are round blobs with ragged edges:

1. A **Cells (Voronoi)** node in *Mounds* mode, with a cell size of 340 and a warp of 60: one smooth mound per cell.
2. A small **Perlin noise** (size 60), to break the outlines up.
3. **Add** them, with the cells in A and the small noise in B, at a strength of 0.2.
4. A **Curve** which turns the tops of the mounds into islands: points (0, 0), (0.8, 0), (0.9, 1), (1, 1). Everything below 0.8 is sky; the rest is island.

## 3. The heights of the islands

Each island should float at its own height. **Add** a large **Perlin noise** (size 700) and the Cells node again, at a strength of 0.5: the islands are higher in the middle and vary from one to the next.

## 4. The Floating islands node

Add a **Floating islands** node (right-click › Structures). Connect the Curve of step 2 to **Shape** and the Add of step 3 to **Top**.

![The finished graph, with the floating islands in the preview](assets/images/editor-floating-islands.png)

| Setting | Value | |
|---|---|---|
| Top at 0 / Top at 1 | 142 / 212 | The height of the tops of the islands for a Top value of 0 and of 1 |
| Thickness | 36 | How far the underside reaches below the top, in the middle |
| Taper | 40 | How far in from the edge the underside reaches its full depth: small for flat slabs, large for cones |
| Roughness | 0.5 | How craggy the underside is |
| Terrain | Grass | The terrain type on top |
| Vegetation, Density | Deciduous forest, 0.4 | The trees on top; a density of 0 for none |

The preview draws the islands over the land, with their shadows.

## 5. Apply and export

Click **Apply to map**. The islands become a *Floating islands* layer: its footprint is painted on the map, and the heights of the tops are kept in a floating dimension of their own. Right-click the layer in the Layers panel to edit it (its thickness, its underside, its edges) or the dimension of its tops.

Export as usual: in Minecraft, the islands have grass and trees on top and stone and dirt below, with nothing but air between them and the ground.

> [!NOTE] The 3D view does not show floating islands yet, and undoing the graph does not remove the layer it created.

## Ideas

- Feed **Shape** from a **Regions** mask to keep the islands over one part of the map.
- Use **Snow and ice** as the vegetation, and *Snow* as the terrain, for frozen islands.
- Several Floating islands nodes make several layers, at different heights.
