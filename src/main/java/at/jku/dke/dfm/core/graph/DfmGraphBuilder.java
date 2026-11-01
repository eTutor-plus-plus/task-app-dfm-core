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
 * Same-named levels share one node within a dimension, but not across dimensions. A fact is connected to the
 * head of the first hierarchy of each of its dimensions; further hierarchies are only reachable via their
 * shared levels.
 * <p>
 * This class is stateless and thread-safe.
 */
public class DfmGraphBuilder {
    public DfmGraph build(List<AbstractElement> elements) {
        var nodes = new LinkedHashMap<String, GraphNode>();
        var edges = new ArrayList<GraphEdge>();
        var facts = new ArrayList<FactData>();
        var dimensions = new ArrayList<DimensionData>();
        int factIndex = 0;
        int dimensionIndex = 0;

        for (var element : elements) {
            if (element instanceof FactElement fact) {
                String factId = "fact-" + factIndex++;
                addNode(nodes, new GraphNode(factId, fact.getName(), NodeKind.FACT, false,
                    fact.getMeasures()));
                for (int i = 0; i < fact.getDescriptives().size(); i++) {
                    String descriptiveId = descriptiveId(factId, i);
                    String descriptive = fact.getDescriptives().get(i);
                    addNode(nodes, new GraphNode(descriptiveId, descriptive, NodeKind.DESCRIPTIVE, false, List.of()));
                }
                var factDimensions = new ArrayList<DimensionData>();
                for (var dimension : fact.getDimensions()) {
                    var dimensionData = new DimensionData(dimension, "dimension-" + dimensionIndex++);
                    dimensions.add(dimensionData);
                    factDimensions.add(dimensionData);
                }
                facts.add(new FactData(fact, factId, factDimensions));
            } else if (element instanceof DimensionElement dimension) {
                dimensions.add(new DimensionData(dimension, "dimension-" + dimensionIndex++));
            }
        }

        for (var dimension : dimensions) {
            for (var hierarchy : dimension.element().getHierarchies()) {
                for (Level level = hierarchy.getHead(); level != null; level = level.getNextLevel()) {
                    var kind = level.getLevelType() == LevelType.DESCRIPTIVE ? NodeKind.DESCRIPTIVE : NodeKind.LEVEL;
                    addNode(nodes, new GraphNode(levelId(dimension, level.getName()), level.getName(), kind,
                        level.isOptional(), List.of()));
                }
            }
        }

        for (var factData : facts) {
            var fact = factData.element();
            for (int i = 0; i < fact.getDescriptives().size(); i++) {
                addEdge(edges, nodes, factData.id(), descriptiveId(factData.id(), i), ConnectionType.SIMPLE, false);
            }
            for (var dimension : factData.dimensions()) {
                var hierarchies = dimension.element().getHierarchies();
                if (!hierarchies.isEmpty() && hierarchies.get(0).getHead() != null) {
                    addEdge(edges, nodes, factData.id(),
                        levelId(dimension, hierarchies.get(0).getHead().getName()), ConnectionType.SIMPLE, false);
                }
            }
        }

        for (var dimension : dimensions) {
            for (var hierarchy : dimension.element().getHierarchies()) {
                for (Level level = hierarchy.getHead(); level != null && level.getNextLevel() != null;
                     level = level.getNextLevel()) {
                    addEdge(edges, nodes, levelId(dimension, level.getName()),
                        levelId(dimension, level.getNextLevel().getName()),
                        level.getConnectionType(), level.isConnectionOptional());
                }
            }
        }
        return new DfmGraph(new ArrayList<>(nodes.values()), edges);
    }

    private void addNode(Map<String, GraphNode> nodes, GraphNode node) {
        nodes.putIfAbsent(node.id(), node);
    }

    private String descriptiveId(String factId, int index) {
        return factId + "-descriptive-" + index;
    }

    private String levelId(DimensionData dimension, String levelName) {
        return dimension.id() + "-level-" + levelName;
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

    private record FactData(FactElement element, String id, List<DimensionData> dimensions) {
    }

    private record DimensionData(DimensionElement element, String id) {
    }
}
