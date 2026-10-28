package org.pepsoft.worldpainter.layers.bo2;

import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.layers.Bo2Layer;
import org.pepsoft.worldpainter.objects.GenericObject;
import org.pepsoft.worldpainter.objects.WPObject;

import javax.vecmath.Point3i;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
        DEAD("Dead trees", 0x6b5a48, 5, 4),
        BUSH("Bushes", 0x4d7a35, 2, 2);

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
        final List<WPObject> objects = new ArrayList<>();
        final Random random = new Random(kind.ordinal() * 7919L + 17);
        for (int i = 0; i < VARIANTS; i++) {
            objects.add(create(kind, i, random));
        }
        final Bo2Layer layer = new Bo2Layer(new Bo2ObjectTube(kind.displayName, objects), kind.displayName + " (WorldPainter tree pack)", new Color(kind.colour));
        // Spread the trees out on a grid with some random displacement, so that big crowns do not overlap too much
        layer.setGridX(kind.grid);
        layer.setGridY(kind.grid);
        layer.setRandomDisplacement(Math.max(1, kind.grid - 1));
        layer.setDensity(kind.density);
        return layer;
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
            case DEAD:
                return dead(variant, random);
            case BUSH:
                return bush(variant, random);
            default:
                throw new InternalError();
        }
    }

    /**
     * A tall spruce with a long bare trunk and sparse tiers of short branches, narrowing to a spike.
     */
    private static WPObject tallSpruce(int variant, Random random) {
        final int height = 22 + random.nextInt(13);
        final Builder b = new Builder("Tall spruce " + (variant + 1), 9, height + 3);
        final Material log = log("spruce"), leaves = leaves("spruce");
        b.trunk(log, height, (height > 30) ? 2 : 1);
        final int firstTier = (int) (height * (0.35 + random.nextDouble() * 0.15));
        for (int z = firstTier; z < height; z += 2 + random.nextInt(2)) {
            final double t = (double) (z - firstTier) / (height - firstTier);
            final double radius = 1 + (1 - t) * (2.2 + random.nextDouble() * 1.2);
            // A ragged tier: a flat disc with gaps
            b.disc(leaves, z, radius, 0.75, random);
            if ((t < 0.7) && random.nextBoolean()) {
                b.branch(log, z, random.nextInt(4), (int) Math.round(radius));
            }
        }
        b.column(leaves, height, height + 2);
        return b.build();
    }

    /**
     * A dense conical fir.
     */
    private static WPObject fir(int variant, Random random) {
        final int height = 12 + random.nextInt(9);
        final Builder b = new Builder("Fir " + (variant + 1), 9, height + 2);
        final Material log = log("spruce"), leaves = leaves("spruce");
        b.trunk(log, height, 1);
        final int bottom = 2 + random.nextInt(2);
        for (int z = bottom; z < height; z++) {
            final double t = (double) (z - bottom) / (height - bottom);
            final double radius = 0.6 + (1 - t) * 3.6 + (((z - bottom) % 3 == 0) ? 0.6 : 0);
            b.disc(leaves, z, radius, 0.95, random);
        }
        b.column(leaves, height, height + 1);
        return b.build();
    }

    /**
     * A rainforest giant: a tall, thick trunk with a few branches at the top carrying a wide, flat, uneven canopy.
     */
    private static WPObject rainforest(int variant, Random random) {
        final int height = 18 + random.nextInt(11);
        final int radius = 6 + random.nextInt(4);
        final Builder b = new Builder("Rainforest giant " + (variant + 1), 2 * radius + 5, height + 5);
        final Material log = log("jungle"), leaves = leaves("jungle");
        b.trunk(log, height, 2);
        // Buttress roots
        for (int d = 0; d < 4; d++) {
            b.branch(log, 0, d, 2);
        }
        for (int i = 0; i < 4; i++) {
            b.branch(log, height - 2 - random.nextInt(4), random.nextInt(4), radius / 2 + random.nextInt(2));
        }
        // The canopy: a flattened, lumpy ellipsoid
        b.blob(leaves, 0.5, 0.5, height + 1, radius, 2.6, 0.82, random);
        b.blob(leaves, 0.5 + random.nextInt(5) - 2, 0.5 + random.nextInt(5) - 2, height + 3, radius * 0.6, 1.8, 0.8, random);
        return b.build();
    }

    /**
     * A big oak with a few thick branches, each ending in a rounded clump of leaves.
     */
    private static WPObject bigOak(int variant, Random random) {
        final int height = 9 + random.nextInt(6);
        final Builder b = new Builder("Big oak " + (variant + 1), 17, height + 7);
        final Material log = log("oak"), leaves = leaves("oak");
        b.trunk(log, height, 1);
        b.blob(leaves, 0.5, 0.5, height + 1, 3.5, 2.8, 0.9, random);
        final int clumps = 3 + random.nextInt(3);
        for (int i = 0; i < clumps; i++) {
            final double angle = random.nextDouble() * Math.PI * 2, reach = 3 + random.nextDouble() * 2.5;
            final int z = height - 3 + random.nextInt(3);
            final double x = 0.5 + Math.cos(angle) * reach, y = 0.5 + Math.sin(angle) * reach;
            b.line(log, 0.5, 0.5, z - 2, x, y, z);
            b.blob(leaves, x, y, z + 1, 2.4 + random.nextDouble(), 2, 0.9, random);
        }
        return b.build();
    }

    /**
     * A slender birch with a narrow crown.
     */
    private static WPObject birch(int variant, Random random) {
        final int height = 9 + random.nextInt(6);
        final Builder b = new Builder("Birch " + (variant + 1), 9, height + 3);
        b.trunk(log("birch"), height, 1);
        b.blob(leaves("birch"), 0.5, 0.5, height - 2, 2.3 + random.nextDouble(), 3.5, 0.9, random);
        return b.build();
    }

    /**
     * A dead tree: a bare trunk with a few crooked branches.
     */
    private static WPObject dead(int variant, Random random) {
        final int height = 6 + random.nextInt(7);
        final Builder b = new Builder("Dead tree " + (variant + 1), 11, height + 3);
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
     * A low, rounded bush.
     */
    private static WPObject bush(int variant, Random random) {
        final Builder b = new Builder("Bush " + (variant + 1), 7, 4);
        final Material leaves = (variant % 3 == 0) ? leaves("azalea") : leaves("oak");
        b.blob(leaves, 0.5, 0.5, 1, 1.4 + random.nextDouble() * 1.2, 1.2, 0.9, random);
        b.set(0, 0, 0, log("oak"));
        return b.build();
    }

    private static Material log(String wood) {
        return Material.get("minecraft:" + wood + "_log", "axis", "y");
    }

    private static Material leaves(String wood) {
        return Material.get("minecraft:" + wood + "_leaves", "distance", "1", "persistent", "true", "waterlogged", "false");
    }

    private static final int VARIANTS = 8;

    /**
     * Builds an object in a box centred on the trunk, at the origin of the horizontal coordinates.
     */
    private static final class Builder {
        Builder(String name, int width, int height) {
            this.name = name;
            this.width = width | 1;
            this.height = height;
            centre = this.width / 2;
            data = new Material[this.width * this.width * height];
        }

        /**
         * Set a block, relative to the trunk; blocks outside the box are ignored, and wood is not replaced by leaves.
         */
        void set(int x, int y, int z, Material material) {
            final int bx = x + centre, by = y + centre;
            if ((bx < 0) || (by < 0) || (z < 0) || (bx >= width) || (by >= width) || (z >= height)) {
                return;
            }
            final int i = bx + by * width + z * width * width;
            if ((data[i] != null) && data[i].name.endsWith("_log") && material.name.endsWith("_leaves")) {
                return;
            }
            data[i] = material;
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
            final int dx = (direction == 0) ? 1 : ((direction == 2) ? -1 : 0), dy = (direction == 1) ? 1 : ((direction == 3) ? -1 : 0);
            final Material horizontal = log.withProperty("axis", (dx != 0) ? "x" : "z");
            for (int i = 1; i <= length; i++) {
                set(dx * i, dy * i, z, horizontal);
            }
        }

        /**
         * A line of wood between two points (relative to the trunk), for slanting branches.
         */
        void line(Material log, double x1, double y1, int z1, double x2, double y2, int z2) {
            final int steps = (int) Math.ceil(Math.max(Math.hypot(x2 - x1, y2 - y1), Math.abs(z2 - z1))) * 2 + 1;
            for (int i = 0; i <= steps; i++) {
                final double t = (double) i / steps;
                set((int) Math.floor(x1 + (x2 - x1) * t), (int) Math.floor(y1 + (y2 - y1) * t), (int) Math.round(z1 + (z2 - z1) * t), log);
            }
        }

        /**
         * A flat disc of leaves around the trunk, with a fraction of the blocks filled in.
         */
        void disc(Material leaves, int z, double radius, double fill, Random random) {
            final int r = (int) Math.ceil(radius);
            for (int x = -r; x <= r; x++) {
                for (int y = -r; y <= r; y++) {
                    final double d = Math.hypot(x, y);
                    if ((d <= radius) && ((d < 1.5) || (random.nextDouble() < fill * (1.15 - d / (radius + 1))))) {
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

        WPObject build() {
            final GenericObject object = new GenericObject(name, width, width, height, data);
            object.setAttribute(WPObject.ATTRIBUTE_OFFSET, new Point3i(-centre, -centre, 0));
            object.setAttribute(WPObject.ATTRIBUTE_RANDOM_ROTATION, true);
            object.setAttribute(WPObject.ATTRIBUTE_LEAF_DECAY_MODE, WPObject.LEAF_DECAY_OFF);
            return object;
        }

        private final String name;
        private final int width, height, centre;
        private final Material[] data;
    }
}
