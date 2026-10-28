package org.pepsoft.worldpainter.nodes;

import org.pepsoft.worldpainter.Terrain;

import java.util.Arrays;
import java.util.List;

/**
 * Base class for nodes which carve bodies of water (rivers, lakes) into the terrain flowing through them, and fill them
 * with water. Their output is the carved terrain; when they are part of the terrain of the {@link OutputNode}, applying
 * the graph also sets the water level, and paints the bed and (optionally) a biome where the water is.
 */
public abstract class WaterNode extends Node {
    @Override
    public NodeCategory getCategory() {
        return NodeCategory.WATER;
    }

    @Override
    public List<String> getInputNames() {
        return INPUT_NAMES;
    }

    /**
     * The terrain type of the bed, or {@code null} to leave it to the other painters and the theme.
     */
    abstract Terrain getBedTerrain();

    /**
     * The biome to paint where the water is, or {@code null} for none.
     */
    abstract MinecraftBiome getBiome();

    /**
     * Carve the water bodies for a region.
     *
     * @param evaluation The evaluation, for evaluating other nodes.
     * @param region     The region.
     * @param heights    The terrain heights in blocks for the region (input); replace them with the carved heights.
     * @param levels     Receives the water level in blocks for each sample, or {@link Float#NaN} where there is no water
     *                   of this node; initially filled with {@code NaN}.
     * @param masks      Receives how much each sample is part of a water body (0-1), for painting the bed and biome.
     */
    abstract void carve(Evaluation evaluation, Region region, float[] heights, float[] levels, float[] masks);

    /**
     * Prepare the node for evaluation, for instance by creating noise generators. Called once when the graph is
     * compiled, on a copy of the node which the user cannot change.
     *
     * @param context The compile context.
     * @param input   The evaluator of the input, or {@code null} if the input is not connected.
     */
    abstract void prepare(CompileContext context, CompiledNode input);

    @Override
    final CompiledNode compile(CompileContext context, CompiledNode[] inputs) {
        final WaterNode snapshot = (WaterNode) clone();
        snapshot.prepare(context, inputs[0]);
        return new CompiledWater(inputs, context, snapshot);
    }

    /**
     * The terrain types which can be chosen for a bed.
     */
    static Terrain[] getBedChoices() {
        return Arrays.stream(Terrain.values()).filter(terrain -> ! terrain.isCustom()).toArray(Terrain[]::new);
    }

    private static final List<String> INPUT_NAMES = List.of("Height");
    private static final long serialVersionUID = 1L;

    /**
     * The evaluator of a {@link WaterNode}. Its value is the carved terrain; the water levels and the masks of the same
     * computation are available separately.
     */
    static final class CompiledWater extends CompiledNode {
        CompiledWater(CompiledNode[] inputs, CompileContext context, WaterNode node) {
            super(inputs, 0.0f);
            minHeight = context.getMinHeight();
            verticalScale = context.getVerticalScale();
            core = new CompiledNode(inputs, 0.0f) {
                @Override
                float[] compute(Evaluation evaluation, Region region) {
                    // Carved values, water levels and masks, packed into one array so that they are cached together
                    final int n = region.size();
                    final float[] in = input(evaluation, 0, region);
                    final float[] heights = new float[n], levels = new float[n], masks = new float[n];
                    for (int i = 0; i < n; i++) {
                        heights[i] = (float) (minHeight + in[i] * verticalScale);
                    }
                    Arrays.fill(levels, Float.NaN);
                    node.carve(evaluation, region, heights, levels, masks);
                    final float[] packed = new float[3 * n];
                    for (int i = 0; i < n; i++) {
                        packed[i] = (float) ((heights[i] - minHeight) / verticalScale);
                    }
                    System.arraycopy(levels, 0, packed, n, n);
                    System.arraycopy(masks, 0, packed, 2 * n, n);
                    return packed;
                }
            };
            strengths = new CompiledNode(new CompiledNode[0]) {
                @Override
                float[] compute(Evaluation evaluation, Region region) {
                    final float[] masks = slice(evaluation, region, 2);
                    for (int i = 0; i < masks.length; i++) {
                        masks[i] *= WATER_PAINT_STRENGTH;
                    }
                    return masks;
                }
            };
        }

        @Override
        float[] compute(Evaluation evaluation, Region region) {
            return slice(evaluation, region, 0);
        }

        /**
         * The water levels in blocks, with {@link Float#NaN} where there is no water.
         */
        float[] getLevels(Evaluation evaluation, Region region) {
            return slice(evaluation, region, 1);
        }

        /**
         * An evaluator of the strength with which the bed and biome are painted.
         */
        CompiledNode getStrengths() {
            return strengths;
        }

        private float[] slice(Evaluation evaluation, Region region, int index) {
            final int n = region.size();
            return Arrays.copyOfRange(evaluation.evaluate(core, region), index * n, (index + 1) * n);
        }

        private final double minHeight, verticalScale;
        private final CompiledNode core, strengths;
    }

    /**
     * The strength with which water bodies paint their bed and biome, so that they win from ordinary painters.
     */
    static final float WATER_PAINT_STRENGTH = 2.0f;
}
