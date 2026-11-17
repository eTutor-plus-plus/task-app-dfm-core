package at.jku.dke.dfm.core.graph;

import at.jku.dke.dfm.core.models.ast.AbstractElement;
import at.jku.dke.dfm.core.models.ast.DimensionElement;
import at.jku.dke.dfm.core.models.ast.FactElement;
import at.jku.dke.dfm.core.models.ast.Hierarchy;
import at.jku.dke.dfm.core.models.ast.HierarchyDefinitionElement;
import at.jku.dke.dfm.core.models.ast.Level;
import at.jku.dke.dfm.core.models.enums.ConnectionType;
import at.jku.dke.dfm.core.models.enums.LevelType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a {@link DfmGraph} from parsed DFM elements.
 * <p>
 * Same-named levels share one node within a dimension, but not across dimensions. A fact is connected to the
 * head of the first hierarchy of each of its dimensions; further hierarchies are only reachable via their
 * shared levels. A level that refers to itself with a role ({@code a -[role]-> a}) yields a self-loop edge
 * (recursive hierarchy); a self-reference without a role is ignored.
 * <p>
 * The levels of a declared hierarchy ({@link HierarchyDefinitionElement}) form one node each, independent of the
 * dimensions. A {@code {shared}} or {@code {cross}} level is not a node of its own: its edge ends at the entry level
 * of the referenced hierarchy, which gets the kind {@link NodeKind#SHARED} or {@link NodeKind#CROSS}.
 * <p>
 * This class is stateless and thread-safe.
 */
public class DfmGraphBuilder {
    public DfmGraph build(List<AbstractElement> elements) {
        var nodes = new LinkedHashMap<String, GraphNode>();
        var edges = new ArrayList<GraphEdge>();
        var facts = new ArrayList<FactData>();
        var dimensions = new ArrayList<DimensionData>();
        var definitions = new LinkedHashMap<String, HierarchyDefinitionElement>();
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
                var definitionsByDimension = new HashMap<DimensionElement, DimensionData>();
                for (var dimension : fact.getDimensions()) {
                    var existing = definitionsByDimension.get(dimension.getDefinition());
                    if (existing == null) {
                        existing = new DimensionData(dimension, "dimension-" + dimensionIndex++);
                        definitionsByDimension.put(dimension.getDefinition(), existing);
                        dimensions.add(existing);
                    }
                    factDimensions.add(new DimensionData(dimension, existing.id()));
                }
                facts.add(new FactData(fact, factId, factDimensions));
            } else if (element instanceof DimensionElement dimension) {
                dimensions.add(new DimensionData(dimension, "dimension-" + dimensionIndex++));
            } else if (element instanceof HierarchyDefinitionElement definition) {
                definitions.putIfAbsent(definition.getName(), definition);
            }
        }

        var referenceKinds = referenceKinds(dimensions, definitions.values());

        for (var dimension : dimensions) {
            addLevelNodes(nodes, dimension.id(), dimension.element().getHierarchies(), null, null);
        }
        for (var definition : definitions.values()) {
            var entry = definition.getHead();
            addLevelNodes(nodes, definitionPrefix(definition), definition.getHierarchies(),
                entry == null ? null : entry.getName(), referenceKinds.get(definition.getName()));
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
                        levelId(dimension.id(), hierarchies.get(0).getHead(), definitions),
                        dimension.element().getFactConnectionType(), dimension.element().isFactConnectionOptional(),
                        dimension.element().getFactConnectionLabel());
                }
            }
        }

        for (var dimension : dimensions) {
            addChainEdges(edges, nodes, dimension.id(), dimension.element().getHierarchies(), definitions);
        }
        for (var definition : definitions.values()) {
            addChainEdges(edges, nodes, definitionPrefix(definition), definition.getHierarchies(), definitions);
        }
        return new DfmGraph(new ArrayList<>(nodes.values()), edges);
    }

    // the kind of the first reference to a hierarchy wins; the parser rejects mixed use
    private Map<String, NodeKind> referenceKinds(List<DimensionData> dimensions,
                                                 Collection<HierarchyDefinitionElement> definitions) {
        var kinds = new HashMap<String, NodeKind>();
        var chains = new ArrayList<Hierarchy>();
        dimensions.forEach(dimension -> chains.addAll(dimension.element().getHierarchies()));
        definitions.forEach(definition -> chains.addAll(definition.getHierarchies()));
        for (var chain : chains) {
            for (Level level = chain.getHead(); level != null; level = level.getNextLevel()) {
                if (level.getLevelType().isReference()) {
                    kinds.putIfAbsent(level.getName(),
                        level.getLevelType() == LevelType.SHARED ? NodeKind.SHARED : NodeKind.CROSS);
                }
            }
        }
        return kinds;
    }

    private void addLevelNodes(Map<String, GraphNode> nodes, String prefix, List<Hierarchy> hierarchies,
                               String entryName, NodeKind entryKind) {
        for (var hierarchy : hierarchies) {
            for (Level level = hierarchy.getHead(); level != null; level = level.getNextLevel()) {
                if (level.getLevelType().isReference()) {
                    continue;
                }
                var kind = level.getLevelType() == LevelType.DESCRIPTIVE ? NodeKind.DESCRIPTIVE : NodeKind.LEVEL;
                if (entryKind != null && level.getName().equals(entryName)) {
                    kind = entryKind;
                }
                addNode(nodes, new GraphNode(levelId(prefix, level.getName()), level.getName(), kind,
                    level.isOptional(), List.of()));
            }
        }
    }

    private void addChainEdges(List<GraphEdge> edges, Map<String, GraphNode> nodes, String prefix,
                               List<Hierarchy> hierarchies, Map<String, HierarchyDefinitionElement> definitions) {
        for (var hierarchy : hierarchies) {
            for (Level level = hierarchy.getHead(); level != null && level.getNextLevel() != null;
                 level = level.getNextLevel()) {
                addEdge(edges, nodes, levelId(prefix, level, definitions),
                    levelId(prefix, level.getNextLevel(), definitions),
                    level.getConnectionType(), level.isConnectionOptional(), level.getConnectionLabel());
            }
        }
    }

    private void addNode(Map<String, GraphNode> nodes, GraphNode node) {
        nodes.putIfAbsent(node.id(), node);
    }

    private String descriptiveId(String factId, int index) {
        return factId + "-descriptive-" + index;
    }

    private String definitionPrefix(HierarchyDefinitionElement definition) {
        return "hierarchy-" + definition.getName();
    }

    private String levelId(String prefix, String levelName) {
        return prefix + "-level-" + levelName;
    }

    // a reference resolves to the entry level of the declared hierarchy, or to nothing if it is not declared
    private String levelId(String prefix, Level level, Map<String, HierarchyDefinitionElement> definitions) {
        if (!level.getLevelType().isReference()) {
            return levelId(prefix, level.getName());
        }
        var definition = definitions.get(level.getName());
        if (definition == null || definition.getHead() == null) {
            return null;
        }
        return levelId(definitionPrefix(definition), definition.getHead().getName());
    }

    private void addEdge(List<GraphEdge> edges, Map<String, GraphNode> nodes, String source, String target,
                         ConnectionType connectionType, boolean optional) {
        addEdge(edges, nodes, source, target, connectionType, optional, null);
    }

    private void addEdge(List<GraphEdge> edges, Map<String, GraphNode> nodes, String source, String target,
                         ConnectionType connectionType, boolean optional, String label) {
        if (!nodes.containsKey(source) || !nodes.containsKey(target)) {
            return;
        }
        if (source.equals(target) && connectionType == ConnectionType.MULTIPLE) {
            throw new IllegalArgumentException("A recursive connection cannot be multiple.");
        }
        // a self-reference is only meaningful as a recursive hierarchy, i.e. with a role
        if (source.equals(target) && label == null) {
            return;
        }
        var edge = new GraphEdge(source, target, connectionType == null ? ConnectionType.SIMPLE : connectionType,
            optional, label);
        if (!edges.contains(edge)) {
            edges.add(edge);
        }
    }

    private record FactData(FactElement element, String id, List<DimensionData> dimensions) {
    }

    private record DimensionData(DimensionElement element, String id) {
    }
}
