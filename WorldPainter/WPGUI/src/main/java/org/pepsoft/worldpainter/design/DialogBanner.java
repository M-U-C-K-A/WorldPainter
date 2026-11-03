package org.pepsoft.worldpainter.design;

import com.formdev.flatlaf.util.UIScale;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * A band at the top of a window: an illustration of what it is about, its title, what it is for in a sentence, and
 * what the selected tab is for.
 */
public final class DialogBanner extends JComponent {
    public DialogBanner(Illustration illustration, Color accent, String title, String description) {
        this.illustration = illustration;
        this.accent = accent;
        this.title = title;
        this.description = description;
    }

    /**
     * Set a line about the selected tab, under the description.
     */
    public void setDetail(String detail) {
        this.detail = detail;
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        // Room for the title, two lines of description and a line about the tab
        final FontMetrics title = getFontMetrics(Design.font(4f, Font.BOLD)), body = getFontMetrics(Design.font(0f, Font.PLAIN)), detail = getFontMetrics(Design.font(-1f, Font.PLAIN));
        final int height = 2 * UIScale.scale(14) + UIScale.scale(2) + title.getHeight() + UIScale.scale(5) + 2 * (body.getHeight() + UIScale.scale(1)) + UIScale.scale(4) + detail.getHeight();
        return new Dimension(UIScale.scale(400), Math.max(UIScale.scale(MIN_HEIGHT), height));
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(UIScale.scale(200), getPreferredSize().height);
    }

    @Override
    protected void paintComponent(Graphics g) {
        final Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            final int w = getWidth(), h = getHeight();
            // A band tinted with the colour of the window, fading into the background towards the right
            final Color base = Design.background();
            final Color tint = Design.mix(base, accent, Design.isDark() ? 0.2f : 0.12f);
            g2.setPaint(new GradientPaint(0, 0, tint, w * 0.75f, 0, base));
            g2.fillRect(0, 0, w, h);
            g2.setColor(accent);
            g2.fillRect(0, 0, UIScale.scale(4), h);
            g2.setColor(Design.border());
            g2.drawLine(0, h - 1, w, h - 1);

            // The illustration, in a frame of its own
            final int pad = UIScale.scale(14), artWidth = UIScale.scale(ART_WIDTH), artHeight = h - 2 * pad;
            final int artX = UIScale.scale(4) + pad;
            g2.setColor(Design.isDark() ? Design.mix(base, Color.BLACK, 0.25f) : Design.mix(base, Color.WHITE, 0.6f));
            g2.fillRect(artX, pad, artWidth, artHeight);
            illustration.paint(g2, artX + UIScale.scale(6), pad + UIScale.scale(5), artWidth - UIScale.scale(12), artHeight - UIScale.scale(10), accent);
            g2.setColor(Design.mix(Design.border(), accent, 0.4f));
            g2.drawRect(artX, pad, artWidth - 1, artHeight - 1);

            // The words
            final int textX = artX + artWidth + UIScale.scale(18), textWidth = w - textX - pad;
            int y = pad + UIScale.scale(2);
            g2.setFont(Design.font(4f, Font.BOLD));
            g2.setColor(Design.foreground());
            FontMetrics metrics = g2.getFontMetrics();
            y += metrics.getAscent();
            g2.drawString(title, textX, y);
            y += metrics.getDescent() + UIScale.scale(5);
            g2.setFont(Design.font(0f, Font.PLAIN));
            metrics = g2.getFontMetrics();
            g2.setColor(Design.mix(Design.foreground(), base, 0.15f));
            for (String line: wrap(description, metrics, textWidth, 2)) {
                y += metrics.getAscent();
                g2.drawString(line, textX, y);
                y += metrics.getDescent() + UIScale.scale(1);
            }
            if (detail != null) {
                y += UIScale.scale(4);
                g2.setFont(Design.font(-1f, Font.PLAIN));
                metrics = g2.getFontMetrics();
                final List<String> lines = wrap(detail, metrics, textWidth - UIScale.scale(12), 1);
                if (! lines.isEmpty()) {
                    g2.setColor(accent);
                    g2.fillRect(textX, y + UIScale.scale(3), UIScale.scale(3), metrics.getHeight() - UIScale.scale(4));
                    g2.setColor(Design.muted());
                    g2.drawString(lines.get(0), textX + UIScale.scale(10), y + metrics.getAscent());
                }
            }
        } finally {
            g2.dispose();
        }
    }

    /**
     * Break a text into lines no wider than a width, ending the last one allowed with an ellipsis if it doesn't fit.
     */
    static List<String> wrap(String text, FontMetrics metrics, int width, int maxLines) {
        final List<String> lines = new ArrayList<>();
        if ((text == null) || text.isEmpty()) {
            return lines;
        }
        StringBuilder line = new StringBuilder();
        final String[] words = text.split("\\s+");
        for (int i = 0; i < words.length; i++) {
            final String candidate = (line.length() == 0) ? words[i] : (line + " " + words[i]);
            if ((metrics.stringWidth(candidate) > width) && (line.length() > 0)) {
                if (lines.size() == maxLines - 1) {
                    lines.add(ellipsise(line + " " + String.join(" ", java.util.Arrays.copyOfRange(words, i, words.length)), metrics, width));
                    return lines;
                }
                lines.add(line.toString());
                line = new StringBuilder(words[i]);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        lines.add(ellipsise(line.toString(), metrics, width));
        return lines;
    }

    private static String ellipsise(String text, FontMetrics metrics, int width) {
        if (metrics.stringWidth(text) <= width) {
            return text;
        }
        String shorter = text;
        while ((shorter.length() > 1) && (metrics.stringWidth(shorter + "…") > width)) {
            shorter = shorter.substring(0, shorter.length() - 1);
        }
        return shorter.trim() + "…";
    }

    private final Illustration illustration;
    private final Color accent;
    private final String title, description;
    private String detail;

    private static final int MIN_HEIGHT = 96, ART_WIDTH = 128;
}
