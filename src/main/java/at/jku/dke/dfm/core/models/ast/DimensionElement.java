package at.jku.dke.dfm.core.models.ast;

import at.jku.dke.dfm.core.models.enums.ElementType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A dimension and its hierarchies.
 */
public class DimensionElement extends AbstractElement {
    private final List<Hierarchy> hierarchies = new ArrayList<>();

    public DimensionElement(String name) {
        super(name, ElementType.DIMENSION);
    }

    public List<Hierarchy> getHierarchies() {
        return hierarchies;
    }

    @Override
    public boolean structurallyEquals(AbstractElement other) {
        if (!(other instanceof DimensionElement dimension)
            || hierarchies.size() != dimension.hierarchies.size()) {
            return false;
        }
        var left = hierarchies.stream().sorted(Comparator.comparing(Hierarchy::getName)).toList();
        var right = dimension.hierarchies.stream().sorted(Comparator.comparing(Hierarchy::getName)).toList();
        for (int i = 0; i < left.size(); i++) {
            if (!left.get(i).structurallyEquals(right.get(i))) {
                return false;
            }
        }
        return true;
    }

    public boolean containsAllHierarchies(DimensionElement other) {
        if (other == null) {
            return false;
        }
        return other.hierarchies.stream()
            .allMatch(candidate -> hierarchies.stream().anyMatch(current -> current.containsAllLevels(candidate)));
    }
}
