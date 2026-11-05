package at.jku.dke.dfm.core.models.ast;

import at.jku.dke.dfm.core.models.enums.ConnectionType;
import at.jku.dke.dfm.core.models.enums.ElementType;

import java.util.Objects;

/**
 * A connection between a fact and a dimension.
 */
public class FactDimensionElement extends AbstractElement {
    private final String relationFromName;
    private final String relationToName;
    private ConnectionType relationType = ConnectionType.SIMPLE;
    private String label;

    public FactDimensionElement(String relationFromName, String relationToName) {
        super(relationFromName, ElementType.FACT_DIMENSION_CONNECTION);
        this.relationFromName = relationFromName;
        this.relationToName = relationToName;
    }

    public String getRelationFromName() {
        return relationFromName;
    }

    public String getRelationToName() {
        return relationToName;
    }

    public ConnectionType getRelationType() {
        return relationType;
    }

    public void setRelationType(ConnectionType relationType) {
        this.relationType = relationType;
    }

    /**
     * The description of the connection, or {@code null} if it has none.
     */
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    @Override
    public boolean structurallyEquals(AbstractElement other) {
        return other instanceof FactDimensionElement connection
            && relationFromName.equals(connection.relationFromName)
            && relationToName.equals(connection.relationToName)
            && relationType == connection.relationType
            && Objects.equals(label, connection.label);
    }
}
