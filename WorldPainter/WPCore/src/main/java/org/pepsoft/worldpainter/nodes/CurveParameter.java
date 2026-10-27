package org.pepsoft.worldpainter.nodes;

import java.io.Serializable;
import java.util.Map;

/**
 * A curve through a number of control points, mapping input values between zero and one to output values. The value
 * is an array of interleaved coordinates {@code x0, y0, x1, y1, ...} sorted by X, with at least two points.
 */
public final class CurveParameter extends Parameter<float[]> {
    public CurveParameter(String key, String label, String description, float[] defaultValue) {
        super(key, label, description, defaultValue);
    }

    @Override
    Serializable toStored(float[] value) {
        return value.clone();
    }

    @Override
    float[] fromStored(Map<String, Serializable> values) {
        final Object value = values.get(getKey());
        return ((value instanceof float[]) && (((float[]) value).length >= 4) && ((((float[]) value).length % 2) == 0)) ? ((float[]) value).clone() : getDefaultValue().clone();
    }

    @Override
    Class<?> getValueClass() {
        return float[].class;
    }
}
