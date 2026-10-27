package org.pepsoft.worldpainter.nodes;

import java.util.Objects;

/**
 * A regular grid of sample locations in world coordinates (blocks). Sample {@code (i, j)} is located at
 * ({@code x0 + i * step}, {@code y0 + j * step}) and is stored at index {@code i + j * width}. Immutable.
 */
public final class Region {
    public Region(double x0, double y0, double step, int width, int height) {
        if ((width <= 0) || (height <= 0) || (! (step > 0))) {
            throw new IllegalArgumentException("Invalid region " + width + "x" + height + " with step " + step);
        }
        this.x0 = x0;
        this.y0 = y0;
        this.step = step;
        this.width = width;
        this.height = height;
    }

    public double getX0() {
        return x0;
    }

    public double getY0() {
        return y0;
    }

    /**
     * The distance between samples, in blocks.
     */
    public double getStep() {
        return step;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    /**
     * The number of samples.
     */
    public int size() {
        return width * height;
    }

    /**
     * The world X coordinate of column {@code i}.
     */
    public double x(int i) {
        return x0 + i * step;
    }

    /**
     * The world Y coordinate of row {@code j}.
     */
    public double y(int j) {
        return y0 + j * step;
    }

    /**
     * Get a grid with spacing {@code cellSize} which is aligned to multiples of the cell size in world coordinates, and
     * which covers this region plus {@code margin} cells on all sides, plus one more cell at the far sides so that
     * this region can be {@link #resample(float[], Region) resampled} from it. Because the grid is aligned to world
     * coordinates, its samples do not depend on the location of this region, which keeps neighbourhood operations
     * seamless across tiles.
     */
    public Region aligned(double cellSize, int margin) {
        final long ix0 = (long) Math.floor(x0 / cellSize) - margin, iy0 = (long) Math.floor(y0 / cellSize) - margin;
        final long ix1 = (long) Math.floor(x(width - 1) / cellSize) + 1 + margin, iy1 = (long) Math.floor(y(height - 1) / cellSize) + 1 + margin;
        return new Region(ix0 * cellSize, iy0 * cellSize, cellSize, (int) (ix1 - ix0 + 1), (int) (iy1 - iy0 + 1));
    }

    /**
     * Sample values on an {@link #aligned(double, int) aligned grid} at the locations of this region, with bilinear
     * interpolation.
     *
     * @param values The values on the grid.
     * @param grid   The grid, which must have been obtained from {@link #aligned(double, int)} on this region.
     * @return The interpolated values for this region.
     */
    public float[] resample(float[] values, Region grid) {
        final double cellSize = grid.step;
        final long gridX0 = Math.round(grid.x0 / cellSize), gridY0 = Math.round(grid.y0 / cellSize);
        final float[] result = new float[size()];
        final int[] columns = new int[width];
        final float[] fractions = new float[width];
        for (int i = 0; i < width; i++) {
            // Computed from the world coordinate only, so that the result does not depend on this region's origin
            final double gx = x(i) / cellSize;
            final double floor = Math.floor(gx);
            columns[i] = (int) ((long) floor - gridX0);
            fractions[i] = (float) (gx - floor);
        }
        final int gridWidth = grid.width;
        for (int j = 0; j < height; j++) {
            final double gy = y(j) / cellSize;
            final double floor = Math.floor(gy);
            final int row = (int) ((long) floor - gridY0);
            final float fy = (float) (gy - floor);
            final int rowOffset = row * gridWidth;
            for (int i = 0; i < width; i++) {
                final int index = rowOffset + columns[i];
                final float fx = fractions[i];
                final float top = values[index] + (values[index + 1] - values[index]) * fx;
                final float bottom = values[index + gridWidth] + (values[index + gridWidth + 1] - values[index + gridWidth]) * fx;
                result[i + j * width] = top + (bottom - top) * fy;
            }
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (! (o instanceof Region)) {
            return false;
        }
        final Region region = (Region) o;
        return (Double.compare(x0, region.x0) == 0) && (Double.compare(y0, region.y0) == 0) && (Double.compare(step, region.step) == 0)
                && (width == region.width) && (height == region.height);
    }

    @Override
    public int hashCode() {
        return Objects.hash(x0, y0, step, width, height);
    }

    @Override
    public String toString() {
        return "Region[" + x0 + "," + y0 + " " + width + "x" + height + " step " + step + "]";
    }

    private final double x0, y0, step;
    private final int width, height;
}
