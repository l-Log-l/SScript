package ai.log.sscript.runtime;

import ai.log.sscript.SScript;
import ai.log.sscript.engine.interpreter.Environment;
import ai.log.sscript.engine.interpreter.Interpreter;
import ai.log.sscript.engine.interpreter.ScriptValue;
import ai.log.sscript.engine.lexer.Lexer;
import ai.log.sscript.engine.lexer.Token;
import ai.log.sscript.engine.parser.ASTNode;
import ai.log.sscript.engine.parser.ASTNode.*;
import ai.log.sscript.engine.parser.Parser;
import ai.log.sscript.global.GlobalVariables;
import ai.log.sscript.util.ErrorHelper;
import ai.log.sscript.util.ScriptLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tick-based scheduler that manages ScriptProcess instances.
 * Hooks into Fabric's ServerTickEvents to execute script statements
 * without blocking the server thread.
 */
public class ProcessScheduler {

    private static ProcessScheduler instance;

    private final Map<Integer, ScriptProcess> processes = new ConcurrentHashMap<>();
    private final AtomicInteger gcCounter = new AtomicInteger(0);
    private long currentTick = 0;
    private int spawnedThisTick = 0;

    // How many statements to execute per process per tick (prevents one script from hogging)
    private static final int STATEMENTS_PER_TICK = 50;

    private static final int MAX_PROCESSES_GLOBAL = 500;
    private static final int MAX_SPAWN_PER_TICK = 20;
    private static final int MAX_CHILDREN_PER_PROCESS = 10;

    public static final AtomicLong METRIC_SPAWNED = new AtomicLong();
    public static final AtomicLong METRIC_DROPPED = new AtomicLong();
    public static final AtomicLong METRIC_ACTIVE = new AtomicLong();

    public static ProcessScheduler getInstance() {
        return instance;
    }

    public static void init() {
        instance = new ProcessScheduler();
        ServerTickEvents.END_SERVER_TICK.register(instance::onTick);
        SScript.LOGGER.info("[SScript] Process scheduler initialized");
    }

    // ─── Tick handler ──────────────────────────────────────────

    private void onTick(MinecraftServer server) {
        currentTick++;
        spawnedThisTick = 0;

        if (currentTick % 40 == 0) {
            GlobalVariables globals = GlobalVariables.getInstance();
            if (globals != null) {
                globals.flushIfDirty();
            }
        }

        for (ScriptProcess process : processes.values()) {
            switch (process.getStatus()) {
                case WAITING -> {
                    if (process.isWaitExpired(currentTick)) {
                        process.clearWait();
                        // Resume immediately on the wake-up tick to avoid an extra 1-tick delay.
                        tickProcess(process, server);
                    }
                }
                case RUNNING -> tickProcess(process, server);
                default -> {
                }
            }
        }

        // Clean up finished processes after a while (keep for 60 seconds for monitor)
        if (gcCounter.incrementAndGet() % 20 == 0) {
            processes.values().removeIf(p -> (p.getStatus() == ProcessStatus.DONE || p.getStatus() == ProcessStatus.ERROR)
                    && (System.currentTimeMillis() - p.getStartTimeMs()) > 60_000);
        }

        METRIC_ACTIVE.set(processes.size());
    }

    private void tickProcess(ScriptProcess process, MinecraftServer server) {
        int statementsExecuted = 0;

        while (process.hasMoreStatements()
                && process.getStatus() == ProcessStatus.RUNNING
                && statementsExecuted < STATEMENTS_PER_TICK) {

            StatementNode stmt = process.nextStatement();
            process.setCurrentLine(stmt.line());

            if (stmt instanceof ForNode forNode) {
                expandForLoop(forNode, process);
                continue;
            }

            if (stmt instanceof IterationEndMarker) {
                if (process.hasLoopFrame()) {
                    advanceLoopFrame(process);
                }
                continue;
            }

            if (stmt instanceof LoopEndMarker) {
                continue;
            }

            if (stmt instanceof WhileNode whileNode) {
                expandWhileLoop(whileNode, process);
                continue;
            }

            try {
                process.getInterpreter().executeStatement(stmt, process.getEnvironment(), process);
                statementsExecuted++;
            } catch (Interpreter.ReturnException ret) {
                process.setAwaitResult(ret.getValue());
                process.setStatus(ProcessStatus.DONE);
                resolveAwaitParent(process);
                return;
            } catch (Interpreter.WaitException wait) {
                long waitTicks = (long) (wait.getSeconds() * 20);
                process.startWait(currentTick + waitTicks, String.format("wait %.1fs", wait.getSeconds()));
                return;
            } catch (Interpreter.AwaitChildException awaitChild) {
                process.setStatus(ProcessStatus.WAITING);
                process.setWaitDescription("await #" + awaitChild.getChildId());
                return;
            } catch (Interpreter.BreakException e) {
                skipToLoopEnd(process);
                continue;
            } catch (Interpreter.ContinueException e) {
                skipToIterationEnd(process);
                continue;
            } catch (Exception e) {
                process.setStatus(ProcessStatus.ERROR);
                process.setErrorMessage(e.getMessage());
                SScript.LOGGER.error("[SScript] Runtime error in {} at line {}: {}",
                        process.getFileName(), process.getCurrentLine(), e.getMessage());
                return;
            }
        }

        if (!process.hasMoreStatements() && process.getStatus() == ProcessStatus.RUNNING) {
            process.setStatus(ProcessStatus.DONE);
            process.setAwaitResult(ScriptValue.NULL);
            resolveAwaitParent(process);
        }
    }

    // ─── Loop expansion ────────────────────────────────────────

    private void expandForLoop(ForNode forNode, ScriptProcess process) {
        Environment env = process.getEnvironment();
        Interpreter interp = process.getInterpreter();

        ScriptValue iterable = interp.evaluateExpr(forNode.iterable(), env);
        if (iterable.getType() != ScriptValue.Type.LIST) {
            throw new Interpreter.InterpreterException("for requires iterable list at line " + forNode.line());
        }

        List<ScriptValue> items = new ArrayList<>(iterable.asList());
        if (items.size() > 100_000) {
            throw new Interpreter.InterpreterException(
                    "Loop too large at line " + forNode.line() + " (" + items.size() + " iterations)");
        }

        ScriptProcess.ForLoopFrame frame = new ScriptProcess.ForLoopFrame(
                forNode.varName(), items, forNode.body(), forNode.line());
        process.pushLoopFrame(frame);

        process.prependStatements(List.of(new LoopEndMarker(forNode.line())));
        advanceLoopFrame(process);
    }

    private void advanceLoopFrame(ScriptProcess process) {
        ScriptProcess.ForLoopFrame frame = process.peekLoopFrame();
        if (frame == null) {
            return;
        }

        if (frame.hasNext()) {
            ScriptValue value = frame.next();
            process.getEnvironment().set(frame.varName, value);

            List<StatementNode> oneIteration = new ArrayList<>(frame.body.size() + 1);
            oneIteration.addAll(frame.body);
            oneIteration.add(new IterationEndMarker(frame.line));
            process.prependStatements(oneIteration);
        } else {
            process.popLoopFrame();
        }
    }

    private void expandWhileLoop(WhileNode whileNode, ScriptProcess process) {
        Environment env = process.getEnvironment();
        Interpreter interp = process.getInterpreter();
        String whileKey = process.getFileName() + ":" + whileNode.line();

        process.incrementWhileIteration(whileKey);
        boolean condition = interp.evaluateExpr(whileNode.condition(), env).asBoolean();
        if (condition) {
            List<StatementNode> expanded = new ArrayList<>();
            expanded.addAll(whileNode.body());
            expanded.add(new SleepNode(new ASTNode.NumberLiteral(1, whileNode.line()), whileNode.line()));
            expanded.add(new IterationEndMarker(whileNode.line()));
            expanded.add(whileNode);
            process.prependStatements(expanded);
        } else {
            process.resetWhileIteration(whileKey);
        }
    }

    private void skipToLoopEnd(ScriptProcess process) {
        if (process.hasLoopFrame()) {
            process.popLoopFrame();
        }

        int depth = 0;
        while (process.hasMoreStatements()) {
            StatementNode s = process.nextStatement();
            if (s instanceof ForNode || s instanceof WhileNode) {
                depth++;
            } else if (s instanceof LoopEndMarker) {
                if (depth == 0) {
                    return;
                }
                depth--;
                if (process.hasLoopFrame()) {
                    process.popLoopFrame();
                }
            }
        }
    }

    private void skipToIterationEnd(ScriptProcess process) {
        int depth = 0;
        while (process.hasMoreStatements()) {
            StatementNode s = process.nextStatement();
            if (s instanceof ForNode || s instanceof WhileNode) {
                depth++;
            } else if (s instanceof LoopEndMarker) {
                if (depth == 0) {
                    return;
                }
                depth--;
            } else if (s instanceof IterationEndMarker && depth == 0) {
                if (process.hasLoopFrame()) {
                    advanceLoopFrame(process);
                }
                return;
            }
        }
    }

    // ─── Await parent resolution ───────────────────────────────

    private void resolveAwaitParent(ScriptProcess child) {
        ScriptProcess parent = child.getParentProcess();
        if (parent != null && parent.getStatus() == ProcessStatus.WAITING) {
            String awaitVar = parent.getAwaitVarName();
            if (awaitVar != null) {
                parent.getEnvironment().set(awaitVar,
                        child.getAwaitResult() != null ? child.getAwaitResult() : ScriptValue.NULL);
                parent.setAwaitVarName(null);
            }
            parent.setChildProcess(null);
            parent.clearWait();
        }
    }

    // ─── Process management ────────────────────────────────────

    public ScriptProcess submit(String fileName, Interpreter interpreter, Environment env,
                                List<StatementNode> statements) {
        if (processes.size() >= MAX_PROCESSES_GLOBAL) {
            METRIC_DROPPED.incrementAndGet();
            SScript.LOGGER.warn("[SScript] Process limit reached ({}/{}), dropping spawn for {}",
                    processes.size(), MAX_PROCESSES_GLOBAL, fileName);
            return null;
        }
        if (spawnedThisTick >= MAX_SPAWN_PER_TICK) {
            METRIC_DROPPED.incrementAndGet();
            SScript.LOGGER.warn("[SScript] Spawn rate limit hit ({}/tick), dropping spawn for {}",
                    MAX_SPAWN_PER_TICK, fileName);
            return null;
        }

        spawnedThisTick++;
        METRIC_SPAWNED.incrementAndGet();
        ScriptProcess process = new ScriptProcess(fileName, interpreter, env, statements);
        processes.put(process.getId(), process);
        METRIC_ACTIVE.set(processes.size());
        return process;
    }

    public ScriptProcess submitReplacingFile(String fileName, Interpreter interpreter, Environment env,
                                             List<StatementNode> statements) {
        stopByFile(fileName);
        return submit(fileName, interpreter, env, statements);
    }

    public ScriptProcess submitChild(String funcName, Interpreter interpreter, Environment env,
                                     List<StatementNode> body, ScriptProcess parent, String awaitVarName) {
        long childCount = processes.values().stream()
                .filter(p -> p.getParentProcess() == parent)
                .count();
        if (childCount >= MAX_CHILDREN_PER_PROCESS) {
            throw new Interpreter.InterpreterException(
                    "Too many child processes for " + parent.getFileName());
        }

        ScriptProcess child = submit(funcName + "()", interpreter, env, body);
        if (child == null) {
            throw new Interpreter.InterpreterException(
                    "Unable to spawn child process for " + parent.getFileName() + ": scheduler limits reached");
        }

        child.setParentProcess(parent);
        parent.setChildProcess(child);
        parent.setAwaitVarName(awaitVarName);
        return child;
    }

    public boolean stop(int id) {
        ScriptProcess p = processes.get(id);
        if (p == null) {
            return false;
        }
        if (p.getChildProcess() != null) {
            stop(p.getChildProcess().getId());
        }
        p.setStatus(ProcessStatus.DONE);
        p.setErrorMessage("stopped by user");
        resolveAwaitParent(p);
        return true;
    }

    public int stopByFile(String fileName) {
        int stopped = 0;
        for (ScriptProcess p : processes.values()) {
            if (isTopLevelScript(p)
                    && p.getFileName().equals(fileName)
                    && p.getStatus() != ProcessStatus.DONE
                    && p.getStatus() != ProcessStatus.ERROR) {
                if (stop(p.getId())) {
                    stopped++;
                }
            }
        }
        return stopped;
    }

    public ScriptProcess reload(int id, MinecraftServer server) {
        ScriptProcess old = processes.get(id);
        if (old == null) {
            return null;
        }

        String fileName = old.getFileName();
        stop(id);

        return loadAndSubmit(fileName, server);
    }

    public ScriptProcess reloadByFile(String fileName, MinecraftServer server) {
        stopByFile(fileName);
        return loadAndSubmit(fileName, server);
    }

    private ScriptProcess loadAndSubmit(String fileName, MinecraftServer server) {
        try {
            ScriptLoader loader = SScript.getScriptLoader();
            String source = loader.readScript(fileName);

            List<String> issues = ErrorHelper.validateScript(source);
            if (!issues.isEmpty()) {
                SScript.LOGGER.warn("[SScript] Potential issues when reloading {}:", fileName);
                for (String issue : issues) {
                    SScript.LOGGER.warn("  {}", issue);
                }
            }

            if (ErrorHelper.hasCriticalErrors(issues)) {
                SScript.LOGGER.error("[SScript] CRITICAL ERRORS in {} - reload rejected", fileName);
                for (String issue : issues) {
                    if (issue.startsWith("[CRITICAL]")) {
                        SScript.LOGGER.error("  {}", issue);
                    }
                }
                return null;
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

            List<StatementNode> executable = program.statements().stream()
                    .filter(s -> !(s instanceof FuncDefNode) && !(s instanceof OnEventNode))
                    .toList();

            Environment env = new Environment();
            return submit(fileName, interpreter, env, new ArrayList<>(executable));
        } catch (Exception e) {
            SScript.LOGGER.error("[SScript] Error reloading {}: {}", fileName, e.getMessage());
            return null;
        }
    }

    private boolean isTopLevelScript(ScriptProcess process) {
        return process.getParentProcess() == null && process.getFileName().endsWith(".ss");
    }

    // ─── Query ─────────────────────────────────────────────────

    public List<ScriptProcess> getProcesses() {
        return new ArrayList<>(processes.values());
    }

    public ScriptProcess getProcess(int id) {
        return processes.get(id);
    }

    public long getCurrentTick() {
        return currentTick;
    }
}
