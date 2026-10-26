package at.jku.dke.dfm.core.graph;

import java.util.List;

/**
 * A node of a {@link DfmGraph}.
 *
 * @param id       The unique node id. Elements with the same name share one node, so the id equals the name.
 * @param label    The text to display.
 * @param kind     The node kind.
 * @param optional Whether the level is optional.
 * @param details  Additional lines to display, i.e. the measures of a fact.
 */
public record GraphNode(String id, String label, NodeKind kind, boolean optional, List<String> details) {
    public GraphNode {
        details = List.copyOf(details);
    }
}
