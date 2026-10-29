package org.pepsoft.worldpainter.help;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * A rich tooltip for a tool: a card with a looping animation of what the tool does, its name, its keyboard shortcut
 * and a short explanation. Buttons created with {@link #button()} or {@link #toggleButton()} show the card set on
 * them with {@link #install(AbstractButton, ToolCard)} instead of a plain tooltip.
 */
public final class ToolCard {
    public ToolCard(String title, String description, String shortcut, ToolDemo demo) {
        this.title = title;
        this.description = description;
        this.shortcut = shortcut;
        this.demo = demo;
    }

    public String getTitle() {
        return title;
    }

    /**
     * Show a card as the tooltip of a button (which must have been created by this class). The plain text of the
     * tooltip is kept for accessibility.
     */
    public static void install(AbstractButton button, ToolCard card) {
        button.putClientProperty(KEY_CARD, card);
        if (button.getToolTipText() == null) {
            button.setToolTipText(card.title);
        }
    }

    public static JButton button() {
        return new JButton() {
            @Override
            public JToolTip createToolTip() {
                final ToolCard card = (ToolCard) getClientProperty(KEY_CARD);
                return (card != null) ? new CardToolTip(this, card) : super.createToolTip();
            }
        };
    }

    public static JToggleButton toggleButton() {
        return new JToggleButton() {
            @Override
            public JToolTip createToolTip() {
                final ToolCard card = (ToolCard) getClientProperty(KEY_CARD);
                return (card != null) ? new CardToolTip(this, card) : super.createToolTip();
            }
        };
    }

    private final String title, description, shortcut;
    private final ToolDemo demo;

    private static final String KEY_CARD = "org.pepsoft.worldpainter.help.toolCard";

    /**
     * The card itself. It paints everything itself, and animates the demo while it is showing.
     */
    private static final class CardToolTip extends JToolTip {
        CardToolTip(JComponent component, ToolCard card) {
            this.card = card;
            setComponent(component);
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder());
            timer = new Timer(33, e -> repaint());
            addHierarchyListener(e -> {
                if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                    final ToolTipManager manager = ToolTipManager.sharedInstance();
                    if (isShowing()) {
                        start = System.currentTimeMillis();
                        timer.start();
                        // Leave time to watch the animation
                        previousDismissDelay = manager.getDismissDelay();
                        manager.setDismissDelay(Math.max(previousDismissDelay, 60_000));
                    } else {
                        timer.stop();
                        if (previousDismissDelay > 0) {
                            manager.setDismissDelay(previousDismissDelay);
                        }
                    }
                }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            final Insets insets = getInsets();
            return new Dimension(s(WIDTH) + insets.left + insets.right, cardLayout().height + insets.top + insets.bottom);
        }

        @Override
        protected void paintComponent(Graphics g) {
            final Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                final Color background = colour("Panel.background", new Color(0x18181b)), foreground = colour("Label.foreground", Color.WHITE);
                final Color muted = colour("WorldPainter.mutedForeground", Color.GRAY), border = colour("Component.borderColor", new Color(0x3f3f46));
                g2.setColor(background);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(border);
                g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);

                // The animation, in a rounded frame
                final Layout layout = cardLayout();
                if (card.demo != null) {
                    final Graphics2D preview = (Graphics2D) g2.create(s(PADDING), s(PADDING), s(WIDTH) - 2 * s(PADDING), s(PREVIEW_HEIGHT));
                    try {
                        preview.clip(new RoundRectangle2D.Float(0, 0, s(WIDTH) - 2 * s(PADDING), s(PREVIEW_HEIGHT), 10, 10));
                        final double time = ((System.currentTimeMillis() - start) % LOOP) / (double) LOOP;
                        card.demo.paint(preview, s(WIDTH) - 2 * s(PADDING), s(PREVIEW_HEIGHT), time);
                    } finally {
                        preview.dispose();
                    }
                    g2.setColor(border);
                    g2.draw(new RoundRectangle2D.Float(s(PADDING), s(PADDING), s(WIDTH) - 2 * s(PADDING) - 1, s(PREVIEW_HEIGHT) - 1, 10, 10));
                }

                // The title, with the shortcut on the right
                int y = layout.titleY;
                g2.setFont(titleFont());
                g2.setColor(foreground);
                g2.drawString(card.title, s(PADDING), y);
                if (card.shortcut != null) {
                    g2.setFont(shortcutFont());
                    final FontMetrics metrics = g2.getFontMetrics();
                    final int chipWidth = metrics.stringWidth(card.shortcut) + 10, chipHeight = metrics.getHeight() + 2;
                    final int chipX = s(WIDTH) - s(PADDING) - chipWidth, chipY = y - metrics.getAscent() - 2;
                    g2.setColor(colour("WorldPainter.subtleBackground", new Color(0x27272a)));
                    g2.fillRoundRect(chipX, chipY, chipWidth, chipHeight, 6, 6);
                    g2.setColor(border);
                    g2.drawRoundRect(chipX, chipY, chipWidth - 1, chipHeight - 1, 6, 6);
                    g2.setColor(muted);
                    g2.drawString(card.shortcut, chipX + 5, y);
                }

                // The explanation
                g2.setFont(bodyFont());
                g2.setColor(muted);
                final FontMetrics metrics = g2.getFontMetrics();
                y = layout.bodyY;
                for (String line: layout.lines) {
                    g2.drawString(line, s(PADDING), y);
                    y += metrics.getHeight();
                }
            } finally {
                g2.dispose();
            }
        }

        private Layout cardLayout() {
            final FontMetrics titleMetrics = getFontMetrics(titleFont()), bodyMetrics = getFontMetrics(bodyFont());
            final Layout layout = new Layout();
            int y = s(PADDING) + ((card.demo != null) ? s(PREVIEW_HEIGHT) + 10 : 0);
            layout.titleY = y + titleMetrics.getAscent();
            y += titleMetrics.getHeight() + 4;
            layout.lines = wrap(stripHtml(card.description), bodyMetrics, s(WIDTH) - 2 * s(PADDING));
            layout.bodyY = y + bodyMetrics.getAscent();
            y += layout.lines.size() * bodyMetrics.getHeight() + s(PADDING) - 2;
            layout.height = y;
            return layout;
        }

        private Font titleFont() {
            return getFont().deriveFont(Font.BOLD, getFont().getSize2D() + 1);
        }

        private Font bodyFont() {
            return getFont().deriveFont(Font.PLAIN);
        }

        private Font shortcutFont() {
            return getFont().deriveFont(Font.PLAIN, getFont().getSize2D() - 1);
        }

        private static List<String> wrap(String text, FontMetrics metrics, int width) {
            final List<String> lines = new ArrayList<>();
            if ((text == null) || text.isEmpty()) {
                return lines;
            }
            StringBuilder line = new StringBuilder();
            for (String word: text.split("\\s+")) {
                if ((line.length() > 0) && (metrics.stringWidth(line + " " + word) > width)) {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    if (line.length() > 0) {
                        line.append(' ');
                    }
                    line.append(word);
                }
            }
            if (line.length() > 0) {
                lines.add(line.toString());
            }
            return lines;
        }

        private static String stripHtml(String text) {
            return (text != null) ? text.replaceAll("<[^>]*>", "").replace("&nbsp;", " ").replace("&amp;", "&").trim() : null;
        }

        private static Color colour(String key, Color fallback) {
            final Color colour = UIManager.getColor(key);
            return (colour != null) ? colour : fallback;
        }

        private final ToolCard card;
        private final Timer timer;
        private long start;
        private int previousDismissDelay;

        private static final class Layout {
            int titleY, bodyY, height;
            List<String> lines;
        }

        private static final int WIDTH = 280, PADDING = 10, PREVIEW_HEIGHT = 140, LOOP = 4200;

        /**
         * Scale a size in pixels to the scale of the user interface.
         */
        private static int s(int size) {
            return com.formdev.flatlaf.util.UIScale.scale(size);
        }
    }
}
