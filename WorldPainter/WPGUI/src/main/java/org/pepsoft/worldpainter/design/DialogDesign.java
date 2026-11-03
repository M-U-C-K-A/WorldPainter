package org.pepsoft.worldpainter.design;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Gives the main windows a banner which says what they are for, and what each of their tabs is for. Windows which are
 * not listed here are left as they are.
 */
public final class DialogDesign {
    private DialogDesign() {
        // Prevent instantiation
    }

    /**
     * Add the banner to a window, if it has one. To be done once, just before it is first shown.
     */
    public static void decorate(JDialog dialog) {
        final Spec spec = SPECS.get(dialog.getClass().getSimpleName());
        if (spec == null) {
            return;
        }
        final String title = dialog.getTitle().replace(" [SNAPSHOT]", "").replace(" [SAFE MODE]", "");
        final DialogBanner banner = new DialogBanner(spec.illustration, new Color(spec.accent), title, spec.description);
        final Container content = dialog.getContentPane();
        // Taking the default button out of the window, as moving the content does, makes it forget that it is one
        final JButton defaultButton = dialog.getRootPane().getDefaultButton();
        final JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(banner, BorderLayout.NORTH);
        wrapper.add(content, BorderLayout.CENTER);
        dialog.setContentPane(wrapper);
        dialog.getRootPane().setDefaultButton(defaultButton);

        // What the selected tab is for
        final JTabbedPane tabs = findTabs(dialog.getRootPane());
        if (tabs != null) {
            final Runnable update = () -> {
                final int index = tabs.getSelectedIndex();
                banner.setDetail((index >= 0) ? spec.describeTab(tabs.getTitleAt(index)) : null);
            };
            tabs.addChangeListener(e -> update.run());
            update.run();
        }

        // Make room for it
        final int bannerHeight = banner.getPreferredSize().height;
        if ((dialog.getWidth() > 0) && (dialog.getHeight() > 0)) {
            final Rectangle screen = dialog.getGraphicsConfiguration().getBounds();
            final int height = Math.min(dialog.getHeight() + bannerHeight, screen.height - 40);
            final int y = Math.max(screen.y, dialog.getY() - bannerHeight / 2);
            dialog.setBounds(dialog.getX(), y, dialog.getWidth(), height);
        } else {
            dialog.pack();
        }
    }

    private static JTabbedPane findTabs(Container container) {
        for (Component component: container.getComponents()) {
            if (component instanceof JTabbedPane) {
                return (JTabbedPane) component;
            } else if (component instanceof Container) {
                final JTabbedPane tabs = findTabs((Container) component);
                if (tabs != null) {
                    return tabs;
                }
            }
        }
        return null;
    }

    private static void spec(String dialog, Illustration illustration, int accent, String description, String... tabs) {
        final Map<String, String> tabDescriptions = new HashMap<>();
        for (int i = 0; i < tabs.length; i += 2) {
            tabDescriptions.put(tabs[i], tabs[i + 1]);
        }
        SPECS.put(dialog, new Spec(illustration, accent, description, tabDescriptions));
    }

    private record Spec(Illustration illustration, int accent, String description, Map<String, String> tabs) {
        String describeTab(String tab) {
            final String description = tabs.get(tab);
            if (description != null) {
                return description;
            }
            final String any = tabs.get("*");
            return (any != null) ? String.format(any, tab) : null;
        }
    }

    private static final Map<String, Spec> SPECS = new HashMap<>();

    private static final int INDIGO = 0x6366f1, AMBER = 0xd97706, GREEN = 0x16a34a, SKY = 0x0ea5e9, TEAL = 0x14b8a6, VIOLET = 0x8b5cf6;

    static {
        spec("PreferencesDialog", Illustration.SETTINGS, INDIGO,
                "Settings for WorldPainter as a whole: how it looks and behaves, what new worlds start with, and how hard it works your computer.",
                "General", "Privacy, undo, the look of the program, and the backups and autosaves which keep your work safe.",
                "Defaults", "What new worlds and the view of them start with. Worlds you already have keep their own settings.",
                "Performance", "How WorldPainter uses your graphics card, memory and processor cores.");
        spec("DimensionPropertiesDialog", Illustration.GROUND, AMBER,
                "How the land is built where you don't paint it: what lies under the surface, what surrounds the map, and the caves and ores.",
                "General", "The ground under the top layer, the border around the map, and the settings Minecraft uses beyond it.",
                "Theme", "The terrain types and layers the Mountain tool and resetting the terrain use, by height.",
                "Caves, Caverns and Chasms", "Where the hollows under the ground go, how deep, whether they are flooded and how they are decorated.",
                "Resources", "How common each ore and underground block is, and at which depths it is found.",
                "Other Layers", "The other layers which can cover the whole map, such as frost and populate.",
                "Custom Layers", "The custom layers of this dimension, and whether they are exported.",
                "Post Processing", "What is fixed up after exporting, so that the world follows the rules of Minecraft.");
        spec("ExportWorldDialog", Illustration.EXPORT, GREEN,
                "Turn the map into a world you can play in Minecraft: where it is saved, the game settings, and how each dimension is exported.",
                "General", "Where the world is saved, its name, the game mode, and the data packs it comes with.",
                "*", "How the %s is exported: the ground under the surface, the border, the caves, the ores and the other layers.");
        spec("TestExportDialog", Illustration.EXPORT, GREEN,
                "Export a small part of the map, to try it out in Minecraft quickly.");
        spec("NewWorldDialog", Illustration.WORLD, SKY,
                "Start a new map: its size and shape, the land it starts with, and the version of Minecraft it is for.");
        spec("MergeWorldDialog", Illustration.MERGE, TEAL,
                "Bring the changes you made in WorldPainter into a world which has already been exported, and perhaps played in.");
        spec("ImportHeightMapDialog", Illustration.IMPORT, TEAL,
                "Build land from an image in shades of grey, where light is high and dark is low.",
                "*", "%s");
        spec("MapImportDialog", Illustration.IMPORT, TEAL,
                "Open an existing Minecraft world, to change it with WorldPainter.");
        spec("ScaleWorldDialog", Illustration.TRANSFORM, VIOLET,
                "Make the whole map larger or smaller, with everything painted on it.");
        spec("ShiftWorldDialog", Illustration.TRANSFORM, VIOLET,
                "Move the whole map, with everything painted on it, by a number of blocks.");
        spec("RotateWorldDialog", Illustration.TRANSFORM, VIOLET,
                "Turn the whole map by a quarter, half or three quarters of a turn.");
        spec("ChangeHeightDialog", Illustration.TRANSFORM, VIOLET,
                "Change the range of heights of the world, and move or stretch the land to fit.");
        spec("ConfigureViewDialog", Illustration.VIEW, SKY,
                "How the map is shown while you work: the grid, the contour lines, distances and images laid over it.");
        spec("FillDialog", Illustration.LAYERS, AMBER,
                "Fill or clear the whole world, or the selection, with a terrain type, a biome or a layer, in one go.");
        spec("CustomMaterialDialog", Illustration.GROUND, AMBER,
                "A custom terrain type: the blocks it is made of, and how they are mixed.");
    }
}
