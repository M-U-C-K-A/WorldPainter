package org.pepsoft.worldpainter.exporting;

import org.junit.Test;
import org.pepsoft.minecraft.Chunk;
import org.pepsoft.minecraft.MC118AnvilChunk;
import org.pepsoft.minecraft.Material;
import org.pepsoft.util.Box;
import org.pepsoft.worldpainter.platforms.JavaExportSettings;

import java.lang.reflect.Proxy;
import java.util.Random;

import static org.junit.Assert.*;
import static org.pepsoft.worldpainter.DefaultPlugin.JAVA_ANVIL_1_20_5;

/**
 * The faster way of propagating light and leaf distances must leave exactly what the passes over everything leave.
 */
public class BlockPropertiesCalculatorTest {
    @Test
    public void testPropagateGivesTheSameResultAsSecondPass() {
        for (long seed = 1; seed <= 12; seed++) {
            final Chunk[][] expected = createChunks(seed), actual = createChunks(seed);
            final int passes = calculate(expected, false), queued = calculate(actual, true);
            assertEquals(-1, queued);
            assertTrue("seed " + seed + " only took " + passes + " passes", passes > 2);
            for (int chunkX = 0; chunkX < CHUNKS; chunkX++) {
                for (int chunkZ = 0; chunkZ < CHUNKS; chunkZ++) {
                    final Chunk one = expected[chunkX][chunkZ], other = actual[chunkX][chunkZ];
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            for (int y = MIN_HEIGHT; y < MAX_HEIGHT; y++) {
                                final String where = "seed " + seed + " chunk " + chunkX + "," + chunkZ + " at " + x + "," + y + "," + z;
                                assertSame(where, one.getMaterial(x, y, z), other.getMaterial(x, y, z));
                                assertEquals(where, one.getSkyLightLevel(x, y, z), other.getSkyLightLevel(x, y, z));
                                assertEquals(where, one.getBlockLightLevel(x, y, z), other.getBlockLightLevel(x, y, z));
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * @return How many passes it took, or -1 when using the queue.
     */
    private static int calculate(Chunk[][] chunks, boolean queue) {
        final MinecraftWorld world = worldOf(chunks);
        final BlockPropertiesCalculator calculator = new BlockPropertiesCalculator(world, JAVA_ANVIL_1_20_5, null, new JavaExportSettings());
        int lowMark = Integer.MAX_VALUE, highMark = Integer.MIN_VALUE;
        for (Chunk[] column: chunks) {
            for (Chunk chunk: column) {
                final int[] levels = calculator.firstPass(chunk);
                lowMark = Math.min(lowMark, levels[0]);
                highMark = Math.max(highMark, levels[1]);
            }
        }
        // Leave out the outermost chunks, as when exporting a region
        calculator.setDirtyArea(new Box(16, (CHUNKS - 1) * 16, lowMark, Math.min(highMark + 2, MAX_HEIGHT), 16, (CHUNKS - 1) * 16));
        int passes = -1;
        if (queue) {
            calculator.propagate();
        } else {
            passes = 0;
            while (calculator.secondPass()) {
                passes++;
            }
        }
        calculator.finalise();
        return passes;
    }

    /**
     * Land with hills, a lake, caves, overhangs, lights in the dark, and trees with some loose leaves.
     */
    private static Chunk[][] createChunks(long seed) {
        final Random random = new Random(seed);
        final Material stone = Material.get("minecraft:stone"), water = Material.get("minecraft:water"), log = Material.get("minecraft:oak_log"),
                leaves = Material.get("minecraft:oak_leaves"), torch = Material.get("minecraft:torch"), glowstone = Material.get("minecraft:glowstone");
        final int size = CHUNKS * 16;
        final Material[][][] blocks = new Material[size][size][MAX_HEIGHT - MIN_HEIGHT];
        final double phaseX = random.nextDouble() * 10, phaseZ = random.nextDouble() * 10;
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                final int height = (int) (62 + 12 * Math.sin(x * 0.11 + phaseX) * Math.cos(z * 0.09 + phaseZ) + 5 * Math.sin(x * 0.37 + z * 0.29));
                for (int y = MIN_HEIGHT; y < MAX_HEIGHT; y++) {
                    final Material material = (y <= height) ? stone : ((y <= 58) ? water : Material.AIR);
                    blocks[x][z][y - MIN_HEIGHT] = material;
                }
            }
        }
        // Caves, some of them open at the top, and an overhang or two
        for (int i = 0; i < 90; i++) {
            final int cx = random.nextInt(size), cz = random.nextInt(size), cy = 35 + random.nextInt(35), radius = 2 + random.nextInt(6);
            for (int x = Math.max(0, cx - radius); x <= Math.min(size - 1, cx + radius); x++) {
                for (int z = Math.max(0, cz - radius); z <= Math.min(size - 1, cz + radius); z++) {
                    for (int y = cy - radius; y <= cy + radius; y++) {
                        if (((x - cx) * (x - cx) + (z - cz) * (z - cz) + (y - cy) * (y - cy) <= radius * radius) && (blocks[x][z][y - MIN_HEIGHT] == stone)) {
                            blocks[x][z][y - MIN_HEIGHT] = Material.AIR;
                        }
                    }
                }
            }
        }
        // Lights, including in the caves and on the edges of the chunks
        for (int i = 0; i < 160; i++) {
            final int x = random.nextInt(size), z = random.nextInt(size), y = 30 + random.nextInt(50);
            if (blocks[x][z][y - MIN_HEIGHT] == Material.AIR) {
                blocks[x][z][y - MIN_HEIGHT] = random.nextBoolean() ? torch : glowstone;
            }
        }
        // Trees, and leaves without a tree which are too far from anything
        for (int i = 0; i < 40; i++) {
            final int x = 3 + random.nextInt(size - 6), z = 3 + random.nextInt(size - 6);
            int top = MAX_HEIGHT - 1;
            while ((top > MIN_HEIGHT) && (blocks[x][z][top - MIN_HEIGHT] != stone)) {
                top--;
            }
            final boolean trunk = random.nextInt(4) != 0;
            final int crown = top + 3 + random.nextInt(3);
            if (trunk) {
                for (int y = top + 1; y <= crown; y++) {
                    blocks[x][z][y - MIN_HEIGHT] = log;
                }
            }
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    for (int dy = -2; dy <= 2; dy++) {
                        if ((blocks[x + dx][z + dz][crown + dy - MIN_HEIGHT] == Material.AIR) && (random.nextInt(5) != 0)) {
                            blocks[x + dx][z + dz][crown + dy - MIN_HEIGHT] = leaves;
                        }
                    }
                }
            }
        }
        final Chunk[][] chunks = new Chunk[CHUNKS][CHUNKS];
        for (int chunkX = 0; chunkX < CHUNKS; chunkX++) {
            for (int chunkZ = 0; chunkZ < CHUNKS; chunkZ++) {
                final Chunk chunk = new MC118AnvilChunk(chunkX, chunkZ, MIN_HEIGHT, MAX_HEIGHT);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = MIN_HEIGHT; y < MAX_HEIGHT; y++) {
                            final Material material = blocks[chunkX * 16 + x][chunkZ * 16 + z][y - MIN_HEIGHT];
                            if (material != Material.AIR) {
                                chunk.setMaterial(x, y, z, material);
                            }
                        }
                    }
                }
                chunks[chunkX][chunkZ] = chunk;
            }
        }
        return chunks;
    }

    private static MinecraftWorld worldOf(Chunk[][] chunks) {
        return (MinecraftWorld) Proxy.newProxyInstance(MinecraftWorld.class.getClassLoader(), new Class<?>[] {MinecraftWorld.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getChunk":
                case "getChunkForEditing":
                    return chunkAt(chunks, (Integer) args[0], (Integer) args[1]);
                case "getMinHeight":
                    return MIN_HEIGHT;
                case "getMaxHeight":
                    return MAX_HEIGHT;
                case "getMaterialAt": {
                    final Chunk chunk = chunkAt(chunks, ((Integer) args[0]) >> 4, ((Integer) args[1]) >> 4);
                    return (chunk != null) ? chunk.getMaterial(((Integer) args[0]) & 0xf, (Integer) args[2], ((Integer) args[1]) & 0xf) : Material.AIR;
                }
                case "getSkyLightLevel": {
                    final Chunk chunk = chunkAt(chunks, ((Integer) args[0]) >> 4, ((Integer) args[1]) >> 4);
                    return (chunk != null) ? chunk.getSkyLightLevel(((Integer) args[0]) & 0xf, (Integer) args[2], ((Integer) args[1]) & 0xf) : 0;
                }
                case "getBlockLightLevel": {
                    final Chunk chunk = chunkAt(chunks, ((Integer) args[0]) >> 4, ((Integer) args[1]) >> 4);
                    return (chunk != null) ? chunk.getBlockLightLevel(((Integer) args[0]) & 0xf, (Integer) args[2], ((Integer) args[1]) & 0xf) : 0;
                }
                default:
                    throw new UnsupportedOperationException(method.getName());
            }
        });
    }

    private static Chunk chunkAt(Chunk[][] chunks, int chunkX, int chunkZ) {
        return ((chunkX >= 0) && (chunkZ >= 0) && (chunkX < CHUNKS) && (chunkZ < CHUNKS)) ? chunks[chunkX][chunkZ] : null;
    }

    private static final int CHUNKS = 5, MIN_HEIGHT = -64, MAX_HEIGHT = 320;
}
