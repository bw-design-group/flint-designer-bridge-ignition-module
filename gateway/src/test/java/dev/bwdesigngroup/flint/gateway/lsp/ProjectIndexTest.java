package dev.bwdesigngroup.flint.gateway.lsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.bwdesigngroup.flint.common.platform.ResourceInfo;
import dev.bwdesigngroup.flint.common.protocol.methods.lsp.CompletionItem;
import dev.bwdesigngroup.flint.common.protocol.methods.lsp.Location;
import dev.bwdesigngroup.flint.common.protocol.methods.lsp.Position;
import dev.bwdesigngroup.flint.common.protocol.methods.lsp.WorkspaceSymbol;
import dev.bwdesigngroup.flint.gateway.resources.GatewayResourceStore;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Phase 5: cross-file project intelligence (index, completion, definition, workspace symbols). */
class ProjectIndexTest {

    /** In-memory store with two script modules. */
    private static class FakeStore implements GatewayResourceStore {
        @Override
        public List<String> listProjectNames() {
            return Arrays.asList("proj");
        }

        @Override
        public boolean isProjectAvailable(String project) {
            return "proj".equals(project);
        }

        @Override
        public String getProjectTitle(String project) {
            return project;
        }

        @Override
        public List<ResourceInfo> getResourcesOfType(
                String project, String moduleId, String typeId) {
            List<ResourceInfo> out = new ArrayList<>();
            if ("ignition".equals(moduleId) && "script-python".equals(typeId)) {
                out.add(new ResourceInfo("util/math", moduleId, typeId, "SRC_MATH", null));
                out.add(new ResourceInfo("app/main", moduleId, typeId, "SRC_MAIN", null));
            }
            return out;
        }

        @Override
        public byte[] readResourceData(ResourceInfo resource, String dataKey) {
            String tag = (String) resource.getNativeResource();
            String code;
            if ("SRC_MATH".equals(tag)) {
                code = "def add(a, b):\n    return a + b\n\nPI = 3.14159\n";
            } else {
                code = "import util.math\n\ndef run():\n    return util.math.add(1, 2)\n";
            }
            return code.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] readDefaultData(ResourceInfo resource) {
            return null;
        }

        @Override
        public String writeResourceData(String p, ResourceInfo r, String k, byte[] d) {
            return null;
        }

        @Override
        public String createResource(
                String p, ResourceInfo t, String m, String ty, String path, String k, byte[] d) {
            return null;
        }

        @Override
        public String deleteResource(String p, ResourceInfo r) {
            return null;
        }
    }

    private FlintLanguageServer server() {
        return new FlintLanguageServer(null, new ProjectIndex(new FakeStore()));
    }

    @Test
    void indexesModulesByDottedPath() {
        ProjectIndex idx = new ProjectIndex(new FakeStore());
        assertNotNull(idx.module("proj", "util.math"));
        assertNotNull(idx.module("proj", "app.main"));
        assertTrue(idx.rootPackages("proj").contains("util"));
    }

    @Test
    void crossFileCompletionOfModuleMembers() {
        // In app/main, completing "util.math." should list add + PI from the other file.
        String text = "import util.math\nx = util.math.\n";
        List<CompletionItem> items =
                server().completion("s", "app/main", text, new Position(1, 14), "proj");
        List<String> labels =
                items.stream().map(CompletionItem::getLabel).collect(Collectors.toList());
        assertTrue(labels.contains("add"), labels.toString());
        assertTrue(labels.contains("PI"), labels.toString());
    }

    @Test
    void crossFileDefinitionIntoOtherModule() {
        String text = "import util.math\nx = util.math.add(1, 2)\n"; // 'add' at line1 col14..
        Location def =
                server().definition(
                                "s", "app/main", text, new Position(1, 15), "proj", "file:///ws");
        assertNotNull(def, "should resolve cross-file to util/math");
        assertTrue(def.uri.contains("util/math"), def.uri);
        assertEquals(0, def.range.start.line, "add is on line 0 of util/math");
    }

    @Test
    void workspaceSymbolSearch() {
        List<WorkspaceSymbol> syms = server().workspaceSymbols("proj", "add", "file:///ws");
        assertTrue(
                syms.stream()
                        .anyMatch(
                                s -> "add".equals(s.name) && s.location.uri.contains("util/math")),
                syms.stream().map(s -> s.name).collect(Collectors.toList()).toString());
    }
}
