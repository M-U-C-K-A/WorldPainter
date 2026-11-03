package org.pepsoft.worldpainter.design;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.WeakHashMap;

import static java.util.Map.entry;

/**
 * The line icons of the Modern theme, which stand in for the old coloured icons in the windows. The old icons are
 * recognised by the name of their file, and each is replaced by an icon which paints the line icon in the Modern theme
 * and the old icon in the Classic theme, so that switching themes needs nothing more than a repaint.
 */
public final class ModernIcons {
    private ModernIcons() {
        // Prevent instantiation
    }

    /**
     * Give the icons of all the buttons and labels in a container, which have a line icon, both looks.
     */
    public static void install(Container container) {
        for (Component component: container.getComponents()) {
            if (component instanceof AbstractButton button) {
                button.setIcon(replace(button.getIcon()));
                button.setSelectedIcon(replace(button.getSelectedIcon()));
                button.setPressedIcon(replace(button.getPressedIcon()));
                button.setRolloverIcon(replace(button.getRolloverIcon()));
                button.setRolloverSelectedIcon(replace(button.getRolloverSelectedIcon()));
                // An information button is a hint, not an action: it does not need to look like a button
                if ((button instanceof JButton) && ((button.getText() == null) || button.getText().isEmpty())
                        && (button.getIcon() instanceof ThemedIcon themed) && themed.name.equals("info")) {
                    button.putClientProperty("JButton.buttonType", "borderless");
                }
            } else if (component instanceof JLabel label) {
                label.setIcon(replace(label.getIcon()));
            } else if (component instanceof JTabbedPane tabs) {
                showTabIcons(tabs);
                tabs.addPropertyChangeListener("UI", event -> showTabIcons(tabs));
            }
            if (component instanceof Container) {
                install((Container) component);
            }
        }
    }

    /**
     * The tabs of the Modern theme are words only, like a segmented control; those of the Classic theme keep their
     * icons, which are put back when switching to it.
     */
    @SuppressWarnings("unchecked")
    private static void showTabIcons(JTabbedPane tabs) {
        Map<Component, Icon> hiddenIcons = (Map<Component, Icon>) tabs.getClientProperty(HIDDEN_TAB_ICONS);
        if (hiddenIcons == null) {
            hiddenIcons = new WeakHashMap<>();
            tabs.putClientProperty(HIDDEN_TAB_ICONS, hiddenIcons);
        }
        final boolean modern = Design.isModern();
        for (int i = 0; i < tabs.getTabCount(); i++) {
            final Component tab = tabs.getComponentAt(i);
            if (modern && (tabs.getIconAt(i) != null)) {
                hiddenIcons.put(tab, tabs.getIconAt(i));
                tabs.setIconAt(i, null);
            } else if ((! modern) && (tabs.getIconAt(i) == null) && hiddenIcons.containsKey(tab)) {
                tabs.setIconAt(i, hiddenIcons.remove(tab));
            }
        }
    }

    /**
     * An icon which is the given icon in the Classic theme and the named line icon in the Modern theme.
     */
    public static Icon themed(Icon classic, String name) {
        return (classic != null) ? new ThemedIcon(classic, icon(name), name) : null;
    }

    private static Icon replace(Icon icon) {
        if ((icon instanceof ImageIcon imageIcon) && (imageIcon.getDescription() != null)) {
            final String description = imageIcon.getDescription();
            final int slash = description.lastIndexOf('/');
            if ((slash >= 0) && description.endsWith(".png") && description.substring(0, slash).endsWith("/icons")) {
                final String name = REPLACEMENTS.get(description.substring(slash + 1, description.length() - 4));
                if (name != null) {
                    return themed(icon, name);
                }
            }
        }
        return icon;
    }

    private static FlatSVGIcon icon(String name) {
        final FlatSVGIcon icon = new FlatSVGIcon("org/pepsoft/worldpainter/icons/modern/" + name + ".svg", 16, 16, ModernIcons.class.getClassLoader());
        icon.setColorFilter(COLOURS);
        return icon;
    }

    private static Color colour(String key, Color fallback) {
        final Color colour = UIManager.getColor(key);
        return (colour != null) ? colour : fallback;
    }

    /**
     * The icons are drawn in placeholder colours, replaced with the colours of the theme in use when they are painted.
     */
    private static final FlatSVGIcon.ColorFilter COLOURS = new FlatSVGIcon.ColorFilter(colour -> {
        final Color replacement = switch (colour.getRGB() & 0xffffff) {
            case 0x000000 -> colour("WorldPainter.icon", colour("Label.foreground", Color.DARK_GRAY));
            case 0x0000ff -> Design.accent();
            case 0xff8000 -> colour("WorldPainter.warning", new Color(0xe8890c));
            default -> colour;
        };
        return (colour.getAlpha() == 255) ? replacement : new Color(replacement.getRed(), replacement.getGreen(), replacement.getBlue(), colour.getAlpha());
    });

    private static final String HIDDEN_TAB_ICONS = "WorldPainter.hiddenTabIcons";

    /**
     * The old icons which have a line icon in the Modern theme, by the name of their file.
     */
    private static final Map<String, String> REPLACEMENTS = Map.ofEntries(
            entry("information", "info"),
            entry("error", "warning"),
            entry("arrow_rotate_clockwise", "rotate_cw"),
            entry("arrow_rotate_anticlockwise", "rotate_ccw"),
            entry("plus", "plus"),
            entry("cross", "xmark"),
            entry("tick", "check"),
            entry("brick_add", "plus"),
            entry("brick_edit", "pencil"),
            entry("brick_delete", "trash"),
            entry("eyedropper", "eyedropper"),
            entry("up", "chevron_up"),
            entry("down", "chevron_down"),
            entry("top", "arrow_to_top"),
            entry("bottom", "arrow_to_bottom"),
            entry("disk", "save"),
            entry("folder_page_white", "folder"),
            entry("magnifier", "magnifier"),
            entry("magnifier_zoom_in", "zoom_in"),
            entry("magnifier_zoom_out", "zoom_out"),
            entry("arrow_in", "origin"),
            entry("resultset_previous", "chevron_left"),
            entry("resultset_next", "chevron_right"));

    /**
     * Paints the old icon or the line icon, whichever belongs to the theme in use.
     */
    private static final class ThemedIcon implements Icon, FlatLaf.DisabledIconProvider {
        ThemedIcon(Icon classic, Icon modern, String name) {
            this.classic = classic;
            this.modern = modern;
            this.name = name;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            current().paintIcon(c, g, x, y);
        }

        @Override
        public int getIconWidth() {
            return current().getIconWidth();
        }

        @Override
        public int getIconHeight() {
            return current().getIconHeight();
        }

        @Override
        public Icon getDisabledIcon() {
            final Icon disabledClassic = UIManager.getLookAndFeel().getDisabledIcon(null, classic);
            final Icon disabledModern = (modern instanceof FlatSVGIcon svgIcon) ? svgIcon.getDisabledIcon() : modern;
            return new ThemedIcon((disabledClassic != null) ? disabledClassic : classic, disabledModern, name);
        }

        private Icon current() {
            return Design.isModern() ? modern : classic;
        }

        private final Icon classic, modern;
        private final String name;
    }
}
