package dev.bwdesigngroup.flint.common.protocol.methods.view;

import java.util.ArrayList;
import java.util.List;

/** Lightweight tree summary of a view's component hierarchy. */
public class ViewTreeResult {
    private String viewPath;
    private ComponentTreeNode root;
    private int totalComponents;

    public ViewTreeResult(String viewPath, ComponentTreeNode root, int totalComponents) {
        this.viewPath = viewPath;
        this.root = root;
        this.totalComponents = totalComponents;
    }

    public String getViewPath() {
        return viewPath;
    }

    public ComponentTreeNode getRoot() {
        return root;
    }

    public int getTotalComponents() {
        return totalComponents;
    }

    /** A node in the component tree summary. */
    public static class ComponentTreeNode {
        private final String path;
        private final String type;
        private final String name;
        private final int childCount;
        private final boolean hasEvents;
        private final List<ComponentTreeNode> children;

        public ComponentTreeNode(
                String path, String type, String name, int childCount, boolean hasEvents) {
            this.path = path;
            this.type = type;
            this.name = name;
            this.childCount = childCount;
            this.hasEvents = hasEvents;
            this.children = new ArrayList<>();
        }

        public void addChild(ComponentTreeNode child) {
            this.children.add(child);
        }

        public String getPath() {
            return path;
        }

        public String getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        public int getChildCount() {
            return childCount;
        }

        public boolean isHasEvents() {
            return hasEvents;
        }

        public List<ComponentTreeNode> getChildren() {
            return children;
        }
    }
}
