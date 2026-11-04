---
title: 3D view
description: The integrated 3D view of the map, with colour modes, a block by block preview, sun and shadows.
---

# 3D view

The **3D** workspace, in the tabs below the toolbar, shows the 3D view next to the map; any area can show it too, with the editor type button at the left of its header (see [Areas](interface.html#areas)). The 3D view is built from the map when you open it, and rebuilt by itself a moment after you stop changing the map; **Refresh** rebuilds it straight away.

![The 3D workspace: the map, and the 3D view of the whole continent next to it](assets/images/view3d-light.jpg)

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

The toolbar also turns the water, the trees and the contour lines (one every 8 blocks) on and off.

## Trees

The trees painted on the land are shown as simple shapes, where the forest layers (Deciduous, Pine, Jungle, Swamp) and the trees of the tree pack are, with their density, size and colour: layered conifers, round or flat crowns, palms, bushes and bare dead trees. Minecraft's trees are only decided when the map is exported, so they stand where the layers are, as densely, but not one for one. Custom objects with leaves are shown as round crowns of their size.

![The Slope mode: steep slopes in red, flat land in blue](assets/images/view3d-slope.jpg)

## In a panel of its own

Like any area, the 3D view can be detached into a window of its own with the button in its header, to float next to the map so that you can paint and watch the result at the same time, or be put on any side of the map by dragging its header there. To look at the land from two sides at once, choose the 3D view in another area too: each one has a camera of its own.

## The sides

The sides of the model show a cross section of the ground, as it is in Minecraft: the surface, a few blocks of soil (dirt, or sand under sand), rock in faint strata, deepslate below zero and bedrock at the bottom of the world, with the water above the ground where there is water.

## Block by block

The **Blocks** button shows the land around the camera as Minecraft will: every block a one metre cube, at its exact height, with the edges of the blocks drawn as you zoom in. The menu next to it sets how far around the camera this goes (256 to 4096 blocks); beyond it the rest of the map is shown as a smooth surface. The area follows the camera when you move.

## Shaders

The **Shaders** button (the sun) lights the land like a shader pack would:

- the land is lit by warm sunlight and cool light from the sky, casts shadows, and gets darker in valleys and crevices
- the sky follows the height of the sun, and distant land fades into it
- the water moves, is clear along the shore and deep blue further out, and reflects the sky and the sun

The panel at the bottom right sets the **direction** the sun shines from and its **height**, or a time of day: **Morning**, **Noon** or **Evening**. The land looks best with the sun to the side of or behind the camera; looking towards the sun, you mostly see the shaded side of the land.

> [!NOTE] The 3D view shows the terrain, water, colours and trees of the map. It does not show other custom objects or floating islands yet.
