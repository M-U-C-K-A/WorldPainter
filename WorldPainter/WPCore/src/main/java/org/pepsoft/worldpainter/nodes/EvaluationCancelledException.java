package org.pepsoft.worldpainter.nodes;

/**
 * Thrown when the thread evaluating a node graph is interrupted.
 */
public class EvaluationCancelledException extends RuntimeException {
    public EvaluationCancelledException() {
        super("Node graph evaluation cancelled");
    }

    private static final long serialVersionUID = 1L;
}
