package io.github.sefirrdot.chess.gui.svg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.sefirrdot.chess.gui.svg.SvgIcon.CircleSpec;
import io.github.sefirrdot.chess.gui.svg.SvgIcon.EllipseSpec;
import io.github.sefirrdot.chess.gui.svg.SvgIcon.PathSpec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Parsing only: building JavaFX nodes needs the toolkit and is covered by running the app. */
class SvgIconTest {

    private static SvgIcon parse(String svg) throws IOException {
        return SvgIcon.parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void stylesAreInheritedFromGroupsAndOverriddenByChildren() throws IOException {
        SvgIcon icon = parse("""
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 45 45">
                  <g stroke="#000" stroke-width="1.5" stroke-linecap="round" fill="none">
                    <path d="M0 0L10 10" fill="#fff"/>
                    <g stroke-width="3"><circle cx="5" cy="6" r="2"/></g>
                  </g>
                  <ellipse cx="1" cy="2" rx="3" ry="4"/>
                </svg>""");
        assertEquals(45, icon.viewBoxWidth());
        assertEquals(3, icon.shapes().size());

        var path = assertInstanceOf(PathSpec.class, icon.shapes().get(0));
        assertEquals("M0 0L10 10", path.data());
        assertEquals("#fff", path.style().fill());
        assertEquals("#000", path.style().stroke());
        assertEquals(1.5, path.style().strokeWidth());
        assertEquals("round", path.style().lineCap());

        var circle = assertInstanceOf(CircleSpec.class, icon.shapes().get(1));
        assertNull(circle.style().fill(), "fill=none inherited");
        assertEquals(3, circle.style().strokeWidth());
        assertEquals(2, circle.r());

        var ellipse = assertInstanceOf(EllipseSpec.class, icon.shapes().get(2));
        assertEquals("#000", ellipse.style().fill(), "SVG default fill is black");
        assertNull(ellipse.style().stroke(), "SVG default stroke is none");
    }

    @Test
    void widthIsUsedWithoutViewBox() throws IOException {
        assertEquals(45, parse("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"45\" height=\"45\"/>").viewBoxWidth());
    }

    @Test
    void rejectsNonSvgAndDoctypes() {
        assertThrows(IOException.class, () -> parse("<html/>"));
        assertThrows(IOException.class, () -> parse("<!DOCTYPE svg [<!ENTITY x \"y\">]><svg width=\"1\"/>"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"wK", "wQ", "wR", "wB", "wN", "wP", "bK", "bQ", "bR", "bB", "bN", "bP"})
    void everyBundledPieceParses(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(
                "/io/github/sefirrdot/chess/gui/pieces/chessnut/" + name + ".svg")) {
            SvgIcon icon = SvgIcon.parse(in);
            assertFalse(icon.shapes().isEmpty());
            assertEquals(800, icon.viewBoxWidth());
        }
    }
}
