package at.jku.dke.dfm.core.parser;

import at.jku.dke.dfm.core.models.ast.DimensionElement;
import at.jku.dke.dfm.core.models.ast.FactElement;
import at.jku.dke.dfm.core.models.enums.ConnectionType;
import at.jku.dke.dfm.core.models.enums.LevelType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @ParameterizedTest
    @ValueSource(strings = {
        "fact Sales { profit; }; Sales",
        "fact Sales { profit; }; Sales -",
        "fact Sales { profit; }; Sales - Time",
        "fact Sales { profit",
        "fact Sales",
        "fact",
        "dimension D { a",
        "dimension D { a -",
        "dimension D { a - b",
        "dimension D { (a",
        "dimension D { a (-",
        "dimension D { a (- b",
        "fact Sales { {descriptive",
        "fact Sales { profit; {"
    })
    void truncatedInputFailsWithParseException(String source) {
        var exception = assertThrows(DfmParseException.class, () -> new DfmParser().parse(source));

        assertTrue(exception.getLine() >= 1);
        assertTrue(exception.getColumn() >= 1);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\t"})
    void rejectsEmptyInput(String source) {
        assertThrows(DfmParseException.class, () -> new DfmParser().parse(source));
    }

    @Test
    void rejectsUnexpectedCharacters() {
        var exception = assertThrows(DfmParseException.class,
            () -> new DfmParser().parse("fact Sales { profit; };\n#"));

        assertEquals(2, exception.getLine());
        assertEquals(1, exception.getColumn());
    }

    @Test
    void rejectsUnknownConnectionTypeBetweenLevels() {
        var exception = assertThrows(DfmParseException.class,
            () -> new DfmParser().parse("dimension D { a b };"));

        assertEquals(1, exception.getLine());
        assertEquals(17, exception.getColumn());
    }

    @Test
    void rejectsConnectionWithoutDash() {
        assertThrows(DfmParseException.class,
            () -> new DfmParser().parse("fact Sales { profit; }; dimension D { a; }; Sales = D;"));
    }

    @Test
    void rejectsUnknownFactInConnection() {
        assertThrows(DfmParseException.class,
            () -> new DfmParser().parse("dimension D { a; }; Missing - D;"));
    }

    @Test
    void rejectsMissingSemicolonAfterDeclaration() {
        assertThrows(DfmParseException.class, () -> new DfmParser().parse("fact Sales { profit; }"));
    }

    @Test
    void parsesAllConnectionTypesBetweenLevels() {
        var elements = new DfmParser().parse("dimension D { a - b = c -> d; };");

        var level = ((DimensionElement) elements.get(0)).getHierarchies().get(0).getHead();
        assertEquals(ConnectionType.SIMPLE, level.getConnectionType());
        assertEquals(ConnectionType.MULTIPLE, level.getNextLevel().getConnectionType());
        assertEquals(ConnectionType.CONVERGENCE, level.getNextLevel().getNextLevel().getConnectionType());
        assertNull(level.getNextLevel().getNextLevel().getNextLevel().getConnectionType());
    }

    @Test
    void parsesOptionalLevelsAndOptionalConnections() {
        var elements = new DfmParser().parse("dimension D { a (-) (b) - c; };");

        var a = ((DimensionElement) elements.get(0)).getHierarchies().get(0).getHead();
        var b = a.getNextLevel();
        var c = b.getNextLevel();
        assertFalse(a.isOptional());
        assertTrue(a.isConnectionOptional());
        assertEquals(ConnectionType.SIMPLE, a.getConnectionType());
        assertTrue(b.isOptional());
        assertFalse(b.isConnectionOptional());
        assertFalse(c.isOptional());
    }

    @Test
    void parsesDescriptiveLevels() {
        var elements = new DfmParser().parse("dimension D { a - {descriptive} note; };");

        var note = ((DimensionElement) elements.get(0)).getHierarchies().get(0).getHead().getNextLevel();
        assertEquals("note", note.getName());
        assertEquals(LevelType.DESCRIPTIVE, note.getLevelType());
    }

    @Test
    void parsesMultipleHierarchiesPerDimension() {
        var elements = new DfmParser().parse("dimension D { a - b; a - c; };");

        assertEquals(2, ((DimensionElement) elements.get(0)).getHierarchies().size());
    }

    @Test
    void keepsUnconnectedDimensionsAndKeywordsAreCaseInsensitive() {
        var elements = new DfmParser().parse("FACT Sales { profit; }; Dimension Time { day; }; Dimension Other { x; };"
            + " Sales - Time;");

        assertEquals(2, elements.size());
        assertEquals("Sales", elements.get(0).getName());
        assertEquals(1, ((FactElement) elements.get(0)).getDimensions().size());
        assertEquals("Other", elements.get(1).getName());
    }

    @Test
    void sharedInstanceIsSafeForConcurrentUse() throws Exception {
        var parser = new DfmParser();
        var sources = List.of(
            "fact A { m1; }; dimension D1 { x - y - z; }; A - D1;",
            "fact B { m2; m3; }; dimension D2 { p = q; }; B - D2;");
        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = new ArrayList<Future<String>>();
            for (int i = 0; i < 400; i++) {
                var source = sources.get(i % 2);
                futures.add(executor.submit(() -> parser.parse(source).get(0).getName()));
            }
            for (int i = 0; i < futures.size(); i++) {
                assertEquals(i % 2 == 0 ? "A" : "B", futures.get(i).get());
            }
        }
    }

    @Test
    void parserInstanceCanBeReusedAfterFailure() {
        var parser = new DfmParser();
        assertThrows(DfmParseException.class, () -> parser.parse("fact Sales"));

        assertEquals(1, parser.parse("fact Sales { profit; };").size());
    }

    @Test
    void reportsLineAndColumnForSyntaxErrors() {
        var exception = assertThrows(DfmParseException.class,
            () -> new DfmParser().parse("fact Sales {\n profit;"));

        assertEquals(2, exception.getLine());
        assertEquals(9, exception.getColumn());
    }
}
