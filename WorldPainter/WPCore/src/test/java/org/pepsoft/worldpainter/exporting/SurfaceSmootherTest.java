package org.pepsoft.worldpainter.exporting;

import org.junit.Test;
import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.*;

import java.awt.Point;
import java.awt.Rectangle;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.pepsoft.minecraft.Constants.*;
import static org.pepsoft.minecraft.Material.*;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE;
import static org.pepsoft.worldpainter.DefaultPlugin.JAVA_ANVIL_1_20_5;
import static org.pepsoft.worldpainter.Dimension.Anchor.NORMAL_DETAIL;

public class SurfaceSmootherTest {
    /**
     * A slope of one block every two columns goes up by half blocks with slabs: 61, 61.5, 62, 62.5...
     */
    @Test
    public void testGentleStoneSlopeGoesUpByHalfBlocks() {
        final Fixture fixture = new Fixture(x -> 60 + x * 0.5f, STONE);
        assertEquals(16, fixture.smooth());
        for (int x = 0; x < 32; x++) {
            assertEquals("column " + x, 61 + x * 0.5f, fixture.top(x), 0.0f);
        }
        assertSame(Material.get(MC_STONE + "_slab", MC_TYPE, "bottom", MC_WATERLOGGED, false), fixture.world.get(1, 61));
    }

    /**
     * Any precise height ends up at the nearest half block, whether it was rounded up or down to the block.
     */
    @Test
    public void testNearestHalfBlock() {
        final Fixture fixture = new Fixture(x -> 60 + x * 0.0625f, COBBLESTONE);
        fixture.smooth();
        for (int x = 0; x < 32; x++) {
            final float height = 60 + x * 0.0625f;
            assertEquals("column " + x, Math.round(height * 2) / 2.0f + 1, fixture.top(x), 0.0f);
        }
    }

    /**
     * Snow goes by eighths of a block, with snow layers.
     */
    @Test
    public void testSnowGoesByEighths() {
        final Fixture fixture = new Fixture(x -> 60 + x * 0.125f, SNOW_BLOCK);
        fixture.smooth();
        for (int x = 0; x < 32; x++) {
            assertEquals("column " + x, 61 + x * 0.125f, fixture.top(x), 0.0f);
        }
    }

    /**
     * Rock with a layer of snow on it (Snow on Rock, the Frost layer) follows the same slope as snow blocks, with at
     * least one layer of snow.
     */
    @Test
    public void testSnowOnRockGoesByEighths() {
        final Fixture fixture = new Fixture(x -> 60 + x * 0.125f, STONE);
        for (int x = 0; x < 32; x++) {
            fixture.world.set(x, Math.round(60 + x * 0.125f) + 1, SNOW);
        }
        fixture.smooth();
        for (int x = 0; x < 32; x++) {
            final float expected = 61 + x * 0.125f + (((x % 8) == 0) ? 0.125f : 0.0f);
            assertEquals("column " + x, expected, fixture.top(x), 0.0f);
            assertTrue(fixture.world.get(x, (int) Math.floor(expected - 0.01f)).isNamed(MC_SNOW));
        }
    }

    /**
     * Snow layers are not put on ice, where Minecraft does not keep them.
     */
    @Test
    public void testNoSnowLayersOnIce() {
        final Fixture fixture = new Fixture(x -> 60.75f, SNOW_BLOCK);
        fixture.world.set(0, 60, PACKED_ICE);
        fixture.world.set(1, 60, ICE);
        fixture.smooth();
        assertSame(SNOW_BLOCK, fixture.world.get(0, 61));
        assertSame(SNOW_BLOCK, fixture.world.get(1, 61));
        assertTrue(fixture.world.get(2, 61).isNamed(MC_SNOW));
    }

    /**
     * Grass has no slab, and what stands on the land (here a flower) is left alone.
     */
    @Test
    public void testLeavesOtherBlocksAlone() {
        final Fixture grass = new Fixture(x -> 60 + x * 0.5f, GRASS_BLOCK);
        assertEquals(0, grass.smooth());
        final Fixture flowers = new Fixture(x -> 60 + x * 0.5f, STONE);
        for (int x = 0; x < 32; x++) {
            flowers.world.set(x, Math.round(60 + x * 0.5f) + 1, DANDELION);
        }
        assertEquals(0, flowers.smooth());
    }

    /**
     * Under water the slabs are waterlogged.
     */
    @Test
    public void testWaterloggedUnderWater() {
        final Fixture fixture = new Fixture(x -> 60 + x * 0.5f, STONE);
        for (int x = 0; x < 32; x++) {
            for (int z = Math.round(60 + x * 0.5f) + 1; z <= 80; z++) {
                fixture.world.set(x, z, STATIONARY_WATER);
            }
        }
        fixture.smooth();
        final Material slab = fixture.world.get(1, 61);
        assertEquals(MC_STONE + "_slab", slab.name);
        assertTrue(slab.is(WATERLOGGED));
    }

    /**
     * The exporter of the Half Blocks layer only builds half blocks where the layer is painted.
     */
    @Test
    public void testOnlyWhereTheLayerIs() {
        final Fixture fixture = new Fixture(x -> 60 + x * 0.5f, STONE);
        final Tile tile = fixture.dimension.getTile(0, 0);
        for (int x = 0; x < 16; x++) {
            tile.setBitLayerValue(org.pepsoft.worldpainter.layers.HalfBlocks.INSTANCE, x, 0, true);
        }
        new org.pepsoft.worldpainter.layers.exporters.HalfBlocksExporter(fixture.dimension, JAVA_ANVIL_1_20_5, null)
                .addFeatures(new Rectangle(-16, -16, 64, 33), new Rectangle(0, 0, 32, 1), fixture.world.proxy());
        for (int x = 0; x < 32; x++) {
            final float expected = (x < 16) ? (61 + x * 0.5f) : (Math.round(60 + x * 0.5f) + 1);
            assertEquals("column " + x, expected, fixture.top(x), 0.0f);
        }
    }

    interface Heights {
        float at(int x);
    }

    static final class Fixture {
        Fixture(Heights heights, Material surface) {
            final World2 world2 = new World2(JAVA_ANVIL_1_20_5, -64, 320);
            final TileFactory tileFactory = TileFactoryFactory.createFlatTileFactory(1, Terrain.STONE, -64, 320, 60, 0, false, false);
            dimension = new Dimension(world2, "Surface", 1, tileFactory, NORMAL_DETAIL);
            world2.addDimension(dimension);
            final Tile tile = tileFactory.createTile(0, 0);
            for (int x = 0; x < TILE_SIZE; x++) {
                for (int y = 0; y < TILE_SIZE; y++) {
                    tile.setHeight(x, y, heights.at(x));
                }
            }
            dimension.addTile(tile);
            for (int x = 0; x < 32; x++) {
                final int top = Math.round(heights.at(x));
                for (int z = 50; z <= top; z++) {
                    world.set(x, z, (z == top) ? surface : STONE);
                }
            }
        }

        int smooth() {
            return new SurfaceSmoother(dimension, JAVA_ANVIL_1_20_5).smooth(world.proxy(), new Rectangle(0, 0, 32, 1));
        }

        /**
         * The height of the top of the land, in blocks.
         */
        float top(int x) {
            for (int z = 100; z >= 50; z--) {
                final Material material = world.get(x, z);
                if (material.air || material.isNamed(MC_WATER)) {
                    continue;
                }
                if (material.isNamed(MC_SNOW)) {
                    return z + material.getProperty(LAYERS) / 8.0f;
                } else if (material.name.endsWith("_slab")) {
                    return z + 0.5f;
                } else {
                    return z + 1;
                }
            }
            throw new AssertionError("No land in column " + x);
        }

        final Dimension dimension;
        final Blocks world = new Blocks();
    }

    /**
     * A row of columns of blocks, at y = 0.
     */
    static final class Blocks {
        Material get(int x, int z) {
            return blocks.getOrDefault(new Point(x, z), AIR);
        }

        void set(int x, int z, Material material) {
            blocks.put(new Point(x, z), material);
        }

        MinecraftWorld proxy() {
            return (MinecraftWorld) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {MinecraftWorld.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getMaterialAt":
                        return get((Integer) args[0], (Integer) args[2]);
                    case "setMaterialAt":
                        set((Integer) args[0], (Integer) args[2], (Material) args[3]);
                        return null;
                    case "getMinHeight":
                        return -64;
                    case "getMaxHeight":
                        return 320;
                    default:
                        throw new UnsupportedOperationException(method.getName());
                }
            });
        }

        private final Map<Point, Material> blocks = new HashMap<>();
    }
}
