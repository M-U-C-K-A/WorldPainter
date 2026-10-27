package org.pepsoft.worldpainter.procedural;

import org.pepsoft.worldpainter.Terrain;

import java.io.Serializable;

/**
 * A rule which determines the terrain type of a procedurally generated column from its height and steepness. Rules are
 * evaluated in order and the first one which matches is used.
 */
public class SurfaceRule implements Serializable, Cloneable {
    public SurfaceRule() {
        // Defaults
    }

    public SurfaceRule(Terrain terrain, int minHeight, int maxHeight, float minSlope, float maxSlope) {
        this.terrain = terrain;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
        this.minSlope = minSlope;
        this.maxSlope = maxSlope;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Terrain getTerrain() {
        return terrain;
    }

    public void setTerrain(Terrain terrain) {
        this.terrain = terrain;
    }

    /**
     * The lowest height (inclusive) at which this rule applies.
     */
    public int getMinHeight() {
        return minHeight;
    }

    public void setMinHeight(int minHeight) {
        this.minHeight = minHeight;
    }

    /**
     * The highest height (inclusive) at which this rule applies.
     */
    public int getMaxHeight() {
        return maxHeight;
    }

    public void setMaxHeight(int maxHeight) {
        this.maxHeight = maxHeight;
    }

    /**
     * The minimum steepness in degrees (0-90) at which this rule applies.
     */
    public float getMinSlope() {
        return minSlope;
    }

    public void setMinSlope(float minSlope) {
        this.minSlope = minSlope;
    }

    /**
     * The maximum steepness in degrees (0-90) at which this rule applies.
     */
    public float getMaxSlope() {
        return maxSlope;
    }

    public void setMaxSlope(float maxSlope) {
        this.maxSlope = maxSlope;
    }

    /**
     * How much the height limits vary from place to place, in blocks, to avoid perfectly straight transitions.
     */
    public float getVariation() {
        return variation;
    }

    public void setVariation(float variation) {
        this.variation = variation;
    }

    boolean matches(float height, float slope, float jitter) {
        if (! enabled) {
            return false;
        }
        final float h = height + jitter * variation;
        return (h >= minHeight) && (h <= maxHeight) && (slope >= minSlope) && (slope <= maxSlope);
    }

    @Override
    public SurfaceRule clone() {
        try {
            return (SurfaceRule) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }

    private boolean enabled = true;
    private Terrain terrain = Terrain.GRASS;
    private int minHeight = Integer.MIN_VALUE, maxHeight = Integer.MAX_VALUE;
    private float minSlope = 0, maxSlope = 90, variation = 3;

    private static final long serialVersionUID = 1L;
}
