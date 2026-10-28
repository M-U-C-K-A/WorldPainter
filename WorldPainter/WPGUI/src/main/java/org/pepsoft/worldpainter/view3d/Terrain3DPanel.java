package org.pepsoft.worldpainter.view3d;

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

        final JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setBorder(new HairlineBorder(false, false, true, false, new Insets(2, 6, 2, 6)));
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
            toolBar.add(button);
        }
        toolBar.addSeparator();
        final JToggleButton waterButton = new JToggleButton(Icons.get("droplets", 18), true);
        waterButton.setToolTipText(App.richToolTip("Water", "Show water and lava surfaces", null));
        waterButton.addActionListener(e -> view.setShowWater(waterButton.isSelected()));
        toolBar.add(waterButton);
        final JToggleButton shadersButton = new JToggleButton(Icons.get("sun", 18));
        shadersButton.setToolTipText(App.richToolTip("Shaders", "Light the land with the sun, with shadows, and show moving water reflecting the sky. Set the sun with the globe at the bottom right", null));
        shadersButton.addActionListener(e -> {
            view.setShaders(shadersButton.isSelected());
            sunControl.setVisible(shadersButton.isSelected());
        });
        toolBar.add(shadersButton);
        final JToggleButton contoursButton = new JToggleButton(Icons.get("chart-spline", 18));
        contoursButton.setToolTipText(App.richToolTip("Contour lines", "Draw a line every " + Terrain3DView.CONTOUR_INTERVAL + " blocks of height", null));
        contoursButton.addActionListener(e -> view.setShowContours(contoursButton.isSelected()));
        toolBar.add(contoursButton);
        toolBar.addSeparator();
        toolBar.add(Box.createHorizontalGlue());
        final JButton rotateLeftButton = new JButton(Icons.get("rotate-ccw", 18));
        rotateLeftButton.setToolTipText(App.richToolTip("Rotate left", "Turn the view a quarter turn", null));
        rotateLeftButton.addActionListener(e -> view.rotateBy(-90));
        toolBar.add(rotateLeftButton);
        final JButton rotateRightButton = new JButton(Icons.get("rotate-cw", 18));
        rotateRightButton.setToolTipText(App.richToolTip("Rotate right", "Turn the view a quarter turn", null));
        rotateRightButton.addActionListener(e -> view.rotateBy(90));
        toolBar.add(rotateRightButton);
        final JButton lowButton = new JButton(Icons.get("sunrise", 18));
        lowButton.setToolTipText(App.richToolTip("Low angle", "Look across the land, as a player would", null));
        lowButton.addActionListener(e -> view.setPitchDegrees(14));
        toolBar.add(lowButton);
        final JButton highButton = new JButton(Icons.get("mountain", 18));
        highButton.setToolTipText(App.richToolTip("High angle", "Look down on the land from above", null));
        highButton.addActionListener(e -> view.setPitchDegrees(62));
        toolBar.add(highButton);
        final JButton topButton = new JButton(Icons.get("grid-3x3", 18));
        topButton.setToolTipText(App.richToolTip("Top view", "Look straight down on the land, like the map", null));
        topButton.addActionListener(e -> {
            view.setYawDegrees(0);
            view.setPitchDegrees(90);
        });
        toolBar.add(topButton);
        blocksButton = new JToggleButton(Icons.get("package", 18));
        blocksButton.setToolTipText(App.richToolTip("Blocks", "Preview the land as Minecraft will show it: every block a one metre cube at its exact height, around the centre of the view", null));
        blocksButton.addActionListener(e -> {
            view.setBlocks(blocksButton.isSelected());
            detailSizeComboBox.setEnabled(blocksButton.isSelected());
            requestDetail();
        });
        toolBar.add(blocksButton);
        detailSizeComboBox = new JComboBox<>(new Integer[] {256, 512, 768, 1024, 1536});
        detailSizeComboBox.setSelectedItem(detailSize);
        detailSizeComboBox.setEnabled(false);
        detailSizeComboBox.setToolTipText(App.richToolTip("Block area", "How far around the camera, in blocks, the land is shown block by block. Larger areas take longer to build and draw", null));
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
        toolBar.add(detailSizeComboBox);
        final JToggleButton orthographicButton = new JToggleButton(Icons.get("box", 18));
        orthographicButton.setToolTipText(App.richToolTip("Orthographic", "Show the land without perspective: parallel lines stay parallel and sizes do not shrink with distance", null));
        orthographicButton.addActionListener(e -> view.setOrthographic(orthographicButton.isSelected()));
        toolBar.add(orthographicButton);
        final JButton resetButton = new JButton(Icons.get("scan-search", 18));
        resetButton.setToolTipText(App.richToolTip("Reset view", "Show the whole map again", null));
        resetButton.addActionListener(e -> view.resetView());
        toolBar.add(resetButton);
        toolBar.addSeparator();
        final JButton refreshButton = new JButton(Icons.get("refresh-cw", 18));
        refreshButton.setToolTipText(App.richToolTip("Refresh", "Update the 3D view with the latest changes to the map", null));
        refreshButton.addActionListener(e -> refresh(true));
        toolBar.add(refreshButton);
        add(toolBar, BorderLayout.NORTH);

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
                sunControl.setBounds(w - SunControl.SIZE - 14, h - helpSize.height - 20 - SunControl.SIZE, SunControl.SIZE, SunControl.SIZE);
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
        statusLabel.setText("Building 3D view…");
        statusLabel.setVisible(true);
        revalidate();
        new SwingWorker<TerrainModel, Void>() {
            @Override
            protected TerrainModel doInBackground() throws Exception {
                return new TerrainModel(dimension, colourScheme, null);
            }

            @Override
            protected void done() {
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
        new SwingWorker<TerrainModel, Void>() {
            @Override
            protected TerrainModel doInBackground() throws Exception {
                return new TerrainModel(dimension, colourScheme, null, area, 1);
            }

            @Override
            protected void done() {
                detailLoading = false;
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
    private Rectangle detailArea;
    private boolean detailLoading, detailPending;
    private final InfoCard infoCard;
    private final Legend legend;
    private final JLabel helpLabel, statusLabel;
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
     * A small globe, seen from above, on which the user drags the sun: the centre is straight overhead, the rim is the
     * horizon, north is up.
     */
    private final class SunControl extends JComponent {
        SunControl() {
            setOpaque(false);
            setToolTipText(App.richToolTip("Sun", "Drag the sun to light the land from another direction and height: the centre is straight overhead, the edge is the horizon. The shadows follow", null));
            applyTimer.setRepeats(false);
            final java.awt.event.MouseAdapter adapter = new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    drag(e);
                }

                @Override
                public void mouseDragged(java.awt.event.MouseEvent e) {
                    drag(e);
                }

                @Override
                public void mouseReleased(java.awt.event.MouseEvent e) {
                    drag(e);
                    applyTimer.stop();
                    apply();
                }
            };
            addMouseListener(adapter);
            addMouseMotionListener(adapter);
        }

        private void drag(java.awt.event.MouseEvent e) {
            final double cx = getWidth() / 2.0, cy = getHeight() / 2.0, radius = getWidth() / 2.0 - 8;
            final double dx = e.getX() - cx, dy = e.getY() - cy;
            azimuth = Math.toDegrees(Math.atan2(dx, -dy));
            elevation = 90 * (1 - Math.min(1, Math.hypot(dx, dy) / radius));
            repaint();
            // Lighting the whole map takes a moment, so do it at most a few times per second while dragging
            if (! applyTimer.isRunning()) {
                applyTimer.start();
            }
        }

        private void apply() {
            view.setSun(azimuth, elevation);
        }

        @Override
        protected void paintComponent(Graphics g) {
            final Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                final int size = Math.min(getWidth(), getHeight());
                final double radius = size / 2.0 - 8, cx = getWidth() / 2.0, cy = getHeight() / 2.0;
                final Color background = UIManager.getColor("Panel.background"), foreground = UIManager.getColor("Label.foreground");
                g2.setColor(new Color(background.getRed(), background.getGreen(), background.getBlue(), 215));
                g2.fill(new java.awt.geom.Ellipse2D.Double(cx - radius - 6, cy - radius - 6, (radius + 6) * 2, (radius + 6) * 2));
                final Color line = ThemeUtils.getColour("WorldPainter.border", Color.GRAY);
                g2.setColor(line);
                g2.draw(new java.awt.geom.Ellipse2D.Double(cx - radius, cy - radius, radius * 2, radius * 2));
                g2.draw(new java.awt.geom.Ellipse2D.Double(cx - radius / 2, cy - radius / 2, radius, radius));
                g2.draw(new java.awt.geom.Line2D.Double(cx - radius, cy, cx + radius, cy));
                g2.draw(new java.awt.geom.Line2D.Double(cx, cy - radius, cx, cy + radius));
                g2.setFont(getFont().deriveFont(Font.BOLD, getFont().getSize2D() - 3f));
                g2.setColor(foreground);
                final FontMetrics fm = g2.getFontMetrics();
                g2.drawString("N", (float) (cx - fm.stringWidth("N") / 2.0), (float) (cy - radius - 1));
                // The sun
                final double r = radius * (1 - elevation / 90), a = Math.toRadians(azimuth);
                final double sx = cx + r * Math.sin(a), sy = cy - r * Math.cos(a);
                g2.setColor(new Color(0xf59e0b));
                g2.fill(new java.awt.geom.Ellipse2D.Double(sx - 6, sy - 6, 12, 12));
                g2.setColor(new Color(0xfffbeb));
                g2.draw(new java.awt.geom.Ellipse2D.Double(sx - 6, sy - 6, 12, 12));
            } finally {
                g2.dispose();
            }
        }

        private double azimuth = -50, elevation = 40;
        private final Timer applyTimer = new Timer(150, e -> apply());

        static final int SIZE = 96;
        private static final long serialVersionUID = 1L;
    }
}
