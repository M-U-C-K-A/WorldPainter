---
title: Edge blending
description: Blend the edges of the map into the land around it, so that it does not end in a cliff.
---

# Edge blending

A map of 200 × 200 blocks used to end in a sheer cliff where Minecraft's own land begins. **Generate › Blend map edges…** makes the relief of the map go over gradually into the land around it.

![Before and after blending the edges of a map](assets/images/edge-blending.jpg)

| Option | |
|---|---|
| **Transition width** | 16 to 4096 blocks |
| **Transition zone** | Outside the map (which is extended with new tiles), inside it (the edge is softened), or across the edge |
| **Shape** | The curve of the transition |
| **Irregularity** | Makes the transition wander instead of running exactly parallel to the edge |
| **Blend into** | Generated terrain, from one of eight presets (*Minecraft-like* by default, close to what Minecraft generates beyond the map), or a flat level |

Round maps are blended too: the land outside the circle, which WorldPainter marks as void block by block and as not present chunk by chunk, is filled in with the blend, so no gaps are left in the corners of the chunks along the edge.

WorldPainter marks the chunks of the map so that Minecraft (1.19 and later, in the Overworld, when the map has no border or wall) blends the land it generates around them into them, as it does for worlds made before 1.18: the new land meets the edge of the map smoothly over a hundred blocks or so, instead of in a step. The height WorldPainter estimates for the seed is only approximate, so this is what makes the last few blocks meet.

> [!TIP] To blend into the land Minecraft will actually generate for your seed, with its biomes, use **Blend edges into this seed…** in the [Surroundings](surroundings-and-seeds.html#blend-the-edges-into-the-seed) panel instead.
