package org.pepsoft.worldpainter.nodes;

import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.procedural.FractalNoise;
import org.pepsoft.worldpainter.procedural.NoiseLayer;

import java.util.List;

/**
 * Carves a network of meandering rivers into the terrain: a bed below the water, banks sloping up to the terrain and
 * water at the level of the smoothed valley floor. The rivers follow the contour lines of a warped noise field, so
 * they have a constant width in blocks and need no knowledge of the rest of the map, which keeps them seamless. They
 * peter out above a chosen height.
 */
public class RiverNode extends WaterNode {
    @Override
    public String getName() {
        return "Rivers";
    }

    @Override
    public List<Parameter<?>> getParameters() {
        return List.of(SPACING, WIDTH, DEPTH, BANKS, MEANDER, MAX_HEIGHT, BED, BIOME, VARIANT);
    }

    @Override
    Terrain getBedTerrain() {
        return get(BED);
    }

    @Override
    MinecraftBiome getBiome() {
        switch (get(BIOME)) {
            case RIVER:
                return MinecraftBiome.RIVER;
            case FROZEN_RIVER:
                return MinecraftBiome.FROZEN_RIVER;
            default:
                return null;
        }
    }

    @Override
    void prepare(CompileContext context, CompiledNode input) {
        spacing = getDouble(SPACING);
        halfWidth = getDouble(WIDTH) / 2;
        depth = getDouble(DEPTH);
        bankSlope = getDouble(BANKS);
        maxHeight = getDouble(MAX_HEIGHT);
        noise = new FractalNoise(seed(context, getInt(VARIANT)), NoiseLayer.Type.FBM, spacing, 3, 0.45, getDouble(MEANDER) * spacing * 0.4, 0);
        // The water follows the terrain smoothed over a few river widths, so that it is level across the river
        final BlurNode blur = new BlurNode();
        blur.set(BlurNode.RADIUS, Math.max(24, halfWidth * 6));
        valley = blur.compile(context, new CompiledNode[] {input});
        minHeight = context.getMinHeight();
        verticalScale = context.getVerticalScale();
    }

    @Override
    void carve(Evaluation evaluation, Region region, float[] heights, float[] levels, float[] masks) {
        final float[] valleyFloor = evaluation.evaluate(valley, region);
        final int width = region.getWidth(), height = region.getHeight();
        // The gradient of the noise is tiny (about one over the spacing); below this it is too flat to tell where the
        // contour line is
        final double minimumGradient = 0.02 / spacing;
        for (int j = 0; j < height; j++) {
            if ((j & 0xf) == 0) {
                evaluation.checkCancelled();
            }
            final double y = region.y(j);
            for (int i = 0; i < width; i++) {
                final int index = i + j * width;
                final double x = region.x(i);
                final double level = Math.floor(minHeight + valleyFloor[index] * verticalScale - BANK_DROP + 0.001);
                final double strength = 1 - HeightMaskNode.rise(level, maxHeight - FADE_DISTANCE, maxHeight);
                if (strength <= 0) {
                    continue;
                }
                final double value = noise.sample(x, y);
                final double gradientX = (noise.sample(x + DELTA, y) - noise.sample(x - DELTA, y)) / (2 * DELTA);
                final double gradientY = (noise.sample(x, y + DELTA) - noise.sample(x, y - DELTA)) / (2 * DELTA);
                final double gradient = Math.sqrt(gradientX * gradientX + gradientY * gradientY);
                if (gradient < minimumGradient) {
                    continue;
                }
                // Distance in blocks to the contour line at one half, which is the centre of the river
                final double distance = Math.abs(value - 0.5) / gradient;
                final double riverHalfWidth = halfWidth * strength;
                final double terrain = heights[index];
                final double carved;
                if (distance < riverHalfWidth) {
                    final double t = distance / riverHalfWidth;
                    carved = Math.min(terrain, level - Math.max(1, depth * strength) * (1 - t * t) - 0.5);
                } else {
                    carved = Math.min(terrain, level + bankSlope * (distance - riverHalfWidth));
                }
                heights[index] = (float) carved;
                if ((distance < riverHalfWidth + 1) && (carved < level)) {
                    levels[index] = (float) level;
                }
                masks[index] = (float) (strength * (1 - HeightMaskNode.rise(distance, riverHalfWidth, riverHalfWidth + 2)));
            }
        }
    }

    public static final NumberParameter SPACING = new NumberParameter("spacing", "Spacing", "The typical distance between rivers", 1200, 200, 8000, 1, true, "blocks");
    public static final NumberParameter WIDTH = new NumberParameter("width", "Width", "The width of the water", 8, 2, 64, 1, false, "blocks");
    public static final NumberParameter DEPTH = new NumberParameter("depth", "Depth", "The depth of the water in the middle", 4, 1, 32, 1, false, "blocks");
    public static final NumberParameter BANKS = new NumberParameter("banks", "Bank slope", "How steeply the banks rise from the water (blocks up per block across)", 0.6, 0.1, 3, 0.05, true, null);
    public static final NumberParameter MEANDER = new NumberParameter("meander", "Meander", "How much the rivers wind", 0.5, 0, 1, 0.01, false, null);
    public static final NumberParameter MAX_HEIGHT = new NumberParameter("maxHeight", "Up to height", "The rivers peter out towards this height", 160, -2048, 4096, 1, false, "blocks");
    public static final ChoiceParameter<Terrain> BED = new ChoiceParameter<>("bed", "Bed", "The terrain type under the water", Terrain.GRAVEL, getBedChoices());
    public static final ChoiceParameter<RiverBiome> BIOME = new ChoiceParameter<>("biome", "Biome", "The biome to paint on the river", RiverBiome.RIVER, RiverBiome.values());

    /**
     * How far below the smoothed valley floor the water is.
     */
    private static final double BANK_DROP = 1.5;
    private static final double FADE_DISTANCE = 24, DELTA = 1;

    private transient FractalNoise noise;
    private transient CompiledNode valley;
    private transient double spacing, halfWidth, depth, bankSlope, maxHeight, minHeight, verticalScale;

    private static final long serialVersionUID = 1L;

    public enum RiverBiome {
        RIVER("River"), FROZEN_RIVER("Frozen river"), NONE("None");

        RiverBiome(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }

        private final String displayName;
    }
}
