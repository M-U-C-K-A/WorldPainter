package org.pepsoft.worldpainter.laf;

import com.formdev.flatlaf.ui.FlatUIUtils;
import com.formdev.flatlaf.util.UIScale;

import javax.swing.*;
import javax.swing.plaf.UIResource;
import java.awt.*;

/**
 * The check boxes and radio buttons of the Tactile theme: small keys with a light which is lit when they are on;
 * square for check boxes, round for radio buttons.
 */
public class TactileCheckBoxIcon implements Icon, UIResource {
    public TactileCheckBoxIcon() {
        this(false);
    }

    TactileCheckBoxIcon(boolean round) {
        this.round = round;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        final Graphics2D g2 = (Graphics2D) g.create();
        try {
            final Object[] hints = FlatUIUtils.setRenderingHints(g2);
            final AbstractButton button = (c instanceof AbstractButton) ? (AbstractButton) c : null;
            final ButtonModel model = (button != null) ? button.getModel() : null;
            final boolean selected = (model != null) && model.isSelected(), pressed = (model != null) && model.isArmed() && model.isPressed();
            final boolean rollover = (model != null) && model.isRollover(), enabled = (c == null) || c.isEnabled();
            final float size = UIScale.scale(SIZE);
            final float arc = round ? size : UIScale.scale(5f);
            Tactile.paintRaised(g2, x, y, size, size, arc, Tactile.Colours.of("Tactile.raised"), pressed || selected, rollover, enabled);
            final float sink = (pressed || selected) ? UIScale.scale(2.5f) : 0f;
            final float faceCentreY = y + sink + UIScale.scale(1f) + (size - sink - UIScale.scale((pressed || selected) ? 2.5f : 5.5f)) / 2f;
            Tactile.paintLed(g2, x + size / 2f, faceCentreY, UIScale.scale(2.6f), (c instanceof JComponent) ? Tactile.ledColour((JComponent) c) : Color.ORANGE, selected && enabled);
            FlatUIUtils.resetRenderingHints(g2, hints);
        } finally {
            g2.dispose();
        }
    }

    @Override
    public int getIconWidth() {
        return Math.round(UIScale.scale(SIZE));
    }

    @Override
    public int getIconHeight() {
        return Math.round(UIScale.scale(SIZE));
    }

    private final boolean round;

    private static final float SIZE = 17f;

    /**
     * The radio buttons: round keys.
     */
    public static class Round extends TactileCheckBoxIcon {
        public Round() {
            super(true);
        }
    }
}
