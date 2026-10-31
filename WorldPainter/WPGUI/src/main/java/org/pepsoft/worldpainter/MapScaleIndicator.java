package org.pepsoft.worldpainter;

import com.formdev.flatlaf.util.UIScale;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.text.NumberFormat;

/**
 * Shows over the map which way is north and how large things are on it: a small needle, and a bar of a round number of
 * blocks whose length follows the zoom level.
 */
public final class MapScaleIndicator extends JComponent {
    public MapScaleIndicator() {
        setOpaque(false);
    }

    /**
     * @param pixelsPerBlock How many pixels of the screen one block of the map takes up.
     */
    public void setPixelsPerBlock(float pixelsPerBlock) {
        this.pixelsPerBlock = pixelsPerBlock;
        repaint();
    }

    @Override
    public java.awt.Dimension getPreferredSize() {
        return new java.awt.Dimension(UIScale.scale(PADDING * 2 + NEEDLE_WIDTH + GAP + MAX_BAR_LENGTH), UIScale.scale(HEIGHT));
    }

    @Override
    public java.awt.Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public java.awt.Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    public boolean contains(int x, int y) {
        // Never in the way of the mouse
        return false;
    }

    @Override
    protected void paintComponent(Graphics g) {
        final Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            final int blocks = roundNumberOfBlocks();
            final int barLength = Math.max(1, Math.round(blocks * pixelsPerBlock));
            final String label = NumberFormat.getIntegerInstance().format(blocks) + ((blocks == 1) ? " block" : " blocks");
            final Font base = UIManager.getFont("Label.font");
            final Font labelFont = base.deriveFont(base.getSize2D() - 2f), northFont = base.deriveFont(Font.BOLD, base.getSize2D() - 2f);
            final FontMetrics metrics = g2.getFontMetrics(labelFont);
            final int padding = UIScale.scale(PADDING), height = UIScale.scale(HEIGHT);
            final int needleWidth = UIScale.scale(NEEDLE_WIDTH), gap = UIScale.scale(GAP);
            final int contentWidth = Math.max(barLength, metrics.stringWidth(label));
            final int width = padding * 2 + needleWidth + gap + contentWidth;

            // Readable on any part of the map, in either theme
            final Color background = colour("Panel.background", Color.DARK_GRAY), foreground = colour("Label.foreground", Color.WHITE);
            g2.setColor(new Color(background.getRed(), background.getGreen(), background.getBlue(), 225));
            g2.fillRect(0, 0, width, height);
            g2.setColor(colour("Component.borderColor", Color.GRAY));
            g2.drawRect(0, 0, width - 1, height - 1);

            // The needle, with an N over it
            g2.setFont(northFont);
            final FontMetrics northMetrics = g2.getFontMetrics();
            g2.setColor(foreground);
            final int needleCentre = padding + needleWidth / 2;
            final int northBaseline = padding + northMetrics.getAscent() - UIScale.scale(1);
            g2.drawString("N", needleCentre - northMetrics.stringWidth("N") / 2, northBaseline);
            final float needleTop = northBaseline + UIScale.scale(2), needleBottom = height - padding;
            final float halfWidth = needleWidth * 0.32f;
            final Path2D needle = new Path2D.Float();
            needle.moveTo(needleCentre, needleTop);
            needle.lineTo(needleCentre + halfWidth, needleBottom);
            needle.lineTo(needleCentre, needleBottom - UIScale.scale(2));
            needle.lineTo(needleCentre - halfWidth, needleBottom);
            needle.closePath();
            g2.setColor(accent());
            g2.fill(needle);

            // The length of the bar in words, and the bar with a small tick at both ends
            final int x = padding + needleWidth + gap;
            g2.setFont(labelFont);
            g2.setColor(foreground);
            g2.drawString(label, x, padding + metrics.getAscent() - UIScale.scale(1));
            final int barY = height - padding - UIScale.scale(1), tick = UIScale.scale(4);
            g2.setStroke(new BasicStroke(UIScale.scale(1.5f), BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
            final Path2D bar = new Path2D.Float();
            bar.moveTo(x, barY - tick);
            bar.lineTo(x, barY);
            bar.lineTo(x + barLength, barY);
            bar.lineTo(x + barLength, barY - tick);
            g2.draw(bar);
        } finally {
            g2.dispose();
        }
    }

    /**
     * The largest round number of blocks (1, 2 or 5 times a power of ten) for which the bar fits.
     */
    private int roundNumberOfBlocks() {
        final double maximum = UIScale.scale(MAX_BAR_LENGTH) / (double) Math.max(pixelsPerBlock, 0.0001f);
        int best = 1;
        for (int magnitude = 1; magnitude <= 100_000_000; magnitude *= 10) {
            for (int factor: new int[] {1, 2, 5}) {
                if (factor * (long) magnitude <= maximum) {
                    best = factor * magnitude;
                }
            }
        }
        return best;
    }

    private static Color accent() {
        Color accent = UIManager.getColor("WorldPainter.accent");
        if (accent == null) {
            accent = UIManager.getColor("Component.accentColor");
        }
        return (accent != null) ? accent : new Color(0xf97316);
    }

    private static Color colour(String key, Color fallback) {
        final Color colour = UIManager.getColor(key);
        return (colour != null) ? colour : fallback;
    }

    private float pixelsPerBlock = 1.0f;

    private static final int PADDING = 6, HEIGHT = 36, NEEDLE_WIDTH = 16, GAP = 8, MAX_BAR_LENGTH = 110;
}
