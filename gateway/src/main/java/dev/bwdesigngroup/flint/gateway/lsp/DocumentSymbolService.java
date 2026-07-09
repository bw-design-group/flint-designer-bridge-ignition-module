package dev.bwdesigngroup.flint.gateway.lsp;

import dev.bwdesigngroup.flint.common.protocol.methods.lsp.DocumentSymbol;
import dev.bwdesigngroup.flint.common.protocol.methods.lsp.Position;
import dev.bwdesigngroup.flint.common.protocol.methods.lsp.Range;
import java.util.ArrayList;
import java.util.List;
import org.python.antlr.PythonTree;
import org.python.antlr.ast.Assign;
import org.python.antlr.ast.ClassDef;
import org.python.antlr.ast.FunctionDef;
import org.python.antlr.ast.Module;
import org.python.antlr.ast.Name;
import org.python.antlr.base.expr;
import org.python.antlr.base.mod;
import org.python.antlr.base.stmt;

/**
 * Builds an LSP document-symbol (outline) tree from a Jython AST: functions, classes, methods, and
 * module/class-level variable/constant assignments, with accurate ranges via {@link LineIndex}.
 */
public class DocumentSymbolService {

    public List<DocumentSymbol> symbols(mod ast, String text) {
        List<DocumentSymbol> out = new ArrayList<>();
        if (!(ast instanceof Module)) {
            return out;
        }
        LineIndex index = new LineIndex(text);
        for (stmt s : ((Module) ast).getInternalBody()) {
            collect(s, false, index, out);
        }
        return out;
    }

    private void collect(stmt s, boolean inClass, LineIndex index, List<DocumentSymbol> out) {
        if (s instanceof FunctionDef) {
            FunctionDef f = (FunctionDef) s;
            DocumentSymbol sym =
                    make(
                            f.getInternalName(),
                            inClass ? DocumentSymbol.KIND_METHOD : DocumentSymbol.KIND_FUNCTION,
                            f,
                            f.getInternalNameNode(),
                            f.getInternalName(),
                            index);
            for (stmt child : f.getInternalBody()) {
                collect(child, false, index, sym.children);
            }
            out.add(sym);
        } else if (s instanceof ClassDef) {
            ClassDef c = (ClassDef) s;
            DocumentSymbol sym =
                    make(
                            c.getInternalName(),
                            DocumentSymbol.KIND_CLASS,
                            c,
                            c.getInternalNameNode(),
                            c.getInternalName(),
                            index);
            for (stmt child : c.getInternalBody()) {
                collect(child, true, index, sym.children);
            }
            out.add(sym);
        } else if (s instanceof Assign) {
            for (expr target : ((Assign) s).getInternalTargets()) {
                if (target instanceof Name) {
                    String id = ((Name) target).getInternalId();
                    if (id == null) {
                        continue;
                    }
                    int kind =
                            isConstantName(id)
                                    ? DocumentSymbol.KIND_CONSTANT
                                    : DocumentSymbol.KIND_VARIABLE;
                    out.add(make(id, kind, target, target, null, index));
                }
            }
        }
    }

    private DocumentSymbol make(
            String name,
            int kind,
            PythonTree node,
            PythonTree nameNode,
            String detail,
            LineIndex index) {
        Range range = rangeOf(node, index);
        Range selection = nameNode != null ? rangeOf(nameNode, index) : range;
        // selectionRange must be contained in range.
        DocumentSymbol sym = new DocumentSymbol(name, kind, range, selection);
        sym.detail = detail;
        return sym;
    }

    private Range rangeOf(PythonTree node, LineIndex index) {
        int start = node.getCharStartIndex();
        int stop = node.getCharStopIndex();
        if (start >= 0 && stop >= start) {
            Position s = index.positionAt(start);
            Position e = index.positionAt(stop);
            return new Range(s, e);
        }
        // Fallback to lineno/col_offset (lineno is 1-based).
        int line = Math.max(0, node.getLineno() - 1);
        int col = Math.max(0, node.getCol_offset());
        return Range.of(line, col, line, col + 1);
    }

    private boolean isConstantName(String id) {
        return id.equals(id.toUpperCase()) && id.matches("[A-Z_][A-Z0-9_]*");
    }
}
