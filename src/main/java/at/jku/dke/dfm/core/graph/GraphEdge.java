package at.jku.dke.dfm.core.graph;

import at.jku.dke.dfm.core.models.enums.ConnectionType;

/**
 * A directed edge of a {@link DfmGraph}.
 *
 * @param source         The id of the source node.
 * @param target         The id of the target node.
 * @param connectionType The connection type.
 * @param optional       Whether the connection is optional.
 */
public record GraphEdge(String source, String target, ConnectionType connectionType, boolean optional) {
}
