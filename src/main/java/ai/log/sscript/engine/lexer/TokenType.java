package ai.log.sscript.engine.lexer;

public enum TokenType {
    // Literals
    NUMBER,
    STRING,
    BOOLEAN,
    IDENTIFIER,

    // Keywords
    IF, ELIF, ELSE, END,
    FUNC, RETURN,
    AWAIT, ON,
    WHILE, FOR, IN, BREAK, CONTINUE,
    TRY, CATCH,

    // Built-in commands / functions
    RUN, LOG, WAIT,
    SLEEP,
    SETGLOBAL, GETGLOBAL,
    GLOBAL,
    RANGE,

    // Arithmetic operators
    PLUS, MINUS, STAR, SLASH, PERCENT,

    // Comparison operators
    EQ, NEQ, LT, GT, LTE, GTE,

    // Logical operators
    AND, OR, NOT,

    // Symbols
    ASSIGN, // =
    PLUS_ASSIGN, // +=
    MINUS_ASSIGN, // -=
    LPAREN, // (
    RPAREN, // )
    LBRACKET, // [
    RBRACKET, // ]
    LBRACE, // {
    RBRACE, // }
    COMMA, // ,
    COLON, // :
    DOT, // .

    // Structure
    NEWLINE,
    EOF
}
