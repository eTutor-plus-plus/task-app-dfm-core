package at.jku.dke.dfm.core.graph;

import java.util.List;

/**
 * A layout-independent graph of a parsed DFM.
 *
 * @param nodes The nodes.
 * @param edges The edges.
 */
public record DfmGraph(List<GraphNode> nodes, List<GraphEdge> edges) {
    public DfmGraph {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }
}
