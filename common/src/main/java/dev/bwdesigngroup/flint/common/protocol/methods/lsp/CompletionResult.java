package dev.bwdesigngroup.flint.common.protocol.methods.lsp;

import java.util.ArrayList;
import java.util.List;

/** Result of the lsp.completion method. Contains a list of completion items. */
public class CompletionResult {
    /** Whether the result is incomplete and should be refreshed on further typing */
    private boolean isIncomplete;

    /** The list of completion items */
    private List<CompletionItem> items;

    public CompletionResult() {
        this.items = new ArrayList<>();
        this.isIncomplete = false;
    }

    public CompletionResult(List<CompletionItem> items) {
        this.items = items != null ? items : new ArrayList<>();
        this.isIncomplete = false;
    }

    public boolean isIncomplete() {
        return isIncomplete;
    }

    public void setIncomplete(boolean incomplete) {
        isIncomplete = incomplete;
    }

    public List<CompletionItem> getItems() {
        return items;
    }

    public void setItems(List<CompletionItem> items) {
        this.items = items;
    }

    public void addItem(CompletionItem item) {
        if (this.items == null) {
            this.items = new ArrayList<>();
        }
        this.items.add(item);
    }
}
