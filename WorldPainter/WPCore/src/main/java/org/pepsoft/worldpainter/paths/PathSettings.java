package org.pepsoft.worldpainter.paths;

import org.pepsoft.worldpainter.Terrain;

/**
 * The settings for laying out a path with the {@link PathGenerator}.
 */
public class PathSettings implements Cloneable {
    /**
     * The width of the path in blocks.
     */
    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = Math.max(1, width);
    }

    /**
     * How much the path may bend to follow the land: 0 is a straight line, 100 follows the terrain as well as possible.
     */
    public int getCurvature() {
        return curvature;
    }

    public void setCurvature(int curvature) {
        this.curvature = Math.max(0, Math.min(100, curvature));
    }

    /**
     * Whether the path may cross valleys and water on bridges.
     */
    public boolean isBridges() {
        return bridges;
    }

    public void setBridges(boolean bridges) {
        this.bridges = bridges;
    }

    /**
     * Whether the path may go through hills in tunnels.
     */
    public boolean isTunnels() {
        return tunnels;
    }

    public void setTunnels(boolean tunnels) {
        this.tunnels = tunnels;
    }

    /**
     * The steepest slope of the path, in blocks of height per block of length.
     */
    public double getMaxGrade() {
        return maxGrade;
    }

    public void setMaxGrade(double maxGrade) {
        this.maxGrade = Math.max(0.02, maxGrade);
    }

    /**
     * The longest bridge or tunnel, in blocks.
     */
    public int getMaxSpan() {
        return maxSpan;
    }

    public void setMaxSpan(int maxSpan) {
        this.maxSpan = Math.max(8, maxSpan);
    }

    /**
     * The height of the inside of tunnels, in blocks.
     */
    public int getTunnelHeight() {
        return tunnelHeight;
    }

    public void setTunnelHeight(int tunnelHeight) {
        this.tunnelHeight = Math.max(2, tunnelHeight);
    }

    /**
     * The material of the surface of the path.
     */
    public Terrain getMaterial() {
        return material;
    }

    public void setMaterial(Terrain material) {
        this.material = material;
    }

    /**
     * Whether the path has borders (edges of a different material, and parapets on bridges).
     */
    public boolean isBorders() {
        return borders;
    }

    public void setBorders(boolean borders) {
        this.borders = borders;
    }

    public Terrain getBorderMaterial() {
        return borderMaterial;
    }

    public void setBorderMaterial(Terrain borderMaterial) {
        this.borderMaterial = borderMaterial;
    }

    /**
     * Whether the width varies with the land: narrower on steep hillsides, on bridges and in tunnels, wider on flat open
     * ground, around the chosen width.
     */
    public boolean isDynamicWidth() {
        return dynamicWidth;
    }

    public void setDynamicWidth(boolean dynamicWidth) {
        this.dynamicWidth = dynamicWidth;
    }

    /**
     * Whether trees, plants and other objects on the path are removed.
     */
    public boolean isClearVegetation() {
        return clearVegetation;
    }

    public void setClearVegetation(boolean clearVegetation) {
        this.clearVegetation = clearVegetation;
    }

    /**
     * What lines the sides of the path on the ground.
     */
    public Sides getSides() {
        return sides;
    }

    public void setSides(Sides sides) {
        this.sides = (sides != null) ? sides : Sides.NONE;
    }

    /**
     * Whether the surface is a mix of blocks which suits the land the path crosses (sandstone and coarse dirt through
     * sand, andesite and cobblestone through rock, ...) rather than the chosen materials. In beta.
     */
    public boolean isAdaptiveSurface() {
        return adaptiveSurface;
    }

    public void setAdaptiveSurface(boolean adaptiveSurface) {
        this.adaptiveSurface = adaptiveSurface;
    }

    @Override
    public PathSettings clone() {
        try {
            return (PathSettings) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }

    private int width = Size.MEDIUM.width, curvature = 60, maxSpan = 96, tunnelHeight = 5;
    private boolean bridges = true, tunnels, borders = true, dynamicWidth = true, clearVegetation = true, adaptiveSurface;
    private Sides sides = Sides.NONE;
    private double maxGrade = 0.25;
    private Terrain material = Terrain.GRAVEL, borderMaterial = Terrain.STONE;

    /**
     * The widths to choose from.
     */
    public enum Size {
        NARROW("Narrow", 3), MEDIUM("Medium", 5), WIDE("Wide", 7);

        Size(String displayName, int width) {
            this.displayName = displayName;
            this.width = width;
        }

        public int getWidth() {
            return width;
        }

        /**
         * The size closest to a width.
         */
        public static Size of(int width) {
            Size best = MEDIUM;
            for (Size size: values()) {
                if (Math.abs(size.width - width) < Math.abs(best.width - width)) {
                    best = size;
                }
            }
            return best;
        }

        @Override
        public String toString() {
            return displayName + " (" + width + " blocks)";
        }

        private final String displayName;
        private final int width;
    }

    /**
     * What can line the sides of a path.
     */
    public enum Sides {
        NONE("Nothing"), FENCE("Wooden fence"), FENCE_AND_HEDGE("Fence and hedges"), HEDGE("Hedge"), STONE_WALL("Low stone wall"),
        LANTERN_POSTS("Posts with lanterns"), FLOWERS("Flowers");

        Sides(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }

        private final String displayName;
    }
}
