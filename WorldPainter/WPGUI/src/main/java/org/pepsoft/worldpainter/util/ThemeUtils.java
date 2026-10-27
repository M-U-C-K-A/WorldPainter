package org.pepsoft.worldpainter.util;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import org.pepsoft.util.SystemUtils;
import org.pepsoft.worldpainter.Configuration;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.Collections;
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

    private static void installFlatLaf(boolean dark, float uiScale) {
        if (uiScale != 1.0f) {
            System.setProperty("flatlaf.uiScale", Float.toString(uiScale));
        }
        FlatLaf.setGlobalExtraDefaults(Collections.singletonMap("@accentColor", ACCENT_COLOUR));
        if (SystemUtils.isMac()) {
            if (dark) {
                FlatMacDarkLaf.setup();
            } else {
                FlatMacLightLaf.setup();
            }
        } else if (dark) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }
        // Tweaks for a cleaner, more compact look
        UIManager.put("Component.arc", 6);
        UIManager.put("Button.arc", 6);
        UIManager.put("TextComponent.arc", 6);
        UIManager.put("ScrollBar.showButtons", false);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("TabbedPane.showTabSeparators", false);
        UIManager.put("TabbedPane.tabsPopupPolicy", "asNeeded");
        UIManager.put("TitlePane.unifiedBackground", true);
        UIManager.put("ToolBar.separatorWidth", 9);
        UIManager.put("SplitPaneDivider.style", "plain");
    }

    private static String run(String... command) throws IOException, InterruptedException {
        final Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        final byte[] output = process.getInputStream().readAllBytes();
        if (! process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly();
        }
        return new String(output, UTF_8);
    }

    private static final String ACCENT_COLOUR = "#3D9A50";
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ThemeUtils.class);
}
