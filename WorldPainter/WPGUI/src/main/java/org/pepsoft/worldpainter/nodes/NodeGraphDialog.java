package org.pepsoft.worldpainter.nodes;

import com.formdev.flatlaf.FlatClientProperties;
import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.ProgressReceiver.OperationCancelled;
import org.pepsoft.util.swing.ProgressDialog;
import org.pepsoft.util.swing.ProgressTask;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.procedural.ProceduralTerrainOperation;
import org.pepsoft.worldpainter.util.HairlineBorder;
import org.pepsoft.worldpainter.util.Icons;
import org.pepsoft.worldpainter.util.ThemeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * A node based height map editor, in the spirit of World Machine or Gaia: the user wires generator, combiner and
 * filter nodes together, sees thumbnails of every node and a shaded preview of the result, and applies it to the whole
 * map or the selection. The graph is stored with the world.
 */
public class NodeGraphDialog extends WorldPainterDialog {
    public NodeGraphDialog(Window parent, Dimension dimension, WorldPainterView view, Point viewCentre, boolean selectionPresent) {
        super(parent);
        this.dimension = dimension;
        this.view = view;
        this.viewCentre = viewCentre;
        world = dimension.getWorld();
        mapBounds = NodeGraphOperation.getMapBounds(dimension);
        final TileFactory tileFactory = dimension.getTileFactory();
        waterLevel = (tileFactory instanceof HeightMapTileFactory) ? ((HeightMapTileFactory) tileFactory).getWaterHeight() : 62;
        final NodeGraph savedGraph = (world != null) ? world.getAttribute(NodeGraph.ATTRIBUTE_NODE_GRAPH).orElse(null) : null;
        graph = (savedGraph != null) ? savedGraph.clone() : createExample(ExampleGraphs.Example.ROLLING_HILLS, new Random().nextLong());
        setTitle("Node Graph Terrain");

        canvas = new NodeCanvas(graph);
        canvas.setListener(new NodeCanvas.Listener() {
            @Override
            public void selectionChanged(Node node) {
                propertiesPanel.setNode(node);
                if (previewSelectedButton.isSelected()) {
                    scheduleUpdate();
                }
            }

            @Override
            public void graphChanged(boolean structural) {
                modified = true;
                if (structural) {
                    scheduleUpdate();
                }
            }
        });
        propertiesPanel = new NodePropertiesPanel(node -> {
            modified = true;
            canvas.repaint();
            scheduleUpdate();
        });
        previewPanel = new HeightPreviewPanel();
        updateTimer = new Timer(150, e -> startUpdate());
        updateTimer.setRepeats(false);

        final JPanel content = new JPanel(new BorderLayout());
        content.add(createToolBar(), BorderLayout.NORTH);
        content.add(canvas, BorderLayout.CENTER);
        content.add(createSidebar(), BorderLayout.EAST);
        content.add(createBottomPanel(selectionPresent), BorderLayout.SOUTH);
        setContentPane(content);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveGraph();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                if (pending != null) {
                    pending.cancel(true);
                }
                executor.shutdownNow();
            }
        });

        scaleToUI();
        pack();
        setSize(new java.awt.Dimension(1320, 840));
        scaleWindowToUI();
        setLocationRelativeTo(parent);
        startUpdate();
    }

    /**
     * Closing the dialog keeps the edited graph with the world.
     */
    @Override
    protected void cancel() {
        saveGraph();
        super.cancel();
    }

    // UI construction

    private JComponent createToolBar() {
        final JPanel toolBar = new JPanel(new BorderLayout());
        toolBar.setBorder(new HairlineBorder(false, false, true, false, new Insets(6, 10, 6, 10)));
        final JPanel left = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 0));
        final JButton examplesButton = toolBarButton("Examples", "sparkles", "Start from a ready made graph");
        examplesButton.addActionListener(e -> {
            final JPopupMenu menu = new JPopupMenu();
            for (ExampleGraphs.Example example: ExampleGraphs.Example.values()) {
                final JMenuItem item = new JMenuItem(example.toString());
                item.addActionListener(event -> loadExample(example));
                menu.add(item);
            }
            menu.show(examplesButton, 0, examplesButton.getHeight());
        });
        left.add(examplesButton);
        final JButton addButton = toolBarButton("Add node", "plus", "Add a node; it is connected to the selected node if possible");
        addButton.addActionListener(e -> {
            final JPopupMenu menu = new JPopupMenu();
            for (NodeCategory category: new NodeCategory[] {NodeCategory.GENERATOR, NodeCategory.COMBINER, NodeCategory.FILTER}) {
                final JMenu submenu = new JMenu(category.toString());
                for (NodeType type: NodeType.getAll()) {
                    if (type.getCategory() == category) {
                        final JMenuItem item = new JMenuItem(type.getName());
                        item.setToolTipText(type.getDescription());
                        item.addActionListener(event -> canvas.addNode(type));
                        submenu.add(item);
                    }
                }
                menu.add(submenu);
            }
            menu.show(addButton, 0, addButton.getHeight());
        });
        left.add(addButton);
        left.add(Box.createHorizontalStrut(8));
        final JButton duplicateButton = toolBarButton(null, "copy", "Duplicate the selected node (\u2318D / Ctrl+D)");
        duplicateButton.addActionListener(e -> canvas.duplicateSelectedNode());
        left.add(duplicateButton);
        final JButton deleteButton = toolBarButton(null, "trash-2", "Delete the selected node (Delete)");
        deleteButton.addActionListener(e -> canvas.deleteSelectedNode());
        left.add(deleteButton);
        final JButton fitButton = toolBarButton(null, "expand", "Show all nodes (F)");
        fitButton.addActionListener(e -> canvas.fitView());
        left.add(fitButton);
        toolBar.add(left, BorderLayout.WEST);

        final JPanel right = new JPanel(new FlowLayout(FlowLayout.TRAILING, 4, 0));
        right.add(mutedLabel("Seed"));
        seedSpinner = new JSpinner(new SpinnerNumberModel(Long.valueOf(graph.getSeed()), Long.valueOf(Long.MIN_VALUE), Long.valueOf(Long.MAX_VALUE), Long.valueOf(1L)));
        seedSpinner.setEditor(new JSpinner.NumberEditor(seedSpinner, "#"));
        ((JSpinner.DefaultEditor) seedSpinner.getEditor()).getTextField().setColumns(14);
        seedSpinner.setToolTipText("All generators derive their patterns from this seed");
        seedSpinner.addChangeListener(e -> {
            final long seed = ((Number) seedSpinner.getValue()).longValue();
            if (seed != graph.getSeed()) {
                graph.setSeed(seed);
                modified = true;
                scheduleUpdate();
            }
        });
        right.add(seedSpinner);
        final JButton randomButton = toolBarButton(null, "shuffle", "Pick a random seed");
        randomButton.addActionListener(e -> seedSpinner.setValue(new Random().nextLong()));
        right.add(randomButton);
        toolBar.add(right, BorderLayout.EAST);
        return toolBar;
    }

    private JComponent createSidebar() {
        final JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(ThemeUtils.getColour("WorldPainter.sidebarBackground", sidebar.getBackground()));
        sidebar.putClientProperty(FlatClientProperties.STYLE, "background: $WorldPainter.sidebarBackground");
        sidebar.setBorder(new HairlineBorder(false, true, false, false));
        sidebar.setPreferredSize(new java.awt.Dimension(380, 600));

        final JPanel previewSection = new JPanel(new BorderLayout(0, 8));
        previewSection.setOpaque(false);
        previewSection.setBorder(new HairlineBorder(false, false, true, false, new Insets(12, 14, 12, 14)));
        final JPanel previewHeader = new JPanel(new BorderLayout());
        previewHeader.setOpaque(false);
        previewHeader.add(sectionLabel("Preview"), BorderLayout.WEST);
        final JPanel toggles = new JPanel(new FlowLayout(FlowLayout.TRAILING, 2, 0));
        toggles.setOpaque(false);
        final JToggleButton previewOutputButton = new JToggleButton("Output", true);
        previewSelectedButton = new JToggleButton("Selected node");
        previewOutputButton.setToolTipText("Preview the terrain produced by the output node");
        previewSelectedButton.setToolTipText("Preview the result of the selected node");
        final ButtonGroup group = new ButtonGroup();
        for (JToggleButton button: new JToggleButton[] {previewOutputButton, previewSelectedButton}) {
            button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
            button.addActionListener(e -> scheduleUpdate());
            group.add(button);
            toggles.add(button);
        }
        previewHeader.add(toggles, BorderLayout.EAST);
        previewSection.add(previewHeader, BorderLayout.NORTH);
        previewSection.add(previewPanel, BorderLayout.CENTER);
        final JPanel previewFooter = new JPanel(new BorderLayout(8, 0));
        previewFooter.setOpaque(false);
        areaComboBox = new JComboBox<>();
        if (mapBounds != null) {
            areaComboBox.addItem("Whole map");
        }
        for (int area = 512; area <= 16384; area *= 2) {
            areaComboBox.addItem(String.format("%,d blocks", area));
        }
        areaComboBox.setSelectedIndex((mapBounds != null) ? 0 : 2);
        areaComboBox.setToolTipText("The area shown in the preview; fixed sizes are centred on the view");
        areaComboBox.addActionListener(e -> scheduleUpdate());
        previewFooter.add(areaComboBox, BorderLayout.WEST);
        rangeLabel = mutedLabel(" ");
        previewFooter.add(rangeLabel, BorderLayout.CENTER);
        previewSection.add(previewFooter, BorderLayout.SOUTH);
        sidebar.add(previewSection, BorderLayout.NORTH);

        final JScrollPane scrollPane = new JScrollPane(propertiesPanel, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        sidebar.add(scrollPane, BorderLayout.CENTER);
        return sidebar;
    }

    private JComponent createBottomPanel(boolean selectionPresent) {
        final JPanel panel = new JPanel(new BorderLayout(12, 0));
        panel.setBorder(new HairlineBorder(true, false, false, false, new Insets(10, 12, 10, 12)));
        final JPanel options = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 0));
        options.add(mutedLabel("Mode"));
        modeComboBox = new JComboBox<>(ProceduralTerrainOperation.Mode.values());
        modeComboBox.setToolTipText("<html><b>Replace</b>: replace the terrain height<br><b>Add relief</b>: add the generated relief (relative to the output's height at 0) to the existing terrain<br><b>Raise only</b> / <b>Lower only</b>: only change the terrain where the generated terrain is higher / lower</html>");
        options.add(modeComboBox);
        options.add(Box.createHorizontalStrut(8));
        options.add(mutedLabel("Strength"));
        strengthSlider = new JSlider(0, 100, 100);
        strengthSlider.setPreferredSize(new java.awt.Dimension(110, strengthSlider.getPreferredSize().height));
        final JLabel strengthLabel = new JLabel("100%");
        strengthSlider.addChangeListener(e -> strengthLabel.setText(strengthSlider.getValue() + "%"));
        options.add(strengthSlider);
        options.add(strengthLabel);
        options.add(Box.createHorizontalStrut(8));
        final JRadioButton wholeMapRadioButton = new JRadioButton("Whole map", true);
        selectionRadioButton = new JRadioButton("Selection, feathered");
        selectionRadioButton.setEnabled(selectionPresent);
        selectionRadioButton.setSelected(selectionPresent);
        final ButtonGroup group = new ButtonGroup();
        group.add(wholeMapRadioButton);
        group.add(selectionRadioButton);
        options.add(wholeMapRadioButton);
        options.add(selectionRadioButton);
        featherSpinner = new JSpinner(new SpinnerNumberModel(32, 0, 512, 4));
        featherSpinner.setToolTipText("The width in blocks over which the effect fades out at the edges of the selection");
        options.add(featherSpinner);
        options.add(mutedLabel("blocks"));
        options.add(Box.createHorizontalStrut(8));
        applyThemeCheckBox = new JCheckBox("Repaint terrain types", true);
        applyThemeCheckBox.setToolTipText("Paint the terrain types (beaches, grass, rock, snow, ...) according to the new heights, as for a new map");
        options.add(applyThemeCheckBox);
        panel.add(options, BorderLayout.CENTER);

        final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING, 6, 0));
        final JButton closeButton = new JButton("Close");
        closeButton.setToolTipText("Close without changing the map; the graph is kept with the world");
        closeButton.addActionListener(e -> cancel());
        final JButton applyButton = new JButton("Apply to map", Icons.get("wand-sparkles", 14));
        applyButton.addActionListener(e -> apply());
        buttons.add(closeButton);
        buttons.add(applyButton);
        getRootPane().setDefaultButton(applyButton);
        panel.add(buttons, BorderLayout.EAST);
        return panel;
    }

    private static JButton toolBarButton(String text, String icon, String tooltip) {
        final JButton button = new JButton(text, Icons.get(icon, 16));
        button.setToolTipText(tooltip);
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        return button;
    }

    private static JLabel mutedLabel(String text) {
        final JLabel label = new JLabel(text);
        label.putClientProperty(FlatClientProperties.STYLE, "foreground: $WorldPainter.mutedForeground");
        return label;
    }

    private static JLabel sectionLabel(String text) {
        final JLabel label = new JLabel(text);
        label.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        return label;
    }

    // Behaviour

    private NodeGraph createExample(ExampleGraphs.Example example, long seed) {
        return ExampleGraphs.create(example, seed, waterLevel, dimension.getMinHeight(), dimension.getMaxHeight());
    }

    private void loadExample(ExampleGraphs.Example example) {
        if (modified && (JOptionPane.showConfirmDialog(this, "Replace the current graph with the \"" + example + "\" example?", "Replace Graph", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION)) {
            return;
        }
        graph = createExample(example, graph.getSeed());
        canvas.setGraph(graph);
        modified = true;
        startUpdate();
    }

    private void scheduleUpdate() {
        updateTimer.restart();
    }

    /**
     * Render the preview and the thumbnails of a copy of the graph in the background, cancelling any render in
     * progress.
     */
    private void startUpdate() {
        updateTimer.stop();
        if (pending != null) {
            pending.cancel(true);
        }
        final NodeGraph snapshot = graph.clone();
        final Node selected = canvas.getSelectedNode();
        final int previewNodeId = (previewSelectedButton.isSelected() && (selected != null)) ? selected.getId() : snapshot.getOutputNode().getId();
        final boolean outputConnected = snapshot.getInput(snapshot.getOutputNode(), 0) != null;
        final Region region = getPreviewRegion();
        final Region thumbnailRegion = new Region(region.getX0(), region.getY0(), region.getStep() * PREVIEW_SIZE / THUMBNAIL_SIZE, THUMBNAIL_SIZE, THUMBNAIL_SIZE);
        final Rectangle bounds = mapBounds;
        previewPanel.setRendering(true);
        pending = executor.submit(() -> {
            try {
                final CompiledGraph compiled = snapshot.compile(bounds);
                if ((previewNodeId == snapshot.getOutputNode().getId()) && (! outputConnected)) {
                    SwingUtilities.invokeLater(() -> {
                        previewPanel.setMessage("Connect a node to the output");
                        previewPanel.setRendering(false);
                        rangeLabel.setText(" ");
                    });
                } else {
                    final float[] heights = toHeights(compiled, compiled.evaluate(compiled.createEvaluation(), previewNodeId, region));
                    float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
                    for (float height: heights) {
                        min = Math.min(min, height);
                        max = Math.max(max, height);
                    }
                    final BufferedImage image = HeightPreviewPanel.renderRelief(heights, PREVIEW_SIZE, region.getStep(), waterLevel);
                    final int lowest = Math.round(min), highest = Math.round(max);
                    SwingUtilities.invokeLater(() -> {
                        previewPanel.setMessage(null);
                        previewPanel.setImage(image);
                        previewPanel.setRendering(false);
                        updateRangeLabel(lowest, highest);
                    });
                }
                final Evaluation evaluation = compiled.createEvaluation();
                final Map<Integer, BufferedImage> thumbnails = new HashMap<>();
                for (Node node: snapshot.getNodes()) {
                    if (node instanceof OutputNode) {
                        if (outputConnected) {
                            final float[] heights = toHeights(compiled, compiled.evaluate(evaluation, node.getId(), thumbnailRegion));
                            thumbnails.put(node.getId(), HeightPreviewPanel.renderRelief(heights, THUMBNAIL_SIZE, thumbnailRegion.getStep(), waterLevel));
                        }
                    } else {
                        thumbnails.put(node.getId(), HeightPreviewPanel.renderGreyscale(compiled.evaluate(evaluation, node.getId(), thumbnailRegion), THUMBNAIL_SIZE));
                    }
                }
                SwingUtilities.invokeLater(() -> canvas.setThumbnails(thumbnails));
            } catch (EvaluationCancelledException e) {
                // Superseded by a newer render
            } catch (RuntimeException | Error e) {
                logger.error("Exception while rendering node graph preview", e);
                SwingUtilities.invokeLater(() -> {
                    previewPanel.setMessage("Error: " + e.getMessage());
                    previewPanel.setRendering(false);
                });
            }
        });
    }

    private static float[] toHeights(CompiledGraph compiled, float[] values) {
        for (int i = 0; i < values.length; i++) {
            values[i] = compiled.toHeight(values[i]);
        }
        return values;
    }

    private Region getPreviewRegion() {
        final String item = (String) areaComboBox.getSelectedItem();
        final double area;
        final double centreX, centreY;
        if ("Whole map".equals(item)) {
            area = Math.max(mapBounds.width, mapBounds.height);
            centreX = mapBounds.getCenterX();
            centreY = mapBounds.getCenterY();
        } else {
            area = Integer.parseInt(item.replaceAll("[^0-9]", ""));
            centreX = viewCentre.x;
            centreY = viewCentre.y;
        }
        final double step = area / PREVIEW_SIZE;
        return new Region(centreX - area / 2, centreY - area / 2, step, PREVIEW_SIZE, PREVIEW_SIZE);
    }

    private void updateRangeLabel(int min, int max) {
        final boolean clipped = (min < dimension.getMinHeight()) || (max >= dimension.getMaxHeight());
        rangeLabel.setText("<html>Heights " + min + " to " + max + (clipped ? " <font color=\"#e05050\">(beyond the map limits " + dimension.getMinHeight() + " to " + (dimension.getMaxHeight() - 1) + ")</font>" : "") + "</html>");
    }

    private void saveGraph() {
        if (modified && (world != null)) {
            world.setAttribute(NodeGraph.ATTRIBUTE_NODE_GRAPH, graph.clone());
            modified = false;
        }
    }

    private void apply() {
        saveGraph();
        if (pending != null) {
            pending.cancel(true);
        }
        final NodeGraphOperation operation = new NodeGraphOperation(graph);
        operation.setMode((ProceduralTerrainOperation.Mode) modeComboBox.getSelectedItem());
        operation.setStrength(strengthSlider.getValue() / 100f);
        operation.setSelectionOnly(selectionRadioButton.isSelected());
        operation.setFeather((Integer) featherSpinner.getValue());
        operation.setApplyTheme(applyThemeCheckBox.isSelected());
        dimension.setEventsInhibited(true);
        dimension.rememberChanges();
        if (view != null) {
            view.setInhibitUpdates(true);
        }
        try {
            final Dimension result = ProgressDialog.executeTask(this, new ProgressTask<Dimension>() {
                @Override
                public String getName() {
                    return "Generating terrain from node graph";
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

    private final Dimension dimension;
    private final World2 world;
    private final WorldPainterView view;
    private final Point viewCentre;
    private final Rectangle mapBounds;
    private final int waterLevel;
    private final NodeCanvas canvas;
    private final NodePropertiesPanel propertiesPanel;
    private final HeightPreviewPanel previewPanel;
    private final Timer updateTimer;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "Node graph preview");
        thread.setDaemon(true);
        return thread;
    });
    private NodeGraph graph;
    private Future<?> pending;
    private boolean modified;
    private JSpinner seedSpinner, featherSpinner;
    private JToggleButton previewSelectedButton;
    private JComboBox<String> areaComboBox;
    private JLabel rangeLabel;
    private JComboBox<ProceduralTerrainOperation.Mode> modeComboBox;
    private JSlider strengthSlider;
    private JRadioButton selectionRadioButton;
    private JCheckBox applyThemeCheckBox;

    private static final int PREVIEW_SIZE = 384, THUMBNAIL_SIZE = 96;
    private static final Logger logger = LoggerFactory.getLogger(NodeGraphDialog.class);
    private static final long serialVersionUID = 1L;
}
