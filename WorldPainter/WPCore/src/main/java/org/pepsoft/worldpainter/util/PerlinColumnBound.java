package org.pepsoft.worldpainter.util;

import org.pepsoft.util.PerlinNoise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Random;

/**
 * Upper bounds of the three dimensional {@link PerlinNoise} of a generator along a vertical column, so that
 * the exporters can skip the blocks where the noise cannot reach a threshold without asking the generator, as the ores
 * of the Resources layer and the patches of granite, diorite and andesite of the Stone Mix terrain do. Skipping only where the noise surely stays below the
 * threshold leaves exactly the same ores.
 *
 * <p>Along a column, inside a cell of the lattice, the noise blends between two straight lines, made of the gradients
 * of the bottom and the top corners of the cell, with the fade of the height in the cell as the weight. So it cannot be
 * above either line anywhere in the cell, which rules out most cells at once, and in the others it can be worked out
 * much more cheaply than by the generator. The bounds are a little larger than that, to allow for rounding.
 *
 * <p>The bound needs the permutation and gradient tables of the generator, which the library does not expose; they are
 * read by reflection, and checked against the real noise. If that fails, {@link #create(PerlinNoise)} returns
 * {@code null} and the noise is worked out everywhere, as before. Instances are not thread safe.
 */
public final class PerlinColumnBound {
    private PerlinColumnBound(short[] permutations, float[] gradients) {
        this.permutations = permutations;
        this.gradients = gradients;
    }

    /**
     * Create a bound for a noise generator, after its seed has been set.
     *
     * @return The bound, or {@code null} if it cannot be worked out for this generator.
     */
    public static PerlinColumnBound create(PerlinNoise noise) {
        try {
            final Object fastPerlin = FAST_PERLIN.get(noise);
            final PerlinColumnBound bound = new PerlinColumnBound((short[]) PERMUTATIONS.get(fastPerlin), (float[]) GRADIENTS.get(null));
            return bound.check(noise) ? bound : null;
        } catch (RuntimeException | IllegalAccessException e) {
            return null;
        }
    }

    /**
     * Another bound for the same generator, to use on another thread.
     */
    public PerlinColumnBound copy() {
        return new PerlinColumnBound(permutations, gradients);
    }

    /**
     * Move to a column, in the coordinates of the noise.
     */
    public void setColumn(double x, double y) {
        final double floorX = Math.floor(x), floorY = Math.floor(y);
        final int cellY = (int) floorY;
        u = (float) (x - floorX);
        v = (float) (y - floorY);
        final double fadeU = fade(u), fadeV = fade(v);
        addCorner(0, u, v, (1 - fadeU) * (1 - fadeV));
        addCorner(1, u, v - 1, (1 - fadeU) * fadeV);
        addCorner(2, u - 1, v, fadeU * (1 - fadeV));
        addCorner(3, u - 1, v - 1, fadeU * fadeV);
        final int a = pair((int) floorX);
        aa = pair(a + cellY);
        ab = pair((a >> 8) + cellY);
        cell = Integer.MIN_VALUE;
    }

    /**
     * Move to a cell of the column: the heights {@code z} for which {@code Math.floor(z) == cell}.
     */
    public void setCell(int cell) {
        if (cell == this.cell) {
            return;
        }
        this.cell = cell;
        final int hash1 = pair(aa + cell), hash2 = pair((aa >> 8) + cell), hash3 = pair(ab + cell), hash4 = pair((ab >> 8) + cell);
        final int bottom1 = hash1 & 15, bottom2 = 16 | (hash2 & 15), bottom3 = 32 | (hash3 & 15), bottom4 = 48 | (hash4 & 15);
        final int top1 = (hash1 >> 8) & 15, top2 = 16 | ((hash2 >> 8) & 15), top3 = 32 | ((hash3 >> 8) & 15), top4 = 48 | ((hash4 >> 8) & 15);
        bottom0 = bases[bottom1] + bases[bottom2] + bases[bottom3] + bases[bottom4];
        bottomSlope = slopes[bottom1] + slopes[bottom2] + slopes[bottom3] + slopes[bottom4];
        top0 = bases[top1] + bases[top2] + bases[top3] + bases[top4];
        topSlope = slopes[top1] + slopes[top2] + slopes[top3] + slopes[top4];
    }

    /**
     * The most the noise, as {@link PerlinNoise#getPerlinNoise(double, double, double)} gives it, can be anywhere in
     * the current cell.
     */
    public float getCellMax() {
        // The noise is a blend between the bottom and top lines, so it cannot be above either of them
        final double max = Math.max(Math.max(bottom0, bottom0 + bottomSlope), Math.max(top0 - topSlope, top0));
        return (float) ((max + MARGIN) * SCALE_3D + MARGIN);
    }

    /**
     * The most the noise, as {@link PerlinNoise#getPerlinNoise(double, double, double)} gives it, can be at a height in
     * the current cell: the noise itself, worked out more cheaply, plus a margin for rounding.
     */
    public float getMax(double z) {
        final float w = positionInCell(z);
        return getMax(w, fade(w));
    }

    /**
     * The same as {@link #getMax(double)}, with the position in the cell and its fade worked out beforehand by
     * {@link #positionInCell(double)} and {@link #fade(float)}.
     */
    public float getMax(float w, double fadeW) {
        final double bottom = bottom0 + bottomSlope * w, top = top0 + topSlope * (w - 1);
        final double noise = bottom + fadeW * (top - bottom);
        return (float) ((noise + MARGIN) * SCALE_3D + MARGIN);
    }

    /**
     * The position of a height in its cell, as the generator works it out.
     */
    public static float positionInCell(double z) {
        return (float) (z - Math.floor(z));
    }

    /**
     * Whether the noise may be above a level at a place, for a caller which asks about the places of a column one
     * after the other: the column and the cell are only worked out again when they change.
     *
     * @return {@code false} if the noise, as {@link PerlinNoise#getPerlinNoise(double, double, double)} gives it, is
     * surely not above the level at that place.
     */
    public boolean mayExceed(double x, double y, double z, float level) {
        if ((x != columnX) || (y != columnY)) {
            setColumn(x, y);
            columnX = x;
            columnY = y;
        }
        final int cell = (int) Math.floor(z);
        if (cell != this.cell) {
            setCell(cell);
            cellMax = getCellMax();
        }
        return (cellMax > level) && (getMax(z) > level);
    }

    /**
     * Work out, for one corner of the cells of the column, what each of the sixteen gradients adds to the noise along
     * the column (where it starts and how steeply it rises), weighed by the fades across the column.
     */
    private void addCorner(int corner, float x, float y, double weight) {
        for (int gradient = 0; gradient < 16; gradient++) {
            bases[(corner << 4) | gradient] = weight * (x * gradients[gradient * 3] + y * gradients[gradient * 3 + 1]);
            slopes[(corner << 4) | gradient] = weight * gradients[gradient * 3 + 2];
        }
    }

    private int pair(int index) {
        return permutations[index & 255] & 0xffff;
    }

    /**
     * Check the bound against the noise at random places, in case the library works differently than assumed.
     */
    private boolean check(PerlinNoise noise) {
        final Random random = new Random(noise.getSeed());
        for (int i = 0; i < 2000; i++) {
            final double x = (random.nextDouble() - 0.5) * 2000, y = (random.nextDouble() - 0.5) * 2000, z = (random.nextDouble() - 0.5) * 200;
            setColumn(x, y);
            setCell((int) Math.floor(z));
            final float value = noise.getPerlinNoise(x, y, z);
            if ((value > getCellMax()) || (value > getMax(z)) || (getMax(z) - value > 1e-3f)) {
                logger.warn("Bound of Perlin noise does not hold; ores are exported without it");
                return false;
            }
        }
        return true;
    }

    public static float fade(float t) {
        return t * t * t * Math.fma(t, Math.fma(t, 6.0f, -15.0f), 10.0f);
    }

    private final short[] permutations;
    private final float[] gradients;
    private float u, v;
    /**
     * For each corner of the cells of the column and each gradient: where it starts and how steeply it rises.
     */
    private final double[] bases = new double[64], slopes = new double[64];
    private int aa, ab, cell;
    private double columnX = Double.NaN, columnY = Double.NaN;
    private float cellMax;
    /**
     * Along the current cell the noise blends, with the fade of the height in the cell, between a line through the
     * bottom gradients and one through the top gradients of its corners, each weighed by the fades across the column.
     */
    private double bottom0, bottomSlope, top0, topSlope;

    /**
     * The scale which {@link PerlinNoise#getPerlinNoise(double, double, double)} applies to the raw noise.
     */
    private static final double SCALE_3D = 0.4824607142760952;

    /**
     * Far more than the rounding errors, which are of the order of 1e-6, and far less than the steps between ore
     * thresholds.
     */
    private static final double MARGIN = 1e-4;

    private static final Field FAST_PERLIN, PERMUTATIONS, GRADIENTS;

    static {
        Field fastPerlin = null, permutations = null, gradients = null;
        try {
            fastPerlin = PerlinNoise.class.getDeclaredField("fastPerlin");
            fastPerlin.setAccessible(true);
            permutations = fastPerlin.getType().getDeclaredField("permPair");
            permutations.setAccessible(true);
            gradients = fastPerlin.getType().getDeclaredField("LUT2");
            gradients.setAccessible(true);
        } catch (NoSuchFieldException | RuntimeException e) {
            LoggerFactory.getLogger(PerlinColumnBound.class).warn("Perlin noise tables not found; ores are exported without bounds", e);
            fastPerlin = null;
        }
        FAST_PERLIN = fastPerlin;
        PERMUTATIONS = permutations;
        GRADIENTS = gradients;
    }

    private static final Logger logger = LoggerFactory.getLogger(PerlinColumnBound.class);
}
