package org.pepsoft.worldpainter.nodes;

import com.formdev.flatlaf.FlatClientProperties;
import org.pepsoft.util.DesktopUtils;
import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.ProgressReceiver.OperationCancelled;
import org.pepsoft.util.swing.ProgressDialog;
import org.pepsoft.util.swing.ProgressTask;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.layers.CustomLayer;
import org.pepsoft.worldpainter.procedural.ProceduralTerrainOperation;
import org.pepsoft.worldpainter.util.HairlineBorder;
import org.pepsoft.worldpainter.util.Icons;
import org.pepsoft.worldpainter.util.ThemeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.pepsoft.util.swing.MessageUtils.showWarning;

/**
 * A node based terrain editor, in the spirit of World Machine or Gaia: the user wires generator, combiner, filter,
 * mask and surface nodes together, sees thumbnails of every node and a preview of the relief, the surface or the biomes,
 * and applies the result to the whole map or the selection. The graph is stored with the world, and can be saved to
 * and loaded from files.
 */
public class NodeGraphDialog extends WorldPainterDialog {
    /**
     * @param existingLayers The custom layers of the world, so that plant layers created earlier can be reused.
     */
    public NodeGraphDialog(Window parent, Dimension dimension, WorldPainterView view, ColourScheme colourScheme, Point viewCentre, boolean selectionPresent, Collection<? extends CustomLayer> existingLayers) {
        super(parent);
        this.dimension = dimension;
        this.view = view;
        this.colourScheme = colourScheme;
        this.viewCentre = viewCentre;
        this.existingLayers = new ArrayList<>(existingLayers);
        world = dimension.getWorld();
        platform = world.getPlatform();
        mapBounds = NodeGraphOperation.getMapBounds(dimension);
        mapSource = new NodeGraphOperation.DimensionMapSource(dimension);
        final TileFactory tileFactory = dimension.getTileFactory();
        waterLevel = (tileFactory instanceof HeightMapTileFactory) ? ((HeightMapTileFactory) tileFactory).getWaterHeight() : 62;
        HeightPreviewPanel.setSeaLevel(waterLevel);
        final NodeGraph savedGraph = world.getAttribute(NodeGraph.ATTRIBUTE_NODE_GRAPH).orElse(null);
        graph = (savedGraph != null) ? savedGraph.clone() : createExample(ExampleGraphs.Example.ROLLING_HILLS, new Random().nextLong());
        committed = graph.clone();
        setTitle("Node Graph Terrain");

        canvas = new NodeCanvas(graph);
        canvas.setListener(new NodeCanvas.Listener() {
            @Override
            public void selectionChanged(Node node) {
                propertiesPanel.setNode(node);
                if (previewMode == PreviewMode.NODE) {
                    scheduleUpdate();
                }
            }

            @Override
            public void graphChanged(boolean structural) {
                recordChange(null);
                if (structural) {
                    scheduleUpdate();
                }
            }
        });
        propertiesPanel = new NodePropertiesPanel((node, parameter) -> {
            recordChange(node.getId() + ":" + parameter.getKey());
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
        installKeyBindings();

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
        setSize(new java.awt.Dimension(1360, 860));
        scaleWindowToUI();
        setLocationRelativeTo(parent);
        updateUndoButtons();
        startUpdate();
    }

    /**
     * The plant layers which were created when the graph was applied, which should be added to the user's layers.
     */
    public List<CustomLayer> getCreatedLayers() {
        return createdLayers;
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
        final JButton fileButton = toolBarButton("File", "folder-open", "Start a new graph, or open or save a graph file to reuse it in other worlds");
        fileButton.addActionListener(e -> {
            final JPopupMenu menu = new JPopupMenu();
            final JMenuItem newItem = new JMenuItem("New empty graph", Icons.get("file-plus"));
            newItem.addActionListener(event -> {
                final NodeGraph empty = new NodeGraph();
                empty.setName("Untitled");
                empty.setSeed(graph.getSeed());
                final OutputNode output = empty.getOutputNode();
                output.set(OutputNode.MIN_HEIGHT, Math.max(dimension.getMinHeight(), waterLevel - 30));
                output.set(OutputNode.MAX_HEIGHT, Math.min(dimension.getMaxHeight() - 1, waterLevel + 120));
                replaceGraph(empty, "Replace the current graph with an empty one?");
            });
            menu.add(newItem);
            final JMenuItem openItem = new JMenuItem("Open graph\u2026", Icons.get("folder-open"));
            openItem.addActionListener(event -> openGraph());
            menu.add(openItem);
            final JMenuItem saveItem = new JMenuItem("Save graph as\u2026", Icons.get("save"));
            saveItem.addActionListener(event -> saveGraphAs());
            menu.add(saveItem);
            menu.show(fileButton, 0, fileButton.getHeight());
        });
        left.add(fileButton);
        final JButton examplesButton = toolBarButton("Examples", "sparkles", "Start from a ready made landscape");
        examplesButton.addActionListener(e -> {
            final JPopupMenu menu = new JPopupMenu();
            for (ExampleGraphs.Example example: ExampleGraphs.Example.values()) {
                final JMenuItem item = new JMenuItem(example.toString());
                item.addActionListener(event -> replaceGraph(createExample(example, graph.getSeed()), "Replace the current graph with the \"" + example + "\" example?"));
                menu.add(item);
            }
            menu.show(examplesButton, 0, examplesButton.getHeight());
        });
        left.add(examplesButton);
        final JButton addButton = toolBarButton("Add node", "plus", "Add a node; it is connected to the selected node if possible");
        addButton.addActionListener(e -> {
            final JPopupMenu menu = new JPopupMenu();
            for (NodeCategory category: NodeCategory.values()) {
                if (category == NodeCategory.OUTPUT) {
                    continue;
                }
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
        undoButton = toolBarButton(null, "undo-2", "Undo (\u2318Z / Ctrl+Z)");
        undoButton.addActionListener(e -> undo());
        left.add(undoButton);
        redoButton = toolBarButton(null, "redo-2", "Redo (\u21e7\u2318Z / Ctrl+Y)");
        redoButton.addActionListener(e -> redo());
        left.add(redoButton);
        left.add(Box.createHorizontalStrut(8));
        final JButton duplicateButton = toolBarButton(null, "copy", "Duplicate the selected nodes (\u2318D / Ctrl+D)");
        duplicateButton.addActionListener(e -> canvas.duplicateSelection());
        left.add(duplicateButton);
        final JButton deleteButton = toolBarButton(null, "trash-2", "Delete the selected nodes (Delete)");
        deleteButton.addActionListener(e -> canvas.deleteSelection());
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
                recordChange("seed");
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
        sidebar.setPreferredSize(new java.awt.Dimension(390, 600));

        final JPanel previewSection = new JPanel(new BorderLayout(0, 8));
        previewSection.setOpaque(false);
        previewSection.setBorder(new HairlineBorder(false, false, true, false, new Insets(12, 14, 12, 14)));
        final JPanel previewHeader = new JPanel(new BorderLayout());
        previewHeader.setOpaque(false);
        previewHeader.add(sectionLabel("Preview"), BorderLayout.WEST);
        final JPanel toggles = new JPanel(new FlowLayout(FlowLayout.TRAILING, 1, 0));
        toggles.setOpaque(false);
        final ButtonGroup group = new ButtonGroup();
        for (PreviewMode mode: PreviewMode.values()) {
            final JToggleButton button = new JToggleButton(mode.label, mode == previewMode);
            button.setToolTipText(mode.description);
            button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
            button.addActionListener(e -> {
                previewMode = mode;
                scheduleUpdate();
            });
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
        options.add(mutedLabel("Strength"));
        strengthSlider = new JSlider(0, 100, 100);
        strengthSlider.setPreferredSize(new java.awt.Dimension(90, strengthSlider.getPreferredSize().height));
        final JLabel strengthLabel = new JLabel("100%");
        strengthSlider.addChangeListener(e -> strengthLabel.setText(strengthSlider.getValue() + "%"));
        options.add(strengthSlider);
        options.add(strengthLabel);
        options.add(Box.createHorizontalStrut(6));
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
        options.add(Box.createHorizontalStrut(6));
        applyThemeCheckBox = new JCheckBox("Repaint from theme", true);
        applyThemeCheckBox.setToolTipText("Paint the terrain types (beaches, grass, rock, snow, ...) according to the new heights, as for a new map, wherever no terrain painter of the graph wins");
        options.add(applyThemeCheckBox);
        applyPaintersCheckBox = new JCheckBox("Paint surface", true);
        applyPaintersCheckBox.setToolTipText("Apply the terrain, biome and flora painters of the graph");
        options.add(applyPaintersCheckBox);
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

    private void installKeyBindings() {
        final InputMap inputMap = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        final ActionMap actionMap = getRootPane().getActionMap();
        final int menuShortcut = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuShortcut), "undoGraph");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuShortcut | KeyEvent.SHIFT_DOWN_MASK), "redoGraph");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, menuShortcut), "redoGraph");
        actionMap.put("undoGraph", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                undo();
            }

            private static final long serialVersionUID = 1L;
        });
        actionMap.put("redoGraph", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                redo();
            }

            private static final long serialVersionUID = 1L;
        });
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

    /**
     * Replace the whole graph, as one undoable change.
     */
    private void replaceGraph(NodeGraph newGraph, String question) {
        if ((graph.getNodes().size() > 1) && (JOptionPane.showConfirmDialog(this, question + "\nYou can undo this.", "Replace Graph", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION)) {
            return;
        }
        graph = newGraph;
        canvas.setGraph(graph);
        seedSpinner.setValue(graph.getSeed());
        recordChange(null);
        startUpdate();
    }

    private void openGraph() {
        final JFileChooser fileChooser = createFileChooser();
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            final File file = fileChooser.getSelectedFile();
            lastGraphDir = file.getParentFile();
            try {
                replaceGraph(NodeGraphIO.load(file), "Replace the current graph with " + file.getName() + "?");
            } catch (IOException e) {
                logger.error("Could not load node graph from " + file, e);
                showWarning(this, "Could not open " + file.getName() + ": " + e.getMessage(), "Error Opening Graph");
            }
        }
    }

    private void saveGraphAs() {
        final JFileChooser fileChooser = createFileChooser();
        if (graph.getName() != null) {
            fileChooser.setSelectedFile(new File(lastGraphDir, graph.getName() + "." + NodeGraphIO.EXTENSION));
        }
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (! file.getName().toLowerCase().endsWith("." + NodeGraphIO.EXTENSION)) {
                file = new File(file.getParentFile(), file.getName() + "." + NodeGraphIO.EXTENSION);
            }
            if (file.exists() && (JOptionPane.showConfirmDialog(this, file.getName() + " already exists. Replace it?", "Replace File", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION)) {
                return;
            }
            lastGraphDir = file.getParentFile();
            try {
                final String name = file.getName();
                graph.setName(name.substring(0, name.length() - NodeGraphIO.EXTENSION.length() - 1));
                NodeGraphIO.save(graph, file);
            } catch (IOException e) {
                logger.error("Could not save node graph to " + file, e);
                showWarning(this, "Could not save " + file.getName() + ": " + e.getMessage(), "Error Saving Graph");
            }
        }
    }

    private static JFileChooser createFileChooser() {
        final JFileChooser fileChooser = new JFileChooser(lastGraphDir);
        fileChooser.setFileFilter(new FileNameExtensionFilter("WorldPainter node graphs (*." + NodeGraphIO.EXTENSION + ")", NodeGraphIO.EXTENSION));
        return fileChooser;
    }

    /**
     * Remember the state after a change for undo. Consecutive changes with the same non-{@code null} key in quick
     * succession (such as dragging a slider) are combined into one undo step.
     */
    private void recordChange(Object key) {
        modified = true;
        final long now = System.currentTimeMillis();
        if ((key == null) || (! key.equals(lastChangeKey)) || (now - lastChangeTime > COALESCE_INTERVAL)) {
            undoStack.push(committed);
            while (undoStack.size() > MAX_UNDO) {
                undoStack.removeLast();
            }
        }
        committed = graph.clone();
        redoStack.clear();
        lastChangeKey = key;
        lastChangeTime = now;
        updateUndoButtons();
    }

    private void undo() {
        if (undoStack.isEmpty()) {
            DesktopUtils.beep();
            return;
        }
        redoStack.push(graph.clone());
        restore(undoStack.pop());
    }

    private void redo() {
        if (redoStack.isEmpty()) {
            DesktopUtils.beep();
            return;
        }
        undoStack.push(graph.clone());
        restore(redoStack.pop());
    }

    private void restore(NodeGraph state) {
        committed = state;
        graph = state.clone();
        lastChangeKey = null;
        modified = true;
        canvas.setGraph(graph, false);
        propertiesPanel.setNode(canvas.getSelectedNode());
        if (((Number) seedSpinner.getValue()).longValue() != graph.getSeed()) {
            seedSpinner.setValue(graph.getSeed());
        }
        updateUndoButtons();
        startUpdate();
    }

    private void updateUndoButtons() {
        undoButton.setEnabled(! undoStack.isEmpty());
        redoButton.setEnabled(! redoStack.isEmpty());
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
        final PreviewMode mode = previewMode;
        final int outputId = snapshot.getOutputNode().getId();
        final int previewNodeId = ((mode == PreviewMode.NODE) && (selected != null) && selected.hasOutput()) ? selected.getId() : outputId;
        final boolean outputConnected = snapshot.getInput(snapshot.getOutputNode(), 0) != null;
        final Region region = getPreviewRegion();
        final Region thumbnailRegion = new Region(region.getX0(), region.getY0(), region.getStep() * PREVIEW_SIZE / THUMBNAIL_SIZE, THUMBNAIL_SIZE, THUMBNAIL_SIZE);
        final Rectangle bounds = mapBounds;
        final long seed = dimension.getSeed();
        previewPanel.setRendering(true);
        pending = executor.submit(() -> {
            try {
                final CompiledGraph compiled = snapshot.compile(bounds, mapSource);
                final Evaluation evaluation = compiled.createEvaluation();
                if ((previewNodeId == outputId) && (! outputConnected)) {
                    SwingUtilities.invokeLater(() -> {
                        previewPanel.setMessage("Connect a node to the output");
                        previewPanel.setRendering(false);
                        rangeLabel.setText(" ");
                    });
                } else {
                    final float[] heights = toHeights(compiled, compiled.evaluate(evaluation, previewNodeId, region));
                    float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
                    for (float height: heights) {
                        min = Math.min(min, height);
                        max = Math.max(max, height);
                    }
                    final float[] waterLevels = getWaterLevels(compiled, evaluation, region);
                    final BufferedImage image;
                    if ((mode == PreviewMode.SURFACE) || (mode == PreviewMode.BIOMES)) {
                        final List<Painter> painters = compiled.getPainters();
                        final float[][] strengths = new float[painters.size()][];
                        for (int p = 0; p < painters.size(); p++) {
                            strengths[p] = painters.get(p).evaluate(evaluation, region);
                        }
                        image = HeightPreviewPanel.renderSurface(heights, region, waterLevels, painters, strengths, mode == PreviewMode.BIOMES, colourScheme, platform, seed);
                    } else {
                        image = HeightPreviewPanel.renderRelief(heights, PREVIEW_SIZE, region.getStep(), waterLevels);
                    }
                    final int lowest = Math.round(min), highest = Math.round(max);
                    SwingUtilities.invokeLater(() -> {
                        previewPanel.setMessage(null);
                        previewPanel.setImage(image);
                        previewPanel.setRendering(false);
                        updateRangeLabel(lowest, highest);
                    });
                }
                final Evaluation thumbnailEvaluation = compiled.createEvaluation();
                final Map<Integer, BufferedImage> thumbnails = new HashMap<>();
                for (Node node: snapshot.getNodes()) {
                    if (node instanceof OutputNode) {
                        if (outputConnected) {
                            final float[] heights = toHeights(compiled, compiled.evaluate(thumbnailEvaluation, node.getId(), thumbnailRegion));
                            thumbnails.put(node.getId(), HeightPreviewPanel.renderRelief(heights, THUMBNAIL_SIZE, thumbnailRegion.getStep(), getWaterLevels(compiled, thumbnailEvaluation, thumbnailRegion)));
                        }
                    } else if (node instanceof PainterNode) {
                        final float[] strengths = compiled.evaluate(thumbnailEvaluation, node.getId(), thumbnailRegion);
                        thumbnails.put(node.getId(), HeightPreviewPanel.renderTinted(strengths, THUMBNAIL_SIZE, getColour(((PainterNode) node).getTarget(), seed)));
                    } else {
                        thumbnails.put(node.getId(), HeightPreviewPanel.renderGreyscale(compiled.evaluate(thumbnailEvaluation, node.getId(), thumbnailRegion), THUMBNAIL_SIZE));
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

    /**
     * The water level of each sample of a region: the sea level, or the level of the rivers and lakes of the graph
     * where that is higher.
     */
    private float[] getWaterLevels(CompiledGraph compiled, Evaluation evaluation, Region region) {
        final float[] levels = new float[region.size()];
        Arrays.fill(levels, waterLevel);
        WaterBody.combine(compiled.getWaterBodies(), evaluation, region, levels);
        return levels;
    }

    private int getColour(Object target, long seed) {
        if (target instanceof Terrain) {
            return ((Terrain) target).getColour(seed, 0, 0, waterLevel + 8, waterLevel + 8, platform, colourScheme);
        } else if (target instanceof MinecraftBiome) {
            return ((MinecraftBiome) target).getColour();
        } else if (target instanceof Vegetation) {
            return ((Vegetation) target).getColour();
        } else if (target instanceof org.pepsoft.worldpainter.MaterialGroup) {
            return ((org.pepsoft.worldpainter.MaterialGroup) target).getColour();
        } else {
            return 0xffffff;
        }
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
        if (modified) {
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
        operation.setApplyPainters(applyPaintersCheckBox.isSelected());
        operation.setExistingLayers(existingLayers);
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
                createdLayers.addAll(operation.getCreatedLayers());
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
    private final Platform platform;
    private final WorldPainterView view;
    private final ColourScheme colourScheme;
    private final Point viewCentre;
    private final Rectangle mapBounds;
    private final MapSource mapSource;
    private final int waterLevel;
    private final List<CustomLayer> existingLayers, createdLayers = new ArrayList<>();
    private final NodeCanvas canvas;
    private final NodePropertiesPanel propertiesPanel;
    private final HeightPreviewPanel previewPanel;
    private final Timer updateTimer;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "Node graph preview");
        thread.setDaemon(true);
        return thread;
    });
    private final Deque<NodeGraph> undoStack = new ArrayDeque<>(), redoStack = new ArrayDeque<>();
    private NodeGraph graph, committed;
    private Object lastChangeKey;
    private long lastChangeTime;
    private Future<?> pending;
    private boolean modified;
    private PreviewMode previewMode = PreviewMode.RELIEF;
    private JButton undoButton, redoButton;
    private JSpinner seedSpinner, featherSpinner;
    private JComboBox<String> areaComboBox;
    private JLabel rangeLabel;
    private JComboBox<ProceduralTerrainOperation.Mode> modeComboBox;
    private JSlider strengthSlider;
    private JRadioButton selectionRadioButton;
    private JCheckBox applyThemeCheckBox, applyPaintersCheckBox;

    private static File lastGraphDir;

    private static final int PREVIEW_SIZE = 384, THUMBNAIL_SIZE = 96, MAX_UNDO = 100;
    private static final long COALESCE_INTERVAL = 1500;
    private static final Logger logger = LoggerFactory.getLogger(NodeGraphDialog.class);
    private static final long serialVersionUID = 1L;

    private enum PreviewMode {
        RELIEF("Relief", "Shaded relief of the terrain"),
        SURFACE("Surface", "The terrain types and vegetation painted by the graph"),
        BIOMES("Biomes", "The biomes painted by the graph (grey: chosen automatically)"),
        NODE("Node", "The result of the selected node");

        PreviewMode(String label, String description) {
            this.label = label;
            this.description = description;
        }

        private final String label, description;
    }
}
