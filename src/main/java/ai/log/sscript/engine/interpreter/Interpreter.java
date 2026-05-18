package ai.log.sscript.engine.interpreter;

import ai.log.sscript.SScript;
import ai.log.sscript.engine.parser.ASTNode.*;
import ai.log.sscript.global.GlobalVariables;
import ai.log.sscript.runtime.ProcessScheduler;
import ai.log.sscript.runtime.ScriptProcess;
import com.google.gson.Gson;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.text.Text;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtLong;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtShort;
import net.minecraft.nbt.NbtIntArray;
import net.minecraft.nbt.NbtLongArray;
import net.minecraft.nbt.NbtByteArray;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.util.ErrorReporter;
import net.minecraft.state.property.Property;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import java.util.Comparator;

/**
 * Walks the SScript AST and executes statements.
 * Supports synchronous execution and tick-based stepping via ProcessScheduler.
 */
public class Interpreter {

    private final MinecraftServer server;
    private final Map<String, FuncDefNode> functions = new HashMap<>();
    private final String fileName;
    private static final Gson GSON = new Gson();
    private static final Duration DEFAULT_HTTP_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration MAX_HTTP_TIMEOUT = Duration.ofSeconds(60);

    // Safety: max iterations per loop to prevent server freeze
    private static final int MAX_LOOP_ITERATIONS = 100_000;

    public Interpreter(MinecraftServer server, String fileName) {
        this.server = server;
        this.fileName = fileName;
    }

    // ─── Entry Points ──────────────────────────────────────────

    public void execute(ProgramNode program) {
        Environment env = new Environment();
        for (StatementNode stmt : program.statements()) {
            if (stmt instanceof FuncDefNode funcDef) {
                functions.put(funcDef.name(), funcDef);
            }
        }
        for (StatementNode stmt : program.statements()) {
            if (!(stmt instanceof FuncDefNode) && !(stmt instanceof OnEventNode)) {
                executeStatement(stmt, env, null);
            }
        }
    }

    public ScriptValue executeFunction(String funcName, List<String> args) {
        FuncDefNode func = functions.get(funcName);
        if (func == null) {
            throw new InterpreterException("Function not found: " + funcName);
        }
        Environment funcEnv = new Environment();
        for (int i = 0; i < func.params().size(); i++) {
            String val = i < args.size() ? args.get(i) : "null";
            funcEnv.defineLocal(func.params().get(i), parseArgValue(val));
        }
        try {
            executeBlock(func.body(), funcEnv, null);
        } catch (ReturnException e) {
            return e.getValue();
        }
        return ScriptValue.NULL;
    }

    public boolean executeInline(List<StatementNode> body, Environment env) {
        try {
            executeBlock(body, env, null);
            return false;
        } catch (ReturnException e) {
            return !e.getValue().asBoolean();
        }
    }

    /**
     * Execute a single statement — called by ProcessScheduler per tick.
     */
    public void executeStatement(StatementNode stmt, Environment env, ScriptProcess process) {
        switch (stmt) {
            case AssignNode assign -> {
                ScriptValue value = evaluate(assign.value(), env);
                env.set(assign.name(), value);
            }
            case AssignTargetNode assignTarget -> executeAssignTarget(assignTarget, env);
            case AssignCallNode assignCall -> {
                ScriptValue result = callFunction(assignCall.funcName(), assignCall.args(), env, process);
                env.set(assignCall.varName(), result);
            }
            case AssignAwaitNode assignAwait -> {
                if (process != null && ProcessScheduler.getInstance() != null) {
                    spawnAwaitChild(assignAwait.funcName(), assignAwait.args(), env, process, assignAwait.varName());
                    throw new AwaitChildException(process.getChildProcess().getId());
                } else {
                    ScriptValue result = callFunction(assignAwait.funcName(), assignAwait.args(), env, process);
                    env.set(assignAwait.varName(), result);
                }
            }
            case IfNode ifNode -> executeIf(ifNode, env, process);
            case WhileNode whileNode -> executeWhile(whileNode, env, process);
            case ForNode forNode -> executeFor(forNode, env, process);
            case BreakNode ignored -> throw new BreakException();
            case ContinueNode ignored -> throw new ContinueException();
            case CallStatement callStmt -> callFunction(callStmt.funcName(), callStmt.args(), env, process);
            case ReturnNode returnNode -> {
                ScriptValue value = returnNode.value() != null ? evaluate(returnNode.value(), env) : ScriptValue.NULL;
                throw new ReturnException(value);
            }
            case AwaitStatement awaitStmt -> {
                if (process != null && ProcessScheduler.getInstance() != null) {
                    spawnAwaitChild(awaitStmt.funcName(), awaitStmt.args(), env, process, null);
                } else {
                    callFunction(awaitStmt.funcName(), awaitStmt.args(), env, process);
                }
            }
            case RunNode runNode -> executeRun(runNode, env);
            case LogNode logNode -> executeLog(logNode, env);
            case WaitNode waitNode -> executeWait(waitNode, env, process);
            case SleepNode sleepNode -> executeSleep(sleepNode, env, process);
            case SetGlobalNode setGlobal -> executeSetGlobal(setGlobal, env);
            case TryCatchNode tryCatchNode -> executeTryCatch(tryCatchNode, env, process);
            case ExprStatement exprStmt -> executeExprStatement(exprStmt, env, process);
            case FuncDefNode ignored -> { /* already registered */ }
            case OnEventNode ignored -> { /* handled by EventManager */ }
            // Marker nodes (LoopEndMarker, IterationEndMarker) are only used by scheduler
            default -> { /* no-op */ }
        }
    }

    private void executeBlock(List<StatementNode> block, Environment env, ScriptProcess process) {
        for (StatementNode stmt : block) {
            executeStatement(stmt, env, process);
        }
    }

    // ─── if / elif / else ──────────────────────────────────────

    private void executeIf(IfNode ifNode, Environment env, ScriptProcess process) {
        if (evaluate(ifNode.condition(), env).asBoolean()) {
            executeBlock(ifNode.body(), env, process);
            return;
        }
        for (ElifBranch elif : ifNode.elifs()) {
            if (evaluate(elif.condition(), env).asBoolean()) {
                executeBlock(elif.body(), env, process);
                return;
            }
        }
        if (!ifNode.elseBody().isEmpty()) {
            executeBlock(ifNode.elseBody(), env, process);
        }
    }

    // ─── while loop ────────────────────────────────────────────

    private void executeWhile(WhileNode whileNode, Environment env, ScriptProcess process) {
        int iterations = 0;
        while (evaluate(whileNode.condition(), env).asBoolean()) {
            if (++iterations > MAX_LOOP_ITERATIONS) {
                throw new InterpreterException(
                        "Infinite loop detected at line " + whileNode.line() + " after " + MAX_LOOP_ITERATIONS + " iterations");
            }
            try {
                executeBlock(whileNode.body(), env, process);
            } catch (BreakException e) {
                break;
            } catch (ContinueException e) {
                continue;
            }
        }
    }

    // ─── for loop ──────────────────────────────────────────────

    private void executeFor(ForNode forNode, Environment env, ScriptProcess process) {
        int iterations = 0;
        ScriptValue iterable = evaluate(forNode.iterable(), env);
        for (ScriptValue value : toIterable(iterable)) {
            if (++iterations > MAX_LOOP_ITERATIONS) {
                throw new InterpreterException(
                        "Infinite loop detected at line " + forNode.line() + " after " + MAX_LOOP_ITERATIONS + " iterations");
            }
            env.set(forNode.varName(), value);
            try {
                executeBlock(forNode.body(), env, process);
            } catch (BreakException e) {
                break;
            } catch (ContinueException e) {
                continue;
            }
        }
    }

    // ─── Function calls ────────────────────────────────────────

    private ScriptValue callFunction(String name, List<ExprNode> argExprs, Environment env, ScriptProcess process) {
        // ─── Built-in functions ──────────────────────────────
        ScriptValue builtIn = tryBuiltinFunction(name, argExprs, env);
        if (builtIn != null) return builtIn;

        FuncDefNode func = functions.get(name);
        if (func == null) {
            throw new InterpreterException("Undefined function: " + name + " in " + fileName);
        }
        Environment funcEnv = new Environment(env);
        for (int i = 0; i < func.params().size(); i++) {
            ScriptValue val = i < argExprs.size() ? evaluate(argExprs.get(i), env) : ScriptValue.NULL;
            funcEnv.defineLocal(func.params().get(i), val);
        }
        try {
            executeBlock(func.body(), funcEnv, process);
        } catch (ReturnException e) {
            return e.getValue();
        }
        return ScriptValue.NULL;
    }

    /**
     * Handle built-in functions.
     * Returns null if name is not a built-in (falls through to user-defined functions).
     */
    private ScriptValue tryBuiltinFunction(String name, List<ExprNode> argExprs, Environment env) {
        return switch (name) {

            // ─── Player functions ────────────────────────────────────
            case "players" -> {
                // players() → "Steve,Alex,Notch" (comma-separated online player names)
                var playerList = server.getPlayerManager().getPlayerList();
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < playerList.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(playerList.get(i).getName().getString());
                }
                yield ScriptValue.of(sb.toString());
            }
            case "random_player" -> {
                // random_player() → "Steve" (random online player name, or "null")
                var playerList = server.getPlayerManager().getPlayerList();
                if (playerList.isEmpty()) yield ScriptValue.NULL;
                int idx = ThreadLocalRandom.current().nextInt(playerList.size());
                yield ScriptValue.of(playerList.get(idx).getName().getString());
            }
            case "player_count" -> {
                // player_count() → 5
                yield ScriptValue.of((double) server.getPlayerManager().getPlayerList().size());
            }
            case "online" -> {
                // online("Steve") → true/false
                if (argExprs.isEmpty()) throw new InterpreterException("online(name) requires 1 arg");
                String pName = evaluate(argExprs.get(0), env).asString();
                boolean found = server.getPlayerManager().getPlayerList().stream()
                        .anyMatch(p -> p.getName().getString().equals(pName));
                yield ScriptValue.of(found);
            }
            case "has_tag" -> {
                // has_tag(target, "V") → true/false
                if (argExprs.size() < 2) throw new InterpreterException("has_tag(player, tag) requires 2 args");
                ScriptValue targetValue = evaluate(argExprs.get(0), env);
                String tag = evaluate(argExprs.get(1), env).asString();
                yield ScriptValue.of(hasTag(targetValue, tag));
            }
            case "tag_add" -> {
                if (argExprs.size() < 2) throw new InterpreterException("tag_add(name, tag) requires 2 args");
                ScriptValue targetValue = evaluate(argExprs.get(0), env);
                String tag = evaluate(argExprs.get(1), env).asString();
                yield ScriptValue.of(addTag(targetValue, tag));
            }
            case "tag_remove" -> {
                if (argExprs.size() < 2) throw new InterpreterException("tag_remove(name, tag) requires 2 args");
                ScriptValue targetValue = evaluate(argExprs.get(0), env);
                String tag = evaluate(argExprs.get(1), env).asString();
                yield ScriptValue.of(removeTag(targetValue, tag));
            }
            case "effect_give" -> {
                // effect_give(name, id, sec, amp, hide)
                if (argExprs.size() < 5) {
                    throw new InterpreterException("effect_give(name, id, sec, amp, hide) requires 5 args");
                }
                String name2 = evaluate(argExprs.get(0), env).asString();
                String rawId = evaluate(argExprs.get(1), env).asString();
                int seconds = Math.max(0, (int) evaluate(argExprs.get(2), env).asNumber());
                int amplifier = Math.max(0, (int) evaluate(argExprs.get(3), env).asNumber());
                boolean hide = evaluate(argExprs.get(4), env).asBoolean();

                var player = server.getPlayerManager().getPlayer(name2);
                if (player == null) {
                    yield ScriptValue.of(false);
                }

                Identifier effectId = normalizeEffectId(rawId);
                if (effectId == null) {
                    throw new InterpreterException("Invalid effect id: '" + rawId + "'");
                }

                RegistryEntry<StatusEffect> effectEntry = Registries.STATUS_EFFECT.getEntry(effectId).orElse(null);
                if (effectEntry == null) {
                    throw new InterpreterException("Unknown effect id: '" + effectId + "'");
                }

                int durationTicks = seconds * 20;
                StatusEffectInstance instance = new StatusEffectInstance(
                    effectEntry,
                        durationTicks,
                        amplifier,
                        false,
                        !hide,
                        !hide
                );
                yield ScriptValue.of(player.addStatusEffect(instance));
            }
            case "effect_clear" -> {
                // effect_clear(name, id)
                if (argExprs.size() < 2) throw new InterpreterException("effect_clear(name, id) requires 2 args");
                String name2 = evaluate(argExprs.get(0), env).asString();
                String rawId = evaluate(argExprs.get(1), env).asString();

                var player = server.getPlayerManager().getPlayer(name2);
                if (player == null) {
                    yield ScriptValue.of(false);
                }

                Identifier effectId = normalizeEffectId(rawId);
                if (effectId == null) {
                    throw new InterpreterException("Invalid effect id: '" + rawId + "'");
                }

                RegistryEntry<StatusEffect> effectEntry = Registries.STATUS_EFFECT.getEntry(effectId).orElse(null);
                if (effectEntry == null) {
                    throw new InterpreterException("Unknown effect id: '" + effectId + "'");
                }

                yield ScriptValue.of(player.removeStatusEffect(effectEntry));
            }
            case "player_tags" -> {
                // player_tags(target) → "V,admin,vip" (comma-separated)
                if (argExprs.isEmpty()) throw new InterpreterException("player_tags(player) requires 1 arg");
                ScriptValue targetValue = evaluate(argExprs.get(0), env);
                Entity entity = resolveEntityReference(targetValue);
                if (entity == null) yield ScriptValue.of("");
                yield ScriptValue.of(String.join(",", entity.getCommandTags()));
            }
            case "exec" -> {
                // exec(command) → {status, result, command, output, error?}
                if (argExprs.isEmpty()) throw new InterpreterException("exec(command) requires 1 arg");
                String command = evaluate(argExprs.get(0), env).asString();
                try {
                    var cmdResult = executeCommandWithOutput(command);
                    Map<String, ScriptValue> result = new HashMap<>();
                    result.put("status", ScriptValue.of("success"));
                    result.put("result", ScriptValue.of((double) cmdResult.exitCode));
                    result.put("command", ScriptValue.of(command));
                    result.put("output", ScriptValue.of(cmdResult.output));
                    yield ScriptValue.ofObject(result);
                } catch (InterpreterException e) {
                    Map<String, ScriptValue> result = new HashMap<>();
                    result.put("status", ScriptValue.of("error"));
                    result.put("result", ScriptValue.of(0.0));
                    result.put("error", ScriptValue.of(e.getMessage()));
                    result.put("command", ScriptValue.of(command));
                    result.put("output", ScriptValue.of(""));
                    yield ScriptValue.ofObject(result);
                }
            }

            case "vec2" -> {
                if (argExprs.size() < 2) throw new InterpreterException("vec2(x, y) requires 2 args");
                yield toVectorObject(
                        evaluate(argExprs.get(0), env).asNumber(),
                        evaluate(argExprs.get(1), env).asNumber(),
                        0.0);
            }
            case "vec3" -> {
                if (argExprs.size() < 3) throw new InterpreterException("vec3(x, y, z) requires 3 args");
                yield toVectorObject(
                        evaluate(argExprs.get(0), env).asNumber(),
                        evaluate(argExprs.get(1), env).asNumber(),
                        evaluate(argExprs.get(2), env).asNumber());
            }
            case "vec_add" -> {
                if (argExprs.size() < 2) throw new InterpreterException("vec_add(a, b) requires 2 args");
                double[] a = resolveVector3(evaluate(argExprs.get(0), env));
                double[] b = resolveVector3(evaluate(argExprs.get(1), env));
                yield toVectorObject(a[0] + b[0], a[1] + b[1], a[2] + b[2]);
            }
            case "vec_sub" -> {
                if (argExprs.size() < 2) throw new InterpreterException("vec_sub(a, b) requires 2 args");
                double[] a = resolveVector3(evaluate(argExprs.get(0), env));
                double[] b = resolveVector3(evaluate(argExprs.get(1), env));
                yield toVectorObject(a[0] - b[0], a[1] - b[1], a[2] - b[2]);
            }
            case "vec_scale" -> {
                if (argExprs.size() < 2) throw new InterpreterException("vec_scale(vec, scalar) requires 2 args");
                double[] v = resolveVector3(evaluate(argExprs.get(0), env));
                double scalar = evaluate(argExprs.get(1), env).asNumber();
                yield toVectorObject(v[0] * scalar, v[1] * scalar, v[2] * scalar);
            }
            case "vec_dot" -> {
                if (argExprs.size() < 2) throw new InterpreterException("vec_dot(a, b) requires 2 args");
                double[] a = resolveVector3(evaluate(argExprs.get(0), env));
                double[] b = resolveVector3(evaluate(argExprs.get(1), env));
                yield ScriptValue.of(a[0] * b[0] + a[1] * b[1] + a[2] * b[2]);
            }
            case "vec_length" -> {
                if (argExprs.isEmpty()) throw new InterpreterException("vec_length(vec) requires 1 arg");
                double[] v = resolveVector3(evaluate(argExprs.get(0), env));
                yield ScriptValue.of(Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]));
            }
            case "vec_distance" -> {
                if (argExprs.size() < 2) throw new InterpreterException("vec_distance(a, b) requires 2 args");
                double[] a = resolveVector3(evaluate(argExprs.get(0), env));
                double[] b = resolveVector3(evaluate(argExprs.get(1), env));
                double dx = a[0] - b[0];
                double dy = a[1] - b[1];
                double dz = a[2] - b[2];
                yield ScriptValue.of(Math.sqrt(dx * dx + dy * dy + dz * dz));
            }
            case "vec_normalize" -> {
                if (argExprs.isEmpty()) throw new InterpreterException("vec_normalize(vec) requires 1 arg");
                double[] v = resolveVector3(evaluate(argExprs.get(0), env));
                double length = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
                if (length == 0) {
                    yield toVectorObject(0, 0, 0);
                }
                yield toVectorObject(v[0] / length, v[1] / length, v[2] / length);
            }

            case "get_targets" -> {
                if (argExprs.isEmpty()) {
                    throw new InterpreterException("get_targets(selector) requires 1 arg");
                }
                String selector = evaluate(argExprs.get(0), env).asString();
                List<ScriptValue> targets = new ArrayList<>();
                for (Entity entity : selectEntities(selector)) {
                    targets.add(toTargetObject(entity, selector));
                }
                yield ScriptValue.ofList(targets);
            }
            case "get_target" -> {
                if (argExprs.isEmpty()) {
                    throw new InterpreterException("get_target(selectorOrName) requires 1 arg");
                }
                String selector = evaluate(argExprs.get(0), env).asString();
                List<Entity> targets = selectEntities(selector);
                if (targets.isEmpty()) {
                    yield ScriptValue.NULL;
                }
                yield toTargetObject(targets.get(0), selector);
            }

            case "tellraw" -> {
                if (argExprs.size() < 2) {
                    throw new InterpreterException("tellraw(target, payload) requires 2 args");
                }
                String selector = evaluate(argExprs.get(0), env).asString();
                ScriptValue payload = evaluate(argExprs.get(1), env);
                String json = payloadToTellrawJson(payload);
                executeCommand("tellraw " + selector + " " + json);
                yield ScriptValue.NULL;
            }

            case "pos" -> {
                if (argExprs.size() < 3) {
                    throw new InterpreterException("pos(x, y, z) requires 3 args");
                }
                double x = evaluate(argExprs.get(0), env).asNumber();
                double y = evaluate(argExprs.get(1), env).asNumber();
                double z = evaluate(argExprs.get(2), env).asNumber();
                yield toPosObject(x, y, z);
            }
            case "get_block" -> {
                if (argExprs.isEmpty()) {
                    throw new InterpreterException("get_block(pos|x,y,z) requires args");
                }
                BlockPos pos = resolveBlockPosArgs(argExprs, env);
                World world = resolveWorldArgs(argExprs, env);
                yield readBlockAt(pos, world);
            }
            case "get_blocks" -> {
                if (argExprs.size() < 2) {
                    throw new InterpreterException("get_blocks(pos1, pos2) requires 2 args");
                }
                BlockPos a = resolveBlockPosArg(evaluate(argExprs.get(0), env));
                BlockPos b = resolveBlockPosArg(evaluate(argExprs.get(1), env));
                World world = resolveWorldArgs(argExprs, env);
                int minX = Math.min(a.getX(), b.getX());
                int maxX = Math.max(a.getX(), b.getX());
                int minY = Math.min(a.getY(), b.getY());
                int maxY = Math.max(a.getY(), b.getY());
                int minZ = Math.min(a.getZ(), b.getZ());
                int maxZ = Math.max(a.getZ(), b.getZ());
                List<ScriptValue> blocks = new ArrayList<>();
                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            blocks.add(readBlockAt(new BlockPos(x, y, z), world));
                        }
                    }
                }
                yield ScriptValue.ofList(blocks);
            }
            case "has_block" -> {
                // has_block(pos1, pos2, blockId[, dimension]) -> true/false
                if (argExprs.size() < 3) {
                    throw new InterpreterException("has_block(pos1, pos2, blockId[, dimension]) requires 3 args");
                }
                BlockPos a = resolveBlockPosArg(evaluate(argExprs.get(0), env));
                BlockPos b = resolveBlockPosArg(evaluate(argExprs.get(1), env));
                String wantedId = evaluate(argExprs.get(2), env).asString();
                World world = resolveWorldArgs(argExprs, env);
                yield ScriptValue.of(areaContainsBlock(a, b, world, wantedId));
            }

            // ─── Math functions ─────────────────────────────────────
            case "range" -> {
                if (argExprs.size() < 2) throw new InterpreterException("range(start, end) requires 2 args");
                int start = (int) evaluate(argExprs.get(0), env).asNumber();
                int end = (int) evaluate(argExprs.get(1), env).asNumber();
                List<ScriptValue> out = new ArrayList<>();
                for (int i = start; i <= end; i++) {
                    out.add(ScriptValue.of((double) i));
                }
                yield ScriptValue.ofList(out);
            }
            case "int" -> {
                if (argExprs.isEmpty()) throw new InterpreterException("int(n) requires 1 arg");
                yield ScriptValue.of(Math.floor(evaluate(argExprs.get(0), env).asNumber()));
            }
            case "sec" -> {
                if (argExprs.isEmpty()) throw new InterpreterException("sec(seconds) requires 1 arg");
                yield ScriptValue.of(evaluate(argExprs.get(0), env).asNumber() * 20.0);
            }
            case "random" -> {
                // random(min, max) → int in [min, max)
                if (argExprs.size() < 2) throw new InterpreterException("random(min, max) requires 2 args");
                int min = (int) evaluate(argExprs.get(0), env).asNumber();
                int max = (int) evaluate(argExprs.get(1), env).asNumber();
                yield ScriptValue.of((double) ThreadLocalRandom.current().nextInt(min, max+1));
            }
            case "floor" -> {
                if (argExprs.isEmpty()) throw new InterpreterException("floor(n) requires 1 arg");
                yield ScriptValue.of(Math.floor(evaluate(argExprs.get(0), env).asNumber()));
            }
            case "ceil" -> {
                if (argExprs.isEmpty()) throw new InterpreterException("ceil(n) requires 1 arg");
                yield ScriptValue.of(Math.ceil(evaluate(argExprs.get(0), env).asNumber()));
            }
            case "abs" -> {
                if (argExprs.isEmpty()) throw new InterpreterException("abs(n) requires 1 arg");
                yield ScriptValue.of(Math.abs(evaluate(argExprs.get(0), env).asNumber()));
            }
            case "min" -> {
                if (argExprs.size() < 2) throw new InterpreterException("min(a, b) requires 2 args");
                yield ScriptValue.of(Math.min(
                        evaluate(argExprs.get(0), env).asNumber(),
                        evaluate(argExprs.get(1), env).asNumber()));
            }
            case "max" -> {
                if (argExprs.size() < 2) throw new InterpreterException("max(a, b) requires 2 args");
                yield ScriptValue.of(Math.max(
                        evaluate(argExprs.get(0), env).asNumber(),
                        evaluate(argExprs.get(1), env).asNumber()));
            }
            case "sqrt" -> {
                if (argExprs.isEmpty()) throw new InterpreterException("sqrt(n) requires 1 arg");
                yield ScriptValue.of(Math.sqrt(evaluate(argExprs.get(0), env).asNumber()));
            }
            case "pow" -> {
                if (argExprs.size() < 2) throw new InterpreterException("pow(base, exp) requires 2 args");
                yield ScriptValue.of(Math.pow(
                        evaluate(argExprs.get(0), env).asNumber(),
                        evaluate(argExprs.get(1), env).asNumber()));
            }
            case "round" -> {
                if (argExprs.isEmpty()) throw new InterpreterException("round(n) requires 1 arg");
                yield ScriptValue.of((double) Math.round(evaluate(argExprs.get(0), env).asNumber()));
            }

            // ─── String functions ───────────────────────────────────
            case "len" -> {
                // len("hello") → 5, len("a,b,c") → 5
                if (argExprs.isEmpty()) throw new InterpreterException("len(str) requires 1 arg");
                yield ScriptValue.of((double) evaluate(argExprs.get(0), env).asString().length());
            }
            case "upper" -> {
                if (argExprs.isEmpty()) throw new InterpreterException("upper(str) requires 1 arg");
                yield ScriptValue.of(evaluate(argExprs.get(0), env).asString().toUpperCase());
            }
            case "lower" -> {
                if (argExprs.isEmpty()) throw new InterpreterException("lower(str) requires 1 arg");
                yield ScriptValue.of(evaluate(argExprs.get(0), env).asString().toLowerCase());
            }
            case "contains" -> {
                // contains("hello world", "world") → true
                if (argExprs.size() < 2) throw new InterpreterException("contains(str, sub) requires 2 args");
                String haystack = evaluate(argExprs.get(0), env).asString();
                String needle = evaluate(argExprs.get(1), env).asString();
                yield ScriptValue.of(haystack.contains(needle));
            }
            case "replace" -> {
                // replace("hello world", "world", "SScript") → "hello SScript"
                if (argExprs.size() < 3) throw new InterpreterException("replace(str, old, new) requires 3 args");
                String src = evaluate(argExprs.get(0), env).asString();
                String old = evaluate(argExprs.get(1), env).asString();
                String rep = evaluate(argExprs.get(2), env).asString();
                yield ScriptValue.of(src.replace(old, rep));
            }
            case "substring" -> {
                // substring("hello", 1, 3) → "el"
                if (argExprs.size() < 2) throw new InterpreterException("substring(str, start[, end]) requires 2-3 args");
                String src = evaluate(argExprs.get(0), env).asString();
                int start = (int) evaluate(argExprs.get(1), env).asNumber();
                if (argExprs.size() >= 3) {
                    int end = (int) evaluate(argExprs.get(2), env).asNumber();
                    yield ScriptValue.of(src.substring(start, Math.min(end, src.length())));
                }
                yield ScriptValue.of(src.substring(start));
            }
            case "starts_with" -> {
                if (argExprs.size() < 2) throw new InterpreterException("starts_with(str, prefix) requires 2 args");
                yield ScriptValue.of(evaluate(argExprs.get(0), env).asString()
                        .startsWith(evaluate(argExprs.get(1), env).asString()));
            }
            case "ends_with" -> {
                if (argExprs.size() < 2) throw new InterpreterException("ends_with(str, suffix) requires 2 args");
                yield ScriptValue.of(evaluate(argExprs.get(0), env).asString()
                        .endsWith(evaluate(argExprs.get(1), env).asString()));
            }
            case "trim" -> {
                if (argExprs.isEmpty()) throw new InterpreterException("trim(str) requires 1 arg");
                yield ScriptValue.of(evaluate(argExprs.get(0), env).asString().trim());
            }
            case "index_of" -> {
                // index_of("hello", "ll") → 2
                if (argExprs.size() < 2) throw new InterpreterException("index_of(str, sub) requires 2 args");
                yield ScriptValue.of((double) evaluate(argExprs.get(0), env).asString()
                        .indexOf(evaluate(argExprs.get(1), env).asString()));
            }
            case "split_get" -> {
                // split_get("a,b,c", ",", 1) → "b"
                if (argExprs.size() < 3) throw new InterpreterException("split_get(str, delim, index) requires 3 args");
                String src = evaluate(argExprs.get(0), env).asString();
                String delim = evaluate(argExprs.get(1), env).asString();
                int idx = (int) evaluate(argExprs.get(2), env).asNumber();
                String[] parts = src.split(java.util.regex.Pattern.quote(delim), -1);
                if (idx >= 0 && idx < parts.length) {
                    yield ScriptValue.of(parts[idx]);
                }
                yield ScriptValue.NULL;
            }
            case "split_count" -> {
                // split_count("a,b,c", ",") → 3
                if (argExprs.size() < 2) throw new InterpreterException("split_count(str, delim) requires 2 args");
                String src = evaluate(argExprs.get(0), env).asString();
                String delim = evaluate(argExprs.get(1), env).asString();
                yield ScriptValue.of((double) src.split(java.util.regex.Pattern.quote(delim)).length);
            }

            // ─── Type functions ─────────────────────────────────────
            case "str" -> {
                // str(42) → "42"
                if (argExprs.isEmpty()) throw new InterpreterException("str(val) requires 1 arg");
                yield ScriptValue.of(evaluate(argExprs.get(0), env).asString());
            }
            case "num" -> {
                // num("42") → 42
                if (argExprs.isEmpty()) throw new InterpreterException("num(val) requires 1 arg");
                ScriptValue val = evaluate(argExprs.get(0), env);
                try {
                    yield ScriptValue.of(Double.parseDouble(val.asString()));
                } catch (NumberFormatException e) {
                    yield ScriptValue.of(0.0);
                }
            }
            case "bool" -> {
                // bool("true") → true, bool(0) → false, bool(1) → true
                if (argExprs.isEmpty()) throw new InterpreterException("bool(val) requires 1 arg");
                yield ScriptValue.of(evaluate(argExprs.get(0), env).asBoolean());
            }
            case "type" -> {
                // type(42) → "number", type("hi") → "string", type(true) → "boolean"
                if (argExprs.isEmpty()) throw new InterpreterException("type(val) requires 1 arg");
                ScriptValue val = evaluate(argExprs.get(0), env);
                yield ScriptValue.of(val.getTypeName());
            }
            // ─── JSON functions ─────────────────────────────────────
            case "json_parse" -> {
                if (argExprs.isEmpty()) throw new InterpreterException("json_parse(text) requires 1 arg");
                String json = evaluate(argExprs.get(0), env).asString();
                try {
                    Object parsed = GSON.fromJson(json, Object.class);
                    yield ScriptValue.fromObject(parsed);
                } catch (Exception e) {
                    throw new InterpreterException("json_parse failed: " + e.getMessage());
                }
            }
            case "json_stringify" -> {
                if (argExprs.isEmpty()) throw new InterpreterException("json_stringify(value) requires 1 arg");
                ScriptValue value = evaluate(argExprs.get(0), env);
                yield ScriptValue.of(GSON.toJson(value.toJavaObject()));
            }

            // ─── HTTP functions ─────────────────────────────────────
            case "http_get" -> {
                if (argExprs.isEmpty()) throw new InterpreterException("http_get(url[, headers[, timeoutSec]]) requires 1-3 args");
                String url = evaluate(argExprs.get(0), env).asString();
                ScriptValue headers = argExprs.size() >= 2 ? evaluate(argExprs.get(1), env) : ScriptValue.NULL;
                ScriptValue timeout = argExprs.size() >= 3 ? evaluate(argExprs.get(2), env) : ScriptValue.NULL;
                yield performHttpRequest("GET", url, ScriptValue.NULL, headers, timeout);
            }
            case "http_post" -> {
                if (argExprs.size() < 2) throw new InterpreterException("http_post(url, body[, headers[, timeoutSec]]) requires 2-4 args");
                String url = evaluate(argExprs.get(0), env).asString();
                ScriptValue body = evaluate(argExprs.get(1), env);
                ScriptValue headers = argExprs.size() >= 3 ? evaluate(argExprs.get(2), env) : ScriptValue.NULL;
                ScriptValue timeout = argExprs.size() >= 4 ? evaluate(argExprs.get(3), env) : ScriptValue.NULL;
                yield performHttpRequest("POST", url, body, headers, timeout);
            }
            case "http_request" -> {
                if (argExprs.size() < 2) {
                    throw new InterpreterException("http_request(method, url[, body[, headers[, timeoutSec]]]) requires 2-5 args");
                }
                String method = evaluate(argExprs.get(0), env).asString();
                String url = evaluate(argExprs.get(1), env).asString();
                ScriptValue body = argExprs.size() >= 3 ? evaluate(argExprs.get(2), env) : ScriptValue.NULL;
                ScriptValue headers = argExprs.size() >= 4 ? evaluate(argExprs.get(3), env) : ScriptValue.NULL;
                ScriptValue timeout = argExprs.size() >= 5 ? evaluate(argExprs.get(4), env) : ScriptValue.NULL;
                yield performHttpRequest(method, url, body, headers, timeout);
            }

            // ─── File functions ─────────────────────────────────────
            case "file_exists" -> {
                if (argExprs.isEmpty()) throw new InterpreterException("file_exists(path) requires 1 arg");
                Path path = resolveScriptPath(evaluate(argExprs.get(0), env).asString());
                yield ScriptValue.of(Files.exists(path));
            }
            case "file_read" -> {
                if (argExprs.isEmpty()) throw new InterpreterException("file_read(path) requires 1 arg");
                Path path = resolveScriptPath(evaluate(argExprs.get(0), env).asString());
                if (!Files.exists(path)) {
                    yield ScriptValue.NULL;
                }
                try {
                    yield ScriptValue.of(Files.readString(path));
                } catch (IOException e) {
                    throw new InterpreterException("file_read failed: " + e.getMessage());
                }
            }
            case "file_write" -> {
                if (argExprs.size() < 2) throw new InterpreterException("file_write(path, content) requires 2 args");
                Path path = resolveScriptPath(evaluate(argExprs.get(0), env).asString());
                String content = evaluate(argExprs.get(1), env).asString();
                try {
                    ensureParentDir(path);
                    Files.writeString(path, content, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                    yield ScriptValue.TRUE;
                } catch (IOException e) {
                    throw new InterpreterException("file_write failed: " + e.getMessage());
                }
            }
            case "file_append" -> {
                if (argExprs.size() < 2) throw new InterpreterException("file_append(path, content) requires 2 args");
                Path path = resolveScriptPath(evaluate(argExprs.get(0), env).asString());
                String content = evaluate(argExprs.get(1), env).asString();
                try {
                    ensureParentDir(path);
                    Files.writeString(path, content, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
                    yield ScriptValue.TRUE;
                } catch (IOException e) {
                    throw new InterpreterException("file_append failed: " + e.getMessage());
                }
            }
            case "file_delete" -> {
                if (argExprs.isEmpty()) throw new InterpreterException("file_delete(path) requires 1 arg");
                Path path = resolveScriptPath(evaluate(argExprs.get(0), env).asString());
                try {
                    yield ScriptValue.of(Files.deleteIfExists(path));
                } catch (IOException e) {
                    throw new InterpreterException("file_delete failed: " + e.getMessage());
                }
            }
            case "file_delete_recursive" -> {
                if (argExprs.isEmpty()) throw new InterpreterException("file_delete_recursive(path) requires 1 arg");
                Path path = resolveScriptPath(evaluate(argExprs.get(0), env).asString());
                try {
                    if (!Files.exists(path)) {
                        yield ScriptValue.FALSE;
                    }
                    if (Files.isDirectory(path)) {
                        try (var stream = Files.walk(path)) {
                            stream.sorted(Comparator.reverseOrder())  // reverse order (delete files before dirs)
                                    .forEach(p -> {
                                        try {
                                            Files.delete(p);
                                        } catch (IOException e) {
                                            throw new RuntimeException(e);
                                        }
                                    });
                        }
                    } else {
                        Files.delete(path);
                    }
                    yield ScriptValue.TRUE;
                } catch (RuntimeException e) {
                    if (e.getCause() instanceof IOException) {
                        throw new InterpreterException("file_delete_recursive failed: " + e.getCause().getMessage());
                    }
                    throw new InterpreterException("file_delete_recursive failed: " + e.getMessage());
                } catch (IOException e) {
                    throw new InterpreterException("file_delete_recursive failed: " + e.getMessage());
                }
            }
            case "file_mkdirs" -> {
                if (argExprs.isEmpty()) throw new InterpreterException("file_mkdirs(path) requires 1 arg");
                Path path = resolveScriptPath(evaluate(argExprs.get(0), env).asString());
                try {
                    Files.createDirectories(path);
                    yield ScriptValue.TRUE;
                } catch (IOException e) {
                    throw new InterpreterException("file_mkdirs failed: " + e.getMessage());
                }
            }
            case "file_read_json" -> {
                if (argExprs.isEmpty()) throw new InterpreterException("file_read_json(path) requires 1 arg");
                Path path = resolveScriptPath(evaluate(argExprs.get(0), env).asString());
                if (!Files.exists(path)) {
                    yield ScriptValue.NULL;
                }
                try {
                    Object parsed = GSON.fromJson(Files.readString(path), Object.class);
                    yield ScriptValue.fromObject(parsed);
                } catch (Exception e) {
                    throw new InterpreterException("file_read_json failed: " + e.getMessage());
                }
            }
            case "file_write_json" -> {
                if (argExprs.size() < 2) throw new InterpreterException("file_write_json(path, value) requires 2 args");
                Path path = resolveScriptPath(evaluate(argExprs.get(0), env).asString());
                ScriptValue value = evaluate(argExprs.get(1), env);
                try {
                    ensureParentDir(path);
                    Files.writeString(path, GSON.toJson(value.toJavaObject()), StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                    yield ScriptValue.TRUE;
                } catch (IOException e) {
                    throw new InterpreterException("file_write_json failed: " + e.getMessage());
                }
            }
            case "file_rename" -> {
                if (argExprs.size() < 2) throw new InterpreterException("file_rename(old_path, new_path) requires 2 args");
                Path oldPath = resolveScriptPath(evaluate(argExprs.get(0), env).asString());
                Path newPath = resolveScriptPath(evaluate(argExprs.get(1), env).asString());
                try {
                    if (!Files.exists(oldPath)) {
                        yield ScriptValue.FALSE;
                    }
                    ensureParentDir(newPath);
                    Files.move(oldPath, newPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    yield ScriptValue.TRUE;
                } catch (IOException e) {
                    throw new InterpreterException("file_rename failed: " + e.getMessage());
                }
            }
            case "file_list" -> {
                if (argExprs.isEmpty()) throw new InterpreterException("file_list(path) requires 1 arg");
                Path path = resolveScriptPath(evaluate(argExprs.get(0), env).asString());
                if (!Files.exists(path) || !Files.isDirectory(path)) {
                    yield ScriptValue.ofList(new ArrayList<>());
                }
                try {
                    List<ScriptValue> files = new ArrayList<>();
                    try (var stream = Files.list(path)) {
                        stream.forEach(p -> files.add(ScriptValue.of(p.getFileName().toString())));
                    }
                    yield ScriptValue.ofList(files);
                } catch (IOException e) {
                    throw new InterpreterException("file_list failed: " + e.getMessage());
                }
            }

            default -> null; // not a built-in → falls through to user-defined
        };
    }

    private ScriptValue performHttpRequest(String methodRaw, String url, ScriptValue bodyValue,
                                           ScriptValue headersValue, ScriptValue timeoutValue) {
        String method = methodRaw == null ? "GET" : methodRaw.trim().toUpperCase();
        if (method.isEmpty()) {
            method = "GET";
        }

        Duration timeout = DEFAULT_HTTP_TIMEOUT;
        if (timeoutValue != null && timeoutValue.getType() != ScriptValue.Type.NULL) {
            double seconds = Math.max(1.0, timeoutValue.asNumber());
            timeout = Duration.ofMillis((long) (seconds * 1000));
        }
        if (timeout.compareTo(MAX_HTTP_TIMEOUT) > 0) {
            timeout = MAX_HTTP_TIMEOUT;
        }

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(timeout);

            Map<String, ScriptValue> headers = headersValue != null && headersValue.getType() == ScriptValue.Type.OBJECT
                    ? headersValue.asObject()
                    : Map.of();

            for (Map.Entry<String, ScriptValue> e : headers.entrySet()) {
                builder.header(e.getKey(), e.getValue().asString());
            }

            String bodyText = "";
            if (bodyValue != null && bodyValue.getType() != ScriptValue.Type.NULL) {
                if (bodyValue.getType() == ScriptValue.Type.OBJECT || bodyValue.getType() == ScriptValue.Type.LIST) {
                    bodyText = GSON.toJson(bodyValue.toJavaObject());
                    if (!headers.containsKey("Content-Type")) {
                        builder.header("Content-Type", "application/json");
                    }
                } else {
                    bodyText = bodyValue.asString();
                }
            }

            if ("GET".equals(method) || "DELETE".equals(method)) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.method(method, HttpRequest.BodyPublishers.ofString(bodyText, StandardCharsets.UTF_8));
            }

            HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build();
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            Map<String, ScriptValue> out = new LinkedHashMap<>();
            out.put("status", ScriptValue.of((double) response.statusCode()));
            out.put("ok", ScriptValue.of(response.statusCode() >= 200 && response.statusCode() < 300));
            out.put("body", ScriptValue.of(response.body()));

            Map<String, ScriptValue> headersOut = new LinkedHashMap<>();
            response.headers().map().forEach((k, v) -> headersOut.put(k, ScriptValue.of(String.join(",", v))));
            out.put("headers", ScriptValue.ofObject(headersOut));

            String contentType = response.headers().firstValue("content-type").orElse("").toLowerCase();
            if (contentType.contains("application/json")) {
                try {
                    Object parsed = GSON.fromJson(response.body(), Object.class);
                    out.put("json", ScriptValue.fromObject(parsed));
                } catch (Exception ignored) {
                    out.put("json", ScriptValue.NULL);
                }
            } else {
                out.put("json", ScriptValue.NULL);
            }

            return ScriptValue.ofObject(out);
        } catch (Exception e) {
            throw new InterpreterException("http_request failed: " + e.getMessage());
        }
    }

    private Path resolveScriptPath(String rawPath) {
        Path input = Path.of(rawPath);
        if (input.isAbsolute()) {
            return input.normalize();
        }
        return server.getRunDirectory().resolve(input).normalize();
    }

    private void ensureParentDir(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private List<Entity> selectEntities(String selector) {
        if (selector == null || selector.isBlank()) {
            return List.of();
        }

        if (selector.startsWith("@")) {
            try {
                var entitySelector = EntityArgumentType.entities().parse(new StringReader(selector));
                ServerCommandSource source = server.getCommandSource();
                return new ArrayList<>(entitySelector.getEntities(source));
            } catch (CommandSyntaxException e) {
                return List.of();
            }
        }

        ServerPlayerEntity byName = server.getPlayerManager().getPlayer(selector);
        if (byName != null) {
            return List.of(byName);
        }

        for (var world : server.getWorlds()) {
            try {
                for (Entity entity : world.iterateEntities()) {
                    if (selector.equals(entity.getUuidAsString()) || selector.equals(entity.getName().getString())) {
                        return List.of(entity);
                    }
                }
            } catch (Exception ignored) {
                // Keep the lookup best-effort if the runtime does not expose entity iteration here.
            }
        }

        return List.of();
    }

    private ScriptValue toPosObject(double x, double y, double z) {
        Map<String, ScriptValue> map = new LinkedHashMap<>();
        map.put("x", ScriptValue.of(x));
        map.put("y", ScriptValue.of(y));
        map.put("z", ScriptValue.of(z));
        map.put("pos", ScriptValue.of(formatPos(x, y, z)));
        return ScriptValue.ofObject(map);
    }

    private ScriptValue toVectorObject(double x, double y, double z) {
        Map<String, ScriptValue> map = new LinkedHashMap<>();
        map.put("x", ScriptValue.of(x));
        map.put("y", ScriptValue.of(y));
        map.put("z", ScriptValue.of(z));
        map.put("len", ScriptValue.of(Math.sqrt(x * x + y * y + z * z)));
        map.put("type", ScriptValue.of("vector"));
        return ScriptValue.ofObject(map);
    }

    private double[] resolveVector3(ScriptValue value) {
        if (value == null) {
            return new double[] {0, 0, 0};
        }
        if (value.getType() == ScriptValue.Type.OBJECT) {
            Map<String, ScriptValue> obj = value.asObject();
            double x = obj.getOrDefault("x", ScriptValue.NULL).asNumber();
            double y = obj.getOrDefault("y", ScriptValue.NULL).asNumber();
            double z = obj.getOrDefault("z", ScriptValue.NULL).asNumber();
            return new double[] {x, y, z};
        }
        if (value.getType() == ScriptValue.Type.LIST) {
            List<ScriptValue> list = value.asList();
            double x = list.size() > 0 ? list.get(0).asNumber() : 0;
            double y = list.size() > 1 ? list.get(1).asNumber() : 0;
            double z = list.size() > 2 ? list.get(2).asNumber() : 0;
            return new double[] {x, y, z};
        }
        return new double[] {value.asNumber(), 0, 0};
    }

    private ScriptValue toTargetObject(Entity entity, String selector) {
        Map<String, ScriptValue> map = new LinkedHashMap<>();
        map.put("name", ScriptValue.of(entity.getName().getString()));
        map.put("uuid", ScriptValue.of(entity.getUuidAsString()));
        map.put("type", ScriptValue.of(entity.getType().toString()));
        map.put("dimension", ScriptValue.of(entity.getEntityWorld().getRegistryKey().getValue().toString()));
        map.put("x", ScriptValue.of(entity.getX()));
        map.put("y", ScriptValue.of(entity.getY()));
        map.put("z", ScriptValue.of(entity.getZ()));
        map.put("pos", ScriptValue.of(formatPos(entity.getX(), entity.getY(), entity.getZ())));

        List<ScriptValue> tags = new ArrayList<>();
        for (String tag : entity.getCommandTags()) {
            tags.add(ScriptValue.of(tag));
        }
        map.put("tags", ScriptValue.ofList(tags));
        map.put("nbt", entity instanceof ServerPlayerEntity player ? extractEntityNbtAsObject(player) : ScriptValue.ofObject(new HashMap<>()));
        map.put("selector", ScriptValue.of(selector));
        return ScriptValue.ofObject(map);
    }

    private Entity resolveEntityReference(ScriptValue value) {
        if (value == null || value.getType() == ScriptValue.Type.NULL) {
            return null;
        }

        if (value.getType() == ScriptValue.Type.OBJECT) {
            ScriptValue selector = value.getMember("selector");
            if (selector.getType() != ScriptValue.Type.NULL) {
                List<Entity> entities = selectEntities(selector.asString());
                if (!entities.isEmpty()) {
                    return entities.get(0);
                }
            }

            ScriptValue name = value.getMember("name");
            if (name.getType() != ScriptValue.Type.NULL) {
                Entity entity = resolveEntityReference(name.asString());
                if (entity != null) {
                    return entity;
                }
            }

            ScriptValue uuid = value.getMember("uuid");
            if (uuid.getType() != ScriptValue.Type.NULL) {
                Entity entity = resolveEntityReference(uuid.asString());
                if (entity != null) {
                    return entity;
                }
            }
        }

        String raw = value.asString();
        if (raw.isBlank()) {
            return null;
        }

        if (raw.startsWith("@")) {
            List<Entity> entities = selectEntities(raw);
            if (!entities.isEmpty()) {
                return entities.get(0);
            }
        }

        ServerPlayerEntity player = server.getPlayerManager().getPlayer(raw);
        if (player != null) {
            return player;
        }

        for (var world : server.getWorlds()) {
            try {
                for (Entity entity : world.iterateEntities()) {
                    if (raw.equals(entity.getUuidAsString()) || raw.equals(entity.getName().getString())) {
                        return entity;
                    }
                }
            } catch (Exception ignored) {
                // Keep the lookup best-effort if the runtime does not expose entity iteration here.
            }
        }

        return null;
    }

    private Entity resolveEntityReference(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        if (raw.startsWith("@")) {
            List<Entity> entities = selectEntities(raw);
            if (!entities.isEmpty()) {
                return entities.get(0);
            }
        }

        ServerPlayerEntity player = server.getPlayerManager().getPlayer(raw);
        if (player != null) {
            return player;
        }

        for (var world : server.getWorlds()) {
            try {
                for (Entity entity : world.iterateEntities()) {
                    if (raw.equals(entity.getUuidAsString()) || raw.equals(entity.getName().getString())) {
                        return entity;
                    }
                }
            } catch (Exception ignored) {
                // Keep the lookup best-effort if the runtime does not expose entity iteration here.
            }
        }

        return null;
    }

    private boolean hasTag(ScriptValue value, String tag) {
        Entity entity = resolveEntityReference(value);
        return entity != null && entity.getCommandTags().contains(tag);
    }

    private boolean addTag(ScriptValue value, String tag) {
        Entity entity = resolveEntityReference(value);
        return entity != null && entity.addCommandTag(tag);
    }

    private boolean removeTag(ScriptValue value, String tag) {
        Entity entity = resolveEntityReference(value);
        return entity != null && entity.removeCommandTag(tag);
    }

    private ScriptValue extractEntityNbtAsObject(ServerPlayerEntity player) {
        try {
            NbtWriteView writeView = NbtWriteView.create(ErrorReporter.EMPTY, server.getRegistryManager());
            player.saveData(writeView);

            NbtCompound nbt = writeView.getNbt();
            if (nbt != null && !nbt.isEmpty()) {
                return nbtToScriptValue(nbt);
            }

            // Fallback path in case saveData skipped for this entity state.
            writeView = NbtWriteView.create(ErrorReporter.EMPTY, server.getRegistryManager());
            player.writeData(writeView);
            nbt = writeView.getNbt();
            if (nbt != null && !nbt.isEmpty()) {
                return nbtToScriptValue(nbt);
            }
        } catch (Exception ignored) {
            // Keep graceful fallback for unusual runtime states.
        }
        return ScriptValue.ofObject(new HashMap<>());
    }

    /**
     * Recursively converts an NbtCompound or NbtElement to ScriptValue.
     * Supports nested objects, lists, and proper primitive type extraction.
     */
    private ScriptValue nbtToScriptValue(NbtElement element) {
        if (element instanceof NbtCompound compound) {
            Map<String, ScriptValue> map = new LinkedHashMap<>();
            for (String key : compound.getKeys()) {
                map.put(key, nbtToScriptValue(compound.get(key)));
            }
            return ScriptValue.ofObject(map);
        } else if (element instanceof NbtList list) {
            List<ScriptValue> values = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                values.add(nbtToScriptValue(list.get(i)));
            }
            return ScriptValue.ofList(values);
        } else if (element instanceof NbtString nbtString) {
            return ScriptValue.of(nbtString.asString().orElse(""));
        } else if (element instanceof NbtInt nbtInt) {
            return ScriptValue.of((double) nbtInt.intValue());
        } else if (element instanceof NbtLong nbtLong) {
            return ScriptValue.of((double) nbtLong.longValue());
        } else if (element instanceof NbtFloat nbtFloat) {
            return ScriptValue.of((double) nbtFloat.floatValue());
        } else if (element instanceof NbtDouble nbtDouble) {
            return ScriptValue.of(nbtDouble.doubleValue());
        } else if (element instanceof NbtByte nbtByte) {
            return ScriptValue.of((double) nbtByte.byteValue());
        } else if (element instanceof NbtShort nbtShort) {
            return ScriptValue.of((double) nbtShort.shortValue());
        } else if (element instanceof NbtByteArray byteArray) {
            List<ScriptValue> values = new ArrayList<>();
            for (byte b : byteArray.getByteArray()) {
                values.add(ScriptValue.of((double) b));
            }
            return ScriptValue.ofList(values);
        } else if (element instanceof NbtIntArray intArray) {
            List<ScriptValue> values = new ArrayList<>();
            for (int i : intArray.getIntArray()) {
                values.add(ScriptValue.of((double) i));
            }
            return ScriptValue.ofList(values);
        } else if (element instanceof NbtLongArray longArray) {
            List<ScriptValue> values = new ArrayList<>();
            for (long l : longArray.getLongArray()) {
                values.add(ScriptValue.of((double) l));
            }
            return ScriptValue.ofList(values);
        } else if (element != null) {
            // Fallback for any other types
            return ScriptValue.of(element.toString());
        }
        return ScriptValue.of("");
    }

    private ScriptValue readBlockAt(BlockPos pos, World world) {
        BlockState state = world.getBlockState(pos);
        Map<String, ScriptValue> map = new LinkedHashMap<>();
        String id = server.getRegistryManager().getOrThrow(RegistryKeys.BLOCK).getId(state.getBlock()).toString();
        map.put("id", ScriptValue.of(id));
        map.put("x", ScriptValue.of((double) pos.getX()));
        map.put("y", ScriptValue.of((double) pos.getY()));
        map.put("z", ScriptValue.of((double) pos.getZ()));
        map.put("dimension", ScriptValue.of(world.getRegistryKey().getValue().toString()));
        map.put("pos", ScriptValue.of(formatPos(pos.getX(), pos.getY(), pos.getZ())));
        map.put("state", extractBlockStateAsObject(state));
        map.put("nbt", extractBlockNbtAsObject(world.getBlockEntity(pos)));
        return ScriptValue.ofObject(map);
    }

    private ScriptValue extractBlockStateAsObject(BlockState state) {
        Map<String, ScriptValue> stateMap = new LinkedHashMap<>();
        try {
            for (net.minecraft.state.property.Property<?> property : state.getProperties()) {
                Comparable<?> value = state.get(property);
                if (value instanceof Boolean b) {
                    stateMap.put(property.getName(), ScriptValue.of(b));
                } else if (value instanceof Number n) {
                    stateMap.put(property.getName(), ScriptValue.of(n.doubleValue()));
                } else if (value instanceof Enum<?> e) {
                    stateMap.put(property.getName(), ScriptValue.of(e.name()));
                } else {
                    stateMap.put(property.getName(), ScriptValue.of(value.toString()));
                }
            }
        } catch (Exception ignored) {
            // Keep graceful fallback for any reflection issues.
        }
        return ScriptValue.ofObject(stateMap);
    }

    private ScriptValue extractBlockNbtAsObject(BlockEntity blockEntity) {
        if (blockEntity == null) {
            return ScriptValue.ofObject(new HashMap<>());
        }

        try {
            NbtCompound nbt = blockEntity.createNbtWithIdentifyingData(server.getRegistryManager());
            if (nbt != null && !nbt.isEmpty()) {
                return nbtToScriptValue(nbt);
            }
        } catch (Exception ignored) {
            // Keep graceful fallback for unusual runtime states.
        }
        return ScriptValue.ofObject(new HashMap<>());
    }

    private boolean areaContainsBlock(BlockPos a, BlockPos b, World world, String wantedId) {
        int minX = Math.min(a.getX(), b.getX());
        int maxX = Math.max(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        int maxY = Math.max(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxZ = Math.max(a.getZ(), b.getZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockState state = world.getBlockState(new BlockPos(x, y, z));
                    String id = server.getRegistryManager().getOrThrow(RegistryKeys.BLOCK)
                            .getId(state.getBlock()).toString();
                    if (wantedId.equals(id)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private World resolveWorldArgs(List<ExprNode> args, Environment env) {
        // get_block(pos, "namespace:dim") or get_blocks(pos1, pos2, "namespace:dim")
        // x,y,z signatures: get_block(x,y,z,"namespace:dim") and get_blocks(...) with 3rd arg.
        if (args.size() == 2 || args.size() == 3 || args.size() == 4) {
            int worldArgIndex = switch (args.size()) {
                case 2 -> 1; // get_block(pos, dim)
                case 3 -> 2; // get_blocks(pos1, pos2, dim)
                case 4 -> 3; // get_block(x, y, z, dim)
                default -> -1;
            };

            if (worldArgIndex >= 0) {
                String worldId = evaluate(args.get(worldArgIndex), env).asString();
                if (!worldId.isBlank()) {
                    Identifier id = Identifier.tryParse(worldId);
                    if (id == null) {
                        throw new InterpreterException("Invalid dimension id: '" + worldId + "'");
                    }

                    RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, id);
                    World world = server.getWorld(key);
                    if (world == null) {
                        throw new InterpreterException("Unknown or unloaded dimension: '" + worldId + "'");
                    }
                    return world;
                }
            }
        }
        return server.getOverworld();
    }

    private BlockPos resolveBlockPosArgs(List<ExprNode> args, Environment env) {
        if (args.size() == 1) {
            return resolveBlockPosArg(evaluate(args.get(0), env));
        }
        if (args.size() >= 3) {
            int x = (int) evaluate(args.get(0), env).asNumber();
            int y = (int) evaluate(args.get(1), env).asNumber();
            int z = (int) evaluate(args.get(2), env).asNumber();
            return new BlockPos(x, y, z);
        }
        throw new InterpreterException("Expected pos object or x,y,z arguments");
    }

    private BlockPos resolveBlockPosArg(ScriptValue posValue) {
        if (posValue.getType() == ScriptValue.Type.OBJECT) {
            Map<String, ScriptValue> obj = posValue.asObject();
            int x = (int) obj.getOrDefault("x", ScriptValue.of(0)).asNumber();
            int y = (int) obj.getOrDefault("y", ScriptValue.of(0)).asNumber();
            int z = (int) obj.getOrDefault("z", ScriptValue.of(0)).asNumber();
            return new BlockPos(x, y, z);
        }
        String[] parts = posValue.asString().trim().split("\\s+");
        if (parts.length < 3) {
            throw new InterpreterException("Invalid pos format: " + posValue.asString());
        }
        return new BlockPos(
                (int) Double.parseDouble(parts[0]),
                (int) Double.parseDouble(parts[1]),
                (int) Double.parseDouble(parts[2]));
    }

    private String payloadToTellrawJson(ScriptValue payload) {
        if (payload.getType() == ScriptValue.Type.STRING) {
            return GSON.toJson(Map.of("text", payload.asString()));
        }
        Object normalized = normalizeTellrawPayload(payload.toJavaObject());
        return GSON.toJson(normalized);
    }

    @SuppressWarnings("unchecked")
    private Object normalizeTellrawPayload(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object normalized = normalizeTellrawPayload(entry.getValue());
                if ("text".equals(key) && normalized != null && !(normalized instanceof String)) {
                    normalized = String.valueOf(normalized);
                }
                out.put(key, normalized);
            }
            return out;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(normalizeTellrawPayload(item));
            }
            return out;
        }
        return value;
    }

    private String formatPos(double x, double y, double z) {
        return ScriptValue.of(x).asString() + " " + ScriptValue.of(y).asString() + " " + ScriptValue.of(z).asString();
    }

    private Identifier normalizeEffectId(String rawId) {
        String value = rawId != null ? rawId.trim() : "";
        if (value.isEmpty()) {
            return null;
        }
        if (!value.contains(":")) {
            value = "minecraft:" + value;
        }
        return Identifier.tryParse(value);
    }

    private void executeCommand(String command) {
        try {
            ServerCommandSource source = SScript.isDebugEnabled()
                    ? server.getCommandSource()
                    : server.getCommandSource().withSilent();
            var parsed = server.getCommandManager().getDispatcher().parse(command, source);
            server.getCommandManager().getDispatcher().execute(parsed);
            
        } catch (CommandSyntaxException e) {
            throw new InterpreterException("Command syntax error: " + e.getMessage());
        } catch (Exception e) {
            throw new InterpreterException("Command execution failed: " + e.getMessage());
        }
    }

    private int executeCommandWithResult(String command) {
        try {
            ServerCommandSource source = SScript.isDebugEnabled()
                    ? server.getCommandSource()
                    : server.getCommandSource().withSilent();
            var parsed = server.getCommandManager().getDispatcher().parse(command, source);
            return server.getCommandManager().getDispatcher().execute(parsed);
        } catch (CommandSyntaxException e) {
            throw new InterpreterException("Command syntax error: " + e.getMessage());
        } catch (Exception e) {
            throw new InterpreterException("Command execution failed: " + e.getMessage());
        }
    }

    // ─── Await child spawning ──────────────────────────────────

    private void spawnAwaitChild(String funcName, List<ExprNode> argExprs, Environment env,
                                 ScriptProcess parentProcess, String awaitVarName) {
        FuncDefNode func = functions.get(funcName);
        if (func == null) {
            throw new InterpreterException("Undefined function: " + funcName + " in " + fileName);
        }

        Environment childEnv = new Environment(env);
        for (int i = 0; i < func.params().size(); i++) {
            ScriptValue val = i < argExprs.size() ? evaluate(argExprs.get(i), env) : ScriptValue.NULL;
            childEnv.defineLocal(func.params().get(i), val);
        }

        Interpreter childInterpreter = new Interpreter(server, fileName);
        childInterpreter.functions.putAll(this.functions);

        ProcessScheduler scheduler = ProcessScheduler.getInstance();
        if (scheduler == null) {
            throw new InterpreterException("Process scheduler is not initialized");
        }
        scheduler.submitChild(funcName, childInterpreter, childEnv,
            new ArrayList<>(func.body()), parentProcess, awaitVarName);
    }

    // ─── Built-in commands ─────────────────────────────────────

    private void executeRun(RunNode node, Environment env) {
        String command = evaluate(node.command(), env).asString();
        try {
            executeCommand(command);
        } catch (InterpreterException e) {
            if (SScript.isDebugEnabled()) {
                SScript.LOGGER.error("[SScript] Command syntax error '{}': {}", command, e.getMessage());
            }
        }
    }

    private void executeLog(LogNode node, Environment env) {
        String message = evaluate(node.message(), env).asString();
        SScript.LOGGER.info("[SScript] {}", message);
    }

    private void executeExprStatement(ExprStatement stmt, Environment env, ScriptProcess process) {
        if (stmt.expression() instanceof CallExpr callExpr
                && process != null
                && ProcessScheduler.getInstance() != null
                && functions.containsKey(callExpr.funcName())) {
            callFunction(callExpr.funcName(), callExpr.args(), env, process);
            return;
        }
        evaluate(stmt.expression(), env);
    }

    private void executeWait(WaitNode node, Environment env, ScriptProcess process) {
        if (node.seconds() instanceof CallExpr callExpr) {
            if (process != null && ProcessScheduler.getInstance() != null) {
                spawnAwaitChild(callExpr.funcName(), callExpr.args(), env, process, null);
                throw new AwaitChildException(process.getChildProcess().getId());
            }
            callFunction(callExpr.funcName(), callExpr.args(), env, process);
            return;
        }

        double seconds = evaluate(node.seconds(), env).asNumber();
        if (process != null) {
            throw new WaitException(seconds);
        } else {
            SScript.LOGGER.info("[SScript] wait {}s (synchronous mode, skipping)", seconds);
        }
    }

    private void executeSleep(SleepNode node, Environment env, ScriptProcess process) {
        double ticks = evaluate(node.ticks(), env).asNumber();
        double seconds = ticks / 20.0;
        if (process != null) {
            throw new WaitException(seconds);
        } else {
            SScript.LOGGER.info("[SScript] sleep {} ticks (synchronous mode, skipping)", ticks);
        }
    }

    private void executeSetGlobal(SetGlobalNode node, Environment env) {
        String key = evaluate(node.key(), env).asString();
        ScriptValue value = evaluate(node.value(), env);
        GlobalVariables globals = GlobalVariables.getInstance();
        if (globals != null) {
            globals.set(key, value);
        } else {
            SScript.LOGGER.warn("[SScript] GlobalVariables not initialized, cannot set '{}'", key);
        }
    }

    private void executeTryCatch(TryCatchNode node, Environment env, ScriptProcess process) {
        try {
            executeBlock(node.tryBody(), env, process);
        } catch (ReturnException | WaitException | AwaitChildException | BreakException | ContinueException controlFlow) {
            throw controlFlow;
        } catch (RuntimeException err) {
            env.set(node.errorName(), ScriptValue.of(err.getMessage() != null ? err.getMessage() : "unknown error"));
            executeBlock(node.catchBody(), env, process);
        }
    }

    private void executeAssignTarget(AssignTargetNode node, Environment env) {
        ScriptValue rhs = evaluate(node.value(), env);

        if (node.target() instanceof IdentifierExpr id) {
            ScriptValue cur = env.get(id.name());
            if (cur == null) {
                cur = ScriptValue.NULL;
            }
            ScriptValue result = applyAssignOperator(cur, rhs, node.operator());
            env.set(id.name(), result);
            return;
        }

        if (node.target() instanceof MemberExpr memberExpr) {
            ScriptValue obj = evaluate(memberExpr.object(), env);
            if (obj.getType() != ScriptValue.Type.OBJECT && memberExpr.object() instanceof IdentifierExpr idExpr) {
                Map<String, ScriptValue> promoted = new LinkedHashMap<>();
                if (obj.getType() == ScriptValue.Type.STRING) {
                    promoted.put("id", obj);
                }
                obj = ScriptValue.ofObject(promoted);
                env.set(idExpr.name(), obj);
            }
            ScriptValue cur = obj.getMember(memberExpr.name());
            ScriptValue result = applyAssignOperator(cur, rhs, node.operator());
            obj.setMember(memberExpr.name(), result);
            return;
        }

        if (node.target() instanceof IndexExpr indexExpr) {
            ScriptValue obj = evaluate(indexExpr.object(), env);
            if (obj.getType() != ScriptValue.Type.OBJECT && obj.getType() != ScriptValue.Type.LIST
                    && indexExpr.object() instanceof IdentifierExpr idExpr) {
                obj = ScriptValue.ofObject(new LinkedHashMap<>());
                env.set(idExpr.name(), obj);
            }
            ScriptValue idx = evaluate(indexExpr.index(), env);
            ScriptValue cur = obj.getIndex(idx);
            ScriptValue result = applyAssignOperator(cur, rhs, node.operator());
            obj.setIndex(idx, result);
            return;
        }

        throw new InterpreterException("Invalid assignment target at line " + node.line());
    }

    private ScriptValue applyAssignOperator(ScriptValue current, ScriptValue rhs, String operator) {
        return switch (operator) {
            case "=" -> rhs;
            case "+=" -> current.add(rhs);
            case "-=" -> current.subtract(rhs);
            default -> throw new InterpreterException("Unsupported assignment operator: " + operator);
        };
    }

    // ─── Expression Evaluation ─────────────────────────────────
    /**
     * Public accessor for expression evaluation — used by ProcessScheduler
     * for loop unrolling (evaluating range bounds, while conditions).
     */
    public ScriptValue evaluateExpr(ExprNode expr, Environment env) {
        return evaluate(expr, env);
    }

    private ScriptValue evaluate(ExprNode expr, Environment env) {
        return switch (expr) {
            case NumberLiteral lit -> ScriptValue.of(lit.value());
            case ValueLiteral lit -> lit.value();
            case StringLiteral lit -> ScriptValue.of(lit.value());
            case BooleanLiteral lit -> ScriptValue.of(lit.value());
            case ListLiteral lit -> {
                List<ScriptValue> values = new ArrayList<>();
                for (ExprNode element : lit.elements()) {
                    values.add(evaluate(element, env));
                }
                yield ScriptValue.ofList(values);
            }
            case ObjectLiteral lit -> {
                Map<String, ScriptValue> map = new LinkedHashMap<>();
                for (ObjectEntry entry : lit.entries()) {
                    map.put(entry.key(), evaluate(entry.value(), env));
                }
                yield ScriptValue.ofObject(map);
            }
            case IdentifierExpr id -> {
                ScriptValue val = env.get(id.name());
                if (val == null) {
                    throw new InterpreterException(
                            "Undefined variable: " + id.name() + " at line " + id.line() + " in " + fileName);
                }
                yield val;
            }
            case MemberExpr memberExpr -> {
                ScriptValue obj = evaluate(memberExpr.object(), env);
                yield obj.getMember(memberExpr.name());
            }
            case IndexExpr indexExpr -> {
                ScriptValue obj = evaluate(indexExpr.object(), env);
                ScriptValue idx = evaluate(indexExpr.index(), env);
                yield obj.getIndex(idx);
            }
            case BinaryExpr bin -> evaluateBinary(bin, env);
            case UnaryExpr unary -> evaluateUnary(unary, env);
            case CallExpr call -> callFunction(call.funcName(), call.args(), env, null);
            case InvokeExpr call -> invokeDynamic(call.callee(), call.args(), env);
            case GetGlobalExpr getGlobal -> {
                String key = evaluate(getGlobal.key(), env).asString();
                GlobalVariables globals = GlobalVariables.getInstance();
                if (globals != null) {
                    yield globals.get(key);
                }
                SScript.LOGGER.warn("[SScript] GlobalVariables not initialized, cannot get '{}'", key);
                yield ScriptValue.NULL;
            }
        };
    }

    private ScriptValue evaluateBinary(BinaryExpr bin, Environment env) {
        ScriptValue left = evaluate(bin.left(), env);
        ScriptValue right = evaluate(bin.right(), env);
        return switch (bin.operator()) {
            case "+" -> left.add(right);
            case "-" -> left.subtract(right);
            case "*" -> left.multiply(right);
            case "/" -> left.divide(right);
            case "%" -> left.modulo(right);
            case "==" -> left.eq(right);
            case "!=" -> left.neq(right);
            case "<" -> left.lt(right);
            case ">" -> left.gt(right);
            case "<=" -> left.lte(right);
            case ">=" -> left.gte(right);
            case "and" -> ScriptValue.of(left.asBoolean() && right.asBoolean());
            case "or" -> ScriptValue.of(left.asBoolean() || right.asBoolean());
            default -> throw new InterpreterException("Unknown operator: " + bin.operator());
        };
    }

    private ScriptValue evaluateUnary(UnaryExpr unary, Environment env) {
        ScriptValue operand = evaluate(unary.operand(), env);
        return switch (unary.operator()) {
            case "-" -> operand.negate();
            case "not" -> ScriptValue.of(!operand.asBoolean());
            default -> throw new InterpreterException("Unknown unary operator: " + unary.operator());
        };
    }

    private ScriptValue invokeDynamic(ExprNode callee, List<ExprNode> args, Environment env) {
        if (callee instanceof MemberExpr member) {
            ScriptValue target = evaluate(member.object(), env);
            String method = member.name();
            if (target.getType() == ScriptValue.Type.LIST) {
                List<ScriptValue> list = target.asList();
                return switch (method) {
                    case "add" -> {
                        if (args.isEmpty()) {
                            throw new InterpreterException("list.add(value) requires 1 arg");
                        }
                        list.add(evaluate(args.get(0), env));
                        yield ScriptValue.NULL;
                    }
                    case "remove" -> {
                        if (args.isEmpty()) {
                            throw new InterpreterException("list.remove(index) requires 1 arg");
                        }
                        int idx = (int) evaluate(args.get(0), env).asNumber();
                        if (idx >= 0 && idx < list.size()) {
                            list.remove(idx);
                        }
                        yield ScriptValue.NULL;
                    }
                    default -> throw new InterpreterException("Unknown list method: " + method);
                };
            }
        }

        ScriptValue callable = evaluate(callee, env);
        throw new InterpreterException("Value is not callable: " + callable.getTypeName());
    }

    private List<ScriptValue> toIterable(ScriptValue iterable) {
        if (iterable.getType() == ScriptValue.Type.LIST) {
            return iterable.asList();
        }
        if (iterable.getType() == ScriptValue.Type.STRING) {
            String s = iterable.asString();
            List<ScriptValue> out = new ArrayList<>(s.length());
            for (int i = 0; i < s.length(); i++) {
                out.add(ScriptValue.of(String.valueOf(s.charAt(i))));
            }
            return out;
        }
        throw new InterpreterException("Value is not iterable: " + iterable.getTypeName());
    }

    private ScriptValue parseArgValue(String val) {
        if (val.equals("true")) return ScriptValue.TRUE;
        if (val.equals("false")) return ScriptValue.FALSE;
        try {
            return ScriptValue.of(Double.parseDouble(val));
        } catch (NumberFormatException e) {
            return ScriptValue.of(val);
        }
    }

    // ─── Control flow exceptions ───────────────────────────────

    public static class ReturnException extends RuntimeException {
        private final ScriptValue value;
        public ReturnException(ScriptValue value) {
            super(null, null, true, false);
            this.value = value;
        }
        public ScriptValue getValue() { return value; }
    }

    public static class WaitException extends RuntimeException {
        private final double seconds;
        public WaitException(double seconds) {
            super(null, null, true, false);
            this.seconds = seconds;
        }
        public double getSeconds() { return seconds; }
    }

    public static class AwaitChildException extends RuntimeException {
        private final int childId;
        public AwaitChildException(int childId) {
            super(null, null, true, false);
            this.childId = childId;
        }
        public int getChildId() { return childId; }
    }

    public static class BreakException extends RuntimeException {
        public BreakException() { super(null, null, true, false); }
    }

    public static class ContinueException extends RuntimeException {
        public ContinueException() { super(null, null, true, false); }
    }

    public static class InterpreterException extends RuntimeException {
        public InterpreterException(String message) {
            super(message);
        }
    }

    // ─── Command execution with output capture ────────────────

    private CommandResult executeCommandWithOutput(String command) {
        StringBuilder output = new StringBuilder();
        
        try {
            ServerCommandSource baseSource = server.getCommandSource();
            
            CommandOutput capturingOutput = new CommandOutput() {
                @Override
                public void sendMessage(Text text) {
                    output.append(text.getString()).append("\n");
                }
                
                @Override
                public boolean shouldReceiveFeedback() { return true; }
                
                @Override
                public boolean shouldTrackOutput() { return true; }
                
                @Override
                public boolean shouldBroadcastConsoleToOps() { return false; }
            };
            
            ServerCommandSource capturingSource = baseSource.withOutput(capturingOutput);
            
            var parsed = server.getCommandManager().getDispatcher().parse(command, capturingSource);
            int exitCode = server.getCommandManager().getDispatcher().execute(parsed);
            
            return new CommandResult(exitCode, output.toString().trim());
            
        } catch (CommandSyntaxException e) {
            throw new InterpreterException("Command syntax error: " + e.getMessage());
        } catch (Exception e) {
            throw new InterpreterException("Command execution failed: " + e.getMessage());
        }
    }

    // ─── Helper Classes ────────────────────────────────────────

    private static class CommandResult {
        final int exitCode;
        final String output;
        
        CommandResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }

    // ─── Access ────────────────────────────────────────────────

    public Map<String, FuncDefNode> getFunctions() { return functions; }
    public MinecraftServer getServer() { return server; }
    public String getFileName() { return fileName; }
}
