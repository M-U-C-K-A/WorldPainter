package org.pepsoft.worldpainter.nodes;

import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.Serializable;

/**
 * An immutable greyscale image with 16 bits of precision, stored in a node graph, for instance an imported height map.
 */
public final class HeightImage implements Serializable {
    public HeightImage(String name, int width, int height, char[] data) {
        if ((width <= 0) || (height <= 0) || (data.length != width * height)) {
            throw new IllegalArgumentException("Invalid image size " + width + "x" + height);
        }
        this.name = name;
        this.width = width;
        this.height = height;
        this.data = data;
    }

    /**
     * Convert an image to greyscale. Sixteen bit greyscale images keep their precision; colour images are converted
     * using their luminance.
     */
    public static HeightImage fromImage(String name, BufferedImage image) {
        final int width = image.getWidth(), height = image.getHeight();
        final char[] data = new char[width * height];
        final Raster raster = image.getRaster();
        if ((raster.getNumBands() == 1) && (raster.getSampleModel().getSampleSize(0) == 16)) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    data[x + y * width] = (char) raster.getSample(x, y, 0);
                }
            }
        } else if ((raster.getNumBands() == 1) && (raster.getSampleModel().getSampleSize(0) <= 8)) {
            final int max = (1 << raster.getSampleModel().getSampleSize(0)) - 1;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    data[x + y * width] = (char) (raster.getSample(x, y, 0) * 65535 / max);
                }
            }
        } else {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    final int rgb = image.getRGB(x, y);
                    final int luminance = (((rgb >> 16) & 0xff) * 299 + ((rgb >> 8) & 0xff) * 587 + (rgb & 0xff) * 114) / 1000;
                    data[x + y * width] = (char) (luminance * 257);
                }
            }
        }
        return new HeightImage(name, width, height, data);
    }

    public String getName() {
        return name;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    /**
     * The value of a pixel, between zero and one.
     */
    public float get(int x, int y) {
        return data[x + y * width] / 65535f;
    }

    @Override
    public String toString() {
        return name + " (" + width + "x" + height + ")";
    }

    private final String name;
    private final int width, height;
    private final char[] data;

    private static final long serialVersionUID = 1L;
}
