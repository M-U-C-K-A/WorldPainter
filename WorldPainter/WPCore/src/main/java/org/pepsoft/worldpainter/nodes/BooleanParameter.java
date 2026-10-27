package org.pepsoft.worldpainter.nodes;

/**
 * An on/off parameter.
 */
public final class BooleanParameter extends Parameter<Boolean> {
    public BooleanParameter(String key, String label, String description, boolean defaultValue) {
        super(key, label, description, defaultValue);
    }

    @Override
    Class<?> getValueClass() {
        return Boolean.class;
    }
}
