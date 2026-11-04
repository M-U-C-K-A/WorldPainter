package org.pepsoft.worldpainter.layers.exporters;

import org.junit.Test;
import org.pepsoft.minecraft.Chunk;
import org.pepsoft.minecraft.MC118AnvilChunk;
import org.pepsoft.util.PerlinNoise;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.layers.Resources;
import org.pepsoft.worldpainter.util.PerlinColumnBound;

import java.util.Random;

import static org.junit.Assert.*;
import static org.pepsoft.minecraft.Material.DEEPSLATE_Y;
import static org.pepsoft.minecraft.Material.STONE;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE;
import static org.pepsoft.worldpainter.Constants.TINY_BLOBS;
import static org.pepsoft.worldpainter.DefaultPlugin.JAVA_ANVIL_1_20_5;
import static org.pepsoft.worldpainter.Dimension.Anchor.NORMAL_DETAIL;

public class ResourcesExporterTest {
    @Test
    public void testBoundHolds() {
        final Random random = new Random(1);
        for (int generator = 0; generator < 20; generator++) {
            final PerlinNoise noise = new PerlinNoise(random.nextLong());
            final PerlinColumnBound bound = PerlinColumnBound.create(noise);
            assertNotNull(bound);
            for (int i = 0; i < 20000; i++) {
                final double x = (random.nextInt(20000) - 10000) / TINY_BLOBS, y = (random.nextInt(20000) - 10000) / TINY_BLOBS, z = (random.nextInt(384) - 64) / TINY_BLOBS;
                bound.setColumn(x, y);
                bound.setCell((int) Math.floor(z));
                final float value = noise.getPerlinNoise(x, y, z);
                assertTrue(value <= bound.getCellMax());
                assertTrue(value <= bound.getMax(z));
                // And close to it, or it would not save much
                assertEquals(value, bound.getMax(z), 1e-3f);
            }
        }
    }

    @Test
    public void testOresAreTheSameWithBounds() {
        final World2 world = new World2(JAVA_ANVIL_1_20_5, -64, 320);
        final TileFactory tileFactory = TileFactoryFactory.createNoiseTileFactory(5, Terrain.GRASS, -64, 320, 58, 62, false, true, 60, 1.0);
        final Dimension dimension = new Dimension(world, "Surface", 5, tileFactory, NORMAL_DETAIL);
        world.addDimension(dimension);
        final Random random = new Random(2);
        for (int tileX = -1; tileX <= 0; tileX++) {
            final Tile tile = tileFactory.createTile(tileX, 0);
            // Every amount of resources, in patches
            for (int x = 0; x < TILE_SIZE; x++) {
                for (int y = 0; y < TILE_SIZE; y++) {
                    tile.setLayerValue(Resources.INSTANCE, x, y, ((x >> 3) + (y >> 3) + random.nextInt(2)) % 16);
                }
            }
            dimension.addTile(tile);
        }
        // The default settings have random seeds, so both exporters need the same ones
        final ExporterSettings settings = ResourcesExporter.ResourcesExporterSettings.defaultSettings(JAVA_ANVIL_1_20_5, NORMAL_DETAIL, -64, 320);
        final ResourcesExporter withBounds = new ResourcesExporter(dimension, JAVA_ANVIL_1_20_5, settings, true), without = new ResourcesExporter(dimension, JAVA_ANVIL_1_20_5, settings, false);
        int ores = 0;
        for (Tile tile: dimension.getTiles()) {
            for (int chunkX = 0; chunkX < 8; chunkX++) {
                for (int chunkZ = 0; chunkZ < 8; chunkZ++) {
                    final Chunk expected = createChunk(tile, chunkX, chunkZ), actual = createChunk(tile, chunkX, chunkZ);
                    without.render(tile, expected);
                    withBounds.render(tile, actual);
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            for (int y = -64; y < 320; y++) {
                                assertSame(expected.getMaterial(x, y, z), actual.getMaterial(x, y, z));
                                if ((expected.getMaterial(x, y, z) != STONE) && (expected.getMaterial(x, y, z) != DEEPSLATE_Y) && (expected.getMaterial(x, y, z) != org.pepsoft.minecraft.Material.AIR)) {
                                    ores++;
                                }
                            }
                        }
                    }
                }
            }
        }
        assertTrue("ores: " + ores, ores > 10000);
    }

    private static Chunk createChunk(Tile tile, int chunkX, int chunkZ) {
        final Chunk chunk = new MC118AnvilChunk((tile.getX() << 3) + chunkX, (tile.getY() << 3) + chunkZ, -64, 320);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                final int height = tile.getIntHeight((chunkX << 4) + x, (chunkZ << 4) + z);
                for (int y = -64; y <= height; y++) {
                    chunk.setMaterial(x, y, z, (y < 0) ? DEEPSLATE_Y : STONE);
                }
            }
        }
        return chunk;
    }
}
