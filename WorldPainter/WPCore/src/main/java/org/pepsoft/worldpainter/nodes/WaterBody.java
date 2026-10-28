package org.pepsoft.worldpainter.nodes;

/**
 * The water of a compiled {@link WaterNode} (rivers or lakes) which is part of the terrain of a graph.
 */
public final class WaterBody {
    WaterBody(int nodeId, WaterNode.CompiledWater water) {
        this.nodeId = nodeId;
        this.water = water;
    }

    public int getNodeId() {
        return nodeId;
    }

    /**
     * Evaluate the water levels in blocks for a region, with {@link Float#NaN} where this body has no water. Water
     * should only be placed where the final terrain is lower than the level.
     */
    public float[] evaluateLevels(Evaluation evaluation, Region region) {
        return water.getLevels(evaluation, region);
    }

    /**
     * Combine the water levels of several bodies into the given array, keeping the highest level for each sample and
     * leaving the existing values where none of the bodies has water.
     */
    public static void combine(Iterable<WaterBody> bodies, Evaluation evaluation, Region region, float[] levels) {
        for (WaterBody body: bodies) {
            final float[] bodyLevels = body.evaluateLevels(evaluation, region);
            for (int i = 0; i < levels.length; i++) {
                if ((! Float.isNaN(bodyLevels[i])) && (Float.isNaN(levels[i]) || (bodyLevels[i] > levels[i]))) {
                    levels[i] = bodyLevels[i];
                }
            }
        }
    }

    private final int nodeId;
    private final WaterNode.CompiledWater water;
}
