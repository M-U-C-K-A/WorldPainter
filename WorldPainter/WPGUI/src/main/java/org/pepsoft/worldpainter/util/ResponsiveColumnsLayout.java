package org.pepsoft.worldpainter.util;

import javax.swing.*;
import java.awt.*;

/**
 * Lays out its components in as many equally wide columns as fit in the width of the container, up to a maximum, so
 * that a form which is one long column in a narrow side panel makes use of the room in a wide one.
 *
 * <p>The container should be stretched horizontally by its parent, since its preferred width is that of a single
 * column; its preferred height depends on its current width.
 */
public class ResponsiveColumnsLayout implements LayoutManager {
    /**
     * @param columnWidth The narrowest a column may be, in pixels.
     * @param gap         The space between the columns and between the rows, in pixels.
     * @param maxColumns  The largest number of columns to use.
     */
    public ResponsiveColumnsLayout(int columnWidth, int gap, int maxColumns) {
        this.columnWidth = columnWidth;
        this.gap = gap;
        this.maxColumns = maxColumns;
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
            final int width = parent.getWidth() - insets.left - insets.right;
            final int columns = (width > 0) ? columns(width) : 1;
            int height = 0, rowHeight = 0, index = 0;
            for (Component component: parent.getComponents()) {
                if (component.isVisible()) {
                    rowHeight = Math.max(rowHeight, component.getPreferredSize().height);
                    index++;
                    if (index % columns == 0) {
                        height += rowHeight + gap;
                        rowHeight = 0;
                    }
                }
            }
            height += (rowHeight > 0) ? rowHeight : ((height > 0) ? -gap : 0);
            return new Dimension(insets.left + insets.right + columnWidth, insets.top + insets.bottom + height);
        }
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        return preferredLayoutSize(parent);
    }

    @Override
    public void layoutContainer(Container parent) {
        synchronized (parent.getTreeLock()) {
            final Insets insets = parent.getInsets();
            final int width = parent.getWidth() - insets.left - insets.right;
            final int columns = columns(width), cellWidth = (width - (columns - 1) * gap) / columns;
            final Component[] components = parent.getComponents();
            int y = insets.top, column = 0, rowStart = 0;
            for (int i = 0; i <= components.length; i++) {
                if ((i == components.length) || (components[i].isVisible() && (column == columns))) {
                    // Lay out the row which has just been completed
                    int rowHeight = 0;
                    for (int j = rowStart; j < i; j++) {
                        if (components[j].isVisible()) {
                            rowHeight = Math.max(rowHeight, components[j].getPreferredSize().height);
                        }
                    }
                    int x = insets.left;
                    for (int j = rowStart; j < i; j++) {
                        if (components[j].isVisible()) {
                            components[j].setBounds(x, y, cellWidth, rowHeight);
                            x += cellWidth + gap;
                        }
                    }
                    y += rowHeight + gap;
                    rowStart = i;
                    column = 0;
                }
                if ((i < components.length) && components[i].isVisible()) {
                    column++;
                }
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

    private int columns(int width) {
        return Math.max(1, Math.min(maxColumns, (width + gap) / (columnWidth + gap)));
    }

    private final int columnWidth, gap, maxColumns;
    private int laidOutColumns = -1;
}
