package org.pepsoft.worldpainter.nodes;

import java.util.List;

/**
 * A combiner of two inputs: add, subtract, multiply, maximum or minimum. The strength scales the effect of input B.
 */
public class CombineNode extends Node {
    public CombineNode() {
        this(Mode.ADD);
    }

    public CombineNode(Mode mode) {
        set(MODE, mode);
    }

    @Override
    public String getName() {
        return "Combine";
    }

    @Override
    public String getTitle() {
        return get(MODE).toString();
    }

    @Override
    public NodeCategory getCategory() {
        return NodeCategory.COMBINER;
    }

    @Override
    public List<String> getInputNames() {
        return INPUT_NAMES;
    }

    @Override
    public List<Parameter<?>> getParameters() {
        return List.of(MODE, STRENGTH);
    }

    @Override
    CompiledNode compile(CompileContext context, CompiledNode[] inputs) {
        final Mode mode = get(MODE);
        final float strength = (float) getDouble(STRENGTH);
        return new CompiledNode(inputs, 0.0f, (mode == Mode.MULTIPLY) ? 1.0f : 0.0f) {
            @Override
            float[] compute(Evaluation evaluation, Region region) {
                final float[] a = input(evaluation, 0, region), b = input(evaluation, 1, region);
                final float[] result = new float[a.length];
                for (int i = 0; i < a.length; i++) {
                    result[i] = a[i] + (combine(mode, a[i], b[i]) - a[i]) * strength;
                }
                return result;
            }
        };
    }

    static float combine(Mode mode, float a, float b) {
        switch (mode) {
            case ADD:
                return a + b;
            case SUBTRACT:
                return a - b;
            case MULTIPLY:
                return a * b;
            case MAX:
                return Math.max(a, b);
            case MIN:
                return Math.min(a, b);
            default:
                throw new InternalError();
        }
    }

    public static final ChoiceParameter<Mode> MODE = new ChoiceParameter<>("mode", "Operation", "How input B is combined with input A", Mode.ADD, Mode.values());
    public static final NumberParameter STRENGTH = new NumberParameter("strength", "Strength", "How strongly input B affects the result", 1, 0, 2, 0.01, false, null);

    private static final List<String> INPUT_NAMES = List.of("A", "B");
    private static final long serialVersionUID = 1L;

    public enum Mode {
        ADD("Add"), SUBTRACT("Subtract"), MULTIPLY("Multiply"), MAX("Maximum"), MIN("Minimum");

        Mode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }

        private final String displayName;
    }
}
