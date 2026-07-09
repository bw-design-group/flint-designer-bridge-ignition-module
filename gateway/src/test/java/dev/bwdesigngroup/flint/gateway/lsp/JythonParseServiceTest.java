package dev.bwdesigngroup.flint.gateway.lsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.bwdesigngroup.flint.common.protocol.methods.lsp.LspDiagnostic;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Phase 1: verifies the Jython 2.7 parser + recovering diagnostics. */
class JythonParseServiceTest {

    private final JythonParseService service = new JythonParseService();

    @Test
    void parsesValidPython2WithNoDiagnostics() {
        // Python-2-only syntax (print statement, except-comma) must parse cleanly.
        String src =
                "def greet(name):\n"
                        + "    print \"hello\", name\n"
                        + "\n"
                        + "try:\n"
                        + "    greet('x')\n"
                        + "except Exception, e:\n"
                        + "    print e\n";
        JythonParseService.ParseResult result = service.parse("test.py", src);
        assertNotNull(result.ast, "expected an AST for valid Py2 source");
        assertTrue(
                result.diagnostics.isEmpty(),
                "expected no diagnostics, got: " + result.diagnostics);
    }

    @Test
    void reportsSyntaxErrorWithPosition() {
        // Missing closing paren -> syntax error.
        String src = "def broken(:\n    pass\n";
        JythonParseService.ParseResult result = service.parse("bad.py", src);
        assertFalse(result.diagnostics.isEmpty(), "expected at least one diagnostic");
        LspDiagnostic d = result.diagnostics.get(0);
        assertEquals(LspDiagnostic.SEVERITY_ERROR, d.severity);
        assertNotNull(d.range);
        assertNotNull(d.range.start);
        assertTrue(d.range.start.line >= 0);
    }

    @Test
    void recoversFromErrorsAndStillReturnsAst() {
        // A broken line in the middle; recovering handler should still yield an AST + a diagnostic.
        String src = "x = 1\ndef f(\ny = 2\n";
        JythonParseService.ParseResult result = service.parse("recover.py", src);
        assertFalse(result.diagnostics.isEmpty(), "expected diagnostics for broken buffer");
    }

    @Test
    void emptyDocumentIsClean() {
        JythonParseService.ParseResult result = service.parse("empty.py", "");
        assertTrue(result.diagnostics.isEmpty());
    }

    @Test
    void languageServerSyncsAndDiagnoses() {
        FlintLanguageServer server = new FlintLanguageServer();
        server.didOpen("s1", "u.py", "print \"ok\"\n");
        List<LspDiagnostic> clean = server.diagnostics("s1", "u.py");
        assertTrue(clean.isEmpty(), "clean doc should have no diagnostics");

        server.didChange("s1", "u.py", "def broken(:\n");
        List<LspDiagnostic> broken = server.diagnostics("s1", "u.py");
        assertFalse(broken.isEmpty(), "broken doc should report diagnostics");

        server.didClose("s1", "u.py");
        assertTrue(server.diagnostics("s1", "u.py").isEmpty());
    }
}
