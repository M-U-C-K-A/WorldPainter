package org.pepsoft.worldpainter.procedural;

import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.ProgressReceiver.OperationCancelled;
import org.pepsoft.util.swing.ProgressDialog;
import org.pepsoft.util.swing.ProgressTask;
import org.pepsoft.worldpainter.ColourScheme;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.WorldPainterDialog;
import org.pepsoft.worldpainter.WorldPainterView;
import org.pepsoft.worldpainter.themes.TerrainListCellRenderer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

import static org.pepsoft.util.swing.MessageUtils.showInfo;

/**
 * A dialog for designing a procedural terrain from a stack of noise layers and surface rules, with a live preview, and
 * applying it to the map.
 */
public class ProceduralTerrainDialog extends WorldPainterDialog {
    /**
     * Create a dialog for applying a procedural terrain to a dimension.
     */
    public ProceduralTerrainDialog(Window parent, Dimension dimension, WorldPainterView view, ColourScheme colourScheme, Point centre, boolean selectionPresent) {
        this(parent, dimension, view, colourScheme, centre, selectionPresent, null);
    }

    /**
     * Create a dialog for editing procedural terrain settings, without applying them. Use {@link #getSettings()} to
     * obtain the result if the dialog was not {@link #isCancelled() cancelled}.
     */
    public static ProceduralTerrainDialog createEditor(Window parent, Dimension dimension, ColourScheme colourScheme, Point centre, ProceduralTerrainSettings settings) {
        return new ProceduralTerrainDialog(parent, dimension, null, colourScheme, centre, false, settings.clone());
    }

    private ProceduralTerrainDialog(Window parent, Dimension dimension, WorldPainterView view, ColourScheme colourScheme, Point centre, boolean selectionPresent, ProceduralTerrainSettings initialSettings) {
        super(parent);
        this.dimension = dimension;
        this.view = view;
        this.colourScheme = colourScheme;
        editOnly = initialSettings != null;
        if (initialSettings != null) {
            settings = initialSettings;
        } else if (lastSettings != null) {
            settings = lastSettings.clone();
        } else {
            settings = ProceduralTerrainSettings.create(ProceduralTerrainSettings.Preset.ALPINE, new Random().nextLong());
        }
        setTitle(editOnly ? "Edit Procedural Terrain" : "Procedural Terrain");

        preview = new TerrainPreviewPanel(colourScheme, dimension.getWorld().getPlatform(), dimension.getMinHeight(), dimension.getMaxHeight());
        preview.setCentre(centre);
        preview.setRangeListener(this::updateRangeLabel);
        previewTimer = new Timer(200, e -> preview.render(settings));
        previewTimer.setRepeats(false);

        final JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBorder(new EmptyBorder(12, 12, 12, 12));
        content.add(createTopBar(), BorderLayout.NORTH);
        final JPanel centrePanel = new JPanel(new BorderLayout(12, 0));
        final JTabbedPane editorTabs = new JTabbedPane();
        editorTabs.addTab("Relief", createReliefPanel());
        editorTabs.addTab("Surface", createSurfacePanel());
        centrePanel.add(editorTabs, BorderLayout.CENTER);
        centrePanel.add(createPreviewPanel(), BorderLayout.EAST);
        content.add(centrePanel, BorderLayout.CENTER);
        content.add(createBottomPanel(selectionPresent), BorderLayout.SOUTH);
        setContentPane(content);

        loadSettings();
        scaleToUI();
        pack();
        scaleWindowToUI();
        setLocationRelativeTo(parent);
    }

    public ProceduralTerrainSettings getSettings() {
        return settings;
    }

    // UI construction

    private JComponent createTopBar() {
        final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 0));
        panel.add(new JLabel("Preset:"));
        presetComboBox = new JComboBox<>(ProceduralTerrainSettings.Preset.values());
        presetComboBox.setSelectedItem(null);
        presetComboBox.putClientProperty("JComboBox.placeholderText", "Choose a preset\u2026");
        presetComboBox.setToolTipText("Start from a preset (replaces all layers and rules)");
        presetComboBox.addActionListener(e -> {
            final ProceduralTerrainSettings.Preset preset = (ProceduralTerrainSettings.Preset) presetComboBox.getSelectedItem();
            if ((preset != null) && (! loading)) {
                settings = ProceduralTerrainSettings.create(preset, settings.getSeed());
                loadSettings();
            }
        });
        panel.add(presetComboBox);
        panel.add(Box.createHorizontalStrut(18));
        panel.add(new JLabel("Seed:"));
        seedField = new JSpinner(new SpinnerNumberModel(Long.valueOf(0L), Long.valueOf(Long.MIN_VALUE), Long.valueOf(Long.MAX_VALUE), Long.valueOf(1L)));
        seedField.setEditor(new JSpinner.NumberEditor(seedField, "#"));
        ((JSpinner.DefaultEditor) seedField.getEditor()).getTextField().setColumns(16);
        seedField.addChangeListener(e -> {
            if (! loading) {
                settings.setSeed(((Number) seedField.getValue()).longValue());
                settingsChanged();
            }
        });
        panel.add(seedField);
        final JButton randomButton = new JButton("Randomise");
        randomButton.setToolTipText("Pick a new random seed");
        randomButton.addActionListener(e -> seedField.setValue(new Random().nextLong()));
        panel.add(randomButton);
        return panel;
    }

    private JComponent createReliefPanel() {
        final JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));

        // Layer list
        layerTableModel = new LayerTableModel();
        layerTable = new JTable(layerTableModel);
        layerTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        layerTable.getColumnModel().getColumn(0).setMaxWidth(32);
        layerTable.setRowHeight(Math.max(layerTable.getRowHeight(), 22));
        layerTable.getSelectionModel().addListSelectionListener(e -> {
            if (! e.getValueIsAdjusting()) {
                selectLayer(layerTable.getSelectedRow());
            }
        });
        final JScrollPane layerScrollPane = new JScrollPane(layerTable);
        layerScrollPane.setPreferredSize(new java.awt.Dimension(380, 130));
        final JPanel layerListPanel = new JPanel(new BorderLayout(0, 4));
        layerListPanel.add(layerScrollPane, BorderLayout.CENTER);
        final JToolBar layerButtons = createListButtons(
                () -> {
                    final NoiseLayer layer = new NoiseLayer("Layer " + (settings.getLayers().size() + 1), NoiseLayer.Type.FBM, NoiseLayer.BlendMode.ADD, 256, 24, 5);
                    layer.setSeedOffset(new Random().nextInt());
                    settings.getLayers().add(layer);
                    return settings.getLayers().size() - 1;
                },
                index -> settings.getLayers().remove((int) index),
                (index, delta) -> swap(settings.getLayers(), index, delta),
                layerTable, layerTableModel);
        layerListPanel.add(layerButtons, BorderLayout.SOUTH);
        panel.add(layerListPanel, BorderLayout.NORTH);

        // Layer editor
        layerEditor = new JPanel(new GridBagLayout());
        layerEditor.setBorder(BorderFactory.createTitledBorder("Selected layer"));
        final GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 4, 2, 4);
        c.anchor = GridBagConstraints.WEST;
        c.gridy = 0;
        c.gridx = 0;
        layerEditor.add(new JLabel("Type:"), c);
        c.gridx = 1;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        typeComboBox = new JComboBox<>(NoiseLayer.Type.values());
        typeComboBox.addActionListener(e -> {
            if ((! loading) && (selectedLayer != null)) {
                selectedLayer.setType((NoiseLayer.Type) typeComboBox.getSelectedItem());
                layerTableModel.fireTableRowsUpdated(layerTable.getSelectedRow(), layerTable.getSelectedRow());
                settingsChanged();
            }
        });
        layerEditor.add(typeComboBox, c);
        c.gridy++;
        c.gridx = 0;
        c.gridwidth = 1;
        c.fill = GridBagConstraints.NONE;
        layerEditor.add(new JLabel("Combine:"), c);
        c.gridx = 1;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        blendComboBox = new JComboBox<>(NoiseLayer.BlendMode.values());
        blendComboBox.addActionListener(e -> {
            if ((! loading) && (selectedLayer != null)) {
                selectedLayer.setBlendMode((NoiseLayer.BlendMode) blendComboBox.getSelectedItem());
                layerTableModel.fireTableRowsUpdated(layerTable.getSelectedRow(), layerTable.getSelectedRow());
                settingsChanged();
            }
        });
        layerEditor.add(blendComboBox, c);
        c.gridwidth = 1;
        addSlider(layerEditor, c, "Feature size", "The size of the largest features, in blocks", 8, 8192, 1, true, () -> selectedLayer.getScale(), v -> selectedLayer.setScale((float) v));
        addSlider(layerEditor, c, "Height", "The height of the layer, in blocks", 0, 384, 1, false, () -> selectedLayer.getAmplitude(), v -> selectedLayer.setAmplitude((float) v));
        addSlider(layerEditor, c, "Detail", "The number of octaves: how many levels of finer detail are added", 1, 10, 1, false, () -> selectedLayer.getOctaves(), v -> selectedLayer.setOctaves((int) Math.round(v)));
        addSlider(layerEditor, c, "Roughness", "How strong the finer detail is (0 - 1)", 0, 1, 0.01, false, () -> selectedLayer.getRoughness(), v -> selectedLayer.setRoughness((float) v));
        addSlider(layerEditor, c, "Sharpness", "Above 1: flatter lowlands and sharper peaks; below 1: the opposite", 0.2, 16, 0.05, true, () -> selectedLayer.getSharpness(), v -> selectedLayer.setSharpness((float) v));
        addSlider(layerEditor, c, "Warp", "How much the shapes are twisted, in blocks, for a more organic look", 0, 512, 1, false, () -> selectedLayer.getWarp(), v -> selectedLayer.setWarp((float) v));
        addSlider(layerEditor, c, "Coverage", "The fraction of the map where this layer is present (0 - 1); use it to create separate mountain ranges, islands, etc.", 0, 1, 0.01, false, () -> selectedLayer.getCoverage(), v -> selectedLayer.setCoverage((float) v));
        addSlider(layerEditor, c, "Direction", "The direction of the ridges, in degrees (dunes only)", 0, 180, 1, false, () -> selectedLayer.getDirection(), v -> selectedLayer.setDirection((float) v));
        panel.add(layerEditor, BorderLayout.CENTER);

        // Global settings
        final JPanel globals = new JPanel(new GridBagLayout());
        globals.setBorder(BorderFactory.createTitledBorder("Global"));
        final GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(2, 4, 2, 4);
        g.anchor = GridBagConstraints.WEST;
        g.gridy = -1;
        addSlider(globals, g, "Base height", "The height to which all layers are added", dimension.getMinHeight(), dimension.getMaxHeight() - 1, 1, false, () -> settings.getBaseHeight(), v -> settings.setBaseHeight((int) Math.round(v)));
        addSlider(globals, g, "Terraces", "The height of terraces in blocks, or 0 for no terraces", 0, 32, 1, false, () -> settings.getTerraceHeight(), v -> settings.setTerraceHeight((int) Math.round(v)));
        addSlider(globals, g, "Terrace sharpness", "How flat the terraces and how steep the steps are (0 - 1)", 0, 1, 0.01, false, () -> settings.getTerraceSharpness(), v -> settings.setTerraceSharpness((float) v));
        g.gridy++;
        g.gridx = 0;
        waterCheckBox = new JCheckBox("Water up to");
        waterCheckBox.addActionListener(e -> {
            if (! loading) {
                settings.setWater(waterCheckBox.isSelected());
                settingsChanged();
            }
        });
        globals.add(waterCheckBox, g);
        g.gridx = 1;
        waterLevelSpinner = new JSpinner(new SpinnerNumberModel(62, dimension.getMinHeight(), dimension.getMaxHeight() - 1, 1));
        waterLevelSpinner.addChangeListener(e -> {
            if (! loading) {
                settings.setWaterLevel((Integer) waterLevelSpinner.getValue());
                settingsChanged();
            }
        });
        globals.add(waterLevelSpinner, g);
        panel.add(globals, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent createSurfacePanel() {
        final JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));
        final JLabel help = new JLabel("<html>The first matching rule determines the terrain type. Leave a height empty for no limit. Slopes are in degrees (0 = flat, 90 = vertical).</html>");
        panel.add(help, BorderLayout.NORTH);
        ruleTableModel = new RuleTableModel();
        ruleTable = new JTable(ruleTableModel);
        ruleTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ruleTable.setRowHeight(Math.max(ruleTable.getRowHeight(), 24));
        ruleTable.getColumnModel().getColumn(0).setMaxWidth(32);
        ruleTable.getColumnModel().getColumn(1).setPreferredWidth(170);
        final JComboBox<Terrain> terrainEditor = new JComboBox<>(Terrain.getConfiguredValues());
        terrainEditor.setRenderer(new TerrainListCellRenderer(colourScheme));
        ruleTable.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(terrainEditor));
        final TerrainListCellRenderer listRenderer = new TerrainListCellRenderer(colourScheme);
        final JList<Terrain> dummyList = new JList<>();
        ruleTable.getColumnModel().getColumn(1).setCellRenderer((table, value, isSelected, hasFocus, row, column) ->
                listRenderer.getListCellRendererComponent(dummyList, value, row, isSelected, hasFocus));
        final JScrollPane scrollPane = new JScrollPane(ruleTable);
        scrollPane.setPreferredSize(new java.awt.Dimension(380, 260));
        final JPanel listPanel = new JPanel(new BorderLayout(0, 4));
        listPanel.add(scrollPane, BorderLayout.CENTER);
        listPanel.add(createListButtons(
                () -> {
                    settings.getRules().add(new SurfaceRule(Terrain.STONE, Integer.MIN_VALUE, Integer.MAX_VALUE, 0, 90));
                    return settings.getRules().size() - 1;
                },
                index -> settings.getRules().remove((int) index),
                (index, delta) -> swap(settings.getRules(), index, delta),
                ruleTable, ruleTableModel), BorderLayout.SOUTH);
        panel.add(listPanel, BorderLayout.CENTER);

        final JPanel defaultPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 0));
        defaultPanel.add(new JLabel("Otherwise:"));
        defaultTerrainComboBox = new JComboBox<>(Terrain.getConfiguredValues());
        defaultTerrainComboBox.setRenderer(new TerrainListCellRenderer(colourScheme));
        defaultTerrainComboBox.addActionListener(e -> {
            if (! loading) {
                settings.setDefaultTerrain((Terrain) defaultTerrainComboBox.getSelectedItem());
                settingsChanged();
            }
        });
        defaultPanel.add(defaultTerrainComboBox);
        panel.add(defaultPanel, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent createPreviewPanel() {
        final JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.add(preview, BorderLayout.CENTER);
        final JPanel controls = new JPanel(new BorderLayout(6, 0));
        final JPanel areaPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 0));
        areaPanel.add(new JLabel("Preview area:"));
        final JComboBox<String> areaComboBox = new JComboBox<>(new String[] {"512 blocks", "1024 blocks", "2048 blocks", "4096 blocks", "8192 blocks", "16384 blocks"});
        areaComboBox.setSelectedIndex(2);
        areaComboBox.addActionListener(e -> {
            preview.setArea(512 << areaComboBox.getSelectedIndex());
            settingsChanged();
        });
        areaPanel.add(areaComboBox);
        controls.add(areaPanel, BorderLayout.NORTH);
        rangeLabel = new JLabel(" ");
        rangeLabel.setBorder(new EmptyBorder(4, 4, 0, 0));
        controls.add(rangeLabel, BorderLayout.SOUTH);
        panel.add(controls, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent createBottomPanel(boolean selectionPresent) {
        final JPanel panel = new JPanel(new BorderLayout(12, 0));
        final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING, 6, 0));
        final JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> cancel());
        final JButton okButton = new JButton(editOnly ? "OK" : "Apply");
        okButton.addActionListener(e -> {
            if (editOnly) {
                lastSettings = settings.clone();
                ok();
            } else {
                apply();
            }
        });
        buttons.add(cancelButton);
        buttons.add(okButton);
        getRootPane().setDefaultButton(okButton);
        panel.add(buttons, BorderLayout.EAST);
        if (editOnly) {
            return panel;
        }

        final JPanel options = new JPanel(new GridBagLayout());
        options.setBorder(BorderFactory.createTitledBorder("Apply to map"));
        final GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 4, 2, 4);
        c.anchor = GridBagConstraints.WEST;
        c.gridy = 0;
        options.add(new JLabel("Mode:"), c);
        modeComboBox = new JComboBox<>(ProceduralTerrainOperation.Mode.values());
        modeComboBox.setToolTipText("<html><b>Replace</b>: replace the terrain height<br><b>Add relief</b>: add the generated relief (relative to the base height) to the existing terrain<br><b>Raise only</b> / <b>Lower only</b>: only change the terrain where the generated terrain is higher / lower</html>");
        options.add(modeComboBox, c);
        options.add(new JLabel("Strength:"), c);
        strengthSlider = new JSlider(0, 100, 100);
        strengthSlider.setPreferredSize(new java.awt.Dimension(120, strengthSlider.getPreferredSize().height));
        final JLabel strengthLabel = new JLabel("100 %");
        strengthSlider.addChangeListener(e -> strengthLabel.setText(strengthSlider.getValue() + " %"));
        options.add(strengthSlider, c);
        options.add(strengthLabel, c);
        c.gridy = 1;
        wholeMapRadioButton = new JRadioButton("Whole map", true);
        selectionRadioButton = new JRadioButton("Selection only, feathered by");
        selectionRadioButton.setEnabled(selectionPresent);
        if (selectionPresent) {
            selectionRadioButton.setSelected(true);
        }
        final ButtonGroup group = new ButtonGroup();
        group.add(wholeMapRadioButton);
        group.add(selectionRadioButton);
        options.add(wholeMapRadioButton, c);
        c.gridwidth = 2;
        options.add(selectionRadioButton, c);
        c.gridwidth = 1;
        featherSpinner = new JSpinner(new SpinnerNumberModel(32, 0, 512, 4));
        options.add(featherSpinner, c);
        options.add(new JLabel("blocks"), c);
        c.gridy = 2;
        c.gridwidth = 2;
        applyTerrainCheckBox = new JCheckBox("Paint terrain types", true);
        options.add(applyTerrainCheckBox, c);
        applyWaterCheckBox = new JCheckBox("Set water level", true);
        options.add(applyWaterCheckBox, c);
        // Push everything to the left
        c.gridx = 6;
        c.weightx = 1.0;
        options.add(Box.createHorizontalGlue(), c);
        panel.add(options, BorderLayout.CENTER);
        return panel;
    }

    // Behaviour

    private void apply() {
        lastSettings = settings.clone();
        final ProceduralTerrainOperation operation = new ProceduralTerrainOperation(settings.clone());
        operation.setMode((ProceduralTerrainOperation.Mode) modeComboBox.getSelectedItem());
        operation.setStrength(strengthSlider.getValue() / 100f);
        operation.setSelectionOnly(selectionRadioButton.isSelected());
        operation.setFeather((Integer) featherSpinner.getValue());
        operation.setApplyTerrain(applyTerrainCheckBox.isSelected());
        operation.setApplyWater(applyWaterCheckBox.isSelected());
        dimension.setEventsInhibited(true);
        dimension.rememberChanges();
        if (view != null) {
            view.setInhibitUpdates(true);
        }
        try {
            final Dimension result = ProgressDialog.executeTask(this, new ProgressTask<Dimension>() {
                @Override
                public String getName() {
                    return "Generating procedural terrain";
                }

                @Override
                public Dimension execute(ProgressReceiver progressReceiver) throws OperationCancelled {
                    operation.apply(dimension, progressReceiver);
                    return dimension;
                }
            });
            if (result == null) {
                // Cancelled by the user
                if (dimension.undoChanges()) {
                    dimension.clearRedo();
                }
            } else {
                dimension.armSavePoint();
                ok();
            }
        } finally {
            if (view != null) {
                view.setInhibitUpdates(false);
            }
            dimension.setEventsInhibited(false);
        }
    }

    private void loadSettings() {
        loading = true;
        try {
            seedField.setValue(settings.getSeed());
            waterCheckBox.setSelected(settings.isWater());
            waterLevelSpinner.setValue(Math.max(dimension.getMinHeight(), Math.min(dimension.getMaxHeight() - 1, settings.getWaterLevel())));
            defaultTerrainComboBox.setSelectedItem(settings.getDefaultTerrain());
            layerTableModel.fireTableDataChanged();
            ruleTableModel.fireTableDataChanged();
            refreshers.forEach(Runnable::run);
        } finally {
            loading = false;
        }
        if (! settings.getLayers().isEmpty()) {
            layerTable.getSelectionModel().setSelectionInterval(0, 0);
        } else {
            selectLayer(-1);
        }
        settingsChanged();
    }

    private void selectLayer(int index) {
        selectedLayer = ((index >= 0) && (index < settings.getLayers().size())) ? settings.getLayers().get(index) : null;
        loading = true;
        try {
            if (selectedLayer != null) {
                typeComboBox.setSelectedItem(selectedLayer.getType());
                blendComboBox.setSelectedItem(selectedLayer.getBlendMode());
            }
            layerRefreshers.forEach(Runnable::run);
        } finally {
            loading = false;
        }
        setEnabledRecursively(layerEditor, selectedLayer != null);
    }

    private void settingsChanged() {
        previewTimer.restart();
    }

    private void updateRangeLabel(float[] range) {
        final int min = Math.round(range[0]), max = Math.round(range[1]);
        final boolean clipped = (min < dimension.getMinHeight()) || (max >= dimension.getMaxHeight());
        rangeLabel.setText("<html>Heights in preview: " + min + " to " + max + (clipped ? " <font color=\"#e05050\">(exceeds map limits " + dimension.getMinHeight() + " to " + (dimension.getMaxHeight() - 1) + ")</font>" : "") + "</html>");
    }

    /**
     * Add a row with a label, a slider and a spinner, bound to a getter and setter. If {@code logarithmic} is set the
     * slider has a logarithmic scale.
     */
    private void addSlider(JPanel panel, GridBagConstraints c, String label, String tooltip, double min, double max, double step, boolean logarithmic, DoubleSupplier getter, DoubleConsumer setter) {
        c.gridy++;
        c.gridx = 0;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        final JLabel jLabel = new JLabel(label + ":");
        jLabel.setToolTipText(tooltip);
        panel.add(jLabel, c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        final JSlider slider = new JSlider(0, 1000);
        slider.setToolTipText(tooltip);
        panel.add(slider, c);
        c.gridx = 2;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        final JSpinner spinner = new JSpinner(new SpinnerNumberModel(min, min, max, step));
        if (step >= 1) {
            spinner.setEditor(new JSpinner.NumberEditor(spinner, "0"));
        } else {
            spinner.setEditor(new JSpinner.NumberEditor(spinner, "0.00"));
        }
        ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setColumns(5);
        spinner.setToolTipText(tooltip);
        panel.add(spinner, c);
        final boolean[] updating = {false};
        final java.util.function.DoubleUnaryOperator toSlider = value -> logarithmic
                ? 1000 * Math.log(Math.max(value, min) / min) / Math.log(max / min)
                : 1000 * (value - min) / (max - min);
        final java.util.function.DoubleUnaryOperator fromSlider = position -> logarithmic
                ? min * Math.pow(max / min, position / 1000)
                : min + (max - min) * position / 1000;
        slider.addChangeListener(e -> {
            if (! updating[0]) {
                double value = fromSlider.applyAsDouble(slider.getValue());
                value = Math.round(value / step) * step;
                updating[0] = true;
                try {
                    spinner.setValue(Math.max(min, Math.min(max, value)));
                } finally {
                    updating[0] = false;
                }
                if (! loading) {
                    setter.accept(value);
                    settingsChanged();
                }
            }
        });
        spinner.addChangeListener(e -> {
            if (! updating[0]) {
                final double value = ((Number) spinner.getValue()).doubleValue();
                updating[0] = true;
                try {
                    slider.setValue((int) Math.round(toSlider.applyAsDouble(value)));
                } finally {
                    updating[0] = false;
                }
                if (! loading) {
                    setter.accept(value);
                    settingsChanged();
                }
            }
        });
        final Runnable refresher = () -> {
            try {
                final double value = Math.max(min, Math.min(max, getter.getAsDouble()));
                updating[0] = true;
                try {
                    spinner.setValue(value);
                    slider.setValue((int) Math.round(toSlider.applyAsDouble(value)));
                } finally {
                    updating[0] = false;
                }
            } catch (NullPointerException e) {
                // No layer selected
            }
        };
        if (panel == layerEditor) {
            layerRefreshers.add(refresher);
        } else {
            refreshers.add(refresher);
        }
    }

    private JToolBar createListButtons(java.util.function.IntSupplier add, java.util.function.IntConsumer remove, java.util.function.BiConsumer<Integer, Integer> move, JTable table, AbstractTableModel model) {
        final JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        final JButton addButton = new JButton("Add");
        addButton.addActionListener(e -> {
            final int index = add.getAsInt();
            model.fireTableDataChanged();
            table.getSelectionModel().setSelectionInterval(index, index);
            settingsChanged();
        });
        toolBar.add(addButton);
        final JButton removeButton = new JButton("Remove");
        removeButton.addActionListener(e -> {
            final int index = table.getSelectedRow();
            if (index != -1) {
                stopEditing(table);
                remove.accept(index);
                model.fireTableDataChanged();
                final int newIndex = Math.min(index, model.getRowCount() - 1);
                if (newIndex >= 0) {
                    table.getSelectionModel().setSelectionInterval(newIndex, newIndex);
                } else if (table == layerTable) {
                    selectLayer(-1);
                }
                settingsChanged();
            }
        });
        toolBar.add(removeButton);
        toolBar.addSeparator();
        for (int delta: new int[] {-1, 1}) {
            final JButton button = new JButton((delta < 0) ? "Move up" : "Move down");
            button.addActionListener(e -> {
                final int index = table.getSelectedRow();
                if ((index != -1) && (index + delta >= 0) && (index + delta < model.getRowCount())) {
                    stopEditing(table);
                    move.accept(index, delta);
                    model.fireTableDataChanged();
                    table.getSelectionModel().setSelectionInterval(index + delta, index + delta);
                    settingsChanged();
                }
            });
            toolBar.add(button);
        }
        return toolBar;
    }

    private static void stopEditing(JTable table) {
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
    }

    private static <T> void swap(List<T> list, int index, int delta) {
        final T item = list.get(index);
        list.set(index, list.get(index + delta));
        list.set(index + delta, item);
    }

    private static void setEnabledRecursively(Container container, boolean enabled) {
        for (Component component: container.getComponents()) {
            component.setEnabled(enabled);
            if (component instanceof Container) {
                setEnabledRecursively((Container) component, enabled);
            }
        }
    }

    private final Dimension dimension;
    private final WorldPainterView view;
    private final ColourScheme colourScheme;
    private final boolean editOnly;
    private final TerrainPreviewPanel preview;
    private final Timer previewTimer;
    private final List<Runnable> refreshers = new ArrayList<>(), layerRefreshers = new ArrayList<>();
    private ProceduralTerrainSettings settings;
    private NoiseLayer selectedLayer;
    private boolean loading;
    private JComboBox<ProceduralTerrainSettings.Preset> presetComboBox;
    private JSpinner seedField, waterLevelSpinner, featherSpinner;
    private JTable layerTable, ruleTable;
    private LayerTableModel layerTableModel;
    private RuleTableModel ruleTableModel;
    private JPanel layerEditor;
    private JComboBox<NoiseLayer.Type> typeComboBox;
    private JComboBox<NoiseLayer.BlendMode> blendComboBox;
    private JComboBox<Terrain> defaultTerrainComboBox;
    private JCheckBox waterCheckBox, applyTerrainCheckBox, applyWaterCheckBox;
    private JLabel rangeLabel;
    private JComboBox<ProceduralTerrainOperation.Mode> modeComboBox;
    private JSlider strengthSlider;
    private JRadioButton wholeMapRadioButton, selectionRadioButton;

    /**
     * The settings last used, so that they are remembered for the rest of the session.
     */
    private static ProceduralTerrainSettings lastSettings;

    private static final long serialVersionUID = 1L;

    private class LayerTableModel extends AbstractTableModel {
        @Override
        public int getRowCount() {
            return settings.getLayers().size();
        }

        @Override
        public int getColumnCount() {
            return 4;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMN_NAMES[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return (column == 0) ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column <= 1;
        }

        @Override
        public Object getValueAt(int row, int column) {
            final NoiseLayer layer = settings.getLayers().get(row);
            switch (column) {
                case 0:
                    return layer.isEnabled();
                case 1:
                    return layer.getName();
                case 2:
                    return layer.getType().toString();
                case 3:
                    return layer.getBlendMode().toString();
                default:
                    throw new IndexOutOfBoundsException();
            }
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            final NoiseLayer layer = settings.getLayers().get(row);
            if (column == 0) {
                layer.setEnabled((Boolean) value);
            } else if (column == 1) {
                layer.setName((String) value);
            }
            settingsChanged();
        }

        private final String[] COLUMN_NAMES = {"", "Layer", "Type", "Combine"};
        private static final long serialVersionUID = 1L;
    }

    private class RuleTableModel extends AbstractTableModel {
        @Override
        public int getRowCount() {
            return settings.getRules().size();
        }

        @Override
        public int getColumnCount() {
            return 6;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMN_NAMES[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            switch (column) {
                case 0:
                    return Boolean.class;
                case 1:
                    return Terrain.class;
                default:
                    return String.class;
            }
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return true;
        }

        @Override
        public Object getValueAt(int row, int column) {
            final SurfaceRule rule = settings.getRules().get(row);
            switch (column) {
                case 0:
                    return rule.isEnabled();
                case 1:
                    return rule.getTerrain();
                case 2:
                    return (rule.getMinHeight() == Integer.MIN_VALUE) ? "" : Integer.toString(rule.getMinHeight());
                case 3:
                    return (rule.getMaxHeight() == Integer.MAX_VALUE) ? "" : Integer.toString(rule.getMaxHeight());
                case 4:
                    return formatSlope(rule.getMinSlope());
                case 5:
                    return formatSlope(rule.getMaxSlope());
                default:
                    throw new IndexOutOfBoundsException();
            }
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            final SurfaceRule rule = settings.getRules().get(row);
            try {
                switch (column) {
                    case 0:
                        rule.setEnabled((Boolean) value);
                        break;
                    case 1:
                        rule.setTerrain((Terrain) value);
                        break;
                    case 2:
                        rule.setMinHeight(((String) value).isBlank() ? Integer.MIN_VALUE : Integer.parseInt(((String) value).trim()));
                        break;
                    case 3:
                        rule.setMaxHeight(((String) value).isBlank() ? Integer.MAX_VALUE : Integer.parseInt(((String) value).trim()));
                        break;
                    case 4:
                        rule.setMinSlope(((String) value).isBlank() ? 0 : Math.max(0, Math.min(90, Float.parseFloat(((String) value).trim()))));
                        break;
                    case 5:
                        rule.setMaxSlope(((String) value).isBlank() ? 90 : Math.max(0, Math.min(90, Float.parseFloat(((String) value).trim()))));
                        break;
                }
                settingsChanged();
            } catch (NumberFormatException e) {
                showInfo(ProceduralTerrainDialog.this, "\"" + value + "\" is not a valid number", "Invalid Number");
            }
        }

        private String formatSlope(float slope) {
            return (slope == Math.round(slope)) ? Integer.toString(Math.round(slope)) : Float.toString(slope);
        }

        private final String[] COLUMN_NAMES = {"", "Terrain", "Min height", "Max height", "Min slope", "Max slope"};
        private static final long serialVersionUID = 1L;
    }
}
