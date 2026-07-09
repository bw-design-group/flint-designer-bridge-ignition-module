package dev.bwdesigngroup.flint.gateway.lsp;

import dev.bwdesigngroup.flint.common.protocol.methods.lsp.DocumentSymbol;
import dev.bwdesigngroup.flint.common.protocol.methods.lsp.Location;
import dev.bwdesigngroup.flint.common.protocol.methods.lsp.Position;
import dev.bwdesigngroup.flint.common.protocol.methods.lsp.Range;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.python.antlr.PythonTree;
import org.python.antlr.ast.ClassDef;
import org.python.antlr.ast.FunctionDef;
import org.python.antlr.ast.Import;
import org.python.antlr.ast.ImportFrom;
import org.python.antlr.ast.Name;
import org.python.antlr.ast.alias;
import org.python.antlr.ast.arguments;
import org.python.antlr.ast.expr_contextType;
import org.python.antlr.base.expr;
import org.python.antlr.base.mod;
import org.python.antlr.base.stmt;

/**
 * Hand-rolled lexical analysis over a single-file Jython AST: collects definitions (functions,
 * classes, parameters, assignments, imports) with their owning function scope, and resolves the
 * symbol at a position for hover / go-to-definition / references. In-memory and null-safe (works on
 * error-recovered ASTs); no PySonar/global state.
 */
public class SymbolResolver {

    /**
     * A binding site. {@code owner} is the innermost enclosing function, or null for module scope.
     */
    public static class Definition {
        public final String id;
        public final int kind; // DocumentSymbol.KIND_*
        public final PythonTree nameNode;
        public final FunctionDef owner;
        public final String detail;

        Definition(String id, int kind, PythonTree nameNode, FunctionDef owner, String detail) {
            this.id = id;
            this.kind = kind;
            this.nameNode = nameNode;
            this.owner = owner;
            this.detail = detail;
        }
    }

    private final LineIndex index;
    private final List<Name> names = new ArrayList<>();
    private final List<FunctionDef> funcs = new ArrayList<>();
    private final List<Definition> definitions = new ArrayList<>();
    private final Map<FunctionDef, FunctionDef> parentFunc = new IdentityHashMap<>();

    public SymbolResolver(mod ast, String text) {
        this.index = new LineIndex(text);
        if (ast != null) {
            for (PythonTree child : safeChildren(ast)) {
                visit(child, null);
            }
        }
    }

    // ---- Public queries ----

    public Location definitionAt(String uri, Position pos) {
        Definition def = resolveAt(pos);
        if (def == null || def.nameNode == null) {
            return null;
        }
        return new Location(uri, rangeOf(def.nameNode));
    }

    public String hoverMarkdownAt(Position pos) {
        Definition def = resolveAt(pos);
        if (def == null) {
            return null;
        }
        switch (def.kind) {
            case DocumentSymbol.KIND_FUNCTION:
            case DocumentSymbol.KIND_METHOD:
                return "```python\ndef "
                        + def.id
                        + (def.detail != null ? def.detail : "()")
                        + "\n```";
            case DocumentSymbol.KIND_CLASS:
                return "```python\nclass " + def.id + "\n```";
            case DocumentSymbol.KIND_CONSTANT:
                return "(constant) `" + def.id + "`";
            default:
                return (def.detail != null ? def.detail + " " : "") + "`" + def.id + "`";
        }
    }

    /** All references (uses + the definition) of the symbol at a position, within this file. */
    public List<Location> referencesAt(String uri, Position pos, boolean includeDeclaration) {
        List<Location> out = new ArrayList<>();
        Name target = nameAt(index.offsetAt(pos.line, pos.character));
        if (target == null || target.getInternalId() == null) {
            return out;
        }
        String id = target.getInternalId();
        Definition def = resolveAt(pos);
        for (Name n : names) {
            if (id.equals(n.getInternalId())) {
                // Same-name usages that resolve to the same definition (best effort: same id).
                if (!includeDeclaration && def != null && n == def.nameNode) {
                    continue;
                }
                out.add(new Location(uri, rangeOf(n)));
            }
        }
        return out;
    }

    public String nameIdAt(Position pos) {
        Name n = nameAt(index.offsetAt(pos.line, pos.character));
        return n != null ? n.getInternalId() : null;
    }

    /** Definitions visible at a position (scope chain), innermost first, deduped by id. */
    public List<Definition> visibleDefinitions(Position pos) {
        int offset = index.offsetAt(pos.line, pos.character);
        List<Definition> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (FunctionDef scope : scopeChain(offset)) {
            for (Definition d : definitions) {
                if (d.owner == scope && d.id != null && seen.add(d.id)) {
                    out.add(d);
                }
            }
        }
        return out;
    }

    // ---- Resolution ----

    private Definition resolveAt(Position pos) {
        int offset = index.offsetAt(pos.line, pos.character);
        Name name = nameAt(offset);
        if (name == null || name.getInternalId() == null) {
            return null;
        }
        String id = name.getInternalId();
        List<FunctionDef> chain = scopeChain(offset);
        for (FunctionDef scope : chain) {
            Definition best = null;
            for (Definition d : definitions) {
                if (!id.equals(d.id) || d.owner != scope) {
                    continue;
                }
                // Prefer the last definition at or before the usage; else the first available.
                if (best == null) {
                    best = d;
                } else {
                    int dPos = startOf(d.nameNode);
                    int bPos = startOf(best.nameNode);
                    if (dPos <= offset && (bPos > offset || dPos > bPos)) {
                        best = d;
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    /** Scope chain from innermost enclosing function up to module (null terminator). */
    private List<FunctionDef> scopeChain(int offset) {
        List<FunctionDef> chain = new ArrayList<>();
        FunctionDef f = enclosingFunc(offset);
        while (f != null) {
            chain.add(f);
            f = parentFunc.get(f);
        }
        chain.add(null); // module scope
        return chain;
    }

    private FunctionDef enclosingFunc(int offset) {
        FunctionDef best = null;
        int bestStart = -1;
        for (FunctionDef f : funcs) {
            int s = f.getCharStartIndex();
            int e = f.getCharStopIndex();
            if (s >= 0 && e >= s && offset >= s && offset <= e + 1 && s > bestStart) {
                best = f;
                bestStart = s;
            }
        }
        return best;
    }

    private Name nameAt(int offset) {
        Name best = null;
        int bestLen = Integer.MAX_VALUE;
        for (Name n : names) {
            int s = n.getCharStartIndex();
            int e = n.getCharStopIndex();
            if (s >= 0 && e >= s && offset >= s && offset <= e + 1) {
                int len = e - s;
                if (len < bestLen) {
                    best = n;
                    bestLen = len;
                }
            }
        }
        return best;
    }

    // ---- AST walk ----

    private void visit(PythonTree node, FunctionDef enclosing) {
        if (node == null) {
            return;
        }
        if (node instanceof FunctionDef) {
            FunctionDef f = (FunctionDef) node;
            parentFunc.put(f, enclosing);
            funcs.add(f);
            PythonTree nameNode = f.getInternalNameNode() != null ? f.getInternalNameNode() : f;
            if (f.getInternalNameNode() != null) {
                names.add(f.getInternalNameNode());
            }
            define(
                    f.getInternalName(),
                    DocumentSymbol.KIND_FUNCTION,
                    nameNode,
                    enclosing,
                    signatureOf(f));
            collectParams(f);
            for (stmt s : safeBody(f.getInternalBody())) {
                visit(s, f);
            }
            return;
        }
        if (node instanceof ClassDef) {
            ClassDef c = (ClassDef) node;
            PythonTree nameNode = c.getInternalNameNode() != null ? c.getInternalNameNode() : c;
            if (c.getInternalNameNode() != null) {
                names.add(c.getInternalNameNode());
            }
            define(c.getInternalName(), DocumentSymbol.KIND_CLASS, nameNode, enclosing, null);
            for (stmt s : safeBody(c.getInternalBody())) {
                visit(s, enclosing);
            }
            return;
        }
        if (node instanceof Name) {
            Name n = (Name) node;
            names.add(n);
            if (n.getInternalCtx() == expr_contextType.Store && n.getInternalId() != null) {
                int kind =
                        isConstant(n.getInternalId())
                                ? DocumentSymbol.KIND_CONSTANT
                                : DocumentSymbol.KIND_VARIABLE;
                define(n.getInternalId(), kind, n, enclosing, "(variable)");
            }
            return;
        }
        if (node instanceof Import) {
            for (alias a : safeAliases(((Import) node).getInternalNames())) {
                addImport(a, enclosing);
            }
        } else if (node instanceof ImportFrom) {
            for (alias a : safeAliases(((ImportFrom) node).getInternalNames())) {
                addImport(a, enclosing);
            }
        }
        for (PythonTree child : safeChildren(node)) {
            visit(child, enclosing);
        }
    }

    private void collectParams(FunctionDef f) {
        arguments args = f.getInternalArgs();
        if (args == null) {
            return;
        }
        for (expr a : safeExprs(args.getInternalArgs())) {
            if (a instanceof Name) {
                Name p = (Name) a;
                names.add(p);
                define(p.getInternalId(), DocumentSymbol.KIND_VARIABLE, p, f, "(parameter)");
            }
        }
        if (args.getInternalVarargName() != null) {
            define(
                    args.getInternalVararg(),
                    DocumentSymbol.KIND_VARIABLE,
                    args.getInternalVarargName(),
                    f,
                    "(parameter)");
        }
        if (args.getInternalKwargName() != null) {
            define(
                    args.getInternalKwarg(),
                    DocumentSymbol.KIND_VARIABLE,
                    args.getInternalKwargName(),
                    f,
                    "(parameter)");
        }
    }

    private void addImport(alias a, FunctionDef enclosing) {
        if (a == null) {
            return;
        }
        String bound;
        PythonTree nameNode;
        if (a.getInternalAsname() != null) {
            bound = a.getInternalAsname();
            nameNode = a.getInternalAsnameNode() != null ? a.getInternalAsnameNode() : a;
        } else {
            // "import a.b.c" binds "a"; "from x import y" binds "y".
            String full = a.getInternalName();
            bound =
                    full != null && full.contains(".")
                            ? full.substring(0, full.indexOf('.'))
                            : full;
            List<Name> nns = a.getInternalNameNodes();
            nameNode = (nns != null && !nns.isEmpty()) ? nns.get(0) : a;
        }
        if (bound != null) {
            define(bound, DocumentSymbol.KIND_MODULE, nameNode, enclosing, "(import)");
        }
    }

    private void define(
            String id, int kind, PythonTree nameNode, FunctionDef owner, String detail) {
        if (id != null && !id.isEmpty()) {
            definitions.add(new Definition(id, kind, nameNode, owner, detail));
        }
    }

    // ---- Helpers ----

    private String signatureOf(FunctionDef f) {
        StringBuilder sb = new StringBuilder("(");
        arguments args = f.getInternalArgs();
        List<String> parts = new ArrayList<>();
        if (args != null) {
            for (expr a : safeExprs(args.getInternalArgs())) {
                if (a instanceof Name) {
                    parts.add(((Name) a).getInternalId());
                }
            }
            if (args.getInternalVararg() != null) {
                parts.add("*" + args.getInternalVararg());
            }
            if (args.getInternalKwarg() != null) {
                parts.add("**" + args.getInternalKwarg());
            }
        }
        sb.append(String.join(", ", parts)).append(")");
        return sb.toString();
    }

    private Range rangeOf(PythonTree node) {
        int s = node.getCharStartIndex();
        int e = node.getCharStopIndex();
        if (s >= 0 && e >= s) {
            return new Range(index.positionAt(s), index.positionAt(e));
        }
        int line = Math.max(0, node.getLineno() - 1);
        int col = Math.max(0, node.getCol_offset());
        return Range.of(line, col, line, col + 1);
    }

    private int startOf(PythonTree node) {
        int s = node.getCharStartIndex();
        return s >= 0 ? s : 0;
    }

    private boolean isConstant(String id) {
        return id.equals(id.toUpperCase()) && id.matches("[A-Z_][A-Z0-9_]*");
    }

    private List<PythonTree> safeChildren(PythonTree node) {
        List<PythonTree> c = node.getChildren();
        return c != null ? c : new ArrayList<PythonTree>();
    }

    private List<stmt> safeBody(List<stmt> body) {
        return body != null ? body : new ArrayList<stmt>();
    }

    private List<expr> safeExprs(List<expr> exprs) {
        return exprs != null ? exprs : new ArrayList<expr>();
    }

    private List<alias> safeAliases(List<alias> aliases) {
        return aliases != null ? aliases : new ArrayList<alias>();
    }
}
