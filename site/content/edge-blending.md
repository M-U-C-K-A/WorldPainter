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
| **Blend into** | A procedural terrain (*Minecraft-like* by default, close to what Minecraft generates beyond the map) or a flat level |

Minecraft then blends the last few blocks itself, with its *chunk blending*, when it generates the chunks around the map.

> [!TIP] To blend into the land Minecraft will actually generate for your seed, with its biomes, use **Blend edges into this seed…** in the [Surroundings](surroundings-and-seeds.html#blend-the-edges-into-the-seed) panel instead.
