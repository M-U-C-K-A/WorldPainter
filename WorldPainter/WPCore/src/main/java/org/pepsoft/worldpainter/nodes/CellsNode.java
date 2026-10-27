package org.pepsoft.worldpainter.nodes;

import org.pepsoft.worldpainter.procedural.FractalNoise;
import org.pepsoft.worldpainter.procedural.NoiseLayer;
import org.pepsoft.worldpainter.procedural.SimplexNoise;

import java.util.List;

import static java.util.Collections.emptyList;

/**
 * A generator of cellular (Worley or Voronoi) noise.
 */
public class CellsNode extends Node {
    public CellsNode() {
        set(MODE, Mode.MOUNDS);
    }

    @Override
    public String getName() {
        return "Cells (Voronoi)";
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
        return List.of(MODE, SCALE, WARP, VARIANT);
    }

    @Override
    CompiledNode compile(CompileContext context, CompiledNode[] inputs) {
        final long seed = seed(context, getInt(VARIANT));
        final SimplexNoise noise = new SimplexNoise(seed);
        final double scale = getDouble(SCALE), warp = getDouble(WARP);
        // Only used for its domain warp
        final FractalNoise warpNoise = (warp > 0) ? new FractalNoise(seed, NoiseLayer.Type.FBM, scale, 1, 0.5, warp, 0) : null;
        final Mode mode = get(MODE);
        return new CompiledNode.Generator() {
            @Override
            float value(double x, double y) {
                if (warpNoise != null) {
                    final double wx = warpNoise.warpX(x, y), wy = warpNoise.warpY(x, y);
                    x += wx;
                    y += wy;
                }
                final double[] f = BUFFER.get();
                noise.cellular(x / scale, y / scale, f);
                switch (mode) {
                    case MOUNDS:
                        return (float) Math.max(0, 1 - f[0]);
                    case PITS:
                        return (float) Math.min(1, f[0]);
                    case RIDGES:
                        return (float) Math.max(0, 1 - (f[1] - f[0]) * 1.5);
                    default:
                        throw new InternalError();
                }
            }
        };
    }

    public static final ChoiceParameter<Mode> MODE = new ChoiceParameter<>("mode", "Shape", "Mounds peak at the centre of each cell, pits are lowest there, ridges follow the cell edges", Mode.MOUNDS, Mode.values());
    public static final NumberParameter SCALE = new NumberParameter("scale", "Cell size", "The average size of the cells", 256, 8, 8192, 1, true, "blocks");
    public static final NumberParameter WARP = new NumberParameter("warp", "Warp", "How much the cells are distorted, for a more organic look", 0, 0, 2048, 1, false, "blocks");

    private static final ThreadLocal<double[]> BUFFER = ThreadLocal.withInitial(() -> new double[2]);
    private static final long serialVersionUID = 1L;

    public enum Mode {
        MOUNDS("Mounds"), PITS("Pits"), RIDGES("Ridges");

        Mode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }

        private final String displayName;
    }
}
