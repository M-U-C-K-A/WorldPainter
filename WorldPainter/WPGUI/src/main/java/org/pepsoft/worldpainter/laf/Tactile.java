package org.pepsoft.worldpainter.laf;

import com.formdev.flatlaf.util.UIScale;

import javax.swing.*;
import javax.swing.plaf.UIResource;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Paints the raised surfaces of the Tactile theme: buttons that stand half out of the window, with a lighter top, a
 * darker lip along their bottom edge, a thin highlight along their top and a soft shadow, as if they could be pressed;
 * and the hollows into which sliders are sunk. The colours come from the {@code Tactile.*} keys of the theme.
 */
public final class Tactile {
    private Tactile() {
        // Prevent instantiation
    }

    /**
     * The colours of one kind of raised surface.
     */
    record Colours(Color top, Color bottom, Color lip, Color border, Color shadow, Color highlight) {
        static Colours of(String prefix) {
            return new Colours(colour(prefix + ".top", Color.WHITE), colour(prefix + ".bottom", Color.LIGHT_GRAY), colour(prefix + ".lip", Color.GRAY),
                    colour(prefix + ".border", Color.GRAY), colour("Tactile.shadow", new Color(0, 0, 0, 40)), colour("Tactile.highlight", new Color(255, 255, 255, 170)));
        }
    }

    /**
     * Paint a key, like that of a mechanical keyboard: a body whose sides show around a slightly smaller top face, the
     * thickest side along the bottom, lit from above, with a thin highlight along the top of the face and a soft shadow
     * under it.
     *
     * @param pressed Whether it is pushed in: the sides almost disappear and the face moves down.
     * @param hover   Whether the mouse is over it: it lightens a little.
     */
    static void paintRaised(Graphics2D g2, float x, float y, float width, float height, float arc, Colours colours, boolean pressed, boolean hover, boolean enabled) {
        final float bottomSide = UIScale.scale(pressed ? 1.5f : 4.5f), side = UIScale.scale(pressed ? 1f : 1.6f), topSide = UIScale.scale(1f);
        final float sink = pressed ? UIScale.scale(2.5f) : 0f;
        final Composite savedComposite = g2.getComposite();
        if (! enabled) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
        }
        try {
            // A soft shadow under the key, stronger close to it
            if (! pressed) {
                g2.setColor(withAlpha(colours.shadow, 0.45f));
                g2.fill(new RoundRectangle2D.Float(x, y + UIScale.scale(2.5f), width, height, arc, arc));
                g2.setColor(withAlpha(colours.shadow, 1f));
                g2.fill(new RoundRectangle2D.Float(x + UIScale.scale(0.5f), y + UIScale.scale(1f), width - UIScale.scale(1f), height, arc, arc));
            }
            // The body: its sides, darker towards the bottom
            final float bodyTop = y + sink;
            g2.setPaint(new GradientPaint(0, bodyTop, colours.bottom, 0, y + height, colours.lip));
            g2.fill(new RoundRectangle2D.Float(x, bodyTop, width, height - sink, arc, arc));
            // The face, a little smaller, lit from above
            final float faceX = x + side, faceY = bodyTop + topSide, faceWidth = width - 2 * side, faceHeight = height - sink - topSide - bottomSide;
            final float faceArc = Math.max(0, arc - 2 * side);
            Color top = colours.top, bottom = colours.bottom;
            if (hover && (! pressed)) {
                top = lighten(top, 0.07f);
                bottom = lighten(bottom, 0.07f);
            } else if (pressed) {
                top = darken(top, 0.03f);
                bottom = darken(bottom, 0.03f);
            }
            g2.setPaint(new GradientPaint(0, faceY, top, 0, faceY + faceHeight, bottom));
            g2.fill(new RoundRectangle2D.Float(faceX, faceY, faceWidth, faceHeight, faceArc, faceArc));
            // A thin highlight along the top of the face, fading out downwards
            final Shape savedClip = g2.getClip();
            g2.clip(new RoundRectangle2D.Float(faceX, faceY, faceWidth, faceHeight, faceArc, faceArc));
            g2.setPaint(new GradientPaint(0, faceY, colours.highlight, 0, faceY + Math.min(faceHeight, UIScale.scale(7f)), withAlpha(colours.highlight, 0f)));
            g2.setStroke(new BasicStroke(UIScale.scale(1.2f)));
            g2.draw(new RoundRectangle2D.Float(faceX + UIScale.scale(0.6f), faceY + UIScale.scale(0.6f), faceWidth - UIScale.scale(1.2f), faceHeight + faceArc, faceArc, faceArc));
            g2.setClip(savedClip);
            // The edge between the face and the sides, and the outline of the key
            g2.setColor(withAlpha(colours.lip, 0.55f));
            g2.setStroke(new BasicStroke(UIScale.scale(0.8f)));
            g2.draw(new RoundRectangle2D.Float(faceX, faceY, faceWidth, faceHeight, faceArc, faceArc));
            g2.setColor(colours.border);
            g2.setStroke(new BasicStroke(UIScale.scale(1f)));
            g2.draw(new RoundRectangle2D.Float(x + 0.5f, bodyTop + 0.5f, width - 1, height - sink - 1, arc, arc));
        } finally {
            g2.setComposite(savedComposite);
        }
    }

    /**
     * Paint a small light: dark when off, glowing in its colour when on.
     */
    static void paintLed(Graphics2D g2, float centreX, float centreY, float radius, Color colour, boolean on) {
        if (on) {
            g2.setPaint(new RadialGradientPaint(centreX, centreY, radius * 3.2f, new float[] {0f, 1f}, new Color[] {withAlpha(colour, 0.55f), withAlpha(colour, 0f)}));
            g2.fill(new java.awt.geom.Ellipse2D.Float(centreX - radius * 3.2f, centreY - radius * 3.2f, radius * 6.4f, radius * 6.4f));
            g2.setPaint(new RadialGradientPaint(centreX - radius * 0.3f, centreY - radius * 0.3f, radius, new float[] {0f, 0.45f, 1f}, new Color[] {Color.WHITE, lighten(colour, 0.25f), colour}));
        } else {
            final Color off = colour("Tactile.led.off", new Color(0x9a9a9a));
            g2.setPaint(new RadialGradientPaint(centreX - radius * 0.3f, centreY - radius * 0.4f, radius * 1.2f, new float[] {0f, 1f}, new Color[] {lighten(off, 0.3f), darken(off, 0.25f)}));
        }
        g2.fill(new java.awt.geom.Ellipse2D.Float(centreX - radius, centreY - radius, 2 * radius, 2 * radius));
    }

    /**
     * The colour of the light of a button: that of the kind of tool it is for (sculpt, paint, water, selection), or the
     * accent.
     */
    static Color ledColour(JComponent component) {
        final Object kind = component.getClientProperty(KEY_KIND);
        final Color accent = colour("Component.accentColor", new Color(0xea6a2a));
        return (kind != null) ? colour("Tactile.led." + kind, accent) : accent;
    }

    /**
     * Paint a hollow sunk into the surface, such as the groove of a slider.
     */
    static void paintSunken(Graphics2D g2, float x, float y, float width, float height, float arc) {
        final Color fill = colour("Tactile.sunken.fill", new Color(0xe6e6e6)), shade = colour("Tactile.sunken.shade", new Color(0, 0, 0, 30));
        g2.setColor(fill);
        g2.fill(new RoundRectangle2D.Float(x, y, width, height, arc, arc));
        // Shaded along the top inside, as the light falls in from above
        final Shape savedClip = g2.getClip();
        g2.clip(new RoundRectangle2D.Float(x, y, width, height, arc, arc));
        g2.setPaint(new GradientPaint(0, y, shade, 0, y + UIScale.scale(6f), withAlpha(shade, 0f)));
        g2.fill(new Rectangle.Float(x, y, width, UIScale.scale(6f)));
        g2.setClip(savedClip);
        // Lit along the bottom edge outside, and outlined
        g2.setColor(colour("Tactile.highlight", new Color(255, 255, 255, 170)));
        g2.setStroke(new BasicStroke(UIScale.scale(1f)));
        g2.draw(new RoundRectangle2D.Float(x + 0.5f, y + 1.5f, width - 1, height - 1, arc, arc));
        g2.setColor(colour("Tactile.sunken.border", new Color(0xd0d0d0)));
        g2.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, width - 1, height - 1, arc, arc));
    }

    /**
     * Paint the background of a button of the Tactile theme, if it is one the theme paints itself.
     *
     * @return Whether it was painted; if not, FlatLaf should paint it.
     */
    static boolean paintButtonBackground(Graphics g, AbstractButton button) {
        if (! paints(button)) {
            return false;
        }
        final ButtonModel model = button.getModel();
        final boolean pressed = model.isArmed() && model.isPressed(), selected = model.isSelected(), rollover = model.isRollover(), enabled = button.isEnabled();
        final Graphics2D g2 = (Graphics2D) g.create();
        try {
            final Object[] hints = com.formdev.flatlaf.ui.FlatUIUtils.setRenderingHints(g2);
            final int width = button.getWidth(), height = button.getHeight();
            if (isToolBarButton(button) && (button instanceof JToggleButton)) {
                // The tools, terrain types, brushes… are keys, pushed in and lit when selected
                final float inset = UIScale.scale(1f), arc = UIScale.scale(9f);
                paintRaised(g2, inset, inset, width - 2 * inset, height - 2 * inset - UIScale.scale(1f), arc, Colours.of("Tactile.raised"), pressed || selected, rollover, enabled);
                final float radius = UIScale.scale(2.2f);
                paintLed(g2, width - inset - UIScale.scale(5.5f), inset + UIScale.scale(selected ? 6.5f : 4.5f), radius, ledColour(button), selected);
            } else if (isToolBarButton(button)) {
                // The commands of the tool bars lie flat until the mouse is over them
                final float inset = UIScale.scale(1f), arc = UIScale.scale(10f);
                if (pressed || rollover) {
                    g2.setColor(colour(pressed ? "Tactile.toolbar.pressed" : "Tactile.toolbar.hover", new Color(0, 0, 0, 20)));
                    g2.fill(new RoundRectangle2D.Float(inset, inset, width - 2 * inset, height - 2 * inset, arc, arc));
                }
            } else {
                final float focusWidth = com.formdev.flatlaf.ui.FlatUIUtils.getBorderFocusWidth(button), arc = com.formdev.flatlaf.ui.FlatUIUtils.getBorderArc(button);
                final boolean isDefault = (button instanceof JButton) && ((JButton) button).isDefaultButton();
                final Colours colours = Colours.of(isDefault ? "Tactile.default" : (selected ? "Tactile.selected" : "Tactile.button"));
                paintRaised(g2, focusWidth, focusWidth, width - 2 * focusWidth, height - 2 * focusWidth, arc, colours, pressed || selected, rollover, enabled);
            }
            com.formdev.flatlaf.ui.FlatUIUtils.resetRenderingHints(g2, hints);
        } finally {
            g2.dispose();
        }
        return true;
    }

    /**
     * Whether the theme paints a button itself: plain push and toggle buttons and those of tool bars, whose background
     * was not changed by the program. Other kinds (borderless, round, tabs, help…) are left to FlatLaf.
     */
    static boolean paints(AbstractButton button) {
        if (! button.isContentAreaFilled()) {
            return false;
        }
        final Color background = button.getBackground();
        if ((background != null) && (! (background instanceof UIResource))) {
            return false;
        }
        final Object type = button.getClientProperty("JButton.buttonType");
        return (type == null) || "toolBarButton".equals(type);
    }

    /**
     * How far down the icon and the text of a button are moved, to stay on the face of its key: a little up when the
     * key is out (its face sits above its thick bottom side), down when it is pushed in.
     */
    static int contentOffset(AbstractButton button) {
        if (! paints(button)) {
            return 0;
        }
        final ButtonModel model = button.getModel();
        final boolean pressed = (model.isArmed() && model.isPressed()) || model.isSelected();
        if (isToolBarButton(button) && (! (button instanceof JToggleButton))) {
            return 0;
        }
        return Math.round(UIScale.scale(pressed ? 1.5f : -1.5f));
    }

    /**
     * The client property which tells which kind of tool a button is for, to colour its light.
     */
    public static final String KEY_KIND = "Tactile.kind";

    static boolean isToolBarButton(AbstractButton button) {
        return "toolBarButton".equals(button.getClientProperty("JButton.buttonType")) || (button.getParent() instanceof JToolBar);
    }

    static Color colour(String key, Color fallback) {
        final Color colour = UIManager.getColor(key);
        return (colour != null) ? colour : fallback;
    }

    static Color withAlpha(Color colour, float factor) {
        return new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), Math.round(colour.getAlpha() * factor));
    }

    static Color lighten(Color colour, float amount) {
        return new Color(Math.round(colour.getRed() + (255 - colour.getRed()) * amount), Math.round(colour.getGreen() + (255 - colour.getGreen()) * amount),
                Math.round(colour.getBlue() + (255 - colour.getBlue()) * amount), colour.getAlpha());
    }

    static Color darken(Color colour, float amount) {
        return new Color(Math.round(colour.getRed() * (1 - amount)), Math.round(colour.getGreen() * (1 - amount)), Math.round(colour.getBlue() * (1 - amount)), colour.getAlpha());
    }
}
