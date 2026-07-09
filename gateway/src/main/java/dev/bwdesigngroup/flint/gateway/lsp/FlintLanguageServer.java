package dev.bwdesigngroup.flint.gateway.lsp;

import dev.bwdesigngroup.flint.common.protocol.methods.lsp.CompletionItem;
import dev.bwdesigngroup.flint.common.protocol.methods.lsp.DocumentSymbol;
import dev.bwdesigngroup.flint.common.protocol.methods.lsp.Hover;
import dev.bwdesigngroup.flint.common.protocol.methods.lsp.Location;
import dev.bwdesigngroup.flint.common.protocol.methods.lsp.LspDiagnostic;
import dev.bwdesigngroup.flint.common.protocol.methods.lsp.Position;
import dev.bwdesigngroup.flint.common.protocol.methods.lsp.Range;
import dev.bwdesigngroup.flint.common.protocol.methods.lsp.WorkspaceSymbol;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Headless language-server core for Ignition Jython. Holds open document text (synced from editors
 * via the thin proxy) and derives LSP features from the Jython AST. Documents are keyed by {@code
 * sessionId::uri} so one gateway can serve multiple concurrent editors/agents.
 *
 * <p>Phase 1 provides document sync + syntax diagnostics; later phases add symbols, hover,
 * definition, completion, and cross-file project intelligence over the same document/parse state.
 */
public class FlintLanguageServer {

    private static final Logger logger = LoggerFactory.getLogger("flint.Gateway.Lsp");

    private final JythonParseService parseService = new JythonParseService();
    private final DocumentSymbolService symbolService = new DocumentSymbolService();
    private final CompletionEngine completionEngine;
    private final ProjectIndex projectIndex;

    public FlintLanguageServer() {
        this(null, null);
    }

    public FlintLanguageServer(HintsSource hints) {
        this(hints, null);
    }

    public FlintLanguageServer(HintsSource hints, ProjectIndex projectIndex) {
        this.completionEngine = new CompletionEngine(hints);
        this.projectIndex = projectIndex;
    }

    private String resolveProject(String project) {
        if (project != null && !project.isEmpty()) {
            return project;
        }
        if (projectIndex != null) {
            List<String> names = projectIndex.projectNames();
            if (names.size() == 1) {
                return names.get(0);
            }
        }
        return project;
    }

    /** docKey -> current text. */
    private final Map<String, String> documents = new ConcurrentHashMap<>();
    /** docKey -> cached parse of current text. */
    private final Map<String, JythonParseService.ParseResult> parseCache =
            new ConcurrentHashMap<>();

    private static String docKey(String sessionId, String uri) {
        return (sessionId == null ? "" : sessionId) + "::" + uri;
    }

    public void didOpen(String sessionId, String uri, String text) {
        put(sessionId, uri, text != null ? text : "");
    }

    public void didChange(String sessionId, String uri, String text) {
        put(sessionId, uri, text != null ? text : "");
    }

    public void didClose(String sessionId, String uri) {
        String key = docKey(sessionId, uri);
        documents.remove(key);
        parseCache.remove(key);
    }

    private void put(String sessionId, String uri, String text) {
        String key = docKey(sessionId, uri);
        documents.put(key, text);
        parseCache.put(key, parseService.parse(uri, text));
        logger.debug("Synced document {} ({} chars)", uri, text.length());
    }

    /** Current text of an open document, or null. */
    public String getText(String sessionId, String uri) {
        return documents.get(docKey(sessionId, uri));
    }

    /**
     * Returns the parse for an open document. If the document isn't open, parses the provided text
     * on demand (agents may query a file without a full didOpen).
     */
    public JythonParseService.ParseResult getParse(
            String sessionId, String uri, String textIfAbsent) {
        String key = docKey(sessionId, uri);
        JythonParseService.ParseResult cached = parseCache.get(key);
        if (cached != null) {
            return cached;
        }
        return parseService.parse(uri, textIfAbsent != null ? textIfAbsent : "");
    }

    /** Syntax diagnostics for an open document (empty if unknown). */
    public List<LspDiagnostic> diagnostics(String sessionId, String uri) {
        JythonParseService.ParseResult parse = parseCache.get(docKey(sessionId, uri));
        return parse != null ? parse.diagnostics : Collections.emptyList();
    }

    /** Diagnostics for arbitrary text (agent/one-shot use), without opening the document. */
    public List<LspDiagnostic> diagnosticsFor(String uri, String text) {
        return parseService.parse(uri, text).diagnostics;
    }

    /** Document symbol (outline) tree for an open document, or for one-shot text. */
    public List<DocumentSymbol> documentSymbols(String sessionId, String uri, String textIfAbsent) {
        String text = resolveText(sessionId, uri, textIfAbsent);
        JythonParseService.ParseResult parse = getParse(sessionId, uri, text);
        return symbolService.symbols(parse.ast, text);
    }

    /** Hover markdown at a position (intra-file), or null. */
    public Hover hover(String sessionId, String uri, String textIfAbsent, Position pos) {
        SymbolResolver resolver = resolverFor(sessionId, uri, textIfAbsent);
        String md = resolver.hoverMarkdownAt(pos);
        return md == null ? null : new Hover(md, null);
    }

    /** Go-to-definition: intra-file first, then cross-file into project scripts. */
    public Location definition(
            String sessionId,
            String uri,
            String textIfAbsent,
            Position pos,
            String project,
            String rootUri) {
        String text = resolveText(sessionId, uri, textIfAbsent);
        JythonParseService.ParseResult parse = getParse(sessionId, uri, text);
        Location intra = new SymbolResolver(parse.ast, text).definitionAt(uri, pos);
        if (intra != null) {
            return intra;
        }
        return crossFileDefinition(text, pos, resolveProject(project), rootUri);
    }

    private Location crossFileDefinition(
            String text, Position pos, String project, String rootUri) {
        if (projectIndex == null || project == null) {
            return null;
        }
        String qualified = qualifiedAround(text, pos);
        if (qualified == null || !qualified.contains(".")) {
            return null;
        }
        // Whole thing is a module?
        ProjectIndex.Module direct = projectIndex.module(project, qualified);
        if (direct != null) {
            return new Location(
                    LspUris.scriptUri(rootUri, project, direct.resourcePath), Range.of(0, 0, 0, 0));
        }
        // module.symbol
        int lastDot = qualified.lastIndexOf('.');
        String modulePath = qualified.substring(0, lastDot);
        String symbol = qualified.substring(lastDot + 1);
        ProjectIndex.Module module = projectIndex.module(project, modulePath);
        if (module == null) {
            return null;
        }
        DocumentSymbol match = findSymbol(module.symbols, symbol);
        String targetUri = LspUris.scriptUri(rootUri, project, module.resourcePath);
        Range range = match != null ? match.selectionRange : Range.of(0, 0, 0, 0);
        return new Location(targetUri, range);
    }

    /** Workspace symbol search across all project scripts. */
    public List<WorkspaceSymbol> workspaceSymbols(String project, String query, String rootUri) {
        List<WorkspaceSymbol> out = new ArrayList<>();
        if (projectIndex == null) {
            return out;
        }
        String resolved = resolveProject(project);
        if (resolved == null) {
            return out;
        }
        String q = query == null ? "" : query.toLowerCase();
        for (ProjectIndex.Module module : projectIndex.modules(resolved).values()) {
            String uri = LspUris.scriptUri(rootUri, resolved, module.resourcePath);
            collectWorkspaceSymbols(module.symbols, module.modulePath, uri, q, out);
        }
        return out;
    }

    private void collectWorkspaceSymbols(
            List<DocumentSymbol> symbols,
            String container,
            String uri,
            String query,
            List<WorkspaceSymbol> out) {
        if (symbols == null) {
            return;
        }
        for (DocumentSymbol s : symbols) {
            if (query.isEmpty() || (s.name != null && s.name.toLowerCase().contains(query))) {
                out.add(
                        new WorkspaceSymbol(
                                s.name, s.kind, container, new Location(uri, s.selectionRange)));
            }
            collectWorkspaceSymbols(s.children, container + "." + s.name, uri, query, out);
        }
    }

    private DocumentSymbol findSymbol(List<DocumentSymbol> symbols, String name) {
        if (symbols == null) {
            return null;
        }
        for (DocumentSymbol s : symbols) {
            if (name.equals(s.name)) {
                return s;
            }
        }
        return null;
    }

    /** Extracts the contiguous dotted identifier run containing the cursor. */
    private String qualifiedAround(String text, Position pos) {
        LineIndex index = new LineIndex(text);
        int offset = index.offsetAt(pos.line, pos.character);
        int start = offset;
        while (start > 0) {
            char c = text.charAt(start - 1);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '.') {
                start--;
            } else {
                break;
            }
        }
        int end = offset;
        while (end < text.length()) {
            char c = text.charAt(end);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '.') {
                end++;
            } else {
                break;
            }
        }
        String run = text.substring(start, end);
        return run.isEmpty() ? null : run;
    }

    /** References to the symbol at a position (intra-file). */
    public List<Location> references(
            String sessionId, String uri, String textIfAbsent, Position pos, boolean includeDecl) {
        return resolverFor(sessionId, uri, textIfAbsent).referencesAt(uri, pos, includeDecl);
    }

    /** Position-aware completion items (scope + system.* + project modules). */
    public List<CompletionItem> completion(
            String sessionId, String uri, String textIfAbsent, Position pos, String project) {
        String text = resolveText(sessionId, uri, textIfAbsent);
        JythonParseService.ParseResult parse = getParse(sessionId, uri, text);
        return completionEngine.complete(
                parse.ast, text, pos, projectIndex, resolveProject(project));
    }

    /** Invalidate the cross-file index for a project (e.g. after resources change). */
    public void invalidateProjectIndex(String project) {
        if (projectIndex != null) {
            projectIndex.invalidate(resolveProject(project));
        }
    }

    private SymbolResolver resolverFor(String sessionId, String uri, String textIfAbsent) {
        String text = resolveText(sessionId, uri, textIfAbsent);
        JythonParseService.ParseResult parse = getParse(sessionId, uri, text);
        return new SymbolResolver(parse.ast, text);
    }

    private String resolveText(String sessionId, String uri, String textIfAbsent) {
        String text = getText(sessionId, uri);
        return text != null ? text : (textIfAbsent != null ? textIfAbsent : "");
    }
}
