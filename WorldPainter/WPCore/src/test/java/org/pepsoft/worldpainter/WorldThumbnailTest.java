package org.pepsoft.worldpainter;

import org.junit.Test;
import org.pepsoft.worldpainter.biomeschemes.CustomBiomeManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;

import static org.junit.Assert.*;
import static org.pepsoft.worldpainter.DefaultPlugin.JAVA_ANVIL_1_20_5;
import static org.pepsoft.worldpainter.Dimension.Anchor.NORMAL_DETAIL;

public class WorldThumbnailTest {
    @Test
    public void testThumbnailIsSavedAndWorldStillLoads() throws IOException, UnloadableWorldException {
        final long seed = 1;
        final World2 world = new World2(JAVA_ANVIL_1_20_5, -64, 320);
        world.setName("Thumbnail");
        final TileFactory tileFactory = TileFactoryFactory.createNoiseTileFactory(seed, Terrain.GRASS, -64, 320, 58, 62, false, true, 20, 1.0);
        final Dimension dimension = new Dimension(world, "Surface", seed, tileFactory, NORMAL_DETAIL);
        world.addDimension(dimension);
        for (int x = 0; x < 6; x++) {
            for (int y = 0; y < 3; y++) {
                dimension.addTile(tileFactory.createTile(x, y));
            }
        }

        final BufferedImage image = WorldThumbnail.render(dimension, ColourScheme.DEFAULT, new CustomBiomeManager(), new HashSet<>());
        // Six by three tiles of 128 blocks: drawn at one pixel per block, as that is less than the largest size
        assertEquals(768, image.getWidth());
        assertEquals(384, image.getHeight());
        final byte[] jpeg = WorldThumbnail.encode(image);
        assertNotNull(jpeg);
        assertTrue(jpeg.length <= WorldThumbnail.MAX_BYTES);

        final WorldIO worldIO = new WorldIO(world);
        worldIO.setThumbnail(jpeg);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        worldIO.save(out);
        final byte[] file = out.toByteArray();

        assertArrayEquals(jpeg, WorldThumbnail.read(new ByteArrayInputStream(file)));
        final BufferedImage read = ImageIO.read(new ByteArrayInputStream(WorldThumbnail.read(new ByteArrayInputStream(file))));
        assertEquals(image.getWidth(), read.getWidth());

        final WorldIO loader = new WorldIO();
        loader.load(new ByteArrayInputStream(file));
        assertEquals("Thumbnail", loader.getWorld().getName());
        assertEquals(18, loader.getWorld().getDimension(NORMAL_DETAIL).getTileCount());
    }

    @Test
    public void testWorldWithoutThumbnail() throws IOException {
        final World2 world = new World2(JAVA_ANVIL_1_20_5, -64, 320);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        new WorldIO(world).save(out);
        assertNull(WorldThumbnail.read(new ByteArrayInputStream(out.toByteArray())));
    }
}
