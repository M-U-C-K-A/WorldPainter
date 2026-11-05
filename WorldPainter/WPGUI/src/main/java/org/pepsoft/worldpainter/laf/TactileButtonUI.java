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
    public void paint(Graphics g, JComponent c) {
        // The icon and the text move with the face of the key
        final int offset = Tactile.contentOffset((AbstractButton) c);
        if (offset == 0) {
            super.paint(g, c);
            return;
        }
        final Graphics g2 = g.create();
        try {
            g2.translate(0, offset);
            super.paint(g2, c);
        } finally {
            g2.dispose();
        }
    }

    @Override
    protected void paintBackground(Graphics g, JComponent c) {
        if (! Tactile.paintButtonBackground(g, (AbstractButton) c)) {
            super.paintBackground(g, c);
        }
    }
}
