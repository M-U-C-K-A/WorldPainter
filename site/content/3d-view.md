---
title: 3D view
description: The integrated 3D view of the map, with colour modes, a block by block preview, sun and shadows.
---

# 3D view

The **Map** and **3D** buttons at the top right of the window switch the workspace between the map and a 3D view of it. The 3D view is built from the map when you open it, and rebuilt when you click **Refresh** after changing the map.

![The 3D view in Terrain mode](assets/images/view3d-light.jpg)

## Moving around

| | |
|---|---|
| Drag | Orbit around the point you look at |
| <kbd>Shift</kbd> + drag, or right-drag | Move across the land |
| Scroll | Zoom in and out |
| Double-click | Look at the clicked point and move closer |

The buttons on the right of the toolbar turn the view a quarter turn, switch between a low angle (looking across the land, as a player would) and a high angle, show the map from straight above with north up, switch to an **orthographic** projection (no perspective, so that distances can be compared), and frame the whole map again.

## Information about every block

Hover over the land to see, in the card at the top left:

- the position (X and Z) and the height (Y) of the block
- the slope
- the terrain type and the biome (painted, or automatic)
- the depth of the water, if any
- the layers present (trees, caves, frost…)

## Colour modes

| Mode | Shows |
|---|---|
| **Terrain** | The colours of the terrain types, as on the map |
| **Height** | A colour gradient from the lowest to the highest point |
| **Slope** | From blue (flat) to red (60° or more): find cliffs and buildable flat land |
| **Biome** | The biome of every column |
| **Relief** | Plain shaded relief, to judge the shape of the land |

The toolbar also turns the water and the contour lines (one every 8 blocks) on and off.

![The Slope mode: steep slopes in red, flat land in blue](assets/images/view3d-slope.jpg)

## Block by block

The **Blocks** button shows the land around the camera as Minecraft will: every block a one metre cube, at its exact height, with the edges of the blocks drawn as you zoom in. The menu next to it sets how far around the camera this goes (256 to 1536 blocks); beyond it the rest of the map is shown as a smooth surface. The area follows the camera when you move.

## Shaders

The **Shaders** button (the sun) lights the land like a shader pack would:

- the land is lit by the sun, and casts shadows
- the sky follows the height of the sun
- the water moves, and reflects the sky and the sun

Drag the sun on the globe at the bottom right to change its direction and height: the centre is straight overhead, the edge is the horizon.

> [!NOTE] The 3D view shows the terrain, water and colours of the map. It does not show custom objects, trees or floating islands yet.
