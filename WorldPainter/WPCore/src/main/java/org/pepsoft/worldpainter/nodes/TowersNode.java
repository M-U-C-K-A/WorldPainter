package org.pepsoft.worldpainter.nodes;

import org.pepsoft.worldpainter.procedural.SimplexNoise;

import java.util.List;

import static java.util.Collections.emptyList;

/**
 * A generator of tall rock towers with steep, grooved walls standing on flat ground, like the karst pillars of tower
 * karst landscapes. Each tower has its own height and width; the density decides how many places get a tower.
 */
public class TowersNode extends Node {
    @Override
    public String getName() {
        return "Rock towers";
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
        return List.of(SPACING, WIDTH, DENSITY, HEIGHT_VARIATION, STEEPNESS, GROOVES, VARIANT);
    }

    @Override
    CompiledNode compile(CompileContext context, CompiledNode[] inputs) {
        final long seed = seed(context, getInt(VARIANT));
        final double spacing = getDouble(SPACING), width = getDouble(WIDTH), density = getDouble(DENSITY);
        final double heightVariation = getDouble(HEIGHT_VARIATION), steepness = getDouble(STEEPNESS), grooves = getDouble(GROOVES);
        final SimplexNoise grooveNoise = new SimplexNoise(seed ^ 0x5DEECE66DL);
        return new CompiledNode.Generator() {
            @Override
            float value(double x, double y) {
                final double gx = x / spacing, gy = y / spacing;
                final int cx = (int) Math.floor(gx), cy = (int) Math.floor(gy);
                double best = 0;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        final long hash = hash(seed, cx + dx, cy + dy);
                        if (unit(hash, 0) >= density) {
                            continue;
                        }
                        // The centre, radius and height of the tower of this cell
                        final double px = cx + dx + 0.2 + 0.6 * unit(hash, 1), py = cy + dy + 0.2 + 0.6 * unit(hash, 2);
                        final double radius = width * (0.6 + 0.8 * unit(hash, 3));
                        final double top = 1 - heightVariation * unit(hash, 4);
                        // Grooves running down the walls: the radius varies around the tower
                        final double angle = Math.atan2(gy - py, gx - px);
                        final double groove = grooves * 0.25 * grooveNoise.noise(Math.cos(angle) * 3 + (cx + dx) * 7.1, Math.sin(angle) * 3 + (cy + dy) * 3.7);
                        final double t = Math.hypot(gx - px, gy - py) / (radius * (1 + groove));
                        if (t >= 1) {
                            continue;
                        }
                        // Steep walls (the edge is soft only over the outer part), a slightly rounded top
                        final double edge = Math.max(0.02, 1 - steepness);
                        final double wall = 1 - smoothstep(1 - edge, 1, t);
                        best = Math.max(best, top * wall * (1 - 0.08 * t * t));
                    }
                }
                return (float) best;
            }
        };
    }

    private static long hash(long seed, int x, int y) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return h;
    }

    /**
     * A number between 0 and 1 from a hash, different for each index.
     */
    private static double unit(long hash, int index) {
        final long h = hash * (0x9E3779B97F4A7C15L + index * 0x632BE59BD9B4E019L);
        return ((h >>> 11) ^ (h >>> 40)) % 1_000_000 / 1_000_000.0;
    }

    private static double smoothstep(double a, double b, double x) {
        final double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    public static final NumberParameter SPACING = new NumberParameter("spacing", "Spacing", "The average distance between towers", 120, 16, 2048, 1, true, "blocks");
    public static final NumberParameter WIDTH = new NumberParameter("width", "Width", "The radius of the towers, relative to the spacing", 0.22, 0.05, 0.5, 0.01, false, null);
    public static final NumberParameter DENSITY = new NumberParameter("density", "Density", "The fraction of places which get a tower", 0.7, 0, 1, 0.01, false, null);
    public static final NumberParameter HEIGHT_VARIATION = new NumberParameter("heightVariation", "Height variation", "How much the heights of the towers differ", 0.6, 0, 1, 0.01, false, null);
    public static final NumberParameter STEEPNESS = new NumberParameter("steepness", "Steepness", "How vertical the walls are", 0.85, 0, 1, 0.01, false, null);
    public static final NumberParameter GROOVES = new NumberParameter("grooves", "Grooves", "How deeply the walls are fluted", 0.5, 0, 1, 0.01, false, null);

    private static final long serialVersionUID = 1L;
}
