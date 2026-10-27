package org.pepsoft.worldpainter.procedural;

import java.util.Random;

/**
 * Fast, seedable two dimensional simplex noise (after Stefan Gustavson's public domain implementation), plus Worley
 * (cellular) noise. Instances are immutable and thread safe.
 */
public final class SimplexNoise {
    public SimplexNoise(long seed) {
        final Random random = new Random(seed);
        final short[] p = new short[256];
        for (short i = 0; i < 256; i++) {
            p[i] = i;
        }
        for (int i = 255; i > 0; i--) {
            final int j = random.nextInt(i + 1);
            final short tmp = p[i];
            p[i] = p[j];
            p[j] = tmp;
        }
        for (int i = 0; i < 512; i++) {
            perm[i] = p[i & 255];
            permMod12[i] = (short) (perm[i] % 12);
        }
    }

    /**
     * Simplex noise.
     *
     * @return A value in the range -1 to 1.
     */
    public double noise(double xin, double yin) {
        double n0, n1, n2;
        final double s = (xin + yin) * F2;
        final int i = fastFloor(xin + s);
        final int j = fastFloor(yin + s);
        final double t = (i + j) * G2;
        final double x0 = xin - (i - t);
        final double y0 = yin - (j - t);
        final int i1, j1;
        if (x0 > y0) {
            i1 = 1;
            j1 = 0;
        } else {
            i1 = 0;
            j1 = 1;
        }
        final double x1 = x0 - i1 + G2;
        final double y1 = y0 - j1 + G2;
        final double x2 = x0 - 1.0 + 2.0 * G2;
        final double y2 = y0 - 1.0 + 2.0 * G2;
        final int ii = i & 255;
        final int jj = j & 255;
        double t0 = 0.5 - x0 * x0 - y0 * y0;
        if (t0 < 0) {
            n0 = 0.0;
        } else {
            final int gi0 = permMod12[ii + perm[jj]];
            t0 *= t0;
            n0 = t0 * t0 * (GRAD_X[gi0] * x0 + GRAD_Y[gi0] * y0);
        }
        double t1 = 0.5 - x1 * x1 - y1 * y1;
        if (t1 < 0) {
            n1 = 0.0;
        } else {
            final int gi1 = permMod12[ii + i1 + perm[jj + j1]];
            t1 *= t1;
            n1 = t1 * t1 * (GRAD_X[gi1] * x1 + GRAD_Y[gi1] * y1);
        }
        double t2 = 0.5 - x2 * x2 - y2 * y2;
        if (t2 < 0) {
            n2 = 0.0;
        } else {
            final int gi2 = permMod12[ii + 1 + perm[jj + 1]];
            t2 *= t2;
            n2 = t2 * t2 * (GRAD_X[gi2] * x2 + GRAD_Y[gi2] * y2);
        }
        // Scale the result to cover the range -1 to 1
        return 70.0 * (n0 + n1 + n2);
    }

    /**
     * Worley (cellular) noise, with one feature point per unit cell.
     *
     * @param result An array of length two in which the distances to the closest and second closest feature points
     *               will be stored.
     */
    public void cellular(double x, double y, double[] result) {
        final int cellX = fastFloor(x), cellY = fastFloor(y);
        double f1 = Double.MAX_VALUE, f2 = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                final int cx = cellX + dx, cy = cellY + dy;
                final int hash = perm[(perm[cx & 255] + cy) & 255];
                final double px = cx + (perm[hash] / 255.0), py = cy + (perm[(hash + 101) & 255] / 255.0);
                final double distanceSquared = (px - x) * (px - x) + (py - y) * (py - y);
                if (distanceSquared < f1) {
                    f2 = f1;
                    f1 = distanceSquared;
                } else if (distanceSquared < f2) {
                    f2 = distanceSquared;
                }
            }
        }
        result[0] = Math.sqrt(f1);
        result[1] = Math.sqrt(f2);
    }

    private static int fastFloor(double x) {
        final int xi = (int) x;
        return (x < xi) ? xi - 1 : xi;
    }

    private final short[] perm = new short[512];
    private final short[] permMod12 = new short[512];

    private static final double F2 = 0.5 * (Math.sqrt(3.0) - 1.0);
    private static final double G2 = (3.0 - Math.sqrt(3.0)) / 6.0;
    private static final double[] GRAD_X = {1, -1, 1, -1, 1, -1, 1, -1, 0, 0, 0, 0};
    private static final double[] GRAD_Y = {1, 1, -1, -1, 0, 0, 0, 0, 1, -1, 1, -1};
}
