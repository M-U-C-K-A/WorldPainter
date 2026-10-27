package org.pepsoft.worldpainter.util;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.*;
import java.awt.*;

/**
 * Vector icons from the <a href="https://lucide.dev/">Lucide</a> icon set (ISC licence, see
 * {@code org/pepsoft/worldpainter/icons/lucide/LICENSE}). The icons are tinted with the colours of the current theme
 * at paint time, so they follow theme changes, and are scaled to the UI scale.
 */
public final class Icons {
    private Icons() {
        // Prevent instantiation
    }

    /**
     * An icon in the normal foreground colour, at the default size of 16 pixels.
     */
    public static Icon get(String name) {
        return get(name, DEFAULT_SIZE);
    }

    /**
     * An icon in the normal foreground colour.
     */
    public static Icon get(String name, int size) {
        return create(name, size, FOREGROUND);
    }

    /**
     * An icon in the muted foreground colour, for secondary controls.
     */
    public static Icon muted(String name, int size) {
        return create(name, size, MUTED);
    }

    /**
     * An icon in the accent colour, for selected or highlighted state.
     */
    public static Icon accent(String name, int size) {
        return create(name, size, ACCENT);
    }

    public static boolean exists(String name) {
        return Icons.class.getClassLoader().getResource(PATH + name + ".svg") != null;
    }

    private static Icon create(String name, int size, FlatSVGIcon.ColorFilter colorFilter) {
        final FlatSVGIcon icon = new FlatSVGIcon(PATH + name + ".svg", size, size, Icons.class.getClassLoader());
        icon.setColorFilter(colorFilter);
        return icon;
    }

    private static Color themeColour(String key, Color fallback) {
        final Color colour = UIManager.getColor(key);
        return (colour != null) ? colour : fallback;
    }

    public static final int DEFAULT_SIZE = 16;

    private static final String PATH = "org/pepsoft/worldpainter/icons/lucide/";
    // The icons are drawn in black ("currentColor"); replace that with the theme colour, preserving the alpha
    private static final FlatSVGIcon.ColorFilter FOREGROUND = new FlatSVGIcon.ColorFilter(colour -> withAlpha(themeColour("Label.foreground", Color.BLACK), colour));
    private static final FlatSVGIcon.ColorFilter MUTED = new FlatSVGIcon.ColorFilter(colour -> withAlpha(themeColour("WorldPainter.mutedForeground", Color.GRAY), colour));
    private static final FlatSVGIcon.ColorFilter ACCENT = new FlatSVGIcon.ColorFilter(colour -> withAlpha(themeColour("WorldPainter.accent", new Color(0xf97316)), colour));

    private static Color withAlpha(Color colour, Color original) {
        return (original.getAlpha() == 255) ? colour : new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), original.getAlpha());
    }
}
