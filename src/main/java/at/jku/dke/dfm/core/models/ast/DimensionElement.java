package at.jku.dke.dfm.core.models.ast;

import at.jku.dke.dfm.core.models.enums.ElementType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A dimension and its hierarchies.
 */
public class DimensionElement extends AbstractElement {
    private final List<Hierarchy> hierarchies = new ArrayList<>();
    private String factConnectionLabel;

    public DimensionElement(String name) {
        super(name, ElementType.DIMENSION);
    }

    public List<Hierarchy> getHierarchies() {
        return hierarchies;
    }

    /**
     * The description of the connection from the fact to this dimension, or {@code null} if there is none.
     * It belongs to the connection and is therefore not part of {@link #structurallyEquals(AbstractElement)};
     * {@link FactElement#structurallyEquals(AbstractElement)} compares it.
     */
    public String getFactConnectionLabel() {
        return factConnectionLabel;
    }

    public void setFactConnectionLabel(String factConnectionLabel) {
        this.factConnectionLabel = factConnectionLabel;
    }

    public boolean hasSameFactConnectionLabel(DimensionElement other) {
        return other != null && Objects.equals(factConnectionLabel, other.factConnectionLabel);
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
