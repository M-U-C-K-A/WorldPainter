package org.pepsoft.worldpainter.nodes;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * A kind of node which the user can add to a graph, as listed in the editor's palette.
 */
public final class NodeType {
    public NodeType(String name, String description, NodeCategory category, Supplier<Node> factory) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.factory = factory;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public NodeCategory getCategory() {
        return category;
    }

    /**
     * Create a new node of this type. Generators get a random {@link Node#VARIANT variant}, so that two generators of
     * the same type differ.
     */
    public Node create() {
        final Node node = factory.get();
        if (node.getParameters().contains(Node.VARIANT)) {
            node.set(Node.VARIANT, RANDOM.nextInt(10000));
        }
        return node;
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * All node types which can be added by the user, by category.
     */
    public static List<NodeType> getAll() {
        return ALL;
    }

    private final String name, description;
    private final NodeCategory category;
    private final Supplier<Node> factory;

    private static final Random RANDOM = new Random();
    private static final List<NodeType> ALL = List.of(
            new NodeType("Perlin noise", "Smooth fractal noise: rolling hills, continents", NodeCategory.GENERATOR, () -> new NoiseNode(NoiseNode.Kind.PERLIN)),
            new NodeType("Ridged noise", "Sharp ridges: mountain ranges", NodeCategory.GENERATOR, () -> new NoiseNode(NoiseNode.Kind.RIDGED)),
            new NodeType("Billow noise", "Rounded, puffy hills", NodeCategory.GENERATOR, () -> new NoiseNode(NoiseNode.Kind.BILLOW)),
            new NodeType("Cells (Voronoi)", "Cellular noise: mounds, pits or ridges along cell edges", NodeCategory.GENERATOR, CellsNode::new),
            new NodeType("Dunes", "Parallel wavy ridges, like sand dunes", NodeCategory.GENERATOR, () -> new NoiseNode(NoiseNode.Kind.DUNES)),
            new NodeType("Rock towers", "Tall rock pillars with steep, fluted walls: tower karst", NodeCategory.GENERATOR, TowersNode::new),
            new NodeType("Volcano", "A volcano with a crater and furrowed flanks, at a position on the map", NodeCategory.GENERATOR, VolcanoNode::new),
            new NodeType("Plateaus", "Flat topped table mountains in levels, bounded by cliffs", NodeCategory.GENERATOR, PlateausNode::new),
            new NodeType("Gradient", "A radial, square or linear gradient, for instance to make an island", NodeCategory.GENERATOR, GradientNode::new),
            new NodeType("Constant", "The same value everywhere", NodeCategory.GENERATOR, ConstantNode::new),
            new NodeType("Image", "An imported greyscale height map", NodeCategory.GENERATOR, ImageNode::new),
            new NodeType("Current map", "The map as it is now, to build on terrain painted by hand", NodeCategory.GENERATOR, CurrentMapNode::new),
            new NodeType("Add", "A + B", NodeCategory.COMBINER, () -> new CombineNode(CombineNode.Mode.ADD)),
            new NodeType("Subtract", "A \u2212 B", NodeCategory.COMBINER, () -> new CombineNode(CombineNode.Mode.SUBTRACT)),
            new NodeType("Multiply", "A \u00d7 B: use B as a mask for A", NodeCategory.COMBINER, () -> new CombineNode(CombineNode.Mode.MULTIPLY)),
            new NodeType("Maximum", "The highest of A and B", NodeCategory.COMBINER, () -> new CombineNode(CombineNode.Mode.MAX)),
            new NodeType("Minimum", "The lowest of A and B", NodeCategory.COMBINER, () -> new CombineNode(CombineNode.Mode.MIN)),
            new NodeType("Blend with mask", "A where the mask is black, B where it is white", NodeCategory.COMBINER, BlendNode::new),
            new NodeType("Curve", "Reshape the heights with a curve", NodeCategory.FILTER, CurveNode::new),
            new NodeType("Terrace", "Flat terraces separated by steep steps", NodeCategory.FILTER, TerraceNode::new),
            new NodeType("Strata", "Ledges and bands of rock of uneven thickness which dip across the map, as in canyon walls", NodeCategory.FILTER, StrataNode::new),
            new NodeType("Clamp", "Limit the values to a range", NodeCategory.FILTER, ClampNode::new),
            new NodeType("Blur", "Smooth the terrain", NodeCategory.FILTER, BlurNode::new),
            new NodeType("Erosion", "Branching gullies and valleys carved down the slopes", NodeCategory.FILTER, GullyErosionNode::new),
            new NodeType("Thermal erosion", "Steep slopes crumble into scree", NodeCategory.FILTER, ThermalErosionNode::new),
            new NodeType("Rivers", "Carve a network of winding rivers, with banks and water", NodeCategory.WATER, RiverNode::new),
            new NodeType("Lakes", "Carve scattered lakes with irregular shores, filled with water", NodeCategory.WATER, LakeNode::new),
            new NodeType("Height range", "Select the terrain between two heights, e.g. for snow or beaches", NodeCategory.MASK, HeightMaskNode::new),
            new NodeType("Slope range", "Select the terrain by steepness, e.g. for rock on cliffs", NodeCategory.MASK, SlopeMaskNode::new),
            new NodeType("Regions", "Large irregular regions of several types, to lay out biome zones", NodeCategory.MASK, RegionsNode::new),
            new NodeType("Selection", "The area selected on the map", NodeCategory.MASK, SelectionMaskNode::new),
            new NodeType("Paint terrain", "Paint a terrain type where the mask is strong", NodeCategory.SURFACE, TerrainPainterNode::new),
            new NodeType("Paint material mix", "Paint a ready made mix of blocks (mixed dirt, forest floor, scree, cliff strata...) where the mask is strong", NodeCategory.SURFACE, MaterialPainterNode::new),
            new NodeType("Paint biome", "Paint a Minecraft biome where the mask is strong", NodeCategory.SURFACE, BiomePainterNode::new),
            new NodeType("Floating islands", "Islands floating in the sky where the Shape input is above one half, with their tops at the height of the Top input", NodeCategory.STRUCTURE, FloatingIslandsNode::new),
            new NodeType("Paint flora", "Plant forests, grass, flowers or snow, as dense as the mask", NodeCategory.SURFACE, FloraPainterNode::new));
}
