---
title: WorldPainter Modernised
nav: Introduction
description: A modernised fork of WorldPainter, the Minecraft map editor: an integrated 3D view, a node graph for procedural worlds, the land around your map, seed search and a clean new interface.
---

<div class="not-prose">
  <p class="mb-4 inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs text-muted-foreground"><span class="size-1.5 rounded-full bg-accent"></span>Preview · Minecraft 1.20.5 to 26.x · macOS, Windows, Linux</p>
  <h1 class="text-4xl font-semibold tracking-tight sm:text-5xl">Paint complete Minecraft worlds, faster.</h1>
  <p class="mt-4 max-w-2xl text-lg text-muted-foreground">WorldPainter Modernised is a fork of <a class="underline underline-offset-4 hover:text-foreground" href="https://www.worldpainter.net">WorldPainter</a>, the map editor in which you paint Minecraft landscapes like in a drawing program. It adds a node graph to generate whole worlds, a 3D view of the result, a preview of the land Minecraft generates around your map, and a clean, modern interface.</p>
  <div class="mt-6 flex flex-wrap gap-3">
    <a href="installation.html" class="inline-flex h-9 items-center rounded-md bg-foreground px-4 text-sm font-medium text-background transition-opacity hover:opacity-90">Get started</a>
    <a href="tutorial-first-archipelago.html" class="inline-flex h-9 items-center rounded-md border px-4 text-sm font-medium transition-colors hover:bg-muted">Your first world in 10 minutes</a>
  </div>
</div>

<figure>
  <img src="assets/images/screenshot-dark.png" alt="The WorldPainter Modernised editor, with the land Minecraft will generate around the map shown faintly around it" class="hidden dark:block">
  <img src="assets/images/screenshot-light.png" alt="The WorldPainter Modernised editor, with the land Minecraft will generate around the map shown faintly around it" class="dark:hidden">
</figure>

## What it does

<div class="not-prose grid gap-3 sm:grid-cols-2">
  <a href="node-graph.html" class="rounded-xl border p-4 transition-colors hover:bg-muted"><p class="font-medium">Node graph</p><p class="mt-1 text-sm text-muted-foreground">Wire noise, erosion, rivers, masks and painters together to generate a complete world: relief, water, materials, biomes and trees.</p></a>
  <a href="3d-view.html" class="rounded-xl border p-4 transition-colors hover:bg-muted"><p class="font-medium">Integrated 3D view</p><p class="mt-1 text-sm text-muted-foreground">See the map in 3D, block by block if you like, with the height, slope, biome and layers of every block under the mouse.</p></a>
  <a href="surroundings-and-seeds.html" class="rounded-xl border p-4 transition-colors hover:bg-muted"><p class="font-medium">Surroundings and seeds</p><p class="mt-1 text-sm text-muted-foreground">See the biomes Minecraft will generate around your map, find a seed that fits it, and blend the edges into it.</p></a>
  <a href="paths.html" class="rounded-xl border p-4 transition-colors hover:bg-muted"><p class="font-medium">Paths</p><p class="mt-1 text-sm text-muted-foreground">Click a few points and get a road which follows the land, with bridges over valleys and tunnels through hills.</p></a>
  <a href="trees-and-materials.html" class="rounded-xl border p-4 transition-colors hover:bg-muted"><p class="font-medium">Trees and materials</p><p class="mt-1 text-sm text-muted-foreground">A pack of custom trees (palms, cherry trees, rainforest giants…) and ready made mixes of blocks for natural ground and cliffs.</p></a>
  <a href="tutorial-floating-islands.html" class="rounded-xl border p-4 transition-colors hover:bg-muted"><p class="font-medium">Floating islands</p><p class="mt-1 text-sm text-muted-foreground">A node which builds islands floating in the sky, with craggy undersides and forests on top, ready to export.</p></a>
</div>

## Made with nodes

Both of these maps were generated entirely by a node graph, without painting a single block by hand. The graphs are included as examples in the editor, and you can [download them](tutorial-coral-archipelago.html#download-the-graphs).

<div class="not-prose grid gap-3 sm:grid-cols-2">
  <figure><img src="assets/images/coral-archipelago.jpg" alt="A coral archipelago with a mountainous island in the middle, turquoise reefs and a blue hole" class="rounded-xl border"><figcaption class="mt-2 text-sm text-muted-foreground">Coral archipelago · 3000 × 3000 · 75 nodes</figcaption></figure>
  <figure><img src="assets/images/continent-seed-4242.jpg" alt="A continent with a snowy mountain range, rivers, badlands, cherry groves and a volcanic island" class="rounded-xl border"><figcaption class="mt-2 text-sm text-muted-foreground">Continent and islands · 5000 × 5000 · 86 nodes</figcaption></figure>
</div>

> [!NOTE] This is a preview. The editor is stable enough to make real maps (the coral archipelago above exports to Minecraft 26.1 in about two minutes), but it has not been released yet and some features are still in development: see the [roadmap](roadmap.html).

## Where to start

- New to WorldPainter? [Install it](installation.html), then follow [Your first archipelago](tutorial-first-archipelago.html): 14 nodes, ten minutes.
- Already know WorldPainter? Read [what's new](whats-new.html) compared to the original.
