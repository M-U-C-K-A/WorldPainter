package org.pepsoft.worldpainter.nodes;

import java.util.List;

import static java.util.Collections.emptyList;

/**
 * A generator which produces the heights of the map as it is now, so that a graph can build on terrain which was
 * painted by hand (for instance by adding erosion or detail to it). Heights are converted to values with the height
 * range of the {@link OutputNode}; outside the map the value is zero.
 */
public class CurrentMapNode extends Node {
    @Override
    public String getName() {
        return "Current map";
    }

    @Override
    public NodeCategory getCategory() {
        return NodeCategory.GENERATOR;
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
        final double minHeight = context.getMinHeight(), verticalScale = context.getVerticalScale();
        return new CompiledNode.Generator() {
            @Override
            float value(double x, double y) {
                if (source == null) {
                    return 0;
                }
                final float height = source.getHeight((int) Math.floor(x + 0.5), (int) Math.floor(y + 0.5));
                return Float.isNaN(height) ? 0 : (float) ((height - minHeight) / verticalScale);
            }
        };
    }

    private static final long serialVersionUID = 1L;
}
