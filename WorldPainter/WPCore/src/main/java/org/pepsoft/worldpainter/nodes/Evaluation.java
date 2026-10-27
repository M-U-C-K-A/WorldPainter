package org.pepsoft.worldpainter.nodes;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * A single evaluation of a {@link CompiledGraph}, which caches the result of each node for each region so that nodes
 * which are used by several others are only evaluated once. Not thread safe: use one evaluation per thread.
 */
public final class Evaluation {
    Evaluation() {
        // Package private
    }

    /**
     * Throw an {@link EvaluationCancelledException} if the current thread has been interrupted.
     */
    public void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) {
            throw new EvaluationCancelledException();
        }
    }

    float[] evaluate(CompiledNode node, Region region) {
        checkCancelled();
        final Map<Region, float[]> nodeCache = cache.computeIfAbsent(node, n -> new HashMap<>());
        float[] values = nodeCache.get(region);
        if (values == null) {
            values = node.compute(this, region);
            nodeCache.put(region, values);
        }
        return values;
    }

    private final Map<CompiledNode, Map<Region, float[]>> cache = new IdentityHashMap<>();
}
