package dev.bwdesigngroup.flint.common.protocol.methods.lsp;

import java.io.Serializable;

/**
 * A completion item representing a suggestion for code completion. Based on LSP CompletionItem
 * structure.
 */
public class CompletionItem implements Serializable {
    private static final long serialVersionUID = 1L;
    /** The label to display in the completion list */
    private String label;

    /**
     * The kind of completion (LSP CompletionItemKind): 1=Text, 2=Method, 3=Function, 4=Constructor,
     * 5=Field, 6=Variable, 7=Class, 8=Interface, 9=Module, 10=Property, 11=Unit, 12=Value, 13=Enum,
     * 14=Keyword, 15=Snippet
     */
    private int kind;

    /** Short detail/signature (e.g., "(tagPaths, timeout) -> List") */
    private String detail;

    /** Full documentation (markdown supported) */
    private String documentation;

    /** Text to insert when completing (supports snippets) */
    private String insertText;

    /** Insert text format: 1=PlainText, 2=Snippet (with $1, $2 placeholders) */
    private int insertTextFormat;

    /** Sort text for ordering in the list */
    private String sortText;

    /** Filter text for matching user input */
    private String filterText;

    /** The full path of the item (e.g., "system.tag.readBlocking") */
    private String path;

    /** Whether this item is deprecated */
    private boolean deprecated;

    public CompletionItem() {
        this.insertTextFormat = 1; // Default to plain text
    }

    public CompletionItem(String label, int kind) {
        this();
        this.label = label;
        this.kind = kind;
    }

    // Getters and setters

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public int getKind() {
        return kind;
    }

    public void setKind(int kind) {
        this.kind = kind;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getDocumentation() {
        return documentation;
    }

    public void setDocumentation(String documentation) {
        this.documentation = documentation;
    }

    public String getInsertText() {
        return insertText;
    }

    public void setInsertText(String insertText) {
        this.insertText = insertText;
    }

    public int getInsertTextFormat() {
        return insertTextFormat;
    }

    public void setInsertTextFormat(int insertTextFormat) {
        this.insertTextFormat = insertTextFormat;
    }

    public String getSortText() {
        return sortText;
    }

    public void setSortText(String sortText) {
        this.sortText = sortText;
    }

    public String getFilterText() {
        return filterText;
    }

    public void setFilterText(String filterText) {
        this.filterText = filterText;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public boolean isDeprecated() {
        return deprecated;
    }

    public void setDeprecated(boolean deprecated) {
        this.deprecated = deprecated;
    }
}
