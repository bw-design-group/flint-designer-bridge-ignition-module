package dev.bwdesigngroup.flint.gateway.lsp;

import dev.bwdesigngroup.flint.common.protocol.methods.lsp.CompletionItem;
import java.util.List;
import java.util.Set;

/**
 * Version-specific access to Ignition's {@code system.*} scripting hints (8.3 {@code getHintsTree}
 * vs 8.1 {@code getHintsMap}), surfaced as LSP completion items — the source of headless {@code
 * system.*} intelligence that replaces external PyPI stubs.
 */
public interface HintsSource {

    /** Top-level module names (e.g. {@code system}). */
    Set<String> rootNames();

    /** Completion items for the members of a dotted path (e.g. {@code system.tag}), filtered. */
    List<CompletionItem> members(String dottedPath, String partial);
}
