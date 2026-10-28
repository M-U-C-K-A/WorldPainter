package org.pepsoft.worldpainter.vanilla;

import java.util.ArrayList;
import java.util.List;

/**
 * A pure Java implementation of the overworld biome generator of Minecraft 1.18 and later (multi-noise climate
 * parameters looked up in the biome parameter tree of 1.21.4+), ported from the
 * <a href="https://github.com/Cubitect/cubiomes">cubiomes</a> library (MIT licence, copyright (c) 2020 Cubitect).
 *
 * <p>It predicts which biome Minecraft will generate at a location for a particular world seed, which WorldPainter
 * uses to show the land beyond the edges of the map and to look for seeds which match a map. Biomes are sampled at
 * Minecraft's biome resolution of one per 4x4x4 blocks. Instances are immutable and thread safe.
 */
public final class VanillaBiomeSource {
    public VanillaBiomeSource(long seed, boolean largeBiomes) {
        this.seed = seed;
        this.largeBiomes = largeBiomes;
        final Xoroshiro xr = new Xoroshiro(seed);
        final long xlo = xr.nextLong(), xhi = xr.nextLong();
        shift = initClimate(xlo, xhi, largeBiomes, NP_SHIFT);
        temperature = initClimate(xlo, xhi, largeBiomes, NP_TEMPERATURE);
        humidity = initClimate(xlo, xhi, largeBiomes, NP_HUMIDITY);
        continentalness = initClimate(xlo, xhi, largeBiomes, NP_CONTINENTALNESS);
        erosion = initClimate(xlo, xhi, largeBiomes, NP_EROSION);
        weirdness = initClimate(xlo, xhi, largeBiomes, NP_WEIRDNESS);
    }

    public long getSeed() {
        return seed;
    }

    public boolean isLargeBiomes() {
        return largeBiomes;
    }

    /**
     * Get the biome Minecraft generates at the surface at a block location (ignoring underground biomes such as caves).
     *
     * @return The biome as a cubiomes biome ID (see {@link VanillaBiomes}).
     */
    public int getSurfaceBiomeAtBlock(int x, int z) {
        return getSurfaceBiome(x >> 2, z >> 2);
    }

    /**
     * Get the biome at the surface at a location in biome coordinates (one quarter of block coordinates). The depth
     * parameter is zero at the surface, so the terrain shape does not need to be evaluated.
     *
     * @return The biome as a cubiomes biome ID (see {@link VanillaBiomes}).
     */
    public int getSurfaceBiome(int x, int z) {
        double px = x, pz = z;
        px += shift.sample(x, 0, z) * 4.0;
        pz += shift.sample(z, x, 0) * 4.0;
        final float c = (float) continentalness.sample(px, 0, pz);
        final float e = (float) erosion.sample(px, 0, pz);
        final float w = (float) weirdness.sample(px, 0, pz);
        final float t = (float) temperature.sample(px, 0, pz);
        final float h = (float) humidity.sample(px, 0, pz);
        final long[] np = {(long) (10000.0F * t), (long) (10000.0F * h), (long) (10000.0F * c), (long) (10000.0F * e), 0L, (long) (10000.0F * w)};
        return climateToBiome(np);
    }

    /**
     * An estimate of the height of the land Minecraft generates at a location in biome coordinates (one quarter of
     * block coordinates): where the terrain "depth" from the continentalness, erosion and weirdness spline reaches zero.
     * It leaves out the small scale noise and the jaggedness of peaks, so it gives the broad shape of the land: low
     * under oceans, around sea level on coasts and plains, high in mountains.
     *
     * @return The estimated height in blocks.
     */
    public float getApproximateSurfaceHeight(int x, int z) {
        double px = x, pz = z;
        px += shift.sample(x, 0, z) * 4.0;
        pz += shift.sample(z, x, 0) * 4.0;
        final float c = (float) continentalness.sample(px, 0, pz);
        final float e = (float) erosion.sample(px, 0, pz);
        final float w = (float) weirdness.sample(px, 0, pz);
        final float[] splineParams = {c, e, -3.0F * (Math.abs(Math.abs(w) - 0.6666667F) - 0.33333334F), w};
        final double offset = spline.get(splineParams) + 0.015F;
        // depth = 1 - y / 128 - 83 / 160 + offset is zero at the surface
        return (float) (128 * (1 - 83.0 / 160 + offset));
    }

    /**
     * Get the biome at a location in biome coordinates (one quarter of block coordinates), taking the depth below the
     * terrain surface into account, as Minecraft does.
     *
     * @return The biome as a cubiomes biome ID (see {@link VanillaBiomes}).
     */
    public int getBiome(int x, int y, int z) {
        double px = x, pz = z;
        px += shift.sample(x, 0, z) * 4.0;
        pz += shift.sample(z, x, 0) * 4.0;

        final float c = (float) continentalness.sample(px, 0, pz);
        final float e = (float) erosion.sample(px, 0, pz);
        final float w = (float) weirdness.sample(px, 0, pz);
        final float[] splineParams = {c, e, -3.0F * (Math.abs(Math.abs(w) - 0.6666667F) - 0.33333334F), w};
        final double offset = spline.get(splineParams) + 0.015F;
        final float d = (float) (1.0 - (y * 4) / 128.0 - 83.0 / 160.0 + offset);
        final float t = (float) temperature.sample(px, 0, pz);
        final float h = (float) humidity.sample(px, 0, pz);

        final long[] np = {(long) (10000.0F * t), (long) (10000.0F * h), (long) (10000.0F * c), (long) (10000.0F * e), (long) (10000.0F * d), (long) (10000.0F * w)};
        return climateToBiome(np);
    }

    // Biome parameter tree

    private static int climateToBiome(long[] np) {
        final int index = getResultingNode(np, 0, 0, -1L, 0);
        return (int) ((BiomeTree.NODES[index] >>> 48) & 0xFF);
    }

    private static long getDistance(long[] np, int index) {
        long ds = 0;
        final long node = BiomeTree.NODES[index];
        for (int i = 0; i < 6; i++) {
            final int parameter = (int) ((node >>> (8 * i)) & 0xFF);
            final long a = np[i] - BiomeTree.PARAMS[2 * parameter + 1];
            final long b = BiomeTree.PARAMS[2 * parameter] - np[i];
            final long d = (a > 0) ? a : ((b > 0) ? b : 0);
            ds += d * d;
        }
        return ds;
    }

    private static int getResultingNode(long[] np, int index, int alt, long ds, int depth) {
        if (BiomeTree.STEPS[depth] == 0) {
            return index;
        }
        int step;
        do {
            step = BiomeTree.STEPS[depth];
            depth++;
        } while (index + step >= BiomeTree.NODES.length);

        final long node = BiomeTree.NODES[index];
        int inner = (int) ((node >>> 48) & 0xFFFF);
        int leaf = alt;
        for (int i = 0; i < BiomeTree.ORDER; i++) {
            final long dsInner = getDistance(np, inner);
            if (Long.compareUnsigned(dsInner, ds) < 0) {
                final int leaf2 = getResultingNode(np, inner, leaf, ds, depth);
                final long dsLeaf2 = (inner == leaf2) ? dsInner : getDistance(np, leaf2);
                if (Long.compareUnsigned(dsLeaf2, ds) < 0) {
                    ds = dsLeaf2;
                    leaf = leaf2;
                }
            }
            inner = (inner + step) & 0xFFFF;
            if (inner >= BiomeTree.NODES.length) {
                break;
            }
        }
        return leaf;
    }

    // Climate noises

    private static DoublePerlinNoise initClimate(long xlo, long xhi, boolean large, int type) {
        switch (type) {
            case NP_SHIFT:
                return new DoublePerlinNoise(new Xoroshiro(xlo ^ 0x080518cf6af25384L, xhi ^ 0x3f3dfb40a54febd5L), new double[] {1, 1, 1, 0}, -3);
            case NP_TEMPERATURE:
                return new DoublePerlinNoise(new Xoroshiro(xlo ^ (large ? 0x944b0073edf549dbL : 0x5c7e6b29735f0d7fL), xhi ^ (large ? 0x4ff44347e9d22b96L : 0xf7d86f1bbc734988L)), new double[] {1.5, 0, 1, 0, 0, 0}, large ? -12 : -10);
            case NP_HUMIDITY:
                return new DoublePerlinNoise(new Xoroshiro(xlo ^ (large ? 0x71b8ab943dbd5301L : 0x81bb4d22e8dc168eL), xhi ^ (large ? 0xbb63ddcf39ff7a2bL : 0xf1c8b4bea16303cdL)), new double[] {1, 1, 0, 0, 0, 0}, large ? -10 : -8);
            case NP_CONTINENTALNESS:
                return new DoublePerlinNoise(new Xoroshiro(xlo ^ (large ? 0x9a3f51a113fce8dcL : 0x83886c9d0ae3a662L), xhi ^ (large ? 0xee2dbd157e5dcdadL : 0xafa638a61b42e8adL)), new double[] {1, 1, 2, 2, 2, 1, 1, 1, 1}, large ? -11 : -9);
            case NP_EROSION:
                return new DoublePerlinNoise(new Xoroshiro(xlo ^ (large ? 0x8c984b1f8702a951L : 0xd02491e6058f6fd8L), xhi ^ (large ? 0xead7b1f92bae535fL : 0x4792512c94c17a80L)), new double[] {1, 1, 0, 1, 1}, large ? -11 : -9);
            case NP_WEIRDNESS:
                return new DoublePerlinNoise(new Xoroshiro(xlo ^ 0xefc8ef4d36102b34L, xhi ^ 0x1beeeb324a0f24eaL), new double[] {1, 2, 1, 0, 0, 0}, -7);
            default:
                throw new IllegalArgumentException("Unknown climate parameter " + type);
        }
    }

    // Terrain offset spline, which determines the "depth" parameter

    private static Spline createOverworldSpline() {
        final Spline sp = new Spline(SP_CONTINENTALNESS);
        final Spline sp1 = createLandSpline(-0.15F, 0.00F, 0.0F, 0.1F, 0.00F, -0.03F, false);
        final Spline sp2 = createLandSpline(-0.10F, 0.03F, 0.1F, 0.1F, 0.01F, -0.03F, false);
        final Spline sp3 = createLandSpline(-0.10F, 0.03F, 0.1F, 0.7F, 0.01F, -0.03F, true);
        final Spline sp4 = createLandSpline(-0.05F, 0.03F, 0.1F, 1.0F, 0.01F, 0.01F, true);
        sp.add(-1.10F, Spline.fixed(0.044F), 0.0F);
        sp.add(-1.02F, Spline.fixed(-0.2222F), 0.0F);
        sp.add(-0.51F, Spline.fixed(-0.2222F), 0.0F);
        sp.add(-0.44F, Spline.fixed(-0.12F), 0.0F);
        sp.add(-0.18F, Spline.fixed(-0.12F), 0.0F);
        sp.add(-0.16F, sp1, 0.0F);
        sp.add(-0.15F, sp1, 0.0F);
        sp.add(-0.10F, sp2, 0.0F);
        sp.add(0.25F, sp3, 0.0F);
        sp.add(1.00F, sp4, 0.0F);
        return sp;
    }

    private static float getOffsetValue(float weirdness, float continentalness) {
        final float f0 = 1.0F - (1.0F - continentalness) * 0.5F;
        final float f1 = 0.5F * (1.0F - continentalness);
        final float f2 = (weirdness + 1.17F) * 0.46082947F;
        final float off = f2 * f0 - f1;
        if (weirdness < -0.7F) {
            return (off > -0.2222F) ? off : -0.2222F;
        } else {
            return (off > 0) ? off : 0;
        }
    }

    private static Spline createRidgeSpline(float f, boolean bl) {
        final Spline sp = new Spline(SP_RIDGES);
        final float i = getOffsetValue(-1.0F, f);
        final float k = getOffsetValue(1.0F, f);
        float l = 1.0F - (1.0F - f) * 0.5F;
        float u = 0.5F * (1.0F - f);
        l = u / (0.46082947F * l) - 1.17F;
        if ((-0.65F < l) && (l < 1.0F)) {
            u = getOffsetValue(-0.65F, f);
            final float p = getOffsetValue(-0.75F, f);
            final float q = (p - i) * 4.0F;
            final float r = getOffsetValue(l, f);
            final float s = (k - r) / (1.0F - l);
            sp.add(-1.0F, Spline.fixed(i), q);
            sp.add(-0.75F, Spline.fixed(p), 0);
            sp.add(-0.65F, Spline.fixed(u), 0);
            sp.add(l - 0.01F, Spline.fixed(r), 0);
            sp.add(l, Spline.fixed(r), s);
            sp.add(1.0F, Spline.fixed(k), s);
        } else {
            u = (k - i) * 0.5F;
            if (bl) {
                sp.add(-1.0F, Spline.fixed((float) ((i > 0.2) ? i : 0.2)), 0);
                sp.add(0.0F, Spline.fixed((float) lerp(0.5F, i, k)), u);
            } else {
                sp.add(-1.0F, Spline.fixed(i), u);
            }
            sp.add(1.0F, Spline.fixed(k), u);
        }
        return sp;
    }

    private static Spline createFlatOffsetSpline(float f, float g, float h, float i, float j, float k) {
        final Spline sp = new Spline(SP_RIDGES);
        float l = 0.5F * (g - f);
        if (l < k) {
            l = k;
        }
        final float m = 5.0F * (h - g);
        sp.add(-1.0F, Spline.fixed(f), l);
        sp.add(-0.4F, Spline.fixed(g), (l < m) ? l : m);
        sp.add(0.0F, Spline.fixed(h), m);
        sp.add(0.4F, Spline.fixed(i), 2.0F * (i - h));
        sp.add(1.0F, Spline.fixed(j), 0.7F * (j - i));
        return sp;
    }

    private static Spline createLandSpline(float f, float g, float h, float i, float j, float k, boolean bl) {
        final Spline sp1 = createRidgeSpline((float) lerp(i, 0.6F, 1.5F), bl);
        final Spline sp2 = createRidgeSpline((float) lerp(i, 0.6F, 1.0F), bl);
        final Spline sp3 = createRidgeSpline(i, bl);
        final float ih = 0.5F * i;
        final Spline sp4 = createFlatOffsetSpline(f - 0.15F, ih, ih, ih, i * 0.6F, 0.5F);
        final Spline sp5 = createFlatOffsetSpline(f, j * i, g * i, ih, i * 0.6F, 0.5F);
        final Spline sp6 = createFlatOffsetSpline(f, j, j, g, h, 0.5F);
        final Spline sp7 = createFlatOffsetSpline(f, j, j, g, h, 0.5F);

        final Spline sp8 = new Spline(SP_RIDGES);
        sp8.add(-1.0F, Spline.fixed(f), 0.0F);
        sp8.add(-0.4F, sp6, 0.0F);
        sp8.add(0.0F, Spline.fixed(h + 0.07F), 0.0F);

        final Spline sp9 = createFlatOffsetSpline(-0.02F, k, k, g, h, 0.0F);
        final Spline sp = new Spline(SP_EROSION);
        sp.add(-0.85F, sp1, 0.0F);
        sp.add(-0.7F, sp2, 0.0F);
        sp.add(-0.4F, sp3, 0.0F);
        sp.add(-0.35F, sp4, 0.0F);
        sp.add(-0.1F, sp5, 0.0F);
        sp.add(0.2F, sp6, 0.0F);
        if (bl) {
            sp.add(0.4F, sp7, 0.0F);
            sp.add(0.45F, sp8, 0.0F);
            sp.add(0.55F, sp8, 0.0F);
            sp.add(0.58F, sp7, 0.0F);
        }
        sp.add(0.7F, sp9, 0.0F);
        return sp;
    }

    static double lerp(double part, double from, double to) {
        return from + part * (to - from);
    }

    private final long seed;
    private final boolean largeBiomes;
    private final DoublePerlinNoise shift, temperature, humidity, continentalness, erosion, weirdness;
    /**
     * The terrain offset spline does not depend on the seed (and is only read), so it is shared.
     */
    private static final Spline spline = createOverworldSpline();

    /**
     * The biome Y coordinate (block Y / 4) at which surface biomes are sampled: sea level.
     */
    public static final int SURFACE_QUART_Y = 15;

    private static final int NP_TEMPERATURE = 0, NP_HUMIDITY = 1, NP_CONTINENTALNESS = 2, NP_EROSION = 3, NP_SHIFT = 4, NP_WEIRDNESS = 5;
    private static final int SP_CONTINENTALNESS = 0, SP_EROSION = 1, SP_RIDGES = 2;

    /**
     * Minecraft's Xoroshiro128++ random number generator.
     */
    static final class Xoroshiro {
        Xoroshiro(long seed) {
            final long xl = 0x9e3779b97f4a7c15L, xh = 0x6a09e667f3bcc909L, a = 0xbf58476d1ce4e5b9L, b = 0x94d049bb133111ebL;
            long l = seed ^ xh;
            long h = l + xl;
            l = (l ^ (l >>> 30)) * a;
            h = (h ^ (h >>> 30)) * a;
            l = (l ^ (l >>> 27)) * b;
            h = (h ^ (h >>> 27)) * b;
            lo = l ^ (l >>> 31);
            hi = h ^ (h >>> 31);
        }

        Xoroshiro(long lo, long hi) {
            this.lo = lo;
            this.hi = hi;
        }

        long nextLong() {
            final long l = lo;
            long h = hi;
            final long n = Long.rotateLeft(l + h, 17) + l;
            h ^= l;
            lo = Long.rotateLeft(l, 49) ^ h ^ (h << 21);
            hi = Long.rotateLeft(h, 28);
            return n;
        }

        int nextInt(int n) {
            long r = (nextLong() & 0xFFFFFFFFL) * n;
            if ((r & 0xFFFFFFFFL) < n) {
                final int threshold = Integer.remainderUnsigned(-n, n);
                while (Integer.compareUnsigned((int) r, threshold) < 0) {
                    r = (nextLong() & 0xFFFFFFFFL) * n;
                }
            }
            return (int) (r >>> 32);
        }

        double nextDouble() {
            return (nextLong() >>> 11) * 1.1102230246251565E-16;
        }

        private long lo, hi;
    }

    /**
     * Improved Perlin noise as used by Minecraft.
     */
    static final class PerlinNoise {
        PerlinNoise(Xoroshiro xr) {
            a = xr.nextDouble() * 256.0;
            b = xr.nextDouble() * 256.0;
            c = xr.nextDouble() * 256.0;
            for (int i = 0; i < 256; i++) {
                d[i] = i;
            }
            for (int i = 0; i < 256; i++) {
                final int j = xr.nextInt(256 - i) + i;
                final int n = d[i];
                d[i] = d[j];
                d[j] = n;
            }
            d[256] = d[0];
            final double i2 = Math.floor(b);
            final double d2 = b - i2;
            h2 = ((int) i2) & 0xFF;
            this.d2 = d2;
            t2 = d2 * d2 * d2 * (d2 * (d2 * 6.0 - 15.0) + 10.0);
        }

        double sample(double d1, double d2, double d3) {
            final int h1, h2, h3;
            final double t1, t2, t3;
            if (d2 == 0.0) {
                d2 = this.d2;
                h2 = this.h2;
                t2 = this.t2;
            } else {
                d2 += b;
                final double i2 = Math.floor(d2);
                d2 -= i2;
                h2 = ((int) i2) & 0xFF;
                t2 = d2 * d2 * d2 * (d2 * (d2 * 6.0 - 15.0) + 10.0);
            }
            d1 += a;
            d3 += c;
            final double i1 = Math.floor(d1), i3 = Math.floor(d3);
            d1 -= i1;
            d3 -= i3;
            h1 = ((int) i1) & 0xFF;
            h3 = ((int) i3) & 0xFF;
            t1 = d1 * d1 * d1 * (d1 * (d1 * 6.0 - 15.0) + 10.0);
            t3 = d3 * d3 * d3 * (d3 * (d3 * 6.0 - 15.0) + 10.0);

            final int[] idx = d;
            final int v1a = (idx[h1] + h2) & 0xFF, v1b = (idx[h1 + 1] + h2) & 0xFF;
            final int v2a = (idx[v1a] + h3) & 0xFF, v2b = (idx[v1a + 1] + h3) & 0xFF;
            final int v3a = (idx[v1b] + h3) & 0xFF, v3b = (idx[v1b + 1] + h3) & 0xFF;

            double l1 = gradient(idx[v2a], d1, d2, d3);
            double l5 = gradient(idx[v2a + 1], d1, d2, d3 - 1);
            double l2 = gradient(idx[v3a], d1 - 1, d2, d3);
            final double l6 = gradient(idx[v3a + 1], d1 - 1, d2, d3 - 1);
            double l3 = gradient(idx[v2b], d1, d2 - 1, d3);
            double l7 = gradient(idx[v2b + 1], d1, d2 - 1, d3 - 1);
            final double l4 = gradient(idx[v3b], d1 - 1, d2 - 1, d3);
            final double l8 = gradient(idx[v3b + 1], d1 - 1, d2 - 1, d3 - 1);

            l1 = lerp(t1, l1, l2);
            l3 = lerp(t1, l3, l4);
            l5 = lerp(t1, l5, l6);
            l7 = lerp(t1, l7, l8);
            l1 = lerp(t2, l1, l3);
            l5 = lerp(t2, l5, l7);
            return lerp(t3, l1, l5);
        }

        private static double gradient(int idx, double a, double b, double c) {
            switch (idx & 0xf) {
                case 0: return a + b;
                case 1: return -a + b;
                case 2: return a - b;
                case 3: return -a - b;
                case 4: return a + c;
                case 5: return -a + c;
                case 6: return a - c;
                case 7: return -a - c;
                case 8: return b + c;
                case 9: return -b + c;
                case 10: return b - c;
                case 11: return -b - c;
                case 12: return a + b;
                case 13: return -b + c;
                case 14: return -a + b;
                case 15: return -b - c;
                default: throw new InternalError();
            }
        }

        private final int[] d = new int[257];
        private final double a, b, c, d2, t2;
        private final int h2;
        double amplitude = 1.0, lacunarity = 1.0;
    }

    /**
     * Several octaves of Perlin noise.
     */
    static final class OctaveNoise {
        OctaveNoise(Xoroshiro xr, double[] amplitudes, int omin) {
            final int len = amplitudes.length;
            double lacuna = LACUNA_INI[-omin];
            double persist = PERSIST_INI[len];
            final long xlo = xr.nextLong(), xhi = xr.nextLong();
            final List<PerlinNoise> list = new ArrayList<>();
            for (int i = 0; i < len; i++, lacuna *= 2.0, persist *= 0.5) {
                if (amplitudes[i] == 0) {
                    continue;
                }
                final PerlinNoise noise = new PerlinNoise(new Xoroshiro(xlo ^ MD5_OCTAVE_N[12 + omin + i][0], xhi ^ MD5_OCTAVE_N[12 + omin + i][1]));
                noise.amplitude = amplitudes[i] * persist;
                noise.lacunarity = lacuna;
                list.add(noise);
            }
            octaves = list.toArray(new PerlinNoise[0]);
        }

        double sample(double x, double y, double z) {
            double v = 0;
            for (PerlinNoise p: octaves) {
                final double lf = p.lacunarity;
                v += p.amplitude * p.sample(x * lf, y * lf, z * lf);
            }
            return v;
        }

        private final PerlinNoise[] octaves;

        private static final long[][] MD5_OCTAVE_N = {
            {0xb198de63a8012672L, 0x7b84cad43ef7b5a8L}, // md5 "octave_-12"
            {0x0fd787bfbc403ec3L, 0x74a4a31ca21b48b8L}, // md5 "octave_-11"
            {0x36d326eed40efeb2L, 0x5be9ce18223c636aL}, // md5 "octave_-10"
            {0x082fe255f8be6631L, 0x4e96119e22dedc81L}, // md5 "octave_-9"
            {0x0ef68ec68504005eL, 0x48b6bf93a2789640L}, // md5 "octave_-8"
            {0xf11268128982754fL, 0x257a1d670430b0aaL}, // md5 "octave_-7"
            {0xe51c98ce7d1de664L, 0x5f9478a733040c45L}, // md5 "octave_-6"
            {0x6d7b49e7e429850aL, 0x2e3063c622a24777L}, // md5 "octave_-5"
            {0xbd90d5377ba1b762L, 0xc07317d419a7548dL}, // md5 "octave_-4"
            {0x53d39c6752dac858L, 0xbcd1c5a80ab65b3eL}, // md5 "octave_-3"
            {0xb4a24d7a84e7677bL, 0x023ff9668e89b5c4L}, // md5 "octave_-2"
            {0xdffa22b534c5f608L, 0xb9b67517d3665ca9L}, // md5 "octave_-1"
            {0xd50708086cef4d7cL, 0x6e1651ecc7f43309L}, // md5 "octave_0"
        };
        private static final double[] LACUNA_INI = {1, .5, .25, 1. / 8, 1. / 16, 1. / 32, 1. / 64, 1. / 128, 1. / 256, 1. / 512, 1. / 1024, 1. / 2048, 1. / 4096};
        private static final double[] PERSIST_INI = {0, 1, 2. / 3, 4. / 7, 8. / 15, 16. / 31, 32. / 63, 64. / 127, 128. / 255, 256. / 511};
    }

    /**
     * Two octave noises sampled at slightly different scales and added together.
     */
    static final class DoublePerlinNoise {
        DoublePerlinNoise(Xoroshiro xr, double[] amplitudes, int omin) {
            octA = new OctaveNoise(xr, amplitudes, omin);
            octB = new OctaveNoise(xr, amplitudes, omin);
            int len = amplitudes.length;
            for (int i = len - 1; (i >= 0) && (amplitudes[i] == 0.0); i--) {
                len--;
            }
            for (int i = 0; amplitudes[i] == 0.0; i++) {
                len--;
            }
            amplitude = AMP_INI[len];
        }

        double sample(double x, double y, double z) {
            final double f = 337.0 / 331.0;
            return (octA.sample(x, y, z) + octB.sample(x * f, y * f, z * f)) * amplitude;
        }

        private final OctaveNoise octA, octB;
        private final double amplitude;

        private static final double[] AMP_INI = {0, 5. / 6, 10. / 9, 15. / 12, 20. / 15, 25. / 18, 30. / 21, 35. / 24, 40. / 27, 45. / 30};
    }

    /**
     * A cubic spline over one of the climate parameters, whose points are either fixed values or nested splines.
     */
    static final class Spline {
        Spline(int type) {
            this.type = type;
            this.value = 0;
        }

        private Spline(float value) {
            this.type = -1;
            this.value = value;
        }

        static Spline fixed(float value) {
            return new Spline(value);
        }

        void add(float location, Spline value, float derivative) {
            locations[length] = location;
            values[length] = value;
            derivatives[length] = derivative;
            length++;
        }

        float get(float[] params) {
            if (type == -1) {
                return value;
            }
            final float f = params[type];
            int i;
            for (i = 0; i < length; i++) {
                if (locations[i] >= f) {
                    break;
                }
            }
            if ((i == 0) || (i == length)) {
                if (i != 0) {
                    i--;
                }
                final float v = values[i].get(params);
                return v + derivatives[i] * (f - locations[i]);
            }
            final Spline sp1 = values[i - 1], sp2 = values[i];
            final float g = locations[i - 1], h = locations[i];
            final float k = (f - g) / (h - g);
            final float l = derivatives[i - 1], m = derivatives[i];
            final float n = sp1.get(params), o = sp2.get(params);
            final float p = l * (h - g) - (o - n);
            final float q = -m * (h - g) + (o - n);
            return (float) (lerp(k, n, o) + k * (1.0F - k) * lerp(k, p, q));
        }

        private final int type;
        private final float value;
        private final float[] locations = new float[12], derivatives = new float[12];
        private final Spline[] values = new Spline[12];
        private int length;
    }
}
