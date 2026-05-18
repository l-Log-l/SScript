package ai.log.sscript.event;

import ai.log.sscript.SScript;
import ai.log.sscript.engine.interpreter.Environment;
import ai.log.sscript.engine.interpreter.Interpreter;
import ai.log.sscript.engine.interpreter.ScriptValue;
import ai.log.sscript.engine.lexer.Lexer;
import ai.log.sscript.engine.lexer.Token;
import ai.log.sscript.engine.parser.ASTNode.*;
import ai.log.sscript.engine.parser.Parser;
import ai.log.sscript.runtime.ProcessScheduler;
import ai.log.sscript.runtime.ScriptProcess;
import ai.log.sscript.util.ScriptLoader;
import ai.log.sscript.util.ErrorHelper;
import net.minecraft.server.MinecraftServer;

import java.util.*;

/**
 * Manages event handler registration from parsed scripts
 * and dispatches events to spawn new processes.
 */
public class EventManager {

    private static EventManager instance;

    /**
     * Maps event name → list of registered handlers.
     * Each handler holds: the file it came from, function definitions, param names,
     * and body statements.
     */
    private final Map<String, List<EventHandler>> handlers = new HashMap<>();

    public static EventManager getInstance() {
        return instance;
    }

    public static void init() {
        instance = new EventManager();
        SScript.LOGGER.info("[SScript] Event manager initialized");
    }

    /**
     * Register all event handlers from a parsed program.
     */
    public void registerFromProgram(ProgramNode program, Interpreter interpreter, String fileName) {
        for (StatementNode stmt : program.statements()) {
            if (stmt instanceof OnEventNode eventNode) {
                String eventName = eventNode.eventName();
                handlers.computeIfAbsent(eventName, k -> new ArrayList<>())
                        .add(new EventHandler(fileName, eventNode.params(), eventNode.body(), interpreter));
                SScript.LOGGER.debug("[SScript] Registered handler for '{}' from {}", eventName, fileName);
            }
        }
    }

    /**
     * Clear all handlers (used on reload).
     */
    public void clearAll() {
        handlers.clear();
    }

    /**
     * Clear handlers from a specific file.
     */
    public void clearFile(String fileName) {
        for (List<EventHandler> list : handlers.values()) {
            list.removeIf(h -> h.fileName.equals(fileName));
        }
    }

    /**
     * Fire an event, spawning processes for all registered handlers.
     */
    public void fire(String eventName, MinecraftServer server, Object... args) {
        List<EventHandler> list = handlers.get(eventName);
        if (list == null || list.isEmpty())
            return;

        ProcessScheduler scheduler = ProcessScheduler.getInstance();
        if (scheduler == null)
            return;

        for (EventHandler handler : list) {
            // Create environment with event parameters
            Environment env = new Environment();
            for (int i = 0; i < handler.params.size(); i++) {
                Object value = i < args.length ? args[i] : "";
                env.defineLocal(handler.params.get(i), ScriptValue.fromObject(value));
            }

            // Create a new interpreter sharing the same function table
            Interpreter childInterpreter = new Interpreter(server, handler.fileName);
            childInterpreter.getFunctions().putAll(handler.interpreter.getFunctions());

            ScriptProcess spawned = scheduler.submit(handler.fileName, childInterpreter, env, new ArrayList<>(handler.body));
            if (spawned == null) {
                SScript.LOGGER.warn("[SScript] Dropped event handler spawn for '{}' from {} due to scheduler limits",
                        eventName, handler.fileName);
            }
        }
    }

    /**
     * Load all .ss files from sscripts/ and register their event handlers.
     */
    public void loadAllScripts(MinecraftServer server) {
        ScriptLoader loader = SScript.getScriptLoader();
        if (loader == null)
            return;

        clearAll();

        List<String> files = loader.listScripts();
        for (String fileName : files) {
            try {
                String source = loader.readScript(fileName);
                
                // Pre-validate for common mistakes
                List<String> issues = ErrorHelper.validateScript(source);
                if (!issues.isEmpty()) {
                    SScript.LOGGER.warn("[SScript] Potential issues in {} (will try to load anyway):", fileName);
                    for (String issue : issues) {
                        SScript.LOGGER.warn("  {}", issue);
                    }
                }
                
                    // Check for CRITICAL errors - skip this file if found
                    if (ErrorHelper.hasCriticalErrors(issues)) {
                        SScript.LOGGER.error("[SScript] CRITICAL ERRORS in {} - skipping file", fileName);
                        for (String issue : issues) {
                            if (issue.startsWith("[CRITICAL]")) {
                                SScript.LOGGER.error("  {}", issue);
                            }
                        }
                        continue;  // Skip to next file
                    }
                Lexer lexer = new Lexer(source);
                List<Token> tokens = lexer.tokenize();
                Parser parser = new Parser(tokens);
                ProgramNode program = parser.parse();

                Interpreter interpreter = new Interpreter(server, fileName);
                // Register functions
                for (StatementNode stmt : program.statements()) {
                    if (stmt instanceof FuncDefNode funcDef) {
                        interpreter.getFunctions().put(funcDef.name(), funcDef);
                    }
                }

                // Only *.event.ss files are treated as optimized event handler containers.
                if (fileName.endsWith(".event.ss")) {
                    registerFromProgram(program, interpreter, fileName);
                    SScript.LOGGER.info("[SScript] Loaded event handlers from {}", fileName);
                }

                // load.ss is a startup script, executed once when scripts are loaded.
                if ("load.ss".equals(fileName)) {
                    List<StatementNode> executable = program.statements().stream()
                            .filter(s -> !(s instanceof FuncDefNode) && !(s instanceof OnEventNode))
                            .toList();
                    if (!executable.isEmpty()) {
                        ProcessScheduler scheduler = ProcessScheduler.getInstance();
                        if (scheduler != null) {
                            ScriptProcess spawned = scheduler.submit(fileName, interpreter, new Environment(),
                                    new ArrayList<>(executable));
                            if (spawned == null) {
                                SScript.LOGGER.warn("[SScript] Dropped startup script {} due to scheduler limits", fileName);
                            }
                        } else {
                            interpreter.execute(program);
                        }
                        SScript.LOGGER.info("[SScript] Executed startup script {}", fileName);
                    }
                }
            } catch (Exception e) {
                SScript.LOGGER.error("[SScript] Error loading {}: {}", fileName, e.getMessage());
            }
        }

        // Fire the 'load' event
        fire("load", server);
    }

    // ─── Inner class ───────────────────────────────────────────

    private record EventHandler(
            String fileName,
            List<String> params,
            List<StatementNode> body,
            Interpreter interpreter) {
    }
}
