**WorldPainter Modernised {{VERSION}}** is a preview of an unofficial, modernised fork of [WorldPainter](https://www.worldpainter.net/), based on WorldPainter 2.27. It is not affiliated with or supported by the original project: please report problems [here](https://github.com/M-U-C-K-A/WorldPainter/issues), not to pepsoft.org.

Documentation and tutorials: https://m-u-c-k-a.github.io/WorldPainter/

## New in this version

- **Large maps**: the map zooms out to 1:64 (1.56%) instead of 1:16, so that a map of 40,000 blocks fits on the screen, and draws faster when zoomed out; the walking distances also show several days and nights of walking on large maps, as far as it takes to cross the map
- **Fix**: the walking distances no longer leave bits of their rings behind on the map when the mouse moves

## New in 2.27.2-modernised.11

- **Manage your own brushes**: add brushes from images, make, rename and delete groups, move a brush to another group by dragging it onto its page, tab or heading (or with a right click), all without restarting; the custom brushes of the original WorldPainter are copied the first time
- **Show all at once**: an area can show all the pages of its kind one below the other, such as all your groups of brushes or all your layer palettes, instead of one at a time with tabs
- **Curvature** in the brush settings: limit a brush to hollows (valleys, dips) or to bumps (ridges, peaks)
- **Rivers** with the Path tool: dug along the line, flowing downhill, with a bed of sand and gravel, the river biome and sloping banks
- **Fixes**: custom brushes no longer leave bits of their outline behind on the map; the brushes of a group fill the width of their area in a grid instead of one column; groups of brushes show the shape of their first brush on their tab instead of a letter

## New in 2.27.2-modernised.10

- **Half Blocks tool**: next to Smooth, it smooths the land like Smooth and builds it in half blocks in Minecraft where you drag it: slabs on stone, cobblestone, sandstone and the like (waterlogged under water), snow layers on snow blocks, so that gentle slopes go up by half a block instead of a whole block every other column. Right-drag goes back to whole blocks; the map shows where it applies with amber stripes. Snow lying on rock (Snow on Rock, Frost) is made as thick as the slope asks, so rock and snow follow the same slope. Grass, dirt and sand have no slab and stay whole
- **The land Minecraft generates around the map meets it smoothly** (Minecraft 1.19 and later): the chunks of the map are marked for Minecraft's *chunk blending*, which it uses for worlds made before 1.18, so that it blends the new land into the edge of the map instead of leaving a step; this also smooths the outer edge of the band added by *Blend edges into this seed*
- **Trees in the 3D view**: the forest layers and the trees of the tree pack are shown as simple shapes, with their density, size and colour; a button turns them off
- **Fuller trees in the tree pack**: conifers have close tiers, and the crowns of oaks, cherry trees, willows and pale oaks are no longer cut off at the edges; worlds painted with the earlier trees get the new ones when they are opened
- **Blending into the seed** only adds the transition: beyond it Minecraft generates its own land, instead of meeting an estimate of it along the straight edges of the added tiles; and no more beach sand high up where the transition carries the land above the sea
- **Two export buttons again**: *Export FAST*, about a third faster on large maps, and the classic *Export*, as WorldPainter always exported; both give exactly the same blocks and light

## New in the betas before it

- **Fix**: a second map shown in an area of another workspace no longer brings up an error window when the map changes (painting, undo, opening another world); it draws again when it is shown
- The **editor type menu** shows the useful part of each page in its pictures: the palettes without their introduction and search field, the map and the 3D view without their bars, and the 3D view even before it was first opened, sharp on high resolution screens
- **Areas as in Blender**: the window is divided into areas, which you resize by dragging the gaps between them, split and join from their corners, move by their header, maximise with Ctrl+Space and detach into windows of their own. The button at the top left of each one chooses what it shows, with a card and a picture of each page. The **map and the 3D view can be shown in several areas at once**, each with a view of its own. Hold **Shift** to close areas with one click
- **Workspace tabs** (Layout, Sculpting, Painting, 3D, Minimal and your own) replace the workspace button and the Map/3D switch
- **Faster export**: about a third faster on large maps, with the same blocks and the same light
- **Map thumbnails**: on macOS the Finder shows the map of a world as the icon of its `.world` file
- **Node graph**: new Minecraft-like and Plains examples; the old Procedural Terrain dialog is gone, the node graph does all it did
- What is under the mouse and the zoom are at the foot of the map; the buttons of the 3D view go on to more rows in a narrow area; the close button of the window works again in Run Script, Merge, Rotate and Shift

## What is in it

- A new interface, with three designs (Modern, Classic and Glass), light or dark, areas which can be split, joined, moved and detached as in Blender, workspace tabs, palettes which fill their panels, and cards on hover which show what the mouse and keys do, with an animation of each tool, a 3D preview of each brush, pictures of the brush options, and the blocks or plants of each mix and their shares
- An integrated 3D view which keeps up with the map, with colour modes, 1:1 blocks up to 4096 blocks around the camera, optional shaders (sun and sky light, shadows, moving water) and cross sections of the ground on its sides
- A node graph to generate complete worlds, with 39 kinds of nodes, examples and `.wpgraph` files, including landforms ready made in one node and a Floating islands node
- Surroundings drawn like the map, seed search, and blending the edges of the map into a seed (round maps included, with undo)
- Paths from point to point with bridges on piers and tunnels, surfaces mixed at random or adapted to the land (beta), in three widths with an optional dynamic width, which clear the trees on their way and can be lined with fences, hedges, walls, lanterns or flowers; and rectangle selection
- Brush settings which stay when switching tools: limits by map selection, by height on a scale you can drag, by several terrain types or layers at once, and by slope, with undo
- Packs ready to paint: 14 kinds of custom trees, 24 presets of flowers and plants, 39 mixes of blocks, and 22 terrain stamp brushes

## Downloads

| System | File |
|---|---|
| macOS, Apple Silicon | `WorldPainter-Modernised-{{VERSION}}-macos-arm64.dmg` |
| macOS, Intel | `WorldPainter-Modernised-{{VERSION}}-macos-x64.dmg` |
| Windows (x64) | `WorldPainter-Modernised-{{VERSION}}-windows-x64.zip`: unzip it and run `WorldPainter.exe` |
| Linux (x64, Debian and Ubuntu) | `WorldPainter-Modernised-{{VERSION}}-linux-amd64.deb` |

The packages include their own Java runtime. `SHA256SUMS.txt` lists the checksums of the files.

## Unsigned application

The application is **not signed or notarised** yet:

- **macOS**: the first time, right-click the application and choose **Open**, then confirm. If macOS says the application is damaged, run `xattr -dr com.apple.quarantine /Applications/WorldPainter.app` in Terminal.
- **Windows**: SmartScreen may warn about an unknown publisher; choose **More info**, then **Run anyway**.

## Good to know

- This version keeps its settings apart from the original WorldPainter (on macOS and Windows in a `WorldPainter [MODERNISED]` folder), so both can be installed side by side. The settings of earlier builds of this fork are copied over the first time.
- Worlds saved with this version may not open in the original WorldPainter. Keep backups of the worlds you care about.
- Undoing a node graph or a path also removes the layers it created (trees, bridges, tunnels, floating islands) from the layer panels; the material mixes it set up stay available as custom terrain.
- This is a preview: test your exported maps in Minecraft before relying on them.

WorldPainter is © pepsoft.org and contributors, licensed under the GNU General Public License v3.
