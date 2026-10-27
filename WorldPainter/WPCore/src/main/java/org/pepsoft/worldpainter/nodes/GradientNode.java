package org.pepsoft.worldpainter.nodes;

import java.awt.*;
import java.util.List;

import static java.util.Collections.emptyList;

/**
 * A generator of a radial, square or linear gradient, by default fitted to the map. Useful as a mask, for instance to
 * create an island.
 */
public class GradientNode extends Node {
    public GradientNode() {
        set(SHAPE, Shape.RADIAL);
    }

    @Override
    public String getName() {
        return "Gradient";
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
        return List.of(SHAPE, FIT_TO_MAP, CENTRE_X, CENTRE_Y, SIZE, ANGLE, SMOOTH);
    }

    @Override
    public boolean isRelevant(Parameter<?> parameter) {
        if ((parameter == CENTRE_X) || (parameter == CENTRE_Y) || (parameter == SIZE)) {
            return ! get(FIT_TO_MAP);
        } else if (parameter == ANGLE) {
            return get(SHAPE) == Shape.LINEAR;
        } else {
            return true;
        }
    }

    @Override
    CompiledNode compile(CompileContext context, CompiledNode[] inputs) {
        final Rectangle bounds = context.getMapBounds();
        final double cx, cy, rx, ry;
        if (get(FIT_TO_MAP) && (bounds != null)) {
            cx = bounds.getCenterX();
            cy = bounds.getCenterY();
            rx = bounds.getWidth() / 2;
            ry = bounds.getHeight() / 2;
        } else {
            cx = getDouble(CENTRE_X);
            cy = getDouble(CENTRE_Y);
            rx = getDouble(SIZE);
            ry = rx;
        }
        final Shape shape = get(SHAPE);
        final boolean smooth = get(SMOOTH);
        final double angle = Math.toRadians(getDouble(ANGLE)), cos = Math.cos(angle), sin = Math.sin(angle);
        final double halfLength = Math.abs(rx * cos) + Math.abs(ry * sin);
        return new CompiledNode.Generator() {
            @Override
            float value(double x, double y) {
                final double dx = x - cx, dy = y - cy;
                final double t;
                switch (shape) {
                    case RADIAL:
                        t = 1 - Math.sqrt((dx / rx) * (dx / rx) + (dy / ry) * (dy / ry));
                        break;
                    case SQUARE:
                        t = 1 - Math.max(Math.abs(dx / rx), Math.abs(dy / ry));
                        break;
                    case LINEAR:
                        t = 0.5 + (dx * cos + dy * sin) / (2 * halfLength);
                        break;
                    default:
                        throw new InternalError();
                }
                final double clamped = Math.max(0, Math.min(1, t));
                return (float) (smooth ? (clamped * clamped * (3 - 2 * clamped)) : clamped);
            }
        };
    }

    public static final ChoiceParameter<Shape> SHAPE = new ChoiceParameter<>("shape", "Shape", "Radial and square gradients are highest in the centre; linear gradients rise in the chosen direction", Shape.RADIAL, Shape.values());
    public static final BooleanParameter FIT_TO_MAP = new BooleanParameter("fitToMap", "Fit to map", "Centre the gradient on the map and size it to reach the edges", true);
    public static final NumberParameter CENTRE_X = new NumberParameter("centreX", "Centre X", "The X coordinate of the centre", 0, -1000000, 1000000, 1, false, "blocks");
    public static final NumberParameter CENTRE_Y = new NumberParameter("centreY", "Centre Y", "The Y coordinate of the centre", 0, -1000000, 1000000, 1, false, "blocks");
    public static final NumberParameter SIZE = new NumberParameter("size", "Radius", "The distance from the centre at which the gradient reaches zero (or half the length of a linear gradient)", 1024, 16, 65536, 1, true, "blocks");
    public static final NumberParameter ANGLE = new NumberParameter("angle", "Direction", "The direction in which a linear gradient rises (0\u00b0 = east, 90\u00b0 = south)", 0, 0, 360, 1, false, "\u00b0");
    public static final BooleanParameter SMOOTH = new BooleanParameter("smooth", "Smooth", "Ease the gradient in and out instead of changing linearly", true);

    private static final long serialVersionUID = 1L;

    public enum Shape {
        RADIAL("Radial"), SQUARE("Square"), LINEAR("Linear");

        Shape(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }

        private final String displayName;
    }
}
