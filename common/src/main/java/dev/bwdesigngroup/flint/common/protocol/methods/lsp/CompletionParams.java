package dev.bwdesigngroup.flint.common.protocol.methods.lsp;

/**
 * Parameters for the lsp.completion method. Requests code completion at a specific position in a
 * document.
 */
public class CompletionParams {
    /** The prefix/path being completed (e.g., "system.tag") */
    private String prefix;

    /** The full line of code for context */
    private String line;

    /** Line number (0-based) */
    private int lineNumber;

    /** Character offset within the line */
    private int character;

    /** The trigger character that initiated completion (e.g., ".") */
    private String triggerCharacter;

    public CompletionParams() {}

    public CompletionParams(String prefix, String line, int lineNumber, int character) {
        this.prefix = prefix;
        this.line = line;
        this.lineNumber = lineNumber;
        this.character = character;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getLine() {
        return line;
    }

    public void setLine(String line) {
        this.line = line;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public int getCharacter() {
        return character;
    }

    public void setCharacter(int character) {
        this.character = character;
    }

    public String getTriggerCharacter() {
        return triggerCharacter;
    }

    public void setTriggerCharacter(String triggerCharacter) {
        this.triggerCharacter = triggerCharacter;
    }
}
