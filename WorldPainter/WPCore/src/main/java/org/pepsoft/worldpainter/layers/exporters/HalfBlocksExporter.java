package org.pepsoft.worldpainter.layers.exporters;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Platform;
import org.pepsoft.worldpainter.exporting.AbstractLayerExporter;
import org.pepsoft.worldpainter.exporting.Fixup;
import org.pepsoft.worldpainter.exporting.MinecraftWorld;
import org.pepsoft.worldpainter.exporting.SecondPassLayerExporter;
import org.pepsoft.worldpainter.exporting.SurfaceSmoother;
import org.pepsoft.worldpainter.layers.HalfBlocks;

import java.awt.*;
import java.util.List;
import java.util.Set;

import static java.util.Collections.singleton;
import static org.pepsoft.worldpainter.Platform.Capability.NAME_BASED;
import static org.pepsoft.worldpainter.exporting.SecondPassLayerExporter.Stage.ADD_FEATURES;

/**
 * Builds the land in half blocks where the {@link HalfBlocks} layer is, after everything else has been placed on it (the
 * layer comes last), so that it can leave alone the columns where something stands.
 */
public class HalfBlocksExporter extends AbstractLayerExporter<HalfBlocks> implements SecondPassLayerExporter {
    public HalfBlocksExporter(Dimension dimension, Platform platform, ExporterSettings settings) {
        super(dimension, platform, settings, HalfBlocks.INSTANCE);
    }

    @Override
    public Set<Stage> getStages() {
        return singleton(ADD_FEATURES);
    }

    @Override
    public List<Fixup> addFeatures(Rectangle area, Rectangle exportedArea, MinecraftWorld minecraftWorld) {
        // Slabs and snow layers are only known by name
        if (platform.capabilities.contains(NAME_BASED)) {
            new SurfaceSmoother(dimension, platform).smooth(minecraftWorld, exportedArea, (x, y) -> dimension.getBitLayerValueAt(HalfBlocks.INSTANCE, x, y));
        }
        return null;
    }
}
