package at.jku.dke.dfm.core.graph;

import at.jku.dke.dfm.core.models.enums.ConnectionType;
import at.jku.dke.dfm.core.parser.DfmParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DfmGraphBuilderTest {
    private static DfmGraph build(String source) {
        return new DfmGraphBuilder().build(new DfmParser().parse(source));
    }

    private static GraphEdge edge(DfmGraph graph, String source, String target) {
        return graph.edges().stream()
            .filter(edge -> edge.source().equals(source) && edge.target().equals(target))
            .findFirst().orElse(null);
    }

    private static GraphNode node(DfmGraph graph, String id) {
        return graph.nodes().stream().filter(node -> node.id().equals(id)).findFirst().orElse(null);
    }

    @Test
    void buildsNodesAndEdgesForFactWithDimension() {
        var graph = build("fact Sales { profit; {descriptive} clerk; }; dimension Time { day - month; }; Sales - Time;");

        assertEquals(List.of("Sales", "clerk", "day", "month"), graph.nodes().stream().map(GraphNode::id).toList());
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

        assertEquals(1, graph.nodes().stream().filter(node -> node.id().equals("a")).count());
        assertEquals(List.of("Sales", "a", "b", "c", "d"), graph.nodes().stream().map(GraphNode::id).toList());
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

        assertEquals(List.of("Sales", "a", "b"), graph.nodes().stream().map(GraphNode::id).toList());
        assertEquals(1, graph.edges().size());
    }

    @Test
    void buildsGraphForMultipleFacts() {
        var graph = build("fact A { m1; }; fact B { m2; }; dimension D1 { x - y; }; dimension D2 { p - q; }; A - D1; B - D2;");

        assertEquals(List.of("A", "B", "x", "y", "p", "q"), graph.nodes().stream().map(GraphNode::id).toList());
        assertTrue(edge(graph, "A", "x") != null);
        assertTrue(edge(graph, "B", "p") != null);
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
