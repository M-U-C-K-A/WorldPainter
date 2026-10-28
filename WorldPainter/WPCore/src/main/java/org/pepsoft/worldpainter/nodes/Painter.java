package org.pepsoft.worldpainter.nodes;

/**
 * A compiled {@link PainterNode}: what it paints and how strongly, as a function of the location.
 */
public final class Painter {
    Painter(int nodeId, Object target, boolean onlyOnLand, CompiledNode mask) {
        this.nodeId = nodeId;
        this.target = target;
        this.onlyOnLand = onlyOnLand;
        this.mask = mask;
    }

    public int getNodeId() {
        return nodeId;
    }

    /**
     * What is painted: a {@link org.pepsoft.worldpainter.Terrain}, a {@link MinecraftBiome} or a {@link Vegetation}.
     */
    public Object getTarget() {
        return target;
    }

    /**
     * Whether nothing should be painted under water.
     */
    public boolean isOnlyOnLand() {
        return onlyOnLand;
    }

    /**
     * Evaluate the strength of the painter (its mask times its strength or density) for a region. The returned array
     * may be shared with other results of the same evaluation and must not be modified.
     */
    public float[] evaluate(Evaluation evaluation, Region region) {
        return evaluation.evaluate(mask, region);
    }

    /**
     * The same painter, painting something else.
     */
    Painter withTarget(Object target) {
        return new Painter(nodeId, target, onlyOnLand, mask);
    }

    private final int nodeId;
    private final Object target;
    private final boolean onlyOnLand;
    private final CompiledNode mask;
}
