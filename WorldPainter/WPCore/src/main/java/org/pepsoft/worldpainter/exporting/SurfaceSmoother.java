package org.pepsoft.worldpainter.exporting;

import com.google.common.collect.ImmutableMap;
import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Platform;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

import static org.pepsoft.minecraft.Constants.*;
import static org.pepsoft.minecraft.Material.LAYERS;
import static org.pepsoft.minecraft.Material.LEVEL;
import static org.pepsoft.minecraft.Material.SNOW;
import static org.pepsoft.worldpainter.DefaultPlugin.ATTRIBUTE_MC_VERSION;

/**
 * Softens the steps of the land when it is exported. The height of the land is known more precisely than to the block,
 * so where it is about half way between two blocks, a slab of the block at the surface (stone, cobblestone, sandstone
 * and the like) makes a half step: gentle slopes go up by half blocks (1, 1.5, 2, 2.5...) instead of by whole blocks
 * every other column (1, 1, 2, 2...). Snow blocks become snow layers, which go by eighths of a block.
 *
 * <p>It is done where the {@link org.pepsoft.worldpainter.layers.HalfBlocks} layer is painted, with the Half Blocks
 * tool. Only the top of the land is changed, in the open air or under water (where the slabs are waterlogged), and only
 * where nothing stands on it, so that plants, trees, objects and the snow of the Frost layer (which is already as thick
 * as the precise height asks) stay as they are. Blocks without a slab, such as grass, dirt and sand, stay whole.
 */
public final class SurfaceSmoother {
    public SurfaceSmoother(Dimension dimension, Platform platform) {
        this.dimension = dimension;
        tuffSlabs = platform.getAttribute(ATTRIBUTE_MC_VERSION).isAtLeast(V_1_21);
    }

    /**
     * Smooth the surface in an area of the world.
     *
     * @param world The world, with the land and the layers already exported.
     * @param area The columns to smooth, in block coordinates.
     * @return The number of blocks changed.
     */
    public int smooth(MinecraftWorld world, Rectangle area) {
        return smooth(world, area, (x, y) -> true);
    }

    /**
     * Smooth the surface of some of the columns in an area of the world.
     *
     * @param world The world, with the land and the layers already exported.
     * @param area The area, in block coordinates.
     * @param columns Which columns of the area to smooth.
     * @return The number of blocks changed.
     */
    public int smooth(MinecraftWorld world, Rectangle area, Columns columns) {
        final int minHeight = world.getMinHeight(), maxHeight = world.getMaxHeight();
        int changed = 0;
        for (int x = area.x; x < area.x + area.width; x++) {
            for (int y = area.y; y < area.y + area.height; y++) {
                if (! columns.includes(x, y)) {
                    continue;
                }
                final float height = dimension.getHeightAt(x, y);
                if (height == -Float.MAX_VALUE) {
                    // No land here
                    continue;
                }
                final int surfaceHeight = Math.round(height);
                if ((surfaceHeight < minHeight) || (surfaceHeight + 2 >= maxHeight)) {
                    continue;
                }
                final Material surface = world.getMaterialAt(x, y, surfaceHeight);
                if (surface.isNamed(MC_SNOW_BLOCK)) {
                    if (smoothSnow(world, x, y, height, surfaceHeight)) {
                        changed++;
                    }
                } else {
                    final Material[] slabs = slabsFor(surface);
                    if ((slabs != null) && smoothWithSlab(world, x, y, height, surfaceHeight, slabs)) {
                        changed++;
                    }
                }
            }
        }
        return changed;
    }

    /**
     * Make a half step where the height is nearer to half way between two blocks than to a whole block: replace the
     * block at the surface with a slab if the height was rounded up, or put a slab on it if it was rounded down.
     */
    private boolean smoothWithSlab(MinecraftWorld world, int x, int y, float height, int surfaceHeight, Material[] slabs) {
        final int halves = Math.round(height * 2);
        if ((halves & 1) == 0) {
            // Nearest to a whole block, which is what is there
            return false;
        }
        // The highest whole block, with the slab on top of it
        final int top = Math.floorDiv(halves - 1, 2);
        final int slabHeight = top + 1;
        if ((surfaceHeight != top) && (surfaceHeight != slabHeight)) {
            return false;
        }
        final Material slab = slabFor(world.getMaterialAt(x, y, slabHeight + 1), world.getMaterialAt(x, y, slabHeight), surfaceHeight == slabHeight, slabs);
        if (slab == null) {
            return false;
        }
        world.setMaterialAt(x, y, slabHeight, slab);
        return true;
    }

    /**
     * Which slab to place, given what is above it and what it replaces, or {@code null} if it should not be placed.
     */
    private static Material slabFor(Material above, Material at, boolean replacing, Material[] slabs) {
        if (replacing) {
            // The surface block becomes a slab; it must have air or water above it
            if (above.air) {
                return slabs[DRY];
            } else if (isStillWater(above)) {
                return slabs[WATERLOGGED];
            } else {
                return null;
            }
        } else {
            // The slab goes on the surface block, where there must be air or water
            if (at.air) {
                return slabs[DRY];
            } else if (isStillWater(at)) {
                return slabs[WATERLOGGED];
            } else {
                return null;
            }
        }
    }

    /**
     * Snow goes by eighths of a block: replace the top snow block with snow layers if the height was rounded up, or put
     * snow layers on it if it was rounded down. Only in the open air, as snow layers cannot be waterlogged.
     */
    private boolean smoothSnow(MinecraftWorld world, int x, int y, float height, int surfaceHeight) {
        final int eighths = Math.round(height * 8);
        final int top = Math.floorDiv(eighths, 8);
        final int layers = eighths - top * 8;
        if (layers == 0) {
            return false;
        }
        final int snowHeight = top + 1;
        if (surfaceHeight == snowHeight) {
            if (! world.getMaterialAt(x, y, snowHeight + 1).air) {
                return false;
            }
        } else if (surfaceHeight == top) {
            if (! world.getMaterialAt(x, y, snowHeight).air) {
                return false;
            }
        } else {
            return false;
        }
        world.setMaterialAt(x, y, snowHeight, SNOW.withProperty(LAYERS, layers));
        return true;
    }

    private static boolean isStillWater(Material material) {
        return material.isNamed(MC_WATER) && (material.getProperty(LEVEL, 0) == 0);
    }

    /**
     * The dry and waterlogged bottom slabs of a block, or {@code null} if it has none.
     */
    private Material[] slabsFor(Material material) {
        final Material[] result = slabs.computeIfAbsent(material, key -> {
            final String slabName = SLABS.get(key.name);
            if ((slabName == null) || ((! tuffSlabs) && TUFF_SLABS.containsKey(key.name))) {
                return NO_SLABS;
            }
            return new Material[] {
                    Material.get(slabName, MC_TYPE, "bottom", MC_WATERLOGGED, false),
                    Material.get(slabName, MC_TYPE, "bottom", MC_WATERLOGGED, true)
            };
        });
        return (result == NO_SLABS) ? null : result;
    }

    /**
     * Which columns to smooth.
     */
    public interface Columns {
        boolean includes(int x, int y);
    }

    private final Dimension dimension;
    private final boolean tuffSlabs;
    private final Map<Material, Material[]> slabs = new HashMap<>();

    private static final int DRY = 0, WATERLOGGED = 1;
    private static final Material[] NO_SLABS = new Material[0];
    private static final org.pepsoft.util.Version V_1_21 = new org.pepsoft.util.Version(1, 21);

    /**
     * Blocks with a slab which only came later (in Minecraft 1.21) than the block itself.
     */
    private static final Map<String, String> TUFF_SLABS = ImmutableMap.of(
            "minecraft:tuff", "minecraft:tuff_slab",
            "minecraft:polished_tuff", "minecraft:polished_tuff_slab",
            "minecraft:tuff_bricks", "minecraft:tuff_brick_slab");

    /**
     * The blocks found at the surface of the land which have a slab of the same look.
     */
    private static final Map<String, String> SLABS = ImmutableMap.<String, String>builder()
            .put("minecraft:stone", "minecraft:stone_slab")
            .put("minecraft:cobblestone", "minecraft:cobblestone_slab")
            .put("minecraft:mossy_cobblestone", "minecraft:mossy_cobblestone_slab")
            .put("minecraft:smooth_stone", "minecraft:smooth_stone_slab")
            .put("minecraft:stone_bricks", "minecraft:stone_brick_slab")
            .put("minecraft:mossy_stone_bricks", "minecraft:mossy_stone_brick_slab")
            .put("minecraft:granite", "minecraft:granite_slab")
            .put("minecraft:polished_granite", "minecraft:polished_granite_slab")
            .put("minecraft:diorite", "minecraft:diorite_slab")
            .put("minecraft:polished_diorite", "minecraft:polished_diorite_slab")
            .put("minecraft:andesite", "minecraft:andesite_slab")
            .put("minecraft:polished_andesite", "minecraft:polished_andesite_slab")
            .put("minecraft:sandstone", "minecraft:sandstone_slab")
            .put("minecraft:cut_sandstone", "minecraft:cut_sandstone_slab")
            .put("minecraft:smooth_sandstone", "minecraft:smooth_sandstone_slab")
            .put("minecraft:red_sandstone", "minecraft:red_sandstone_slab")
            .put("minecraft:cut_red_sandstone", "minecraft:cut_red_sandstone_slab")
            .put("minecraft:smooth_red_sandstone", "minecraft:smooth_red_sandstone_slab")
            .put("minecraft:bricks", "minecraft:brick_slab")
            .put("minecraft:mud_bricks", "minecraft:mud_brick_slab")
            .put("minecraft:nether_bricks", "minecraft:nether_brick_slab")
            .put("minecraft:red_nether_bricks", "minecraft:red_nether_brick_slab")
            .put("minecraft:quartz_block", "minecraft:quartz_slab")
            .put("minecraft:smooth_quartz", "minecraft:smooth_quartz_slab")
            .put("minecraft:prismarine", "minecraft:prismarine_slab")
            .put("minecraft:prismarine_bricks", "minecraft:prismarine_brick_slab")
            .put("minecraft:dark_prismarine", "minecraft:dark_prismarine_slab")
            .put("minecraft:purpur_block", "minecraft:purpur_slab")
            .put("minecraft:end_stone_bricks", "minecraft:end_stone_brick_slab")
            .put("minecraft:blackstone", "minecraft:blackstone_slab")
            .put("minecraft:polished_blackstone", "minecraft:polished_blackstone_slab")
            .put("minecraft:polished_blackstone_bricks", "minecraft:polished_blackstone_brick_slab")
            .put("minecraft:cobbled_deepslate", "minecraft:cobbled_deepslate_slab")
            .put("minecraft:polished_deepslate", "minecraft:polished_deepslate_slab")
            .put("minecraft:deepslate_bricks", "minecraft:deepslate_brick_slab")
            .put("minecraft:deepslate_tiles", "minecraft:deepslate_tile_slab")
            .put("minecraft:resin_bricks", "minecraft:resin_brick_slab")
            .put("minecraft:oak_planks", "minecraft:oak_slab")
            .put("minecraft:spruce_planks", "minecraft:spruce_slab")
            .put("minecraft:birch_planks", "minecraft:birch_slab")
            .put("minecraft:jungle_planks", "minecraft:jungle_slab")
            .put("minecraft:acacia_planks", "minecraft:acacia_slab")
            .put("minecraft:dark_oak_planks", "minecraft:dark_oak_slab")
            .put("minecraft:mangrove_planks", "minecraft:mangrove_slab")
            .put("minecraft:cherry_planks", "minecraft:cherry_slab")
            .put("minecraft:pale_oak_planks", "minecraft:pale_oak_slab")
            .put("minecraft:bamboo_planks", "minecraft:bamboo_slab")
            .put("minecraft:crimson_planks", "minecraft:crimson_slab")
            .put("minecraft:warped_planks", "minecraft:warped_slab")
            .putAll(TUFF_SLABS)
            .build();
}
