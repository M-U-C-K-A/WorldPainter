package org.pepsoft.worldpainter.layers.bo2;

import org.pepsoft.minecraft.Material;
import org.pepsoft.util.AttributeKey;
import org.pepsoft.worldpainter.layers.Bo2Layer;
import org.pepsoft.worldpainter.objects.GenericObject;
import org.pepsoft.worldpainter.objects.WPObject;

import javax.vecmath.Point3i;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * A pack of custom trees which ship with WorldPainter: tall sparse spruces, dense firs, rainforest giants with wide flat
 * canopies, big oaks, birches, dead trees and bushes. Each kind is generated (always the same way) in several sizes
 * and shapes, and comes as a custom object layer which plants a random one of them at a time.
 */
public final class TreePack {
    private TreePack() {
        // Prevent instantiation
    }

    public enum Kind {
        TALL_SPRUCE("Tall spruces", 0x24452f, 4, 3),
        FIR("Firs", 0x2b5a3a, 4, 3),
        RAINFOREST("Rainforest giants", 0x3f7f2a, 8, 4),
        BIG_OAK("Big oaks", 0x3a6b2a, 6, 4),
        BIRCH("Birches", 0x6e9b45, 3, 3),
        CHERRY("Cherry trees", 0xf0a3c8, 6, 4),
        DEAD("Dead trees", 0x6b5a48, 5, 4),
        BUSH("Bushes", 0x4d7a35, 2, 2),
        PALM("Palm trees", 0x5c9a3a, 5, 4),
        ACACIA("Acacias", 0x8a9a3a, 7, 3),
        WILLOW("Willows", 0x5d8a3a, 7, 3),
        AZALEA("Azalea bushes", 0x7fa04a, 3, 3),
        PALE_OAK("Pale oaks", 0x9a9a8a, 6, 4),
        REDWOOD("Giant redwoods", 0x6b3a2a, 7, 3),
        SLENDER_SPRUCE("Slender spruces", 0x234a32, 3, 3),
        LARCH("Larches", 0x8aa64a, 3, 3);

        Kind(String displayName, int colour, int grid, int density) {
            this.displayName = displayName;
            this.colour = colour;
            this.grid = grid;
            this.density = density;
        }

        public int getColour() {
            return colour;
        }

        /**
         * The name of the layer for this kind of tree.
         */
        public String getLayerName() {
            return displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }

        private final String displayName;
        private final int colour, grid, density;
    }

    /**
     * Create the custom object layer for a kind of tree, with its variants.
     */
    public static Bo2Layer createLayer(Kind kind) {
        final Bo2Layer layer = new Bo2Layer(createObjects(kind), kind.displayName + DESCRIPTION_SUFFIX, new Color(kind.colour));
        // Spread the trees out on a grid with some random displacement, so that big crowns do not overlap too much
        layer.setGridX(kind.grid);
        layer.setGridY(kind.grid);
        layer.setRandomDisplacement(Math.max(1, kind.grid - 1));
        layer.setDensity(kind.density);
        return layer;
    }

    /**
     * The trees of a layer of this pack which was made by an earlier version of it, made again, so that worlds get the
     * current trees; or {@code null} if the layer is not one of this pack, or is up to date.
     */
    public static Bo2ObjectProvider upgrade(Bo2Layer layer) {
        if ((layer.getDescription() == null) || (! layer.getDescription().endsWith(DESCRIPTION_SUFFIX)) || (! (layer.getObjectProvider() instanceof Bo2ObjectTube))) {
            return null;
        }
        for (Kind kind: Kind.values()) {
            if (layer.getDescription().equals(kind.displayName + DESCRIPTION_SUFFIX)) {
                final List<WPObject> objects = layer.getObjectProvider().getAllObjects();
                if ((! objects.isEmpty()) && (objects.get(0).getAttribute(ATTRIBUTE_VERSION) >= VERSION)) {
                    return null;
                }
                return createObjects(kind);
            }
        }
        return null;
    }

    private static Bo2ObjectTube createObjects(Kind kind) {
        final List<WPObject> objects = new ArrayList<>();
        final Random random = new Random(kind.ordinal() * 7919L + 17);
        for (int i = 0; i < VARIANTS; i++) {
            objects.add(create(kind, i, random));
        }
        return new Bo2ObjectTube(kind.displayName, objects);
    }

    static WPObject create(Kind kind, int variant, Random random) {
        switch (kind) {
            case TALL_SPRUCE:
                return tallSpruce(variant, random);
            case FIR:
                return fir(variant, random);
            case RAINFOREST:
                return rainforest(variant, random);
            case BIG_OAK:
                return bigOak(variant, random);
            case BIRCH:
                return birch(variant, random);
            case CHERRY:
                return cherry(variant, random);
            case DEAD:
                return dead(variant, random);
            case BUSH:
                return bush(variant, random);
            case PALM:
                return palm(variant, random);
            case ACACIA:
                return acacia(variant, random);
            case WILLOW:
                return willow(variant, random);
            case AZALEA:
                return azalea(variant, random);
            case PALE_OAK:
                return paleOak(variant, random);
            case REDWOOD:
                return redwood(variant, random);
            case SLENDER_SPRUCE:
                return slenderSpruce(variant, random);
            case LARCH:
                return larch(variant, random);
            default:
                throw new InternalError();
        }
    }

    /**
     * A tall spruce: a long straight trunk, bare at the bottom, with close tiers of drooping branches which narrow to a
     * spike.
     */
    private static WPObject tallSpruce(int variant, Random random) {
        final int height = 22 + random.nextInt(13);
        final Builder b = new Builder("Tall spruce " + (variant + 1));
        final Material log = log("spruce"), leaves = leaves("spruce");
        final int thickness = (height > 30) ? 2 : 1;
        final double c = (thickness - 1) / 2.0 + 0.5;
        b.trunk(log, height, thickness);
        final int firstTier = (int) (height * (0.28 + random.nextDouble() * 0.1));
        for (int z = firstTier; z < height; z++) {
            final double t = (double) (z - firstTier) / (height - firstTier);
            // Every other layer a wide tier, in between a narrow one, so that the tree looks layered but full
            final double radius = ((z - firstTier) % 2 == 0)
                    ? (thickness * 0.5 + 0.9 + (1 - t) * (3.0 + random.nextDouble() * 0.8))
                    : (thickness * 0.5 + 0.5 + (1 - t) * 1.6);
            b.disc(leaves, c, c, z, radius, 0.92, random);
            if ((z - firstTier) % 2 == 0) {
                // The tips of the tier droop a little
                b.ring(leaves, c, c, z - 1, radius - 0.6, radius + 0.2, 0.35, random);
            }
        }
        b.column(leaves, height, height + 2);
        return b.build();
    }

    /**
     * A slender mountain spruce: a narrow column of short layered branches, as on steep alpine slopes, from young
     * trees to tall ones.
     */
    private static WPObject slenderSpruce(int variant, Random random) {
        final int height = 9 + random.nextInt(14);
        final Builder b = new Builder("Slender spruce " + (variant + 1));
        final Material log = log("spruce"), leaves = leaves("spruce");
        b.trunk(log, height, 1);
        final int bottom = 1 + random.nextInt(2);
        final double width = 1.3 + random.nextDouble() * 0.7 + height * 0.03;
        for (int z = bottom; z < height; z++) {
            final double t = (double) (z - bottom) / (height - bottom);
            // Narrowing slowly up the trunk, and quickly near the top, with every other layer a little wider
            final double radius = 0.6 + width * Math.pow(1 - t, 0.6) + (((z - bottom) % 2 == 0) ? 0.45 : 0);
            b.disc(leaves, 0.5, 0.5, z, radius, 0.9, random);
        }
        b.column(leaves, height, height + 2);
        return b.build();
    }

    /**
     * A larch: a narrow, airy cone of light green needles on a straight trunk.
     */
    private static WPObject larch(int variant, Random random) {
        final int height = 11 + random.nextInt(10);
        final Builder b = new Builder("Larch " + (variant + 1));
        final Material log = log("spruce"), leaves = leaves("birch");
        b.trunk(log, height, 1);
        final int bottom = 2 + random.nextInt(3);
        for (int z = bottom; z < height; z++) {
            final double t = (double) (z - bottom) / (height - bottom);
            final double radius = 0.6 + (1 - t) * (2.2 + random.nextDouble() * 0.4);
            b.disc(leaves, 0.5, 0.5, z, radius, 0.72, random);
        }
        b.column(leaves, height, height + 1);
        return b.build();
    }

    /**
     * A dense conical fir.
     */
    private static WPObject fir(int variant, Random random) {
        final int height = 12 + random.nextInt(9);
        final Builder b = new Builder("Fir " + (variant + 1));
        final Material log = log("spruce"), leaves = leaves("spruce");
        b.trunk(log, height, 1);
        final int bottom = 2 + random.nextInt(2);
        for (int z = bottom; z < height; z++) {
            final double t = (double) (z - bottom) / (height - bottom);
            final double radius = 0.7 + (1 - t) * 3.8 + (((z - bottom) % 3 == 0) ? 0.7 : 0);
            b.disc(leaves, 0.5, 0.5, z, radius, 0.95, random);
        }
        b.column(leaves, height, height + 1);
        return b.build();
    }

    /**
     * A rainforest giant: a tall, thick trunk on buttress roots, with limbs at the top spreading a wide, lumpy canopy.
     */
    private static WPObject rainforest(int variant, Random random) {
        final int height = 18 + random.nextInt(11);
        final double spread = 5.5 + random.nextDouble() * 3;
        final Builder b = new Builder("Rainforest giant " + (variant + 1));
        final Material log = log("jungle"), leaves = leaves("jungle");
        b.trunk(log, height, 2);
        for (int d = 0; d < 4; d++) {
            b.branch(log, 0, d, 2, 1, 1);
            b.branch(log, 1, d, 1, 1, 1);
        }
        final int limbs = 4 + random.nextInt(3);
        for (int i = 0; i < limbs; i++) {
            final double angle = (i + random.nextDouble() * 0.6) * Math.PI * 2 / limbs, reach = spread * (0.55 + random.nextDouble() * 0.35);
            final int z = height - 1 + random.nextInt(3);
            final double x = 1 + Math.cos(angle) * reach, y = 1 + Math.sin(angle) * reach;
            b.line(log, 1, 1, height - 4 - random.nextInt(3), x, y, z);
            b.cluster(leaves, x, y, z + 1, 2.6 + random.nextDouble(), 1.6, random);
        }
        b.blob(leaves, 1, 1, height + 1, spread * 0.6, 2.0, 0.9, random);
        return b.build();
    }

    /**
     * A big oak: a stout trunk with a few thick limbs reaching up and out, each ending in a round clump of leaves, under
     * a full crown.
     */
    private static WPObject bigOak(int variant, Random random) {
        final int height = 8 + random.nextInt(6);
        final int thickness = (height >= 11) ? 2 : 1;
        final double c = (thickness - 1) / 2.0 + 0.5;
        final Builder b = new Builder("Big oak " + (variant + 1));
        final Material log = log("oak"), leaves = leaves("oak");
        b.trunk(log, height, thickness);
        b.cluster(leaves, c, c, height + 1, 3.2 + random.nextDouble() * 0.6, 2.6, random);
        final int limbs = 4 + random.nextInt(3);
        for (int i = 0; i < limbs; i++) {
            final double angle = (i + random.nextDouble() * 0.7) * Math.PI * 2 / limbs, reach = 3 + random.nextDouble() * 2.5;
            final int from = (int) (height * (0.5 + random.nextDouble() * 0.3)), z = from + 2 + random.nextInt(3);
            final double x = c + Math.cos(angle) * reach, y = c + Math.sin(angle) * reach;
            b.line(log, c, c, from, x, y, z);
            b.cluster(leaves, x, y, z + 1, 2.3 + random.nextDouble() * 0.8, 2.0, random);
        }
        return b.build();
    }

    /**
     * A slender birch with a narrow, tall crown.
     */
    private static WPObject birch(int variant, Random random) {
        final int height = 9 + random.nextInt(6);
        final Builder b = new Builder("Birch " + (variant + 1));
        b.trunk(log("birch"), height, 1);
        b.blob(leaves("birch"), 0.5, 0.5, height - 2, 2.3 + random.nextDouble(), 3.6, 0.92, random);
        b.column(leaves("birch"), height, height + 1);
        return b.build();
    }

    /**
     * A cherry tree: a short trunk which forks into a few spreading limbs carrying a broad, rounded pink crown whose rim
     * hangs down a little.
     */
    private static WPObject cherry(int variant, Random random) {
        final int height = 5 + random.nextInt(3);
        final Builder b = new Builder("Cherry tree " + (variant + 1));
        final Material log = log("cherry"), leaves = leaves("cherry");
        b.trunk(log, height, 1);
        final int forks = 3 + random.nextInt(2);
        for (int i = 0; i < forks; i++) {
            final double angle = (i + random.nextDouble() * 0.6) * Math.PI * 2 / forks, reach = 2.2 + random.nextDouble() * 2;
            final double x = 0.5 + Math.cos(angle) * reach, y = 0.5 + Math.sin(angle) * reach;
            final int z = height + 1 + random.nextInt(2);
            b.line(log, 0.5, 0.5, height - 1, x, y, z);
            b.cluster(leaves, x, y, z + 1, 2.8 + random.nextDouble() * 1.0, 1.9, random);
        }
        b.blob(leaves, 0.5, 0.5, height + 2, 2.8, 1.8, 0.9, random);
        b.hang(leaves, 0.35, 2, random);
        return b.build();
    }

    /**
     * A dead tree: a bare trunk with a few crooked branches.
     */
    private static WPObject dead(int variant, Random random) {
        final int height = 6 + random.nextInt(7);
        final Builder b = new Builder("Dead tree " + (variant + 1));
        final Material log = random.nextBoolean() ? log("oak") : log("spruce");
        b.trunk(log, height, 1);
        final int branches = 2 + random.nextInt(3);
        for (int i = 0; i < branches; i++) {
            final double angle = random.nextDouble() * Math.PI * 2, reach = 2 + random.nextDouble() * 2.5;
            final int z = height / 2 + random.nextInt(Math.max(1, height / 2));
            b.line(log, 0.5, 0.5, z, 0.5 + Math.cos(angle) * reach, 0.5 + Math.sin(angle) * reach, z + 1 + random.nextInt(2));
        }
        return b.build();
    }

    /**
     * A palm tree: a slender, leaning, curving trunk with a crown of long drooping fronds.
     */
    private static WPObject palm(int variant, Random random) {
        final int height = 7 + random.nextInt(6);
        final Builder b = new Builder("Palm tree " + (variant + 1));
        final Material log = log("jungle"), leaves = leaves("jungle");
        // The trunk leans more and more towards the top
        final double angle = random.nextDouble() * Math.PI * 2, lean = 2 + random.nextDouble() * 2.5;
        double topX = 0, topY = 0;
        for (int z = 0; z < height; z++) {
            final double t = (double) z / height;
            topX = Math.cos(angle) * lean * t * t;
            topY = Math.sin(angle) * lean * t * t;
            b.set((int) Math.floor(topX + 0.5), (int) Math.floor(topY + 0.5), z, log);
        }
        final int cx = (int) Math.floor(topX + 0.5), cy = (int) Math.floor(topY + 0.5), cz = height;
        b.disc(leaves, cx + 0.5, cy + 0.5, cz, 1.5, 1.0, random);
        b.set(cx, cy, cz + 1, leaves);
        // Fronds: arcs which rise a little and then droop, two blocks wide near the crown
        final int fronds = 7 + random.nextInt(3);
        for (int f = 0; f < fronds; f++) {
            final double a = f * Math.PI * 2 / fronds + random.nextDouble() * 0.4;
            final int length = 5 + random.nextInt(2);
            for (int i = 1; i <= length; i++) {
                final double droop = (i <= 2) ? 0 : -0.3 * (i - 2) * (i - 2);
                final int x = cx + (int) Math.round(Math.cos(a) * i), y = cy + (int) Math.round(Math.sin(a) * i), z = cz + (int) Math.round(droop);
                b.set(x, y, z, leaves);
                if ((i >= 2) && (i <= length - 2)) {
                    b.set(cx + (int) Math.round(Math.cos(a + 0.25) * i), cy + (int) Math.round(Math.sin(a + 0.25) * i), z, leaves);
                }
                if (i == length) {
                    b.set(x, y, z - 1, leaves);
                }
            }
        }
        return b.build();
    }

    /**
     * A low, rounded bush.
     */
    private static WPObject bush(int variant, Random random) {
        final Builder b = new Builder("Bush " + (variant + 1));
        final Material leaves = (variant % 3 == 0) ? leaves("azalea") : leaves("oak");
        b.blob(leaves, 0.5, 0.5, 1, 1.6 + random.nextDouble() * 1.2, 1.4, 0.92, random);
        b.set(0, 0, 0, log("oak"));
        return b.build();
    }

    /**
     * A savanna acacia: a short trunk which forks into slanting limbs, each carrying a flat, two-layered umbrella of
     * leaves.
     */
    private static WPObject acacia(int variant, Random random) {
        final int height = 6 + random.nextInt(4);
        final Builder b = new Builder("Acacia " + (variant + 1));
        final Material log = log("acacia"), leaves = leaves("acacia");
        b.trunk(log, height - 2, 1);
        final int forks = 2 + random.nextInt(2);
        for (int i = 0; i < forks; i++) {
            final double angle = i * Math.PI * 2 / forks + random.nextDouble() * 0.8, reach = 2 + random.nextDouble() * 2;
            final double x = 0.5 + Math.cos(angle) * reach, y = 0.5 + Math.sin(angle) * reach;
            final int z = height + random.nextInt(2);
            b.line(log, 0.5, 0.5, height - 3, x, y, z);
            final double radius = 3.0 + random.nextDouble();
            b.disc(leaves, x, y, z + 1, radius, 0.95, random);
            b.disc(leaves, x, y, z + 2, radius - 1.3, 0.95, random);
        }
        return b.build();
    }

    /**
     * A weeping willow: a broad, round crown with curtains of leaves hanging down from all around its rim.
     */
    private static WPObject willow(int variant, Random random) {
        final int height = 7 + random.nextInt(4);
        final Builder b = new Builder("Willow " + (variant + 1));
        final Material log = log("oak"), leaves = leaves("mangrove");
        b.trunk(log, height, 1);
        for (int i = 0; i < 4; i++) {
            b.line(log, 0.5, 0.5, height - 3, 0.5 + Math.cos(i * Math.PI / 2 + 0.4) * 2.5, 0.5 + Math.sin(i * Math.PI / 2 + 0.4) * 2.5, height);
        }
        b.cluster(leaves, 0.5, 0.5, height + 1, 4.3 + random.nextDouble() * 0.6, 2.4, random);
        b.hang(leaves, 0.65, height - 2, random);
        return b.build();
    }

    /**
     * A low, round azalea bush with flowering patches.
     */
    private static WPObject azalea(int variant, Random random) {
        final int height = 2 + random.nextInt(2);
        final Builder b = new Builder("Azalea " + (variant + 1));
        final Material log = log("oak"), leaves = leaves("azalea"), flowering = leaves("flowering_azalea");
        b.trunk(log, height, 1);
        b.blob(leaves, 0.5, 0.5, height + 1, 2.4 + random.nextDouble() * 0.6, 1.7, 0.9, random);
        for (int i = 0; i < 10; i++) {
            final double angle = random.nextDouble() * Math.PI * 2, reach = random.nextDouble() * 2.5;
            b.recolour(leaves, flowering, (int) Math.floor(0.5 + Math.cos(angle) * reach), (int) Math.floor(0.5 + Math.sin(angle) * reach), height + random.nextInt(3));
        }
        return b.build();
    }

    /**
     * A pale oak: a thick trunk with short limbs under a broad, heavy, dark grey-green crown.
     */
    private static WPObject paleOak(int variant, Random random) {
        final int height = 7 + random.nextInt(5);
        final Builder b = new Builder("Pale oak " + (variant + 1));
        final Material log = log("pale_oak"), leaves = leaves("pale_oak");
        b.trunk(log, height, 2);
        b.cluster(leaves, 1, 1, height + 1, 4.0 + random.nextDouble(), 2.4, random);
        final int limbs = 4 + random.nextInt(2);
        for (int i = 0; i < limbs; i++) {
            final double angle = (i + random.nextDouble() * 0.6) * Math.PI * 2 / limbs, reach = 3 + random.nextDouble() * 1.8;
            final int z = height - 1 + random.nextInt(2);
            final double x = 1 + Math.cos(angle) * reach, y = 1 + Math.sin(angle) * reach;
            b.line(log, 1, 1, z - 2, x, y, z);
            b.cluster(leaves, x, y, z + 1, 2.6 + random.nextDouble() * 0.6, 1.9, random);
        }
        b.hang(leaves, 0.15, 2, random);
        return b.build();
    }

    /**
     * A giant redwood: a thick, very tall trunk with a full, narrow crown in its upper half.
     */
    private static WPObject redwood(int variant, Random random) {
        final int height = 30 + random.nextInt(14);
        final Builder b = new Builder("Giant redwood " + (variant + 1));
        final Material log = log("spruce"), leaves = leaves("spruce");
        b.trunk(log, height, 2);
        final int firstTier = (int) (height * (0.45 + random.nextDouble() * 0.1));
        for (int z = firstTier; z < height; z++) {
            final double t = (double) (z - firstTier) / (height - firstTier);
            final double radius = ((z - firstTier) % 3 == 0) ? (2.4 + (1 - t) * 2.8) : (1.9 + (1 - t) * 1.8);
            b.disc(leaves, 1, 1, z, radius, 0.92, random);
            if (((z - firstTier) % 3 == 0) && (t < 0.8)) {
                b.branch(log, z, random.nextInt(4), (int) Math.round(radius) - 1, 1, 1);
            }
        }
        b.blob(leaves, 1, 1, height, 1.8, 1.8, 0.95, random);
        return b.build();
    }

    private static Material log(String wood) {
        return Material.get("minecraft:" + wood + "_log", "axis", "y");
    }

    private static Material leaves(String wood) {
        return Material.get("minecraft:" + wood + "_leaves", "distance", "1", "persistent", "true", "waterlogged", "false");
    }

    private static final int VARIANTS = 8;
    private static final String DESCRIPTION_SUFFIX = " (WorldPainter tree pack)";

    /**
     * The version of the trees: worlds keep the trees they were painted with, so those of an earlier version are made
     * again when a world is loaded (see {@link #upgrade(Bo2Layer)}). Version 2 made the crowns fuller and stopped them
     * from being cut off by the edges of the object.
     */
    static final int VERSION = 2;
    static final AttributeKey<Integer> ATTRIBUTE_VERSION = new AttributeKey<>("org.pepsoft.worldpainter.treePack.version", 1);

    /**
     * Builds a tree around its trunk, at the origin of the horizontal coordinates. The object is made as large as what
     * was built, so that crowns are never cut off, and centred on the trunk, so that it turns around it.
     */
    private static final class Builder {
        Builder(String name) {
            this.name = name;
        }

        /**
         * Set a block, relative to the trunk; wood is not replaced by leaves.
         */
        void set(int x, int y, int z, Material material) {
            if (z < 0) {
                return;
            }
            final Long key = key(x, y, z);
            final Material existing = blocks.get(key);
            if ((existing != null) && existing.name.endsWith("_log") && material.name.endsWith("_leaves")) {
                return;
            }
            blocks.put(key, material);
        }

        Material get(int x, int y, int z) {
            return blocks.get(key(x, y, z));
        }

        void trunk(Material log, int height, int thickness) {
            for (int z = 0; z < height; z++) {
                for (int dx = 0; dx < thickness; dx++) {
                    for (int dy = 0; dy < thickness; dy++) {
                        set(dx, dy, z, log);
                    }
                }
            }
        }

        void column(Material material, int from, int to) {
            for (int z = from; z <= to; z++) {
                set(0, 0, z, material);
            }
        }

        /**
         * A horizontal branch from the trunk in one of the four directions.
         */
        void branch(Material log, int z, int direction, int length) {
            branch(log, z, direction, length, 0, 0);
        }

        /**
         * A horizontal branch from a corner of a thick trunk in one of the four directions.
         */
        void branch(Material log, int z, int direction, int length, int offsetX, int offsetY) {
            final int dx = (direction == 0) ? 1 : ((direction == 2) ? -1 : 0), dy = (direction == 1) ? 1 : ((direction == 3) ? -1 : 0);
            final int startX = (dx > 0) ? offsetX : 0, startY = (dy > 0) ? offsetY : 0;
            final Material horizontal = log.withProperty("axis", (dx != 0) ? "x" : "z");
            for (int i = 1; i <= length; i++) {
                set(startX + dx * i, startY + dy * i, z, horizontal);
            }
        }

        /**
         * A line of wood between two points (relative to the trunk), for slanting limbs.
         */
        void line(Material log, double x1, double y1, int z1, double x2, double y2, int z2) {
            final int steps = (int) Math.ceil(Math.max(Math.hypot(x2 - x1, y2 - y1), Math.abs(z2 - z1))) * 2 + 1;
            for (int i = 0; i <= steps; i++) {
                final double t = (double) i / steps;
                set((int) Math.floor(x1 + (x2 - x1) * t), (int) Math.floor(y1 + (y2 - y1) * t), (int) Math.round(z1 + (z2 - z1) * t), log);
            }
        }

        /**
         * A flat disc of leaves, with the blocks towards its rim filled in less and less.
         */
        void disc(Material leaves, double cx, double cy, int z, double radius, double fill, Random random) {
            final int r = (int) Math.ceil(radius);
            for (int x = (int) Math.floor(cx) - r; x <= (int) Math.floor(cx) + r; x++) {
                for (int y = (int) Math.floor(cy) - r; y <= (int) Math.floor(cy) + r; y++) {
                    final double d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
                    if ((d <= radius) && ((d < radius * 0.6) || (random.nextDouble() < fill * (1.25 - d / (radius + 1))))) {
                        set(x, y, z, leaves);
                    }
                }
            }
        }

        /**
         * A partial ring of leaves, for the drooping tips of a tier.
         */
        void ring(Material leaves, double cx, double cy, int z, double inner, double outer, double fill, Random random) {
            final int r = (int) Math.ceil(outer);
            for (int x = (int) Math.floor(cx) - r; x <= (int) Math.floor(cx) + r; x++) {
                for (int y = (int) Math.floor(cy) - r; y <= (int) Math.floor(cy) + r; y++) {
                    final double d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
                    if ((d >= inner) && (d <= outer) && (random.nextDouble() < fill)) {
                        set(x, y, z, leaves);
                    }
                }
            }
        }

        /**
         * An ellipsoid of leaves, with its outer layer partly filled in so that it looks lumpy.
         */
        void blob(Material leaves, double cx, double cy, int cz, double radius, double halfHeight, double fill, Random random) {
            final int r = (int) Math.ceil(radius), h = (int) Math.ceil(halfHeight);
            for (int z = cz - h; z <= cz + h; z++) {
                for (int x = (int) Math.floor(cx) - r; x <= (int) Math.floor(cx) + r; x++) {
                    for (int y = (int) Math.floor(cy) - r; y <= (int) Math.floor(cy) + r; y++) {
                        final double dx = (x + 0.5 - cx) / radius, dy = (y + 0.5 - cy) / radius, dz = (z - cz) / halfHeight;
                        final double d = dx * dx + dy * dy + dz * dz;
                        if ((d <= 1) && ((d < 0.6) || (random.nextDouble() < fill))) {
                            set(x, y, z, leaves);
                        }
                    }
                }
            }
        }

        /**
         * A rounded clump of leaves made of a few overlapping ellipsoids, so that it is full but not a smooth ball.
         */
        void cluster(Material leaves, double cx, double cy, int cz, double radius, double halfHeight, Random random) {
            blob(leaves, cx, cy, cz, radius, halfHeight, 0.9, random);
            final int lumps = 3 + random.nextInt(3);
            for (int i = 0; i < lumps; i++) {
                final double angle = random.nextDouble() * Math.PI * 2, reach = radius * (0.45 + random.nextDouble() * 0.35);
                blob(leaves, cx + Math.cos(angle) * reach, cy + Math.sin(angle) * reach, cz + random.nextInt(3) - 1,
                        radius * (0.5 + random.nextDouble() * 0.2), halfHeight * 0.8, 0.85, random);
            }
        }

        /**
         * Strands of leaves hanging down from the underside of the leaves at the rim of the crown.
         *
         * @param chance How many of the leaves at the rim have a strand.
         * @param maxLength The longest a strand can be.
         */
        void hang(Material leaves, double chance, int maxLength, Random random) {
            final List<int[]> starts = new ArrayList<>();
            for (Map.Entry<Long, Material> entry: blocks.entrySet()) {
                if (! entry.getValue().name.endsWith("_leaves")) {
                    continue;
                }
                final int[] p = unkey(entry.getKey());
                // At the underside, and at the rim (an open side)
                if ((get(p[0], p[1], p[2] - 1) == null) && ((get(p[0] + 1, p[1], p[2]) == null) || (get(p[0] - 1, p[1], p[2]) == null) || (get(p[0], p[1] + 1, p[2]) == null) || (get(p[0], p[1] - 1, p[2]) == null))) {
                    starts.add(p);
                }
            }
            starts.sort((a, b) -> (a[0] != b[0]) ? Integer.compare(a[0], b[0]) : ((a[1] != b[1]) ? Integer.compare(a[1], b[1]) : Integer.compare(a[2], b[2])));
            for (int[] p: starts) {
                if (random.nextDouble() < chance) {
                    final int length = 1 + random.nextInt(Math.max(1, maxLength));
                    for (int z = p[2] - 1; (z >= p[2] - length) && (z >= 1); z--) {
                        set(p[0], p[1], z, leaves);
                    }
                }
            }
        }

        /**
         * Replace one kind of block with another at a location and its direct neighbours, for patches of colour.
         */
        void recolour(Material from, Material to, int x, int y, int z) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    final Long key = key(x + dx, y + dy, z);
                    if (from.equals(blocks.get(key))) {
                        blocks.put(key, to);
                    }
                }
            }
        }

        WPObject build() {
            int reach = 0, height = 1;
            for (Long key: blocks.keySet()) {
                final int[] p = unkey(key);
                reach = Math.max(reach, Math.max(Math.abs(p[0]), Math.abs(p[1])));
                height = Math.max(height, p[2] + 1);
            }
            final int width = 2 * reach + 1;
            final Material[] data = new Material[width * width * height];
            for (Map.Entry<Long, Material> entry: blocks.entrySet()) {
                final int[] p = unkey(entry.getKey());
                data[(p[0] + reach) + (p[1] + reach) * width + p[2] * width * width] = entry.getValue();
            }
            final GenericObject object = new GenericObject(name, width, width, height, data);
            object.setAttribute(WPObject.ATTRIBUTE_OFFSET, new Point3i(-reach, -reach, 0));
            object.setAttribute(WPObject.ATTRIBUTE_RANDOM_ROTATION, true);
            object.setAttribute(WPObject.ATTRIBUTE_LEAF_DECAY_MODE, WPObject.LEAF_DECAY_OFF);
            object.setAttribute(ATTRIBUTE_VERSION, VERSION);
            return object;
        }

        private static Long key(int x, int y, int z) {
            return (((long) (x + 512)) << 40) | (((long) (y + 512)) << 20) | (z + 512);
        }

        private static int[] unkey(long key) {
            return new int[] {(int) (key >> 40) - 512, (int) ((key >> 20) & 0xfffff) - 512, (int) (key & 0xfffff) - 512};
        }

        private final String name;
        private final Map<Long, Material> blocks = new HashMap<>();
    }
}
