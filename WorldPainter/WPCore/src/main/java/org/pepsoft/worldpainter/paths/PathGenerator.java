package org.pepsoft.worldpainter.paths;

import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.ProgressReceiver.OperationCancelled;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Dimension.Anchor;
import org.pepsoft.worldpainter.MaterialGroup;
import org.pepsoft.worldpainter.nodes.SurfaceRules;
import org.pepsoft.worldpainter.Platform;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.TileFactoryFactory;
import org.pepsoft.worldpainter.World2;
import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.layers.Bo2Layer;
import org.pepsoft.worldpainter.layers.CustomLayer;
import org.pepsoft.worldpainter.layers.Layer;
import org.pepsoft.worldpainter.layers.TreeLayer;
import org.pepsoft.worldpainter.layers.bo2.Bo2ObjectTube;
import org.pepsoft.worldpainter.layers.plants.PlantLayer;
import org.pepsoft.worldpainter.objects.GenericObject;
import org.pepsoft.worldpainter.objects.WPObject;
import org.pepsoft.worldpainter.layers.tunnel.TunnelLayer;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;

import static org.pepsoft.worldpainter.Constants.TILE_SIZE_BITS;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE_MASK;
import static org.pepsoft.worldpainter.Dimension.Role.CAVE_FLOOR;
import static org.pepsoft.worldpainter.Dimension.Role.FLOATING_FLOOR;
import static org.pepsoft.worldpainter.layers.tunnel.TunnelLayer.EdgeShape.SHEER;
import static org.pepsoft.worldpainter.layers.tunnel.TunnelLayer.LayerMode.CAVE;
import static org.pepsoft.worldpainter.layers.tunnel.TunnelLayer.LayerMode.FLOATING;
import static org.pepsoft.worldpainter.layers.tunnel.TunnelLayer.Mode.CUSTOM_DIMENSION;
import static org.pepsoft.worldpainter.layers.tunnel.TunnelLayer.Mode.FIXED_HEIGHT_ABOVE_FLOOR;

/**
 * Lays out a path (a road, a track) from one point of a dimension to another: it finds a route over the land which, as
 * much as the user wants, follows the terrain rather than going straight, works out a height profile with a limited
 * slope, decides where the path should cross on a bridge or go through a tunnel (if allowed), and finally carves and
 * paints the path into the dimension.
 *
 * <p>Bridges are made with a floating {@link TunnelLayer} whose floor dimension holds the deck (and parapets); tunnels
 * with a cave {@code TunnelLayer} whose floor dimension follows the path, so both export to Minecraft like any other
 * layer.
 */
public final class PathGenerator {
    private PathGenerator() {
        // Prevent instantiation
    }

    /**
     * Work out a path between two points, without changing the dimension.
     */
    public static PathPlan plan(Dimension dimension, Point a, Point b, PathSettings settings) {
        return plan(dimension, List.of(a, b), settings);
    }

    /**
     * Work out a path through a series of points (at least two), without changing the dimension.
     */
    public static PathPlan plan(Dimension dimension, List<Point> waypoints, PathSettings settings) {
        if (waypoints.size() < 2) {
            throw new IllegalArgumentException("A path needs at least two points");
        }
        final List<double[]> route = new ArrayList<>();
        for (int i = 0; i < waypoints.size() - 1; i++) {
            final List<double[]> leg = route(dimension, waypoints.get(i), waypoints.get(i + 1), settings);
            // The legs share their end and start points
            route.addAll((i == 0) ? leg : leg.subList(1, leg.size()));
        }
        final List<double[]> points = resample(smooth(route, 3), 1.0);
        final int n = points.size();
        final float[] terrain = new float[n], water = new float[n];
        for (int i = 0; i < n; i++) {
            final int x = (int) Math.floor(points.get(i)[0]), y = (int) Math.floor(points.get(i)[1]);
            terrain[i] = height(dimension, x, y);
            final int waterLevel = dimension.getWaterLevelAt(x, y);
            water[i] = (waterLevel > terrain[i]) ? waterLevel : Float.NaN;
        }
        // Where the path crosses a place without land (outside the map), carry on at the height of the last land
        float last = Float.NaN;
        for (int i = 0; i < n; i++) {
            if (terrain[i] == -Float.MAX_VALUE) {
                terrain[i] = Float.NaN;
            } else if (Float.isNaN(last)) {
                last = terrain[i];
                for (int j = 0; j < i; j++) {
                    terrain[j] = last;
                }
            }
            if (Float.isNaN(terrain[i])) {
                terrain[i] = Float.isNaN(last) ? 64 : last;
            } else {
                last = terrain[i];
            }
        }
        final float[] profile = profile(terrain, water, settings);
        final byte[] types = segments(terrain, water, profile, settings);
        return new PathPlan(points, terrain, water, profile, types);
    }

    /**
     * Carve and paint a planned path into a dimension, creating bridge and tunnel layers (with their floor dimensions)
     * as needed.
     *
     * @param layerNumber A number to tell the bridge and tunnel layers of different paths apart.
     * @return The bridge and tunnel layers which were created, if any, so that they can be shown to the user.
     */
    public static List<CustomLayer> apply(Dimension dimension, PathPlan plan, PathSettings settings, int layerNumber) {
        try {
            return apply(dimension, plan, settings, layerNumber, null);
        } catch (OperationCancelled e) {
            throw new InternalError(e);
        }
    }

    /**
     * Lay out a path, reporting the progress.
     *
     * @see #apply(Dimension, PathPlan, PathSettings, int)
     */
    public static List<CustomLayer> apply(Dimension dimension, PathPlan plan, PathSettings settings, int layerNumber, ProgressReceiver progressReceiver) throws OperationCancelled {
        final List<CustomLayer> createdLayers = new ArrayList<>();
        final List<double[]> points = plan.getPoints();
        final int n = points.size();
        if (n == 0) {
            return createdLayers;
        }
        // The width at every point of the path: as set, or varying with the land
        final float[] halves = halfWidths(dimension, plan, settings);
        float maxHalf = 0;
        for (float h: halves) {
            maxHalf = Math.max(maxHalf, h);
        }
        final float reach = maxHalf + Math.max(3, maxHalf * 1.5f) + 1;

        // For every block near the path, find the nearest point of the path and the distance to it
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (double[] point: points) {
            minX = Math.min(minX, point[0]);
            minY = Math.min(minY, point[1]);
            maxX = Math.max(maxX, point[0]);
            maxY = Math.max(maxY, point[1]);
        }
        final int x0 = (int) Math.floor(minX - reach - 1), y0 = (int) Math.floor(minY - reach - 1);
        final int width = (int) Math.ceil(maxX + reach + 1) - x0 + 1, height = (int) Math.ceil(maxY + reach + 1) - y0 + 1;
        final float[] distances = new float[width * height];
        final int[] nearest = new int[width * height];
        Arrays.fill(distances, Float.MAX_VALUE);
        for (int i = 0; i < n; i++) {
            if ((progressReceiver != null) && ((i & 255) == 0)) {
                progressReceiver.setProgress(0.3f * i / n);
            }
            final double px = points.get(i)[0], py = points.get(i)[1];
            for (int y = (int) Math.floor(py - reach); y <= (int) Math.ceil(py + reach); y++) {
                for (int x = (int) Math.floor(px - reach); x <= (int) Math.ceil(px + reach); x++) {
                    final float d = (float) Math.hypot(x + 0.5 - px, y + 0.5 - py);
                    final int j = (y - y0) * width + (x - x0);
                    if (d < distances[j]) {
                        distances[j] = d;
                        nearest[j] = i;
                    }
                }
            }
        }

        final World2 world = dimension.getWorld();
        final Platform platform = world.getPlatform();
        final Anchor anchor = dimension.getAnchor();
        final float[] profile = plan.getProfile();
        final byte[] types = plan.getTypes();
        final boolean anyBridge = plan.count(PathPlan.BRIDGE) > 0, anyTunnel = plan.count(PathPlan.TUNNEL) > 0;
        TunnelLayer bridgeLayer = null, tunnelLayer = null;
        if (anyBridge) {
            bridgeLayer = new TunnelLayer("Bridge " + layerNumber, FLOATING, new Color(0x9a6b3f), platform);
            bridgeLayer.setFloorMode(CUSTOM_DIMENSION);
            bridgeLayer.setRoofMode(FIXED_HEIGHT_ABOVE_FLOOR);
            bridgeLayer.setRoofLevel(2); // The thickness of the deck
            bridgeLayer.setBottomEdgeShape(SHEER);
            bridgeLayer.setFloorWallDepth(1);
            bridgeLayer.setRoofWallDepth(1);
            createdLayers.add(bridgeLayer);
        }
        if (anyTunnel) {
            tunnelLayer = new TunnelLayer("Tunnel " + layerNumber, CAVE, new Color(0x57534e), platform);
            tunnelLayer.setFloorMode(CUSTOM_DIMENSION);
            tunnelLayer.setRoofMode(FIXED_HEIGHT_ABOVE_FLOOR);
            tunnelLayer.setRoofLevel(settings.getTunnelHeight());
            tunnelLayer.setFloorWallDepth(1);
            tunnelLayer.setRoofWallDepth(2);
            tunnelLayer.setRemoveWater(true);
            createdLayers.add(tunnelLayer);
        }

        // The ground: cut and fill to the profile, with a soft transition to the land on either side, and paint it.
        // Bridges and tunnels: mark out their layers
        for (int y = 0; y < height; y++) {
            if ((progressReceiver != null) && ((y & 63) == 0)) {
                progressReceiver.setProgress(0.3f + 0.6f * y / height);
            }
            for (int x = 0; x < width; x++) {
                final int j = y * width + x;
                final float d = distances[j];
                if (d > reach) {
                    continue;
                }
                final int wx = x0 + x, wy = y0 + y;
                if (! dimension.isTilePresent(wx >> TILE_SIZE_BITS, wy >> TILE_SIZE_BITS)) {
                    continue;
                }
                final int i = nearest[j];
                final float target = profile[i];
                final float half = halves[i], falloff = Math.max(3, half * 1.5f);
                if (d > half + falloff) {
                    continue;
                }
                if (settings.isClearVegetation() && (types[i] == PathPlan.GROUND) && (d <= half + 3)) {
                    // No trees, plants or objects on the path or overhanging it
                    clearVegetation(dimension, wx, wy);
                }
                switch (types[i]) {
                    case PathPlan.GROUND:
                        if (d <= half) {
                            dimension.setHeightAt(wx, wy, target);
                            dimension.setTerrainAt(wx, wy, surfaceAt(settings, dimension.getTerrainAt(wx, wy), wx, wy, d, half));
                            if (dimension.getWaterLevelAt(wx, wy) > target) {
                                dimension.setWaterLevelAt(wx, wy, (int) Math.floor(target));
                            }
                        } else {
                            final float current = dimension.getHeightAt(wx, wy);
                            final float t = smoothstep((d - half) / falloff);
                            dimension.setHeightAt(wx, wy, target + (current - target) * t);
                        }
                        break;
                    case PathPlan.BRIDGE:
                        if (d <= half) {
                            dimension.setBitLayerValueAt(bridgeLayer, wx, wy, true);
                        }
                        break;
                    case PathPlan.TUNNEL:
                        if (d <= half) {
                            dimension.setBitLayerValueAt(tunnelLayer, wx, wy, true);
                        }
                        break;
                }
            }
        }

        // What lines the sides, on the ground on either side of the path
        if (settings.getSides() != PathSettings.Sides.NONE) {
            createdLayers.addAll(lineSides(dimension, settings.getSides(), layerNumber, distances, nearest, types, halves, x0, y0, width, height));
        }

        // The floor dimensions of the bridges and tunnels, which hold the height and material of the deck or floor
        if (bridgeLayer != null) {
            // Loose materials such as gravel or sand cannot hang in the air: such paths get a wooden deck on wooden
            // piers, others a deck of their own material on stone piers
            final boolean wooden = settings.isAdaptiveSurface() || isLoose(settings.getMaterial());
            final Terrain deck = wooden ? installOr(MaterialGroup.WOODEN_DECK, Terrain.COBBLESTONE) : settings.getMaterial();
            final Dimension floor = createFloorDimension(dimension, bridgeLayer, FLOATING_FLOOR, deck, averageProfile(plan, PathPlan.BRIDGE));
            bridgeLayer.updateFloorDimension(dimension, bridgeLayer.getName() + " Deck");
            shapeFloor(floor, plan, PathPlan.BRIDGE, settings, distances, nearest, x0, y0, width, height, maxHalf, true, wooden ? deck : null);
            final TunnelLayer piers = createPiers(dimension, plan, layerNumber, distances, nearest, halves, x0, y0, width, height,
                    wooden ? installOr(MaterialGroup.WOODEN_PIERS, Terrain.COBBLESTONE) : installOr(MaterialGroup.STONE_BRICKS, Terrain.COBBLESTONE));
            if (piers != null) {
                createdLayers.add(piers);
            }
        }
        if (tunnelLayer != null) {
            final Dimension floor = createFloorDimension(dimension, tunnelLayer, CAVE_FLOOR, settings.getMaterial(), averageProfile(plan, PathPlan.TUNNEL));
            tunnelLayer.updateFloorDimension(dimension, tunnelLayer.getName() + " Floor");
            shapeFloor(floor, plan, PathPlan.TUNNEL, settings, distances, nearest, x0, y0, width, height, maxHalf, false, null);
        }
        if (progressReceiver != null) {
            progressReceiver.setProgress(1.0f);
        }
        return createdLayers;
    }

    /**
     * Half the width of the path at each of its points: as set, or, for a dynamic width, varying with the land around
     * the set width: narrower across steep hillsides and on bridges and in tunnels, wider on flat open ground, with a
     * gentle variation, and smoothed so that the width never changes abruptly.
     */
    static float[] halfWidths(Dimension dimension, PathPlan plan, PathSettings settings) {
        final List<double[]> points = plan.getPoints();
        final byte[] types = plan.getTypes();
        final int n = points.size(), width = settings.getWidth();
        float[] widths = new float[n];
        Arrays.fill(widths, width);
        if (settings.isDynamicWidth() && (n > 2)) {
            for (int i = 0; i < n; i++) {
                final double[] before = points.get(Math.max(0, i - 2)), after = points.get(Math.min(n - 1, i + 2)), here = points.get(i);
                double dx = after[0] - before[0], dy = after[1] - before[1];
                final double length = Math.hypot(dx, dy);
                if (length > 0) {
                    dx /= length;
                    dy /= length;
                }
                // The slope of the land across the path
                final double reach = Math.max(3, width);
                final float left = heightAt(dimension, here[0] - dy * reach, here[1] + dx * reach), right = heightAt(dimension, here[0] + dy * reach, here[1] - dx * reach);
                final double cross = (Float.isNaN(left) || Float.isNaN(right)) ? 0 : Math.abs(left - right) / (2 * reach);
                double factor = 1.15 - Math.min(0.5, cross * 1.2);
                if (types[i] != PathPlan.GROUND) {
                    factor = Math.min(factor, 0.85);
                }
                factor += 0.08 * Math.sin(i * 0.045) + 0.05 * Math.sin(i * 0.13 + 1.3);
                widths[i] = (float) Math.max(2, width * factor);
            }
            // Smooth twice with a moving average, so that the width changes gradually
            for (int pass = 0; pass < 2; pass++) {
                final float[] smoothed = new float[n];
                final int window = 12;
                for (int i = 0; i < n; i++) {
                    float sum = 0;
                    int count = 0;
                    for (int k = Math.max(0, i - window); k <= Math.min(n - 1, i + window); k++) {
                        sum += widths[k];
                        count++;
                    }
                    smoothed[i] = sum / count;
                }
                widths = smoothed;
            }
        }
        final float[] halves = new float[n];
        for (int i = 0; i < n; i++) {
            halves[i] = widths[i] / 2;
        }
        return halves;
    }

    private static float heightAt(Dimension dimension, double x, double y) {
        final int bx = (int) Math.floor(x), by = (int) Math.floor(y);
        return dimension.isTilePresent(bx >> TILE_SIZE_BITS, by >> TILE_SIZE_BITS) ? dimension.getHeightAt(bx, by) : Float.NaN;
    }

    /**
     * Remove the trees, plants and custom objects at a block.
     */
    private static void clearVegetation(Dimension dimension, int x, int y) {
        final Tile tile = dimension.getTileForEditing(x >> TILE_SIZE_BITS, y >> TILE_SIZE_BITS);
        if (tile == null) {
            return;
        }
        final int xInTile = x & TILE_SIZE_MASK, yInTile = y & TILE_SIZE_MASK;
        for (Layer layer: new ArrayList<>(tile.getLayers())) {
            if ((layer instanceof TreeLayer) || (layer instanceof Bo2Layer) || (layer instanceof PlantLayer)) {
                if ((layer.getDataSize() == Layer.DataSize.BIT) || (layer.getDataSize() == Layer.DataSize.BIT_PER_CHUNK)) {
                    tile.setBitLayerValue(layer, xInTile, yInTile, false);
                } else {
                    tile.setLayerValue(layer, xInTile, yInTile, 0);
                }
            }
        }
    }

    /**
     * Line the sides of the ground parts of a path with fences, hedges, walls, lanterns or flowers: custom object
     * layers of single blocks (or short columns), placed on every marked block, which connect to each other.
     */
    private static List<CustomLayer> lineSides(Dimension dimension, PathSettings.Sides sides, int layerNumber, float[] distances, int[] nearest, byte[] types, float[] halves, int x0, int y0, int width, int height) {
        final Material fence = Material.get("minecraft:oak_fence"), leaves = Material.get("minecraft:oak_leaves", "distance", "1", "persistent", "true", "waterlogged", "false");
        final Bo2Layer main, other;
        switch (sides) {
            case FENCE:
                main = sideLayer("Path " + layerNumber + " fence", new Color(0x9a6b3f), sideObject("Fence", fence));
                other = null;
                break;
            case FENCE_AND_HEDGE:
                main = sideLayer("Path " + layerNumber + " fence", new Color(0x9a6b3f), sideObject("Fence", fence));
                other = sideLayer("Path " + layerNumber + " hedge", new Color(0x3f7f2f), sideObject("Hedge", leaves));
                break;
            case HEDGE:
                main = sideLayer("Path " + layerNumber + " hedge", new Color(0x3f7f2f), sideObject("Hedge", leaves));
                other = null;
                break;
            case STONE_WALL:
                main = sideLayer("Path " + layerNumber + " wall", new Color(0x8a8a8a), sideObject("Wall", Material.get("minecraft:cobblestone_wall")));
                other = null;
                break;
            case LANTERN_POSTS:
                main = sideLayer("Path " + layerNumber + " lanterns", new Color(0xf5b642), sideObject("Lantern post", fence, Material.get("minecraft:lantern", "hanging", "false", "waterlogged", "false")));
                other = null;
                break;
            case FLOWERS:
                main = sideLayer("Path " + layerNumber + " flowers", new Color(0xe07ab8),
                        sideObject("Poppy", Material.get("minecraft:poppy")), sideObject("Dandelion", Material.get("minecraft:dandelion")),
                        sideObject("Cornflower", Material.get("minecraft:cornflower")), sideObject("Oxeye daisy", Material.get("minecraft:oxeye_daisy")),
                        sideObject("Azure bluet", Material.get("minecraft:azure_bluet")), sideObject("Allium", Material.get("minecraft:allium")));
                other = null;
                break;
            default:
                return new ArrayList<>();
        }
        boolean mainUsed = false, otherUsed = false;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int j = y * width + x;
                final int i = nearest[j];
                if (types[i] != PathPlan.GROUND) {
                    continue;
                }
                final float d = distances[j], half = halves[i];
                if ((d < half + 0.5f) || (d >= half + 1.9f)) {
                    continue;
                }
                final int wx = x0 + x, wy = y0 + y;
                if ((! dimension.isTilePresent(wx >> TILE_SIZE_BITS, wy >> TILE_SIZE_BITS)) || (dimension.getWaterLevelAt(wx, wy) > dimension.getIntHeightAt(wx, wy))) {
                    continue;
                }
                final Bo2Layer layer;
                if (sides == PathSettings.Sides.FENCE_AND_HEDGE) {
                    // Runs of fence alternating with runs of hedge
                    layer = (((i / 7) & 1) == 0) ? main : other;
                } else if (sides == PathSettings.Sides.LANTERN_POSTS) {
                    // A post every few blocks
                    if ((i % 9) != 0) {
                        continue;
                    }
                    layer = main;
                } else if (sides == PathSettings.Sides.FLOWERS) {
                    if (((wx * 0x9E3779B1) ^ (wy * 0x85EBCA6B)) % 5 == 0) {
                        continue;
                    }
                    layer = main;
                } else {
                    layer = main;
                }
                dimension.setLayerValueAt(layer, wx, wy, 15);
                if (layer == main) {
                    mainUsed = true;
                } else {
                    otherUsed = true;
                }
            }
        }
        final List<CustomLayer> layers = new ArrayList<>();
        if (mainUsed) {
            layers.add(main);
        }
        if (otherUsed) {
            layers.add(other);
        }
        return layers;
    }

    /**
     * A custom object layer which places one of its objects on every block where it is set.
     */
    private static Bo2Layer sideLayer(String name, Color colour, WPObject... objects) {
        final Bo2Layer layer = new Bo2Layer(new Bo2ObjectTube(name, Arrays.asList(objects)), "Placed along the sides of a path", colour);
        layer.setGridX(1);
        layer.setGridY(1);
        layer.setRandomDisplacement(0);
        layer.setDensity(1);
        return layer;
    }

    /**
     * An object of a single column of blocks, which connects to the same blocks next to it (as fences and walls do).
     */
    private static WPObject sideObject(String name, Material... column) {
        final GenericObject object = new GenericObject(name, 1, 1, column.length, column);
        object.setAttribute(WPObject.ATTRIBUTE_RANDOM_ROTATION, false);
        object.setAttribute(WPObject.ATTRIBUTE_CONNECT_BLOCKS, true);
        object.setAttribute(WPObject.ATTRIBUTE_LEAF_DECAY_MODE, WPObject.LEAF_DECAY_OFF);
        return object;
    }

    // Routing

    /**
     * Find a route from {@code a} to {@code b}: straight for a curvature of zero, otherwise the cheapest route on a
     * grid, where climbing (and, depending on the curvature, straying from the straight line) costs extra.
     */
    static List<double[]> route(Dimension dimension, Point a, Point b, PathSettings settings) {
        final List<double[]> straight = new ArrayList<>();
        straight.add(new double[] {a.x + 0.5, a.y + 0.5});
        straight.add(new double[] {b.x + 0.5, b.y + 0.5});
        final double curvature = settings.getCurvature() / 100.0;
        final double length = a.distance(b);
        if ((curvature <= 0) || (length < 4)) {
            return straight;
        }
        // A grid of cells over the area around the two points, coarse enough to stay fast for long paths
        final int cell = Math.max(1, (int) Math.ceil(length / 300));
        final double margin = 24 + length * (0.15 + 0.6 * curvature);
        final int gx0 = (int) Math.floor(Math.min(a.x, b.x) - margin), gy0 = (int) Math.floor(Math.min(a.y, b.y) - margin);
        final int nx = (int) Math.ceil((Math.abs(a.x - b.x) + 2 * margin) / cell) + 1, ny = (int) Math.ceil((Math.abs(a.y - b.y) + 2 * margin) / cell) + 1;
        final float[] heights = new float[nx * ny];
        final boolean[] wet = new boolean[nx * ny], blocked = new boolean[nx * ny];
        for (int cy = 0; cy < ny; cy++) {
            for (int cx = 0; cx < nx; cx++) {
                final int x = gx0 + cx * cell + cell / 2, y = gy0 + cy * cell + cell / 2;
                final float h = height(dimension, x, y);
                final int i = cy * nx + cx;
                if (h == -Float.MAX_VALUE) {
                    blocked[i] = true;
                } else {
                    heights[i] = h;
                    wet[i] = dimension.getWaterLevelAt(x, y) > h;
                }
            }
        }
        final int start = cellIndex(a, gx0, gy0, cell, nx, ny), goal = cellIndex(b, gx0, gy0, cell, nx, ny);
        if ((start < 0) || (goal < 0) || blocked[start] || blocked[goal]) {
            return straight;
        }
        final double maxGrade = settings.getMaxGrade();
        // How much climbing costs (relative to distance): nothing at curvature 0, a lot at 100
        final double climbWeight = 2 + 30 * curvature * curvature;
        // How much straying from the straight line costs: a lot at low curvature, nothing at 100
        final double strayWeight = 2.0 * (1 - curvature) * (1 - curvature);
        final double ax = (a.x - gx0) / (double) cell, ay = (a.y - gy0) / (double) cell, bx = (b.x - gx0) / (double) cell, by = (b.y - gy0) / (double) cell;
        final double lineLength = Math.max(1e-6, Math.hypot(bx - ax, by - ay));

        final double[] costs = new double[nx * ny];
        Arrays.fill(costs, Double.MAX_VALUE);
        final int[] from = new int[nx * ny];
        Arrays.fill(from, -1);
        final boolean[] closed = new boolean[nx * ny];
        final PriorityQueue<double[]> open = new PriorityQueue<>((p, q) -> Double.compare(p[0], q[0]));
        costs[start] = 0;
        open.add(new double[] {heuristic(start, goal, nx), start});
        final int[] dxs = {1, 1, 0, -1, -1, -1, 0, 1}, dys = {0, 1, 1, 1, 0, -1, -1, -1};
        while (! open.isEmpty()) {
            final int current = (int) open.poll()[1];
            if (closed[current]) {
                continue;
            }
            if (current == goal) {
                break;
            }
            closed[current] = true;
            final int cx = current % nx, cy = current / nx;
            for (int k = 0; k < 8; k++) {
                final int ncx = cx + dxs[k], ncy = cy + dys[k];
                if ((ncx < 0) || (ncy < 0) || (ncx >= nx) || (ncy >= ny)) {
                    continue;
                }
                final int next = ncy * nx + ncx;
                if (closed[next] || blocked[next]) {
                    continue;
                }
                final double step = ((k % 2) == 0) ? 1.0 : Math.sqrt(2);
                final double distance = step * cell;
                final double climb = Math.abs(heights[next] - heights[current]);
                final double grade = climb / distance;
                double climbCost = climbWeight * (climb + 8 * distance * Math.max(0, grade - maxGrade));
                if (settings.isTunnels()) {
                    // Through a hill is always possible, at a price
                    climbCost = Math.min(climbCost, distance * 6);
                }
                double waterCost = 0;
                if (wet[next]) {
                    waterCost = distance * (settings.isBridges() ? 1.5 : 6);
                }
                // Distance of the next cell from the straight line, relative to the length of the line
                final double stray = Math.abs((bx - ax) * (ay - ncy) - (ax - ncx) * (by - ay)) / lineLength;
                final double strayCost = strayWeight * distance * stray / Math.max(4, lineLength * 0.08);
                final double cost = costs[current] + distance + climbCost + waterCost + strayCost;
                if (cost < costs[next]) {
                    costs[next] = cost;
                    from[next] = current;
                    open.add(new double[] {cost + heuristic(next, goal, nx) * cell, next});
                }
            }
        }
        if (from[goal] < 0) {
            return straight;
        }
        final List<double[]> result = new ArrayList<>();
        for (int i = goal; i >= 0; i = from[i]) {
            result.add(0, new double[] {gx0 + (i % nx) * cell + cell / 2.0, gy0 + (i / nx) * cell + cell / 2.0});
            if (i == start) {
                break;
            }
        }
        // Start and end exactly at the chosen points
        result.set(0, straight.get(0));
        result.set(result.size() - 1, straight.get(1));
        return result;
    }

    private static int cellIndex(Point p, int gx0, int gy0, int cell, int nx, int ny) {
        final int cx = (p.x - gx0) / cell, cy = (p.y - gy0) / cell;
        return ((cx >= 0) && (cy >= 0) && (cx < nx) && (cy < ny)) ? (cy * nx + cx) : -1;
    }

    private static double heuristic(int cell, int goal, int nx) {
        return Math.hypot((cell % nx) - (goal % nx), (cell / nx) - (goal / nx));
    }

    /**
     * Round the corners of a polyline (Chaikin's algorithm), keeping the end points.
     */
    static List<double[]> smooth(List<double[]> points, int iterations) {
        List<double[]> result = points;
        for (int k = 0; k < iterations; k++) {
            if (result.size() < 3) {
                return result;
            }
            final List<double[]> next = new ArrayList<>();
            next.add(result.get(0));
            for (int i = 0; i < result.size() - 1; i++) {
                final double[] p = result.get(i), q = result.get(i + 1);
                next.add(new double[] {p[0] * 0.75 + q[0] * 0.25, p[1] * 0.75 + q[1] * 0.25});
                next.add(new double[] {p[0] * 0.25 + q[0] * 0.75, p[1] * 0.25 + q[1] * 0.75});
            }
            next.add(result.get(result.size() - 1));
            result = next;
        }
        return result;
    }

    /**
     * Points along a polyline at a regular spacing.
     */
    static List<double[]> resample(List<double[]> points, double spacing) {
        final List<double[]> result = new ArrayList<>();
        result.add(points.get(0));
        double carry = 0;
        for (int i = 0; i < points.size() - 1; i++) {
            final double[] p = points.get(i), q = points.get(i + 1);
            final double length = Math.hypot(q[0] - p[0], q[1] - p[1]);
            double t = spacing - carry;
            while (t <= length) {
                result.add(new double[] {p[0] + (q[0] - p[0]) * t / length, p[1] + (q[1] - p[1]) * t / length});
                t += spacing;
            }
            carry = length - (t - spacing);
        }
        final double[] last = points.get(points.size() - 1);
        if (Math.hypot(last[0] - result.get(result.size() - 1)[0], last[1] - result.get(result.size() - 1)[1]) > spacing * 0.3) {
            result.add(last);
        }
        return result;
    }

    // Height profile

    /**
     * The height of the path along its length: the land (or the water surface plus some clearance) smoothed, with
     * valleys spanned when bridges are allowed and hills cut through when tunnels are allowed, and the slope limited.
     */
    static float[] profile(float[] terrain, float[] water, PathSettings settings) {
        final int n = terrain.length;
        final float[] surface = new float[n];
        for (int i = 0; i < n; i++) {
            // Across water the path is at least a block above it (an embankment), or higher for a bridge
            final float clearance = settings.isBridges() ? 4 : 1;
            surface[i] = Float.isNaN(water[i]) ? terrain[i] : Math.max(terrain[i], water[i] + clearance);
        }
        float[] result = movingAverage(surface, Math.max(2, settings.getWidth()));
        final int span = settings.getMaxSpan();
        if (settings.isBridges()) {
            // Closing: fills in dips narrower than the span, which become bridges
            result = minFilter(maxFilter(result, span / 2), span / 2);
        }
        if (settings.isTunnels()) {
            // Opening: removes humps narrower than the span, which become tunnels
            result = maxFilter(minFilter(result, span / 2), span / 2);
        }
        result = movingAverage(result, Math.max(3, settings.getWidth() * 2));
        // Start and end at the land
        result[0] = surface[0];
        result[n - 1] = surface[n - 1];
        limitGrade(result, (float) settings.getMaxGrade());
        return result;
    }

    /**
     * Limit the slope of a profile, in both directions, so that it rises or falls by at most {@code grade} per block.
     */
    static void limitGrade(float[] profile, float grade) {
        final int n = profile.length;
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 1; i < n; i++) {
                profile[i] = Math.max(profile[i - 1] - grade, Math.min(profile[i - 1] + grade, profile[i]));
            }
            for (int i = n - 2; i >= 0; i--) {
                profile[i] = Math.max(profile[i + 1] - grade, Math.min(profile[i + 1] + grade, profile[i]));
            }
        }
    }

    /**
     * Decide, for every point of the path, whether it is on the ground, on a bridge or in a tunnel. Bridges and tunnels
     * shorter than a few blocks are not worth it and stay on the ground.
     */
    static byte[] segments(float[] terrain, float[] water, float[] profile, PathSettings settings) {
        final int n = terrain.length;
        final byte[] types = new byte[n];
        for (int i = 0; i < n; i++) {
            if (settings.isBridges() && ((profile[i] - terrain[i] > 4) || (! Float.isNaN(water[i]) && (profile[i] > water[i])))) {
                types[i] = PathPlan.BRIDGE;
            } else if (settings.isTunnels() && (terrain[i] - profile[i] > settings.getTunnelHeight() + 2)) {
                types[i] = PathPlan.TUNNEL;
            } else {
                types[i] = PathPlan.GROUND;
            }
        }
        // Remove runs which are too short
        int runStart = 0;
        for (int i = 1; i <= n; i++) {
            if ((i == n) || (types[i] != types[runStart])) {
                if ((types[runStart] != PathPlan.GROUND) && ((i - runStart) < 5)) {
                    Arrays.fill(types, runStart, i, PathPlan.GROUND);
                }
                runStart = i;
            }
        }
        return types;
    }

    private static float[] movingAverage(float[] values, int radius) {
        final int n = values.length;
        final float[] result = new float[n];
        double sum = 0;
        int count = 0;
        // Sliding window [i - radius, i + radius]
        for (int i = 0; i < Math.min(n, radius); i++) {
            sum += values[i];
            count++;
        }
        for (int i = 0; i < n; i++) {
            final int add = i + radius, remove = i - radius - 1;
            if (add < n) {
                sum += values[add];
                count++;
            }
            if (remove >= 0) {
                sum -= values[remove];
                count--;
            }
            result[i] = (float) (sum / count);
        }
        return result;
    }

    private static float[] maxFilter(float[] values, int radius) {
        final int n = values.length;
        final float[] result = new float[n];
        for (int i = 0; i < n; i++) {
            float max = -Float.MAX_VALUE;
            for (int j = Math.max(0, i - radius); j <= Math.min(n - 1, i + radius); j++) {
                max = Math.max(max, values[j]);
            }
            result[i] = max;
        }
        return result;
    }

    private static float[] minFilter(float[] values, int radius) {
        final int n = values.length;
        final float[] result = new float[n];
        for (int i = 0; i < n; i++) {
            float min = Float.MAX_VALUE;
            for (int j = Math.max(0, i - radius); j <= Math.min(n - 1, i + radius); j++) {
                min = Math.min(min, values[j]);
            }
            result[i] = min;
        }
        return result;
    }

    // Applying

    /**
     * The surface of the path at a block: the chosen material with the border material scattered through it (more of
     * it towards the edges) in small clumps, or, for an adaptive surface, a mix which suits the land it crosses, with
     * some of that land showing through along the edges.
     *
     * @param surrounding The terrain at the block before the path was laid out.
     */
    static Terrain surfaceAt(PathSettings settings, Terrain surrounding, int x, int y, float distance, float half) {
        final float pick = 0.55f * SurfaceRules.random(x, y, 0x2f1b873) + 0.45f * SurfaceRules.random(x >> 1, y >> 1, 0x51ed270b);
        if (settings.isAdaptiveSurface()) {
            if ((surrounding != null) && (distance > half - 1) && (pick < 0.3f)) {
                return surrounding;
            }
            final Object[] palette = adaptivePalette(surrounding);
            float total = 0;
            for (int i = 1; i < palette.length; i += 2) {
                total += (Integer) palette[i];
            }
            float cumulative = 0;
            for (int i = 0; i < palette.length; i += 2) {
                cumulative += (Integer) palette[i + 1] / total;
                if (pick < cumulative) {
                    return (Terrain) palette[i];
                }
            }
            return (Terrain) palette[0];
        } else if (settings.isBorders() && (settings.getWidth() >= 3)) {
            final float borderShare = (distance > half - 1) ? 0.55f : 0.2f;
            return (pick < borderShare) ? settings.getBorderMaterial() : settings.getMaterial();
        } else {
            return settings.getMaterial();
        }
    }

    /**
     * The blocks of a path through some kind of land, with their weights.
     */
    private static Object[] adaptivePalette(Terrain surrounding) {
        if (surrounding == null) {
            return new Object[] {Terrain.GRASS_PATH, 45, Terrain.PERMADIRT, 30, Terrain.GRAVEL, 15, Terrain.COBBLESTONE, 10};
        }
        switch (surrounding) {
            case SAND: case DESERT: case BEACHES: case BARE_BEACHES: case SANDSTONE:
                return new Object[] {Terrain.SANDSTONE, 40, Terrain.SAND, 25, Terrain.PERMADIRT, 20, Terrain.GRAVEL, 15};
            case RED_SAND: case RED_DESERT: case MESA: case RED_SANDSTONE: case HARDENED_CLAY:
                return new Object[] {Terrain.RED_SANDSTONE, 40, Terrain.RED_SAND, 25, Terrain.PERMADIRT, 20, Terrain.GRAVEL, 15};
            case STONE: case ROCK: case STONE_MIX: case GRANITE: case DIORITE: case ANDESITE: case COBBLESTONE:
            case MOSSY_COBBLESTONE: case TUFF: case DEEPSLATE: case CALCITE: case GRAVEL:
                return new Object[] {Terrain.ANDESITE, 35, Terrain.COBBLESTONE, 25, Terrain.GRAVEL, 25, Terrain.STONE, 15};
            case SNOW: case DEEP_SNOW:
                return new Object[] {Terrain.GRAVEL, 40, Terrain.STONE, 30, Terrain.COBBLESTONE, 30};
            case MUD: case CLAY:
                return new Object[] {Terrain.MUD, 35, Terrain.PERMADIRT, 35, Terrain.GRASS_PATH, 30};
            case NETHERRACK: case NETHERLIKE: case SOUL_SAND: case SOUL_SOIL: case BASALT: case BLACKSTONE:
                return new Object[] {Terrain.BLACKSTONE, 50, Terrain.BASALT, 50};
            default:
                // Grass, dirt, podzol, moss and the like: a trodden earth path
                return new Object[] {Terrain.GRASS_PATH, 45, Terrain.PERMADIRT, 30, Terrain.GRAVEL, 15, Terrain.COBBLESTONE, 10};
        }
    }

    /**
     * Whether a material falls or washes away, so that it cannot form a bridge deck.
     */
    private static boolean isLoose(Terrain terrain) {
        switch (terrain) {
            case GRAVEL: case SAND: case DESERT: case BEACHES: case BARE_BEACHES: case RED_SAND: case RED_DESERT:
            case PERMADIRT: case GRASS_PATH: case DIRT: case MUD: case CLAY: case GRASS: case BARE_GRASS: case PODZOL:
                return true;
            default:
                return false;
        }
    }

    /**
     * The custom terrain type for a mix of blocks, or another terrain type if all custom terrain types are in use.
     */
    private static Terrain installOr(MaterialGroup group, Terrain fallback) {
        final Terrain terrain = group.install();
        return (terrain != null) ? terrain : fallback;
    }

    /**
     * Piers under a bridge: pairs of short pillars every dozen blocks, from just under the deck down into the ground
     * or the water bed, as a floating layer of their own.
     */
    private static TunnelLayer createPiers(Dimension dimension, PathPlan plan, int layerNumber, float[] distances, int[] nearest, float[] halves, int x0, int y0, int width, int height, Terrain material) {
        final byte[] types = plan.getTypes();
        final float[] profile = plan.getProfile();
        // Where the piers stand, and the lowest ground under them
        final List<int[]> blocks = new ArrayList<>();
        int lowest = Integer.MAX_VALUE;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int j = y * width + x;
                final int i = nearest[j];
                if ((types[i] != PathPlan.BRIDGE) || ((i % 12) < 5) || ((i % 12) > 6)) {
                    continue;
                }
                final float d = distances[j], half = halves[i];
                final boolean leg = (half >= 2.5f) ? ((d >= half - 2.0f) && (d < half - 0.5f)) : (d < 1.0f);
                if (! leg) {
                    continue;
                }
                final int wx = x0 + x, wy = y0 + y;
                if (! dimension.isTilePresent(wx >> TILE_SIZE_BITS, wy >> TILE_SIZE_BITS)) {
                    continue;
                }
                blocks.add(new int[] {wx, wy, i});
                lowest = Math.min(lowest, dimension.getIntHeightAt(wx, wy));
            }
        }
        if (blocks.isEmpty()) {
            return null;
        }
        final World2 world = dimension.getWorld();
        final TunnelLayer piers = new TunnelLayer("Bridge " + layerNumber + " piers", FLOATING, new Color(0x6b5a48), world.getPlatform());
        piers.setFloorMode(CUSTOM_DIMENSION);
        piers.setRoofMode(TunnelLayer.Mode.FIXED_HEIGHT);
        piers.setRoofLevel(Math.max(dimension.getMinHeight() + 1, lowest - 3));
        piers.setBottomEdgeShape(SHEER);
        piers.setFloorWallDepth(1);
        piers.setRoofWallDepth(1);
        final Dimension floor = createFloorDimension(dimension, piers, FLOATING_FLOOR, material, averageProfile(plan, PathPlan.BRIDGE) - 2);
        piers.updateFloorDimension(dimension, piers.getName() + " Tops");
        floor.setEventsInhibited(true);
        try {
            for (int[] block: blocks) {
                dimension.setBitLayerValueAt(piers, block[0], block[1], true);
                // The top of a pier is just under the deck
                floor.setHeightAt(block[0], block[1], profile[block[2]] - 2);
                floor.setTerrainAt(block[0], block[1], material);
            }
        } finally {
            floor.setEventsInhibited(false);
        }
        return piers;
    }

    private static boolean borderAt(PathSettings settings, float distance, float half) {
        return settings.isBorders() && (settings.getWidth() >= 3) && (distance > half - 1);
    }

    private static float smoothstep(float t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    private static float height(Dimension dimension, int x, int y) {
        return dimension.getHeightAt(x, y);
    }

    private static int averageProfile(PathPlan plan, byte type) {
        double sum = 0;
        int count = 0;
        for (int i = 0; i < plan.getTypes().length; i++) {
            if (plan.getTypes()[i] == type) {
                sum += plan.getProfile()[i];
                count++;
            }
        }
        return (count > 0) ? (int) Math.round(sum / count) : 64;
    }

    /**
     * Create the floor dimension of a bridge or tunnel layer.
     */
    private static Dimension createFloorDimension(Dimension dimension, TunnelLayer layer, Dimension.Role role, Terrain material, int level) {
        final World2 world = dimension.getWorld();
        final Anchor anchor = dimension.getAnchor();
        int id = 0;
        for (Dimension existing: world.getDimensions()) {
            final Anchor existingAnchor = existing.getAnchor();
            if ((existingAnchor.dim == anchor.dim) && (existingAnchor.role == role) && (existingAnchor.invert == anchor.invert)) {
                id = Math.max(id, existingAnchor.id + 1);
            }
        }
        layer.setFloorDimensionId(id);
        layer.setFloorLevel(level);
        final long seed = dimension.getSeed() + 1 + id;
        final int minHeight = dimension.getMinHeight(), maxHeight = dimension.getMaxHeight();
        final Dimension floor = new Dimension(world, null, seed, TileFactoryFactory.createFlatTileFactory(seed, material, minHeight, maxHeight, level, minHeight, false, false), new Anchor(anchor.dim, role, anchor.invert, id));
        world.addDimension(floor);
        return floor;
    }

    /**
     * Set the height and material of the floor dimension of a bridge or tunnel along the path: the profile of the path,
     * with (for bridges with borders) a parapet one block higher along the edges.
     */
    private static void shapeFloor(Dimension floor, PathPlan plan, byte type, PathSettings settings, float[] distances, int[] nearest, int x0, int y0, int width, int height, float half, boolean parapets, Terrain material) {
        floor.setEventsInhibited(true);
        try {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    final int j = y * width + x;
                    if (distances[j] > half) {
                        continue;
                    }
                    final int i = nearest[j];
                    if (plan.getTypes()[i] != type) {
                        continue;
                    }
                    final int wx = x0 + x, wy = y0 + y;
                    final boolean border = borderAt(settings, distances[j], half);
                    floor.setHeightAt(wx, wy, plan.getProfile()[i] + ((parapets && border) ? 1 : 0));
                    floor.setTerrainAt(wx, wy, (material != null) ? material : (settings.isAdaptiveSurface() ? Terrain.COBBLESTONE : (border ? settings.getBorderMaterial() : settings.getMaterial())));
                }
            }
        } finally {
            floor.setEventsInhibited(false);
        }
    }
}
