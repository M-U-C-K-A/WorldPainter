package org.pepsoft.worldpainter.nodes;

/**
 * The kinds of {@link Node}.
 */
public enum NodeCategory {
    /**
     * Nodes without inputs which create a height field, such as noise.
     */
    GENERATOR("Generators"),
    /**
     * Nodes which combine two or more inputs.
     */
    COMBINER("Combiners"),
    /**
     * Nodes which modify one input.
     */
    FILTER("Filters"),
    /**
     * Nodes which carve rivers and lakes into the terrain and fill them with water.
     */
    WATER("Water"),
    /**
     * Nodes which select parts of the terrain, for instance by height or slope, producing values between zero and one.
     */
    MASK("Masks"),
    /**
     * Nodes which paint terrain types, biomes or vegetation where their mask is strong.
     */
    SURFACE("Surface"),
    /**
     * The node which determines the final height of the terrain.
     */
    OUTPUT("Output");

    NodeCategory(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }

    private final String displayName;
}
