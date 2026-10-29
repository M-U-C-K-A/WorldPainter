package org.pepsoft.worldpainter.layers.plants;

import java.awt.*;
import java.util.Arrays;

import static org.pepsoft.worldpainter.layers.plants.Plants.*;

/**
 * Ready made {@link PlantLayer plant layers}, for instance for painting vegetation from a node graph.
 */
public final class PlantLayerPresets {
    private PlantLayerPresets() {
        // Prevent instantiation
    }

    /**
     * Create a new plant layer for a preset. Its name is {@link Preset#getLayerName()}, so that existing layers created
     * from the same preset can be recognised.
     */
    public static PlantLayer create(Preset preset) {
        final PlantLayer layer = new PlantLayer(preset.getLayerName(), preset.description, new Color(preset.colour));
        for (int i = 0; i < preset.plants.length; i++) {
            final Plant plant = preset.plants[i];
            final int index = Arrays.asList(ALL_PLANTS).indexOf(plant);
            final PlantLayer.PlantSettings settings = new PlantLayer.PlantSettings();
            settings.occurrence = (short) preset.occurrences[i];
            settings.growthFrom = Math.max(plant.getDefaultGrowth() / 2, 1);
            settings.growthTo = plant.getDefaultGrowth();
            layer.setSettings(index, settings);
        }
        if (preset.family == Family.CROPS) {
            layer.setGenerateFarmland(true);
        }
        return layer;
    }

    /**
     * The families the presets are grouped in for the user.
     */
    public enum Family {
        FLOWERS("Flowers"), PLANTS("Plants & ground cover"), WATER("Water plants"), CROPS("Crops");

        Family(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }

        private final String displayName;
    }

    public enum Preset {
        GRASS_AND_FLOWERS("Grass and flowers", "Short and tall grass with a few wild flowers", 0x86c45f, Family.PLANTS,
                new Plant[] {GRASS, Plants.TALL_GRASS, DANDELION, POPPY, OXEYE_DAISY, CORNFLOWER, AZURE_BLUET},
                new int[] {60, 8, 3, 3, 2, 2, 1}),
        TALL_GRASS("Tall grass", "Dense short and tall grass", 0x6aa84f, Family.PLANTS,
                new Plant[] {GRASS, Plants.TALL_GRASS, FERN},
                new int[] {30, 30, 4}),
        FLOWERS("Flowers", "A colourful mix of flowers", 0xe07ab8, Family.FLOWERS,
                new Plant[] {DANDELION, POPPY, OXEYE_DAISY, CORNFLOWER, AZURE_BLUET, ALLIUM, TULIP_RED, TULIP_ORANGE, TULIP_WHITE, TULIP_PINK, LILAC, ROSE_BUSH, PEONY},
                new int[] {10, 10, 6, 6, 5, 3, 3, 3, 3, 3, 2, 2, 2}),
        FERNS("Ferns", "Ferns and grass for forest floors", 0x3f8f4f, Family.PLANTS,
                new Plant[] {FERN, LARGE_FERN, GRASS},
                new int[] {20, 8, 10}),
        DESERT_PLANTS("Desert plants", "Dead shrubs, cacti and dry grass", 0xa8a060, Family.PLANTS,
                new Plant[] {DEAD_SHRUB, CACTUS, SHORT_DRY_GRASS},
                new int[] {20, 6, 10}),
        MUSHROOMS("Mushrooms", "Red and brown mushrooms", 0xb05040, Family.PLANTS,
                new Plant[] {MUSHROOM_RED, MUSHROOM_BROWN},
                new int[] {5, 5}),
        WILDFLOWER_MEADOW("Wildflower meadow", "Grass full of small wildflowers, dandelions, poppies and cornflowers", 0xf0d060, Family.FLOWERS,
                new Plant[] {GRASS, WILDFLOWERS, DANDELION, POPPY, CORNFLOWER, OXEYE_DAISY, AZURE_BLUET},
                new int[] {40, 25, 4, 4, 3, 3, 2}),
        TULIP_FIELD("Tulip field", "Red, orange, white and pink tulips", 0xe8504a, Family.FLOWERS,
                new Plant[] {TULIP_RED, TULIP_ORANGE, TULIP_WHITE, TULIP_PINK, GRASS},
                new int[] {12, 10, 8, 10, 10}),
        SUNFLOWER_FIELD("Sunflower field", "Sunflowers among grass", 0xf5c518, Family.FLOWERS,
                new Plant[] {SUNFLOWER, GRASS, Plants.TALL_GRASS},
                new int[] {25, 20, 5}),
        ROSE_GARDEN("Roses and lilacs", "Rose bushes, peonies and lilacs", 0xc8305a, Family.FLOWERS,
                new Plant[] {ROSE_BUSH, PEONY, LILAC, POPPY},
                new int[] {10, 8, 8, 4}),
        SPRING_FLOWERS("Spring flowers", "Lilies of the valley, alliums and bluets", 0xb8a0e8, Family.FLOWERS,
                new Plant[] {LILY_OF_THE_VALLEY, ALLIUM, AZURE_BLUET, OXEYE_DAISY, GRASS},
                new int[] {10, 6, 6, 4, 20}),
        PINK_PETALS("Pink petals", "Carpets of pink petals, as under cherry trees", 0xf4a6c8, Family.FLOWERS,
                new Plant[] {Plants.PINK_PETALS, GRASS},
                new int[] {30, 10}),
        SWAMP_FLOWERS("Swamp flowers", "Blue orchids among grass", 0x3aa0e0, Family.FLOWERS,
                new Plant[] {BLUE_ORCHID, GRASS, FERN},
                new int[] {10, 20, 4}),
        FOREST_FLOOR("Forest floor", "Leaf litter, moss, ferns and bushes", 0x7a6a3a, Family.PLANTS,
                new Plant[] {LEAF_LITTER, FERN, MOSS_CARPET, Plants.BUSH, MUSHROOM_BROWN},
                new int[] {20, 8, 6, 4, 1}),
        BUSHES("Bushes", "Leafy bushes and berry bushes", 0x4d7a35, Family.PLANTS,
                new Plant[] {Plants.BUSH, SWEET_BERRY_BUSH, FIREFLY_BUSH},
                new int[] {20, 6, 2}),
        DRY_GRASS("Dry grass", "Short and tall dry grass for savannas and badlands", 0xc8b070, Family.PLANTS,
                new Plant[] {SHORT_DRY_GRASS, TALL_DRY_GRASS, DEAD_SHRUB},
                new int[] {30, 10, 3}),
        JUNGLE_UNDERGROWTH("Jungle undergrowth", "Ferns, bamboo and melons", 0x2e8b2e, Family.PLANTS,
                new Plant[] {FERN, LARGE_FERN, BAMBOO, MELON, GRASS},
                new int[] {15, 8, 4, 1, 15}),
        PALE_GARDEN("Pale garden", "Pale moss carpets and eyeblossoms", 0xb8bcb0, Family.PLANTS,
                new Plant[] {PALE_MOSS_CARPET, EYEBLOSSOM},
                new int[] {20, 5}),
        LILY_PADS("Lily pads", "Lily pads on the surface of still water", 0x3f9f3f, Family.WATER,
                new Plant[] {LILY_PAD},
                new int[] {20}),
        SEAGRASS_AND_KELP("Seagrass and kelp", "Seagrass meadows and kelp forests under water", 0x2f7f5f, Family.WATER,
                new Plant[] {SEAGRASS, TALL_SEAGRASS, KELP},
                new int[] {20, 10, 8}),
        CORALS("Coral garden", "Coral, coral fans and sea pickles in warm shallow water", 0xe07a8f, Family.WATER,
                new Plant[] {TUBE_CORAL, BRAIN_CORAL, BUBBLE_CORAL, FIRE_CORAL, HORN_CORAL, TUBE_CORAL_FAN, BRAIN_CORAL_FAN, BUBBLE_CORAL_FAN, FIRE_CORAL_FAN, HORN_CORAL_FAN, SEA_PICKLE},
                new int[] {6, 6, 6, 6, 6, 4, 4, 4, 4, 4, 3}),
        SUGAR_CANE("Sugar cane", "Sugar cane for river and lake shores", 0x8ac86a, Family.WATER,
                new Plant[] {Plants.SUGAR_CANE},
                new int[] {15}),
        FARM_CROPS("Farm crops", "Wheat, carrots, potatoes and beetroots on farmland", 0xd8b84a, Family.CROPS,
                new Plant[] {WHEAT, CARROTS, POTATOES, BEETROOTS},
                new int[] {10, 10, 10, 10}),
        PUMPKINS_AND_MELONS("Pumpkins and melons", "Pumpkin and melon patches", 0xe08a2a, Family.CROPS,
                new Plant[] {PUMPKIN, MELON, GRASS},
                new int[] {6, 6, 20});

        Preset(String name, String description, int colour, Family family, Plant[] plants, int[] occurrences) {
            this.name = name;
            this.description = description;
            this.colour = colour;
            this.family = family;
            this.plants = plants;
            this.occurrences = occurrences;
        }

        /**
         * The name of layers created from this preset.
         */
        public String getLayerName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public int getColour() {
            return colour;
        }

        public Family getFamily() {
            return family;
        }

        @Override
        public String toString() {
            return name;
        }

        private final String name, description;
        private final int colour;
        private final Family family;
        private final Plant[] plants;
        private final int[] occurrences;
    }
}
