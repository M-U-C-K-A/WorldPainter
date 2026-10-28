package org.pepsoft.worldpainter.nodes;

import org.pepsoft.worldpainter.procedural.FractalNoise;
import org.pepsoft.worldpainter.procedural.NoiseLayer;

import java.util.List;

import static java.util.Collections.emptyList;

/**
 * Divides the map into large, irregular regions (warped Voronoi cells), each of which is given one of a number of
 * region types at random, and outputs a mask of the regions of one type, with soft borders. Several Regions nodes with
 * the same settings and variant but a different type split the map between them, which is how to lay out biome zones:
 * cherry groves here, badlands there, forest elsewhere.
 */
public class RegionsNode extends Node {
    @Override
    public String getName() {
        return "Regions";
    }

    @Override
    public String getTitle() {
        return "Regions: type " + (getInt(TYPE) + 1) + " of " + getInt(COUNT);
    }

    @Override
    public NodeCategory getCategory() {
        return NodeCategory.MASK;
    }

    @Override
    public List<String> getInputNames() {
        return emptyList();
    }

    @Override
    public List<Parameter<?>> getParameters() {
        return List.of(SIZE, COUNT, TYPE, WARP, FADE, VARIANT);
    }

    @Override
    CompiledNode compile(CompileContext context, CompiledNode[] inputs) {
        final long seed = seed(context, getInt(VARIANT));
        final double size = getDouble(SIZE), fade = Math.max(1, getDouble(FADE));
        final int count = getInt(COUNT), type = Math.min(getInt(TYPE), count - 1);
        final FractalNoise warpNoise = new FractalNoise(seed, NoiseLayer.Type.FBM, size, 1, 0.5, getDouble(WARP), 0);
        return new CompiledNode.Generator() {
            @Override
            float value(double x, double y) {
                final double wx = x + warpNoise.warpX(x, y), wy = y + warpNoise.warpY(x, y);
                final double gx = wx / size, gy = wy / size;
                final int cx = (int) Math.floor(gx), cy = (int) Math.floor(gy);
                // The nearest feature point, and the nearest one of a different type
                double nearest = Double.MAX_VALUE, nearestOther = Double.MAX_VALUE;
                int nearestType = -1, otherType = -1;
                final double[] distances = new double[25];
                final int[] types = new int[25];
                int n = 0;
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dy = -2; dy <= 2; dy++) {
                        final long hash = hash(seed, cx + dx, cy + dy);
                        final double px = cx + dx + unit(hash, 1), py = cy + dy + unit(hash, 2);
                        final double d = Math.hypot(px - gx, py - gy) * size;
                        distances[n] = d;
                        types[n++] = (int) Math.floorMod(hash >>> 7, count);
                        if (d < nearest) {
                            nearest = d;
                            nearestType = types[n - 1];
                        }
                    }
                }
                for (int i = 0; i < n; i++) {
                    if ((types[i] != nearestType) && (distances[i] < nearestOther)) {
                        nearestOther = distances[i];
                        otherType = types[i];
                    }
                }
                // Half the gap to the nearest region of another type is the distance to the border
                final double toBorder = (nearestOther - nearest) / 2;
                final double inside = 0.5 + 0.5 * smoothstep(0, fade, toBorder);
                if (nearestType == type) {
                    return (float) inside;
                } else if (otherType == type) {
                    // Just across the border from a region of this type
                    return (float) (1 - inside);
                } else {
                    return 0;
                }
            }
        };
    }

    static long hash(long seed, int x, int y) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return h;
    }

    static double unit(long hash, int index) {
        final long h = hash * (0x9E3779B97F4A7C15L + index * 0x632BE59BD9B4E019L);
        return ((h >>> 11) ^ (h >>> 40)) % 1_000_000 / 1_000_000.0;
    }

    private static double smoothstep(double a, double b, double x) {
        final double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    public static final NumberParameter SIZE = new NumberParameter("size", "Region size", "The typical size of a region", 1400, 64, 16384, 1, true, "blocks");
    public static final NumberParameter COUNT = new NumberParameter("count", "Region types", "The number of different types the regions are divided into", 5, 2, 16, 1, false, null);
    public static final NumberParameter TYPE = new NumberParameter("type", "Type", "Which type of region this node selects (0 is the first)", 0, 0, 15, 1, false, null);
    public static final NumberParameter WARP = new NumberParameter("warp", "Warp", "How irregular the borders are", 500, 0, 4096, 1, false, "blocks");
    public static final NumberParameter FADE = new NumberParameter("fade", "Border", "How wide the transition between regions is", 120, 1, 2048, 1, false, "blocks");

    private static final long serialVersionUID = 1L;
}
