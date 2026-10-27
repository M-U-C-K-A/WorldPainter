package org.pepsoft.worldpainter.nodes;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A node in a {@link NodeGraph}: a generator, combiner or filter of height fields. Height field values are nominally
 * between zero and one; the {@link OutputNode} maps them to block heights.
 *
 * <p>Nodes describe their inputs and {@link Parameter parameters}, so that the user interface can be built generically,
 * and {@link #compile(CompileContext, CompiledNode[]) compile} into an immutable, thread safe evaluator. Evaluation
 * must be a pure function of the world coordinates (it may not depend on the extent of the evaluated region), so that
 * the terrain can be generated tile by tile without seams.
 */
public abstract class Node implements Serializable, Cloneable {
    /**
     * The name of the kind of node, for instance "Perlin noise".
     */
    public abstract String getName();

    /**
     * The title to show on the node, which may reflect its settings. By default the {@link #getName() name}.
     */
    public String getTitle() {
        return getName();
    }

    public abstract NodeCategory getCategory();

    /**
     * The names of the inputs of the node, in order. Empty for generators.
     */
    public abstract List<String> getInputNames();

    /**
     * Whether an input, when it is not connected, receives the final terrain (the input of the {@link OutputNode}).
     * This lets masks work on the terrain without wiring. By default no input does.
     */
    public boolean isTerrainInput(int index) {
        return false;
    }

    /**
     * A short hint shown next to the name of an input while it is not connected, describing what it then receives,
     * or {@code null}. By default "terrain" for {@link #isTerrainInput(int) terrain inputs}.
     */
    public String getInputHint(int index) {
        return isTerrainInput(index) ? "terrain" : null;
    }

    /**
     * Whether the node has an output which can be connected to other nodes.
     */
    public boolean hasOutput() {
        return true;
    }

    /**
     * The parameters of the node, in the order in which they should be presented.
     */
    public abstract List<Parameter<?>> getParameters();

    /**
     * Whether a parameter currently has an effect, given the values of the other parameters. Irrelevant parameters may
     * be hidden from the user. By default all parameters are relevant.
     */
    public boolean isRelevant(Parameter<?> parameter) {
        return true;
    }

    /**
     * The unique ID of the node within its graph.
     */
    public int getId() {
        return id;
    }

    /**
     * The X coordinate of the top left corner of the node in the editor.
     */
    public double getX() {
        return x;
    }

    /**
     * The Y coordinate of the top left corner of the node in the editor.
     */
    public double getY() {
        return y;
    }

    public void setLocation(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public <T> T get(Parameter<T> parameter) {
        return parameter.fromStored(values);
    }

    public <T> void set(Parameter<T> parameter, T value) {
        if (value == null) {
            values.remove(parameter.getKey());
        } else {
            values.put(parameter.getKey(), parameter.toStored(value));
        }
    }

    /**
     * Convenience method for getting a numeric parameter as a {@code double}.
     */
    public double getDouble(NumberParameter parameter) {
        return get(parameter);
    }

    /**
     * Convenience method for getting a numeric parameter as an {@code int}.
     */
    public int getInt(NumberParameter parameter) {
        return (int) Math.round(get(parameter));
    }

    /**
     * Convenience method for setting a numeric parameter.
     */
    public void set(NumberParameter parameter, double value) {
        set(parameter, (Double) value);
    }

    /**
     * Create a deep copy of the node, with the same ID.
     */
    @Override
    public Node clone() {
        try {
            final Node clone = (Node) super.clone();
            clone.values = new HashMap<>(values);
            clone.values.replaceAll((key, value) -> (value instanceof float[]) ? ((float[]) value).clone() : value);
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }

    @Override
    public String toString() {
        return getTitle() + " #" + id;
    }

    void setId(int id) {
        this.id = id;
    }

    /**
     * Create an evaluator for this node.
     *
     * @param context The context, providing the seed, the height range, etc.
     * @param inputs  The evaluators of the nodes connected to the inputs, with {@code null} for unconnected inputs.
     */
    abstract CompiledNode compile(CompileContext context, CompiledNode[] inputs);

    /**
     * Derive a seed for a node from the graph seed and a variant number.
     */
    static long seed(CompileContext context, int variant) {
        return context.getSeed() + variant * 0x9E3779B97F4A7C15L;
    }

    private int id;
    private double x, y;
    private Map<String, Serializable> values = new HashMap<>();

    /**
     * The variant parameter which generators use, together with the seed of the graph, to derive their seed.
     */
    public static final NumberParameter VARIANT = new NumberParameter("variant", "Variant", "Change to get a different pattern with the same settings", 0, 0, 9999, 1, false, null);

    private static final long serialVersionUID = 1L;
}
