package org.pepsoft.worldpainter;

import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.MixedMaterial.Row;

import static org.pepsoft.worldpainter.Terrain.CUSTOM_TERRAIN_COUNT;

/**
 * Ready made mixes of blocks for natural looking ground, rock and cliffs (mixed dirt, forest floor, scree, banded cliff
 * rock, ...), which can be added as custom terrain types.
 */
public enum MaterialGroup {
    MIXED_DIRT("Mixed dirt", 0x7a5a3c, Family.SOIL) {
        @Override MixedMaterial createMaterial() {
            return noise(this, row("dirt", 40), row("coarse_dirt", 30), row("rooted_dirt", 15), row("podzol", 15));
        }
    },
    FOREST_FLOOR("Forest floor", 0x4f6b2f, Family.GRASS) {
        @Override MixedMaterial createMaterial() {
            return blobs(this, 0.6f, row("grass_block", 45), row("podzol", 25), row("coarse_dirt", 15), row("moss_block", 15));
        }
    },
    MEADOW("Meadow", 0x6f9e3c, Family.GRASS) {
        @Override MixedMaterial createMaterial() {
            return noise(this, row("grass_block", 80), row("moss_block", 10), row("coarse_dirt", 10));
        }
    },
    ROCKY_GROUND("Rocky ground", 0x7d7d7d, Family.STONE) {
        @Override MixedMaterial createMaterial() {
            return noise(this, row("stone", 40), row("andesite", 25), row("cobblestone", 15), row("gravel", 10), row("tuff", 10));
        }
    },
    LIMESTONE("Limestone", 0x8e8a82, Family.STONE) {
        @Override MixedMaterial createMaterial() {
            return blobs(this, 0.45f, row("stone", 35), row("andesite", 25), row("tuff", 15), row("diorite", 10), row("calcite", 10), row("cobblestone", 5));
        }
    },
    MOSSY_ROCK("Mossy rock", 0x5f7350, Family.STONE) {
        @Override MixedMaterial createMaterial() {
            return blobs(this, 0.5f, row("stone", 40), row("mossy_cobblestone", 25), row("moss_block", 20), row("cobblestone", 15));
        }
    },
    SCREE("Alpine scree", 0x8a8a86, Family.STONE) {
        @Override MixedMaterial createMaterial() {
            return blobs(this, 0.4f, row("stone", 30), row("gravel", 30), row("andesite", 20), row("cobblestone", 20));
        }
    },
    SNOWY_ROCK("Snowy rock", 0xdfe6ec, Family.SPECIAL) {
        @Override MixedMaterial createMaterial() {
            return blobs(this, 0.5f, row("snow_block", 50), row("stone", 25), row("calcite", 15), row("packed_ice", 10));
        }
    },
    RED_CLIFFS("Red cliff strata", 0xa0563a, Family.CLIFFS) {
        @Override MixedMaterial createMaterial() {
            return strata(this, row("terracotta", 3), row("red_terracotta", 2), row("brown_terracotta", 2), row("orange_terracotta", 1),
                    row("granite", 2), row("light_gray_terracotta", 1), row("red_terracotta", 3), row("white_terracotta", 1));
        }
    },
    GREY_CLIFFS("Grey cliff strata", 0x6f6f6f, Family.CLIFFS) {
        @Override MixedMaterial createMaterial() {
            return strata(this, row("stone", 4), row("andesite", 2), row("tuff", 2), row("deepslate", 1), row("stone", 3), row("calcite", 1), row("andesite", 2));
        }
    },
    SANDSTONE_CLIFFS("Sandstone strata", 0xd8c48c, Family.CLIFFS) {
        @Override MixedMaterial createMaterial() {
            return strata(this, row("sandstone", 4), row("smooth_sandstone", 2), row("terracotta", 1), row("sandstone", 3), row("yellow_terracotta", 1));
        }
    },
    BEACH("Beach mix", 0xe2d6a0, Family.SAND) {
        @Override MixedMaterial createMaterial() {
            return noise(this, row("sand", 65), row("gravel", 15), row("sandstone", 10), row("clay", 10));
        }
    },
    RIVERBED("Riverbed", 0x8b7f6a, Family.SAND) {
        @Override MixedMaterial createMaterial() {
            return blobs(this, 0.4f, row("gravel", 40), row("clay", 20), row("sand", 20), row("mud", 20));
        }
    },
    SWAMP_MUD("Swamp mud", 0x4a4232, Family.SOIL) {
        @Override MixedMaterial createMaterial() {
            return blobs(this, 0.5f, row("mud", 50), row("packed_mud", 15), row("muddy_mangrove_roots", 15), row("coarse_dirt", 20));
        }
    },
    CORAL_REEF("Coral reef", 0xe07a8f, Family.SAND) {
        @Override MixedMaterial createMaterial() {
            return blobs(this, 0.35f, row("sand", 35), row("tube_coral_block", 13), row("brain_coral_block", 13), row("bubble_coral_block", 13), row("fire_coral_block", 13), row("horn_coral_block", 13));
        }
    },
    VOLCANIC("Volcanic rock", 0x3d3a3a, Family.SPECIAL) {
        @Override MixedMaterial createMaterial() {
            return blobs(this, 0.5f, row("basalt", 35), row("blackstone", 25), row("tuff", 20), row("smooth_basalt", 15), row("magma_block", 5));
        }
    },
    GLACIER("Glacier", 0xbcd9f0, Family.SPECIAL) {
        @Override MixedMaterial createMaterial() {
            return blobs(this, 0.6f, row("packed_ice", 40), row("snow_block", 35), row("blue_ice", 15), row("ice", 10));
        }
    },
    DESERT("Desert mix", 0xdcc58e, Family.SAND) {
        @Override MixedMaterial createMaterial() {
            return blobs(this, 0.8f, row("sand", 65), row("sandstone", 20), row("red_sand", 15));
        }
    },
    COARSE_DIRT("Coarse dirt", 0x6e5038, Family.SOIL) {
        @Override MixedMaterial createMaterial() {
            return noise(this, row("coarse_dirt", 50), row("dirt", 30), row("gravel", 10), row("rooted_dirt", 10));
        }
    },
    TAIGA_FLOOR("Taiga floor", 0x5a4426, Family.SOIL) {
        @Override MixedMaterial createMaterial() {
            return blobs(this, 0.5f, row("podzol", 50), row("coarse_dirt", 20), row("moss_block", 15), row("dirt", 15));
        }
    },
    MOSSY_GROUND("Mossy ground", 0x5a7a32, Family.GRASS) {
        @Override MixedMaterial createMaterial() {
            return blobs(this, 0.5f, row("moss_block", 50), row("grass_block", 30), row("mossy_cobblestone", 10), row("coarse_dirt", 10));
        }
    },
    TRODDEN_PATH("Trodden path", 0x947a4c, Family.SOIL) {
        @Override MixedMaterial createMaterial() {
            return noise(this, row("dirt_path", 50), row("coarse_dirt", 30), row("gravel", 20));
        }
    },
    COBBLESTONE_MIX("Cobblestone mix", 0x7a7a7a, Family.STONE) {
        @Override MixedMaterial createMaterial() {
            return noise(this, row("cobblestone", 40), row("mossy_cobblestone", 20), row("stone", 20), row("gravel", 20));
        }
    },
    ANDESITE_MIX("Andesite mix", 0x888888, Family.STONE) {
        @Override MixedMaterial createMaterial() {
            return blobs(this, 0.45f, row("andesite", 50), row("stone", 30), row("cobblestone", 10), row("gravel", 10));
        }
    },
    GRANITE_MIX("Granite mix", 0x9a6a58, Family.STONE) {
        @Override MixedMaterial createMaterial() {
            return blobs(this, 0.45f, row("granite", 50), row("andesite", 20), row("stone", 20), row("diorite", 10));
        }
    },
    DEEPSLATE_MIX("Deepslate mix", 0x4a4a50, Family.STONE) {
        @Override MixedMaterial createMaterial() {
            return blobs(this, 0.45f, row("deepslate", 45), row("cobbled_deepslate", 25), row("tuff", 20), row("gravel", 10));
        }
    },
    SAND_AND_GRAVEL("Sand and gravel", 0xb8ac8c, Family.SAND) {
        @Override MixedMaterial createMaterial() {
            return blobs(this, 0.5f, row("sand", 55), row("gravel", 35), row("clay", 10));
        }
    },
    RED_SAND_MIX("Red sand mix", 0xb8643a, Family.SAND) {
        @Override MixedMaterial createMaterial() {
            return noise(this, row("red_sand", 60), row("terracotta", 15), row("coarse_dirt", 15), row("red_sandstone", 10));
        }
    },
    CHALK_CLIFFS("White chalk strata", 0xe6e2da, Family.CLIFFS) {
        @Override MixedMaterial createMaterial() {
            return strata(this, row("calcite", 4), row("diorite", 2), row("white_terracotta", 1), row("calcite", 2), row("stone", 1));
        }
    },
    GRASS_AND_DIRT("Grass and dirt", 0x6c8a3a, Family.GRASS) {
        @Override MixedMaterial createMaterial() {
            return noise(this, row("grass_block", 60), row("dirt", 25), row("coarse_dirt", 15));
        }
    },
    GRASS_AND_STONE("Grass and stone", 0x6e8460, Family.GRASS) {
        @Override MixedMaterial createMaterial() {
            return blobs(this, 0.5f, row("grass_block", 60), row("stone", 20), row("cobblestone", 10), row("gravel", 10));
        }
    },
    LUSH_GRASS("Lush grass", 0x4f9a32, Family.GRASS) {
        @Override MixedMaterial createMaterial() {
            return blobs(this, 0.6f, row("grass_block", 70), row("moss_block", 30));
        }
    },
    DRY_GRASS("Dry grass", 0x8c8a4a, Family.GRASS) {
        @Override MixedMaterial createMaterial() {
            return noise(this, row("grass_block", 50), row("coarse_dirt", 30), row("dirt", 15), row("dirt_path", 5));
        }
    },
    GRAVEL_MIX("Gravel mix", 0x857f78, Family.STONE) {
        @Override MixedMaterial createMaterial() {
            return noise(this, row("gravel", 60), row("stone", 15), row("andesite", 15), row("cobblestone", 10));
        }
    },
    MUD_AND_CLAY("Mud and clay", 0x5e5446, Family.SOIL) {
        @Override MixedMaterial createMaterial() {
            return blobs(this, 0.5f, row("mud", 40), row("clay", 30), row("packed_mud", 15), row("coarse_dirt", 15));
        }
    },
    BADLANDS("Badlands strata", 0xc8743a, Family.CLIFFS) {
        @Override MixedMaterial createMaterial() {
            return strata(this, row("orange_terracotta", 3), row("terracotta", 2), row("yellow_terracotta", 1), row("white_terracotta", 1),
                    row("red_terracotta", 2), row("brown_terracotta", 1), row("light_gray_terracotta", 1));
        }
    };

    /**
     * The families the mixes are grouped in for the user.
     */
    public enum Family {
        GRASS("Grass mixes"), SOIL("Dirt & soil mixes"), STONE("Stone mixes"), SAND("Sand & shore mixes"), CLIFFS("Cliff & strata mixes"), SPECIAL("Snow, ice & volcanic mixes");

        Family(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }

        private final String displayName;
    }

    MaterialGroup(String displayName, int colour, Family family) {
        this.displayName = displayName;
        this.colour = colour;
        this.family = family;
    }

    public Family getFamily() {
        return family;
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
    private final Family family;
}
