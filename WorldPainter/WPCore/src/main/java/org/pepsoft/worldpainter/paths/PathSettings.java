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

    @Override
    public PathSettings clone() {
        try {
            return (PathSettings) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }

    private int width = 4, curvature = 60, maxSpan = 96, tunnelHeight = 5;
    private boolean bridges = true, tunnels, borders = true;
    private double maxGrade = 0.25;
    private Terrain material = Terrain.GRAVEL, borderMaterial = Terrain.STONE;
}
