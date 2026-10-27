import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Turns the square application artwork into an application icon: the artwork in a rounded square with the standard
 * macOS margins and a soft shadow, on a transparent 1024x1024 canvas.
 *
 * <p>Usage: {@code java IconMaker.java <artwork.png> <icon.png>}
 */
public class IconMaker {
    public static void main(String[] args) throws Exception {
        final BufferedImage artwork = ImageIO.read(new File(args[0]));
        final int size = 1024;
        final float margin = size * 0.098f, side = size - 2 * margin, radius = side * 0.45f;
        final BufferedImage icon = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = icon.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        // Soft shadow
        for (int i = 12; i > 0; i--) {
            g.setColor(new Color(0, 0, 0, 5));
            g.fill(new RoundRectangle2D.Float(margin - i / 2f, margin + 10 - i / 2f + 4, side + i, side + i, radius + i, radius + i));
        }
        final Shape plate = new RoundRectangle2D.Float(margin, margin, side, side, radius, radius);
        g.setClip(plate);
        g.drawImage(artwork, Math.round(margin), Math.round(margin), Math.round(side), Math.round(side), null);
        g.setClip(null);
        // Subtle edge highlight
        g.setColor(new Color(255, 255, 255, 40));
        g.setStroke(new BasicStroke(2f));
        g.draw(plate);
        g.dispose();
        ImageIO.write(icon, "png", new File(args[1]));
    }
}
