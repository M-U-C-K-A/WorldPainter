package org.pepsoft.worldpainter.nodes;

/**
 * The rules by which {@link Painter painters} are combined, shared by the previews and the actual application so that
 * they agree exactly. Terrain types and biomes go to the painter with the strongest mask, among those stronger than one
 * half, with some noise so that the borders between competing painters are mottled instead of straight; vegetation is
 * placed at random with a probability equal to its strength.
 */
public final class SurfaceRules {
    private SurfaceRules() {
        // Prevent instantiation
    }

    /**
     * Determine which of a set of competing painters wins at a block.
     *
     * @param strengths The strengths of the painters, per painter, as returned by {@link Painter#evaluate}.
     * @param index     The index of the block in the strength arrays.
     * @param x         The X coordinate of the block in the world.
     * @param y         The Y coordinate of the block in the world.
     * @param salts     A different number per painter, so that their noise differs.
     * @return The index of the winning painter, or -1 if none is strong enough.
     */
    public static int winner(float[][] strengths, int index, int x, int y, int[] salts) {
        int winner = -1;
        float best = -Float.MAX_VALUE;
        for (int p = 0; p < strengths.length; p++) {
            final float strength = strengths[p][index];
            if (strength <= THRESHOLD) {
                continue;
            }
            // The noise only decides between competing painters, so that a painter on its own covers its area cleanly
            final float score = strength + (random(x, y, salts[p]) - 0.5f) * MOTTLING;
            if (score > best) {
                best = score;
                winner = p;
            }
        }
        return winner;
    }

    /**
     * Determine whether a plant (or snow) should be placed at a block.
     *
     * @param strength The strength of the painter at the block (zero to one).
     */
    public static boolean place(float strength, int x, int y, int salt) {
        return strength > random(x, y, salt ^ 0x5bd1e995);
    }

    /**
     * The intensity (0-15) of a forest layer for a strength.
     */
    public static int forestLevel(float strength) {
        return Math.max(0, Math.min(15, Math.round(strength * 15)));
    }

    /**
     * A deterministic pseudo random number between zero (inclusive) and one (exclusive) for a block.
     */
    public static float random(int x, int y, int salt) {
        long h = (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x165667B19E3779F9L);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 40) / (float) (1L << 24);
    }

    /**
     * The strength a painter needs to win.
     */
    public static final float THRESHOLD = 0.5f;
    private static final float MOTTLING = 0.3f;
}
