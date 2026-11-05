package org.pepsoft.worldpainter;

import org.junit.Test;
import org.pepsoft.worldpainter.biomeschemes.CustomBiomeManager;

import java.awt.image.BufferedImage;

import static org.junit.Assert.assertEquals;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE;
import static org.pepsoft.worldpainter.DefaultPlugin.JAVA_ANVIL_1_20_5;
import static org.pepsoft.worldpainter.Dimension.Anchor.NORMAL_DETAIL;

/**
 * Zoomed out, the renderer only reads the heights of the blocks it draws and those next to them: the pixels must still
 * be exactly those of the same blocks at 100%.
 */
public class TileRendererZoomTest {
    @Test
    public void testZoomedOutPixelsMatchUnzoomedBlocks() {
        final long seed = 7;
        final World2 world = new World2(JAVA_ANVIL_1_20_5, -64, 320);
        // Hilly land with water in the dips, so that the shading and the water both show
        final TileFactory tileFactory = TileFactoryFactory.createNoiseTileFactory(seed, Terrain.GRASS, -64, 320, 58, 62, false, true, 40, 1.0);
        final Dimension dimension = new Dimension(world, "Surface", seed, tileFactory, NORMAL_DETAIL);
        world.addDimension(dimension);
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                dimension.addTile(tileFactory.createTile(x, y));
            }
        }
        final Tile tile = dimension.getTile(0, 0);
        final BufferedImage full = render(dimension, tile, 0);
        for (int zoom = -1; zoom >= -7; zoom--) {
            final int scale = 1 << -zoom;
            final BufferedImage zoomed = render(dimension, tile, zoom);
            for (int x = 0; x < TILE_SIZE / scale; x++) {
                for (int y = 0; y < TILE_SIZE / scale; y++) {
                    assertEquals("zoom " + zoom + " at " + x + "," + y, full.getRGB(x * scale, y * scale), zoomed.getRGB(x, y));
                }
            }
        }
    }

    private static BufferedImage render(Dimension dimension, Tile tile, int zoom) {
        // Render another tile first, so that whatever the renderer keeps from it cannot make up for heights it skipped
        final TileRenderer renderer = new TileRenderer(dimension, ColourScheme.DEFAULT, new CustomBiomeManager(), zoom, false, null);
        final BufferedImage image = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
        renderer.renderTile(dimension.getTile(1, 1), image, 0, 0);
        renderer.renderTile(tile, image, 0, 0);
        return image;
    }
}
