package dev.bwdesigngroup.flint.designer.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import dev.bwdesigngroup.flint.common.protocol.methods.view.ViewValidationResult;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Validates view JSON against Perspective's component registry. */
public class ViewValidator {
    private static final Logger logger = LoggerFactory.getLogger("flint.Designer.ViewValidator");

    private final DesignerContext context;
    private Set<String> registeredComponentTypes;

    /** Standard Perspective component types used as fallback if registry reflection fails. */
    private static final Set<String> STANDARD_COMPONENT_TYPES =
            new HashSet<>(
                    Arrays.asList(
                            // Containers
                            "ia.container.flex",
                            "ia.container.column",
                            "ia.container.coord",
                            "ia.container.tab",
                            "ia.container.breakpoint",
                            "ia.container.card",
                            "ia.container.split",
                            "ia.container.carousel",
                            // Display
                            "ia.display.label",
                            "ia.display.icon",
                            "ia.display.image",
                            "ia.display.markdown",
                            "ia.display.pdf-viewer",
                            "ia.display.video-player",
                            "ia.display.audio-player",
                            "ia.display.cylinder",
                            "ia.display.gauge",
                            "ia.display.linear-indicator",
                            "ia.display.led-display",
                            "ia.display.thermometer",
                            "ia.display.progress-bar",
                            // Input
                            "ia.input.button",
                            "ia.input.text-field",
                            "ia.input.text-area",
                            "ia.input.numeric-entry-field",
                            "ia.input.slider",
                            "ia.input.radio-group",
                            "ia.input.checkbox",
                            "ia.input.toggle-switch",
                            "ia.input.dropdown",
                            "ia.input.multi-state-button",
                            "ia.input.datetime-input",
                            "ia.input.file-upload",
                            "ia.input.color-picker",
                            // Table
                            "ia.display.table",
                            // Chart
                            "ia.chart.time-series",
                            "ia.chart.pie",
                            "ia.chart.bar",
                            "ia.chart.xy",
                            "ia.chart.power-chart",
                            "ia.chart.sparkline",
                            "ia.chart.status-chart",
                            // Misc
                            "ia.display.view",
                            "ia.display.link",
                            "ia.display.menu-tree",
                            "ia.map.vector",
                            "ia.display.alarm-journal",
                            "ia.display.alarm-status-table"));

    public ViewValidator(DesignerContext context) {
        this.context = context;
    }

    /**
     * Returns the set of registered component types, loading from Perspective's registry if not yet
     * cached.
     */
    public Set<String> getRegisteredComponentTypes() {
        if (registeredComponentTypes == null) {
            registeredComponentTypes = discoverComponentTypes();
        }
        return registeredComponentTypes;
    }

    /** Validates a view JSON config. */
    public ViewValidationResult validate(JsonObject viewJson) {
        ViewValidationResult result = new ViewValidationResult();

        if (!viewJson.has("root")) {
            result.addError("", "missing_root", "View must have a 'root' property");
            return result;
        }

        JsonElement rootElement = viewJson.get("root");
        if (!rootElement.isJsonObject()) {
            result.addError("root", "invalid_root", "'root' must be an object");
            return result;
        }

        validateComponent(rootElement.getAsJsonObject(), "root", result);
        return result;
    }

    // --- Private helpers ---

    private void validateComponent(JsonObject component, String path, ViewValidationResult result) {
        // Check type
        if (!component.has("type")) {
            result.addError(path, "missing_type", "Component must have a 'type' string");
        } else {
            JsonElement typeElement = component.get("type");
            if (!typeElement.isJsonPrimitive() || !typeElement.getAsJsonPrimitive().isString()) {
                result.addError(path, "invalid_type", "'type' must be a string");
            } else {
                String type = typeElement.getAsString();
                Set<String> knownTypes = getRegisteredComponentTypes();
                if (!knownTypes.contains(type)) {
                    result.addWarning(
                            path,
                            "unknown_component_type",
                            "Unknown component type: "
                                    + type
                                    + ". It may be from a third-party module.");
                }
            }
        }

        // Validate children
        if (component.has("children")) {
            JsonElement childrenElement = component.get("children");
            if (!childrenElement.isJsonArray()) {
                result.addError(path, "invalid_children", "'children' must be an array");
            } else {
                JsonArray children = childrenElement.getAsJsonArray();
                for (int i = 0; i < children.size(); i++) {
                    JsonElement childElement = children.get(i);
                    if (!childElement.isJsonObject()) {
                        result.addError(path + "/" + i, "invalid_child", "Child must be an object");
                    } else {
                        JsonObject child = childElement.getAsJsonObject();
                        String childName = "";
                        if (child.has("meta") && child.getAsJsonObject("meta").has("name")) {
                            childName = child.getAsJsonObject("meta").get("name").getAsString();
                        }
                        String childPath =
                                path + "/" + (childName.isEmpty() ? String.valueOf(i) : childName);
                        validateComponent(child, childPath, result);
                    }
                }
            }
        }

        // Validate position
        if (component.has("position")) {
            JsonElement posElement = component.get("position");
            if (!posElement.isJsonObject()) {
                result.addError(path, "invalid_position", "'position' must be an object");
            }
        }

        // Validate propConfig
        if (component.has("propConfig")) {
            JsonElement propConfigElement = component.get("propConfig");
            if (!propConfigElement.isJsonObject()) {
                result.addError(path, "invalid_propConfig", "'propConfig' must be an object");
            }
        }

        // Check for inline bindings in props (common authoring mistake)
        if (component.has("props") && component.get("props").isJsonObject()) {
            checkForInlineBindings(component.getAsJsonObject("props"), path, "props", result);
        }

        // Check for misplaced bidirectional key in propConfig bindings (common authoring mistake)
        if (component.has("propConfig") && component.get("propConfig").isJsonObject()) {
            checkBidirectionalPlacement(component.getAsJsonObject("propConfig"), path, result);
        }

        // Check for unindented event scripts (common authoring mistake)
        if (component.has("events") && component.get("events").isJsonObject()) {
            checkScriptIndentation(component.getAsJsonObject("events"), path, result);
        }
    }

    /**
     * Recursively checks props for inline binding objects that should be in propConfig. Detects the
     * pattern: {"binding": {"type": "...", "config": {...}}}
     */
    private void checkForInlineBindings(
            JsonObject propsObj,
            String componentPath,
            String propPath,
            ViewValidationResult result) {
        for (String key : propsObj.keySet()) {
            JsonElement value = propsObj.get(key);
            String fullPropPath = propPath + "." + key;

            if (value.isJsonObject()) {
                JsonObject valueObj = value.getAsJsonObject();
                if (valueObj.has("binding") && valueObj.get("binding").isJsonObject()) {
                    JsonObject binding = valueObj.getAsJsonObject("binding");
                    if (binding.has("type")) {
                        result.addError(
                                componentPath,
                                "inline_binding",
                                "Binding found inline at '"
                                        + fullPropPath
                                        + "'. "
                                        + "Bindings must go in 'propConfig' on the component, not inside 'props'. "
                                        + "Move to: \"propConfig\": {\""
                                        + fullPropPath
                                        + "\": {\"binding\": {...}}} "
                                        + "and set '"
                                        + fullPropPath
                                        + "' to a default value instead.");
                    }
                } else {
                    // Recurse into nested prop objects (e.g. props.style)
                    checkForInlineBindings(valueObj, componentPath, fullPropPath, result);
                }
            }
        }
    }

    /**
     * Checks propConfig entries for 'bidirectional' placed at the wrong level. It must be inside
     * 'config', not as a sibling of 'type' and 'config'.
     */
    private void checkBidirectionalPlacement(
            JsonObject propConfig, String componentPath, ViewValidationResult result) {
        for (String propKey : propConfig.keySet()) {
            JsonElement entry = propConfig.get(propKey);
            if (!entry.isJsonObject()) continue;

            JsonObject entryObj = entry.getAsJsonObject();
            if (!entryObj.has("binding") || !entryObj.get("binding").isJsonObject()) continue;

            JsonObject binding = entryObj.getAsJsonObject("binding");
            if (binding.has("bidirectional")) {
                result.addError(
                        componentPath,
                        "bidirectional_placement",
                        "Binding for '"
                                + propKey
                                + "' has 'bidirectional' at the wrong level. "
                                + "It must be INSIDE 'config', not as a sibling of 'type' and 'config'.\n"
                                + "WRONG: {\"binding\": {\"type\": \"property\", \"bidirectional\": true, \"config\": {\"path\": \"...\"}}}\n"
                                + "RIGHT: {\"binding\": {\"type\": \"property\", \"config\": {\"path\": \"...\", \"bidirectional\": true}}}");
            }
        }
    }

    /**
     * Checks event scripts for missing tab indentation. Perspective scripts require \t at the start
     * of every line.
     */
    private void checkScriptIndentation(
            JsonObject events, String componentPath, ViewValidationResult result) {
        for (String eventGroup : events.keySet()) {
            JsonElement groupElement = events.get(eventGroup);
            if (!groupElement.isJsonObject()) continue;

            JsonObject group = groupElement.getAsJsonObject();
            for (String eventName : group.keySet()) {
                JsonElement eventElement = group.get(eventName);
                if (!eventElement.isJsonObject()) continue;

                JsonObject event = eventElement.getAsJsonObject();
                if (!"script".equals(getStringField(event, "type"))) continue;

                if (event.has("config") && event.get("config").isJsonObject()) {
                    JsonObject config = event.getAsJsonObject("config");
                    if (config.has("script") && config.get("script").isJsonPrimitive()) {
                        String script = config.get("script").getAsString();
                        String[] lines = script.split("\n");
                        for (int i = 0; i < lines.length; i++) {
                            String line = lines[i];
                            if (!line.isEmpty() && !line.startsWith("\t")) {
                                result.addError(
                                        componentPath,
                                        "script_indentation",
                                        "Script in event '"
                                                + eventGroup
                                                + "."
                                                + eventName
                                                + "' "
                                                + "line "
                                                + (i + 1)
                                                + " is missing leading tab indentation. "
                                                + "Every line of a Perspective event script must start with \\t (tab character).");
                                break; // One error per script is enough
                            }
                        }
                    }
                }
            }
        }
    }

    private String getStringField(JsonObject obj, String key) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsString();
        }
        return null;
    }

    /**
     * Discovers component types from Perspective's live component registry via reflection. Falls
     * back to the hardcoded standard types if reflection fails.
     *
     * <p>Path: context.getModule("com.inductiveautomation.perspective") →
     * DesignerHook.getDesignerComponentRegistry() → DesignerComponentRegistry.get() → Map<String,
     * ComponentDescriptor>
     */
    @SuppressWarnings("unchecked")
    private Set<String> discoverComponentTypes() {
        Set<String> types = new HashSet<>(STANDARD_COMPONENT_TYPES);

        try {
            // getModule(String) returns the DesignerModuleHook for the given module ID
            Method getModule = context.getClass().getMethod("getModule", String.class);
            Object perspectiveHook =
                    getModule.invoke(context, "com.inductiveautomation.perspective");

            if (perspectiveHook == null) {
                logger.debug("Perspective module not found via getModule(String)");
                logger.info(
                        "Using {} standard component types (Perspective module not loaded)",
                        types.size());
                return types;
            }

            // DesignerHook.getDesignerComponentRegistry() → DesignerComponentRegistry
            Method getRegistry =
                    perspectiveHook.getClass().getMethod("getDesignerComponentRegistry");
            Object registry = getRegistry.invoke(perspectiveHook);

            if (registry == null) {
                logger.debug("getDesignerComponentRegistry() returned null");
                logger.info(
                        "Using {} standard component types (registry not available)", types.size());
                return types;
            }

            // DesignerComponentRegistry.get() → Map<String, ComponentDescriptor>
            Method getMap = registry.getClass().getMethod("get");
            Object result = getMap.invoke(registry);

            if (result instanceof Map) {
                Map<String, ?> componentMap = (Map<String, ?>) result;
                types.addAll(componentMap.keySet());
                logger.info(
                        "Discovered {} component types from Perspective registry", types.size());
                return types;
            }

            logger.debug(
                    "Registry.get() returned unexpected type: {}", result.getClass().getName());
        } catch (Exception e) {
            logger.debug("Failed to discover component types via reflection: {}", e.getMessage());
        }

        logger.info("Using {} standard component types (registry reflection failed)", types.size());
        return types;
    }
}
