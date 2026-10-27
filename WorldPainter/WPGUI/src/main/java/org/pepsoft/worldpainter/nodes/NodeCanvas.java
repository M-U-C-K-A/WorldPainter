package org.pepsoft.worldpainter.nodes;

import org.pepsoft.util.DesktopUtils;
import org.pepsoft.worldpainter.util.Icons;
import org.pepsoft.worldpainter.util.ThemeUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;

/**
 * A pannable and zoomable editor for a {@link NodeGraph}: nodes with input and output ports, connected by bezier
 * wires, each showing a thumbnail of its output.
 *
 * <p>Drag a node to move it, drag empty space to pan and use the mouse wheel to zoom. Shift-click or Shift-drag on empty
 * space to select several nodes. Drag from an output to an input (or the other way around) to connect them; drag a
 * connected input away to disconnect it; release a wire on empty space to add a node there which is connected to it.
 * Right-click or double-click for a menu.
 */
public class NodeCanvas extends JComponent {
    public NodeCanvas(NodeGraph graph) {
        this.graph = graph;
        setFocusable(true);
        setOpaque(true);
        final MouseHandler mouseHandler = new MouseHandler();
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
        addMouseWheelListener(mouseHandler);
        installKeyBindings();
        setToolTipText("");
    }

    public NodeGraph getGraph() {
        return graph;
    }

    /**
     * Show a different graph, clearing the selection and fitting the view to it.
     */
    public void setGraph(NodeGraph graph) {
        setGraph(graph, true);
    }

    /**
     * Show a different graph. If {@code fit} is {@code false} the view is kept, as is the selection for the nodes
     * which have the same IDs (for undo and redo).
     */
    public void setGraph(NodeGraph graph, boolean fit) {
        final List<Integer> selectedIds = new ArrayList<>();
        selection.forEach(node -> selectedIds.add(node.getId()));
        final int primaryId = (selectedNode != null) ? selectedNode.getId() : -1;
        this.graph = graph;
        selection.clear();
        selectedNode = null;
        if (fit) {
            thumbnails.clear();
            notifySelection(null);
            fitView();
        } else {
            for (int id: selectedIds) {
                final Node node = graph.getNode(id);
                if (node != null) {
                    selection.add(node);
                }
            }
            notifySelection(graph.getNode(primaryId));
            repaint();
        }
    }

    /**
     * The node whose settings are shown: the one selected last.
     */
    public Node getSelectedNode() {
        return selectedNode;
    }

    /**
     * All selected nodes.
     */
    public Set<Node> getSelectedNodes() {
        return Collections.unmodifiableSet(selection);
    }

    /**
     * Select only one node, or nothing.
     */
    public void setSelectedNode(Node node) {
        selection.clear();
        if (node != null) {
            selection.add(node);
        }
        notifySelection(node);
        repaint();
    }

    public void selectAll() {
        selection.addAll(graph.getNodes());
        repaint();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * Set the thumbnails to show on the nodes, by node ID. Nodes without a thumbnail show a placeholder.
     */
    public void setThumbnails(Map<Integer, BufferedImage> thumbnails) {
        this.thumbnails.clear();
        this.thumbnails.putAll(thumbnails);
        repaint();
    }

    /**
     * Zoom and pan so that all nodes are visible.
     */
    public void fitView() {
        if ((getWidth() <= 0) || (getHeight() <= 0)) {
            fitPending = true;
            return;
        }
        fitPending = false;
        Rectangle2D bounds = null;
        for (Node node: graph.getNodes()) {
            final Rectangle2D nodeBounds = getBounds(node);
            if (bounds == null) {
                bounds = nodeBounds;
            } else {
                bounds.add(nodeBounds);
            }
        }
        if (bounds == null) {
            return;
        }
        final double margin = 40;
        zoom = Math.max(MIN_ZOOM, Math.min(1.0, Math.min((getWidth() - 2 * margin) / bounds.getWidth(), (getHeight() - 2 * margin) / bounds.getHeight())));
        offsetX = (getWidth() - bounds.getWidth() * zoom) / 2 - bounds.getX() * zoom;
        offsetY = (getHeight() - bounds.getHeight() * zoom) / 2 - bounds.getY() * zoom;
        repaint();
    }

    /**
     * Add a node of the specified type in the centre of the view, connect the selected node to it if possible and
     * select it.
     */
    public void addNode(NodeType type) {
        final Point2D centre = toCanvas(new Point(getWidth() / 2, getHeight() / 2));
        final Node node = type.create();
        node.setLocation(centre.getX() - NODE_WIDTH / 2.0, centre.getY() - 80);
        graph.add(node);
        if ((selectedNode != null) && (! node.getInputNames().isEmpty()) && graph.canConnect(selectedNode, node, 0)) {
            graph.connect(selectedNode, node, 0);
            node.setLocation(selectedNode.getX() + NODE_WIDTH + 64, selectedNode.getY());
        }
        setSelectedNode(node);
        graphChanged(true);
    }

    /**
     * Delete the selected nodes, except the output node.
     */
    public void deleteSelection() {
        final List<Node> nodes = getEditableSelection();
        if (nodes.isEmpty()) {
            DesktopUtils.beep();
            return;
        }
        nodes.forEach(graph::remove);
        setSelectedNode(null);
        graphChanged(true);
    }

    /**
     * Duplicate the selected nodes (except the output node), with the connections between them and to their inputs.
     */
    public void duplicateSelection() {
        final List<Node> nodes = getEditableSelection();
        if (nodes.isEmpty()) {
            DesktopUtils.beep();
            return;
        }
        final Set<Integer> ids = new HashSet<>();
        nodes.forEach(node -> ids.add(node.getId()));
        final List<Connection> connections = new ArrayList<>();
        for (Connection connection: graph.getConnections()) {
            if (ids.contains(connection.getToNodeId())) {
                connections.add(connection);
            }
        }
        insertCopies(nodes, connections, 32);
    }

    /**
     * Copy the selected nodes (except the output node) and the connections between them to the clipboard, which is
     * shared by all editors.
     */
    public void copySelection() {
        final List<Node> nodes = getEditableSelection();
        if (nodes.isEmpty()) {
            DesktopUtils.beep();
            return;
        }
        final Set<Integer> ids = new HashSet<>();
        nodes.forEach(node -> ids.add(node.getId()));
        clipboardNodes = new ArrayList<>();
        nodes.forEach(node -> clipboardNodes.add(node.clone()));
        clipboardConnections = new ArrayList<>();
        for (Connection connection: graph.getConnections()) {
            if (ids.contains(connection.getFromNodeId()) && ids.contains(connection.getToNodeId())) {
                clipboardConnections.add(connection);
            }
        }
    }

    /**
     * Paste the nodes on the clipboard into the graph.
     */
    public void paste() {
        if (clipboardNodes == null) {
            DesktopUtils.beep();
            return;
        }
        insertCopies(clipboardNodes, clipboardConnections, 48);
    }

    private void insertCopies(List<Node> originals, List<Connection> connections, double offset) {
        final Map<Integer, Node> copies = new LinkedHashMap<>();
        for (Node original: originals) {
            final Node copy = original.clone();
            graph.add(copy);
            copy.setLocation(original.getX() + offset, original.getY() + offset);
            copies.put(original.getId(), copy);
        }
        for (Connection connection: connections) {
            final Node to = copies.get(connection.getToNodeId());
            final Node from = copies.containsKey(connection.getFromNodeId()) ? copies.get(connection.getFromNodeId()) : graph.getNode(connection.getFromNodeId());
            if ((to != null) && (from != null) && graph.canConnect(from, to, connection.getToInput())) {
                graph.connect(from, to, connection.getToInput());
            }
        }
        selection.clear();
        selection.addAll(copies.values());
        Node last = null;
        for (Node copy: copies.values()) {
            last = copy;
        }
        notifySelection(last);
        graphChanged(true);
    }

    private List<Node> getEditableSelection() {
        final List<Node> nodes = new ArrayList<>();
        for (Node node: selection) {
            if (! (node instanceof OutputNode)) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    private void notifySelection(Node primary) {
        if (primary != selectedNode) {
            selectedNode = primary;
            if (listener != null) {
                listener.selectionChanged(primary);
            }
        }
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        final Point2D point = toCanvas(event.getPoint());
        final Node node = getNodeAt(point);
        if (node != null) {
            final int input = getInputAt(node, point);
            if (input >= 0) {
                final Node source = graph.getInput(node, input);
                return node.getInputNames().get(input) + ((source != null) ? (" \u2190 " + source.getTitle()) : " (not connected)");
            }
        }
        return null;
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (fitPending) {
            fitView();
        }
        final Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            paintBackground(g2);
            g2.translate(offsetX, offsetY);
            g2.scale(zoom, zoom);
            for (Connection connection: graph.getConnections()) {
                final Node from = graph.getNode(connection.getFromNodeId()), to = graph.getNode(connection.getToNodeId());
                if ((from != null) && (to != null)) {
                    final boolean highlighted = selection.contains(from) || selection.contains(to);
                    paintWire(g2, getOutputPort(from), getInputPort(to, connection.getToInput()), getCategoryColour(from.getCategory()), highlighted);
                }
            }
            for (Node node: graph.getNodes()) {
                paintNode(g2, node);
            }
            if (marquee != null) {
                final Color accent = ThemeUtils.getColour("WorldPainter.accent", Color.ORANGE);
                g2.setColor(withAlpha(accent, 30));
                g2.fill(marquee);
                g2.setColor(accent);
                g2.setStroke(new BasicStroke((float) (1 / zoom)));
                g2.draw(marquee);
            }
            if (wireDrag != null) {
                final Point2D start = wireDrag.reverse ? getInputPort(wireDrag.node, wireDrag.input) : getOutputPort(wireDrag.node);
                final Color colour = getCategoryColour(wireDrag.node.getCategory());
                if (wireDrag.reverse) {
                    paintWire(g2, wireDrag.end, start, colour, true);
                } else {
                    paintWire(g2, start, wireDrag.end, colour, true);
                }
            }
        } finally {
            g2.dispose();
        }
        paintHint((Graphics2D) g);
    }

    private void paintBackground(Graphics2D g2) {
        g2.setColor(ThemeUtils.getCanvasBackground());
        g2.fillRect(0, 0, getWidth(), getHeight());
        final double spacing = GRID_SPACING * zoom;
        if (spacing >= 8) {
            g2.setColor(ThemeUtils.getColour("WorldPainter.canvasDot", Color.GRAY));
            final double startX = offsetX - Math.floor(offsetX / spacing) * spacing, startY = offsetY - Math.floor(offsetY / spacing) * spacing;
            final double dotSize = Math.max(1.5, 2.2 * Math.min(1, zoom));
            for (double y = startY; y < getHeight(); y += spacing) {
                for (double x = startX; x < getWidth(); x += spacing) {
                    g2.fill(new Ellipse2D.Double(x - dotSize / 2, y - dotSize / 2, dotSize, dotSize));
                }
            }
        }
    }

    private void paintHint(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(getFont().deriveFont(getFont().getSize2D() - 1));
        g2.setColor(ThemeUtils.getColour("WorldPainter.mutedForeground", Color.GRAY));
        g2.drawString("Right-click or double-click to add nodes \u00b7 drag from a port to connect \u00b7 Shift-drag to select several \u00b7 scroll to zoom, drag to pan", 12, getHeight() - 12);
    }

    private void paintWire(Graphics2D g2, Point2D from, Point2D to, Color colour, boolean highlighted) {
        final double dx = Math.max(48, Math.abs(to.getX() - from.getX()) / 2);
        final CubicCurve2D curve = new CubicCurve2D.Double(from.getX(), from.getY(), from.getX() + dx, from.getY(), to.getX() - dx, to.getY(), to.getX(), to.getY());
        g2.setStroke(new BasicStroke(highlighted ? 2.5f : 2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(highlighted ? colour : withAlpha(ThemeUtils.getColour("WorldPainter.node.wire", Color.GRAY), 220));
        g2.draw(curve);
    }

    private void paintNode(Graphics2D g2, Node node) {
        final Rectangle2D bounds = getBounds(node);
        final double x = bounds.getX(), y = bounds.getY(), width = bounds.getWidth(), height = bounds.getHeight();
        final Color background = ThemeUtils.getColour("WorldPainter.node.background", Color.WHITE);
        final Color border = ThemeUtils.getColour("WorldPainter.node.border", Color.LIGHT_GRAY);
        final Color accent = ThemeUtils.getColour("WorldPainter.accent", Color.ORANGE);
        final Color muted = ThemeUtils.getColour("WorldPainter.mutedForeground", Color.GRAY);
        final Color foreground = ThemeUtils.getColour("Label.foreground", Color.BLACK);
        final Color categoryColour = getCategoryColour(node.getCategory());
        final boolean selected = selection.contains(node);

        // Shadow and card
        g2.setColor(ThemeUtils.getColour("WorldPainter.node.shadow", new Color(0, 0, 0, 20)));
        g2.fill(new RoundRectangle2D.Double(x, y + 2, width, height, 14, 14));
        final RoundRectangle2D card = new RoundRectangle2D.Double(x, y, width, height, 14, 14);
        g2.setColor(background);
        g2.fill(card);
        g2.setStroke(new BasicStroke(selected ? 2f : 1f));
        g2.setColor(selected ? accent : border);
        g2.draw(card);

        // Header: category swatch and title
        g2.setColor(categoryColour);
        g2.fill(new RoundRectangle2D.Double(x + 12, y + (HEADER - 10) / 2.0, 10, 10, 4, 4));
        final Font baseFont = getFont();
        g2.setFont(baseFont.deriveFont(Font.BOLD, 12.5f));
        g2.setColor(foreground);
        drawClipped(g2, node.getTitle(), x + 30, y + HEADER / 2.0, width - 42);
        g2.setColor(border);
        g2.fill(new Rectangle2D.Double(x + 1, y + HEADER, width - 2, 1));

        // Ports
        g2.setFont(baseFont.deriveFont(11.5f));
        final List<String> inputNames = node.getInputNames();
        for (int i = 0; i < inputNames.size(); i++) {
            final Point2D port = getInputPort(node, i);
            final Node source = graph.getInput(node, i);
            final boolean hot = (hoverNode == node) && (hoverInput == i);
            paintPort(g2, port, (source != null) ? getCategoryColour(source.getCategory()) : null, hot ? accent : muted, background);
            g2.setColor(muted);
            final String hint = (source == null) ? node.getInputHint(i) : null;
            drawClipped(g2, (hint != null) ? (inputNames.get(i) + " \u00b7 " + hint) : inputNames.get(i), x + 14, port.getY(), node.hasOutput() ? (width / 2 + 8) : (width - 28));
        }
        if (node.hasOutput()) {
            final Point2D port = getOutputPort(node);
            final boolean hot = (hoverNode == node) && hoverOutput;
            paintPort(g2, port, categoryColour, hot ? accent : categoryColour, background);
            g2.setColor(muted);
            final String label = "Out";
            final FontMetrics metrics = g2.getFontMetrics();
            g2.drawString(label, (float) (x + width - 14 - metrics.stringWidth(label)), (float) (port.getY() + metrics.getAscent() / 2.0 - 1));
        }

        // Thumbnail
        final Rectangle2D thumbnail = getThumbnailBounds(node);
        final Shape thumbnailShape = new RoundRectangle2D.Double(thumbnail.getX(), thumbnail.getY(), thumbnail.getWidth(), thumbnail.getHeight(), 8, 8);
        final BufferedImage image = thumbnails.get(node.getId());
        if (image != null) {
            final Shape oldClip = g2.getClip();
            g2.clip(thumbnailShape);
            g2.drawImage(image, (int) thumbnail.getX(), (int) thumbnail.getY(), (int) thumbnail.getWidth(), (int) thumbnail.getHeight(), null);
            g2.setClip(oldClip);
        } else {
            g2.setColor(ThemeUtils.getCanvasBackground());
            g2.fill(thumbnailShape);
            if ((node instanceof OutputNode) && (graph.getInput(node, 0) == null)) {
                g2.setColor(muted);
                g2.setFont(baseFont.deriveFont(11f));
                drawCentred(g2, "Connect a node", thumbnail);
            }
        }
        g2.setColor(border);
        g2.setStroke(new BasicStroke(1f));
        g2.draw(thumbnailShape);
    }

    private static void paintPort(Graphics2D g2, Point2D centre, Color fill, Color outline, Color background) {
        final Ellipse2D circle = new Ellipse2D.Double(centre.getX() - PORT_RADIUS, centre.getY() - PORT_RADIUS, PORT_RADIUS * 2, PORT_RADIUS * 2);
        g2.setColor((fill != null) ? fill : background);
        g2.fill(circle);
        g2.setStroke(new BasicStroke(1.75f));
        g2.setColor(outline);
        g2.draw(circle);
    }

    private static void drawClipped(Graphics2D g2, String text, double x, double centreY, double maxWidth) {
        final FontMetrics metrics = g2.getFontMetrics();
        String shown = text;
        if (metrics.stringWidth(shown) > maxWidth) {
            while ((shown.length() > 1) && (metrics.stringWidth(shown + "\u2026") > maxWidth)) {
                shown = shown.substring(0, shown.length() - 1);
            }
            shown += "\u2026";
        }
        g2.drawString(shown, (float) x, (float) (centreY + metrics.getAscent() / 2.0 - 1));
    }

    private static void drawCentred(Graphics2D g2, String text, Rectangle2D area) {
        final FontMetrics metrics = g2.getFontMetrics();
        g2.drawString(text, (float) (area.getCenterX() - metrics.stringWidth(text) / 2.0), (float) (area.getCenterY() + metrics.getAscent() / 2.0 - 1));
    }

    // Geometry, in canvas coordinates

    static Rectangle2D getBounds(Node node) {
        final int rows = getRowCount(node);
        return new Rectangle2D.Double(node.getX(), node.getY(), NODE_WIDTH, HEADER + rows * ROW + PADDING + THUMBNAIL_SIZE + PADDING + 2);
    }

    private static int getRowCount(Node node) {
        return Math.max(node.getInputNames().size(), node.hasOutput() ? 1 : 0);
    }

    private static Point2D getInputPort(Node node, int index) {
        return new Point2D.Double(node.getX(), node.getY() + HEADER + PADDING / 2.0 + index * ROW + ROW / 2.0);
    }

    private static Point2D getOutputPort(Node node) {
        return new Point2D.Double(node.getX() + NODE_WIDTH, node.getY() + HEADER + PADDING / 2.0 + ROW / 2.0);
    }

    private static Rectangle2D getThumbnailBounds(Node node) {
        return new Rectangle2D.Double(node.getX() + (NODE_WIDTH - THUMBNAIL_SIZE) / 2.0, node.getY() + HEADER + getRowCount(node) * ROW + PADDING, THUMBNAIL_SIZE, THUMBNAIL_SIZE);
    }

    private Point2D toCanvas(Point point) {
        return new Point2D.Double((point.x - offsetX) / zoom, (point.y - offsetY) / zoom);
    }

    private Node getNodeAt(Point2D point) {
        final List<Node> nodes = graph.getNodes();
        // Topmost first; include the port areas which stick out of the node
        for (int i = nodes.size() - 1; i >= 0; i--) {
            final Node node = nodes.get(i);
            final Rectangle2D bounds = getBounds(node);
            final double margin = PORT_HIT_RADIUS / zoom;
            if (new Rectangle2D.Double(bounds.getX() - margin, bounds.getY(), bounds.getWidth() + 2 * margin, bounds.getHeight()).contains(point)) {
                return node;
            }
        }
        return null;
    }

    private int getInputAt(Node node, Point2D point) {
        final double radius = PORT_HIT_RADIUS / zoom;
        for (int i = 0; i < node.getInputNames().size(); i++) {
            final Point2D port = getInputPort(node, i);
            // Generous hit area: the port and its label
            if ((Math.abs(point.getY() - port.getY()) <= Math.max(radius, ROW / 2.0)) && (point.getX() >= port.getX() - radius) && (point.getX() <= port.getX() + NODE_WIDTH / 3.0)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isOverOutput(Node node, Point2D point) {
        if (! node.hasOutput()) {
            return false;
        }
        final Point2D port = getOutputPort(node);
        final double radius = PORT_HIT_RADIUS / zoom;
        return (Math.abs(point.getY() - port.getY()) <= Math.max(radius, ROW / 2.0)) && (point.getX() <= port.getX() + radius) && (point.getX() >= port.getX() - NODE_WIDTH / 3.0);
    }

    // Behaviour

    private void graphChanged(boolean structural) {
        repaint();
        if (listener != null) {
            listener.graphChanged(structural);
        }
    }

    private void zoomAt(Point point, double factor) {
        final double newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * factor));
        offsetX = point.x - (point.x - offsetX) * newZoom / zoom;
        offsetY = point.y - (point.y - offsetY) * newZoom / zoom;
        zoom = newZoom;
        repaint();
    }

    private void installKeyBindings() {
        final InputMap inputMap = getInputMap(WHEN_FOCUSED);
        final ActionMap actionMap = getActionMap();
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteNode");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "deleteNode");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), "duplicateNode");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, 0), "fitView");
        final int menuShortcut = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, menuShortcut), "selectAll");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, menuShortcut), "copy");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, menuShortcut), "paste");
        actionMap.put("deleteNode", action(e -> deleteSelection()));
        actionMap.put("duplicateNode", action(e -> duplicateSelection()));
        actionMap.put("fitView", action(e -> fitView()));
        actionMap.put("selectAll", action(e -> selectAll()));
        actionMap.put("copy", action(e -> copySelection()));
        actionMap.put("paste", action(e -> paste()));
    }

    /**
     * Show a menu of node types at a location. The chosen node is added there and passed to {@code onAdded}.
     *
     * @param categories The categories of nodes to offer.
     */
    private void showAddMenu(Point screenLocation, Point2D canvasLocation, List<NodeCategory> categories, Consumer<Node> onAdded) {
        final JPopupMenu menu = new JPopupMenu();
        final JMenuItem title = new JMenuItem("Add node");
        title.setEnabled(false);
        menu.add(title);
        for (NodeCategory category: categories) {
            final JMenu submenu = new JMenu(category.toString());
            submenu.setIcon(new SwatchIcon(getCategoryColour(category)));
            for (NodeType type: NodeType.getAll()) {
                if (type.getCategory() == category) {
                    final JMenuItem item = new JMenuItem(type.getName());
                    item.setToolTipText(type.getDescription());
                    item.addActionListener(e -> {
                        final Node node = type.create();
                        node.setLocation(canvasLocation.getX(), canvasLocation.getY() - HEADER);
                        graph.add(node);
                        if (onAdded != null) {
                            onAdded.accept(node);
                        }
                        setSelectedNode(node);
                        graphChanged(true);
                    });
                    submenu.add(item);
                }
            }
            menu.add(submenu);
        }
        menu.show(this, screenLocation.x, screenLocation.y);
    }

    private void showNodeMenu(Node node, Point screenLocation) {
        final JPopupMenu menu = new JPopupMenu();
        final boolean output = node instanceof OutputNode;
        final JMenuItem duplicateItem = new JMenuItem("Duplicate", Icons.get("copy"));
        duplicateItem.setEnabled(! output);
        duplicateItem.addActionListener(e -> duplicateSelection());
        menu.add(duplicateItem);
        final JMenuItem copyItem = new JMenuItem("Copy");
        copyItem.setEnabled(! output);
        copyItem.addActionListener(e -> copySelection());
        menu.add(copyItem);
        final JMenuItem disconnectItem = new JMenuItem("Disconnect inputs");
        disconnectItem.setEnabled(! node.getInputNames().isEmpty());
        disconnectItem.addActionListener(e -> {
            for (int i = 0; i < node.getInputNames().size(); i++) {
                graph.disconnect(node, i);
            }
            graphChanged(true);
        });
        menu.add(disconnectItem);
        menu.addSeparator();
        final JMenuItem deleteItem = new JMenuItem("Delete", Icons.get("trash-2"));
        deleteItem.setEnabled(! output);
        deleteItem.addActionListener(e -> deleteSelection());
        menu.add(deleteItem);
        menu.show(this, screenLocation.x, screenLocation.y);
    }

    static Color getCategoryColour(NodeCategory category) {
        switch (category) {
            case GENERATOR:
                return ThemeUtils.getColour("WorldPainter.node.generator", new Color(0x0ea5e9));
            case COMBINER:
                return ThemeUtils.getColour("WorldPainter.node.combiner", new Color(0x8b5cf6));
            case FILTER:
                return ThemeUtils.getColour("WorldPainter.node.filter", new Color(0x14b8a6));
            case MASK:
                return ThemeUtils.getColour("WorldPainter.node.mask", new Color(0xec4899));
            case SURFACE:
                return ThemeUtils.getColour("WorldPainter.node.surface", new Color(0x22c55e));
            case OUTPUT:
                return ThemeUtils.getColour("WorldPainter.node.output", new Color(0xf97316));
            default:
                throw new InternalError();
        }
    }

    private static Color withAlpha(Color colour, int alpha) {
        return new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), alpha);
    }

    private static Action action(Consumer<ActionEvent> handler) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handler.accept(e);
            }

            private static final long serialVersionUID = 1L;
        };
    }

    private NodeGraph graph;
    private Node selectedNode, hoverNode;
    private int hoverInput = -1;
    private boolean hoverOutput, fitPending = true;
    private double zoom = 1.0, offsetX, offsetY;
    private Listener listener;
    private WireDrag wireDrag;
    private Rectangle2D marquee;
    private final Set<Node> selection = new LinkedHashSet<>();
    private final Map<Integer, BufferedImage> thumbnails = new HashMap<>();

    private static List<Node> clipboardNodes;
    private static List<Connection> clipboardConnections;

    static final int NODE_WIDTH = 176, HEADER = 32, ROW = 22, PADDING = 8, THUMBNAIL_SIZE = 120;
    private static final double PORT_RADIUS = 5, PORT_HIT_RADIUS = 10, GRID_SPACING = 24, MIN_ZOOM = 0.2, MAX_ZOOM = 2.0;
    private static final List<NodeCategory> ALL_CATEGORIES = List.of(NodeCategory.GENERATOR, NodeCategory.COMBINER, NodeCategory.FILTER, NodeCategory.MASK, NodeCategory.SURFACE);
    private static final List<NodeCategory> CONSUMER_CATEGORIES = List.of(NodeCategory.COMBINER, NodeCategory.FILTER, NodeCategory.MASK, NodeCategory.SURFACE);
    private static final long serialVersionUID = 1L;

    /**
     * Notified of changes made by the user.
     */
    public interface Listener {
        void selectionChanged(Node node);

        /**
         * @param structural {@code true} if the result of the graph may have changed (nodes or connections were added or
         *                   removed), {@code false} if only the layout changed.
         */
        void graphChanged(boolean structural);
    }

    /**
     * A wire being dragged, from the output of {@link #node} or (if {@link #reverse}) from input {@link #input} of
     * {@link #node}.
     */
    private static final class WireDrag {
        WireDrag(Node node, int input, boolean reverse, Point2D end) {
            this.node = node;
            this.input = input;
            this.reverse = reverse;
            this.end = end;
        }

        final Node node;
        final int input;
        final boolean reverse;
        Point2D end;
    }

    /**
     * A small rounded square in a colour, for menus.
     */
    private static final class SwatchIcon implements Icon {
        SwatchIcon(Color colour) {
            this.colour = colour;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            final Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(colour);
                g2.fill(new RoundRectangle2D.Double(x + 2, y + 2, getIconWidth() - 4, getIconHeight() - 4, 4, 4));
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return 14;
        }

        @Override
        public int getIconHeight() {
            return 14;
        }

        private final Color colour;
    }

    private final class MouseHandler extends MouseAdapter {
        @Override
        public void mousePressed(MouseEvent e) {
            requestFocusInWindow();
            pressPoint = e.getPoint();
            lastPoint = e.getPoint();
            moved = false;
            if (e.isPopupTrigger()) {
                showContextMenu(e);
                return;
            }
            if (! SwingUtilities.isLeftMouseButton(e)) {
                // Middle (or other) button: pan
                panning = true;
                return;
            }
            final Point2D point = toCanvas(e.getPoint());
            final Node node = getNodeAt(point);
            if (node == null) {
                if (e.isShiftDown()) {
                    marqueeStart = point;
                    marquee = new Rectangle2D.Double(point.getX(), point.getY(), 0, 0);
                } else {
                    panning = true;
                }
                return;
            }
            if (isOverOutput(node, point)) {
                wireDrag = new WireDrag(node, -1, false, point);
                return;
            }
            final int input = getInputAt(node, point);
            if (input >= 0) {
                final Node source = graph.getInput(node, input);
                if (source != null) {
                    // Pick up the wire to move or remove it
                    graph.disconnect(node, input);
                    wireDrag = new WireDrag(source, -1, false, point);
                    graphChanged(true);
                } else {
                    wireDrag = new WireDrag(node, input, true, point);
                }
                return;
            }
            if (e.isShiftDown() || ((e.getModifiersEx() & Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()) != 0)) {
                // Add to or remove from the selection
                if (selection.remove(node)) {
                    Node last = null;
                    for (Node selected: selection) {
                        last = selected;
                    }
                    notifySelection(last);
                } else {
                    selection.add(node);
                    notifySelection(node);
                }
                repaint();
                return;
            }
            if (selection.contains(node)) {
                notifySelection(node);
            } else {
                setSelectedNode(node);
            }
            graph.bringToFront(node);
            draggedNode = node;
            dragStart = point;
            dragOrigins.clear();
            for (Node selected: selection) {
                dragOrigins.put(selected, new Point2D.Double(selected.getX(), selected.getY()));
            }
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if ((! moved) && (pressPoint != null) && (pressPoint.distance(e.getPoint()) > 3)) {
                moved = true;
            }
            final Point2D point = toCanvas(e.getPoint());
            if (wireDrag != null) {
                wireDrag.end = point;
                updateHover(point);
                repaint();
            } else if (draggedNode != null) {
                final double dx = point.getX() - dragStart.getX(), dy = point.getY() - dragStart.getY();
                dragOrigins.forEach((node, origin) -> node.setLocation(origin.getX() + dx, origin.getY() + dy));
                repaint();
            } else if (marquee != null) {
                marquee = new Rectangle2D.Double(Math.min(marqueeStart.getX(), point.getX()), Math.min(marqueeStart.getY(), point.getY()),
                        Math.abs(point.getX() - marqueeStart.getX()), Math.abs(point.getY() - marqueeStart.getY()));
                repaint();
            } else if (panning) {
                offsetX += e.getX() - lastPoint.x;
                offsetY += e.getY() - lastPoint.y;
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                repaint();
            }
            lastPoint = e.getPoint();
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            try {
                if (e.isPopupTrigger()) {
                    if (! moved) {
                        showContextMenu(e);
                    }
                    return;
                }
                if (wireDrag != null) {
                    finishWire(e);
                } else if (draggedNode != null) {
                    if (moved) {
                        graphChanged(false);
                    }
                } else if (marquee != null) {
                    Node last = null;
                    for (Node node: graph.getNodes()) {
                        if (marquee.intersects(getBounds(node))) {
                            selection.add(node);
                            last = node;
                        }
                    }
                    if (last != null) {
                        notifySelection(last);
                    }
                } else if (panning && (! moved) && SwingUtilities.isLeftMouseButton(e)) {
                    setSelectedNode(null);
                }
            } finally {
                wireDrag = null;
                draggedNode = null;
                marquee = null;
                panning = false;
                setCursor(Cursor.getDefaultCursor());
                repaint();
            }
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            if (SwingUtilities.isLeftMouseButton(e) && (e.getClickCount() == 2)) {
                final Point2D point = toCanvas(e.getPoint());
                if (getNodeAt(point) == null) {
                    showAddMenu(e.getPoint(), point, ALL_CATEGORIES, null);
                }
            }
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            final Point2D point = toCanvas(e.getPoint());
            updateHover(point);
            final Node node = getNodeAt(point);
            if ((node != null) && (hoverOutput || (hoverInput >= 0))) {
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            } else {
                setCursor(Cursor.getDefaultCursor());
            }
        }

        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
            zoomAt(e.getPoint(), Math.pow(1.1, -e.getPreciseWheelRotation()));
        }

        private void updateHover(Point2D point) {
            final Node node = getNodeAt(point);
            final Node oldNode = hoverNode;
            final int oldInput = hoverInput;
            final boolean oldOutput = hoverOutput;
            hoverNode = node;
            hoverInput = (node != null) ? getInputAt(node, point) : -1;
            hoverOutput = (node != null) && isOverOutput(node, point);
            if ((oldNode != hoverNode) || (oldInput != hoverInput) || (oldOutput != hoverOutput)) {
                repaint();
            }
        }

        private void finishWire(MouseEvent e) {
            final Point2D point = toCanvas(e.getPoint());
            final Node target = getNodeAt(point);
            final WireDrag drag = wireDrag;
            if (drag.reverse) {
                if ((target != null) && graph.canConnect(target, drag.node, drag.input)) {
                    graph.connect(target, drag.node, drag.input);
                    graphChanged(true);
                } else if (target == null) {
                    showAddMenu(e.getPoint(), new Point2D.Double(point.getX() - NODE_WIDTH, point.getY()), ALL_CATEGORIES, node -> {
                        if (graph.canConnect(node, drag.node, drag.input)) {
                            graph.connect(node, drag.node, drag.input);
                        }
                    });
                }
            } else {
                if (target != null) {
                    int input = getInputAt(target, point);
                    if (input < 0) {
                        // Dropped on the node itself: use the first free input
                        for (int i = 0; i < target.getInputNames().size(); i++) {
                            if (graph.getInput(target, i) == null) {
                                input = i;
                                break;
                            }
                        }
                    }
                    if ((input >= 0) && graph.canConnect(drag.node, target, input)) {
                        graph.connect(drag.node, target, input);
                        graphChanged(true);
                    } else if (target != drag.node) {
                        DesktopUtils.beep();
                    }
                } else if (moved) {
                    showAddMenu(e.getPoint(), point, CONSUMER_CATEGORIES, node -> {
                        if ((! node.getInputNames().isEmpty()) && graph.canConnect(drag.node, node, 0)) {
                            graph.connect(drag.node, node, 0);
                        }
                    });
                }
            }
        }

        private void showContextMenu(MouseEvent e) {
            final Point2D point = toCanvas(e.getPoint());
            final Node node = getNodeAt(point);
            if (node != null) {
                if (! selection.contains(node)) {
                    setSelectedNode(node);
                }
                showNodeMenu(node, e.getPoint());
            } else {
                showAddMenu(e.getPoint(), point, ALL_CATEGORIES, null);
            }
        }

        private Point pressPoint, lastPoint;
        private boolean moved, panning;
        private Node draggedNode;
        private Point2D dragStart, marqueeStart;
        private final Map<Node, Point2D> dragOrigins = new HashMap<>();
    }
}
