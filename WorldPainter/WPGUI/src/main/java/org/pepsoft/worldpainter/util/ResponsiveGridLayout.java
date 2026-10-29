package org.pepsoft.worldpainter.util;

import javax.swing.*;
import java.awt.*;

/**
 * A grid of square cells which uses as many columns as fit in the width of the container, so that a palette of
 * buttons fills a narrow or a wide side panel alike. The cells are at least as large as the largest preferred size of
 * the components, and grow by up to half again to use the whole width.
 *
 * <p>The container should be stretched horizontally by its parent (or be the view of a scroll pane), since its
 * preferred width is that of a single column; its preferred height depends on its current width.
 */
public class ResponsiveGridLayout implements LayoutManager {
    /**
     * @param gap            The space between the cells, in pixels.
     * @param defaultColumns The number of columns to assume as long as the container has no width yet.
     */
    public ResponsiveGridLayout(int gap, int defaultColumns) {
        this.gap = gap;
        this.defaultColumns = defaultColumns;
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
            final int count = visibleCount(parent), cell = cellSize(parent);
            if (count == 0) {
                return new Dimension(insets.left + insets.right, insets.top + insets.bottom);
            }
            final int width = parent.getWidth() - insets.left - insets.right;
            final int columns = (width > 0) ? columns(width, cell) : defaultColumns;
            final int side = (width > 0) ? side(width, columns, cell) : cell;
            final int rows = (count + columns - 1) / columns;
            return new Dimension(insets.left + insets.right + cell, insets.top + insets.bottom + rows * side + (rows - 1) * gap);
        }
    }

    /**
     * As narrow as one column, but as high as all the rows, since a layout manager such as the grid bag layout falls
     * back to the minimum sizes when it is short of space, which would otherwise cut the grid down to one row.
     */
    @Override
    public Dimension minimumLayoutSize(Container parent) {
        return preferredLayoutSize(parent);
    }

    @Override
    public void layoutContainer(Container parent) {
        synchronized (parent.getTreeLock()) {
            final Insets insets = parent.getInsets();
            final int width = parent.getWidth() - insets.left - insets.right, cell = cellSize(parent);
            final int columns = columns(width, cell), side = side(width, columns, cell);
            int index = 0;
            for (Component component: parent.getComponents()) {
                if (! component.isVisible()) {
                    continue;
                }
                final int column = index % columns, row = index / columns;
                component.setBounds(insets.left + column * (side + gap), insets.top + row * (side + gap), side, side);
                index++;
            }
            if (columns != laidOutColumns) {
                // The number of rows may have changed, and with it the height the container needs
                laidOutColumns = columns;
                SwingUtilities.invokeLater(() -> {
                    parent.invalidate();
                    final Container ancestor = parent.getParent();
                    if (ancestor instanceof JComponent) {
                        ((JComponent) ancestor).revalidate();
                    } else if (ancestor != null) {
                        ancestor.validate();
                    }
                });
            }
        }
    }

    private int columns(int width, int cell) {
        return Math.max(1, (width + gap) / (cell + gap));
    }

    /**
     * The size of the cells: grown to use the whole width, but by no more than half.
     */
    private int side(int width, int columns, int cell) {
        final int fill = (width - (columns - 1) * gap) / columns;
        return Math.max(cell, Math.min(fill, cell * 3 / 2));
    }

    private static int cellSize(Container parent) {
        int size = 0;
        for (Component component: parent.getComponents()) {
            if (component.isVisible()) {
                final Dimension preferredSize = component.getPreferredSize();
                size = Math.max(size, Math.max(preferredSize.width, preferredSize.height));
            }
        }
        return Math.max(size, 1);
    }

    private static int visibleCount(Container parent) {
        int count = 0;
        for (Component component: parent.getComponents()) {
            if (component.isVisible()) {
                count++;
            }
        }
        return count;
    }

    private final int gap, defaultColumns;
    private int laidOutColumns = -1;
}
