package io.github.sefirrdot.chess.gui.analysis;

import java.util.Locale;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Engine analysis of the position on the board: evaluation, depth, speed and the best line in SAN. */
public final class AnalysisPanel extends VBox {

    private final Label engine = new Label();
    private final Label evaluation = new Label();
    private final Label stats = new Label();
    private final Label line = new Label();

    public AnalysisPanel() {
        getStyleClass().add("analysis-panel");
        var title = new Label("ANALYSIS");
        title.getStyleClass().add("section-header");
        engine.getStyleClass().add("analysis-engine");
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var header = new HBox(8, title, spacer, engine);
        header.setAlignment(Pos.BASELINE_LEFT);

        evaluation.getStyleClass().add("analysis-eval");
        stats.getStyleClass().add("analysis-stats");
        var scoreRow = new HBox(12, evaluation, stats);
        scoreRow.setAlignment(Pos.BASELINE_LEFT);
        // Messages ("Thinking…", "Checkmate") come without a score: collapse the row instead of leaving a gap.
        scoreRow.visibleProperty().bind(evaluation.textProperty().isNotEmpty());
        scoreRow.managedProperty().bind(scoreRow.visibleProperty());

        line.getStyleClass().add("analysis-line");
        line.setWrapText(true);
        line.setMinHeight(Region.USE_PREF_SIZE);
        line.setMaxWidth(Double.MAX_VALUE);

        getChildren().addAll(header, scoreRow, line);
        setSpacing(6);
    }

    public void showInfo(String engineName, Evaluation eval, int depth, long nps, String sanLine) {
        engine.setText(engineName);
        evaluation.setText(eval.label());
        stats.setText("depth " + depth + (nps > 0 ? "  ·  " + formatNps(nps) : ""));
        line.setText(sanLine);
    }

    /** A message instead of engine output: "Starting engine…", "Checkmate", ... */
    public void showMessage(String engineName, String message) {
        engine.setText(engineName);
        evaluation.setText("");
        stats.setText("");
        line.setText(message);
    }

    static String formatNps(long nps) {
        if (nps >= 1_000_000) {
            return String.format(Locale.ROOT, "%.1f Mn/s", nps / 1_000_000.0);
        }
        return String.format(Locale.ROOT, "%d kn/s", nps / 1000);
    }
}
