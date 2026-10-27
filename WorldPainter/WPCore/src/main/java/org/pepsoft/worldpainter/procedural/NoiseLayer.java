package org.pepsoft.worldpainter.procedural;

import java.io.Serializable;

/**
 * One layer of a {@link ProceduralTerrainSettings procedural terrain}. Each layer produces a noise value between zero
 * and one, which is shaped, scaled by the {@link #getAmplitude() amplitude} and combined with the layers before it
 * according to its {@link #getBlendMode() blend mode}.
 */
public class NoiseLayer implements Serializable, Cloneable {
    public NoiseLayer() {
        // Defaults
    }

    public NoiseLayer(String name, Type type, BlendMode blendMode, float scale, float amplitude, int octaves) {
        this.name = name;
        this.type = type;
        this.blendMode = blendMode;
        this.scale = scale;
        this.amplitude = amplitude;
        this.octaves = octaves;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public BlendMode getBlendMode() {
        return blendMode;
    }

    public void setBlendMode(BlendMode blendMode) {
        this.blendMode = blendMode;
    }

    /**
     * The size of the largest features, in blocks.
     */
    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        this.scale = scale;
    }

    /**
     * The height of the layer, in blocks.
     */
    public float getAmplitude() {
        return amplitude;
    }

    public void setAmplitude(float amplitude) {
        this.amplitude = amplitude;
    }

    /**
     * The number of successively finer noise octaves which are added together.
     */
    public int getOctaves() {
        return octaves;
    }

    public void setOctaves(int octaves) {
        this.octaves = octaves;
    }

    /**
     * How strong each finer octave is compared to the previous one (0-1). Higher values produce rougher terrain.
     */
    public float getRoughness() {
        return roughness;
    }

    public void setRoughness(float roughness) {
        this.roughness = roughness;
    }

    /**
     * The exponent with which the noise value is shaped. Values above one flatten the low areas and sharpen the peaks;
     * values below one do the opposite.
     */
    public float getSharpness() {
        return sharpness;
    }

    public void setSharpness(float sharpness) {
        this.sharpness = sharpness;
    }

    /**
     * How far the input coordinates are distorted by another noise field, in blocks. Produces more organic, twisted
     * shapes.
     */
    public float getWarp() {
        return warp;
    }

    public void setWarp(float warp) {
        this.warp = warp;
    }

    /**
     * The fraction of the map (0-1) in which this layer is present. Below one, the layer only appears in large,
     * irregular regions, for instance to create separate mountain ranges.
     */
    public float getCoverage() {
        return coverage;
    }

    public void setCoverage(float coverage) {
        this.coverage = coverage;
    }

    /**
     * The direction of the ridges, in degrees. Only used for the {@link Type#DUNES} type.
     */
    public float getDirection() {
        return direction;
    }

    public void setDirection(float direction) {
        this.direction = direction;
    }

    public int getSeedOffset() {
        return seedOffset;
    }

    public void setSeedOffset(int seedOffset) {
        this.seedOffset = seedOffset;
    }

    @Override
    public NoiseLayer clone() {
        try {
            return (NoiseLayer) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }

    @Override
    public String toString() {
        return (name != null) ? name : type.toString();
    }

    private String name;
    private boolean enabled = true;
    private Type type = Type.FBM;
    private BlendMode blendMode = BlendMode.ADD;
    private float scale = 256, amplitude = 32, roughness = 0.5f, sharpness = 1.0f, warp = 0, coverage = 1.0f, direction = 45;
    private int octaves = 5, seedOffset;

    private static final long serialVersionUID = 1L;

    public enum Type {
        /**
         * Smooth fractal noise: rolling hills, continents.
         */
        FBM("Smooth"),
        /**
         * Ridged multifractal noise: sharp mountain ridges.
         */
        RIDGED("Ridged"),
        /**
         * Billowy noise: rounded, puffy hills.
         */
        BILLOW("Billow"),
        /**
         * Cellular noise: rounded mounds or, when subtracted, basins and craters.
         */
        CELLS("Cells"),
        /**
         * Parallel wavy ridges, like sand dunes.
         */
        DUNES("Dunes");

        Type(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }

        private final String displayName;
    }

    public enum BlendMode {
        /**
         * Add the layer to the terrain so far.
         */
        ADD("Add"),
        /**
         * Subtract the layer from the terrain so far.
         */
        SUBTRACT("Subtract"),
        /**
         * Use the highest of the layer and the terrain so far.
         */
        MAX("Maximum"),
        /**
         * Use the lowest of the layer and the terrain so far.
         */
        MIN("Minimum"),
        /**
         * Multiply the terrain so far with the layer (with a value of one meaning "unchanged"). The amplitude is
         * ignored.
         */
        MULTIPLY("Multiply");

        BlendMode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }

        private final String displayName;
    }
}
