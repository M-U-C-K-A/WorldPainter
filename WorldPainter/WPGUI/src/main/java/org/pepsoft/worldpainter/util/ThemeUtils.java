package org.pepsoft.worldpainter.util;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import org.pepsoft.util.SystemUtils;
import org.pepsoft.worldpainter.Configuration;
import org.pepsoft.worldpainter.laf.WorldPainterDarkLaf;
import org.pepsoft.worldpainter.laf.WorldPainterLightLaf;
import org.pepsoft.worldpainter.laf.WorldPainterModernDarkLaf;
import org.pepsoft.worldpainter.laf.WorldPainterModernLightLaf;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Utilities for installing and querying the look and feel ("theme") of WorldPainter.
 */
public final class ThemeUtils {
    private ThemeUtils() {
        // Prevent instantiation
    }

    /**
     * Install the specified theme. The look and feels of earlier versions are mapped to the Classic theme.
     *
     * @param lookAndFeel The theme to install.
     * @param uiScale     The UI scale to apply, in case the look and feel can scale itself.
     */
    public static void installLookAndFeel(Configuration.LookAndFeel lookAndFeel, float uiScale) {
        final Configuration.LookAndFeel theme = normalise(lookAndFeel);
        installFlatLaf(isClassic(theme), isDark(theme), uiScale);
    }

    /**
     * Switch to another theme while the application is running, with a short cross-fade.
     */
    public static void switchTheme(Configuration.LookAndFeel lookAndFeel) {
        final Configuration.LookAndFeel theme = normalise(lookAndFeel);
        FlatAnimatedLafChange.showSnapshot();
        installFlatLaf(isClassic(theme), isDark(theme), 1.0f);
        FlatLaf.updateUI();
        FlatAnimatedLafChange.hideSnapshotWithAnimation();
    }

    /**
     * Switch between the light and dark variants of the current theme while the application is running.
     *
     * @return The theme which is now in use.
     */
    public static Configuration.LookAndFeel switchAppearance(boolean dark) {
        final Configuration.LookAndFeel theme = theme(isClassicTheme(), dark ? Appearance.DARK : Appearance.LIGHT);
        switchTheme(theme);
        return theme;
    }

    /**
     * Map {@code null} and the look and feels of earlier versions to the themes which are offered: Modern or Classic,
     * light, dark or following the system.
     */
    public static Configuration.LookAndFeel normalise(Configuration.LookAndFeel lookAndFeel) {
        if (lookAndFeel == null) {
            return Configuration.LookAndFeel.MODERN_AUTO;
        }
        switch (lookAndFeel) {
            case SYSTEM:
                return Configuration.LookAndFeel.CLASSIC_AUTO;
            case METAL:
            case NIMBUS:
                return Configuration.LookAndFeel.CLASSIC_LIGHT;
            case DARK_METAL:
            case DARK_NIMBUS:
                return Configuration.LookAndFeel.CLASSIC_DARK;
            default:
                return lookAndFeel;
        }
    }

    /**
     * Whether a theme is a variant of the Classic theme, rather than the Modern one.
     */
    public static boolean isClassic(Configuration.LookAndFeel lookAndFeel) {
        final Configuration.LookAndFeel theme = normalise(lookAndFeel);
        return (theme == Configuration.LookAndFeel.CLASSIC_LIGHT) || (theme == Configuration.LookAndFeel.CLASSIC_DARK) || (theme == Configuration.LookAndFeel.CLASSIC_AUTO);
    }

    /**
     * Whether a theme is light, dark or follows the system.
     */
    public static Appearance getAppearance(Configuration.LookAndFeel lookAndFeel) {
        switch (normalise(lookAndFeel)) {
            case MODERN_LIGHT:
            case CLASSIC_LIGHT:
                return Appearance.LIGHT;
            case MODERN_DARK:
            case CLASSIC_DARK:
                return Appearance.DARK;
            default:
                return Appearance.SYSTEM;
        }
    }

    /**
     * The theme with a design (Classic or Modern) and an appearance.
     */
    public static Configuration.LookAndFeel theme(boolean classic, Appearance appearance) {
        switch (appearance) {
            case LIGHT:
                return classic ? Configuration.LookAndFeel.CLASSIC_LIGHT : Configuration.LookAndFeel.MODERN_LIGHT;
            case DARK:
                return classic ? Configuration.LookAndFeel.CLASSIC_DARK : Configuration.LookAndFeel.MODERN_DARK;
            default:
                return classic ? Configuration.LookAndFeel.CLASSIC_AUTO : Configuration.LookAndFeel.MODERN_AUTO;
        }
    }

    /**
     * Indicates whether the theme which is installed is the Classic one, rather than the Modern one.
     */
    public static boolean isClassicTheme() {
        return isModernTheme() && (! UIManager.getBoolean("WorldPainter.modern"));
    }

    private static boolean isDark(Configuration.LookAndFeel theme) {
        switch (getAppearance(theme)) {
            case LIGHT:
                return false;
            case DARK:
                return true;
            default:
                return isSystemDark();
        }
    }

    /**
     * Indicates whether the currently installed look and feel is one of the WorldPainter (FlatLaf) themes, Modern or
     * Classic, which scale themselves.
     */
    public static boolean isModernTheme() {
        return UIManager.getLookAndFeel() instanceof FlatLaf;
    }

    /**
     * Indicates whether the currently installed look and feel has a dark background.
     */
    public static boolean isDarkTheme() {
        final javax.swing.LookAndFeel laf = UIManager.getLookAndFeel();
        if (laf instanceof FlatLaf) {
            return ((FlatLaf) laf).isDark();
        }
        final Color background = UIManager.getColor("Panel.background");
        if (background == null) {
            return false;
        }
        // Perceived brightness
        return (background.getRed() * 299 + background.getGreen() * 587 + background.getBlue() * 114) / 1000 < 128;
    }

    /**
     * Determine whether the operating system is set to dark mode. Returns {@code false} if it cannot be determined.
     */
    public static boolean isSystemDark() {
        try {
            if (SystemUtils.isMac()) {
                return run("defaults", "read", "-g", "AppleInterfaceStyle").trim().equalsIgnoreCase("Dark");
            } else if (SystemUtils.isWindows()) {
                return run("reg", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", "/v", "AppsUseLightTheme").contains("0x0");
            } else {
                final String colourScheme = run("gsettings", "get", "org.gnome.desktop.interface", "color-scheme");
                return colourScheme.contains("dark") || run("gsettings", "get", "org.gnome.desktop.interface", "gtk-theme").toLowerCase().contains("dark");
            }
        } catch (IOException e) {
            logger.debug("Could not determine system dark mode setting", e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Get a colour from the current theme.
     */
    public static Color getColour(String key, Color fallback) {
        final Color colour = UIManager.getColor(key);
        return (colour != null) ? colour : fallback;
    }

    /**
     * The colour of the canvas around the map.
     */
    public static Color getCanvasBackground() {
        return getColour("WorldPainter.canvasBackground", isDarkTheme() ? new Color(0x09090b) : new Color(0xf4f4f5));
    }

    /**
     * Create a tile for the dotted background of the canvas around the map, to be repeated.
     */
    public static BufferedImage createCanvasDotPattern() {
        final int cell = 22;
        final BufferedImage image = new BufferedImage(cell, cell, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g2 = image.createGraphics();
        try {
            g2.setColor(getCanvasBackground());
            g2.fillRect(0, 0, cell, cell);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getColour("WorldPainter.canvasDot", Color.GRAY));
            g2.fill(new java.awt.geom.Ellipse2D.Float(cell / 2f - 1.1f, cell / 2f - 1.1f, 2.2f, 2.2f));
        } finally {
            g2.dispose();
        }
        return image;
    }

    private static void installFlatLaf(boolean classic, boolean dark, float uiScale) {
        if (uiScale != 1.0f) {
            System.setProperty("flatlaf.uiScale", Float.toString(uiScale));
        }
        if (! fontInstalled) {
            if (GeistFont.install()) {
                FlatLaf.setPreferredFontFamily(GeistFont.FAMILY);
                FlatLaf.setPreferredLightFontFamily(GeistFont.FAMILY_LIGHT);
                FlatLaf.setPreferredSemiboldFontFamily(GeistFont.FAMILY_SEMIBOLD);
            } else {
                logger.warn("Could not install the Geist font; using the default font of the system");
            }
            fontInstalled = true;
        }
        if (classic) {
            if (dark) {
                WorldPainterDarkLaf.setup();
            } else {
                WorldPainterLightLaf.setup();
            }
        } else if (dark) {
            WorldPainterModernDarkLaf.setup();
        } else {
            WorldPainterModernLightLaf.setup();
        }
        // Shorter delays make tooltips feel responsive, as in modern web applications
        ToolTipManager.sharedInstance().setInitialDelay(300);
        ToolTipManager.sharedInstance().setReshowDelay(50);
        ToolTipManager.sharedInstance().setDismissDelay(15000);
    }

    private static String run(String... command) throws IOException, InterruptedException {
        final Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        final byte[] output = process.getInputStream().readAllBytes();
        if (! process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly();
        }
        return new String(output, UTF_8);
    }

    /**
     * Whether a theme is light, dark, or follows the setting of the operating system.
     */
    public enum Appearance {SYSTEM, LIGHT, DARK}

    private static boolean fontInstalled;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ThemeUtils.class);
}
