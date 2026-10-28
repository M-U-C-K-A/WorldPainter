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
        return layer;
    }

    public enum Preset {
        GRASS_AND_FLOWERS("Grass and flowers", "Short and tall grass with a few wild flowers", 0x86c45f,
                new Plant[] {GRASS, Plants.TALL_GRASS, DANDELION, POPPY, OXEYE_DAISY, CORNFLOWER, AZURE_BLUET},
                new int[] {60, 8, 3, 3, 2, 2, 1}),
        TALL_GRASS("Tall grass", "Dense short and tall grass", 0x6aa84f,
                new Plant[] {GRASS, Plants.TALL_GRASS, FERN},
                new int[] {30, 30, 4}),
        FLOWERS("Flowers", "A colourful mix of flowers", 0xe07ab8,
                new Plant[] {DANDELION, POPPY, OXEYE_DAISY, CORNFLOWER, AZURE_BLUET, ALLIUM, TULIP_RED, TULIP_ORANGE, TULIP_WHITE, TULIP_PINK, LILAC, ROSE_BUSH, PEONY},
                new int[] {10, 10, 6, 6, 5, 3, 3, 3, 3, 3, 2, 2, 2}),
        FERNS("Ferns", "Ferns and grass for forest floors", 0x3f8f4f,
                new Plant[] {FERN, LARGE_FERN, GRASS},
                new int[] {20, 8, 10}),
        DESERT_PLANTS("Desert plants", "Dead shrubs and cacti", 0xa8a060,
                new Plant[] {DEAD_SHRUB, CACTUS},
                new int[] {20, 6}),
        MUSHROOMS("Mushrooms", "Red and brown mushrooms", 0xb05040,
                new Plant[] {MUSHROOM_RED, MUSHROOM_BROWN},
                new int[] {5, 5});

        Preset(String name, String description, int colour, Plant[] plants, int[] occurrences) {
            this.name = name;
            this.description = description;
            this.colour = colour;
            this.plants = plants;
            this.occurrences = occurrences;
        }

        /**
         * The name of layers created from this preset.
         */
        public String getLayerName() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }

        private final String name, description;
        private final int colour;
        private final Plant[] plants;
        private final int[] occurrences;
    }
}
