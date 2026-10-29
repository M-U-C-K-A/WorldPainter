---
title: Surroundings and seeds
description: See the land Minecraft generates around your map, find a matching seed and blend the edges into it.
---

# Surroundings and seeds

Beyond the edges of your map, Minecraft generates its own land from the world seed. WorldPainter Modernised shows you what that land will be, so that you can choose a seed which suits the map, and join the two.

These features are in the **Surroundings** panel (on the right, next to Brush Settings). They work for the surface of Minecraft 1.18 and later maps with the *Default* or *Large Biomes* world type.

## See what lies beyond the map

Tick **Show what lies beyond the map**: the land Minecraft will generate around the map is drawn around it like the map itself, with the same terrain colours, relief shading and contour lines, fading out up to the distance set by **Margin** (64 to 1024 blocks; 300 by default). The status bar names the biome under the mouse: *Minecraft will generate: River*.

The biomes are computed with a Java port of [Cubiomes](https://github.com/Cubitect/cubiomes), checked to give exactly the same biomes as Minecraft on thousands of locations.

## Change the seed

Type a number, or any text (as in Minecraft), in the **World seed** field and press <kbd>Enter</kbd>. The button next to it picks a random seed. The land around the map updates straight away.

## Find a matching seed

**Find a matching seed…** looks for a seed for which the land around the map matches what you painted along its edges: a taiga where the map has spruce forest at its edge, an ocean where it has water, and so on.

![The seed search: the best seeds found, and a preview of the land around the map for the selected one](assets/images/seed-search.png)

- The search runs on half of the cores, so that the computer stays usable, and goes on until you click **Stop**. The status line shows how many seeds have been tested, and how fast.
- The eight best seeds are listed with how well they match (as a percentage), with the current seed for comparison. Select one to see the land around the map.
- If no seed matches perfectly, choose the closest: the next step makes up the difference.

> [!TIP] Minecraft's biomes are large. A small map whose edges change biome every hundred blocks will rarely match above 70 %; letting the search run longer helps, and so does blending the edges.

## Blend the edges into the seed

**Blend edges into this seed…** (or the option at the bottom of the seed search) adds a band of land around the map, 100 blocks wide by default, which goes over smoothly into the land Minecraft generates for the seed: its approximate height, its biomes and a matching surface (sand on beaches, gravel under the sea, podzol in taiga…). The terrain of the map and that of the seed mix block by block across the band, more and more towards the seed, rather than meeting along a line, and trees like those of the seed's forests thin out towards the map. Minecraft then blends the last few blocks itself when it generates the chunks around the map.

The band follows the actual edge of the land, also on round maps, whose corners are filled in. <kbd>⌘</kbd> <kbd>Z</kbd> (<kbd>Ctrl</kbd> <kbd>Z</kbd>) undoes the whole blend, including the land it added, and redo puts it back.

> [!NOTE] The height of the land beyond the map is an estimate from the same climate and terrain spline Minecraft uses, without its fine noise: the large shapes (oceans, coasts, plains, mountains) match, the block by block relief does not.
