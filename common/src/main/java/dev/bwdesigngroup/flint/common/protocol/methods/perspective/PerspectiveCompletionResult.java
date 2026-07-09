package dev.bwdesigngroup.flint.common.protocol.methods.perspective;

import dev.bwdesigngroup.flint.common.protocol.methods.lsp.CompletionItem;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of a Perspective component completion request. Contains completion items for component
 * properties (self.props.*, self.custom.*, etc.)
 */
public class PerspectiveCompletionResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private List<CompletionItem> items;
    private boolean isIncomplete;

    public PerspectiveCompletionResult() {
        this.items = new ArrayList<>();
        this.isIncomplete = false;
    }

    public PerspectiveCompletionResult(List<CompletionItem> items) {
        this.items = items != null ? items : new ArrayList<>();
        this.isIncomplete = false;
    }

    public List<CompletionItem> getItems() {
        return items;
    }

    public void setItems(List<CompletionItem> items) {
        this.items = items;
    }

    public void addItem(CompletionItem item) {
        this.items.add(item);
    }

    public boolean isIncomplete() {
        return isIncomplete;
    }

    public void setIncomplete(boolean incomplete) {
        isIncomplete = incomplete;
    }
}
