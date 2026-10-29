package org.pepsoft.worldpainter.paths;

import org.pepsoft.util.swing.ProgressTask;

import org.pepsoft.util.swing.ProgressDialog;

import org.pepsoft.util.ProgressReceiver;

import org.pepsoft.worldpainter.ColourScheme;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.WorldPainterDialog;
import org.pepsoft.worldpainter.WorldPainterView;
import org.pepsoft.worldpainter.layers.CustomLayer;
import org.pepsoft.worldpainter.themes.TerrainListCellRenderer;
import org.pepsoft.worldpainter.util.ThemeUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * The options for a path from one point to another, with a live preview of the route (seen from above) and of its
 * height profile, showing where it runs on the ground, on bridges and through tunnels.
 */
public class PathDialog extends WorldPainterDialog {
    public PathDialog(Window parent, Dimension dimension, WorldPainterView view, ColourScheme colourScheme, List<Point> waypoints, int layerNumber) {
        super(parent);
        this.dimension = dimension;
        this.view = view;
        this.waypoints = waypoints;
        final Point start = waypoints.get(0), end = waypoints.get(waypoints.size() - 1);
        this.layerNumber = layerNumber;
        setTitle("Path");

        final JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBorder(new EmptyBorder(12, 12, 12, 12));
        content.add(new JLabel("<html><div style=\"width: 640px\">A path from <b>" + start.x + ", " + start.y + "</b> to <b>" + end.x + ", " + end.y + "</b>"
                + ((waypoints.size() > 2) ? (" through " + (waypoints.size() - 2) + " point" + ((waypoints.size() > 3) ? "s" : "")) : "") + ", "
                + "laid out over the land with a limited slope. Bridges and tunnels are created as layers, which you can edit afterwards like any other.</div></html>"), BorderLayout.NORTH);

        final JPanel form = new JPanel(new GridBagLayout());
        final GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 4, 3, 4);
        c.anchor = GridBagConstraints.WEST;
        c.gridy = 0;

        // Curvature
        c.gridx = 0;
        form.add(new JLabel("Bends:"), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        curvatureLabel = new JLabel(String.valueOf(last.getCurvature()));
        curvatureLabel.setPreferredSize(new java.awt.Dimension(130, curvatureLabel.getPreferredSize().height));
        curvatureSlider = new JSlider(0, 100, last.getCurvature());
        curvatureSlider.setToolTipText("0: a straight line from start to end. 100: the route which follows the land best, avoiding climbs");
        curvatureSlider.addChangeListener(e -> {
            curvatureLabel.setText(curvatureSlider.getValue() + (curvatureSlider.getValue() == 0 ? " (straight)" : (curvatureSlider.getValue() == 100 ? " (follows the land)" : "")));
            schedulePlan();
        });
        form.add(curvatureSlider, c);
        c.gridx = 2;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        form.add(curvatureLabel, c);

        // Crossings
        c.gridy++;
        c.gridx = 0;
        form.add(new JLabel("Crossings:"), c);
        c.gridx = 1;
        c.gridwidth = 2;
        crossingsComboBox = new JComboBox<>(Crossings.values());
        crossingsComboBox.setSelectedItem(Crossings.of(last));
        crossingsComboBox.setToolTipText("Natural: the path goes up and down with the land. Bridges span valleys and water; tunnels go through hills");
        crossingsComboBox.addActionListener(e -> schedulePlan());
        form.add(crossingsComboBox, c);
        c.gridwidth = 1;

        // Width and slope
        c.gridy++;
        c.gridx = 0;
        form.add(new JLabel("Width:"), c);
        c.gridx = 1;
        c.gridwidth = 2;
        final JPanel sizes = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 0));
        widthSpinner = new JSpinner(new SpinnerNumberModel(last.getWidth(), 1, 24, 1));
        widthSpinner.addChangeListener(e -> schedulePlan());
        sizes.add(widthSpinner);
        sizes.add(new JLabel("blocks     Steepest slope:"));
        slopeSpinner = new JSpinner(new SpinnerNumberModel((int) Math.round(last.getMaxGrade() * 100), 2, 100, 1));
        slopeSpinner.setToolTipText("The steepest the path may climb or descend, in blocks of height per 100 blocks of length");
        slopeSpinner.addChangeListener(e -> schedulePlan());
        sizes.add(slopeSpinner);
        sizes.add(new JLabel("%     Longest bridge or tunnel:"));
        spanSpinner = new JSpinner(new SpinnerNumberModel(last.getMaxSpan(), 8, 512, 8));
        spanSpinner.addChangeListener(e -> schedulePlan());
        sizes.add(spanSpinner);
        sizes.add(new JLabel("blocks"));
        form.add(sizes, c);
        c.gridwidth = 1;

        // Materials
        c.gridy++;
        c.gridx = 0;
        form.add(new JLabel("Surface:"), c);
        c.gridx = 1;
        c.gridwidth = 2;
        final JPanel materials = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 0));
        materialComboBox = new JComboBox<>(Terrain.getConfiguredValues());
        materialComboBox.setRenderer(new TerrainListCellRenderer(colourScheme));
        materialComboBox.setSelectedItem(last.getMaterial());
        materials.add(materialComboBox);
        bordersCheckBox = new JCheckBox("Borders:", last.isBorders());
        bordersCheckBox.setToolTipText("Edges of a different material along both sides, and low parapets on bridges");
        materials.add(bordersCheckBox);
        borderComboBox = new JComboBox<>(Terrain.getConfiguredValues());
        borderComboBox.setRenderer(new TerrainListCellRenderer(colourScheme));
        borderComboBox.setSelectedItem(last.getBorderMaterial());
        borderComboBox.setEnabled(last.isBorders());
        bordersCheckBox.addActionListener(e -> borderComboBox.setEnabled(bordersCheckBox.isSelected()));
        materials.add(borderComboBox);
        form.add(materials, c);
        c.gridwidth = 1;

        // Preview
        final JPanel previews = new JPanel(new GridLayout(1, 2, 10, 0));
        mapPreview = new MapPreview();
        mapPreview.setBorder(BorderFactory.createTitledBorder("Route"));
        previews.add(mapPreview);
        profilePreview = new ProfilePreview();
        profilePreview.setBorder(BorderFactory.createTitledBorder("Height profile"));
        previews.add(profilePreview);

        final JPanel centre = new JPanel(new BorderLayout(0, 8));
        centre.add(form, BorderLayout.NORTH);
        centre.add(previews, BorderLayout.CENTER);
        infoLabel = new JLabel(" ");
        centre.add(infoLabel, BorderLayout.SOUTH);
        content.add(centre, BorderLayout.CENTER);

        final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING, 6, 0));
        final JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> cancel());
        applyButton = new JButton("Build Path");
        applyButton.setEnabled(false);
        applyButton.addActionListener(e -> apply());
        buttons.add(cancelButton);
        buttons.add(applyButton);
        getRootPane().setDefaultButton(applyButton);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);

        planTimer = new Timer(200, e -> plan());
        planTimer.setRepeats(false);
        plan();
        scaleToUI();
        pack();
        scaleWindowToUI();
        setLocationRelativeTo(parent);
    }

    /**
     * The bridge and tunnel layers created by the path, if it was built.
     */
    public List<CustomLayer> getCreatedLayers() {
        return createdLayers;
    }

    private PathSettings createSettings() {
        final PathSettings settings = new PathSettings();
        settings.setCurvature(curvatureSlider.getValue());
        final Crossings crossings = (Crossings) crossingsComboBox.getSelectedItem();
        settings.setBridges((crossings == Crossings.BRIDGES) || (crossings == Crossings.BOTH));
        settings.setTunnels((crossings == Crossings.TUNNELS) || (crossings == Crossings.BOTH));
        settings.setWidth((Integer) widthSpinner.getValue());
        settings.setMaxGrade((Integer) slopeSpinner.getValue() / 100.0);
        settings.setMaxSpan((Integer) spanSpinner.getValue());
        settings.setMaterial((Terrain) materialComboBox.getSelectedItem());
        settings.setBorders(bordersCheckBox.isSelected());
        settings.setBorderMaterial((Terrain) borderComboBox.getSelectedItem());
        return settings;
    }

    private void schedulePlan() {
        planTimer.restart();
    }

    /**
     * Work out the path in the background (finding the route can take a moment for long paths).
     */
    private void plan() {
        final PathSettings settings = createSettings();
        final int generation = ++planGeneration;
        infoLabel.setText("Finding the route…");
        applyButton.setEnabled(false);
        new SwingWorker<PathPlan, Void>() {
            @Override
            protected PathPlan doInBackground() {
                return PathGenerator.plan(dimension, waypoints, settings);
            }

            @Override
            protected void done() {
                if (generation != planGeneration) {
                    return;
                }
                try {
                    plan = get();
                    planSettings = settings;
                    mapPreview.setPlan(plan);
                    profilePreview.setPlan(plan);
                    updateInfo();
                    applyButton.setEnabled(true);
                } catch (InterruptedException | ExecutionException e) {
                    infoLabel.setText("Could not lay out the path: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void updateInfo() {
        final int length = plan.getPoints().size();
        final int bridges = plan.count(PathPlan.BRIDGE), tunnels = plan.count(PathPlan.TUNNEL);
        final float[] cutAndFill = plan.getCutAndFill();
        final float[] profile = plan.getProfile();
        float steepest = 0;
        for (int i = 1; i < profile.length; i++) {
            steepest = Math.max(steepest, Math.abs(profile[i] - profile[i - 1]));
        }
        infoLabel.setText(String.format("<html><b>%,d</b> blocks long &nbsp;·&nbsp; %s &nbsp;·&nbsp; %s &nbsp;·&nbsp; steepest slope <b>%d%%</b> &nbsp;·&nbsp; dug out <b>%,d</b> and filled in <b>%,d</b> blocks</html>",
                length,
                (bridges > 0) ? String.format("<b>%,d</b> on bridges", bridges) : "no bridges",
                (tunnels > 0) ? String.format("<b>%,d</b> in tunnels", tunnels) : "no tunnels",
                Math.round(steepest * 100),
                Math.round(cutAndFill[0] * planSettings.getWidth()), Math.round(cutAndFill[1] * planSettings.getWidth())));
    }

    private void apply() {
        if (plan == null) {
            return;
        }
        last = planSettings.clone();
        dimension.setEventsInhibited(true);
        dimension.rememberChanges();
        if (view != null) {
            view.setInhibitUpdates(true);
        }
        final PathPlan plan = this.plan;
        final PathSettings settings = planSettings;
        final List<CustomLayer> layers;
        try {
            layers = ProgressDialog.executeTask(this, new ProgressTask<List<CustomLayer>>() {
                @Override
                public String getName() {
                    return "Laying out the path";
                }

                @Override
                public List<CustomLayer> execute(ProgressReceiver progressReceiver) throws ProgressReceiver.OperationCancelled {
                    return PathGenerator.apply(dimension, plan, settings, layerNumber, progressReceiver);
                }
            });
            if (layers == null) {
                // Cancelled
                if (dimension.undoChanges()) {
                    dimension.clearRedo();
                }
                return;
            }
            createdLayers.addAll(layers);
            dimension.armSavePoint();
        } finally {
            if (view != null) {
                view.setInhibitUpdates(false);
            }
            dimension.setEventsInhibited(false);
        }
        ok();
    }

    private final Dimension dimension;
    private final WorldPainterView view;
    private final List<Point> waypoints;
    private final int layerNumber;
    private final JSlider curvatureSlider;
    private final JLabel curvatureLabel, infoLabel;
    private final JComboBox<Crossings> crossingsComboBox;
    private final JSpinner widthSpinner, slopeSpinner, spanSpinner;
    private final JComboBox<Terrain> materialComboBox, borderComboBox;
    private final JCheckBox bordersCheckBox;
    private final JButton applyButton;
    private final MapPreview mapPreview;
    private final ProfilePreview profilePreview;
    private final Timer planTimer;
    private final List<CustomLayer> createdLayers = new ArrayList<>();
    private PathPlan plan;
    private PathSettings planSettings;
    private int planGeneration;

    private static PathSettings last = new PathSettings();

    static final Color GROUND_COLOUR = new Color(0xf97316), BRIDGE_COLOUR = new Color(0x8b5cf6), TUNNEL_COLOUR = new Color(0x737373);
    private static final long serialVersionUID = 1L;

    enum Crossings {
        NATURAL("Natural (follows the land up and down)"), BRIDGES("Bridges over valleys and water"), TUNNELS("Tunnels through hills"), BOTH("Bridges and tunnels");

        Crossings(String displayName) {
            this.displayName = displayName;
        }

        static Crossings of(PathSettings settings) {
            return settings.isBridges() ? (settings.isTunnels() ? BOTH : BRIDGES) : (settings.isTunnels() ? TUNNELS : NATURAL);
        }

        @Override
        public String toString() {
            return displayName;
        }

        private final String displayName;
    }

    /**
     * The route seen from above, over a shaded relief of the land around it.
     */
    private final class MapPreview extends JComponent {
        MapPreview() {
            setPreferredSize(new java.awt.Dimension(360, 280));
        }

        void setPlan(PathPlan plan) {
            this.plan = plan;
            // The area around the route, with a margin
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            for (double[] point: plan.getPoints()) {
                minX = Math.min(minX, point[0]);
                minY = Math.min(minY, point[1]);
                maxX = Math.max(maxX, point[0]);
                maxY = Math.max(maxY, point[1]);
            }
            final double size = Math.max(maxX - minX, maxY - minY) * 1.15 + 32;
            originX = (minX + maxX - size) / 2;
            originY = (minY + maxY - size) / 2;
            this.size = size;
            relief = null;
            repaint();
        }

        private BufferedImage relief(int pixels) {
            if ((relief == null) || (relief.getWidth() != pixels)) {
                relief = new BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_RGB);
                final double scale = size / pixels;
                final boolean dark = ThemeUtils.isDarkTheme();
                for (int py = 0; py < pixels; py++) {
                    for (int px = 0; px < pixels; px++) {
                        final int x = (int) Math.floor(originX + px * scale), y = (int) Math.floor(originY + py * scale);
                        final float h = dimension.getHeightAt(x, y);
                        if (h == -Float.MAX_VALUE) {
                            relief.setRGB(px, py, dark ? 0x18181b : 0xf4f4f5);
                            continue;
                        }
                        if (dimension.getWaterLevelAt(x, y) > h) {
                            relief.setRGB(px, py, 0x5b8fd9);
                            continue;
                        }
                        final float hw = dimension.getHeightAt((int) (x - scale), y), hn = dimension.getHeightAt(x, (int) (y - scale));
                        final double slope = ((hw == -Float.MAX_VALUE) ? 0 : (h - hw)) + ((hn == -Float.MAX_VALUE) ? 0 : (h - hn));
                        final double shade = Math.max(0.55, Math.min(1.2, 0.9 + slope / scale * 0.25));
                        final int base = 0x8fb573;
                        final int r = (int) Math.min(255, ((base >> 16) & 0xff) * shade), g = (int) Math.min(255, ((base >> 8) & 0xff) * shade), b = (int) Math.min(255, (base & 0xff) * shade);
                        relief.setRGB(px, py, (r << 16) | (g << 8) | b);
                    }
                }
            }
            return relief;
        }

        @Override
        protected void paintComponent(Graphics g) {
            final Insets insets = getInsets();
            final int available = Math.min(getWidth() - insets.left - insets.right, getHeight() - insets.top - insets.bottom);
            if ((plan == null) || (available <= 10)) {
                return;
            }
            final int ox = insets.left + (getWidth() - insets.left - insets.right - available) / 2, oy = insets.top;
            final Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.drawImage(relief(available), ox, oy, null);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                final double scale = available / size;
                final List<double[]> points = plan.getPoints();
                final byte[] types = plan.getTypes();
                final float width = (float) Math.max(2.5, planSettings.getWidth() * scale);
                for (int i = 1; i < points.size(); i++) {
                    final byte type = types[i];
                    g2.setColor((type == PathPlan.BRIDGE) ? BRIDGE_COLOUR : ((type == PathPlan.TUNNEL) ? TUNNEL_COLOUR : GROUND_COLOUR));
                    g2.setStroke((type == PathPlan.TUNNEL)
                            ? new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10, new float[] {4, 3}, 0)
                            : new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    final double[] p = points.get(i - 1), q = points.get(i);
                    g2.draw(new java.awt.geom.Line2D.Double(ox + (p[0] - originX) * scale, oy + (p[1] - originY) * scale, ox + (q[0] - originX) * scale, oy + (q[1] - originY) * scale));
                }
                // Start and end
                g2.setStroke(new BasicStroke(2));
                for (double[] point: new double[][] {points.get(0), points.get(points.size() - 1)}) {
                    final double x = ox + (point[0] - originX) * scale, y = oy + (point[1] - originY) * scale;
                    g2.setColor(Color.WHITE);
                    g2.fill(new java.awt.geom.Ellipse2D.Double(x - 5, y - 5, 10, 10));
                    g2.setColor(new Color(0x18181b));
                    g2.draw(new java.awt.geom.Ellipse2D.Double(x - 5, y - 5, 10, 10));
                }
            } finally {
                g2.dispose();
            }
        }

        private PathPlan plan;
        private double originX, originY, size = 1;
        private BufferedImage relief;
        private static final long serialVersionUID = 1L;
    }

    /**
     * The height of the land and of the path along its length, with bridges and tunnels marked.
     */
    private final class ProfilePreview extends JComponent {
        ProfilePreview() {
            setPreferredSize(new java.awt.Dimension(360, 280));
        }

        void setPlan(PathPlan plan) {
            this.plan = plan;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (plan == null) {
                return;
            }
            final Insets insets = getInsets();
            final int x0 = insets.left + 36, y0 = insets.top + 6, w = getWidth() - x0 - insets.right - 8, h = getHeight() - y0 - insets.bottom - 22;
            if ((w < 20) || (h < 20)) {
                return;
            }
            final float[] terrain = plan.getTerrain(), water = plan.getWater(), profile = plan.getProfile();
            final byte[] types = plan.getTypes();
            final int n = terrain.length;
            float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                min = Math.min(min, Math.min(terrain[i], profile[i]));
                max = Math.max(max, Math.max(terrain[i], Math.max(profile[i], Float.isNaN(water[i]) ? -Float.MAX_VALUE : water[i])));
            }
            min -= 4;
            max += 6;
            final float range = Math.max(8, max - min);
            final Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // The land, filled
                final Path2D land = new Path2D.Double();
                land.moveTo(x0, y0 + h);
                for (int i = 0; i < n; i++) {
                    land.lineTo(x0 + (double) i / Math.max(1, n - 1) * w, y0 + h - (terrain[i] - min) / range * h);
                }
                land.lineTo(x0 + w, y0 + h);
                land.closePath();
                g2.setColor(ThemeUtils.isDarkTheme() ? new Color(0x3f6212) : new Color(0xbbd59c));
                g2.fill(land);
                // Water
                g2.setColor(new Color(0x5b8fd9));
                for (int i = 0; i < n - 1; i++) {
                    if (! Float.isNaN(water[i])) {
                        final double xa = x0 + (double) i / Math.max(1, n - 1) * w, xb = x0 + (double) (i + 1) / Math.max(1, n - 1) * w;
                        final double top = y0 + h - (water[i] - min) / range * h, bottom = y0 + h - (terrain[i] - min) / range * h;
                        g2.fill(new java.awt.geom.Rectangle2D.Double(xa, top, xb - xa + 0.5, Math.max(0, bottom - top)));
                    }
                }
                // The path
                g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (int i = 1; i < n; i++) {
                    final byte type = types[i];
                    g2.setColor((type == PathPlan.BRIDGE) ? BRIDGE_COLOUR : ((type == PathPlan.TUNNEL) ? TUNNEL_COLOUR : GROUND_COLOUR));
                    g2.draw(new java.awt.geom.Line2D.Double(x0 + (double) (i - 1) / (n - 1) * w, y0 + h - (profile[i - 1] - min) / range * h, x0 + (double) i / (n - 1) * w, y0 + h - (profile[i] - min) / range * h));
                }
                // Axis labels
                g2.setColor(UIManager.getColor("Label.disabledForeground"));
                g2.setFont(getFont().deriveFont(getFont().getSize2D() - 2f));
                final FontMetrics fm = g2.getFontMetrics();
                g2.drawString("Y " + Math.round(max), insets.left + 2, y0 + fm.getAscent());
                g2.drawString("Y " + Math.round(min), insets.left + 2, y0 + h);
                g2.drawString("0", x0, y0 + h + fm.getAscent() + 2);
                final String total = n + " blocks";
                g2.drawString(total, x0 + w - fm.stringWidth(total), y0 + h + fm.getAscent() + 2);
                // Legend
                int lx = x0 + 40;
                for (Object[] entry: new Object[][] {{"ground", GROUND_COLOUR}, {"bridge", BRIDGE_COLOUR}, {"tunnel", TUNNEL_COLOUR}}) {
                    g2.setColor((Color) entry[1]);
                    g2.fillRect(lx, y0 + h + 6, 10, 4);
                    g2.setColor(UIManager.getColor("Label.foreground"));
                    g2.drawString((String) entry[0], lx + 14, y0 + h + fm.getAscent() + 2);
                    lx += 18 + fm.stringWidth((String) entry[0]) + 12;
                }
            } finally {
                g2.dispose();
            }
        }

        private PathPlan plan;
        private static final long serialVersionUID = 1L;
    }
}
