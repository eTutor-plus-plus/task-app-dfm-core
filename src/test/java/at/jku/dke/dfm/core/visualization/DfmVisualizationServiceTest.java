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
    void rendersConnectionLabelsAsEdgeText() {
        var svg = render("fact Sales { profit; }; dimension D { a -[a_to_b]- b; }; Sales - D;");

        assertTrue(svg.contains("class=\"edge-label\""));
        assertTrue(svg.contains(">a_to_b</text>"));
    }

    @Test
    void edgeLabelLiesOnTheEdgeButKeepsItsMiddleFree() {
        var pattern = java.util.regex.Pattern.compile("<circle cx=\"([\\d.\\-]+)\" cy=\"([\\d.\\-]+)\"");
        for (var source : List.of(
            "fact S { m; }; dimension D { a (-[longerlabel]-) b; }; S - D;",
            "fact S { m; }; dimension D { a (-[x]-) b; a - c; a - d; a - e; }; S - D;",
            "fact S { m; }; dimension D { a (-[described_connection]-) b - c - d; }; S - D;")) {
            var svg = render(source);
            var circles = pattern.matcher(svg).results()
                .map(match -> new double[]{Double.parseDouble(match.group(1)), Double.parseDouble(match.group(2))})
                .toList();
            var text = java.util.regex.Pattern.compile(
                "class=\"edge-label\" text-anchor=\"middle\" x=\"([\\d.\\-]+)\" y=\"([\\d.\\-]+)\">([^<]*)<")
                .matcher(svg);
            assertTrue(text.find());
            double labelX = Double.parseDouble(text.group(1));
            double labelY = Double.parseDouble(text.group(2)) - 4;
            double halfWidth = text.group(3).length() * 7 / 2;
            var from = circles.get(0);
            var to = circles.get(1);
            double length = Math.hypot(to[0] - from[0], to[1] - from[1]);
            double normalX = -(to[1] - from[1]) / length;
            double normalY = (to[0] - from[0]) / length;
            double middleX = (from[0] + to[0]) / 2;
            double middleY = (from[1] + to[1]) / 2;

            for (int i = -5; i <= 5; i++) {
                assertFalse(insideLabel(middleX + normalX * i, middleY + normalY * i, labelX, labelY, halfWidth),
                    source);
            }
            assertFalse(insideLabel(middleX, middleY, labelX, labelY, halfWidth), source);
            double distanceToLine = Math.abs((labelX - from[0]) * normalX + (labelY - from[1]) * normalY);
            boolean onEdge = distanceToLine < 1;
            if (!onEdge) {
                // too short for the label: it has to stay clear of the whole edge
                for (int i = 0; i <= 100; i++) {
                    double t = i / 100.0;
                    assertFalse(insideLabel(from[0] + (to[0] - from[0]) * t, from[1] + (to[1] - from[1]) * t,
                        labelX, labelY, halfWidth), source);
                }
            }
            if (source.contains("longerlabel")) {
                assertTrue(onEdge, source + " label is not on the edge: " + distanceToLine);
            }
        }
    }

    private static boolean insideLabel(double x, double y, double labelX, double labelY, double halfWidth) {
        return Math.abs(x - labelX) <= halfWidth && Math.abs(y - labelY) <= 7;
    }

    @Test
    void edgeLabelIsCenteredOnPlainEdges() {
        var circles = java.util.regex.Pattern.compile("<circle cx=\"([\\d.\\-]+)\" cy=\"([\\d.\\-]+)\"")
            .matcher(render("fact S { m; }; dimension D { a -[plain]- b; }; S - D;"))
            .results().map(match -> new double[]{Double.parseDouble(match.group(1)),
                Double.parseDouble(match.group(2))}).toList();
        var svg = render("fact S { m; }; dimension D { a -[plain]- b; }; S - D;");
        var text = java.util.regex.Pattern.compile(
            "class=\"edge-label\" text-anchor=\"middle\" x=\"([\\d.\\-]+)\" y=\"([\\d.\\-]+)\"").matcher(svg);
        assertTrue(text.find());

        assertEquals((circles.get(0)[0] + circles.get(1)[0]) / 2, Double.parseDouble(text.group(1)), 0.2);
        assertEquals((circles.get(0)[1] + circles.get(1)[1]) / 2 + 4, Double.parseDouble(text.group(2)), 0.2);
    }

    @Test
    void factEdgeLabelStaysOutsideTheFactBox() {
        var svg = render("fact S { m; }; dimension D { a - b; }; S -[uses]- D;");
        var box = java.util.regex.Pattern.compile(
            "<rect x=\"([\\d.\\-]+)\" y=\"([\\d.\\-]+)\" width=\"([\\d.\\-]+)\" height=\"([\\d.\\-]+)\"").matcher(svg);
        var text = java.util.regex.Pattern.compile(
            "class=\"edge-label\" text-anchor=\"middle\" x=\"([\\d.\\-]+)\" y=\"([\\d.\\-]+)\"").matcher(svg);
        assertTrue(box.find());
        assertTrue(text.find());

        double x = Double.parseDouble(text.group(1));
        double y = Double.parseDouble(text.group(2)) - 4;
        double halfWidth = "uses".length() * 7 / 2.0;
        double left = Double.parseDouble(box.group(1));
        double top = Double.parseDouble(box.group(2));
        double right = left + Double.parseDouble(box.group(3));
        double bottom = top + Double.parseDouble(box.group(4));
        boolean overlaps = x + halfWidth > left && x - halfWidth < right && y + 7 > top && y - 7 < bottom;
        assertFalse(overlaps);
    }

    @Test
    void shortEdgesPlaceTheLabelBesideTheEdge() {
        var svg = render("fact S { m; }; dimension D { a (-[a_rather_long_description_text]-) b; }; S - D;");

        assertTrue(svg.contains(">a_rather_long_description_text</text>"));
    }

    @Test
    void rendersNoEdgeLabelsWithoutDescriptions() {
        var svg = render("fact Sales { profit; }; dimension D { a - b; }; Sales - D;");

        assertFalse(svg.contains("class=\"edge-label\" "));
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
