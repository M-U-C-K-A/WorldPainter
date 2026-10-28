package org.pepsoft.worldpainter.paths;

import org.junit.Test;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.layers.CustomLayer;
import org.pepsoft.worldpainter.layers.tunnel.TunnelLayer;

import java.awt.*;
import java.util.List;

import static org.junit.Assert.*;
import static org.pepsoft.worldpainter.DefaultPlugin.JAVA_ANVIL_1_20_5;
import static org.pepsoft.worldpainter.Dimension.Anchor.NORMAL_DETAIL;
import static org.pepsoft.worldpainter.Dimension.Role.CAVE_FLOOR;
import static org.pepsoft.worldpainter.Dimension.Role.FLOATING_FLOOR;

public class PathApplyTest {
    @Test
    public void testBridgeAndTunnelAreCreated() {
        final long seed = 1;
        final World2 world = new World2(JAVA_ANVIL_1_20_5, -64, 320);
        final TileFactory tileFactory = TileFactoryFactory.createFlatTileFactory(seed, Terrain.GRASS, -64, 320, 70, 62, false, false);
        final Dimension dimension = new Dimension(world, "Surface", seed, tileFactory, NORMAL_DETAIL);
        world.addDimension(dimension);
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 2; y++) {
                dimension.addTile(tileFactory.createTile(x, y));
            }
        }
        // A ravine down to 40 and a hill up to 120 across the route
        for (int y = 0; y < 256; y++) {
            for (int x = 200; x < 230; x++) {
                dimension.setHeightAt(x, y, 40);
            }
            for (int x = 380; x < 420; x++) {
                dimension.setHeightAt(x, y, 120);
            }
        }
        final PathSettings settings = new PathSettings();
        settings.setCurvature(0);
        settings.setBridges(true);
        settings.setTunnels(true);
        final PathPlan plan = PathGenerator.plan(dimension, new Point(20, 128), new Point(600, 128), settings);
        assertTrue("bridge length " + plan.count(PathPlan.BRIDGE), plan.count(PathPlan.BRIDGE) >= 25);
        assertTrue("tunnel length " + plan.count(PathPlan.TUNNEL), plan.count(PathPlan.TUNNEL) >= 30);

        final List<CustomLayer> layers = PathGenerator.apply(dimension, plan, settings, 1);
        assertEquals(2, layers.size());
        final TunnelLayer bridge = (TunnelLayer) layers.get(0), tunnel = (TunnelLayer) layers.get(1);
        assertTrue(dimension.getBitLayerValueAt(bridge, 215, 128));
        assertTrue(dimension.getBitLayerValueAt(tunnel, 400, 128));
        final Dimension deck = world.getDimension(new Dimension.Anchor(0, FLOATING_FLOOR, false, bridge.getFloorDimensionId()));
        final Dimension floor = world.getDimension(new Dimension.Anchor(0, CAVE_FLOOR, false, tunnel.getFloorDimensionId()));
        assertNotNull(deck);
        assertNotNull(floor);
        // The deck spans the ravine at about the height of the land on either side, the tunnel floor is near the land
        // on either side of the hill rather than on top of it
        assertTrue("deck " + deck.getHeightAt(215, 128), deck.getHeightAt(215, 128) > 60);
        assertTrue("tunnel floor " + floor.getHeightAt(400, 128), floor.getHeightAt(400, 128) < 90);
        // The ground part of the path is painted
        assertEquals(Terrain.GRAVEL, dimension.getTerrainAt(100, 128));
    }
}
