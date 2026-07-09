package dev.bwdesigngroup.flint.gateway.lsp;

import com.inductiveautomation.ignition.common.script.hints.ScriptFunctionHint;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import dev.bwdesigngroup.flint.common.protocol.methods.lsp.CompletionItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ignition 8.1 hints source: uses {@code ScriptManager.getHintsMap()} (top-level module -> list of
 * function hints whose {@code getAutocompleteText()} is the full dotted path).
 */
public class V81HintsSource implements HintsSource {

    private static final Logger logger = LoggerFactory.getLogger("flint.Gateway.Lsp.Hints");

    private static final int KIND_FUNCTION = 3;
    private static final int KIND_MODULE = 9;

    private final GatewayContext context;

    public V81HintsSource(GatewayContext context) {
        this.context = context;
    }

    private Map<String, List<ScriptFunctionHint>> hints() {
        try {
            return context.getScriptManager().getHintsMap();
        } catch (Throwable t) {
            logger.debug("Hints map unavailable: {}", t.getMessage());
            return Collections.emptyMap();
        }
    }

    @Override
    public Set<String> rootNames() {
        return hints().keySet();
    }

    @Override
    public List<CompletionItem> members(String dottedPath, String partial) {
        Map<String, List<ScriptFunctionHint>> map = hints();
        String p = partial == null ? "" : partial.toLowerCase();

        if (dottedPath == null || dottedPath.isEmpty()) {
            List<CompletionItem> out = new ArrayList<>();
            for (String root : map.keySet()) {
                if (matches(root, p)) {
                    out.add(item(root, KIND_MODULE, null, null, null));
                }
            }
            return out;
        }

        String firstSegment = dottedPath.split("\\.")[0];
        List<ScriptFunctionHint> relevant = null;
        for (Map.Entry<String, List<ScriptFunctionHint>> e : map.entrySet()) {
            if (e.getKey().equalsIgnoreCase(firstSegment)) {
                relevant = e.getValue();
                break;
            }
        }
        if (relevant == null) {
            return Collections.emptyList();
        }

        String base = dottedPath + ".";
        // Dedupe by next segment; a segment is a subpackage if the remainder still has a dot.
        Map<String, CompletionItem> byName = new LinkedHashMap<>();
        for (ScriptFunctionHint hint : relevant) {
            String path = stripParens(hint.getAutocompleteText());
            if (path == null || !path.toLowerCase().startsWith(base.toLowerCase())) {
                continue;
            }
            String remainder = path.substring(base.length());
            if (remainder.isEmpty()) {
                continue;
            }
            int dot = remainder.indexOf('.');
            if (dot >= 0) {
                String seg = remainder.substring(0, dot);
                if (matches(seg, p) && !byName.containsKey(seg)) {
                    byName.put(seg, item(seg, KIND_MODULE, dottedPath, null, null));
                }
            } else {
                if (matches(remainder, p) && !byName.containsKey(remainder)) {
                    byName.put(
                            remainder,
                            item(
                                    remainder,
                                    KIND_FUNCTION,
                                    dottedPath,
                                    hint.getMethodSignature(),
                                    hint.getMethodDescription()));
                }
            }
        }
        return new ArrayList<>(byName.values());
    }

    private boolean matches(String name, String lowerPartial) {
        return name != null
                && (lowerPartial.isEmpty() || name.toLowerCase().startsWith(lowerPartial));
    }

    private String stripParens(String autocompleteText) {
        if (autocompleteText == null) {
            return null;
        }
        int paren = autocompleteText.indexOf('(');
        return paren >= 0 ? autocompleteText.substring(0, paren) : autocompleteText;
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
