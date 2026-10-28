package org.pepsoft.worldpainter.nodes;

import org.pepsoft.worldpainter.layers.*;
import org.pepsoft.worldpainter.layers.plants.PlantLayerPresets;

/**
 * The kinds of vegetation (and snow) which a {@link FloraPainterNode} can paint: WorldPainter's built-in forest and
 * frost layers, or ready made plant layers.
 */
public enum Vegetation {
    DECIDUOUS_FOREST("Deciduous forest", DeciduousForest.INSTANCE, null, 0x2f6b2a),
    PINE_FOREST("Pine forest", PineForest.INSTANCE, null, 0x1f4d36),
    JUNGLE("Jungle", Jungle.INSTANCE, null, 0x2e8b2e),
    SWAMP_TREES("Swamp trees", SwampLand.INSTANCE, null, 0x4a5d23),
    SNOW("Snow and ice", Frost.INSTANCE, null, 0xf4f8ff),
    GRASS_AND_FLOWERS("Grass and flowers", null, PlantLayerPresets.Preset.GRASS_AND_FLOWERS, 0x86c45f),
    TALL_GRASS("Tall grass", null, PlantLayerPresets.Preset.TALL_GRASS, 0x6aa84f),
    FLOWERS("Flowers", null, PlantLayerPresets.Preset.FLOWERS, 0xe07ab8),
    FERNS("Ferns", null, PlantLayerPresets.Preset.FERNS, 0x3f8f4f),
    DESERT_PLANTS("Desert plants", null, PlantLayerPresets.Preset.DESERT_PLANTS, 0xa8a060),
    MUSHROOMS("Mushrooms", null, PlantLayerPresets.Preset.MUSHROOMS, 0xb05040);

    Vegetation(String displayName, Layer layer, PlantLayerPresets.Preset plantPreset, int colour) {
        this.displayName = displayName;
        this.layer = layer;
        this.plantPreset = plantPreset;
        this.colour = colour;
    }

    /**
     * The built-in layer, or {@code null} if this is a {@link #getPlantPreset() plant preset}.
     */
    public Layer getLayer() {
        return layer;
    }

    /**
     * The plant layer preset, or {@code null} if this is a {@link #getLayer() built-in layer}.
     */
    public PlantLayerPresets.Preset getPlantPreset() {
        return plantPreset;
    }

    /**
     * Whether this is a forest, painted with an intensity rather than on or off.
     */
    public boolean isForest() {
        return layer instanceof TreeLayer;
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
    private final Layer layer;
    private final PlantLayerPresets.Preset plantPreset;
    private final int colour;
}
