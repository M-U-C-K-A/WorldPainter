package org.pepsoft.worldpainter.procedural;

/**
 * Seedable fractal noise of one of the {@link NoiseLayer.Type types}, optionally domain warped, producing values
 * between zero and one. Shared by the {@link ProceduralTerrain procedural terrain} and the node graph generators.
 * Instances are immutable and thread safe.
 */
public final class FractalNoise {
    /**
     * @param seed      The seed. The warp, if any, uses {@code seed + 1}.
     * @param type      The kind of noise.
     * @param scale     The size of the largest features, in blocks.
     * @param octaves   The number of successively finer octaves (1-10).
     * @param roughness How strong each finer octave is compared to the previous one (0-1).
     * @param warp      How far the coordinates are distorted by another noise field, in blocks, or zero for no warp.
     * @param direction The direction of the ridges in degrees, for {@link NoiseLayer.Type#DUNES}.
     */
    public FractalNoise(long seed, NoiseLayer.Type type, double scale, int octaves, double roughness, double warp, double direction) {
        this.type = type;
        this.scale = scale;
        this.warp = warp;
        this.octaves = Math.max(1, Math.min(octaves, 10));
        this.roughness = roughness;
        noise = new SimplexNoise(seed);
        warpNoise = (warp > 0) ? new SimplexNoise(seed + 1) : null;
        final double angle = Math.toRadians(direction);
        cos = Math.cos(angle);
        sin = Math.sin(angle);
        double norm = 0, amp = 1;
        for (int i = 0; i < this.octaves; i++) {
            norm += amp;
            amp *= roughness;
        }
        normalisation = norm;
    }

    public boolean isWarped() {
        return warpNoise != null;
    }

    /**
     * The horizontal displacement of the domain warp at a location, in blocks.
     */
    public double warpX(double x, double y) {
        return warpNoise.noise(x / (scale * 0.75), y / (scale * 0.75)) * warp;
    }

    /**
     * The vertical displacement of the domain warp at a location, in blocks.
     */
    public double warpY(double x, double y) {
        return warpNoise.noise(x / (scale * 0.75) + 31.7, y / (scale * 0.75) - 47.3) * warp;
    }

    /**
     * Sample the noise at a location in blocks, applying the warp and the scale.
     *
     * @return A value between zero and one.
     */
    public double sample(double x, double y) {
        if (warpNoise != null) {
            final double wx = warpX(x, y), wy = warpY(x, y);
            x += wx;
            y += wy;
        }
        return value(x / scale, y / scale);
    }

    /**
     * Sample the noise at a location in noise space (blocks divided by the scale), without warp.
     *
     * @return A value between zero and one.
     */
    public double value(double x, double y) {
        switch (type) {
            case FBM: {
                double sum = 0, amp = 1, frequency = 1;
                for (int i = 0; i < octaves; i++) {
                    sum += noise.noise(x * frequency + i * 17.31, y * frequency - i * 9.13) * amp;
                    amp *= roughness;
                    frequency *= LACUNARITY;
                }
                return 0.5 + 0.5 * Math.tanh(TANH_GAIN * sum / normalisation) / TANH_NORM;
            }
            case RIDGED: {
                double sum = 0, amp = 1, frequency = 1, weight = 1;
                for (int i = 0; i < octaves; i++) {
                    double r = 1 - Math.abs(noise.noise(x * frequency + i * 17.31, y * frequency - i * 9.13));
                    r *= r * weight;
                    weight = Math.min(Math.max(r * 2, 0), 1);
                    sum += r * amp;
                    amp *= roughness;
                    frequency *= LACUNARITY;
                }
                return Math.min(1.0, sum / normalisation * 1.25);
            }
            case BILLOW: {
                double sum = 0, amp = 1, frequency = 1;
                for (int i = 0; i < octaves; i++) {
                    sum += Math.abs(noise.noise(x * frequency + i * 17.31, y * frequency - i * 9.13)) * amp;
                    amp *= roughness;
                    frequency *= LACUNARITY;
                }
                return Math.min(1.0, sum / normalisation * 1.6);
            }
            case CELLS: {
                final double[] f = CELLULAR_BUFFER.get();
                noise.cellular(x, y, f);
                double value = Math.max(0, 1 - f[0]);
                if (octaves > 1) {
                    // Add some fractal detail
                    double sum = 0, amp = roughness, frequency = LACUNARITY * 2;
                    for (int i = 1; i < octaves; i++) {
                        sum += noise.noise(x * frequency + i * 17.31, y * frequency - i * 9.13) * amp;
                        amp *= roughness;
                        frequency *= LACUNARITY;
                    }
                    value = Math.min(1, Math.max(0, value + sum * 0.25));
                }
                return value;
            }
            case DUNES: {
                final double u = x * cos + y * sin;
                final double variation = noise.noise(x * 0.35, y * 0.35);
                double phase = u + variation * 0.6;
                phase -= Math.floor(phase);
                // Gentle windward slope, steep lee slope
                final double profile = (phase < 0.75) ? ProceduralTerrain.smoothStep(0, 0.75, phase) : ProceduralTerrain.smoothStep(1, 0.75, phase);
                double detail = 0, amp = 1, frequency = 2;
                for (int i = 1; i < octaves; i++) {
                    amp *= roughness;
                    detail += noise.noise(x * frequency + i * 17.31, y * frequency - i * 9.13) * amp;
                    frequency *= LACUNARITY;
                }
                return Math.min(1, Math.max(0, profile * (0.75 + 0.25 * noise.noise(x * 0.15 + 50, y * 0.15)) + detail * 0.15));
            }
            default:
                throw new InternalError();
        }
    }

    private final NoiseLayer.Type type;
    private final SimplexNoise noise, warpNoise;
    private final double scale, warp, roughness, normalisation, cos, sin;
    private final int octaves;

    private static final double LACUNARITY = 2.0;
    private static final double TANH_GAIN = 1.8, TANH_NORM = Math.tanh(TANH_GAIN);
    private static final ThreadLocal<double[]> CELLULAR_BUFFER = ThreadLocal.withInitial(() -> new double[2]);
}
