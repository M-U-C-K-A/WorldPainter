package org.pepsoft.worldpainter;

import com.formdev.flatlaf.FlatClientProperties;
import org.pepsoft.worldpainter.TileRenderer.ViewMode;
import org.pepsoft.worldpainter.util.HairlineBorder;
import org.pepsoft.worldpainter.util.ThemeUtils;
import org.pepsoft.worldpainter.util.WrapLayout;

import javax.swing.*;
import java.awt.*;
import java.util.EnumMap;
import java.util.Map;

/**
 * The bar at the top of the map which chooses what it shows, as the 3D view does: the terrain and layers as painted, or
 * the height, slope, biome or relief of the land on its own, with a legend for the colours of the height and the slope.
 */
public class MapModeBar extends JPanel {
    public MapModeBar(WorldPainter view) {
        super(new WrapLayout(0, 0));
        this.view = view;
        setBorder(new HairlineBorder(false, false, true, false, new Insets(2, 6, 2, 6)));
        final ButtonGroup group = new ButtonGroup();
        for (ViewMode mode: ViewMode.values()) {
            final JToggleButton button = new JToggleButton(mode.getDisplayName(), mode == view.getViewMode());
            button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
            button.setFocusable(false);
            button.setToolTipText(App.richToolTip(mode.getDisplayName(), getDescription(mode), null));
            button.addActionListener(e -> view.setViewMode(mode));
            group.add(button);
            buttons.put(mode, button);
            add(button);
        }
        separator = new JSeparator(SwingConstants.VERTICAL);
        separator.setPreferredSize(new java.awt.Dimension(13, 20));
        add(separator);
        legend = new Legend();
        add(legend);
        view.addPropertyChangeListener("viewMode", e -> update());
        view.addPropertyChangeListener("heightRange", e -> legend.repaint());
        update();
        // Its height depends on its width, so lay out again when the width changes
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (getPreferredSize().height != getHeight()) {
                    revalidate();
                }
            }
        });
    }

    /**
     * What a mode shows, for its tool tip and menu item.
     */
    static String getDescription(ViewMode mode) {
        return (mode == ViewMode.TERRAIN)
                ? mode.getDescription()
                : (mode.getDescription() + ". Only the selection is drawn over it, and the tools paint as usual" + ((mode == ViewMode.HEIGHT) ? ". Click it again to fit the colours to the land as it is now" : ""));
    }

    private void update() {
        final ViewMode mode = view.getViewMode();
        final JToggleButton button = buttons.get(mode);
        if (! button.isSelected()) {
            button.setSelected(true);
        }
        final boolean showLegend = (mode == ViewMode.HEIGHT) || (mode == ViewMode.SLOPE);
        separator.setVisible(showLegend);
        legend.setVisible(showLegend);
        legend.repaint();
    }

    private final WorldPainter view;
    private final Map<ViewMode, JToggleButton> buttons = new EnumMap<>(ViewMode.class);
    private final JSeparator separator;
    private final Legend legend;

    private static final long serialVersionUID = 1L;

    /**
     * The colours of the height or slope, with the heights or slopes at either end.
     */
    private final class Legend extends JComponent {
        Legend() {
            setToolTipText("<html>The colours of the map, from the lowest to the highest land<br>or from flat to steep</html>");
        }

        @Override
        public java.awt.Dimension getPreferredSize() {
            return new java.awt.Dimension(240, 24);
        }

        @Override
        protected void paintComponent(Graphics g) {
            final ViewMode mode = view.getViewMode();
            final int[] stops;
            final String left, right;
            if (mode == ViewMode.HEIGHT) {
                final float[] range = view.getHeightRange();
                stops = TileRenderer.HEIGHT_RAMP;
                left = "Y " + Math.round(range[0]);
                right = "Y " + Math.round(range[1]);
            } else if (mode == ViewMode.SLOPE) {
                stops = TileRenderer.SLOPE_RAMP;
                left = "flat";
                right = "60°+";
            } else {
                return;
            }
            final Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(getFont().deriveFont(getFont().getSize2D() - 1f));
                final FontMetrics fm = g2.getFontMetrics();
                final int textY = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.setColor(UIManager.getColor("Label.foreground"));
                g2.drawString(left, 0, textY);
                final int rightWidth = fm.stringWidth(right);
                g2.drawString(right, getWidth() - rightWidth, textY);
                final int barX = fm.stringWidth(left) + 6, barWidth = getWidth() - rightWidth - 6 - barX, barHeight = 10, barY = (getHeight() - barHeight) / 2;
                if (barWidth < 4) {
                    return;
                }
                for (int x = 0; x < barWidth; x++) {
                    g2.setColor(new Color(TileRenderer.ramp(stops, (float) x / (barWidth - 1))));
                    g2.fillRect(barX + x, barY, 1, barHeight);
                }
                g2.setColor(ThemeUtils.getColour("WorldPainter.border", Color.GRAY));
                g2.drawRect(barX - 1, barY - 1, barWidth + 1, barHeight + 1);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public String getToolTipText() {
            return (view.getViewMode() == ViewMode.HEIGHT)
                    ? "<html>The heights of the land not under water, from the lowest to the highest<br>Click <b>Height</b> again to fit them to the land as it is now</html>"
                    : "<html>The slope of the land, from flat to 60° and steeper</html>";
        }

        private static final long serialVersionUID = 1L;
    }
}
