package org.pepsoft.worldpainter.operations;

import org.pepsoft.util.DesktopUtils;
import org.pepsoft.worldpainter.App;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.WorldPainter;
import org.pepsoft.worldpainter.paths.PathDialog;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.beans.PropertyVetoException;
import java.util.ArrayList;
import java.util.List;

import static org.pepsoft.util.GUIUtils.getUIScale;

/**
 * Lays out a path through a series of points: left-click each point in turn (right-click or Backspace removes the last
 * one, Escape starts again), then press Enter to choose the options for the path (bends, bridges, tunnels, materials)
 * in a dialog with a preview.
 */
public class PathTool extends MouseOrTabletOperation {
    public PathTool(WorldPainter view) {
        super("Path", "Lay out a road or track through points you click, following the land", view, "operation.path");
        this.mapView = view;
        optionsPanel = new JPanel(new BorderLayout(0, 4));
        final JLabel title = new JLabel("Path");
        title.setFont(title.getFont().deriveFont(Font.BOLD, (int) (title.getFont().getSize() * 1.1)));
        optionsPanel.add(title, BorderLayout.NORTH);
        hintLabel = new JLabel();
        hintLabel.setVerticalAlignment(SwingConstants.TOP);
        optionsPanel.add(hintLabel, BorderLayout.CENTER);
        updateHint();
    }

    @Override
    public JPanel getOptionsPanel() {
        return optionsPanel;
    }

    @Override
    protected void activate() throws PropertyVetoException {
        super.activate();
        waypoints.clear();
        updateHint();
        mapView.setToolOverlay(this::paintWaypoints);
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyDispatcher);
    }

    @Override
    protected void deactivate() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyDispatcher);
        mapView.setToolOverlay(null);
        waypoints.clear();
        super.deactivate();
    }

    @Override
    protected void tick(int centreX, int centreY, boolean inverse, boolean first, float dynamicLevel) {
        if ((! first) || (getDimension() == null)) {
            return;
        }
        if (inverse) {
            removeLastWaypoint();
        } else {
            waypoints.add(new Point(centreX, centreY));
            changed();
        }
    }

    /**
     * Keys while the tool is active and the map window has the focus (not while typing in a text field): Enter to lay
     * out the path, Backspace to remove the last point, Escape to start again.
     */
    private boolean dispatchKey(KeyEvent event) {
        final KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        if ((event.getID() != KeyEvent.KEY_PRESSED) || (SwingUtilities.getWindowAncestor(mapView) != focusManager.getFocusedWindow())
                || (focusManager.getFocusOwner() instanceof JTextComponent)) {
            return false;
        }
        switch (event.getKeyCode()) {
            case KeyEvent.VK_ENTER:
                // Open the dialog after this key event has been handled
                SwingUtilities.invokeLater(this::finish);
                return true;
            case KeyEvent.VK_BACK_SPACE:
            case KeyEvent.VK_DELETE:
                if (waypoints.isEmpty()) {
                    return false;
                }
                removeLastWaypoint();
                return true;
            case KeyEvent.VK_ESCAPE:
                if (waypoints.isEmpty()) {
                    return false;
                }
                waypoints.clear();
                changed();
                return true;
            default:
                return false;
        }
    }

    private void removeLastWaypoint() {
        if (! waypoints.isEmpty()) {
            waypoints.remove(waypoints.size() - 1);
            changed();
        }
    }

    /**
     * Open the dialog to lay out the path through the points clicked so far.
     */
    private void finish() {
        final Dimension dimension = getDimension();
        if ((dimension == null) || (waypoints.size() < 2)) {
            DesktopUtils.beep();
            return;
        }
        final List<Point> points = new ArrayList<>(waypoints);
        final App app = App.getInstance();
        final PathDialog dialog = new PathDialog(app, dimension, getView(), app.getColourScheme(), points, app.nextGeneratedLayerNumber());
        dialog.setVisible(true);
        if (! dialog.isCancelled()) {
            waypoints.clear();
            changed();
            if (! dialog.getCreatedLayers().isEmpty()) {
                app.registerGeneratedLayers(dialog.getCreatedLayers());
            }
        }
    }

    private void changed() {
        updateHint();
        mapView.repaint();
    }

    private void updateHint() {
        final String text;
        if (waypoints.isEmpty()) {
            text = "<p>Left-click the points the path should pass through, in order.</p><p>Then press <b>Enter</b> to choose how it follows the land, bridges and tunnels, and its materials.</p>";
        } else {
            text = "<p><b>" + waypoints.size() + "</b> point" + ((waypoints.size() != 1) ? "s" : "") + ".</p>"
                    + ((waypoints.size() < 2) ? "<p>Click the next point.</p>" : "<p>Click more points, or press <b>Enter</b> to lay out the path.</p>")
                    + "<p>Right-click or Backspace removes the last point, Escape starts again.</p>";
        }
        // A fixed width, so that the text wraps instead of widening the panel
        hintLabel.setText("<html><div style=\"width: " + Math.round(170 * getUIScale()) + "px\">" + text + "</div></html>");
    }

    /**
     * Draw the points clicked so far, joined by dashed lines, over the map.
     */
    private void paintWaypoints(Graphics2D g2, float scale) {
        if (waypoints.isEmpty()) {
            return;
        }
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        final float pixel = 1 / scale;
        g2.setColor(new Color(0xf97316));
        g2.setStroke(new BasicStroke(2 * pixel, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10, new float[] {6 * pixel, 4 * pixel}, 0));
        for (int i = 1; i < waypoints.size(); i++) {
            final Point p = waypoints.get(i - 1), q = waypoints.get(i);
            g2.draw(new Line2D.Double(p.x + 0.5, p.y + 0.5, q.x + 0.5, q.y + 0.5));
        }
        final double radius = 6 * pixel;
        g2.setStroke(new BasicStroke(2 * pixel));
        for (int i = 0; i < waypoints.size(); i++) {
            final Point p = waypoints.get(i);
            final Ellipse2D circle = new Ellipse2D.Double(p.x + 0.5 - radius, p.y + 0.5 - radius, radius * 2, radius * 2);
            g2.setColor((i == 0) ? new Color(0x16a34a) : Color.WHITE);
            g2.fill(circle);
            g2.setColor(new Color(0x18181b));
            g2.draw(circle);
        }
    }

    private final WorldPainter mapView;
    private final JPanel optionsPanel;
    private final JLabel hintLabel;
    private final List<Point> waypoints = new ArrayList<>();
    private final KeyEventDispatcher keyDispatcher = this::dispatchKey;
}
