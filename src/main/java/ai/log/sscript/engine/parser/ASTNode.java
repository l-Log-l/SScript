package ai.log.sscript.engine.parser;

import ai.log.sscript.engine.interpreter.ScriptValue;

import java.util.List;

/**
 * All AST node types for the SScript language.
 */
public sealed interface ASTNode {

    int line();

    // ─── Program ───────────────────────────────────────────────
    record ProgramNode(List<StatementNode> statements, int line) implements ASTNode {
    }

    // ─── Statements ────────────────────────────────────────────
    sealed interface StatementNode extends ASTNode {
    }

    record AssignNode(String name, ExprNode value, int line) implements StatementNode {
    }

        /** obj.field = expr / obj[idx] += expr */
        record AssignTargetNode(ExprNode target, String operator, ExprNode value, int line) implements StatementNode {
        }

    /** result = await func(args) — assign form */
    record AssignAwaitNode(String varName, String funcName, List<ExprNode> args, int line) implements StatementNode {
    }

    /** result = call func(args) — assign form */
    record AssignCallNode(String varName, String funcName, List<ExprNode> args, int line) implements StatementNode {
    }

    record IfNode(
            ExprNode condition,
            List<StatementNode> body,
            List<ElifBranch> elifs,
            List<StatementNode> elseBody,
            int line) implements StatementNode {
    }

    record ElifBranch(ExprNode condition, List<StatementNode> body, int line) implements ASTNode {
    }

    record FuncDefNode(String name, List<String> params, List<StatementNode> body, int line) implements StatementNode {
    }

    /** Statement-level: call func(args) */
    record CallStatement(String funcName, List<ExprNode> args, int line) implements StatementNode {
    }

    record ReturnNode(ExprNode value, int line) implements StatementNode {
    }

    /** Statement-level: await func(args) — fire-and-forget */
    record AwaitStatement(String funcName, List<ExprNode> args, int line) implements StatementNode {
    }

    record OnEventNode(String eventName, List<String> params, List<StatementNode> body, int line)
            implements StatementNode {
    }

    // ─── Loop statements ───────────────────────────────────────

    /** while condition: ... end */
    record WhileNode(ExprNode condition, List<StatementNode> body, int line) implements StatementNode {
    }

    /** for var in iterable: ... end */
    record ForNode(String varName, ExprNode iterable, List<StatementNode> body, int line)
            implements StatementNode {
    }

    record BreakNode(int line) implements StatementNode {
    }

    record ContinueNode(int line) implements StatementNode {
    }

    record TryCatchNode(List<StatementNode> tryBody, String errorName, List<StatementNode> catchBody, int line)
            implements StatementNode {
    }

    /** Sentinel: marks end of an unrolled loop (for break to skip to) */
    record LoopEndMarker(int line) implements StatementNode {
    }

    /** Sentinel: marks end of one iteration (for continue to skip to) */
    record IterationEndMarker(int line) implements StatementNode {
    }

    // ─── Built-in command statements ───────────────────────────

    record RunNode(ExprNode command, int line) implements StatementNode {
    }

    record LogNode(ExprNode message, int line) implements StatementNode {
    }

    record WaitNode(ExprNode seconds, int line) implements StatementNode {
    }

        record SleepNode(ExprNode ticks, int line) implements StatementNode {
        }

    record SetGlobalNode(ExprNode key, ExprNode value, int line) implements StatementNode {
    }

        record ExprStatement(ExprNode expression, int line) implements StatementNode {
        }

    // ─── Expressions ───────────────────────────────────────────
    sealed interface ExprNode extends ASTNode {
    }

    record NumberLiteral(double value, int line) implements ExprNode {
    }

    /** Internal literal used by scheduler for loop expansion. */
    record ValueLiteral(ScriptValue value, int line) implements ExprNode {
    }

    record StringLiteral(String value, int line) implements ExprNode {
    }

    record BooleanLiteral(boolean value, int line) implements ExprNode {
    }

    record IdentifierExpr(String name, int line) implements ExprNode {
    }

        record ListLiteral(List<ExprNode> elements, int line) implements ExprNode {
        }

        record ObjectEntry(String key, ExprNode value) {
        }

        record ObjectLiteral(List<ObjectEntry> entries, int line) implements ExprNode {
        }

        record MemberExpr(ExprNode object, String name, int line) implements ExprNode {
        }

        record IndexExpr(ExprNode object, ExprNode index, int line) implements ExprNode {
        }

    record BinaryExpr(ExprNode left, String operator, ExprNode right, int line) implements ExprNode {
    }

    record UnaryExpr(String operator, ExprNode operand, int line) implements ExprNode {
    }

    /** Expression-level function call: func(args) inside expressions */
    record CallExpr(String funcName, List<ExprNode> args, int line) implements ExprNode {
    }

        /** Generic invocation: expr(args), supports object methods. */
        record InvokeExpr(ExprNode callee, List<ExprNode> args, int line) implements ExprNode {
        }

    /** getglobal(key) as expression */
    record GetGlobalExpr(ExprNode key, int line) implements ExprNode {
    }
}
