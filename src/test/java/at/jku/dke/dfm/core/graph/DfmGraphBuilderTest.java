package at.jku.dke.dfm.core.graph;

import at.jku.dke.dfm.core.models.enums.ConnectionType;
import at.jku.dke.dfm.core.parser.DfmParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DfmGraphBuilderTest {
    private static DfmGraph build(String source) {
        return new DfmGraphBuilder().build(new DfmParser().parse(source));
    }

    private static GraphEdge edge(DfmGraph graph, String source, String target) {
        var labelsById = graph.nodes().stream()
            .collect(java.util.stream.Collectors.toMap(GraphNode::id, GraphNode::label));
        return graph.edges().stream()
            .filter(edge -> source.equals(labelsById.get(edge.source()))
                && target.equals(labelsById.get(edge.target())))
            .findFirst().orElse(null);
    }

    private static GraphNode node(DfmGraph graph, String label) {
        return graph.nodes().stream().filter(node -> node.label().equals(label)).findFirst().orElse(null);
    }

    @Test
    void buildsNodesAndEdgesForFactWithDimension() {
        var graph = build("fact Sales { profit; {descriptive} clerk; }; dimension Time { day - month; }; Sales - Time;");

        assertEquals(List.of("Sales", "clerk", "day", "month"),
            graph.nodes().stream().map(GraphNode::label).toList());
        assertEquals(NodeKind.FACT, node(graph, "Sales").kind());
        assertEquals(List.of("profit"), node(graph, "Sales").details());
        assertEquals(NodeKind.DESCRIPTIVE, node(graph, "clerk").kind());
        assertEquals(NodeKind.LEVEL, node(graph, "day").kind());
        assertEquals(3, graph.edges().size());
        assertEquals(ConnectionType.SIMPLE, edge(graph, "Sales", "clerk").connectionType());
        assertEquals(ConnectionType.SIMPLE, edge(graph, "Sales", "day").connectionType());
        assertEquals(ConnectionType.SIMPLE, edge(graph, "day", "month").connectionType());
    }

    @Test
    void connectsFactOnlyToHeadOfFirstHierarchy() {
        var graph = build("fact Sales { profit; }; dimension D { a - b; c - d; }; Sales - D;");

        assertTrue(edge(graph, "Sales", "a") != null);
        assertEquals(null, edge(graph, "Sales", "c"));
        assertTrue(edge(graph, "c", "d") != null);
    }

    @Test
    void sharesNodesWithSameNameAcrossHierarchies() {
        var graph = build("fact Sales { profit; }; dimension D { a - b - c; a - d; }; Sales - D;");

        assertEquals(1, graph.nodes().stream().filter(node -> node.label().equals("a")).count());
        assertEquals(List.of("Sales", "a", "b", "c", "d"),
            graph.nodes().stream().map(GraphNode::label).toList());
        assertTrue(edge(graph, "a", "b") != null);
        assertTrue(edge(graph, "a", "d") != null);
    }

    @Test
    void keepsConnectionTypesAndOptionality() {
        var graph = build("dimension D { a = b -> c (-) (d); };");

        assertEquals(ConnectionType.MULTIPLE, edge(graph, "a", "b").connectionType());
        assertEquals(ConnectionType.CONVERGENCE, edge(graph, "b", "c").connectionType());
        assertEquals(ConnectionType.SIMPLE, edge(graph, "c", "d").connectionType());
        assertTrue(edge(graph, "c", "d").optional());
        assertFalse(edge(graph, "a", "b").optional());
        assertTrue(node(graph, "d").optional());
        assertFalse(node(graph, "c").optional());
    }

    @Test
    void marksDescriptiveLevels() {
        var graph = build("dimension D { a - {descriptive} note; };");

        assertEquals(NodeKind.DESCRIPTIVE, node(graph, "note").kind());
    }

    @Test
    void includesUnconnectedDimensionsWithoutFactEdges() {
        var graph = build("fact Sales { profit; }; dimension D { a - b; };");

        assertEquals(List.of("Sales", "a", "b"), graph.nodes().stream().map(GraphNode::label).toList());
        assertEquals(1, graph.edges().size());
    }

    @Test
    void buildsGraphForMultipleFacts() {
        var graph = build("fact A { m1; }; fact B { m2; }; dimension D1 { x - y; }; dimension D2 { p - q; }; A - D1; B - D2;");

        assertEquals(List.of("A", "B", "x", "y", "p", "q"),
            graph.nodes().stream().map(GraphNode::label).toList());
        assertTrue(edge(graph, "A", "x") != null);
        assertTrue(edge(graph, "B", "p") != null);
    }

    @Test
    void keepsSameNamedLevelsSeparateAcrossDimensionsAndRetainsBothConnectionTypes() {
        var graph = build("""
            fact Purchase { quantity; };
            dimension Payment { paymentDateTime - day - month - year; };
            dimension PurchaseDateTime {
                purchaseDateTime - day - month -> year;
                purchaseDateTime - day - calendarWeek -> year;
            };
            Purchase - Payment;
            Purchase - PurchaseDateTime;
            """);

        var labelsById = graph.nodes().stream()
            .collect(java.util.stream.Collectors.toMap(GraphNode::id, GraphNode::label));
        var monthToYear = graph.edges().stream()
            .filter(edge -> "month".equals(labelsById.get(edge.source()))
                && "year".equals(labelsById.get(edge.target())))
            .toList();

        assertEquals(2, monthToYear.size());
        assertEquals(1, monthToYear.stream()
            .filter(edge -> edge.connectionType() == ConnectionType.SIMPLE).count());
        assertEquals(1, monthToYear.stream()
            .filter(edge -> edge.connectionType() == ConnectionType.CONVERGENCE).count());
        assertNotEquals(monthToYear.get(0).source(), monthToYear.get(1).source());
        assertNotEquals(monthToYear.get(0).target(), monthToYear.get(1).target());
    }

    @Test
    void ignoresEmptyModelAndSelfLinks() {
        assertTrue(new DfmGraphBuilder().build(List.of()).nodes().isEmpty());

        var graph = build("dimension D { a - a; };");
        assertEquals(1, graph.nodes().size());
        assertTrue(graph.edges().isEmpty());
    }

    @Test
    void ignoresDuplicateEdges() {
        var graph = build("dimension D { a - b; a - b; };");

        assertEquals(1, graph.edges().size());
    }
}
