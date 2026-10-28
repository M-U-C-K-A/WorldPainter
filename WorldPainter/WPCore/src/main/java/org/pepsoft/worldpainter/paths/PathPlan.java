package org.pepsoft.worldpainter.paths;

import java.util.List;

/**
 * A path laid out by the {@link PathGenerator}: points one block apart, with for each the height of the land, the
 * water level (NaN where it is dry), the height of the path and whether it is on the ground, on a bridge or in a
 * tunnel.
 */
public final class PathPlan {
    PathPlan(List<double[]> points, float[] terrain, float[] water, float[] profile, byte[] types) {
        this.points = points;
        this.terrain = terrain;
        this.water = water;
        this.profile = profile;
        this.types = types;
    }

    public List<double[]> getPoints() {
        return points;
    }

    public float[] getTerrain() {
        return terrain;
    }

    public float[] getWater() {
        return water;
    }

    public float[] getProfile() {
        return profile;
    }

    public byte[] getTypes() {
        return types;
    }

    /**
     * The number of points (blocks of length) of a type.
     */
    public int count(byte type) {
        int count = 0;
        for (byte t: types) {
            if (t == type) {
                count++;
            }
        }
        return count;
    }

    /**
     * The volume of land, in blocks, that the path cuts away and fills in, per block of width.
     */
    public float[] getCutAndFill() {
        float cut = 0, fill = 0;
        for (int i = 0; i < profile.length; i++) {
            if (types[i] == GROUND) {
                final float difference = profile[i] - terrain[i];
                if (difference > 0) {
                    fill += difference;
                } else {
                    cut -= difference;
                }
            }
        }
        return new float[] {cut, fill};
    }

    private final List<double[]> points;
    private final float[] terrain, water, profile;
    private final byte[] types;

    public static final byte GROUND = 0, BRIDGE = 1, TUNNEL = 2;
}
