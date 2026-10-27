package org.pepsoft.worldpainter.nodes;

import java.util.List;

/**
 * A filter which remaps values through a smooth curve. Between the control points the curve is a monotone cubic
 * spline, so it does not overshoot; outside the range zero to one it continues in a straight line.
 */
public class CurveNode extends Node {
    public CurveNode() {
        // Defaults
    }

    public CurveNode(float... points) {
        set(CURVE, points);
    }

    @Override
    public String getName() {
        return "Curve";
    }

    @Override
    public NodeCategory getCategory() {
        return NodeCategory.FILTER;
    }

    @Override
    public List<String> getInputNames() {
        return INPUT_NAMES;
    }

    @Override
    public List<Parameter<?>> getParameters() {
        return List.of(CURVE);
    }

    @Override
    CompiledNode compile(CompileContext context, CompiledNode[] inputs) {
        final Spline spline = new Spline(get(CURVE));
        final float[] table = new float[TABLE_SIZE + 1];
        for (int i = 0; i <= TABLE_SIZE; i++) {
            table[i] = (float) spline.evaluate((double) i / TABLE_SIZE);
        }
        return new CompiledNode.Filter(inputs[0]) {
            @Override
            float apply(float value) {
                if (value <= 0) {
                    return (float) spline.evaluate(value);
                } else if (value >= 1) {
                    return (float) spline.evaluate(value);
                }
                final float position = value * TABLE_SIZE;
                final int index = (int) position;
                return table[index] + (table[index + 1] - table[index]) * (position - index);
            }
        };
    }

    /**
     * Evaluate a curve, for instance to draw it.
     *
     * @param points The control points, as for {@link #CURVE}.
     * @param x      The input value.
     * @return The output value.
     */
    public static double evaluate(float[] points, double x) {
        return new Spline(points).evaluate(x);
    }

    public static final CurveParameter CURVE = new CurveParameter("curve", "Curve", "Drag the points to reshape the terrain; double-click to add a point, right-click to remove one", new float[] {0, 0, 1, 1});

    private static final List<String> INPUT_NAMES = List.of("In");
    private static final int TABLE_SIZE = 1024;
    private static final long serialVersionUID = 1L;

    /**
     * A monotone cubic Hermite spline (Fritsch-Carlson) through a set of points.
     */
    static final class Spline {
        Spline(float[] points) {
            // Sort the points by X and drop points with duplicate X coordinates
            final int count = points.length / 2;
            final Integer[] order = new Integer[count];
            for (int i = 0; i < count; i++) {
                order[i] = i;
            }
            java.util.Arrays.sort(order, (i1, i2) -> Float.compare(points[i1 * 2], points[i2 * 2]));
            final double[] xs = new double[count], ys = new double[count];
            int n = 0;
            for (int i = 0; i < count; i++) {
                final double x = points[order[i] * 2], y = points[order[i] * 2 + 1];
                if ((n == 0) || (x > xs[n - 1])) {
                    xs[n] = x;
                    ys[n] = y;
                    n++;
                }
            }
            if (n == 1) {
                xs[1] = xs[0] + 1;
                ys[1] = ys[0];
                n = 2;
            }
            this.xs = java.util.Arrays.copyOf(xs, n);
            this.ys = java.util.Arrays.copyOf(ys, n);
            final double[] deltas = new double[n - 1];
            for (int i = 0; i < n - 1; i++) {
                deltas[i] = (this.ys[i + 1] - this.ys[i]) / (this.xs[i + 1] - this.xs[i]);
            }
            tangents = new double[n];
            tangents[0] = deltas[0];
            tangents[n - 1] = deltas[n - 2];
            for (int i = 1; i < n - 1; i++) {
                tangents[i] = ((deltas[i - 1] * deltas[i]) <= 0) ? 0 : ((deltas[i - 1] + deltas[i]) / 2);
            }
            for (int i = 0; i < n - 1; i++) {
                if (deltas[i] == 0) {
                    tangents[i] = 0;
                    tangents[i + 1] = 0;
                } else {
                    final double a = tangents[i] / deltas[i], b = tangents[i + 1] / deltas[i];
                    final double s = a * a + b * b;
                    if (s > 9) {
                        final double t = 3 / Math.sqrt(s);
                        tangents[i] = t * a * deltas[i];
                        tangents[i + 1] = t * b * deltas[i];
                    }
                }
            }
        }

        double evaluate(double x) {
            final int n = xs.length;
            if (x <= xs[0]) {
                return ys[0] + (x - xs[0]) * tangents[0];
            } else if (x >= xs[n - 1]) {
                return ys[n - 1] + (x - xs[n - 1]) * tangents[n - 1];
            }
            int i = 0;
            while (x > xs[i + 1]) {
                i++;
            }
            final double h = xs[i + 1] - xs[i], t = (x - xs[i]) / h, t2 = t * t, t3 = t2 * t;
            return (2 * t3 - 3 * t2 + 1) * ys[i] + (t3 - 2 * t2 + t) * h * tangents[i] + (-2 * t3 + 3 * t2) * ys[i + 1] + (t3 - t2) * h * tangents[i + 1];
        }

        private final double[] xs, ys, tangents;
    }
}
