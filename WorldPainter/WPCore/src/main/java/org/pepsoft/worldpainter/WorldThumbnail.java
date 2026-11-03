package org.pepsoft.worldpainter;

import org.pepsoft.worldpainter.biomeschemes.CustomBiomeManager;
import org.pepsoft.worldpainter.layers.Layer;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.HashSet;
import java.util.Set;

import static java.awt.RenderingHints.*;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE_BITS;

/**
 * A small picture of a world, kept at the start of its .world file so that the file browser of the operating system
 * can show the map instead of a blank page, as it does for photos (packaging/macos holds the Quick Look extension which
 * shows it in the Finder).
 *
 * <p>A .world file is a gzip stream. The picture is a JPEG image in a subfield with the ID "WP" of the extra field of
 * the gzip header (RFC 1952, section 2.3.1.1), which every reader of gzip streams skips, including the
 * {@link java.util.zip.GZIPInputStream} of older versions of WorldPainter. It can be read without decompressing
 * anything.
 */
public final class WorldThumbnail {
    private WorldThumbnail() {
        // Prevent instantiation
    }

    /**
     * Draw a world as it looks in the editor, scaled down to fit in {@link #MAX_SIZE} pixels, on a dark background
     * where the world has no tiles.
     *
     * @param dimension The dimension to draw, normally the surface.
     * @param colourScheme The colours of the materials.
     * @param customBiomeManager The custom biomes of the world.
     * @param hiddenLayers The layers to leave out, as in the editor.
     * @return The picture.
     */
    public static BufferedImage render(Dimension dimension, ColourScheme colourScheme, CustomBiomeManager customBiomeManager, Set<Layer> hiddenLayers) {
        final int widthInBlocks = dimension.getWidth() << TILE_SIZE_BITS, heightInBlocks = dimension.getHeight() << TILE_SIZE_BITS;
        // Draw at the smallest power of two scale that is still at least as large as the picture, then scale smoothly
        int zoom = 0;
        while ((zoom > -7) && ((Math.max(widthInBlocks, heightInBlocks) >> (1 - zoom)) >= MAX_SIZE)) {
            zoom--;
        }
        final int tileSize = TILE_SIZE >> -zoom;
        final TileRenderer renderer = new TileRenderer(dimension, colourScheme, customBiomeManager, zoom, true, null);
        renderer.setContourLines(false);
        renderer.setHiddenLayers(new HashSet<>(hiddenLayers));
        final BufferedImage map = new BufferedImage(dimension.getWidth() * tileSize, dimension.getHeight() * tileSize, BufferedImage.TYPE_INT_ARGB);
        final int xOffset = dimension.getLowestX(), yOffset = dimension.getLowestY();
        for (Tile tile: dimension.getTiles()) {
            renderer.renderTile(tile, map, (tile.getX() - xOffset) * tileSize, (tile.getY() - yOffset) * tileSize);
        }

        final float scale = Math.min(1f, (float) MAX_SIZE / Math.max(map.getWidth(), map.getHeight()));
        final int width = Math.max(Math.round(map.getWidth() * scale), 1), height = Math.max(Math.round(map.getHeight() * scale), 1);
        final BufferedImage thumbnail = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g2 = thumbnail.createGraphics();
        try {
            g2.setColor(new Color(BACKGROUND));
            g2.fillRect(0, 0, width, height);
            g2.setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(KEY_RENDERING, VALUE_RENDER_QUALITY);
            g2.drawImage(map, 0, 0, width, height, null);
        } finally {
            g2.dispose();
        }
        return thumbnail;
    }

    /**
     * Encode a picture as a JPEG image small enough for the extra field of a gzip header, lowering the quality and then
     * the size until it fits.
     *
     * @param image The picture.
     * @return The JPEG image, or {@code null} if it could not be made small enough.
     */
    public static byte[] encode(BufferedImage image) throws IOException {
        for (int size = Math.max(image.getWidth(), image.getHeight()); size >= 128; size = size * 3 / 4) {
            final BufferedImage scaled = scale(image, size);
            for (float quality: new float[] {0.85f, 0.7f, 0.55f}) {
                final byte[] jpeg = toJpeg(scaled, quality);
                if (jpeg.length <= MAX_BYTES) {
                    return jpeg;
                }
            }
        }
        return null;
    }

    /**
     * Read the picture of a world from a .world file, without loading the world.
     *
     * @param file The .world file.
     * @return The JPEG image, or {@code null} if the file has none (because it was saved by an older version of
     * WorldPainter, for instance).
     */
    public static byte[] read(File file) throws IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            return read(in);
        }
    }

    /**
     * Read the picture of a world from the start of a stream holding a .world file.
     *
     * @param in The stream, which is left just after the extra field.
     * @return The JPEG image, or {@code null} if the stream has none.
     */
    public static byte[] read(InputStream in) throws IOException {
        final DataInputStream dataIn = new DataInputStream(in);
        final byte[] header = new byte[HEADER_SIZE];
        try {
            dataIn.readFully(header);
            if (((header[0] & 0xff) != 0x1f) || ((header[1] & 0xff) != 0x8b) || ((header[3] & FEXTRA) == 0)) {
                return null;
            }
            final byte[] extra = new byte[dataIn.readUnsignedByte() | (dataIn.readUnsignedByte() << 8)];
            dataIn.readFully(extra);
            for (int i = 0; i + 4 <= extra.length; ) {
                final int length = (extra[i + 2] & 0xff) | ((extra[i + 3] & 0xff) << 8);
                if ((extra[i] == 'W') && (extra[i + 1] == 'P') && (i + 4 + length <= extra.length)) {
                    final byte[] jpeg = new byte[length];
                    System.arraycopy(extra, i + 4, jpeg, 0, length);
                    return jpeg;
                }
                i += 4 + length;
            }
            return null;
        } catch (EOFException e) {
            return null;
        }
    }

    /**
     * Wrap a stream so that the gzip header which a {@link java.util.zip.GZIPOutputStream} writes to it gets an extra
     * field holding a picture.
     *
     * @param out The stream to which the gzip stream will be written.
     * @param jpeg The picture, as made by {@link #encode(BufferedImage)}.
     * @return The stream to give to the {@code GZIPOutputStream}.
     */
    static OutputStream withThumbnail(OutputStream out, byte[] jpeg) {
        if (jpeg.length > MAX_BYTES) {
            throw new IllegalArgumentException("Thumbnail too large: " + jpeg.length + " bytes");
        }
        return new FilterOutputStream(out) {
            @Override
            public void write(int b) throws IOException {
                if (position < HEADER_SIZE) {
                    // Announce the extra field in the flags
                    out.write((position == 3) ? (b | FEXTRA) : b);
                    position++;
                    if (position == HEADER_SIZE) {
                        // The extra field comes right after the fixed part of the header
                        final int length = jpeg.length + 4;
                        out.write(new byte[] {(byte) length, (byte) (length >> 8), 'W', 'P', (byte) jpeg.length, (byte) (jpeg.length >> 8)});
                        out.write(jpeg);
                    }
                } else {
                    out.write(b);
                }
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                while ((position < HEADER_SIZE) && (len > 0)) {
                    write(b[off++] & 0xff);
                    len--;
                }
                if (len > 0) {
                    out.write(b, off, len);
                }
            }

            private int position;
        };
    }

    private static BufferedImage scale(BufferedImage image, int size) {
        if (Math.max(image.getWidth(), image.getHeight()) <= size) {
            return image;
        }
        final float scale = (float) size / Math.max(image.getWidth(), image.getHeight());
        final BufferedImage scaled = new BufferedImage(Math.max(Math.round(image.getWidth() * scale), 1), Math.max(Math.round(image.getHeight() * scale), 1), BufferedImage.TYPE_INT_RGB);
        final Graphics2D g2 = scaled.createGraphics();
        try {
            g2.setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_BICUBIC);
            g2.drawImage(image, 0, 0, scaled.getWidth(), scaled.getHeight(), null);
        } finally {
            g2.dispose();
        }
        return scaled;
    }

    private static byte[] toJpeg(BufferedImage image, float quality) throws IOException {
        final ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try {
            final ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(bytes)) {
                writer.setOutput(out);
                writer.write(null, new IIOImage(image, null, null), param);
            }
            return bytes.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    /**
     * The largest width or height of the picture, in pixels.
     */
    public static final int MAX_SIZE = 768;

    /**
     * The largest JPEG image which fits in the extra field of a gzip header, after the four bytes of the subfield
     * header.
     */
    static final int MAX_BYTES = 65535 - 4;

    private static final int HEADER_SIZE = 10, FEXTRA = 0x04, BACKGROUND = 0x26282b;
}
