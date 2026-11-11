package at.jku.dke.dfm.core.models.ast;

import at.jku.dke.dfm.core.models.enums.ElementType;

import java.util.ArrayList;
import java.util.List;

/**
 * A declared hierarchy ({@code hierarchy Name { chain; chain; };}) that dimensions and other hierarchies can refer
 * to with a {@code {shared}} or {@code {cross}} level.
 * <p>
 * Every chain starts with the head of the first chain, which is the entry level of the hierarchy.
 */
public class HierarchyDefinitionElement extends AbstractElement {
    private final List<Hierarchy> hierarchies = new ArrayList<>();

    public HierarchyDefinitionElement(String name) {
        super(name, ElementType.HIERARCHY_DEFINITION);
    }

    public List<Hierarchy> getHierarchies() {
        return hierarchies;
    }

    /**
     * The entry level of the hierarchy, or {@code null} if it has no chains.
     */
    public Level getHead() {
        return hierarchies.isEmpty() ? null : hierarchies.get(0).getHead();
    }

    /**
     * Compares the name and the chains; the order of the chains is irrelevant.
     */
    @Override
    public boolean structurallyEquals(AbstractElement other) {
        if (!(other instanceof HierarchyDefinitionElement definition)
            || !getName().equals(definition.getName())
            || hierarchies.size() != definition.hierarchies.size()) {
            return false;
        }
        var remaining = new ArrayList<>(definition.hierarchies);
        for (var hierarchy : hierarchies) {
            var match = remaining.stream().filter(hierarchy::structurallyEquals).findFirst();
            if (match.isEmpty()) {
                return false;
            }
            remaining.remove(match.get());
        }
        return true;
    }

    public boolean containsAllHierarchies(HierarchyDefinitionElement other) {
        if (other == null) {
            return false;
        }
        return other.hierarchies.stream()
            .allMatch(candidate -> hierarchies.stream().anyMatch(current -> current.containsAllLevels(candidate)));
    }
}
