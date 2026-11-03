**WorldPainter Modernised {{VERSION}}** is a beta preview of an unofficial, modernised fork of [WorldPainter](https://www.worldpainter.net/), based on WorldPainter 2.27. It is not affiliated with or supported by the original project: please report problems [here](https://github.com/M-U-C-K-A/WorldPainter/issues), not to pepsoft.org.

Documentation and tutorials: https://m-u-c-k-a.github.io/WorldPainter/

## New in this beta

- **Three designs**, each light, dark or following the system, which change at once from Preferences, without restarting (Cancel takes the change back): **Modern**, with layered grey surfaces, a blue accent and line icons; **Classic**, the earlier look; and **Glass**, new, with frosted panels on a soft gradient, the map in a pane with rounded corners, translucent cards and pill buttons. Metal, Nimbus and System are gone; settings which used them switch to Classic
- **Preferences** has an **Apply** button
- **Settings windows** group their settings in cards with explanations and badges for what needs a restart, a reload or the next export
- The **Geist** typeface, **workspaces** for terraforming, painting, the node graph or only the map, and panels which dock at the bottom too
- **Node graph**: mountains, rolling hills and an island ready made in one node, with the seldom used settings folded away
- **Seed search** scores the height of the land as well as the biomes, relative to chance
- The **3D view** closes the land with a floor when it is seen from below
- **Export** (experimental button): the lighting goes through a queue, which is faster on large maps

As a beta, it has had less testing than the previews before it: keep backups of your worlds, and report what goes wrong.

## What is in it

- A new interface, with three designs (Modern, Classic and Glass), light or dark, side panels which fold away, palettes which fill their panels, and cards on hover which show what the mouse and keys do, with an animation of each tool, a 3D preview of each brush, pictures of the brush options, and the blocks or plants of each mix and their shares
- An integrated 3D view which keeps up with the map, with colour modes, 1:1 blocks up to 4096 blocks around the camera, optional shaders (sun and sky light, shadows, moving water), cross sections of the ground on its sides, and a panel of its own to float next to the map
- A node graph to generate complete worlds, with 36 kinds of nodes, examples and `.wpgraph` files, including a Floating islands node
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
