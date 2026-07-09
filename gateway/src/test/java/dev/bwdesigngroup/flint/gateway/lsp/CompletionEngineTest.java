package dev.bwdesigngroup.flint.gateway.lsp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.bwdesigngroup.flint.common.protocol.methods.lsp.CompletionItem;
import dev.bwdesigngroup.flint.common.protocol.methods.lsp.Position;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Phase 4: position-aware completion (scope symbols + system.* hints + keywords). */
class CompletionEngineTest {

    private final JythonParseService parse = new JythonParseService();

    /**
     * Minimal fake hints source mimicking system.* -> {tag, date, ...}, system.tag ->
     * {readBlocking}.
     */
    private static class FakeHints implements HintsSource {
        @Override
        public Set<String> rootNames() {
            return new HashSet<>(Arrays.asList("system"));
        }

        @Override
        public List<CompletionItem> members(String dottedPath, String partial) {
            List<CompletionItem> out = new ArrayList<>();
            if ("system".equals(dottedPath)) {
                for (String m : new String[] {"tag", "date", "perspective"}) {
                    if (m.startsWith(partial.toLowerCase())) {
                        out.add(new CompletionItem(m, 9));
                    }
                }
            } else if ("system.tag".equals(dottedPath)) {
                for (String m : new String[] {"readBlocking", "writeBlocking"}) {
                    if (m.toLowerCase().startsWith(partial.toLowerCase())) {
                        out.add(new CompletionItem(m, 3));
                    }
                }
            }
            return out;
        }
    }

    private List<String> completeLabels(String src, int line, int ch) {
        CompletionEngine engine = new CompletionEngine(new FakeHints());
        return engine.complete(parse.parse("m.py", src).ast, src, new Position(line, ch)).stream()
                .map(CompletionItem::getLabel)
                .collect(Collectors.toList());
    }

    @Test
    void memberCompletionOfSystemModule() {
        // "system." then complete -> tag/date/perspective
        String src = "x = system.\n";
        List<String> labels = completeLabels(src, 0, 11); // after "system."
        assertTrue(labels.contains("tag"), labels.toString());
        assertTrue(labels.contains("date"), labels.toString());
    }

    @Test
    void memberCompletionNested() {
        String src = "x = system.tag.re\n";
        List<String> labels = completeLabels(src, 0, 17); // after "system.tag.re"
        assertTrue(labels.contains("readBlocking"), labels.toString());
        assertFalse(labels.contains("writeBlocking"), labels.toString());
    }

    @Test
    void bareCompletionIncludesScopeSymbolsAndRoots() {
        String src =
                "def readValues(paths):\n"
                        + "    return paths\n"
                        + "\n"
                        + "def readNames():\n"
                        + "    r\n"; // completing 'r' at module-visible scope
        List<String> labels = completeLabels(src, 4, 5);
        assertTrue(labels.contains("readValues"), labels.toString());
        assertTrue(labels.contains("readNames"), labels.toString());
        assertTrue(labels.contains("return"), "keywords expected: " + labels);
    }

    @Test
    void bareCompletionIncludesSystemRoot() {
        String src = "sy\n";
        List<String> labels = completeLabels(src, 0, 2);
        assertTrue(labels.contains("system"), labels.toString());
    }

    @Test
    void localParameterVisibleInCompletion() {
        String src = "def scale(value):\n    v\n";
        List<String> labels = completeLabels(src, 1, 5);
        assertTrue(labels.contains("value"), labels.toString());
    }
}
