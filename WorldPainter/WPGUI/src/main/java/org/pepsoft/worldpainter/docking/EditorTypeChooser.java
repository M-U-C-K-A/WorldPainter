package org.pepsoft.worldpainter.docking;

import org.pepsoft.worldpainter.docking.AreaTree.Leaf;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import static org.pepsoft.util.GUIUtils.getUIScale;

/**
 * The choice of what an area shows, like the editor type menu of Blender: the pages in columns by category, each with
 * its icon. It is shown as a menu by the button at the top left of each area, and in empty areas.
 */
final class EditorTypeChooser extends JPanel {
    /**
     * @param popup The menu the chooser is in, to close when a choice is made, or {@code null} if it is shown in an
     *              empty area.
     */
    EditorTypeChooser(AreaManager manager, Leaf leaf, JPopupMenu popup) {
        super(new GridBagLayout());
        this.manager = manager;
        this.leaf = leaf;
        this.popup = popup;
        setOpaque(popup != null);
        final JPanel columns = new JPanel(new GridLayout(1, 0, Math.round(18 * getUIScale()), 0));
        columns.setOpaque(false);
        for (String category: manager.getCategories()) {
            final List<DockableFrame> pages = manager.pagesIn(category);
            if (pages.isEmpty()) {
                continue;
            }
            final JPanel column = new JPanel();
            column.setOpaque(false);
            column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
            final JLabel heading = new JLabel(category);
            heading.putClientProperty("FlatLaf.style", "font: -1; foreground: $WorldPainter.mutedForeground");
            heading.setBorder(BorderFactory.createEmptyBorder(0, 6, 4, 6));
            heading.setAlignmentX(LEFT_ALIGNMENT);
            column.add(heading);
            for (DockableFrame page: pages) {
                final Item item = new Item(page);
                item.setAlignmentX(LEFT_ALIGNMENT);
                column.add(item);
            }
            column.add(Box.createVerticalGlue());
            columns.add(column);
        }
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.anchor = GridBagConstraints.CENTER;
        if (popup == null) {
            final JLabel title = new JLabel("What should this area show?");
            title.putClientProperty("FlatLaf.styleClass", "semibold");
            title.putClientProperty("FlatLaf.style", "font: +2");
            constraints.insets = new Insets(0, 0, 4, 0);
            add(title, constraints);
            final JLabel hint = new JLabel("<html><div style=\"text-align: center; width: " + Math.round(300 * getUIScale()) + "px\">Pages shown in another area are swapped with it. Drag from a corner of an area to split it, or into a neighbour to join them.</div></html>");
            hint.putClientProperty("FlatLaf.style", "font: -1; foreground: $WorldPainter.mutedForeground");
            hint.setHorizontalAlignment(SwingConstants.CENTER);
            constraints.insets = new Insets(0, 0, 14, 0);
            add(hint, constraints);
            constraints.insets = new Insets(0, 0, 0, 0);
        }
        add(columns, constraints);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    }

    /**
     * The choice for an empty area: centred in it, and scrolling if the area is too small.
     */
    static JComponent inline(AreaManager manager, Leaf leaf) {
        final EditorTypeChooser chooser = new EditorTypeChooser(manager, leaf, null);
        final Centred centred = new Centred(chooser);
        final JScrollPane scrollPane = new JScrollPane(centred, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        return scrollPane;
    }

    /**
     * Show the choice as a menu below a button.
     */
    static void showPopup(AreaManager manager, Leaf leaf, JComponent invoker) {
        final JPopupMenu popup = new JPopupMenu();
        popup.setLayout(new BorderLayout());
        popup.add(new EditorTypeChooser(manager, leaf, popup), BorderLayout.CENTER);
        popup.show(invoker, 0, invoker.getHeight());
    }

    private void choose(String key) {
        if (popup != null) {
            popup.setVisible(false);
        }
        manager.showPageIn(leaf, key);
    }

    private final AreaManager manager;
    private final Leaf leaf;
    private final JPopupMenu popup;

    private static final long serialVersionUID = 1L;

    /**
     * Holds the chooser in the middle of the viewport if there is room, or at its top left if not, so that it scrolls.
     */
    private static final class Centred extends JPanel implements Scrollable {
        Centred(JComponent content) {
            super(new GridBagLayout());
            setOpaque(false);
            add(content);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 64;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            final Container parent = getParent();
            return (parent instanceof JViewport) && (parent.getWidth() > getPreferredSize().width);
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            final Container parent = getParent();
            return (parent instanceof JViewport) && (parent.getHeight() > getPreferredSize().height);
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * A page to choose: its icon and name, highlighted under the mouse and if it is the one the area shows, with a dot
     * if it is shown in another area.
     */
    private final class Item extends JComponent {
        Item(DockableFrame page) {
            this.page = page;
            final Leaf shownIn = manager.getLeafOf(page.getKey());
            current = (shownIn == leaf);
            elsewhere = (shownIn != null) && (shownIn != leaf);
            final StringBuilder toolTip = new StringBuilder("<html><b>").append(page.getTitle()).append("</b>");
            if (page.getDescription() != null) {
                toolTip.append("<br><div style=\"width: 220px\">").append(page.getDescription()).append("</div>");
            }
            if (elsewhere) {
                toolTip.append("<br><font color=\"#a1a1aa\">").append(manager.isFloating(shownIn) ? "In a window of its own" : "Shown in another area").append(": the two areas will swap</font>");
            }
            setToolTipText(toolTip.append("</html>").toString());
            setFont(UIManager.getFont("Label.font"));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e) && contains(e.getPoint())) {
                        choose(page.getKey());
                    }
                }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            final FontMetrics metrics = getFontMetrics(getFont());
            final float scale = getUIScale();
            return new Dimension(Math.round(ICON_SPACE * scale) + metrics.stringWidth(page.getTitle()) + Math.round(30 * scale), Math.max(Math.round(26 * scale), metrics.getHeight() + Math.round(8 * scale)));
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            final Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                final float scale = getUIScale();
                final int width = getWidth(), height = getHeight();
                if (hover || current) {
                    Color background = UIManager.getColor(hover ? "MenuItem.selectionBackground" : "Button.selectedBackground");
                    if (background == null) {
                        background = UIManager.getColor("List.selectionBackground");
                    }
                    if (background != null) {
                        g2.setColor(background);
                        final int arc = Math.round(8 * scale);
                        g2.fillRoundRect(0, 0, width, height, arc, arc);
                    }
                }
                final Icon icon = page.getFrameIcon();
                int x = Math.round(6 * scale);
                if (icon != null) {
                    icon.paintIcon(this, g2, x, (height - icon.getIconHeight()) / 2);
                }
                x += Math.round(ICON_SPACE * scale) - Math.round(6 * scale);
                Color foreground = UIManager.getColor((hover) ? "MenuItem.selectionForeground" : "Label.foreground");
                if (foreground == null) {
                    foreground = UIManager.getColor("Label.foreground");
                }
                g2.setColor(foreground);
                g2.setFont(current ? getFont().deriveFont(Font.BOLD) : getFont());
                final FontMetrics metrics = g2.getFontMetrics();
                g2.drawString(page.getTitle(), x, (height - metrics.getHeight()) / 2 + metrics.getAscent());
                if (elsewhere) {
                    Color dot = UIManager.getColor("WorldPainter.accent");
                    if (dot == null) {
                        dot = new Color(0xf97316);
                    }
                    g2.setColor(dot);
                    final float size = 6 * scale;
                    g2.fill(new java.awt.geom.Ellipse2D.Float(width - size - 8 * scale, (height - size) / 2f, size, size));
                }
            } finally {
                g2.dispose();
            }
        }

        private final DockableFrame page;
        private final boolean current, elsewhere;
        private boolean hover;

        private static final int ICON_SPACE = 30;
        private static final long serialVersionUID = 1L;
    }
}
