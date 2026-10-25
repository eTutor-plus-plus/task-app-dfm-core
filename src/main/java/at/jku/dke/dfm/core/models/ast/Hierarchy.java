package at.jku.dke.dfm.core.models.ast;

import at.jku.dke.dfm.core.models.enums.ElementType;

/**
 * A named hierarchy containing an ordered chain of levels.
 */
public class Hierarchy extends AbstractElement {
    private Level head;

    public Hierarchy(String name) {
        super(name, ElementType.HIERARCHY);
    }

    public Level getHead() {
        return head;
    }

    public void setHead(Level head) {
        this.head = head;
    }

    @Override
    public boolean structurallyEquals(AbstractElement other) {
        return other instanceof Hierarchy hierarchy
            && (head == null ? hierarchy.head == null : head.structurallyEquals(hierarchy.head));
    }

    public boolean containsAllLevels(Hierarchy other) {
        Level current = head;
        Level candidate = other == null ? null : other.head;
        while (current != null && candidate != null) {
            if (!current.structurallyEquals(candidate)) {
                return false;
            }
            current = current.getNextLevel();
            candidate = candidate.getNextLevel();
        }
        return current == null && candidate == null;
    }
}
