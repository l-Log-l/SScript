package ai.log.sscript.runtime;

import ai.log.sscript.engine.interpreter.Environment;
import ai.log.sscript.engine.interpreter.Interpreter;
import ai.log.sscript.engine.interpreter.ScriptValue;
import ai.log.sscript.engine.parser.ASTNode.StatementNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a single running script instance.
 * The scheduler ticks through the process's statements step by step.
 */
public class ScriptProcess {

    private static final AtomicInteger ID_COUNTER = new AtomicInteger(1);
    private static final int MAX_QUEUE_SIZE = 50_000;
    private static final int MAX_WHILE_ITERATIONS = 1_000_000_000;

    public static class ForLoopFrame {
        public final String varName;
        public final List<ScriptValue> items;
        public final List<StatementNode> body;
        public final int line;
        public int index = 0;

        public ForLoopFrame(String varName, List<ScriptValue> items, List<StatementNode> body, int line) {
            this.varName = varName;
            this.items = items;
            this.body = body;
            this.line = line;
        }

        public boolean hasNext() {
            return index < items.size();
        }

        public ScriptValue next() {
            return items.get(index++);
        }
    }

    private final int id;
    private final String fileName;
    private final Interpreter interpreter;
    private final Environment environment;
    private final List<StatementNode> statements;

    private ProcessStatus status = ProcessStatus.RUNNING;
    private int currentIndex = 0;
    private int currentLine = 0;
    private long startTimeMs;

    // Wait state
    private long waitUntilTick = -1;
    private String waitDescription = "";

    // Await state (Stage 3)
    private ScriptProcess parentProcess;
    private ScriptProcess childProcess;
    private String awaitVarName; // variable to store the child's return value
    private ScriptValue awaitResult;

    // Error info
    private String errorMessage;

    // Loop state
    private final Deque<ForLoopFrame> loopStack = new ArrayDeque<>();
    private final Map<String, Integer> whileIterations = new HashMap<>();

    public ScriptProcess(String fileName, Interpreter interpreter, Environment environment,
            List<StatementNode> statements) {
        this.id = ID_COUNTER.getAndIncrement();
        this.fileName = fileName;
        this.interpreter = interpreter;
        this.environment = environment;
        this.statements = statements;
        this.startTimeMs = System.currentTimeMillis();
    }

    // ─── Getters ───────────────────────────────────────────────

    public int getId() {
        return id;
    }

    public String getFileName() {
        return fileName;
    }

    public Interpreter getInterpreter() {
        return interpreter;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public ProcessStatus getStatus() {
        return status;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public int getCurrentLine() {
        return currentLine;
    }

    public long getStartTimeMs() {
        return startTimeMs;
    }

    public String getWaitDescription() {
        return waitDescription;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public ScriptProcess getParentProcess() {
        return parentProcess;
    }

    public ScriptProcess getChildProcess() {
        return childProcess;
    }

    public String getAwaitVarName() {
        return awaitVarName;
    }

    public ScriptValue getAwaitResult() {
        return awaitResult;
    }

    // ─── Setters ───────────────────────────────────────────────

    public void setStatus(ProcessStatus status) {
        this.status = status;
    }

    public void setCurrentIndex(int index) {
        this.currentIndex = index;
    }

    public void setCurrentLine(int line) {
        this.currentLine = line;
    }

    public void setErrorMessage(String msg) {
        this.errorMessage = msg;
    }

    public void setParentProcess(ScriptProcess parent) {
        this.parentProcess = parent;
    }

    public void setChildProcess(ScriptProcess child) {
        this.childProcess = child;
    }

    public void setAwaitVarName(String varName) {
        this.awaitVarName = varName;
    }

    public void setAwaitResult(ScriptValue result) {
        this.awaitResult = result;
    }

    public void setWaitDescription(String desc) {
        this.waitDescription = desc;
    }

    // ─── Wait logic ────────────────────────────────────────────

    public void startWait(long targetTick, String description) {
        this.status = ProcessStatus.WAITING;
        this.waitUntilTick = targetTick;
        this.waitDescription = description;
    }

    public boolean isWaitExpired(long currentTick) {
        return waitUntilTick >= 0 && currentTick >= waitUntilTick;
    }

    public void clearWait() {
        this.waitUntilTick = -1;
        this.waitDescription = "";
        this.status = ProcessStatus.RUNNING;
    }

    // ─── Progress ──────────────────────────────────────────────

    public boolean hasMoreStatements() {
        return currentIndex < statements.size();
    }

    public StatementNode nextStatement() {
        return statements.get(currentIndex++);
    }

    /**
     * Prepend statements at the current position in the queue.
     * Used by the scheduler to "unroll" loops so wait works inside them.
     */
    public void prependStatements(List<StatementNode> stmts) {
        if (statements.size() - currentIndex + stmts.size() > MAX_QUEUE_SIZE) {
            throw new Interpreter.InterpreterException(
                    "Statement queue overflow (limit " + MAX_QUEUE_SIZE + ") in " + fileName);
        }
        statements.addAll(currentIndex, stmts);
    }

    public void pushLoopFrame(ForLoopFrame frame) {
        loopStack.push(frame);
    }

    public ForLoopFrame peekLoopFrame() {
        return loopStack.peek();
    }

    public ForLoopFrame popLoopFrame() {
        return loopStack.poll();
    }

    public boolean hasLoopFrame() {
        return !loopStack.isEmpty();
    }

    public void incrementWhileIteration(String nodeKey) {
        int count = whileIterations.merge(nodeKey, 1, Integer::sum);
        if (count > MAX_WHILE_ITERATIONS) {
            throw new Interpreter.InterpreterException(
                    "While loop exceeded " + MAX_WHILE_ITERATIONS + " iterations in " + fileName);
        }
    }

    public void resetWhileIteration(String nodeKey) {
        whileIterations.remove(nodeKey);
    }

    // ─── Display ───────────────────────────────────────────────

    public String getElapsedTime() {
        long elapsed = System.currentTimeMillis() - startTimeMs;
        if (elapsed < 1000)
            return elapsed + "ms";
        return String.format("%.1fs", elapsed / 1000.0);
    }

    public Map<String, ScriptValue> getVariables() {
        return environment.getAll();
    }

    public String getStatusDisplay() {
        return switch (status) {
            case IDLE -> "idle";
            case RUNNING -> "running  " + getElapsedTime();
            case WAITING -> "waiting  " + waitDescription;
            case DONE -> "done     " + getElapsedTime();
            case ERROR -> "error    " + (errorMessage != null ? errorMessage : "");
        };
    }
}
