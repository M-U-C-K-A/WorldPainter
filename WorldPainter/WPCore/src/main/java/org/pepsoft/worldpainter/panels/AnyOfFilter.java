package org.pepsoft.worldpainter.panels;

import org.pepsoft.worldpainter.operations.Filter;

import java.util.Collection;
import java.util.List;

import static java.util.Collections.unmodifiableList;

/**
 * A filter that lets through what any one of a number of subordinate filters lets through, returning the highest
 * strength among them. For "only on" with several items, since nothing can be on all of them at the same time.
 */
public final class AnyOfFilter implements Filter {
    public AnyOfFilter(Collection<Filter> filters) {
        this.filters = List.copyOf(filters);
    }

    public List<Filter> getFilters() {
        return unmodifiableList(filters);
    }

    @Override
    public float modifyStrength(int x, int y, float strength) {
        float highest = 0.0f;
        for (Filter filter: filters) {
            highest = Math.max(highest, filter.modifyStrength(x, y, strength));
        }
        return highest;
    }

    private final List<Filter> filters;
}
