package org.pepsoft.worldpainter.nodes;

/**
 * A numeric parameter with a range.
 */
public final class NumberParameter extends Parameter<Double> {
    /**
     * @param min         The lowest allowed value.
     * @param max         The highest allowed value.
     * @param step        The increment for spinners, and the precision. Use one or more for whole numbers.
     * @param logarithmic Whether sliders for the parameter should have a logarithmic scale.
     * @param unit        The unit to display after the value, or {@code null}.
     */
    public NumberParameter(String key, String label, String description, double defaultValue, double min, double max, double step, boolean logarithmic, String unit) {
        super(key, label, description, defaultValue);
        this.min = min;
        this.max = max;
        this.step = step;
        this.logarithmic = logarithmic;
        this.unit = unit;
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getStep() {
        return step;
    }

    /**
     * Whether the value is a whole number.
     */
    public boolean isInteger() {
        return step >= 1 && step == Math.rint(step);
    }

    public boolean isLogarithmic() {
        return logarithmic;
    }

    public String getUnit() {
        return unit;
    }

    /**
     * Clamp a value to the range of this parameter.
     */
    public double clamp(double value) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    Class<?> getValueClass() {
        return Double.class;
    }

    private final double min, max, step;
    private final boolean logarithmic;
    private final String unit;
}
