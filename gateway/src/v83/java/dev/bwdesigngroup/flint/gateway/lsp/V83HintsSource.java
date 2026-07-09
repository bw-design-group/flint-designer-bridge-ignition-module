package dev.bwdesigngroup.flint.gateway.lsp;

import com.inductiveautomation.ignition.common.script.PackageTreeNode;
import com.inductiveautomation.ignition.common.script.typing.CompletionDescriptor;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import dev.bwdesigngroup.flint.common.protocol.methods.lsp.CompletionItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ignition 8.3 hints source: navigates {@code ScriptManager.getHintsTree()} ({@link
 * PackageTreeNode}).
 */
public class V83HintsSource implements HintsSource {

    private static final Logger logger = LoggerFactory.getLogger("flint.Gateway.Lsp.Hints");

    private static final int KIND_FUNCTION = 3;
    private static final int KIND_MODULE = 9;
    private static final int KIND_PROPERTY = 10;

    private final GatewayContext context;

    public V83HintsSource(GatewayContext context) {
        this.context = context;
    }

    private PackageTreeNode root() {
        try {
            return context.getScriptManager().getHintsTree();
        } catch (Throwable t) {
            logger.debug("Hints tree unavailable: {}", t.getMessage());
            return null;
        }
    }

    @Override
    public Set<String> rootNames() {
        PackageTreeNode root = root();
        if (root == null || root.children() == null) {
            return Collections.emptySet();
        }
        return root.children().keySet();
    }

    @Override
    public List<CompletionItem> members(String dottedPath, String partial) {
        List<CompletionItem> out = new ArrayList<>();
        PackageTreeNode node = root();
        if (node == null) {
            return out;
        }
        if (dottedPath != null && !dottedPath.isEmpty()) {
            for (String seg : dottedPath.split("\\.")) {
                if (node.children() == null) {
                    return out;
                }
                node = node.children().get(seg);
                if (node == null) {
                    return out;
                }
            }
        }
        String p = partial == null ? "" : partial.toLowerCase();

        if (node.children() != null) {
            for (String childName : node.children().keySet()) {
                if (matches(childName, p)) {
                    out.add(item(childName, KIND_MODULE, dottedPath, null, null));
                }
            }
        }
        if (node.methods() != null) {
            for (CompletionDescriptor.Method m : node.methods()) {
                if (matches(m.getName(), p)) {
                    out.add(
                            item(
                                    m.getName(),
                                    KIND_FUNCTION,
                                    dottedPath,
                                    signature(m),
                                    m.getDescription()));
                }
            }
        }
        if (node.attributes() != null) {
            for (CompletionDescriptor.Attribute a : node.attributes()) {
                if (matches(a.getName(), p)) {
                    out.add(item(a.getName(), KIND_PROPERTY, dottedPath, null, a.getDescription()));
                }
            }
        }
        return out;
    }

    private boolean matches(String name, String lowerPartial) {
        return name != null
                && (lowerPartial.isEmpty() || name.toLowerCase().startsWith(lowerPartial));
    }

    private String signature(CompletionDescriptor.Method m) {
        List<String> params = new ArrayList<>();
        if (m.getParameters() != null) {
            for (CompletionDescriptor.Parameter p : m.getParameters()) {
                params.add(p.getName());
            }
        }
        return m.getName() + "(" + String.join(", ", params) + ")";
    }

    private CompletionItem item(String label, int kind, String base, String detail, String doc) {
        CompletionItem ci = new CompletionItem(label, kind);
        ci.setDetail(detail);
        ci.setDocumentation(doc);
        ci.setInsertText(label);
        ci.setPath(base != null && !base.isEmpty() ? base + "." + label : label);
        return ci;
    }
}
