package org.pepsoft.worldpainter.selection;

import javax.swing.*;
import java.awt.*;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * The choice, at the top of the options of the selection tools, of how to select: by dragging a rectangle or an
 * ellipse, or by painting with the brush, as in the original WorldPainter (the size and shape of the brush then set
 * what is selected). Choosing the brush switches to the brush selection tool, and choosing a rectangle or an ellipse
 * back to the drag selection tool.
 */
public final class SelectionShapes {
    private SelectionShapes() {
        // Prevent instantiation
    }

    public enum Shape {RECTANGLE, ELLIPSE, BRUSH}

    /**
     * Tell how to switch to the two selection tools, by selecting their buttons.
     */
    public static void setTools(AbstractButton dragTool, AbstractButton brushTool) {
        SelectionShapes.dragTool = dragTool;
        SelectionShapes.brushTool = brushTool;
    }

    public static Shape getShape() {
        return shape;
    }

    /**
     * Note which tool is in use, when it is chosen with its own button.
     */
    static void toolActivated(boolean brush) {
        if (brush) {
            setShape(Shape.BRUSH);
        } else if (shape == Shape.BRUSH) {
            setShape(Shape.RECTANGLE);
        }
    }

    /**
     * A row of three buttons to choose how to select.
     */
    static JComponent createChooser() {
        final JPanel panel = new JPanel(new GridLayout(1, 3, 0, 0));
        panel.putClientProperty("JComponent.sizeVariant", "small");
        final ButtonGroup group = new ButtonGroup();
        final JToggleButton[] buttons = new JToggleButton[Shape.values().length];
        for (Shape option: Shape.values()) {
            final JToggleButton button = new JToggleButton(LABELS[option.ordinal()]);
            button.setToolTipText(TIPS[option.ordinal()]);
            button.putClientProperty("JButton.buttonType", "segmented");
            button.setFocusable(false);
            button.addActionListener(e -> choose(option));
            group.add(button);
            panel.add(button);
            buttons[option.ordinal()] = button;
        }
        buttons[shape.ordinal()].setSelected(true);
        synchronized (CHOOSERS) {
            CHOOSERS.add(new WeakReference<>(buttons));
        }
        return panel;
    }

    private static void choose(Shape option) {
        setShape(option);
        final AbstractButton tool = (option == Shape.BRUSH) ? brushTool : dragTool;
        if ((tool != null) && (! tool.isSelected())) {
            tool.doClick();
        }
    }

    private static void setShape(Shape shape) {
        SelectionShapes.shape = shape;
        synchronized (CHOOSERS) {
            CHOOSERS.removeIf(reference -> reference.get() == null);
            for (WeakReference<JToggleButton[]> reference: CHOOSERS) {
                final JToggleButton[] buttons = reference.get();
                if (buttons != null) {
                    buttons[shape.ordinal()].setSelected(true);
                }
            }
        }
    }

    private static volatile Shape shape = Shape.RECTANGLE;
    private static AbstractButton dragTool, brushTool;
    private static final List<WeakReference<JToggleButton[]>> CHOOSERS = new ArrayList<>();
    private static final String[] LABELS = {"Rectangle", "Ellipse", "Brush"};
    private static final String[] TIPS = {
            "<html><b>Rectangle</b><br>Drag a rectangle; hold Shift for a square</html>",
            "<html><b>Ellipse</b><br>Drag an ellipse; hold Shift for a circle</html>",
            "<html><b>Brush</b><br>Paint the selection with the brush, as in the original WorldPainter:<br>its size and shape set what is selected</html>"};
}
