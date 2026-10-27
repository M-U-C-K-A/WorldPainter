package org.pepsoft.worldpainter.nodes;

import org.pepsoft.worldpainter.procedural.FractalNoise;
import org.pepsoft.worldpainter.procedural.NoiseLayer;

import java.util.List;

import static java.util.Collections.emptyList;

/**
 * A generator of fractal noise: Perlin style fBm, ridged or billowy noise, or dunes. Uses the same noise as the
 * procedural terrain.
 */
public class NoiseNode extends Node {
    public NoiseNode() {
        this(Kind.PERLIN);
    }

    public NoiseNode(Kind kind) {
        set(KIND, kind);
    }

    @Override
    public String getName() {
        return "Noise";
    }

    @Override
    public String getTitle() {
        return get(KIND).toString();
    }

    @Override
    public NodeCategory getCategory() {
        return NodeCategory.GENERATOR;
    }

    @Override
    public List<String> getInputNames() {
        return emptyList();
    }

    @Override
    public List<Parameter<?>> getParameters() {
        return List.of(KIND, SCALE, OCTAVES, ROUGHNESS, WARP, DIRECTION, VARIANT);
    }

    @Override
    public boolean isRelevant(Parameter<?> parameter) {
        return (parameter != DIRECTION) || (get(KIND) == Kind.DUNES);
    }

    @Override
    CompiledNode compile(CompileContext context, CompiledNode[] inputs) {
        final FractalNoise noise = new FractalNoise(seed(context, getInt(VARIANT)), get(KIND).type, getDouble(SCALE), getInt(OCTAVES), getDouble(ROUGHNESS), getDouble(WARP), getDouble(DIRECTION));
        return new CompiledNode.Generator() {
            @Override
            float value(double x, double y) {
                return (float) noise.sample(x, y);
            }
        };
    }

    public static final ChoiceParameter<Kind> KIND = new ChoiceParameter<>("kind", "Type", "The kind of noise", Kind.PERLIN, Kind.values());
    public static final NumberParameter SCALE = new NumberParameter("scale", "Feature size", "The size of the largest features", 512, 8, 16384, 1, true, "blocks");
    public static final NumberParameter OCTAVES = new NumberParameter("octaves", "Detail", "The number of levels of finer detail which are added", 6, 1, 10, 1, false, null);
    public static final NumberParameter ROUGHNESS = new NumberParameter("roughness", "Roughness", "How strong the finer detail is", 0.5, 0, 1, 0.01, false, null);
    public static final NumberParameter WARP = new NumberParameter("warp", "Warp", "How much the shapes are twisted, for a more organic look", 0, 0, 2048, 1, false, "blocks");
    public static final NumberParameter DIRECTION = new NumberParameter("direction", "Direction", "The direction of the ridges", 45, 0, 180, 1, false, "°");

    private static final long serialVersionUID = 1L;

    public enum Kind {
        PERLIN("Perlin noise", NoiseLayer.Type.FBM),
        RIDGED("Ridged noise", NoiseLayer.Type.RIDGED),
        BILLOW("Billow noise", NoiseLayer.Type.BILLOW),
        DUNES("Dunes", NoiseLayer.Type.DUNES);

        Kind(String displayName, NoiseLayer.Type type) {
            this.displayName = displayName;
            this.type = type;
        }

        @Override
        public String toString() {
            return displayName;
        }

        private final String displayName;
        private final NoiseLayer.Type type;
    }
}
