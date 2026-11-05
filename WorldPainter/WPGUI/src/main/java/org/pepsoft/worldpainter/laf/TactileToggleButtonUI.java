package org.pepsoft.worldpainter.laf;

import com.formdev.flatlaf.ui.FlatToggleButtonUI;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;

/**
 * The toggle buttons of the Tactile theme: raised, and pressed in (or, in tool bars and palettes, standing out as a
 * tile) when selected.
 */
public class TactileToggleButtonUI extends FlatToggleButtonUI {
    public TactileToggleButtonUI() {
        super(false);
    }

    public static ComponentUI createUI(JComponent c) {
        return new TactileToggleButtonUI();
    }

    @Override
    protected void paintBackground(Graphics g, JComponent c) {
        if (! Tactile.paintButtonBackground(g, (AbstractButton) c)) {
            super.paintBackground(g, c);
        }
    }
}
