package org.pepsoft.worldpainter.docking;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * The part of the main window which holds the areas. The gaps between them are its own; dragging one resizes the areas
 * on either side of it.
 */
final class ScreenPanel extends JPanel {
    ScreenPanel(AreaManager manager) {
        super(null);
        this.manager = manager;
        setLayout(new LayoutManager() {
            @Override public void addLayoutComponent(String name, Component comp) {}
            @Override public void removeLayoutComponent(Component comp) {}

            @Override
            public Dimension preferredLayoutSize(Container parent) {
                return new Dimension(800, 600);
            }

            @Override
            public Dimension minimumLayoutSize(Container parent) {
                return new Dimension(100, 100);
            }

            @Override
            public void layoutContainer(Container parent) {
                manager.layoutScreen();
            }
        });
        final MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                setHover(dividerAt(e.getPoint()));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (dragged == null) {
                    setHover(null);
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    dragged = dividerAt(e.getPoint());
                    if (dragged != null) {
                        lastPosition = dragged.split.horizontal ? e.getXOnScreen() : e.getYOnScreen();
                        setHover(dragged);
                    }
                } else if (e.isPopupTrigger()) {
                    showMenu(e);
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragged == null) {
                    return;
                }
                final int position = dragged.split.horizontal ? e.getXOnScreen() : e.getYOnScreen();
                final int delta = position - lastPosition;
                if (delta != 0) {
                    // The divider may not move as far as the mouse, because of the minimum sizes of the areas
                    lastPosition += manager.moveDivider(dragged, delta);
                    final AreaTree.Divider current = findSame(dragged);
                    if (current != null) {
                        dragged = current;
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showMenu(e);
                }
                dragged = null;
                setHover(dividerAt(e.getPoint()));
            }

            private void showMenu(MouseEvent e) {
                final AreaTree.Divider divider = dividerAt(e.getPoint());
                if ((divider != null) && (divider.before instanceof AreaTree.Leaf) && (divider.after instanceof AreaTree.Leaf)) {
                    final AreaTree.Leaf before = (AreaTree.Leaf) divider.before, after = (AreaTree.Leaf) divider.after;
                    final JPopupMenu menu = new JPopupMenu();
                    final boolean horizontal = divider.split.horizontal;
                    JMenuItem menuItem = new JMenuItem(horizontal ? "Join into the left area" : "Join into the area above");
                    menuItem.addActionListener(event -> manager.join(before, after));
                    menu.add(menuItem);
                    menuItem = new JMenuItem(horizontal ? "Join into the right area" : "Join into the area below");
                    menuItem.addActionListener(event -> manager.join(after, before));
                    menu.add(menuItem);
                    menu.addSeparator();
                    menuItem = new JMenuItem("Swap areas");
                    menuItem.addActionListener(event -> manager.swapAreas(before, after));
                    menu.add(menuItem);
                    menu.show(ScreenPanel.this, e.getX(), e.getY());
                }
            }

            private int lastPosition;
        };
        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Color line = UIManager.getColor("Separator.foreground");
        if (line == null) {
            line = Color.LIGHT_GRAY;
        }
        Color accent = UIManager.getColor("Component.focusColor");
        if (accent == null) {
            accent = Color.GRAY;
        }
        for (AreaTree.Divider divider: manager.getDividers()) {
            final Rectangle b = divider.bounds;
            final boolean highlighted = (hover != null) && (hover.before == divider.before) && (hover.after == divider.after);
            g.setColor(highlighted ? accent : line);
            final int thickness = highlighted ? 2 : 1;
            if (divider.split.horizontal) {
                g.fillRect(b.x + (b.width - thickness) / 2, b.y, thickness, b.height);
            } else {
                g.fillRect(b.x, b.y + (b.height - thickness) / 2, b.width, thickness);
            }
        }
    }

    private AreaTree.Divider dividerAt(Point point) {
        for (AreaTree.Divider divider: manager.getDividers()) {
            // A little wider than the gap, to make it easier to catch
            final Rectangle b = new Rectangle(divider.bounds);
            if (divider.split.horizontal) {
                b.grow(1, 0);
            } else {
                b.grow(0, 1);
            }
            if (b.contains(point)) {
                return divider;
            }
        }
        return null;
    }

    /**
     * Find the divider between the same two nodes after a new layout.
     */
    private AreaTree.Divider findSame(AreaTree.Divider divider) {
        for (AreaTree.Divider candidate: manager.getDividers()) {
            if ((candidate.before == divider.before) && (candidate.after == divider.after)) {
                return candidate;
            }
        }
        return null;
    }

    private void setHover(AreaTree.Divider divider) {
        final boolean changed = (divider == null) ? (hover != null) : ((hover == null) || (hover.before != divider.before) || (hover.after != divider.after));
        hover = divider;
        if (changed) {
            setCursor((divider == null) ? Cursor.getDefaultCursor() : Cursor.getPredefinedCursor(divider.split.horizontal ? Cursor.E_RESIZE_CURSOR : Cursor.N_RESIZE_CURSOR));
            repaint();
        }
    }

    private final AreaManager manager;
    private AreaTree.Divider hover, dragged;

    private static final long serialVersionUID = 1L;
}
