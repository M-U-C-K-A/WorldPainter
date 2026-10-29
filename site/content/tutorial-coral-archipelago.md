---
title: The coral archipelago
nav: The coral archipelago
description: How the 75 node coral archipelago is made, section by section.
---

# The coral archipelago

The coral archipelago is a complete 3000 × 3000 world, made entirely of nodes: clusters of jungle islands around a high mountain, pale limestone cliffs and sea stacks, wide white beaches with palm trees, turquoise reef flats with coral over a gently sloping shelf, and a deep ocean with trenches and a blue hole. This page explains how its 75 nodes fit together. Read [Your first archipelago](tutorial-first-archipelago.html) first: this is the same idea, with more detail.

![The coral archipelago](assets/images/coral-archipelago.jpg)

## Download the graphs

| Graph | Nodes | Map |
|---|---|---|
| [Coral archipelago](assets/graphs/coral-archipelago.wpgraph) | 75 | 3000 × 3000 |
| [Continent and islands](assets/graphs/continent-and-islands.wpgraph) | 86 | 5000 × 5000 |
| [Simple archipelago](assets/graphs/simple-archipelago.wpgraph) | 14 | any |
| [Floating islands](assets/graphs/floating-islands.wpgraph) | 11 | any |

Open them with **File › Open graph…** in the node editor; they are also in the **Examples** menu. On a 3000 × 3000 map, the coral archipelago generates in about 10 seconds and exports to Minecraft in about 2 minutes.

## 1. Where the islands are

The graph starts with an *island potential*: a value which is high where there should be land.

- Two **Perlin noise** nodes, **added** together: a large one (1300 blocks) for the clusters of islands, and a smaller one (380 blocks) for the islands within them.
- A **Gradient** centred on the map raises the middle, where the largest island will be.
- A second, small Gradient raises a round shelf in the south west, where the blue hole will be.
- A last Gradient, through a Curve, fades everything out towards the edges of the map, so that the archipelago is surrounded by open ocean.

## 2. The profile

A **Curve** with ten points turns the potential into heights, in bands:

| Potential | Height | |
|---|---|---|
| up to 0.45 | Y 1 to 3 | The deep sea floor |
| 0.45 to 0.8 | Y 3 to 55 | A long, gradual slope: no sudden drop |
| 0.8 to 1 | Y 55 to 60 | Wide, nearly flat reef flats just under the surface |
| 1 to 1.1 | Y 60 to 66 | Wide beaches with a gentle slope |
| above | up to Y 110, more with the relief | The land |

The Output maps 0 to Y −28 and 1 to Y 212, so that the trenches and the blue hole can go below the sea floor and the central mountain well above the rest.

This is what gives the archipelago its turquoise shallows: most of the area around the islands is a few blocks under water.

## 3. The relief of the islands

- **Ridged noise**, masked to the land, gives the islands rugged ridges.
- **Rock towers** add karst pinnacles to the larger islands.
- A **Blur** softens them, and an **Add** puts them on top of the profile.
- The central mountain is a **Gradient** shaped by a Curve into a cone, roughened by ridged noise, and combined with **Maximum** so that it rises above everything else.
- Small **Rock towers** in the shallows make the sea stacks.

## 4. The deep ocean

Ridged noise, masked to the deep water and **subtracted**, carves trenches into the sea floor. A small **Gradient** subtracted in the south west digs the blue hole into the reef shelf. A **Clamp** keeps everything in range, and a light **Erosion** carves gullies down the slopes before the **Output**.

## 5. The surface

| Mask | Painter |
|---|---|
| Everywhere | Biome: Lukewarm Ocean (the deep water) |
| The shelf (Y 32 to 62) | Biome: Warm Ocean (turquoise water), terrain: Sand |
| The deep (below Y 32) | Terrain: Gravel |
| The reef flats, in patches of noise | Material mix: Coral reef |
| Beaches: Y 60 to 67, slopes under 22° | Terrain: Sand, biome: Beach |
| The shore | Flora: Palm trees |
| Cliffs, steeper than 48° | Material mix: Limestone |
| Steep slopes, 38° to 48° | Material mix: Mossy rock |
| Land on gentler slopes | Material mix: Forest floor; flora: Rainforest giants, Bushes, Ferns; biome: Jungle |

Combining a height mask with a slope mask (with **Multiply**) is the key to most of these: *beaches* are low **and** flat, *jungle* is above the shore **and** not too steep.

## Make it your own

- Change the **Seed**: another archipelago of the same kind.
- Move the blue hole and its shelf: they are the two small Gradients with a centre of X −620, Z 720.
- Swap the palms for **Cherry trees** and the warm ocean for a **Lukewarm Ocean** for a temperate archipelago.
