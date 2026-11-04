package org.pepsoft.worldpainter.view3d;

import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.layers.*;
import org.pepsoft.worldpainter.layers.bo2.TreePack;
import org.pepsoft.worldpainter.objects.WPObject;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static org.pepsoft.worldpainter.Constants.TILE_SIZE_BITS;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE_MASK;

/**
 * The trees painted on the land, for the 3D view: where they stand and roughly what they look like (a conifer, a round
 * crown, a flat crown, a palm, a bush or a bare trunk, with its size and colours). The trees Minecraft gets are only
 * decided when the map is exported, so these are placed like them, with the same density, but not one for one.
 */
final class TreeSamples {
    private TreeSamples(int capacity) {
        x = new float[capacity];
        y = new float[capacity];
        base = new float[capacity];
        height = new float[capacity];
        width = new float[capacity];
        shape = new byte[capacity];
        leaves = new int[capacity];
        trunk = new int[capacity];
    }

    /**
     * Find the trees in an area of a dimension.
     */
    static TreeSamples sample(Dimension dimension, Rectangle area) {
        final List<Style> styles = new ArrayList<>();
        for (Layer layer: dimension.getAllLayers(false)) {
            final Style style = Style.of(layer);
            if (style != null) {
                styles.add(style);
            }
        }
        if (styles.isEmpty()) {
            return NONE;
        }
        // One candidate place for a tree every few blocks, fewer on large maps so that there are not too many to draw
        int spacing = 4;
        while (((long) (area.width / spacing)) * (area.height / spacing) > MAX_CANDIDATES) {
            spacing *= 2;
        }
        TreeSamples trees = new TreeSamples(1024);
        int count = 0;
        for (int cy = area.y; cy < area.y + area.height; cy += spacing) {
            for (int cx = area.x; cx < area.x + area.width; cx += spacing) {
                final int x = cx + (int) (random(cx, cy, 1) * spacing), y = cy + (int) (random(cx, cy, 2) * spacing);
                final Tile tile = dimension.getTile(x >> TILE_SIZE_BITS, y >> TILE_SIZE_BITS);
                if (tile == null) {
                    continue;
                }
                final int xInTile = x & TILE_SIZE_MASK, yInTile = y & TILE_SIZE_MASK;
                final float ground = tile.getHeight(xInTile, yInTile);
                if (tile.getWaterLevel(xInTile, yInTile) > ground) {
                    continue;
                }
                for (Style style: styles) {
                    final int value = tile.getLayerValue(style.layer, xInTile, yInTile);
                    if (value <= 0) {
                        continue;
                    }
                    // The chance that a candidate has a tree: the density of the layer, times the share of the area
                    // around a tree which a candidate stands for
                    final float chance = Math.min(1.0f, (value / 15.0f) * (spacing * spacing) / (style.spacing * style.spacing));
                    if (random(x, y, 3) < chance) {
                        if (count == trees.x.length) {
                            trees = trees.grow();
                        }
                        final float size = 0.8f + 0.4f * random(x, y, 4);
                        trees.x[count] = x + 0.5f;
                        trees.y[count] = y + 0.5f;
                        trees.base[count] = (float) Math.floor(ground + 0.5f) + 1;
                        trees.height[count] = style.height * size;
                        trees.width[count] = style.width * (0.85f + 0.3f * random(x, y, 5));
                        trees.shape[count] = style.shape;
                        trees.leaves[count] = style.leaves;
                        trees.trunk[count] = style.trunk;
                        count++;
                        if (count >= MAX_TREES) {
                            trees.count = count;
                            return trees;
                        }
                        break;
                    }
                }
            }
        }
        trees.count = count;
        return trees;
    }

    private TreeSamples grow() {
        final TreeSamples grown = new TreeSamples(x.length * 2);
        System.arraycopy(x, 0, grown.x, 0, x.length);
        System.arraycopy(y, 0, grown.y, 0, y.length);
        System.arraycopy(base, 0, grown.base, 0, base.length);
        System.arraycopy(height, 0, grown.height, 0, height.length);
        System.arraycopy(width, 0, grown.width, 0, width.length);
        System.arraycopy(shape, 0, grown.shape, 0, shape.length);
        System.arraycopy(leaves, 0, grown.leaves, 0, leaves.length);
        System.arraycopy(trunk, 0, grown.trunk, 0, trunk.length);
        return grown;
    }

    /**
     * A repeatable random number from 0 (inclusive) to 1 (exclusive) for a location.
     */
    private static float random(int x, int y, int salt) {
        long h = x * 0x9E3779B97F4A7C15L + y * 0xC2B2AE3D27D4EB4FL + salt * 0x165667B19E3779F9L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 40) / (float) (1L << 24);
    }

    int count;
    final float[] x, y, base, height, width;
    final byte[] shape;
    final int[] leaves, trunk;

    static final byte CONIFER = 0, ROUND = 1, FLAT = 2, PALM = 3, BUSH = 4, BARE = 5;
    static final TreeSamples NONE = new TreeSamples(0);

    private static final int MAX_CANDIDATES = 2_000_000, MAX_TREES = 40_000;

    /**
     * What the trees of a layer look like.
     */
    private static final class Style {
        Style(Layer layer, byte shape, float height, float width, int spacing, int leaves, int trunk) {
            this.layer = layer;
            this.shape = shape;
            this.height = height;
            this.width = width;
            this.spacing = spacing;
            this.leaves = leaves;
            this.trunk = trunk;
        }

        /**
         * The style of the trees of a layer, or {@code null} if it does not plant trees.
         */
        static Style of(Layer layer) {
            if (layer instanceof DeciduousForest) {
                return new Style(layer, ROUND, 7, 5, 5, 0x4c8a2c, OAK);
            } else if (layer instanceof PineForest) {
                return new Style(layer, CONIFER, 10, 5, 5, 0x2f5a35, SPRUCE);
            } else if (layer instanceof Jungle) {
                return new Style(layer, ROUND, 13, 7, 6, 0x3d8f2a, OAK);
            } else if (layer instanceof SwampLand) {
                return new Style(layer, ROUND, 7, 7, 7, 0x4f7a35, OAK);
            } else if (layer instanceof Bo2Layer) {
                return ofObjects((Bo2Layer) layer);
            } else {
                return null;
            }
        }

        private static Style ofObjects(Bo2Layer layer) {
            final String description = layer.getDescription();
            final int spacing = Math.max(4, Math.max(layer.getGridX(), layer.getGridY()));
            if ((description != null) && description.endsWith(" (WorldPainter tree pack)")) {
                for (TreePack.Kind kind: TreePack.Kind.values()) {
                    if (description.equals(kind.getLayerName() + " (WorldPainter tree pack)")) {
                        return ofKind(layer, kind, spacing);
                    }
                }
            }
            // Custom objects: trees if they have leaves, drawn as a round crown of their size
            final List<WPObject> objects = layer.getObjectProvider().getAllObjects();
            if (objects.isEmpty()) {
                return null;
            }
            final WPObject object = objects.get(0);
            final javax.vecmath.Point3i size = object.getDimensions();
            if ((((long) size.x) * size.y * size.z > 200_000) || (! hasLeaves(object))) {
                return null;
            }
            final Object paint = layer.getPaint();
            return new Style(layer, ROUND, size.z, Math.max(size.x, size.y) * 0.8f, spacing, (paint instanceof Color) ? (((Color) paint).getRGB() & 0xffffff) : 0x4c8a2c, OAK);
        }

        private static Style ofKind(Layer layer, TreePack.Kind kind, int spacing) {
            final int leaves = kind.getColour();
            switch (kind) {
                case TALL_SPRUCE:
                    return new Style(layer, CONIFER, 28, 7, spacing, 0x2f5a35, SPRUCE);
                case FIR:
                    return new Style(layer, CONIFER, 16, 8, spacing, 0x2b5a3a, SPRUCE);
                case REDWOOD:
                    return new Style(layer, CONIFER, 36, 9, spacing, 0x2f5a35, 0x6b3a2a);
                case RAINFOREST:
                    return new Style(layer, FLAT, 24, 15, spacing, 0x3f7f2a, OAK);
                case BIG_OAK:
                    return new Style(layer, ROUND, 13, 11, spacing, 0x3a6b2a, OAK);
                case BIRCH:
                    return new Style(layer, ROUND, 12, 5, spacing, 0x6e9b45, 0xd8d4c8);
                case CHERRY:
                    return new Style(layer, ROUND, 10, 10, spacing, leaves, 0x5a3530);
                case DEAD:
                    return new Style(layer, BARE, 9, 3, spacing, 0x6b5a48, OAK);
                case BUSH:
                case AZALEA:
                    return new Style(layer, BUSH, 3, 4, spacing, leaves, OAK);
                case PALM:
                    return new Style(layer, PALM, 10, 9, spacing, 0x5c9a3a, 0x8a7a5a);
                case ACACIA:
                    return new Style(layer, FLAT, 10, 9, spacing, 0x8a9a3a, 0x7a6a5a);
                case WILLOW:
                    return new Style(layer, ROUND, 11, 11, spacing, 0x5d8a3a, OAK);
                case PALE_OAK:
                    return new Style(layer, ROUND, 12, 12, spacing, 0x9a9a8a, 0x4a3a2a);
                default:
                    return new Style(layer, ROUND, 10, 7, spacing, leaves, OAK);
            }
        }

        private static boolean hasLeaves(WPObject object) {
            final javax.vecmath.Point3i size = object.getDimensions();
            for (int z = size.z - 1; z >= 0; z--) {
                for (int x = 0; x < size.x; x++) {
                    for (int y = 0; y < size.y; y++) {
                        if (object.getMask(x, y, z)) {
                            final Material material = object.getMaterial(x, y, z);
                            if ((material != null) && material.leafBlock) {
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        }

        final Layer layer;
        final byte shape;
        final float height, width;
        final int spacing, leaves, trunk;

        private static final int OAK = 0x6b4f2e, SPRUCE = 0x5a4026;
    }
}
