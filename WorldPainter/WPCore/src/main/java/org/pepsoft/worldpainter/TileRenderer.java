/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.pepsoft.worldpainter;

import org.pepsoft.util.ColourUtils;
import org.pepsoft.util.IconUtils;
import org.pepsoft.worldpainter.Dimension.Anchor;
import org.pepsoft.worldpainter.biomeschemes.CustomBiomeManager;
import org.pepsoft.worldpainter.layers.*;
import org.pepsoft.worldpainter.layers.Void;
import org.pepsoft.worldpainter.layers.renderers.*;
import org.pepsoft.worldpainter.layers.tunnel.TunnelLayer;
import org.pepsoft.worldpainter.layers.tunnel.TunnelLayerHelper;
import org.pepsoft.worldpainter.ramps.ColourRamp;
import org.pepsoft.worldpainter.selection.SelectionBlock;
import org.pepsoft.worldpainter.selection.SelectionChunk;
import org.pepsoft.worldpainter.vanilla.VanillaBiomes;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.*;
import java.util.List;

import static org.pepsoft.minecraft.Constants.DEFAULT_WATER_LEVEL;
import static org.pepsoft.minecraft.Material.*;
import static org.pepsoft.worldpainter.Constants.*;
import static org.pepsoft.worldpainter.Dimension.Role.CAVE_FLOOR;
import static org.pepsoft.worldpainter.Dimension.Role.FLOATING_FLOOR;
import static org.pepsoft.worldpainter.Dimension.Role.DETAIL;
import static org.pepsoft.worldpainter.layers.tunnel.TunnelLayer.Mode.FIXED_HEIGHT_ABOVE_FLOOR;

/**
 * This class is <strong>not</strong> thread-safe! It keeps render state and should only be used to render one tile at a
 * time.
 * 
 * @author pepijn
 */
public final class TileRenderer {
    public TileRenderer(TileProvider tileProvider, ColourScheme colourScheme, CustomBiomeManager customBiomeManager, int zoom, boolean transparentVoid, ColourRamp colourRamp) {
        biomeRenderer = new BiomeRenderer(customBiomeManager, colourScheme);
        this.tileProvider = tileProvider;
        final Dimension dimension = (tileProvider instanceof Dimension) ? (Dimension) tileProvider : null;
        TileProvider relatedTileProvider = null;
        boolean renderCeilingIntersection = false, renderTunnelRoofIntersection = false;
        TunnelLayerHelper tunnelLayerHelper = null;
        if ((dimension != null) && (dimension.getWorld() != null)) {
            platform = dimension.getWorld().getPlatform();
            final Anchor anchor = dimension.getAnchor();
            if (anchor.role == DETAIL) {
                relatedTileProvider = dimension.getWorld().getDimension(new Anchor(anchor.dim, anchor.role, ! anchor.invert, 0));
                renderCeilingIntersection = (relatedTileProvider != null);
            } else if (anchor.role == CAVE_FLOOR) {
                final Dimension detailDimension = dimension.getWorld().getDimension(new Anchor(anchor.dim, DETAIL, anchor.invert, 0));
                if (detailDimension != null) {
                    final TunnelLayer tunnelLayer = TunnelLayer.find(dimension);
                    if (tunnelLayer.getRoofMode() != FIXED_HEIGHT_ABOVE_FLOOR) {
                        relatedTileProvider = detailDimension;
                        renderTunnelRoofIntersection = true;
                        tunnelLayerHelper = new TunnelLayerHelper(tunnelLayer, detailDimension);
                    }
                }
            }
        } else {
            platform = Configuration.DEFAULT_PLATFORM;
        }
        this.relatedTileProvider = relatedTileProvider;
        this.renderCeilingIntersection = renderCeilingIntersection;
        this.renderTunnelRoofIntersection = renderTunnelRoofIntersection;
        this.tunnelLayerHelper = tunnelLayerHelper;
        this.zoom = zoom;
        this.colourRamp = colourRamp;
        this.colourScheme = colourScheme;
        waterColour = colourScheme.getColour(WATER);
        lavaColour = colourScheme.getColour(LAVA);
        bedrockColour = colourScheme.getColour(BEDROCK);
        notPresentColour = 0x00000000;
        voidColour = (transparentVoid ? 0x00000000 : 0xff000000) | VoidRenderer.getColour();
        bufferedImage = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
        renderBuffer = ((DataBufferInt) bufferedImage.getRaster().getDataBuffer()).getData();
    }

    public Platform getPlatform() {
        return platform;
    }

    public ColourScheme getColourScheme() {
        return colourScheme;
    }

    public void addHiddenLayers(Collection<Layer> hiddenLayers) {
        this.hiddenLayers.addAll(hiddenLayers);
    }
    
    public void setHiddenLayers(Set<Layer> hiddenLayers) {
        this.hiddenLayers.clear();
        // The FloodWithLava layer should *always* remain hidden
        hiddenLayers.add(FloodWithLava.INSTANCE);
        this.hiddenLayers.addAll(hiddenLayers);
    }

    public Set<Layer> getHiddenLayers() {
        return Collections.unmodifiableSet(hiddenLayers);
    }

    public int getZoom() {
        return zoom;
    }

    public void setContourLines(boolean contourLines) {
        this.contourLines = contourLines;
    }

    public int getContourSeparation() {
        return contourSeparation;
    }

    public void setContourSeparation(int contourSeparation) {
        this.contourSeparation = contourSeparation;
    }

    public boolean isHideAllLayers() {
        return hideAllLayers;
    }

    public void setHideAllLayers(boolean hideAllLayers) {
        this.hideAllLayers = hideAllLayers;
    }

    public ViewMode getViewMode() {
        return viewMode;
    }

    /**
     * Set what the map shows: the terrain and layers as painted, or one property of the land on its own (its height,
     * slope, biome or relief), with only the selection on top of it.
     */
    public void setViewMode(ViewMode viewMode) {
        if (viewMode == null) {
            throw new NullPointerException();
        }
        this.viewMode = viewMode;
    }

    /**
     * Set the heights between which the colours of {@link ViewMode#HEIGHT} run, from the bottom to the top of
     * {@link #HEIGHT_RAMP}. See {@link #measureLandHeights(Dimension)}.
     */
    public void setHeightRange(float lowest, float highest) {
        heightRangeLow = lowest;
        heightRangeHigh = Math.max(lowest + 1, highest);
    }

    /**
     * Render the tile to an image, with an optional offset. This will
     * completely paint the square covered by the tile, replacing any existing
     * contents.
     *
     * @param tile The tile to render.
     * @param image The image to which to render the tile.
     * @param dx The horizontal offset at which to render the tile.
     * @param dy The vertical offset at which to render the tile.
     */
    public void renderTile(Tile tile, Image image, int dx, int dy) {
        // TODO this deadlocks background painting. Find out why:
//        synchronized (tile) {

        final int tileX = tile.getX(), tileY = tile.getY();
        // Zoomed out only one block in so many is drawn, so only its height and those of the blocks next to it (for the
        // shading) are needed, unless the whole tile is compared with another one below
        final int sampleMask = ((zoom < 0) && (! renderCeilingIntersection) && (! renderTunnelRoofIntersection)) ? ((1 << -zoom) - 1) : 0;
        for (int x = 0; x < TILE_SIZE; x++) {
            if ((sampleMask != 0) && (! isSampledOrNextToIt(x, sampleMask))) {
                continue;
            }
            for (int y = 0; y < TILE_SIZE; y++) {
                if ((sampleMask != 0) && (! isSampledOrNextToIt(y, sampleMask))) {
                    continue;
                }
                final float height = tile.getHeight(x, y);
                floatHeightCache[x | (y << TILE_SIZE_BITS)] = height;
                intHeightCache[x | (y << TILE_SIZE_BITS)] = Math.round(height);
                intFluidHeightCache[x | (y << TILE_SIZE_BITS)] = tile.getWaterLevel(x, y);
            }
        }

        // Determine which coordinates, if any, have heights which would intersect with the opposite tile, if any
        final boolean bottomless, topLayersRelativeToTerrain;
        final long seed;
        boolean noOpposites = true;
        if (tileProvider instanceof Dimension) {
            final Dimension dim = (Dimension) tileProvider, oppositeDim = (Dimension) relatedTileProvider;
            bottomless = dim.isBottomless();
            topLayersRelativeToTerrain = dim.getTopLayerAnchor() == Dimension.LayerAnchor.TERRAIN;
            seed = dim.getSeed();
            if (renderCeilingIntersection) {
                final int reflectionPoint = (dim.getAnchor().invert ? dim.getCeilingHeight() : oppositeDim.getCeilingHeight()) + oppositeDim.getMinHeight();
                final Tile relatedTile = oppositeDim.getTile(tile.getX(), tile.getY());
                if (relatedTile != null) {
                    Arrays.fill(oppositesOverlap, false);
                    for (int x = 0; x < TILE_SIZE; x++) {
                        for (int y = 0; y < TILE_SIZE; y++) {
                            if ((reflectionPoint - relatedTile.getIntHeight(x, y)) <= intHeightCache[x | (y << TILE_SIZE_BITS)]) {
                                oppositesOverlap[x | (y << TILE_SIZE_BITS)] = true;
                                noOpposites = false;
                            }
                        }
                    }
                }
            } else if (renderTunnelRoofIntersection) {
                final Dimension detailDimension = (Dimension) relatedTileProvider;
                final int minZ = detailDimension.getMinHeight() + (detailDimension.isBottomless() ? 0 : 1);
                final int maxZ = detailDimension.getMaxHeight() - 1;
                Arrays.fill(oppositesOverlap, false);
                for (int x = 0; x < TILE_SIZE; x++) {
                    for (int y = 0; y < TILE_SIZE; y++) {
                        final int worldX = (tileX << TILE_SIZE_BITS) | x, worldY = (tileY << TILE_SIZE_BITS) | y;
                        final int terrainHeight = detailDimension.getIntHeightAt(worldX, worldY);
                        final int tunnelFloorLevel = tunnelLayerHelper.calculateFloorLevel(worldX, worldY, terrainHeight, minZ, maxZ);
                        if (intHeightCache[x | (y << TILE_SIZE_BITS)] >= tunnelLayerHelper.calculateRoofLevel(worldX, worldY, terrainHeight, minZ, maxZ, tunnelFloorLevel)) {
                            oppositesOverlap[x | (y << TILE_SIZE_BITS)] = true;
                            noOpposites = false;
                        }
                    }
                }
            }
        } else {
            bottomless = false;
            topLayersRelativeToTerrain = false;
            seed = 0L;
        }

        final List<Layer> layerList = new ArrayList<>(tile.getLayers());
        if (! layerList.contains(Biome.INSTANCE)) {
            layerList.add(Biome.INSTANCE);
        }
        layerList.removeAll(hiddenLayers);
        final boolean hideTerrain = hiddenLayers.contains(TERRAIN_AS_LAYER);
        final boolean hideFluids = hiddenLayers.contains(FLUIDS_AS_LAYER);
        if (hiddenLayers.contains(ALL_TUNNELS_AS_LAYER)) {
            layerList.removeIf(layer -> layer instanceof TunnelLayer);
        }
        final boolean _void = layerList.contains(org.pepsoft.worldpainter.layers.Void.INSTANCE), notAllBlocksPresent = layerList.contains(NotPresent.INSTANCE) || layerList.contains(NotPresentBlock.INSTANCE);
        if (hideAllLayers) {
            layerList.clear();
        } else {
            layerList.removeIf(layer -> (layer instanceof Void) || (layer instanceof NotPresent) || (layer instanceof NotPresentBlock));
            if (viewMode != ViewMode.TERRAIN) {
                // Only the selection over the colours of the mode, so that what it shows stands on its own
                layerList.removeIf(layer -> (layer != SelectionChunk.INSTANCE) && (layer != SelectionBlock.INSTANCE));
            }
        }
        final Layer[] layers = layerList.toArray(new Layer[layerList.size()]);
        final LayerRenderer[] renderers = new LayerRenderer[layers.length];
        for (int i = 0; i < layers.length; i++) {
            renderers[i] = getRenderer(layers[i]);
        }
        prepareIslands(tile, layers);

        final int scale = 1 << -zoom;
        final Graphics2D g2 = (Graphics2D) image.getGraphics();
        try {
            g2.setComposite(AlphaComposite.Src);
            if (zoom == 0) {
                for (int x = 0; x < TILE_SIZE; x++) {
                    for (int y = 0; y < TILE_SIZE; y++) {
                        final int worldX = (tileX << TILE_SIZE_BITS) | x, worldY = (tileY << TILE_SIZE_BITS) | y;
                        if (notAllBlocksPresent && (tile.getBitLayerValue(NotPresent.INSTANCE, x, y) || tile.getBitLayerValue(NotPresentBlock.INSTANCE, x, y))) {
                            renderBuffer[x | (y << TILE_SIZE_BITS)] = notPresentColour;
                        } else if ((! noOpposites) && oppositesOverlap[x | (y << TILE_SIZE_BITS)] && CEILING_PATTERN[x & 0x7][y & 0x7]) {
                            renderBuffer[x | (y << TILE_SIZE_BITS)] = 0xff000000;
                        } else if (_void && tile.getBitLayerValue(org.pepsoft.worldpainter.layers.Void.INSTANCE, x, y)) {
                            renderBuffer[x | (y << TILE_SIZE_BITS)] = voidColour;
                            // TODO still render ReadOnly, and layers which might still be exported over Void
                        } else {
                            int colour = getPixelColour(tile, worldX, worldY, layers, renderers, contourLines, hideTerrain, hideFluids, bottomless, topLayersRelativeToTerrain, seed);
                            if (viewMode == ViewMode.TERRAIN) {
                                colour = ColourUtils.multiply(colour, getTerrainBrightenAmount());
                                final int offset = x + y * TILE_SIZE;
                                if (intFluidHeightCache[offset] > intHeightCache[offset]) {
                                    colour = ColourUtils.multiply(colour, getFluidBrightenAmount());
                                }
                            }
                            renderBuffer[x | (y << TILE_SIZE_BITS)] = 0xff000000 | colour;
                        }
                    }
                }

                g2.drawImage(bufferedImage, dx, dy, null);
            } else {
                final int tileSize = TILE_SIZE / scale;
                for (int x = 0; x < TILE_SIZE; x += scale) {
                    for (int y = 0; y < TILE_SIZE; y += scale) {
                        final int worldX = (tileX << TILE_SIZE_BITS) | x, worldY = (tileY << TILE_SIZE_BITS) | y;
                        if (notAllBlocksPresent && (tile.getBitLayerValue(NotPresent.INSTANCE, x, y) || tile.getBitLayerValue(NotPresentBlock.INSTANCE, x, y))) {
                            renderBuffer[x / scale + y * tileSize] = notPresentColour;
                        } else if ((! noOpposites) && oppositesOverlap[x | (y << TILE_SIZE_BITS)]) {
                            renderBuffer[x / scale + y * tileSize] = 0xff000000;
                        } else if (_void && tile.getBitLayerValue(org.pepsoft.worldpainter.layers.Void.INSTANCE, x, y)) {
                            renderBuffer[x / scale + y * tileSize] = voidColour;
                        } else {
                            int colour = getPixelColour(tile, worldX, worldY, layers, renderers, contourLines, hideTerrain, hideFluids, bottomless, topLayersRelativeToTerrain, seed);
                            if (viewMode == ViewMode.TERRAIN) {
                                colour = ColourUtils.multiply(colour, getTerrainBrightenAmount());
                                final int offset = x + y * TILE_SIZE;
                                if (intFluidHeightCache[offset] > intHeightCache[offset]) {
                                    colour = ColourUtils.multiply(colour, getFluidBrightenAmount());
                                }
                            }
                            renderBuffer[x / scale + y * tileSize] = 0xff000000 | colour;
                        }
                    }
                }

                g2.drawImage(bufferedImage, dx, dy, dx + tileSize, dy + tileSize, 0, 0, tileSize, tileSize, null);
            }
        } finally {
            g2.dispose();
        }
//        }
    }

    /**
     * Determine the brighten amount. This method assumes that the
     * {@link #deltas} array has been filled by a previous call to
     * {@link #getPixelColour(Tile, int, int, Layer[], LayerRenderer[], boolean, boolean, boolean, boolean, boolean, long)}.
     * 
     * @return The amount by which to brighten the pixel for the specified
     * block, out of 256; values below 256 darkening the pixel; values above
     * brightening it and 256 resulting in no change.
     */
    private int getTerrainBrightenAmount() {
        return getBrightenAmount(deltas);
    }

    /**
     * Determine the brighten amount for fluid. This method assumes that the
     * {@link #deltas} array has been filled by a previous call to
     * {@link #getPixelColour(Tile, int, int, Layer[], LayerRenderer[], boolean, boolean, boolean, boolean, boolean, long)}.
     *
     * @return The amount by which to brighten the pixel for the specified
     * block, out of 256; values below 256 darkening the pixel; values above
     * brightening it and 256 resulting in no change.
     */
    private int getFluidBrightenAmount() {
        return getBrightenAmount(fluidDeltas);
    }

    private int getBrightenAmount(int[][] deltas) {
        switch (lightOrigin) {
            case NORTHWEST:
                return Math.max(0, ((deltas[2][1] - deltas[0][1] + deltas[1][2] - deltas[1][0]) << 5) + 256);
            case NORTHEAST:
                return Math.max(0, ((deltas[0][1] - deltas[2][1] + deltas[1][2] - deltas[1][0]) << 5) + 256);
            case SOUTHEAST:
                return Math.max(0, ((deltas[0][1] - deltas[2][1] + deltas[1][0] - deltas[1][2]) << 5) + 256);
            case SOUTHWEST:
                return Math.max(0, ((deltas[2][1] - deltas[0][1] + deltas[1][0] - deltas[1][2]) << 5) + 256);
            case ABOVE:
                return 256;
            default:
                throw new InternalError();
        }
    }

    public LightOrigin getLightOrigin() {
        return lightOrigin;
    }
    
    public void setLightOrigin(LightOrigin lightOrigin) {
        if (lightOrigin == null) {
            throw new NullPointerException();
        }
        this.lightOrigin = lightOrigin;
    }

    private int getPixelColour(Tile tile, int worldX, int worldY, Layer[] layers, LayerRenderer[] renderers, boolean contourLines, boolean hideTerrain, boolean hideFluids, boolean bottomless, boolean topLayersRelativeToTerrain, long seed) {
        final int x = worldX & TILE_SIZE_MASK, y = worldY & TILE_SIZE_MASK;
        final int offset = x + y * TILE_SIZE;
        if (islandLayers != null) {
            // Floating islands hide the land below them: draw their tops instead
            for (int i = 0; i < islandLayers.length; i++) {
                if (tile.getBitLayerValue(islandLayers[i], x, y)) {
                    return getIslandColour(tile, i, worldX, worldY, seed);
                }
            }
        }
        final int colour = getGroundColour(tile, worldX, worldY, layers, renderers, contourLines, hideTerrain, hideFluids, bottomless, topLayersRelativeToTerrain, seed);
        if ((islandLayers != null) && (colour != BLACK)) {
            // The shadows the islands cast on the land, away from the light
            for (int i = 0; i < islandLayers.length; i++) {
                if (isIsland(tile, i, worldX - shadowDX, worldY - shadowDY)) {
                    return ColourUtils.multiply(colour, ISLAND_SHADOW);
                }
            }
        }
        return colour;
    }

    private int getGroundColour(Tile tile, int worldX, int worldY, Layer[] layers, LayerRenderer[] renderers, boolean contourLines, boolean hideTerrain, boolean hideFluids, boolean bottomless, boolean topLayersRelativeToTerrain, long seed) {
        final int x = worldX & TILE_SIZE_MASK, y = worldY & TILE_SIZE_MASK;
        final int offset = x + y * TILE_SIZE;
        final int intHeight = intHeightCache[offset], minHeight = tile.getMinHeight();
        heights[1][0] = getNeighbourHeight(tile, x, y,  0, -1);
        deltas [1][0] = heights[1][0] - intHeight;
        heights[0][1] = getNeighbourHeight(tile, x, y, -1,  0);
        deltas [0][1] = heights[0][1] - intHeight;
        heights[2][1] = getNeighbourHeight(tile, x, y,  1,  0);
        deltas [2][1] = heights[2][1] - intHeight;
        heights[1][2] = getNeighbourHeight(tile, x, y,  0,  1);
        deltas [1][2] = heights[1][2] - intHeight;
        if (viewMode != ViewMode.TERRAIN) {
            int colour = getModeColour(tile, x, y, worldX, worldY, offset, intHeight, contourLines, hideFluids);
            // Only the selection is drawn over it
            for (int i = 0; i < layers.length; i++) {
                if ((renderers[i] instanceof BitLayerRenderer) && tile.getBitLayerValue(layers[i], x, y)) {
                    colour = ((BitLayerRenderer) renderers[i]).getPixelColour(worldX, worldY, colour, true);
                }
            }
            return colour;
        }
        if (contourLines && ((intHeight % contourSeparation) == 0)
                && ((deltas[0][1] < 0)
                    || (deltas[2][1] < 0)
                    || (deltas[1][0] < 0)
                    || (deltas[1][2] < 0))) {
            return BLACK;
        }
        final int waterLevel = tile.getWaterLevel(x, y);
        fluidHeights[1][0] = getNeighbourFluidHeight(tile, x, y, 0, -1, waterLevel);
        fluidDeltas [1][0] = fluidHeights[1][0] - waterLevel;
        fluidHeights[0][1] = getNeighbourFluidHeight(tile, x, y, -1, 0, waterLevel);
        fluidDeltas [0][1] = fluidHeights[0][1] - waterLevel;
        fluidHeights[2][1] = getNeighbourFluidHeight(tile, x, y, 1, 0, waterLevel);
        fluidDeltas [2][1] = fluidHeights[2][1] - waterLevel;
        fluidHeights[1][2] = getNeighbourFluidHeight(tile, x, y, 0, 1, waterLevel);
        fluidDeltas [1][2] = fluidHeights[1][2] - waterLevel;
        int colour;
        if ((! hideFluids) && (waterLevel > intHeight)) {
            if (tile.getBitLayerValue(FloodWithLava.INSTANCE, x, y)) {
                colour = lavaColour;
            } else {
                colour = waterColour;
            }
        } else if (! hideTerrain) {
            final float height = floatHeightCache[offset];
            if ((! bottomless) && (intHeight == minHeight)) {
                colour = bedrockColour;
            } else {
                Terrain terrain = tile.getTerrain(x, y);
                if (topLayersRelativeToTerrain
                        && terrain.isCustom()) {
                    MixedMaterial mixedMaterial = Terrain.getCustomMaterial(terrain.getCustomTerrainIndex());
                    if (mixedMaterial.getMode() == MixedMaterial.Mode.LAYERED){
                        colour = terrain.getColour(seed, worldX, worldY, mixedMaterial.getPatternHeight() - 1, intHeight, platform, colourScheme);
                    } else {
                        colour = terrain.getColour(seed, worldX, worldY, height, intHeight, platform, colourScheme);
                    }
                } else {
                    colour = terrain.getColour(seed, worldX, worldY, height, intHeight, platform, colourScheme);
                }
            }
        } else {
            colour = (colourRamp != null) ? colourRamp.getColour(floatHeightCache[offset]) : LIGHT_GREY;
        }
        for (int i = 0; i < layers.length; i++) {
            final Layer layer = layers[i];
            switch (layer.getDataSize()) {
                case BIT:
                case BIT_PER_CHUNK:
                    if (hideFluids && (layer instanceof Frost) && (waterLevel > intHeightCache[offset])) {
                        continue;
                    }
                    boolean bitLayerValue = tile.getBitLayerValue(layer, x, y);
                    if (bitLayerValue) {
                        final BitLayerRenderer renderer = (BitLayerRenderer) renderers[i];
                        colour = renderer.getPixelColour(worldX, worldY, colour, true);
                    }
                    break;
                case NIBBLE:
                    int layerValue = tile.getLayerValue(layer, x, y);
                    if (layerValue > 0) {
                        final NibbleLayerRenderer renderer = (NibbleLayerRenderer) renderers[i];
                        colour = renderer.getPixelColour(worldX, worldY, colour, layerValue);
                    }
                    break;
                case BYTE:
                    final ByteLayerRenderer byteLayerRenderer = (ByteLayerRenderer) renderers[i];
                    colour = byteLayerRenderer.getPixelColour(worldX, worldY, colour, tile.getLayerValue(layer, x, y));
                    break;
                default:
                    throw new UnsupportedOperationException("Don't know how to render " + layer.getClass().getSimpleName());
            }
        }
        return colour;
    }

    /**
     * The colour of a column in one of the modes which show a property of the land on its own: from the same colour
     * ramps as the 3D view, lit by the slope of the land from the side of {@link #lightOrigin}. Assumes that
     * {@link #heights} and {@link #deltas} have been filled in for the column.
     */
    private int getModeColour(Tile tile, int x, int y, int worldX, int worldY, int offset, int intHeight, boolean contourLines, boolean hideFluids) {
        final float height = floatHeightCache[offset];
        // The slopes over the width of a pixel, which is several blocks when zoomed out
        final int step = (zoom < 0) ? (1 << -zoom) : 1;
        final float west = getNeighbourFloatHeight(tile, x, y, -step, 0, height), east = getNeighbourFloatHeight(tile, x, y, step, 0, height);
        final float north = getNeighbourFloatHeight(tile, x, y, 0, -step, height), south = getNeighbourFloatHeight(tile, x, y, 0, step, height);
        final float dzdx = (east - west) / (2 * step), dzdy = (south - north) / (2 * step);
        final int waterLevel = tile.getWaterLevel(x, y);
        final boolean flooded = (! hideFluids) && (viewMode != ViewMode.SLOPE) && (waterLevel > intHeight);
        int colour;
        switch (viewMode) {
            case HEIGHT:
                colour = shade(ramp(HEIGHT_RAMP, (height - heightRangeLow) / (heightRangeHigh - heightRangeLow)), 0.72f + 0.28f * light(dzdx, dzdy, 1.5f));
                break;
            case SLOPE:
                final float slope = (float) Math.toDegrees(Math.atan(Math.sqrt(dzdx * dzdx + dzdy * dzdy)));
                colour = shade(ramp(SLOPE_RAMP, Math.min(1, slope / 60f)), 0.82f + 0.18f * light(dzdx, dzdy, 1.5f));
                break;
            case BIOME:
                int biome = tile.getLayerValue(Biome.INSTANCE, x, y);
                if ((biome == 255) && (tileProvider instanceof Dimension)) {
                    biome = ((Dimension) tileProvider).getAutoBiome(tile, x, y);
                }
                // Only a little relief, so that the borders between the biomes stand out
                colour = shade(((biome >= 0) && (biome != 255)) ? VanillaBiomes.getColour(VanillaBiomes.fromWorldPainterBiome(biome)) : 0x808080, 0.88f + 0.12f * light(dzdx, dzdy, 1.0f));
                break;
            case RELIEF:
                colour = shade(RELIEF_COLOUR, 0.45f + 0.52f * light(dzdx, dzdy, 1.2f));
                break;
            default:
                throw new InternalError();
        }
        if (flooded) {
            if (tile.getBitLayerValue(FloodWithLava.INSTANCE, x, y)) {
                colour = lavaColour;
            } else {
                final float depth = Math.min(1, (waterLevel - height) / 24f);
                final int water = blend(0x4a8fe7, 0x1d3f8f, depth);
                colour = (viewMode == ViewMode.HEIGHT) ? water : blend(colour, water, (viewMode == ViewMode.BIOME) ? 0.4f : 0.55f);
                // The shore, drawn as a line
                if ((heights[1][0] >= waterLevel) || (heights[0][1] >= waterLevel) || (heights[2][1] >= waterLevel) || (heights[1][2] >= waterLevel)) {
                    colour = shade(colour, 0.7f);
                }
            }
        } else if (contourLines && (viewMode == ViewMode.HEIGHT)) {
            // A line on the upper side wherever a contour runs between this column and the one next to it, so that the
            // lines stay unbroken on steep and rough land; every fifth one darker
            // (Further apart when zoomed out far, so that they do not run together)
            final int interval = contourSeparation * ((zoom < -1) ? (1 << (-zoom - 1)) : 1);
            final int level = (int) Math.floor(height / interval);
            final int lowestLevel = (int) Math.floor(Math.min(Math.min(west, east), Math.min(north, south)) / interval);
            if (lowestLevel < level) {
                colour = shade(colour, (Math.floorMod(level, 5) == 0) ? 0.55f : 0.75f);
            }
        }
        return colour;
    }

    /**
     * How brightly the land is lit, from the side of {@link #lightOrigin}, half way up the sky: 1 for flat land, from 0
     * for land facing away from the light to about 1.4 for land facing it.
     *
     * @param exaggeration How much steeper than it is to light the land, to bring out the shape of gentle land.
     */
    private float light(float dzdx, float dzdy, float exaggeration) {
        dzdx *= exaggeration;
        dzdy *= exaggeration;
        final float lightX, lightY;
        switch (lightOrigin) {
            case NORTHEAST -> { lightX = 0.5f; lightY = -0.5f; }
            case SOUTHEAST -> { lightX = 0.5f; lightY = 0.5f; }
            case SOUTHWEST -> { lightX = -0.5f; lightY = 0.5f; }
            case ABOVE -> { lightX = 0; lightY = 0; }
            default -> { lightX = -0.5f; lightY = -0.5f; }
        }
        final float lightZ = (lightOrigin == LightOrigin.ABOVE) ? 1.0f : 0.70710677f;
        final float dot = (-dzdx * lightX - dzdy * lightY + lightZ) / (float) Math.sqrt(dzdx * dzdx + dzdy * dzdy + 1);
        return Math.max(0, dot) / lightZ;
    }

    private float getNeighbourFloatHeight(Tile tile, int x, int y, int dx, int dy, float defaultHeight) {
        x += dx;
        y += dy;
        if ((x >= 0) && (x < TILE_SIZE) && (y >= 0) && (y < TILE_SIZE)) {
            return floatHeightCache[x + y * TILE_SIZE];
        }
        final Tile neighbour = tileProvider.getTile(tile.getX() + (x >> TILE_SIZE_BITS), tile.getY() + (y >> TILE_SIZE_BITS));
        return (neighbour != null) ? neighbour.getHeight(x & TILE_SIZE_MASK, y & TILE_SIZE_MASK) : defaultHeight;
    }

    /**
     * Measure the heights of the land of a dimension which is not under water, from a sample of its columns, for the
     * colours of {@link ViewMode#HEIGHT}. If all of it is under water, the heights of all of it.
     *
     * @return The lowest and highest height, in that order.
     */
    public static float[] measureLandHeights(Dimension dimension) {
        float lowestDry = Float.MAX_VALUE, highestDry = -Float.MAX_VALUE, lowest = Float.MAX_VALUE, highest = -Float.MAX_VALUE;
        final int step = (dimension.getTileCount() > 4096) ? 16 : 4;
        for (Tile tile: dimension.getTiles()) {
            for (int y = step / 2; y < TILE_SIZE; y += step) {
                for (int x = step / 2; x < TILE_SIZE; x += step) {
                    final float height = tile.getHeight(x, y);
                    lowest = Math.min(lowest, height);
                    highest = Math.max(highest, height);
                    if (tile.getWaterLevel(x, y) <= Math.round(height)) {
                        lowestDry = Math.min(lowestDry, height);
                        highestDry = Math.max(highestDry, height);
                    }
                }
            }
        }
        if (lowestDry <= highestDry) {
            return new float[] {lowestDry, Math.max(lowestDry + 1, highestDry)};
        } else if (lowest <= highest) {
            return new float[] {lowest, Math.max(lowest + 1, highest)};
        } else {
            return new float[] {DEFAULT_WATER_LEVEL, DEFAULT_WATER_LEVEL + 64};
        }
    }

    /**
     * A colour part of the way along a ramp of colours.
     *
     * @param stops The colours of the ramp, evenly spaced.
     * @param t How far along it, from 0 to 1.
     */
    public static int ramp(int[] stops, float t) {
        t = Math.max(0, Math.min(1, t));
        final float position = t * (stops.length - 1);
        final int index = Math.min(stops.length - 2, (int) position);
        return blend(stops[index], stops[index + 1], position - index);
    }

    private static int blend(int colour1, int colour2, float t) {
        final int r = (int) (((colour1 >> 16) & 0xff) * (1 - t) + ((colour2 >> 16) & 0xff) * t);
        final int g = (int) (((colour1 >> 8) & 0xff) * (1 - t) + ((colour2 >> 8) & 0xff) * t);
        final int b = (int) ((colour1 & 0xff) * (1 - t) + (colour2 & 0xff) * t);
        return (r << 16) | (g << 8) | b;
    }

    private static int shade(int colour, float factor) {
        final int r = Math.min(255, (int) (((colour >> 16) & 0xff) * factor));
        final int g = Math.min(255, (int) (((colour >> 8) & 0xff) * factor));
        final int b = Math.min(255, (int) ((colour & 0xff) * factor));
        return (r << 16) | (g << 8) | b;
    }

    /**
     * Find the floating islands (floating dimension layers) of a tile, which are drawn as the land on top of them,
     * seen from above, rather than as a colour.
     */
    private void prepareIslands(Tile tile, Layer[] layers) {
        islandLayers = null;
        if (! (tileProvider instanceof Dimension)) {
            return;
        }
        final Dimension dimension = (Dimension) tileProvider;
        final List<TunnelLayer> found = new ArrayList<>();
        final List<Dimension> floors = new ArrayList<>();
        for (Layer layer: layers) {
            if ((layer instanceof TunnelLayer) && (((TunnelLayer) layer).getLayerMode() == TunnelLayer.LayerMode.FLOATING) && (((TunnelLayer) layer).getFloorDimensionId() != null)) {
                final Anchor anchor = dimension.getAnchor();
                final Dimension floor = dimension.getWorld().getDimension(new Anchor(anchor.dim, FLOATING_FLOOR, anchor.invert, ((TunnelLayer) layer).getFloorDimensionId()));
                if (floor != null) {
                    found.add((TunnelLayer) layer);
                    floors.add(floor);
                }
            }
        }
        if (found.isEmpty()) {
            return;
        }
        final int count = found.size();
        islandLayers = found.toArray(new TunnelLayer[count]);
        islandFloors = floors.toArray(new Dimension[count]);
        islandFloorTiles = new Tile[count];
        islandFloorLayers = new Layer[count][];
        islandFloorRenderers = new LayerRenderer[count][];
        islandRimColours = new int[count];
        for (int i = 0; i < count; i++) {
            final Tile floorTile = islandFloors[i].getTile(tile.getX(), tile.getY());
            islandFloorTiles[i] = floorTile;
            final List<Layer> floorLayers = (floorTile != null) ? new ArrayList<>(floorTile.getLayers()) : new ArrayList<>();
            floorLayers.removeIf(layer -> (layer instanceof Biome) || hiddenLayers.contains(layer) || (layer.getDataSize() == Layer.DataSize.BYTE) || (layer.getDataSize() == Layer.DataSize.NONE));
            islandFloorLayers[i] = floorLayers.toArray(new Layer[0]);
            islandFloorRenderers[i] = new LayerRenderer[islandFloorLayers[i].length];
            for (int j = 0; j < islandFloorLayers[i].length; j++) {
                islandFloorRenderers[i][j] = getRenderer(islandFloorLayers[i][j]);
            }
            final Object paint = islandLayers[i].getPaint();
            islandRimColours[i] = (paint instanceof Color) ? (((Color) paint).getRGB() & 0xffffff) : 0xeab308;
        }
        // Outlines and shadows a few pixels wide however far the map is zoomed out
        final int scale = (zoom < 0) ? (1 << -zoom) : 1;
        final int shadow = Math.max(ISLAND_SHADOW_DISTANCE, 3 * scale);
        switch (lightOrigin) {
            case NORTHEAST -> { shadowDX = -shadow; shadowDY = shadow; }
            case SOUTHEAST -> { shadowDX = -shadow; shadowDY = -shadow; }
            case SOUTHWEST -> { shadowDX = shadow; shadowDY = -shadow; }
            case ABOVE -> { shadowDX = 0; shadowDY = 0; }
            default -> { shadowDX = shadow; shadowDY = shadow; }
        }
        islandStep = scale;
    }

    /**
     * Whether there is an island of one of the floating layers of the tile being drawn at a location.
     */
    private boolean isIsland(Tile tile, int index, int worldX, int worldY) {
        if (((worldX >> TILE_SIZE_BITS) == tile.getX()) && ((worldY >> TILE_SIZE_BITS) == tile.getY())) {
            return tile.getBitLayerValue(islandLayers[index], worldX & TILE_SIZE_MASK, worldY & TILE_SIZE_MASK);
        } else {
            return ((Dimension) tileProvider).getBitLayerValueAt(islandLayers[index], worldX, worldY);
        }
    }

    /**
     * The colour of the top of a floating island: the terrain and layers (trees, snow) of its floor dimension, lit by
     * the slopes of the island rather than of the land below it, with a rim of the colour of its layer around the edge
     * so that islands stand out from the land.
     */
    private int getIslandColour(Tile tile, int index, int worldX, int worldY, long seed) {
        final Dimension floor = islandFloors[index];
        final Tile floorTile = islandFloorTiles[index];
        final int x = worldX & TILE_SIZE_MASK, y = worldY & TILE_SIZE_MASK;
        final int top = (floorTile != null) ? floorTile.getIntHeight(x, y) : islandLayers[index].getFloorLevel();
        int colour;
        if (floorTile != null) {
            colour = floorTile.getTerrain(x, y).getColour(floor.getSeed(), worldX, worldY, floorTile.getHeight(x, y), top, platform, colourScheme);
            final Layer[] floorLayers = islandFloorLayers[index];
            for (int j = 0; j < floorLayers.length; j++) {
                switch (floorLayers[j].getDataSize()) {
                    case BIT, BIT_PER_CHUNK -> {
                        if (floorTile.getBitLayerValue(floorLayers[j], x, y)) {
                            colour = ((BitLayerRenderer) islandFloorRenderers[index][j]).getPixelColour(worldX, worldY, colour, true);
                        }
                    }
                    case NIBBLE -> {
                        final int value = floorTile.getLayerValue(floorLayers[j], x, y);
                        if (value > 0) {
                            colour = ((NibbleLayerRenderer) islandFloorRenderers[index][j]).getPixelColour(worldX, worldY, colour, value);
                        }
                    }
                    default -> {}
                }
            }
        } else {
            colour = islandRimColours[index];
        }
        // The slopes of the top of the island, for the shading; the edge counts as a drop, which bevels the island
        boolean edge = false;
        for (int d = 0; d < 4; d++) {
            final int dx = NEIGHBOUR_DX[d], dy = NEIGHBOUR_DY[d];
            final int nx = worldX + dx * islandStep, ny = worldY + dy * islandStep;
            final int neighbourHeight;
            if (isIsland(tile, index, nx, ny)) {
                neighbourHeight = floor.getIntHeightAt(nx, ny, top);
            } else {
                neighbourHeight = top - ISLAND_EDGE_DROP;
                edge = true;
            }
            deltas[1 + dx][1 + dy] = (neighbourHeight - top) / islandStep;
        }
        for (int[] row: fluidDeltas) {
            Arrays.fill(row, 0);
        }
        if (edge) {
            colour = ColourUtils.mix(islandRimColours[index], colour, 160);
        }
        return colour;
    }

    /**
     * Whether a coordinate in a tile is drawn when zoomed out, or is next to one which is.
     *
     * @param coordinate The coordinate in the tile.
     * @param sampleMask The scale of the zoom minus one: one coordinate in every {@code sampleMask + 1} is drawn.
     */
    private static boolean isSampledOrNextToIt(int coordinate, int sampleMask) {
        final int offset = coordinate & sampleMask;
        return (offset <= 1) || (offset == sampleMask);
    }

    private int getNeighbourHeight(Tile tile, int x, int y, int dx, int dy) {
        x = x + dx;
        y = y + dy;
        if ((x >= 0) && (x < TILE_SIZE) && (y >= 0) && (y < TILE_SIZE)) {
            return intHeightCache[x + y * TILE_SIZE];
        } else {
            int tileDX = 0, tileDY = 0;
            if (x < 0) {
                tileDX = -1;
                x += TILE_SIZE;
            } else if (x >= TILE_SIZE) {
                tileDX = 1;
                x -= TILE_SIZE;
            }
            if (y < 0) {
                tileDY = -1;
                y += TILE_SIZE;
            } else if (y >= TILE_SIZE) {
                tileDY = 1;
                y -= TILE_SIZE;
            }
            Tile neighborTile = tileProvider.getTile(tile.getX() + tileDX, tile.getY() + tileDY);
            return (neighborTile != null) ? neighborTile.getIntHeight(x, y) : DEFAULT_WATER_LEVEL;
        }
    }

    private int getNeighbourFluidHeight(Tile tile, int x, int y, int dx, int dy, int defaultHeight) {
        x = x + dx;
        y = y + dy;
        if ((x >= 0) && (x < TILE_SIZE) && (y >= 0) && (y < TILE_SIZE)) {
            int offset = x + y * TILE_SIZE;
            return (intFluidHeightCache[offset] > intHeightCache[offset]) ? intFluidHeightCache[offset] : defaultHeight;
        } else {
            int tileDX = 0, tileDY = 0;
            if (x < 0) {
                tileDX = -1;
                x += TILE_SIZE;
            } else if (x >= TILE_SIZE) {
                tileDX = 1;
                x -= TILE_SIZE;
            }
            if (y < 0) {
                tileDY = -1;
                y += TILE_SIZE;
            } else if (y >= TILE_SIZE) {
                tileDY = 1;
                y -= TILE_SIZE;
            }
            Tile neighborTile = tileProvider.getTile(tile.getX() + tileDX, tile.getY() + tileDY);
            if (neighborTile != null) {
                int waterLevel = neighborTile.getWaterLevel(x, y);
                return (waterLevel > neighborTile.getIntHeight(x, y)) ? waterLevel : defaultHeight;
            } else {
                return defaultHeight;
            }
        }
    }

    private LayerRenderer getRenderer(Layer layer) {
        return rendererCache.computeIfAbsent(layer, k -> {
            final LayerRenderer renderer;
            if (layer instanceof Biome) {
                renderer = biomeRenderer;
            } else {
                renderer = layer.getRenderer();
            }
            if (renderer instanceof ColourSchemeRenderer) {
                ((ColourSchemeRenderer) renderer).setColourScheme(colourScheme);
            }
            if ((renderer instanceof DimensionAwareRenderer) && (tileProvider instanceof Dimension)) {
                ((DimensionAwareRenderer) renderer).setDimension((Dimension) tileProvider);
            }
            return renderer;
        });
    }

    private final Set<Layer> hiddenLayers = new HashSet<>(Collections.singletonList(FloodWithLava.INSTANCE));
    private final int[] intHeightCache = new int[TILE_SIZE * TILE_SIZE], intFluidHeightCache = new int[TILE_SIZE * TILE_SIZE];
    private final float[] floatHeightCache = new float[TILE_SIZE * TILE_SIZE];
    private final BufferedImage bufferedImage;
    private final int[] renderBuffer;
    private final int[][] heights = new int[3][3], deltas = new int[3][3], fluidHeights = new int[3][3], fluidDeltas = new int[3][3];
    private final boolean[] oppositesOverlap = new boolean[TILE_SIZE * TILE_SIZE];
    private final int zoom, waterColour, lavaColour, bedrockColour, notPresentColour, voidColour;;
    private final Platform platform;
    private final TileProvider tileProvider, relatedTileProvider;
    private final boolean renderCeilingIntersection, renderTunnelRoofIntersection;
    private final TunnelLayerHelper tunnelLayerHelper;
    private final ColourRamp colourRamp;
    private final BiomeRenderer biomeRenderer;
    private final ColourScheme colourScheme;
    private final Map<Layer, LayerRenderer> rendererCache = new HashMap<>();
    // The floating islands of the tile being drawn, if any
    private TunnelLayer[] islandLayers;
    private Dimension[] islandFloors;
    private Tile[] islandFloorTiles;
    private Layer[][] islandFloorLayers;
    private LayerRenderer[][] islandFloorRenderers;
    private int[] islandRimColours;
    private int islandStep = 1, shadowDX, shadowDY;

    private static final int[] NEIGHBOUR_DX = {0, -1, 1, 0}, NEIGHBOUR_DY = {-1, 0, 0, 1};
    private static final int ISLAND_SHADOW_DISTANCE = 8, ISLAND_EDGE_DROP = 4, ISLAND_SHADOW = 150;
    private boolean contourLines = true, hideAllLayers;
    private int contourSeparation = 10;
    private LightOrigin lightOrigin = LightOrigin.NORTHWEST;
    private ViewMode viewMode = ViewMode.TERRAIN;
    private float heightRangeLow = DEFAULT_WATER_LEVEL, heightRangeHigh = DEFAULT_WATER_LEVEL + 64;

    /**
     * The colours of {@link ViewMode#HEIGHT}, from low to high land: green lowlands, sand, brown hills and white peaks.
     * The 3D view uses the same.
     */
    public static final int[] HEIGHT_RAMP = {0x2e6b3f, 0x5f9e4a, 0xa9c56b, 0xe6d79a, 0xc39a68, 0x8b6a52, 0xf5f5f4};

    /**
     * The colours of {@link ViewMode#SLOPE}, from flat (blue) to 60° and steeper (red). The 3D view uses the same.
     */
    public static final int[] SLOPE_RAMP = {0x3b82f6, 0x22c55e, 0xeab308, 0xf97316, 0xdc2626};

    private static final int RELIEF_COLOUR = 0xe2dfdc;

    public static final Layer TERRAIN_AS_LAYER = new Layer("org.pepsoft.synthetic.Terrain", "Terrain", "The terrain type of the surface", Layer.DataSize.NONE, false, 0) {
        @Override
        public BufferedImage getIcon() {
            return ICON;
        }

        private final BufferedImage ICON = IconUtils.scaleIcon(IconUtils.loadScaledImage("org/pepsoft/worldpainter/resources/terrain.png"), 16);
    };
    public static final Layer FLUIDS_AS_LAYER = new Layer("org.pepsoft.synthetic.Fluids", "Water/Lava", "Areas flooded with water or lava", Layer.DataSize.NONE, false, 0) {
        @Override
        public BufferedImage getIcon() {
            return ICON;
        }

        private final BufferedImage ICON = IconUtils.loadScaledImage("org/pepsoft/worldpainter/resources/fluids.png");
    };
    public static final Layer ALL_TUNNELS_AS_LAYER = new Layer("org.pepsoft.synthetic.Tunnels", "Cave/Tunnel Layers", "All Custom Cave/Tunnel Layers", Layer.DataSize.NONE, false, 0) {
        @Override
        public BufferedImage getIcon() {
            return ICON;
        }

        private final BufferedImage ICON = IconUtils.loadScaledImage("org/pepsoft/worldpainter/resources/tunnels.png");
    };

    private static final int BLACK = 0x000000, LIGHT_GREY = 0xD0D0D0;
    private static final boolean[][] CEILING_PATTERN = {
            { true,  true, false, false,  true,  true, false, false},
            {false,  true,  true,  true,  true, false, false, false},
            {false, false,  true,  true, false, false, false, false},
            {false,  true,  true,  true,  true, false, false, false},
            { true,  true, false, false,  true,  true, false, false},
            { true, false, false, false, false,  true,  true,  true},
            {false, false, false, false, false, false,  true,  true},
            { true, false, false, false, false,  true,  true,  true}};
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(TileRenderer.class);

    /**
     * What the map shows.
     */
    public enum ViewMode {
        TERRAIN("Terrain", "The terrain and the layers, as painted"),
        HEIGHT("Height", "A colour gradient from the lowest to the highest land, with contour lines: read the altitude at a glance"),
        SLOPE("Slope", "From blue (flat) to red (steep): find cliffs and flat land to build on"),
        BIOME("Biome", "The biome of each column, painted or automatic"),
        RELIEF("Relief", "Plain shaded relief, to judge the shape of the land");

        ViewMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }

        private final String displayName, description;
    }

    public enum LightOrigin {
        NORTHWEST {
            @Override
            public LightOrigin left() {
                return ABOVE;
            }

            @Override
            public LightOrigin right() {
                return NORTHEAST;
            }
        },
        NORTHEAST {
            @Override
            public LightOrigin left() {
                return NORTHWEST;
            }

            @Override
            public LightOrigin right() {
                return SOUTHEAST;
            }
        },
        SOUTHEAST{
            @Override
            public LightOrigin left() {
                return NORTHEAST;
            }

            @Override
            public LightOrigin right() {
                return SOUTHWEST;
            }
        },
        SOUTHWEST{
            @Override
            public LightOrigin left() {
                return SOUTHEAST;
            }

            @Override
            public LightOrigin right() {
                return ABOVE;
            }
        },
        ABOVE{
            @Override
            public LightOrigin left() {
                return SOUTHWEST;
            }

            @Override
            public LightOrigin right() {
                return NORTHWEST;
            }
        };

        public abstract LightOrigin left();
        
        public abstract LightOrigin right();
    }
}