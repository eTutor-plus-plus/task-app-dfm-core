package at.jku.dke.dfm.core.graph;

import at.jku.dke.dfm.core.models.enums.ConnectionType;

/**
 * A directed edge of a {@link DfmGraph}.
 *
 * @param source         The id of the source node.
 * @param target         The id of the target node.
 * @param connectionType The connection type.
 * @param optional       Whether the connection is optional.
 * @param label          The description of the connection, or {@code null} if it has none.
 */
public record GraphEdge(String source, String target, ConnectionType connectionType, boolean optional,
                        String label) {
    public GraphEdge(String source, String target, ConnectionType connectionType, boolean optional) {
        this(source, target, connectionType, optional, null);
    }
}
