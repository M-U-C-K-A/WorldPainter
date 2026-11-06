package org.pepsoft.worldpainter.composition;

import com.formdev.flatlaf.FlatClientProperties;
import org.pepsoft.worldpainter.ColourScheme;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.TileRenderer;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.biomeschemes.Minecraft1_21Biomes;
import org.pepsoft.worldpainter.layers.Layer;
import org.pepsoft.worldpainter.selection.SelectionBlock;
import org.pepsoft.worldpainter.selection.SelectionChunk;
import org.pepsoft.worldpainter.util.ThemeUtils;
import org.pepsoft.worldpainter.vanilla.VanillaBiomes;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.*;
import java.util.function.Supplier;

import static org.pepsoft.worldpainter.Constants.TILE_SIZE;

/**
 * What the land in view is made of, at a glance: the surface (terrain, heights, slopes), the water, the biomes, what is
 * painted on the land and under it, and the rock of a column from the surface down to the bottom of the world. It
 * follows the 3D view, the map or the selection, and updates as they move.
 */
public class CompositionPanel extends JPanel {
    public CompositionPanel(Supplier<Dimension> dimension, Supplier<ColourScheme> colourScheme, Supplier<Rectangle> mapArea,
                            Supplier<Rectangle> threeDeeArea, Supplier<Point> threeDeeColumn, Supplier<Boolean> threeDeeShown) {
        super(new BorderLayout());
        this.dimension = dimension;
        this.colourScheme = colourScheme;
        this.mapArea = mapArea;
        this.threeDeeArea = threeDeeArea;
        this.threeDeeColumn = threeDeeColumn;
        this.threeDeeShown = threeDeeShown;

        final JPanel top = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
        top.setOpaque(false);
        top.setBorder(BorderFactory.createEmptyBorder(6, 8, 2, 8));
        final ButtonGroup group = new ButtonGroup();
        for (Source source: Source.values()) {
            final JToggleButton button = new JToggleButton(source.label);
            button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
            button.setFocusable(false);
            button.setToolTipText(source.description);
            button.addActionListener(e -> {
                chosenSource = source;
                refresh(true);
            });
            group.add(button);
            sourceButtons.put(source, button);
            top.add(button);
        }
        add(top, BorderLayout.NORTH);
        content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(4, 10, 10, 10));
        add(content, BorderLayout.CENTER);
        showMessage("Open a world to see what its land is made of");

        // Follow the view: look now and then whether it has moved, and work it out again when it has, or every few
        // seconds anyway to show changes to the land
        timer = new javax.swing.Timer(700, e -> refresh(false));
        timer.start();
    }

    /**
     * Work out the composition again, now if {@code force} is set, otherwise only if the area has changed or it has
     * been a while.
     */
    private void refresh(boolean force) {
        if ((! isShowing()) || (worker != null)) {
            return;
        }
        final Dimension dimension = this.dimension.get();
        if (dimension == null) {
            if (lastDimension != null) {
                lastDimension = null;
                showMessage("Open a world to see what its land is made of");
            }
            return;
        }
        final Source source = currentSource(dimension);
        final Rectangle area;
        final Point column;
        switch (source) {
            case VIEW_3D:
                area = threeDeeArea.get();
                column = threeDeeColumn.get();
                break;
            case SELECTION:
                area = selectionBounds(dimension);
                column = null;
                break;
            default:
                area = mapArea.get();
                column = null;
                break;
        }
        if ((area == null) || area.isEmpty()) {
            showMessage((source == Source.SELECTION) ? "Nothing is selected" : (source == Source.VIEW_3D) ? "The 3D view shows nothing yet" : "The map shows nothing");
            lastArea = null;
            return;
        }
        final long now = System.currentTimeMillis();
        if ((! force) && (dimension == lastDimension) && area.equals(lastArea) && (source == lastSource) && (now - lastTime < 4000)) {
            return;
        }
        lastDimension = dimension;
        lastArea = area;
        lastSource = source;
        lastTime = now;
        final ColourScheme scheme = colourScheme.get();
        worker = new SwingWorker<AreaComposition, Void>() {
            @Override
            protected AreaComposition doInBackground() {
                return AreaComposition.analyse(dimension, scheme, area, source == Source.SELECTION, column);
            }

            @Override
            protected void done() {
                worker = null;
                try {
                    show(get(), dimension, scheme, source);
                } catch (Exception e) {
                    logger.error("{} while working out the composition of {}", e.getClass().getSimpleName(), area, e);
                }
            }
        };
        worker.execute();
    }

    private Source currentSource(Dimension dimension) {
        final Source source = (chosenSource != null) ? chosenSource : (threeDeeShown.get() ? Source.VIEW_3D : Source.MAP);
        final JToggleButton button = sourceButtons.get(source);
        if (! button.isSelected()) {
            button.setSelected(true);
        }
        return source;
    }

    /**
     * The bounds of the selection, by tile.
     */
    private static Rectangle selectionBounds(Dimension dimension) {
        Rectangle bounds = null;
        for (org.pepsoft.worldpainter.Tile tile: dimension.getTiles()) {
            if (tile.hasLayer(SelectionChunk.INSTANCE) || tile.hasLayer(SelectionBlock.INSTANCE)) {
                final Rectangle tileBounds = new Rectangle(tile.getX() * TILE_SIZE, tile.getY() * TILE_SIZE, TILE_SIZE, TILE_SIZE);
                bounds = (bounds == null) ? tileBounds : bounds.union(tileBounds);
            }
        }
        return bounds;
    }

    private void showMessage(String message) {
        content.removeAll();
        final JLabel label = new JLabel(message);
        label.putClientProperty(FlatClientProperties.STYLE, "foreground: $WorldPainter.mutedForeground");
        label.setAlignmentX(LEFT_ALIGNMENT);
        content.add(label);
        content.revalidate();
        content.repaint();
    }

    private void show(AreaComposition composition, Dimension dimension, ColourScheme scheme, Source source) {
        content.removeAll();
        if (composition.samples == 0) {
            showMessage((source == Source.SELECTION) ? "Nothing is selected" : "There is no land here");
            return;
        }
        final Rectangle area = composition.area;
        addText(String.format("%,d × %,d blocks around x %,d, z %,d, as seen in the %s", area.width, area.height,
                area.x + area.width / 2, area.y + area.height / 2, source.inText), true);

        // The surface
        addHeading("Surface");
        addText(String.format("From Y %d to Y %d, %d on average", Math.round(composition.lowest), Math.round(composition.highest), Math.round(composition.averageHeight)), false);
        final String[] slopeNames = {"Flat", "Gentle", "Steep", "Cliffs"};
        final int[] slopeColours = {TileRenderer.SLOPE_RAMP[0], TileRenderer.SLOPE_RAMP[1], TileRenderer.SLOPE_RAMP[3], TileRenderer.SLOPE_RAMP[4]};
        final List<Share> slopes = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            slopes.add(new Share(slopeNames[i] + ((i == 0) ? " (under 10°)" : (i == 1) ? " (10-30°)" : (i == 2) ? " (30-50°)" : " (50° and more)"), composition.slopes[i], slopeColours[i], null));
        }
        addShares(slopes, composition.samples, 4);
        final List<Share> terrains = new ArrayList<>();
        final org.pepsoft.worldpainter.Platform platform = dimension.getWorld().getPlatform();
        for (Map.Entry<Terrain, Integer> entry: composition.terrains.entrySet()) {
            final Terrain terrain = entry.getKey();
            int colour;
            try {
                colour = terrain.getColour(dimension.getSeed(), 0, 0, 62, 62, platform, scheme) & 0xffffff;
            } catch (RuntimeException e) {
                colour = 0x808080;
            }
            terrains.add(new Share(terrain.getName(), entry.getValue(), colour, null));
        }
        addSubheading("Terrain");
        addShares(terrains, composition.samples, 7);

        // The water
        addHeading("Water");
        if ((composition.water == 0) && (composition.lava == 0)) {
            addText("Dry land: no water or lava", false);
        } else {
            if (composition.water > 0) {
                addText(String.format("Water covers %s of it, %.0f blocks deep on average and %.0f at the deepest", percentage(composition.water, composition.samples), composition.averageDepth, composition.deepest), false);
            }
            if (composition.lava > 0) {
                addText("Lava covers " + percentage(composition.lava, composition.samples) + " of it", false);
            }
        }

        // The biomes
        addHeading("Biomes");
        final List<Share> biomes = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry: composition.biomes.entrySet()) {
            final int biome = entry.getKey();
            final String name = ((biome < Minecraft1_21Biomes.BIOME_NAMES.length) && (Minecraft1_21Biomes.BIOME_NAMES[biome] != null)) ? Minecraft1_21Biomes.BIOME_NAMES[biome] : ("Biome " + biome);
            biomes.add(new Share(name, entry.getValue(), VanillaBiomes.getColour(VanillaBiomes.fromWorldPainterBiome(biome)), null));
        }
        addShares(biomes, composition.samples, 6);
        addText((composition.paintedBiomes == 0) ? "All chosen automatically from the terrain"
                : (composition.paintedBiomes == composition.samples) ? "All painted"
                : (percentage(composition.paintedBiomes, composition.samples) + " painted, the rest chosen automatically from the terrain"), false);

        // What is painted on the land and under it
        final List<Map.Entry<Layer, int[]>> onLand = new ArrayList<>(), underground = new ArrayList<>();
        for (Map.Entry<Layer, int[]> entry: composition.layers.entrySet()) {
            (AreaComposition.isUnderground(entry.getKey()) ? underground : onLand).add(entry);
        }
        final Comparator<Map.Entry<Layer, int[]>> byCoverage = Comparator.comparingInt(entry -> -entry.getValue()[0]);
        onLand.sort(byCoverage);
        underground.sort(byCoverage);
        addHeading("On the land");
        if (onLand.isEmpty()) {
            addText("Nothing painted: bare terrain", false);
        }
        for (Map.Entry<Layer, int[]> entry: onLand) {
            addLayer(entry.getKey(), entry.getValue(), composition.samples);
        }

        addHeading("Underground");
        if (underground.isEmpty() && composition.everywhere.isEmpty()) {
            addText("Solid rock: no caves, tunnels, pockets or ores", false);
        }
        for (Map.Entry<Layer, int[]> entry: underground) {
            addLayer(entry.getKey(), entry.getValue(), composition.samples);
        }
        for (Layer layer: composition.everywhere) {
            if (layer == org.pepsoft.worldpainter.layers.Resources.INSTANCE) {
                continue;
            }
            final int[] levels = composition.levels.get(layer);
            addText(layer.getName() + " everywhere" + ((levels != null) ? (", from Y " + levels[0] + " to Y " + levels[1]) : ""), false);
        }
        if (! composition.ores.isEmpty()) {
            final StringBuilder sb = new StringBuilder(composition.everywhere.contains(org.pepsoft.worldpainter.layers.Resources.INSTANCE) ? "Ores everywhere: " : "Ores where Resources is painted: ");
            int count = 0;
            for (AreaComposition.Ore ore: composition.ores) {
                if (count++ > 0) {
                    sb.append(", ");
                }
                sb.append(ore.material.simpleName.replace('_', ' ')).append(" (Y ").append(ore.minLevel).append(" to ").append(ore.maxLevel).append(')');
                if (count == 8) {
                    sb.append(", …");
                    break;
                }
            }
            addText(sb.toString(), false);
        }

        // The rock of a column
        if (! composition.rock.isEmpty()) {
            addHeading("Rock column");
            addText(String.format("At x %,d, z %,d%s, from the top down, before caves and ores", composition.column.x, composition.column.y,
                    (source == Source.VIEW_3D) ? " (the point the 3D view turns around)" : " (the middle of the area)"), false);
            final RockColumn rockColumn = new RockColumn(composition.rock);
            rockColumn.setAlignmentX(LEFT_ALIGNMENT);
            content.add(rockColumn);
        }
        content.add(Box.createVerticalGlue());
        content.revalidate();
        content.repaint();
    }

    private void addHeading(String text) {
        content.add(Box.createVerticalStrut(10));
        final JLabel label = new JLabel(text.toUpperCase());
        label.putClientProperty(FlatClientProperties.STYLE, "font: -2 bold; foreground: $WorldPainter.mutedForeground");
        label.setAlignmentX(LEFT_ALIGNMENT);
        content.add(label);
        content.add(Box.createVerticalStrut(3));
    }

    private void addSubheading(String text) {
        content.add(Box.createVerticalStrut(4));
        final JLabel label = new JLabel(text);
        label.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        label.setAlignmentX(LEFT_ALIGNMENT);
        content.add(label);
    }

    /**
     * A paragraph, which wraps to the width of the panel.
     */
    private void addText(String text, boolean muted) {
        final JTextArea area = new JTextArea(text) {
            @Override
            public java.awt.Dimension getMaximumSize() {
                return new java.awt.Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        area.setEditable(false);
        area.setFocusable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setBorder(null);
        area.setFont(UIManager.getFont("Label.font"));
        if (muted) {
            area.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $WorldPainter.mutedForeground");
        } else {
            area.setForeground(UIManager.getColor("Label.foreground"));
        }
        area.setAlignmentX(LEFT_ALIGNMENT);
        content.add(area);
    }

    /**
     * A bar with the shares of the whole, in their colours, and the largest of them by name.
     */
    private void addShares(List<Share> shares, int total, int max) {
        shares.removeIf(share -> share.count == 0);
        shares.sort(Comparator.comparingInt((Share share) -> -share.count));
        final ShareBar bar = new ShareBar(shares, total);
        bar.setAlignmentX(LEFT_ALIGNMENT);
        content.add(Box.createVerticalStrut(2));
        content.add(bar);
        int shown = 0, rest = 0;
        for (Share share: shares) {
            if (shown < max) {
                final JLabel label = new JLabel(share.name + "  " + percentage(share.count, total), swatch(share.colour, share.icon), SwingConstants.LEADING);
                label.setAlignmentX(LEFT_ALIGNMENT);
                content.add(label);
                shown++;
            } else {
                rest += share.count;
            }
        }
        if (rest > 0) {
            addText((shares.size() - max) + " more, " + percentage(rest, total) + " together", true);
        }
    }

    private void addLayer(Layer layer, int[] counts, int total) {
        final String strength = (layer.getDataSize() == Layer.DataSize.NIBBLE) || (layer.getDataSize() == Layer.DataSize.BYTE)
                ? (", at " + Math.round(counts[1] * 100f / (counts[0] * 15f)) + "% on average") : "";
        Icon icon = null;
        try {
            final BufferedImage image = layer.getIcon();
            if (image != null) {
                icon = new ImageIcon(image.getScaledInstance(16, 16, Image.SCALE_SMOOTH));
            }
        } catch (RuntimeException e) {
            // No icon then
        }
        final JLabel label = new JLabel(layer.getName() + "  on " + percentage(counts[0], total) + strength, icon, SwingConstants.LEADING);
        label.setAlignmentX(LEFT_ALIGNMENT);
        content.add(label);
    }

    private static String percentage(int count, int total) {
        final float percentage = count * 100f / total;
        return (percentage < 1) ? "under 1%" : (Math.round(percentage) + "%");
    }

    private static Icon swatch(int colour, Icon icon) {
        if (icon != null) {
            return icon;
        }
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                final Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(colour));
                    g2.fillRoundRect(x, y + 1, 12, 12, 4, 4);
                    g2.setColor(ThemeUtils.getColour("WorldPainter.border", Color.GRAY));
                    g2.drawRoundRect(x, y + 1, 11, 11, 4, 4);
                } finally {
                    g2.dispose();
                }
            }

            @Override
            public int getIconWidth() {
                return 14;
            }

            @Override
            public int getIconHeight() {
                return 14;
            }
        };
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        // (Started again when shown; the timer does nothing while it is not)
    }

    private final Supplier<Dimension> dimension;
    private final Supplier<ColourScheme> colourScheme;
    private final Supplier<Rectangle> mapArea, threeDeeArea;
    private final Supplier<Point> threeDeeColumn;
    private final Supplier<Boolean> threeDeeShown;
    private final Map<Source, JToggleButton> sourceButtons = new EnumMap<>(Source.class);
    private final JPanel content;
    private final javax.swing.Timer timer;
    private Source chosenSource, lastSource;
    private Dimension lastDimension;
    private Rectangle lastArea;
    private long lastTime;
    private SwingWorker<AreaComposition, Void> worker;

    private static final long serialVersionUID = 1L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CompositionPanel.class);

    enum Source {
        VIEW_3D("3D view", "3D view", "What the 3D view looks at, around the point it turns around"),
        MAP("Map", "map", "What the map shows"),
        SELECTION("Selection", "selection", "What is selected");

        Source(String label, String inText, String description) {
            this.label = label;
            this.inText = inText;
            this.description = description;
        }

        final String label, inText, description;
    }

    static final class Share {
        Share(String name, int count, int colour, Icon icon) {
            this.name = name;
            this.count = count;
            this.colour = colour;
            this.icon = icon;
        }

        final String name;
        final int count, colour;
        final Icon icon;
    }

    /**
     * A bar divided into the shares of a whole, each in its colour.
     */
    static final class ShareBar extends JComponent {
        ShareBar(List<Share> shares, int total) {
            this.shares = shares;
            this.total = total;
            setToolTipText("");
        }

        @Override
        public java.awt.Dimension getPreferredSize() {
            return new java.awt.Dimension(200, 14);
        }

        @Override
        public java.awt.Dimension getMaximumSize() {
            return new java.awt.Dimension(Integer.MAX_VALUE, 14);
        }

        @Override
        protected void paintComponent(Graphics g) {
            final Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                final int w = getWidth() - 1, h = 10;
                g2.setClip(new java.awt.geom.RoundRectangle2D.Float(0, 1, w, h, 6, 6));
                float x = 0;
                for (Share share: shares) {
                    final float width = w * (float) share.count / total;
                    g2.setColor(new Color(share.colour));
                    g2.fill(new java.awt.geom.Rectangle2D.Float(x, 1, width + 0.5f, h));
                    x += width;
                }
                g2.setClip(null);
                g2.setColor(ThemeUtils.getColour("WorldPainter.border", Color.GRAY));
                g2.draw(new java.awt.geom.RoundRectangle2D.Float(0, 1, w, h, 6, 6));
            } finally {
                g2.dispose();
            }
        }

        @Override
        public String getToolTipText(java.awt.event.MouseEvent event) {
            float x = 0;
            final int w = getWidth() - 1;
            for (Share share: shares) {
                final float width = w * (float) share.count / total;
                if ((event.getX() >= x) && (event.getX() < x + width)) {
                    return share.name + ": " + percentage(share.count, total);
                }
                x += width;
            }
            return null;
        }

        private final List<Share> shares;
        private final int total;
        private static final long serialVersionUID = 1L;
    }

    /**
     * The bands of rock of a column, from the top down, each with its name and heights: thin bands are drawn a little
     * thicker than they are, so that they can be seen and named.
     */
    static final class RockColumn extends JComponent {
        RockColumn(List<AreaComposition.Band> bands) {
            this.bands = bands;
            int total = 0;
            for (AreaComposition.Band band: bands) {
                total += rowHeight(band);
            }
            height = total + 4;
        }

        private static int rowHeight(AreaComposition.Band band) {
            if (! band.mixedWith.isEmpty()) {
                return Math.max(50, Math.min(96, (int) Math.round(Math.sqrt(band.thickness()) * 8)));
            }
            return Math.max(34, Math.min(96, (int) Math.round(Math.sqrt(band.thickness()) * 8)));
        }

        @Override
        public java.awt.Dimension getPreferredSize() {
            return new java.awt.Dimension(240, height);
        }

        @Override
        public java.awt.Dimension getMaximumSize() {
            return new java.awt.Dimension(Integer.MAX_VALUE, height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            final Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                final FontMetrics fm = g2.getFontMetrics(getFont());
                final Font small = getFont().deriveFont(getFont().getSize2D() - 1.5f);
                final FontMetrics smallFm = g2.getFontMetrics(small);
                final Color text = UIManager.getColor("Label.foreground"), muted = ThemeUtils.getColour("WorldPainter.mutedForeground", Color.GRAY);
                int y = 2;
                for (AreaComposition.Band band: bands) {
                    final int h = rowHeight(band);
                    g2.setColor(new Color(band.colour()));
                    g2.fillRect(2, y, 34, h);
                    g2.setColor(ThemeUtils.getColour("WorldPainter.border", Color.GRAY));
                    g2.drawLine(2, y + h - 1, 35, y + h - 1);
                    final int textY = y + Math.max(fm.getAscent(), (h - fm.getHeight() - smallFm.getHeight()) / 2 + fm.getAscent());
                    g2.setFont(getFont());
                    g2.setColor(text);
                    g2.drawString(band.kind, 44, textY);
                    g2.setFont(small);
                    g2.setColor(muted);
                    final String extent = (band.top == band.bottom) ? ("Y " + band.top + ", 1 block")
                            : ("Y " + band.top + " to " + band.bottom + ", " + band.thickness() + " blocks");
                    g2.drawString(extent, 44, textY + smallFm.getHeight());
                    if ((! band.mixedWith.isEmpty()) && (h >= fm.getHeight() + smallFm.getHeight() * 2)) {
                        String mix = (band.kind.endsWith("(top layer)") ? "" : "with ") + String.join(", ", band.mixedWith);
                        final int room = getWidth() - 48;
                        if (smallFm.stringWidth(mix) > room) {
                            String cut = mix;
                            while ((cut.length() > 1) && (smallFm.stringWidth(cut + "…") > room)) {
                                cut = cut.substring(0, cut.length() - 1);
                            }
                            mix = cut.replaceAll("[, ]+$", "") + "…";
                        }
                        g2.drawString(mix, 44, textY + smallFm.getHeight() * 2);
                    }
                    y += h;
                }
                g2.setColor(ThemeUtils.getColour("WorldPainter.border", Color.GRAY));
                g2.drawRect(2, 2, 33, y - 3);
            } finally {
                g2.dispose();
            }
        }

        private final List<AreaComposition.Band> bands;
        private final int height;
        private static final long serialVersionUID = 1L;
    }
}
