package at.jku.dke.dfm.core.visualization;

import at.jku.dke.dfm.core.models.ast.FactElement;
import at.jku.dke.dfm.core.parser.DfmParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DfmVisualizationServiceTest {
    @Test
    void rendersEscapedDfmNamesAsSvg() {
        var elements = new DfmParser().parse("fact Sales { profit; };");
        var svg = new DfmVisualizationService().render(elements);

        assertTrue(svg.startsWith("<svg "));
        assertTrue(svg.contains("Sales"));
        assertTrue(svg.endsWith("</svg>"));
    }

    @Test
    void rendersAllHierarchyLevelsAsConnectedNodes() {
        var elements = new DfmParser().parse(
            "fact Sales { profit; }; dimension Time { day - month - quarter - year; }; Sales - Time;");
        var svg = new DfmVisualizationService().render(elements);

        assertTrue(svg.contains("<circle"));
        assertTrue(svg.contains(">day</text>"));
        assertTrue(svg.contains(">month</text>"));
        assertTrue(svg.contains(">quarter</text>"));
        assertTrue(svg.contains(">year</text>"));
        assertTrue(svg.contains("class=\"edge\""));
    }

    @Test
    void rendersConvergenceWithArrowMarker() {
        var svg = render("fact Sales { profit; }; dimension D { a -> b; }; Sales - D;");

        assertTrue(svg.contains("marker-end=\"url(#arrow)\""));
    }

    @Test
    void rendersMultipleConnectionAsTwoLines() {
        var simple = render("fact Sales { profit; }; dimension D { a - b; }; Sales - D;");
        var multiple = render("fact Sales { profit; }; dimension D { a = b; }; Sales - D;");

        assertEquals(count(simple, "<line class=\"edge\"") + 1, count(multiple, "<line class=\"edge\""));
    }

    @Test
    void rendersOptionalLevelAndConnectionMarkers() {
        var plain = render("fact Sales { profit; }; dimension D { a - b; }; Sales - D;");
        var optionalLevel = render("fact Sales { profit; }; dimension D { a - (b); }; Sales - D;");
        var optionalConnection = render("fact Sales { profit; }; dimension D { a (-) b; }; Sales - D;");

        assertEquals(count(plain, "<line") + 1, count(optionalLevel, "<line"));
        assertEquals(count(plain, "<line") + 1, count(optionalConnection, "<line"));
    }

    @Test
    void escapesSpecialCharactersInLabels() {
        var fact = new FactElement("A&B<\"'>");
        fact.getMeasures().add("m&m");
        var svg = new DfmVisualizationService().render(List.of(fact));

        assertTrue(svg.contains("A&amp;B&lt;&quot;&apos;&gt;"));
        assertTrue(svg.contains(">m&amp;m</text>"));
        assertFalse(svg.contains("A&B"));
    }

    @Test
    void rendersEmptyModelAsValidSvg() {
        var svg = new DfmVisualizationService().render(List.of());

        assertTrue(svg.startsWith("<svg "));
        assertTrue(svg.endsWith("</svg>"));
    }

    @Test
    void rendersUnconnectedDimensions() {
        var svg = render("dimension D { a - b; };");

        assertTrue(svg.contains(">a</text>"));
        assertTrue(svg.contains(">b</text>"));
    }

    @Test
    void rendersOnlyFirstHierarchyHeadConnectedToFact() {
        var svg = render("fact Sales { profit; }; dimension D { a - b; c - d; }; Sales - D;");
        var singleHierarchy = render("fact Sales { profit; }; dimension D { a - b; }; Sales - D;");

        // the only additional edge is c - d; no edge from the fact to c
        assertEquals(count(singleHierarchy, "<line class=\"edge\"") + 1, count(svg, "<line class=\"edge\""));
    }

    @Test
    void rendersDeterministicOutput() {
        var source = "fact Sales { profit; }; dimension D { a - b - c; }; Sales - D;";

        assertEquals(render(source), render(source));
    }

    private static String render(String source) {
        return new DfmVisualizationService().render(new DfmParser().parse(source));
    }

    private static long count(String text, String token) {
        return text.split(java.util.regex.Pattern.quote(token), -1).length - 1;
    }

    @Test
    void rendersDescriptivesOutsideTheFactNode() {
        var elements = new DfmParser().parse("fact Sales { profit; {descriptive} accountHandler; };");
        var svg = new DfmVisualizationService().render(elements);

        assertTrue(svg.contains("class=\"descriptive\""));
        assertTrue(svg.contains(">accountHandler</text>"));
        assertTrue(!svg.contains("class=\"fact-detail\" x=\"637.0\" y=\"515.0\">accountHandler"));
    }
}
