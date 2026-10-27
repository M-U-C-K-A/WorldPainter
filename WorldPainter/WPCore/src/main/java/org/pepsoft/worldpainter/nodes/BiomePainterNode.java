package org.pepsoft.worldpainter.nodes;

import java.util.List;

/**
 * Paints a Minecraft biome where its mask is strong. Where several biome painters overlap the one with the strongest
 * mask wins; where none is strong enough the biome is left to be chosen automatically.
 */
public class BiomePainterNode extends PainterNode {
    public BiomePainterNode() {
        this(MinecraftBiome.PLAINS);
    }

    public BiomePainterNode(MinecraftBiome biome) {
        set(BIOME, biome);
    }

    @Override
    public String getName() {
        return "Paint biome";
    }

    @Override
    public String getTitle() {
        return "Biome: " + get(BIOME);
    }

    @Override
    public List<Parameter<?>> getParameters() {
        return List.of(BIOME, STRENGTH);
    }

    @Override
    public Object getTarget() {
        return get(BIOME);
    }

    @Override
    double getWeight() {
        return getDouble(STRENGTH);
    }

    public static final ChoiceParameter<MinecraftBiome> BIOME = new ChoiceParameter<>("biome", "Biome", "The biome to paint", MinecraftBiome.PLAINS, MinecraftBiome.values());
    public static final NumberParameter STRENGTH = new NumberParameter("strength", "Strength", "Multiplies the mask; where painters overlap the strongest one wins", 1, 0, 2, 0.01, false, null);

    private static final long serialVersionUID = 1L;
}
