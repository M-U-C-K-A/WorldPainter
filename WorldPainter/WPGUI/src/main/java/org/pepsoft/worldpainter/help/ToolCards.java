package org.pepsoft.worldpainter.help;

import org.pepsoft.util.SystemUtils;

/**
 * The cards of the tools: what each one does, and what the mouse and the keys do with it, so that they can be taken in
 * at a glance.
 */
public final class ToolCards {
    private ToolCards() {
        // Prevent instantiation
    }

    /**
     * @param operationType The simple name of the class of the operation.
     * @param name          The name of the operation.
     * @param description   Its own description, for operations which aren't known here.
     * @param shortcut      Its keyboard shortcut, if any.
     * @param demo          The animation of what it does, if any.
     */
    public static ToolCard forTool(String operationType, String name, String description, String shortcut, ToolDemo demo) {
        final ToolCard card;
        switch (operationType) {
            case "Height":
                card = card(name, "Raises or lowers the land under the brush.", shortcut, demo)
                        .action("Drag", "Raise the land")
                        .action("Right-drag", "Lower the land")
                        .tip("The brush sets the shape, the intensity the speed.");
                break;
            case "Flatten":
                card = card(name, "Levels the land to a single height.", shortcut, demo)
                        .action("Drag", "Level to the height where you started")
                        .tip("For building sites, fields and roads.");
                break;
            case "Smooth":
                card = card(name, "Softens rough or jagged land without changing its overall shape.", shortcut, demo)
                        .action("Drag", "Smooth the land");
                break;
            case "SmoothHalfBlocks":
                card = card(name, "Smooths the land like Smooth, and builds it in half blocks in Minecraft: gentle slopes go up by half a block instead of a whole block every other column.", shortcut, demo)
                        .action("Drag", "Smooth and build in half blocks")
                        .action("Right-drag", "Back to whole blocks")
                        .tip("Slabs on stone, cobblestone, sandstone and the like, snow layers on snow blocks. Grass, dirt and sand have no slab and stay whole.");
                break;
            case "RaiseMountain":
                card = card(name, "Grows a mountain with ridges under the brush.", shortcut, demo)
                        .action("Hold", "Grow the mountain")
                        .action("Right-hold", "Lower the land in the same shape")
                        .tip("The larger the brush, the larger the mountain.");
                break;
            case "RaisePyramid":
                card = card(name, "Raises a square pyramid, for sharp peaks and man made shapes.", shortcut, demo)
                        .action("Hold", "Grow the pyramid");
                break;
            case "RaiseRotatedPyramid":
                card = card(name, "Raises a pyramid turned by 45 degrees, with its ridges along the axes.", shortcut, demo)
                        .action("Hold", "Grow the pyramid");
                break;
            case "PathTool":
                card = card(name, "Lays out a road or track through the points you click, following the land.", shortcut, demo)
                        .action("Click", "Add a point")
                        .action("Right-click", "Remove the last point")
                        .action("Enter", "Choose how to build it")
                        .action("Escape", "Start again");
                break;
            case "Pencil":
                card = card(name, "Paints the selected terrain, layer or biome with a hard edge.", shortcut, demo)
                        .action("Drag", "Paint")
                        .action("Right-drag", "Remove")
                        .action("Shift+click", "Straight line from the last point")
                        .action(CTRL + "+drag", "Stay on straight or 45° lines");
                break;
            case "SprayPaint":
                card = card(name, "Sprays the selected terrain, layer or biome. It builds up gradually, for natural transitions.", shortcut, demo)
                        .action("Drag", "Spray")
                        .action("Right-drag", "Remove");
                break;
            case "Fill":
                card = card(name, "Fills a connected area which has the same terrain, layer or height with what is selected.", shortcut, demo)
                        .action("Click", "Fill the area")
                        .action("Right-click", "Remove from the area");
                break;
            case "Flood":
                final String liquid = name.toLowerCase().contains("lava") ? "lava" : "water";
                card = card(name, "Fills low ground with " + liquid + " as far as it is enclosed" + (liquid.equals("water") ? ": lakes, seas and rivers." : "."), shortcut, demo)
                        .action("Click", "Flood up to this height, or raise the " + liquid + " by one")
                        .action("Right-click", "Lower the " + liquid + " by one");
                break;
            case "Sponge":
                card = card(name, "Removes water and lava.", shortcut, demo)
                        .action("Drag", "Dry up")
                        .action("Right-drag", "Back to the default water level");
                break;
            case "EditSelectionOperation":
                card = card(name, "Paints the selection with the brush. The other tools can then be limited to it.", shortcut, demo)
                        .action("Drag", "Add to the selection")
                        .action("Right-drag", "Remove from the selection");
                break;
            case "RectangleSelectionOperation":
                card = card(name, "Selects a rectangle. The other tools can then be limited to it.", shortcut, demo)
                        .action("Drag", "Select a rectangle")
                        .action("Right-drag", "Remove a rectangle")
                        .action("Shift+drag", "Make it a square")
                        .action(CTRL + "+drag", "Make it an ellipse");
                break;
            case "CopySelectionOperation":
                card = card(name, "Copies the selected area, with its terrain and layers, to elsewhere on the map.", shortcut, demo)
                        .action("Click", "Place the copy here");
                break;
            case "SetSpawnPoint":
                card = card(name, "Sets where players appear when they first join the world.", shortcut, demo)
                        .action("Click", "Set the spawn point here");
                break;
            case "Text":
                card = card(name, "Writes text on the map, with a terrain, a layer or as an annotation.", shortcut, demo)
                        .action("Click", "Place text here");
                break;
            default:
                card = card(name, description, shortcut, demo);
                break;
        }
        return card;
    }

    public static ToolCard eyedropper(String shortcut) {
        return card("Eyedropper", "Picks a terrain or layer from the map, to paint with it.", shortcut, ToolDemo.EYEDROPPER)
                .action("Click", "Pick what is here");
    }

    public static ToolCard globalOperations() {
        return card("Global operations", "Fills or clears the whole world, or only the selection, with a terrain, a biome or a layer, in one go.", null, ToolDemo.GLOBAL);
    }

    public static ToolCard clearSelection() {
        return card("Clear selection", "Removes the selection, so that the tools can work on the whole map again.", null, ToolDemo.CLEAR_SELECTION);
    }

    /**
     * @param preview The preview of the shape of the brush.
     */
    public static ToolCard brush(String name, ToolCard.Preview preview) {
        return new ToolCard(name, "The picture shows the land this brush raises. When painting, it paints most where the land is highest.", null, preview, null)
                .action("Scroll", "On the map: change its size")
                .action("Shift+scroll", "On the map: change its size by one")
                .action("Alt+scroll", "On the map: rotate it");
    }

    private static ToolCard card(String name, String description, String shortcut, ToolDemo demo) {
        return new ToolCard(name, description, shortcut, demo);
    }

    private static final String CTRL = SystemUtils.isMac() ? "Cmd" : "Ctrl";
}
