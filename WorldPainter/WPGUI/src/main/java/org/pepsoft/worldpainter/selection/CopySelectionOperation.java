package org.pepsoft.worldpainter.selection;

import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.WorldPainter;
import org.pepsoft.worldpainter.WorldPainterView;
import org.pepsoft.worldpainter.brushes.BrushShape;
import org.pepsoft.worldpainter.operations.MouseOrTabletOperation;
import org.pepsoft.worldpainter.operations.StandardOptionsPanel;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyVetoException;

/**
 * Created by Pepijn Schmitz on 03-11-16.
 */
public class CopySelectionOperation extends MouseOrTabletOperation {
    public CopySelectionOperation(WorldPainterView view) {
        super("Copy Selection", "Copy the selection to another location", view, "operation.selection.copy", "copy_selection");
    }

    @Override
    public JPanel getOptionsPanel() {
        return optionsPanel;
    }

    @Override
    protected void activate() throws PropertyVetoException {
        final Dimension dimension = getDimension();
        if (dimension == null) {
            throw new PropertyVetoException("Dimension not set", null);
        }
        super.activate();
        selectionHelper = new SelectionHelper(dimension);
        WorldPainter view = (WorldPainter) getView();
        Rectangle bounds = selectionHelper.getSelectionBounds();
        if (bounds == null) {
            super.deactivate();
            throw new PropertyVetoException("No active selection", null);
        }
        selectionWidth = bounds.width;
        selectionHeight = bounds.height;
        updateOutline();
        view.setBrushShape(BrushShape.CUSTOM);
        view.setDrawBrush(true);
    }

    @Override
    protected void deactivate() {
        getView().setDrawBrush(false);
        super.deactivate();
    }

    @Override
    protected void tick(int centreX, int centreY, boolean inverse, boolean first, float dynamicLevel) {
        selectionHelper.setOptions(options);
        final Dimension dimension = getDimension();
        if (dimension == null) {
            // Probably some kind of race condition
            return;
        }
        dimension.setEventsInhibited(true);
        try {
            // TODO: opening the progress dialog (and presumeably any dialog)
            // causes the JIDE docking framework to malfunction
//            ProgressDialog.executeTask(SwingUtilities.getWindowAncestor(getView()), new ProgressTask<Void>() {
//                @Override
//                public String getName() {
//                    return "Copying selection";
//                }
//
//                @Override
//                public Void execute(ProgressReceiver progressReceiver) throws ProgressReceiver.OperationCancelled {
                    selectionHelper.copySelection(centreX, centreY, null);
//                    return null;
//                }
//            }, false);
        } catch (ProgressReceiver.OperationCancelled e) {
            throw new InternalError(e);
        } finally {
            dimension.setEventsInhibited(false);
        }
    }

    /**
     * Show where the copy will go: the selection's bounds, turned as chosen, with the mouse at their top left corner.
     */
    private void updateOutline() {
        if ((selectionWidth == 0) || (getView() == null)) {
            return;
        }
        final java.awt.Dimension size = SelectionHelper.transformedSize(selectionWidth, selectionHeight, options.getRotation());
        ((WorldPainter) getView()).setCustomBrushShape(new Rectangle(0, 0, size.width, size.height));
    }

    private void turn(int quarters) {
        options.setRotation(options.getRotation() + quarters);
        updateTurnLabel();
        updateOutline();
    }

    private void updateTurnLabel() {
        if (turnLabel != null) {
            turnLabel.setText((options.getRotation() * 90) + "°" + (options.isMirror() ? ", mirrored" : ""));
        }
    }

    private int selectionWidth, selectionHeight;
    private JLabel turnLabel;
    private SelectionHelper selectionHelper;
    private final CopySelectionOperationOptions options = new CopySelectionOperationOptions();
    private final StandardOptionsPanel optionsPanel = new StandardOptionsPanel("Copy Selection", "<p>Click to copy the selected area to the indicated location; it can be turned and mirrored below. Choose which aspects of the world to copy:") {
        @Override
        protected void addAdditionalComponents(GridBagConstraints constraints) {
            add(new CopySelectionOperationOptionsPanel(options), constraints);
            // Turning and mirroring the copy
            final JPanel turnPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 0));
            turnPanel.add(new JLabel("Turn:"));
            final JButton left = new JButton("\u21BA"), right = new JButton("\u21BB");
            left.setToolTipText("<html><b>Turn left</b><br>Turn the copy a quarter turn anticlockwise</html>");
            right.setToolTipText("<html><b>Turn right</b><br>Turn the copy a quarter turn clockwise</html>");
            left.addActionListener(e -> turn(-1));
            right.addActionListener(e -> turn(1));
            final JToggleButton mirror = new JToggleButton("Mirror");
            mirror.setToolTipText("<html><b>Mirror</b><br>Copy the selection mirrored from left to right</html>");
            mirror.addActionListener(e -> {
                options.setMirror(mirror.isSelected());
                updateTurnLabel();
            });
            turnLabel = new JLabel();
            for (JComponent component: new JComponent[] {left, right, mirror, turnLabel}) {
                turnPanel.add(component);
            }
            updateTurnLabel();
            add(turnPanel, constraints);
        }
    };
}