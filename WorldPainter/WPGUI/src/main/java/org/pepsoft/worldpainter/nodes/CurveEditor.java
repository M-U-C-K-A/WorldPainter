package org.pepsoft.worldpainter.nodes;

import org.pepsoft.worldpainter.util.ThemeUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * An editor for the control points of a {@link CurveNode curve}: drag a point to move it, double-click to add one and
 * right-click (or double-click) a point to remove it. The first and last points stay at the left and right edges.
 */
public class CurveEditor extends JComponent {
    public CurveEditor() {
        setPreferredSize(new Dimension(220, 180));
        setMinimumSize(new Dimension(120, 160));
        final MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                final int index = getPointAt(e.getPoint());
                if ((SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger()) && (index > 0) && (index < count() - 1)) {
                    removePoint(index);
                } else if (SwingUtilities.isLeftMouseButton(e)) {
                    if ((e.getClickCount() == 2) && (index < 0)) {
                        dragIndex = addPoint(toValueX(e.getX()), toValueY(e.getY()));
                    } else if ((e.getClickCount() == 2) && (index > 0) && (index < count() - 1)) {
                        removePoint(index);
                    } else {
                        dragIndex = index;
                    }
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragIndex >= 0) {
                    final int last = count() - 1;
                    float x = (float) toValueX(e.getX());
                    if (dragIndex == 0) {
                        x = 0;
                    } else if (dragIndex == last) {
                        x = 1;
                    } else {
                        // Keep the points in order
                        x = Math.max(points[(dragIndex - 1) * 2] + 0.01f, Math.min(points[(dragIndex + 1) * 2] - 0.01f, x));
                    }
                    points[dragIndex * 2] = x;
                    points[dragIndex * 2 + 1] = (float) Math.max(0, Math.min(1, toValueY(e.getY())));
                    fireChanged();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragIndex = -1;
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                setCursor(Cursor.getPredefinedCursor((getPointAt(e.getPoint()) >= 0) ? Cursor.HAND_CURSOR : Cursor.CROSSHAIR_CURSOR));
            }
        };
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
        setToolTipText("Drag the points to reshape the curve; double-click to add or remove a point");
    }

    /**
     * Get a copy of the points, as interleaved X and Y coordinates.
     */
    public float[] getPoints() {
        return points.clone();
    }

    public void setPoints(float[] points) {
        this.points = points.clone();
        repaint();
    }

    /**
     * Set the action to perform when the user changes the curve. It receives a copy of the new points.
     */
    public void setChangeListener(Consumer<float[]> changeListener) {
        this.changeListener = changeListener;
    }

    @Override
    protected void paintComponent(Graphics g) {
        final Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            final Rectangle2D area = getPlotArea();
            g2.setColor(ThemeUtils.getCanvasBackground());
            g2.fill(area);
            g2.setColor(ThemeUtils.getColour("WorldPainter.border", Color.LIGHT_GRAY));
            for (int i = 0; i <= 4; i++) {
                final double x = area.getX() + area.getWidth() * i / 4, y = area.getY() + area.getHeight() * i / 4;
                g2.draw(new java.awt.geom.Line2D.Double(x, area.getY(), x, area.getMaxY()));
                g2.draw(new java.awt.geom.Line2D.Double(area.getX(), y, area.getMaxX(), y));
            }
            // The curve, filled underneath like a terrain profile
            final Color accent = ThemeUtils.getColour("WorldPainter.accent", Color.ORANGE);
            final Path2D line = new Path2D.Double(), fill = new Path2D.Double();
            fill.moveTo(area.getX(), area.getMaxY());
            final int steps = (int) Math.max(32, area.getWidth());
            for (int i = 0; i <= steps; i++) {
                final double vx = (double) i / steps;
                final double vy = Math.max(-0.05, Math.min(1.05, CurveNode.evaluate(points, vx)));
                final double x = toScreenX(vx), y = toScreenY(vy);
                if (i == 0) {
                    line.moveTo(x, y);
                } else {
                    line.lineTo(x, y);
                }
                fill.lineTo(x, y);
            }
            fill.lineTo(area.getMaxX(), area.getMaxY());
            fill.closePath();
            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 40));
            g2.fill(fill);
            g2.setColor(accent);
            g2.setStroke(new BasicStroke(2f));
            g2.draw(line);
            final Color background = ThemeUtils.getColour("WorldPainter.node.background", Color.WHITE);
            for (int i = 0; i < count(); i++) {
                final Ellipse2D handle = new Ellipse2D.Double(toScreenX(points[i * 2]) - HANDLE_RADIUS, toScreenY(points[i * 2 + 1]) - HANDLE_RADIUS, HANDLE_RADIUS * 2, HANDLE_RADIUS * 2);
                g2.setColor(background);
                g2.fill(handle);
                g2.setColor(accent);
                g2.setStroke(new BasicStroke(1.75f));
                g2.draw(handle);
            }
            g2.setColor(ThemeUtils.getColour("WorldPainter.border", Color.LIGHT_GRAY));
            g2.setStroke(new BasicStroke(1f));
            g2.draw(area);
        } finally {
            g2.dispose();
        }
    }

    private Rectangle2D getPlotArea() {
        final Insets insets = getInsets();
        return new Rectangle2D.Double(insets.left + MARGIN, insets.top + MARGIN, getWidth() - insets.left - insets.right - 2 * MARGIN - 1, getHeight() - insets.top - insets.bottom - 2 * MARGIN - 1);
    }

    private double toScreenX(double x) {
        final Rectangle2D area = getPlotArea();
        return area.getX() + x * area.getWidth();
    }

    private double toScreenY(double y) {
        final Rectangle2D area = getPlotArea();
        return area.getMaxY() - y * area.getHeight();
    }

    private double toValueX(int x) {
        final Rectangle2D area = getPlotArea();
        return Math.max(0, Math.min(1, (x - area.getX()) / area.getWidth()));
    }

    private double toValueY(int y) {
        final Rectangle2D area = getPlotArea();
        return (area.getMaxY() - y) / area.getHeight();
    }

    private int count() {
        return points.length / 2;
    }

    private int getPointAt(Point point) {
        for (int i = 0; i < count(); i++) {
            if (point.distance(toScreenX(points[i * 2]), toScreenY(points[i * 2 + 1])) <= HANDLE_RADIUS + 4) {
                return i;
            }
        }
        return -1;
    }

    private int addPoint(double x, double y) {
        int index = 1;
        while ((index < count()) && (points[index * 2] < x)) {
            index++;
        }
        if ((index >= count()) || (x <= points[(index - 1) * 2]) || (x >= points[index * 2])) {
            return -1;
        }
        final float[] newPoints = new float[points.length + 2];
        System.arraycopy(points, 0, newPoints, 0, index * 2);
        newPoints[index * 2] = (float) x;
        newPoints[index * 2 + 1] = (float) Math.max(0, Math.min(1, y));
        System.arraycopy(points, index * 2, newPoints, index * 2 + 2, points.length - index * 2);
        points = newPoints;
        fireChanged();
        return index;
    }

    private void removePoint(int index) {
        final float[] newPoints = new float[points.length - 2];
        System.arraycopy(points, 0, newPoints, 0, index * 2);
        System.arraycopy(points, index * 2 + 2, newPoints, index * 2, points.length - index * 2 - 2);
        points = newPoints;
        fireChanged();
    }

    private void fireChanged() {
        repaint();
        if (changeListener != null) {
            changeListener.accept(Arrays.copyOf(points, points.length));
        }
    }

    private float[] points = {0, 0, 1, 1};
    private int dragIndex = -1;
    private Consumer<float[]> changeListener;

    private static final double HANDLE_RADIUS = 5, MARGIN = 8;
    private static final long serialVersionUID = 1L;
}
