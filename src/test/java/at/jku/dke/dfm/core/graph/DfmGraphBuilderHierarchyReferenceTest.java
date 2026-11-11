package at.jku.dke.dfm.core.graph;

import at.jku.dke.dfm.core.models.enums.ConnectionType;
import at.jku.dke.dfm.core.parser.DfmParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class DfmGraphBuilderHierarchyReferenceTest {
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

    private static DfmGraph build(String source) {
        return new DfmGraphBuilder().build(new DfmParser().parse(source));
    }

    private static GraphNode node(DfmGraph graph, String label) {
        return graph.nodes().stream().filter(node -> node.label().equals(label)).findFirst().orElse(null);
    }

    private static GraphEdge edge(DfmGraph graph, String source, String target) {
        var labelsById = new java.util.HashMap<String, String>();
        graph.nodes().forEach(node -> labelsById.put(node.id(), node.label()));
        return graph.edges().stream()
            .filter(edge -> source.equals(labelsById.get(edge.source()))
                && target.equals(labelsById.get(edge.target())))
            .findFirst().orElse(null);
    }

    private static long incoming(DfmGraph graph, String label) {
        var id = node(graph, label).id();
        return graph.edges().stream().filter(edge -> edge.target().equals(id)).count();
    }

    @Test
    void createsOneNodePerSharedAndCrossHierarchy() {
        var graph = build(MODEL);

        assertEquals(List.of("Sales", "customer", "product", "category", "VAT", "vatClass", "city", "region",
                "country", "continent", "zipCode", "population"),
            graph.nodes().stream().map(GraphNode::label).toList());
        assertEquals(NodeKind.SHARED, node(graph, "city").kind());
        assertEquals(NodeKind.CROSS, node(graph, "VAT").kind());
        assertEquals(NodeKind.LEVEL, node(graph, "region").kind());
        assertEquals(NodeKind.DESCRIPTIVE, node(graph, "zipCode").kind());
    }

    @Test
    void usesIdsIndependentOfTheReferencingDimensions() {
        var graph = build(MODEL);

        assertEquals("hierarchy-city-level-city", node(graph, "city").id());
        assertEquals("hierarchy-VAT-level-VAT", node(graph, "VAT").id());
    }

    @Test
    void connectsSeveralSourcesToTheSharedNodeWithTheirLabels() {
        var graph = build(MODEL);

        assertEquals(2, incoming(graph, "city"));
        assertEquals("storeCity", edge(graph, "Sales", "city").label());
        assertEquals("customerCity", edge(graph, "customer", "city").label());
        assertNull(edge(graph, "Sales", "customer").label());
    }

    @Test
    void connectsCrossAttributeToLevelsOfDifferentHierarchies() {
        var graph = build(MODEL);

        assertEquals(2, incoming(graph, "VAT"));
        assertNotNull(edge(graph, "country", "VAT"));
        assertNotNull(edge(graph, "category", "VAT"));
        assertNotNull(edge(graph, "VAT", "vatClass"));
    }

    @Test
    void buildsTheEdgesOfTheDeclaredHierarchy() {
        var graph = build(MODEL);

        assertEquals(ConnectionType.MULTIPLE, edge(graph, "city", "region").connectionType());
        assertEquals(ConnectionType.SIMPLE, edge(graph, "city", "country").connectionType());
        assertNotNull(edge(graph, "country", "continent"));
        assertNotNull(edge(graph, "city", "zipCode"));
        assertNotNull(edge(graph, "city", "population"));
        assertEquals(13, graph.edges().size());
    }

    @Test
    void sameLevelNameInDimensionAndHierarchyStaysSeparate() {
        var graph = build("hierarchy h { a - b; }; dimension d { b - {shared} h; };");

        assertEquals(List.of("b", "a", "b"), graph.nodes().stream().map(GraphNode::label).toList());
        assertEquals(2, graph.edges().size());
    }

    @Test
    void keepsLabelsAndOptionalMarkerOfTheConnectionToTheReference() {
        var graph = build("hierarchy h { a; }; dimension d { x (-[role]-) {shared} h; };");

        var edge = edge(graph, "x", "a");
        assertEquals("role", edge.label());
        assertEquals(true, edge.optional());
    }

    @Test
    void rendersUnreferencedHierarchyAsPlainLevels() {
        var graph = build("hierarchy h { a - b; };");

        assertEquals(NodeKind.LEVEL, node(graph, "a").kind());
        assertEquals(1, graph.edges().size());
    }

    @Test
    void nestedReferencesEndAtTheEntryOfTheReferencedHierarchy() {
        var graph = build("hierarchy outer { o - {cross} inner; }; hierarchy inner { i - j; };"
            + " dimension d { x - {shared} outer; };");

        assertNotNull(edge(graph, "x", "o"));
        assertNotNull(edge(graph, "o", "i"));
        assertEquals(NodeKind.SHARED, node(graph, "o").kind());
        assertEquals(NodeKind.CROSS, node(graph, "i").kind());
    }
}
