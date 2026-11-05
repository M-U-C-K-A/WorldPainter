package org.pepsoft.worldpainter.laf;

import com.formdev.flatlaf.ui.FlatComboBoxUI;
import com.formdev.flatlaf.ui.FlatUIUtils;
import com.formdev.flatlaf.util.UIScale;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;

/**
 * The drop down lists of the Tactile theme: a key, with the chosen item on its face and the arrow in a small hollow at
 * its right end. Editable ones, which hold a text field, are left as they are.
 */
public class TactileComboBoxUI extends FlatComboBoxUI {
    public static ComponentUI createUI(JComponent c) {
        return new TactileComboBoxUI();
    }

    @Override
    public void update(Graphics g, JComponent c) {
        if (comboBox.isEditable()) {
            super.update(g, c);
            return;
        }
        final Graphics2D g2 = (Graphics2D) g.create();
        try {
            final Object[] hints = FlatUIUtils.setRenderingHints(g2);
            final float focusWidth = FlatUIUtils.getBorderFocusWidth(c), arc = UIScale.scale(9f);
            final float x = focusWidth, y = focusWidth, width = c.getWidth() - 2 * focusWidth, height = c.getHeight() - 2 * focusWidth;
            final boolean open = comboBox.isPopupVisible();
            Tactile.paintRaised(g2, x, y, width, height, arc, Tactile.Colours.of("Tactile.button"), open, isHover(), comboBox.isEnabled());
            if ((arrowButton != null) && arrowButton.isVisible()) {
                // The hollow for the arrow, on the face of the key
                final Rectangle bounds = arrowButton.getBounds();
                final float inset = UIScale.scale(4f), sink = open ? UIScale.scale(2.5f) : 0f;
                final float wellHeight = height - UIScale.scale(open ? 4.5f : 7.5f) - 2 * inset;
                Tactile.paintSunken(g2, bounds.x + inset * 0.5f, y + sink + UIScale.scale(1f) + inset, bounds.width - inset * 1.5f, wellHeight, UIScale.scale(6f));
            }
            FlatUIUtils.resetRenderingHints(g2, hints);
        } finally {
            g2.dispose();
        }
        // The chosen item, on the face of the key
        final Graphics g3 = g.create();
        try {
            g3.translate(0, Math.round(UIScale.scale(comboBox.isPopupVisible() ? 1f : -1.5f)));
            paint(g3, c);
        } finally {
            g3.dispose();
        }
    }

    @Override
    public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
        if (comboBox.isEditable()) {
            super.paintCurrentValueBackground(g, bounds, hasFocus);
        }
        // Otherwise the face of the key is the background
    }

    private boolean isHover() {
        final Point mouse = comboBox.getMousePosition();
        return mouse != null;
    }
}
