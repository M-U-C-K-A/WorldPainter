package org.pepsoft.worldpainter.nodes;

import org.pepsoft.worldpainter.Terrain;

import java.util.List;

/**
 * Creates floating islands, high in the sky above the land: wherever its Shape input is above one half there is an
 * island, with its top at the height given by its Top input and an underside which deepens like an upside down cone
 * towards the middle of the island. When the graph is applied the islands become a WorldPainter floating dimension
 * layer (with its own floor dimension holding the tops), which exports to Minecraft like any other and can be edited
 * afterwards.
 */
public class FloatingIslandsNode extends Node {
    @Override
    public String getName() {
        return "Floating islands";
    }

    @Override
    public NodeCategory getCategory() {
        return NodeCategory.STRUCTURE;
    }

    @Override
    public List<String> getInputNames() {
        return INPUT_NAMES;
    }

    @Override
    public String getInputHint(int index) {
        return (index == 0) ? "none" : "level";
    }

    @Override
    public boolean hasOutput() {
        return false;
    }

    @Override
    public List<Parameter<?>> getParameters() {
        return List.of(TOP_LOW, TOP_HIGH, THICKNESS, TAPER, ROUGHNESS, TERRAIN, VEGETATION, DENSITY);
    }

    @Override
    CompiledNode compile(CompileContext context, CompiledNode[] inputs) {
        return new Compiled(inputs, this);
    }

    /**
     * The height of the top of the islands in blocks for a value of the Top input.
     */
    float toTopHeight(float value) {
        final double low = getDouble(TOP_LOW), high = getDouble(TOP_HIGH);
        return (float) (low + value * (high - low));
    }

    public static final NumberParameter TOP_LOW = new NumberParameter("topLow", "Top at 0", "The height of the tops of the islands for a Top value of zero", 150, -2048, 4096, 1, false, "blocks");
    public static final NumberParameter TOP_HIGH = new NumberParameter("topHigh", "Top at 1", "The height of the tops of the islands for a Top value of one", 190, -2048, 4096, 1, false, "blocks");
    public static final NumberParameter THICKNESS = new NumberParameter("thickness", "Thickness", "How far the underside reaches below the top, in the middle of the islands", 36, 2, 512, 1, false, "blocks");
    public static final NumberParameter TAPER = new NumberParameter("taper", "Taper", "How far in from the edge the underside reaches its full depth: small for flat slabs, large for cones", 40, 1, 512, 1, false, "blocks");
    public static final NumberParameter ROUGHNESS = new NumberParameter("roughness", "Roughness", "How rough and craggy the underside is", 0.5, 0, 1, 0.01, false, null);
    public static final ChoiceParameter<Terrain> TERRAIN = new ChoiceParameter<>("terrain", "Terrain", "The terrain type on top of the islands", Terrain.GRASS,
            Terrain.GRASS, Terrain.BARE_GRASS, Terrain.PODZOL, Terrain.DIRT, Terrain.SAND, Terrain.STONE, Terrain.STONE_MIX, Terrain.SNOW, Terrain.MYCELIUM, Terrain.MESA);
    public static final ChoiceParameter<Vegetation> VEGETATION = new ChoiceParameter<>("vegetation", "Vegetation", "The trees (or snow) on top of the islands", Vegetation.DECIDUOUS_FOREST,
            Vegetation.DECIDUOUS_FOREST, Vegetation.PINE_FOREST, Vegetation.JUNGLE, Vegetation.SWAMP_TREES, Vegetation.SNOW);
    public static final NumberParameter DENSITY = new NumberParameter("density", "Density", "How dense the vegetation is; zero for none", 0.4, 0, 1, 0.01, false, null);

    private static final List<String> INPUT_NAMES = List.of("Shape", "Top");
    private static final long serialVersionUID = 1L;

    /**
     * The compiled node: its value is the Shape input; the Top input is available separately.
     */
    public static final class Compiled extends CompiledNode {
        Compiled(CompiledNode[] inputs, FloatingIslandsNode node) {
            super(inputs, 0.0f, 0.5f);
            this.node = (FloatingIslandsNode) node.clone();
        }

        @Override
        float[] compute(Evaluation evaluation, Region region) {
            final float[] shape = input(evaluation, 0, region);
            return shape.clone();
        }

        /**
         * The graph this node is part of, to create evaluations with.
         */
        public CompiledGraph getCompiledGraph() {
            return compiledGraph;
        }

        void setCompiledGraph(CompiledGraph compiledGraph) {
            this.compiledGraph = compiledGraph;
        }

        /**
         * Where the islands are: above one half.
         */
        public float[] shape(Evaluation evaluation, Region region) {
            return evaluation.evaluate(this, region);
        }

        /**
         * The height of the tops of the islands in blocks.
         */
        public float[] tops(Evaluation evaluation, Region region) {
            final float[] values = input(evaluation, 1, region);
            final float[] heights = new float[values.length];
            for (int i = 0; i < values.length; i++) {
                heights[i] = node.toTopHeight(values[i]);
            }
            return heights;
        }

        /**
         * A copy of the node, with the settings of the islands.
         */
        public FloatingIslandsNode getNode() {
            return node;
        }

        private final FloatingIslandsNode node;
        private CompiledGraph compiledGraph;
    }
}
