package org.pepsoft.worldpainter.util;

import javax.swing.*;
import java.awt.*;

/**
 * A grid of square cells which uses as many columns as fit in the width of the container, so that a palette of
 * buttons fills a narrow or a wide side panel alike. The cells are as large as the largest preferred size of the
 * components and keep that size whatever the width. The gaps between them only depend on how wide the container is:
 * they get larger in steps as it gets wider, and never smaller again, so that nothing shifts back and forth while a
 * panel is being resized. What is left over stays at the right.
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
            final int currentGap = (width > 0) ? gapFor(width, cell) : gap;
            final int columns = (width > 0) ? columns(width, cell, currentGap) : defaultColumns;
            final int rows = (count + columns - 1) / columns;
            return new Dimension(insets.left + insets.right + cell, insets.top + insets.bottom + rows * cell + (rows - 1) * currentGap);
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
            final int currentGap = gapFor(width, cell), columns = columns(width, cell, currentGap);
            int index = 0;
            for (Component component: parent.getComponents()) {
                if (! component.isVisible()) {
                    continue;
                }
                final int column = index % columns, row = index / columns;
                component.setBounds(insets.left + column * (cell + currentGap), insets.top + row * (cell + currentGap), cell, cell);
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

    private static int columns(int width, int cell, int gap) {
        return Math.max(1, (width + gap) / (cell + gap));
    }

    /**
     * The gap for a width: the smallest one up to the first step, twice that up to the second, and four times that from
     * there. A step is put off until the larger gap leaves the same number of columns, since otherwise a wider panel
     * would briefly have fewer of them.
     */
    private int gapFor(int width, int cell) {
        int currentGap = gap;
        for (int step = 0; step < STEPS.length; step++) {
            final int nextGap = currentGap * 2;
            int threshold = com.formdev.flatlaf.util.UIScale.scale(STEPS[step]);
            // Very small cells may never get there, so give up after the width of a column
            final int lastThreshold = threshold + cell + nextGap;
            while ((threshold < lastThreshold) && (columns(threshold, cell, nextGap) < columns(threshold, cell, currentGap))) {
                threshold++;
            }
            if (width < threshold) {
                break;
            }
            currentGap = nextGap;
        }
        return currentGap;
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

    /**
     * The widths from which the gaps get larger, before scaling.
     */
    private static final int[] STEPS = {230, 340};
}
