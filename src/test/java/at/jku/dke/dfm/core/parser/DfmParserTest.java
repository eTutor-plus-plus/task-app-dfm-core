package at.jku.dke.dfm.core.parser;

import at.jku.dke.dfm.core.models.ast.DimensionElement;
import at.jku.dke.dfm.core.models.ast.FactElement;
import at.jku.dke.dfm.core.models.enums.ConnectionType;
import at.jku.dke.dfm.core.models.enums.LevelType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DfmParserTest {
    private static final String MODEL = """
        fact Sales { profit; {descriptive} accountant; };
        dimension ProductDim { product - category - family; product - {descriptive} weight; };
        Sales - ProductDim;
        """;

    @Test
    void parsesFactsDimensionsAndConnections() {
        var elements = new DfmParser().parse(MODEL);

        var fact = (FactElement) elements.get(0);
        assertEquals("Sales", fact.getName());
        assertEquals(1, fact.getMeasures().size());
        assertEquals(1, fact.getDescriptives().size());
        assertEquals(1, fact.getDimensions().size());
        var hierarchy = ((DimensionElement) fact.getDimensions().get(0)).getHierarchies().get(0);
        assertEquals(ConnectionType.SIMPLE, hierarchy.getHead().getConnectionType());
        assertEquals(LevelType.LEVEL, hierarchy.getHead().getLevelType());
    }

    @Test
    void rejectsUnknownConnectionTarget() {
        assertThrows(DfmParseException.class, () -> new DfmParser().parse("fact Sales { profit; }; Sales - Missing;"));
    }

    @Test
    void reportsLineAndColumnForSyntaxErrors() {
        var exception = assertThrows(DfmParseException.class,
            () -> new DfmParser().parse("fact Sales {\n profit;"));

        assertEquals(2, exception.getLine());
        assertEquals(9, exception.getColumn());
    }
}
