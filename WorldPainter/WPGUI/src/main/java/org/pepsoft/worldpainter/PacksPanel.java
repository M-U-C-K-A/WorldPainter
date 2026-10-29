package org.pepsoft.worldpainter;

import com.formdev.flatlaf.FlatClientProperties;
import org.pepsoft.worldpainter.help.CompositionCards;
import org.pepsoft.worldpainter.help.ToolCard;
import org.pepsoft.worldpainter.layers.bo2.TreePack;
import org.pepsoft.worldpainter.layers.plants.PlantLayerPresets;
import org.pepsoft.worldpainter.util.Icons;
import org.pepsoft.worldpainter.util.ResponsiveGridLayout;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.awt.GridBagConstraints.HORIZONTAL;
import static org.pepsoft.worldpainter.App.createSectionLabel;

/**
 * The library of ready made things to paint with, so that a new world can be dressed without any setting up: trees
 * from the tree pack, flowers, plants and water plants, and mixes of blocks for the ground, rock and cliffs. Clicking
 * one adds it to the world (as a layer or a custom terrain type), or selects it if it was added before, ready for
 * painting.
 */
class PacksPanel extends JPanel {
    PacksPanel(Consumer<TreePack.Kind> treeHandler, Consumer<PlantLayerPresets.Preset> plantHandler, Consumer<MaterialGroup> materialHandler, ColourScheme colourScheme) {
        super(new GridBagLayout());
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridwidth = GridBagConstraints.REMAINDER;
        constraints.anchor = GridBagConstraints.FIRST_LINE_START;
        constraints.fill = HORIZONTAL;
        constraints.weightx = 1.0;

        // Search field, filtering the items by name
        final JTextField searchField = new JTextField();
        searchField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Search packs");
        searchField.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, Icons.muted("search", 14));
        searchField.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        constraints.insets = new Insets(4, 2, 4, 2);
        add(searchField, constraints);

        final Map<String, List<JButton>> trees = new LinkedHashMap<>();
        for (TreePack.Kind kind: TreePack.Kind.values()) {
            final String description = "Custom trees from the WorldPainter tree pack, in eight variants. Paint them like any custom object layer. Click to add them and paint.";
            trees.computeIfAbsent("Trees", k -> new ArrayList<>()).add(createButton(swatch(kind.getColour(), treeIcon(kind)), kind.toString(),
                    () -> new ToolCard(kind.toString(), description, null, null, null), () -> treeHandler.accept(kind)));
        }
        addSections(trees, constraints);

        final Map<String, List<JButton>> plants = new LinkedHashMap<>();
        for (PlantLayerPresets.Preset preset: PlantLayerPresets.Preset.values()) {
            final String description = preset.getDescription() + ". A plant layer: the more intensity you paint it with, the denser the plants. Click to add it and paint.";
            plants.computeIfAbsent(preset.getFamily().toString(), k -> new ArrayList<>()).add(createButton(swatch(preset.getColour(), plantIcon(preset.getFamily())), preset.toString(),
                    () -> CompositionCards.forPlants(preset.toString(), description, PlantLayerPresets.create(preset), colourScheme), () -> plantHandler.accept(preset)));
        }
        addSections(plants, constraints);

        final Map<String, List<JButton>> materials = new LinkedHashMap<>();
        for (MaterialGroup group: MaterialGroup.values()) {
            final MixedMaterial material = group.newMaterial();
            materials.computeIfAbsent(group.getFamily().toString(), k -> new ArrayList<>()).add(createButton(new ImageIcon(material.getIcon(colourScheme)), group.toString(),
                    () -> CompositionCards.forMaterial(group.toString(), "A mix of blocks, added as a custom terrain type. Click to add it and paint.", material, colourScheme), () -> materialHandler.accept(group)));
        }
        addSections(materials, constraints);

        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { filter(searchField.getText()); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { filter(searchField.getText()); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { filter(searchField.getText()); }
        });

        // Keep the sections at the top
        constraints.weighty = 1.0;
        add(Box.createGlue(), constraints);
    }

    private void addSections(Map<String, List<JButton>> sections, GridBagConstraints constraints) {
        for (Map.Entry<String, List<JButton>> entry: sections.entrySet()) {
            final JLabel label = createSectionLabel(entry.getKey());
            constraints.insets = new Insets(this.sections.isEmpty() ? 2 : 8, 4, 3, 4);
            add(label, constraints);
            final JPanel grid = new JPanel(new ResponsiveGridLayout(2, 6));
            entry.getValue().forEach(grid::add);
            constraints.insets = new Insets(0, 2, 0, 2);
            add(grid, constraints);
            this.sections.put(label, entry.getValue());
        }
    }

    private void filter(String text) {
        final String query = text.trim().toLowerCase();
        sections.forEach((label, buttons) -> {
            boolean anyVisible = query.isEmpty() || label.getText().toLowerCase().contains(query);
            final boolean sectionMatches = anyVisible;
            for (JButton button: buttons) {
                final boolean visible = sectionMatches || ((String) button.getClientProperty(KEY_NAME)).toLowerCase().contains(query);
                button.setVisible(visible);
                anyVisible |= visible;
            }
            label.setVisible(anyVisible);
            buttons.get(0).getParent().setVisible(anyVisible);
        });
        revalidate();
        repaint();
    }

    private static JButton createButton(Icon icon, String name, Supplier<ToolCard> card, Runnable action) {
        final JButton button = ToolCard.button();
        button.setIcon(icon);
        button.setMargin(App.SMALLER_BUTTON_INSETS);
        button.setToolTipText(name);
        ToolCard.install(button, card);
        button.putClientProperty(KEY_NAME, name);
        button.addActionListener(e -> action.run());
        return button;
    }

    private static String treeIcon(TreePack.Kind kind) {
        switch (kind) {
            case TALL_SPRUCE:
            case FIR:
            case REDWOOD:
                return "tree-pine";
            case PALM:
                return "tree-palm";
            default:
                return "tree-deciduous";
        }
    }

    private static String plantIcon(PlantLayerPresets.Family family) {
        switch (family) {
            case WATER:
                return "waves";
            case CROPS:
                return "sprout";
            case FLOWERS:
                return "flower";
            default:
                return "leaf";
        }
    }

    /**
     * A rounded swatch in a colour, with an icon on it in a colour which contrasts with it.
     */
    private static Icon swatch(int colour, String glyph) {
        final Color background = new Color(colour);
        final boolean light = (background.getRed() * 299 + background.getGreen() * 587 + background.getBlue() * 114) / 1000 > 150;
        final Icon icon = Icons.exists(glyph) ? Icons.tinted(glyph, 16, light ? new Color(0x18181b) : Color.WHITE) : null;
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                final Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(background);
                    g2.fillRoundRect(x, y, SWATCH_SIZE, SWATCH_SIZE, 7, 7);
                    if (icon != null) {
                        icon.paintIcon(c, g2, x + (SWATCH_SIZE - icon.getIconWidth()) / 2, y + (SWATCH_SIZE - icon.getIconHeight()) / 2);
                    }
                } finally {
                    g2.dispose();
                }
            }

            @Override
            public int getIconWidth() {
                return SWATCH_SIZE;
            }

            @Override
            public int getIconHeight() {
                return SWATCH_SIZE;
            }
        };
    }

    private final Map<JLabel, List<JButton>> sections = new LinkedHashMap<>();

    private static final String KEY_NAME = "org.pepsoft.worldpainter.packs.name";
    private static final int SWATCH_SIZE = 24;

    /**
     * The name of the palette the layers from the packs are put in.
     */
    static final String PALETTE = "Packs";
}
