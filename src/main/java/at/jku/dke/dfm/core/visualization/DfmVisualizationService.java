package at.jku.dke.dfm.core.visualization;

import at.jku.dke.dfm.core.models.ast.AbstractElement;
import at.jku.dke.dfm.core.models.ast.DimensionElement;
import at.jku.dke.dfm.core.models.ast.FactElement;
import at.jku.dke.dfm.core.models.ast.Hierarchy;
import at.jku.dke.dfm.core.models.ast.Level;
import at.jku.dke.dfm.core.models.enums.ConnectionType;
import at.jku.dke.dfm.core.models.enums.LevelType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates a graph-style SVG overview of a parsed DFM.
 */
public class DfmVisualizationService {
    private static final int WIDTH = 1400;
    private static final int HEIGHT = 1000;
    private static final double FACT_WIDTH = 120;
    private static final double FACT_BASE_HEIGHT = 35;
    private static final double LEVEL_RADIUS = 10;
    private static final double DESCRIPTIVE_WIDTH = 100;
    private static final double DESCRIPTIVE_HEIGHT = 20;
    private static final double LINK_DISTANCE = 220;
    private static final double COLLISION_PADDING = 24;

    public String render(List<AbstractElement> elements) {
        var graph = createGraph(elements);
        layout(graph.nodes(), graph.links());
        var svg = new StringBuilder()
            .append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"")
            .append(WIDTH).append("\" height=\"").append(HEIGHT)
            .append("\" viewBox=\"0 0 ").append(WIDTH).append(" ").append(HEIGHT).append("\">")
            .append("<style>text{font:16px serif}.fact-title{font-weight:bold;fill:#00e}")
            .append(".fact-detail{fill:#666;font-size:14px}.descriptive{fill:#000}")
            .append(".edge{stroke:#999;stroke-width:1;fill:none}</style>")
            .append("<defs><marker id=\"arrow\" viewBox=\"0 0 10 10\" refX=\"8\" refY=\"5\"")
            .append(" markerWidth=\"7\" markerHeight=\"7\" orient=\"auto-start-reverse\">")
            .append("<path d=\"M 0 0 L 10 5 L 0 10 z\" fill=\"#222\"/></marker></defs>");

        for (var link : graph.links()) {
            appendLink(svg, link);
        }
        for (var node : graph.nodes()) {
            appendNode(svg, node);
        }
        return svg.append("</svg>").toString();
    }

    private Graph createGraph(List<AbstractElement> elements) {
        var nodes = new ArrayList<Node>();
        var nodeByName = new HashMap<String, Node>();
        var links = new ArrayList<Link>();
        var facts = new ArrayList<FactElement>();
        var dimensions = new ArrayList<DimensionElement>();

        for (var element : elements) {
            if (element instanceof FactElement fact) {
                facts.add(fact);
                var factNode = addNode(nodeByName, nodes, fact.getName(), NodeType.FACT, false);
                factNode.measures.addAll(fact.getMeasures());
                factNode.descriptives.addAll(fact.getDescriptives());
                for (var descriptive : fact.getDescriptives()) {
                    addNode(nodeByName, nodes, descriptive, NodeType.DESCRIPTIVE, false);
                }
                dimensions.addAll(fact.getDimensions());
            } else if (element instanceof DimensionElement dimension) {
                dimensions.add(dimension);
            }
        }

        for (var dimension : dimensions) {
            for (var hierarchy : dimension.getHierarchies()) {
                Level current = hierarchy.getHead();
                while (current != null) {
                    addNode(nodeByName, nodes, current.getName(),
                        current.getLevelType() == LevelType.DESCRIPTIVE
                            ? NodeType.DESCRIPTIVE : NodeType.LEVEL, current.isOptional());
                    current = current.getNextLevel();
                }
            }
        }

        for (var fact : facts) {
            for (var descriptive : fact.getDescriptives()) {
                addLink(links, nodeByName, fact.getName(), descriptive, ConnectionType.SIMPLE, false);
            }
            for (var dimension : fact.getDimensions()) {
                for (var hierarchy : dimension.getHierarchies()) {
                    if (hierarchy.getHead() != null) {
                        addLink(links, nodeByName, fact.getName(), hierarchy.getHead().getName(),
                            ConnectionType.SIMPLE, false);
                    }
                }
            }
        }

        for (var dimension : dimensions) {
            for (var hierarchy : dimension.getHierarchies()) {
                Level current = hierarchy.getHead();
                while (current != null && current.getNextLevel() != null) {
                    addLink(links, nodeByName, current.getName(), current.getNextLevel().getName(),
                        current.getConnectionType(), current.isConnectionOptional());
                    current = current.getNextLevel();
                }
            }
        }
        return new Graph(nodes, links);
    }

    private Node addNode(Map<String, Node> nodeByName, List<Node> nodes,
                         String name, NodeType type, boolean optional) {
        var existing = nodeByName.get(name);
        if (existing != null) {
            return existing;
        }
        var node = new Node(name, type, optional);
        nodeByName.put(name, node);
        nodes.add(node);
        return node;
    }

    private void addLink(List<Link> links, Map<String, Node> nodeByName,
                         String source, String target, ConnectionType connectionType, boolean optional) {
        var sourceNode = nodeByName.get(source);
        var targetNode = nodeByName.get(target);
        if (sourceNode == null || targetNode == null || sourceNode == targetNode) {
            return;
        }
        for (var link : links) {
            if (link.source() == sourceNode && link.target() == targetNode) {
                return;
            }
        }
        links.add(new Link(sourceNode, targetNode,
            connectionType == null ? ConnectionType.SIMPLE : connectionType, optional));
    }

    private void layout(List<Node> nodes, List<Link> links) {
        var fact = nodes.stream().filter(node -> node.type() == NodeType.FACT).findFirst().orElse(null);
        double centerX = WIDTH / 2.0;
        double centerY = HEIGHT / 2.0;
        for (int i = 0; i < nodes.size(); i++) {
            var node = nodes.get(i);
            double angle = 2 * Math.PI * i / Math.max(1, nodes.size());
            double radius = node == fact ? 0 : 260;
            node.x = centerX + Math.cos(angle) * radius;
            node.y = centerY + Math.sin(angle) * radius;
        }

        for (int iteration = 0; iteration < 350; iteration++) {
            var forces = new double[nodes.size()][2];
            for (int i = 0; i < nodes.size(); i++) {
                for (int j = i + 1; j < nodes.size(); j++) {
                    var first = nodes.get(i);
                    var second = nodes.get(j);
                    double dx = second.x - first.x;
                    double dy = second.y - first.y;
                    double distance = Math.max(1, Math.sqrt(dx * dx + dy * dy));
                    double minimumDistance = collisionRadius(first) + collisionRadius(second)
                        + COLLISION_PADDING;
                    double force = distance < minimumDistance
                        ? (minimumDistance - distance) * 0.42
                        : 9000 / (distance * distance);
                    forces[i][0] -= dx / distance * force;
                    forces[i][1] -= dy / distance * force;
                    forces[j][0] += dx / distance * force;
                    forces[j][1] += dy / distance * force;
                }
            }
            for (var link : links) {
                int source = nodes.indexOf(link.source());
                int target = nodes.indexOf(link.target());
                double dx = link.target().x - link.source().x;
                double dy = link.target().y - link.source().y;
                double distance = Math.max(1, Math.sqrt(dx * dx + dy * dy));
                double force = (distance - LINK_DISTANCE) * 0.012;
                forces[source][0] += dx / distance * force;
                forces[source][1] += dy / distance * force;
                forces[target][0] -= dx / distance * force;
                forces[target][1] -= dy / distance * force;
            }
            for (int i = 0; i < nodes.size(); i++) {
                var node = nodes.get(i);
                if (node == fact) {
                    node.x += (centerX - node.x) * 0.08;
                    node.y += (centerY - node.y) * 0.08;
                } else {
                    node.x = clamp(node.x + forces[i][0], horizontalMargin(node), WIDTH - horizontalMargin(node));
                    node.y = clamp(node.y + forces[i][1], 35, HEIGHT - 35);
                }
            }
        }
    }

    private double collisionRadius(Node node) {
        return switch (node.type()) {
            case FACT -> FACT_WIDTH / 1.5;
            case LEVEL -> LEVEL_RADIUS * 7;
            case DESCRIPTIVE -> DESCRIPTIVE_WIDTH / 1.5;
        };
    }

    private double horizontalMargin(Node node) {
        if (node.type() == NodeType.FACT) {
            return FACT_WIDTH / 2 + 12;
        }
        return Math.max(LEVEL_RADIUS + 12, estimatedLabelWidth(node.name) / 2 + 12);
    }

    private double estimatedLabelWidth(String value) {
        return value.length() * 8.5;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void appendLink(StringBuilder svg, Link link) {
        double dx = link.target().x - link.source().x;
        double dy = link.target().y - link.source().y;
        double distance = Math.max(1, Math.sqrt(dx * dx + dy * dy));
        double offset = link.connectionType() == ConnectionType.MULTIPLE ? 2.5 : 0;
        double offsetX = -dy / distance * offset;
        double offsetY = dx / distance * offset;
        double endX = link.target().x;
        double endY = link.target().y;
        if (link.connectionType() == ConnectionType.CONVERGENCE) {
            endX = (link.source().x + link.target().x) / 2;
            endY = (link.source().y + link.target().y) / 2;
        }
        appendLine(svg, link.source().x + offsetX, link.source().y + offsetY,
            endX + offsetX, endY + offsetY, link.connectionType() == ConnectionType.CONVERGENCE);
        if (link.connectionType() == ConnectionType.CONVERGENCE) {
            appendLine(svg, endX, endY, link.target().x, link.target().y, false);
        }
        if (link.connectionType() == ConnectionType.MULTIPLE) {
            svg.append("<line class=\"edge\" x1=\"").append(format(link.source().x - offsetX))
                .append("\" y1=\"").append(format(link.source().y - offsetY)).append("\" x2=\"")
                .append(format(link.target().x - offsetX)).append("\" y2=\"")
                .append(format(link.target().y - offsetY)).append("\"/>");
        }
        if (link.optional()) {
            double middleX = (link.source().x + link.target().x) / 2;
            double middleY = (link.source().y + link.target().y) / 2;
            double perpendicularX = -dy / distance * 5;
            double perpendicularY = dx / distance * 5;
            appendLine(svg, middleX - perpendicularX, middleY - perpendicularY,
                middleX + perpendicularX, middleY + perpendicularY, false);
        }
    }

    private void appendLine(StringBuilder svg, double sourceX, double sourceY,
                            double targetX, double targetY, boolean arrow) {
        svg.append("<line class=\"edge\" x1=\"").append(format(sourceX))
            .append("\" y1=\"").append(format(sourceY)).append("\" x2=\"")
            .append(format(targetX)).append("\" y2=\"").append(format(targetY)).append("\"");
        if (arrow) {
            svg.append(" marker-end=\"url(#arrow)\"");
        }
        svg.append("/>");
    }

    private void appendNode(StringBuilder svg, Node node) {
        if (node.type() == NodeType.FACT) {
            double factHeight = factHeight(node);
            svg.append("<rect x=\"").append(format(node.x - FACT_WIDTH / 2)).append("\" y=\"")
                .append(format(node.y - factHeight / 2)).append("\" width=\"").append(FACT_WIDTH)
                .append("\" height=\"").append(format(factHeight)).append("\" rx=\"10\" ry=\"10\" fill=\"white\" stroke=\"blue\"/>")
                .append("<text class=\"fact-title\" text-anchor=\"middle\" x=\"").append(format(node.x))
                .append("\" y=\"").append(format(node.y - (10 + node.measures.size() * 20) / 2)).append("\">")
                .append(escape(node.name)).append("</text>")
                .append("<line x1=\"").append(format(node.x - FACT_WIDTH / 2)).append("\" y1=\"")
                .append(format(node.y - (50 + node.measures.size() * 20) / 2 + 30)).append("\" x2=\"")
                .append(format(node.x + FACT_WIDTH / 2)).append("\" y2=\"")
                .append(format(node.y - (50 + node.measures.size() * 20) / 2 + 30)).append("\" stroke=\"blue\"/>");
            int detailIndex = 0;
            for (var measure : node.measures) {
                appendFactDetail(svg, node, measure, detailIndex++);
            }
            return;
        }
        if (node.type() == NodeType.LEVEL) {
            svg.append("<circle cx=\"").append(format(node.x)).append("\" cy=\"").append(format(node.y))
                .append("\" r=\"").append(LEVEL_RADIUS).append("\" fill=\"white\" stroke=\"blue\"/>");
        } else {
            svg.append("<rect x=\"").append(format(node.x - DESCRIPTIVE_WIDTH / 2)).append("\" y=\"")
                .append(format(node.y - DESCRIPTIVE_HEIGHT / 2)).append("\" width=\"")
                .append(DESCRIPTIVE_WIDTH).append("\" height=\"").append(DESCRIPTIVE_HEIGHT)
                .append("\" fill=\"white\" stroke=\"none\"/>")
                .append("<text class=\"descriptive\" text-anchor=\"middle\" x=\"").append(format(node.x))
                .append("\" y=\"").append(format(node.y)).append("\">").append(escape(node.name)).append("</text>")
                .append("<line x1=\"").append(format(node.x - DESCRIPTIVE_WIDTH / 2)).append("\" y1=\"")
                .append(format(node.y + DESCRIPTIVE_HEIGHT / 2)).append("\" x2=\"")
                .append(format(node.x + DESCRIPTIVE_WIDTH / 2)).append("\" y2=\"")
                .append(format(node.y + DESCRIPTIVE_HEIGHT / 2)).append("\" stroke=\"black\"/>");
            return;
        }
        svg.append("<text text-anchor=\"middle\" x=\"").append(format(node.x)).append("\" y=\"")
            .append(format(node.y + 20)).append("\">").append(escape(node.name)).append("</text>");
        if (node.optional()) {
            svg.append("<line x1=\"").append(format(node.x - 15)).append("\" y1=\"")
                .append(format(node.y)).append("\" x2=\"").append(format(node.x + 15))
                .append("\" y2=\"").append(format(node.y)).append("\" stroke=\"black\"/>");
        }
    }

    private double factHeight(Node node) {
        return FACT_BASE_HEIGHT + node.measures.size() * 20;
    }

    private void appendFactDetail(StringBuilder svg, Node node, String detail, int index) {
        svg.append("<text class=\"fact-detail\" x=\"").append(format(node.x - FACT_WIDTH / 2 + 12))
            .append("\" y=\"").append(format(node.y - factHeight(node) / 2 + 40 + index * 20)).append("\">")
            .append(escape(detail)).append("</text>");
    }

    private String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    private enum NodeType {
        FACT, LEVEL, DESCRIPTIVE
    }

    private static final class Node {
        private final String name;
        private final NodeType type;
        private final boolean optional;
        private final List<String> measures = new ArrayList<>();
        private final List<String> descriptives = new ArrayList<>();
        private double x;
        private double y;

        private Node(String name, NodeType type, boolean optional) {
            this.name = name;
            this.type = type;
            this.optional = optional;
        }

        private String name() {
            return name;
        }

        private NodeType type() {
            return type;
        }

        private boolean optional() {
            return optional;
        }
    }

    private record Link(Node source, Node target, ConnectionType connectionType, boolean optional) {
    }

    private record Graph(List<Node> nodes, List<Link> links) {
    }
}
