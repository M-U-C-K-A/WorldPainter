package org.pepsoft.worldpainter.laf;

import com.formdev.flatlaf.ui.FlatUIUtils;
import com.formdev.flatlaf.util.UIScale;

import javax.swing.*;
import javax.swing.plaf.UIResource;
import java.awt.*;

/**
 * The check boxes of the Glass theme: switches as on the iPhone, green when on, with a knob of liquid glass.
 */
public class GlassSwitchIcon implements Icon, UIResource {
    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        final Graphics2D g2 = (Graphics2D) g.create();
        try {
            final Object[] hints = FlatUIUtils.setRenderingHints(g2);
            final ButtonModel model = (c instanceof AbstractButton) ? ((AbstractButton) c).getModel() : null;
            final boolean on = (model != null) && model.isSelected(), pressed = (model != null) && model.isArmed() && model.isPressed();
            LiquidGlass.paintSwitch(g2, x + UIScale.scale(1f), y + UIScale.scale(1f), UIScale.scale(WIDTH - 2), UIScale.scale(HEIGHT - 2), on, pressed, (c == null) || c.isEnabled());
            FlatUIUtils.resetRenderingHints(g2, hints);
        } finally {
            g2.dispose();
        }
    }

    @Override
    public int getIconWidth() {
        return Math.round(UIScale.scale(WIDTH));
    }

    @Override
    public int getIconHeight() {
        return Math.round(UIScale.scale(HEIGHT));
    }

    private static final float WIDTH = 22f, HEIGHT = 14f;
}
