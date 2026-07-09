package dev.bwdesigngroup.flint.gateway.lsp;

import dev.bwdesigngroup.flint.common.platform.ResourceInfo;
import dev.bwdesigngroup.flint.common.protocol.methods.lsp.DocumentSymbol;
import dev.bwdesigngroup.flint.gateway.resources.GatewayResourceStore;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cross-file index of a project's Jython script library. Enumerates {@code ignition/script-python}
 * resources, maps each resource path to a Python module path ({@code myPkg/myMod} -> {@code
 * myPkg.myMod}), and extracts each module's top-level symbols so the language server can offer
 * cross-file completion, go-to-definition into project scripts, and workspace symbol search — the
 * capability external stubs cannot provide. Cached per project; rebuilt on demand.
 */
public class ProjectIndex {

    private static final Logger logger = LoggerFactory.getLogger("flint.Gateway.Lsp.ProjectIndex");

    private static final String MODULE_ID = "ignition";
    private static final String TYPE_ID = "script-python";
    private static final String CODE_KEY = "code.py";

    /** One project script module. */
    public static class Module {
        public final String modulePath; // dotted, e.g. "myPkg.myMod"
        public final String resourcePath; // slashed, e.g. "myPkg/myMod"
        public final String text;
        public final List<DocumentSymbol> symbols; // top-level defs/classes/vars

        Module(String modulePath, String resourcePath, String text, List<DocumentSymbol> symbols) {
            this.modulePath = modulePath;
            this.resourcePath = resourcePath;
            this.text = text;
            this.symbols = symbols;
        }
    }

    private final GatewayResourceStore store;
    private final JythonParseService parser = new JythonParseService();
    private final DocumentSymbolService symbolService = new DocumentSymbolService();

    // project -> (modulePath -> Module)
    private final Map<String, Map<String, Module>> cache = new ConcurrentHashMap<>();

    public ProjectIndex(GatewayResourceStore store) {
        this.store = store;
    }

    public void invalidate(String project) {
        if (project != null) {
            cache.remove(project);
        }
    }

    /** All modules of a project (cached). */
    public Map<String, Module> modules(String project) {
        if (project == null) {
            return new LinkedHashMap<>();
        }
        return cache.computeIfAbsent(project, this::build);
    }

    public Module module(String project, String modulePath) {
        return modules(project).get(modulePath);
    }

    /** Project names available on the gateway. */
    public List<String> projectNames() {
        try {
            return store.listProjectNames();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** Top-level module segments (e.g. "myPkg") for bare completion. */
    public List<String> rootPackages(String project) {
        List<String> roots = new ArrayList<>();
        for (String modulePath : modules(project).keySet()) {
            String root =
                    modulePath.contains(".")
                            ? modulePath.substring(0, modulePath.indexOf('.'))
                            : modulePath;
            if (!roots.contains(root)) {
                roots.add(root);
            }
        }
        return roots;
    }

    private Map<String, Module> build(String project) {
        Map<String, Module> result = new LinkedHashMap<>();
        try {
            List<ResourceInfo> resources = store.getResourcesOfType(project, MODULE_ID, TYPE_ID);
            for (ResourceInfo res : resources) {
                String resourcePath = res.getPath();
                if (resourcePath == null || resourcePath.isEmpty()) {
                    continue;
                }
                byte[] data = store.readResourceData(res, CODE_KEY);
                if (data == null || data.length == 0) {
                    data = store.readDefaultData(res);
                }
                String text = data != null ? new String(data, StandardCharsets.UTF_8) : "";
                JythonParseService.ParseResult parse = parser.parse(resourcePath, text);
                List<DocumentSymbol> syms = symbolService.symbols(parse.ast, text);
                String modulePath = resourcePath.replace('/', '.');
                result.put(modulePath, new Module(modulePath, resourcePath, text, syms));
            }
            logger.debug("Indexed {} script modules for project {}", result.size(), project);
        } catch (Exception e) {
            logger.debug("Failed to index project {}: {}", project, e.getMessage());
        }
        return result;
    }
}
