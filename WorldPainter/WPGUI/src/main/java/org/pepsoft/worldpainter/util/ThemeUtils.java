package org.pepsoft.worldpainter.util;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import com.formdev.flatlaf.fonts.inter.FlatInterFont;
import org.pepsoft.util.SystemUtils;
import org.pepsoft.worldpainter.Configuration;
import org.pepsoft.worldpainter.laf.WorldPainterDarkLaf;
import org.pepsoft.worldpainter.laf.WorldPainterLightLaf;

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
     * Install the specified look and feel. The legacy dark themes are mapped to the modern dark theme.
     *
     * @param lookAndFeel The look and feel to install.
     * @param uiScale     The UI scale to apply, in case the look and feel can scale itself.
     */
    public static void installLookAndFeel(Configuration.LookAndFeel lookAndFeel, float uiScale) throws ClassNotFoundException, UnsupportedLookAndFeelException, InstantiationException, IllegalAccessException {
        switch (lookAndFeel) {
            case SYSTEM:
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                break;
            case METAL:
                UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
                break;
            case NIMBUS:
                UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
                break;
            case MODERN_LIGHT:
                installFlatLaf(false, uiScale);
                break;
            case MODERN_DARK:
            case DARK_METAL:
            case DARK_NIMBUS:
                installFlatLaf(true, uiScale);
                break;
            case MODERN_AUTO:
                installFlatLaf(isSystemDark(), uiScale);
                break;
            default:
                throw new InternalError("Unknown look and feel " + lookAndFeel);
        }
    }

    /**
     * Indicates whether the currently installed look and feel is one of the modern (FlatLaf) themes, which scale
     * themselves.
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
     * Switch between the modern light and dark themes while the application is running, with a short cross-fade.
     */
    public static void switchModernTheme(boolean dark) {
        FlatAnimatedLafChange.showSnapshot();
        installFlatLaf(dark, 1.0f);
        FlatLaf.updateUI();
        FlatAnimatedLafChange.hideSnapshotWithAnimation();
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

    private static void installFlatLaf(boolean dark, float uiScale) {
        if (uiScale != 1.0f) {
            System.setProperty("flatlaf.uiScale", Float.toString(uiScale));
        }
        if (! fontInstalled) {
            FlatInterFont.install();
            FlatLaf.setPreferredFontFamily(FlatInterFont.FAMILY);
            fontInstalled = true;
        }
        if (dark) {
            WorldPainterDarkLaf.setup();
        } else {
            WorldPainterLightLaf.setup();
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

    private static boolean fontInstalled;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ThemeUtils.class);
}
