package at.jku.dke.dfm.core.visualization;

import at.jku.dke.dfm.core.graph.DfmGraphBuilder;
import at.jku.dke.dfm.core.graph.NodeKind;
import at.jku.dke.dfm.core.models.ast.AbstractElement;
import at.jku.dke.dfm.core.models.enums.ConnectionType;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Creates a graph-style PNG overview of a parsed DFM using Java2D.
 * <p>
 * Text rendering needs fonts; on servers without a display, run the JVM with {@code -Djava.awt.headless=true}.
 */
public class DfmVisualizationService {
    private static final int WIDTH = 1400;
    private static final int HEIGHT = 1000;
    private static final double FACT_WIDTH = 120;
    private static final double FACT_BASE_HEIGHT = 35;
    private static final double LEVEL_RADIUS = 10;
    private static final double SHARED_RING_GAP = 4;
    private static final double DESCRIPTIVE_WIDTH = 100;
    private static final double DESCRIPTIVE_HEIGHT = 20;
    private static final double LINK_DISTANCE = 220;
    private static final double COLLISION_PADDING = 24;
    private static final double LABEL_MARGIN = 9;
    private static final double LABEL_HALF_HEIGHT = 7;
    private static final double SELF_LOOP_HEIGHT = 38;
    private static final double SELF_LOOP_HALF_WIDTH = 26;
    private static final double SELF_LOOP_HALF_GAP = 6;
    private static final double SELF_LOOP_MAX_CLEARANCE = Math.toRadians(70);

    /**
     * Renders the model as a PNG image.
     *
     * @param elements the parsed DFM elements
     * @return the PNG file content
     * @throws UncheckedIOException if encoding the image fails
     */
    public byte[] renderPng(List<AbstractElement> elements) {
        var canvas = new Java2dCanvas(WIDTH, HEIGHT);
        draw(elements, canvas);
        try (var output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(canvas.finish(), "png", output)) {
                throw new IllegalStateException("No PNG writer available.");
            }
            return output.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void draw(List<AbstractElement> elements, DfmCanvas canvas) {
        var graph = createGraph(elements);
        layout(graph.nodes(), graph.links());
        for (var link : graph.links()) {
            if (link.source() == link.target()) {
                appendSelfLoop(canvas, link, graph.links());
            } else if (parallelLinks(link, graph.links()).size() > 1) {
                appendParallelLink(canvas, link, graph.links());
            } else {
                appendLink(canvas, link);
            }
        }
        for (var node : graph.nodes()) {
            appendNode(canvas, node);
        }
    }

    private Graph createGraph(List<AbstractElement> elements) {
        var model = new DfmGraphBuilder().build(elements);
        var nodes = new ArrayList<Node>();
        var nodeById = new HashMap<String, Node>();
        for (var graphNode : model.nodes()) {
            var node = new Node(graphNode.label(), toNodeType(graphNode.kind()), graphNode.optional());
            node.measures.addAll(graphNode.details());
            nodes.add(node);
            nodeById.put(graphNode.id(), node);
        }
        var links = new ArrayList<Link>();
        for (var edge : model.edges()) {
            links.add(new Link(nodeById.get(edge.source()), nodeById.get(edge.target()),
                edge.connectionType(), edge.optional(), edge.label()));
        }
        return new Graph(nodes, links);
    }

    private NodeType toNodeType(NodeKind kind) {
        return switch (kind) {
            case FACT -> NodeType.FACT;
            case LEVEL -> NodeType.LEVEL;
            case DESCRIPTIVE -> NodeType.DESCRIPTIVE;
            case SHARED -> NodeType.SHARED;
            case CROSS -> NodeType.CROSS;
        };
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
                if (link.source() == link.target()) {
                    continue;
                }
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
            case LEVEL, SHARED, CROSS -> LEVEL_RADIUS * 7;
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

    private void appendLink(DfmCanvas canvas, Link link) {
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
        canvas.edgeLine(link.source().x + offsetX, link.source().y + offsetY,
            endX + offsetX, endY + offsetY, link.connectionType() == ConnectionType.CONVERGENCE);
        if (link.connectionType() == ConnectionType.CONVERGENCE) {
            canvas.edgeLine(endX, endY, link.target().x, link.target().y, false);
        }
        if (link.connectionType() == ConnectionType.MULTIPLE) {
            canvas.edgeLine(link.source().x - offsetX, link.source().y - offsetY,
                link.target().x - offsetX, link.target().y - offsetY, false);
        }
        if (link.optional()) {
            double middleX = (link.source().x + link.target().x) / 2;
            double middleY = (link.source().y + link.target().y) / 2;
            double perpendicularX = -dy / distance * 5;
            double perpendicularY = dx / distance * 5;
            canvas.optionalMarker(middleX - perpendicularX, middleY - perpendicularY,
                middleX + perpendicularX, middleY + perpendicularY);
        }
        if (link.label() != null) {
            appendLinkLabel(canvas, link, dx, dy, distance, 0);
        }
    }

    private List<Link> parallelLinks(Link link, List<Link> links) {
        return links.stream().filter(other -> other.source() == link.source() && other.target() == link.target())
            .toList();
    }

    private void appendParallelLink(DfmCanvas canvas, Link link, List<Link> links) {
        var parallel = parallelLinks(link, links);
        double spacing = Math.max(50, parallel.stream().filter(other -> other.label() != null)
            .mapToInt(other -> other.label().length()).max().orElse(0) * 7 + 16);
        double offset = (parallel.indexOf(link) - (parallel.size() - 1) / 2.0) * spacing;
        double dx = link.target().x - link.source().x;
        double dy = link.target().y - link.source().y;
        double distance = Math.max(1, Math.hypot(dx, dy));
        double nx = -dy / distance;
        double ny = dx / distance;
        int lines = link.connectionType() == ConnectionType.MULTIPLE ? 2 : 1;
        for (int i = 0; i < lines; i++) {
            double curveOffset = offset + (lines == 2 ? (i == 0 ? 2.5 : -2.5) : 0);
            double cx = (link.source().x + link.target().x) / 2 + nx * 2 * curveOffset;
            double cy = (link.source().y + link.target().y) / 2 + ny * 2 * curveOffset;
            if (link.connectionType() == ConnectionType.CONVERGENCE) {
                double mx = (link.source().x + 2 * cx + link.target().x) / 4;
                double my = (link.source().y + 2 * cy + link.target().y) / 4;
                canvas.edgeQuad(link.source().x, link.source().y,
                    (link.source().x + cx) / 2, (link.source().y + cy) / 2, mx, my, true);
                canvas.edgeQuad(mx, my, (link.target().x + cx) / 2, (link.target().y + cy) / 2,
                    link.target().x, link.target().y, false);
            } else {
                canvas.edgeQuad(link.source().x, link.source().y, cx, cy,
                    link.target().x, link.target().y, false);
            }
        }
        double mx = (link.source().x + link.target().x) / 2 + nx * offset;
        double my = (link.source().y + link.target().y) / 2 + ny * offset;
        if (link.optional()) {
            canvas.optionalMarker(mx - nx * 5, my - ny * 5, mx + nx * 5, my + ny * 5);
        }
        if (link.label() != null) {
            appendLinkLabel(canvas, link, dx, dy, distance, offset);
        }
    }

    private void appendSelfLoop(DfmCanvas canvas, Link link, List<Link> links) {
        var node = link.source();

        // the loop points upwards (node names hang below the nodes) and as far away from the connected edges
        // as possible; without a clear difference it points straight up
        var neighborAngles = new ArrayList<Double>();
        for (var other : links) {
            if (other.source() == other.target()) {
                continue;
            }
            if (other.source() == node || other.target() == node) {
                var neighbor = other.source() == node ? other.target() : other.source();
                neighborAngles.add(Math.atan2(neighbor.y - node.y, neighbor.x - node.x));
            }
        }
        double bestAngle = -Math.PI / 2;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int degrees = -150; degrees <= -30; degrees += 5) {
            double angle = Math.toRadians(degrees);
            double clearance = SELF_LOOP_MAX_CLEARANCE;
            for (double neighborAngle : neighborAngles) {
                clearance = Math.min(clearance,
                    Math.abs(Math.atan2(Math.sin(angle - neighborAngle), Math.cos(angle - neighborAngle))));
            }
            double score = clearance - Math.abs(angle + Math.PI / 2) * 0.01;
            if (score > bestScore) {
                bestScore = score;
                bestAngle = angle;
            }
        }
        double dirX = Math.cos(bestAngle);
        double dirY = Math.sin(bestAngle);
        double perpX = -dirY;
        double perpY = dirX;

        double startAlong = Math.sqrt(LEVEL_RADIUS * LEVEL_RADIUS - SELF_LOOP_HALF_GAP * SELF_LOOP_HALF_GAP);
        var parallel = parallelLinks(link, links);
        int loopIndex = parallel.indexOf(link);
        double controlAlong = LEVEL_RADIUS + SELF_LOOP_HEIGHT + loopIndex * 40;
        appendSelfLoopPath(canvas, node, dirX, dirY, perpX, perpY, startAlong, controlAlong,
            link.connectionType() == ConnectionType.CONVERGENCE);

        double tip = 0.75 * controlAlong + 0.25 * startAlong;
        if (link.optional()) {
            canvas.optionalMarker(node.x + dirX * (tip - 5), node.y + dirY * (tip - 5),
                node.x + dirX * (tip + 5), node.y + dirY * (tip + 5));
        }
        if (link.label() != null) {
            double halfWidth = link.label().length() * 7 / 2;
            double extent = Math.abs(dirX) * halfWidth + Math.abs(dirY) * LABEL_HALF_HEIGHT;
            double distance = tip + LABEL_MARGIN + extent;
            double labelX = node.x + dirX * distance;
            double labelY = node.y + dirY * distance;
            if (loopIndex < parallel.size() - 1) {
                double outerControl = LEVEL_RADIUS + SELF_LOOP_HEIGHT + (parallel.size() - 1) * 40 + 4;
                double outerLeft = Math.min(
                    Math.min(node.x - perpX * SELF_LOOP_HALF_GAP + dirX * startAlong,
                        node.x + perpX * SELF_LOOP_HALF_GAP + dirX * startAlong),
                    Math.min(node.x - perpX * SELF_LOOP_HALF_WIDTH + dirX * outerControl,
                        node.x + perpX * SELF_LOOP_HALF_WIDTH + dirX * outerControl));
                labelX = outerLeft - halfWidth - LABEL_MARGIN;
                labelY = node.y + dirY * tip;
            }
            canvas.text(labelX, labelY + 4, link.label(), DfmCanvas.TextStyle.EDGE_LABEL);
        }
    }

    private void appendSelfLoopPath(DfmCanvas canvas, Node node, double dirX, double dirY, double perpX,
                                    double perpY, double startAlong, double controlAlong, boolean arrow) {
        canvas.edgeCubic(
            node.x - perpX * SELF_LOOP_HALF_GAP + dirX * startAlong,
            node.y - perpY * SELF_LOOP_HALF_GAP + dirY * startAlong,
            node.x - perpX * SELF_LOOP_HALF_WIDTH + dirX * controlAlong,
            node.y - perpY * SELF_LOOP_HALF_WIDTH + dirY * controlAlong,
            node.x + perpX * SELF_LOOP_HALF_WIDTH + dirX * controlAlong,
            node.y + perpY * SELF_LOOP_HALF_WIDTH + dirY * controlAlong,
            node.x + perpX * SELF_LOOP_HALF_GAP + dirX * startAlong,
            node.y + perpY * SELF_LOOP_HALF_GAP + dirY * startAlong,
            arrow);
    }

    private void appendLinkLabel(DfmCanvas canvas, Link link, double dx, double dy, double distance, double offset) {
        double halfWidth = link.label().length() * 7 / 2;
        double unitX = dx / distance;
        double unitY = dy / distance;

        // the label belongs to the visible part of the edge, i.e. the part between the node borders
        double startClip = borderDistance(link.source(), unitX, unitY);
        double endClip = borderDistance(link.target(), -unitX, -unitY);
        double visible = distance - startClip - endClip;
        double middleX = (link.source().x + link.target().x) / 2 + unitX * (startClip - endClip) / 2;
        double middleY = (link.source().y + link.target().y) / 2 + unitY * (startClip - endClip) / 2;

        // node names hang below the nodes, so a shifted label is moved towards the lower node
        double directionX = unitY < 0 ? -unitX : unitX;
        double directionY = Math.abs(unitY);
        double extent = Math.abs(directionX) * halfWidth + Math.abs(directionY) * LABEL_HALF_HEIGHT;
        boolean reserveMiddle = link.optional() || link.connectionType() == ConnectionType.CONVERGENCE;
        double x = middleX;
        double y = middleY;
        if (reserveMiddle && LABEL_MARGIN + 2 * extent + 2 <= visible / 2) {
            // the middle carries the optional marker or the convergence arrow
            x = middleX + directionX * (extent + LABEL_MARGIN);
            y = middleY + directionY * (extent + LABEL_MARGIN);
        } else if (reserveMiddle || 2 * extent + 2 > visible) {
            // the edge is too short, so the label is placed beside the edge instead
            double normalX = -directionY;
            double normalY = directionX;
            if (normalY > 0) {
                normalX = -normalX;
                normalY = -normalY;
            }
            double labelOffset = LABEL_MARGIN + Math.abs(normalX) * halfWidth + Math.abs(normalY) * LABEL_HALF_HEIGHT;
            x = middleX + normalX * labelOffset;
            y = middleY + normalY * labelOffset;
        }
        double fraction = Math.max(0, Math.min(1,
            ((x - link.source().x) * unitX + (y - link.source().y) * unitY) / distance));
        double bend = offset * 4 * fraction * (1 - fraction);
        x -= unitY * bend;
        y += unitX * bend;
        canvas.text(x, y + 4, link.label(), DfmCanvas.TextStyle.EDGE_LABEL);
    }

    private double borderDistance(Node node, double unitX, double unitY) {
        double halfWidth;
        double halfHeight;
        switch (node.type()) {
            case LEVEL, CROSS -> {
                return LEVEL_RADIUS;
            }
            case SHARED -> {
                return LEVEL_RADIUS + SHARED_RING_GAP;
            }
            case FACT -> {
                halfWidth = FACT_WIDTH / 2.0;
                halfHeight = factHeight(node) / 2;
            }
            default -> {
                halfWidth = DESCRIPTIVE_WIDTH / 2;
                halfHeight = DESCRIPTIVE_HEIGHT / 2;
            }
        }
        double horizontal = Math.abs(unitX) < 1e-9 ? Double.POSITIVE_INFINITY : halfWidth / Math.abs(unitX);
        double vertical = Math.abs(unitY) < 1e-9 ? Double.POSITIVE_INFINITY : halfHeight / Math.abs(unitY);
        return Math.min(horizontal, vertical);
    }

    private void appendNode(DfmCanvas canvas, Node node) {
        if (node.type() == NodeType.FACT) {
            double factHeight = factHeight(node);
            canvas.factBox(node.x - FACT_WIDTH / 2, node.y - factHeight / 2, FACT_WIDTH, factHeight);
            canvas.text(node.x, node.y - (10 + node.measures.size() * 20) / 2, node.name,
                DfmCanvas.TextStyle.FACT_TITLE);
            double ruleY = node.y - (50 + node.measures.size() * 20) / 2 + 30;
            canvas.rule(node.x - FACT_WIDTH / 2, ruleY, node.x + FACT_WIDTH / 2, ruleY, DfmCanvas.RuleColor.BLUE);
            int detailIndex = 0;
            for (var measure : node.measures) {
                canvas.text(node.x - FACT_WIDTH / 2 + 12, node.y - factHeight / 2 + 40 + detailIndex++ * 20,
                    measure, DfmCanvas.TextStyle.FACT_DETAIL);
            }
            return;
        }
        if (node.type() == NodeType.DESCRIPTIVE) {
            canvas.descriptiveBox(node.x - DESCRIPTIVE_WIDTH / 2, node.y - DESCRIPTIVE_HEIGHT / 2,
                DESCRIPTIVE_WIDTH, DESCRIPTIVE_HEIGHT);
            canvas.text(node.x, node.y, node.name, DfmCanvas.TextStyle.DESCRIPTIVE);
            canvas.rule(node.x - DESCRIPTIVE_WIDTH / 2, node.y + DESCRIPTIVE_HEIGHT / 2,
                node.x + DESCRIPTIVE_WIDTH / 2, node.y + DESCRIPTIVE_HEIGHT / 2, DfmCanvas.RuleColor.BLACK);
            return;
        }
        canvas.circle(node.x, node.y, LEVEL_RADIUS, true);
        if (node.type() == NodeType.SHARED) {
            canvas.circle(node.x, node.y, LEVEL_RADIUS + SHARED_RING_GAP, false);
        } else if (node.type() == NodeType.CROSS) {
            double arcRadius = LEVEL_RADIUS + SHARED_RING_GAP;
            double start = Math.toDegrees(Math.atan2(4, -arcRadius));
            double end = Math.toDegrees(Math.atan2(arcRadius, -4));
            canvas.arc(node.x, node.y, arcRadius, start, end - start);
        }
        canvas.text(node.x, node.y + 20, node.name, DfmCanvas.TextStyle.LEVEL_NAME);
        if (node.optional()) {
            canvas.rule(node.x - 15, node.y, node.x + 15, node.y, DfmCanvas.RuleColor.BLACK);
        }
    }

    private double factHeight(Node node) {
        return FACT_BASE_HEIGHT + node.measures.size() * 20;
    }

    private enum NodeType {
        FACT, LEVEL, DESCRIPTIVE, SHARED, CROSS
    }

    private static final class Node {
        private final String name;
        private final NodeType type;
        private final boolean optional;
        private final List<String> measures = new ArrayList<>();
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

    private record Link(Node source, Node target, ConnectionType connectionType, boolean optional, String label) {
    }

    private record Graph(List<Node> nodes, List<Link> links) {
    }
}
