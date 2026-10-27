package org.pepsoft.worldpainter.nodes;

import java.util.List;

import static java.util.Collections.emptyList;

/**
 * A mask which is one where the map is selected and zero elsewhere. Add a blur to soften its edges.
 */
public class SelectionMaskNode extends Node {
    @Override
    public String getName() {
        return "Selection";
    }

    @Override
    public NodeCategory getCategory() {
        return NodeCategory.MASK;
    }

    @Override
    public List<String> getInputNames() {
        return emptyList();
    }

    @Override
    public List<Parameter<?>> getParameters() {
        return emptyList();
    }

    @Override
    CompiledNode compile(CompileContext context, CompiledNode[] inputs) {
        final MapSource source = context.getMapSource();
        return new CompiledNode.Generator() {
            @Override
            float value(double x, double y) {
                return ((source != null) && source.isSelected((int) Math.floor(x + 0.5), (int) Math.floor(y + 0.5))) ? 1 : 0;
            }
        };
    }

    private static final long serialVersionUID = 1L;
}
