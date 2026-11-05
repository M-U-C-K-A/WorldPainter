package org.pepsoft.worldpainter.laf;

import com.formdev.flatlaf.ui.FlatButtonUI;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;

/**
 * The buttons of the Glass theme: capsules of liquid glass.
 */
public class GlassButtonUI extends FlatButtonUI {
    public GlassButtonUI() {
        super(false);
    }

    public static ComponentUI createUI(JComponent c) {
        return new GlassButtonUI();
    }

    @Override
    protected void paintBackground(Graphics g, JComponent c) {
        if (! LiquidGlass.paintButtonBackground(g, (AbstractButton) c)) {
            super.paintBackground(g, c);
        }
    }
}
