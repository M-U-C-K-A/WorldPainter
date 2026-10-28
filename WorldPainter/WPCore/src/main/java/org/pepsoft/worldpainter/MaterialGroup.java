package org.pepsoft.worldpainter;

import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.MixedMaterial.Row;

import static org.pepsoft.worldpainter.Terrain.CUSTOM_TERRAIN_COUNT;

/**
 * Ready made mixes of blocks for natural looking ground, rock and cliffs (mixed dirt, forest floor, scree, banded cliff
 * rock, ...), which can be added as custom terrain types.
 */
public enum MaterialGroup {
    MIXED_DIRT("Mixed dirt", 0x7a5a3c) {
        @Override MixedMaterial createMaterial() {
            return noise(this, row("dirt", 40), row("coarse_dirt", 30), row("rooted_dirt", 15), row("podzol", 15));
        }
    },
    FOREST_FLOOR("Forest floor", 0x4f6b2f) {
        @Override MixedMaterial createMaterial() {
            return blobs(this, 0.6f, row("grass_block", 45), row("podzol", 25), row("coarse_dirt", 15), row("moss_block", 15));
        }
    },
    MEADOW("Meadow", 0x6f9e3c) {
        @Override MixedMaterial createMaterial() {
            return noise(this, row("grass_block", 80), row("moss_block", 10), row("coarse_dirt", 10));
        }
    },
    ROCKY_GROUND("Rocky ground", 0x7d7d7d) {
        @Override MixedMaterial createMaterial() {
            return noise(this, row("stone", 40), row("andesite", 25), row("cobblestone", 15), row("gravel", 10), row("tuff", 10));
        }
    },
    MOSSY_ROCK("Mossy rock", 0x5f7350) {
        @Override MixedMaterial createMaterial() {
            return blobs(this, 0.5f, row("stone", 40), row("mossy_cobblestone", 25), row("moss_block", 20), row("cobblestone", 15));
        }
    },
    SCREE("Alpine scree", 0x8a8a86) {
        @Override MixedMaterial createMaterial() {
            return blobs(this, 0.4f, row("stone", 30), row("gravel", 30), row("andesite", 20), row("cobblestone", 20));
        }
    },
    SNOWY_ROCK("Snowy rock", 0xdfe6ec) {
        @Override MixedMaterial createMaterial() {
            return blobs(this, 0.5f, row("snow_block", 50), row("stone", 25), row("calcite", 15), row("packed_ice", 10));
        }
    },
    RED_CLIFFS("Red cliff strata", 0xa0563a) {
        @Override MixedMaterial createMaterial() {
            return strata(this, row("terracotta", 3), row("red_terracotta", 2), row("brown_terracotta", 2), row("orange_terracotta", 1),
                    row("granite", 2), row("light_gray_terracotta", 1), row("red_terracotta", 3), row("white_terracotta", 1));
        }
    },
    GREY_CLIFFS("Grey cliff strata", 0x6f6f6f) {
        @Override MixedMaterial createMaterial() {
            return strata(this, row("stone", 4), row("andesite", 2), row("tuff", 2), row("deepslate", 1), row("stone", 3), row("calcite", 1), row("andesite", 2));
        }
    },
    SANDSTONE_CLIFFS("Sandstone strata", 0xd8c48c) {
        @Override MixedMaterial createMaterial() {
            return strata(this, row("sandstone", 4), row("smooth_sandstone", 2), row("terracotta", 1), row("sandstone", 3), row("yellow_terracotta", 1));
        }
    },
    BEACH("Beach mix", 0xe2d6a0) {
        @Override MixedMaterial createMaterial() {
            return noise(this, row("sand", 65), row("gravel", 15), row("sandstone", 10), row("clay", 10));
        }
    },
    RIVERBED("Riverbed", 0x8b7f6a) {
        @Override MixedMaterial createMaterial() {
            return blobs(this, 0.4f, row("gravel", 40), row("clay", 20), row("sand", 20), row("mud", 20));
        }
    },
    SWAMP_MUD("Swamp mud", 0x4a4232) {
        @Override MixedMaterial createMaterial() {
            return blobs(this, 0.5f, row("mud", 50), row("packed_mud", 15), row("muddy_mangrove_roots", 15), row("coarse_dirt", 20));
        }
    },
    DESERT("Desert mix", 0xdcc58e) {
        @Override MixedMaterial createMaterial() {
            return blobs(this, 0.8f, row("sand", 65), row("sandstone", 20), row("red_sand", 15));
        }
    };

    MaterialGroup(String displayName, int colour) {
        this.displayName = displayName;
        this.colour = colour;
    }

    /**
     * A new mixed material for this group.
     */
    abstract MixedMaterial createMaterial();

    /**
     * A colour for previews, as RGB.
     */
    public int getColour() {
        return colour;
    }

    @Override
    public String toString() {
        return displayName;
    }

    /**
     * A new mixed material for this group, for adding to the custom terrain types by hand.
     */
    public MixedMaterial newMaterial() {
        return createMaterial();
    }

    /**
     * The custom terrain type for this group: the one which already has its material, or else a free one which is set
     * up with it.
     *
     * @return The terrain type, or {@code null} if all custom terrain types are in use.
     */
    public Terrain install() {
        int free = -1;
        for (int i = 0; i < CUSTOM_TERRAIN_COUNT; i++) {
            if (Terrain.isCustomMaterialConfigured(i)) {
                if (displayName.equals(Terrain.getCustomMaterial(i).getName())) {
                    return Terrain.getCustomTerrain(i);
                }
            } else if (free == -1) {
                free = i;
            }
        }
        if (free == -1) {
            return null;
        }
        Terrain.setCustomMaterial(free, createMaterial());
        return Terrain.getCustomTerrain(free);
    }

    private static Row row(String block, int count) {
        return new Row(Material.get("minecraft:" + block), Math.max(1, count), 1.0f);
    }

    private static MixedMaterial noise(MaterialGroup group, Row... rows) {
        return new MixedMaterial(group.displayName, rows, -1, group.colour);
    }

    private static MixedMaterial blobs(MaterialGroup group, float scale, Row... rows) {
        return new MixedMaterial(group.displayName, rows, -1, group.colour, scale);
    }

    /**
     * Horizontal bands of rock which repeat upwards and dip slightly, as in a cliff of sedimentary rock.
     */
    private static MixedMaterial strata(MaterialGroup group, Row... rows) {
        return new MixedMaterial(group.displayName, rows, -1, group.colour, null, 0.08, 0.03, true);
    }

    private final String displayName;
    private final int colour;
}
