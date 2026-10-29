**WorldPainter Modernised {{VERSION}}** is a preview of an unofficial, modernised fork of [WorldPainter](https://www.worldpainter.net/), based on WorldPainter 2.27. It is not affiliated with or supported by the original project: please report problems [here](https://github.com/M-U-C-K-A/WorldPainter/issues), not to pepsoft.org.

Documentation and tutorials: https://m-u-c-k-a.github.io/WorldPainter/

## What is in it

- A new interface, with light and dark themes and grouped tools
- An integrated 3D view, with colour modes, 1:1 blocks near the camera and optional shaders (sun, shadows, water)
- A node graph to generate complete worlds, with 36 kinds of nodes, examples and `.wpgraph` files, including a Floating islands node
- Surroundings, seed search and blending the edges of the map into a seed
- Paths from point to point with bridges and tunnels
- A tree pack and material mixes

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
