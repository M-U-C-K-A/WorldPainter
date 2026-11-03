package org.pepsoft.worldpainter.nodes;

import java.util.List;

import static java.util.Collections.emptyList;

/**
 * A ready made landscape in one node: a few nodes which are often wired together, such as noise shaped by a curve and
 * carved by erosion, preconfigured, with only the settings which matter most. The parts are compiled as if they were
 * wired in a graph, so a landform gives the same terrain as the nodes it consists of.
 */
public abstract class LandformNode extends Node {
    @Override
    public NodeCategory getCategory() {
        return NodeCategory.LANDFORM;
    }

    @Override
    public List<String> getInputNames() {
        return emptyList();
    }

    /**
     * Create a noise node which is part of this landform, with a variant derived from that of this node.
     */
    NoiseNode noise(NoiseNode.Kind kind, double scale, int octaves, double roughness, double warp, int variantOffset) {
        final NoiseNode node = new NoiseNode(kind);
        node.set(NoiseNode.SCALE, scale);
        node.set(NoiseNode.OCTAVES, octaves);
        node.set(NoiseNode.ROUGHNESS, roughness);
        node.set(NoiseNode.WARP, warp);
        node.set(VARIANT, getInt(VARIANT) + variantOffset);
        return node;
    }

    /**
     * Compile a node which is part of this landform, with the given inputs.
     */
    static CompiledNode part(CompileContext context, Node node, CompiledNode... inputs) {
        return node.compile(context, inputs);
    }

    private static final long serialVersionUID = 1L;
}
