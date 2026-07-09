package dev.bwdesigngroup.flint.common.view;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.bwdesigngroup.flint.common.protocol.methods.view.ViewTreeResult.ComponentTreeNode;

/**
 * Pure JSON tree operations over Perspective view configs. Shared by the Designer WebSocket bridge
 * ({@code ViewResourceHelper}) and the Gateway HTTP transport ({@code GatewayViewService}) so the
 * view-editing logic lives in exactly one place. Contains no SDK or Swing dependencies.
 */
public final class ViewJsonUtil {

    private ViewJsonUtil() {
        // Prevent instantiation
    }

    /** Navigates to a component within the view JSON by path (e.g. "root/Container/Label"). */
    public static JsonObject navigateToComponent(JsonObject viewJson, String componentPath) {
        if (componentPath == null || componentPath.isEmpty()) {
            return null;
        }

        String[] segments = componentPath.split("/");
        if (segments.length == 0 || !"root".equals(segments[0])) {
            return null;
        }

        JsonObject current = viewJson.getAsJsonObject("root");
        if (current == null) {
            return null;
        }

        for (int i = 1; i < segments.length; i++) {
            current = findChild(current, segments[i]);
            if (current == null) {
                return null;
            }
        }

        return current;
    }

    /**
     * Replaces a component at the given path within the view JSON, returning a modified deep copy
     * (or null if the path could not be resolved).
     */
    public static JsonObject replaceComponent(
            JsonObject viewJson, String componentPath, JsonObject newComponent) {
        if (componentPath == null || componentPath.isEmpty()) {
            return null;
        }

        String[] segments = componentPath.split("/");
        if (segments.length < 1 || !"root".equals(segments[0])) {
            return null;
        }

        if (segments.length == 1) {
            JsonObject modified = viewJson.deepCopy();
            modified.add("root", newComponent);
            return modified;
        }

        JsonObject modified = viewJson.deepCopy();
        JsonObject parent = modified.getAsJsonObject("root");

        for (int i = 1; i < segments.length - 1; i++) {
            parent = findChild(parent, segments[i]);
            if (parent == null) {
                return null;
            }
        }

        String targetSegment = segments[segments.length - 1];
        JsonArray children = parent.has("children") ? parent.getAsJsonArray("children") : null;
        if (children == null) {
            return null;
        }

        for (int j = 0; j < children.size(); j++) {
            JsonObject child = children.get(j).getAsJsonObject();
            if (child.has("meta") && child.getAsJsonObject("meta").has("name")) {
                String name = child.getAsJsonObject("meta").get("name").getAsString();
                if (targetSegment.equals(name)) {
                    children.set(j, newComponent);
                    return modified;
                }
            }
        }

        try {
            int index = Integer.parseInt(targetSegment);
            if (index >= 0 && index < children.size()) {
                children.set(index, newComponent);
                return modified;
            }
        } catch (NumberFormatException e) {
            // Not a valid index
        }

        return null;
    }

    /** Builds a component tree summary from view JSON. */
    public static ComponentTreeNode buildComponentTree(JsonObject viewJson) {
        JsonObject root = viewJson.getAsJsonObject("root");
        if (root == null) {
            return null;
        }
        int[] counter = {0};
        return buildTreeNode(root, "root", counter);
    }

    /** Counts total components in the tree. */
    public static int countComponents(JsonObject viewJson) {
        JsonObject root = viewJson.getAsJsonObject("root");
        if (root == null) {
            return 0;
        }
        return countComponentsRecursive(root);
    }

    // --- Private helpers ---

    private static ComponentTreeNode buildTreeNode(
            JsonObject component, String path, int[] counter) {
        counter[0]++;

        String type = component.has("type") ? component.get("type").getAsString() : "unknown";
        String name = "";
        if (component.has("meta") && component.getAsJsonObject("meta").has("name")) {
            name = component.getAsJsonObject("meta").get("name").getAsString();
        }

        JsonArray children =
                component.has("children") ? component.getAsJsonArray("children") : null;
        int childCount = children != null ? children.size() : 0;

        boolean hasEvents =
                component.has("events")
                        && component.getAsJsonObject("events").entrySet().size() > 0;

        ComponentTreeNode node = new ComponentTreeNode(path, type, name, childCount, hasEvents);

        if (children != null) {
            for (int i = 0; i < children.size(); i++) {
                JsonObject child = children.get(i).getAsJsonObject();
                String childName = "";
                if (child.has("meta") && child.getAsJsonObject("meta").has("name")) {
                    childName = child.getAsJsonObject("meta").get("name").getAsString();
                }
                String childPath =
                        path + "/" + (childName.isEmpty() ? String.valueOf(i) : childName);
                node.addChild(buildTreeNode(child, childPath, counter));
            }
        }

        return node;
    }

    private static int countComponentsRecursive(JsonObject component) {
        int count = 1;
        if (component.has("children")) {
            JsonArray children = component.getAsJsonArray("children");
            for (int i = 0; i < children.size(); i++) {
                count += countComponentsRecursive(children.get(i).getAsJsonObject());
            }
        }
        return count;
    }

    private static JsonObject findChild(JsonObject parent, String segment) {
        JsonArray children = parent.has("children") ? parent.getAsJsonArray("children") : null;
        if (children == null) {
            return null;
        }

        for (int j = 0; j < children.size(); j++) {
            JsonObject child = children.get(j).getAsJsonObject();
            if (child.has("meta") && child.getAsJsonObject("meta").has("name")) {
                String name = child.getAsJsonObject("meta").get("name").getAsString();
                if (segment.equals(name)) {
                    return child;
                }
            }
        }

        try {
            int index = Integer.parseInt(segment);
            if (index >= 0 && index < children.size()) {
                return children.get(index).getAsJsonObject();
            }
        } catch (NumberFormatException e) {
            // Not a valid index
        }

        return null;
    }
}
