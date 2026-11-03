package org.pepsoft.worldpainter.design;

import com.formdev.flatlaf.util.UIScale;

import javax.swing.*;
import java.awt.*;

/**
 * Cards of settings on a page which scrolls, in two columns when there is room for them.
 */
public final class SettingsPage extends JPanel implements Scrollable {
    private SettingsPage(int preferredWidth, JComponent... cards) {
        super(new MasonryLayout());
        this.preferredWidth = preferredWidth;
        setOpaque(false);
        applyStyle();
        for (JComponent card: cards) {
            add(card);
        }
    }

    @Override
    public void updateUI() {
        super.updateUI();
        setOpaque(false);
        applyStyle();
    }

    /**
     * The Modern theme leaves more room around and between the cards. The cards of the Glass theme have room around
     * them for their shadows, which is part of that.
     */
    private void applyStyle() {
        final int padding = UIScale.scale(Design.isGlass() ? (16 - SettingsCard.GLASS_MARGIN) : (Design.isModern() ? 16 : 12));
        setBorder(BorderFactory.createEmptyBorder(padding, padding, padding, padding));
    }

    public static JScrollPane of(JComponent... cards) {
        return of(PREFERRED_WIDTH, cards);
    }

    /**
     * @param preferredWidth How wide the page would like to be, before scaling.
     */
    public static JScrollPane of(int preferredWidth, JComponent... cards) {
        final JScrollPane scrollPane = new JScrollPane(new SettingsPage(preferredWidth, cards), ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        // Not null, which the look and feel would replace with its own border when the theme is switched
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(UIScale.scale(16));
        return scrollPane;
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        // As wide as two columns, and as high as the cards need at that width, within reason
        final int width = UIScale.scale(preferredWidth);
        final int height = ((MasonryLayout) getLayout()).heightFor(this, width);
        // The cards of the Glass theme take more room, with the room for their shadows
        return new Dimension(width, Math.min(height, UIScale.scale(Design.isGlass() ? (MAX_HEIGHT + 60) : MAX_HEIGHT)));
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return UIScale.scale(16);
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return visibleRect.height - UIScale.scale(32);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }

    private final int preferredWidth;

    static final String KEY_WIDE = "org.pepsoft.worldpainter.design.wide";

    private static final int PREFERRED_WIDTH = 900, MAX_HEIGHT = 620;

    /**
     * Places the cards in as many columns as fit (up to two), each card in the column which is shortest so far, in the
     * order in which they were added.
     */
    private static final class MasonryLayout implements LayoutManager {
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
            final int width = (parent.getWidth() > 0) ? parent.getWidth() : UIScale.scale(PREFERRED_WIDTH);
            return new Dimension(UIScale.scale(COLUMN_WIDTH), heightFor(parent, width));
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return preferredLayoutSize(parent);
        }

        @Override
        public void layoutContainer(Container parent) {
            place(parent, parent.getWidth(), true);
        }

        int heightFor(Container parent, int width) {
            return place(parent, width, false);
        }

        private int place(Container parent, int width, boolean apply) {
            final Insets insets = parent.getInsets();
            final int gap = UIScale.scale(Design.isGlass() ? (MODERN_GAP - 2 * SettingsCard.GLASS_MARGIN) : (Design.isModern() ? MODERN_GAP : GAP)), available = width - insets.left - insets.right;
            final int columns = (available >= 2 * UIScale.scale(COLUMN_WIDTH) + gap) ? 2 : 1;
            final int columnWidth = (available - (columns - 1) * gap) / columns;
            final int[] heights = new int[columns];
            for (Component component: parent.getComponents()) {
                if (! component.isVisible()) {
                    continue;
                }
                if ((component instanceof JComponent) && Boolean.TRUE.equals(((JComponent) component).getClientProperty(KEY_WIDE))) {
                    // Across all the columns, below what is there so far
                    int top = 0;
                    for (int height: heights) {
                        top = Math.max(top, height);
                    }
                    component.setSize(available, Short.MAX_VALUE);
                    layOut(component);
                    layOut(component);
                    final int height = component.getPreferredSize().height;
                    if (apply) {
                        component.setBounds(insets.left, insets.top + top, available, height);
                    }
                    java.util.Arrays.fill(heights, top + height + gap);
                    continue;
                }
                int column = 0;
                for (int i = 1; i < columns; i++) {
                    if (heights[i] < heights[column]) {
                        column = i;
                    }
                }
                // The height depends on the width, for text which wraps: lay the card out at its width first, so that
                // the text knows how wide it is
                component.setSize(columnWidth, Short.MAX_VALUE);
                layOut(component);
                layOut(component);
                final int height = component.getPreferredSize().height;
                if (apply) {
                    component.setBounds(insets.left + column * (columnWidth + gap), insets.top + heights[column], columnWidth, height);
                }
                heights[column] += height + gap;
            }
            int highest = 0;
            for (int height: heights) {
                highest = Math.max(highest, height);
            }
            return insets.top + insets.bottom + Math.max(0, highest - gap);
        }

        private static void layOut(Component component) {
            if (component instanceof Container) {
                final Container container = (Container) component;
                container.doLayout();
                for (Component child: container.getComponents()) {
                    layOut(child);
                }
            }
        }

        private static final int COLUMN_WIDTH = 400, GAP = 12, MODERN_GAP = 16;
    }
}
