package org.pepsoft.worldpainter.util;

import javax.swing.*;
import java.awt.*;

/**
 * A grid of square cells which uses as many columns as fit in the width of the container, so that a palette of
 * buttons fills a narrow or a wide side panel alike. The cells are as large as the largest preferred size of the
 * components and keep that size whatever the width; what is left over goes in the gaps between the columns, so that
 * the grid uses the whole width without the buttons, or the height of the grid, changing as the panel is resized.
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
            final int rows = (count + columns - 1) / columns;
            return new Dimension(insets.left + insets.right + cell, insets.top + insets.bottom + rows * cell + (rows - 1) * gap);
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
            final int columns = columns(width, cell);
            final int leftOver = Math.max(0, width - columns * cell - (columns - 1) * gap);
            int index = 0;
            for (Component component: parent.getComponents()) {
                if (! component.isVisible()) {
                    continue;
                }
                final int column = index % columns, row = index / columns;
                final int x = insets.left + column * (cell + gap) + ((columns > 1) ? (leftOver * column / (columns - 1)) : 0);
                component.setBounds(x, insets.top + row * (cell + gap), cell, cell);
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
