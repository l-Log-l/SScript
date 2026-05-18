package ai.log.sscript.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper for providing better error messages and suggestions for common mistakes.
 */
public class ErrorHelper {

    private static final List<String> KEYWORDS = List.of(
            "if", "elif", "else", "end", "func", "def", "return",
            "await", "on", "while", "for", "in", "break", "continue",
            "try", "catch", "run", "log", "wait", "sleep", "setglobal",
            "getglobal", "global", "range", "and", "or", "not", "true", "false"
    );

    /**
     * Check for common case-sensitivity issues with keywords
     * e.g., "While" instead of "while", "True" instead of "true"
     */
    public static String suggestKeywordFix(String possibleKeyword) {
        String lower = possibleKeyword.toLowerCase();
        if (KEYWORDS.contains(lower)) {
            return "Did you mean '" + lower + "'? (keywords are case-sensitive)";
        }
        return null;
    }

    /**
     * Extract source line from script for error context
     */
    public static String getLineContext(String source, int lineNumber) {
        String[] lines = source.split("\n");
        if (lineNumber > 0 && lineNumber <= lines.length) {
            return lines[lineNumber - 1];
        }
        return "";
    }

    /**
     * Create a detailed error message with context
     */
    public static String formatSyntaxError(String baseMessage, String source, int lineNumber) {
        StringBuilder sb = new StringBuilder();
        sb.append(baseMessage);

        String line = getLineContext(source, lineNumber);
        if (!line.isEmpty()) {
            sb.append("\n  Line ").append(lineNumber).append(": ").append(line.trim());
            
            // Try to extract identifier from error context and suggest fix
            String[] parts = baseMessage.split("'");
            if (parts.length > 1) {
                String suggestion = suggestKeywordFix(parts[1]);
                if (suggestion != null) {
                    sb.append("\n  ").append(suggestion);
                }
            }
        }

        return sb.toString();
    }

    /**
     * Validate script for common issues before parsing
     * Critical errors are prefixed with [CRITICAL]
     */
    public static List<String> validateScript(String source) {
        List<String> issues = new ArrayList<>();
        String[] lines = source.split("\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            int lineNum = i + 1;

            // Skip empty lines and comments
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            // Check for incorrect capitalization of keywords - CRITICAL
            if (line.startsWith("While ") || line.matches("^While\\(.*")) {
                issues.add("[CRITICAL] Line " + lineNum + ": 'While' should be 'while' (keywords are case-sensitive)");
            }
            if (line.startsWith("If ") || line.matches("^If\\(.*")) {
                issues.add("[CRITICAL] Line " + lineNum + ": 'If' should be 'if' (keywords are case-sensitive)");
            }
            if (line.startsWith("For ")) {
                issues.add("[CRITICAL] Line " + lineNum + ": 'For' should be 'for' (keywords are case-sensitive)");
            }
            if (line.startsWith("Def ")) {
                issues.add("[CRITICAL] Line " + lineNum + ": 'Def' should be 'def' (keywords are case-sensitive)");
            }

            // Check for True/False instead of true/false - CRITICAL
            // Catches: "while True:", "if True", "False", etc.
            if (line.contains("True") && !line.contains("true")) {
                // Make sure it's not inside a string
                if (!isInsideString(line, line.indexOf("True"))) {
                    issues.add("[CRITICAL] Line " + lineNum + ": 'True' should be 'true' (boolean literals are lowercase)");
                }
            }
            if (line.contains("False") && !line.contains("false")) {
                if (!isInsideString(line, line.indexOf("False"))) {
                    issues.add("[CRITICAL] Line " + lineNum + ": 'False' should be 'false' (boolean literals are lowercase)");
                }
            }

            // Check for 'End' instead of 'end' - CRITICAL
            if (line.equals("End") || line.equals("END")) {
                issues.add("[CRITICAL] Line " + lineNum + ": 'End'/'END' should be 'end' (keywords are case-sensitive)");
            }
            
            // Check for missing colons after keywords - CRITICAL
            if ((line.startsWith("while ") || line.startsWith("if ") || 
                 line.startsWith("for ") || line.startsWith("def ")) && !line.endsWith(":")) {
                issues.add("[CRITICAL] Line " + lineNum + ": Missing ':' at end of statement");
            }
        }

        return issues;
    }

    /**
     * Helper to check if a position in a line is inside a string literal
     */
    private static boolean isInsideString(String line, int position) {
        boolean inDoubleQuote = false;
        boolean inSingleQuote = false;
        for (int i = 0; i < position && i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"' && (i == 0 || line.charAt(i - 1) != '\\')) {
                inDoubleQuote = !inDoubleQuote;
            }
            if (c == '\'' && (i == 0 || line.charAt(i - 1) != '\\')) {
                inSingleQuote = !inSingleQuote;
            }
        }
        return inDoubleQuote || inSingleQuote;
    }

    /**
     * Check if validation found any critical errors
     */
    public static boolean hasCriticalErrors(List<String> issues) {
        for (String issue : issues) {
            if (issue.startsWith("[CRITICAL]")) {
                return true;
            }
        }
        return false;
    }
}
