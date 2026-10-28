package org.pepsoft.worldpainter.procedural;

import org.pepsoft.worldpainter.Terrain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static org.pepsoft.worldpainter.procedural.NoiseLayer.BlendMode.*;
import static org.pepsoft.worldpainter.procedural.NoiseLayer.Type.*;

/**
 * The complete description of a procedurally generated terrain: a stack of {@link NoiseLayer noise layers} which
 * determine the height, optional terracing, a water level and a list of {@link SurfaceRule surface rules} which
 * determine the terrain type. Use {@link ProceduralTerrain} to evaluate it.
 */
public class ProceduralTerrainSettings implements Serializable, Cloneable {
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getSeed() {
        return seed;
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }

    /**
     * The height to which the layers are added.
     */
    public int getBaseHeight() {
        return baseHeight;
    }

    public void setBaseHeight(int baseHeight) {
        this.baseHeight = baseHeight;
    }

    public List<NoiseLayer> getLayers() {
        return layers;
    }

    public void setLayers(List<NoiseLayer> layers) {
        this.layers = layers;
    }

    /**
     * The height of terraces in blocks, or zero for no terracing.
     */
    public int getTerraceHeight() {
        return terraceHeight;
    }

    public void setTerraceHeight(int terraceHeight) {
        this.terraceHeight = terraceHeight;
    }

    /**
     * How flat the terraces are and how steep the steps between them (0-1).
     */
    public float getTerraceSharpness() {
        return terraceSharpness;
    }

    public void setTerraceSharpness(float terraceSharpness) {
        this.terraceSharpness = terraceSharpness;
    }

    public boolean isWater() {
        return water;
    }

    public void setWater(boolean water) {
        this.water = water;
    }

    public int getWaterLevel() {
        return waterLevel;
    }

    public void setWaterLevel(int waterLevel) {
        this.waterLevel = waterLevel;
    }

    public List<SurfaceRule> getRules() {
        return rules;
    }

    public void setRules(List<SurfaceRule> rules) {
        this.rules = rules;
    }

    /**
     * The terrain type to use when none of the rules match.
     */
    public Terrain getDefaultTerrain() {
        return defaultTerrain;
    }

    public void setDefaultTerrain(Terrain defaultTerrain) {
        this.defaultTerrain = defaultTerrain;
    }

    @Override
    public ProceduralTerrainSettings clone() {
        try {
            final ProceduralTerrainSettings clone = (ProceduralTerrainSettings) super.clone();
            clone.layers = new ArrayList<>(layers.size());
            layers.forEach(layer -> clone.layers.add(layer.clone()));
            clone.rules = new ArrayList<>(rules.size());
            rules.forEach(rule -> clone.rules.add(rule.clone()));
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * Create the settings for a preset.
     */
    public static ProceduralTerrainSettings create(Preset preset, long seed) {
        final ProceduralTerrainSettings settings = new ProceduralTerrainSettings();
        settings.name = preset.toString();
        settings.seed = seed;
        final List<NoiseLayer> layers = settings.layers;
        final List<SurfaceRule> rules = settings.rules;
        switch (preset) {
            case MINECRAFT_LIKE:
                settings.baseHeight = 58;
                layers.add(layer("Continents", FBM, ADD, 900, 22, 4, 0.5f, 1.0f, 120, 1.0f));
                layers.add(layer("Hills", FBM, ADD, 220, 18, 5, 0.5f, 1.4f, 30, 0.7f));
                layers.add(layer("Detail", FBM, ADD, 40, 3, 3, 0.5f, 1.0f, 0, 1.0f));
                addTemperateRules(rules, settings.waterLevel);
                break;
            case ROLLING_HILLS:
                settings.baseHeight = 60;
                layers.add(layer("Hills", FBM, ADD, 380, 34, 5, 0.5f, 1.2f, 40, 1.0f));
                layers.add(layer("Bumps", BILLOW, ADD, 110, 8, 3, 0.45f, 1.0f, 0, 1.0f));
                addTemperateRules(rules, settings.waterLevel);
                break;
            case ALPINE:
                settings.baseHeight = 56;
                layers.add(layer("Lowlands", FBM, ADD, 900, 36, 4, 0.5f, 1.0f, 80, 1.0f));
                layers.add(layer("Mountain ranges", RIDGED, ADD, 620, 115, 5, 0.45f, 1.15f, 90, 0.55f));
                layers.add(layer("Detail", FBM, ADD, 50, 5, 3, 0.5f, 1.0f, 0, 1.0f));
                rules.add(rule(Terrain.DEEP_SNOW, 200, Integer.MAX_VALUE, 0, 90, 8));
                rules.add(rule(Terrain.SNOW, 175, Integer.MAX_VALUE, 0, 50, 8));
                rules.add(rule(Terrain.ROCK, Integer.MIN_VALUE, Integer.MAX_VALUE, 42, 90, 0));
                rules.add(rule(Terrain.STONE_MIX, 145, Integer.MAX_VALUE, 0, 90, 8));
                rules.add(rule(Terrain.GRAVEL, Integer.MIN_VALUE, settings.waterLevel - 3, 0, 90, 2));
                rules.add(rule(Terrain.BEACHES, Integer.MIN_VALUE, settings.waterLevel + 2, 0, 30, 1));
                break;
            case ARCHIPELAGO:
                settings.baseHeight = 24;
                layers.add(layer("Islands", FBM, ADD, 340, 78, 5, 0.5f, 1.35f, 60, 1.0f));
                layers.add(layer("Peaks", RIDGED, ADD, 220, 22, 4, 0.45f, 1.2f, 30, 0.4f));
                rules.add(rule(Terrain.ROCK, Integer.MIN_VALUE, Integer.MAX_VALUE, 45, 90, 0));
                rules.add(rule(Terrain.SAND, Integer.MIN_VALUE, settings.waterLevel + 3, 0, 90, 1));
                rules.add(rule(Terrain.STONE, 120, Integer.MAX_VALUE, 0, 90, 6));
                break;
            case CANYONS:
                settings.baseHeight = 78;
                settings.terraceHeight = 7;
                settings.terraceSharpness = 0.6f;
                settings.defaultTerrain = Terrain.MESA;
                layers.add(layer("Plateaus", FBM, ADD, 650, 34, 4, 0.5f, 1.0f, 50, 1.0f));
                layers.add(layer("Canyons", RIDGED, SUBTRACT, 320, 52, 4, 0.45f, 3.0f, 60, 1.0f));
                rules.add(rule(Terrain.RED_SAND, Integer.MIN_VALUE, 66, 0, 25, 2));
                rules.add(rule(Terrain.RED_DESERT, Integer.MIN_VALUE, Integer.MAX_VALUE, 0, 12, 0));
                break;
            case DUNES:
                settings.baseHeight = 64;
                settings.defaultTerrain = Terrain.DESERT;
                final NoiseLayer dunes = layer("Dunes", DUNES, ADD, 90, 11, 3, 0.4f, 1.0f, 35, 1.0f);
                dunes.setDirection(30);
                layers.add(dunes);
                layers.add(layer("Swells", FBM, ADD, 520, 16, 4, 0.5f, 1.0f, 0, 1.0f));
                rules.add(rule(Terrain.SANDSTONE, Integer.MIN_VALUE, Integer.MAX_VALUE, 40, 90, 0));
                break;
            case VOLCANIC:
                settings.baseHeight = 30;
                layers.add(layer("Volcanoes", CELLS, ADD, 520, 150, 1, 0.5f, 2.6f, 60, 1.0f));
                layers.add(layer("Craters", CELLS, SUBTRACT, 520, 18, 1, 0.5f, 14.0f, 60, 1.0f));
                layers.add(layer("Lava flows", FBM, ADD, 160, 14, 4, 0.55f, 1.0f, 20, 1.0f));
                rules.add(rule(Terrain.BLACKSTONE, 150, Integer.MAX_VALUE, 0, 90, 6));
                rules.add(rule(Terrain.BASALT, 100, Integer.MAX_VALUE, 0, 90, 6));
                rules.add(rule(Terrain.ROCK, Integer.MIN_VALUE, Integer.MAX_VALUE, 40, 90, 0));
                rules.add(rule(Terrain.SAND, Integer.MIN_VALUE, settings.waterLevel + 2, 0, 90, 1));
                break;
            case FLAT_PLAINS:
                settings.baseHeight = 64;
                layers.add(layer("Gentle undulation", FBM, ADD, 500, 8, 4, 0.5f, 1.0f, 0, 1.0f));
                addTemperateRules(rules, settings.waterLevel);
                break;
            default:
                throw new IllegalArgumentException("Unknown preset " + preset);
        }
        return settings;
    }

    private static void addTemperateRules(List<SurfaceRule> rules, int waterLevel) {
        rules.add(rule(Terrain.SNOW, 180, Integer.MAX_VALUE, 0, 90, 6));
        rules.add(rule(Terrain.ROCK, Integer.MIN_VALUE, Integer.MAX_VALUE, 45, 90, 0));
        rules.add(rule(Terrain.STONE_MIX, 130, Integer.MAX_VALUE, 0, 90, 8));
        rules.add(rule(Terrain.GRAVEL, Integer.MIN_VALUE, waterLevel - 4, 0, 90, 2));
        rules.add(rule(Terrain.BEACHES, Integer.MIN_VALUE, waterLevel + 2, 0, 30, 1));
    }

    private static NoiseLayer layer(String name, NoiseLayer.Type type, NoiseLayer.BlendMode blendMode, float scale, float amplitude, int octaves, float roughness, float sharpness, float warp, float coverage) {
        final NoiseLayer layer = new NoiseLayer(name, type, blendMode, scale, amplitude, octaves);
        layer.setRoughness(roughness);
        layer.setSharpness(sharpness);
        layer.setWarp(warp);
        layer.setCoverage(coverage);
        layer.setSeedOffset(name.hashCode());
        return layer;
    }

    private static SurfaceRule rule(Terrain terrain, int minHeight, int maxHeight, float minSlope, float maxSlope, float variation) {
        final SurfaceRule rule = new SurfaceRule(terrain, minHeight, maxHeight, minSlope, maxSlope);
        rule.setVariation(variation);
        return rule;
    }

    private String name = "Custom";
    private long seed;
    private int baseHeight = 62, terraceHeight, waterLevel = 62;
    private float terraceSharpness = 0.5f;
    private boolean water = true;
    private List<NoiseLayer> layers = new ArrayList<>();
    private List<SurfaceRule> rules = new ArrayList<>();
    private Terrain defaultTerrain = Terrain.GRASS;

    private static final long serialVersionUID = 1L;

    public enum Preset {
        MINECRAFT_LIKE("Minecraft-like"),
        FLAT_PLAINS("Plains"),
        ROLLING_HILLS("Rolling hills"),
        ALPINE("Alpine mountains"),
        ARCHIPELAGO("Archipelago"),
        CANYONS("Mesa canyons"),
        DUNES("Desert dunes"),
        VOLCANIC("Volcanic islands");

        Preset(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }

        private final String displayName;
    }
}
