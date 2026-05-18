package ai.log.sscript.command;

import ai.log.sscript.SScript;
import ai.log.sscript.engine.interpreter.Environment;
import ai.log.sscript.engine.interpreter.Interpreter;
import ai.log.sscript.engine.interpreter.ScriptValue;
import ai.log.sscript.engine.lexer.Lexer;
import ai.log.sscript.engine.lexer.Token;
import ai.log.sscript.engine.parser.ASTNode;
import ai.log.sscript.engine.parser.ASTNode.*;
import ai.log.sscript.engine.parser.Parser;
import ai.log.sscript.runtime.ProcessStatus;
import ai.log.sscript.runtime.ProcessScheduler;
import ai.log.sscript.runtime.ScriptProcess;
import ai.log.sscript.util.ScriptLoader;
import ai.log.sscript.util.ErrorHelper;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registers all /sscript commands.
 */
public class SScriptCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher);
        });
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("sscript")
                        // /sscript run <file>
                        .then(CommandManager.literal("run")
                                .then(CommandManager.argument("file", StringArgumentType.string())
                                        .executes(ctx -> runScript(ctx, false))
                                        // /sscript run <file> function <name> [args...]
                                        .then(CommandManager.literal("function")
                                                .then(CommandManager.argument("funcName", StringArgumentType.string())
                                                        .executes(ctx -> runScript(ctx, true))
                                                        .then(CommandManager
                                                                .argument("args", StringArgumentType.greedyString())
                                                                .executes(ctx -> runScript(ctx, true)))))))
                        // /sscript monitor
                        .then(CommandManager.literal("monitor")
                                .executes(SScriptCommand::monitorAll)
                            .then(CommandManager.literal("all")
                                .executes(SScriptCommand::monitorVerbose))
                                // /sscript monitor <id>
                                .then(CommandManager.argument("id", IntegerArgumentType.integer(1))
                                        .executes(SScriptCommand::monitorOne)))
                        // /sscript stop <id>
                        .then(CommandManager.literal("stop")
                            .then(CommandManager.literal("all")
                                .executes(SScriptCommand::stopAllProcesses))
                            .then(CommandManager.argument("target", StringArgumentType.word())
                                .executes(SScriptCommand::stopProcessTarget))
                                .then(CommandManager.argument("id", IntegerArgumentType.integer(1))
                                .executes(SScriptCommand::stopProcess))
                            .then(CommandManager.literal("file")
                                .then(CommandManager.argument("file", StringArgumentType.string())
                                    .executes(SScriptCommand::stopProcessByFile))))
                        // /sscript reload <id>
                        .then(CommandManager.literal("reload")
                            .then(CommandManager.literal("all")
                                .executes(SScriptCommand::reloadAllProcesses))
                            .then(CommandManager.argument("target", StringArgumentType.word())
                                .executes(SScriptCommand::reloadProcessTarget))
                                .then(CommandManager.argument("id", IntegerArgumentType.integer(1))
                                .executes(SScriptCommand::reloadProcess))
                            .then(CommandManager.literal("file")
                                .then(CommandManager.argument("file", StringArgumentType.string())
                                    .executes(SScriptCommand::reloadProcessByFile))))
                        // /sscript debug on|off
                        .then(CommandManager.literal("debug")
                            .then(CommandManager.literal("on")
                                .executes(ctx -> setDebug(ctx, true)))
                            .then(CommandManager.literal("off")
                                .executes(ctx -> setDebug(ctx, false))))
                        // /sscript events reload
                        .then(CommandManager.literal("events")
                            .then(CommandManager.literal("reload")
                                .executes(SScriptCommand::reloadEvents)))
                        // /sscript mixins reload
                        .then(CommandManager.literal("mixins")
                            .then(CommandManager.literal("reload")
                                .executes(SScriptCommand::reloadMixins)))
                        .requires(source -> {
                            if (source.getEntity() == null)
                                return true;
                            if (source.getEntity() instanceof ServerPlayerEntity player) {
                                return source.getServer().getPlayerManager().isOperator(player.getPlayerConfigEntry());
                            }
                            return false;
                        }));
    }

    // ─── /sscript run ──────────────────────────────────────────

    private static int runScript(CommandContext<ServerCommandSource> ctx, boolean hasFunction) {
        ServerCommandSource source = ctx.getSource();
        MinecraftServer server = source.getServer();
        String fileName = StringArgumentType.getString(ctx, "file");

        if (!fileName.endsWith(".ss")) {
            fileName += ".ss";
        }

        ScriptLoader loader = SScript.getScriptLoader();
        try {
            String scriptSource = loader.readScript(fileName);

            // Pre-validate for common mistakes before parsing
            List<String> validationIssues = ErrorHelper.validateScript(scriptSource);
            if (!validationIssues.isEmpty()) {
                StringBuilder msg = new StringBuilder("§cPotential issues found:");
                for (String issue : validationIssues) {
                    msg.append("\n  ").append(issue);
                }
                final String finalMsg = msg.toString();
                source.sendFeedback(() -> Text.literal(finalMsg), false);
                // Continue anyway - might still work
            }

                // Check for CRITICAL errors - must reject these
                if (ErrorHelper.hasCriticalErrors(validationIssues)) {
                    StringBuilder msg = new StringBuilder("§c[SScript] CRITICAL ERRORS - Script rejected:");
                    for (String issue : validationIssues) {
                        if (issue.startsWith("[CRITICAL]")) {
                            msg.append("\n  ").append(issue);
                        }
                    }
                    final String finalMsg = msg.toString();
                    source.sendFeedback(() -> Text.literal(finalMsg), false);
                    SScript.LOGGER.error("[SScript] Critical validation errors in {}", fileName);
                    return 0;  // Reject the script
                }
            // Lex → Parse
            Lexer lexer = new Lexer(scriptSource);
            List<Token> tokens = lexer.tokenize();
            Parser parser = new Parser(tokens);
            ProgramNode program = parser.parse();

            Interpreter interpreter = new Interpreter(server, fileName);

            if (hasFunction) {
                // Synchronous: register functions then call specific one
                interpreter.execute(program);
                String funcName = StringArgumentType.getString(ctx, "funcName");

                List<String> args = List.of();
                try {
                    String argsStr = StringArgumentType.getString(ctx, "args");
                    args = Arrays.asList(argsStr.split("\\s+"));
                } catch (IllegalArgumentException ignored) {
                }

                ScriptValue result = interpreter.executeFunction(funcName, args);
                final String resultStr = result.asString();
                source.sendFeedback(() -> Text.literal("§a[SScript] " + funcName + " returned: " + resultStr), false);
            } else {
                // Submit to scheduler for non-blocking execution
                ProcessScheduler scheduler = ProcessScheduler.getInstance();
                if (scheduler != null) {
                    // Register functions first
                    for (StatementNode stmt : program.statements()) {
                        if (stmt instanceof FuncDefNode funcDef) {
                            interpreter.getFunctions().put(funcDef.name(), funcDef);
                        }
                    }
                    // Collect executable statements
                    List<StatementNode> executable = program.statements().stream()
                            .filter(s -> !(s instanceof FuncDefNode) && !(s instanceof OnEventNode))
                            .toList();

                    Environment env = new Environment();
                        int replaced = scheduler.stopByFile(fileName);
                        ScriptProcess process = scheduler.submit(fileName, interpreter, env, new ArrayList<>(executable));
                    final int pid = process.getId();
                    String finalFileName = fileName;
                        if (replaced > 0) {
                        source.sendFeedback(
                            () -> Text.literal("§a[SScript] Restarted " + finalFileName + " (process #" + pid + ", replaced " + replaced + ")"),
                            false);
                        } else {
                        source.sendFeedback(
                            () -> Text.literal("§a[SScript] Started " + finalFileName + " (process #" + pid + ")"), false);
                        }
                } else {
                    // Fallback: synchronous
                    interpreter.execute(program);
                    String finalFileName1 = fileName;
                    source.sendFeedback(() -> Text.literal("§a[SScript] Executed " + finalFileName1), false);
                }
            }

            return 1;
        } catch (Lexer.LexerException e) {
            final String msg = e.getMessage();
            String suggestion = ErrorHelper.suggestKeywordFix(msg);
            String finalMsg = suggestion != null ? msg + "\n  " + suggestion : msg;
            source.sendFeedback(() -> Text.literal("§c[SScript] Lexer error: " + finalMsg), false);
            SScript.LOGGER.error("[SScript] Lexer error in {}: {}", fileName, msg);
            return 0;
        } catch (Parser.ParserException e) {
            final String msg = e.getMessage();
            source.sendFeedback(() -> Text.literal("§c[SScript] Parser error: " + msg), false);
            SScript.LOGGER.error("[SScript] Parser error in {}: {}", fileName, msg);
            return 0;
        } catch (Interpreter.InterpreterException e) {
            final String msg = e.getMessage();
            source.sendFeedback(() -> Text.literal("§c[SScript] Runtime error: " + msg), false);
            SScript.LOGGER.error("[SScript] Runtime error in {}: {}", fileName, msg);
            return 0;
        } catch (Exception e) {
            final String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            source.sendFeedback(() -> Text.literal("§c[SScript] Error: " + msg), false);
            SScript.LOGGER.error("[SScript] Error in {}: {}", fileName, msg);
            return 0;
        }
    }

    // ─── /sscript monitor ──────────────────────────────────────

    private static int monitorAll(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ProcessScheduler scheduler = ProcessScheduler.getInstance();
        if (scheduler == null) {
            source.sendFeedback(() -> Text.literal("§c[SScript] Scheduler not initialized"), false);
            return 0;
        }

        List<ScriptProcess> allProcesses = scheduler.getProcesses();
        List<ScriptProcess> processes = allProcesses.stream()
                .filter(SScriptCommand::isTopLevelUserScript)
                .filter(SScriptCommand::isActiveProcess)
                .toList();

        if (processes.isEmpty()) {
            source.sendFeedback(() -> Text.literal("§e[SScript] No active top-level script processes"), false);
            return 1;
        }

        source.sendFeedback(() -> Text.literal("§6[SScript] Active scripts:"), false);
        for (ScriptProcess p : processes) {
            final String line = String.format("  §f#%d  §b%-15s §a%s",
                    p.getId(), p.getFileName(), p.getStatusDisplay());
            source.sendFeedback(() -> Text.literal(line), false);
        }

        source.sendFeedback(() -> Text.literal("§7Tips: /sscript stop <file|id|all>, /sscript reload <file|id|all>, /sscript monitor all"), false);
        return 1;
    }

    private static int monitorVerbose(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ProcessScheduler scheduler = ProcessScheduler.getInstance();
        if (scheduler == null) {
            source.sendFeedback(() -> Text.literal("§c[SScript] Scheduler not initialized"), false);
            return 0;
        }

        List<ScriptProcess> processes = scheduler.getProcesses();
        if (processes.isEmpty()) {
            source.sendFeedback(() -> Text.literal("§e[SScript] No processes"), false);
            return 1;
        }

        source.sendFeedback(() -> Text.literal("§6[SScript] All processes (debug view):"), false);
        for (ScriptProcess p : processes) {
            final String line = String.format("  §f#%d  §b%-15s §a%s",
                    p.getId(), p.getFileName(), p.getStatusDisplay());
            source.sendFeedback(() -> Text.literal(line), false);

            // Show child process if any
            if (p.getChildProcess() != null) {
                ScriptProcess child = p.getChildProcess();
                final String childLine = String.format("  §f#%d  §b%-15s §a%s §7(await от #%d)",
                        child.getId(), child.getFileName(), child.getStatusDisplay(), p.getId());
                source.sendFeedback(() -> Text.literal(childLine), false);
            }
        }
        return 1;
    }

    private static int monitorOne(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        int id = IntegerArgumentType.getInteger(ctx, "id");

        ProcessScheduler scheduler = ProcessScheduler.getInstance();
        if (scheduler == null) {
            source.sendFeedback(() -> Text.literal("§c[SScript] Scheduler not initialized"), false);
            return 0;
        }

        ScriptProcess p = scheduler.getProcess(id);
        if (p == null) {
            source.sendFeedback(() -> Text.literal("§c[SScript] Process #" + id + " not found"), false);
            return 0;
        }

        source.sendFeedback(() -> Text.literal("§6[SScript] Process #" + p.getId() + ":"), false);
        source.sendFeedback(() -> Text.literal("  §ffile:    §b" + p.getFileName()), false);
        source.sendFeedback(() -> Text.literal("  §fline:    §b" + p.getCurrentLine()), false);
        source.sendFeedback(() -> Text.literal("  §fstatus:  §a" + p.getStatusDisplay()), false);

        // Show variables (safe size to avoid oversized system_chat packets)
        Map<String, ScriptValue> vars = p.getVariables();
        if (!vars.isEmpty()) {
            final int MAX_TOTAL = 1600;
            final int MAX_VALUE = 180;
            StringBuilder sb = new StringBuilder("  §fvars:    §e");
            int shown = 0;
            int total = vars.size();

            for (Map.Entry<String, ScriptValue> e : vars.entrySet()) {
                String key = e.getKey();
                ScriptValue value = e.getValue();
                String rendered = renderVarValue(value, MAX_VALUE);
                String part = key + "=" + rendered;

                if (sb.length() + part.length() + 2 > MAX_TOTAL) {
                    break;
                }

                if (shown > 0) {
                    sb.append(", ");
                }
                sb.append(part);
                shown++;
            }

            if (shown < total) {
                sb.append(", ... (+").append(total - shown).append(" more)");
            }

            final String varsLine = sb.toString();
            source.sendFeedback(() -> Text.literal(varsLine), false);
        }

        if (p.getChildProcess() != null) {
            ScriptProcess child = p.getChildProcess();
            source.sendFeedback(() -> Text.literal("  §fawait:   §b#" + child.getId() + " " + child.getFileName()),
                    false);
        }

        return 1;
    }

    // ─── /sscript stop <id> ────────────────────────────────────

    private static int stopProcess(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        int id = IntegerArgumentType.getInteger(ctx, "id");

        ProcessScheduler scheduler = ProcessScheduler.getInstance();
        if (scheduler == null) {
            source.sendFeedback(() -> Text.literal("§c[SScript] Scheduler not initialized"), false);
            return 0;
        }

        if (scheduler.stop(id)) {
            source.sendFeedback(() -> Text.literal("§a[SScript] Stopped process #" + id), false);
            return 1;
        } else {
            source.sendFeedback(() -> Text.literal("§c[SScript] Process #" + id + " not found"), false);
            return 0;
        }
    }

    // ─── /sscript reload <id> ──────────────────────────────────

    private static int reloadProcess(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        int id = IntegerArgumentType.getInteger(ctx, "id");
        MinecraftServer server = source.getServer();

        ProcessScheduler scheduler = ProcessScheduler.getInstance();
        if (scheduler == null) {
            source.sendFeedback(() -> Text.literal("§c[SScript] Scheduler not initialized"), false);
            return 0;
        }

        ScriptProcess newProcess = scheduler.reload(id, server);
        if (newProcess != null) {
            final int newId = newProcess.getId();
            source.sendFeedback(() -> Text.literal("§a[SScript] Reloaded as process #" + newId), false);
            return 1;
        } else {
            source.sendFeedback(() -> Text.literal("§c[SScript] Failed to reload process #" + id), false);
            return 0;
        }
    }

    // ─── /sscript stop file <name> ─────────────────────────────

    private static int stopProcessByFile(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        String fileName = normalizeFileName(StringArgumentType.getString(ctx, "file"));

        ProcessScheduler scheduler = ProcessScheduler.getInstance();
        if (scheduler == null) {
            source.sendFeedback(() -> Text.literal("§c[SScript] Scheduler not initialized"), false);
            return 0;
        }

        int stopped = scheduler.stopByFile(fileName);
        if (stopped > 0) {
            final String msg = "§a[SScript] Stopped " + stopped + " process(es) for " + fileName;
            source.sendFeedback(() -> Text.literal(msg), false);
            return 1;
        }

        final String msg = "§e[SScript] No active processes for " + fileName;
        source.sendFeedback(() -> Text.literal(msg), false);
        return 0;
    }

    // ─── /sscript reload file <name> ───────────────────────────

    private static int reloadProcessByFile(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        MinecraftServer server = source.getServer();
        String fileName = normalizeFileName(StringArgumentType.getString(ctx, "file"));

        ProcessScheduler scheduler = ProcessScheduler.getInstance();
        if (scheduler == null) {
            source.sendFeedback(() -> Text.literal("§c[SScript] Scheduler not initialized"), false);
            return 0;
        }

        ScriptProcess newProcess = scheduler.reloadByFile(fileName, server);
        if (newProcess != null) {
            final int newId = newProcess.getId();
            final String msg = "§a[SScript] Reloaded " + fileName + " as process #" + newId;
            source.sendFeedback(() -> Text.literal(msg), false);
            return 1;
        }

        final String msg = "§c[SScript] Failed to reload " + fileName;
        source.sendFeedback(() -> Text.literal(msg), false);
        return 0;
    }

    private static int stopProcessTarget(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ProcessScheduler scheduler = ProcessScheduler.getInstance();
        if (scheduler == null) {
            source.sendFeedback(() -> Text.literal("§c[SScript] Scheduler not initialized"), false);
            return 0;
        }

        String target = StringArgumentType.getString(ctx, "target");
        if (target.chars().allMatch(Character::isDigit)) {
            int id = Integer.parseInt(target);
            if (scheduler.stop(id)) {
                source.sendFeedback(() -> Text.literal("§a[SScript] Stopped process #" + id), false);
                return 1;
            }
            source.sendFeedback(() -> Text.literal("§c[SScript] Process #" + id + " not found"), false);
            return 0;
        }
        return stopByFile(source, normalizeFileName(target));
    }

    private static int reloadProcessTarget(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ProcessScheduler scheduler = ProcessScheduler.getInstance();
        if (scheduler == null) {
            source.sendFeedback(() -> Text.literal("§c[SScript] Scheduler not initialized"), false);
            return 0;
        }

        String target = StringArgumentType.getString(ctx, "target");
        if (target.chars().allMatch(Character::isDigit)) {
            int id = Integer.parseInt(target);
            ScriptProcess newProcess = scheduler.reload(id, source.getServer());
            if (newProcess != null) {
                final int newId = newProcess.getId();
                source.sendFeedback(() -> Text.literal("§a[SScript] Reloaded as process #" + newId), false);
                return 1;
            }
            source.sendFeedback(() -> Text.literal("§c[SScript] Failed to reload process #" + id), false);
            return 0;
        }
        return reloadByFile(source, normalizeFileName(target));
    }

    private static int stopAllProcesses(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ProcessScheduler scheduler = ProcessScheduler.getInstance();
        if (scheduler == null) {
            source.sendFeedback(() -> Text.literal("§c[SScript] Scheduler not initialized"), false);
            return 0;
        }

        int stopped = 0;
        for (ScriptProcess p : scheduler.getProcesses()) {
            if (isTopLevelUserScript(p) && isActiveProcess(p) && scheduler.stop(p.getId())) {
                stopped++;
            }
        }

        final String msg = stopped > 0
                ? "§a[SScript] Stopped " + stopped + " active script process(es)"
                : "§e[SScript] No active script processes to stop";
        source.sendFeedback(() -> Text.literal(msg), false);
        return stopped > 0 ? 1 : 0;
    }

    private static int reloadAllProcesses(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ProcessScheduler scheduler = ProcessScheduler.getInstance();
        if (scheduler == null) {
            source.sendFeedback(() -> Text.literal("§c[SScript] Scheduler not initialized"), false);
            return 0;
        }

        Set<String> files = new LinkedHashSet<>();
        for (ScriptProcess p : scheduler.getProcesses()) {
            if (isTopLevelUserScript(p) && isActiveProcess(p)) {
                files.add(p.getFileName());
            }
        }

        if (files.isEmpty()) {
            source.sendFeedback(() -> Text.literal("§e[SScript] No active script processes to reload"), false);
            return 0;
        }

        int reloaded = 0;
        for (String file : files) {
            if (scheduler.reloadByFile(file, source.getServer()) != null) {
                reloaded++;
            }
        }

        final String msg = "§a[SScript] Reloaded " + reloaded + " script file(s)";
        source.sendFeedback(() -> Text.literal(msg), false);
        return reloaded > 0 ? 1 : 0;
    }

    private static int stopByFile(ServerCommandSource source, String fileName) {
        ProcessScheduler scheduler = ProcessScheduler.getInstance();
        if (scheduler == null) {
            source.sendFeedback(() -> Text.literal("§c[SScript] Scheduler not initialized"), false);
            return 0;
        }

        int stopped = scheduler.stopByFile(fileName);
        if (stopped > 0) {
            final String msg = "§a[SScript] Stopped " + stopped + " process(es) for " + fileName;
            source.sendFeedback(() -> Text.literal(msg), false);
            return 1;
        }

        final String msg = "§e[SScript] No active processes for " + fileName;
        source.sendFeedback(() -> Text.literal(msg), false);
        return 0;
    }

    private static int reloadByFile(ServerCommandSource source, String fileName) {
        ProcessScheduler scheduler = ProcessScheduler.getInstance();
        if (scheduler == null) {
            source.sendFeedback(() -> Text.literal("§c[SScript] Scheduler not initialized"), false);
            return 0;
        }

        ScriptProcess newProcess = scheduler.reloadByFile(fileName, source.getServer());
        if (newProcess != null) {
            final int newId = newProcess.getId();
            final String msg = "§a[SScript] Reloaded " + fileName + " as process #" + newId;
            source.sendFeedback(() -> Text.literal(msg), false);
            return 1;
        }

        final String msg = "§c[SScript] Failed to reload " + fileName;
        source.sendFeedback(() -> Text.literal(msg), false);
        return 0;
    }

    private static String normalizeFileName(String fileName) {
        return fileName.endsWith(".ss") ? fileName : fileName + ".ss";
    }

    private static boolean isTopLevelUserScript(ScriptProcess process) {
        return process.getParentProcess() == null && process.getFileName().endsWith(".ss");
    }

    private static boolean isActiveProcess(ScriptProcess process) {
        return process.getStatus() == ProcessStatus.RUNNING || process.getStatus() == ProcessStatus.WAITING;
    }

    private static String renderVarValue(ScriptValue value, int maxLen) {
        if (value == null) {
            return "null";
        }

        String out;
        switch (value.getType()) {
            case LIST -> out = "[list size=" + value.asList().size() + "]";
            case OBJECT -> out = "{object keys=" + value.asObject().size() + "}";
            default -> out = value.asString();
        }

        if (out.length() > maxLen) {
            return out.substring(0, maxLen - 3) + "...";
        }
        return out;
    }

    // ─── /sscript debug on|off ────────────────────────────────

    private static int setDebug(CommandContext<ServerCommandSource> ctx, boolean enabled) {
        ServerCommandSource source = ctx.getSource();
        SScript.setDebugEnabled(enabled);
        String state = enabled ? "§aon" : "§coff";
        source.sendFeedback(() -> Text.literal("§6[SScript] Debug is now " + state), false);
        return 1;
    }

    private static int reloadEvents(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        var manager = ai.log.sscript.event.EventManager.getInstance();
        if (manager == null) {
            source.sendFeedback(() -> Text.literal("§c[SScript] Event manager not initialized"), false);
            return 0;
        }
        manager.loadAllScripts(source.getServer());
        source.sendFeedback(() -> Text.literal("§a[SScript] Event handlers reloaded"), false);
        return 1;
    }

    private static int reloadMixins(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        var manager = ai.log.sscript.event.MixinManager.getInstance();
        if (manager == null) {
            source.sendFeedback(() -> Text.literal("§c[SScript] Mixin manager not initialized"), false);
            return 0;
        }
        manager.loadAllScripts(source.getServer());
        source.sendFeedback(() -> Text.literal("§a[SScript] Mixin hooks reloaded"), false);
        return 1;
    }
}
