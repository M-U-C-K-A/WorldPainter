package org.pepsoft.worldpainter.help;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * A rich tooltip: a card with an optional preview (such as a looping animation of what a tool does), a title, a
 * keyboard shortcut, a short explanation, and optionally a list of rows (such as the blocks of a mix of materials, with
 * their proportions). Buttons created with {@link #button()} or {@link #toggleButton()} show the card set on them with
 * {@link #install(AbstractButton, ToolCard)} instead of a plain tooltip.
 */
public final class ToolCard {
    public ToolCard(String title, String description, String shortcut, ToolDemo demo) {
        this(title, description, shortcut, (demo != null) ? demo::paint : null, null);
    }

    public ToolCard(String title, String description, String shortcut, Preview preview, List<Row> rows) {
        this.title = title;
        this.description = description;
        this.shortcut = shortcut;
        this.preview = preview;
        this.rows = (rows != null) ? rows : Collections.emptyList();
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

    /**
     * Show a card as the tooltip of a button (which must have been created by this class), worked out each time it is
     * shown, for things which can change. The supplier may return {@code null} for a plain tooltip.
     */
    public static void install(AbstractButton button, Supplier<ToolCard> cardSupplier) {
        button.putClientProperty(KEY_CARD, cardSupplier);
    }

    public static JButton button() {
        return new JButton() {
            @Override
            public JToolTip createToolTip() {
                final ToolCard card = cardOf(this);
                return (card != null) ? new CardToolTip(this, card) : super.createToolTip();
            }
        };
    }

    public static JToggleButton toggleButton() {
        return new JToggleButton() {
            @Override
            public JToolTip createToolTip() {
                final ToolCard card = cardOf(this);
                return (card != null) ? new CardToolTip(this, card) : super.createToolTip();
            }
        };
    }

    /**
     * The card tooltip for a component on which a card was installed, for components not created by this class which
     * override {@link JComponent#createToolTip()} themselves, or {@code null} if it has none.
     */
    public static JToolTip toolTipFor(JComponent component) {
        final ToolCard card = cardOf(component);
        return (card != null) ? new CardToolTip(component, card) : null;
    }

    @SuppressWarnings("unchecked") // Guaranteed by install()
    private static ToolCard cardOf(JComponent component) {
        final Object card = component.getClientProperty(KEY_CARD);
        if (card instanceof ToolCard) {
            return (ToolCard) card;
        } else if (card instanceof Supplier) {
            return ((Supplier<ToolCard>) card).get();
        } else {
            return null;
        }
    }

    private final String title, description, shortcut;
    private final Preview preview;
    private final List<Row> rows;

    private static final String KEY_CARD = "org.pepsoft.worldpainter.help.toolCard";

    /**
     * Something drawn at the top of a card, which may change with the time.
     */
    public interface Preview {
        /**
         * @param time The time in a loop of a few seconds, from zero to one.
         */
        void paint(Graphics2D g2, int width, int height, double time);
    }

    /**
     * A row of a card: an icon, a name and a value, such as a block of a mix and its proportion.
     */
    public static final class Row {
        public Row(Icon icon, String name, String value) {
            this.icon = icon;
            this.name = name;
            this.value = value;
        }

        final Icon icon;
        final String name, value;
    }

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
                if (card.preview != null) {
                    final Graphics2D preview = (Graphics2D) g2.create(s(PADDING), s(PADDING), s(WIDTH) - 2 * s(PADDING), s(PREVIEW_HEIGHT));
                    try {
                        preview.clip(new RoundRectangle2D.Float(0, 0, s(WIDTH) - 2 * s(PADDING), s(PREVIEW_HEIGHT), 10, 10));
                        final double time = ((System.currentTimeMillis() - start) % LOOP) / (double) LOOP;
                        card.preview.paint(preview, s(WIDTH) - 2 * s(PADDING), s(PREVIEW_HEIGHT), time);
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

                // The rows: an icon, a name and a value
                if (! card.rows.isEmpty()) {
                    g2.setColor(border);
                    g2.drawLine(s(PADDING), layout.rowsY - s(4), getWidth() - s(PADDING), layout.rowsY - s(4));
                    final FontMetrics rowMetrics = g2.getFontMetrics(bodyFont());
                    int rowY = layout.rowsY;
                    for (Row row: card.rows) {
                        final int iconSize = s(ROW_HEIGHT) - s(6);
                        if (row.icon != null) {
                            final Graphics2D iconGraphics = (Graphics2D) g2.create(s(PADDING), rowY + s(3), iconSize, iconSize);
                            try {
                                iconGraphics.clip(new RoundRectangle2D.Float(0, 0, iconSize, iconSize, s(6), s(6)));
                                iconGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                                final double scale = (double) iconSize / Math.max(1, Math.max(row.icon.getIconWidth(), row.icon.getIconHeight()));
                                iconGraphics.scale(scale, scale);
                                row.icon.paintIcon(this, iconGraphics, 0, 0);
                            } finally {
                                iconGraphics.dispose();
                            }
                        }
                        final int baseline = rowY + (s(ROW_HEIGHT) + rowMetrics.getAscent() - rowMetrics.getDescent()) / 2;
                        g2.setFont(bodyFont());
                        g2.setColor(foreground);
                        g2.drawString(row.name, s(PADDING) + iconSize + s(10), baseline);
                        if (row.value != null) {
                            g2.setColor(muted);
                            g2.drawString(row.value, getWidth() - s(PADDING) - rowMetrics.stringWidth(row.value), baseline);
                        }
                        rowY += s(ROW_HEIGHT);
                    }
                }
            } finally {
                g2.dispose();
            }
        }

        private Layout cardLayout() {
            final FontMetrics titleMetrics = getFontMetrics(titleFont()), bodyMetrics = getFontMetrics(bodyFont());
            final Layout layout = new Layout();
            int y = s(PADDING) + ((card.preview != null) ? s(PREVIEW_HEIGHT) + 10 : 0);
            layout.titleY = y + titleMetrics.getAscent();
            y += titleMetrics.getHeight() + 4;
            layout.lines = wrap(stripHtml(card.description), bodyMetrics, s(WIDTH) - 2 * s(PADDING));
            layout.bodyY = y + bodyMetrics.getAscent();
            y += layout.lines.size() * bodyMetrics.getHeight();
            if (! card.rows.isEmpty()) {
                y += s(8);
                layout.rowsY = y;
                y += card.rows.size() * s(ROW_HEIGHT);
            }
            y += s(PADDING) - 2;
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
            int titleY, bodyY, rowsY, height;
            List<String> lines;
        }

        private static final int WIDTH = 280, PADDING = 10, PREVIEW_HEIGHT = 140, LOOP = 4200, ROW_HEIGHT = 28;

        /**
         * Scale a size in pixels to the scale of the user interface.
         */
        private static int s(int size) {
            return com.formdev.flatlaf.util.UIScale.scale(size);
        }
    }
}
