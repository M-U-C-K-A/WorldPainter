package org.pepsoft.worldpainter.nodes;

import java.awt.*;
import java.util.List;

import static java.util.Collections.emptyList;

/**
 * A generator which uses an imported greyscale image, for instance a real world height map. Black is zero and white
 * is one.
 */
public class ImageNode extends Node {
    @Override
    public String getName() {
        return "Image";
    }

    @Override
    public String getTitle() {
        final HeightImage image = get(IMAGE);
        return (image != null) ? ("Image: " + image.getName()) : "Image";
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
        return List.of(IMAGE, FIT_TO_MAP, X, Y, SCALE, OUTSIDE);
    }

    @Override
    public boolean isRelevant(Parameter<?> parameter) {
        return ((parameter != X) && (parameter != Y) && (parameter != SCALE)) || (! get(FIT_TO_MAP));
    }

    @Override
    CompiledNode compile(CompileContext context, CompiledNode[] inputs) {
        final HeightImage image = get(IMAGE);
        if (image == null) {
            return new CompiledNode.Generator() {
                @Override
                float value(double x, double y) {
                    return 0;
                }
            };
        }
        final int width = image.getWidth(), height = image.getHeight();
        final Rectangle bounds = context.getMapBounds();
        final double originX, originY, scaleX, scaleY;
        if (get(FIT_TO_MAP) && (bounds != null)) {
            originX = bounds.getX();
            originY = bounds.getY();
            scaleX = bounds.getWidth() / width;
            scaleY = bounds.getHeight() / height;
        } else {
            originX = getDouble(X);
            originY = getDouble(Y);
            scaleX = getDouble(SCALE);
            scaleY = scaleX;
        }
        final Outside outside = get(OUTSIDE);
        return new CompiledNode.Generator() {
            @Override
            float value(double x, double y) {
                // Pixel centres are at half pixel offsets
                final double u = (x - originX) / scaleX - 0.5, v = (y - originY) / scaleY - 0.5;
                if ((outside == Outside.ZERO) && ((u < -0.5) || (v < -0.5) || (u > width - 0.5) || (v > height - 0.5))) {
                    return 0;
                }
                final int x0 = (int) Math.floor(u), y0 = (int) Math.floor(v);
                final float fx = (float) (u - x0), fy = (float) (v - y0);
                final float top = pixel(x0, y0) + (pixel(x0 + 1, y0) - pixel(x0, y0)) * fx;
                final float bottom = pixel(x0, y0 + 1) + (pixel(x0 + 1, y0 + 1) - pixel(x0, y0 + 1)) * fx;
                return top + (bottom - top) * fy;
            }

            private float pixel(int x, int y) {
                if (outside == Outside.REPEAT) {
                    return image.get(Math.floorMod(x, width), Math.floorMod(y, height));
                } else {
                    return image.get(Math.max(0, Math.min(width - 1, x)), Math.max(0, Math.min(height - 1, y)));
                }
            }
        };
    }

    public static final ImageParameter IMAGE = new ImageParameter("image", "Image", "A greyscale image; black is the lowest and white the highest");
    public static final BooleanParameter FIT_TO_MAP = new BooleanParameter("fitToMap", "Fit to map", "Stretch the image to cover the whole map", true);
    public static final NumberParameter X = new NumberParameter("x", "Left", "The X coordinate of the left edge of the image", 0, -1000000, 1000000, 1, false, "blocks");
    public static final NumberParameter Y = new NumberParameter("y", "Top", "The Y coordinate of the top edge of the image", 0, -1000000, 1000000, 1, false, "blocks");
    public static final NumberParameter SCALE = new NumberParameter("scale", "Scale", "The number of blocks per pixel", 1, 0.0625, 64, 0.0625, true, "blocks/pixel");
    public static final ChoiceParameter<Outside> OUTSIDE = new ChoiceParameter<>("outside", "Outside", "What to produce outside the image", Outside.EDGE, Outside.values());

    private static final long serialVersionUID = 1L;

    public enum Outside {
        ZERO("Zero"), EDGE("Extend edges"), REPEAT("Repeat");

        Outside(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }

        private final String displayName;
    }
}
