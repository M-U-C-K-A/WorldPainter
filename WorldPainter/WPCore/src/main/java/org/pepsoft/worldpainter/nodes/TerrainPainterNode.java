package org.pepsoft.worldpainter.nodes;

import org.pepsoft.worldpainter.Terrain;

import java.util.Arrays;
import java.util.List;

/**
 * Paints a terrain type where its mask is strong. Where several terrain painters overlap the one with the strongest
 * mask wins; where none is strong enough the map's theme decides (if the terrain is repainted).
 */
public class TerrainPainterNode extends PainterNode {
    public TerrainPainterNode() {
        this(Terrain.STONE);
    }

    public TerrainPainterNode(Terrain terrain) {
        set(TERRAIN, terrain);
    }

    @Override
    public String getName() {
        return "Paint terrain";
    }

    @Override
    public String getTitle() {
        return "Terrain: " + get(TERRAIN);
    }

    @Override
    public List<Parameter<?>> getParameters() {
        return List.of(TERRAIN, STRENGTH);
    }

    @Override
    public Object getTarget() {
        return get(TERRAIN);
    }

    @Override
    double getWeight() {
        return getDouble(STRENGTH);
    }

    public static final ChoiceParameter<Terrain> TERRAIN = new ChoiceParameter<>("terrain", "Terrain", "The terrain type to paint", Terrain.STONE,
            Arrays.stream(Terrain.values()).filter(terrain -> ! terrain.isCustom()).toArray(Terrain[]::new));
    public static final NumberParameter STRENGTH = new NumberParameter("strength", "Strength", "Multiplies the mask; where painters overlap the strongest one wins", 1, 0, 2, 0.01, false, null);

    private static final long serialVersionUID = 1L;
}
