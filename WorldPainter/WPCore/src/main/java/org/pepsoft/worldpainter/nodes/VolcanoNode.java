package org.pepsoft.worldpainter.nodes;

import org.pepsoft.worldpainter.procedural.SimplexNoise;

import java.awt.*;
import java.util.List;

import static java.util.Collections.emptyList;

/**
 * A single volcano: a concave cone with a crater at the top and gullies running down its flanks, placed at a position
 * on the map (as a fraction of its width and height).
 */
public class VolcanoNode extends Node {
    @Override
    public String getName() {
        return "Volcano";
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
        return List.of(POSITION_X, POSITION_Y, RADIUS, CRATER, GULLIES, VARIANT);
    }

    @Override
    CompiledNode compile(CompileContext context, CompiledNode[] inputs) {
        final long seed = seed(context, getInt(VARIANT));
        final SimplexNoise noise = new SimplexNoise(seed);
        final Rectangle bounds = context.getMapBounds();
        final double centreX = (bounds != null) ? (bounds.x + bounds.width * getDouble(POSITION_X)) : 0;
        final double centreY = (bounds != null) ? (bounds.y + bounds.height * getDouble(POSITION_Y)) : 0;
        final double radius = getDouble(RADIUS), crater = getDouble(CRATER), gullies = getDouble(GULLIES);
        return new CompiledNode.Generator() {
            @Override
            float value(double x, double y) {
                final double dx = x - centreX, dy = y - centreY;
                final double angle = Math.atan2(dy, dx);
                // An irregular outline
                final double outline = 1 + 0.18 * noise.noise(Math.cos(angle) * 1.5, Math.sin(angle) * 1.5);
                final double t = Math.hypot(dx, dy) / (radius * outline);
                if (t >= 1) {
                    return 0;
                }
                // A concave cone, steepening towards the top
                double h = Math.pow(1 - t, 1.8);
                // Gullies down the flanks: ridged noise around the cone, fading out near the top and the foot
                final double ridges = 1 - Math.abs(noise.noise(Math.cos(angle) * 9 + t * 2, Math.sin(angle) * 9 - t * 2));
                h *= 1 - gullies * 0.25 * ridges * Math.sin(Math.PI * Math.min(1, t * 1.2));
                // The crater
                if (t < crater) {
                    final double c = t / crater;
                    final double rim = Math.pow(1 - crater, 1.8);
                    h = rim - (rim * 0.35) * (1 - c * c);
                }
                return (float) h;
            }
        };
    }

    public static final NumberParameter POSITION_X = new NumberParameter("positionX", "Position across", "Where the volcano is, from the west (0) to the east (1) edge of the map", 0.5, 0, 1, 0.01, false, null);
    public static final NumberParameter POSITION_Y = new NumberParameter("positionY", "Position down", "Where the volcano is, from the north (0) to the south (1) edge of the map", 0.5, 0, 1, 0.01, false, null);
    public static final NumberParameter RADIUS = new NumberParameter("radius", "Radius", "The radius of the foot of the volcano", 400, 16, 8192, 1, true, "blocks");
    public static final NumberParameter CRATER = new NumberParameter("crater", "Crater", "The size of the crater relative to the volcano", 0.12, 0, 0.5, 0.01, false, null);
    public static final NumberParameter GULLIES = new NumberParameter("gullies", "Gullies", "How deeply the flanks are furrowed", 0.6, 0, 1, 0.01, false, null);

    private static final long serialVersionUID = 1L;
}
