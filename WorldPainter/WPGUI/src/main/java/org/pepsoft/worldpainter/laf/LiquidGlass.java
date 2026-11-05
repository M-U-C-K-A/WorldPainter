package org.pepsoft.worldpainter.laf;

import com.formdev.flatlaf.ui.FlatUIUtils;
import com.formdev.flatlaf.util.UIScale;
import org.pepsoft.worldpainter.design.Glass;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Paints the controls of the Glass theme in liquid glass, after the Liquid Glass of Apple: buttons are capsules of
 * clear glass (the default one tinted with the accent), selected tools sit in a drop of glass which magnifies them a
 * little, and check boxes are switches with a knob of glass.
 */
final class LiquidGlass {
    private LiquidGlass() {
        // Prevent instantiation
    }

    /**
     * Paint the background of a button in liquid glass, if it is one the theme paints itself.
     *
     * @return Whether it was painted; if not, FlatLaf should paint it.
     */
    static boolean paintButtonBackground(Graphics g, AbstractButton button) {
        if (! Tactile.paints(button)) {
            return false;
        }
        final ButtonModel model = button.getModel();
        final boolean pressed = model.isArmed() && model.isPressed(), selected = model.isSelected(), rollover = model.isRollover();
        final Graphics2D g2 = (Graphics2D) g.create();
        try {
            final Object[] hints = FlatUIUtils.setRenderingHints(g2);
            if (! button.isEnabled()) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
            }
            final int width = button.getWidth(), height = button.getHeight();
            final boolean iconOnly = (button.getText() == null) || button.getText().isEmpty();
            if (Tactile.isToolBarButton(button) || iconOnly) {
                // Tools, swatches and other buttons without text: flat in their cell, on a tile of glass when selected
                final float inset = UIScale.scale(1.5f), arc = UIScale.scale(9f);
                final Shape drop = new RoundRectangle2D.Float(inset, inset, width - 2 * inset, height - 2 * inset, arc, arc);
                if (selected) {
                    // A tile of glass under the selected tool, the shape of its cell
                    g2.setColor(colour("WorldPainter.glass.selectedTile"));
                    g2.fill(drop);
                    Glass.paintLiquid(g2, drop, null);
                    if (pressed || rollover) {
                        overlay(g2, drop, pressed ? 0.25f : 0.12f);
                    }
                } else if (pressed || rollover) {
                    overlay(g2, drop, pressed ? 0.35f : 0.22f);
                }
            } else {
                final float focusWidth = FlatUIUtils.getBorderFocusWidth(button);
                final float x = focusWidth, y = focusWidth, w = width - 2 * focusWidth, h = height - 2 * focusWidth;
                final Shape capsule = new RoundRectangle2D.Float(x, y, w, h, h, h);
                final boolean isDefault = (button instanceof JButton) && ((JButton) button).isDefaultButton();
                paintShadow(g2, capsule);
                Glass.paintLiquid(g2, capsule, isDefault ? UIManager.getColor("Component.accentColor") : (selected ? colour("WorldPainter.glass.selectedTint") : null));
                if (pressed || rollover) {
                    overlay(g2, capsule, pressed ? 0.22f : 0.12f);
                }
            }
            FlatUIUtils.resetRenderingHints(g2, hints);
        } finally {
            g2.dispose();
        }
        return true;
    }

    /**
     * Paint an iOS like switch: a capsule, green when on, with a knob of glass at one end.
     */
    static void paintSwitch(Graphics2D g2, float x, float y, float width, float height, boolean on, boolean pressed, boolean enabled) {
        final Composite saved = g2.getComposite();
        if (! enabled) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
        }
        try {
            final Shape track = new RoundRectangle2D.Float(x, y, width, height, height, height);
            g2.setColor(on ? colour("WorldPainter.glass.switchOn") : colour("WorldPainter.glass.switchOff"));
            g2.fill(track);
            g2.setColor(new Color(0, 0, 0, 22));
            g2.setStroke(new BasicStroke(UIScale.scale(1f)));
            g2.draw(track);
            // The knob, a little wider while pressed
            final float inset = UIScale.scale(2f), knobHeight = height - 2 * inset, knobWidth = knobHeight + (pressed ? UIScale.scale(4f) : 0f);
            final float knobX = on ? (x + width - inset - knobWidth) : (x + inset);
            final Shape knob = new RoundRectangle2D.Float(knobX, y + inset, knobWidth, knobHeight, knobHeight, knobHeight);
            paintShadow(g2, knob);
            g2.setColor(colour("WorldPainter.glass.knob"));
            g2.fill(knob);
        } finally {
            g2.setComposite(saved);
        }
    }

    /**
     * A soft shadow under a shape, offset downwards.
     */
    static void paintShadow(Graphics2D g2, Shape shape) {
        final Color shadow = colour("WorldPainter.glass.controlShadow");
        for (int i = 3; i >= 1; i--) {
            final Graphics2D g = (Graphics2D) g2.create();
            try {
                g.translate(0, UIScale.scale(i * 0.7f));
                g.setColor(new Color(shadow.getRed(), shadow.getGreen(), shadow.getBlue(), shadow.getAlpha() / (i + 1)));
                g.setStroke(new BasicStroke(UIScale.scale(i * 1.2f)));
                g.draw(shape);
                g.fill(shape);
            } finally {
                g.dispose();
            }
        }
    }

    /**
     * Lighten a shape, as glass lights up under the finger.
     */
    static void overlay(Graphics2D g2, Shape shape, float amount) {
        final Color light = colour("WorldPainter.glass.hover");
        g2.setColor(new Color(light.getRed(), light.getGreen(), light.getBlue(), Math.round(light.getAlpha() * amount)));
        g2.fill(shape);
    }

    static Color colour(String key) {
        final Color colour = UIManager.getColor(key);
        return (colour != null) ? colour : Color.WHITE;
    }

}
