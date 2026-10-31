package org.pepsoft.worldpainter.help;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

/**
 * The cards of the options of a brush, each with little pictures which compare the choices side by side.
 */
public final class OptionCards {
    private OptionCards() {
        // Prevent instantiation
    }

    public static ToolCard whereOnTheMap() {
        return new ToolCard("Where on the map", "Limits the brush to part of the map which you selected with a selection tool, such as Select Rectangle.", null,
                new Panels(new String[] {"Whole map", "Inside", "Outside"}, OptionCards::paintSelection), null)
                .tip("This is about the selection on the map. The height limits below work separately, and on top of it.");
    }

    public static ToolCard heightLimits() {
        return new ToolCard("Height limits", "Limits the brush to land at certain heights, whichever part of the map it is on.", null,
                new Panels(new String[] {"Between", "Beyond"}, OptionCards::paintHeights), null)
                .action("Drag a bar", "Set that limit and switch it on")
                .action("Between", "Only the land between the bars")
                .action("Beyond", "Only the land below and above them");
    }

    public static ToolCard fade() {
        return new ToolCard("Fade at the limits", "Lets the brush fade out over the 4 blocks beyond the height limits, instead of stopping abruptly, which avoids a visible line.", null,
                new Panels(new String[] {"Off", "On"}, OptionCards::paintFade), null);
    }

    public static ToolCard onlyOn() {
        return new ToolCard("Only on", "The brush only changes the map where it already has the terrain types or layers you choose.", null,
                new Panels(new String[] {"Without", "Only on stone"}, OptionCards::paintOnlyOn), null)
                .tip("For instance: put snow only on stone.");
    }

    public static ToolCard exceptOn() {
        return new ToolCard("Except on", "The brush leaves the terrain types or layers you choose alone, and changes everything else.", null,
                new Panels(new String[] {"Without", "Except on stone"}, OptionCards::paintExceptOn), null)
                .tip("For instance: paint grass without covering the paths.");
    }

    public static ToolCard slope() {
        return new ToolCard("Slope", "Limits the brush to land which is steeper or flatter than a number of degrees.", null,
                new Panels(new String[] {"Above 45°", "Below 45°"}, OptionCards::paintSlope), null)
                .tip("For instance: paint rock only on steep slopes.");
    }

    public static ToolCard intensity() {
        return new ToolCard("Intensity", "How strongly the brush works: how quickly a height tool changes the land, and how densely a layer or terrain is painted.", null,
                new Panels(new String[] {"25 %", "50 %", "100 %"}, OptionCards::paintIntensity), null);
    }

    public static ToolCard rotation() {
        return new ToolCard("Rotation", "Turns the brush, which matters for brushes which aren't round.", null,
                new Panels(new String[] {"0°", "45°", "90°"}, OptionCards::paintRotation), null)
                .action("Alt+scroll", "On the map: rotate it")
                .action("[  and  ]", "Rotate it with the keyboard");
    }

    // The pictures. Each paints one choice, on a panel with its origin at the top left

    private static void paintSelection(Graphics2D g2, int index, int w, int h) {
        g2.setColor(GRASS);
        g2.fillRect(0, 0, w, h);
        final Rectangle2D selection = new Rectangle2D.Float(w * 0.2f, h * 0.22f, w * 0.6f, h * 0.56f);
        final Shape oldClip = g2.getClip();
        if (index == 1) {
            g2.clip(selection);
        } else if (index == 2) {
            final Area outside = new Area(new Rectangle(0, 0, w, h));
            outside.subtract(new Area(selection));
            g2.clip(outside);
        }
        paintStroke(g2, w, h);
        g2.setClip(oldClip);
        if (index != 0) {
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {3f, 3f}, 0f));
            g2.draw(selection);
        }
    }

    private static void paintHeights(Graphics2D g2, int index, int w, int h) {
        g2.setColor(SKY);
        g2.fillRect(0, 0, w, h);
        final Path2D land = landscape(w, h);
        g2.setColor(GROUND);
        g2.fill(land);
        final int upper = (int) (h * 0.32f), lower = (int) (h * 0.68f);
        final Shape oldClip = g2.getClip();
        if (index == 0) {
            g2.clip(new Rectangle(0, upper, w, lower - upper));
        } else {
            final Area beyond = new Area(new Rectangle(0, 0, w, upper));
            beyond.add(new Area(new Rectangle(0, lower, w, h - lower)));
            g2.clip(beyond);
        }
        g2.setColor(ACCENT);
        g2.fill(land);
        g2.setClip(oldClip);
        g2.setColor(ACCENT);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawLine(0, upper, w, upper);
        g2.drawLine(0, lower, w, lower);
    }

    private static void paintFade(Graphics2D g2, int index, int w, int h) {
        g2.setColor(SKY);
        g2.fillRect(0, 0, w, h);
        final Path2D land = landscape(w, h);
        g2.setColor(GROUND);
        g2.fill(land);
        final int limit = (int) (h * 0.5f);
        final Shape oldClip = g2.getClip();
        g2.clip(land);
        if (index == 0) {
            g2.setColor(ACCENT);
            g2.fillRect(0, 0, w, limit);
        } else {
            g2.setPaint(new GradientPaint(0, limit - h * 0.16f, ACCENT, 0, limit + h * 0.16f, new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 0)));
            g2.fillRect(0, 0, w, h);
        }
        g2.setClip(oldClip);
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {3f, 3f}, 0f));
        g2.drawLine(0, limit, w, limit);
    }

    private static void paintOnlyOn(Graphics2D g2, int index, int w, int h) {
        paintGroundWithStone(g2, w, h);
        final Shape oldClip = g2.getClip();
        if (index == 1) {
            g2.clip(stone(w, h));
        }
        paintStroke(g2, w, h);
        g2.setClip(oldClip);
    }

    private static void paintExceptOn(Graphics2D g2, int index, int w, int h) {
        paintGroundWithStone(g2, w, h);
        final Shape oldClip = g2.getClip();
        if (index == 1) {
            final Area rest = new Area(new Rectangle(0, 0, w, h));
            rest.subtract(new Area(stone(w, h)));
            g2.clip(rest);
        }
        paintStroke(g2, w, h);
        g2.setClip(oldClip);
    }

    private static void paintSlope(Graphics2D g2, int index, int w, int h) {
        g2.setColor(SKY);
        g2.fillRect(0, 0, w, h);
        // A plateau with steep sides
        final float[][] points = {{0, 0.82f}, {0.2f, 0.78f}, {0.4f, 0.26f}, {0.62f, 0.22f}, {0.8f, 0.74f}, {1, 0.78f}};
        final Path2D land = new Path2D.Float();
        land.moveTo(0, h);
        for (float[] point: points) {
            land.lineTo(point[0] * w, point[1] * h);
        }
        land.lineTo(w, h);
        land.closePath();
        g2.setColor(GROUND);
        g2.fill(land);
        g2.setStroke(new BasicStroke(Math.max(3f, h * 0.06f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(ACCENT);
        for (int i = 0; i < points.length - 1; i++) {
            final boolean steep = (i == 1) || (i == 3);
            if (steep == (index == 0)) {
                g2.drawLine((int) (points[i][0] * w), (int) (points[i][1] * h), (int) (points[i + 1][0] * w), (int) (points[i + 1][1] * h));
            }
        }
    }

    private static void paintIntensity(Graphics2D g2, int index, int w, int h) {
        g2.setColor(GRASS);
        g2.fillRect(0, 0, w, h);
        // A dab of paint with more or fewer dots in it, as it comes out on the map
        final double density = new double[] {0.25, 0.5, 1.0}[index];
        final int size = Math.max(2, Math.round(h / 28f));
        final double radius = Math.min(w, h) * 0.38;
        g2.setColor(ACCENT);
        for (int y = 0; y < h; y += size) {
            for (int x = 0; x < w; x += size) {
                final double dx = x - w / 2.0, dy = y - h / 2.0;
                if ((dx * dx + dy * dy < radius * radius) && (noise(x / size, y / size) < density)) {
                    g2.fillRect(x, y, size, size);
                }
            }
        }
    }

    private static void paintRotation(Graphics2D g2, int index, int w, int h) {
        g2.setColor(SKY);
        g2.fillRect(0, 0, w, h);
        final Graphics2D rotated = (Graphics2D) g2.create();
        try {
            rotated.translate(w / 2.0, h / 2.0);
            rotated.rotate(Math.toRadians(new int[] {0, 45, 90}[index]));
            final float length = Math.min(w, h) * 0.8f, width = length * 0.34f;
            rotated.setColor(ACCENT);
            rotated.fill(new Ellipse2D.Float(-length / 2, -width / 2, length, width));
            rotated.setColor(Color.WHITE);
            rotated.fill(new Ellipse2D.Float(length / 2 - width * 0.7f, -width * 0.2f, width * 0.4f, width * 0.4f));
        } finally {
            rotated.dispose();
        }
    }

    private static void paintGroundWithStone(Graphics2D g2, int w, int h) {
        g2.setColor(GRASS);
        g2.fillRect(0, 0, w, h);
        g2.setColor(STONE);
        g2.fill(stone(w, h));
    }

    private static Shape stone(int w, int h) {
        return new Ellipse2D.Float(w * 0.3f, h * 0.12f, w * 0.42f, h * 0.76f);
    }

    /**
     * A stroke of the brush across a panel.
     */
    private static void paintStroke(Graphics2D g2, int w, int h) {
        final Path2D stroke = new Path2D.Float();
        stroke.moveTo(-w * 0.1f, h * 0.7f);
        stroke.curveTo(w * 0.3f, h * 0.15f, w * 0.55f, h * 0.95f, w * 1.1f, h * 0.3f);
        g2.setColor(ACCENT);
        g2.setStroke(new BasicStroke(h * 0.32f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g2.draw(stroke);
    }

    /**
     * A valley and a mountain, down to the bottom of the panel.
     */
    private static Path2D landscape(int w, int h) {
        final Path2D land = new Path2D.Float();
        land.moveTo(0, h);
        land.lineTo(0, h * 0.72f);
        land.curveTo(w * 0.2f, h * 0.62f, w * 0.3f, h * 0.92f, w * 0.48f, h * 0.76f);
        land.curveTo(w * 0.62f, h * 0.62f, w * 0.68f, h * 0.06f, w * 0.82f, h * 0.1f);
        land.curveTo(w * 0.9f, h * 0.14f, w * 0.95f, h * 0.3f, w, h * 0.34f);
        land.lineTo(w, h);
        land.closePath();
        return land;
    }

    private static double noise(int x, int y) {
        final double value = Math.sin(x * 12.9898 + y * 78.233) * 43758.5453;
        return value - Math.floor(value);
    }

    private static final Color SKY = new Color(0x26262b), GROUND = new Color(0x6b6b70), GRASS = new Color(0x4f7a3f), STONE = new Color(0x8b8b8f), ACCENT = new Color(0xf97316);

    /**
     * Paints one of the choices in a picture.
     */
    private interface ChoicePainter {
        void paint(Graphics2D g2, int index, int width, int height);
    }

    /**
     * Pictures next to each other with a label under each one.
     */
    private static final class Panels implements ToolCard.Preview {
        Panels(String[] labels, ChoicePainter painter) {
            this.labels = labels;
            this.painter = painter;
        }

        @Override
        public boolean isAnimated() {
            return false;
        }

        @Override
        public void paint(Graphics2D g2, int width, int height, double time) {
            g2.setColor(new Color(0x18181b));
            g2.fillRect(0, 0, width, height);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            final Font base = UIManager.getFont("Label.font");
            g2.setFont(base.deriveFont(base.getSize2D() - 1f));
            final FontMetrics metrics = g2.getFontMetrics();
            final int margin = Math.round(width * 0.03f), gap = margin;
            final int panelWidth = (width - 2 * margin - (labels.length - 1) * gap) / labels.length;
            final int panelHeight = height - 2 * margin - metrics.getHeight() - margin / 2;
            for (int i = 0; i < labels.length; i++) {
                final int x = margin + i * (panelWidth + gap);
                final Graphics2D panel = (Graphics2D) g2.create(x, margin, panelWidth, panelHeight);
                try {
                    panel.clipRect(0, 0, panelWidth, panelHeight);
                    painter.paint(panel, i, panelWidth, panelHeight);
                } finally {
                    panel.dispose();
                }
                g2.setColor(new Color(0xe4e4e7));
                g2.drawString(labels[i], x + (panelWidth - metrics.stringWidth(labels[i])) / 2, height - margin - metrics.getDescent());
            }
        }

        private final String[] labels;
        private final ChoicePainter painter;
    }
}
