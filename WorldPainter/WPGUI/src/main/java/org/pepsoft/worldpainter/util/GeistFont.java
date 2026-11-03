package org.pepsoft.worldpainter.util;

import com.formdev.flatlaf.util.FontUtils;

import java.awt.*;
import java.net.URL;
import java.util.Locale;

/**
 * The Geist typeface by Vercel, licensed under the SIL Open Font License 1.1 (see {@code fonts/geist/OFL.txt}), in the
 * regular, bold, light and semibold weights which FlatLaf uses.
 */
public final class GeistFont {
    private GeistFont() {
        // Prevent instantiation
    }

    /**
     * Register the font with Java, unless it is installed on the system already.
     *
     * @return {@code true} if the font can be used.
     */
    public static boolean install() {
        installStyle("Geist-Regular.ttf");
        installStyle("Geist-Bold.ttf");
        installStyle("Geist-Light.ttf");
        installStyle("Geist-SemiBold.ttf");
        // Registering fails if the font is installed on the system, in which case that one is used
        return FAMILY.equals(new Font(FAMILY, Font.PLAIN, 12).getFamily(Locale.ENGLISH));
    }

    private static void installStyle(String name) {
        final URL url = GeistFont.class.getResource("/org/pepsoft/worldpainter/fonts/geist/" + name);
        if (url != null) {
            FontUtils.installFont(url);
        }
    }

    public static final String FAMILY = "Geist";
    public static final String FAMILY_LIGHT = "Geist Light";
    public static final String FAMILY_SEMIBOLD = "Geist SemiBold";
}
