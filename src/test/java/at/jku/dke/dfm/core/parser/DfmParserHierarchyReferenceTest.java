package at.jku.dke.dfm.core.parser;

import at.jku.dke.dfm.core.models.ast.DimensionElement;
import at.jku.dke.dfm.core.models.ast.FactElement;
import at.jku.dke.dfm.core.models.ast.HierarchyDefinitionElement;
import at.jku.dke.dfm.core.models.enums.ConnectionType;
import at.jku.dke.dfm.core.models.enums.ElementType;
import at.jku.dke.dfm.core.models.enums.LevelType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DfmParserHierarchyReferenceTest {
    private static final String MODEL = """
        fact Sales { profit; };

        hierarchy VAT { VAT - vatClass; };
        hierarchy city {
            city = region;
            city - country - continent;
            city - country - {cross} VAT;
            city - {descriptive} zipCode;
            city - {descriptive} population;
        };

        dimension city { {shared} city; };
        dimension customer { customer -[customerCity]- {shared} city; };
        dimension product { product - category - {cross} VAT; };

        Sales -[storeCity]- city;
        Sales - customer;
        Sales - product;
        """;

    @Test
    void parsesHierarchyDeclarationsAndReferences() {
        var elements = new DfmParser().parse(MODEL);

        assertEquals(3, elements.size());
        var fact = (FactElement) elements.get(0);
        var vat = (HierarchyDefinitionElement) elements.get(1);
        var city = (HierarchyDefinitionElement) elements.get(2);
        assertEquals(ElementType.HIERARCHY_DEFINITION, city.getElementType());
        assertEquals("VAT", vat.getName());
        assertEquals(1, vat.getHierarchies().size());
        assertEquals("city", city.getName());
        assertEquals(5, city.getHierarchies().size());
        assertEquals("city", city.getHead().getName());
        assertEquals(ConnectionType.MULTIPLE, city.getHierarchies().get(0).getHead().getConnectionType());

        var crossChain = city.getHierarchies().get(2).getHead().getNextLevel().getNextLevel();
        assertEquals("VAT", crossChain.getName());
        assertEquals(LevelType.CROSS, crossChain.getLevelType());
        assertNull(crossChain.getNextLevel());

        var dimensions = fact.getDimensions();
        assertEquals(3, dimensions.size());
        var cityDimension = (DimensionElement) dimensions.get(0);
        assertEquals("storeCity", cityDimension.getFactConnectionLabel());
        assertEquals(LevelType.SHARED, cityDimension.getHierarchies().get(0).getHead().getLevelType());
        var customer = dimensions.get(1).getHierarchies().get(0).getHead();
        assertEquals("customerCity", customer.getConnectionLabel());
        assertEquals(LevelType.SHARED, customer.getNextLevel().getLevelType());
    }

    @Test
    void allowsReferencesBeforeTheDeclaration() {
        var elements = new DfmParser().parse("dimension d { x - {shared} h; }; hierarchy h { a - b; };");

        assertEquals(2, elements.size());
        assertTrue(elements.get(1) instanceof HierarchyDefinitionElement);
    }

    @Test
    void markersAreCaseInsensitive() {
        var elements = new DfmParser().parse("hierarchy h { a; }; dimension d { x - {SHARED} h; };");

        var dimension = (DimensionElement) elements.get(0);
        assertEquals(LevelType.SHARED, dimension.getHierarchies().get(0).getHead().getNextLevel().getLevelType());
    }

    @Test
    void allowsOptionalConnectionToReference() {
        var elements = new DfmParser().parse("hierarchy h { a; }; dimension d { x (-) {shared} h; };");

        var head = ((DimensionElement) elements.get(0)).getHierarchies().get(0).getHead();
        assertTrue(head.isConnectionOptional());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "dimension d { a - {shared} x; };|x; };|is not declared",
        "hierarchy h { a; }; hierarchy h { a; };|h { a; };|declared more than once",
        "hierarchy h { a; }; dimension d { x - {shared} h - y; };|- y;|must be the last level",
        "hierarchy h { a; }; dimension d { x - {cross} h - y; };|- y;|must be the last level",
        "hierarchy h { a; }; dimension d { x - {shared} h; y - {cross} h; };|h; };|only be one of them",
        "hierarchy a { x - {cross} b; }; hierarchy b { y - {cross} a; };|a; };|must not refer to itself",
        "hierarchy a { x - {shared} a; };|a; };|must not refer to itself",
        "hierarchy h { a - b; c - d; };|c - d|must start with a",
        "hierarchy a { b; }; hierarchy h { {shared} a; };|{shared} a|must start with a level of its own",
        "hierarchy h { };|};|at least one chain",
        "hierarchy h { ; };|; };|Expected a chain",
        "hierarchy h { a; }; dimension d { x - ({shared} h); };|({shared}|cannot be optional"
    })
    void rejectsInvalidHierarchyReferences(String source, String at, String message) {
        var exception = assertThrows(DfmParseException.class, () -> new DfmParser().parse(source));

        assertTrue(exception.getMessage().contains(message), exception.getMessage());
        assertEquals(1, exception.getLine());
        assertEquals(source.lastIndexOf(at) + 1, exception.getColumn(), exception.getMessage());
    }

    @Test
    void reportsPositionOfUnknownHierarchyInMultilineSource() {
        var source = "dimension d {\n  x - {shared} missing;\n};";

        var exception = assertThrows(DfmParseException.class, () -> new DfmParser().parse(source));

        assertEquals(2, exception.getLine());
        assertEquals(16, exception.getColumn());
    }
}
