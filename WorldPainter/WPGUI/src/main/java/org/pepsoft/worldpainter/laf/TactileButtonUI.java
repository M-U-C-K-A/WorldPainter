package org.pepsoft.worldpainter.laf;

import com.formdev.flatlaf.ui.FlatButtonUI;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;

/**
 * The buttons of the Tactile theme: raised, with a lip along their bottom edge, coloured for the default button.
 */
public class TactileButtonUI extends FlatButtonUI {
    public TactileButtonUI() {
        super(false);
    }

    public static ComponentUI createUI(JComponent c) {
        return new TactileButtonUI();
    }

    @Override
    protected void paintBackground(Graphics g, JComponent c) {
        if (! Tactile.paintButtonBackground(g, (AbstractButton) c)) {
            super.paintBackground(g, c);
        }
    }
}
