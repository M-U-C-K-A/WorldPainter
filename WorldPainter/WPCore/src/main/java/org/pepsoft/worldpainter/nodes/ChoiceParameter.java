package org.pepsoft.worldpainter.nodes;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * A parameter with a value from a fixed set of choices. The value is stored by name, so that saved graphs survive
 * changes to the set of choices.
 *
 * @param <E> The enum type of the choices. Its {@code toString()} is used as the display name.
 */
public final class ChoiceParameter<E extends Enum<E>> extends Parameter<E> {
    @SafeVarargs
    public ChoiceParameter(String key, String label, String description, E defaultValue, E... choices) {
        super(key, label, description, defaultValue);
        this.choices = List.of(choices);
        enumClass = defaultValue.getDeclaringClass();
    }

    public List<E> getChoices() {
        return choices;
    }

    @Override
    Serializable toStored(E value) {
        return value.name();
    }

    @Override
    E fromStored(Map<String, Serializable> values) {
        final Object value = values.get(getKey());
        if (value instanceof String) {
            for (E choice: choices) {
                if (choice.name().equals(value)) {
                    return choice;
                }
            }
        }
        return getDefaultValue();
    }

    @Override
    Class<?> getValueClass() {
        return enumClass;
    }

    private final List<E> choices;
    private final Class<E> enumClass;
}
