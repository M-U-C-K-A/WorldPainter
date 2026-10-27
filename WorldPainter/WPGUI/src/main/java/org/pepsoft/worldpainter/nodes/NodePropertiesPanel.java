package org.pepsoft.worldpainter.nodes;

import com.formdev.flatlaf.FlatClientProperties;
import org.pepsoft.worldpainter.util.Icons;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.DoubleUnaryOperator;

import static org.pepsoft.util.swing.MessageUtils.showWarning;

/**
 * Shows editors for the {@link Parameter parameters} of the selected {@link Node}, built from the parameter
 * descriptions.
 */
public class NodePropertiesPanel extends JPanel implements Scrollable {
    /**
     * @param changeListener Notified on the event dispatch thread whenever the user changes a parameter of the node.
     */
    public NodePropertiesPanel(Consumer<Node> changeListener) {
        super(new GridBagLayout());
        this.changeListener = changeListener;
        setOpaque(false);
        setBorder(new EmptyBorder(12, 14, 12, 14));
        setNode(null);
    }

    public Node getNode() {
        return node;
    }

    /**
     * Show the parameters of a node, or a hint if {@code node} is {@code null}.
     */
    public void setNode(Node node) {
        this.node = node;
        removeAll();
        rows.clear();
        final GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;
        if (node == null) {
            add(wrappedLabel("Select a node to change its settings.<br><br>Each node shows a thumbnail of its result: black is low, white is high. The <b>Output</b> node turns the result into terrain heights."), c);
        } else {
            titleLabel = new JLabel(node.getTitle());
            titleLabel.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
            add(titleLabel, c);
            c.gridy++;
            c.insets = new Insets(2, 0, 10, 0);
            add(wrappedLabel(node.getCategory().toString().replaceAll("s$", "") + getDescription(node)), c);
            c.insets = new Insets(0, 0, 0, 0);
            for (Parameter<?> parameter: node.getParameters()) {
                c.gridy++;
                final JComponent row = createRow(parameter);
                rows.add(new Row(parameter, row));
                add(row, c);
            }
            updateRelevance();
        }
        // Push everything to the top
        c.gridy++;
        c.weighty = 1;
        add(Box.createGlue(), c);
        revalidate();
        repaint();
    }

    // Scrollable: follow the width of the sidebar and only scroll vertically

    @Override
    public java.awt.Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return Math.max(16, visibleRect.height - 32);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }

    private JComponent createRow(Parameter<?> parameter) {
        final JPanel row = new JPanel(new BorderLayout(8, 4));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(6, 0, 6, 0));
        final JLabel label = new JLabel(parameter.getLabel());
        label.setToolTipText(parameter.getDescription());
        if (parameter instanceof NumberParameter) {
            createNumberEditor(row, label, (NumberParameter) parameter);
        } else if (parameter instanceof BooleanParameter) {
            final JCheckBox checkBox = new JCheckBox(parameter.getLabel(), node.get((BooleanParameter) parameter));
            checkBox.setOpaque(false);
            checkBox.setToolTipText(parameter.getDescription());
            checkBox.addActionListener(e -> changed(() -> node.set((BooleanParameter) parameter, checkBox.isSelected())));
            row.add(checkBox, BorderLayout.CENTER);
        } else if (parameter instanceof ChoiceParameter) {
            createChoiceEditor(row, label, (ChoiceParameter<?>) parameter);
        } else if (parameter instanceof CurveParameter) {
            createCurveEditor(row, label, (CurveParameter) parameter);
        } else if (parameter instanceof ImageParameter) {
            createImageEditor(row, label, (ImageParameter) parameter);
        } else {
            throw new IllegalArgumentException("Unsupported parameter type " + parameter.getClass());
        }
        return row;
    }

    private void createNumberEditor(JPanel row, JLabel label, NumberParameter parameter) {
        final double min = parameter.getMin(), max = parameter.getMax(), step = parameter.getStep();
        final boolean logarithmic = parameter.isLogarithmic();
        final JSpinner spinner = new JSpinner(new SpinnerNumberModel(parameter.clamp(node.getDouble(parameter)), min, max, step));
        spinner.setEditor(new JSpinner.NumberEditor(spinner, parameter.isInteger() ? "0" : ((step >= 0.1) ? "0.0" : ((step >= 0.01) ? "0.00" : "0.0###"))));
        ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setColumns(6);
        spinner.setToolTipText(parameter.getDescription());
        final JPanel header = new JPanel(new BorderLayout(6, 0));
        header.setOpaque(false);
        header.add(label, BorderLayout.CENTER);
        final JPanel valuePanel = new JPanel(new FlowLayout(FlowLayout.TRAILING, 4, 0));
        valuePanel.setOpaque(false);
        valuePanel.add(spinner);
        if (parameter == Node.VARIANT) {
            final JButton randomButton = new JButton(Icons.get("shuffle", 14));
            randomButton.setToolTipText("Pick a random variant");
            randomButton.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
            randomButton.addActionListener(e -> spinner.setValue((double) RANDOM.nextInt((int) max + 1)));
            valuePanel.add(randomButton);
        }
        if (parameter.getUnit() != null) {
            valuePanel.add(mutedLabel(parameter.getUnit()));
        }
        header.add(valuePanel, BorderLayout.EAST);
        row.add(header, BorderLayout.NORTH);
        final boolean sliderUseful = logarithmic || ((max - min) / step <= 100000);
        final JSlider slider = sliderUseful ? new JSlider(0, SLIDER_RANGE) : null;
        final DoubleUnaryOperator toSlider = value -> logarithmic
                ? SLIDER_RANGE * Math.log(Math.max(value, min) / min) / Math.log(max / min)
                : SLIDER_RANGE * (value - min) / (max - min);
        final DoubleUnaryOperator fromSlider = position -> logarithmic
                ? min * Math.pow(max / min, position / SLIDER_RANGE)
                : min + (max - min) * position / SLIDER_RANGE;
        final boolean[] updating = {false};
        if (slider != null) {
            slider.setOpaque(false);
            slider.setToolTipText(parameter.getDescription());
            slider.setValue((int) Math.round(toSlider.applyAsDouble(node.getDouble(parameter))));
            slider.addChangeListener(e -> {
                if (! updating[0]) {
                    final double value = parameter.clamp(Math.round(fromSlider.applyAsDouble(slider.getValue()) / step) * step);
                    updating[0] = true;
                    try {
                        spinner.setValue(value);
                    } finally {
                        updating[0] = false;
                    }
                    changed(() -> node.set(parameter, value));
                }
            });
            row.add(slider, BorderLayout.CENTER);
        }
        spinner.addChangeListener(e -> {
            if (! updating[0]) {
                final double value = ((Number) spinner.getValue()).doubleValue();
                if (slider != null) {
                    updating[0] = true;
                    try {
                        slider.setValue((int) Math.round(toSlider.applyAsDouble(value)));
                    } finally {
                        updating[0] = false;
                    }
                }
                changed(() -> node.set(parameter, value));
            }
        });
    }

    private <E extends Enum<E>> void createChoiceEditor(JPanel row, JLabel label, ChoiceParameter<E> parameter) {
        final JComboBox<E> comboBox = new JComboBox<>(new DefaultComboBoxModel<>(new java.util.Vector<>(parameter.getChoices())));
        comboBox.setSelectedItem(node.get(parameter));
        comboBox.setToolTipText(parameter.getDescription());
        comboBox.addActionListener(e -> {
            @SuppressWarnings("unchecked") // Guaranteed by the model
            final E value = (E) comboBox.getSelectedItem();
            if (value != null) {
                changed(() -> node.set(parameter, value));
            }
        });
        row.add(label, BorderLayout.NORTH);
        row.add(comboBox, BorderLayout.CENTER);
    }

    private void createCurveEditor(JPanel row, JLabel label, CurveParameter parameter) {
        final CurveEditor editor = new CurveEditor();
        editor.setPoints(node.get(parameter));
        editor.setChangeListener(points -> changed(() -> node.set(parameter, points)));
        final JButton presets = new JButton("Presets", Icons.get("chevron-down", 14));
        presets.setHorizontalTextPosition(SwingConstants.LEADING);
        presets.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        presets.setToolTipText("Replace the curve with a preset");
        presets.addActionListener(e -> {
            final JPopupMenu menu = new JPopupMenu();
            for (int i = 0; i < CURVE_PRESETS.length; i++) {
                final float[] preset = CURVE_PRESETS[i];
                final JMenuItem item = new JMenuItem(CURVE_PRESET_NAMES[i]);
                item.addActionListener(event -> {
                    editor.setPoints(preset);
                    changed(() -> node.set(parameter, preset.clone()));
                });
                menu.add(item);
            }
            menu.show(presets, 0, presets.getHeight());
        });
        final JPanel header = new JPanel(new BorderLayout(6, 0));
        header.setOpaque(false);
        header.add(label, BorderLayout.CENTER);
        header.add(presets, BorderLayout.EAST);
        row.add(header, BorderLayout.NORTH);
        row.add(editor, BorderLayout.CENTER);
        row.add(wrappedLabel("Input on the horizontal axis, output on the vertical axis. Double-click to add or remove points."), BorderLayout.SOUTH);
    }

    private void createImageEditor(JPanel row, JLabel label, ImageParameter parameter) {
        final HeightImage image = node.get(parameter);
        final JLabel nameLabel = mutedLabel((image != null) ? image.toString() : "No image loaded");
        final JButton loadButton = new JButton("Load image\u2026", Icons.get("image", 14));
        loadButton.setToolTipText(parameter.getDescription());
        loadButton.addActionListener(e -> {
            final JFileChooser fileChooser = new JFileChooser(lastImageDir);
            fileChooser.setFileFilter(new FileNameExtensionFilter("Images (PNG, JPEG, TIFF, BMP, GIF)", "png", "jpg", "jpeg", "tif", "tiff", "bmp", "gif"));
            if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                final File file = fileChooser.getSelectedFile();
                lastImageDir = file.getParentFile();
                try {
                    final BufferedImage bufferedImage = ImageIO.read(file);
                    if (bufferedImage == null) {
                        showWarning(this, "The format of " + file.getName() + " is not supported", "Unsupported Image");
                        return;
                    }
                    final HeightImage heightImage = HeightImage.fromImage(file.getName(), bufferedImage);
                    nameLabel.setText(heightImage.toString());
                    changed(() -> node.set(parameter, heightImage));
                } catch (IOException ex) {
                    showWarning(this, "Could not read " + file.getName() + ": " + ex.getMessage(), "Error Reading Image");
                }
            }
        });
        row.add(label, BorderLayout.NORTH);
        row.add(nameLabel, BorderLayout.CENTER);
        row.add(loadButton, BorderLayout.EAST);
    }

    /**
     * Apply a change to the node and notify the listener.
     */
    private void changed(Runnable change) {
        change.run();
        if (titleLabel != null) {
            titleLabel.setText(node.getTitle());
        }
        updateRelevance();
        changeListener.accept(node);
    }

    private void updateRelevance() {
        boolean changed = false;
        for (Row row: rows) {
            final boolean relevant = node.isRelevant(row.parameter);
            if (row.component.isVisible() != relevant) {
                row.component.setVisible(relevant);
                changed = true;
            }
        }
        if (changed) {
            revalidate();
            repaint();
        }
    }

    private static String getDescription(Node node) {
        for (NodeType type: NodeType.getAll()) {
            if (type.getName().equals(node.getTitle()) || type.getName().equals(node.getName())) {
                return " \u00b7 " + type.getDescription();
            }
        }
        return (node instanceof OutputNode) ? " \u00b7 Turns values into terrain heights" : "";
    }

    /**
     * A muted label which wraps its text, so that it does not make the panel wider than the sidebar.
     */
    private static JLabel wrappedLabel(String html) {
        return mutedLabel("<html><div style=\"width: " + WRAP_WIDTH + "px\">" + html + "</div></html>");
    }

    private static JLabel mutedLabel(String text) {
        final JLabel label = new JLabel(text);
        label.putClientProperty(FlatClientProperties.STYLE, "foreground: $WorldPainter.mutedForeground; font: -1");
        return label;
    }

    private final Consumer<Node> changeListener;
    private final List<Row> rows = new ArrayList<>();
    private Node node;
    private JLabel titleLabel;

    private static File lastImageDir;

    private static final int SLIDER_RANGE = 1000, WRAP_WIDTH = 290;
    private static final Random RANDOM = new Random();
    private static final String[] CURVE_PRESET_NAMES = {"Linear", "Ease in (wide valleys)", "Ease out (wide plateaus)", "S-curve (more contrast)", "Plateau", "Invert"};
    private static final float[][] CURVE_PRESETS = {
            {0, 0, 1, 1},
            {0, 0, 0.5f, 0.2f, 1, 1},
            {0, 0, 0.5f, 0.8f, 1, 1},
            {0, 0, 0.3f, 0.12f, 0.7f, 0.88f, 1, 1},
            {0, 0, 0.45f, 0.1f, 0.6f, 0.7f, 1, 0.8f},
            {0, 1, 1, 0}};
    private static final long serialVersionUID = 1L;

    private static final class Row {
        Row(Parameter<?> parameter, JComponent component) {
            this.parameter = parameter;
            this.component = component;
        }

        final Parameter<?> parameter;
        final JComponent component;
    }
}
