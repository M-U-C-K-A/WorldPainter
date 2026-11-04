package org.pepsoft.worldpainter.docking;

import com.formdev.flatlaf.FlatClientProperties;
import org.pepsoft.worldpainter.docking.AreaTree.Leaf;
import org.pepsoft.worldpainter.util.HairlineBorder;
import org.pepsoft.worldpainter.util.Icons;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.List;

import static org.pepsoft.util.GUIUtils.getUIScale;

/**
 * An area of the window: a header with the editor type button at its left, which chooses what the area shows, the
 * name of the page, and buttons to maximise, detach and close it; tabs down the left side for the other pages of the
 * same kind; and the page itself. Dragging the header moves the area elsewhere; dragging from one of its corners
 * splits it, or joins it with a neighbour, as in Blender.
 */
final class AreaPanel extends JPanel {
    AreaPanel(AreaManager manager, Leaf leaf, boolean floating) {
        super(null);
        this.manager = manager;
        this.leaf = leaf;
        this.floating = floating;
        setLayout(new AreaLayout());
        header = new Header();
        tabStrip = new TabStrip();
        content = new JPanel(new BorderLayout());
        content.setOpaque(false);
        corners = floating ? null : new CornerZones();
        if (corners != null) {
            // First, so that it is on top
            add(corners);
        }
        add(header);
        add(tabStrip);
        add(content);
    }

    AreaManager getManager() {
        return manager;
    }

    Leaf getLeaf() {
        return leaf;
    }

    boolean isFloating() {
        return floating;
    }

    DockableFrame getPage() {
        return page;
    }

    /**
     * Show a page, or the choice of what to show if it is {@code null}, and bring the header and the tabs up to date.
     */
    void update(DockableFrame page) {
        this.page = page;
        final Component current = (content.getComponentCount() > 0) ? content.getComponent(0) : null;
        if ((page == null) || (current != page)) {
            content.removeAll();
            if (page != null) {
                page.setVisible(true);
                content.add(page, BorderLayout.CENTER);
            } else {
                content.add(EditorTypeChooser.inline(manager, leaf), BorderLayout.CENTER);
            }
        }
        header.update();
        tabStrip.update();
        revalidate();
        repaint();
    }

    /**
     * The title or icon of a page changed.
     */
    void pageChanged(DockableFrame changed) {
        if (changed == page) {
            header.update();
        }
        tabStrip.update();
    }

    /**
     * Shift was pressed or let go.
     */
    void setShiftDown(boolean shiftDown) {
        header.setShiftDown(shiftDown);
    }

    void dispose() {
        content.removeAll();
        tabStrip.removeAll();
    }

    /**
     * The commands for the area, shown by a right click on its header or with its menu button.
     */
    private JPopupMenu createMenu() {
        final JPopupMenu menu = new JPopupMenu();
        manager.fillAreaMenu(menu, leaf);
        return menu;
    }

    private final AreaManager manager;
    private final Leaf leaf;
    private final boolean floating;
    private final Header header;
    private final TabStrip tabStrip;
    private final JPanel content;
    private final CornerZones corners;
    private DockableFrame page;

    private static final long serialVersionUID = 1L;

    /**
     * The red of the bin which closes an area while Shift is held down.
     */
    private static final Color CLOSE_COLOUR = new Color(0xe5484d);

    /**
     * The header at the top, the tabs (if any) down the left side below it, and the page in the rest. The corner
     * zones are on top of everything.
     */
    private final class AreaLayout implements LayoutManager {
        @Override public void addLayoutComponent(String name, Component comp) {}
        @Override public void removeLayoutComponent(Component comp) {}

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            final Dimension headerSize = header.getPreferredSize(), contentSize = content.getPreferredSize();
            final int tabsWidth = tabStrip.isVisible() ? tabStrip.getPreferredSize().width : 0;
            return new Dimension(Math.max(headerSize.width, tabsWidth + contentSize.width), headerSize.height + contentSize.height);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return new Dimension(Math.round(MIN_WIDTH * getUIScale()), header.getPreferredSize().height);
        }

        @Override
        public void layoutContainer(Container parent) {
            final int width = parent.getWidth(), height = parent.getHeight();
            final int headerHeight = Math.min(height, header.getPreferredSize().height);
            header.setBounds(0, 0, width, headerHeight);
            final int tabsWidth = tabStrip.isVisible() ? Math.min(width, tabStrip.getPreferredSize().width) : 0;
            tabStrip.setBounds(0, headerHeight, tabsWidth, height - headerHeight);
            content.setBounds(tabsWidth, headerHeight, width - tabsWidth, height - headerHeight);
            if (corners != null) {
                corners.setBounds(0, 0, width, height);
            }
        }
    }

    /**
     * The header of the area.
     */
    private final class Header extends JPanel {
        Header() {
            super(null);
            setLayout(new HeaderLayout());
            setBorder(new HairlineBorder(false, false, true, false, new Insets(3, 6, 3, 4)));
            typeButton = new JButton();
            typeButton.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
            typeButton.setFocusable(false);
            typeButton.setMargin(new Insets(3, 4, 3, 4));
            typeButton.setToolTipText("<html><b>Editor type</b><br>Choose what this area shows</html>");
            typeButton.addActionListener(e -> EditorTypeChooser.showPopup(manager, leaf, typeButton));
            titleLabel = new JLabel();
            titleLabel.putClientProperty("FlatLaf.styleClass", "semibold");
            buttons = new JToolBar();
            buttons.setFloatable(false);
            buttons.setOpaque(false);
            buttons.setBorder(null);
            if (floating) {
                pinButton = new JToggleButton(Icons.muted("pin", 14));
                pinButton.setSelectedIcon(Icons.accent("pin", 14));
                pinButton.setToolTipText("<html><b>Keep on top</b><br>Keep this window in front of other applications too</html>");
                pinButton.addActionListener(e -> manager.setOnTop(leaf, pinButton.isSelected()));
                buttons.add(pinButton);
                maximiseButton = null;
                detachButton = new JButton(Icons.muted("picture-in-picture", 14));
                detachButton.setToolTipText("<html><b>Put back</b><br>Put this area back in the main window, where it was. You can also drag its header onto the main window</html>");
                detachButton.addActionListener(e -> manager.dockBack(leaf));
                buttons.add(detachButton);
            } else {
                pinButton = null;
                maximiseButton = new JButton();
                maximiseButton.addActionListener(e -> manager.toggleMaximise(leaf));
                buttons.add(maximiseButton);
                detachButton = new JButton(Icons.muted("picture-in-picture-2", 14));
                detachButton.setToolTipText("<html><b>Open in a new window</b><br>Detach this area into a window of its own, which floats over the main window. You can also drag its header out of the window</html>");
                detachButton.addActionListener(e -> manager.detach(leaf, null));
                buttons.add(detachButton);
            }
            menuButton = new JButton();
            menuButton.addActionListener(e -> {
                if (closing) {
                    manager.closeArea(leaf);
                } else {
                    createMenu().show(menuButton, 0, menuButton.getHeight());
                }
            });
            buttons.add(menuButton);
            updateMenuButton();
            for (Component button: buttons.getComponents()) {
                ((JComponent) button).setFocusable(false);
            }
            add(typeButton);
            add(titleLabel);
            add(buttons);

            final MouseAdapter mouseAdapter = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        showMenu(e);
                    } else if (SwingUtilities.isLeftMouseButton(e)) {
                        pressPoint = e.getLocationOnScreen();
                        dragging = false;
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (pressPoint == null) {
                        return;
                    }
                    if ((! dragging) && (pressPoint.distance(e.getLocationOnScreen()) > DRAG_THRESHOLD)) {
                        dragging = true;
                        manager.getDrag().startArea(leaf, AreaPanel.this, pressPoint);
                    }
                    if (dragging) {
                        manager.getDrag().update(e.getLocationOnScreen());
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        showMenu(e);
                    } else if (dragging) {
                        manager.getDrag().finish(e.getLocationOnScreen());
                    }
                    pressPoint = null;
                    dragging = false;
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e) && (e.getClickCount() == 2) && (! floating)) {
                        manager.toggleMaximise(leaf);
                    }
                }

                private void showMenu(MouseEvent e) {
                    createMenu().show(e.getComponent(), e.getX(), e.getY());
                }

                private Point pressPoint;
                private boolean dragging;
            };
            addMouseListener(mouseAdapter);
            addMouseMotionListener(mouseAdapter);
            titleLabel.addMouseListener(mouseAdapter);
            titleLabel.addMouseMotionListener(mouseAdapter);
            setToolTipText(null);
        }

        void update() {
            if (page != null) {
                typeButton.setIcon(new ChevronIcon(page.getFrameIcon()));
                titleLabel.setText(page.getTitle());
                titleLabel.putClientProperty(FlatClientProperties.STYLE, null);
            } else {
                typeButton.setIcon(new ChevronIcon(Icons.get("layout-grid", 16)));
                titleLabel.setText("Empty area");
                titleLabel.putClientProperty(FlatClientProperties.STYLE, "foreground: $WorldPainter.mutedForeground");
            }
            if (maximiseButton != null) {
                final boolean maximised = manager.isMaximised(leaf);
                maximiseButton.setIcon(maximised ? Icons.accent("minimize-2", 14) : Icons.muted("maximize-2", 14));
                maximiseButton.setToolTipText(maximised
                        ? "<html><b>Restore</b>&nbsp;&nbsp;<font color=\"#a1a1aa\">Ctrl+Space</font><br>Show the other areas again</html>"
                        : "<html><b>Maximise</b>&nbsp;&nbsp;<font color=\"#a1a1aa\">Ctrl+Space</font><br>Let this area fill the window for now. Double-clicking the header does the same</html>");
                maximiseButton.setEnabled(maximised || manager.canDetach(leaf));
                detachButton.setEnabled(manager.canDetach(leaf));
            }
            if (pinButton != null) {
                pinButton.setSelected(manager.isOnTop(leaf));
            }
            closing = manager.isShiftDown() && manager.canClose(leaf);
            updateMenuButton();
            revalidate();
            repaint();
        }

        /**
         * While Shift is held down the menu button closes the area instead, shown as a red bin, to clear away areas
         * quickly.
         */
        void setShiftDown(boolean shiftDown) {
            final boolean closing = shiftDown && manager.canClose(leaf);
            if (closing != this.closing) {
                this.closing = closing;
                updateMenuButton();
            }
        }

        private void updateMenuButton() {
            if (closing) {
                menuButton.setIcon(Icons.tinted("trash-2", 14, CLOSE_COLOUR));
                menuButton.setToolTipText("<html><b>Close area</b><br>Its neighbour takes its room. Let go of Shift for the menu of the area</html>");
            } else {
                menuButton.setIcon(Icons.muted("ellipsis-vertical", 14));
                menuButton.setToolTipText("<html><b>Area</b><br>Split, join, maximise or close this area. Right click the header for the same; hold Shift to close it with one click</html>");
            }
        }

        private final JButton typeButton, maximiseButton, detachButton, menuButton;
        private boolean closing;
        private final JToggleButton pinButton;
        private final JLabel titleLabel;
        private final JToolBar buttons;

        private static final long serialVersionUID = 1L;

        /**
         * The editor type button on the left, the buttons on the right as far as there is room for them, and the
         * title in between.
         */
        private final class HeaderLayout implements LayoutManager {
            @Override public void addLayoutComponent(String name, Component comp) {}
            @Override public void removeLayoutComponent(Component comp) {}

            @Override
            public Dimension preferredLayoutSize(Container parent) {
                final Insets insets = parent.getInsets();
                final Dimension type = typeButton.getPreferredSize(), title = titleLabel.getPreferredSize(), tools = buttons.getPreferredSize();
                return new Dimension(insets.left + type.width + GAP + title.width + GAP + tools.width + insets.right,
                        insets.top + Math.max(type.height, Math.max(title.height, tools.height)) + insets.bottom);
            }

            @Override
            public Dimension minimumLayoutSize(Container parent) {
                final Insets insets = parent.getInsets();
                final Dimension type = typeButton.getPreferredSize();
                return new Dimension(insets.left + type.width + insets.right, preferredLayoutSize(parent).height);
            }

            @Override
            public void layoutContainer(Container parent) {
                final Insets insets = parent.getInsets();
                final int width = parent.getWidth() - insets.left - insets.right, height = parent.getHeight() - insets.top - insets.bottom;
                final Dimension type = typeButton.getPreferredSize(), tools = buttons.getPreferredSize();
                typeButton.setBounds(insets.left, insets.top + (height - type.height) / 2, type.width, type.height);
                int right = insets.left + width;
                // Leave out the buttons if there is not enough room for them
                final boolean showButtons = width >= type.width + GAP + Math.round(24 * getUIScale()) + tools.width;
                buttons.setVisible(showButtons);
                if (showButtons) {
                    right -= tools.width;
                    buttons.setBounds(right, insets.top + (height - tools.height) / 2, tools.width, tools.height);
                    right -= GAP;
                }
                final int titleX = insets.left + type.width + GAP;
                titleLabel.setBounds(titleX, insets.top, Math.max(0, right - titleX), height);
            }
        }
    }

    /**
     * The tabs for the pages of the same kind as the one shown, down the left side.
     */
    private final class TabStrip extends JPanel {
        TabStrip() {
            super(new BorderLayout());
            setBorder(new HairlineBorder(false, false, false, true, new Insets(0, 0, 0, 0)));
            column = new JPanel();
            column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
            column.setBorder(BorderFactory.createEmptyBorder(4, 3, 4, 3));
            column.setOpaque(false);
            final JScrollPane scrollPane = new JScrollPane(column, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scrollPane.setBorder(BorderFactory.createEmptyBorder());
            scrollPane.setOpaque(false);
            scrollPane.getViewport().setOpaque(false);
            scrollPane.getVerticalScrollBar().putClientProperty(FlatClientProperties.STYLE, "width: 4");
            scrollPane.getVerticalScrollBar().setUnitIncrement(16);
            add(scrollPane, BorderLayout.CENTER);
            setVisible(false);
        }

        void update() {
            column.removeAll();
            final List<DockableFrame> pages = (page != null) ? manager.pagesOf(page.getGroup()) : List.of();
            if (pages.size() < 2) {
                setVisible(false);
                return;
            }
            final ButtonGroup buttonGroup = new ButtonGroup();
            for (DockableFrame tabPage: pages) {
                final String key = tabPage.getKey();
                final Leaf shownIn = manager.getLeafOf(key);
                final boolean elsewhere = (shownIn != null) && (shownIn != leaf);
                final JToggleButton button = new TabButton(tabPage.getFrameIcon(), elsewhere);
                button.setSelected(tabPage == page);
                final StringBuilder toolTip = new StringBuilder("<html><b>").append(tabPage.getTitle()).append("</b>");
                if (tabPage.getDescription() != null) {
                    toolTip.append("<br><div style=\"width: 220px\">").append(tabPage.getDescription()).append("</div>");
                }
                if (elsewhere) {
                    toolTip.append("<br><font color=\"#a1a1aa\">Shown in another area: click to swap it with this one</font>");
                } else {
                    toolTip.append("<br><font color=\"#a1a1aa\">Drag it onto another area, or out of the window, to see it at the same time</font>");
                }
                button.setToolTipText(toolTip.append("</html>").toString());
                button.addActionListener(e -> manager.showPageIn(leaf, key));
                final MouseAdapter dragAdapter = new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        pressPoint = SwingUtilities.isLeftMouseButton(e) ? e.getLocationOnScreen() : null;
                        dragging = false;
                    }

                    @Override
                    public void mouseDragged(MouseEvent e) {
                        if (pressPoint == null) {
                            return;
                        }
                        if ((! dragging) && (pressPoint.distance(e.getLocationOnScreen()) > DRAG_THRESHOLD)) {
                            dragging = true;
                            // Make sure letting go does not also click the button
                            button.getModel().setArmed(false);
                            button.getModel().setPressed(false);
                            manager.getDrag().startPage(key, leaf, AreaPanel.this, pressPoint);
                        }
                        if (dragging) {
                            manager.getDrag().update(e.getLocationOnScreen());
                        }
                    }

                    @Override
                    public void mouseReleased(MouseEvent e) {
                        if (dragging) {
                            manager.getDrag().finish(e.getLocationOnScreen());
                        }
                        pressPoint = null;
                        dragging = false;
                    }

                    private Point pressPoint;
                    private boolean dragging;
                };
                button.addMouseListener(dragAdapter);
                button.addMouseMotionListener(dragAdapter);
                buttonGroup.add(button);
                button.setAlignmentX(Component.CENTER_ALIGNMENT);
                column.add(button);
                column.add(Box.createVerticalStrut(2));
            }
            setVisible(true);
            revalidate();
            repaint();
        }

        private final JPanel column;

        private static final long serialVersionUID = 1L;
    }

    /**
     * A tab: the icon of a page, with a dot if the page is shown in another area.
     */
    private static final class TabButton extends JToggleButton {
        TabButton(Icon icon, boolean elsewhere) {
            super(icon);
            this.elsewhere = elsewhere;
            putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
            setFocusable(false);
            final int size = Math.round(28 * getUIScale());
            setMargin(new Insets(2, 2, 2, 2));
            setPreferredSize(new Dimension(size, size));
            setMinimumSize(new Dimension(size, size));
            setMaximumSize(new Dimension(size, size));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (elsewhere) {
                final Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    final Color accent = UIManager.getColor("WorldPainter.accent");
                    g2.setColor((accent != null) ? accent : new Color(0xf97316));
                    final float dot = 5 * getUIScale();
                    g2.fill(new java.awt.geom.Ellipse2D.Float(getWidth() - dot - 2, 2, dot, dot));
                } finally {
                    g2.dispose();
                }
            }
        }

        private final boolean elsewhere;

        private static final long serialVersionUID = 1L;
    }

    /**
     * The corners of the area, from which to drag to split it or to join it with a neighbour. Only the corners belong
     * to it, so that everywhere else the mouse reaches what is under it.
     */
    private final class CornerZones extends JComponent {
        CornerZones() {
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            setToolTipText("<html><b>Split or join</b><br>Drag into the area to split it in two, or into the area next to it to join them</html>");
            final MouseAdapter mouseAdapter = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    setHover(cornerAt(e.getX(), e.getY()));
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    setHover(cornerAt(e.getX(), e.getY()));
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (! dragging) {
                        setHover(-1);
                    }
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        pressCorner = cornerAt(e.getX(), e.getY());
                        pressPoint = e.getLocationOnScreen();
                        dragging = false;
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if ((pressPoint == null) || (pressCorner < 0)) {
                        return;
                    }
                    if ((! dragging) && (pressPoint.distance(e.getLocationOnScreen()) > DRAG_THRESHOLD)) {
                        dragging = true;
                        manager.getDrag().startCorner(leaf, AreaPanel.this, pressCorner, pressPoint);
                    }
                    if (dragging) {
                        manager.getDrag().update(e.getLocationOnScreen());
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (dragging) {
                        manager.getDrag().finish(e.getLocationOnScreen());
                    }
                    pressPoint = null;
                    dragging = false;
                    setHover(-1);
                }

                private Point pressPoint;
                private int pressCorner = -1;
            };
            addMouseListener(mouseAdapter);
            addMouseMotionListener(mouseAdapter);
        }

        @Override
        public boolean contains(int x, int y) {
            return cornerAt(x, y) >= 0;
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (hover < 0) {
                return;
            }
            final Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                final Color accent = UIManager.getColor("Component.accentColor");
                g2.setColor((accent != null) ? accent : new Color(0x0071e3));
                g2.setStroke(new BasicStroke(1.5f * getUIScale(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                final int w = getWidth(), h = getHeight();
                final float size = zoneSize();
                // Two short diagonal lines across the corner, like a grip
                for (float d: new float[] {size * 0.45f, size * 0.85f}) {
                    final Path2D line = new Path2D.Float();
                    switch (hover) {
                        case 0:
                            line.moveTo(1, d);
                            line.lineTo(d, 1);
                            break;
                        case 1:
                            line.moveTo(w - 1, d);
                            line.lineTo(w - d, 1);
                            break;
                        case 2:
                            line.moveTo(1, h - d);
                            line.lineTo(d, h - 1);
                            break;
                        default:
                            line.moveTo(w - 1, h - d);
                            line.lineTo(w - d, h - 1);
                            break;
                    }
                    g2.draw(line);
                }
            } finally {
                g2.dispose();
            }
        }

        private void setHover(int corner) {
            if (corner != hover) {
                hover = corner;
                repaint();
            }
        }

        /**
         * Which corner a point is in: 0 top left, 1 top right, 2 bottom left, 3 bottom right, or -1 for none.
         */
        private int cornerAt(int x, int y) {
            final int w = getWidth(), h = getHeight();
            final float size = zoneSize();
            if (x + y < size) {
                return 0;
            } else if ((w - x) + y < size) {
                return 1;
            } else if (x + (h - y) < size) {
                return 2;
            } else if ((w - x) + (h - y) < size) {
                return 3;
            } else {
                return -1;
            }
        }

        private float zoneSize() {
            return 12 * getUIScale();
        }

        private int hover = -1;
        private boolean dragging;

        private static final long serialVersionUID = 1L;
    }

    /**
     * An icon followed by a small chevron, for the editor type button.
     */
    static final class ChevronIcon implements Icon {
        ChevronIcon(Icon icon) {
            this.icon = icon;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            final int height = getIconHeight();
            if (icon != null) {
                icon.paintIcon(c, g, x, y + (height - icon.getIconHeight()) / 2);
            }
            final Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                final Color muted = UIManager.getColor("WorldPainter.mutedForeground");
                g2.setColor((muted != null) ? muted : Color.GRAY);
                g2.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                final float scale = getUIScale();
                final float cx = x + getIconWidth() - 4 * scale, cy = y + height / 2f;
                final Path2D chevron = new Path2D.Float();
                chevron.moveTo(cx - 3 * scale, cy - 1.5f * scale);
                chevron.lineTo(cx, cy + 1.5f * scale);
                chevron.lineTo(cx + 3 * scale, cy - 1.5f * scale);
                g2.draw(chevron);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return ((icon != null) ? icon.getIconWidth() : 0) + Math.round(12 * getUIScale());
        }

        @Override
        public int getIconHeight() {
            return Math.max((icon != null) ? icon.getIconHeight() : 0, Math.round(16 * getUIScale()));
        }

        private final Icon icon;
    }

    private static final int GAP = 6, MIN_WIDTH = 60;
    static final int DRAG_THRESHOLD = 6;
}
