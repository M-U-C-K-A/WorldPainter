package org.pepsoft.worldpainter.help;

import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.ColourScheme;
import org.pepsoft.worldpainter.MixedMaterial;
import org.pepsoft.worldpainter.Platform;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.layers.plants.Plant;
import org.pepsoft.worldpainter.layers.plants.PlantLayer;

import java.util.*;

/**
 * Cards which show what something is made of: the blocks of a mix of materials, or the plants of a plant layer, each
 * with its icon and its share.
 */
public final class CompositionCards {
    private CompositionCards() {
        // Prevent instantiation
    }

    public static ToolCard forMaterial(String title, String description, MixedMaterial material, ColourScheme colourScheme) {
        // The same block may occur in several rows (for instance in the bands of strata); add them up
        final Map<String, Integer> counts = new LinkedHashMap<>();
        final Map<String, Material> materials = new HashMap<>();
        int total = 0;
        for (MixedMaterial.Row row: material.getRows()) {
            final String name = row.material.simpleName;
            counts.merge(name, row.occurrence, Integer::sum);
            materials.putIfAbsent(name, row.material);
            total += row.occurrence;
        }
        final List<ToolCard.Row> rows = new ArrayList<>();
        final int sum = Math.max(1, total);
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> rows.add(new ToolCard.Row(BlockIcons.block(materials.get(entry.getKey()), colourScheme), humanise(entry.getKey()), percentage(entry.getValue(), sum))));
        return new ToolCard(title, description, null, null, rows);
    }

    public static ToolCard forPlants(String title, String description, PlantLayer layer, ColourScheme colourScheme) {
        final Map<Plant, PlantLayer.PlantSettings> plants = layer.getConfiguredPlants();
        int total = 0;
        for (PlantLayer.PlantSettings settings: plants.values()) {
            total += settings.getOccurrence();
        }
        final int sum = Math.max(1, total);
        final List<ToolCard.Row> rows = new ArrayList<>();
        plants.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<Plant, PlantLayer.PlantSettings> entry) -> entry.getValue().getOccurrence()).reversed())
                .forEach(entry -> rows.add(new ToolCard.Row(BlockIcons.plant(entry.getKey(), colourScheme), entry.getKey().getName(), percentage(entry.getValue().getOccurrence(), sum))));
        return new ToolCard(title, description, null, null, rows);
    }

    /**
     * What the surface of a terrain type is made of, worked out by sampling its top blocks over an area (built in terrain
     * types are defined in code, as noise, patterns or rules, rather than as a list of blocks).
     */
    public static ToolCard forTerrain(Terrain terrain, Platform platform, ColourScheme colourScheme) {
        final Map<String, Integer> counts = new HashMap<>();
        final Map<String, Material> materials = new HashMap<>();
        int total = 0;
        // The top block and a few below it, since some terrain types have pockets of other blocks just under the top
        for (int depth: new int[] {0, 0, 1, 2, 4}) {
            for (int x = 0; x < SAMPLES; x++) {
                for (int y = 0; y < SAMPLES; y++) {
                    final Material material = terrain.getMaterial(platform, SEED, x * 3, y * 3, 64 - depth, 64);
                    if ((material == null) || (material == Material.AIR)) {
                        continue;
                    }
                    counts.merge(material.simpleName, 1, Integer::sum);
                    materials.putIfAbsent(material.simpleName, material);
                    total++;
                }
            }
        }
        final int sum = Math.max(1, total);
        final List<ToolCard.Row> rows = new ArrayList<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(8)
                .forEach(entry -> rows.add(new ToolCard.Row(BlockIcons.block(materials.get(entry.getKey()), colourScheme), humanise(entry.getKey()), percentage(entry.getValue(), sum))));
        return new ToolCard(terrain.getName(), terrain.getDescription(), null, null, rows);
    }

    /**
     * A block ID as words: {@code coarse_dirt} becomes "Coarse dirt".
     */
    static String humanise(String name) {
        final String words = name.replace('_', ' ');
        return words.isEmpty() ? words : (Character.toUpperCase(words.charAt(0)) + words.substring(1));
    }

    private static String percentage(int count, int total) {
        final int percent = Math.round(count * 100f / total);
        return (percent < 1) ? "<1%" : (percent + "%");
    }

    private static final int SAMPLES = 64;
    private static final long SEED = 1234567L;
}
