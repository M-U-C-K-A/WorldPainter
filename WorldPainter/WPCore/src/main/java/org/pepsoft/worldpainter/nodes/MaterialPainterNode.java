package org.pepsoft.worldpainter.nodes;

import org.pepsoft.worldpainter.MaterialGroup;

import java.util.List;

/**
 * Paints a ready made mix of blocks (mixed dirt, forest floor, cliff strata, ...) where its mask is strong. The mix is
 * added to the custom terrain types when the graph is applied.
 */
public class MaterialPainterNode extends PainterNode {
    public MaterialPainterNode() {
        this(MaterialGroup.MIXED_DIRT);
    }

    public MaterialPainterNode(MaterialGroup group) {
        set(MATERIAL, group);
    }

    @Override
    public String getName() {
        return "Paint material mix";
    }

    @Override
    public String getTitle() {
        return "Mix: " + get(MATERIAL);
    }

    @Override
    public List<Parameter<?>> getParameters() {
        return List.of(MATERIAL, STRENGTH);
    }

    @Override
    public Object getTarget() {
        return get(MATERIAL);
    }

    @Override
    double getWeight() {
        return getDouble(STRENGTH);
    }

    public static final ChoiceParameter<MaterialGroup> MATERIAL = new ChoiceParameter<>("material", "Mix", "The mix of blocks to paint", MaterialGroup.MIXED_DIRT, MaterialGroup.values());
    public static final NumberParameter STRENGTH = new NumberParameter("strength", "Strength", "Multiplies the mask; where painters overlap the strongest one wins", 1, 0, 2, 0.01, false, null);

    private static final long serialVersionUID = 1L;
}
