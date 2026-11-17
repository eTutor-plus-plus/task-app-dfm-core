package at.jku.dke.dfm.core.visualization;

/**
 * Drawing primitives used by {@link DfmVisualizationService}; separates the layout from the actual rendering.
 */
interface DfmCanvas {
    enum TextStyle {
        FACT_TITLE, FACT_DETAIL, DESCRIPTIVE, LEVEL_NAME, EDGE_LABEL
    }

    enum RuleColor {
        BLUE, BLACK
    }

    void edgeLine(double x1, double y1, double x2, double y2, boolean arrow);

    void edgeQuad(double x1, double y1, double cx, double cy, double x2, double y2, boolean arrow);

    void edgeCubic(double x1, double y1, double c1x, double c1y, double c2x, double c2y,
                   double x2, double y2, boolean arrow);

    void optionalMarker(double x1, double y1, double x2, double y2);

    void factBox(double x, double y, double width, double height);

    void descriptiveBox(double x, double y, double width, double height);

    void rule(double x1, double y1, double x2, double y2, RuleColor color);

    void circle(double cx, double cy, double radius, boolean filled);

    /**
     * Draws an arc; angles in degrees, counterclockwise as displayed.
     */
    void arc(double cx, double cy, double radius, double startAngle, double extent);

    /**
     * Draws text with its baseline at {@code y}; centered at {@code x}, except for {@link TextStyle#FACT_DETAIL},
     * which starts at {@code x}.
     */
    void text(double x, double y, String text, TextStyle style);
}
