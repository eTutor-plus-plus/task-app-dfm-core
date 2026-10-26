package at.jku.dke.dfm.core.graph;

import at.jku.dke.dfm.core.models.ast.AbstractElement;
import at.jku.dke.dfm.core.models.ast.DimensionElement;
import at.jku.dke.dfm.core.models.ast.FactElement;
import at.jku.dke.dfm.core.models.ast.Level;
import at.jku.dke.dfm.core.models.enums.ConnectionType;
import at.jku.dke.dfm.core.models.enums.LevelType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a {@link DfmGraph} from parsed DFM elements.
 * <p>
 * Elements with the same name share one node. A fact is connected to the head of the first hierarchy
 * of each of its dimensions; further hierarchies are only reachable via their shared levels.
 * <p>
 * This class is stateless and thread-safe.
 */
public class DfmGraphBuilder {
    public DfmGraph build(List<AbstractElement> elements) {
        var nodes = new LinkedHashMap<String, GraphNode>();
        var edges = new ArrayList<GraphEdge>();
        var facts = new ArrayList<FactElement>();
        var dimensions = new ArrayList<DimensionElement>();

        for (var element : elements) {
            if (element instanceof FactElement fact) {
                facts.add(fact);
                addNode(nodes, new GraphNode(fact.getName(), fact.getName(), NodeKind.FACT, false,
                    fact.getMeasures()));
                for (var descriptive : fact.getDescriptives()) {
                    addNode(nodes, new GraphNode(descriptive, descriptive, NodeKind.DESCRIPTIVE, false, List.of()));
                }
                dimensions.addAll(fact.getDimensions());
            } else if (element instanceof DimensionElement dimension) {
                dimensions.add(dimension);
            }
        }

        for (var dimension : dimensions) {
            for (var hierarchy : dimension.getHierarchies()) {
                for (Level level = hierarchy.getHead(); level != null; level = level.getNextLevel()) {
                    var kind = level.getLevelType() == LevelType.DESCRIPTIVE ? NodeKind.DESCRIPTIVE : NodeKind.LEVEL;
                    addNode(nodes, new GraphNode(level.getName(), level.getName(), kind, level.isOptional(),
                        List.of()));
                }
            }
        }

        for (var fact : facts) {
            for (var descriptive : fact.getDescriptives()) {
                addEdge(edges, nodes, fact.getName(), descriptive, ConnectionType.SIMPLE, false);
            }
            for (var dimension : fact.getDimensions()) {
                if (!dimension.getHierarchies().isEmpty() && dimension.getHierarchies().get(0).getHead() != null) {
                    addEdge(edges, nodes, fact.getName(), dimension.getHierarchies().get(0).getHead().getName(),
                        ConnectionType.SIMPLE, false);
                }
            }
        }

        for (var dimension : dimensions) {
            for (var hierarchy : dimension.getHierarchies()) {
                for (Level level = hierarchy.getHead(); level != null && level.getNextLevel() != null;
                     level = level.getNextLevel()) {
                    addEdge(edges, nodes, level.getName(), level.getNextLevel().getName(),
                        level.getConnectionType(), level.isConnectionOptional());
                }
            }
        }
        return new DfmGraph(new ArrayList<>(nodes.values()), edges);
    }

    private void addNode(Map<String, GraphNode> nodes, GraphNode node) {
        nodes.putIfAbsent(node.id(), node);
    }

    private void addEdge(List<GraphEdge> edges, Map<String, GraphNode> nodes, String source, String target,
                         ConnectionType connectionType, boolean optional) {
        if (!nodes.containsKey(source) || !nodes.containsKey(target) || source.equals(target)) {
            return;
        }
        for (var edge : edges) {
            if (edge.source().equals(source) && edge.target().equals(target)) {
                return;
            }
        }
        edges.add(new GraphEdge(source, target, connectionType == null ? ConnectionType.SIMPLE : connectionType,
            optional));
    }
}
