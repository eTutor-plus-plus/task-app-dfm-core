package at.jku.dke.dfm.core.models.ast;

import at.jku.dke.dfm.core.parser.DfmParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HierarchyDefinitionElementTest {
    private static HierarchyDefinitionElement parse(String source) {
        return (HierarchyDefinitionElement) new DfmParser().parse(source).get(0);
    }

    @Test
    void definitionsAreComparedIndependentlyOfChainOrder() {
        assertTrue(parse("hierarchy h { a - b; a - c; };").structurallyEquals(parse("hierarchy h { a - c; a - b; };")));
    }

    @Test
    void definitionsDifferInNameChainsAndMarkers() {
        var definition = parse("hierarchy h { a - b; };");

        assertFalse(definition.structurallyEquals(parse("hierarchy g { a - b; };")));
        assertFalse(definition.structurallyEquals(parse("hierarchy h { a = b; };")));
        assertFalse(definition.structurallyEquals(parse("hierarchy h { a - b; a - c; };")));
        assertFalse(parse("hierarchy v { a - {cross} w; a - c - {cross} w; }; hierarchy w { b; };")
            .structurallyEquals(parse("hierarchy v { a - {shared} w; a - c - {shared} w; }; hierarchy w { b; };")));
    }

    @Test
    void containsAllHierarchiesRequiresMatchingChains() {
        var complete = parse("hierarchy h { a - b; a - c; };");

        assertTrue(complete.containsAllHierarchies(parse("hierarchy h { a - b; };")));
        assertFalse(parse("hierarchy h { a - b; };").containsAllHierarchies(complete));
        assertFalse(complete.containsAllHierarchies(null));
    }
}
