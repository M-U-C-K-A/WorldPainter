package org.pepsoft.worldpainter;

import org.junit.Test;
import org.pepsoft.worldpainter.TileRenderer.ViewMode;
import org.pepsoft.worldpainter.layers.Frost;
import org.pepsoft.worldpainter.selection.SelectionBlock;

import java.awt.*;
import java.awt.image.BufferedImage;

import static org.junit.Assert.*;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE;

public class TileRendererViewModeTest {
    /**
     * The height runs from the bottom of the ramp (green) on low land to the top of it (light) on high land.
     */
    @Test
    public void testHeightFollowsTheRamp() {
        final Dimension dimension = slope();
        final BufferedImage image = render(dimension, ViewMode.HEIGHT);
        final int low = image.getRGB(2, 64) & 0xffffff, high = image.getRGB(125, 64) & 0xffffff;
        assertTrue("low land is green: " + Integer.toHexString(low), green(low) > red(low));
        assertTrue("high land is lighter: " + Integer.toHexString(high), brightness(high) > brightness(low) + 100);
    }

    /**
     * Flat land is blue and a steep slope red.
     */
    @Test
    public void testSlope() {
        final Dimension dimension = TestData.createDimension(new Rectangle(0, 0, TILE_SIZE, TILE_SIZE), 70);
        for (int x = 64; x < TILE_SIZE; x++) {
            for (int y = 0; y < TILE_SIZE; y++) {
                // 63°
                dimension.setHeightAt(x, y, 70 + (x - 64) * 2);
            }
        }
        final BufferedImage image = render(dimension, ViewMode.SLOPE);
        final int flat = image.getRGB(20, 64) & 0xffffff, steep = image.getRGB(100, 64) & 0xffffff;
        assertTrue("flat land is blue: " + Integer.toHexString(flat), (blue(flat) > red(flat)) && (blue(flat) > green(flat)));
        assertTrue("steep land is red: " + Integer.toHexString(steep), (red(steep) > blue(steep) + 80) && (red(steep) > green(steep)));
    }

    /**
     * In the other modes than the terrain only the selection is drawn over the land; the other layers are not.
     */
    @Test
    public void testOnlyTheSelectionIsDrawn() {
        final Dimension plain = slope(), layered = slope();
        for (int x = 0; x < TILE_SIZE; x++) {
            for (int y = 0; y < 32; y++) {
                layered.setBitLayerValueAt(Frost.INSTANCE, x, y, true);
                layered.setBitLayerValueAt(SelectionBlock.INSTANCE, x, y + 64, true);
            }
        }
        final BufferedImage plainImage = render(plain, ViewMode.RELIEF), layeredImage = render(layered, ViewMode.RELIEF);
        assertEquals("frost is not drawn", plainImage.getRGB(40, 10), layeredImage.getRGB(40, 10));
        assertNotEquals("the selection is drawn", plainImage.getRGB(40, 80), layeredImage.getRGB(40, 80));
        // But the frost shows on the terrain
        assertNotEquals(render(plain, ViewMode.TERRAIN).getRGB(40, 10), render(layered, ViewMode.TERRAIN).getRGB(40, 10));
    }

    /**
     * Land between two heights, rising from west to east: one block for every two.
     */
    private static Dimension slope() {
        final Dimension dimension = TestData.createDimension(new Rectangle(0, 0, TILE_SIZE, TILE_SIZE), 62);
        for (int x = 0; x < TILE_SIZE; x++) {
            for (int y = 0; y < TILE_SIZE; y++) {
                dimension.setHeightAt(x, y, 64 + x / 2f);
                dimension.setWaterLevelAt(x, y, 62);
            }
        }
        return dimension;
    }

    private static BufferedImage render(Dimension dimension, ViewMode mode) {
        final TileRenderer renderer = new TileRenderer(dimension, ColourScheme.DEFAULT, null, 0, false, null);
        renderer.setViewMode(mode);
        final float[] range = TileRenderer.measureLandHeights(dimension);
        renderer.setHeightRange(range[0], range[1]);
        final BufferedImage image = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
        renderer.renderTile(dimension.getTile(0, 0), image, 0, 0);
        return image;
    }

    private static int red(int colour) {
        return (colour >> 16) & 0xff;
    }

    private static int green(int colour) {
        return (colour >> 8) & 0xff;
    }

    private static int blue(int colour) {
        return colour & 0xff;
    }

    private static int brightness(int colour) {
        return red(colour) + green(colour) + blue(colour);
    }
}
