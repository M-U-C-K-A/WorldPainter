package org.pepsoft.worldpainter.nodes;

import java.util.List;

/**
 * Base class for nodes which paint the surface (terrain types, biomes or vegetation) where their mask is strong. They
 * have no output; they are applied together with the height when the graph is applied to the map. An unconnected mask
 * means everywhere.
 */
public abstract class PainterNode extends Node {
    @Override
    public NodeCategory getCategory() {
        return NodeCategory.SURFACE;
    }

    @Override
    public List<String> getInputNames() {
        return INPUT_NAMES;
    }

    @Override
    public String getInputHint(int index) {
        return "everywhere";
    }

    @Override
    public boolean hasOutput() {
        return false;
    }

    /**
     * What is painted: a {@link org.pepsoft.worldpainter.Terrain}, a {@link MinecraftBiome} or a {@link Vegetation}.
     */
    public abstract Object getTarget();

    /**
     * The factor by which the mask is multiplied.
     */
    abstract double getWeight();

    @Override
    CompiledNode compile(CompileContext context, CompiledNode[] inputs) {
        final float weight = (float) getWeight();
        return new CompiledNode(inputs, 1.0f) {
            @Override
            float[] compute(Evaluation evaluation, Region region) {
                final float[] mask = input(evaluation, 0, region);
                final float[] result = new float[mask.length];
                for (int i = 0; i < mask.length; i++) {
                    result[i] = Math.max(0.0f, mask[i]) * weight;
                }
                return result;
            }
        };
    }

    private static final List<String> INPUT_NAMES = List.of("Mask");
    private static final long serialVersionUID = 1L;
}
