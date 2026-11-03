package org.pepsoft.worldpainter.docking;

import org.junit.Test;
import org.pepsoft.worldpainter.docking.AreaTree.Leaf;
import org.pepsoft.worldpainter.docking.AreaTree.Node;
import org.pepsoft.worldpainter.docking.AreaTree.Split;

import java.awt.*;
import java.util.List;

import static org.junit.Assert.*;
import static org.pepsoft.worldpainter.docking.AreaTree.AUTO;
import static org.pepsoft.worldpainter.docking.AreaTree.FILL;

public class AreaTreeTest {
    @Test
    public void textFormRoundTrips() {
        final AreaTree tree = tree(LAYOUT);
        assertEquals(LAYOUT, tree.toString());
        assertEquals(6, tree.leaves().size());
        assertEquals("map", tree.leaves().get(3).page);
        // Names with special characters survive
        final AreaTree named = tree("H[*=L(paint,customLayerPalette.My+%28odd%29+palette%3B);100=L(,)]");
        assertEquals("customLayerPalette.My (odd) palette;", named.leaves().get(0).page);
        assertNull(named.leaves().get(1).group);
        assertEquals("H[*=L(paint,customLayerPalette.My+%28odd%29+palette%3B);100=L(,)]", named.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidTextIsRejected() {
        AreaTree.parse("H[*=L(map,map)", 1.0f);
    }

    @Test
    public void panelsKeepTheirSizeAndTheMapFills() {
        final AreaTree tree = tree(LAYOUT);
        tree.layout(new Rectangle(0, 0, 1600, 900), SIZES);
        final Leaf map = leaf(tree, "map");
        assertEquals(1600 - 270 - 320 - 2 * DIVIDER, map.bounds.width);
        assertEquals(270, leaf(tree, "tools").bounds.width);
        // A wider window only makes the map wider
        tree.layout(new Rectangle(0, 0, 1920, 900), SIZES);
        assertEquals(1920 - 270 - 320 - 2 * DIVIDER, map.bounds.width);
        assertEquals(320, leaf(tree, "brushSettings").bounds.width);
        // Areas which take the size of their contents get it, and the one which fills gets the rest
        assertEquals(PREFERRED, leaf(tree, "tools").bounds.height);
        assertEquals(PREFERRED, leaf(tree, "toolSettings").bounds.height);
        assertEquals(900 - 2 * PREFERRED - 2 * DIVIDER, leaf(tree, "terrain").bounds.height);
    }

    @Test
    public void tooLittleRoomShrinksTheOthersDownToTheirMinimum() {
        final AreaTree tree = tree(LAYOUT);
        tree.layout(new Rectangle(0, 0, 400, 900), SIZES);
        final Leaf map = leaf(tree, "map");
        assertEquals(MINIMUM, map.bounds.width);
        int total = 0;
        for (Node child: ((Split) tree.getRoot()).children) {
            assertTrue(child.actualSize >= MINIMUM);
            total += child.actualSize;
        }
        assertEquals(400 - 2 * DIVIDER, total);
    }

    @Test
    public void splittingPutsTheNewAreaOnTheRightSide() {
        final AreaTree tree = tree(LAYOUT);
        tree.layout(new Rectangle(0, 0, 1600, 900), SIZES);
        final Leaf map = leaf(tree, "map");
        final Leaf view3d = new Leaf("view3d", "view3d");
        tree.insert(view3d, map, Side.EAST, 400);
        // Same direction as the row: it goes in the row, next to the map, which still fills
        assertSame(tree.getRoot(), view3d.parent);
        assertEquals(4, ((Split) tree.getRoot()).children.size());
        assertEquals(FILL, map.size);
        tree.layout(new Rectangle(0, 0, 1600, 900), SIZES);
        assertEquals(400, view3d.bounds.width);
        assertEquals(map.bounds.x + map.bounds.width + DIVIDER, view3d.bounds.x);

        // Across the row: a new split, the old area filling it
        final Leaf info = new Leaf("properties", "infoPanel");
        tree.insert(info, map, Side.SOUTH, 200);
        assertTrue(map.parent != tree.getRoot());
        assertFalse(map.parent.horizontal);
        assertSame(map.parent, info.parent);
        assertEquals(FILL, map.size);
        tree.layout(new Rectangle(0, 0, 1600, 900), SIZES);
        assertEquals(200, info.bounds.height);
        assertEquals(map.bounds.width, info.bounds.width);
        assertEquals(map.bounds.y + map.bounds.height + DIVIDER, info.bounds.y);
    }

    @Test
    public void removingGivesTheSpaceToANeighbourAndNormalises() {
        final AreaTree tree = tree(LAYOUT);
        tree.layout(new Rectangle(0, 0, 1600, 900), SIZES);
        final Leaf map = leaf(tree, "map");
        final Leaf info = new Leaf("properties", "infoPanel");
        tree.insert(info, map, Side.SOUTH, 200);
        tree.layout(new Rectangle(0, 0, 1600, 900), SIZES);
        tree.remove(info, null);
        // The split it was in is gone again
        assertSame(tree.getRoot(), map.parent);
        assertEquals(FILL, map.size);
        assertEquals(LAYOUT, tree.toString());

        // Removing the area which fills makes the neighbour fill
        final Leaf terrain = leaf(tree, "terrain");
        final Leaf toolSettings = leaf(tree, "toolSettings");
        tree.remove(terrain, null);
        assertEquals(FILL, toolSettings.size);
    }

    @Test
    public void onlyNeighboursWhichLineUpCanBeJoined() {
        final AreaTree tree = tree(LAYOUT);
        tree.layout(new Rectangle(0, 0, 1600, 900), SIZES);
        final Leaf tools = leaf(tree, "tools"), toolSettings = leaf(tree, "toolSettings"), terrain = leaf(tree, "terrain"), map = leaf(tree, "map");
        assertTrue(tree.canJoin(tools, toolSettings));
        assertFalse(tree.canJoin(tools, terrain));
        // The map shares an edge with the whole left column, but not with any single area in it
        assertFalse(tree.canJoin(map, tools));
        assertEquals(Side.SOUTH, AreaTree.sideOf(tools, toolSettings));
        assertEquals(Side.NORTH, AreaTree.sideOf(toolSettings, tools));
        assertNull(AreaTree.sideOf(map, tools));

        final int toolsHeight = tools.bounds.height, toolSettingsHeight = toolSettings.bounds.height;
        tree.join(tools, toolSettings);
        assertEquals(5, tree.leaves().size());
        assertEquals(toolsHeight + toolSettingsHeight + DIVIDER, tools.size);
        tree.layout(new Rectangle(0, 0, 1600, 900), SIZES);
        assertEquals(toolsHeight + toolSettingsHeight + DIVIDER, tools.bounds.height);
    }

    @Test
    public void joiningTheLastTwoAreasOfASplitRemovesIt() {
        final AreaTree tree = tree("H[300=L(tools,tools);*=V[*=L(map,map);200=L(view3d,view3d)]]");
        tree.layout(new Rectangle(0, 0, 1000, 800), SIZES);
        final Leaf map = leaf(tree, "map"), view3d = leaf(tree, "view3d");
        tree.join(map, view3d);
        assertSame(tree.getRoot(), map.parent);
        assertEquals(FILL, map.size);
        assertEquals("H[300=L(tools,tools);*=L(map,map)]", tree.toString());
    }

    @Test
    public void splitsInTheSameDirectionAreFlattened() {
        final AreaTree tree = tree("H[300=L(tools,tools);*=V[*=L(map,map);200=L(view3d,view3d)]]");
        tree.layout(new Rectangle(0, 0, 1000, 800), SIZES);
        final Leaf view3d = leaf(tree, "view3d");
        // Take the 3D view out: the column holding the map is left with one area, which takes its place in the row
        tree.remove(view3d, null);
        assertEquals("H[300=L(tools,tools);*=L(map,map)]", tree.toString());
        // Put it to the right of the map: straight into the row
        final Leaf map = leaf(tree, "map");
        tree.insert(view3d, map, Side.EAST, 250);
        assertEquals("H[300=L(tools,tools);*=L(map,map);250=L(view3d,view3d)]", tree.toString());
    }

    @Test
    public void draggingADividerResizesOnlyItsNeighbours() {
        final AreaTree tree = tree(LAYOUT);
        tree.layout(new Rectangle(0, 0, 1600, 900), SIZES);
        final Split root = (Split) tree.getRoot();
        final Node left = root.children.get(0), map = root.children.get(1), right = root.children.get(2);
        assertEquals(30, tree.moveDivider(left, map, 30, SIZES));
        tree.layout(new Rectangle(0, 0, 1600, 900), SIZES);
        assertEquals(300, left.actualSize);
        assertEquals(320, right.actualSize);
        assertEquals(1600 - 300 - 320 - 2 * DIVIDER, map.actualSize);
        // Not past the minimum size of the map
        final int moved = tree.moveDivider(map, right, -2000, SIZES);
        assertEquals(-(1600 - 300 - 320 - 2 * DIVIDER - MINIMUM), moved);
        tree.layout(new Rectangle(0, 0, 1600, 900), SIZES);
        assertEquals(MINIMUM, map.actualSize);

        // Between two areas which take the size of their contents
        final Leaf tools = leaf(tree, "tools"), toolSettings = leaf(tree, "toolSettings");
        assertEquals(AUTO, tools.size);
        tree.moveDivider(tools, toolSettings, 20, SIZES);
        tree.layout(new Rectangle(0, 0, 1600, 900), SIZES);
        assertEquals(PREFERRED + 20, tools.bounds.height);
        assertEquals(PREFERRED - 20, toolSettings.bounds.height);
    }

    @Test
    public void collapsedNodesGetNoRoom() {
        final AreaTree tree = tree(LAYOUT);
        final Split root = (Split) tree.getRoot();
        final Node left = root.children.get(0);
        final AreaTree.Sizes sizes = new AreaTree.Sizes() {
            @Override public int getPreferredSize(Leaf leaf, boolean horizontal) { return PREFERRED; }
            @Override public int getMinimumSize(Leaf leaf, boolean horizontal) { return MINIMUM; }
            @Override public boolean isCollapsed(Node node) { return node == left; }
        };
        tree.layout(new Rectangle(0, 0, 1600, 900), sizes);
        final Leaf map = leaf(tree, "map");
        assertEquals(0, map.bounds.x);
        assertEquals(1600 - 320 - DIVIDER, map.bounds.width);
        assertTrue(leaf(tree, "tools").bounds.isEmpty());
        assertEquals(1, root.dividers.size());
    }

    @Test
    public void sharesOfTheSplitScaleWithIt() {
        final AreaTree tree = tree("H[250=L(tools,tools);*=L(map,map);45%=L(view3d,view3d)]");
        assertEquals("H[250=L(tools,tools);*=L(map,map);45%=L(view3d,view3d)]", tree.toString());
        final Leaf map = leaf(tree, "map"), view3d = leaf(tree, "view3d");
        tree.layout(new Rectangle(0, 0, 1010, 800), SIZES);
        assertEquals(450, view3d.bounds.width);
        assertEquals(1000 - 250 - 450, map.bounds.width);
        tree.layout(new Rectangle(0, 0, 2010, 800), SIZES);
        assertEquals(900, view3d.bounds.width);
        // Dragging the divider keeps it a share
        assertEquals(-100, tree.moveDivider(map, view3d, -100, SIZES));
        assertEquals(AreaTree.FRACTION, view3d.size);
        tree.layout(new Rectangle(0, 0, 2010, 800), SIZES);
        assertEquals(1000, view3d.bounds.width);
        assertEquals("H[250=L(tools,tools);*=L(map,map);50%=L(view3d,view3d)]", tree.toString());
    }

    private static AreaTree tree(String text) {
        return new AreaTree(AreaTree.parse(text, 1.0f), DIVIDER);
    }

    private static Leaf leaf(AreaTree tree, String page) {
        final List<Leaf> leaves = tree.leaves();
        for (Leaf leaf: leaves) {
            if (page.equals(leaf.page)) {
                return leaf;
            }
        }
        throw new AssertionError("No leaf " + page);
    }

    private static final String LAYOUT = "H[270=V[a=L(tools,tools);a=L(toolSettings,toolSettings);*=L(paint,terrain)];*=L(map,map);320=V[a=L(brushes,brushes);*=L(properties,brushSettings)]]";
    private static final int DIVIDER = 5, PREFERRED = 150, MINIMUM = 60;
    private static final AreaTree.Sizes SIZES = new AreaTree.Sizes() {
        @Override public int getPreferredSize(Leaf leaf, boolean horizontal) { return PREFERRED; }
        @Override public int getMinimumSize(Leaf leaf, boolean horizontal) { return MINIMUM; }
        @Override public boolean isCollapsed(Node node) { return false; }
    };
}
