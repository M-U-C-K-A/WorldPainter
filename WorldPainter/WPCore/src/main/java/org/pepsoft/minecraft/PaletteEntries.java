package org.pepsoft.minecraft;

import org.jnbt.CompoundTag;
import org.jnbt.StringTag;
import org.jnbt.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static org.pepsoft.minecraft.Constants.TAG_NAME;
import static org.pepsoft.minecraft.Constants.TAG_PROPERTIES;

/**
 * Reads the entries of a block state palette, as found in chunk sections and structure files, in all the forms
 * Minecraft writes them:
 *
 * <ul>
 *     <li>a compound with a {@code Name} and optionally {@code Properties}, as always;
 *     <li>a compound with an {@code id} and optionally {@code properties}, as Minecraft 26.1 and later write them;
 *     <li>just the name of the block, for a block without properties, as Minecraft 26.1 and later write them;
 *     <li>and the name wrapped in a compound under an empty key, which is how such a name is stored in a list which also
 *     has compounds in it (a list of mixed types).
 * </ul>
 */
public final class PaletteEntries {
    private PaletteEntries() {
        // Prevent instantiation
    }

    /**
     * The material of a palette entry.
     *
     * @param entry The entry.
     * @return The material, or {@code null} if the entry is plain air (without properties) and {@code airAsNull} is set.
     * @throws IllegalArgumentException If the entry is not in any of the known forms.
     */
    public static Material getMaterial(Tag entry, boolean airAsNull) {
        if (entry instanceof StringTag) {
            return material(((StringTag) entry).getValue(), null, airAsNull);
        } else if (entry instanceof CompoundTag) {
            final CompoundTag compound = (CompoundTag) entry;
            final Tag nameTag = compound.getTag(TAG_NAME);
            if (nameTag instanceof StringTag) {
                return material(((StringTag) nameTag).getValue(), (CompoundTag) compound.getTag(TAG_PROPERTIES), airAsNull);
            }
            // As Minecraft 26.1 and later write them: id and properties
            final Tag idTag = compound.getTag(TAG_ID_26);
            if (idTag instanceof StringTag) {
                return material(((StringTag) idTag).getValue(), (CompoundTag) compound.getTag(TAG_PROPERTIES_26), airAsNull);
            }
            final Tag wrapped = compound.getTag("");
            if ((wrapped != null) && (compound.getValue().size() == 1)) {
                // An element of a list of mixed types
                return getMaterial(wrapped, airAsNull);
            }
        }
        throw new IllegalArgumentException("Unknown block state palette entry " + entry);
    }

    /**
     * The value of a string entry of a palette (such as of biomes), which may be wrapped in a compound under an empty
     * key in a list of mixed types.
     */
    public static String getString(Tag entry) {
        if (entry instanceof StringTag) {
            return ((StringTag) entry).getValue();
        } else if ((entry instanceof CompoundTag) && (((CompoundTag) entry).getTag("") != null)) {
            return getString(((CompoundTag) entry).getTag(""));
        }
        throw new IllegalArgumentException("Unknown palette entry " + entry);
    }

    private static Material material(String name, CompoundTag propertiesTag, boolean airAsNull) {
        if (airAsNull && name.equals(Constants.MC_AIR) && (propertiesTag == null)) {
            return null;
        }
        final Map<String, String> properties;
        if (propertiesTag != null) {
            properties = new HashMap<>();
            for (Map.Entry<String, Tag> entry: propertiesTag.getValue().entrySet()) {
                final Tag tag = entry.getValue();
                if (tag instanceof StringTag) {
                    properties.put(entry.getKey(), ((StringTag) tag).getValue());
                } else {
                    logger.warn("Ignoring non-vanilla material property tag {} of unsupported type {}", entry.getKey(), tag.getClass().getSimpleName());
                }
            }
        } else {
            properties = null;
        }
        return Material.get(name, withDefaults(name, properties));
    }

    /**
     * Minecraft 26.1 and later leave out the properties which have their default values. WorldPainter expects them to
     * be there, so put back those whose default is certain; the others are left out, which Minecraft reads as their
     * defaults if the block is written back.
     */
    private static Map<String, String> withDefaults(String name, Map<String, String> properties) {
        final Material prototype = Material.getPrototype(name);
        final Map<String, Material.PropertyDescriptor> descriptors = prototype.propertyDescriptors;
        if ((descriptors == null) || descriptors.isEmpty() || ((properties != null) && properties.keySet().containsAll(descriptors.keySet()))) {
            return properties;
        }
        final String simpleName = name.substring(name.indexOf(':') + 1);
        Map<String, String> result = properties;
        for (Map.Entry<String, Material.PropertyDescriptor> entry: descriptors.entrySet()) {
            if ((properties != null) && properties.containsKey(entry.getKey())) {
                continue;
            }
            final String value = defaultValue(simpleName, entry.getKey(), entry.getValue());
            if (value != null) {
                if (result == properties) {
                    result = (properties != null) ? new HashMap<>(properties) : new HashMap<>();
                }
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    /**
     * The default value of a property of a block in Minecraft, if it is certain, otherwise {@code null}.
     */
    static String defaultValue(String block, String property, Material.PropertyDescriptor descriptor) {
        switch (descriptor.type) {
            case BOOLEAN:
                switch (property) {
                    case "lit":
                        return (block.equals("redstone_torch") || block.equals("redstone_wall_torch") || block.endsWith("campfire")) ? "true" : "false";
                    case "north": case "east": case "south": case "west": case "up": case "down":
                        if (block.endsWith("mushroom_block") || block.equals("mushroom_stem")) {
                            return "true";
                        }
                        return (property.equals("up") && block.endsWith("_wall")) ? "true" : "false";
                    case "waterlogged": case "snowy": case "persistent": case "powered": case "triggered": case "open":
                    case "occupied": case "extended": case "in_wall": case "attached": case "disarmed": case "inverted":
                    case "locked": case "conditional": case "hanging": case "unstable": case "eye": case "has_book":
                    case "has_record": case "signal_fire": case "bottom": case "short": case "berries": case "tip":
                        return "false";
                    default:
                        return null;
                }
            case INTEGER:
                switch (property) {
                    case "level":
                        if (block.equals("light")) {
                            return "15";
                        }
                        return Integer.toString(descriptor.minValue);
                    case "distance":
                        return (block.endsWith("_leaves") || block.equals("scaffolding")) ? Integer.toString(descriptor.maxValue) : null;
                    case "layers": case "age": case "stage": case "bites": case "moisture": case "power": case "rotation":
                    case "note": case "honey_level": case "hatch": case "dusted": case "charges": case "pickles": case "eggs":
                    case "candles": case "flower_amount": case "segment_amount": case "delay":
                        return Integer.toString(descriptor.minValue);
                    default:
                        return null;
                }
            case ENUM:
                final java.util.List<String> values = java.util.Arrays.asList(descriptor.enumValues);
                switch (property) {
                    case "axis":
                        return block.equals("nether_portal") ? "x" : (values.contains("y") ? "y" : null);
                    case "half":
                        return values.contains("lower") ? "lower" : (values.contains("bottom") ? "bottom" : null);
                    case "type":
                        return values.contains("bottom") ? "bottom" : (values.contains("single") ? "single" : (values.contains("normal") ? "normal" : null));
                    case "shape":
                        return values.contains("straight") ? "straight" : (values.contains("north_south") ? "north_south" : null);
                    case "facing":
                        // Only blocks which face sideways all default to north
                        return (values.contains("north") && (! values.contains("up")) && (! values.contains("down"))) ? "north" : null;
                    case "north": case "east": case "south": case "west": case "up": case "down":
                        return values.contains("none") ? "none" : null;
                    case "face":
                        return values.contains("wall") ? "wall" : null;
                    case "hinge":
                        return values.contains("left") ? "left" : null;
                    case "part":
                        return values.contains("foot") ? "foot" : null;
                    case "attachment":
                        return values.contains("floor") ? "floor" : null;
                    case "instrument":
                        return values.contains("harp") ? "harp" : null;
                    case "thickness":
                        return values.contains("tip") ? "tip" : null;
                    case "vertical_direction":
                        return values.contains("up") ? "up" : null;
                    case "tilt":
                        return values.contains("none") ? "none" : null;
                    default:
                        return null;
                }
            default:
                return null;
        }
    }

    private static final String TAG_ID_26 = "id", TAG_PROPERTIES_26 = "properties";
    private static final Logger logger = LoggerFactory.getLogger(PaletteEntries.class);
}
