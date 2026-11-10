package org.pepsoft.worldpainter.composition;

import com.formdev.flatlaf.FlatClientProperties;
import org.pepsoft.worldpainter.ColourScheme;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.TileRenderer;
import org.pepsoft.worldpainter.biomeschemes.Minecraft1_21Biomes;
import org.pepsoft.worldpainter.layers.Layer;
import org.pepsoft.worldpainter.layers.Resources;
import org.pepsoft.worldpainter.layers.TreeLayer;
import org.pepsoft.worldpainter.selection.SelectionBlock;
import org.pepsoft.worldpainter.selection.SelectionChunk;
import org.pepsoft.worldpainter.util.Icons;
import org.pepsoft.worldpainter.util.ThemeUtils;
import org.pepsoft.worldpainter.vanilla.VanillaBiomes;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.*;
import java.util.function.Supplier;

import static org.pepsoft.worldpainter.Constants.TILE_SIZE;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE_BITS;

/**
 * What the land in view is made of, at a glance: where it is, a few key figures, and then section by section its
 * relief, surface, water, biomes, what grows and stands on it, what lies under it and the rock of a column down to the
 * bedrock. It follows the 3D view, the map or the selection, and updates as they move and as the land is edited.
 */
public class CompositionPanel extends JPanel implements Scrollable {
    public CompositionPanel(Supplier<Dimension> dimension, Supplier<ColourScheme> colourScheme, Supplier<Rectangle> mapArea,
                            Supplier<Rectangle> threeDeeArea, Supplier<Point> threeDeeColumn, Supplier<Boolean> threeDeeShown) {
        super(new BorderLayout());
        this.dimension = dimension;
        this.colourScheme = colourScheme;
        this.mapArea = mapArea;
        this.threeDeeArea = threeDeeArea;
        this.threeDeeColumn = threeDeeColumn;
        this.threeDeeShown = threeDeeShown;

        // What to look at: three segmented buttons, with what the chosen one means right under them
        final JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setOpaque(false);
        top.setBorder(BorderFactory.createEmptyBorder(8, 10, 4, 10));
        final JPanel segments = new JPanel(new GridLayout(1, 0, 2, 0));
        segments.setOpaque(false);
        final ButtonGroup group = new ButtonGroup();
        for (Source source: Source.values()) {
            final JToggleButton button = new JToggleButton(source.label, Icons.get(source.icon, 14));
            button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
            button.setFocusable(false);
            button.setToolTipText("<html><b>" + source.label + "</b><br>" + source.description + "</html>");
            button.addActionListener(e -> {
                chosenSource = source;
                refresh(true);
            });
            group.add(button);
            sourceButtons.put(source, button);
            segments.add(button);
        }
        segments.setAlignmentX(LEFT_ALIGNMENT);
        segments.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, segments.getPreferredSize().height));
        top.add(segments);
        sourceExplanation = new JLabel(" ");
        sourceExplanation.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $WorldPainter.mutedForeground");
        sourceExplanation.setAlignmentX(LEFT_ALIGNMENT);
        sourceExplanation.setBorder(BorderFactory.createEmptyBorder(4, 2, 0, 0));
        top.add(sourceExplanation);
        add(top, BorderLayout.NORTH);

        content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(4, 10, 12, 10));
        add(content, BorderLayout.CENTER);
        showMessage("No world", "Open or create a world to see what its land is made of.");

        // Follow the view: look now and then whether it has moved, and work it out again when it has, or every few
        // seconds anyway to show changes to the land
        timer = new javax.swing.Timer(700, e -> refresh(false));
        timer.start();
    }

    // Scrollable: as wide as the area, scrolling up and down

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
        return visibleRect.height - 32;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
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
        final Source source = currentSource();
        sourceExplanation.setText("<html>" + source.description + "</html>");
        if (dimension == null) {
            if ((lastDimension != null) || force) {
                lastDimension = null;
                showMessage("No world", "Open or create a world to see what its land is made of.");
            }
            return;
        }
        final Rectangle area;
        final Point column;
        switch (source) {
            case VIEW_3D:
                area = threeDeeShown.get() ? threeDeeArea.get() : null;
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
            if ((lastArea != null) || (source != lastSource) || force) {
                lastArea = null;
                lastSource = source;
                switch (source) {
                    case SELECTION:
                        showMessage("Nothing is selected", "Select land with the Select tool (or Select with Brush) to see what only that land is made of.");
                        break;
                    case VIEW_3D:
                        showMessage("The 3D view is not open", "Show the 3D view in an area (or with its button in the toolbar) to see what the land it looks at is made of, or choose Map.");
                        break;
                    default:
                        showMessage("The map shows nothing", "Move the map over the land.");
                        break;
                }
            }
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

    private Source currentSource() {
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

    private void showMessage(String title, String text) {
        content.removeAll();
        content.add(Box.createVerticalStrut(24));
        final JLabel icon = new JLabel(Icons.muted("pickaxe", 28));
        icon.setAlignmentX(LEFT_ALIGNMENT);
        content.add(icon);
        content.add(Box.createVerticalStrut(8));
        final JLabel heading = new JLabel(title);
        heading.putClientProperty(FlatClientProperties.STYLE, "font: +2 semibold");
        heading.setAlignmentX(LEFT_ALIGNMENT);
        content.add(heading);
        content.add(paragraph(text, true));
        content.revalidate();
        content.repaint();
    }

    private void show(AreaComposition composition, Dimension dimension, ColourScheme scheme, Source source) {
        content.removeAll();
        if (composition.samples == 0) {
            showMessage((source == Source.SELECTION) ? "Nothing is selected" : "No land here",
                    (source == Source.SELECTION) ? "Select land with the Select tool to see what only that land is made of." : "There is no land in this part of the map.");
            return;
        }
        final Rectangle area = composition.area;

        // Where: a small map of the dimension with the area on it, and its size
        final JPanel where = new JPanel(new BorderLayout(10, 0));
        where.setOpaque(false);
        where.add(new LocationMap(dimension, area, composition.column), BorderLayout.WEST);
        final JPanel whereText = new JPanel();
        whereText.setOpaque(false);
        whereText.setLayout(new BoxLayout(whereText, BoxLayout.Y_AXIS));
        final JLabel size = new JLabel(String.format("%,d × %,d blocks", area.width, area.height));
        size.putClientProperty(FlatClientProperties.STYLE, "font: +1 semibold");
        whereText.add(size);
        whereText.add(muted(String.format("Centred on x %,d, z %,d", area.x + area.width / 2, area.y + area.height / 2)));
        final long columns = (long) composition.samples * composition.step * composition.step;
        whereText.add(muted(String.format("%s of land%s", areaText(columns), (source == Source.SELECTION) ? " selected" : "")));
        if (composition.step > 1) {
            whereText.add(muted(String.format("One column in %d measured", composition.step * composition.step)));
        }
        where.add(whereText, BorderLayout.CENTER);
        where.setAlignmentX(LEFT_ALIGNMENT);
        where.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, where.getPreferredSize().height));
        content.add(where);
        content.add(Box.createVerticalStrut(10));

        // The key figures
        final JPanel figures = new JPanel(new GridLayout(0, 2, 6, 6));
        figures.setOpaque(false);
        int treeCover = 0;
        for (Map.Entry<Layer, int[]> entry: composition.layers.entrySet()) {
            if (isTree(entry.getKey())) {
                treeCover = Math.max(treeCover, entry.getValue()[0]);
            }
        }
        figures.add(new Figure("Average height", "Y " + Math.round(composition.averageHeight), "from " + Math.round(composition.lowest) + " to " + Math.round(composition.highest)));
        figures.add(new Figure("Relief", Math.round(composition.highest - composition.lowest) + " blocks", "lowest to highest"));
        figures.add(new Figure("Average slope", Math.round(composition.slopeSum / composition.samples) + "°", "steepest " + Math.round(composition.steepest) + "°"));
        figures.add(new Figure("Water", percentage(composition.water + composition.lava, composition.samples), (composition.water > 0) ? String.format("%.0f blocks deep at most", composition.deepest) : "dry land"));
        figures.add(new Figure("Tree cover", percentage(treeCover, composition.samples), "of the land has trees"));
        figures.add(new Figure("Biomes", Integer.toString(composition.biomes.size()), (composition.paintedBiomes == 0) ? "all automatic" : (composition.paintedBiomes == composition.samples) ? "all painted" : "some painted"));
        figures.setAlignmentX(LEFT_ALIGNMENT);
        figures.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, figures.getPreferredSize().height));
        content.add(figures);

        // Relief
        final Section relief = section("Relief", "mountain");
        relief.add(subheading("Heights"));
        final HeightHistogram histogram = new HeightHistogram(composition, dimension);
        relief.add(histogram);
        relief.add(subheading("Slopes"));
        final String[] slopeNames = {"Flat, under 10°", "Gentle, 10 to 30°", "Steep, 30 to 50°", "Cliffs, 50° and more"};
        final int[] slopeColours = {TileRenderer.SLOPE_RAMP[0], TileRenderer.SLOPE_RAMP[1], TileRenderer.SLOPE_RAMP[3], TileRenderer.SLOPE_RAMP[4]};
        final List<Share> slopes = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            slopes.add(new Share(slopeNames[i], composition.slopes[i], slopeColours[i]));
        }
        addShares(relief, slopes, composition.samples, 4, false);

        // Surface
        final Section surface = section("Surface", "mountain-snow");
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
            terrains.add(new Share(terrain.getName(), entry.getValue(), colour));
        }
        addShares(surface, terrains, composition.samples, 8, true);

        // Water
        final Section water = section("Water", "droplets");
        if ((composition.water == 0) && (composition.lava == 0)) {
            water.add(paragraph("Dry land: no water or lava.", false));
        } else {
            final List<Share> fluids = new ArrayList<>();
            fluids.add(new Share("Under water", composition.water, 0x3f76e4));
            fluids.add(new Share("Under lava", composition.lava, 0xe8601c));
            fluids.add(new Share("Dry land", composition.samples - composition.water - composition.lava, 0x8fbf6a));
            addShares(water, fluids, composition.samples, 3, false);
            if (composition.water > 0) {
                water.add(paragraph(String.format("The water is %.0f blocks deep on average, %.0f at the deepest.", composition.averageDepth, composition.deepest), true));
            }
        }

        // Biomes
        final Section biomes = section("Biomes", "trees");
        final List<Share> biomeShares = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry: composition.biomes.entrySet()) {
            final int biome = entry.getKey();
            final String name = ((biome < Minecraft1_21Biomes.BIOME_NAMES.length) && (Minecraft1_21Biomes.BIOME_NAMES[biome] != null)) ? Minecraft1_21Biomes.BIOME_NAMES[biome] : ("Biome " + biome);
            biomeShares.add(new Share(name, entry.getValue(), VanillaBiomes.getColour(VanillaBiomes.fromWorldPainterBiome(biome))));
        }
        addShares(biomes, biomeShares, composition.samples, 6, true);
        biomes.add(paragraph((composition.paintedBiomes == 0) ? "All chosen automatically from the terrain."
                : (composition.paintedBiomes == composition.samples) ? "All painted."
                : (percentage(composition.paintedBiomes, composition.samples) + " painted, the rest chosen automatically from the terrain."), true));

        // What grows and stands on the land, by kind
        final Map<String, List<Map.Entry<Layer, int[]>>> onLand = new LinkedHashMap<>();
        onLand.put("Trees", new ArrayList<>());
        onLand.put("Plants and ground cover", new ArrayList<>());
        onLand.put("Other", new ArrayList<>());
        final List<Map.Entry<Layer, int[]>> underground = new ArrayList<>();
        for (Map.Entry<Layer, int[]> entry: composition.layers.entrySet()) {
            final Layer layer = entry.getKey();
            if (AreaComposition.isUnderground(layer)) {
                underground.add(entry);
            } else if (isTree(layer)) {
                onLand.get("Trees").add(entry);
            } else if (isPlant(layer)) {
                onLand.get("Plants and ground cover").add(entry);
            } else {
                onLand.get("Other").add(entry);
            }
        }
        final Section land = section("On the land", "trees");
        boolean anything = false;
        for (Map.Entry<String, List<Map.Entry<Layer, int[]>>> kind: onLand.entrySet()) {
            if (kind.getValue().isEmpty()) {
                continue;
            }
            anything = true;
            land.add(subheading(kind.getKey()));
            kind.getValue().sort(Comparator.comparingInt(entry -> -entry.getValue()[0]));
            for (Map.Entry<Layer, int[]> entry: kind.getValue()) {
                land.add(new LayerRow(entry.getKey(), entry.getValue(), composition.samples));
            }
        }
        if (! anything) {
            land.add(paragraph("Nothing painted on it: bare terrain.", false));
        }

        // Under the land
        final Section under = section("Underground", "pickaxe");
        underground.sort(Comparator.comparingInt(entry -> -entry.getValue()[0]));
        if (underground.isEmpty() && composition.everywhere.isEmpty() && composition.ores.isEmpty()) {
            under.add(paragraph("Solid rock: no caves, tunnels, pockets or ores.", false));
        }
        if (! underground.isEmpty()) {
            under.add(subheading("Painted under this land"));
            for (Map.Entry<Layer, int[]> entry: underground) {
                if (entry.getKey() != Resources.INSTANCE) {
                    under.add(new LayerRow(entry.getKey(), entry.getValue(), composition.samples));
                }
            }
        }
        final List<Layer> everywhere = new ArrayList<>(composition.everywhere);
        everywhere.remove(Resources.INSTANCE);
        if (! everywhere.isEmpty()) {
            under.add(subheading("Everywhere in the world"));
            for (Layer layer: everywhere) {
                final int[] levels = composition.levels.get(layer);
                under.add(paragraph(layer.getName() + ((levels != null) ? (", from Y " + levels[0] + " to Y " + levels[1]) : ""), false));
            }
        }
        if (! composition.ores.isEmpty()) {
            final int[] resources = composition.layers.get(Resources.INSTANCE);
            under.add(subheading(composition.everywhere.contains(Resources.INSTANCE) ? "Ores, everywhere"
                    : ((resources != null) ? ("Ores, where Resources is painted (" + percentage(resources[0], composition.samples) + ")") : "Ores, where Resources is painted (nowhere here)")));
            under.add(new OreTable(composition.ores, scheme));
        }

        // The rock of a column
        if (! composition.rock.isEmpty()) {
            final Section rock = section("Rock column", "layers");
            rock.add(paragraph(String.format("The blocks of the column at x %,d, z %,d%s, from the top down, as they will be exported before the caves and ores.",
                    composition.column.x, composition.column.y, (source == Source.VIEW_3D) ? " (the point the 3D view turns around)" : " (the middle of the area)"), true));
            final RockColumn rockColumn = new RockColumn(composition.rock);
            rockColumn.setAlignmentX(LEFT_ALIGNMENT);
            rock.add(rockColumn);
        }
        content.add(Box.createVerticalGlue());
        content.revalidate();
        content.repaint();
    }

    private static boolean isTree(Layer layer) {
        // (The trees of the tree pack are custom object layers, marked by their description)
        return (layer instanceof TreeLayer) || layer.getClass().getName().contains(".trees.")
                || ((layer.getDescription() != null) && layer.getDescription().endsWith("(WorldPainter tree pack)"));
    }

    private static boolean isPlant(Layer layer) {
        final String name = layer.getClass().getName();
        return name.contains(".plants.") || name.contains(".groundcover.") || name.contains("Frost") || name.contains("GardenCategory");
    }

    // Building blocks

    private Section section(String title, String icon) {
        final Section section = new Section(title, icon);
        section.setAlignmentX(LEFT_ALIGNMENT);
        content.add(Box.createVerticalStrut(12));
        content.add(section);
        return section;
    }

    private static JComponent subheading(String text) {
        final JLabel label = new JLabel(text);
        label.putClientProperty(FlatClientProperties.STYLE, "font: -1 semibold");
        label.setBorder(BorderFactory.createEmptyBorder(6, 0, 2, 0));
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private static JLabel muted(String text) {
        final JLabel label = new JLabel(text);
        label.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $WorldPainter.mutedForeground");
        return label;
    }

    /**
     * A paragraph, which wraps to the width of the panel.
     */
    private static JComponent paragraph(String text, boolean muted) {
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
        area.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        area.setFont(UIManager.getFont("Label.font"));
        if (muted) {
            area.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $WorldPainter.mutedForeground");
        } else {
            area.setForeground(UIManager.getColor("Label.foreground"));
        }
        area.setAlignmentX(LEFT_ALIGNMENT);
        return area;
    }

    /**
     * A bar with the shares of the whole, in their colours, and the largest of them as rows with their percentage.
     */
    private static void addShares(Section section, List<Share> shares, int total, int max, boolean collapseRest) {
        shares.removeIf(share -> share.count == 0);
        shares.sort(Comparator.comparingInt((Share share) -> -share.count));
        final ShareBar bar = new ShareBar(shares, total);
        bar.setAlignmentX(LEFT_ALIGNMENT);
        section.add(bar);
        int shown = 0, rest = 0;
        for (Share share: shares) {
            if ((shown < max) || (! collapseRest)) {
                section.add(new ShareRow(share, total));
                shown++;
            } else {
                rest += share.count;
            }
        }
        if (rest > 0) {
            final JComponent more = paragraph(String.format("and %d more, %s together", shares.size() - max, percentage(rest, total)), true);
            section.add(more);
        }
    }

    private static String percentage(int count, int total) {
        final float percentage = count * 100f / total;
        return (count == 0) ? "0%" : (percentage < 1) ? "under 1%" : (Math.round(percentage) + "%");
    }

    private static String areaText(long columns) {
        return (columns >= 1_000_000) ? String.format("%.1f km²", columns / 1_000_000.0) : String.format("%,d m²", columns);
    }

    private final Supplier<Dimension> dimension;
    private final Supplier<ColourScheme> colourScheme;
    private final Supplier<Rectangle> mapArea, threeDeeArea;
    private final Supplier<Point> threeDeeColumn;
    private final Supplier<Boolean> threeDeeShown;
    private final Map<Source, JToggleButton> sourceButtons = new EnumMap<>(Source.class);
    private final JLabel sourceExplanation;
    private final JPanel content;
    private final javax.swing.Timer timer;
    private Source chosenSource, lastSource;
    private Dimension lastDimension;
    private Rectangle lastArea;
    private long lastTime;
    private SwingWorker<AreaComposition, Void> worker;

    /**
     * Which sections are folded away, by title, for as long as WorldPainter runs.
     */
    private static final Set<String> FOLDED = new HashSet<>();
    private static final long serialVersionUID = 1L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CompositionPanel.class);

    enum Source {
        VIEW_3D("3D view", "box", "The land the 3D view looks at: a square around the point it turns around, as large as the camera is far from it. Moves as you move the 3D view."),
        MAP("Map", "map", "Everything the map shows at the moment. Moves as you scroll and zoom the map."),
        SELECTION("Selection", "square-dashed", "Only the land you selected with the Select tools, wherever it is.");

        Source(String label, String icon, String description) {
            this.label = label;
            this.icon = icon;
            this.description = description;
        }

        final String label, icon, description;
    }

    static final class Share {
        Share(String name, int count, int colour) {
            this.name = name;
            this.count = count;
            this.colour = colour;
        }

        final String name;
        final int count, colour;
    }

    /**
     * A section with a heading which folds it away when clicked.
     */
    static final class Section extends JPanel {
        Section(String title, String iconName) {
            super(new BorderLayout());
            setOpaque(false);
            body = new JPanel();
            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
            body.setOpaque(false);
            body.setBorder(BorderFactory.createEmptyBorder(4, 2, 0, 0));
            final JLabel header = new JLabel(title.toUpperCase(), Icons.muted(iconName, 14), SwingConstants.LEADING);
            header.putClientProperty(FlatClientProperties.STYLE, "font: -1 bold; foreground: $WorldPainter.mutedForeground");
            header.setIconTextGap(6);
            header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            header.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeUtils.getColour("WorldPainter.border", Color.GRAY)),
                    BorderFactory.createEmptyBorder(0, 0, 4, 0)));
            final JLabel chevron = new JLabel();
            chevron.setForeground(ThemeUtils.getColour("WorldPainter.mutedForeground", Color.GRAY));
            final JPanel headerRow = new JPanel(new BorderLayout());
            headerRow.setOpaque(false);
            headerRow.add(header, BorderLayout.CENTER);
            headerRow.add(chevron, BorderLayout.EAST);
            headerRow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            headerRow.setToolTipText("Click to fold or unfold");
            add(headerRow, BorderLayout.NORTH);
            add(body, BorderLayout.CENTER);
            final Runnable update = () -> {
                final boolean folded = FOLDED.contains(title);
                body.setVisible(! folded);
                chevron.setIcon(Icons.muted(folded ? "chevron-right" : "chevron-down", 14));
            };
            update.run();
            headerRow.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (! FOLDED.remove(title)) {
                        FOLDED.add(title);
                    }
                    update.run();
                    revalidate();
                }
            });
        }

        @Override
        public Component add(Component component) {
            if (component instanceof JComponent) {
                ((JComponent) component).setAlignmentX(LEFT_ALIGNMENT);
            }
            return body.add(component);
        }

        @Override
        public java.awt.Dimension getMaximumSize() {
            return new java.awt.Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        private final JPanel body;
        private static final long serialVersionUID = 1L;
    }

    /**
     * A key figure: a large value with what it is above it and a detail under it.
     */
    static final class Figure extends JPanel {
        Figure(String caption, String value, String detail) {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(7, 9, 7, 9));
            final JLabel captionLabel = new JLabel(caption);
            captionLabel.putClientProperty(FlatClientProperties.STYLE, "font: -2; foreground: $WorldPainter.mutedForeground");
            add(captionLabel);
            final JLabel valueLabel = new JLabel(value);
            valueLabel.putClientProperty(FlatClientProperties.STYLE, "font: +4 semibold");
            add(valueLabel);
            final JLabel detailLabel = new JLabel(detail);
            detailLabel.putClientProperty(FlatClientProperties.STYLE, "font: -2; foreground: $WorldPainter.mutedForeground");
            add(detailLabel);
            setToolTipText(caption + ": " + value + " (" + detail + ")");
        }

        @Override
        protected void paintComponent(Graphics g) {
            final Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                final Color background = UIManager.getColor("Panel.background");
                g2.setColor(ThemeUtils.isDarkTheme() ? background.brighter() : darker(background));
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 10, 10));
                g2.setColor(ThemeUtils.getColour("WorldPainter.border", Color.GRAY));
                g2.draw(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 10, 10));
            } finally {
                g2.dispose();
            }
            super.paintComponent(g);
        }

        private static Color darker(Color colour) {
            return new Color(Math.max(0, colour.getRed() - 8), Math.max(0, colour.getGreen() - 8), Math.max(0, colour.getBlue() - 6));
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * A row with the colour, name and share of a part of the whole, and a thin bar for the share.
     */
    static final class ShareRow extends JComponent {
        ShareRow(Share share, int total) {
            this.share = share;
            this.total = total;
            setToolTipText(share.name + ": " + percentage(share.count, total));
        }

        @Override
        public java.awt.Dimension getPreferredSize() {
            return new java.awt.Dimension(200, 20);
        }

        @Override
        public java.awt.Dimension getMaximumSize() {
            return new java.awt.Dimension(Integer.MAX_VALUE, 20);
        }

        @Override
        protected void paintComponent(Graphics g) {
            final Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                final int w = getWidth();
                g2.setColor(new Color(share.colour));
                g2.fillRoundRect(0, 5, 10, 10, 3, 3);
                g2.setColor(ThemeUtils.getColour("WorldPainter.border", Color.GRAY));
                g2.drawRoundRect(0, 5, 10, 10, 3, 3);
                g2.setFont(getFont());
                final FontMetrics fm = g2.getFontMetrics();
                final String percentage = percentage(share.count, total);
                final int percentageWidth = fm.stringWidth(percentage);
                // The share as a faint bar behind the percentage
                final int barRight = w - percentageWidth - 10, barWidth = Math.max(30, Math.min(70, w / 4));
                final int barLeft = barRight - barWidth;
                g2.setColor(ThemeUtils.getColour("WorldPainter.border", Color.GRAY));
                g2.fillRoundRect(barLeft, 9, barWidth, 3, 3, 3);
                g2.setColor(new Color(share.colour));
                g2.fillRoundRect(barLeft, 9, Math.max(2, Math.round(barWidth * (float) share.count / total)), 3, 3, 3);
                g2.setColor(UIManager.getColor("Label.foreground"));
                String name = share.name;
                final int room = barLeft - 24;
                if (fm.stringWidth(name) > room) {
                    while ((name.length() > 1) && (fm.stringWidth(name + "…") > room)) {
                        name = name.substring(0, name.length() - 1);
                    }
                    name = name + "…";
                }
                final int baseline = (20 + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(name, 16, baseline);
                g2.drawString(percentage, w - percentageWidth, baseline);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public Font getFont() {
            return UIManager.getFont("Label.font");
        }

        private final Share share;
        private final int total;
        private static final long serialVersionUID = 1L;
    }

    /**
     * A layer with its icon, name, how much of the land it is on and how strongly.
     */
    static final class LayerRow extends JPanel {
        LayerRow(Layer layer, int[] counts, int total) {
            super(new BorderLayout(6, 0));
            setOpaque(false);
            Icon icon = null;
            try {
                final BufferedImage image = layer.getIcon();
                if (image != null) {
                    icon = new ImageIcon(image.getScaledInstance(16, 16, Image.SCALE_SMOOTH));
                }
            } catch (RuntimeException e) {
                // No icon then
            }
            final JLabel name = new JLabel(layer.getName(), icon, SwingConstants.LEADING);
            add(name, BorderLayout.CENTER);
            final boolean graded = (layer.getDataSize() == Layer.DataSize.NIBBLE) || (layer.getDataSize() == Layer.DataSize.BYTE);
            final String text = "on " + percentage(counts[0], total) + (graded ? (", " + Math.round(counts[1] * 100f / (counts[0] * 15f)) + "% dense") : "");
            final JLabel coverage = muted(text);
            add(coverage, BorderLayout.EAST);
            setToolTipText(layer.getName() + ": " + text + ((layer.getDescription() != null) ? (" — " + layer.getDescription()) : ""));
            setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
        }

        @Override
        public java.awt.Dimension getMaximumSize() {
            return new java.awt.Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * The ores, with the heights between which they are placed and how common they are.
     */
    static final class OreTable extends JPanel {
        OreTable(List<AreaComposition.Ore> ores, ColourScheme scheme) {
            super(new GridBagLayout());
            setOpaque(false);
            final GridBagConstraints c = new GridBagConstraints();
            c.anchor = GridBagConstraints.LINE_START;
            c.insets = new Insets(1, 0, 1, 8);
            c.gridy = 0;
            for (String heading: new String[] {"Block", "From", "To", "How common"}) {
                final JLabel label = muted(heading);
                add(label, c);
            }
            final int mostCommon = ores.stream().mapToInt(ore -> ore.chance).max().orElse(1);
            int count = 0;
            for (AreaComposition.Ore ore: ores) {
                if (count++ == 10) {
                    c.gridy++;
                    c.gridx = 0;
                    c.gridwidth = 4;
                    add(muted("and " + (ores.size() - 10) + " more"), c);
                    break;
                }
                c.gridy++;
                c.gridx = GridBagConstraints.RELATIVE;
                c.gridwidth = 1;
                final String name = ore.material.simpleName.replace('_', ' ');
                final int colour = scheme.getColour(ore.material) & 0xffffff;
                add(new JLabel(Character.toUpperCase(name.charAt(0)) + name.substring(1), swatch(colour), SwingConstants.LEADING), c);
                add(new JLabel("Y " + ore.minLevel), c);
                add(new JLabel("Y " + ore.maxLevel), c);
                final JProgressBar bar = new JProgressBar(0, mostCommon);
                bar.setValue(ore.chance);
                bar.setPreferredSize(new java.awt.Dimension(60, 6));
                bar.setToolTipText(ore.chance + " in 1,000 blocks");
                add(bar, c);
            }
            c.gridy++;
            c.weightx = 1;
            add(Box.createHorizontalGlue(), c);
        }

        @Override
        public java.awt.Dimension getMaximumSize() {
            return new java.awt.Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        private static final long serialVersionUID = 1L;
    }

    private static Icon swatch(int colour) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                final Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(colour));
                    g2.fillRoundRect(x, y + 1, 10, 10, 3, 3);
                    g2.setColor(ThemeUtils.getColour("WorldPainter.border", Color.GRAY));
                    g2.drawRoundRect(x, y + 1, 10, 10, 3, 3);
                } finally {
                    g2.dispose();
                }
            }

            @Override
            public int getIconWidth() {
                return 12;
            }

            @Override
            public int getIconHeight() {
                return 12;
            }
        };
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
            return new java.awt.Dimension(200, 16);
        }

        @Override
        public java.awt.Dimension getMaximumSize() {
            return new java.awt.Dimension(Integer.MAX_VALUE, 16);
        }

        @Override
        protected void paintComponent(Graphics g) {
            final Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                final int w = getWidth() - 1, h = 10;
                final RoundRectangle2D outline = new RoundRectangle2D.Float(0, 3, w, h, h, h);
                g2.setClip(outline);
                float x = 0;
                for (Share share: shares) {
                    final float width = w * (float) share.count / total;
                    g2.setColor(new Color(share.colour));
                    g2.fill(new java.awt.geom.Rectangle2D.Float(x, 3, width + 0.5f, h));
                    x += width;
                }
                g2.setClip(null);
                g2.setColor(ThemeUtils.getColour("WorldPainter.border", Color.GRAY));
                g2.draw(outline);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public String getToolTipText(MouseEvent event) {
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
     * How much of the land lies at each height, as bars in the colours of the Height view, with the level of the water.
     */
    static final class HeightHistogram extends JComponent {
        HeightHistogram(AreaComposition composition, Dimension dimension) {
            this.composition = composition;
            int max = 1;
            for (int count: composition.heightBins) {
                max = Math.max(max, count);
            }
            this.max = max;
            seaLevel = composition.water > 0 ? (int) Math.round(composition.averageDepth + composition.lowest) : Integer.MIN_VALUE;
            setToolTipText("");
        }

        @Override
        public java.awt.Dimension getPreferredSize() {
            return new java.awt.Dimension(200, 70);
        }

        @Override
        public java.awt.Dimension getMaximumSize() {
            return new java.awt.Dimension(Integer.MAX_VALUE, 70);
        }

        @Override
        protected void paintComponent(Graphics g) {
            final Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                final Font small = getFont().deriveFont(getFont().getSize2D() - 2f);
                g2.setFont(small);
                final FontMetrics fm = g2.getFontMetrics();
                final int labelHeight = fm.getHeight();
                final int w = getWidth(), chartHeight = getHeight() - labelHeight - 2;
                final int bins = AreaComposition.HEIGHT_BINS;
                final float barWidth = (float) w / bins;
                for (int i = 0; i < bins; i++) {
                    final int count = composition.heightBins[i];
                    if (count == 0) {
                        continue;
                    }
                    final int barHeight = Math.max(1, Math.round((float) Math.sqrt((double) count / max) * (chartHeight - 2)));
                    g2.setColor(new Color(TileRenderer.ramp(TileRenderer.HEIGHT_RAMP, (i + 0.5f) / bins)));
                    g2.fill(new RoundRectangle2D.Float(i * barWidth + 0.5f, chartHeight - barHeight, Math.max(1, barWidth - 1), barHeight, 3, 3));
                }
                g2.setColor(ThemeUtils.getColour("WorldPainter.border", Color.GRAY));
                g2.drawLine(0, chartHeight, w, chartHeight);
                g2.setColor(ThemeUtils.getColour("WorldPainter.mutedForeground", Color.GRAY));
                final String low = "Y " + Math.round(composition.lowest), high = "Y " + Math.round(composition.highest);
                g2.drawString(low, 0, chartHeight + fm.getAscent() + 1);
                g2.drawString(high, w - fm.stringWidth(high), chartHeight + fm.getAscent() + 1);
                final String middle = "average Y " + Math.round(composition.averageHeight);
                g2.drawString(middle, (w - fm.stringWidth(middle)) / 2, chartHeight + fm.getAscent() + 1);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            final int bins = AreaComposition.HEIGHT_BINS;
            final int i = Math.max(0, Math.min(bins - 1, event.getX() * bins / Math.max(1, getWidth())));
            final float range = Math.max(1, composition.highest - composition.lowest);
            final int from = Math.round(composition.lowest + range * i / bins), to = Math.round(composition.lowest + range * (i + 1) / bins);
            return "Y " + from + " to " + to + ": " + percentage(composition.heightBins[i], composition.samples) + " of the land";
        }

        private final AreaComposition composition;
        private final int max, seaLevel;
        private static final long serialVersionUID = 1L;
    }

    /**
     * A small map of the dimension, with the area which is described on it.
     */
    static final class LocationMap extends JComponent {
        LocationMap(Dimension dimension, Rectangle area, Point column) {
            this.area = area;
            this.column = column;
            extent = new Rectangle(dimension.getLowestX() << TILE_SIZE_BITS, dimension.getLowestY() << TILE_SIZE_BITS,
                    dimension.getWidth() << TILE_SIZE_BITS, dimension.getHeight() << TILE_SIZE_BITS);
            setToolTipText("Where the land described here is in the world");
        }

        @Override
        public java.awt.Dimension getPreferredSize() {
            return new java.awt.Dimension(64, 64);
        }

        @Override
        protected void paintComponent(Graphics g) {
            final Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                final Rectangle all = extent.union(area);
                final double scale = Math.min((getWidth() - 4.0) / all.width, (getHeight() - 4.0) / all.height);
                final double ox = (getWidth() - all.width * scale) / 2 - all.x * scale, oy = (getHeight() - all.height * scale) / 2 - all.y * scale;
                g2.setColor(ThemeUtils.isDarkTheme() ? new Color(255, 255, 255, 24) : new Color(0, 0, 0, 18));
                g2.fill(new RoundRectangle2D.Double(ox + extent.x * scale, oy + extent.y * scale, extent.width * scale, extent.height * scale, 6, 6));
                g2.setColor(ThemeUtils.getColour("WorldPainter.border", Color.GRAY));
                g2.draw(new RoundRectangle2D.Double(ox + extent.x * scale, oy + extent.y * scale, extent.width * scale, extent.height * scale, 6, 6));
                final Color accent = UIManager.getColor("Component.accentColor") != null ? UIManager.getColor("Component.accentColor") : new Color(0x3b82f6);
                final double ax = ox + area.x * scale, ay = oy + area.y * scale, aw = Math.max(3, area.width * scale), ah = Math.max(3, area.height * scale);
                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 70));
                g2.fill(new java.awt.geom.Rectangle2D.Double(ax, ay, aw, ah));
                g2.setColor(accent);
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(new java.awt.geom.Rectangle2D.Double(ax, ay, aw, ah));
                if (column != null) {
                    final double cx = ox + column.x * scale, cy = oy + column.y * scale;
                    g2.fill(new java.awt.geom.Ellipse2D.Double(cx - 2.5, cy - 2.5, 5, 5));
                }
            } finally {
                g2.dispose();
            }
        }

        private final Rectangle area, extent;
        private final Point column;
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
                return Math.max(56, Math.min(96, (int) Math.round(Math.sqrt(band.thickness()) * 8)));
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
                final int top = y;
                for (AreaComposition.Band band: bands) {
                    final int h = rowHeight(band);
                    g2.setColor(new Color(band.colour()));
                    g2.fillRect(2, y, 34, h);
                    g2.setColor(ThemeUtils.getColour("WorldPainter.border", Color.GRAY));
                    g2.drawLine(2, y + h - 1, 35, y + h - 1);
                    final int lines = (band.mixedWith.isEmpty() ? 1 : 2);
                    final int textY = y + Math.max(fm.getAscent() + 3, (h - fm.getHeight() - smallFm.getHeight() * lines) / 2 + fm.getAscent());
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
                g2.drawRoundRect(2, top, 33, y - top - 1, 4, 4);
            } finally {
                g2.dispose();
            }
        }

        private final List<AreaComposition.Band> bands;
        private final int height;
        private static final long serialVersionUID = 1L;
    }
}
