package org.pepsoft.worldpainter.util;

import java.awt.*;

/**
 * Lays out components from left to right, as {@link FlowLayout} does, going on to a new row when there is no more
 * room, and asks for the height of all the rows it needs, so that a tool bar in a narrow area grows down instead of
 * losing its last buttons out of sight. The components are aligned to the left and centred vertically in their row.
 *
 * <p>The container should be stretched horizontally by its parent, for instance at the top of a
 * {@link BorderLayout}: its preferred height depends on its current width.
 */
public class WrapLayout implements LayoutManager {
    /**
     * @param hgap The space between the components in a row, in pixels.
     * @param vgap The space between the rows, in pixels.
     */
    public WrapLayout(int hgap, int vgap) {
        this.hgap = hgap;
        this.vgap = vgap;
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
        // Do nothing
    }

    @Override
    public void removeLayoutComponent(Component comp) {
        // Do nothing
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        synchronized (parent.getTreeLock()) {
            final Insets insets = parent.getInsets();
            // As long as the container has no width yet, everything on one row
            final int available = (parent.getWidth() > 0) ? (parent.getWidth() - insets.left - insets.right) : Integer.MAX_VALUE;
            int width = 0, height = 0, rowWidth = 0, rowHeight = 0;
            for (Component component: parent.getComponents()) {
                if (! component.isVisible()) {
                    continue;
                }
                final Dimension size = component.getPreferredSize();
                if ((rowWidth > 0) && (rowWidth + hgap + size.width > available)) {
                    width = Math.max(width, rowWidth);
                    height += rowHeight + vgap;
                    rowWidth = 0;
                    rowHeight = 0;
                }
                rowWidth += ((rowWidth > 0) ? hgap : 0) + size.width;
                rowHeight = Math.max(rowHeight, size.height);
            }
            width = Math.max(width, rowWidth);
            height += rowHeight;
            return new Dimension(insets.left + width + insets.right, insets.top + height + insets.bottom);
        }
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        synchronized (parent.getTreeLock()) {
            // As narrow as the widest component
            final Insets insets = parent.getInsets();
            int width = 0;
            for (Component component: parent.getComponents()) {
                if (component.isVisible()) {
                    width = Math.max(width, component.getPreferredSize().width);
                }
            }
            return new Dimension(insets.left + width + insets.right, preferredLayoutSize(parent).height);
        }
    }

    @Override
    public void layoutContainer(Container parent) {
        synchronized (parent.getTreeLock()) {
            final Insets insets = parent.getInsets();
            final int available = parent.getWidth() - insets.left - insets.right;
            final Component[] components = parent.getComponents();
            int y = insets.top, rowStart = 0;
            while (rowStart < components.length) {
                // Find the components of this row, and its height
                int rowEnd = rowStart, rowWidth = 0, rowHeight = 0;
                while (rowEnd < components.length) {
                    final Component component = components[rowEnd];
                    if (component.isVisible()) {
                        final Dimension size = component.getPreferredSize();
                        if ((rowWidth > 0) && (rowWidth + hgap + size.width > available)) {
                            break;
                        }
                        rowWidth += ((rowWidth > 0) ? hgap : 0) + size.width;
                        rowHeight = Math.max(rowHeight, size.height);
                    }
                    rowEnd++;
                }
                int x = insets.left;
                for (int i = rowStart; i < rowEnd; i++) {
                    final Component component = components[i];
                    if (component.isVisible()) {
                        final Dimension size = component.getPreferredSize();
                        component.setBounds(x, y + (rowHeight - size.height) / 2, size.width, size.height);
                        x += size.width + hgap;
                    }
                }
                y += rowHeight + vgap;
                rowStart = rowEnd;
            }
        }
    }

    private final int hgap, vgap;
}
