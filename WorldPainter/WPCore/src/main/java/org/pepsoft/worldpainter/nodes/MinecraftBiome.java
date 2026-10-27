package org.pepsoft.worldpainter.nodes;

import static org.pepsoft.worldpainter.biomeschemes.Minecraft1_21Biomes.*;

/**
 * The Minecraft 1.18+ overworld biomes which a {@link BiomePainterNode} can paint, with a colour for previews.
 */
public enum MinecraftBiome {
    PLAINS("Plains", BIOME_PLAINS, 0x8db360),
    SUNFLOWER_PLAINS("Sunflower Plains", BIOME_SUNFLOWER_PLAINS, 0xb5db88),
    MEADOW("Meadow", BIOME_MEADOW, 0x9fd27a),
    FOREST("Forest", BIOME_FOREST, 0x056621),
    FLOWER_FOREST("Flower Forest", BIOME_FLOWER_FOREST, 0x2d8e49),
    BIRCH_FOREST("Birch Forest", BIOME_BIRCH_FOREST, 0x307444),
    DARK_FOREST("Dark Forest", BIOME_DARK_FOREST, 0x40511a),
    CHERRY_GROVE("Cherry Grove", BIOME_CHERRY_GROVE, 0xe8a5c9),
    PALE_GARDEN("Pale Garden", BIOME_PALE_GARDEN, 0x9a9a8a),
    TAIGA("Taiga", BIOME_TAIGA, 0x0b6659),
    OLD_GROWTH_PINE_TAIGA("Old Growth Pine Taiga", BIOME_OLD_GROWTH_PINE_TAIGA, 0x596651),
    OLD_GROWTH_SPRUCE_TAIGA("Old Growth Spruce Taiga", BIOME_OLD_GROWTH_SPRUCE_TAIGA, 0x818e79),
    SNOWY_TAIGA("Snowy Taiga", BIOME_SNOWY_TAIGA, 0x31554a),
    SNOWY_PLAINS("Snowy Plains", BIOME_SNOWY_PLAINS, 0xe8f4ff),
    ICE_SPIKES("Ice Spikes", BIOME_ICE_SPIKES, 0xb4dcdc),
    GROVE("Grove", BIOME_GROVE, 0x8fb8a6),
    SNOWY_SLOPES("Snowy Slopes", BIOME_SNOWY_SLOPES, 0xc4d6e6),
    FROZEN_PEAKS("Frozen Peaks", BIOME_FROZEN_PEAKS, 0xa0c4e8),
    JAGGED_PEAKS("Jagged Peaks", BIOME_JAGGED_PEAKS, 0xdce4ec),
    STONY_PEAKS("Stony Peaks", BIOME_STONY_PEAKS, 0x9b9b9b),
    WINDSWEPT_HILLS("Windswept Hills", BIOME_WINDSWEPT_HILLS, 0x606060),
    SAVANNA("Savanna", BIOME_SAVANNA, 0xbdb25f),
    DESERT("Desert", BIOME_DESERT, 0xfa9418),
    BADLANDS("Badlands", BIOME_BADLANDS, 0xd94515),
    ERODED_BADLANDS("Eroded Badlands", BIOME_ERODED_BADLANDS, 0xff6d3d),
    WOODED_BADLANDS("Wooded Badlands", BIOME_WOODED_BADLANDS, 0xb09765),
    JUNGLE("Jungle", BIOME_JUNGLE, 0x537b09),
    SPARSE_JUNGLE("Sparse Jungle", BIOME_SPARSE_JUNGLE, 0x628b17),
    BAMBOO_JUNGLE("Bamboo Jungle", BIOME_BAMBOO_JUNGLE, 0x768e14),
    SWAMP("Swamp", BIOME_SWAMP, 0x07f9b2),
    MANGROVE_SWAMP("Mangrove Swamp", BIOME_MANGROVE_SWAMP, 0x67b26b),
    MUSHROOM_FIELDS("Mushroom Fields", BIOME_MUSHROOM_FIELDS, 0xff00ff),
    BEACH("Beach", BIOME_BEACH, 0xfade55),
    SNOWY_BEACH("Snowy Beach", BIOME_SNOWY_BEACH, 0xfaf0c0),
    STONY_SHORE("Stony Shore", BIOME_STONY_SHORE, 0xa2a284),
    RIVER("River", BIOME_RIVER, 0x0000ff),
    FROZEN_RIVER("Frozen River", BIOME_FROZEN_RIVER, 0xa0a0ff),
    OCEAN("Ocean", BIOME_OCEAN, 0x000070),
    DEEP_OCEAN("Deep Ocean", BIOME_DEEP_OCEAN, 0x000030),
    WARM_OCEAN("Warm Ocean", BIOME_WARM_OCEAN, 0x0000ac),
    LUKEWARM_OCEAN("Lukewarm Ocean", BIOME_LUKEWARM_OCEAN, 0x000090),
    COLD_OCEAN("Cold Ocean", BIOME_COLD_OCEAN, 0x202070),
    FROZEN_OCEAN("Frozen Ocean", BIOME_FROZEN_OCEAN, 0x7070d6);

    MinecraftBiome(String displayName, int id, int colour) {
        this.displayName = displayName;
        this.id = id;
        this.colour = colour;
    }

    /**
     * The WorldPainter ID of the biome, as stored in the biome layer.
     */
    public int getId() {
        return id;
    }

    /**
     * A colour for previews, as RGB.
     */
    public int getColour() {
        return colour;
    }

    @Override
    public String toString() {
        return displayName;
    }

    private final String displayName;
    private final int id, colour;
}
