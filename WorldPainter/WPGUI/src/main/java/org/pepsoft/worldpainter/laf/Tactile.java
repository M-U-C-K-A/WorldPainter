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
final class Tactile {
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
     * Paint a raised surface.
     *
     * @param pressed Whether it is pushed in: the lip disappears and the surface moves down.
     * @param hover   Whether the mouse is over it: it lightens a little.
     */
    static void paintRaised(Graphics2D g2, float x, float y, float width, float height, float arc, Colours colours, boolean pressed, boolean hover, boolean enabled) {
        final float lip = UIScale.scale(pressed ? 1f : 3f), offset = pressed ? UIScale.scale(2f) : 0f;
        final Composite savedComposite = g2.getComposite();
        if (! enabled) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
        }
        try {
            // A soft shadow under the button, stronger close to it
            if (! pressed) {
                g2.setColor(withAlpha(colours.shadow, 0.45f));
                g2.fill(new RoundRectangle2D.Float(x, y + UIScale.scale(2f), width, height, arc, arc));
                g2.setColor(withAlpha(colours.shadow, 1f));
                g2.fill(new RoundRectangle2D.Float(x + UIScale.scale(0.5f), y + UIScale.scale(1f), width - UIScale.scale(1f), height, arc, arc));
            }
            // The lip: the darker side of the button, seen along its bottom edge
            g2.setColor(colours.lip);
            g2.fill(new RoundRectangle2D.Float(x, y + offset, width, height - offset, arc, arc));
            // The face, lit from above
            final float faceTop = y + offset, faceHeight = height - offset - lip;
            Color top = colours.top, bottom = colours.bottom;
            if (hover && (! pressed)) {
                top = lighten(top, 0.06f);
                bottom = lighten(bottom, 0.06f);
            } else if (pressed) {
                top = darken(top, 0.04f);
                bottom = darken(bottom, 0.04f);
            }
            g2.setPaint(new GradientPaint(0, faceTop, top, 0, faceTop + faceHeight, bottom));
            g2.fill(new RoundRectangle2D.Float(x, faceTop, width, faceHeight, arc, arc));
            // A thin highlight along the top of the face, fading out downwards
            final Shape savedClip = g2.getClip();
            g2.clip(new RoundRectangle2D.Float(x, faceTop, width, faceHeight, arc, arc));
            g2.setPaint(new GradientPaint(0, faceTop, colours.highlight, 0, faceTop + Math.min(faceHeight, UIScale.scale(8f)), withAlpha(colours.highlight, 0f)));
            g2.setStroke(new BasicStroke(UIScale.scale(1.2f)));
            g2.draw(new RoundRectangle2D.Float(x + UIScale.scale(0.6f), faceTop + UIScale.scale(0.6f), width - UIScale.scale(1.2f), faceHeight + arc, arc, arc));
            g2.setClip(savedClip);
            // The outline
            g2.setColor(colours.border);
            g2.setStroke(new BasicStroke(UIScale.scale(1f)));
            g2.draw(new RoundRectangle2D.Float(x + 0.5f, y + offset + 0.5f, width - 1, height - offset - 1, arc, arc));
        } finally {
            g2.setComposite(savedComposite);
        }
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
            if (isToolBarButton(button)) {
                // Tool bar buttons lie flat until the mouse is over them; when selected they stand out as a tile
                final float inset = UIScale.scale(1f), arc = UIScale.scale(10f);
                if (selected) {
                    paintRaised(g2, inset, inset, width - 2 * inset, height - 2 * inset - UIScale.scale(1f), arc, Colours.of("Tactile.raised"), pressed, rollover, enabled);
                } else if (pressed || rollover) {
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
