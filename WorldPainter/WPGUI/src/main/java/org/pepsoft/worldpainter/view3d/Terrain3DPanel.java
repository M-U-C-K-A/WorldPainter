package org.pepsoft.worldpainter.view3d;

import org.pepsoft.util.SubProgressReceiver;

import org.pepsoft.util.ProgressReceiver;

import com.formdev.flatlaf.FlatClientProperties;
import org.pepsoft.worldpainter.App;
import org.pepsoft.worldpainter.ColourScheme;
import org.pepsoft.worldpainter.Constants;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.biomeschemes.Minecraft1_21Biomes;
import org.pepsoft.worldpainter.layers.Biome;
import org.pepsoft.worldpainter.layers.Layer;
import org.pepsoft.worldpainter.util.HairlineBorder;
import org.pepsoft.worldpainter.util.WrapLayout;
import org.pepsoft.worldpainter.util.Icons;
import org.pepsoft.worldpainter.util.ThemeUtils;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.pepsoft.worldpainter.Constants.TILE_SIZE_BITS;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE_MASK;

/**
 * The 3D view of the map, integrated in the main window: a perspective or orthographic view of the terrain with several
 * colour modes (terrain, height, slope, biome, relief), water and contour line overlays, a block by block preview
 * around the camera, optional sun lighting with shadows and moving water, preset view directions, and a card showing
 * everything that is known about the block under the mouse.
 */
public class Terrain3DPanel extends JPanel {
    public Terrain3DPanel() {
        super(new BorderLayout());
        view = new Terrain3DView();
        view.setHoverListener(this::hoverChanged);
        view.setTargetListener(this::requestDetail);
        infoCard = new InfoCard();
        legend = new Legend();

        // The buttons in groups (the kinds of view, what to show, the camera, and refreshing), which go on to another
        // row when the area is too narrow for all of them
        final JToolBar modes = createGroup(), display = createGroup(), camera = createGroup(), actions = createGroup();
        final ButtonGroup modeGroup = new ButtonGroup();
        for (Terrain3DView.Mode mode: Terrain3DView.Mode.values()) {
            final JToggleButton button = new JToggleButton(mode.displayName, mode == Terrain3DView.Mode.TERRAIN);
            button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
            button.setToolTipText(App.richToolTip(mode.displayName, mode.description, null));
            button.addActionListener(e -> {
                view.setMode(mode);
                legend.repaint();
            });
            modeGroup.add(button);
            modes.add(button);
        }
        final JToggleButton waterButton = new JToggleButton(Icons.get("droplets", 18), true);
        waterButton.setToolTipText(App.richToolTip("Water", "Show water and lava surfaces", null));
        waterButton.addActionListener(e -> view.setShowWater(waterButton.isSelected()));
        display.add(waterButton);
        final JToggleButton shadersButton = new JToggleButton(Icons.get("sun", 18));
        shadersButton.setToolTipText(App.richToolTip("Shaders", "Light the land with the sun, with shadows, and show moving water reflecting the sky. Set the sun with the globe at the bottom right", null));
        shadersButton.addActionListener(e -> {
            view.setShaders(shadersButton.isSelected());
            sunControl.setVisible(shadersButton.isSelected());
        });
        display.add(shadersButton);
        final JToggleButton treesButton = new JToggleButton(Icons.get("trees", 18), true);
        treesButton.setToolTipText(App.richToolTip("Trees", "Show the trees painted on the land, as simple shapes where the forest layers and the tree pack are, with their density", null));
        treesButton.addActionListener(e -> view.setShowTrees(treesButton.isSelected()));
        display.add(treesButton);
        final JToggleButton contoursButton = new JToggleButton(Icons.get("chart-spline", 18));
        contoursButton.setToolTipText(App.richToolTip("Contour lines", "Draw a line every " + Terrain3DView.CONTOUR_INTERVAL + " blocks of height", null));
        contoursButton.addActionListener(e -> view.setShowContours(contoursButton.isSelected()));
        display.add(contoursButton);
        final JButton rotateLeftButton = new JButton(Icons.get("rotate-ccw", 18));
        rotateLeftButton.setToolTipText(App.richToolTip("Rotate left", "Turn the view a quarter turn", null));
        rotateLeftButton.addActionListener(e -> view.rotateBy(-90));
        camera.add(rotateLeftButton);
        final JButton rotateRightButton = new JButton(Icons.get("rotate-cw", 18));
        rotateRightButton.setToolTipText(App.richToolTip("Rotate right", "Turn the view a quarter turn", null));
        rotateRightButton.addActionListener(e -> view.rotateBy(90));
        camera.add(rotateRightButton);
        final JButton lowButton = new JButton(Icons.get("sunrise", 18));
        lowButton.setToolTipText(App.richToolTip("Low angle", "Look across the land, as a player would", null));
        lowButton.addActionListener(e -> view.setPitchDegrees(14));
        camera.add(lowButton);
        final JButton highButton = new JButton(Icons.get("mountain", 18));
        highButton.setToolTipText(App.richToolTip("High angle", "Look down on the land from above", null));
        highButton.addActionListener(e -> view.setPitchDegrees(62));
        camera.add(highButton);
        final JButton topButton = new JButton(Icons.get("grid-3x3", 18));
        topButton.setToolTipText(App.richToolTip("Top view", "Look straight down on the land, like the map", null));
        topButton.addActionListener(e -> {
            view.setYawDegrees(0);
            view.setPitchDegrees(90);
        });
        camera.add(topButton);
        blocksButton = new JToggleButton(Icons.get("package", 18));
        blocksButton.setToolTipText(App.richToolTip("Blocks", "Preview the land as Minecraft will show it: every block a one metre cube at its exact height, around the centre of the view", null));
        blocksButton.addActionListener(e -> {
            view.setBlocks(blocksButton.isSelected());
            detailSizeComboBox.setEnabled(blocksButton.isSelected());
            requestDetail();
        });
        camera.add(blocksButton);
        detailSizeComboBox = new JComboBox<>(new Integer[] {256, 512, 768, 1024, 1536, 2048, 3072, 4096});
        detailSizeComboBox.setSelectedItem(detailSize);
        detailSizeComboBox.setEnabled(false);
        detailSizeComboBox.setToolTipText(App.richToolTip("Block area", "How far around the camera, in blocks, the land is shown block by block. Larger areas take longer to build and draw, and use more memory", null));
        detailSizeComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                return super.getListCellRendererComponent(list, (value != null) ? (value + " blocks") : null, index, isSelected, cellHasFocus);
            }
        });
        detailSizeComboBox.setMaximumSize(detailSizeComboBox.getPreferredSize());
        detailSizeComboBox.addActionListener(e -> {
            detailSize = (Integer) detailSizeComboBox.getSelectedItem();
            detailArea = null;
            requestDetail();
        });
        camera.add(detailSizeComboBox);
        final JToggleButton orthographicButton = new JToggleButton(Icons.get("box", 18));
        orthographicButton.setToolTipText(App.richToolTip("Orthographic", "Show the land without perspective: parallel lines stay parallel and sizes do not shrink with distance", null));
        orthographicButton.addActionListener(e -> view.setOrthographic(orthographicButton.isSelected()));
        camera.add(orthographicButton);
        final JButton resetButton = new JButton(Icons.get("scan-search", 18));
        resetButton.setToolTipText(App.richToolTip("Reset view", "Show the whole map again", null));
        resetButton.addActionListener(e -> view.resetView());
        camera.add(resetButton);
        final JButton refreshButton = new JButton(Icons.get("refresh-cw", 18));
        refreshButton.setToolTipText(App.richToolTip("Refresh", "Update the 3D view with the latest changes to the map", null));
        refreshButton.addActionListener(e -> refresh(true));
        actions.add(refreshButton);
        popOutButton = new JButton(Icons.get("picture-in-picture-2", 18));
        popOutButton.setToolTipText(App.richToolTip("Detach", "Show the 3D view in a panel of its own, which can float next to the map or be docked like the other panels", null));
        popOutButton.addActionListener(e -> {
            if (popOutAction != null) {
                popOutAction.run();
            }
        });
        actions.add(popOutButton);
        final JPanel toolBar = new JPanel(new WrapLayout(0, 0));
        toolBar.setBorder(new HairlineBorder(false, false, true, false, new Insets(2, 6, 2, 6)));
        for (JToolBar group: new JToolBar[] {modes, display, camera, actions}) {
            if (toolBar.getComponentCount() > 0) {
                final JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
                separator.setPreferredSize(new java.awt.Dimension(13, 20));
                toolBar.add(separator);
            }
            for (Component component: group.getComponents()) {
                if (component instanceof AbstractButton) {
                    ((AbstractButton) component).putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
                    ((AbstractButton) component).setFocusable(false);
                }
                toolBar.add(component);
            }
        }
        // Its height depends on its width, so lay out again when the width changes
        toolBar.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (toolBar.getPreferredSize().height != toolBar.getHeight()) {
                    revalidate();
                }
            }
        });
        add(toolBar, BorderLayout.NORTH);
        autoRefreshTimer.start();

        // The view with the info card, the legend and the help text on top of it
        final JLayeredPane layers = new JLayeredPane() {
            @Override
            public void doLayout() {
                final int w = getWidth(), h = getHeight();
                view.setBounds(0, 0, w, h);
                final java.awt.Dimension cardSize = infoCard.getPreferredSize();
                infoCard.setBounds(12, 12, Math.max(cardSize.width, 230), cardSize.height);
                legend.setBounds(12, h - 58, 280, 46);
                final java.awt.Dimension helpSize = helpLabel.getPreferredSize();
                helpLabel.setBounds(w - helpSize.width - 14, h - helpSize.height - 12, helpSize.width, helpSize.height);
                // Leave out the help when it would run into the legend, rather than cut it off
                helpLabel.setVisible(helpSize.width + 14 + 12 <= w - (legend.isVisible() ? 292 : 0));
                final java.awt.Dimension sunSize = sunControl.getPreferredSize();
                sunControl.setBounds(w - sunSize.width - 14, h - helpSize.height - 20 - sunSize.height, sunSize.width, sunSize.height);
                final java.awt.Dimension statusSize = statusLabel.getPreferredSize();
                statusLabel.setBounds((w - statusSize.width) / 2, (h - statusSize.height) / 2, statusSize.width, statusSize.height);
            }
        };
        helpLabel = new JLabel("Drag to orbit · Shift-drag to pan · Scroll to zoom · Double-click to focus");
        helpLabel.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $WorldPainter.mutedForeground");
        statusLabel = new JLabel(" ");
        statusLabel.putClientProperty(FlatClientProperties.STYLE, "font: +2 semibold");
        layers.add(view, JLayeredPane.DEFAULT_LAYER);
        layers.add(infoCard, JLayeredPane.PALETTE_LAYER);
        layers.add(legend, JLayeredPane.PALETTE_LAYER);
        layers.add(helpLabel, JLayeredPane.PALETTE_LAYER);
        sunControl = new SunControl();
        sunControl.setVisible(false);
        layers.add(sunControl, JLayeredPane.PALETTE_LAYER);
        layers.add(statusLabel, JLayeredPane.MODAL_LAYER);
        add(layers, BorderLayout.CENTER);
        pictureArea = layers;
    }

    /**
     * Show a dimension. The 3D model is (re)built in the background if the dimension changed since it was last built.
     */
    public void setDimension(Dimension dimension, ColourScheme colourScheme) {
        this.dimension = dimension;
        this.colourScheme = colourScheme;
        refresh(false);
    }

    /**
     * Rebuild the 3D model, if the dimension has changed or if forced.
     */
    public void refresh(boolean force) {
        if (dimension == null) {
            return;
        }
        if ((! force) && (view.getModel() != null) && (builtFor == dimension) && (builtAtChange == dimension.getChangeNo())) {
            return;
        }
        if (dimension.getTileCount() == 0) {
            return;
        }
        final Dimension dimension = this.dimension;
        final long changeNo = dimension.getChangeNo();
        building = true;
        statusLabel.setText("Building 3D view…");
        statusLabel.setVisible(true);
        revalidate();
        new SwingWorker<TerrainModel, Void>() {
            @Override
            protected TerrainModel doInBackground() throws Exception {
                return new TerrainModel(dimension, colourScheme, new StatusProgress("Building 3D view"));
            }

            @Override
            protected void done() {
                building = false;
                try {
                    final TerrainModel model = get();
                    if (dimension == Terrain3DPanel.this.dimension) {
                        view.setModel(model);
                        builtFor = dimension;
                        builtAtChange = changeNo;
                        detailArea = null;
                        requestDetail();
                    }
                    statusLabel.setVisible(false);
                } catch (InterruptedException | ExecutionException e) {
                    logger.error("Could not build the 3D view", e);
                    statusLabel.setText("Could not build the 3D view: " + e.getMessage());
                }
            }
        }.execute();
    }

    /**
     * In block mode, load the blocks around the centre of the view at full resolution (one column per block), unless
     * they are already loaded. The rest of the map is shown as the smooth surface.
     */
    /**
     * Set what the detach button does, and whether the view is detached (in a panel of its own) or not, for the tooltip
     * of the button.
     */
    /**
     * The part of the panel which shows the land, without the buttons above it.
     */
    public JComponent getPictureArea() {
        return pictureArea;
    }

    /**
     * Whether it shows the land yet (it only loads it when it is first shown).
     */
    public boolean isReady() {
        return view.hasModel();
    }

    /**
     * Stop updating, when the panel is thrown away.
     */
    public void dispose() {
        autoRefreshTimer.stop();
    }

    private static JToolBar createGroup() {
        final JToolBar group = new JToolBar();
        group.setFloatable(false);
        group.setOpaque(false);
        group.setBorder(null);
        return group;
    }

    public void setPopOutAction(Runnable popOutAction, boolean detached) {
        this.popOutAction = popOutAction;
        popOutButton.setVisible(popOutAction != null);
        popOutButton.setToolTipText(detached
                ? App.richToolTip("Attach", "Show the 3D view in the main window again, in place of the map", null)
                : App.richToolTip("Detach", "Show the 3D view in a panel of its own, which can float next to the map or be docked like the other panels", null));
    }

    private void requestDetail() {
        final TerrainModel model = view.getModel();
        if ((! blocksButton.isSelected()) || (dimension == null) || (model == null)) {
            return;
        }
        final Point target = view.getDetailCentre(detailSize);
        final Rectangle extent = TerrainModel.getBlockExtent(dimension);
        if ((detailArea != null) && covers(detailArea, extent, target)) {
            return;
        }
        if (detailLoading) {
            detailPending = true;
            return;
        }
        final Rectangle area = new Rectangle(target.x - detailSize / 2, target.y - detailSize / 2, detailSize, detailSize).intersection(extent);
        if (area.isEmpty()) {
            return;
        }
        final Dimension dimension = this.dimension;
        final TerrainModel overview = model;
        detailLoading = true;
        statusLabel.setText("Loading the blocks…");
        statusLabel.setVisible(true);
        revalidate();
        new SwingWorker<TerrainModel, Void>() {
            @Override
            protected TerrainModel doInBackground() throws Exception {
                return new TerrainModel(dimension, colourScheme, new StatusProgress("Loading the blocks"), area, 1);
            }

            @Override
            protected void done() {
                detailLoading = false;
                statusLabel.setVisible(false);
                try {
                    final TerrainModel detail = get();
                    if ((dimension == Terrain3DPanel.this.dimension) && (view.getModel() == overview)) {
                        view.setDetail(detail);
                        detailArea = area;
                    }
                } catch (InterruptedException | ExecutionException e) {
                    logger.error("Could not load the blocks for the 3D view", e);
                }
                if (detailPending) {
                    detailPending = false;
                    requestDetail();
                }
            }
        }.execute();
    }

    /**
     * Shows the progress of building the 3D view (or loading its blocks) as a percentage in the status label.
     */
    private final class StatusProgress implements ProgressReceiver {
        StatusProgress(String text) {
            this.text = text;
        }

        @Override
        public void setProgress(float progress) {
            final int percent = Math.round(progress * 100);
            if (percent != lastPercent) {
                lastPercent = percent;
                SwingUtilities.invokeLater(() -> {
                    if (statusLabel.isVisible()) {
                        statusLabel.setText(text + "… " + percent + "%");
                    }
                });
            }
        }

        @Override public void exceptionThrown(Throwable exception) {}
        @Override public void done() {}
        @Override public void setMessage(String message) {}
        @Override public void checkForCancellation() {}
        @Override public void reset() {}
        @Override public void subProgressStarted(SubProgressReceiver subProgressReceiver) {}

        private final String text;
        private volatile int lastPercent = -1;
    }

    /**
     * Whether a loaded area still covers a target well: it is at least a quarter of the area away from every edge which
     * is not also an edge of the map.
     */
    private boolean covers(Rectangle area, Rectangle extent, Point target) {
        final int margin = detailSize / 4;
        return ((target.x - area.x >= margin) || (area.x <= extent.x))
                && ((area.x + area.width - target.x >= margin) || (area.x + area.width >= extent.x + extent.width))
                && ((target.y - area.y >= margin) || (area.y <= extent.y))
                && ((area.y + area.height - target.y >= margin) || (area.y + area.height >= extent.y + extent.height));
    }

    private void hoverChanged(boolean over, int x, int y) {
        final TerrainModel model = view.getModel();
        if ((! over) || (model == null) || (dimension == null)) {
            infoCard.setLines(null);
            return;
        }
        final int sx = Math.max(0, Math.min(model.width - 1, (x - model.x0) / model.step)), sy = Math.max(0, Math.min(model.height - 1, (y - model.y0) / model.step));
        final int sample = sy * model.width + sx;
        final Tile tile = dimension.getTile(x >> TILE_SIZE_BITS, y >> TILE_SIZE_BITS);
        if (tile == null) {
            infoCard.setLines(null);
            return;
        }
        final int xInTile = x & TILE_SIZE_MASK, yInTile = y & TILE_SIZE_MASK;
        final List<String[]> lines = new ArrayList<>();
        lines.add(new String[] {"Position", "X " + x + "  ·  Z " + y});
        final float height = tile.getHeight(xInTile, yInTile);
        lines.add(new String[] {"Height", "Y " + Math.round(height)});
        lines.add(new String[] {"Slope", Math.round(model.slopes[sample]) + "°"});
        final Terrain terrain = tile.getTerrain(xInTile, yInTile);
        lines.add(new String[] {"Terrain", terrain.getName()});
        int biome = tile.getLayerValue(Biome.INSTANCE, xInTile, yInTile);
        final boolean auto = biome == 255;
        if (auto) {
            biome = dimension.getAutoBiome(tile, xInTile, yInTile);
        }
        if ((biome >= 0) && (biome < Minecraft1_21Biomes.BIOME_NAMES.length) && (Minecraft1_21Biomes.BIOME_NAMES[biome] != null)) {
            lines.add(new String[] {"Biome", Minecraft1_21Biomes.BIOME_NAMES[biome] + (auto ? " (automatic)" : "")});
        }
        final int waterLevel = tile.getWaterLevel(xInTile, yInTile);
        if (waterLevel > height) {
            lines.add(new String[] {"Water", "depth " + Math.round(waterLevel - height) + " (surface Y " + waterLevel + ")"});
        } else {
            lines.add(new String[] {"Water", "dry"});
        }
        final StringBuilder layerNames = new StringBuilder();
        for (Layer layer: tile.getActiveLayers(xInTile, yInTile)) {
            if ((layer instanceof Biome) || Constants.SYSTEM_LAYERS.contains(layer)) {
                continue;
            }
            if (layerNames.length() > 0) {
                layerNames.append(", ");
            }
            layerNames.append(layer.getName());
        }
        lines.add(new String[] {"Layers", (layerNames.length() > 0) ? layerNames.toString() : "none"});
        infoCard.setLines(lines);
    }

    private final Terrain3DView view;
    private SunControl sunControl;
    private final JToggleButton blocksButton;
    private JComboBox<Integer> detailSizeComboBox;
    private final JButton popOutButton;
    private Runnable popOutAction;
    private Rectangle detailArea;
    private boolean detailLoading, detailPending;
    private final InfoCard infoCard;
    private final Legend legend;
    private final JLabel helpLabel, statusLabel;
    /**
     * Brings the view up to date by itself after the map has been edited, once the edits have stopped for a moment (so
     * not in the middle of a brush stroke).
     */
    private void autoRefresh() {
        if ((! isShowing()) || (dimension == null) || (builtFor != dimension) || building) {
            return;
        }
        final long changeNo = dimension.getChangeNo();
        if (changeNo == builtAtChange) {
            lastSeenChange = changeNo;
        } else if (changeNo == lastSeenChange) {
            refresh(false);
        } else {
            lastSeenChange = changeNo;
        }
    }

    private JComponent pictureArea;
    private final Timer autoRefreshTimer = new Timer(1200, e -> autoRefresh());
    private volatile boolean building;
    private long lastSeenChange = -1;
    private Dimension dimension, builtFor;
    private ColourScheme colourScheme;
    private long builtAtChange = -1;

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Terrain3DPanel.class);
    /**
     * The size of the area around the centre of the view which is shown block by block, in blocks.
     */
    private int detailSize = 768;
    private static final long serialVersionUID = 1L;

    /**
     * A translucent card with labelled values, like Minecraft's debug screen.
     */
    private static final class InfoCard extends JComponent {
        void setLines(List<String[]> lines) {
            this.lines = lines;
            setVisible(lines != null);
            final Container parent = getParent();
            if (parent != null) {
                parent.doLayout();
            }
            repaint();
        }

        @Override
        public java.awt.Dimension getPreferredSize() {
            final int count = (lines != null) ? lines.size() : 0;
            int width = 230;
            if (lines != null) {
                final FontMetrics fm = getFontMetrics(getFont());
                for (String[] line: lines) {
                    width = Math.max(width, 100 + fm.stringWidth(line[1]) + 24);
                }
            }
            return new java.awt.Dimension(width, 20 + count * 20);
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (lines == null) {
                return;
            }
            final Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                final Color background = UIManager.getColor("Panel.background");
                g2.setColor(new Color(background.getRed(), background.getGreen(), background.getBlue(), 225));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.setColor(ThemeUtils.getColour("WorldPainter.border", Color.GRAY));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                final FontMetrics fm = g2.getFontMetrics(getFont());
                int y = 14 + fm.getAscent();
                for (String[] line: lines) {
                    g2.setColor(ThemeUtils.getColour("WorldPainter.mutedForeground", Color.GRAY));
                    g2.setFont(getFont());
                    g2.drawString(line[0], 12, y);
                    g2.setColor(UIManager.getColor("Label.foreground"));
                    g2.drawString(line[1], 100, y);
                    y += 20;
                }
            } finally {
                g2.dispose();
            }
        }

        private List<String[]> lines;
        private static final long serialVersionUID = 1L;
    }

    /**
     * A legend for the current colour mode.
     */
    private final class Legend extends JComponent {
        @Override
        protected void paintComponent(Graphics g) {
            final TerrainModel model = view.getModel();
            final Terrain3DView.Mode mode = view.getMode();
            if ((model == null) || ((mode != Terrain3DView.Mode.HEIGHT) && (mode != Terrain3DView.Mode.SLOPE))) {
                return;
            }
            final Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                final int[] stops = (mode == Terrain3DView.Mode.HEIGHT) ? Terrain3DView.HEIGHT_RAMP : Terrain3DView.SLOPE_RAMP;
                final int barWidth = getWidth() - 2, barY = 4;
                for (int x = 0; x < barWidth; x++) {
                    g2.setColor(new Color(Terrain3DView.ramp(stops, (float) x / (barWidth - 1))));
                    g2.fillRect(x + 1, barY, 1, 12);
                }
                g2.setColor(ThemeUtils.getColour("WorldPainter.border", Color.GRAY));
                g2.drawRect(0, barY - 1, barWidth + 1, 13);
                g2.setColor(UIManager.getColor("Label.foreground"));
                g2.setFont(getFont().deriveFont(getFont().getSize2D() - 1f));
                final FontMetrics fm = g2.getFontMetrics();
                final String left = (mode == Terrain3DView.Mode.HEIGHT) ? ("Y " + Math.round(model.lowestHeight)) : "flat";
                final String right = (mode == Terrain3DView.Mode.HEIGHT) ? ("Y " + Math.round(model.highestHeight)) : "60°+";
                g2.drawString(left, 1, barY + 14 + fm.getAscent());
                g2.drawString(right, barWidth + 1 - fm.stringWidth(right), barY + 14 + fm.getAscent());
            } finally {
                g2.dispose();
            }
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Where the sun is: two sliders, for the direction it shines from and how high it stands, and a few times of day.
     */
    private final class SunControl extends JPanel {
        SunControl() {
            super(new GridBagLayout());
            setOpaque(true);
            final GridBagConstraints constraints = new GridBagConstraints();
            constraints.anchor = GridBagConstraints.LINE_START;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.insets = new Insets(1, 0, 1, 6);

            directionSlider.setFocusable(false);
            heightSlider.setFocusable(false);
            updateLabels();
            addRow(constraints, 0, "Direction", directionSlider, directionLabel);
            addRow(constraints, 1, "Height", heightSlider, heightLabel);
            directionSlider.addChangeListener(e -> sliderChanged(directionSlider));
            heightSlider.addChangeListener(e -> sliderChanged(heightSlider));

            final JPanel times = new JPanel(new GridLayout(1, 3, 2, 0));
            times.setOpaque(false);
            times.add(timeButton("Morning", 100, 16));
            times.add(timeButton("Noon", 170, 62));
            times.add(timeButton("Evening", -100, 12));
            constraints.gridx = 0;
            constraints.gridy = 2;
            constraints.gridwidth = 3;
            constraints.insets = new Insets(4, 0, 0, 0);
            add(times, constraints);

            applyTimer.setRepeats(false);
        }

        @Override
        public void updateUI() {
            super.updateUI();
            setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(ThemeUtils.getColour("WorldPainter.border", Color.GRAY)), BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        }

        private void addRow(GridBagConstraints constraints, int row, String title, JSlider slider, JLabel valueLabel) {
            final Font small = getFont().deriveFont(getFont().getSize2D() - 2f);
            constraints.gridy = row;
            constraints.gridx = 0;
            constraints.weightx = 0;
            final JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(small);
            add(titleLabel, constraints);
            constraints.gridx = 1;
            constraints.weightx = 1;
            slider.setPreferredSize(new java.awt.Dimension(90, slider.getPreferredSize().height));
            add(slider, constraints);
            constraints.gridx = 2;
            constraints.weightx = 0;
            valueLabel.setFont(small);
            valueLabel.setHorizontalAlignment(SwingConstants.TRAILING);
            valueLabel.setPreferredSize(new java.awt.Dimension(26, valueLabel.getPreferredSize().height));
            add(valueLabel, constraints);
        }

        private JButton timeButton(String text, int azimuth, int elevation) {
            final JButton button = new JButton(text);
            button.setFont(button.getFont().deriveFont(button.getFont().getSize2D() - 2f));
            button.setMargin(new Insets(1, 2, 1, 2));
            button.setFocusable(false);
            button.addActionListener(e -> {
                directionSlider.setValue(azimuth);
                heightSlider.setValue(elevation);
                applyTimer.stop();
                apply();
            });
            return button;
        }

        private void sliderChanged(JSlider slider) {
            updateLabels();
            if (! slider.getValueIsAdjusting()) {
                applyTimer.stop();
                apply();
            } else if (! applyTimer.isRunning()) {
                // Lighting the whole map takes a moment, so do it at most a few times per second while dragging
                applyTimer.start();
            }
        }

        private void updateLabels() {
            final String[] compass = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
            directionLabel.setText(compass[Math.floorMod(Math.round(directionSlider.getValue() / 45f), 8)]);
            heightLabel.setText(heightSlider.getValue() + "\u00b0");
        }

        private void apply() {
            view.setSun(directionSlider.getValue(), heightSlider.getValue());
        }

        @Override
        public java.awt.Dimension getPreferredSize() {
            return new java.awt.Dimension(com.formdev.flatlaf.util.UIScale.scale(WIDTH), super.getPreferredSize().height);
        }

        private final JSlider directionSlider = new JSlider(-180, 180, 130), heightSlider = new JSlider(2, 90, 35);
        private final JLabel directionLabel = new JLabel(), heightLabel = new JLabel();
        private final Timer applyTimer = new Timer(150, e -> apply());

        private static final int WIDTH = 214;
        private static final long serialVersionUID = 1L;
    }
}
