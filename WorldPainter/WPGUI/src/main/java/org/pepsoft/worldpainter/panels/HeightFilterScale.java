package org.pepsoft.worldpainter.panels;

import com.formdev.flatlaf.util.UIScale;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Shows which heights a brush applies at: a landscape on a scale of heights, with a bar for the "at or above" level and
 * one for the "at or below" level, and the land which will be affected in the accent colour. The bars can be dragged,
 * which also switches them on, and the two buttons below choose between the land between the bars and beyond them. This has nothing to do with the
 * selection on the map.
 */
public final class HeightFilterScale extends JComponent {
    public HeightFilterScale() {
        final MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    if (betweenButton().contains(e.getPoint())) {
                        chooseBetween();
                    } else if (beyondButton().contains(e.getPoint())) {
                        chooseBeyond();
                    } else {
                        pressed = barsNear(e.getY());
                        pressY = e.getY();
                        dragNumber++;
                        if (pressed.size() == 1) {
                            dragging = pressed.get(0);
                            drag(e.getY());
                        }
                    }
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if ((dragging == null) && (pressed.size() > 1) && (e.getY() != pressY)) {
                    // Both bars were under the mouse, so go by the direction: up takes the upper one, down the lower one
                    dragging = (e.getY() < pressY) ? upperBar() : lowerBar();
                }
                if (dragging != null) {
                    drag(e.getY());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragging = null;
                pressed = List.of();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                final List<Bar> near = barsNear(e.getY());
                final Bar newHover = near.isEmpty() ? null : near.get(0);
                if (newHover != hover) {
                    hover = newHover;
                    repaint();
                }
                final boolean onButton = betweenButton().contains(e.getPoint()) || beyondButton().contains(e.getPoint());
                setCursor(Cursor.getPredefinedCursor(onButton ? Cursor.HAND_CURSOR : ((newHover != null) ? Cursor.N_RESIZE_CURSOR : Cursor.DEFAULT_CURSOR)));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (hover != null) {
                    hover = null;
                    repaint();
                }
            }
        };
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
        org.pepsoft.worldpainter.help.ToolCard.install(this, org.pepsoft.worldpainter.help.OptionCards.heightLimits());
    }

    @Override
    public JToolTip createToolTip() {
        final JToolTip card = org.pepsoft.worldpainter.help.ToolCard.toolTipFor(this);
        return (card != null) ? card : super.createToolTip();
    }

    /**
     * Only on the title and the buttons, since a card would get in the way when dragging the bars.
     */
    @Override
    public String getToolTipText(MouseEvent event) {
        return scene().contains(event.getPoint()) ? null : super.getToolTipText(event);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * @param minHeight The lowest height on the scale, inclusive.
     * @param maxHeight The highest height on the scale, inclusive.
     */
    public void setRange(int minHeight, int maxHeight) {
        this.minHeight = minHeight;
        this.maxHeight = Math.max(minHeight + 1, maxHeight);
        repaint();
    }

    public void setLevels(boolean aboveActive, int above, boolean belowActive, int below) {
        this.aboveActive = aboveActive;
        this.above = above;
        this.belowActive = belowActive;
        this.below = below;
        repaint();
    }

    /**
     * A number which identifies the current drag of a bar, or zero if no bar is being dragged, so that all the changes
     * of one drag can be treated as one.
     */
    int getDragNumber() {
        return (dragging != null) ? dragNumber : 0;
    }

    @Override
    public Dimension getPreferredSize() {
        // No wider than it has to be, so that it does not make the panel wider, but it fills whatever width there is
        return new Dimension(UIScale.scale(MINIMUM_WIDTH), UIScale.scale(TITLE_HEIGHT + SCENE_HEIGHT + 2 * GAP + BUTTON_HEIGHT + CAPTION_HEIGHT));
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Short.MAX_VALUE, getPreferredSize().height);
    }

    @Override
    protected void paintComponent(Graphics g) {
        final Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setFont(smallFont());
            g2.setColor(colour("Label.foreground", Color.WHITE));
            g2.drawString("Height limits", 0, g2.getFontMetrics().getAscent());
            final Rectangle scene = scene();
            paintScene(g2, scene);
            paintAxis(g2, scene);
            paintBar(g2, scene, Bar.BELOW);
            paintBar(g2, scene, Bar.ABOVE);
            paintButton(g2, betweenButton(), "Between", isBetween());
            paintButton(g2, beyondButton(), "Beyond", isBeyond());
            g2.setColor(colour("WorldPainter.mutedForeground", Color.GRAY));
            final FontMetrics metrics = g2.getFontMetrics();
            final String caption = caption(metrics);
            g2.drawString(caption, Math.max(0, (getWidth() - metrics.stringWidth(caption)) / 2), getHeight() - metrics.getDescent());
        } finally {
            g2.dispose();
        }
    }

    private void paintScene(Graphics2D g2, Rectangle scene) {
        final Color accent = accent();
        g2.setColor(subtleBackground());
        g2.fill(scene);

        final Shape oldClip = g2.getClip();
        g2.clip(scene);
        final Path2D land = landOutline(scene);

        // The water in the valley, then the land, in a neutral colour
        final int sea = heightToY(scene, seaLevel());
        g2.setColor(new Color(0x3b82c4));
        g2.fillRect(scene.x, sea, scene.width, scene.y + scene.height - sea);
        final Path2D ground = new Path2D.Float(land);
        ground.lineTo(scene.x + scene.width, scene.y + scene.height);
        ground.lineTo(scene.x, scene.y + scene.height);
        ground.closePath();
        final Color foreground = colour("Label.foreground", Color.WHITE);
        g2.setColor(mix(colour("Panel.background", Color.DARK_GRAY), foreground, 0.32f));
        g2.fill(ground);

        // The heights which will be affected: lightly in the air, strongly on the land
        for (int[] zone: zones()) {
            final int top = heightToY(scene, zone[1]), bottom = heightToY(scene, zone[0]);
            final Rectangle band = new Rectangle(scene.x, top, scene.width, Math.max(1, bottom - top));
            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 46));
            g2.fill(band);
            final Shape clip = g2.getClip();
            g2.clip(band);
            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 60));
            g2.setStroke(new BasicStroke(1f));
            final int spacing = UIScale.scale(6);
            for (int x = scene.x - band.height; x < scene.x + scene.width; x += spacing) {
                g2.drawLine(x, bottom, x + band.height, top);
            }
            g2.setColor(accent);
            g2.fill(ground);
            g2.setClip(clip);
        }

        g2.setColor(foreground);
        g2.setStroke(new BasicStroke(UIScale.scale(1f)));
        g2.draw(land);
        g2.setClip(oldClip);

        g2.setColor(colour("Component.borderColor", new Color(0x3f3f46)));
        g2.drawRect(scene.x, scene.y, scene.width - 1, scene.height - 1);
    }

    private void paintAxis(Graphics2D g2, Rectangle scene) {
        final FontMetrics metrics = g2.getFontMetrics();
        final int range = maxHeight - minHeight;
        // A round step, with room for the labels
        final double pixelsPerBlock = (double) scene.height / range;
        final int minimumStep = (int) Math.ceil(metrics.getHeight() * 1.5 / pixelsPerBlock);
        int step = 1;
        for (int magnitude = 1; step < minimumStep; magnitude *= 10) {
            for (int factor: new int[] {1, 2, 5}) {
                step = factor * magnitude;
                if (step >= minimumStep) {
                    break;
                }
            }
        }
        final Color muted = colour("WorldPainter.mutedForeground", Color.GRAY);
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(muted);
        // The ends of the scale, which show how far it really goes, and the round numbers there is room for in between
        paintTick(g2, scene, metrics, minHeight);
        paintTick(g2, scene, metrics, maxHeight);
        final int bottomY = heightToY(scene, minHeight), topY = heightToY(scene, maxHeight);
        for (int height = Math.floorDiv(minHeight, step) * step; height <= maxHeight; height += step) {
            final int y = heightToY(scene, height);
            if ((height > minHeight) && (height < maxHeight) && (bottomY - y >= metrics.getHeight()) && (y - topY >= metrics.getHeight())) {
                paintTick(g2, scene, metrics, height);
            }
        }
    }

    private void paintTick(Graphics2D g2, Rectangle scene, FontMetrics metrics, int height) {
        final int y = heightToY(scene, height);
        g2.drawLine(scene.x - UIScale.scale(3), y, scene.x - 1, y);
        final String label = Integer.toString(height);
        final int baseline = Math.max(scene.y + metrics.getAscent(), Math.min(scene.y + scene.height, y + (metrics.getAscent() - metrics.getDescent()) / 2));
        g2.drawString(label, scene.x - UIScale.scale(5) - metrics.stringWidth(label), baseline);
    }

    private void paintBar(Graphics2D g2, Rectangle scene, Bar bar) {
        final boolean active = isActive(bar), highlighted = (hover == bar) || (dragging == bar);
        final int y = heightToY(scene, level(bar));
        final Color colour = active ? mix(accent(), colour("Label.foreground", Color.WHITE), highlighted ? 0.25f : 0.0f) : colour("WorldPainter.mutedForeground", Color.GRAY);
        g2.setColor(colour);
        final float width = UIScale.scale((active || highlighted) ? 2f : 1f);
        g2.setStroke(active ? new BasicStroke(width) : new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {UIScale.scale(4f), UIScale.scale(3f)}, 0f));
        g2.drawLine(scene.x, y, scene.x + scene.width - 1, y);

        // A label on the bar, kept apart from the other one
        final FontMetrics metrics = g2.getFontMetrics();
        final String label = ((bar == Bar.ABOVE) ? "≥ " : "≤ ") + level(bar);
        final int labelWidth = metrics.stringWidth(label) + UIScale.scale(8), labelHeight = metrics.getHeight();
        final Bar upper = upperBar();
        final int otherY = heightToY(scene, level((bar == Bar.ABOVE) ? Bar.BELOW : Bar.ABOVE));
        int labelY = y - labelHeight / 2;
        if (Math.abs(y - otherY) < labelHeight) {
            final int middle = (y + otherY) / 2;
            labelY = (bar == upper) ? (middle - labelHeight) : middle;
        }
        labelY = Math.max(scene.y, Math.min(scene.y + scene.height - labelHeight, labelY));
        final int labelX = scene.x + scene.width - labelWidth;
        if (active) {
            g2.fillRect(labelX, labelY, labelWidth, labelHeight);
            g2.setColor(Color.WHITE);
        } else {
            g2.setColor(subtleBackground());
            g2.fillRect(labelX, labelY, labelWidth, labelHeight);
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(colour);
            g2.drawRect(labelX, labelY, labelWidth - 1, labelHeight - 1);
        }
        g2.drawString(label, labelX + UIScale.scale(4), labelY + metrics.getAscent());
    }

    private void paintButton(Graphics2D g2, Rectangle bounds, String text, boolean selected) {
        g2.setColor(selected ? accent() : subtleBackground());
        g2.fill(bounds);
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(selected ? accent() : colour("Component.borderColor", new Color(0x3f3f46)));
        g2.drawRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1);
        g2.setColor(selected ? Color.WHITE : colour("Label.foreground", Color.WHITE));
        final FontMetrics metrics = g2.getFontMetrics();
        final Rectangle2D size = metrics.getStringBounds(text, g2);
        g2.drawString(text, bounds.x + (int) ((bounds.width - size.getWidth()) / 2), bounds.y + (bounds.height + metrics.getAscent() - metrics.getDescent()) / 2);
    }

    /**
     * The surface of a valley with a lake and a mountain, reaching from below the sea to near the top of the scale.
     */
    private Path2D landOutline(Rectangle scene) {
        final double sea = (seaLevel() - minHeight) / (double) (maxHeight - minHeight);
        final double top = Math.max(sea + 0.1, 0.86);
        final Path2D outline = new Path2D.Float();
        final int step = Math.max(1, UIScale.scale(2));
        for (int x = 0; x <= scene.width; x += step) {
            final double u = (double) x / scene.width;
            final double ground = sea + LANDSCAPE.at(u) * (top - sea);
            final double y = scene.y + (1 - ground) * scene.height;
            if (x == 0) {
                outline.moveTo(scene.x, y);
            } else {
                outline.lineTo(scene.x + x, y);
            }
        }
        // All the way to the edge
        outline.lineTo(scene.x + scene.width, scene.y + (1 - (sea + LANDSCAPE.at(1.0) * (top - sea))) * scene.height);
        return outline;
    }

    /**
     * The height ranges which will be affected, as {lowest, highest}.
     */
    private List<int[]> zones() {
        final List<int[]> zones = new ArrayList<>();
        if (isBetween()) {
            zones.add(new int[] {above, below});
        } else if (isBeyond()) {
            zones.add(new int[] {minHeight, below});
            zones.add(new int[] {above, maxHeight});
        } else if (aboveActive) {
            zones.add(new int[] {above, maxHeight});
        } else if (belowActive) {
            zones.add(new int[] {minHeight, below});
        }
        return zones;
    }

    /**
     * What the bars come down to, in as many words as there is room for.
     */
    private String caption(FontMetrics metrics) {
        final String caption;
        final String shortCaption;
        if (isBetween()) {
            caption = "Applies from " + above + " to " + below;
            shortCaption = above + " to " + below;
        } else if (isBeyond()) {
            caption = "Applies up to " + below + " and from " + above;
            shortCaption = "\u2264 " + below + " and \u2265 " + above;
        } else if (aboveActive) {
            caption = "Applies at " + above + " and above";
            shortCaption = above + " and above";
        } else if (belowActive) {
            caption = "Applies at " + below + " and below";
            shortCaption = below + " and below";
        } else {
            caption = "Applies at every height";
            shortCaption = "Every height";
        }
        return (metrics.stringWidth(caption) <= getWidth()) ? caption : shortCaption;
    }

    private boolean isBetween() {
        return aboveActive && belowActive && (below >= above);
    }

    private boolean isBeyond() {
        return aboveActive && belowActive && (below < above);
    }

    private void chooseBetween() {
        if (isBetween()) {
            notifyListener(false, above, false, below);
        } else {
            notifyListener(true, Math.min(above, below), true, Math.max(above, below));
        }
    }

    private void chooseBeyond() {
        if (isBeyond()) {
            notifyListener(false, above, false, below);
        } else {
            int low = Math.min(above, below), high = Math.max(above, below);
            if (low == high) {
                // Beyond needs at least one height between them which is left alone
                if (high < maxHeight) {
                    high++;
                } else {
                    low--;
                }
            }
            notifyListener(true, high, true, low);
        }
    }

    private void drag(int y) {
        final Rectangle scene = scene();
        int height = yToHeight(scene, y);
        final boolean otherActive = isActive((dragging == Bar.ABOVE) ? Bar.BELOW : Bar.ABOVE);
        if (otherActive) {
            // The bars don't cross; that is what the buttons are for
            if (isBetween()) {
                height = (dragging == Bar.ABOVE) ? Math.min(height, below) : Math.max(height, above);
            } else if (isBeyond()) {
                height = (dragging == Bar.ABOVE) ? Math.max(height, below + 1) : Math.min(height, above - 1);
            }
        }
        if (dragging == Bar.ABOVE) {
            notifyListener(true, height, belowActive, below);
        } else {
            notifyListener(aboveActive, above, true, height);
        }
    }

    private void notifyListener(boolean newAboveActive, int newAbove, boolean newBelowActive, int newBelow) {
        setLevels(newAboveActive, newAbove, newBelowActive, newBelow);
        if (listener != null) {
            listener.levelsChanged(newAboveActive, newAbove, newBelowActive, newBelow);
        }
    }

    private List<Bar> barsNear(int y) {
        final Rectangle scene = scene();
        if ((y < scene.y - UIScale.scale(GRAB)) || (y > scene.y + scene.height + UIScale.scale(GRAB))) {
            return List.of();
        }
        final int aboveDistance = Math.abs(heightToY(scene, above) - y), belowDistance = Math.abs(heightToY(scene, below) - y);
        final int reach = UIScale.scale(GRAB);
        final List<Bar> bars = new ArrayList<>(2);
        if (aboveDistance <= belowDistance) {
            if (aboveDistance <= reach) {
                bars.add(Bar.ABOVE);
            }
            if (belowDistance <= reach) {
                bars.add(Bar.BELOW);
            }
        } else {
            if (belowDistance <= reach) {
                bars.add(Bar.BELOW);
            }
            if (aboveDistance <= reach) {
                bars.add(Bar.ABOVE);
            }
        }
        return bars;
    }

    private Bar upperBar() {
        return (below >= above) ? Bar.BELOW : Bar.ABOVE;
    }

    private Bar lowerBar() {
        return (below >= above) ? Bar.ABOVE : Bar.BELOW;
    }

    private boolean isActive(Bar bar) {
        return (bar == Bar.ABOVE) ? aboveActive : belowActive;
    }

    private int level(Bar bar) {
        return (bar == Bar.ABOVE) ? above : below;
    }

    private int seaLevel() {
        return Math.max(minHeight, Math.min(maxHeight, SEA_LEVEL));
    }

    private Rectangle scene() {
        final FontMetrics metrics = getFontMetrics(smallFont());
        final int axis = Math.max(metrics.stringWidth(Integer.toString(minHeight)), metrics.stringWidth(Integer.toString(maxHeight))) + UIScale.scale(8);
        return new Rectangle(axis, UIScale.scale(TITLE_HEIGHT), Math.max(1, getWidth() - axis), UIScale.scale(SCENE_HEIGHT));
    }

    private Rectangle betweenButton() {
        final int y = UIScale.scale(TITLE_HEIGHT + SCENE_HEIGHT + GAP);
        return new Rectangle(0, y, getWidth() / 2, UIScale.scale(BUTTON_HEIGHT));
    }

    private Rectangle beyondButton() {
        final int y = UIScale.scale(TITLE_HEIGHT + SCENE_HEIGHT + GAP);
        return new Rectangle(getWidth() / 2 - 1, y, getWidth() - getWidth() / 2 + 1, UIScale.scale(BUTTON_HEIGHT));
    }

    private int heightToY(Rectangle scene, int height) {
        return scene.y + (int) Math.round((double) (maxHeight - height) / (maxHeight - minHeight) * (scene.height - 1));
    }

    private int yToHeight(Rectangle scene, int y) {
        final int height = maxHeight - (int) Math.round((double) (y - scene.y) / (scene.height - 1) * (maxHeight - minHeight));
        return Math.max(minHeight, Math.min(maxHeight, height));
    }

    private Font smallFont() {
        final Font font = UIManager.getFont("Label.font");
        return font.deriveFont(font.getSize2D() - 2f);
    }

    private static Color accent() {
        Color accent = UIManager.getColor("WorldPainter.accent");
        if (accent == null) {
            accent = UIManager.getColor("Component.accentColor");
        }
        return (accent != null) ? accent : new Color(0xf97316);
    }

    private static Color subtleBackground() {
        return mix(colour("Panel.background", Color.DARK_GRAY), colour("Label.foreground", Color.WHITE), 0.1f);
    }

    private static Color colour(String key, Color fallback) {
        final Color colour = UIManager.getColor(key);
        return (colour != null) ? colour : fallback;
    }

    private static Color mix(Color from, Color to, float amount) {
        return new Color((int) (from.getRed() + (to.getRed() - from.getRed()) * amount), (int) (from.getGreen() + (to.getGreen() - from.getGreen()) * amount), (int) (from.getBlue() + (to.getBlue() - from.getBlue()) * amount));
    }

    private Listener listener;
    private int minHeight = 0, maxHeight = 255, above, below = 255;
    private boolean aboveActive, belowActive;
    private Bar hover, dragging;
    private List<Bar> pressed = List.of();
    private int pressY, dragNumber;

    private static final int MINIMUM_WIDTH = 110, TITLE_HEIGHT = 16, SCENE_HEIGHT = 124, BUTTON_HEIGHT = 20, CAPTION_HEIGHT = 14, GAP = 4, GRAB = 6, SEA_LEVEL = 62;

    /**
     * The shape of the land, in fractions of the height from the sea to the top of the mountain.
     */
    private static final Landscape LANDSCAPE = new Landscape(new double[][] {
            {0.00, 0.10}, {0.10, 0.20}, {0.17, 0.02}, {0.24, -0.42}, {0.32, -0.42}, {0.39, 0.04}, {0.48, 0.30},
            {0.56, 0.38}, {0.64, 0.68}, {0.72, 1.00}, {0.80, 0.78}, {0.88, 0.90}, {1.00, 0.62}});

    private enum Bar {ABOVE, BELOW}

    private static final class Landscape {
        Landscape(double[][] points) {
            this.points = points;
        }

        /**
         * The height at a horizontal position from 0 to 1: a smooth curve through the points, with a few small bumps.
         */
        double at(double u) {
            int i = 0;
            while ((i < points.length - 2) && (u > points[i + 1][0])) {
                i++;
            }
            final double[] p0 = points[Math.max(0, i - 1)], p1 = points[i], p2 = points[i + 1], p3 = points[Math.min(points.length - 1, i + 2)];
            final double t = (u - p1[0]) / (p2[0] - p1[0]);
            final double curve = 0.5 * ((2 * p1[1]) + (-p0[1] + p2[1]) * t + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t * t + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t * t * t);
            final double bumps = (Math.sin(u * 47) * 0.012 + Math.sin(u * 113 + 1.3) * 0.007) * Math.max(0, curve);
            return curve + bumps;
        }

        private final double[][] points;
    }

    public interface Listener {
        void levelsChanged(boolean aboveActive, int above, boolean belowActive, int below);
    }
}
