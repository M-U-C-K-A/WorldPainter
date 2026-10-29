package org.pepsoft.worldpainter.nodes;

import org.pepsoft.worldpainter.MaterialGroup;
import org.pepsoft.worldpainter.Terrain;

/**
 * Ready made node graphs to start from. Each one produces a complete landscape: heights, terrain types, biomes and
 * vegetation.
 */
public final class ExampleGraphs {
    private ExampleGraphs() {
        // Prevent instantiation
    }

    /**
     * Create an example graph, with the heights adapted to a map.
     *
     * @param example    The example to create.
     * @param seed       The seed for the graph.
     * @param waterLevel The water level of the map, around which the heights are chosen.
     * @param minHeight  The lowest height of the map.
     * @param maxHeight  The highest height of the map (exclusive).
     */
    public static NodeGraph create(Example example, long seed, int waterLevel, int minHeight, int maxHeight) {
        final Builder b = new Builder(example, seed);
        final int w = waterLevel;
        switch (example) {
            case ROLLING_HILLS: {
                final NoiseNode hills = b.noise(NoiseNode.Kind.PERLIN, 768, 5, 1, 0, 0);
                final CurveNode shape = b.add(new CurveNode(0, 0, 0.5f, 0.35f, 1, 1), 1, 0);
                b.wire(hills, shape);
                b.output(shape, 2, 0, w - 24, w + 72, minHeight, maxHeight);
                // Patches of forest, and grass and flowers everywhere else
                final NoiseNode forestNoise = b.noise(NoiseNode.Kind.PERLIN, 400, 4, 11, 0, 1);
                final CurveNode forests = b.add(new CurveNode(0, 0, 0.5f, 0, 0.62f, 1, 1, 1), 1, 1);
                b.wire(forestNoise, forests);
                b.wire(forests, b.flora(Vegetation.DECIDUOUS_FOREST, 0.6, 2, 1));
                b.flora(Vegetation.GRASS_AND_FLOWERS, 0.35, 3, 1);
                b.wire(forests, b.biome(MinecraftBiome.FOREST, 1, 2, 2));
                b.biome(MinecraftBiome.PLAINS, 0.6, 3, 2);
                break;
            }
            case ERODED_MOUNTAINS: {
                final NoiseNode base = b.noise(NoiseNode.Kind.PERLIN, 2400, 5, 1, 0, 0);
                final NoiseNode peaks = b.noise(NoiseNode.Kind.RIDGED, 900, 7, 2, 0, 1);
                final CurveNode lowlands = b.add(new CurveNode(0, 0.05f, 1, 0.45f), 1, 0);
                b.wire(base, lowlands);
                final CurveNode ranges = b.add(new CurveNode(0, 0, 0.45f, 0, 0.75f, 1, 1, 1), 1, 1);
                b.wire(base, ranges);
                final CombineNode mountains = b.add(new CombineNode(CombineNode.Mode.MULTIPLY), 2, 1);
                b.wire(peaks, mountains, 0);
                b.wire(ranges, mountains, 1);
                final CombineNode terrain = b.add(new CombineNode(CombineNode.Mode.ADD), 3, 0);
                terrain.set(CombineNode.STRENGTH, 0.65);
                b.wire(lowlands, terrain, 0);
                b.wire(mountains, terrain, 1);
                final GullyErosionNode erosion = b.add(new GullyErosionNode(), 4, 0);
                erosion.set(GullyErosionNode.DEPTH, 40);
                erosion.set(Node.VARIANT, 6);
                b.wire(terrain, erosion);
                b.output(erosion, 5, 0, w - 50, w + 210, minHeight, maxHeight);
                // Rock on steep slopes, snow on the peaks, pine forests on the gentle lower slopes
                final SlopeMaskNode cliffs = b.slope(38, 90, 6, 0, 2);
                b.wire(cliffs, b.terrain(Terrain.ROCK, 1.2, 1, 2));
                final HeightMaskNode snowline = b.height(w + 140, 4096, 20, 0, 3);
                b.wire(snowline, b.terrain(Terrain.SNOW, 1.1, 1, 3));
                b.wire(snowline, b.flora(Vegetation.SNOW, 1, 2, 3));
                b.wire(snowline, b.biome(MinecraftBiome.JAGGED_PEAKS, 1, 3, 3));
                final HeightMaskNode forestBelt = b.height(w + 3, w + 110, 15, 2, 4);
                final SlopeMaskNode gentle = b.slope(0, 30, 6, 2, 5);
                final CombineNode forests = b.add(new CombineNode(CombineNode.Mode.MULTIPLY), 3, 4);
                b.wire(forestBelt, forests, 0);
                b.wire(gentle, forests, 1);
                b.wire(forests, b.flora(Vegetation.PINE_FOREST, 0.7, 4, 4));
                b.wire(forests, b.biome(MinecraftBiome.TAIGA, 0.9, 5, 4));
                b.biome(MinecraftBiome.MEADOW, 0.55, 5, 3);
                break;
            }
            case ARCHIPELAGO: {
                final NoiseNode islands = b.noise(NoiseNode.Kind.PERLIN, 500, 6, 3, 0, 0);
                final CellsNode bumps = b.add(new CellsNode(), 0, 1);
                bumps.set(CellsNode.SCALE, 350);
                bumps.set(Node.VARIANT, 4);
                final CombineNode land = b.add(new CombineNode(CombineNode.Mode.ADD), 1, 0);
                land.set(CombineNode.STRENGTH, 0.25);
                b.wire(islands, land, 0);
                b.wire(bumps, land, 1);
                final GradientNode falloff = b.add(new GradientNode(), 1, 1);
                final CombineNode masked = b.add(new CombineNode(CombineNode.Mode.MULTIPLY), 2, 0);
                masked.set(CombineNode.STRENGTH, 0.55);
                b.wire(land, masked, 0);
                b.wire(falloff, masked, 1);
                final CurveNode coast = b.add(new CurveNode(0, 0, 0.42f, 0.18f, 0.52f, 0.3f, 0.68f, 0.55f, 1, 1), 3, 0);
                b.wire(masked, coast);
                b.output(coast, 4, 0, w - 40, w + 100, minHeight, maxHeight);
                // Sandy beaches, jungle inland, warm ocean around
                final HeightMaskNode beaches = b.height(w - 6, w + 3, 2, 0, 2);
                b.wire(beaches, b.terrain(Terrain.SAND, 1.2, 1, 2));
                b.wire(beaches, b.biome(MinecraftBiome.BEACH, 1.2, 2, 2));
                final HeightMaskNode inland = b.height(w + 4, 4096, 4, 0, 3);
                b.wire(inland, b.flora(Vegetation.JUNGLE, 0.55, 1, 3));
                b.wire(inland, b.biome(MinecraftBiome.JUNGLE, 1, 2, 3));
                b.biome(MinecraftBiome.WARM_OCEAN, 0.6, 3, 2);
                break;
            }
            case MESA: {
                final NoiseNode base = b.noise(NoiseNode.Kind.PERLIN, 800, 6, 5, 60, 0);
                final CurveNode plateaus = b.add(new CurveNode(0, 0, 0.4f, 0.12f, 0.55f, 0.6f, 0.75f, 0.75f, 1, 1), 1, 0);
                b.wire(base, plateaus);
                final NoiseNode detail = b.noise(NoiseNode.Kind.BILLOW, 300, 4, 7, 0, 1);
                final CombineNode layers = b.add(new CombineNode(CombineNode.Mode.ADD), 2, 0);
                layers.set(CombineNode.STRENGTH, 0.2);
                b.wire(plateaus, layers, 0);
                b.wire(detail, layers, 1);
                final TerraceNode terraces = b.add(new TerraceNode(), 3, 0);
                terraces.set(TerraceNode.STEPS, 6);
                terraces.set(TerraceNode.SHARPNESS, 0.85);
                b.wire(layers, terraces);
                final ThermalErosionNode crumble = b.add(new ThermalErosionNode(), 4, 0);
                crumble.set(ErosionNode.ITERATIONS, 10.0);
                crumble.set(ThermalErosionNode.ANGLE, 65.0);
                b.wire(terraces, crumble);
                b.output(crumble, 5, 0, w - 6, w + 100, minHeight, maxHeight);
                // Banded terracotta on the slopes, red sand and cacti on the flat tops
                b.terrain(Terrain.MESA, 0.7, 1, 2);
                final SlopeMaskNode flat = b.slope(0, 12, 4, 0, 3);
                final HeightMaskNode high = b.height(w + 30, 4096, 10, 0, 4);
                final CombineNode tops = b.add(new CombineNode(CombineNode.Mode.MULTIPLY), 1, 3);
                b.wire(flat, tops, 0);
                b.wire(high, tops, 1);
                b.wire(tops, b.terrain(Terrain.RED_SAND, 1, 2, 3));
                b.wire(tops, b.flora(Vegetation.DESERT_PLANTS, 0.04, 3, 3));
                b.biome(MinecraftBiome.BADLANDS, 0.6, 2, 2);
                break;
            }
            case DESERT_DUNES: {
                final NoiseNode swell = b.noise(NoiseNode.Kind.PERLIN, 2000, 3, 8, 0, 0);
                final CurveNode ground = b.add(new CurveNode(0, 0.15f, 1, 0.45f), 1, 0);
                b.wire(swell, ground);
                final NoiseNode dunes = b.noise(NoiseNode.Kind.DUNES, 180, 4, 9, 40, 1);
                dunes.set(NoiseNode.DIRECTION, 30);
                final CombineNode desert = b.add(new CombineNode(CombineNode.Mode.ADD), 2, 0);
                desert.set(CombineNode.STRENGTH, 0.5);
                b.wire(ground, desert, 0);
                b.wire(dunes, desert, 1);
                b.output(desert, 3, 0, w - 10, w + 70, minHeight, maxHeight);
                // Sand everywhere, sandstone on the steep lee sides, a few dead shrubs and cacti
                b.terrain(Terrain.SAND, 1, 1, 2);
                b.wire(b.slope(30, 90, 5, 0, 3), b.terrain(Terrain.SANDSTONE, 1.2, 1, 3));
                b.flora(Vegetation.DESERT_PLANTS, 0.015, 2, 2);
                b.biome(MinecraftBiome.DESERT, 1, 3, 2);
                break;
            }
            case VOLCANIC_ISLAND: {
                final GradientNode cone = b.add(new GradientNode(), 0, 0);
                cone.set(GradientNode.SMOOTH, false);
                // A steepening cone with a caldera at the top
                final CurveNode volcano = b.add(new CurveNode(0, 0, 0.15f, 0.08f, 0.45f, 0.38f, 0.82f, 0.9f, 0.91f, 1, 1, 0.78f), 1, 0);
                b.wire(cone, volcano);
                final NoiseNode ridges = b.noise(NoiseNode.Kind.RIDGED, 300, 6, 10, 0, 1);
                final CombineNode ribs = b.add(new CombineNode(CombineNode.Mode.MULTIPLY), 1, 1);
                b.wire(ridges, ribs, 0);
                b.wire(volcano, ribs, 1);
                final CombineNode mountain = b.add(new CombineNode(CombineNode.Mode.ADD), 2, 0);
                mountain.set(CombineNode.STRENGTH, 0.15);
                b.wire(volcano, mountain, 0);
                b.wire(ribs, mountain, 1);
                final GullyErosionNode erosion = b.add(new GullyErosionNode(), 3, 0);
                erosion.set(GullyErosionNode.DEPTH, 30);
                erosion.set(GullyErosionNode.SPACING, 96);
                erosion.set(Node.VARIANT, 12);
                b.wire(mountain, erosion);
                b.output(erosion, 4, 0, w - 50, w + 190, minHeight, maxHeight);
                // Bare stone, a magma filled caldera, a green belt with jungle and beaches around
                b.terrain(Terrain.STONE_MIX, 0.6, 1, 2);
                final CurveNode caldera = b.add(new CurveNode(0, 0, 0.93f, 0, 0.96f, 1, 1, 1), 1, 3);
                b.wire(cone, caldera);
                b.wire(caldera, b.terrain(Terrain.MAGMA, 1.5, 2, 3));
                final HeightMaskNode belt = b.height(w + 3, w + 60, 10, 0, 4);
                final SlopeMaskNode gentle = b.slope(0, 30, 6, 0, 5);
                final CombineNode green = b.add(new CombineNode(CombineNode.Mode.MULTIPLY), 1, 4);
                b.wire(belt, green, 0);
                b.wire(gentle, green, 1);
                b.wire(green, b.terrain(Terrain.GRASS, 1, 2, 4));
                b.wire(green, b.flora(Vegetation.JUNGLE, 0.5, 3, 4));
                b.wire(green, b.biome(MinecraftBiome.JUNGLE, 1, 4, 4));
                final HeightMaskNode beaches = b.height(w - 6, w + 2, 2, 2, 5);
                b.wire(beaches, b.terrain(Terrain.SAND, 1.2, 3, 5));
                b.wire(beaches, b.biome(MinecraftBiome.BEACH, 1.2, 4, 5));
                b.biome(MinecraftBiome.STONY_PEAKS, 0.6, 3, 2);
                b.wire(b.height(-4096, w - 1, 0, 3, 1), b.biome(MinecraftBiome.WARM_OCEAN, 1, 4, 2));
                break;
            }
            case LAKES_AND_FORESTS: {
                final NoiseNode land = b.noise(NoiseNode.Kind.PERLIN, 900, 5, 12, 0, 0);
                final CellsNode basins = b.add(new CellsNode(), 0, 1);
                basins.set(CellsNode.SCALE, 500);
                basins.set(Node.VARIANT, 13);
                final CurveNode lakeShapes = b.add(new CurveNode(0, 0, 0.6f, 0, 1, 1), 1, 1);
                b.wire(basins, lakeShapes);
                final CombineNode lakes = b.add(new CombineNode(CombineNode.Mode.SUBTRACT), 2, 0);
                lakes.set(CombineNode.STRENGTH, 0.6);
                b.wire(land, lakes, 0);
                b.wire(lakeShapes, lakes, 1);
                b.output(lakes, 3, 0, w - 30, w + 60, minHeight, maxHeight);
                // Deciduous forests and birch woods with meadows of grass and flowers in between
                final NoiseNode woods = b.noise(NoiseNode.Kind.PERLIN, 350, 4, 14, 0, 2);
                final CurveNode forestMask = b.add(new CurveNode(0, 0, 0.45f, 0, 0.58f, 1, 1, 1), 1, 2);
                b.wire(woods, forestMask);
                b.wire(forestMask, b.flora(Vegetation.DECIDUOUS_FOREST, 0.7, 2, 2));
                b.wire(forestMask, b.biome(MinecraftBiome.FOREST, 1, 3, 2));
                final NoiseNode birch = b.noise(NoiseNode.Kind.PERLIN, 600, 3, 15, 0, 3);
                final CurveNode birchMask = b.add(new CurveNode(0, 0, 0.55f, 0, 0.65f, 1, 1, 1), 1, 3);
                b.wire(birch, birchMask);
                b.wire(birchMask, b.biome(MinecraftBiome.BIRCH_FOREST, 1.1, 2, 3));
                b.flora(Vegetation.GRASS_AND_FLOWERS, 0.3, 3, 3);
                b.flora(Vegetation.FLOWERS, 0.04, 4, 3);
                b.biome(MinecraftBiome.PLAINS, 0.6, 4, 2);
                break;
            }
            case RIVER_VALLEYS: {
                final NoiseNode land = b.noise(NoiseNode.Kind.PERLIN, 1400, 6, 20, 0, 0);
                final CurveNode shape = b.add(new CurveNode(0, 0.05f, 0.5f, 0.3f, 1, 0.75f), 1, 0);
                b.wire(land, shape);
                final LakeNode lakes = b.add(new LakeNode(), 2, 0);
                lakes.set(LakeNode.SIZE, 80);
                lakes.set(LakeNode.DEPTH, 7);
                lakes.set(LakeNode.MAX_HEIGHT, w + 80);
                lakes.set(Node.VARIANT, 21);
                b.wire(shape, lakes);
                final RiverNode rivers = b.add(new RiverNode(), 3, 0);
                rivers.set(RiverNode.SPACING, 1100);
                rivers.set(RiverNode.WIDTH, 9);
                rivers.set(RiverNode.MAX_HEIGHT, w + 70);
                rivers.set(Node.VARIANT, 22);
                b.wire(lakes, rivers);
                b.output(rivers, 4, 0, w - 20, w + 110, minHeight, maxHeight);
                // Woods and flowery meadows in the valleys, pines and taiga higher up; the rivers paint their own biome
                final NoiseNode woods = b.noise(NoiseNode.Kind.PERLIN, 400, 4, 23, 0, 1);
                final CurveNode forestMask = b.add(new CurveNode(0, 0, 0.48f, 0, 0.6f, 1, 1, 1), 1, 1);
                b.wire(woods, forestMask);
                b.wire(forestMask, b.flora(Vegetation.DECIDUOUS_FOREST, 0.6, 2, 1));
                b.wire(forestMask, b.biome(MinecraftBiome.FOREST, 1, 3, 1));
                b.flora(Vegetation.GRASS_AND_FLOWERS, 0.3, 2, 2);
                b.biome(MinecraftBiome.PLAINS, 0.6, 3, 2);
                final HeightMaskNode highlands = b.height(w + 55, 4096, 15, 0, 3);
                b.wire(highlands, b.flora(Vegetation.PINE_FOREST, 0.6, 1, 3));
                b.wire(highlands, b.biome(MinecraftBiome.TAIGA, 1.2, 2, 3));
                break;
            }
            case TOWER_KARST: {
                // Low, gently rolling jungle floor with tall, fluted rock towers rising out of it
                final NoiseNode ground = b.noise(NoiseNode.Kind.PERLIN, 500, 4, 31, 0, 0);
                final CurveNode floor = b.add(new CurveNode(0, 0.02f, 1, 0.12f), 1, 0);
                b.wire(ground, floor);
                final TowersNode towers = b.add(new TowersNode(), 0, 1);
                towers.set(TowersNode.SPACING, 90);
                towers.set(TowersNode.WIDTH, 0.3);
                towers.set(TowersNode.DENSITY, 0.8);
                towers.set(TowersNode.HEIGHT_VARIATION, 0.55);
                towers.set(Node.VARIANT, 32);
                final StrataNode ledges = b.add(new StrataNode(), 1, 1);
                ledges.set(StrataNode.LAYERS, 20);
                ledges.set(StrataNode.STRENGTH, 0.35);
                ledges.set(Node.VARIANT, 33);
                b.wire(towers, ledges);
                final CombineNode land = b.add(new CombineNode(CombineNode.Mode.MAX), 2, 0);
                b.wire(floor, land, 0);
                b.wire(ledges, land, 1);
                b.output(land, 3, 0, w + 2, w + 150, minHeight, maxHeight);
                // Mossy rock walls, green tops, and a dense rainforest on the ground
                final SlopeMaskNode walls = b.slope(45, 90, 6, 0, 2);
                b.wire(walls, b.material(MaterialGroup.MOSSY_ROCK, 1.3, 1, 2));
                final SlopeMaskNode gentle = b.slope(0, 35, 6, 0, 3);
                b.wire(gentle, b.material(MaterialGroup.FOREST_FLOOR, 1, 1, 3));
                final HeightMaskNode lowland = b.height(w, w + 20, 6, 0, 4);
                final CombineNode jungleFloor = b.add(new CombineNode(CombineNode.Mode.MULTIPLY), 1, 4);
                b.wire(lowland, jungleFloor, 0);
                b.wire(gentle, jungleFloor, 1);
                b.wire(jungleFloor, b.flora(Vegetation.RAINFOREST, 0.75, 2, 4));
                b.wire(jungleFloor, b.flora(Vegetation.BUSHES, 0.5, 2, 5));
                final HeightMaskNode summits = b.height(w + 40, 4096, 10, 0, 5);
                final CombineNode tops = b.add(new CombineNode(CombineNode.Mode.MULTIPLY), 1, 5);
                b.wire(summits, tops, 0);
                b.wire(gentle, tops, 1);
                b.wire(tops, b.flora(Vegetation.BUSHES, 0.6, 3, 5));
                b.biome(MinecraftBiome.JUNGLE, 0.6, 3, 2);
                break;
            }
            case RED_CLIFFS: {
                // Stacked plateaus with layered red rock walls, snow on the ledges and tall spruces on the shelves
                final PlateausNode plateaus = b.add(new PlateausNode(), 0, 0);
                plateaus.set(PlateausNode.SCALE, 700);
                plateaus.set(PlateausNode.LEVELS, 3);
                plateaus.set(PlateausNode.COVERAGE, 0.6);
                plateaus.set(Node.VARIANT, 41);
                final NoiseNode relief = b.noise(NoiseNode.Kind.RIDGED, 260, 5, 42, 40, 1);
                final CombineNode rough = b.add(new CombineNode(CombineNode.Mode.ADD), 1, 0);
                rough.set(CombineNode.STRENGTH, 0.12);
                b.wire(plateaus, rough, 0);
                b.wire(relief, rough, 1);
                final StrataNode strata = b.add(new StrataNode(), 2, 0);
                strata.set(StrataNode.LAYERS, 26);
                strata.set(StrataNode.SHARPNESS, 0.75);
                strata.set(Node.VARIANT, 43);
                b.wire(rough, strata);
                final ThermalErosionNode crumble = b.add(new ThermalErosionNode(), 3, 0);
                crumble.set(ErosionNode.ITERATIONS, 8.0);
                crumble.set(ThermalErosionNode.ANGLE, 70.0);
                b.wire(strata, crumble);
                b.output(crumble, 4, 0, w + 4, w + 190, minHeight, maxHeight);
                final SlopeMaskNode walls = b.slope(40, 90, 6, 0, 2);
                b.wire(walls, b.material(MaterialGroup.RED_CLIFFS, 1.4, 1, 2));
                final SlopeMaskNode ledges = b.slope(0, 30, 6, 0, 3);
                final HeightMaskNode high = b.height(w + 110, 4096, 12, 0, 4);
                final CombineNode snowy = b.add(new CombineNode(CombineNode.Mode.MULTIPLY), 1, 4);
                b.wire(ledges, snowy, 0);
                b.wire(high, snowy, 1);
                b.wire(snowy, b.material(MaterialGroup.SNOWY_ROCK, 1.2, 2, 4));
                b.wire(snowy, b.flora(Vegetation.SNOW, 1, 3, 4));
                b.wire(ledges, b.material(MaterialGroup.FOREST_FLOOR, 1, 1, 3));
                b.wire(ledges, b.flora(Vegetation.TALL_SPRUCES, 0.45, 2, 3));
                b.wire(ledges, b.flora(Vegetation.GRASS_AND_FLOWERS, 0.25, 3, 3));
                b.biome(MinecraftBiome.WINDSWEPT_HILLS, 0.6, 3, 2);
                break;
            }
            case ALPINE_VALLEY: {
                // High ridged mountains around a wide valley with a river and lakes; firs and spruces below the snow
                final NoiseNode base = b.noise(NoiseNode.Kind.PERLIN, 2000, 4, 51, 0, 0);
                final NoiseNode peaks = b.noise(NoiseNode.Kind.RIDGED, 700, 7, 52, 80, 1);
                final CurveNode ranges = b.add(new CurveNode(0, 0, 0.35f, 0.04f, 0.7f, 0.45f, 1, 0.55f), 1, 0);
                b.wire(base, ranges);
                final CombineNode mountains = b.add(new CombineNode(CombineNode.Mode.MULTIPLY), 2, 0);
                b.wire(peaks, mountains, 0);
                b.wire(ranges, mountains, 1);
                final CombineNode land = b.add(new CombineNode(CombineNode.Mode.ADD), 3, 0);
                land.set(CombineNode.STRENGTH, 0.5);
                b.wire(ranges, land, 0);
                b.wire(mountains, land, 1);
                // Keep the sum within range, with wide valley floors
                final ClampNode heights = b.add(new ClampNode(), 3, 1);
                b.wire(land, heights);
                final GullyErosionNode erosion = b.add(new GullyErosionNode(), 4, 0);
                erosion.set(GullyErosionNode.DEPTH, 45);
                erosion.set(Node.VARIANT, 53);
                b.wire(heights, erosion);
                final LakeNode lakes = b.add(new LakeNode(), 5, 0);
                lakes.set(LakeNode.MAX_HEIGHT, w + 40);
                lakes.set(Node.VARIANT, 54);
                b.wire(erosion, lakes);
                final RiverNode rivers = b.add(new RiverNode(), 6, 0);
                rivers.set(RiverNode.MAX_HEIGHT, w + 60);
                rivers.set(RiverNode.WIDTH, 7);
                rivers.set(Node.VARIANT, 55);
                b.wire(lakes, rivers);
                b.output(rivers, 7, 0, w - 4, w + 200, minHeight, maxHeight);
                final SlopeMaskNode cliffs = b.slope(42, 90, 6, 0, 2);
                b.wire(cliffs, b.material(MaterialGroup.GREY_CLIFFS, 1.3, 1, 2));
                final SlopeMaskNode steep = b.slope(30, 42, 4, 0, 3);
                b.wire(steep, b.material(MaterialGroup.SCREE, 1.1, 1, 3));
                final HeightMaskNode snowline = b.height(w + 150, 4096, 25, 2, 2);
                b.wire(snowline, b.material(MaterialGroup.SNOWY_ROCK, 1.2, 3, 2));
                b.wire(snowline, b.flora(Vegetation.SNOW, 1, 4, 2));
                b.wire(snowline, b.biome(MinecraftBiome.SNOWY_SLOPES, 1.1, 5, 2));
                final HeightMaskNode forestBelt = b.height(w + 2, w + 140, 15, 2, 4);
                final SlopeMaskNode gentle = b.slope(0, 32, 6, 2, 5);
                final CombineNode forests = b.add(new CombineNode(CombineNode.Mode.MULTIPLY), 3, 4);
                b.wire(forestBelt, forests, 0);
                b.wire(gentle, forests, 1);
                b.wire(forests, b.material(MaterialGroup.FOREST_FLOOR, 1, 4, 3));
                b.wire(forests, b.flora(Vegetation.FIRS, 0.55, 4, 4));
                b.wire(forests, b.flora(Vegetation.TALL_SPRUCES, 0.3, 4, 5));
                b.wire(forests, b.biome(MinecraftBiome.OLD_GROWTH_SPRUCE_TAIGA, 1, 5, 4));
                b.biome(MinecraftBiome.MEADOW, 0.55, 5, 5);
                break;
            }
            case CONTINENT: {
                // The shape of the land: a large continent filling most of the map (noise faded out towards the
                // edges of the map, so that it is surrounded by sea), with an archipelago of smaller islands around it
                final NoiseNode base = b.noise(NoiseNode.Kind.PERLIN, 1900, 6, 61, 160, 0);
                final GradientNode falloff = b.add(new GradientNode(), 0, 1);
                falloff.set(GradientNode.SHAPE, GradientNode.Shape.SQUARE);
                final CurveNode edges = b.add(new CurveNode(0, 0, 0.12f, 0.3f, 0.3f, 0.92f, 1, 1), 1, 1);
                b.wire(falloff, edges);
                // A radial gradient rounds off the corners and pulls the land towards the middle of the map
                final GradientNode centre = b.add(new GradientNode(), 0, 2);
                final CombineNode centred = b.add(new CombineNode(CombineNode.Mode.ADD), 1, 2);
                centred.set(CombineNode.STRENGTH, 0.5);
                b.wire(base, centred, 0);
                b.wire(centre, centred, 1);
                final CombineNode shaped = b.add(new CombineNode(CombineNode.Mode.MULTIPLY), 1, 0);
                b.wire(centred, shaped, 0);
                b.wire(edges, shaped, 1);
                // Sea level is at a value of 40 / 240 = 0.17: a continental shelf, coasts, rolling lowlands
                final CurveNode continent = b.add(new CurveNode(0, 0, 0.42f, 0.1f, 0.56f, 0.165f, 0.64f, 0.2f, 0.95f, 0.3f, 1.3f, 0.38f, 2, 0.4f), 2, 0);
                b.wire(shaped, continent);
                final NoiseNode islandNoise = b.noise(NoiseNode.Kind.PERLIN, 230, 5, 62, 25, 3);
                final CurveNode islandShapes = b.add(new CurveNode(0, 0, 0.6f, 0.06f, 0.7f, 0.19f, 1, 0.28f), 1, 3);
                b.wire(islandNoise, islandShapes);
                final CurveNode islandZone = b.add(new CurveNode(0, 0, 0.04f, 0.3f, 0.12f, 1, 0.38f, 1, 0.5f, 0, 1, 0), 2, 3);
                b.wire(centre, islandZone);
                final CombineNode islands = b.add(new CombineNode(CombineNode.Mode.MULTIPLY), 3, 2);
                b.wire(islandShapes, islands, 0);
                b.wire(islandZone, islands, 1);
                final CombineNode land = b.add(new CombineNode(CombineNode.Mode.MAX), 3, 0);
                b.wire(continent, land, 0);
                b.wire(islands, land, 1);
                // A large mountain range, mostly towards the north east, carved by erosion
                final NoiseNode rangeNoise = b.noise(NoiseNode.Kind.PERLIN, 2400, 3, 64, 300, 5);
                final GradientNode northEast = b.add(new GradientNode(), 0, 6);
                northEast.set(GradientNode.SHAPE, GradientNode.Shape.LINEAR);
                northEast.set(GradientNode.ANGLE, 315);
                final CombineNode climate = b.add(new CombineNode(CombineNode.Mode.ADD), 1, 5);
                climate.set(CombineNode.STRENGTH, 1.1);
                b.wire(rangeNoise, climate, 0);
                b.wire(northEast, climate, 1);
                final CurveNode rangeZone = b.add(new CurveNode(0, 0, 0.95f, 0, 1.25f, 1, 2.5f, 1), 2, 5);
                b.wire(climate, rangeZone);
                final CurveNode onLand = b.add(new CurveNode(0, 0, 0.58f, 0, 0.72f, 1, 2, 1), 2, 4);
                b.wire(shaped, onLand);
                final CombineNode zone = b.add(new CombineNode(CombineNode.Mode.MULTIPLY), 3, 5);
                b.wire(rangeZone, zone, 0);
                b.wire(onLand, zone, 1);
                final NoiseNode peaks = b.noise(NoiseNode.Kind.RIDGED, 600, 7, 63, 30, 4);
                final CombineNode mountains = b.add(new CombineNode(CombineNode.Mode.MULTIPLY), 3, 4);
                b.wire(peaks, mountains, 0);
                b.wire(zone, mountains, 1);
                final GullyErosionNode erosion = b.add(new GullyErosionNode(), 4, 4);
                erosion.set(GullyErosionNode.DEPTH, 30);
                erosion.set(Node.VARIANT, 66);
                b.wire(mountains, erosion);
                final CombineNode relief = b.add(new CombineNode(CombineNode.Mode.ADD), 4, 0);
                relief.set(CombineNode.STRENGTH, 1.05);
                b.wire(land, relief, 0);
                b.wire(erosion, relief, 1);
                // A volcanic island in the south west
                final VolcanoNode volcano = b.add(new VolcanoNode(), 3, 6);
                volcano.set(VolcanoNode.POSITION_X, 0.13);
                volcano.set(VolcanoNode.POSITION_Y, 0.76);
                volcano.set(VolcanoNode.RADIUS, 380);
                volcano.set(Node.VARIANT, 65);
                final CurveNode volcanoHeight = b.add(new CurveNode(0, 0, 0.01f, 0.18f, 1, 0.82f), 4, 6);
                b.wire(volcano, volcanoHeight);
                final CombineNode withVolcano = b.add(new CombineNode(CombineNode.Mode.MAX), 5, 0);
                b.wire(relief, withVolcano, 0);
                b.wire(volcanoHeight, withVolcano, 1);
                final ClampNode clamp = b.add(new ClampNode(), 6, 0);
                b.wire(withVolcano, clamp);
                final LakeNode lakes = b.add(new LakeNode(), 7, 0);
                lakes.set(LakeNode.MAX_HEIGHT, w + 45);
                lakes.set(Node.VARIANT, 67);
                b.wire(clamp, lakes);
                final RiverNode rivers = b.add(new RiverNode(), 8, 0);
                rivers.set(RiverNode.SPACING, 1500);
                rivers.set(RiverNode.WIDTH, 10);
                rivers.set(RiverNode.MAX_HEIGHT, w + 90);
                rivers.set(RiverNode.MIN_HEIGHT, w - 4);
                rivers.set(Node.VARIANT, 68);
                b.wire(lakes, rivers);
                b.output(rivers, 9, 0, w - 40, w + 200, minHeight, maxHeight);

                // The surface. Snow on the high mountains, rock on cliffs, scree below them
                final HeightMaskNode snowline = b.height(w + 120, 4096, 20, 0, 8);
                b.wire(snowline, b.material(MaterialGroup.SNOWY_ROCK, 1.3, 1, 8));
                b.wire(snowline, b.flora(Vegetation.SNOW, 1, 2, 8));
                b.wire(snowline, b.biome(MinecraftBiome.FROZEN_PEAKS, 1.3, 3, 8));
                final SlopeMaskNode cliffs = b.slope(42, 90, 6, 0, 9);
                b.wire(cliffs, b.material(MaterialGroup.GREY_CLIFFS, 1.2, 1, 9));
                final SlopeMaskNode steep = b.slope(30, 42, 4, 0, 10);
                b.wire(steep, b.material(MaterialGroup.SCREE, 1.05, 1, 10));
                // Beaches, and coral reefs in patches of the shallow sea
                final HeightMaskNode coast = b.height(w - 3, w + 3, 1, 0, 11);
                b.wire(coast, b.material(MaterialGroup.BEACH, 1.25, 1, 11));
                b.wire(coast, b.biome(MinecraftBiome.BEACH, 1.25, 2, 11));
                final HeightMaskNode shallows = b.height(w - 12, w - 2, 3, 0, 12);
                final NoiseNode reefNoise = b.noise(NoiseNode.Kind.PERLIN, 90, 3, 69, 0, 13);
                final CurveNode reefPatches = b.add(new CurveNode(0, 0, 0.5f, 0, 0.62f, 1, 1, 1), 1, 13);
                b.wire(reefNoise, reefPatches);
                final CombineNode reefs = b.add(new CombineNode(CombineNode.Mode.MULTIPLY), 1, 12);
                b.wire(shallows, reefs, 0);
                b.wire(reefPatches, reefs, 1);
                b.wire(reefs, b.material(MaterialGroup.CORAL_REEF, 1.2, 2, 12));
                b.wire(shallows, b.biome(MinecraftBiome.WARM_OCEAN, 1, 3, 12));
                b.biome(MinecraftBiome.OCEAN, 0.4, 3, 13);
                // The volcano: dark volcanic rock
                final CurveNode volcanoMask = b.add(new CurveNode(0, 0, 0.01f, 0, 0.04f, 1, 1, 1), 5, 6);
                b.wire(volcano, volcanoMask);
                b.wire(volcanoMask, b.material(MaterialGroup.VOLCANIC, 1.6, 6, 6));
                // Forests everywhere on the lowlands, firs and spruces higher up
                final HeightMaskNode lowlands = b.height(w + 2, w + 110, 15, 4, 8);
                final HeightMaskNode highlands = b.height(w + 85, w + 130, 15, 4, 13);
                final SlopeMaskNode gentle = b.slope(0, 34, 6, 4, 9);
                final CombineNode plantable = b.add(new CombineNode(CombineNode.Mode.MULTIPLY), 5, 8);
                b.wire(lowlands, plantable, 0);
                b.wire(gentle, plantable, 1);
                // Meadows everywhere, with patches of forest
                b.wire(plantable, b.material(MaterialGroup.MEADOW, 0.8, 6, 7));
                b.wire(plantable, b.flora(Vegetation.GRASS_AND_FLOWERS, 0.35, 7, 7));
                b.wire(plantable, b.biome(MinecraftBiome.PLAINS, 0.75, 8, 7));
                final NoiseNode woodNoise = b.noise(NoiseNode.Kind.PERLIN, 420, 4, 72, 60, 14);
                final CurveNode woodPatches = b.add(new CurveNode(0, 0, 0.47f, 0, 0.56f, 1, 1, 1), 5, 14);
                b.wire(woodNoise, woodPatches);
                final CombineNode woods = b.add(new CombineNode(CombineNode.Mode.MULTIPLY), 6, 14);
                b.wire(woodPatches, woods, 0);
                b.wire(plantable, woods, 1);
                b.wire(woods, b.material(MaterialGroup.FOREST_FLOOR, 0.9, 7, 14));
                b.wire(woods, b.flora(Vegetation.BIG_OAKS, 0.5, 8, 14));
                b.wire(woods, b.flora(Vegetation.BIRCHES, 0.3, 9, 14));
                b.wire(woods, b.biome(MinecraftBiome.FOREST, 0.9, 10, 14));
                final CombineNode alpine = b.add(new CombineNode(CombineNode.Mode.MULTIPLY), 5, 13);
                b.wire(highlands, alpine, 0);
                b.wire(gentle, alpine, 1);
                b.wire(alpine, b.flora(Vegetation.FIRS, 0.5, 6, 13));
                b.wire(alpine, b.flora(Vegetation.TALL_SPRUCES, 0.25, 7, 13));
                b.wire(alpine, b.biome(MinecraftBiome.OLD_GROWTH_SPRUCE_TAIGA, 1.1, 8, 13));
                // Cherry groves in some regions
                final CombineNode cherry = b.add(new CombineNode(CombineNode.Mode.MULTIPLY), 6, 9);
                final RegionsNode cherryRegions = b.regions(6, 0, 70, 5, 9);
                cherryRegions.set(RegionsNode.SIZE, 700);
                b.wire(cherryRegions, cherry, 0);
                b.wire(plantable, cherry, 1);
                b.wire(cherry, b.material(MaterialGroup.MEADOW, 1.1, 7, 9));
                b.wire(cherry, b.flora(Vegetation.CHERRY_TREES, 0.55, 8, 9));
                b.wire(cherry, b.flora(Vegetation.FLOWERS, 0.35, 9, 9));
                b.wire(cherry, b.biome(MinecraftBiome.CHERRY_GROVE, 1.1, 10, 9));
                // Badlands and savanna in the south
                final GradientNode south = b.add(new GradientNode(), 4, 10);
                south.set(GradientNode.SHAPE, GradientNode.Shape.LINEAR);
                south.set(GradientNode.ANGLE, 90);
                final NoiseNode dryNoise = b.noise(NoiseNode.Kind.PERLIN, 1200, 4, 71, 200, 11);
                final CombineNode dryness = b.add(new CombineNode(CombineNode.Mode.ADD), 5, 11);
                dryness.set(CombineNode.STRENGTH, 0.8);
                b.wire(dryNoise, dryness, 0);
                b.wire(south, dryness, 1);
                final CurveNode badlandZone = b.add(new CurveNode(0, 0, 1.2f, 0, 1.28f, 1, 2, 1), 6, 11);
                b.wire(dryness, badlandZone);
                final CombineNode badlands = b.add(new CombineNode(CombineNode.Mode.MULTIPLY), 7, 11);
                b.wire(badlandZone, badlands, 0);
                b.wire(lowlands, badlands, 1);
                b.wire(badlands, b.material(MaterialGroup.RED_CLIFFS, 1.15, 8, 11));
                b.wire(badlands, b.flora(Vegetation.DEAD_TREES, 0.04, 9, 11));
                b.wire(badlands, b.biome(MinecraftBiome.BADLANDS, 1.15, 10, 11));
                final CurveNode savannaZone = b.add(new CurveNode(0, 0, 1.08f, 0, 1.13f, 1, 1.18f, 1, 1.24f, 0, 2, 0), 6, 10);
                b.wire(dryness, savannaZone);
                final CombineNode savanna = b.add(new CombineNode(CombineNode.Mode.MULTIPLY), 7, 10);
                b.wire(savannaZone, savanna, 0);
                b.wire(plantable, savanna, 1);
                b.wire(savanna, b.material(MaterialGroup.MIXED_DIRT, 1.05, 8, 10));
                b.wire(savanna, b.flora(Vegetation.TALL_GRASS, 0.5, 9, 10));
                b.wire(savanna, b.biome(MinecraftBiome.SAVANNA, 1.05, 10, 10));
                break;
            }
            case CORAL_ARCHIPELAGO: {
                // Heights: 0 is Y w - 90 (the deep sea floor), 1 is Y w + 150. Sea level is at 90 / 240 = 0.375.
                // "Island potential": clusters of islands, the islands within them, raised around the centrepiece
                // mountain and around the blue hole, and faded out towards the edges of the map
                final NoiseNode clusters = b.noise(NoiseNode.Kind.PERLIN, 1300, 4, 81, 200, 0);
                final NoiseNode detail = b.noise(NoiseNode.Kind.PERLIN, 380, 6, 82, 35, 1);
                final CombineNode islandField = b.add(new CombineNode(CombineNode.Mode.ADD), 1, 0);
                islandField.set(CombineNode.STRENGTH, 0.6);
                b.wire(detail, islandField, 0);
                b.wire(clusters, islandField, 1);
                final GradientNode middle = b.add(new GradientNode(), 0, 2);
                middle.set(GradientNode.FIT_TO_MAP, false);
                middle.set(GradientNode.SIZE, 650);
                final CombineNode raised = b.add(new CombineNode(CombineNode.Mode.ADD), 2, 0);
                raised.set(CombineNode.STRENGTH, 0.35);
                b.wire(islandField, raised, 0);
                b.wire(middle, raised, 1);
                final GradientNode holeShelf = b.add(new GradientNode(), 0, 3);
                holeShelf.set(GradientNode.FIT_TO_MAP, false);
                holeShelf.set(GradientNode.CENTRE_X, -620);
                holeShelf.set(GradientNode.CENTRE_Y, 720);
                holeShelf.set(GradientNode.SIZE, 360);
                final CurveNode holeShelfShape = b.add(new CurveNode(0, 0, 0.5f, 0.6f, 1, 0.6f), 1, 3);
                b.wire(holeShelf, holeShelfShape);
                final CombineNode withShelf = b.add(new CombineNode(CombineNode.Mode.MAX), 3, 0);
                b.wire(raised, withShelf, 0);
                // The shelf around the blue hole: at reef flat level (in potential terms about 0.95)
                final CurveNode holeShelfLevel = b.add(new CurveNode(0, 0, 0.01f, 0.6f, 0.6f, 0.95f, 1, 0.95f), 2, 3);
                b.wire(holeShelfShape, holeShelfLevel);
                b.wire(holeShelfLevel, withShelf, 1);
                final GradientNode edgesOfMap = b.add(new GradientNode(), 0, 4);
                final CurveNode edgeFade = b.add(new CurveNode(0, 0.45f, 0.25f, 1, 1, 1), 1, 4);
                b.wire(edgesOfMap, edgeFade);
                final CombineNode potential = b.add(new CombineNode(CombineNode.Mode.MULTIPLY), 4, 0);
                b.wire(withShelf, potential, 0);
                b.wire(edgeFade, potential, 1);
                // From potential to height: a deep sea floor, a gradual slope up to wide, nearly flat reef flats just
                // under the surface, wide beaches, then the land
                final CurveNode profile = b.add(new CurveNode(0, 0.12f, 0.45f, 0.13f, 0.66f, 0.26f, 0.78f, 0.345f, 0.98f, 0.366f,
                        1.03f, 0.377f, 1.1f, 0.39f, 1.18f, 0.415f, 1.4f, 0.48f, 1.9f, 0.58f), 5, 0);
                b.wire(potential, profile);
                // Rugged mountains on the larger islands
                final CurveNode landMask = b.add(new CurveNode(0, 0, 1.15f, 0, 1.5f, 1, 2, 1), 5, 1);
                b.wire(potential, landMask);
                final NoiseNode ridges = b.noise(NoiseNode.Kind.RIDGED, 520, 4, 83, 60, 5);
                final CombineNode mountains = b.add(new CombineNode(CombineNode.Mode.MULTIPLY), 6, 1);
                b.wire(ridges, mountains, 0);
                b.wire(landMask, mountains, 1);
                final TowersNode karst = b.add(new TowersNode(), 0, 6);
                karst.set(TowersNode.SPACING, 130);
                karst.set(TowersNode.WIDTH, 0.28);
                karst.set(TowersNode.DENSITY, 0.55);
                karst.set(Node.VARIANT, 84);
                final CombineNode karstOnLand = b.add(new CombineNode(CombineNode.Mode.MULTIPLY), 6, 2);
                b.wire(karst, karstOnLand, 0);
                b.wire(landMask, karstOnLand, 1);
                final CombineNode rugged = b.add(new CombineNode(CombineNode.Mode.ADD), 7, 1);
                rugged.set(CombineNode.STRENGTH, 0.35);
                b.wire(mountains, rugged, 0);
                b.wire(karstOnLand, rugged, 1);
                final BlurNode softened = b.add(new BlurNode(), 8, 1);
                softened.set(BlurNode.RADIUS, 3);
                b.wire(rugged, softened);
                final CombineNode relief = b.add(new CombineNode(CombineNode.Mode.ADD), 7, 0);
                relief.set(CombineNode.STRENGTH, 0.2);
                b.wire(profile, relief, 0);
                b.wire(softened, relief, 1);
                // The centrepiece: a high, jagged mountain island in the middle
                final GradientNode peak = b.add(new GradientNode(), 5, 3);
                peak.set(GradientNode.FIT_TO_MAP, false);
                peak.set(GradientNode.CENTRE_X, 60);
                peak.set(GradientNode.CENTRE_Y, -90);
                peak.set(GradientNode.SIZE, 360);
                final CurveNode peakCone = b.add(new CurveNode(0, 0, 0.3f, 0.12f, 0.7f, 0.55f, 1, 1), 6, 3);
                b.wire(peak, peakCone);
                final NoiseNode peakRidges = b.noise(NoiseNode.Kind.RIDGED, 200, 5, 86, 40, 7);
                final CombineNode jagged = b.add(new CombineNode(CombineNode.Mode.MULTIPLY), 6, 4);
                b.wire(peakCone, jagged, 0);
                b.wire(peakRidges, jagged, 1);
                final CombineNode massif = b.add(new CombineNode(CombineNode.Mode.ADD), 7, 4);
                massif.set(CombineNode.STRENGTH, 0.8);
                b.wire(peakCone, massif, 0);
                b.wire(jagged, massif, 1);
                final CurveNode peakDetail = b.add(new CurveNode(0, 0, 0.02f, 0.372f, 0.25f, 0.44f, 1.8f, 0.95f), 7, 3);
                b.wire(massif, peakDetail);
                final CombineNode withPeak = b.add(new CombineNode(CombineNode.Mode.MAX), 8, 0);
                b.wire(relief, withPeak, 0);
                b.wire(peakDetail, withPeak, 1);
                // Sea stacks: small rock pillars standing in the shallows
                final CurveNode shallowZone = b.add(new CurveNode(0, 0, 0.8f, 0, 0.86f, 1, 0.98f, 1, 1.02f, 0, 2, 0), 5, 2);
                b.wire(potential, shallowZone);
                final TowersNode stacks = b.add(new TowersNode(), 0, 8);
                stacks.set(TowersNode.SPACING, 48);
                stacks.set(TowersNode.WIDTH, 0.13);
                stacks.set(TowersNode.DENSITY, 0.22);
                stacks.set(Node.VARIANT, 87);
                final CombineNode stacksInShallows = b.add(new CombineNode(CombineNode.Mode.MULTIPLY), 6, 5);
                b.wire(stacks, stacksInShallows, 0);
                b.wire(shallowZone, stacksInShallows, 1);
                final CurveNode stackHeight = b.add(new CurveNode(0, 0, 0.02f, 0.3f, 1, 0.47f), 7, 5);
                b.wire(stacksInShallows, stackHeight);
                final CombineNode withStacks = b.add(new CombineNode(CombineNode.Mode.MAX), 9, 0);
                b.wire(withPeak, withStacks, 0);
                b.wire(stackHeight, withStacks, 1);
                // The deep ocean: trenches, and a blue hole in the reef to the south west
                final CurveNode deepZone = b.add(new CurveNode(0, 1, 0.55f, 1, 0.68f, 0, 2, 0), 5, 4);
                b.wire(potential, deepZone);
                final NoiseNode trenchNoise = b.noise(NoiseNode.Kind.RIDGED, 700, 3, 88, 150, 9);
                final CurveNode trenchShape = b.add(new CurveNode(0, 0, 0.8f, 0, 1, 1), 1, 9);
                b.wire(trenchNoise, trenchShape);
                final CombineNode trenches = b.add(new CombineNode(CombineNode.Mode.MULTIPLY), 6, 6);
                b.wire(trenchShape, trenches, 0);
                b.wire(deepZone, trenches, 1);
                final CombineNode withTrenches = b.add(new CombineNode(CombineNode.Mode.SUBTRACT), 10, 0);
                withTrenches.set(CombineNode.STRENGTH, 0.1);
                b.wire(withStacks, withTrenches, 0);
                b.wire(trenches, withTrenches, 1);
                final GradientNode blueHole = b.add(new GradientNode(), 0, 10);
                blueHole.set(GradientNode.FIT_TO_MAP, false);
                blueHole.set(GradientNode.CENTRE_X, -620);
                blueHole.set(GradientNode.CENTRE_Y, 720);
                blueHole.set(GradientNode.SIZE, 75);
                final CurveNode holeShape = b.add(new CurveNode(0, 0, 0.08f, 0.85f, 1, 1), 1, 10);
                b.wire(blueHole, holeShape);
                final CombineNode withHole = b.add(new CombineNode(CombineNode.Mode.SUBTRACT), 11, 0);
                withHole.set(CombineNode.STRENGTH, 0.3);
                b.wire(withTrenches, withHole, 0);
                b.wire(holeShape, withHole, 1);
                final ClampNode clamp = b.add(new ClampNode(), 12, 0);
                b.wire(withHole, clamp);
                final GullyErosionNode erosion = b.add(new GullyErosionNode(), 13, 0);
                erosion.set(GullyErosionNode.DEPTH, 14);
                erosion.set(GullyErosionNode.SPACING, 70);
                erosion.set(Node.VARIANT, 89);
                b.wire(clamp, erosion);
                b.output(erosion, 14, 0, w - 90, w + 150, minHeight, maxHeight);

                // The sea: turquoise warm ocean over the shelf, lukewarm ocean over the deep water; a sandy sea bed with
                // coral reefs in patches on the reef flats, gravel in the deep
                b.biome(MinecraftBiome.LUKEWARM_OCEAN, 0.5, 1, 12);
                final HeightMaskNode shelf = b.height(w - 30, w, 6, 0, 13);
                b.wire(shelf, b.biome(MinecraftBiome.WARM_OCEAN, 1, 1, 13));
                b.wire(shelf, b.terrain(Terrain.SAND, 0.8, 2, 13));
                final HeightMaskNode deep = b.height(-4096, w - 30, 6, 0, 14);
                b.wire(deep, b.terrain(Terrain.GRAVEL, 0.7, 1, 14));
                final HeightMaskNode reefFlats = b.height(w - 14, w - 2, 2, 0, 15);
                final NoiseNode reefNoise = b.noise(NoiseNode.Kind.PERLIN, 60, 3, 90, 20, 16);
                final CurveNode reefPatches = b.add(new CurveNode(0, 0, 0.55f, 0, 0.65f, 1, 1, 1), 1, 16);
                b.wire(reefNoise, reefPatches);
                final CombineNode reefs = b.add(new CombineNode(CombineNode.Mode.MULTIPLY), 1, 15);
                b.wire(reefFlats, reefs, 0);
                b.wire(reefPatches, reefs, 1);
                b.wire(reefs, b.material(MaterialGroup.CORAL_REEF, 1.2, 2, 15));
                // Wide, gently sloping beaches of white sand
                final HeightMaskNode beachHeight = b.height(w - 2, w + 5, 2, 3, 13);
                final SlopeMaskNode beachSlope = b.slope(0, 22, 6, 3, 14);
                final CombineNode beaches = b.add(new CombineNode(CombineNode.Mode.MULTIPLY), 4, 13);
                b.wire(beachHeight, beaches, 0);
                b.wire(beachSlope, beaches, 1);
                b.wire(beaches, b.terrain(Terrain.SAND, 1.3, 5, 13));
                b.wire(beaches, b.biome(MinecraftBiome.BEACH, 1.2, 6, 13));
                // Palms along the shore
                final HeightMaskNode shore = b.height(w + 2, w + 10, 2, 3, 15);
                final CombineNode palmShore = b.add(new CombineNode(CombineNode.Mode.MULTIPLY), 4, 15);
                b.wire(shore, palmShore, 0);
                b.wire(beachSlope, palmShore, 1);
                b.wire(palmShore, b.flora(Vegetation.PALMS, 0.22, 5, 15));
                // Pale limestone cliffs, mossy rock on the steep slopes
                final SlopeMaskNode cliffs = b.slope(48, 90, 6, 3, 16);
                b.wire(cliffs, b.material(MaterialGroup.LIMESTONE, 1.35, 4, 16));
                final SlopeMaskNode steep = b.slope(38, 48, 4, 3, 17);
                b.wire(steep, b.material(MaterialGroup.MOSSY_ROCK, 1.1, 4, 17));
                // Dense jungle on the land
                final HeightMaskNode land = b.height(w + 4, 4096, 3, 6, 15);
                final SlopeMaskNode gentle = b.slope(0, 44, 6, 6, 16);
                final CombineNode jungle = b.add(new CombineNode(CombineNode.Mode.MULTIPLY), 7, 15);
                b.wire(land, jungle, 0);
                b.wire(gentle, jungle, 1);
                b.wire(jungle, b.material(MaterialGroup.FOREST_FLOOR, 1, 8, 14));
                b.wire(jungle, b.flora(Vegetation.RAINFOREST, 0.5, 8, 15));
                b.wire(jungle, b.flora(Vegetation.BUSHES, 0.55, 8, 16));
                b.wire(jungle, b.flora(Vegetation.FERNS, 0.4, 8, 17));
                b.wire(land, b.biome(MinecraftBiome.JUNGLE, 1, 9, 15));
                break;
            }
            case ERODE_CURRENT_MAP: {
                final CurrentMapNode current = b.add(new CurrentMapNode(), 0, 0);
                final GullyErosionNode erosion = b.add(new GullyErosionNode(), 1, 0);
                erosion.set(GullyErosionNode.DEPTH, 20);
                erosion.set(GullyErosionNode.SPACING, 96);
                b.wire(current, erosion);
                b.output(erosion, 2, 0, minHeight, maxHeight - 1, minHeight, maxHeight);
                // Rock where the erosion exposed steep slopes; the theme does the rest
                b.wire(b.slope(40, 90, 6, 1, 1), b.terrain(Terrain.ROCK, 1, 2, 1));
                break;
            }
            default:
                throw new InternalError();
        }
        return b.graph;
    }

    public enum Example {
        ROLLING_HILLS("Rolling hills"),
        ERODED_MOUNTAINS("Eroded mountains"),
        ARCHIPELAGO("Tropical archipelago"),
        MESA("Mesa canyons"),
        DESERT_DUNES("Desert dunes"),
        VOLCANIC_ISLAND("Volcanic island"),
        LAKES_AND_FORESTS("Lakes and forests"),
        RIVER_VALLEYS("River valleys"),
        TOWER_KARST("Tower karst jungle"),
        RED_CLIFFS("Red cliffs and spruces"),
        ALPINE_VALLEY("Alpine valley"),
        CONTINENT("Continent and islands (5000 x 5000)"),
        CORAL_ARCHIPELAGO("Coral archipelago (3000 x 3000)"),
        ERODE_CURRENT_MAP("Erode the current map");

        Example(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }

        private final String displayName;
    }

    /**
     * Places nodes on a grid of columns and rows and wires them together.
     */
    private static final class Builder {
        Builder(Example example, long seed) {
            graph = new NodeGraph();
            graph.setName(example.toString());
            graph.setSeed(seed);
        }

        <N extends Node> N add(N node, int column, int row) {
            graph.add(node);
            node.setLocation(40 + column * COLUMN_WIDTH, 40 + row * ROW_HEIGHT);
            return node;
        }

        NoiseNode noise(NoiseNode.Kind kind, double scale, int octaves, int variant, double warp, int row) {
            final NoiseNode node = add(new NoiseNode(kind), 0, row);
            node.set(NoiseNode.SCALE, scale);
            node.set(NoiseNode.OCTAVES, octaves);
            node.set(NoiseNode.WARP, warp);
            node.set(Node.VARIANT, variant);
            return node;
        }

        HeightMaskNode height(double from, double to, double fade, int column, int row) {
            final HeightMaskNode node = add(new HeightMaskNode(), column, row);
            node.set(HeightMaskNode.FROM, from);
            node.set(HeightMaskNode.TO, to);
            node.set(HeightMaskNode.FADE, fade);
            return node;
        }

        SlopeMaskNode slope(double min, double max, double fade, int column, int row) {
            final SlopeMaskNode node = add(new SlopeMaskNode(), column, row);
            node.set(SlopeMaskNode.MIN, min);
            node.set(SlopeMaskNode.MAX, max);
            node.set(SlopeMaskNode.FADE, fade);
            return node;
        }

        TerrainPainterNode terrain(Terrain terrain, double strength, int column, int row) {
            final TerrainPainterNode node = add(new TerrainPainterNode(terrain), column, row);
            node.set(TerrainPainterNode.STRENGTH, strength);
            return node;
        }

        BiomePainterNode biome(MinecraftBiome biome, double strength, int column, int row) {
            final BiomePainterNode node = add(new BiomePainterNode(biome), column, row);
            node.set(BiomePainterNode.STRENGTH, strength);
            return node;
        }

        MaterialPainterNode material(MaterialGroup group, double strength, int column, int row) {
            final MaterialPainterNode node = add(new MaterialPainterNode(group), column, row);
            node.set(MaterialPainterNode.STRENGTH, strength);
            return node;
        }

        RegionsNode regions(int count, int type, int variant, int column, int row) {
            final RegionsNode node = add(new RegionsNode(), column, row);
            node.set(RegionsNode.COUNT, count);
            node.set(RegionsNode.TYPE, type);
            node.set(Node.VARIANT, variant);
            return node;
        }

        FloraPainterNode flora(Vegetation vegetation, double density, int column, int row) {
            final FloraPainterNode node = add(new FloraPainterNode(vegetation), column, row);
            node.set(FloraPainterNode.DENSITY, density);
            return node;
        }

        void output(Node input, int column, int row, int low, int high, int minHeight, int maxHeight) {
            final OutputNode output = graph.getOutputNode();
            output.setLocation(40 + column * COLUMN_WIDTH, 40 + row * ROW_HEIGHT);
            output.set(OutputNode.MIN_HEIGHT, Math.max(minHeight, low));
            output.set(OutputNode.MAX_HEIGHT, Math.min(maxHeight - 1, high));
            graph.connect(input, output, 0);
        }

        void wire(Node from, Node to) {
            graph.connect(from, to, 0);
        }

        void wire(Node from, Node to, int input) {
            graph.connect(from, to, input);
        }

        final NodeGraph graph;

        private static final int COLUMN_WIDTH = 240, ROW_HEIGHT = 250;
    }
}
