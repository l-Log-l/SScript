package ai.log.sscript.util;

import ai.log.sscript.SScript;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reads .ss script files from the server's sscripts/ directory.
 */
public class ScriptLoader {

    private final Path scriptsDir;

    public ScriptLoader(Path serverDir) {
        this.scriptsDir = serverDir.resolve("sscripts");
    }

    /**
     * Ensure the sscripts directory exists.
     */
    public void ensureDirectory() {
        try {
            if (!Files.exists(scriptsDir)) {
                Files.createDirectories(scriptsDir);
                SScript.LOGGER.info("[SScript] Created sscripts/ directory");
            }
        } catch (IOException e) {
            SScript.LOGGER.error("[SScript] Failed to create sscripts/ directory: {}", e.getMessage());
        }
    }

    /**
     * Read a script file by name (e.g. "main.ss").
     */
    public String readScript(String fileName) throws IOException {
        Path file = scriptsDir.resolve(fileName);
        if (!Files.exists(file)) {
            throw new IOException("Script file not found: " + fileName);
        }
        return Files.readString(file);
    }

    /**
     * List all .ss files in the sscripts directory.
     */
    public List<String> listScripts() {
        try (Stream<Path> stream = Files.list(scriptsDir)) {
            return stream
                    .filter(p -> p.toString().endsWith(".ss"))
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .toList();
        } catch (IOException e) {
            SScript.LOGGER.error("[SScript] Failed to list scripts: {}", e.getMessage());
            return List.of();
        }
    }

    public Path getScriptsDir() {
        return scriptsDir;
    }
}
