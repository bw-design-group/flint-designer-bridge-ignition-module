package dev.bwdesigngroup.flint.gateway.lsp;

import dev.bwdesigngroup.flint.common.protocol.methods.lsp.LspDiagnostic;
import dev.bwdesigngroup.flint.common.protocol.methods.lsp.Range;
import java.util.ArrayList;
import java.util.List;
import org.python.antlr.BaseParser;
import org.python.antlr.RecordingErrorHandler;
import org.python.antlr.base.mod;
import org.python.antlr.runtime.ANTLRStringStream;
import org.python.antlr.runtime.CharStream;
import org.python.antlr.runtime.RecognitionException;
import org.python.core.CompilerFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses Ignition Jython (Python 2.7) source into a positioned AST using Jython's own ANTLR parser,
 * with a recovering error handler so partially-invalid editor buffers still yield an AST plus a
 * collected list of syntax diagnostics. This is the foundation for all LSP features.
 */
public class JythonParseService {

    private static final Logger logger = LoggerFactory.getLogger("flint.Gateway.Lsp.Parse");
    private static final String SOURCE = "flint-jython";

    /** Result of parsing: the (possibly error-recovered) AST and any syntax diagnostics. */
    public static class ParseResult {
        public final mod ast;
        public final List<LspDiagnostic> diagnostics;

        public ParseResult(mod ast, List<LspDiagnostic> diagnostics) {
            this.ast = ast;
            this.diagnostics = diagnostics;
        }
    }

    /** Parses source text; never throws. */
    public ParseResult parse(String uri, String text) {
        List<LspDiagnostic> diagnostics = new ArrayList<>();
        mod ast = null;
        try {
            CharStream charStream = new ANTLRStringStream(text != null ? text : "");
            BaseParser parser =
                    new BaseParser(charStream, uri != null ? uri : "<flint>", new CompilerFlags());
            RecordingErrorHandler errorHandler = new RecordingErrorHandler();
            parser.setAntlrErrorHandler(errorHandler);

            ast = parser.parseModule();

            for (RecognitionException error : errorHandler.errs) {
                diagnostics.add(toDiagnostic(error));
            }
        } catch (Throwable t) {
            // Lexer-level or unexpected failures still surface as a single diagnostic.
            logger.debug("Parse failure for {}: {}", uri, t.getMessage());
            diagnostics.add(
                    new LspDiagnostic(
                            Range.of(0, 0, 0, 1),
                            LspDiagnostic.SEVERITY_ERROR,
                            "Parse error: " + describeThrowable(t),
                            SOURCE));
        }
        return new ParseResult(ast, diagnostics);
    }

    private LspDiagnostic toDiagnostic(RecognitionException error) {
        // ANTLR lines are 1-based; LSP lines are 0-based. charPositionInLine is already 0-based.
        int line = Math.max(0, error.line - 1);
        int character = Math.max(0, error.charPositionInLine);
        int endChar = character + 1;
        String message = "Syntax error";
        try {
            if (error.token != null && error.token.getText() != null) {
                message = "Syntax error near '" + error.token.getText() + "'";
            }
        } catch (Exception ignored) {
            // keep default message
        }
        return new LspDiagnostic(
                Range.of(line, character, line, endChar),
                LspDiagnostic.SEVERITY_ERROR,
                message,
                SOURCE);
    }

    private String describeThrowable(Throwable t) {
        String message = t.getMessage();
        return message != null ? message : t.getClass().getSimpleName();
    }
}
