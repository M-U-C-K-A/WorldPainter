package org.pepsoft.worldpainter.help;

import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.ColourScheme;
import org.pepsoft.worldpainter.biomeschemes.BiomeSchemeManager;
import org.pepsoft.worldpainter.layers.plants.Plant;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Small icons of blocks and plants for the cards: their textures from the Minecraft jar if it is installed (the grass
 * and foliage tinted green, as in a plains biome), or else a swatch of the colour of the block in the colour scheme.
 */
public final class BlockIcons {
    private BlockIcons() {
        // Prevent instantiation
    }

    public static Icon block(Material material, ColourScheme colourScheme) {
        final String name = material.simpleName;
        BufferedImage texture = null;
        for (String candidate: new String[] {name, name + "_top", name + "_side", name.replace("_block", ""), name + "_front"}) {
            texture = texture("block/" + candidate + ".png", tint(name));
            if (texture != null) {
                break;
            }
        }
        return (texture != null) ? new ImageIcon(texture) : swatch(colourScheme.getColour(material));
    }

    public static Icon plant(Plant plant, ColourScheme colourScheme) {
        final String[] iconNames = plant.getIconNames();
        if (iconNames != null) {
            for (String iconName: iconNames) {
                final String simpleName = iconName.substring(iconName.lastIndexOf('/') + 1).replace(".png", "");
                final BufferedImage texture = texture(iconName, tint(simpleName));
                if (texture != null) {
                    return new ImageIcon(texture);
                }
            }
        }
        final Material material = plant.getMaterial(0, 0, 0);
        return swatch((material != null) ? colourScheme.getColour(material) : 0x5f9e3a);
    }

    /**
     * The tint for a texture which Minecraft colours by biome, or -1 if it is not tinted.
     */
    private static int tint(String name) {
        if (name.startsWith("grass_block") || GRASS_TINTED.contains(name)) {
            return GRASS;
        } else if (name.endsWith("_leaves") && (! name.startsWith("cherry")) && (! name.startsWith("azalea")) && (! name.startsWith("flowering")) && (! name.startsWith("pale"))) {
            return FOLIAGE;
        } else {
            return -1;
        }
    }

    private static synchronized BufferedImage texture(String path, int tint) {
        if (cache.containsKey(path)) {
            return cache.get(path);
        }
        BufferedImage image = null;
        final JarFile jar = jar();
        if (jar != null) {
            final JarEntry entry = jar.getJarEntry("assets/minecraft/textures/" + path);
            if (entry != null) {
                try (InputStream in = jar.getInputStream(entry)) {
                    image = ImageIO.read(in);
                    if ((image != null) && (image.getHeight() > image.getWidth())) {
                        // An animation strip; take its first frame
                        image = image.getSubimage(0, 0, image.getWidth(), image.getWidth());
                    }
                    if ((image != null) && (tint != -1)) {
                        image = tinted(image, tint);
                    }
                } catch (IOException e) {
                    image = null;
                }
            }
        }
        cache.put(path, image);
        return image;
    }

    private static BufferedImage tinted(BufferedImage image, int tint) {
        final BufferedImage result = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                final int argb = image.getRGB(x, y);
                final int r = ((argb >> 16) & 0xff) * ((tint >> 16) & 0xff) / 255, g = ((argb >> 8) & 0xff) * ((tint >> 8) & 0xff) / 255, b = (argb & 0xff) * (tint & 0xff) / 255;
                result.setRGB(x, y, (argb & 0xff000000) | (r << 16) | (g << 8) | b);
            }
        }
        return result;
    }

    private static JarFile jar() {
        if (! jarLookedUp) {
            jarLookedUp = true;
            try {
                final File file = BiomeSchemeManager.getLatestMinecraftJar();
                if (file != null) {
                    jarFile = new JarFile(file);
                }
            } catch (IOException | RuntimeException e) {
                jarFile = null;
            }
        }
        return jarFile;
    }

    private static Icon swatch(int colour) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                g.setColor(new Color(colour));
                g.fillRect(x, y, 16, 16);
            }

            @Override
            public int getIconWidth() {
                return 16;
            }

            @Override
            public int getIconHeight() {
                return 16;
            }
        };
    }

    private static final Map<String, BufferedImage> cache = new HashMap<>();
    private static JarFile jarFile;
    private static boolean jarLookedUp;

    private static final int GRASS = 0x79c05a, FOLIAGE = 0x59ae30;
    private static final Set<String> GRASS_TINTED = Set.of("short_grass", "grass", "tall_grass_top", "tall_grass_bottom", "fern", "large_fern_top", "large_fern_bottom", "lily_pad", "vine", "sugar_cane");
}
