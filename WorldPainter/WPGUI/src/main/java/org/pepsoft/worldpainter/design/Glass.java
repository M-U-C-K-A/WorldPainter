package org.pepsoft.worldpainter.design;

import com.formdev.flatlaf.util.UIScale;

import javax.swing.*;
import javax.swing.plaf.LayerUI;
import javax.swing.plaf.UIResource;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ContainerEvent;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * The liquid glass of the Glass theme, after the Liquid Glass of Apple: a vivid backdrop of colours melting into each
 * other behind the windows, and on it panes of clear glass which catch the light: a bright specular rim, strongest at
 * their top left and with a reflection at their bottom right, a glow just inside their edges, as thick glass bends the
 * light there, and a soft shadow. For the gradient to show, the panels between the window and the surfaces are made see
 * through while the Glass theme is in use, and opaque again when switching to another theme.
 */
public final class Glass {
    private Glass() {
        // Prevent instantiation
    }

    /**
     * Paint the gradient behind a window, with a light in its top left corner and another in its bottom right one.
     */
    public static void paintBackdrop(Graphics2D g2, int width, int height) {
        g2.setPaint(new GradientPaint(0, 0, colour("WorldPainter.glass.backdropTop", Design.background()), width * 0.3f, height, colour("WorldPainter.glass.backdropBottom", Design.background())));
        g2.fillRect(0, 0, width, height);
        // Colours melting into each other, like the wallpapers behind Liquid Glass
        final float radius = Math.max(width, height) * 0.6f;
        paintGlow(g2, colour("WorldPainter.glass.glow", null), width * 0.05f, height * 0.02f, radius, width, height);
        paintGlow(g2, colour("WorldPainter.glass.glow2", null), width * 0.98f, height * 0.98f, radius * 0.8f, width, height);
        paintGlow(g2, colour("WorldPainter.glass.glow3", null), width * 0.92f, height * 0.08f, radius * 0.7f, width, height);
        paintGlow(g2, colour("WorldPainter.glass.glow4", null), width * 0.12f, height * 0.95f, radius * 0.75f, width, height);
        paintGlow(g2, colour("WorldPainter.glass.glow5", null), width * 0.55f, height * 0.5f, radius * 0.45f, width, height);
    }

    /**
     * Paint a piece of liquid glass in a shape: clear glass (or glass tinted with a colour), a glow just inside its
     * edge where the thick glass bends the light, and a specular rim, bright at the top left with a reflection at the
     * bottom right. Nothing is painted outside the shape.
     *
     * @param tint The colour of tinted glass, such as that of a default button, or {@code null} for clear glass.
     */
    public static void paintLiquid(Graphics2D g2, Shape shape, Color tint) {
        final Graphics2D g = (Graphics2D) g2.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            final Rectangle2D bounds = shape.getBounds2D();
            final float x = (float) bounds.getX(), y = (float) bounds.getY(), width = (float) bounds.getWidth(), height = (float) bounds.getHeight();
            // The glass, lighter at the top
            if (tint != null) {
                g.setPaint(new GradientPaint(0, y, lighten(tint, 0.18f), 0, y + height, tint));
            } else {
                g.setPaint(new GradientPaint(0, y, colour("WorldPainter.glass.surfaceTop", Design.surface()), 0, y + Math.min(height, UIScale.scale(120f)), colour("WorldPainter.glass.surface", Design.surface())));
            }
            g.fill(shape);
            // The glow inside the edge: the light gathered by the rounded edge of thick glass
            g.clip(shape);
            final Color glow = colour("WorldPainter.glass.innerGlow", new Color(255, 255, 255, 90));
            // (Thinner on small things, such as buttons, than on panes)
            final float glowWidth = Math.min(Math.min(width, height) / 5f, UIScale.scale(7f));
            for (int i = 0; i < 4; i++) {
                final float t = (i + 1) / 4f;
                g.setStroke(new BasicStroke(glowWidth * 2 * t));
                g.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), Math.round(glow.getAlpha() * (1 - t) * 0.6f)));
                g.draw(shape);
            }
            // A faint shade along the inside of the bottom edge
            g.setPaint(new GradientPaint(0, y + height - UIScale.scale(5f), new Color(0, 0, 0, 0), 0, y + height, colour("WorldPainter.glass.innerShade", new Color(0, 0, 0, 18))));
            g.fill(new Rectangle2D.Float(x, y + height - UIScale.scale(5f), width, UIScale.scale(5f)));
            // The specular rim: bright where the light comes from (the top left), dim along the sides, and reflected
            // at the bottom right
            final Color specular = colour("WorldPainter.glass.specular", Color.WHITE);
            g.setStroke(new BasicStroke(Math.max(UIScale.scale(1f), Math.min(UIScale.scale(2.2f), Math.min(width, height) / 14f))));
            g.setPaint(new LinearGradientPaint(new Point2D.Float(x, y), new Point2D.Float(x + width, y + height), new float[] {0f, 0.32f, 0.68f, 1f},
                    new Color[] {withAlpha(specular, 1f), withAlpha(specular, 0.18f), withAlpha(specular, 0.12f), withAlpha(specular, 0.7f)}));
            g.draw(shape);
        } finally {
            g.dispose();
        }
    }

    private static Color withAlpha(Color colour, float factor) {
        return new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), Math.round(colour.getAlpha() * factor));
    }

    private static Color lighten(Color colour, float amount) {
        return new Color(Math.round(colour.getRed() + (255 - colour.getRed()) * amount), Math.round(colour.getGreen() + (255 - colour.getGreen()) * amount),
                Math.round(colour.getBlue() + (255 - colour.getBlue()) * amount), colour.getAlpha());
    }

    /**
     * Paint a surface of frosted glass: translucent, lighter at the top, with a light edge and a soft shadow around
     * it, outside its bounds.
     */
    public static void paintSurface(Graphics2D g2, float x, float y, float width, float height, float arc) {
        final Graphics2D g = (Graphics2D) g2.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            final RoundRectangle2D shape = new RoundRectangle2D.Float(x, y, width, height, arc, arc);
            paintShadow(g, shape);
            paintLiquid(g, shape, null);
        } finally {
            g.dispose();
        }
    }

    /**
     * Wrap something which paints itself, such as the map, in a pane of glass: inset from its surroundings, with
     * rounded corners, a light edge and a soft shadow. Elsewhere it is shown as it is.
     */
    public static JComponent frame(JComponent content) {
        return new Frame(content);
    }

    /**
     * A soft shadow around a shape, outside it only, as the glass lets through what is under it.
     */
    private static void paintShadow(Graphics2D g2, RoundRectangle2D shape) {
        final int size = UIScale.scale(SHADOW_SIZE);
        final Graphics2D g = (Graphics2D) g2.create();
        try {
            final Rectangle bounds = shape.getBounds();
            final Area outside = new Area(new Rectangle(bounds.x - size - 1, bounds.y - size - 1, bounds.width + 2 * size + 2, bounds.height + 3 * size + 2));
            outside.subtract(new Area(shape));
            g.clip(outside);
            final Color shadow = colour("WorldPainter.glass.shadow", Color.BLACK);
            final float strength = Design.isDark() ? 0.30f : 0.075f;
            final float x = (float) shape.getX(), y = (float) shape.getY(), width = (float) shape.getWidth(), height = (float) shape.getHeight(), arc = (float) shape.getArcWidth();
            for (int i = size; i >= 1; i--) {
                final float t = 1f - (float) i / (size + 1);
                g.setColor(new Color(shadow.getRed(), shadow.getGreen(), shadow.getBlue(), Math.round(255 * strength * t * t / 2)));
                g.fill(new RoundRectangle2D.Float(x - i, y - i + i / 2f, width + 2 * i, height + 2 * i, arc + 2 * i, arc + 2 * i));
            }
        } finally {
            g.dispose();
        }
    }

    /**
     * The edge of a pane of glass, where the light catches it.
     */
    private static void paintEdge(Graphics2D g, RoundRectangle2D shape) {
        final float x = (float) shape.getX(), y = (float) shape.getY(), width = (float) shape.getWidth(), height = (float) shape.getHeight(), arc = (float) shape.getArcWidth();
        g.setPaint(new GradientPaint(0, y, colour("WorldPainter.glass.edge", Design.surfaceBorder()), 0, y + height, colour("WorldPainter.glass.edgeBottom", Design.surfaceBorder())));
        g.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, width - 1, height - 1, arc - 1, arc - 1));
    }

    /**
     * The specular rim alone, around something which is not glass itself, such as the map.
     */
    private static void paintRim(Graphics2D g, RoundRectangle2D shape) {
        final float x = (float) shape.getX(), y = (float) shape.getY(), width = (float) shape.getWidth(), height = (float) shape.getHeight();
        final Color specular = colour("WorldPainter.glass.specular", Color.WHITE);
        g.setStroke(new BasicStroke(UIScale.scale(2.5f)));
        g.setPaint(new LinearGradientPaint(new Point2D.Float(x, y), new Point2D.Float(x + width, y + height), new float[] {0f, 0.3f, 0.7f, 1f},
                new Color[] {withAlpha(specular, 1f), withAlpha(specular, 0.25f), withAlpha(specular, 0.2f), withAlpha(specular, 0.8f)}));
        g.draw(shape);
    }

    /**
     * Make the panels in a container see through if the Glass theme is in use, or opaque again if it is not.
     */
    public static void update(Container container) {
        update(container, Design.isGlass());
    }

    private static void update(Container container, boolean glass) {
        for (Component child: container.getComponents()) {
            if (child instanceof JComponent component) {
                if (glass) {
                    if (component.isOpaque() && canSeeThrough(component)) {
                        component.setOpaque(false);
                        component.putClientProperty(CLEARED, Boolean.TRUE);
                    }
                } else if (component.getClientProperty(CLEARED) != null) {
                    component.setOpaque(true);
                    component.putClientProperty(CLEARED, null);
                }
            }
            if ((child instanceof Container) && (! (child instanceof CellRendererPane))) {
                update((Container) child, glass);
            }
        }
    }

    /**
     * Whether a component only paints the background of the window behind what it holds, so that it may be see
     * through. Lists, tables, text and the like keep their own background.
     */
    private static boolean canSeeThrough(JComponent component) {
        final Color background = component.getBackground();
        if ((background != null) && (! (background instanceof UIResource))) {
            return false;
        } else if (component instanceof JViewport viewport) {
            final Component view = viewport.getView();
            return ! ((view instanceof JList) || (view instanceof JTable) || (view instanceof JTree) || (view instanceof JTextComponent));
        } else {
            return (component instanceof JPanel) || (component instanceof JTabbedPane) || (component instanceof JScrollPane)
                    || (component instanceof JCheckBox) || (component instanceof JRadioButton) || (component instanceof JSlider)
                    || (component instanceof JLabel) || (component instanceof JToolBar) || (component instanceof JScrollBar);
        }
    }

    private static void paintGlow(Graphics2D g2, Color colour, float x, float y, float radius, int width, int height) {
        if (colour == null) {
            return;
        }
        g2.setPaint(new RadialGradientPaint(new Point2D.Float(x, y), radius, new float[] {0f, 1f}, new Color[] {colour, new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), 0)}));
        g2.fillRect(0, 0, width, height);
    }

    private static Color colour(String key, Color fallback) {
        final Color colour = UIManager.getColor(key);
        return (colour != null) ? colour : fallback;
    }

    /**
     * Panels which see through to the gradient behind them, in the Glass theme. Elsewhere it is an ordinary panel. Panels
     * added to it later are made see through too.
     */
    public static class Backdrop extends JPanel {
        public Backdrop(LayoutManager layout) {
            super(layout);
            installListener();
        }

        @Override
        public void updateUI() {
            super.updateUI();
            // After the components in it have been updated too
            SwingUtilities.invokeLater(() -> Glass.update(this));
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (Design.isGlass()) {
                final Graphics2D g2 = (Graphics2D) g.create();
                try {
                    paintBackdrop(g2, getWidth(), getHeight());
                } finally {
                    g2.dispose();
                }
            } else {
                super.paintComponent(g);
            }
        }

        private static synchronized void installListener() {
            if (! listenerInstalled) {
                Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
                    if ((event.getID() == ContainerEvent.COMPONENT_ADDED) && Design.isGlass()) {
                        final Component child = ((ContainerEvent) event).getChild();
                        if ((child instanceof Container) && (SwingUtilities.getAncestorOfClass(Backdrop.class, child) != null)) {
                            updateAdded((Container) child);
                        }
                    }
                }, AWTEvent.CONTAINER_EVENT_MASK);
                listenerInstalled = true;
            }
        }

        private static void updateAdded(Container child) {
            if ((child instanceof JComponent component) && component.isOpaque() && canSeeThrough(component)) {
                component.setOpaque(false);
                component.putClientProperty(CLEARED, Boolean.TRUE);
            }
            Glass.update(child, true);
        }

        private static boolean listenerInstalled;
        private static final long serialVersionUID = 1L;
    }

    /**
     * The pane of glass around the map. Its corners are painted over with the backdrop after its content, in a layer,
     * so that they stay rounded when only the content is repainted.
     */
    private static final class Frame extends JPanel {
        Frame(JComponent content) {
            super(new BorderLayout());
            putClientProperty(KEY_FRAME, Boolean.TRUE);
            layer = new JLayer<>(content, new LayerUI<>() {
                @Override
                public void paint(Graphics g, JComponent c) {
                    super.paint(g, c);
                    if (Design.isGlass()) {
                        final Graphics2D g2 = (Graphics2D) g.create();
                        try {
                            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                            final float arc = UIScale.scale(FRAME_ARC);
                            paintCorners(g2, c, arc);
                            paintRim(g2, new RoundRectangle2D.Float(0.5f, 0.5f, c.getWidth() - 1, c.getHeight() - 1, arc, arc));
                        } finally {
                            g2.dispose();
                        }
                    }
                }
            });
            add(layer, BorderLayout.CENTER);
            applyStyle();
        }

        @Override
        public void updateUI() {
            super.updateUI();
            if (layer != null) {
                applyStyle();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (Design.isGlass()) {
                final Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    final Rectangle bounds = layer.getBounds();
                    final float arc = UIScale.scale(FRAME_ARC);
                    paintShadow(g2, new RoundRectangle2D.Float(bounds.x, bounds.y, bounds.width, bounds.height, arc, arc));
                } finally {
                    g2.dispose();
                }
            }
        }

        private void applyStyle() {
            final int margin = UIScale.scale(FRAME_MARGIN);
            setBorder(Design.isGlass() ? BorderFactory.createEmptyBorder(UIScale.scale(2), margin, margin, margin) : null);
        }

        /**
         * Paint the corners outside the rounded shape with the backdrop under them, a small piece at a time.
         */
        private static void paintCorners(Graphics2D g2, JComponent c, float arc) {
            final Backdrop backdrop = (Backdrop) SwingUtilities.getAncestorOfClass(Backdrop.class, c);
            if (backdrop == null) {
                return;
            }
            final int width = c.getWidth(), height = c.getHeight(), size = (int) Math.ceil(arc / 2) + 1;
            final Point offset = SwingUtilities.convertPoint(c, 0, 0, backdrop);
            final Area outside = new Area(new Rectangle(0, 0, width, height));
            outside.subtract(new Area(new RoundRectangle2D.Float(0, 0, width, height, arc, arc)));
            final Rectangle clip = g2.getClipBounds();
            for (Point corner: new Point[] {new Point(0, 0), new Point(width - size, 0), new Point(0, height - size), new Point(width - size, height - size)}) {
                final Rectangle square = new Rectangle(corner.x, corner.y, size, size);
                if ((clip != null) && (! clip.intersects(square))) {
                    continue;
                }
                final BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
                final Graphics2D imageGraphics = image.createGraphics();
                try {
                    imageGraphics.translate(-(offset.x + square.x), -(offset.y + square.y));
                    paintBackdrop(imageGraphics, backdrop.getWidth(), backdrop.getHeight());
                } finally {
                    imageGraphics.dispose();
                }
                final Area piece = new Area(square);
                piece.intersect(outside);
                g2.setPaint(new TexturePaint(image, square));
                g2.fill(piece);
            }
        }

        private final JLayer<JComponent> layer;
        private static final long serialVersionUID = 1L;
    }

    /**
     * The client property by which the glass frame around the map can be recognised.
     */
    public static final String KEY_FRAME = "WorldPainter.glass.frame";
    private static final String CLEARED = "WorldPainter.glass.cleared";
    private static final int SHADOW_SIZE = 10, FRAME_MARGIN = 8;
    private static final float FRAME_ARC = 20f;
}
