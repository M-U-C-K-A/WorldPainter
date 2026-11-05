package org.pepsoft.worldpainter.laf;

import com.formdev.flatlaf.ui.FlatToggleButtonUI;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;

/**
 * The toggle buttons of the Glass theme: capsules of liquid glass; a selected tool sits on a tile of glass.
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
}
