package ai.log.sscript.engine.lexer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Lexer {

    private static final Map<String, TokenType> KEYWORDS = Map.ofEntries(
            Map.entry("if", TokenType.IF),
            Map.entry("elif", TokenType.ELIF),
            Map.entry("else", TokenType.ELSE),
            Map.entry("end", TokenType.END),
            Map.entry("func", TokenType.FUNC),
            Map.entry("def", TokenType.FUNC),
            Map.entry("return", TokenType.RETURN),
            Map.entry("await", TokenType.AWAIT),
            Map.entry("on", TokenType.ON),
            Map.entry("while", TokenType.WHILE),
            Map.entry("for", TokenType.FOR),
            Map.entry("in", TokenType.IN),
            Map.entry("break", TokenType.BREAK),
            Map.entry("continue", TokenType.CONTINUE),
            Map.entry("try", TokenType.TRY),
            Map.entry("catch", TokenType.CATCH),
            Map.entry("run", TokenType.RUN),
            Map.entry("log", TokenType.LOG),
            Map.entry("wait", TokenType.WAIT),
            Map.entry("sleep", TokenType.SLEEP),
            Map.entry("setglobal", TokenType.SETGLOBAL),
            Map.entry("getglobal", TokenType.GETGLOBAL),
            Map.entry("global", TokenType.GLOBAL),
            Map.entry("range", TokenType.RANGE),
            Map.entry("and", TokenType.AND),
            Map.entry("or", TokenType.OR),
            Map.entry("not", TokenType.NOT),
            Map.entry("true", TokenType.BOOLEAN),
            Map.entry("false", TokenType.BOOLEAN));

    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private int pos = 0;
    private int line = 1;

    public Lexer(String source) {
        this.source = source;
    }

    public List<Token> tokenize() {
        while (pos < source.length()) {
            char c = current();

            // Skip spaces and tabs (not newlines)
            if (c == ' ' || c == '\t' || c == '\r') {
                advance();
                continue;
            }

            // Comments
            if (c == '#') {
                skipComment();
                continue;
            }

            // Newlines
            if (c == '\n') {
                // Only add NEWLINE if the last token is not already a NEWLINE
                if (tokens.isEmpty() || tokens.get(tokens.size() - 1).type() != TokenType.NEWLINE) {
                    tokens.add(new Token(TokenType.NEWLINE, "\\n", line));
                }
                line++;
                advance();
                continue;
            }

            // Strings
            if (c == '"') {
                readString();
                continue;
            }

            // Numbers
            if (Character.isDigit(c)) {
                readNumber();
                continue;
            }

            // Identifiers and keywords
            if (Character.isLetter(c) || c == '_') {
                readIdentifier();
                continue;
            }

            // Two-character operators
            if (c == '=' && peek() == '=') {
                tokens.add(new Token(TokenType.EQ, "==", line));
                advance();
                advance();
                continue;
            }
            if (c == '!' && peek() == '=') {
                tokens.add(new Token(TokenType.NEQ, "!=", line));
                advance();
                advance();
                continue;
            }
            if (c == '+' && peek() == '=') {
                tokens.add(new Token(TokenType.PLUS_ASSIGN, "+=", line));
                advance();
                advance();
                continue;
            }
            if (c == '-' && peek() == '=') {
                tokens.add(new Token(TokenType.MINUS_ASSIGN, "-=", line));
                advance();
                advance();
                continue;
            }
            if (c == '<' && peek() == '=') {
                tokens.add(new Token(TokenType.LTE, "<=", line));
                advance();
                advance();
                continue;
            }
            if (c == '>' && peek() == '=') {
                tokens.add(new Token(TokenType.GTE, ">=", line));
                advance();
                advance();
                continue;
            }

            // Single-character tokens
            switch (c) {
                case '=' -> tokens.add(new Token(TokenType.ASSIGN, "=", line));
                case '+' -> tokens.add(new Token(TokenType.PLUS, "+", line));
                case '-' -> tokens.add(new Token(TokenType.MINUS, "-", line));
                case '*' -> tokens.add(new Token(TokenType.STAR, "*", line));
                case '/' -> tokens.add(new Token(TokenType.SLASH, "/", line));
                case '%' -> tokens.add(new Token(TokenType.PERCENT, "%", line));
                case '<' -> tokens.add(new Token(TokenType.LT, "<", line));
                case '>' -> tokens.add(new Token(TokenType.GT, ">", line));
                case '(' -> tokens.add(new Token(TokenType.LPAREN, "(", line));
                case ')' -> tokens.add(new Token(TokenType.RPAREN, ")", line));
                case '[' -> tokens.add(new Token(TokenType.LBRACKET, "[", line));
                case ']' -> tokens.add(new Token(TokenType.RBRACKET, "]", line));
                case '{' -> tokens.add(new Token(TokenType.LBRACE, "{", line));
                case '}' -> tokens.add(new Token(TokenType.RBRACE, "}", line));
                case ',' -> tokens.add(new Token(TokenType.COMMA, ",", line));
                case ':' -> tokens.add(new Token(TokenType.COLON, ":", line));
                case '.' -> tokens.add(new Token(TokenType.DOT, ".", line));
                default -> throw new LexerException("Unexpected character '" + c + "' at line " + line);
            }
            advance();
        }

        // Ensure we always end with a NEWLINE before EOF
        if (!tokens.isEmpty() && tokens.get(tokens.size() - 1).type() != TokenType.NEWLINE) {
            tokens.add(new Token(TokenType.NEWLINE, "\\n", line));
        }
        tokens.add(new Token(TokenType.EOF, "", line));
        return tokens;
    }

    private char current() {
        return source.charAt(pos);
    }

    private char peek() {
        return pos + 1 < source.length() ? source.charAt(pos + 1) : '\0';
    }

    private void advance() {
        pos++;
    }

    private void skipComment() {
        while (pos < source.length() && source.charAt(pos) != '\n') {
            advance();
        }
    }

    private void readString() {
        advance(); // skip opening "
        StringBuilder sb = new StringBuilder();
        while (pos < source.length() && current() != '"') {
            if (current() == '\\') {
                advance();
                if (pos < source.length()) {
                    switch (current()) {
                        case 'n' -> sb.append('\n');
                        case 't' -> sb.append('\t');
                        case '\\' -> sb.append('\\');
                        case '"' -> sb.append('"');
                        default -> {
                            sb.append('\\');
                            sb.append(current());
                        }
                    }
                }
            } else if (current() == '\n') {
                throw new LexerException("Unterminated string at line " + line);
            } else {
                sb.append(current());
            }
            advance();
        }
        if (pos >= source.length()) {
            throw new LexerException("Unterminated string at line " + line);
        }
        advance(); // skip closing "
        tokens.add(new Token(TokenType.STRING, sb.toString(), line));
    }

    private void readNumber() {
        StringBuilder sb = new StringBuilder();
        boolean hasDot = false;
        while (pos < source.length() && (Character.isDigit(current()) || current() == '.')) {
            if (current() == '.') {
                if (hasDot)
                    break;
                hasDot = true;
            }
            sb.append(current());
            advance();
        }
        tokens.add(new Token(TokenType.NUMBER, sb.toString(), line));
    }

    private void readIdentifier() {
        StringBuilder sb = new StringBuilder();
        while (pos < source.length() && (Character.isLetterOrDigit(current()) || current() == '_')) {
            sb.append(current());
            advance();
        }
        String word = sb.toString();
        TokenType type = KEYWORDS.getOrDefault(word, TokenType.IDENTIFIER);
        tokens.add(new Token(type, word, line));
    }

    public static class LexerException extends RuntimeException {
        public LexerException(String message) {
            super(message);
        }
    }
}
