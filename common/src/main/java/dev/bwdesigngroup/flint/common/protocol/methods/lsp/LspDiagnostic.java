package dev.bwdesigngroup.flint.common.protocol.methods.lsp;

/** LSP Diagnostic (a syntax/semantic problem in a document). */
public class LspDiagnostic {
    /** LSP DiagnosticSeverity: 1=Error, 2=Warning, 3=Information, 4=Hint. */
    public static final int SEVERITY_ERROR = 1;

    public static final int SEVERITY_WARNING = 2;
    public static final int SEVERITY_INFO = 3;
    public static final int SEVERITY_HINT = 4;

    public Range range;
    public int severity;
    public String message;
    public String source;

    public LspDiagnostic() {}

    public LspDiagnostic(Range range, int severity, String message, String source) {
        this.range = range;
        this.severity = severity;
        this.message = message;
        this.source = source;
    }
}
