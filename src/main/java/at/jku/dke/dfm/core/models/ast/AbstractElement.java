package at.jku.dke.dfm.core.models.ast;

import at.jku.dke.dfm.core.models.enums.ElementType;

/**
 * Base type for elements in a dimensional fact model.
 */
public abstract class AbstractElement {
    private final String name;
    private final ElementType elementType;

    protected AbstractElement(String name, ElementType elementType) {
        this.name = name;
        this.elementType = elementType;
    }

    public String getName() {
        return name;
    }

    public ElementType getElementType() {
        return elementType;
    }

    public abstract boolean structurallyEquals(AbstractElement other);
}
