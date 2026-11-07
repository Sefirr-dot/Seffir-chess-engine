package io.github.sefirrdot.chess.gui.svg;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.transform.Scale;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Minimal SVG reader for flat icons such as chess piece sets.
 *
 * <p>JavaFX can draw SVG path data ({@link SVGPath}) but cannot load SVG files. This class supports the subset piece
 * sets use: {@code <g>}, {@code <path>}, {@code <circle>} and {@code <ellipse>}, with inherited presentation attributes
 * (fill, stroke, stroke-width, line cap/join, miter limit, fill rule) and a {@code viewBox}. Transforms, gradients and
 * CSS are not supported.
 *
 * <p>Parsing produces plain data ({@link #shapes()}), which is cached; {@link #createNode(double)} builds fresh JavaFX
 * nodes each time, since a node can only appear once in a scene graph.
 */
public final class SvgIcon {

    /** Presentation attributes after inheritance. A {@code null} paint means "none". */
    public record Style(String fill, String stroke, double strokeWidth, String lineCap, String lineJoin,
                        double miterLimit, String fillRule) {

        static final Style DEFAULT = new Style("#000", null, 1, "butt", "miter", 4, "nonzero");

        Style inherit(Element e) {
            return new Style(
                    paint(e, "fill", fill),
                    paint(e, "stroke", stroke),
                    number(e, "stroke-width", strokeWidth),
                    text(e, "stroke-linecap", lineCap),
                    text(e, "stroke-linejoin", lineJoin),
                    number(e, "stroke-miterlimit", miterLimit),
                    text(e, "fill-rule", fillRule));
        }

        private static String paint(Element e, String name, String inherited) {
            if (!e.hasAttribute(name)) {
                return inherited;
            }
            String value = e.getAttribute(name).strip();
            return value.equals("none") ? null : value;
        }

        private static String text(Element e, String name, String inherited) {
            return e.hasAttribute(name) ? e.getAttribute(name).strip() : inherited;
        }

        private static double number(Element e, String name, double inherited) {
            return e.hasAttribute(name) ? Double.parseDouble(e.getAttribute(name).strip()) : inherited;
        }
    }

    public sealed interface ShapeSpec permits PathSpec, CircleSpec, EllipseSpec {
        Style style();
    }

    public record PathSpec(String data, Style style) implements ShapeSpec {
    }

    public record CircleSpec(double cx, double cy, double r, Style style) implements ShapeSpec {
    }

    public record EllipseSpec(double cx, double cy, double rx, double ry, Style style) implements ShapeSpec {
    }

    private final double viewBoxWidth;
    private final List<ShapeSpec> shapes;

    private SvgIcon(double viewBoxWidth, List<ShapeSpec> shapes) {
        this.viewBoxWidth = viewBoxWidth;
        this.shapes = List.copyOf(shapes);
    }

    public static SvgIcon parse(InputStream svg) throws IOException {
        Element root;
        try {
            var factory = DocumentBuilderFactory.newInstance();
            // Icons are trusted resources, but never resolve external entities anyway.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            root = factory.newDocumentBuilder().parse(svg).getDocumentElement();
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Invalid SVG", e);
        }
        if (!root.getTagName().equals("svg")) {
            throw new IOException("Root element is <" + root.getTagName() + ">, expected <svg>");
        }
        List<ShapeSpec> shapes = new ArrayList<>();
        collect(root, Style.DEFAULT.inherit(root), shapes);
        return new SvgIcon(viewBoxWidth(root), shapes);
    }

    public List<ShapeSpec> shapes() {
        return shapes;
    }

    public double viewBoxWidth() {
        return viewBoxWidth;
    }

    /** A new node drawing this icon in a {@code size} x {@code size} box at the origin. */
    public Node createNode(double size) {
        var group = new Group();
        for (ShapeSpec spec : shapes) {
            group.getChildren().add(toShape(spec));
        }
        double scale = size / viewBoxWidth;
        group.getTransforms().add(new Scale(scale, scale, 0, 0));
        // A Group's bounds follow the drawn shapes (strokes included); wrap it so the layout box is exactly the icon box.
        var box = new Pane(group);
        box.setMinSize(size, size);
        box.setPrefSize(size, size);
        box.setMaxSize(size, size);
        return box;
    }

    private static void collect(Element parent, Style style, List<ShapeSpec> out) {
        for (var child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }
            var e = (Element) child;
            Style s = style.inherit(e);
            switch (e.getTagName()) {
                case "g" -> collect(e, s, out);
                case "path" -> out.add(new PathSpec(e.getAttribute("d"), s));
                case "circle" -> out.add(new CircleSpec(num(e, "cx"), num(e, "cy"), num(e, "r"), s));
                case "ellipse" -> out.add(new EllipseSpec(num(e, "cx"), num(e, "cy"), num(e, "rx"), num(e, "ry"), s));
                default -> {
                    // Unsupported elements (defs, title...) are skipped.
                }
            }
        }
    }

    private static double num(Element e, String name) {
        return e.hasAttribute(name) ? Double.parseDouble(e.getAttribute(name)) : 0;
    }

    private static double viewBoxWidth(Element root) {
        if (root.hasAttribute("viewBox")) {
            String[] parts = root.getAttribute("viewBox").strip().split("[\\s,]+");
            return Double.parseDouble(parts[2]);
        }
        if (root.hasAttribute("width")) {
            return Double.parseDouble(root.getAttribute("width").replaceAll("[^0-9.]", ""));
        }
        throw new IllegalArgumentException("SVG has neither viewBox nor width");
    }

    private static Shape toShape(ShapeSpec spec) {
        Shape shape = switch (spec) {
            case PathSpec p -> {
                var path = new SVGPath();
                path.setContent(p.data());
                path.setFillRule("evenodd".equals(p.style().fillRule()) ? FillRule.EVEN_ODD : FillRule.NON_ZERO);
                yield path;
            }
            case CircleSpec c -> new Circle(c.cx(), c.cy(), c.r());
            case EllipseSpec e -> new Ellipse(e.cx(), e.cy(), e.rx(), e.ry());
        };
        Style s = spec.style();
        shape.setFill(s.fill() == null ? null : Color.web(s.fill()));
        shape.setStroke(s.stroke() == null ? null : Color.web(s.stroke()));
        shape.setStrokeWidth(s.strokeWidth());
        shape.setStrokeMiterLimit(s.miterLimit());
        shape.setStrokeLineCap(switch (s.lineCap()) {
            case "round" -> StrokeLineCap.ROUND;
            case "square" -> StrokeLineCap.SQUARE;
            default -> StrokeLineCap.BUTT;
        });
        shape.setStrokeLineJoin(switch (s.lineJoin()) {
            case "round" -> StrokeLineJoin.ROUND;
            case "bevel" -> StrokeLineJoin.BEVEL;
            default -> StrokeLineJoin.MITER;
        });
        return shape;
    }
}
