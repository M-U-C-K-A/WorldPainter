package org.pepsoft.worldpainter.laf;

import com.formdev.flatlaf.ui.FlatToggleButtonUI;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;

/**
 * The toggle buttons of the Glass theme: capsules of liquid glass; a selected tool sits in a drop of glass which magnifies it a little.
 */
public class GlassToggleButtonUI extends FlatToggleButtonUI {
    public GlassToggleButtonUI() {
        super(false);
    }

    public static ComponentUI createUI(JComponent c) {
        return new GlassToggleButtonUI();
    }

    @Override
    protected void paintBackground(Graphics g, JComponent c) {
        if (! LiquidGlass.paintButtonBackground(g, (AbstractButton) c)) {
            super.paintBackground(g, c);
        }
    }

    @Override
    protected void paintIcon(Graphics g, JComponent c, Rectangle iconRect) {
        final AbstractButton button = (AbstractButton) c;
        if (button.isSelected() && Tactile.isToolBarButton(button) && Tactile.paints(button)) {
            // Seen through the drop of glass, the icon is a little larger
            final Graphics2D g2 = (Graphics2D) g.create();
            try {
                final double centreX = iconRect.getCenterX(), centreY = iconRect.getCenterY();
                g2.translate(centreX, centreY);
                g2.scale(1.14, 1.14);
                g2.translate(-centreX, -centreY);
                super.paintIcon(g2, c, iconRect);
            } finally {
                g2.dispose();
            }
        } else {
            super.paintIcon(g, c, iconRect);
        }
    }
}
