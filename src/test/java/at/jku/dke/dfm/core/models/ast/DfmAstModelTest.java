package at.jku.dke.dfm.core.models.ast;

import at.jku.dke.dfm.core.models.enums.ConnectionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DfmAstModelTest {

    @Test
    void dimensionsAreComparedIndependentlyOfHierarchyOrder() {
        var first = new DimensionElement("Date");
        var year = new Hierarchy("Calendar");
        year.setHead(new Level("Year", ConnectionType.SIMPLE));
        var month = new Hierarchy("Fiscal");
        month.setHead(new Level("Month", ConnectionType.MULTIPLE));
        first.getHierarchies().add(year);
        first.getHierarchies().add(month);

        var second = new DimensionElement("Date");
        second.getHierarchies().add(month);
        second.getHierarchies().add(year);

        assertTrue(first.structurallyEquals(second));
    }

    @Test
    void dimensionContainsAllHierarchiesRequiresMatchingLevelChains() {
        var complete = new DimensionElement("Date");
        var hierarchy = new Hierarchy("Calendar");
        var year = new Level("Year", ConnectionType.SIMPLE);
        year.setNextLevel(new Level("Month", ConnectionType.SIMPLE));
        hierarchy.setHead(year);
        complete.getHierarchies().add(hierarchy);

        var candidate = new DimensionElement("Date");
        var candidateHierarchy = new Hierarchy("Calendar");
        candidateHierarchy.setHead(new Level("Year", ConnectionType.SIMPLE));
        candidate.getHierarchies().add(candidateHierarchy);

        assertFalse(complete.containsAllHierarchies(candidate));
    }
}
