package org.pepsoft.worldpainter.nodes;

import java.util.List;

/**
 * Paints vegetation (forests, plants, snow) with a density which follows its mask. Unlike terrain and biomes,
 * different kinds of vegetation can overlap.
 */
public class FloraPainterNode extends PainterNode {
    public FloraPainterNode() {
        this(Vegetation.DECIDUOUS_FOREST);
    }

    public FloraPainterNode(Vegetation vegetation) {
        set(VEGETATION, vegetation);
    }

    @Override
    public String getName() {
        return "Paint flora";
    }

    @Override
    public String getTitle() {
        return "Flora: " + get(VEGETATION);
    }

    @Override
    public List<String> getInputNames() {
        return INPUT_NAMES;
    }

    @Override
    public List<Parameter<?>> getParameters() {
        return List.of(VEGETATION, DENSITY, ONLY_ON_LAND);
    }

    @Override
    public Object getTarget() {
        return get(VEGETATION);
    }

    /**
     * Whether nothing should be painted under water.
     */
    public boolean isOnlyOnLand() {
        return get(ONLY_ON_LAND);
    }

    @Override
    double getWeight() {
        return getDouble(DENSITY);
    }

    public static final ChoiceParameter<Vegetation> VEGETATION = new ChoiceParameter<>("vegetation", "Vegetation", "What to plant", Vegetation.DECIDUOUS_FOREST, Vegetation.values());
    public static final NumberParameter DENSITY = new NumberParameter("density", "Density", "How dense the vegetation is where the mask is one", 0.5, 0, 1, 0.01, false, null);
    public static final BooleanParameter ONLY_ON_LAND = new BooleanParameter("onlyOnLand", "Only on land", "Do not plant anything under water", true);

    private static final List<String> INPUT_NAMES = List.of("Density");
    private static final long serialVersionUID = 1L;
}
