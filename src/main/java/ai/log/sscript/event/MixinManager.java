package ai.log.sscript.event;

import ai.log.sscript.SScript;
import ai.log.sscript.engine.interpreter.Environment;
import ai.log.sscript.engine.interpreter.Interpreter;
import ai.log.sscript.engine.interpreter.ScriptValue;
import ai.log.sscript.engine.lexer.Lexer;
import ai.log.sscript.engine.lexer.Token;
import ai.log.sscript.engine.parser.ASTNode.*;
import ai.log.sscript.engine.parser.Parser;
import ai.log.sscript.runtime.ScriptProcess;
import ai.log.sscript.util.ErrorHelper;
import ai.log.sscript.util.ScriptLoader;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads cancellable mixin scripts from *.mixin.ss files.
 * Handlers run synchronously so they can block actions before they complete.
 */
public class MixinManager {

    private static MixinManager instance;

    private final Map<String, List<MixinHandler>> handlers = new HashMap<>();

    public static MixinManager getInstance() {
        return instance;
    }

    public static void init() {
        instance = new MixinManager();
        SScript.LOGGER.info("[SScript] Mixin manager initialized");
    }

    public void registerFromProgram(ProgramNode program, Interpreter interpreter, String fileName) {
        for (StatementNode stmt : program.statements()) {
            if (stmt instanceof OnEventNode eventNode) {
                handlers.computeIfAbsent(eventNode.eventName(), k -> new ArrayList<>())
                        .add(new MixinHandler(fileName, eventNode.params(), eventNode.body(), interpreter));
                SScript.LOGGER.debug("[SScript] Registered mixin hook for '{}' from {}", eventNode.eventName(), fileName);
            }
        }
    }

    public void clearAll() {
        handlers.clear();
    }

    public void clearFile(String fileName) {
        for (List<MixinHandler> list : handlers.values()) {
            list.removeIf(h -> h.fileName.equals(fileName));
        }
    }

    public boolean fireCancelable(String eventName, MinecraftServer server, Object... args) {
        List<MixinHandler> list = handlers.get(eventName);
        if (list == null || list.isEmpty()) {
            return false;
        }

        for (MixinHandler handler : list) {
            if (runHandler(handler, server, args)) {
                return true;
            }
        }
        return false;
    }

    public void fire(String eventName, MinecraftServer server, Object... args) {
        List<MixinHandler> list = handlers.get(eventName);
        if (list == null || list.isEmpty()) {
            return;
        }

        for (MixinHandler handler : list) {
            runHandler(handler, server, args);
        }
    }

    public void loadAllScripts(MinecraftServer server) {
        ScriptLoader loader = SScript.getScriptLoader();
        if (loader == null) {
            return;
        }

        clearAll();

        List<String> files = loader.listScripts();
        for (String fileName : files) {
            if (!fileName.endsWith(".mixin.ss")) {
                continue;
            }

            try {
                String source = loader.readScript(fileName);

                List<String> issues = ErrorHelper.validateScript(source);
                if (!issues.isEmpty()) {
                    SScript.LOGGER.warn("[SScript] Potential issues in {} (will try to load anyway):", fileName);
                    for (String issue : issues) {
                        SScript.LOGGER.warn("  {}", issue);
                    }
                }

                if (ErrorHelper.hasCriticalErrors(issues)) {
                    SScript.LOGGER.error("[SScript] CRITICAL ERRORS in {} - skipping file", fileName);
                    for (String issue : issues) {
                        if (issue.startsWith("[CRITICAL]")) {
                            SScript.LOGGER.error("  {}", issue);
                        }
                    }
                    continue;
                }

                Lexer lexer = new Lexer(source);
                List<Token> tokens = lexer.tokenize();
                Parser parser = new Parser(tokens);
                ProgramNode program = parser.parse();

                Interpreter interpreter = new Interpreter(server, fileName);
                for (StatementNode stmt : program.statements()) {
                    if (stmt instanceof FuncDefNode funcDef) {
                        interpreter.getFunctions().put(funcDef.name(), funcDef);
                    }
                }

                registerFromProgram(program, interpreter, fileName);
                SScript.LOGGER.info("[SScript] Loaded mixin hooks from {}", fileName);
            } catch (Exception e) {
                SScript.LOGGER.error("[SScript] Error loading {}: {}", fileName, e.getMessage());
            }
        }

        fire("load", server);
    }

    private boolean runHandler(MixinHandler handler, MinecraftServer server, Object... args) {
        Environment env = new Environment();
        for (int i = 0; i < handler.params.size(); i++) {
            Object value = i < args.length ? args[i] : "";
            env.defineLocal(handler.params.get(i), ScriptValue.fromObject(value));
        }

        Interpreter childInterpreter = new Interpreter(server, handler.fileName);
        childInterpreter.getFunctions().putAll(handler.interpreter.getFunctions());
        return childInterpreter.executeInline(handler.body, env);
    }

    private record MixinHandler(String fileName, List<String> params, List<StatementNode> body, Interpreter interpreter) {
    }
}