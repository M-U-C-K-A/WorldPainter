package org.pepsoft.worldpainter.procedural;

import org.pepsoft.worldpainter.AddedTiles;

import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.ProgressReceiver.OperationCancelled;
import org.pepsoft.util.swing.ProgressDialog;
import org.pepsoft.util.swing.ProgressTask;
import org.pepsoft.worldpainter.ColourScheme;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.WorldPainterDialog;
import org.pepsoft.worldpainter.WorldPainterView;
import org.pepsoft.worldpainter.themes.TerrainListCellRenderer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.Path2D;
import java.util.HashSet;
import java.util.Set;

import static org.pepsoft.worldpainter.Constants.TILE_SIZE;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE_BITS;

/**
 * A dialog for progressively blending the edges of the map into a target terrain, so that the map does not end in an
 * abrupt cliff.
 */
public class EdgeBlendDialog extends WorldPainterDialog {
    public EdgeBlendDialog(Window parent, Dimension dimension, WorldPainterView view, ColourScheme colourScheme, Point centre) {
        super(parent);
        this.dimension = dimension;
        this.view = view;
        this.colourScheme = colourScheme;
        this.centre = centre;
        edgeHeight = averageEdgeHeight(dimension);
        targetSettings = (lastTargetSettings != null) ? lastTargetSettings.clone() : ProceduralTerrainSettings.create(ProceduralTerrainSettings.Preset.MINECRAFT_LIKE, dimension.getMinecraftSeed());
        setTitle("Blend Map Edges");

        final JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBorder(new EmptyBorder(12, 12, 12, 12));
        final JLabel intro = new JLabel("<html><div style=\"width: 560px\">Smoothly blend the edges of the map into a target terrain instead of ending in a cliff. " +
                "Choose the <b>Minecraft-like</b> target to match the land Minecraft generates beyond the map; Minecraft then blends the last few blocks itself when it generates the surrounding chunks.</div></html>");
        content.add(intro, BorderLayout.NORTH);

        final JPanel form = new JPanel(new GridBagLayout());
        final GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 4, 3, 4);
        c.anchor = GridBagConstraints.WEST;
        c.gridy = 0;

        // Width
        c.gridx = 0;
        form.add(new JLabel("Transition width:"), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        widthSlider = new JSlider(16, 1024, lastWidth);
        form.add(widthSlider, c);
        c.gridx = 2;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        widthSpinner = new JSpinner(new SpinnerNumberModel(lastWidth, 16, 4096, 16));
        form.add(widthSpinner, c);
        c.gridx = 3;
        form.add(new JLabel("blocks"), c);
        widthSlider.addChangeListener(e -> {
            if (! updating) {
                updating = true;
                widthSpinner.setValue(widthSlider.getValue());
                updating = false;
                updatePreview();
            }
        });
        widthSpinner.addChangeListener(e -> {
            if (! updating) {
                updating = true;
                widthSlider.setValue(Math.min(1024, (Integer) widthSpinner.getValue()));
                updating = false;
                updatePreview();
            }
        });

        // Placement
        c.gridy++;
        c.gridx = 0;
        form.add(new JLabel("Transition zone:"), c);
        c.gridx = 1;
        c.gridwidth = 3;
        placementComboBox = new JComboBox<>(EdgeBlendOperation.Placement.values());
        placementComboBox.setSelectedItem(lastPlacement);
        placementComboBox.addActionListener(e -> updatePreview());
        form.add(placementComboBox, c);
        c.gridwidth = 1;

        // Curve
        c.gridy++;
        c.gridx = 0;
        form.add(new JLabel("Shape:"), c);
        c.gridx = 1;
        c.gridwidth = 3;
        curveComboBox = new JComboBox<>(EdgeBlendOperation.Curve.values());
        curveComboBox.setSelectedItem(lastCurve);
        curveComboBox.addActionListener(e -> updatePreview());
        form.add(curveComboBox, c);
        c.gridwidth = 1;

        // Irregularity
        c.gridy++;
        c.gridx = 0;
        form.add(new JLabel("Irregularity:"), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        irregularitySlider = new JSlider(0, 100, lastIrregularity);
        irregularitySlider.setToolTipText("Make the transition zone wander instead of running exactly parallel to the edge of the map");
        form.add(irregularitySlider, c);
        c.fill = GridBagConstraints.NONE;

        // Target
        c.gridy++;
        c.gridx = 0;
        c.gridwidth = 4;
        form.add(new JSeparator(), c);
        c.gridy++;
        c.gridwidth = 1;
        form.add(new JLabel("Blend into:"), c);
        c.gridx = 1;
        c.gridwidth = 3;
        proceduralRadioButton = new JRadioButton("Procedural terrain:", ! lastFlatTarget);
        final JPanel proceduralPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 0));
        proceduralPanel.add(proceduralRadioButton);
        presetComboBox = new JComboBox<>(ProceduralTerrainSettings.Preset.values());
        presetComboBox.setSelectedItem(ProceduralTerrainSettings.Preset.MINECRAFT_LIKE);
        presetComboBox.setSelectedIndex(-1);
        presetComboBox.putClientProperty("JComboBox.placeholderText", "Choose a preset\u2026");
        presetComboBox.addActionListener(e -> {
            final ProceduralTerrainSettings.Preset preset = (ProceduralTerrainSettings.Preset) presetComboBox.getSelectedItem();
            if (preset != null) {
                targetSettings = ProceduralTerrainSettings.create(preset, dimension.getMinecraftSeed());
                proceduralRadioButton.setSelected(true);
                updateTargetLabel();
                updatePreview();
            }
        });
        proceduralPanel.add(presetComboBox);
        final JButton customiseButton = new JButton("Customise…");
        customiseButton.addActionListener(e -> {
            final ProceduralTerrainDialog editor = ProceduralTerrainDialog.createEditor(this, dimension, colourScheme, centre, targetSettings);
            editor.setVisible(true);
            if (! editor.isCancelled()) {
                targetSettings = editor.getSettings();
                presetComboBox.setSelectedIndex(-1);
                proceduralRadioButton.setSelected(true);
                updateTargetLabel();
                updatePreview();
            }
        });
        proceduralPanel.add(customiseButton);
        targetLabel = new JLabel();
        proceduralPanel.add(targetLabel);
        form.add(proceduralPanel, c);
        c.gridy++;
        flatRadioButton = new JRadioButton("Flat level at", lastFlatTarget);
        final JPanel flatPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 0));
        flatPanel.add(flatRadioButton);
        targetHeightSpinner = new JSpinner(new SpinnerNumberModel(Math.max(dimension.getMinHeight(), Math.min(dimension.getMaxHeight() - 1, lastTargetHeight)), dimension.getMinHeight(), dimension.getMaxHeight() - 1, 1));
        targetHeightSpinner.addChangeListener(e -> {
            flatRadioButton.setSelected(true);
            updatePreview();
        });
        flatPanel.add(targetHeightSpinner);
        flatPanel.add(new JLabel("covered with"));
        targetTerrainComboBox = new JComboBox<>(Terrain.getConfiguredValues());
        targetTerrainComboBox.setRenderer(new TerrainListCellRenderer(colourScheme));
        targetTerrainComboBox.setSelectedItem(Terrain.GRASS);
        flatPanel.add(targetTerrainComboBox);
        form.add(flatPanel, c);
        final ButtonGroup targetGroup = new ButtonGroup();
        targetGroup.add(proceduralRadioButton);
        targetGroup.add(flatRadioButton);
        proceduralRadioButton.addActionListener(e -> updatePreview());
        flatRadioButton.addActionListener(e -> updatePreview());

        // Terrain and water
        c.gridy++;
        c.gridx = 1;
        applyTerrainCheckBox = new JCheckBox("Paint terrain types in the transition zone", true);
        form.add(applyTerrainCheckBox, c);
        c.gridy++;
        final JPanel waterPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 0));
        applyWaterCheckBox = new JCheckBox("Fill with water up to", true);
        waterPanel.add(applyWaterCheckBox);
        waterLevelSpinner = new JSpinner(new SpinnerNumberModel(Math.max(dimension.getMinHeight(), Math.min(dimension.getMaxHeight() - 1, 62)), dimension.getMinHeight(), dimension.getMaxHeight() - 1, 1));
        waterLevelSpinner.addChangeListener(e -> updatePreview());
        waterPanel.add(waterLevelSpinner);
        form.add(waterPanel, c);
        c.gridwidth = 1;

        final JPanel centrePanel = new JPanel(new BorderLayout(0, 8));
        centrePanel.add(form, BorderLayout.NORTH);
        profile = new ProfilePanel();
        profile.setBorder(BorderFactory.createTitledBorder("Cross-section of the transition"));
        centrePanel.add(profile, BorderLayout.CENTER);
        infoLabel = new JLabel(" ");
        centrePanel.add(infoLabel, BorderLayout.SOUTH);
        content.add(centrePanel, BorderLayout.CENTER);

        final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING, 6, 0));
        final JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> cancel());
        final JButton applyButton = new JButton("Blend Edges");
        applyButton.addActionListener(e -> apply());
        buttons.add(cancelButton);
        buttons.add(applyButton);
        getRootPane().setDefaultButton(applyButton);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);

        updateTargetLabel();
        updatePreview();
        scaleToUI();
        pack();
        scaleWindowToUI();
        setLocationRelativeTo(parent);
    }

    private EdgeBlendOperation createOperation() {
        final EdgeBlendOperation operation = new EdgeBlendOperation();
        operation.setWidth((Integer) widthSpinner.getValue());
        operation.setPlacement((EdgeBlendOperation.Placement) placementComboBox.getSelectedItem());
        operation.setCurve((EdgeBlendOperation.Curve) curveComboBox.getSelectedItem());
        operation.setIrregularity(irregularitySlider.getValue() / 100f);
        operation.setTarget(proceduralRadioButton.isSelected() ? targetSettings.clone() : null);
        operation.setTargetHeight((Integer) targetHeightSpinner.getValue());
        operation.setTargetTerrain((Terrain) targetTerrainComboBox.getSelectedItem());
        operation.setApplyTerrain(applyTerrainCheckBox.isSelected());
        operation.setApplyWater(applyWaterCheckBox.isSelected());
        operation.setWaterLevel((Integer) waterLevelSpinner.getValue());
        operation.setSeed(dimension.getMinecraftSeed());
        return operation;
    }

    private void apply() {
        final EdgeBlendOperation operation = createOperation();
        lastWidth = operation.getWidth();
        lastPlacement = operation.getPlacement();
        lastCurve = operation.getCurve();
        lastIrregularity = irregularitySlider.getValue();
        lastFlatTarget = flatRadioButton.isSelected();
        lastTargetHeight = operation.getTargetHeight();
        lastTargetSettings = targetSettings.clone();
        final java.util.Set<java.awt.Point> tilesBefore = new java.util.HashSet<>(dimension.getTileCoords());
        dimension.setEventsInhibited(true);
        dimension.rememberChanges();
        if (view != null) {
            view.setInhibitUpdates(true);
        }
        try {
            final Dimension result = ProgressDialog.executeTask(this, new ProgressTask<Dimension>() {
                @Override
                public String getName() {
                    return "Blending map edges";
                }

                @Override
                public Dimension execute(ProgressReceiver progressReceiver) throws OperationCancelled {
                    operation.apply(dimension, progressReceiver);
                    return dimension;
                }
            });
            if (result == null) {
                if (dimension.undoChanges()) {
                    dimension.clearRedo();
                }
                AddedTiles.remove(dimension, tilesBefore);
            } else {
                AddedTiles.record(dimension, tilesBefore);
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

    private void updateTargetLabel() {
        targetLabel.setText("(" + targetSettings.getName() + ")");
    }

    private void updatePreview() {
        final EdgeBlendOperation operation = createOperation();
        profile.setOperation(operation);
        final int tilesToAdd = operation.getTilesToAdd(dimension).size();
        infoLabel.setText((tilesToAdd > 0)
                ? "<html>The map will be extended with <b>" + tilesToAdd + "</b> new tile" + ((tilesToAdd != 1) ? "s" : "") + " (" + (tilesToAdd * TILE_SIZE * TILE_SIZE / 1000) + "k blocks). Undo restores the heights but keeps the new tiles; remove them with the Add/Remove Tiles tool if needed.</html>"
                : "<html>The existing terrain near the edges will be faded; no tiles will be added.</html>");
    }

    /**
     * The average height along the outer edge of the map.
     */
    private static float averageEdgeHeight(Dimension dimension) {
        final Set<Point> coords = new HashSet<>(dimension.getTileCoords());
        double sum = 0;
        int count = 0;
        for (Point tileCoords: coords) {
            final Tile tile = dimension.getTile(tileCoords);
            final boolean west = ! coords.contains(new Point(tileCoords.x - 1, tileCoords.y)), east = ! coords.contains(new Point(tileCoords.x + 1, tileCoords.y));
            final boolean north = ! coords.contains(new Point(tileCoords.x, tileCoords.y - 1)), south = ! coords.contains(new Point(tileCoords.x, tileCoords.y + 1));
            for (int i = 0; i < TILE_SIZE; i += 4) {
                if (west) { sum += tile.getHeight(0, i); count++; }
                if (east) { sum += tile.getHeight(TILE_SIZE - 1, i); count++; }
                if (north) { sum += tile.getHeight(i, 0); count++; }
                if (south) { sum += tile.getHeight(i, TILE_SIZE - 1); count++; }
            }
        }
        return (count > 0) ? (float) (sum / count) : 64;
    }

    private final Dimension dimension;
    private final WorldPainterView view;
    private final ColourScheme colourScheme;
    private final Point centre;
    private final float edgeHeight;
    private final JSlider widthSlider, irregularitySlider;
    private final JSpinner widthSpinner, targetHeightSpinner, waterLevelSpinner;
    private final JComboBox<EdgeBlendOperation.Placement> placementComboBox;
    private final JComboBox<EdgeBlendOperation.Curve> curveComboBox;
    private final JComboBox<ProceduralTerrainSettings.Preset> presetComboBox;
    private final JComboBox<Terrain> targetTerrainComboBox;
    private final JRadioButton proceduralRadioButton, flatRadioButton;
    private final JCheckBox applyTerrainCheckBox, applyWaterCheckBox;
    private final JLabel targetLabel, infoLabel;
    private final ProfilePanel profile;
    private ProceduralTerrainSettings targetSettings;
    private boolean updating;

    private static int lastWidth = 192, lastIrregularity = 50, lastTargetHeight = 66;
    private static EdgeBlendOperation.Placement lastPlacement = EdgeBlendOperation.Placement.OUTSIDE;
    private static EdgeBlendOperation.Curve lastCurve = EdgeBlendOperation.Curve.SMOOTH;
    private static boolean lastFlatTarget;
    private static ProceduralTerrainSettings lastTargetSettings;

    private static final long serialVersionUID = 1L;

    /**
     * Draws a cross-section of the transition: the average height of the map edge on the left, the target on the
     * right, and the blended profile in between.
     */
    private class ProfilePanel extends JComponent {
        ProfilePanel() {
            setPreferredSize(new java.awt.Dimension(600, 190));
        }

        void setOperation(EdgeBlendOperation operation) {
            this.operation = operation;
            if (operation.getTarget() != null) {
                // Sample the target along a line to show its variation
                final ProceduralTerrain terrain = new ProceduralTerrain(operation.getTarget());
                targetSamples = new float[SAMPLES];
                for (int i = 0; i < SAMPLES; i++) {
                    targetSamples[i] = terrain.getHeight(centre.x + i * 4, centre.y);
                }
            } else {
                targetSamples = null;
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (operation == null) {
                return;
            }
            final Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                final Insets insets = getInsets();
                final int x0 = insets.left + 8, y0 = insets.top + 8, w = getWidth() - insets.left - insets.right - 16, h = getHeight() - insets.top - insets.bottom - 28;
                if ((w <= 0) || (h <= 0)) {
                    return;
                }
                final int width = operation.getWidth();
                final float outsideFraction = operation.getPlacement().getOutsideFraction();
                // Horizontal range: some original map, the transition, some target
                final float from = -(1 - outsideFraction) * width - width * 0.35f, to = outsideFraction * width + width * 0.35f;
                final float[] heights = new float[w];
                float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
                for (int px = 0; px < w; px++) {
                    final float position = from + (to - from) * px / (w - 1);
                    final float target = (targetSamples != null) ? targetSamples[Math.min(SAMPLES - 1, Math.max(0, (int) ((position - from) / 4)))] : operation.getTargetHeight();
                    final float weight = operation.originalWeight(-position);
                    heights[px] = target + (edgeHeight - target) * weight;
                    min = Math.min(min, heights[px]);
                    max = Math.max(max, heights[px]);
                }
                final int waterLevel = operation.isApplyWater() ? operation.getWaterLevel() : Integer.MIN_VALUE;
                min = Math.min(min, (waterLevel != Integer.MIN_VALUE) ? waterLevel : min) - 8;
                max = max + 8;
                final float range = Math.max(max - min, 16);
                // Transition zone
                final int zoneStart = x0 + Math.round((-(1 - outsideFraction) * width - from) / (to - from) * (w - 1));
                final int zoneEnd = x0 + Math.round((outsideFraction * width - from) / (to - from) * (w - 1));
                final Color accent = UIManager.getColor("Component.accentColor") != null ? UIManager.getColor("Component.accentColor") : new Color(0x3d9a50);
                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 40));
                g2.fillRect(zoneStart, y0, zoneEnd - zoneStart, h);
                // Water
                if (waterLevel != Integer.MIN_VALUE) {
                    final int wy = y0 + Math.round((max - waterLevel) / range * h);
                    g2.setColor(new Color(63, 118, 228, 90));
                    g2.fillRect(x0, wy, w, y0 + h - wy);
                }
                // Terrain
                final Path2D path = new Path2D.Float();
                path.moveTo(x0, y0 + h);
                for (int px = 0; px < w; px++) {
                    path.lineTo(x0 + px, y0 + (max - heights[px]) / range * h);
                }
                path.lineTo(x0 + w - 1, y0 + h);
                path.closePath();
                g2.setColor(new Color(0x6a8f3a));
                g2.fill(path);
                g2.setColor(new Color(0x3c5a1e));
                g2.draw(path);
                // Map edge
                final int edgeX = x0 + Math.round(-from / (to - from) * (w - 1));
                g2.setColor(getForeground());
                g2.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] {4, 4}, 0));
                g2.drawLine(edgeX, y0, edgeX, y0 + h);
                g2.setStroke(new BasicStroke(1));
                final FontMetrics fm = g2.getFontMetrics();
                g2.drawString("← current map", Math.max(x0, edgeX - fm.stringWidth("← current map") - 6), y0 + h + 16);
                g2.drawString("beyond the edge →", Math.min(x0 + w - fm.stringWidth("beyond the edge →"), edgeX + 6), y0 + h + 16);
                g2.drawString(Math.round(edgeHeight) + "", x0 + 2, y0 + fm.getAscent());
            } finally {
                g2.dispose();
            }
        }

        private EdgeBlendOperation operation;
        private float[] targetSamples;

        private static final int SAMPLES = 1024;
        private static final long serialVersionUID = 1L;
    }
}
