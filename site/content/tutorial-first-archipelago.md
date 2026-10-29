---
title: Your first archipelago
nav: Your first archipelago
description: Build a small tropical archipelago with 14 nodes, from an empty world to a map you can play.
---

# Your first archipelago

In this tutorial you build a small tropical archipelago from scratch with the node graph: islands with hills, white beaches, rock on the cliffs, palm trees and turquoise water. It takes 14 nodes and about ten minutes.

![The finished archipelago, seen from above](assets/images/simple-archipelago.jpg)

> [!TIP] In a hurry? The finished graph is in the editor under **Examples › Simple archipelago (beginner)**, or [download it](assets/graphs/simple-archipelago.wpgraph) and open it with **File › Open graph…**.

## 1. Create a world

Create a new world with **File › New World…**, about 2048 × 2048 blocks, for Minecraft 26.1 (or any version from 1.20.5). The terrain you start with does not matter: the graph replaces it.

Then open **Generate › Node graph terrain…** and choose **File › New empty graph**. You start with a single node, the **Output**.

## 2. The shape of the islands

Islands are the high points of a noise, faded out towards the edges of the map so that the map is surrounded by sea.

1. Add a **Perlin noise** node (right-click the canvas › Generators). Set its **Size** to 500 blocks. Its thumbnail shows smooth light and dark patches: the light ones will become islands.
2. Add a **Gradient** node. It is white in the middle of the map and black at the edges.
3. Add a **Multiply** node and connect the noise to its input **A** and the gradient to **B**. The patches now fade out towards the edges.

## 3. From values to heights

The Output node turns values from 0 to 1 into heights. Select it and set **Height at 0** to 22 and **Height at 1** to 152. The sea is at Y 62, so the coast is at a value of (62 − 22) / 130 = **0.31**.

Most of the noise would be underwater, or would rise straight out of it. A **Curve** shapes the profile of the islands. Add one between the Multiply node and the Output, and give it these points (double-click the curve to add a point, drag to move it):

| Value in | Value out | Becomes |
|---|---|---|
| 0 | 0 | The deep sea floor |
| 0.2 | 0.12 | |
| 0.3 | 0.29 | Shallow water, just below the surface |
| 0.36 | 0.32 | The coast: a gentle slope makes wide beaches |
| 0.5 | 0.45 | Hills |
| 1 | 0.85 | The highest peaks |

Select the Output node and switch the preview to **Relief**: you have an archipelago.

![The finished graph in the node editor](assets/images/editor-simple-archipelago.png)

## 4. Paint the surface

Masks select parts of the land; painters paint where their mask is strong. Connect each mask to its painter:

| Mask | Settings | Painter | Settings |
|---|---|---|---|
| Height range | 42 to 62, fade 4 | Paint biome | Warm Ocean: turquoise water in Minecraft |
| Height range | 59 to 65, fade 1 | Paint terrain | Sand, strength 1.2 |
| Slope range | 40° to 90°, fade 6 | Paint material mix | Limestone, strength 1.3 |
| Height range | 65 and up, fade 2 | Paint flora | Palm trees (tree pack), density 0.3 |
| (the same mask) | | Paint biome | Jungle |

Strengths above 1 make a painter win where it overlaps others: the sand beats the jungle on the beaches, and the rock beats everything on the cliffs. Switch the preview to **Surface** to see the result.

## 5. Apply and export

Click **Apply to map**. The map is generated, the palm trees appear as a *Palm trees* layer in the Layers panel, and the limestone as a custom terrain type.

Look at it in the [3D view](3d-view.html), change the **Seed** at the top of the node editor if you would like another archipelago, then export as usual with **File › Export › Export as Minecraft map…**.

## Going further

- Add a **Rock towers** node and combine it with **Maximum** for sea stacks in the shallows.
- Add **Erosion** between the Curve and the Output for gullies down the hills.
- Open **Examples › Coral archipelago** to see how the [complete archipelago](tutorial-coral-archipelago.html) is made.
