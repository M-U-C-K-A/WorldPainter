package org.pepsoft.worldpainter.procedural;

import org.pepsoft.worldpainter.Terrain;

import java.util.ArrayList;
import java.util.List;

/**
 * A compiled, thread safe evaluator for {@link ProceduralTerrainSettings}. Heights are deterministic functions of the
 * world coordinates and the seed, so arbitrarily large areas can be generated tile by tile, in parallel, without seams.
 */
public final class ProceduralTerrain {
    public ProceduralTerrain(ProceduralTerrainSettings settings) {
        this.settings = settings.clone();
        final long seed = settings.getSeed();
        for (NoiseLayer layer: this.settings.getLayers()) {
            if (layer.isEnabled() && (layer.getScale() > 0)) {
                layers.add(new CompiledLayer(layer, seed));
            }
        }
        rules = this.settings.getRules().toArray(new SurfaceRule[0]);
        jitterNoise = new SimplexNoise(seed ^ 0x5DEECE66DL);
    }

    public ProceduralTerrainSettings getSettings() {
        return settings;
    }

    /**
     * Get the height of the terrain at a particular location.
     */
    public float getHeight(double x, double y) {
        float total = 0;
        for (CompiledLayer layer: layers) {
            total = layer.apply(x, y, total);
        }
        float height = settings.getBaseHeight() + total;
        final int terraceHeight = settings.getTerraceHeight();
        if (terraceHeight > 0) {
            final float k = height / terraceHeight;
            final float floor = (float) Math.floor(k);
            final float fraction = k - floor;
            final float exponent = 1 + 7 * settings.getTerraceSharpness();
            height = (floor + (float) Math.pow(fraction, exponent)) * terraceHeight;
        }
        return height;
    }

    /**
     * Generate the heights for a rectangular area.
     *
     * @param x0     The X coordinate of the top left corner.
     * @param y0     The Y coordinate of the top left corner.
     * @param width  The width of the area.
     * @param height The height of the area.
     * @param out    The array, of length at least {@code width * height}, in which to store the heights in row major
     *               order.
     */
    public void getHeights(int x0, int y0, int width, int height, float[] out) {
        for (int dy = 0; dy < height; dy++) {
            for (int dx = 0; dx < width; dx++) {
                out[dy * width + dx] = getHeight(x0 + dx, y0 + dy);
            }
        }
    }

    /**
     * Determine the terrain type for a column.
     *
     * @param x      The X coordinate of the column.
     * @param y      The Y coordinate of the column.
     * @param height The height of the column.
     * @param slope  The steepness of the terrain at the column, in degrees.
     */
    public Terrain getTerrain(int x, int y, float height, float slope) {
        final float jitter = (float) (jitterNoise.noise(x / 24.0, y / 24.0) * 0.7 + jitterNoise.noise(x / 6.0, y / 6.0) * 0.3);
        for (SurfaceRule rule: rules) {
            if (rule.matches(height, slope, jitter)) {
                return rule.getTerrain();
            }
        }
        return settings.getDefaultTerrain();
    }

    /**
     * Calculate the steepness in degrees at the centre of a 3x3 neighbourhood of heights.
     */
    public static float slope(float west, float east, float north, float south) {
        final float dx = (east - west) / 2, dy = (south - north) / 2;
        return (float) Math.toDegrees(Math.atan(Math.sqrt(dx * dx + dy * dy)));
    }

    private final ProceduralTerrainSettings settings;
    private final List<CompiledLayer> layers = new ArrayList<>();
    private final SurfaceRule[] rules;
    private final SimplexNoise jitterNoise;

    private static final class CompiledLayer {
        CompiledLayer(NoiseLayer layer, long seed) {
            blendMode = layer.getBlendMode();
            final long layerSeed = seed + layer.getSeedOffset() * 0x9E3779B97F4A7C15L;
            noise = new FractalNoise(layerSeed, layer.getType(), layer.getScale(), layer.getOctaves(), layer.getRoughness(), layer.getWarp(), layer.getDirection());
            maskNoise = (layer.getCoverage() < 1.0f) ? new SimplexNoise(layerSeed + 2) : null;
            scale = layer.getScale();
            amplitude = layer.getAmplitude();
            sharpness = layer.getSharpness();
            coverage = layer.getCoverage();
        }

        float apply(double x, double y, float total) {
            if (noise.isWarped()) {
                final double wx = noise.warpX(x, y), wy = noise.warpY(x, y);
                x += wx;
                y += wy;
            }
            double value = noise.value(x / scale, y / scale);
            if (sharpness != 1.0f) {
                value = Math.pow(value, sharpness);
            }
            double mask = 1.0;
            if (maskNoise != null) {
                final double m = 0.5 + 0.5 * (maskNoise.noise(x / (scale * 3), y / (scale * 3)) * 0.75 + maskNoise.noise(x / (scale * 1.5), y / (scale * 1.5)) * 0.25);
                mask = smoothStep(1 - coverage - 0.12, 1 - coverage + 0.12, m);
            }
            switch (blendMode) {
                case ADD:
                    return (float) (total + value * mask * amplitude);
                case SUBTRACT:
                    return (float) (total - value * mask * amplitude);
                case MAX:
                    return (float) Math.max(total, value * mask * amplitude);
                case MIN:
                    return (float) ((mask > 0) ? Math.min(total, total + (value * amplitude - total) * mask) : total);
                case MULTIPLY:
                    return (float) (total * (1 + (value - 1) * mask));
                default:
                    throw new InternalError();
            }
        }

        private final NoiseLayer.BlendMode blendMode;
        private final FractalNoise noise;
        private final SimplexNoise maskNoise;
        private final float scale, amplitude, sharpness, coverage;
    }

    static double smoothStep(double edge0, double edge1, double x) {
        final double t = Math.min(Math.max((x - edge0) / (edge1 - edge0), 0), 1);
        return t * t * (3 - 2 * t);
    }
}
