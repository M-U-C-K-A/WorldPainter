package org.pepsoft.worldpainter.vanilla;

import org.pepsoft.worldpainter.biomeschemes.Minecraft1_21Biomes;

import java.util.Locale;

/**
 * Information about the overworld biomes produced by {@link VanillaBiomeSource}, which uses the biome IDs of the
 * cubiomes library: names, map colours (those of cubiomes and Amidst), the corresponding WorldPainter biome IDs, and a
 * coarse {@link Category category} used to compare biomes.
 */
public final class VanillaBiomes {
    private VanillaBiomes() {
        // Prevent instantiation
    }

    /**
     * The technical name of a biome, without namespace, e.g. {@code "snowy_plains"}.
     */
    public static String getName(int biome) {
        return ((biome >= 0) && (biome < NAMES.length) && (NAMES[biome] != null)) ? NAMES[biome] : "unknown";
    }

    /**
     * A human readable name for a biome, e.g. {@code "Snowy Plains"}.
     */
    public static String getDisplayName(int biome) {
        final String[] words = getName(biome).split("_");
        final StringBuilder sb = new StringBuilder();
        for (String word: words) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    /**
     * The map colour of a biome as an RGB value.
     */
    public static int getColour(int biome) {
        return ((biome >= 0) && (biome < COLOURS.length) && (COLOURS[biome] != -1)) ? COLOURS[biome] : 0x808080;
    }

    /**
     * The WorldPainter biome ID corresponding to a biome.
     */
    public static int toWorldPainterBiome(int biome) {
        switch (biome) {
            case 177: return Minecraft1_21Biomes.BIOME_MEADOW;
            case 178: return Minecraft1_21Biomes.BIOME_GROVE;
            case 179: return Minecraft1_21Biomes.BIOME_SNOWY_SLOPES;
            case 180: return Minecraft1_21Biomes.BIOME_JAGGED_PEAKS;
            case 181: return Minecraft1_21Biomes.BIOME_FROZEN_PEAKS;
            case 182: return Minecraft1_21Biomes.BIOME_STONY_PEAKS;
            case 183: return Minecraft1_21Biomes.BIOME_DEEP_DARK;
            case 184: return Minecraft1_21Biomes.BIOME_MANGROVE_SWAMP;
            case 185: return Minecraft1_21Biomes.BIOME_CHERRY_GROVE;
            case 186: return Minecraft1_21Biomes.BIOME_PALE_GARDEN;
            default: return biome;
        }
    }

    /**
     * The biome ID (as used by this class) corresponding to a WorldPainter biome ID.
     */
    public static int fromWorldPainterBiome(int worldPainterBiome) {
        switch (worldPainterBiome) {
            case Minecraft1_21Biomes.BIOME_MEADOW: return 177;
            case Minecraft1_21Biomes.BIOME_GROVE: return 178;
            case Minecraft1_21Biomes.BIOME_SNOWY_SLOPES: return 179;
            case Minecraft1_21Biomes.BIOME_JAGGED_PEAKS: return 180;
            case Minecraft1_21Biomes.BIOME_FROZEN_PEAKS: return 181;
            case Minecraft1_21Biomes.BIOME_STONY_PEAKS: return 182;
            case Minecraft1_21Biomes.BIOME_DEEP_DARK: return 183;
            case Minecraft1_21Biomes.BIOME_MANGROVE_SWAMP: return 184;
            case Minecraft1_21Biomes.BIOME_CHERRY_GROVE: return 185;
            case Minecraft1_21Biomes.BIOME_PALE_GARDEN: return 186;
            default: return worldPainterBiome;
        }
    }

    public static Category getCategory(int biome) {
        return Category.of(getName(biome));
    }

    /**
     * The category of a WorldPainter biome, based on its technical or display name.
     */
    public static Category getCategoryOfWorldPainterBiome(int worldPainterBiome) {
        if ((worldPainterBiome < 0) || (worldPainterBiome >= Minecraft1_21Biomes.BIOME_NAMES.length)) {
            return Category.PLAINS;
        }
        String name = (worldPainterBiome < Minecraft1_21Biomes.MODERN_IDS.length) ? Minecraft1_21Biomes.MODERN_IDS[worldPainterBiome] : null;
        if (name == null) {
            name = Minecraft1_21Biomes.BIOME_NAMES[worldPainterBiome];
        }
        return (name != null) ? Category.of(name) : Category.PLAINS;
    }

    /**
     * Coarse biome categories, for judging how well a map fits in with the terrain Minecraft generates around it.
     */
    public enum Category {
        OCEAN, RIVER, BEACH, DESERT, BADLANDS, SAVANNA, PLAINS, FOREST, TAIGA, SNOWY, MOUNTAINS, JUNGLE, SWAMP, MUSHROOM, CAVE;

        static Category of(String name) {
            final String n = name.toLowerCase(Locale.ROOT).replace("minecraft:", "");
            if (n.contains("ocean")) return OCEAN;
            if (n.contains("river")) return RIVER;
            if (n.contains("beach") || n.contains("shore")) return BEACH;
            if (n.contains("cave") || n.contains("deep_dark") || n.contains("deep dark")) return CAVE;
            if (n.contains("mushroom")) return MUSHROOM;
            if (n.contains("badlands") || n.contains("mesa")) return BADLANDS;
            if (n.contains("desert")) return DESERT;
            if (n.contains("savanna")) return SAVANNA;
            if (n.contains("jungle") || n.contains("bamboo")) return JUNGLE;
            if (n.contains("swamp")) return SWAMP;
            if (n.contains("peaks") || n.contains("slopes") || n.contains("windswept") || n.contains("mountain") || n.contains("extreme") || n.contains("grove") && ! n.contains("cherry")) return MOUNTAINS;
            if (n.contains("snow") || n.contains("ice") || n.contains("frozen") || n.contains("cold")) return SNOWY;
            if (n.contains("taiga")) return TAIGA;
            if (n.contains("forest") || n.contains("garden") || n.contains("cherry") || n.contains("woods") || n.contains("birch")) return FOREST;
            return PLAINS;
        }

        /**
         * How similar two categories are, from 0 (completely different) to 1 (the same).
         */
        public float similarity(Category other) {
            if (this == other) {
                return 1.0f;
            }
            final boolean thisWet = (this == OCEAN) || (this == RIVER), otherWet = (other == OCEAN) || (other == RIVER);
            if (thisWet != otherWet) {
                // Land where water was expected or vice versa is the worst mismatch, except for beaches
                return ((this == BEACH) || (other == BEACH)) ? 0.4f : 0.0f;
            }
            if (thisWet) {
                return 0.6f;
            }
            return RELATED[ordinal()][other.ordinal()];
        }

        private static final float[][] RELATED = new float[values().length][values().length];

        static {
            relate(PLAINS, FOREST, 0.45f);
            relate(PLAINS, SAVANNA, 0.45f);
            relate(PLAINS, SWAMP, 0.3f);
            relate(PLAINS, BEACH, 0.3f);
            relate(FOREST, TAIGA, 0.5f);
            relate(FOREST, JUNGLE, 0.4f);
            relate(FOREST, SWAMP, 0.35f);
            relate(TAIGA, SNOWY, 0.5f);
            relate(TAIGA, MOUNTAINS, 0.35f);
            relate(SNOWY, MOUNTAINS, 0.45f);
            relate(DESERT, BADLANDS, 0.55f);
            relate(DESERT, SAVANNA, 0.4f);
            relate(DESERT, BEACH, 0.35f);
            relate(BADLANDS, SAVANNA, 0.35f);
            relate(JUNGLE, SWAMP, 0.35f);
            relate(MOUNTAINS, PLAINS, 0.2f);
            relate(MOUNTAINS, FOREST, 0.2f);
            relate(CAVE, MOUNTAINS, 0.3f);
        }

        private static void relate(Category a, Category b, float similarity) {
            RELATED[a.ordinal()][b.ordinal()] = similarity;
            RELATED[b.ordinal()][a.ordinal()] = similarity;
        }
    }

    private static final String[] NAMES = new String[256];
    private static final int[] COLOURS = new int[256];

    static {
        java.util.Arrays.fill(COLOURS, -1);
        define(0, "ocean", 0x000070);
        define(1, "plains", 0x8db360);
        define(2, "desert", 0xfa9418);
        define(3, "windswept_hills", 0x606060);
        define(4, "forest", 0x056621);
        define(5, "taiga", 0x0b6a5f);
        define(6, "swamp", 0x07f9b2);
        define(7, "river", 0x0000ff);
        define(10, "frozen_ocean", 0x7070d6);
        define(11, "frozen_river", 0xa0a0ff);
        define(12, "snowy_plains", 0xffffff);
        define(14, "mushroom_fields", 0xff00ff);
        define(16, "beach", 0xfade55);
        define(21, "jungle", 0x507b0a);
        define(23, "sparse_jungle", 0x60930f);
        define(24, "deep_ocean", 0x000030);
        define(25, "stony_shore", 0xa2a284);
        define(26, "snowy_beach", 0xfaf0c0);
        define(27, "birch_forest", 0x307444);
        define(29, "dark_forest", 0x40511a);
        define(30, "snowy_taiga", 0x31554a);
        define(32, "old_growth_pine_taiga", 0x596651);
        define(34, "windswept_forest", 0x5b7352);
        define(35, "savanna", 0xbdb25f);
        define(36, "savanna_plateau", 0xa79d64);
        define(37, "badlands", 0xd94515);
        define(38, "wooded_badlands", 0xb09765);
        define(44, "warm_ocean", 0x0000ac);
        define(45, "lukewarm_ocean", 0x000090);
        define(46, "cold_ocean", 0x202070);
        define(47, "deep_warm_ocean", 0x000050);
        define(48, "deep_lukewarm_ocean", 0x000040);
        define(49, "deep_cold_ocean", 0x202038);
        define(50, "deep_frozen_ocean", 0x404090);
        define(129, "sunflower_plains", 0xb5db88);
        define(131, "windswept_gravelly_hills", 0x888888);
        define(132, "flower_forest", 0x2d8e49);
        define(140, "ice_spikes", 0xb4dcdc);
        define(155, "old_growth_birch_forest", 0x589c6c);
        define(160, "old_growth_spruce_taiga", 0x818e79);
        define(163, "windswept_savanna", 0xe5da87);
        define(165, "eroded_badlands", 0xff6d3d);
        define(168, "bamboo_jungle", 0x849500);
        define(174, "dripstone_caves", 0x4e3012);
        define(175, "lush_caves", 0x283c00);
        define(177, "meadow", 0x60a445);
        define(178, "grove", 0x47726c);
        define(179, "snowy_slopes", 0xc4c4c4);
        define(180, "jagged_peaks", 0xdcdcc8);
        define(181, "frozen_peaks", 0xb0b3ce);
        define(182, "stony_peaks", 0x7b8f74);
        define(183, "deep_dark", 0x031f29);
        define(184, "mangrove_swamp", 0x2ccc8e);
        define(185, "cherry_grove", 0xff91c8);
        define(186, "pale_garden", 0x696d95);
    }

    private static void define(int id, String name, int colour) {
        NAMES[id] = name;
        COLOURS[id] = colour;
    }
}
