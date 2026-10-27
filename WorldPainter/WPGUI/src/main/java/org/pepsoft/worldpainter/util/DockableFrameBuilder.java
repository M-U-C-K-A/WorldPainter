package org.pepsoft.worldpainter.util;

import org.pepsoft.util.IconUtils;
import org.pepsoft.worldpainter.App;
import org.pepsoft.worldpainter.docking.DockableFrame;
import org.pepsoft.worldpainter.docking.Side;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;

import static java.awt.GridBagConstraints.HORIZONTAL;
import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;
import static javax.swing.SwingConstants.VERTICAL;
import static org.pepsoft.util.GUIUtils.getUIScale;
import static org.pepsoft.worldpainter.App.KEY_ICON;

public class DockableFrameBuilder {
    public DockableFrameBuilder(Component component, String title, Side side, int index) {
        this.component = component;
        this.title = title;
        this.side = side;
        this.index = index;
        id = (Character.toLowerCase(title.charAt(0)) + title.substring(1)).replaceAll("\\s", "");
    }

    public DockableFrameBuilder withId(String id) {
        this.id = id;
        return this;
    }

    public DockableFrameBuilder expand() {
        expand = true;
        return this;
    }

    public DockableFrameBuilder withIcon(Icon icon) {
        this.icon = icon;
        return this;
    }

    public DockableFrameBuilder withMargin(int margin) {
        this.margin = margin;
        return this;
    }

    /**
     * Add a title and a short explanation of what the panel is for at the top of the panel, also used as the tooltip of
     * its tab.
     */
    public DockableFrameBuilder withDescription(String description) {
        this.description = description;
        return this;
    }

    public DockableFrameBuilder scrollable() {
        scrollable = true;
        return this;
    }

    public DockableFrame build() {
        DockableFrame dockableFrame = new DockableFrame(id);

        JPanel panel = new VerticalScrollingJPanel(new GridBagLayout());
        if (margin > 0) {
            panel.setBorder(new EmptyBorder(margin, margin, margin, margin));
        }
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridwidth = GridBagConstraints.REMAINDER;
        constraints.weightx = 1.0;
        if (expand) {
            constraints.fill = GridBagConstraints.BOTH;
            constraints.weighty = 1.0;
            panel.add(component, constraints);
        } else {
            constraints.fill = HORIZONTAL;
            panel.add(component, constraints);
            constraints.fill = GridBagConstraints.BOTH;
            constraints.weighty = 1.0;
            panel.add(new JPanel(), constraints);
        }
        if (scrollable) {
            final JScrollPane scrollPane = new JScrollPane(panel, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
            scrollPane.setBorder(null);
            dockableFrame.add(scrollPane, BorderLayout.CENTER);
        } else {
            dockableFrame.add(panel, BorderLayout.CENTER);
        }
        if (description != null) {
            // A fixed width makes the text wrap instead of widening the dock
            final JLabel header = new JLabel("<html><div style=\"width: " + Math.round(170 * getUIScale()) + "px\"><b>" + title + "</b><br>" + description + "</div></html>");
            header.putClientProperty("FlatLaf.style", "font: -1");
            header.setBorder(new HairlineBorder(false, false, true, false, new Insets(6, 8, 6, 8)));
            dockableFrame.add(header, BorderLayout.NORTH);
        }

        dockableFrame.setTitle(title);
        dockableFrame.setToolTipText((description != null) ? ("<html><b>" + title + "</b><br><div style=\"width: 220px\">" + description + "</div></html>") : title);

        // Try to find an icon to use for the tab
        if ((icon == null) && (component instanceof Container)) {
            icon = findIcon((Container) component);
            if (icon != null) {
                final int desiredSize = Math.round(16 * getUIScale());
                if (((icon.getIconHeight() > desiredSize) || (icon.getIconWidth() > desiredSize))
                        && (icon instanceof ImageIcon)
                        && (((ImageIcon) icon).getImage() instanceof BufferedImage)) {
                    float s;
                    if (icon.getIconWidth() > icon.getIconHeight()) {
                        // Wide icon
                        s = (float) desiredSize / icon.getIconWidth();
                    } else {
                        // Tall (or square) icon
                        s = (float) desiredSize / icon.getIconHeight();
                    }
                    BufferedImageOp op = new AffineTransformOp(AffineTransform.getScaleInstance(s, s), AffineTransformOp.TYPE_BICUBIC);
                    BufferedImage iconImage = op.filter((BufferedImage) ((ImageIcon) icon).getImage(), null);
                    icon = new ImageIcon(iconImage);
                }
            }
        }
        dockableFrame.setFrameIcon((icon != null) ? icon : ICON_UNKNOWN_PATTERN);

        // Initial location of panel
        dockableFrame.setInitSide(side);
        dockableFrame.setInitIndex(index);

        //Help key
        dockableFrame.putClientProperty(App.KEY_HELP_KEY, "Panel/" + id);
        return dockableFrame;
    }

    private final String title;
    private final Side side;
    private final int index;
    private final Component component;

    private String id;
    private boolean expand;
    private Icon icon;
    private int margin = 2;
    private boolean scrollable;
    private String description;

    private static Icon findIcon(Container container) {
        if (container instanceof JComponent) {
            Icon icon = (Icon) ((JComponent) container).getClientProperty(KEY_ICON);
            if (icon != null) {
                return icon;
            }
        }
        for (Component component: container.getComponents()) {
            if ((component instanceof AbstractButton) && (((AbstractButton) component).getIcon() != null)) {
                return ((AbstractButton) component).getIcon();
            } else if (component instanceof Container) {
                Icon icon = findIcon((Container) component);
                if (icon != null) {
                    return icon;
                }
            }
        }
        return null;
    }

    private static final Icon ICON_UNKNOWN_PATTERN = IconUtils.loadScaledIcon("org/pepsoft/worldpainter/icons/unknown_pattern.png");

    /**
     * A {@code JPanel} implementation that implements {@code Scrollable} and tracks the viewport width; meant for use
     * with vertical scrolling only.
     */
    static class VerticalScrollingJPanel extends JPanel implements Scrollable {
        public VerticalScrollingJPanel(LayoutManager layout) {
            super(layout);
        }

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
            return ((orientation == VERTICAL) ? visibleRect.height : visibleRect.width) - 16;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}