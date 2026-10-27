package org.pepsoft.worldpainter.nodes;

/**
 * Ready made node graphs to start from.
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
        final NodeGraph graph = new NodeGraph();
        graph.setName(example.toString());
        graph.setSeed(seed);
        final OutputNode output = graph.getOutputNode();
        switch (example) {
            case ROLLING_HILLS: {
                final NoiseNode hills = place(graph, new NoiseNode(NoiseNode.Kind.PERLIN), 40, 120);
                hills.set(NoiseNode.SCALE, 768);
                hills.set(NoiseNode.OCTAVES, 5);
                hills.set(Node.VARIANT, 1);
                final CurveNode shape = place(graph, new CurveNode(0, 0, 0.5f, 0.35f, 1, 1), 300, 120);
                graph.connect(hills, shape, 0);
                graph.connect(shape, output, 0);
                output.setLocation(560, 120);
                setHeights(output, waterLevel - 24, waterLevel + 72, minHeight, maxHeight);
                break;
            }
            case ERODED_MOUNTAINS: {
                final NoiseNode base = place(graph, new NoiseNode(NoiseNode.Kind.PERLIN), 40, 60);
                base.set(NoiseNode.SCALE, 2400);
                base.set(NoiseNode.OCTAVES, 5);
                base.set(Node.VARIANT, 1);
                final NoiseNode peaks = place(graph, new NoiseNode(NoiseNode.Kind.RIDGED), 40, 330);
                peaks.set(NoiseNode.SCALE, 900);
                peaks.set(NoiseNode.OCTAVES, 7);
                peaks.set(Node.VARIANT, 2);
                final CurveNode lowlands = place(graph, new CurveNode(0, 0.05f, 1, 0.45f), 290, 20);
                graph.connect(base, lowlands, 0);
                final CurveNode ranges = place(graph, new CurveNode(0, 0, 0.45f, 0, 0.75f, 1, 1, 1), 290, 230);
                graph.connect(base, ranges, 0);
                final CombineNode mountains = place(graph, new CombineNode(CombineNode.Mode.MULTIPLY), 540, 300);
                graph.connect(peaks, mountains, 0);
                graph.connect(ranges, mountains, 1);
                final CombineNode terrain = place(graph, new CombineNode(CombineNode.Mode.ADD), 780, 120);
                terrain.set(CombineNode.STRENGTH, 0.65);
                graph.connect(lowlands, terrain, 0);
                graph.connect(mountains, terrain, 1);
                final GullyErosionNode erosion = place(graph, new GullyErosionNode(), 1020, 120);
                erosion.set(GullyErosionNode.DEPTH, 40);
                erosion.set(Node.VARIANT, 6);
                graph.connect(terrain, erosion, 0);
                graph.connect(erosion, output, 0);
                output.setLocation(1260, 120);
                setHeights(output, waterLevel - 50, waterLevel + 210, minHeight, maxHeight);
                break;
            }
            case ARCHIPELAGO: {
                final NoiseNode islands = place(graph, new NoiseNode(NoiseNode.Kind.PERLIN), 40, 40);
                islands.set(NoiseNode.SCALE, 500);
                islands.set(Node.VARIANT, 3);
                final CellsNode bumps = place(graph, new CellsNode(), 40, 300);
                bumps.set(CellsNode.SCALE, 350);
                bumps.set(Node.VARIANT, 4);
                final CombineNode land = place(graph, new CombineNode(CombineNode.Mode.ADD), 290, 140);
                land.set(CombineNode.STRENGTH, 0.25);
                graph.connect(islands, land, 0);
                graph.connect(bumps, land, 1);
                final GradientNode falloff = place(graph, new GradientNode(), 290, 400);
                final CombineNode masked = place(graph, new CombineNode(CombineNode.Mode.MULTIPLY), 530, 220);
                masked.set(CombineNode.STRENGTH, 0.55);
                graph.connect(land, masked, 0);
                graph.connect(falloff, masked, 1);
                final CurveNode coast = place(graph, new CurveNode(0, 0, 0.42f, 0.18f, 0.52f, 0.3f, 0.68f, 0.55f, 1, 1), 770, 160);
                graph.connect(masked, coast, 0);
                graph.connect(coast, output, 0);
                output.setLocation(1020, 160);
                setHeights(output, waterLevel - 40, waterLevel + 100, minHeight, maxHeight);
                break;
            }
            case MESA: {
                final NoiseNode base = place(graph, new NoiseNode(NoiseNode.Kind.PERLIN), 40, 120);
                base.set(NoiseNode.SCALE, 800);
                base.set(NoiseNode.WARP, 60);
                base.set(Node.VARIANT, 5);
                final CurveNode plateaus = place(graph, new CurveNode(0, 0, 0.4f, 0.12f, 0.55f, 0.6f, 0.75f, 0.75f, 1, 1), 290, 120);
                graph.connect(base, plateaus, 0);
                final NoiseNode detail = place(graph, new NoiseNode(NoiseNode.Kind.BILLOW), 290, 360);
                detail.set(NoiseNode.SCALE, 300);
                detail.set(NoiseNode.OCTAVES, 4);
                detail.set(Node.VARIANT, 7);
                final CombineNode layers = place(graph, new CombineNode(CombineNode.Mode.ADD), 530, 200);
                layers.set(CombineNode.STRENGTH, 0.2);
                graph.connect(plateaus, layers, 0);
                graph.connect(detail, layers, 1);
                final TerraceNode terraces = place(graph, new TerraceNode(), 770, 120);
                terraces.set(TerraceNode.STEPS, 6);
                terraces.set(TerraceNode.SHARPNESS, 0.85);
                graph.connect(layers, terraces, 0);
                final ThermalErosionNode crumble = place(graph, new ThermalErosionNode(), 1010, 120);
                crumble.set(ErosionNode.ITERATIONS, 10.0);
                crumble.set(ThermalErosionNode.ANGLE, 65.0);
                graph.connect(terraces, crumble, 0);
                graph.connect(crumble, output, 0);
                output.setLocation(1250, 120);
                setHeights(output, waterLevel - 6, waterLevel + 100, minHeight, maxHeight);
                break;
            }
            default:
                throw new InternalError();
        }
        return graph;
    }

    private static <N extends Node> N place(NodeGraph graph, N node, double x, double y) {
        graph.add(node);
        node.setLocation(x, y);
        return node;
    }

    private static void setHeights(OutputNode output, int low, int high, int minHeight, int maxHeight) {
        output.set(OutputNode.MIN_HEIGHT, Math.max(minHeight, low));
        output.set(OutputNode.MAX_HEIGHT, Math.min(maxHeight - 1, high));
    }

    public enum Example {
        ROLLING_HILLS("Rolling hills"), ERODED_MOUNTAINS("Eroded mountains"), ARCHIPELAGO("Archipelago"), MESA("Mesa");

        Example(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }

        private final String displayName;
    }
}
