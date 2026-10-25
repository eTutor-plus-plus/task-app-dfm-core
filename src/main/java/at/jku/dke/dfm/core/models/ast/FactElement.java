package at.jku.dke.dfm.core.models.ast;

import at.jku.dke.dfm.core.models.enums.ElementType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

    @Override
    public boolean structurallyEquals(AbstractElement other) {
        if (!(other instanceof FactElement fact)
            || !structurallyEqualsWithoutDimensions(fact)
            || dimensions.size() != fact.dimensions.size()) {
            return false;
        }
        var left = dimensions.stream().sorted(Comparator.comparing(DimensionElement::getName)).toList();
        var right = fact.dimensions.stream().sorted(Comparator.comparing(DimensionElement::getName)).toList();
        for (int i = 0; i < left.size(); i++) {
            if (!left.get(i).structurallyEquals(right.get(i))) {
                return false;
            }
        }
        return true;
    }
}
