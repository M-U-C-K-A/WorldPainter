package org.pepsoft.worldpainter.operations;

import org.pepsoft.worldpainter.App;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.WorldPainter;
import org.pepsoft.worldpainter.paths.PathDialog;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyVetoException;

import static org.pepsoft.util.GUIUtils.getUIScale;

/**
 * Lays out a path from one point to another: click the start, then the end, and choose the options for the path
 * (bends, bridges, tunnels, materials) in a dialog with a preview.
 */
public class PathTool extends MouseOrTabletOperation {
    public PathTool(WorldPainter view) {
        super("Path", "Lay out a road or track from one point to another, following the land", view, "operation.path");
        optionsPanel = new JPanel(new BorderLayout(0, 4));
        final JLabel title = new JLabel("Path");
        title.setFont(title.getFont().deriveFont(Font.BOLD, (int) (title.getFont().getSize() * 1.1)));
        optionsPanel.add(title, BorderLayout.NORTH);
        hintLabel = new JLabel();
        hintLabel.setVerticalAlignment(SwingConstants.TOP);
        optionsPanel.add(hintLabel, BorderLayout.CENTER);
        setHint(HINT_START);
    }

    @Override
    public JPanel getOptionsPanel() {
        return optionsPanel;
    }

    @Override
    protected void activate() throws PropertyVetoException {
        super.activate();
        start = null;
        setHint(HINT_START);
    }

    @Override
    protected void tick(int centreX, int centreY, boolean inverse, boolean first, float dynamicLevel) {
        if (! first) {
            return;
        }
        final Dimension dimension = getDimension();
        if (dimension == null) {
            return;
        }
        if (inverse || (start == null)) {
            start = new Point(centreX, centreY);
            setHint("<p>Start: <b>" + centreX + ", " + centreY + "</b>.</p><p>Now click the end of the path.</p><p>Right-click to start again.</p>");
            return;
        }
        final Point end = new Point(centreX, centreY);
        final Point from = start;
        start = null;
        setHint(HINT_START);
        // Open the dialog once the mouse event has been handled
        SwingUtilities.invokeLater(() -> {
            final App app = App.getInstance();
            final PathDialog dialog = new PathDialog(app, dimension, getView(), app.getColourScheme(), from, end, app.nextGeneratedLayerNumber());
            dialog.setVisible(true);
            if ((! dialog.isCancelled()) && (! dialog.getCreatedLayers().isEmpty())) {
                app.registerGeneratedLayers(dialog.getCreatedLayers());
            }
        });
    }

    private void setHint(String html) {
        // A fixed width, so that the text wraps instead of widening the panel
        hintLabel.setText("<html><div style=\"width: " + Math.round(170 * getUIScale()) + "px\">" + html + "</div></html>");
    }

    private final JPanel optionsPanel;
    private final JLabel hintLabel;
    private Point start;

    private static final String HINT_START = "<p>Click the start of the path, then its end.</p><p>You then choose how much the path may bend to follow the land, whether it crosses valleys on bridges or goes through hills in tunnels, and its materials.</p>";
}
