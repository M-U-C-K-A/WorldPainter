package org.pepsoft.worldpainter.view3d;

import org.pepsoft.worldpainter.ColourScheme;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Platform;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.layers.Layer;
import org.pepsoft.worldpainter.layers.tunnel.TunnelLayer;
import org.pepsoft.worldpainter.layers.tunnel.TunnelLayerHelper;

import java.util.ArrayList;
import java.util.List;

import static org.pepsoft.worldpainter.Constants.TILE_SIZE_BITS;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE_MASK;
import static org.pepsoft.worldpainter.Dimension.Role.FLOATING_FLOOR;

/**
 * The floating islands of a dimension (its floating dimension layers), for the 3D view: for each sample of the model,
 * the top and the bottom of the island above it, if any, worked out as when exporting: the top from the floor
 * dimension of the layer, the underside from the thickness and the taper of the layer.
 */
final class IslandSamples {
    private IslandSamples(int width, int height) {
        tops = new float[width * height];
        bottoms = new float[width * height];
        colours = new int[width * height];
        java.util.Arrays.fill(tops, Float.NaN);
        java.util.Arrays.fill(bottoms, Float.NaN);
    }

    /**
     * Sample the islands of a dimension on the grid of a model.
     *
     * @return The islands, or {@link #NONE} if there are none.
     */
    static IslandSamples sample(Dimension dimension, ColourScheme colourScheme, int x0, int y0, int step, int width, int height) {
        final List<TunnelLayer> layers = floatingLayers(dimension);
        if (layers.isEmpty()) {
            return NONE;
        }
        final IslandSamples islands = new IslandSamples(width, height);
        final int minZ = dimension.getMinHeight(), maxZ = dimension.getMaxHeight() - 1;
        final Platform platform = dimension.getWorld().getPlatform();
        for (TunnelLayer layer: layers) {
            final Dimension floor = getFloorDimension(dimension, layer);
            final TunnelLayerHelper helper = layer.getHelper(dimension);
            for (int sy = 0; sy < height; sy++) {
                final int y = y0 + sy * step;
                for (int sx = 0; sx < width; sx++) {
                    final int x = x0 + sx * step;
                    final int i = sy * width + sx;
                    if ((! Float.isNaN(islands.tops[i])) || (! dimension.getBitLayerValueAt(layer, x, y))) {
                        continue;
                    }
                    final int top = helper.calculateFloorLevel(x, y, dimension.getIntHeightAt(x, y), minZ, maxZ);
                    final int bottom = helper.calculateBottomLevel(x, y, minZ, maxZ, top, helper.getDistanceToWall(x, y));
                    islands.tops[i] = top + 1; // The top of the topmost block
                    islands.bottoms[i] = Math.min(bottom, top);
                    final Tile floorTile = (floor != null) ? floor.getTile(x >> TILE_SIZE_BITS, y >> TILE_SIZE_BITS) : null;
                    islands.colours[i] = (floorTile != null)
                            ? floorTile.getTerrain(x & TILE_SIZE_MASK, y & TILE_SIZE_MASK).getColour(floor.getSeed(), x, y, top, top, platform, colourScheme) & 0xffffff
                            : 0x6a9a3a;
                    islands.count++;
                }
            }
        }
        return (islands.count > 0) ? islands : NONE;
    }

    /**
     * The floating dimension layers of a dimension which have a floor dimension.
     */
    static List<TunnelLayer> floatingLayers(Dimension dimension) {
        final List<TunnelLayer> layers = new ArrayList<>();
        if (dimension.getWorld() == null) {
            return layers;
        }
        for (Layer layer: dimension.getAllLayers(false)) {
            if ((layer instanceof TunnelLayer) && (((TunnelLayer) layer).getLayerMode() == TunnelLayer.LayerMode.FLOATING)
                    && (((TunnelLayer) layer).getFloorDimensionId() != null) && (getFloorDimension(dimension, (TunnelLayer) layer) != null)) {
                layers.add((TunnelLayer) layer);
            }
        }
        return layers;
    }

    /**
     * The dimension which holds the tops of the islands of a floating layer, or {@code null} if it has none.
     */
    static Dimension getFloorDimension(Dimension dimension, TunnelLayer layer) {
        final Dimension.Anchor anchor = dimension.getAnchor();
        return dimension.getWorld().getDimension(new Dimension.Anchor(anchor.dim, FLOATING_FLOOR, anchor.invert, layer.getFloorDimensionId()));
    }

    /**
     * Whether there is an island above a sample.
     */
    boolean isIsland(int i) {
        return (this != NONE) && (! Float.isNaN(tops[i]));
    }

    /**
     * The tops (the top of the topmost block) and bottoms (the bottom of the lowest block) of the islands, NaN where
     * there are none.
     */
    final float[] tops, bottoms;
    /**
     * The colours of the surface on top of the islands.
     */
    final int[] colours;
    int count;

    static final IslandSamples NONE = new IslandSamples(0, 0);
}
