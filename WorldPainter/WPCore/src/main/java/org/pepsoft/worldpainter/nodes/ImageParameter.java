package org.pepsoft.worldpainter.nodes;

/**
 * A greyscale image, such as an imported height map.
 */
public final class ImageParameter extends Parameter<HeightImage> {
    public ImageParameter(String key, String label, String description) {
        super(key, label, description, null);
    }

    @Override
    Class<?> getValueClass() {
        return HeightImage.class;
    }
}
