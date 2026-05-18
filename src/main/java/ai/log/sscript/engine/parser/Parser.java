package ai.log.sscript.engine.parser;

import ai.log.sscript.engine.lexer.Token;
import ai.log.sscript.engine.lexer.TokenType;
import ai.log.sscript.engine.parser.ASTNode.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Recursive-descent parser for the SScript language.
 * Consumes a token list from the Lexer and produces a ProgramNode AST.
 */
public class Parser {

    private final List<Token> tokens;
    private int pos = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    // ─── Entry Point ───────────────────────────────────────────

    public ProgramNode parse() {
        List<StatementNode> statements = new ArrayList<>();
        skipNewlines();
        while (!check(TokenType.EOF)) {
            statements.add(parseStatement());
            skipNewlines();
        }
        return new ProgramNode(statements, 1);
    }

    // ─── Statement Parsing ─────────────────────────────────────

    private StatementNode parseStatement() {
        Token cur = current();

        return switch (cur.type()) {
            case IF -> parseIf();
            case WHILE -> parseWhile();
            case FOR -> parseFor();
            case BREAK -> parseBreak();
            case CONTINUE -> parseContinue();
            case FUNC -> parseFuncDef();
            case RETURN -> parseReturn();
            case AWAIT -> parseAwaitStatement();
            case ON -> parseOnEvent();
            case RUN -> parseRun();
            case LOG -> parseLog();
            case WAIT -> parseWait();
            case SLEEP -> parseSleep();
            case SETGLOBAL -> parseSetGlobal();
            case GLOBAL -> parseGlobalSet();
            case TRY -> parseTryCatch();
            case IDENTIFIER -> parseAssignmentOrExpression();
            case NUMBER, STRING, BOOLEAN, GETGLOBAL, RANGE, LPAREN, LBRACKET, LBRACE, NOT, MINUS
                    -> parseExpressionStatement();
            default -> throw error("Unexpected token: " + cur);
        };
    }

    // ─── if / elif / else / end ────────────────────────────────

    private IfNode parseIf() {
        int ln = current().line();
        expect(TokenType.IF);
        ExprNode condition = parseExpression();
        expect(TokenType.COLON);
        expectNewline();

        List<StatementNode> body = parseBlock();
        List<ElifBranch> elifs = new ArrayList<>();
        List<StatementNode> elseBody = List.of();

        while (check(TokenType.ELIF) || (check(TokenType.ELSE) && peek().type() == TokenType.IF)) {
            int elifLn = current().line();
            if (check(TokenType.ELIF)) {
                advance(); // elif
            } else {
                advance(); // else
                expect(TokenType.IF);
            }
            ExprNode elifCond = parseExpression();
            expect(TokenType.COLON);
            expectNewline();
            List<StatementNode> elifBody = parseBlock();
            elifs.add(new ElifBranch(elifCond, elifBody, elifLn));
        }

        if (check(TokenType.ELSE)) {
            advance(); // skip 'else'
            expect(TokenType.COLON);
            expectNewline();
            elseBody = parseBlock();
        }

        expect(TokenType.END);
        expectNewlineOrEof();
        return new IfNode(condition, body, elifs, elseBody, ln);
    }

    // ─── while condition: ... end ──────────────────────────────

    private WhileNode parseWhile() {
        int ln = current().line();
        expect(TokenType.WHILE);
        ExprNode condition = parseExpression();
        expect(TokenType.COLON);
        expectNewline();

        List<StatementNode> body = parseBlock();
        expect(TokenType.END);
        expectNewlineOrEof();
        return new WhileNode(condition, body, ln);
    }

    // ─── for var in range(start, end): ... end ─────────────────

    private ForNode parseFor() {
        int ln = current().line();
        expect(TokenType.FOR);
        String varName = expect(TokenType.IDENTIFIER).value();
        expect(TokenType.IN);
        ExprNode iterable = parseExpression();
        expect(TokenType.COLON);
        expectNewline();

        List<StatementNode> body = parseBlock();
        expect(TokenType.END);
        expectNewlineOrEof();
        return new ForNode(varName, iterable, body, ln);
    }

    // ─── break / continue ──────────────────────────────────────

    private BreakNode parseBreak() {
        int ln = current().line();
        expect(TokenType.BREAK);
        expectNewlineOrEof();
        return new BreakNode(ln);
    }

    private ContinueNode parseContinue() {
        int ln = current().line();
        expect(TokenType.CONTINUE);
        expectNewlineOrEof();
        return new ContinueNode(ln);
    }

    // ─── func name(params): ... end ────────────────────────────

    private FuncDefNode parseFuncDef() {
        int ln = current().line();
        expect(TokenType.FUNC);
        String name = expect(TokenType.IDENTIFIER).value();
        expect(TokenType.LPAREN);

        List<String> params = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            params.add(expect(TokenType.IDENTIFIER).value());
            while (check(TokenType.COMMA)) {
                advance();
                params.add(expect(TokenType.IDENTIFIER).value());
            }
        }
        expect(TokenType.RPAREN);
        expect(TokenType.COLON);
        expectNewline();

        List<StatementNode> body = parseBlock();
        expect(TokenType.END);
        expectNewlineOrEof();
        return new FuncDefNode(name, params, body, ln);
    }

    // ─── return [expr] ─────────────────────────────────────────

    private ReturnNode parseReturn() {
        int ln = current().line();
        expect(TokenType.RETURN);
        ExprNode value = null;
        if (!check(TokenType.NEWLINE) && !check(TokenType.EOF)) {
            value = parseExpression();
        }
        expectNewlineOrEof();
        return new ReturnNode(value, ln);
    }

    // ─── await func(args) ──────────────────────────────────────

    private StatementNode parseAwaitStatement() {
        int ln = current().line();
        expect(TokenType.AWAIT);
        String name = expect(TokenType.IDENTIFIER).value();
        expect(TokenType.LPAREN);
        List<ExprNode> args = parseArgList();
        expect(TokenType.RPAREN);
        expectNewlineOrEof();
        return new AwaitStatement(name, args, ln);
    }

    // ─── on event(params): ... end ─────────────────────────────

    private OnEventNode parseOnEvent() {
        int ln = current().line();
        expect(TokenType.ON);
        String eventName = expect(TokenType.IDENTIFIER).value();

        List<String> params = new ArrayList<>();
        if (check(TokenType.LPAREN)) {
            advance();
            if (!check(TokenType.RPAREN)) {
                params.add(expect(TokenType.IDENTIFIER).value());
                while (check(TokenType.COMMA)) {
                    advance();
                    params.add(expect(TokenType.IDENTIFIER).value());
                }
            }
            expect(TokenType.RPAREN);
        }
        expect(TokenType.COLON);
        expectNewline();

        List<StatementNode> body = parseBlock();
        expect(TokenType.END);
        expectNewlineOrEof();
        return new OnEventNode(eventName, params, body, ln);
    }

    // ─── run <expr> ────────────────────────────────────────────

    private RunNode parseRun() {
        int ln = current().line();
        expect(TokenType.RUN);
        ExprNode cmd = parseExpression();
        expectNewlineOrEof();
        return new RunNode(cmd, ln);
    }

    // ─── log <expr> ────────────────────────────────────────────

    private LogNode parseLog() {
        int ln = current().line();
        expect(TokenType.LOG);
        ExprNode msg = parseExpression();
        expectNewlineOrEof();
        return new LogNode(msg, ln);
    }

    // ─── wait <expr> ───────────────────────────────────────────

    private WaitNode parseWait() {
        int ln = current().line();
        expect(TokenType.WAIT);
        ExprNode seconds = parseExpression();
        expectNewlineOrEof();
        return new WaitNode(seconds, ln);
    }

    private SleepNode parseSleep() {
        int ln = current().line();
        expect(TokenType.SLEEP);
        ExprNode ticks = parseExpression();
        expectNewlineOrEof();
        return new SleepNode(ticks, ln);
    }

    // ─── setglobal <key> <value> ───────────────────────────────

    private SetGlobalNode parseSetGlobal() {
        int ln = current().line();
        expect(TokenType.SETGLOBAL);
        ExprNode key = parseExpression();
        ExprNode value = parseExpression();
        expectNewlineOrEof();
        return new SetGlobalNode(key, value, ln);
    }

    private SetGlobalNode parseGlobalSet() {
        int ln = current().line();
        expect(TokenType.GLOBAL);
        String key = expect(TokenType.IDENTIFIER).value();
        expect(TokenType.ASSIGN);
        ExprNode value = parseExpression();
        expectNewlineOrEof();
        return new SetGlobalNode(new StringLiteral(key, ln), value, ln);
    }

    private TryCatchNode parseTryCatch() {
        int ln = current().line();
        expect(TokenType.TRY);
        expect(TokenType.COLON);
        expectNewline();
        List<StatementNode> tryBody = parseBlockUntil(TokenType.CATCH);

        expect(TokenType.CATCH);
        String errName = expect(TokenType.IDENTIFIER).value();
        expect(TokenType.COLON);
        expectNewline();
        List<StatementNode> catchBody = parseBlock();
        expect(TokenType.END);
        expectNewlineOrEof();
        return new TryCatchNode(tryBody, errName, catchBody, ln);
    }

    // ─── assignment: x = expr / x = call func() / x = await func() ─

    private StatementNode parseAssignmentOrExpression() {
        int checkpoint = pos;
        int ln = current().line();

        ExprNode target = parseAssignableTarget();
        if (check(TokenType.ASSIGN) || check(TokenType.PLUS_ASSIGN) || check(TokenType.MINUS_ASSIGN)) {
            String op = current().value();
            advance();

            if (target instanceof IdentifierExpr id) {
                if (check(TokenType.AWAIT)) {
                    advance();
                    String funcName = expect(TokenType.IDENTIFIER).value();
                    expect(TokenType.LPAREN);
                    List<ExprNode> args = parseArgList();
                    expect(TokenType.RPAREN);
                    expectNewlineOrEof();
                    return new AssignAwaitNode(id.name(), funcName, args, ln);
                }
            }

            ExprNode value = parseExpression();
            expectNewlineOrEof();
            if (target instanceof IdentifierExpr id && "=".equals(op)) {
                return new AssignNode(id.name(), value, ln);
            }
            return new AssignTargetNode(target, op, value, ln);
        }

        pos = checkpoint;
        return parseExpressionStatement();
    }

    // ─── Expression Parsing (precedence climbing) ──────────────

    private ExprNode parseExpression() {
        return parseOr();
    }

    private ExprNode parseOr() {
        ExprNode left = parseAnd();
        while (check(TokenType.OR)) {
            int ln = current().line();
            advance();
            ExprNode right = parseAnd();
            left = new BinaryExpr(left, "or", right, ln);
        }
        return left;
    }

    private ExprNode parseAnd() {
        ExprNode left = parseNot();
        while (check(TokenType.AND)) {
            int ln = current().line();
            advance();
            ExprNode right = parseNot();
            left = new BinaryExpr(left, "and", right, ln);
        }
        return left;
    }

    private ExprNode parseNot() {
        if (check(TokenType.NOT)) {
            int ln = current().line();
            advance();
            ExprNode operand = parseNot();
            return new UnaryExpr("not", operand, ln);
        }
        return parseComparison();
    }

    private ExprNode parseComparison() {
        ExprNode left = parseAddSub();
        while (check(TokenType.EQ) || check(TokenType.NEQ) ||
                check(TokenType.LT) || check(TokenType.GT) ||
                check(TokenType.LTE) || check(TokenType.GTE)) {
            int ln = current().line();
            String op = current().value();
            advance();
            ExprNode right = parseAddSub();
            left = new BinaryExpr(left, op, right, ln);
        }
        return left;
    }

    private ExprNode parseAddSub() {
        ExprNode left = parseMulDiv();
        while (check(TokenType.PLUS) || check(TokenType.MINUS)) {
            int ln = current().line();
            String op = current().value();
            advance();
            ExprNode right = parseMulDiv();
            left = new BinaryExpr(left, op, right, ln);
        }
        return left;
    }

    private ExprNode parseMulDiv() {
        ExprNode left = parseUnary();
        while (check(TokenType.STAR) || check(TokenType.SLASH) || check(TokenType.PERCENT)) {
            int ln = current().line();
            String op = current().value();
            advance();
            ExprNode right = parseUnary();
            left = new BinaryExpr(left, op, right, ln);
        }
        return left;
    }

    private ExprNode parseUnary() {
        if (check(TokenType.MINUS)) {
            int ln = current().line();
            advance();
            ExprNode operand = parseUnary();
            return new UnaryExpr("-", operand, ln);
        }
        return parsePostfix();
    }

    private ExprNode parsePostfix() {
        ExprNode expr = parseAtom();
        while (true) {
            if (check(TokenType.LPAREN)) {
                int ln = current().line();
                advance();
                List<ExprNode> args = parseArgList();
                expect(TokenType.RPAREN);
                if (expr instanceof IdentifierExpr id) {
                    expr = new CallExpr(id.name(), args, ln);
                } else {
                    expr = new InvokeExpr(expr, args, ln);
                }
                continue;
            }
            if (check(TokenType.DOT)) {
                int ln = current().line();
                advance();
                if (check(TokenType.LBRACKET)) {
                    advance();
                    ExprNode index = parseExpression();
                    expect(TokenType.RBRACKET);
                    expr = new IndexExpr(expr, index, ln);
                    continue;
                }
                String name = expect(TokenType.IDENTIFIER).value();
                expr = new MemberExpr(expr, name, ln);
                continue;
            }
            if (check(TokenType.LBRACKET)) {
                int ln = current().line();
                advance();
                ExprNode index = parseExpression();
                expect(TokenType.RBRACKET);
                expr = new IndexExpr(expr, index, ln);
                continue;
            }
            break;
        }
        return expr;
    }

    private ExprNode parseAtom() {
        Token cur = current();

        switch (cur.type()) {
            case NUMBER -> {
                advance();
                return new NumberLiteral(Double.parseDouble(cur.value()), cur.line());
            }
            case STRING -> {
                advance();
                return new StringLiteral(cur.value(), cur.line());
            }
            case BOOLEAN -> {
                advance();
                return new BooleanLiteral(cur.value().equals("true"), cur.line());
            }
            case GLOBAL -> {
                int ln = cur.line();
                advance();
                String key = expect(TokenType.IDENTIFIER).value();
                return new GetGlobalExpr(new StringLiteral(key, ln), ln);
            }
            case GETGLOBAL -> {
                int ln = cur.line();
                advance();
                expect(TokenType.LPAREN);
                ExprNode key = parseExpression();
                expect(TokenType.RPAREN);
                return new GetGlobalExpr(key, ln);
            }
            case IDENTIFIER -> {
                advance();
                return new IdentifierExpr(cur.value(), cur.line());
            }
            case RANGE -> {
                advance();
                return new IdentifierExpr("range", cur.line());
            }
            case LPAREN -> {
                advance(); // skip (
                ExprNode expr = parseExpression();
                expect(TokenType.RPAREN);
                return expr;
            }
            case LBRACKET -> {
                int ln = cur.line();
                advance();
                List<ExprNode> elements = new ArrayList<>();
                skipInlineNewlines();
                if (!check(TokenType.RBRACKET)) {
                    elements.add(parseExpression());
                    skipInlineNewlines();
                    while (check(TokenType.COMMA)) {
                        advance();
                        skipInlineNewlines();
                        elements.add(parseExpression());
                        skipInlineNewlines();
                    }
                }
                expect(TokenType.RBRACKET);
                return new ListLiteral(elements, ln);
            }
            case LBRACE -> {
                int ln = cur.line();
                advance();
                List<ObjectEntry> entries = new ArrayList<>();
                skipInlineNewlines();
                if (!check(TokenType.RBRACE)) {
                    entries.add(parseObjectEntry());
                    skipInlineNewlines();
                    while (check(TokenType.COMMA)) {
                        advance();
                        skipInlineNewlines();
                        entries.add(parseObjectEntry());
                        skipInlineNewlines();
                    }
                }
                expect(TokenType.RBRACE);
                return new ObjectLiteral(entries, ln);
            }
            default -> throw error("Unexpected token in expression: " + cur);
        }
    }

    private ObjectEntry parseObjectEntry() {
        String key;
        skipInlineNewlines();
        if (check(TokenType.STRING) || check(TokenType.IDENTIFIER)) {
            key = current().value();
            advance();
        } else {
            throw error("Expected object key, got " + current());
        }
        expect(TokenType.COLON);
        ExprNode value = parseExpression();
        return new ObjectEntry(key, value);
    }

    private ExprNode parseAssignableTarget() {
        Token first = expect(TokenType.IDENTIFIER);
        ExprNode target = new IdentifierExpr(first.value(), first.line());
        while (true) {
            if (check(TokenType.DOT)) {
                int ln = current().line();
                advance();
                if (check(TokenType.LBRACKET)) {
                    advance();
                    ExprNode index = parseExpression();
                    expect(TokenType.RBRACKET);
                    target = new IndexExpr(target, index, ln);
                    continue;
                }
                String member = expect(TokenType.IDENTIFIER).value();
                target = new MemberExpr(target, member, ln);
                continue;
            }
            if (check(TokenType.LBRACKET)) {
                int ln = current().line();
                advance();
                ExprNode index = parseExpression();
                expect(TokenType.RBRACKET);
                target = new IndexExpr(target, index, ln);
                continue;
            }
            break;
        }
        return target;
    }

    private ExprStatement parseExpressionStatement() {
        int ln = current().line();
        ExprNode expr = parseExpression();
        expectNewlineOrEof();
        return new ExprStatement(expr, ln);
    }

    // ─── Helpers ───────────────────────────────────────────────

    private List<ExprNode> parseArgList() {
        List<ExprNode> args = new ArrayList<>();
        skipInlineNewlines();
        if (!check(TokenType.RPAREN)) {
            args.add(parseExpression());
            skipInlineNewlines();
            while (check(TokenType.COMMA)) {
                advance();
                skipInlineNewlines();
                args.add(parseExpression());
                skipInlineNewlines();
            }
        }
        return args;
    }

    private List<StatementNode> parseBlock() {
        List<StatementNode> block = new ArrayList<>();
        skipNewlines();
        while (!check(TokenType.END) && !check(TokenType.ELIF) &&
                !check(TokenType.ELSE) && !check(TokenType.EOF)) {
            block.add(parseStatement());
            skipNewlines();
        }
        return block;
    }

    private List<StatementNode> parseBlockUntil(TokenType stopType) {
        List<StatementNode> block = new ArrayList<>();
        skipNewlines();
        while (!check(stopType) && !check(TokenType.EOF)) {
            block.add(parseStatement());
            skipNewlines();
        }
        return block;
    }

    private Token current() {
        return tokens.get(pos);
    }

    private Token peek() {
        return pos + 1 < tokens.size() ? tokens.get(pos + 1) : tokens.get(tokens.size() - 1);
    }

    private boolean check(TokenType type) {
        return current().type() == type;
    }

    private void advance() {
        if (pos < tokens.size() - 1) {
            pos++;
        }
    }

    private Token expect(TokenType type) {
        if (!check(type)) {
            throw error("Expected " + type + " but got " + current().type() + " ('" + current().value() + "') at line "
                    + current().line());
        }
        Token t = current();
        advance();
        return t;
    }

    private void expectNewline() {
        if (!check(TokenType.NEWLINE) && !check(TokenType.EOF)) {
            throw error("Expected newline at line " + current().line() + " but got " + current());
        }
        skipNewlines();
    }

    private void expectNewlineOrEof() {
        if (!check(TokenType.NEWLINE) && !check(TokenType.EOF)) {
            throw error("Expected newline or end of file at line " + current().line() + " but got " + current());
        }
        if (check(TokenType.NEWLINE)) {
            advance();
        }
    }

    private void skipNewlines() {
        while (check(TokenType.NEWLINE)) {
            advance();
        }
    }

    private void skipInlineNewlines() {
        while (check(TokenType.NEWLINE)) {
            advance();
        }
    }

    private ParserException error(String message) {
        return new ParserException(message);
    }

    public static class ParserException extends RuntimeException {
        public ParserException(String message) {
            super(message);
        }
    }
}
