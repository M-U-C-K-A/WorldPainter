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
| **Width** | In blocks |
| **Steepest slope** | The steepest the path may climb, in blocks of height per 100 blocks of length |
| **Longest bridge or tunnel** | Longer crossings stay on the ground |
| **Surface** | The terrain type of the path |
| **Borders** | Edges of another material along both sides, and low parapets on bridges |

The preview shows the route over the land (orange on the ground, purple on bridges, grey and dashed in tunnels) and the height profile of the path over the land. Below them: the length, how much is on bridges and in tunnels, the steepest slope, and how much land is dug out and filled in.

**Build Path** carves the path into the land, with a smooth transition to the land on either side.

## Bridges and tunnels

Bridges and tunnels are created as WorldPainter layers, named *Bridge 1*, *Tunnel 1* and so on: a floating layer for the deck of a bridge, a cave layer for a tunnel. They appear in the Layers panel, export to Minecraft like any other layer, and can be edited afterwards (right-click the layer).

> [!WARNING] Undo removes the path from the land, but not the bridge and tunnel layers it created: delete those by hand if you need to.
