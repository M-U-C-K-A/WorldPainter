---
title: Paths
description: Lay out roads and tracks through points you click, following the land, with bridges and tunnels.
---

# Paths

The **Path** tool, in the *Sculpt terrain* group, lays out a road or a track through points you click, following the land.

1. Select the Path tool.
2. **Left-click** the points the path should pass through, in order. They are joined by a dashed line on the map. **Right-click** or <kbd>Backspace</kbd> removes the last point, <kbd>Escape</kbd> starts again.
3. Press <kbd>Enter</kbd> to choose the options for the path.

![Three points of a path on the map](assets/images/path-waypoints.png)

## Options

![The path options, with the route and the height profile](assets/images/path-dialog.png)

| Option | |
|---|---|
| **Bends** | 0 is a straight line between the points; 100 is the route which follows the land best, going round hills rather than over them |
| **Crossings** | *Natural*: the path goes up and down with the land. *Bridges* over valleys and water, *Tunnels* through hills, or both |
| **Width** | Narrow (3 blocks), medium (5) or wide (7). With **Dynamic**, the width varies with the land: narrower across steep hillsides, on bridges and in tunnels, wider on flat open ground, and always gradually |
| **Steepest slope** | The steepest the path may climb, in blocks of height per 100 blocks of length |
| **Longest bridge or tunnel** | Longer crossings stay on the ground |
| **Surface** | The terrain type of the path |
| **Borders** | A second material mixed into the path, at random and in small clumps, more of it towards the edges; and low parapets on bridges |
| **Adapt to the land** <sup>beta</sup> | Instead of the materials above, mix blocks which suit the land the path crosses: sandstone, sand and coarse dirt through the desert, andesite, cobblestone and gravel in the mountains, gravel and stone in the snow, trodden earth and gravel through grass. Along the edges some of the land is left showing |
| **Sides** | What lines both sides on the ground: a wooden fence, a fence alternating with hedges, a hedge, a low stone wall, posts with lanterns, or flowers. They are custom object layers, which you can edit or remove afterwards |
| **Remove trees and plants on the path** | Clears the trees, plants and custom objects on the path and overhanging it |

The preview shows the route over the land (orange on the ground, purple on bridges, grey and dashed in tunnels) and the height profile of the path over the land. Below them: the length, how much is on bridges and in tunnels, the steepest slope, and how much land is dug out and filled in.

**Build Path** carves the path into the land, with a smooth transition to the land on either side.

## Bridges and tunnels

Bridges and tunnels are created as WorldPainter layers, named *Bridge 1*, *Tunnel 1* and so on: a floating layer for the deck of a bridge, a cave layer for a tunnel. They appear in the Layers panel, export to Minecraft like any other layer, and can be edited afterwards (right-click the layer).

A bridge stands on pairs of piers every twelve blocks or so, from under its deck down into the ground (*Bridge 1 piers*). Loose materials such as gravel and sand would fall in Minecraft, so a bridge on a gravel or sand path, or with **Adapt to the land**, gets a deck of wooden planks on wooden piers; other bridges are built of the path's material on stone brick piers. The planks, logs and bricks are added to the Terrain panel as custom terrain types.

> [!TIP] Undo removes the whole path: the land, and the bridge and tunnel layers it created, which leave the layer panels too. Redo brings them back.
