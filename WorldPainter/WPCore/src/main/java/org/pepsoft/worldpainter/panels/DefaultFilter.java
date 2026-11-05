/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.pepsoft.worldpainter.panels;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.layers.Layer;
import org.pepsoft.worldpainter.operations.Filter;
import org.pepsoft.worldpainter.selection.SelectionBlock;
import org.pepsoft.worldpainter.selection.SelectionChunk;

/**
 *
 * @author pepijn
 */
public final class DefaultFilter implements Filter {
    public DefaultFilter(Dimension dimension, boolean inSelection, boolean outsideSelection, int aboveLevel, int belowLevel, boolean feather, boolean onlyOn, Object onlyOnItem, boolean exceptOn, Object exceptOnItem, int aboveDegrees, boolean slopeIsAbove) {
        this.dimension = dimension;
        this.inSelection = inSelection;
        this.outsideSelection = outsideSelection;
        if (inSelection && outsideSelection) {
            throw new IllegalArgumentException("inSelection and outsideSelection are mutually exclusive");
        }
        this.aboveLevel = aboveLevel;
        this.belowLevel = belowLevel;
        if (aboveLevel != Integer.MIN_VALUE) {
            checkLevel = true;
            if (belowLevel != Integer.MIN_VALUE) {
                // Above and below are checked
                if (belowLevel >= aboveLevel) {
                    levelType = LevelType.BETWEEN;
                } else {
                    levelType = LevelType.OUTSIDE;
                }
            } else {
                // Only above checked
                levelType = LevelType.ABOVE;
            }
        } else if (belowLevel != Integer.MIN_VALUE) {
            // Only below checked
            checkLevel = true;
            levelType = LevelType.BELOW;
        } else {
            // Neither checked
            checkLevel = false;
            levelType = null;
        }
        this.feather = feather;
        this.onlyOn = onlyOn;
        onlyOnFilter = (onlyOnItem != null) ? OnlyOnTerrainOrLayerFilter.create(dimension, onlyOnItem) : null;
        this.exceptOn = exceptOn;
        exceptOnFilter = (exceptOnItem != null) ? ExceptOnTerrainOrLayerFilter.create(dimension, exceptOnItem) : null;
        this.degrees = aboveDegrees;
        checkSlope = aboveDegrees >= 0;
        if (checkSlope) {
            this.slope = (float) Math.tan(aboveDegrees / (180 / Math.PI));
    //        System.out.println(degrees + "° -> " + slope);
        } else {
            slope = 0.0f;
        }
        this.slopeIsAbove = slopeIsAbove;
    }

    /**
     * Also limit the brush by the curvature of the land: to hollows (valleys, dips, the foot of slopes: the land around
     * is higher) or bumps (ridges, peaks, the brow of slopes: the land around is lower).
     *
     * @param mode {@link #CURVATURE_NONE}, {@link #CURVATURE_HOLLOWS} or {@link #CURVATURE_BUMPS}.
     * @param depth How much higher (for hollows) or lower (for bumps) the land around must be on average, in blocks.
     * @return This filter.
     */
    public DefaultFilter withCurvature(int mode, float depth) {
        curvatureMode = mode;
        curvatureDepth = depth;
        return this;
    }

    public int getCurvatureMode() {
        return curvatureMode;
    }

    public float getCurvatureDepth() {
        return curvatureDepth;
    }

    /**
     * How much higher the land around a location is than the location itself, on average, in blocks: positive in a
     * hollow, negative on a bump. The land is sampled at {@link #CURVATURE_RADIUS} blocks in eight directions.
     */
    public static float curvatureAt(Dimension dimension, int x, int y) {
        final float height = dimension.getHeightAt(x, y);
        if (height == -Float.MAX_VALUE) {
            return 0.0f;
        }
        float sum = 0.0f;
        int count = 0;
        for (int i = 0; i < 8; i++) {
            final float around = dimension.getHeightAt(x + CURVATURE_DX[i], y + CURVATURE_DY[i]);
            if (around != -Float.MAX_VALUE) {
                sum += around;
                count++;
            }
        }
        return (count > 0) ? (sum / count - height) : 0.0f;
    }

    public int getAboveLevel() {
        return aboveLevel;
    }

    public int getBelowLevel() {
        return belowLevel;
    }

    public Dimension getDimension() {
        return dimension;
    }

    public void setDimension(Dimension dimension) {
        this.dimension = dimension;
        setDimension(onlyOnFilter, dimension);
        setDimension(exceptOnFilter, dimension);
    }

    private static void setDimension(Filter filter, Dimension dimension) {
        if (filter instanceof TerrainOrLayerFilter) {
            ((TerrainOrLayerFilter) filter).dimension = dimension;
        } else if (filter instanceof AnyOfFilter) {
            ((AnyOfFilter) filter).getFilters().forEach(subFilter -> setDimension(subFilter, dimension));
        } else if (filter instanceof CombinedFilter) {
            ((CombinedFilter) filter).getFilters().forEach(subFilter -> setDimension(subFilter, dimension));
        }
    }

    public boolean isInSelection() {
        return inSelection;
    }

    /**
     * This gets the "only on" layer, but only if there is one layer selected.
     */
    public Layer getOnlyOnLayer() {
        return (onlyOnFilter instanceof OnlyOnTerrainOrLayerFilter) ? ((OnlyOnTerrainOrLayerFilter) onlyOnFilter).getLayer() : null;
    }

    /**
     * This gets the "except on" layer, but only if there is one layer selected.
     */
    public Layer getExceptOnLayer() {
        return (exceptOnFilter instanceof ExceptOnTerrainOrLayerFilter) ? ((ExceptOnTerrainOrLayerFilter) exceptOnFilter).getLayer() : null;
    }

    public static Builder buildForDimension(Dimension dimension) {
        return new Builder(dimension);
    }

    // Filter
    
    @Override
    public float modifyStrength(int x, int y, float strength) {
        if (strength > 0.0f) {
            if (inSelection && (! dimension.getBitLayerValueAt(SelectionChunk.INSTANCE, x, y)) && (! dimension.getBitLayerValueAt(SelectionBlock.INSTANCE, x, y))) {
                return 0.0f;
            } else if (outsideSelection && (dimension.getBitLayerValueAt(SelectionChunk.INSTANCE, x, y) || dimension.getBitLayerValueAt(SelectionBlock.INSTANCE, x, y))) {
                return 0.0f;
            }
            if (exceptOn && (exceptOnFilter.modifyStrength(x, y, strength) <= 0.0f)) {
                return 0.0f;
            }
            if (onlyOn && (onlyOnFilter.modifyStrength(x, y, strength) <= 0.0f)) {
                return 0.0f;
            }
            if (checkLevel) {
                final int terrainLevel = dimension.getIntHeightAt(x, y);
                switch (levelType) {
                    case ABOVE:
                        if (terrainLevel < aboveLevel) {
                            return feather ? Math.max((1 - (aboveLevel - terrainLevel) / 4.0f) * strength, 0.0f) : 0.0f;
                        }
                        break;
                    case BELOW:
                        if (terrainLevel > belowLevel) {
                            return feather ? Math.max((1 - (terrainLevel - belowLevel) / 4.0f) * strength, 0.0f) : 0.0f;
                        }
                        break;
                    case BETWEEN:
                        if ((terrainLevel < aboveLevel) || (terrainLevel > belowLevel)) {
                            return feather ? Math.max(Math.min((1 - (aboveLevel - terrainLevel) / 4.0f), (1 - (terrainLevel - belowLevel) / 4.0f)) * strength, 0.0f) : 0.0f;
                        }
                        break;
                    case OUTSIDE:
                        if ((terrainLevel > belowLevel) && (terrainLevel < aboveLevel)) {
                            return feather ? Math.max(Math.max((1 - (terrainLevel - belowLevel) / 4.0f), (1 - (aboveLevel - terrainLevel) / 4.0f)) * strength, 0.0f) : 0.0f;
                        }
                        break;
                }
            }
            if (checkSlope) {
                float terrainSlope = dimension.getSlope(x, y);
                if (slopeIsAbove ? (terrainSlope < slope) : (terrainSlope > slope)) {
                    return 0.0f;
                }
            }
            if (curvatureMode != CURVATURE_NONE) {
                final float curvature = curvatureAt(dimension, x, y) * ((curvatureMode == CURVATURE_HOLLOWS) ? 1 : -1);
                if (curvature < curvatureDepth) {
                    // Fade out over half a block, so that the edge of the area is not ragged
                    return Math.max(0.0f, 1.0f - (curvatureDepth - curvature) * 2) * strength;
                }
            }
            return strength;
        } else {
            return 0.0f;
        }
    }

    // Object

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("DefaultFilter{");
        if (inSelection) {
            sb.append("in selection");
        } else if (outsideSelection) {
            sb.append("outside selection");
        }
        if (onlyOn) {
            if (inSelection || outsideSelection) {
                sb.append(" and ");
            }
            sb.append("only on ");
            sb.append(onlyOnFilter);
        }
        if (exceptOn) {
            if (inSelection || outsideSelection || onlyOn) {
                sb.append(' ');
            }
            sb.append("except on ");
            sb.append(exceptOnFilter);
        }
        if (checkLevel) {
            if (inSelection || outsideSelection || onlyOn || exceptOn) {
                sb.append(" and ");
            }
            sb.append("height ");
            switch (levelType) {
                case ABOVE:
                    sb.append("at or above ").append(aboveLevel);
                    break;
                case BELOW:
                    sb.append("at or below ").append(belowLevel);
                    break;
                case BETWEEN:
                    sb.append("between ").append(aboveLevel).append(" and ").append(belowLevel);
                    break;
                case OUTSIDE:
                    sb.append("not between ").append(aboveLevel).append(" and ").append(belowLevel);
                    break;
            }
        }
        if (curvatureMode != CURVATURE_NONE) {
            sb.append((curvatureMode == CURVATURE_HOLLOWS) ? "hollows " : "bumps ").append(curvatureDepth).append(' ');
        }
        if (checkSlope) {
            if (inSelection || outsideSelection || onlyOn || exceptOn || checkLevel) {
                sb.append(" and ");
            }
            sb.append("gradient ").append(slopeIsAbove ? "above " : "below ").append(slope);
        }
        sb.append('}');
        return sb.toString();
    }


    final boolean checkLevel, onlyOn, exceptOn, feather, checkSlope,
            slopeIsAbove, inSelection, outsideSelection;
    final LevelType levelType;
    final int aboveLevel, belowLevel, degrees;
    int curvatureMode = CURVATURE_NONE;
    float curvatureDepth = 1.0f;

    public static final int CURVATURE_NONE = 0, CURVATURE_HOLLOWS = 1, CURVATURE_BUMPS = 2;
    public static final int CURVATURE_RADIUS = 4;
    private static final int[] CURVATURE_DX = {CURVATURE_RADIUS, -CURVATURE_RADIUS, 0, 0, 3, 3, -3, -3};
    private static final int[] CURVATURE_DY = {0, 0, CURVATURE_RADIUS, -CURVATURE_RADIUS, 3, -3, 3, -3};
    final float slope;
    final Filter onlyOnFilter;
    final Filter exceptOnFilter;
    Dimension dimension;

    public enum LevelType {
        BETWEEN, OUTSIDE, ABOVE, BELOW
    }

    public enum Condition {
        EQUAL, LOWER_THAN_OR_EQUAL, HIGHER_THAN_OR_EQUAL
    }

    public static class LayerValue {
        public LayerValue(Layer layer) {
            this.layer = layer;
            value = -1;
            condition = null;
        }

        public LayerValue(Layer layer, int value) {
            this(layer, value, Condition.EQUAL);
        }

        public LayerValue(Layer layer, int value, Condition condition) {
            switch (layer.getDataSize()) {
                case BIT_PER_CHUNK:
                case BIT:
                    if ((value < -1) || (value > 1)) {
                        throw new IllegalArgumentException("value " + value);
                    }
                    break;
                case NIBBLE:
                    if ((value < -15) || (value > 15)) {
                        throw new IllegalArgumentException("value " + value);
                    }
                    break;
                case BYTE:
                    if ((value < -255) || (value > 255)) {
                        throw new IllegalArgumentException("value " + value);
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Data size " + layer.getDataSize() + " not supported");
            }
            this.layer = layer;
            this.value = value;
            this.condition = condition;
        }

        public final Layer layer;
        public final int value;
        public final Condition condition;
    }

    public static class Builder {
        public Builder(Dimension dimension) {
            this.dimension = dimension;
        }

        public Builder inSelection() {
            inSelection = true;
            return this;
        }

        public Builder outsideSelection() {
            outsideSelection = true;
            return this;
        }

        public Builder aboveLevel(int level) {
            aboveLevel = level;
            return this;
        }

        public Builder belowLevel(int level) {
            belowLevel = level;
            return this;
        }

        public Builder betweenLevels(int aboveLevel, int belowLevel) {
            this.aboveLevel = aboveLevel;
            this.belowLevel = belowLevel;
            return this;
        }

        public Builder feather() {
            feather = true;
            return this;
        }

        public Builder onlyOn(Object item) {
            onlyOn = item;
            return this;
        }

        public Builder exceptOn(Object item) {
            exceptOn = item;
            return this;
        }

        public Builder slopeIsAbove(int degrees) {
            aboveDegrees = degrees;
            slopeIsAbove = true;
            return this;
        }

        public Builder slopeIsBelow(int degrees) {
            aboveDegrees = degrees;
            slopeIsAbove = false;
            return this;
        }

        public DefaultFilter build() {
            return new DefaultFilter(dimension, inSelection, outsideSelection, aboveLevel, belowLevel, feather, onlyOn != null, onlyOn, exceptOn != null, exceptOn, aboveDegrees, slopeIsAbove);
        }

        private final Dimension dimension;
        private boolean inSelection, outsideSelection, feather, slopeIsAbove;
        private int aboveLevel, belowLevel, aboveDegrees;
        private Object onlyOn, exceptOn;
    }
}