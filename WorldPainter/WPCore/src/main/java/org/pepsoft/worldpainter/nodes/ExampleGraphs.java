package org.pepsoft.worldpainter.nodes;

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
