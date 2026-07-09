package dev.bwdesigngroup.flint.gateway.view;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.bwdesigngroup.flint.common.protocol.methods.view.ViewValidationResult;
import java.util.Collections;
import java.util.List;

/**
 * Structural validation of a Perspective view config for the headless gateway. Verifies the JSON
 * shape (root present, components carry a string {@code type}, children are arrays) without
 * requiring a live Perspective component registry. Registry-backed component-type validation is a
 * later-phase enhancement (see component.getSchema).
 */
public class GatewayViewValidator {

    /** Validates a view config object, returning collected errors/warnings. */
    public ViewValidationResult validate(JsonObject viewJson) {
        ViewValidationResult result = new ViewValidationResult();

        if (viewJson == null) {
            result.addError("", "NULL_CONFIG", "View config is null");
            return result;
        }

        if (!viewJson.has("root") || !viewJson.get("root").isJsonObject()) {
            result.addError("root", "MISSING_ROOT", "View must have a 'root' component object");
            return result;
        }

        validateComponent(viewJson.getAsJsonObject("root"), "root", result);
        return result;
    }

    private void validateComponent(JsonObject component, String path, ViewValidationResult result) {
        if (!component.has("type") || !component.get("type").isJsonPrimitive()) {
            result.addError(path, "MISSING_TYPE", "Component is missing a string 'type'");
        }

        if (component.has("children")) {
            JsonElement childrenEl = component.get("children");
            if (!childrenEl.isJsonArray()) {
                result.addError(path, "INVALID_CHILDREN", "'children' must be an array");
            } else {
                JsonArray children = childrenEl.getAsJsonArray();
                for (int i = 0; i < children.size(); i++) {
                    if (!children.get(i).isJsonObject()) {
                        result.addError(
                                path + "/" + i,
                                "INVALID_CHILD",
                                "Each child must be a component object");
                        continue;
                    }
                    JsonObject child = children.get(i).getAsJsonObject();
                    String childName = i + "";
                    if (child.has("meta") && child.getAsJsonObject("meta").has("name")) {
                        childName = child.getAsJsonObject("meta").get("name").getAsString();
                    }
                    validateComponent(child, path + "/" + childName, result);
                }
            }
        }
    }

    /** Registered component types (empty on the headless gateway until registry wiring). */
    public List<String> getRegisteredComponentTypes() {
        return Collections.emptyList();
    }
}
