---
title: Interface
description: The redesigned interface of WorldPainter Modernised.
---

# Interface

![The editor in the light theme](assets/images/screenshot-light.png)

## Themes

Four designs, each light, dark or following the system:

| Design | Looks like |
|---|---|
| **Modern** | A window of soft grey with raised surfaces, a blue accent, softly rounded controls, tabs as a segmented control, line icons, the Geist typeface, and settings grouped on rounded surfaces with their controls on the right, as in the settings of macOS |
| **Classic** | A classic desktop application, as WorldPainter used to look: a grey window (charcoal in the dark, in the spirit of Darcula), square controls with clear borders, buttons with a slight gradient, areas with title bars, scroll bars with arrows, card shaped tabs, yellow tooltips, a steel blue selection, the font of the system and the original colour icons of WorldPainter in the toolbar, the tools and the layers |
| **Glass** | Liquid glass, after Apple's: the areas float as panes of clear glass on a soft backdrop, with a thin specular rim, bright at the top left; buttons are capsules of glass, the default one tinted blue; the selected tool sits on a tile of glass; check boxes are switches; sliders are thin, with a white knob which turns into a lens while it is dragged. It keeps the line icons and the layout of Modern |
| **Tactile** | A piece of hardware, on light grey (graphite in the dark): tools, brushes and tabs are keys, as on a mechanical keyboard, with a light which comes on in the colour of their kind (orange for sculpting, blue for painting, cyan for water, violet for selection); buttons stand half out of the window, the default one in a warm orange; sliders are faders over a row of lights lit up to the value; check boxes and radio buttons are lit keys; drop down lists are keys with the arrow in a hollow; the map lies on graph paper. It keeps the line icons and the layout of Modern |

Choose them in **Preferences › General › Theme**: the change shows at once, without restarting, and **Cancel** takes it back. The sun and moon button in the toolbar switches between light and dark.

## Panels

The panels for painting explain themselves at their top:

| Panel | Contains |
|---|---|
| **Terrain** | The block type of the ground surface: grass, sand, stone, snow… |
| **Layers** | What is added on top of or under the terrain: trees, caves, ores, frost… with an eye to show or hide each one |
| **Biomes** | The Minecraft biome of each area, chosen automatically from the terrain unless you paint one |
| **Packs** | Ready made trees, flowers, plants and mixes of blocks: click one to add it and paint with it (see [Trees and materials](trees-and-materials.html#the-packs-panel)) |
| **Annotations** | Coloured markings for planning the map, not exported unless you choose to |

The palettes of terrain types, biomes and colours use the whole width of their panel, however wide or narrow it is, and the terrain types are grouped by family (grass and soil, stone, sand and desert, terracotta…) with a search field. The Terrain panel also lists the [material mixes](trees-and-materials.html#material-mixes) (grass, dirt, stone, sand, cliff mixes…): click one to add it as a custom terrain type and paint with it.

Hover over a mix of blocks or a plant layer to see what it is made of: each block or plant with its icon and its share.

## Areas

![Two maps side by side, one zoomed in and one showing the whole continent, between the tools and the brushes](assets/images/areas.png)

The window is divided into areas, as in Blender. At the left of the header of each one, the **editor type** button chooses what it shows: the map, the 3D view, the tools, their settings, the brushes, the brush settings, the terrain, the layers, the biomes, the packs, the annotations, the surroundings or the info. Pages of the same kind share the tabs down the side of their area, like the tabs of the Properties editor of Blender: the terrain, the layers, the biomes, the packs and the annotations are one kind, the brushes and your custom brushes another. To see all the pages of a kind at once instead, such as all your groups of brushes or all your layer palettes, click the **Show all at once** button in the header of the area (or choose it in its menu): they are shown one below the other, each under its name, which folds it away when clicked. Hover over a choice to see what it is for and a picture of it. The map and the 3D view can be shown in several areas at once, each with its own view, as the editors of Blender can: choosing one of them while it is shown elsewhere opens another one, moved and zoomed on its own, in which the current tool works too. Any other page is shown once: choosing one which is already shown in another area swaps the two.

- **Resize** two areas by dragging the gap between them.
- **Split** an area by dragging from one of its corners into it, or with *Split vertically* or *Split horizontally* in its menu (the ⋮ button, or a right click on its header). The new area shows another page of the same kind, or asks what to show.
- **Join** two areas by dragging from a corner of one into the other, which it takes over: they must line up.
- **Move** an area by dragging its header onto another one: near an edge to put it on that side, in the middle to swap the two. Drag a tab the same way to show that page in an area of its own.
- **Maximise** the area under the mouse with <kbd>Ctrl</kbd>+<kbd>Space</kbd>, the button in its header or a double click on the header, and put it back the same way.
- **Close** an area with *Close area* in its menu, or hold <kbd>Shift</kbd>: the ⋮ button of each area turns into a red bin which closes it with one click.
- **Detach** an area into a window of its own, which floats over the main window like picture in picture, with the button in its header or by dragging the header out of the window. Its pin keeps it in front of other applications too; closing it, or dragging its header back onto the main window, puts it back.

What is under the mouse (its location, height, terrain, biome…) and the zoom are at the foot of the map, since they belong to it; another view of the map has its own zoom. The map zooms out to 1:64 (1.56%), far enough to see a map of 40,000 blocks whole. The **walking distances** button of the toolbar draws around the mouse how far you walk in five minutes, in a day and in a day and a night; on large maps also in several days and nights, as far as it takes to cross the map. **View › Panels** shows or hides each page, and **View › Area** has the commands for the area you last clicked in.

## Workspaces

The tabs below the toolbar switch between workspaces, as in Blender: each keeps its own arrangement of areas, and remembers it from one session to the next.

| Workspace | Shows |
|---|---|
| **Layout** | All the panels around the map, as when WorldPainter starts |
| **Sculpting** | The tools, their settings and the brush settings on one side of the map and the brushes on the other, to shape the land |
| **Painting** | The terrain on one side of the map and the layers on the other, next to the tools and the brushes |
| **3D** | The map and the [3D view](3d-view.html) side by side, to see the land take shape as you work |
| **Minimal** | Only the map: split it, or choose what it shows, to add panels |

The **+** after the tabs adds a workspace, made from one of these or as a copy of the current one. Right click a tab to rename, duplicate, reset, move or delete it, or double click it to rename it; <kbd>Ctrl</kbd>+<kbd>Page Up</kbd> and <kbd>Ctrl</kbd>+<kbd>Page Down</kbd> go to the previous and the next one. **View › Workspaces** has the same commands, and can put back the built-in workspaces.

## Tools

The tools are grouped by what they do: **Sculpt terrain** (raise, lower, flatten, smooth, mountains, pyramids, paths), **Paint** (pencil, spray, fill, eyedropper, global operations, text), **Water** (flood with water or lava, sponge) and **Selection & more**. Hover over any tool to see a card with a short looping animation of what it does, its shortcut and how to use it.

To select an area, **Select** works as in an image editor: drag across the map to add a rectangle or an ellipse to the selection, as chosen at the top of its options, with the right mouse button to remove one; hold <kbd>Shift</kbd> for a square or a circle (<kbd>Ctrl</kbd>, or <kbd>⌘</kbd> on a Mac, switches between rectangle and ellipse while dragging). Choose **Brush** there, or the **Select with Brush** tool, to paint the selection with the brush as in the original WorldPainter: the size and shape of the brush set what is selected. Tick *inside selection* in the options of a tool to limit it to the selection.

The buttons of your **custom terrain** can be put in any order: drag one onto another to put it in its place, or use *Move left*, *Move right*, *Move to the start* or *Move to the end* in its right click menu. The order is saved with the world.

**Copy Selection** copies the selected area to where you click. Its options choose what to copy, and turn the copy a quarter turn at a time (↺ ↻) or mirror it, the outline on the map showing where it will go.

## Brushes

A single **Brushes** panel with six basic shapes and twenty-two **terrain stamps** generated procedurally, by family:

| Family | Stamps |
|---|---|
| Mountains | Peak, alpine peak, twin peaks, crag, mountain range, mountain chain, eroded ridge, massif, glacial cirque |
| Hills & plateaus | Hills, eroded dome, terraced hill, plateau, mesa, butte, escarpment, karst towers, dunes |
| Volcanoes & craters | Volcano, shield volcano, crater rim |
| Valleys | Valley, to carve with the lower tool (hold <kbd>Ctrl</kbd> with the height tool) |

Hover over a brush to see its shape in 3D, turning slowly on a block of ground. On the map the outline of a brush shows its actual shape, rotation included. Rotate it with <kbd>[</kbd> and <kbd>]</kbd> (or <kbd>Alt</kbd>+scroll), and zoom the map with <kbd>Ctrl</kbd>+scroll or <kbd>⌘</kbd>+scroll. ### Your own brushes

Brushes made from your own images (greyscale: white paints, black doesn't) come in groups, each with a page of its own, and with the shape of its first brush on its tab. At the bottom of the Brushes page, and in the menu of their area:

- **Add brushes…** copies images into a group (the **+** at the end of each group does the same);
- **New brush group…**, **Rename group…** and **Delete group…** (which moves it to the Trash);
- **Open the brushes folder**, where each group is a folder: you can arrange them there too;
- **Copy brushes from WorldPainter** brings the brushes of the original WorldPainter, which keeps its settings apart. They are copied the first time anyway.

Drag a brush onto another group, its tab or its heading to move it there, or right click it to move it or remove it. Changes show at once, without restarting.

## Where a brush applies

The brush settings (intensity, rotation, brush and options) stay as they are when you switch between tools, and when you paint with a different material.

The **Options** can limit a brush in several independent ways:

- **Where on the map** limits it to what you selected on the map with a selection tool, or to everything else. With *Inside selection* and nothing selected, the brush paints nowhere, and the panel says so.
- The **height limits** limit it to certain heights, shown on a scale with a little landscape: the land in the accent colour is the land the brush will change.

- **What is on the map** limits it to certain terrain types or layers (*only on*, *except on*), to steep or gentle slopes, or by **curvature**: to *hollows*, where the land around is higher (valleys, dips, the foot of slopes), or to *bumps*, where it is lower (ridges, peaks, the brow of slopes), by at least a number of blocks (from 0.1, for gentle hollows and bumps), measured over a number of blocks around: a larger reach finds broader valleys and ridges and goes further up their sides. The land is compared with a ring around each point and with the average of the few blocks at its centre, so that land going up in whole blocks does not count as curved at every step, and the edge of the area fades out softly. You can choose as many terrain types and layers as you like, with **Add another**: to turn grass and stone into dirt, paint dirt *only on* grass and stone.

For the height limits:

- Drag the bars to set the limits, which also switches them on, or type them in the *at or above* and *at or below* fields. A value you type counts as soon as you click elsewhere, without pressing Enter. *Fade at the limits* lets the brush fade over the 4 blocks beyond them, instead of stopping abruptly.
- **Between** applies between the two bars; **Beyond** applies below the lower one and above the upper one. Click the highlighted button again to switch the limits off. 
- Ctrl+Z (⌘Z on macOS) takes back changes to these options, and Ctrl+Y brings them back, as long as you have not changed the map since. After that they undo and redo changes to the map as usual.

## What the map shows

The bar at the top of the map chooses what it shows, as the [3D view](3d-view.html) does, so that one property of the land can be looked at on its own while you paint:

| Mode | Shows |
|---|---|
| **Terrain** | The terrain and the layers, as painted |
| **Height** | A colour gradient from the lowest to the highest land not under water, with contour lines (every fifth one darker) and the water by depth. Click **Height** again to fit the colours to the land as it is after you have changed it |
| **Slope** | From blue (flat) to red (60° or more): find cliffs, and flat land to build on |
| **Biome** | The biome of every column, painted or automatic, in the same colours as the 3D view |
| **Relief** | Plain shaded relief, to judge the shape of the land |

The legend next to the buttons gives the heights or slopes at either end of the colours. In every mode but Terrain only the selection is drawn over the land, and the tools paint as usual: raise a mountain in Height mode and watch it turn white at the top. The light comes from the side chosen for the map, and the contour lines follow the **Contours** button of the toolbar and their spacing in *View > Configure view*; zoomed out far they are drawn further apart. The same choice is in *View > Show on the Map*, and an exported image of the map shows what the map shows. When the map is shown in several areas, each has its own bar: paint in Terrain in one and watch the slopes in another.

![The Height mode of the map: green valleys, brown slopes and contour lines](assets/images/map-height.jpg)

![The Slope mode of the map: flat valleys in blue, steep slopes in red](assets/images/map-slope.jpg)

## What the land is made of

The **Composition** page (in the editor type menu of any area, or the pickaxe button of the 3D view) tells at a glance what a piece of land is made of, and follows it as it moves. The three buttons at its top choose which land, and say under them what it covers:

- **3D view**: the land the 3D view looks at, a square around the point it turns around, as large as the camera is far from it; the rock column is the one at that point;
- **Map**: everything the map shows at the moment;
- **Selection**: only the land you selected with the Select tools, wherever it is.

At the top a small map of the world shows where that land is, with its size; then key figures (average height, relief, average and steepest slope, water, tree cover, number of biomes); then sections, each of which folds away when its heading is clicked:

- **Relief**: how much of the land lies at each height (in the colours of the Height view), and how much of it is flat, gentle, steep or cliff;
- **Surface**: the share of each terrain type;
- **Water**: how much is under water or lava, and how deep;
- **Biomes**: the share of each, and how much is painted;
- **On the land**: the trees, the plants and ground cover and the other layers, each with how much of the land it is on and how dense;
- **Underground**: the caves, tunnels and pockets painted under it, what applies everywhere in the world and between which heights, and a table of the ores with their heights and how common they are;
- **Rock column**: the blocks of a column from the top down to the bottom of the world, as they will be exported before the caves and ores: the top layer of its terrain, then the rock under it (stone with granite, diorite and andesite, deepslate and tuff below Y 0), down to the bedrock.

## Keyboard shortcuts

*Tools > Keyboard Shortcuts* lists every command which can have a shortcut: the items of the menus, the painting and sculpting tools (Alt and a letter by default, or none), the keys of the brush and the map (its size, its rotation, its intensity, the zoom, the light) and a few commands which have none to begin with (what the map shows, the Composition page, the 3D view, the map). Double-click one, or choose *Change*, and press the keys you want; *Add* gives it one more, *Remove* none, *Reset* the ones it came with. If the keys are already the shortcut of another command, it asks whether to move them. The search field finds commands by name or by shortcut. Your shortcuts are kept in `keybindings.properties` in the configuration folder; *Reset All* puts the original ones back.

## Canvas

The map sits on a dotted canvas instead of a dark blue background. On macOS the toolbar doubles as the title bar.

## Settings windows

In the Classic design the settings windows (Preferences, Edit Surface Properties, Export, New World…) open with a banner: a picture of what they are about, a sentence explaining them and, for windows with tabs, what the selected tab contains. The Modern and Glass designs leave the banner out and start with the settings, and replace the old coloured icons with line icons in the colours of the theme. **Preferences** also has an **Apply** button, which puts the changes into effect without closing the window.

Their settings are grouped in cards, each with a title and a sentence about what it does. Badges point out the settings which only apply after a restart, after reloading the map, on the next export or to new worlds, and those which are experimental or can be slow.

![Edit Surface Properties in the Modern design](assets/images/settings-windows.png)
