package org.pepsoft.worldpainter.selection;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.WorldPainter;
import org.pepsoft.worldpainter.operations.AbstractOperation;
import org.pepsoft.worldpainter.operations.StandardOptionsPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;

/**
 * Selects an area by dragging a rectangle across the map, as in an image editor: drag to add the rectangle to the
 * selection, or drag with the right button (or with Alt held) to remove it. Hold Shift for a square, and Ctrl (Cmd on a
 * Mac) for an ellipse.
 */
public class RectangleSelectionOperation extends AbstractOperation {
    public RectangleSelectionOperation(WorldPainter view) {
        super("Select Rectangle", "Drag to select a rectangle or an ellipse", "edit_selection");
        setView(view);
        mapView = view;
    }

    @Override
    public JPanel getOptionsPanel() {
        return OPTIONS_PANEL;
    }

    @Override
    protected void activate() {
        mapView.addMouseListener(mouseHandler);
        mapView.addMouseMotionListener(mouseHandler);
        mapView.setToolOverlay(this::paintOverlay);
    }

    @Override
    protected void deactivate() {
        mapView.removeMouseListener(mouseHandler);
        mapView.removeMouseMotionListener(mouseHandler);
        mapView.setToolOverlay(null);
        start = null;
    }

    @Override
    public void interrupt() {
        start = null;
        mapView.repaint();
    }

    /**
     * The shape being dragged, in world coordinates, or {@code null} if none.
     */
    private Shape getShape() {
        if ((start == null) || (end == null)) {
            return null;
        }
        int width = Math.abs(end.x - start.x) + 1, height = Math.abs(end.y - start.y) + 1;
        if (square) {
            width = height = Math.max(width, height);
        }
        final int x = (end.x >= start.x) ? start.x : (start.x - width + 1), y = (end.y >= start.y) ? start.y : (start.y - height + 1);
        return ellipse ? new Ellipse2D.Float(x, y, width, height) : new Rectangle(x, y, width, height);
    }

    private void apply(Shape shape) {
        final Dimension dimension = getDimension();
        if (dimension == null) {
            return;
        }
        dimension.setEventsInhibited(true);
        try {
            final SelectionHelper selectionHelper = new SelectionHelper(dimension);
            if (removing) {
                selectionHelper.removeFromSelection(shape);
            } else {
                selectionHelper.addToSelection(shape);
            }
            dimension.armSavePoint();
        } finally {
            dimension.setEventsInhibited(false);
        }
    }

    private void paintOverlay(Graphics2D g2, float scale) {
        final Shape shape = getShape();
        if (shape == null) {
            return;
        }
        final float pixel = 1 / scale;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        final Color colour = removing ? new Color(0xef4444) : new Color(0xf97316);
        g2.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), 40));
        g2.fill(shape);
        g2.setStroke(new BasicStroke(1.5f * pixel, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] {5 * pixel, 4 * pixel}, 0));
        g2.setColor(Color.WHITE);
        g2.draw(shape);
        g2.setStroke(new BasicStroke(1.5f * pixel, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] {5 * pixel, 4 * pixel}, 4.5f * pixel));
        g2.setColor(colour);
        g2.draw(shape);
        // The size, next to the corner
        final Rectangle2D bounds = shape.getBounds2D();
        g2.setFont(g2.getFont().deriveFont(12 * pixel));
        final String size = Math.round(bounds.getWidth()) + " × " + Math.round(bounds.getHeight());
        g2.setColor(new Color(0, 0, 0, 160));
        final FontMetrics metrics = g2.getFontMetrics();
        final float textX = (float) bounds.getMaxX() + 6 * pixel, textY = (float) bounds.getMaxY() + 14 * pixel;
        g2.fill(new Rectangle2D.Float(textX - 3 * pixel, textY - metrics.getAscent() - pixel, metrics.stringWidth(size) + 6 * pixel, metrics.getHeight() + 2 * pixel));
        g2.setColor(Color.WHITE);
        g2.drawString(size, textX, textY);
    }

    private final WorldPainter mapView;
    private final MouseAdapter mouseHandler = new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
            if ((e.getButton() != MouseEvent.BUTTON1) && (e.getButton() != MouseEvent.BUTTON3)) {
                return;
            }
            start = mapView.viewToWorld(e.getPoint());
            end = start;
            removing = (e.getButton() == MouseEvent.BUTTON3) || e.isAltDown();
            update(e);
            e.consume();
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (start != null) {
                end = mapView.viewToWorld(e.getPoint());
                update(e);
                e.consume();
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (start != null) {
                end = mapView.viewToWorld(e.getPoint());
                update(e);
                final Shape shape = getShape();
                start = null;
                if (shape != null) {
                    apply(shape);
                }
                mapView.repaint();
                e.consume();
            }
        }

        private void update(MouseEvent e) {
            square = e.isShiftDown();
            ellipse = e.isControlDown() || e.isMetaDown();
            mapView.repaint();
        }
    };
    private volatile Point start, end;
    private volatile boolean removing, square, ellipse;

    private static final JPanel OPTIONS_PANEL = new StandardOptionsPanel("Select Rectangle", "<ul><li>Drag to add a rectangle to the selection<li>Drag with the right mouse button (or with Alt held) to remove it<li>Hold Shift for a square, Ctrl (Cmd on a Mac) for an ellipse</ul>The other tools then only work inside the selection when <em>inside selection</em> is ticked in their options.");
}
