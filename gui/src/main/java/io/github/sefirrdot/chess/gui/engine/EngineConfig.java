package io.github.sefirrdot.chess.gui.engine;

import io.github.sefirrdot.chess.core.EngineInfo;
import io.github.sefirrdot.chess.uci.UciMain;
import java.nio.file.Path;
import java.util.List;

/**
 * How to start an engine process.
 *
 * @param name    display name
 * @param command executable and arguments
 */
public record EngineConfig(String name, List<String> command) {

    public EngineConfig {
        command = List.copyOf(command);
    }

    /**
     * The bundled Seffir engine, started as a separate JVM from this application's own classpath. It is still a real
     * external UCI process, exactly like any other engine: the GUI never calls the engine in-process.
     */
    public static EngineConfig builtIn() {
        return new EngineConfig(EngineInfo.NAME + " (built-in)",
                List.of(javaExecutable(), "-cp", System.getProperty("java.class.path"), UciMain.class.getName()));
    }

    /**
     * Any UCI engine: a native executable such as Stockfish, a script, or a runnable jar (started with this JVM's
     * {@code java -jar}) - for instance {@code seffir.jar} built from another branch, to test two versions against
     * each other.
     */
    public static EngineConfig executable(Path path) {
        String fileName = path.getFileName().toString();
        String name = fileName.replaceFirst("\\.(exe|bat|cmd|sh|jar)$", "");
        String absolute = path.toAbsolutePath().toString();
        return fileName.toLowerCase().endsWith(".jar")
                ? new EngineConfig(name, List.of(javaExecutable(), "-jar", absolute))
                : new EngineConfig(name, List.of(absolute));
    }

    private static String javaExecutable() {
        String java = ProcessHandle.current().info().command()
                .orElse(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        // Prefer java over javaw so the child has standard streams wired the usual way.
        return java.replaceFirst("(?i)javaw(\\.exe)?$", "java$1");
    }

    @Override
    public String toString() {
        return name;
    }
}
