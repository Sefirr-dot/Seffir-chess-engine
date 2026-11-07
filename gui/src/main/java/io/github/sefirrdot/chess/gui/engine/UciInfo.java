package io.github.sefirrdot.chess.gui.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * One {@code info} line sent by a UCI engine while it thinks. Fields the engine did not send are absent
 * ({@code -1} / {@code null} / empty list).
 *
 * @param depth         search depth, or -1
 * @param scoreCp       score in centipawns from the engine's side, or null
 * @param mateIn        mate in N moves (negative: getting mated), or null
 * @param nodes         nodes searched, or -1
 * @param nps           nodes per second, or -1
 * @param timeMillis    time spent, or -1
 * @param pv            principal variation in UCI notation
 */
public record UciInfo(int depth, Integer scoreCp, Integer mateIn, long nodes, long nps, long timeMillis,
                      List<String> pv) {

    /** Parses an {@code info ...} line; empty for other lines and for info lines without depth or score. */
    public static Optional<UciInfo> parse(String line) {
        String[] t = line.strip().split("\\s+");
        if (t.length == 0 || !t[0].equals("info")) {
            return Optional.empty();
        }
        int depth = -1;
        Integer cp = null;
        Integer mate = null;
        long nodes = -1;
        long nps = -1;
        long time = -1;
        List<String> pv = new ArrayList<>();
        try {
            for (int i = 1; i < t.length; i++) {
                switch (t[i]) {
                    case "depth" -> depth = Integer.parseInt(t[++i]);
                    case "nodes" -> nodes = Long.parseLong(t[++i]);
                    case "nps" -> nps = Long.parseLong(t[++i]);
                    case "time" -> time = Long.parseLong(t[++i]);
                    case "score" -> {
                        String kind = t[++i];
                        int value = Integer.parseInt(t[++i]);
                        if (kind.equals("cp")) {
                            cp = value;
                        } else if (kind.equals("mate")) {
                            mate = value;
                        }
                        // "lowerbound"/"upperbound" may follow; they are skipped as unknown tokens.
                    }
                    case "pv" -> {
                        for (i = i + 1; i < t.length; i++) {
                            pv.add(t[i]);
                        }
                    }
                    case "string" -> i = t.length;  // free text until the end of the line
                    case "seldepth", "multipv", "currmovenumber", "hashfull", "tbhits", "cpuload" -> i++;
                    case "currmove", "refutation", "currline" -> i++;
                    default -> {
                        // Unknown token: ignore.
                    }
                }
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return Optional.empty();
        }
        if (depth < 0 || (cp == null && mate == null)) {
            return Optional.empty();
        }
        return Optional.of(new UciInfo(depth, cp, mate, nodes, nps, time, List.copyOf(pv)));
    }

    /** Human-readable score from the engine's point of view: {@code +0.35}, {@code -1.20}, {@code #3}, {@code #-2}. */
    public String formatScore() {
        if (mateIn != null) {
            return "#" + mateIn;
        }
        return String.format(Locale.ROOT, "%+.2f", scoreCp / 100.0);
    }
}
