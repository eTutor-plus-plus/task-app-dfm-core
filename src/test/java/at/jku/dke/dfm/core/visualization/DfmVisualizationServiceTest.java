package at.jku.dke.dfm.core.visualization;

import at.jku.dke.dfm.core.parser.DfmParser;
import org.junit.jupiter.api.Test;

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
    void rendersDescriptivesOutsideTheFactNode() {
        var elements = new DfmParser().parse("fact Sales { profit; {descriptive} accountHandler; };");
        var svg = new DfmVisualizationService().render(elements);

        assertTrue(svg.contains("class=\"descriptive\""));
        assertTrue(svg.contains(">accountHandler</text>"));
        assertTrue(!svg.contains("class=\"fact-detail\" x=\"637.0\" y=\"515.0\">accountHandler"));
    }
}
