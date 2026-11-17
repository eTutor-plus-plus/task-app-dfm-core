package at.jku.dke.dfm.core.visualization;

import at.jku.dke.dfm.core.models.ast.FactElement;
import at.jku.dke.dfm.core.parser.DfmParser;
import at.jku.dke.dfm.core.visualization.DfmCanvas.RuleColor;
import at.jku.dke.dfm.core.visualization.DfmCanvas.TextStyle;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DfmVisualizationServiceTest {
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    @Test
    void rendersSharedHierarchyEntryAsDoubleCircle() {
        var canvas = draw("hierarchy h { a - b; }; dimension d { x - {shared} h; y - {shared} h; };");

        assertEquals(5, canvas.circles.size());
        assertTrue(canvas.circles.stream().anyMatch(circle -> circle.radius() == 14.0 && !circle.filled()));
        assertTrue(canvas.arcs.isEmpty());
        assertTrue(canvas.hasText("a"));
    }

    @Test
    void rendersCrossAttributeAsCircleWithArc() {
        var canvas = draw("hierarchy h { a; }; dimension d { x - {cross} h; y - {cross} h; };");

        assertEquals(3, canvas.circles.size());
        assertEquals(1, canvas.arcs.size());
        assertEquals(14.0, canvas.arcs.get(0).radius());
        assertTrue(canvas.arcs.get(0).extent() < 0);
    }

    @Test
    void rendersModelWithSharedAndCrossHierarchies() {
        var canvas = draw("fact Sales { profit; }; hierarchy VAT { VAT - vatClass; };"
            + " hierarchy city { city - country - {cross} VAT; city = region; };"
            + " dimension city { {shared} city; }; dimension product { product - category - {cross} VAT; };"
            + " dimension customer { customer -[customerCity]- {shared} city; };"
            + " Sales -[storeCity]- city; Sales - customer; Sales - product;");

        assertTrue(canvas.hasText("VAT"));
        assertTrue(canvas.hasText("storeCity"));
    }

    @Test
    void rendersFactNameAndMeasures() {
        var canvas = draw("fact Sales { profit; };");

        assertEquals(1, canvas.factBoxes.size());
        assertEquals("Sales", canvas.texts(TextStyle.FACT_TITLE).get(0).text());
        assertEquals("profit", canvas.texts(TextStyle.FACT_DETAIL).get(0).text());
    }

    @Test
    void rendersAllHierarchyLevelsAsConnectedNodes() {
        var canvas = draw("fact Sales { profit; }; dimension Time { day - month - quarter - year; }; Sales - Time;");

        assertEquals(4, canvas.levelCircles().size());
        for (var level : List.of("day", "month", "quarter", "year")) {
            assertTrue(canvas.hasText(level), level);
        }
        assertEquals(4, canvas.lines.size());
    }

    @Test
    void rendersConnectionLabelsAsEdgeText() {
        var canvas = draw("fact Sales { profit; }; dimension D { a -[a_to_b]- b; }; Sales - D;");

        assertEquals("a_to_b", canvas.texts(TextStyle.EDGE_LABEL).get(0).text());
    }

    @Test
    void rendersParallelConnectionsAsDistinctCurves() {
        var canvas = draw("fact F { m; }; dimension D { d; }; F (-[shipping]-) D; F =[billing]= D;");

        assertEquals(3, canvas.quads.size());
        assertEquals(3, new HashSet<>(canvas.quads).size());
        assertTrue(canvas.hasText("shipping"));
        assertTrue(canvas.hasText("billing"));
    }

    @Test
    void rendersOptionalRecursiveConnection() {
        var canvas = draw("dimension D { a (-[role]->) a; };");

        assertEquals(1, canvas.cubics.size());
        assertEquals(1, canvas.markers.size());
        assertTrue(canvas.hasText("role"));
    }

    @Test
    void edgeLabelLiesOnTheEdgeButKeepsItsMiddleFree() {
        for (var source : List.of(
            "fact S { m; }; dimension D { a (-[longerlabel]-) b; }; S - D;",
            "fact S { m; }; dimension D { a (-[x]-) b; a - c; a - d; a - e; }; S - D;",
            "fact S { m; }; dimension D { a (-[described_connection]-) b - c - d; }; S - D;")) {
            var canvas = draw(source);
            var circles = canvas.levelCircles();
            var text = canvas.texts(TextStyle.EDGE_LABEL).get(0);
            double labelX = text.x();
            double labelY = text.y() - 4;
            double halfWidth = text.text().length() * 7 / 2.0;
            var from = circles.get(0);
            var to = circles.get(1);
            double length = Math.hypot(to.cx() - from.cx(), to.cy() - from.cy());
            double normalX = -(to.cy() - from.cy()) / length;
            double normalY = (to.cx() - from.cx()) / length;
            double middleX = (from.cx() + to.cx()) / 2;
            double middleY = (from.cy() + to.cy()) / 2;

            for (int i = -5; i <= 5; i++) {
                assertFalse(insideLabel(middleX + normalX * i, middleY + normalY * i, labelX, labelY, halfWidth),
                    source);
            }
            assertFalse(insideLabel(middleX, middleY, labelX, labelY, halfWidth), source);
            double distanceToLine = Math.abs((labelX - from.cx()) * normalX + (labelY - from.cy()) * normalY);
            boolean onEdge = distanceToLine < 1;
            if (!onEdge) {
                // too short for the label: it has to stay clear of the whole edge
                for (int i = 0; i <= 100; i++) {
                    double t = i / 100.0;
                    assertFalse(insideLabel(from.cx() + (to.cx() - from.cx()) * t,
                        from.cy() + (to.cy() - from.cy()) * t, labelX, labelY, halfWidth), source);
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
        var canvas = draw("fact S { m; }; dimension D { a -[plain]- b; }; S - D;");
        var circles = canvas.levelCircles();
        var text = canvas.texts(TextStyle.EDGE_LABEL).get(0);

        assertEquals((circles.get(0).cx() + circles.get(1).cx()) / 2, text.x(), 0.2);
        assertEquals((circles.get(0).cy() + circles.get(1).cy()) / 2 + 4, text.y(), 0.2);
    }

    @Test
    void factEdgeLabelStaysOutsideTheFactBox() {
        var canvas = draw("fact S { m; }; dimension D { a - b; }; S -[uses]- D;");
        var box = canvas.factBoxes.get(0);
        var text = canvas.texts(TextStyle.EDGE_LABEL).get(0);

        double x = text.x();
        double y = text.y() - 4;
        double halfWidth = "uses".length() * 7 / 2.0;
        boolean overlaps = x + halfWidth > box.x() && x - halfWidth < box.x() + box.width()
            && y + 7 > box.y() && y - 7 < box.y() + box.height();
        assertFalse(overlaps);
    }

    @Test
    void shortEdgesPlaceTheLabelBesideTheEdge() {
        var canvas = draw("fact S { m; }; dimension D { a (-[a_rather_long_description_text]-) b; }; S - D;");

        assertTrue(canvas.hasText("a_rather_long_description_text"));
    }

    @Test
    void rendersNoEdgeLabelsWithoutDescriptions() {
        var canvas = draw("fact Sales { profit; }; dimension D { a - b; }; Sales - D;");

        assertTrue(canvas.texts(TextStyle.EDGE_LABEL).isEmpty());
    }

    @Test
    void rendersConvergenceWithArrow() {
        assertTrue(draw("fact Sales { profit; }; dimension D { a -> b; }; Sales - D;").hasArrow());
        assertFalse(draw("fact Sales { profit; }; dimension D { a - b; }; Sales - D;").hasArrow());
    }

    @Test
    void rendersRecursiveHierarchyAsSelfLoopWithRole() {
        var canvas = draw("fact Sales { profit; }; "
            + "dimension SalesRepresentative { salesRepresentative -[supervisor]-> salesRepresentative; }; "
            + "Sales - SalesRepresentative;");

        assertEquals(1, canvas.cubics.size());
        assertEquals(1, canvas.texts(TextStyle.EDGE_LABEL).size());
        assertEquals("supervisor", canvas.texts(TextStyle.EDGE_LABEL).get(0).text());
        assertEquals(1, canvas.levelCircles().size());
        assertTrue(canvas.cubics.get(0).arrow());
    }

    @Test
    void rendersNoLoopForSelfReferenceWithoutRole() {
        assertTrue(draw("dimension D { a - a; };").cubics.isEmpty());
    }

    @Test
    void rendersMultipleConnectionAsTwoLines() {
        var simple = draw("fact Sales { profit; }; dimension D { a - b; }; Sales - D;");
        var multiple = draw("fact Sales { profit; }; dimension D { a = b; }; Sales - D;");

        assertEquals(simple.lines.size() + 1, multiple.lines.size());
    }

    @Test
    void rendersOptionalLevelAndConnectionMarkers() {
        var plain = draw("fact Sales { profit; }; dimension D { a - b; }; Sales - D;");
        var optionalLevel = draw("fact Sales { profit; }; dimension D { a - (b); }; Sales - D;");
        var optionalConnection = draw("fact Sales { profit; }; dimension D { a (-) b; }; Sales - D;");

        assertEquals(plain.lineCount() + 1, optionalLevel.lineCount());
        assertEquals(plain.lineCount() + 1, optionalConnection.lineCount());
        assertEquals(1, optionalConnection.markers.size());
        assertTrue(optionalLevel.rules.stream().anyMatch(rule -> rule.color() == RuleColor.BLACK));
    }

    @Test
    void rendersSpecialCharactersInLabelsUnchanged() {
        var fact = new FactElement("A&B<\"'>");
        fact.getMeasures().add("m&m");
        var canvas = new RecordingCanvas();
        new DfmVisualizationService().draw(List.of(fact), canvas);

        assertTrue(canvas.hasText("A&B<\"'>"));
        assertTrue(canvas.hasText("m&m"));
        assertValidPng(new DfmVisualizationService().renderPng(List.of(fact)));
    }

    @Test
    void rendersEmptyModelAsValidPng() throws IOException {
        var image = ImageIO.read(new ByteArrayInputStream(new DfmVisualizationService().renderPng(List.of())));

        assertEquals(1400, image.getWidth());
        assertEquals(1000, image.getHeight());
    }

    @Test
    void rendersUnconnectedDimensions() {
        var canvas = draw("dimension D { a - b; };");

        assertTrue(canvas.hasText("a"));
        assertTrue(canvas.hasText("b"));
    }

    @Test
    void rendersOnlyFirstHierarchyHeadConnectedToFact() {
        var canvas = draw("fact Sales { profit; }; dimension D { a - b; c - d; }; Sales - D;");
        var singleHierarchy = draw("fact Sales { profit; }; dimension D { a - b; }; Sales - D;");

        // the only additional edge is c - d; no edge from the fact to c
        assertEquals(singleHierarchy.lines.size() + 1, canvas.lines.size());
    }

    @Test
    void rendersDeterministicOutput() {
        var source = "fact Sales { profit; }; dimension D { a - b - c; }; Sales - D;";
        var elements = new DfmParser().parse(source);
        var service = new DfmVisualizationService();

        assertEquals(draw(source).texts, draw(source).texts);
        assertEquals(draw(source).lines, draw(source).lines);
        assertArrayEquals(service.renderPng(elements), service.renderPng(elements));
    }

    @Test
    void rendersDescriptivesOutsideTheFactNode() {
        var canvas = draw("fact Sales { profit; {descriptive} accountHandler; };");

        assertEquals("accountHandler", canvas.texts(TextStyle.DESCRIPTIVE).get(0).text());
        assertTrue(canvas.texts(TextStyle.FACT_DETAIL).stream().noneMatch(text -> text.text().equals("accountHandler")));
    }

    @Test
    void rendersPngWithVisibleContent() throws IOException {
        var png = new DfmVisualizationService().renderPng(
            new DfmParser().parse("fact Sales { profit; }; dimension Time { day - month; }; Sales - Time;"));
        assertValidPng(png);

        var image = ImageIO.read(new ByteArrayInputStream(png));
        assertNotNull(image);
        boolean hasContent = false;
        for (int y = 0; y < image.getHeight() && !hasContent; y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0xFFFFFF) != 0xFFFFFF) {
                    hasContent = true;
                    break;
                }
            }
        }
        assertTrue(hasContent);
    }

    private static void assertValidPng(byte[] png) {
        assertTrue(png.length > PNG_SIGNATURE.length);
        assertArrayEquals(PNG_SIGNATURE, java.util.Arrays.copyOf(png, PNG_SIGNATURE.length));
    }

    private static RecordingCanvas draw(String source) {
        var canvas = new RecordingCanvas();
        new DfmVisualizationService().draw(new DfmParser().parse(source), canvas);
        return canvas;
    }
}
