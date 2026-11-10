package org.pepsoft.worldpainter.composition;

import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.layers.*;
import org.pepsoft.worldpainter.layers.Void;
import org.pepsoft.worldpainter.layers.exporters.CavernsExporter.CavernsSettings;
import org.pepsoft.worldpainter.layers.exporters.CavesExporter.CavesSettings;
import org.pepsoft.worldpainter.layers.exporters.ChasmsExporter.ChasmsSettings;
import org.pepsoft.worldpainter.layers.exporters.ExporterSettings;
import org.pepsoft.worldpainter.layers.exporters.ResourcesExporter.ResourcesExporterSettings;
import org.pepsoft.worldpainter.layers.pockets.UndergroundPocketsLayer;
import org.pepsoft.worldpainter.layers.tunnel.TunnelLayer;
import org.pepsoft.worldpainter.selection.SelectionBlock;
import org.pepsoft.worldpainter.selection.SelectionChunk;

import java.awt.*;
import java.util.List;
import java.util.*;

import static org.pepsoft.worldpainter.Constants.TILE_SIZE_BITS;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE_MASK;

/**
 * What an area of a dimension is made of: the terrain and height of its surface, its slopes, water, biomes, what is
 * painted on it and under it, and the rock of a column through it from the surface down to the bottom of the world, as
 * it will be exported. Worked out from a sample of its columns, so that it is quick for large areas too.
 */
public final class AreaComposition {
    private AreaComposition(Rectangle area) {
        this.area = area;
    }

    /**
     * Work out what an area is made of.
     *
     * @param dimension     The dimension.
     * @param area          The area, in blocks.
     * @param selectionOnly Whether to count only the selected columns.
     * @param colourScheme  The colours of the blocks.
     * @param column        The column through which to show the rock, or {@code null} for the middle of the area.
     */
    public static AreaComposition analyse(Dimension dimension, ColourScheme colourScheme, Rectangle area, boolean selectionOnly, Point column) {
        final AreaComposition result = new AreaComposition(area);
        final int step = Math.max(1, (int) Math.ceil(Math.sqrt((double) area.width * area.height / MAX_SAMPLES)));
        result.step = step;
        float heightSum = 0, depthSum = 0;
        final float[] heights = new float[MAX_SAMPLES + 1000];
        int heightCount = 0;
        for (int y = area.y + step / 2; y < area.y + area.height; y += step) {
            for (int x = area.x + step / 2; x < area.x + area.width; x += step) {
                final Tile tile = dimension.getTile(x >> TILE_SIZE_BITS, y >> TILE_SIZE_BITS);
                if (tile == null) {
                    continue;
                }
                final int xInTile = x & TILE_SIZE_MASK, yInTile = y & TILE_SIZE_MASK;
                if (tile.getBitLayerValue(NotPresent.INSTANCE, xInTile, yInTile) || tile.getBitLayerValue(NotPresentBlock.INSTANCE, xInTile, yInTile)) {
                    continue;
                }
                if (selectionOnly && (! (tile.getBitLayerValue(SelectionChunk.INSTANCE, xInTile, yInTile) || tile.getBitLayerValue(SelectionBlock.INSTANCE, xInTile, yInTile)))) {
                    continue;
                }
                result.samples++;
                final float height = tile.getHeight(xInTile, yInTile);
                final int intHeight = Math.round(height);
                heightSum += height;
                result.lowest = Math.min(result.lowest, height);
                if (heightCount < heights.length) {
                    heights[heightCount++] = height;
                }
                result.highest = Math.max(result.highest, height);
                result.terrains.merge(tile.getTerrain(xInTile, yInTile), 1, Integer::sum);
                final float slope = (float) Math.toDegrees(Math.atan(dimension.getSlope(x, y)));
                result.slopes[(slope < 10) ? 0 : (slope < 30) ? 1 : (slope < 50) ? 2 : 3]++;
                result.slopeSum += slope;
                result.steepest = Math.max(result.steepest, slope);
                final int waterLevel = tile.getWaterLevel(xInTile, yInTile);
                if (waterLevel > intHeight) {
                    if (tile.getBitLayerValue(FloodWithLava.INSTANCE, xInTile, yInTile)) {
                        result.lava++;
                    } else {
                        result.water++;
                        depthSum += waterLevel - height;
                        result.deepest = Math.max(result.deepest, waterLevel - height);
                    }
                }
                int biome = tile.getLayerValue(Biome.INSTANCE, xInTile, yInTile);
                if (biome == 255) {
                    biome = dimension.getAutoBiome(tile, xInTile, yInTile);
                } else {
                    result.paintedBiomes++;
                }
                if (biome >= 0) {
                    result.biomes.merge(biome, 1, Integer::sum);
                }
                for (Layer layer: tile.getLayers()) {
                    if (IGNORED.contains(layer.getClass()) || (layer instanceof Biome)) {
                        continue;
                    }
                    final int value;
                    switch (layer.getDataSize()) {
                        case BIT:
                        case BIT_PER_CHUNK:
                            value = tile.getBitLayerValue(layer, xInTile, yInTile) ? 15 : 0;
                            break;
                        case NIBBLE:
                            value = tile.getLayerValue(layer, xInTile, yInTile);
                            break;
                        case BYTE:
                            value = (tile.getLayerValue(layer, xInTile, yInTile) + 8) / 17;
                            break;
                        default:
                            value = 0;
                    }
                    if (value > 0) {
                        final int[] counts = result.layers.computeIfAbsent(layer, l -> new int[2]);
                        counts[0]++;
                        counts[1] += value;
                    }
                }
            }
        }
        if (result.samples > 0) {
            result.averageHeight = heightSum / result.samples;
            // How much of the land is at each height
            final float range = Math.max(1, result.highest - result.lowest);
            for (int i = 0; i < heightCount; i++) {
                result.heightBins[Math.min(HEIGHT_BINS - 1, (int) ((heights[i] - result.lowest) / range * HEIGHT_BINS))]++;
            }
        }
        if (result.water > 0) {
            result.averageDepth = depthSum / result.water;
        }

        // What applies everywhere, from the settings of the dimension
        for (Layer layer: new Layer[] {Caves.INSTANCE, Caverns.INSTANCE, Chasms.INSTANCE, Resources.INSTANCE}) {
            final ExporterSettings settings = dimension.getLayerSettings(layer);
            if ((settings != null) && settings.isApplyEverywhere()) {
                result.everywhere.add(layer);
            }
        }
        for (Layer layer: new Layer[] {Caves.INSTANCE, Caverns.INSTANCE, Chasms.INSTANCE}) {
            final ExporterSettings settings = dimension.getLayerSettings(layer);
            if (settings instanceof CavesSettings) {
                result.levels.put(layer, new int[] {((CavesSettings) settings).getMinimumLevel(), ((CavesSettings) settings).getMaximumLevel()});
            } else if (settings instanceof CavernsSettings) {
                result.levels.put(layer, new int[] {((CavernsSettings) settings).getMinimumLevel(), ((CavernsSettings) settings).getMaximumLevel()});
            } else if (settings instanceof ChasmsSettings) {
                result.levels.put(layer, new int[] {((ChasmsSettings) settings).getMinimumLevel(), ((ChasmsSettings) settings).getMaximumLevel()});
            }
        }
        final ExporterSettings resourcesSettings = dimension.getLayerSettings(Resources.INSTANCE);
        if (resourcesSettings instanceof ResourcesExporterSettings) {
            final ResourcesExporterSettings settings = (ResourcesExporterSettings) resourcesSettings;
            for (Material material: settings.getMaterials()) {
                if (settings.getChance(material) > 0) {
                    result.ores.add(new Ore(material, settings.getMinLevel(material), settings.getMaxLevel(material), settings.getChance(material)));
                }
            }
            // The ores first, then the other blocks (dirt, gravel…), each by how common they are
            result.ores.sort(Comparator.comparing((Ore ore) -> ! ore.material.simpleName.contains("ore")).thenComparingInt(ore -> -ore.chance));
        }

        // A column through the area, from the top of the water or the land down to the bottom of the world
        final Point centre = (column != null) ? column : new Point(area.x + area.width / 2, area.y + area.height / 2);
        final int height = dimension.getIntHeightAt(centre.x, centre.y);
        if (height != Integer.MIN_VALUE) {
            result.column = centre;
            result.columnHeight = height;
            result.rock = rockColumn(dimension, colourScheme, centre.x, centre.y, height);
        }
        return result;
    }

    /**
     * The blocks of a column as the exporter puts them, before caves, ores and other layers, as bands of the same kind
     * of block from the top down.
     */
    private static List<Band> rockColumn(Dimension dimension, ColourScheme colourScheme, int x, int y, int height) {
        final Platform platform = dimension.getWorld().getPlatform();
        final long seed = dimension.getSeed();
        final Terrain terrain = dimension.getTerrainAt(x, y), subsurface = dimension.getSubsurfaceMaterial();
        final int waterLevel = dimension.getWaterLevelAt(x, y);
        final boolean lava = dimension.getBitLayerValueAt(FloodWithLava.INSTANCE, x, y);
        final int minHeight = dimension.getMinHeight();
        final int topLayerDepth = dimension.getTopLayerDepth(x, y, height);
        final List<Band> bands = new ArrayList<>();
        for (int z = Math.max(height, waterLevel); z >= minHeight; z--) {
            final String kind;
            final int colour;
            if (z > height) {
                kind = lava ? "Lava" : "Water";
                colour = lava ? 0xe8601c : 0x3f76e4;
            } else if ((z == minHeight) && (! dimension.isBottomless())) {
                kind = "Bedrock";
                colour = 0x3a3a3a;
            } else if (z > height - topLayerDepth) {
                // The top layer of the terrain, as one band, whatever blocks it mixes
                final Material material = terrain.getMaterial(platform, seed, x, y, z, height);
                kind = terrain.getName() + " (top layer)";
                colour = colourScheme.getColour(material) & 0xffffff;
                final Band last = bands.isEmpty() ? null : bands.get(bands.size() - 1);
                final Band band;
                if ((last != null) && last.kind.equals(kind)) {
                    band = last;
                    band.bottom = z;
                } else {
                    band = new Band(kind, z);
                    bands.add(band);
                }
                band.addColour(colour);
                band.mixedWith.add(family(material).toLowerCase());
                continue;
            } else {
                final Material material = subsurface.getMaterial(platform, seed, x, y, z, height);
                kind = family(material);
                colour = colourScheme.getColour(material) & 0xffffff;
            }
            final Band last = bands.isEmpty() ? null : bands.get(bands.size() - 1);
            if ((last != null) && last.kind.equals(kind)) {
                last.bottom = z;
                last.addColour(colour);
            } else {
                final Band band = new Band(kind, z);
                band.addColour(colour);
                bands.add(band);
            }
        }
        // Tidy up: small patches within a larger band (a single block of granite in the stone) join it
        for (int i = 1; i < bands.size() - 1; i++) {
            final Band band = bands.get(i), above = bands.get(i - 1), below = bands.get(i + 1);
            if ((band.thickness() <= 2) && above.kind.equals(below.kind) && (above.thickness() > 2)) {
                above.bottom = below.bottom;
                above.mixedWith.add(band.kind.toLowerCase());
                above.mixedWith.addAll(below.mixedWith);
                above.red += band.red + below.red;
                above.green += band.green + below.green;
                above.blue += band.blue + below.blue;
                above.count += band.count + below.count;
                bands.remove(i + 1);
                bands.remove(i);
                i--;
            }
        }
        return bands;
    }

    /**
     * The name of a block for the rock column, with the variants of stone and of deepslate together.
     */
    static String family(Material material) {
        final String name = material.simpleName;
        switch (name) {
            case "stone": case "granite": case "diorite": case "andesite":
                return "Stone, granite, diorite, andesite";
            case "deepslate": case "tuff":
                return "Deepslate and tuff";
            default:
                final String words = name.replace('_', ' ');
                return Character.toUpperCase(words.charAt(0)) + words.substring(1);
        }
    }

    /**
     * Whether a layer is under the ground: caves, tunnels, pockets and ores.
     */
    public static boolean isUnderground(Layer layer) {
        return (layer instanceof Caves) || (layer instanceof Caverns) || (layer instanceof Chasms) || (layer instanceof Resources)
                || (layer instanceof UndergroundPocketsLayer)
                || ((layer instanceof TunnelLayer) && (((TunnelLayer) layer).getLayerMode() != TunnelLayer.LayerMode.FLOATING));
    }

    public final Rectangle area;
    int samples, step;
    float lowest = Float.MAX_VALUE, highest = -Float.MAX_VALUE, averageHeight, averageDepth, deepest;
    final int[] slopes = new int[4];
    float slopeSum, steepest;
    final int[] heightBins = new int[HEIGHT_BINS];
    static final int HEIGHT_BINS = 32;
    int water, lava, paintedBiomes;
    final Map<Terrain, Integer> terrains = new HashMap<>();
    final Map<Integer, Integer> biomes = new HashMap<>();
    /**
     * For each layer: the number of samples where it is, and the sum of its strength there (0-15).
     */
    final Map<Layer, int[]> layers = new HashMap<>();
    final Set<Layer> everywhere = new HashSet<>();
    final Map<Layer, int[]> levels = new HashMap<>();
    final List<Ore> ores = new ArrayList<>();
    Point column;
    int columnHeight;
    List<Band> rock = Collections.emptyList();

    private static final int MAX_SAMPLES = 40_000;
    private static final Set<Class<?>> IGNORED = new HashSet<>(Arrays.asList(SelectionChunk.class, SelectionBlock.class,
            FloodWithLava.class, NotPresent.class, NotPresentBlock.class, Void.class));

    /**
     * A band of the rock column: the same kind of block from {@code top} down to {@code bottom}.
     */
    static final class Band {
        Band(String kind, int top) {
            this.kind = kind;
            this.top = top;
            bottom = top;
        }

        int thickness() {
            return top - bottom + 1;
        }

        void addColour(int colour) {
            red += (colour >> 16) & 0xff;
            green += (colour >> 8) & 0xff;
            blue += colour & 0xff;
            count++;
        }

        int colour() {
            return (count == 0) ? 0x808080 : (((int) (red / count) << 16) | ((int) (green / count) << 8) | (int) (blue / count));
        }

        final String kind;
        final int top;
        int bottom;
        final Set<String> mixedWith = new LinkedHashSet<>();
        long red, green, blue, count;
    }

    /**
     * An ore as the Resources settings place it.
     */
    static final class Ore {
        Ore(Material material, int minLevel, int maxLevel, int chance) {
            this.material = material;
            this.minLevel = minLevel;
            this.maxLevel = maxLevel;
            this.chance = chance;
        }

        final Material material;
        final int minLevel, maxLevel, chance;
    }
}
