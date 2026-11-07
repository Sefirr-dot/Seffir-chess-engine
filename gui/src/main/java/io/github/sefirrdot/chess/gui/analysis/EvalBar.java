package io.github.sefirrdot.chess.gui.analysis;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

/**
 * Vertical evaluation bar: White's share from the bottom (or the top when the board is flipped), with the score
 * written at the end of the side that is ahead. Changes slide smoothly.
 */
public final class EvalBar extends Region {

    private static final double WIDTH = 22;

    private final Rectangle black = new Rectangle();
    private final Rectangle white = new Rectangle();
    private final Rectangle midline = new Rectangle();
    private final Label label = new Label();
    private final DoubleProperty whiteShare = new SimpleDoubleProperty(0.5);
    private final Timeline slide = new Timeline();
    private boolean flipped;

    public EvalBar() {
        getStyleClass().add("eval-bar");
        black.getStyleClass().add("eval-black");
        white.getStyleClass().add("eval-white");
        midline.getStyleClass().add("eval-midline");
        label.getStyleClass().add("eval-label");
        label.setAlignment(Pos.CENTER);
        label.setTextAlignment(TextAlignment.CENTER);
        getChildren().addAll(black, white, midline, label);
        setMinWidth(WIDTH);
        setPrefWidth(WIDTH);
        setMaxWidth(WIDTH);
        whiteShare.addListener((obs, old, now) -> requestLayout());
    }

    public void setFlipped(boolean flipped) {
        this.flipped = flipped;
        requestLayout();
    }

    public void show(Evaluation evaluation) {
        show(evaluation.whiteShare(), evaluation.compactLabel());
    }

    /** @param share White's share, 0..1 */
    public void show(double share, String text) {
        label.setText(text);
        slide.stop();
        slide.getKeyFrames().setAll(new KeyFrame(Duration.millis(300),
                new KeyValue(whiteShare, Math.max(0, Math.min(1, share)), Interpolator.EASE_BOTH)));
        slide.play();
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        double whiteHeight = Math.round(h * whiteShare.get());
        black.setWidth(w);
        black.setHeight(h);
        white.setWidth(w);
        white.setHeight(whiteHeight);
        white.setY(flipped ? 0 : h - whiteHeight);
        midline.setWidth(w);
        midline.setHeight(1);
        midline.setY(Math.floor(h / 2));

        // The score sits at the end belonging to the side that is ahead, in a colour readable on that side.
        boolean whiteAhead = whiteShare.get() >= 0.5;
        boolean atBottom = whiteAhead != flipped;
        label.getStyleClass().removeAll("on-white", "on-black");
        label.getStyleClass().add(whiteAhead ? "on-white" : "on-black");
        double lh = label.prefHeight(w);
        label.resizeRelocate(0, atBottom ? h - lh - 4 : 4, w, lh);
    }

    @Override
    protected double computePrefHeight(double width) {
        return 400;
    }
}
