package org.pepsoft.worldpainter.vanilla;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.WorldPainterDialog;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.pepsoft.worldpainter.Constants.TILE_SIZE_BITS;

/**
 * Searches, on all processor cores, for world seeds for which the terrain Minecraft generates around the map matches
 * the biomes along the edges of the map, showing the best candidates found so far with a preview.
 */
public class SeedSearchDialog extends WorldPainterDialog {
    public SeedSearchDialog(Window parent, Dimension dimension, boolean largeBiomes, long currentSeed) {
        super(parent);
        this.dimension = dimension;
        this.largeBiomes = largeBiomes;
        matcher = new SeedMatcher(dimension, largeBiomes);
        setTitle("Find a Matching Seed");

        final JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBorder(new EmptyBorder(12, 12, 12, 12));
        final JLabel intro = new JLabel("<html><div style=\"width: 520px\">Looking for world seeds for which the land Minecraft generates around the map matches the biomes along its edges, so that the map blends in with its surroundings. "
                + "The best candidates found so far are listed below; select one to preview it.</div></html>");
        content.add(intro, BorderLayout.NORTH);

        listModel = new DefaultListModel<>();
        resultList = new JList<>(listModel);
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                final Result result = (Result) value;
                setText(String.format("<html><b>%d%%</b> match &nbsp; <font color=\"#a1a1aa\">seed %d%s</font></html>", Math.round(result.score * 100), result.seed, result.current ? " \u00b7 current" : ""));
                setBorder(new EmptyBorder(6, 8, 6, 8));
                return this;
            }
        });
        resultList.addListSelectionListener(e -> {
            if (! e.getValueIsAdjusting()) {
                updatePreview();
            }
        });
        final JScrollPane listScrollPane = new JScrollPane(resultList);
        listScrollPane.setPreferredSize(new java.awt.Dimension(340, 300));
        preview = new JLabel("", SwingConstants.CENTER);
        preview.setPreferredSize(new java.awt.Dimension(300, 300));
        final JPanel centre = new JPanel(new BorderLayout(12, 0));
        centre.add(listScrollPane, BorderLayout.WEST);
        centre.add(preview, BorderLayout.CENTER);
        content.add(centre, BorderLayout.CENTER);

        statusLabel = new JLabel(" ");
        final JPanel bottom = new JPanel(new BorderLayout(12, 0));
        blendCheckBox = new JCheckBox("Then blend the edges of the map into the land of this seed (100 blocks)", lastBlend);
        blendCheckBox.setToolTipText("Add 100 blocks around the map which go over smoothly into the height, biomes and surface Minecraft generates for the seed beyond them");
        bottom.add(blendCheckBox, BorderLayout.NORTH);
        bottom.add(statusLabel, BorderLayout.CENTER);
        final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING, 6, 0));
        stopButton = new JButton("Stop");
        stopButton.addActionListener(e -> stopSearch());
        final JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> cancel());
        useButton = new JButton("Use Seed");
        useButton.setEnabled(false);
        useButton.addActionListener(e -> {
            final Result result = resultList.getSelectedValue();
            if (result != null) {
                selectedSeed = result.seed;
                lastBlend = blendCheckBox.isSelected();
                stopSearch();
                ok();
            }
        });
        buttons.add(stopButton);
        buttons.add(cancelButton);
        buttons.add(useButton);
        getRootPane().setDefaultButton(useButton);
        bottom.add(buttons, BorderLayout.EAST);
        content.add(bottom, BorderLayout.SOUTH);
        setContentPane(content);

        // Include the current seed for comparison
        addResult(new Result(currentSeed, matcher.score(currentSeed), true));

        timer = new Timer(250, e -> refresh());
        scaleToUI();
        pack();
        scaleWindowToUI();
        setLocationRelativeTo(parent);
        startSearch();
    }

    /**
     * The seed chosen by the user, if the dialog was not cancelled.
     */
    /**
     * Whether the user wants the edges of the map blended into the land of the chosen seed.
     */
    public boolean isBlendEdges() {
        return blendCheckBox.isSelected();
    }

    public long getSelectedSeed() {
        return selectedSeed;
    }

    @Override
    protected void cancel() {
        stopSearch();
        super.cancel();
    }

    private void startSearch() {
        // Half of the cores, so that the computer stays responsive during a long search
        final int threads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        executor = Executors.newFixedThreadPool(threads, runnable -> {
            final Thread thread = new Thread(runnable, "Seed search");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                final ThreadLocalRandom random = ThreadLocalRandom.current();
                while (! stopped.get()) {
                    final long seed = random.nextLong();
                    final float threshold = currentThreshold();
                    final float score = matcher.score(new VanillaBiomeSource(seed, largeBiomes), threshold);
                    tested.incrementAndGet();
                    if (score > threshold) {
                        synchronized (candidates) {
                            candidates.add(new Result(seed, score, false));
                        }
                    }
                }
                return null;
            });
        }
        executor.shutdown();
        timer.start();
    }

    private void stopSearch() {
        stopped.set(true);
        stopButton.setEnabled(false);
    }

    /**
     * The score a new candidate must beat to make it into the list.
     */
    private float currentThreshold() {
        synchronized (best) {
            return (best.size() < MAX_RESULTS) ? 0.0f : best.get(best.size() - 1).score;
        }
    }

    private void refresh() {
        final List<Result> newCandidates;
        synchronized (candidates) {
            newCandidates = new ArrayList<>(candidates);
            candidates.clear();
        }
        newCandidates.forEach(this::addResult);
        final boolean done = stopped.get() || executor.isTerminated();
        final long now = System.nanoTime(), count = tested.get();
        if (now - rateTime > 1_000_000_000L) {
            rate = (count - rateCount) * 1_000_000_000L / (now - rateTime);
            rateTime = now;
            rateCount = count;
        }
        statusLabel.setText(String.format("%s %,d seeds tested%s%s", done ? "Stopped:" : "Searching…", count, done ? "" : String.format(" (%,d/s)", rate), best.isEmpty() ? "" : String.format(" · best match %d%%", Math.round(best.get(0).score * 100))));
        if (done && executor.isTerminated()) {
            timer.stop();
            stopButton.setEnabled(false);
        }
    }

    private void addResult(Result result) {
        synchronized (best) {
            int index = 0;
            while ((index < best.size()) && (best.get(index).score >= result.score)) {
                index++;
            }
            if (index >= MAX_RESULTS) {
                return;
            }
            best.add(index, result);
            if (best.size() > MAX_RESULTS) {
                best.remove(best.size() - 1);
            }
        }
        final Result selected = resultList.getSelectedValue();
        listModel.clear();
        synchronized (best) {
            best.forEach(listModel::addElement);
        }
        if (selected != null) {
            resultList.setSelectedValue(selected, false);
        } else if (! listModel.isEmpty()) {
            resultList.setSelectedIndex(0);
        }
    }

    private void updatePreview() {
        final Result result = resultList.getSelectedValue();
        useButton.setEnabled((result != null) && (! result.current));
        if (result == null) {
            preview.setIcon(null);
            return;
        }
        preview.setIcon(new ImageIcon(renderPreview(result.seed)));
    }

    /**
     * Render the map's footprint with the biomes Minecraft generates around it.
     */
    private BufferedImage renderPreview(long seed) {
        final VanillaBiomeSource source = new VanillaBiomeSource(seed, largeBiomes);
        final Rectangle extent = dimension.getExtent();
        final int margin = 320;
        final int x0 = (extent.x << TILE_SIZE_BITS) - margin, y0 = (extent.y << TILE_SIZE_BITS) - margin;
        final int width = (extent.width << TILE_SIZE_BITS) + 2 * margin, height = (extent.height << TILE_SIZE_BITS) + 2 * margin;
        final int size = 280;
        final double step = Math.max(width, height) / (double) size;
        final BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                final int x = (int) (x0 + px * step), y = (int) (y0 + py * step);
                if ((x >= x0 + width) || (y >= y0 + height)) {
                    continue;
                }
                final boolean onMap = dimension.isTilePresent(x >> TILE_SIZE_BITS, y >> TILE_SIZE_BITS);
                final int colour = VanillaBiomes.getColour(source.getSurfaceBiomeAtBlock(x, y));
                image.setRGB(px, py, (onMap ? 0x50000000 : 0xff000000) | colour);
            }
        }
        // Outline the map
        final Graphics2D g2 = image.createGraphics();
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(2));
        g2.drawRect((int) (margin / step), (int) (margin / step), (int) ((extent.width << TILE_SIZE_BITS) / step), (int) ((extent.height << TILE_SIZE_BITS) / step));
        g2.dispose();
        return image;
    }

    private final Dimension dimension;
    private final boolean largeBiomes;
    private final SeedMatcher matcher;
    private final DefaultListModel<Result> listModel;
    private final JList<Result> resultList;
    private final JLabel preview, statusLabel;
    private final JButton stopButton, useButton;
    private final JCheckBox blendCheckBox;
    private static boolean lastBlend;
    private final Timer timer;
    private final List<Result> best = new ArrayList<>(), candidates = new ArrayList<>();
    private final AtomicBoolean stopped = new AtomicBoolean();
    private final AtomicLong tested = new AtomicLong();
    private ExecutorService executor;
    private long selectedSeed;

    private static final int MAX_RESULTS = 8;
    private long rate, rateCount, rateTime = System.nanoTime();
    private static final long serialVersionUID = 1L;

    private static final class Result {
        Result(long seed, float score, boolean current) {
            this.seed = seed;
            this.score = score;
            this.current = current;
        }

        @Override
        public boolean equals(Object o) {
            return (o instanceof Result) && (((Result) o).seed == seed);
        }

        @Override
        public int hashCode() {
            return Long.hashCode(seed);
        }

        final long seed;
        final float score;
        final boolean current;
    }
}
