package at.jku.dke.dfm.core.models.ast;

import at.jku.dke.dfm.core.models.enums.ElementType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * A fact with descriptives, measures and dimensions.
 */
public class FactElement extends AbstractElement {
    private final List<String> descriptives = new ArrayList<>();
    private final List<String> measures = new ArrayList<>();
    private final List<DimensionElement> dimensions = new ArrayList<>();

    public FactElement(String name) {
        super(name, ElementType.FACT);
    }

    public List<String> getDescriptives() {
        return descriptives;
    }

    public List<String> getMeasures() {
        return measures;
    }

    public List<DimensionElement> getDimensions() {
        return dimensions;
    }

    public boolean structurallyEqualsWithoutDimensions(FactElement other) {
        return other != null
            && descriptives.stream().sorted().toList().equals(other.descriptives.stream().sorted().toList())
            && measures.stream().sorted().toList().equals(other.measures.stream().sorted().toList());
    }

    /**
     * Matches the expected connections one-to-one, preserving which connections share a dimension.
     * Additional dimensions or connections in this fact are permitted.
     *
     * @param expected Fact containing the expected connections.
     * @param dimensionMatches Predicate receiving an actual and an expected dimension, respectively.
     * @return Whether all expected connections can be matched.
     */
    public boolean containsMatchingDimensionConnections(FactElement expected,
                                                        BiPredicate<DimensionElement, DimensionElement> dimensionMatches) {
        if (expected == null) {
            return false;
        }
        var actualGroups = groupDimensions(dimensions);
        var expectedGroups = groupDimensions(expected.dimensions);
        var assigned = new int[actualGroups.size()];
        java.util.Arrays.fill(assigned, -1);
        for (int i = 0; i < expectedGroups.size(); i++) {
            if (!assignDimensionGroup(i, expectedGroups, actualGroups, assigned, new boolean[actualGroups.size()],
                dimensionMatches)) {
                return false;
            }
        }
        return true;
    }

    private static List<List<DimensionElement>> groupDimensions(List<DimensionElement> dimensions) {
        var groups = new LinkedHashMap<DimensionElement, List<DimensionElement>>();
        for (var dimension : dimensions) {
            groups.computeIfAbsent(dimension.getDefinition(), ignored -> new ArrayList<>()).add(dimension);
        }
        return new ArrayList<>(groups.values());
    }

    private static boolean assignDimensionGroup(int expectedIndex, List<List<DimensionElement>> expectedGroups,
                                                List<List<DimensionElement>> actualGroups, int[] assigned,
                                                boolean[] visited,
                                                BiPredicate<DimensionElement, DimensionElement> dimensionMatches) {
        var expected = expectedGroups.get(expectedIndex);
        for (int i = 0; i < actualGroups.size(); i++) {
            if (visited[i] || !groupMatches(actualGroups.get(i), expected, dimensionMatches)) {
                continue;
            }
            visited[i] = true;
            if (assigned[i] == -1 || assignDimensionGroup(assigned[i], expectedGroups, actualGroups,
                assigned, visited, dimensionMatches)) {
                assigned[i] = expectedIndex;
                return true;
            }
        }
        return false;
    }

    private static boolean groupMatches(List<DimensionElement> actual, List<DimensionElement> expected,
                                        BiPredicate<DimensionElement, DimensionElement> dimensionMatches) {
        if (actual.size() < expected.size() || !dimensionMatches.test(actual.get(0), expected.get(0))) {
            return false;
        }
        var remaining = new ArrayList<>(actual);
        for (var expectedConnection : expected) {
            var match = remaining.stream().filter(expectedConnection::hasSameFactConnection).findFirst();
            if (match.isEmpty()) {
                return false;
            }
            remaining.remove(match.get());
        }
        return true;
    }

    @Override
    public boolean structurallyEquals(AbstractElement other) {
        if (!(other instanceof FactElement fact)
            || !structurallyEqualsWithoutDimensions(fact)
            || dimensions.size() != fact.dimensions.size()) {
            return false;
        }
        return containsMatchingDimensionConnections(fact, DimensionElement::structurallyEquals);
    }
}
