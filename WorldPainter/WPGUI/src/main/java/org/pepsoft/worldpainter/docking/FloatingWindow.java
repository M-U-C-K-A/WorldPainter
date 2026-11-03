package org.pepsoft.worldpainter.docking;

import org.pepsoft.worldpainter.design.Glass;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * A window of its own for an area detached from the main window, which floats over it, like picture in picture.
 * Closing it puts the area back where it came from.
 */
final class FloatingWindow extends JDialog {
    FloatingWindow(AreaManager manager, AreaManager.FloatingArea floating, AreaPanel panel) {
        super(manager.getOwner(), "", ModalityType.MODELESS);
        this.floating = floating;
        this.panel = panel;
        setType(Type.UTILITY);
        getRootPane().putClientProperty("Window.style", "small");
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                manager.dockBack(floating.leaf);
            }
        });
        final Glass.Backdrop backdrop = new Glass.Backdrop(new BorderLayout());
        backdrop.add(panel, BorderLayout.CENTER);
        setContentPane(backdrop);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                remember();
            }

            @Override
            public void componentResized(ComponentEvent e) {
                remember();
            }

            private void remember() {
                if (isShowing()) {
                    floating.bounds = getBounds();
                    manager.scheduleSave();
                }
            }
        });
        setAlwaysOnTop(floating.onTop);
        update();
    }

    void open() {
        final Rectangle bounds = floating.bounds;
        if ((bounds != null) && isOnScreen(bounds)) {
            setBounds(bounds);
        } else {
            pack();
            if (bounds != null) {
                setSize(Math.max(bounds.width, getWidth()), Math.max(bounds.height, getHeight()));
            }
            setLocationRelativeTo(getOwner());
        }
        setVisible(true);
    }

    void update() {
        final DockableFrame page = panel.getPage();
        setTitle((page != null) ? page.getTitle() : "Empty area");
    }

    void close() {
        setVisible(false);
        getContentPane().removeAll();
        dispose();
    }

    AreaPanel getAreaPanel() {
        return panel;
    }

    private static boolean isOnScreen(Rectangle bounds) {
        for (GraphicsDevice device: GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            if (device.getDefaultConfiguration().getBounds().intersects(bounds)) {
                return true;
            }
        }
        return false;
    }

    private final AreaManager.FloatingArea floating;
    private final AreaPanel panel;

    private static final long serialVersionUID = 1L;
}
