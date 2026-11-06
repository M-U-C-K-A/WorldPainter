package org.pepsoft.minecraft;

import org.jnbt.CompoundTag;
import org.jnbt.StringTag;
import org.jnbt.Tag;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * The block state palettes of chunks saved by Minecraft 26.1 and later, which leave out the properties which have their
 * default values and write blocks without properties as just their names.
 */
public class PaletteEntriesTest {
    @Test
    public void testClassicEntry() {
        final Map<String, Tag> properties = new HashMap<>();
        properties.put("axis", new StringTag("axis", "x"));
        final Map<String, Tag> entry = new HashMap<>();
        entry.put("Name", new StringTag("Name", "minecraft:oak_log"));
        entry.put("Properties", new CompoundTag("Properties", properties));
        assertEquals("x", PaletteEntries.getMaterial(new CompoundTag("", entry), true).getProperty(Constants.MC_AXIS));
    }

    @Test
    public void testNameOnly() {
        // Just the name: the defaults are put back
        final Material log = PaletteEntries.getMaterial(new StringTag("", "minecraft:oak_log"), true);
        assertEquals("minecraft:oak_log", log.name);
        assertEquals("y", log.getProperty(Constants.MC_AXIS));
        assertEquals(Integer.valueOf(0), PaletteEntries.getMaterial(new StringTag("", "minecraft:water"), true).getProperty(Material.LEVEL));
        assertFalse(PaletteEntries.getMaterial(new StringTag("", "minecraft:grass_block"), true).is(Material.SNOWY));
        assertNull("plain air", PaletteEntries.getMaterial(new StringTag("", "minecraft:air"), true));
    }

    @Test
    public void testWrappedName() {
        // In a list of mixed types a name is wrapped in a compound under an empty key
        final Map<String, Tag> entry = new HashMap<>();
        entry.put("", new StringTag("", "minecraft:bedrock"));
        assertEquals("minecraft:bedrock", PaletteEntries.getMaterial(new CompoundTag("", entry), true).name);
    }

    @Test
    public void testIdAndProperties() {
        final Map<String, Tag> properties = new HashMap<>();
        properties.put("distance", new StringTag("distance", "3"));
        final Map<String, Tag> entry = new HashMap<>();
        entry.put("id", new StringTag("id", "minecraft:oak_leaves"));
        entry.put("properties", new CompoundTag("properties", properties));
        final Material leaves = PaletteEntries.getMaterial(new CompoundTag("", entry), true);
        assertEquals("minecraft:oak_leaves", leaves.name);
        assertEquals("3", leaves.getProperty("distance"));
        // The ones left out are their defaults
        assertEquals("false", leaves.getProperty("persistent"));
        assertEquals("false", leaves.getProperty("waterlogged"));
    }
}
