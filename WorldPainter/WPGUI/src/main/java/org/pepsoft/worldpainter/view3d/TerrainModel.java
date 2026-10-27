package org.pepsoft.worldpainter.view3d;

import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.ProgressReceiver.OperationCancelled;
import org.pepsoft.worldpainter.ColourScheme;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Platform;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.layers.Biome;

import java.awt.*;

import static org.pepsoft.worldpainter.Constants.TILE_SIZE_BITS;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE_MASK;

/**
 * A snapshot of the heights, water, surface colours, biomes and slopes of a dimension, sampled on a regular grid, for
 * the 3D view. Large dimensions are sampled at a coarser resolution so that the model never exceeds about a million
 * samples.
 */
final class TerrainModel {
    TerrainModel(Dimension dimension, ColourScheme colourScheme, ProgressReceiver progressReceiver) throws OperationCancelled {
        final Rectangle extent = dimension.getExtent(); // In tiles
        x0 = extent.x << TILE_SIZE_BITS;
        y0 = extent.y << TILE_SIZE_BITS;
        final int blocksWide = extent.width << TILE_SIZE_BITS, blocksHigh = extent.height << TILE_SIZE_BITS;
        int step = 1;
        while (((long) blocksWide / step) * (blocksHigh / step) > MAX_SAMPLES) {
            step *= 2;
        }
        this.step = step;
        width = blocksWide / step;
        height = blocksHigh / step;
        minHeight = dimension.getMinHeight();
        maxHeight = dimension.getMaxHeight();
        heights = new float[width * height];
        waterLevels = new float[width * height];
        colours = new int[width * height];
        biomes = new int[width * height];
        present = new boolean[width * height];
        final long seed = dimension.getSeed();
        final Platform platform = dimension.getWorld().getPlatform();
        float lowest = Float.MAX_VALUE, highest = -Float.MAX_VALUE;
        for (int sy = 0; sy < height; sy++) {
            if ((progressReceiver != null) && ((sy & 15) == 0)) {
                progressReceiver.setProgress(0.9f * sy / height);
            }
            final int y = y0 + sy * step;
            Tile tile = null;
            int tileX = Integer.MIN_VALUE;
            for (int sx = 0; sx < width; sx++) {
                final int x = x0 + sx * step;
                if ((x >> TILE_SIZE_BITS) != tileX) {
                    tileX = x >> TILE_SIZE_BITS;
                    tile = dimension.getTile(tileX, y >> TILE_SIZE_BITS);
                }
                if (tile == null) {
                    continue;
                }
                final int i = sy * width + sx;
                final int xInTile = x & TILE_SIZE_MASK, yInTile = y & TILE_SIZE_MASK;
                final float h = tile.getHeight(xInTile, yInTile);
                heights[i] = h;
                waterLevels[i] = tile.getWaterLevel(xInTile, yInTile);
                final Terrain terrain = tile.getTerrain(xInTile, yInTile);
                final int intHeight = Math.round(h);
                colours[i] = terrain.getColour(seed, x, y, intHeight, intHeight, platform, colourScheme) & 0xffffff;
                int biome = tile.getLayerValue(Biome.INSTANCE, xInTile, yInTile);
                if (biome == 255) {
                    biome = dimension.getAutoBiome(tile, xInTile, yInTile);
                }
                biomes[i] = biome;
                present[i] = true;
                lowest = Math.min(lowest, h);
                highest = Math.max(highest, h);
            }
        }
        lowestHeight = (lowest != Float.MAX_VALUE) ? lowest : minHeight;
        highestHeight = (highest != -Float.MAX_VALUE) ? highest : maxHeight;

        // Slopes and shading. The slope is measured on a slightly smoothed copy of the heights, so that the small
        // bumps of the surface do not drown the shape of the land in noise
        final float[] smoothed = smooth(heights, present, width, height, Math.max(1, 4 / step));
        slopes = new float[width * height];
        normalShades = new float[width * height];
        for (int sy = 0; sy < height; sy++) {
            for (int sx = 0; sx < width; sx++) {
                final int i = sy * width + sx;
                if (! present[i]) {
                    continue;
                }
                final float sw = heightAt(smoothed, sx - 2, sy, smoothed[i]), se = heightAt(smoothed, sx + 2, sy, smoothed[i]);
                final float sn = heightAt(smoothed, sx, sy - 2, smoothed[i]), ss = heightAt(smoothed, sx, sy + 2, smoothed[i]);
                final float sdx = (se - sw) / (4 * step), sdy = (ss - sn) / (4 * step);
                slopes[i] = (float) Math.toDegrees(Math.atan(Math.sqrt(sdx * sdx + sdy * sdy)));
                // Lambertian shading with the light from the north west, slightly above the horizon. This uses the
                // actual heights, to keep the detail
                final float hw = heightAt(heights, sx - 2, sy, heights[i]), he = heightAt(heights, sx + 2, sy, heights[i]);
                final float hn = heightAt(heights, sx, sy - 2, heights[i]), hs = heightAt(heights, sx, sy + 2, heights[i]);
                final float dx = (he - hw) / (4 * step), dy = (hs - hn) / (4 * step);
                final double nx = -dx, ny = -dy, nz = 1;
                final double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
                final double light = ((nx * -0.5) + (ny * -0.5) + (nz * 0.7071)) / length;
                normalShades[i] = (float) Math.max(0.25, Math.min(1.15, 0.35 + light * 0.85));
            }
        }
        if (progressReceiver != null) {
            progressReceiver.setProgress(1.0f);
        }
    }

    boolean isPresent(int sx, int sy) {
        return (sx >= 0) && (sy >= 0) && (sx < width) && (sy < height) && present[sy * width + sx];
    }

    private float heightAt(float[] values, int sx, int sy, float fallback) {
        return isPresent(sx, sy) ? values[sy * width + sx] : fallback;
    }

    /**
     * Box blur a height field (in two separable passes), ignoring samples that are not present.
     */
    private static float[] smooth(float[] values, boolean[] present, int width, int height, int radius) {
        final float[] horizontal = new float[values.length], result = new float[values.length];
        for (int sy = 0; sy < height; sy++) {
            for (int sx = 0; sx < width; sx++) {
                float sum = 0;
                int count = 0;
                for (int x = Math.max(0, sx - radius); x <= Math.min(width - 1, sx + radius); x++) {
                    final int i = sy * width + x;
                    if (present[i]) {
                        sum += values[i];
                        count++;
                    }
                }
                horizontal[sy * width + sx] = (count > 0) ? sum / count : values[sy * width + sx];
            }
        }
        for (int sy = 0; sy < height; sy++) {
            for (int sx = 0; sx < width; sx++) {
                float sum = 0;
                int count = 0;
                for (int y = Math.max(0, sy - radius); y <= Math.min(height - 1, sy + radius); y++) {
                    final int i = y * width + sx;
                    if (present[i]) {
                        sum += horizontal[i];
                        count++;
                    }
                }
                result[sy * width + sx] = (count > 0) ? sum / count : horizontal[sy * width + sx];
            }
        }
        return result;
    }

    final int x0, y0, step, width, height, minHeight, maxHeight;
    final float lowestHeight, highestHeight;
    final float[] heights, waterLevels, slopes, normalShades;
    final int[] colours, biomes;
    final boolean[] present;

    private static final long MAX_SAMPLES = 1_048_576;
}
