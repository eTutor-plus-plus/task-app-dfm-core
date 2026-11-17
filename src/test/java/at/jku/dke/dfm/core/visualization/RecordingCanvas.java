package at.jku.dke.dfm.core.visualization;

import java.util.ArrayList;
import java.util.List;

/**
 * Records the drawing primitives for assertions on the layout.
 */
final class RecordingCanvas implements DfmCanvas {
    record Line(double x1, double y1, double x2, double y2, boolean arrow) {
    }

    record Quad(double x1, double y1, double cx, double cy, double x2, double y2, boolean arrow) {
    }

    record Cubic(double x1, double y1, double c1x, double c1y, double c2x, double c2y, double x2, double y2,
                 boolean arrow) {
    }

    record Marker(double x1, double y1, double x2, double y2) {
    }

    record Box(double x, double y, double width, double height) {
    }

    record Rule(double x1, double y1, double x2, double y2, RuleColor color) {
    }

    record Circle(double cx, double cy, double radius, boolean filled) {
    }

    record Arc(double cx, double cy, double radius, double start, double extent) {
    }

    record Text(double x, double y, String text, TextStyle style) {
    }

    final List<Line> lines = new ArrayList<>();
    final List<Quad> quads = new ArrayList<>();
    final List<Cubic> cubics = new ArrayList<>();
    final List<Marker> markers = new ArrayList<>();
    final List<Box> factBoxes = new ArrayList<>();
    final List<Box> descriptiveBoxes = new ArrayList<>();
    final List<Rule> rules = new ArrayList<>();
    final List<Circle> circles = new ArrayList<>();
    final List<Arc> arcs = new ArrayList<>();
    final List<Text> texts = new ArrayList<>();

    List<Text> texts(TextStyle style) {
        return texts.stream().filter(text -> text.style() == style).toList();
    }

    boolean hasText(String value) {
        return texts.stream().anyMatch(text -> text.text().equals(value));
    }

    boolean hasArrow() {
        return lines.stream().anyMatch(Line::arrow) || quads.stream().anyMatch(Quad::arrow)
            || cubics.stream().anyMatch(Cubic::arrow);
    }

    /**
     * Everything that is drawn as a straight line, including markers and rules.
     */
    int lineCount() {
        return lines.size() + markers.size() + rules.size();
    }

    List<Circle> levelCircles() {
        return circles.stream().filter(Circle::filled).toList();
    }

    @Override
    public void edgeLine(double x1, double y1, double x2, double y2, boolean arrow) {
        lines.add(new Line(x1, y1, x2, y2, arrow));
    }

    @Override
    public void edgeQuad(double x1, double y1, double cx, double cy, double x2, double y2, boolean arrow) {
        quads.add(new Quad(x1, y1, cx, cy, x2, y2, arrow));
    }

    @Override
    public void edgeCubic(double x1, double y1, double c1x, double c1y, double c2x, double c2y,
                          double x2, double y2, boolean arrow) {
        cubics.add(new Cubic(x1, y1, c1x, c1y, c2x, c2y, x2, y2, arrow));
    }

    @Override
    public void optionalMarker(double x1, double y1, double x2, double y2) {
        markers.add(new Marker(x1, y1, x2, y2));
    }

    @Override
    public void factBox(double x, double y, double width, double height) {
        factBoxes.add(new Box(x, y, width, height));
    }

    @Override
    public void descriptiveBox(double x, double y, double width, double height) {
        descriptiveBoxes.add(new Box(x, y, width, height));
    }

    @Override
    public void rule(double x1, double y1, double x2, double y2, RuleColor color) {
        rules.add(new Rule(x1, y1, x2, y2, color));
    }

    @Override
    public void circle(double cx, double cy, double radius, boolean filled) {
        circles.add(new Circle(cx, cy, radius, filled));
    }

    @Override
    public void arc(double cx, double cy, double radius, double startAngle, double extent) {
        arcs.add(new Arc(cx, cy, radius, startAngle, extent));
    }

    @Override
    public void text(double x, double y, String text, TextStyle style) {
        texts.add(new Text(x, y, text, style));
    }
}
