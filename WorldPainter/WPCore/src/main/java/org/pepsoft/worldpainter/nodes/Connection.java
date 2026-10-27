package org.pepsoft.worldpainter.nodes;

import java.io.Serializable;
import java.util.Objects;

/**
 * A wire from the output of one {@link Node} to an input of another. Immutable.
 */
public final class Connection implements Serializable {
    public Connection(int fromNodeId, int toNodeId, int toInput) {
        this.fromNodeId = fromNodeId;
        this.toNodeId = toNodeId;
        this.toInput = toInput;
    }

    /**
     * The ID of the node whose output is connected.
     */
    public int getFromNodeId() {
        return fromNodeId;
    }

    /**
     * The ID of the node whose input is connected.
     */
    public int getToNodeId() {
        return toNodeId;
    }

    /**
     * The index of the input which is connected.
     */
    public int getToInput() {
        return toInput;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (! (o instanceof Connection)) {
            return false;
        }
        final Connection that = (Connection) o;
        return (fromNodeId == that.fromNodeId) && (toNodeId == that.toNodeId) && (toInput == that.toInput);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromNodeId, toNodeId, toInput);
    }

    @Override
    public String toString() {
        return fromNodeId + " -> " + toNodeId + "[" + toInput + "]";
    }

    private final int fromNodeId, toNodeId, toInput;

    private static final long serialVersionUID = 1L;
}
