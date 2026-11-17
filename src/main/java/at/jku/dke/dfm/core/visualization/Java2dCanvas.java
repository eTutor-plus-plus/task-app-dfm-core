package at.jku.dke.dfm.core.visualization;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * {@link DfmCanvas} that draws onto a {@link BufferedImage} with Java2D.
 */
final class Java2dCanvas implements DfmCanvas {
    private static final Color EDGE = new Color(0x999999);
    private static final Color ARROW = new Color(0x222222);
    private static final Color FACT_TITLE = new Color(0x0000EE);
    private static final Color DETAIL = new Color(0x666666);
    private static final Color LABEL = new Color(0x333333);
    private static final Color BLUE = new Color(0x0000FF);
    private static final BasicStroke THIN = new BasicStroke(1f);
    private static final BasicStroke HALO = new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    private final BufferedImage image;
    private final Graphics2D g;
    private final Font regular = new Font(Font.SERIF, Font.PLAIN, 16);
    private final Font bold = regular.deriveFont(Font.BOLD);
    private final Font detail = regular.deriveFont(14f);
    private final Font label = regular.deriveFont(13f);

    Java2dCanvas(int width, int height) {
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setStroke(THIN);
    }

    BufferedImage finish() {
        g.dispose();
        return image;
    }

    @Override
    public void edgeLine(double x1, double y1, double x2, double y2, boolean arrow) {
        g.setColor(EDGE);
        g.draw(new Line2D.Double(x1, y1, x2, y2));
        if (arrow) {
            arrowHead(x2, y2, x2 - x1, y2 - y1);
        }
    }

    @Override
    public void edgeQuad(double x1, double y1, double cx, double cy, double x2, double y2, boolean arrow) {
        g.setColor(EDGE);
        g.draw(new QuadCurve2D.Double(x1, y1, cx, cy, x2, y2));
        if (arrow) {
            arrowHead(x2, y2, x2 - cx, y2 - cy);
        }
    }

    @Override
    public void edgeCubic(double x1, double y1, double c1x, double c1y, double c2x, double c2y,
                          double x2, double y2, boolean arrow) {
        g.setColor(EDGE);
        g.draw(new CubicCurve2D.Double(x1, y1, c1x, c1y, c2x, c2y, x2, y2));
        if (arrow) {
            arrowHead(x2, y2, x2 - c2x, y2 - c2y);
        }
    }

    @Override
    public void optionalMarker(double x1, double y1, double x2, double y2) {
        g.setColor(EDGE);
        g.draw(new Line2D.Double(x1, y1, x2, y2));
    }

    @Override
    public void factBox(double x, double y, double width, double height) {
        var box = new RoundRectangle2D.Double(x, y, width, height, 20, 20);
        g.setColor(Color.WHITE);
        g.fill(box);
        g.setColor(BLUE);
        g.draw(box);
    }

    @Override
    public void descriptiveBox(double x, double y, double width, double height) {
        g.setColor(Color.WHITE);
        g.fill(new java.awt.geom.Rectangle2D.Double(x, y, width, height));
    }

    @Override
    public void rule(double x1, double y1, double x2, double y2, RuleColor color) {
        g.setColor(color == RuleColor.BLUE ? BLUE : Color.BLACK);
        g.draw(new Line2D.Double(x1, y1, x2, y2));
    }

    @Override
    public void circle(double cx, double cy, double radius, boolean filled) {
        var shape = new Ellipse2D.Double(cx - radius, cy - radius, 2 * radius, 2 * radius);
        if (filled) {
            g.setColor(Color.WHITE);
            g.fill(shape);
        }
        g.setColor(BLUE);
        g.draw(shape);
    }

    @Override
    public void arc(double cx, double cy, double radius, double startAngle, double extent) {
        g.setColor(BLUE);
        g.draw(new Arc2D.Double(cx - radius, cy - radius, 2 * radius, 2 * radius, startAngle, extent, Arc2D.OPEN));
    }

    @Override
    public void text(double x, double y, String text, TextStyle style) {
        if (text.isEmpty()) {
            return;
        }
        var font = switch (style) {
            case FACT_TITLE -> bold;
            case FACT_DETAIL -> detail;
            case EDGE_LABEL -> label;
            default -> regular;
        };
        var glyphs = font.createGlyphVector(g.getFontRenderContext(), text);
        double left = style == TextStyle.FACT_DETAIL ? x : x - glyphs.getLogicalBounds().getWidth() / 2;
        var outline = glyphs.getOutline((float) left, (float) y);
        if (style == TextStyle.EDGE_LABEL) {
            g.setColor(Color.WHITE);
            g.setStroke(HALO);
            g.draw(outline);
            g.setStroke(THIN);
        }
        g.setColor(switch (style) {
            case FACT_TITLE -> FACT_TITLE;
            case FACT_DETAIL -> DETAIL;
            case EDGE_LABEL -> LABEL;
            default -> Color.BLACK;
        });
        g.fill(outline);
    }

    private void arrowHead(double x, double y, double dx, double dy) {
        double length = Math.hypot(dx, dy);
        if (length < 1e-9) {
            return;
        }
        double ux = dx / length;
        double uy = dy / length;
        double baseX = x - ux * 5.6;
        double baseY = y - uy * 5.6;
        var head = new Path2D.Double();
        head.moveTo(x + ux * 1.4, y + uy * 1.4);
        head.lineTo(baseX - uy * 3.5, baseY + ux * 3.5);
        head.lineTo(baseX + uy * 3.5, baseY - ux * 3.5);
        head.closePath();
        g.setColor(ARROW);
        g.fill(head);
    }
}
