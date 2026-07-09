package dev.bwdesigngroup.flint.gateway.lsp;

import dev.bwdesigngroup.flint.common.protocol.methods.lsp.CompletionItem;
import dev.bwdesigngroup.flint.common.protocol.methods.lsp.DocumentSymbol;
import dev.bwdesigngroup.flint.common.protocol.methods.lsp.Position;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.python.antlr.base.mod;

/**
 * Position-aware completion: extracts the dotted expression before the cursor and returns either
 * member completions ({@code system.tag.<here>}) from the {@link HintsSource}, or bare-identifier
 * completions (visible scope symbols + hint roots + keywords).
 */
public class CompletionEngine {

    // LSP CompletionItemKind
    private static final int KIND_METHOD = 2;
    private static final int KIND_FUNCTION = 3;
    private static final int KIND_VARIABLE = 6;
    private static final int KIND_CLASS = 7;
    private static final int KIND_MODULE = 9;
    private static final int KIND_KEYWORD = 14;
    private static final int KIND_CONSTANT = 21;

    private static final String[] KEYWORDS = {
        "def",
        "class",
        "return",
        "import",
        "from",
        "if",
        "elif",
        "else",
        "for",
        "while",
        "try",
        "except",
        "finally",
        "with",
        "as",
        "pass",
        "break",
        "continue",
        "lambda",
        "print",
        "and",
        "or",
        "not",
        "in",
        "is",
        "None",
        "True",
        "False",
        "global",
        "yield",
        "raise",
        "del",
        "assert",
        "exec"
    };

    private final HintsSource hints;

    public CompletionEngine(HintsSource hints) {
        this.hints = hints;
    }

    public List<CompletionItem> complete(mod ast, String text, Position pos) {
        return complete(ast, text, pos, null, null);
    }

    public List<CompletionItem> complete(
            mod ast, String text, Position pos, ProjectIndex projectIndex, String project) {
        LineIndex index = new LineIndex(text);
        int offset = index.offsetAt(pos.line, pos.character);
        String expr = exprBeforeCursor(text, offset);

        String base;
        String partial;
        int lastDot = expr.lastIndexOf('.');
        if (lastDot >= 0) {
            base = expr.substring(0, lastDot);
            partial = expr.substring(lastDot + 1);
        } else {
            base = "";
            partial = expr;
        }

        Map<String, CompletionItem> out = new LinkedHashMap<>();

        if (!base.isEmpty()) {
            // Member completion: system.* hints...
            if (hints != null) {
                for (CompletionItem ci : hints.members(base, partial)) {
                    out.putIfAbsent(ci.getKind() + ":" + ci.getLabel(), ci);
                }
            }
            // ...and project-script module members (cross-file).
            if (projectIndex != null && project != null) {
                ProjectIndex.Module m = projectIndex.module(project, base);
                if (m != null) {
                    for (DocumentSymbol sym : m.symbols) {
                        if (startsWith(sym.name, partial)) {
                            CompletionItem ci = symbolItem(sym);
                            out.putIfAbsent(ci.getKind() + ":" + ci.getLabel(), ci);
                        }
                    }
                }
            }
        } else {
            // Bare identifier: visible scope symbols first, then hint roots, project roots,
            // keywords.
            SymbolResolver resolver = new SymbolResolver(ast, text);
            for (SymbolResolver.Definition d : resolver.visibleDefinitions(pos)) {
                if (startsWith(d.id, partial)) {
                    CompletionItem ci = scopeItem(d);
                    out.putIfAbsent(ci.getKind() + ":" + ci.getLabel(), ci);
                }
            }
            if (hints != null) {
                for (String root : hints.rootNames()) {
                    if (startsWith(root, partial)) {
                        CompletionItem ci = new CompletionItem(root, KIND_MODULE);
                        ci.setInsertText(root);
                        out.putIfAbsent(KIND_MODULE + ":" + root, ci);
                    }
                }
            }
            if (projectIndex != null && project != null) {
                for (String root : projectIndex.rootPackages(project)) {
                    if (startsWith(root, partial)) {
                        CompletionItem ci = new CompletionItem(root, KIND_MODULE);
                        ci.setInsertText(root);
                        out.putIfAbsent(KIND_MODULE + ":" + root, ci);
                    }
                }
            }
            for (String kw : KEYWORDS) {
                if (startsWith(kw, partial)) {
                    out.putIfAbsent(KIND_KEYWORD + ":" + kw, new CompletionItem(kw, KIND_KEYWORD));
                }
            }
        }
        return new ArrayList<>(out.values());
    }

    private CompletionItem symbolItem(DocumentSymbol sym) {
        int kind;
        switch (sym.kind) {
            case DocumentSymbol.KIND_FUNCTION:
                kind = KIND_FUNCTION;
                break;
            case DocumentSymbol.KIND_METHOD:
                kind = KIND_METHOD;
                break;
            case DocumentSymbol.KIND_CLASS:
                kind = KIND_CLASS;
                break;
            case DocumentSymbol.KIND_CONSTANT:
                kind = KIND_CONSTANT;
                break;
            default:
                kind = KIND_VARIABLE;
        }
        CompletionItem ci = new CompletionItem(sym.name, kind);
        ci.setInsertText(sym.name);
        return ci;
    }

    private String exprBeforeCursor(String text, int offset) {
        int start = offset;
        while (start > 0) {
            char c = text.charAt(start - 1);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '.') {
                start--;
            } else {
                break;
            }
        }
        return text.substring(start, offset);
    }

    private CompletionItem scopeItem(SymbolResolver.Definition d) {
        int kind;
        switch (d.kind) {
            case DocumentSymbol.KIND_FUNCTION:
                kind = KIND_FUNCTION;
                break;
            case DocumentSymbol.KIND_METHOD:
                kind = KIND_METHOD;
                break;
            case DocumentSymbol.KIND_CLASS:
                kind = KIND_CLASS;
                break;
            case DocumentSymbol.KIND_CONSTANT:
                kind = KIND_CONSTANT;
                break;
            case DocumentSymbol.KIND_MODULE:
                kind = KIND_MODULE;
                break;
            default:
                kind = KIND_VARIABLE;
        }
        CompletionItem ci = new CompletionItem(d.id, kind);
        ci.setInsertText(d.id);
        if (kind == KIND_FUNCTION && d.detail != null) {
            ci.setDetail(d.id + d.detail);
        } else if (d.detail != null && !d.detail.startsWith("(parameter")) {
            ci.setDetail(d.detail);
        }
        return ci;
    }

    private boolean startsWith(String name, String partial) {
        return name != null
                && (partial.isEmpty() || name.toLowerCase().startsWith(partial.toLowerCase()));
    }
}
