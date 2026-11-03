package org.pepsoft.worldpainter.docking;

import org.pepsoft.worldpainter.docking.AreaTree.Leaf;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.HashMap;
import java.util.Map;

import static java.awt.GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT;
import static org.pepsoft.util.GUIUtils.getUIScale;

/**
 * Dragging things around: an area by its header (to move it next to another area, swap it with one, or detach it into
 * a window of its own), a page by its tab (to show it in another area, in a new area next to one, or in a window of its
 * own), or a corner of an area (to split it, or to join it with a neighbour). While dragging, what letting go would do
 * is shown on the window under the mouse. Escape cancels.
 */
final class AreaDrag {
    AreaDrag(AreaManager manager) {
        this.manager = manager;
    }

    void startArea(Leaf leaf, AreaPanel source, Point start) {
        begin(Kind.AREA, leaf, null, source, -1, start);
    }

    void startPage(String key, Leaf leaf, AreaPanel source, Point start) {
        begin(Kind.PAGE, leaf, key, source, -1, start);
    }

    void startCorner(Leaf leaf, AreaPanel source, int corner, Point start) {
        begin(Kind.CORNER, leaf, null, source, corner, start);
    }

    void update(Point point) {
        if (kind == null) {
            return;
        }
        target = (kind == Kind.CORNER) ? cornerTarget(point) : dropTarget(point);
        show(target, point);
    }

    void finish(Point point) {
        if (kind == null) {
            return;
        }
        final Target target = (kind == Kind.CORNER) ? cornerTarget(point) : dropTarget(point);
        final Kind kind = this.kind;
        final Leaf leaf = this.leaf;
        final String pageKey = this.pageKey;
        end();
        if ((target == null) || (! target.valid)) {
            return;
        }
        switch (kind) {
            case AREA:
                if (target.newWindow) {
                    if (manager.isFloating(leaf)) {
                        final AreaPanel panel = manager.getAreaPanel(leaf);
                        final Window window = (panel != null) ? SwingUtilities.getWindowAncestor(panel) : null;
                        if (window != null) {
                            window.setLocation(target.windowBounds.getLocation());
                        }
                    } else {
                        manager.detach(leaf, target.windowBounds);
                    }
                } else if (target.side == null) {
                    manager.swapAreas(leaf, target.leaf);
                } else {
                    manager.moveArea(leaf, target.leaf, target.side);
                }
                break;
            case PAGE:
                if (target.newWindow) {
                    manager.floatPage(pageKey, target.windowBounds);
                } else if (target.side == null) {
                    manager.showPageIn(target.leaf, pageKey);
                } else {
                    manager.movePageToNewArea(pageKey, target.leaf, target.side);
                }
                break;
            case CORNER:
                if (target.join) {
                    manager.join(leaf, target.leaf);
                } else {
                    manager.split(leaf, target.side, target.size);
                }
                break;
        }
    }

    boolean isDragging() {
        return kind != null;
    }

    private void begin(Kind kind, Leaf leaf, String pageKey, AreaPanel source, int corner, Point start) {
        if (this.kind != null) {
            end();
        }
        this.kind = kind;
        this.leaf = leaf;
        this.pageKey = pageKey;
        this.source = source;
        this.corner = corner;
        this.start = start;
        sourceBounds = new Rectangle(source.getLocationOnScreen(), source.getSize());
        splitHorizontally = null;
        if (kind != Kind.CORNER) {
            previousCursor = source.getCursor();
            source.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        }
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(escapeDispatcher);
    }

    private void end() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(escapeDispatcher);
        if ((kind != Kind.CORNER) && (source != null)) {
            source.setCursor(previousCursor);
        }
        kind = null;
        target = null;
        source = null;
        for (DropOverlay overlay: overlays.values()) {
            overlay.dispose();
        }
        overlays.clear();
        if (ghost != null) {
            ghost.dispose();
            ghost = null;
        }
    }

    /**
     * Where an area or a page would go if dropped at a point on the screen.
     */
    private Target dropTarget(Point point) {
        final Target target = new Target();
        // The floating windows are in front of the main window
        for (FloatingWindow window: manager.getFloatingWindows()) {
            if (window.isShowing() && window.getBounds().contains(point)) {
                target.window = window;
                target.panel = window.getAreaPanel();
                break;
            }
        }
        final JFrame owner = manager.getOwner();
        if (target.window == null) {
            if (owner.isShowing() && owner.getBounds().contains(point)) {
                target.window = owner;
                for (AreaPanel panel: manager.getAreaPanels()) {
                    if ((! panel.isFloating()) && panel.isShowing() && new Rectangle(panel.getLocationOnScreen(), panel.getSize()).contains(point)) {
                        target.panel = panel;
                        break;
                    }
                }
                if (target.panel == null) {
                    // Over the header or the status bar of the window, or a gap between areas
                    return target;
                }
            } else {
                // Outside the windows: a window of its own
                target.newWindow = true;
                final Dimension size = (kind == Kind.AREA) ? source.getSize() : preferredWindowSize();
                target.windowBounds = new Rectangle(point.x - Math.min(48, size.width / 4), point.y - 12, Math.max(size.width, Math.round(260 * getUIScale())), Math.max(size.height, Math.round(200 * getUIScale())));
                if (kind == Kind.AREA) {
                    target.valid = manager.isFloating(leaf) || manager.canDetach(leaf);
                    target.label = manager.isFloating(leaf) ? "Move window" : "New window";
                } else {
                    target.valid = true;
                    target.label = "New window";
                }
                return target;
            }
        }
        target.leaf = target.panel.getLeaf();
        final Point local = new Point(point);
        SwingUtilities.convertPointFromScreen(local, target.panel);
        final int width = target.panel.getWidth(), height = target.panel.getHeight();
        final double x = (double) local.x / Math.max(width, 1), y = (double) local.y / Math.max(height, 1);
        if (target.panel.isFloating() || ((x > CENTRE_FROM) && (x < CENTRE_TO) && (y > CENTRE_FROM) && (y < CENTRE_TO))) {
            target.side = null;
        } else {
            double distance = x;
            target.side = Side.WEST;
            if (1 - x < distance) {
                distance = 1 - x;
                target.side = Side.EAST;
            }
            if (y < distance) {
                distance = y;
                target.side = Side.NORTH;
            }
            if (1 - y < distance) {
                target.side = Side.SOUTH;
            }
        }
        final Rectangle area = new Rectangle(0, 0, width, height);
        if (target.side == null) {
            target.preview = area;
        } else {
            final int size = (kind == Kind.AREA) ? manager.dockSize(leaf, target.leaf, target.side) : half(target.leaf, target.side);
            switch (target.side) {
                case WEST:
                    target.preview = new Rectangle(0, 0, size, height);
                    break;
                case EAST:
                    target.preview = new Rectangle(width - size, 0, size, height);
                    break;
                case NORTH:
                    target.preview = new Rectangle(0, 0, width, size);
                    break;
                default:
                    target.preview = new Rectangle(0, height - size, width, size);
                    break;
            }
        }
        target.preview = SwingUtilities.convertRectangle(target.panel, target.preview, layeredPaneOf(target.window));
        final String targetName = nameOf(target.leaf);
        if (kind == Kind.AREA) {
            target.valid = (target.leaf != leaf);
            target.label = (target.side == null) ? "Swap with " + targetName : "Put " + SIDE_WORDS.get(target.side) + " " + targetName;
        } else {
            final boolean here = target.leaf == manager.getLeafOf(pageKey);
            target.valid = (target.side != null) || (! here);
            target.label = (target.side == null) ? "Show here" : "New area " + SIDE_WORDS.get(target.side) + " " + targetName;
        }
        return target;
    }

    /**
     * What dragging from a corner to a point would do: split the area, or join it with the neighbour the point is in.
     */
    private Target cornerTarget(Point point) {
        final Target target = new Target();
        target.window = manager.getOwner();
        final JLayeredPane layeredPane = layeredPaneOf(target.window);
        if (sourceBounds.contains(point)) {
            if (splitHorizontally == null) {
                final int dx = point.x - start.x, dy = point.y - start.y;
                if (Math.max(Math.abs(dx), Math.abs(dy)) < AreaPanel.DRAG_THRESHOLD) {
                    return null;
                }
                // Moving sideways divides the area into two side by side
                splitHorizontally = Math.abs(dx) >= Math.abs(dy);
            }
            final int divider = manager.getDividerSize();
            final int minimum = Math.round(MIN_SPLIT * getUIScale());
            final int across = splitHorizontally ? sourceBounds.width : sourceBounds.height;
            if (across < 2 * minimum + divider) {
                return null;
            }
            final boolean fromStart = splitHorizontally ? ((corner == 0) || (corner == 2)) : ((corner == 0) || (corner == 1));
            target.side = splitHorizontally ? (fromStart ? Side.WEST : Side.EAST) : (fromStart ? Side.NORTH : Side.SOUTH);
            int size;
            if (splitHorizontally) {
                size = fromStart ? (point.x - sourceBounds.x) : (sourceBounds.x + sourceBounds.width - point.x);
            } else {
                size = fromStart ? (point.y - sourceBounds.y) : (sourceBounds.y + sourceBounds.height - point.y);
            }
            size = Math.max(minimum, Math.min(across - minimum - divider, size));
            target.size = size;
            final Rectangle newArea;
            switch (target.side) {
                case WEST:
                    newArea = new Rectangle(sourceBounds.x, sourceBounds.y, size, sourceBounds.height);
                    break;
                case EAST:
                    newArea = new Rectangle(sourceBounds.x + sourceBounds.width - size, sourceBounds.y, size, sourceBounds.height);
                    break;
                case NORTH:
                    newArea = new Rectangle(sourceBounds.x, sourceBounds.y, sourceBounds.width, size);
                    break;
                default:
                    newArea = new Rectangle(sourceBounds.x, sourceBounds.y + sourceBounds.height - size, sourceBounds.width, size);
                    break;
            }
            target.preview = toLayeredPane(newArea, layeredPane);
            target.line = target.side;
            target.label = "Split";
            target.valid = true;
            return target;
        }
        // Outside the area: join it with the neighbour the mouse is in, if they line up
        for (AreaPanel panel: manager.getAreaPanels()) {
            if ((! panel.isFloating()) && panel.isShowing() && (panel.getLeaf() != leaf) && new Rectangle(panel.getLocationOnScreen(), panel.getSize()).contains(point)) {
                final Leaf other = panel.getLeaf();
                target.leaf = other;
                target.preview = SwingUtilities.convertRectangle(panel, new Rectangle(0, 0, panel.getWidth(), panel.getHeight()), layeredPane);
                target.join = true;
                target.valid = manager.getActive().tree.canJoin(leaf, other);
                target.arrow = AreaTree.sideOf(leaf, other);
                target.label = target.valid ? "Join" : "These areas do not line up";
                return target;
            }
        }
        return null;
    }

    private void show(Target target, Point point) {
        final Window window = (target != null) ? target.window : null;
        for (Map.Entry<Window, DropOverlay> entry: overlays.entrySet()) {
            if (entry.getKey() != window) {
                entry.getValue().show(null);
            }
        }
        if (window != null) {
            overlayFor(window).show(target);
        }
        // A ghost of the window an area or page would get
        if ((target != null) && target.newWindow && target.valid) {
            if (ghost == null) {
                ghost = Ghost.create(manager.getOwner());
            }
            if (ghost != null) {
                ghost.setLabel(target.label);
                ghost.setBounds(target.windowBounds);
                if (! ghost.isVisible()) {
                    ghost.setVisible(true);
                }
            }
        } else if ((ghost != null) && ghost.isVisible()) {
            ghost.setVisible(false);
        }
    }

    private DropOverlay overlayFor(Window window) {
        DropOverlay overlay = overlays.get(window);
        final JLayeredPane layeredPane = layeredPaneOf(window);
        if (overlay == null) {
            overlay = new DropOverlay();
            overlays.put(window, overlay);
        }
        if (overlay.getParent() != layeredPane) {
            layeredPane.add(overlay, JLayeredPane.DRAG_LAYER);
        }
        overlay.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
        return overlay;
    }

    private Dimension preferredWindowSize() {
        final DockableFrame page = manager.getPage(pageKey);
        final Dimension preferred = (page != null) ? page.getPreferredSize() : new Dimension(320, 400);
        return new Dimension(Math.min(preferred.width + 40, Math.round(420 * getUIScale())), Math.min(preferred.height + 60, Math.round(560 * getUIScale())));
    }

    private int half(Leaf target, Side side) {
        return ((side.isHorizontal() ? target.bounds.width : target.bounds.height) - manager.getDividerSize()) / 2;
    }

    private String nameOf(Leaf target) {
        final String key = manager.getShownPage(target);
        final DockableFrame page = (key != null) ? manager.getPage(key) : null;
        return (page != null) ? page.getTitle() : "the empty area";
    }

    private static JLayeredPane layeredPaneOf(Window window) {
        return ((RootPaneContainer) window).getLayeredPane();
    }

    private static Rectangle toLayeredPane(Rectangle screenBounds, JLayeredPane layeredPane) {
        final Point location = screenBounds.getLocation();
        SwingUtilities.convertPointFromScreen(location, layeredPane);
        return new Rectangle(location, screenBounds.getSize());
    }

    private final AreaManager manager;
    private final Map<Window, DropOverlay> overlays = new HashMap<>();
    private final KeyEventDispatcher escapeDispatcher = event -> {
        if ((event.getID() == KeyEvent.KEY_PRESSED) && (event.getKeyCode() == KeyEvent.VK_ESCAPE) && (this.kind != null)) {
            end();
            return true;
        }
        return false;
    };
    private Kind kind;
    private Leaf leaf;
    private String pageKey;
    private AreaPanel source;
    private int corner;
    private Point start;
    private Rectangle sourceBounds;
    private Boolean splitHorizontally;
    private Cursor previousCursor;
    private Target target;
    private Ghost ghost;

    private static final double CENTRE_FROM = 0.3, CENTRE_TO = 0.7;
    private static final int MIN_SPLIT = 48;
    private static final Map<Side, String> SIDE_WORDS = Map.of(Side.WEST, "left of", Side.EAST, "right of", Side.NORTH, "above", Side.SOUTH, "below");

    private enum Kind {AREA, PAGE, CORNER}

    private static final class Target {
        Window window;
        AreaPanel panel;
        Leaf leaf;
        Side side, line, arrow;
        Rectangle preview, windowBounds;
        String label;
        int size;
        boolean valid, join, newWindow;
    }

    /**
     * Shows what letting go would do, over a window.
     */
    private static final class DropOverlay extends JComponent {
        void show(Target target) {
            this.target = target;
            repaint();
        }

        void dispose() {
            target = null;
            final Container parent = getParent();
            if (parent != null) {
                parent.remove(this);
                parent.repaint();
            }
        }

        @Override
        public boolean contains(int x, int y) {
            // Let the mouse through
            return false;
        }

        @Override
        protected void paintComponent(Graphics g) {
            final Target target = this.target;
            if ((target == null) || (target.preview == null)) {
                return;
            }
            final Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                final Color accent = accent();
                final Rectangle r = target.preview;
                final float scale = getUIScale();
                if (target.join) {
                    // Darken the area which will be taken over, with an arrow into it
                    g2.setColor(new Color(0, 0, 0, target.valid ? 110 : 60));
                    g2.fill(r);
                    if (target.valid && (target.arrow != null)) {
                        paintArrow(g2, r, target.arrow, scale);
                    }
                } else {
                    final float arc = 10 * scale;
                    final RoundRectangle2D shape = new RoundRectangle2D.Float(r.x + 2, r.y + 2, r.width - 4, r.height - 4, arc, arc);
                    g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), target.valid ? 60 : 20));
                    g2.fill(shape);
                    g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), target.valid ? 220 : 90));
                    g2.setStroke(new BasicStroke(2 * scale));
                    g2.draw(shape);
                    if (target.line != null) {
                        // The line along which the area will be split
                        g2.setStroke(new BasicStroke(2.5f * scale));
                        switch (target.line) {
                            case WEST:
                                g2.drawLine(r.x + r.width, r.y, r.x + r.width, r.y + r.height);
                                break;
                            case EAST:
                                g2.drawLine(r.x, r.y, r.x, r.y + r.height);
                                break;
                            case NORTH:
                                g2.drawLine(r.x, r.y + r.height, r.x + r.width, r.y + r.height);
                                break;
                            default:
                                g2.drawLine(r.x, r.y, r.x + r.width, r.y);
                                break;
                        }
                    }
                }
                if (target.label != null) {
                    paintLabel(g2, target.label, r, target.valid, scale);
                }
            } finally {
                g2.dispose();
            }
        }

        private static void paintLabel(Graphics2D g2, String label, Rectangle r, boolean valid, float scale) {
            final Font font = UIManager.getFont("Label.font").deriveFont(Font.BOLD);
            g2.setFont(font);
            final FontMetrics metrics = g2.getFontMetrics();
            final int padX = Math.round(12 * scale), padY = Math.round(6 * scale);
            final int width = metrics.stringWidth(label) + 2 * padX, height = metrics.getHeight() + 2 * padY;
            final int x = r.x + (r.width - width) / 2, y = r.y + (r.height - height) / 2;
            final Color accent = accent();
            g2.setColor(valid ? accent : new Color(80, 80, 80, 220));
            g2.fill(new RoundRectangle2D.Float(x, y, width, height, height, height));
            g2.setColor(Color.WHITE);
            g2.drawString(label, x + padX, y + padY + metrics.getAscent());
        }

        private static void paintArrow(Graphics2D g2, Rectangle r, Side direction, float scale) {
            final float cx = (float) r.getCenterX(), cy = (float) r.getCenterY();
            final float length = Math.min(Math.min(r.width, r.height) * 0.35f, 70 * scale), head = Math.min(length * 0.5f, 22 * scale);
            final Path2D arrow = new Path2D.Float();
            final float dx, dy;
            switch (direction) {
                case WEST:
                    dx = -1;
                    dy = 0;
                    break;
                case EAST:
                    dx = 1;
                    dy = 0;
                    break;
                case NORTH:
                    dx = 0;
                    dy = -1;
                    break;
                default:
                    dx = 0;
                    dy = 1;
                    break;
            }
            final float x1 = cx - dx * length, y1 = cy - dy * length, x2 = cx + dx * length, y2 = cy + dy * length;
            arrow.moveTo(x1, y1);
            arrow.lineTo(x2, y2);
            arrow.moveTo(x2 - dx * head - dy * head, y2 - dy * head - dx * head);
            arrow.lineTo(x2, y2);
            arrow.lineTo(x2 - dx * head + dy * head, y2 - dy * head + dx * head);
            g2.setColor(new Color(255, 255, 255, 200));
            g2.setStroke(new BasicStroke(5 * scale, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(arrow);
        }

        private Target target;

        private static final long serialVersionUID = 1L;
    }

    /**
     * An outline of the window an area or page will get when it is dropped outside the windows.
     */
    private static final class Ghost extends JWindow {
        private Ghost(Window owner) {
            super(owner);
            setFocusableWindowState(false);
            setBackground(new Color(0, 0, 0, 0));
            final JComponent content = new JComponent() {
                @Override
                protected void paintComponent(Graphics g) {
                    final Graphics2D g2 = (Graphics2D) g.create();
                    try {
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                        final float scale = getUIScale(), arc = 12 * scale;
                        final Color accent = accent();
                        final Rectangle r = new Rectangle(0, 0, getWidth(), getHeight());
                        final RoundRectangle2D shape = new RoundRectangle2D.Float(1, 1, r.width - 2, r.height - 2, arc, arc);
                        g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 50));
                        g2.fill(shape);
                        g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 220));
                        g2.setStroke(new BasicStroke(2 * scale));
                        g2.draw(shape);
                        if (label != null) {
                            DropOverlay.paintLabel(g2, label, r, true, scale);
                        }
                    } finally {
                        g2.dispose();
                    }
                }
            };
            content.setOpaque(false);
            setContentPane(content);
        }

        static Ghost create(Window owner) {
            final GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            if (! device.isWindowTranslucencySupported(PERPIXEL_TRANSLUCENT)) {
                return null;
            }
            return new Ghost(owner);
        }

        void setLabel(String label) {
            if (! label.equals(this.label)) {
                this.label = label;
                repaint();
            }
        }

        private String label;

        private static final long serialVersionUID = 1L;
    }

    private static Color accent() {
        final Color accent = UIManager.getColor("Component.accentColor");
        return (accent != null) ? accent : new Color(0x0071e3);
    }
}
