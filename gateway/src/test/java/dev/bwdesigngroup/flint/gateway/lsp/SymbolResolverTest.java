package dev.bwdesigngroup.flint.gateway.lsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.bwdesigngroup.flint.common.protocol.methods.lsp.Location;
import dev.bwdesigngroup.flint.common.protocol.methods.lsp.Position;
import org.junit.jupiter.api.Test;

/** Phase 3: intra-file hover + go-to-definition + references. */
class SymbolResolverTest {

    private final JythonParseService parse = new JythonParseService();

    private SymbolResolver resolver(String src) {
        return new SymbolResolver(parse.parse("m.py", src).ast, src);
    }

    // line/character are 0-based
    private Position at(int line, int ch) {
        return new Position(line, ch);
    }

    @Test
    void gotoDefinitionOfTopLevelFunctionFromCallSite() {
        String src =
                "def add(a, b):\n" // line 0; def name 'add' at col 4
                        + "    return a + b\n" // line 1
                        + "\n"
                        + "result = add(1, 2)\n"; // line 3; 'add' call at col 9
        Location def = resolver(src).definitionAt("m.py", at(3, 9));
        assertNotNull(def, "should resolve call 'add' to its def");
        assertEquals(0, def.range.start.line);
        assertEquals(4, def.range.start.character);
    }

    @Test
    void gotoDefinitionOfLocalParameter() {
        String src = "def scale(value):\n" + "    return value * 2\n"; // 'value' used line1 col11
        Location def = resolver(src).definitionAt("m.py", at(1, 11));
        assertNotNull(def);
        assertEquals(0, def.range.start.line); // param declared on def line
    }

    @Test
    void hoverShowsFunctionSignature() {
        String src = "def readValues(paths, timeout):\n    return None\n\nreadValues([], 5)\n";
        String hover = resolver(src).hoverMarkdownAt(at(3, 2));
        assertNotNull(hover);
        assertTrue(hover.contains("def readValues(paths, timeout)"), hover);
    }

    @Test
    void referencesFindsAllUses() {
        String src = "def foo():\n    pass\n\nfoo()\nfoo()\n";
        // position on the 'foo' def
        int count = resolver(src).referencesAt("m.py", at(0, 4), true).size();
        assertTrue(count >= 3, "expected def + 2 calls, got " + count);
    }

    @Test
    void unresolvedNameReturnsNull() {
        String src = "x = undefined_thing\n";
        // hover on 'undefined_thing' (col 4) — not defined anywhere
        assertNull(resolver(src).hoverMarkdownAt(at(0, 4)));
    }

    @Test
    void localShadowsAndResolvesToInnerScope() {
        String src =
                "value = 1\n" // module var
                        + "def f():\n"
                        + "    value = 2\n" // local
                        + "    return value\n"; // use -> should resolve to local (line 2), not
        // module
        Location def = resolver(src).definitionAt("m.py", at(3, 11));
        assertNotNull(def);
        assertEquals(2, def.range.start.line, "should resolve to the local assignment, not module");
    }
}
