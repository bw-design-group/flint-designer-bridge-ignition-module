package dev.bwdesigngroup.flint.gateway.lsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.bwdesigngroup.flint.common.protocol.methods.lsp.DocumentSymbol;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Phase 2: document symbol / outline extraction. */
class DocumentSymbolServiceTest {

    private final JythonParseService parse = new JythonParseService();
    private final DocumentSymbolService symbols = new DocumentSymbolService();

    private List<DocumentSymbol> outline(String src) {
        return symbols.symbols(parse.parse("m.py", src).ast, src);
    }

    @Test
    void extractsFunctionsClassesMethodsAndConstants() {
        String src =
                "MAX_RETRIES = 5\n"
                        + "def top_level(a, b):\n"
                        + "    return a + b\n"
                        + "\n"
                        + "class Motor(object):\n"
                        + "    def start(self):\n"
                        + "        pass\n"
                        + "    def stop(self):\n"
                        + "        pass\n";
        List<DocumentSymbol> syms = outline(src);
        List<String> names = syms.stream().map(s -> s.name).collect(Collectors.toList());
        assertTrue(names.contains("MAX_RETRIES"), names.toString());
        assertTrue(names.contains("top_level"), names.toString());
        assertTrue(names.contains("Motor"), names.toString());

        DocumentSymbol constant =
                syms.stream().filter(s -> s.name.equals("MAX_RETRIES")).findFirst().orElseThrow();
        assertEquals(DocumentSymbol.KIND_CONSTANT, constant.kind);

        DocumentSymbol fn =
                syms.stream().filter(s -> s.name.equals("top_level")).findFirst().orElseThrow();
        assertEquals(DocumentSymbol.KIND_FUNCTION, fn.kind);

        DocumentSymbol motor =
                syms.stream().filter(s -> s.name.equals("Motor")).findFirst().orElseThrow();
        assertEquals(DocumentSymbol.KIND_CLASS, motor.kind);
        List<String> methods =
                motor.children.stream().map(s -> s.name).collect(Collectors.toList());
        assertTrue(methods.contains("start") && methods.contains("stop"), methods.toString());
        assertEquals(DocumentSymbol.KIND_METHOD, motor.children.get(0).kind);
    }

    @Test
    void selectionRangeIsContainedInRangeAndOnNameLine() {
        String src = "def readValues(paths):\n    return paths\n";
        DocumentSymbol fn = outline(src).get(0);
        assertNotNull(fn.range);
        assertNotNull(fn.selectionRange);
        // name 'readValues' starts at column 4 on line 0
        assertEquals(0, fn.selectionRange.start.line);
        assertEquals(4, fn.selectionRange.start.character);
        assertTrue(fn.range.start.line <= fn.selectionRange.start.line);
    }
}
