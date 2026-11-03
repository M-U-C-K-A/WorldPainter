package org.pepsoft.worldpainter.design;

import org.pepsoft.worldpainter.util.ThemeUtils;

import javax.swing.*;
import java.awt.*;

/**
 * The colours and fonts the windows are made of, taken from the theme so that they work in light and dark alike.
 */
public final class Design {
    private Design() {
        // Prevent instantiation
    }

    public static boolean isDark() {
        return ThemeUtils.isDarkTheme();
    }

    /**
     * Whether the Glass theme is in use. It is a variant of the Modern theme, so {@link #isModern()} is also true.
     */
    public static boolean isGlass() {
        return UIManager.getBoolean("WorldPainter.glass");
    }

    /**
     * Whether the Modern theme, or the Glass theme which builds on it, is in use, rather than the Classic one.
     */
    public static boolean isModern() {
        return UIManager.getBoolean("WorldPainter.modern");
    }

    /**
     * The background of the raised surfaces on which the Modern theme groups settings.
     */
    public static Color surface() {
        return colour("WorldPainter.surface", cardBackground());
    }

    public static Color surfaceBorder() {
        return colour("WorldPainter.surfaceBorder", border());
    }

    /**
     * The colour of the hairlines between rows of settings.
     */
    public static Color separator() {
        return colour("Separator.foreground", border());
    }

    public static Color accent() {
        return colour("Component.accentColor", new Color(0x0071e3));
    }

    public static Color background() {
        return colour("Panel.background", Color.DARK_GRAY);
    }

    public static Color foreground() {
        return colour("Label.foreground", Color.WHITE);
    }

    public static Color muted() {
        return mix(background(), foreground(), 0.62f);
    }

    public static Color border() {
        return colour("Component.borderColor", mix(background(), foreground(), 0.2f));
    }

    /**
     * The background of a card: set off a little from that of the window.
     */
    public static Color cardBackground() {
        return isDark() ? mix(background(), Color.WHITE, 0.035f) : mix(background(), Color.BLACK, 0.025f);
    }

    public static Font font(float relativeSize, int style) {
        final Font base = UIManager.getFont("Label.font");
        return base.deriveFont(style, base.getSize2D() + relativeSize);
    }

    /**
     * The semibold weight of the font of the theme, or bold if it has none.
     */
    public static Font semibold(float relativeSize) {
        final Font semibold = UIManager.getFont("semibold.font");
        if (semibold == null) {
            return font(relativeSize, Font.BOLD);
        }
        final Font base = UIManager.getFont("Label.font");
        return semibold.deriveFont(base.getSize2D() + relativeSize);
    }

    public static Color mix(Color from, Color to, float amount) {
        return new Color(Math.round(from.getRed() + (to.getRed() - from.getRed()) * amount),
                Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * amount),
                Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * amount));
    }

    private static Color colour(String key, Color fallback) {
        final Color colour = UIManager.getColor(key);
        return (colour != null) ? colour : fallback;
    }
}
