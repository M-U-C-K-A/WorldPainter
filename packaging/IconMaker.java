import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Renders the WorldPainter application icon (blocky painted terrain on a rounded square) at 1024x1024 pixels.
 *
 * <p>Usage: {@code java IconMaker.java <output.png>}
 */
public class IconMaker {
    public static void main(String[] args) throws Exception {
        final int size = 1024;
        final BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Rounded square with the standard macOS margins
        final float margin = size * 0.09f, side = size - 2 * margin;
        final Shape plate = new RoundRectangle2D.Float(margin, margin, side, side, side * 0.45f, side * 0.45f);
        g.setColor(new Color(0, 0, 0, 70));
        g.fill(new RoundRectangle2D.Float(margin, margin + 12, side, side, side * 0.45f, side * 0.45f));
        g.setClip(plate);
        g.setPaint(new GradientPaint(0, margin, new Color(0x7cc4f2), 0, margin + side, new Color(0xd8f0ff)));
        g.fill(plate);

        // Blocky terrain: columns of a fixed width with heights following a few smooth curves
        final int block = 32;
        final int x0 = (int) margin, x1 = (int) (margin + side);
        // Far mountain
        for (int x = x0; x < x1; x += block) {
            final double t = (x - x0) / side;
            final int top = (int) (margin + side * (0.62 - 0.36 * Math.exp(-Math.pow((t - 0.62) / 0.2, 2))));
            final int snapped = (top / block) * block;
            g.setColor(new Color(0x8b939c));
            g.fillRect(x, snapped, block, size);
            if (snapped < margin + side * 0.42) {
                g.setColor(new Color(0xf4f7fa));
                g.fillRect(x, snapped, block, (int) (margin + side * 0.42) - snapped);
            }
        }
        // Water
        g.setColor(new Color(0x3f76e4));
        g.fillRect(x0, (int) (margin + side * 0.74), (int) side, size);
        // Near hills with grass and dirt
        for (int x = x0; x < x1; x += block) {
            final double t = (x - x0) / side;
            final double h = 0.68 + 0.09 * Math.sin(t * 6.0 + 0.5) - 0.12 * Math.exp(-Math.pow((t - 0.2) / 0.16, 2));
            if (t > 0.08 && t < 0.38) {
                continue; // a bay
            }
            final int top = ((int) (margin + side * h) / block) * block;
            g.setColor(new Color(0x7a5230));
            g.fillRect(x, top, block, size);
            g.setColor(new Color(0x5fa83a));
            g.fillRect(x, top, block, block);
            g.setColor(new Color(0x4c8c2c));
            g.fillRect(x, top + block - 6, block, 6);
        }
        g.dispose();
        ImageIO.write(image, "png", new File(args[0]));
    }
}
