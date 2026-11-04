package org.pepsoft.worldpainter.layers;

/**
 * Where the land is built in half blocks when it is exported: where its precise height is about half way between two
 * blocks, a slab of the block at the surface (stone, cobblestone, sandstone and the like) makes a half step, and snow
 * blocks become snow layers, by eighths of a block. Painted with the Half Blocks tool, which smooths the land as it goes.
 * See {@link org.pepsoft.worldpainter.exporting.SurfaceSmoother}.
 */
public class HalfBlocks extends Layer {
    private HalfBlocks() {
        super("org.pepsoft.HalfBlocks", "Half Blocks", "Build the land in half blocks: slabs on stone and the like, snow layers on snow blocks", DataSize.BIT, false, 95);
    }

    public static final HalfBlocks INSTANCE = new HalfBlocks();

    private static final long serialVersionUID = 1L;
}
