package org.pepsoft.worldpainter.nodes;

import java.io.Serializable;
import java.util.Map;

/**
 * A description of a parameter of a {@link Node}: its key, its label and description for the user interface, and its
 * default value. Parameter values are stored in the node, keyed by {@link #getKey() key}, so that nodes can gain new
 * parameters without breaking saved graphs. Subclasses describe the different kinds of value, which the user interface
 * uses to choose an editor.
 *
 * @param <T> The type of the value.
 */
public abstract class Parameter<T> {
    protected Parameter(String key, String label, String description, T defaultValue) {
        this.key = key;
        this.label = label;
        this.description = description;
        this.defaultValue = defaultValue;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    /**
     * A description of the parameter for tooltips, or {@code null}.
     */
    public String getDescription() {
        return description;
    }

    public T getDefaultValue() {
        return defaultValue;
    }

    /**
     * Convert a value to the form in which it is stored.
     */
    Serializable toStored(T value) {
        return (Serializable) value;
    }

    /**
     * Convert a stored value back to a value, or return the default value if it is missing or of the wrong type.
     */
    @SuppressWarnings("unchecked") // Guarded by isInstance
    T fromStored(Map<String, Serializable> values) {
        final Object value = values.get(key);
        return getValueClass().isInstance(value) ? (T) value : defaultValue;
    }

    abstract Class<?> getValueClass();

    private final String key, label, description;
    private final T defaultValue;
}
