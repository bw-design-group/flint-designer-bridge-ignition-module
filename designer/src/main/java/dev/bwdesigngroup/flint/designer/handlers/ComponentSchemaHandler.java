package dev.bwdesigngroup.flint.designer.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import dev.bwdesigngroup.flint.common.FlintConstants;
import dev.bwdesigngroup.flint.common.protocol.ErrorCodes;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcRequest;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcResponse;
import dev.bwdesigngroup.flint.designer.server.FlintWebSocketHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for component.list and component.getSchema JSON-RPC methods. Uses reflection to access
 * Perspective's component registry.
 */
public class ComponentSchemaHandler implements MethodHandler {
    private static final Logger logger = LoggerFactory.getLogger("flint.Designer.ComponentSchema");

    private final FlintWebSocketHandler handler;
    private final DesignerContext context;

    // Cached registry reference
    private Object componentRegistry;
    private boolean registryLoaded = false;

    public ComponentSchemaHandler(FlintWebSocketHandler handler) {
        this.handler = handler;
        this.context = handler.getContext();
    }

    @Override
    public JsonRpcResponse handle(JsonRpcRequest request) {
        String method = request.getMethod();
        Object id = request.getId();

        switch (method) {
            case FlintConstants.METHOD_COMPONENT_LIST:
                return handleComponentList(request);
            case FlintConstants.METHOD_COMPONENT_GET_SCHEMA:
                return handleGetSchema(request);
            default:
                return JsonRpcResponse.error(
                        ErrorCodes.METHOD_NOT_FOUND, "Unknown component method: " + method, id);
        }
    }

    /** component.list - Lists all registered Perspective component types. */
    private JsonRpcResponse handleComponentList(JsonRpcRequest request) {
        Object id = request.getId();

        Object registry = getComponentRegistry();
        if (registry == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.INTERNAL_ERROR,
                    "Could not access Perspective component registry",
                    id);
        }

        List<Map<String, Object>> components = new ArrayList<>();
        Collection<?> descriptors = getRegistryDescriptors(registry);

        if (descriptors != null) {
            for (Object descriptor : descriptors) {
                Map<String, Object> info = extractComponentInfo(descriptor);
                if (info != null) {
                    components.add(info);
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("components", components);
        result.put("count", components.size());
        return JsonRpcResponse.success(result, id);
    }

    /** component.getSchema - Gets the property schema for a specific component. */
    private JsonRpcResponse handleGetSchema(JsonRpcRequest request) {
        Object id = request.getId();
        JsonObject params = getParams(request);

        String componentId = getStringParam(params, "componentId");
        if (componentId == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Missing required parameter: componentId", id);
        }

        Object registry = getComponentRegistry();
        if (registry == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.INTERNAL_ERROR,
                    "Could not access Perspective component registry",
                    id);
        }

        // Find the specific descriptor
        Object descriptor = findDescriptor(registry, componentId);
        if (descriptor == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Component not found: " + componentId, id);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("componentId", componentId);

        // Extract display name
        String name = extractName(descriptor);
        if (name != null) {
            result.put("name", name);
        }

        // Extract schema
        Object schema = extractSchema(descriptor);
        if (schema != null) {
            result.put("schema", schema);
        } else {
            result.put("schema", null);
            result.put("message", "No property schema available for this component");
        }

        // Extract event descriptors
        Object events = extractEvents(descriptor);
        if (events != null) {
            result.put("events", events);
        }

        return JsonRpcResponse.success(result, id);
    }

    // --- Registry access via reflection ---

    private Object getComponentRegistry() {
        if (registryLoaded) {
            return componentRegistry;
        }
        registryLoaded = true;

        try {
            // Get Perspective DesignerHook via context.getModule(String moduleId)
            Method getModule = context.getClass().getMethod("getModule", String.class);
            Object perspectiveHook =
                    getModule.invoke(context, "com.inductiveautomation.perspective");

            if (perspectiveHook == null) {
                logger.warn("Perspective module not found");
                return null;
            }

            // Call getDesignerComponentRegistry() on the DesignerHook
            Method getRegistry =
                    perspectiveHook.getClass().getMethod("getDesignerComponentRegistry");
            Object registry = getRegistry.invoke(perspectiveHook);

            if (registry != null) {
                componentRegistry = registry;
                logger.info("Got Perspective component registry");
                return componentRegistry;
            }
        } catch (Exception e) {
            logger.error("Failed to access component registry: {}", e.getMessage());
        }

        logger.warn("Could not access Perspective component registry");
        return null;
    }

    /**
     * Gets the map of component descriptors from the registry. The registry's get() method returns
     * a ConcurrentHashMap&lt;String, ComponentDescriptorImpl&gt;.
     */
    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> getRegistryMap(Object registry) {
        try {
            Method getMethod = registry.getClass().getMethod("get");
            Object result = getMethod.invoke(registry);
            if (result instanceof java.util.Map) {
                return (java.util.Map<String, Object>) result;
            }
        } catch (Exception e) {
            logger.debug("Error calling registry.get(): {}", e.getMessage());
        }
        return null;
    }

    private Collection<?> getRegistryDescriptors(Object registry) {
        java.util.Map<String, Object> map = getRegistryMap(registry);
        if (map != null) {
            return map.values();
        }
        return null;
    }

    private Object findDescriptor(Object registry, String componentId) {
        // Use the map for direct lookup by key
        java.util.Map<String, Object> map = getRegistryMap(registry);
        if (map != null) {
            Object descriptor = map.get(componentId);
            if (descriptor != null) {
                return descriptor;
            }
        }

        // Fallback: try find(String) method on the registry
        try {
            Method findMethod = registry.getClass().getMethod("find", String.class);
            Object descriptor = findMethod.invoke(registry, componentId);
            if (descriptor != null) {
                return descriptor;
            }
        } catch (Exception e) {
            // Not available
        }

        return null;
    }

    // --- Descriptor field extraction ---

    private Map<String, Object> extractComponentInfo(Object descriptor) {
        String typeId = extractTypeId(descriptor);
        if (typeId == null) {
            return null;
        }

        Map<String, Object> info = new HashMap<>();
        info.put("id", typeId);

        String name = extractName(descriptor);
        if (name != null) {
            info.put("name", name);
        }

        // Check if schema is available
        info.put("schema", extractSchema(descriptor) != null);

        return info;
    }

    private String extractTypeId(Object descriptor) {
        for (String methodName :
                new String[] {"getId", "getTypeId", "getComponentId", "getType", "id"}) {
            try {
                Method getter = descriptor.getClass().getMethod(methodName);
                Object typeId = getter.invoke(descriptor);
                if (typeId instanceof String) {
                    return (String) typeId;
                }
                if (typeId != null) {
                    String typeStr = typeId.toString();
                    if (typeStr.contains(".") && !typeStr.contains("@")) {
                        return typeStr;
                    }
                }
            } catch (Exception e) {
                // Try next
            }
        }
        return null;
    }

    private String extractName(Object descriptor) {
        for (String methodName :
                new String[] {"getName", "getDisplayName", "name", "displayName"}) {
            try {
                Method getter = descriptor.getClass().getMethod(methodName);
                Object name = getter.invoke(descriptor);
                if (name instanceof String) {
                    return (String) name;
                }
            } catch (Exception e) {
                // Try next
            }
        }
        return null;
    }

    /**
     * Attempts to extract the property schema from a component descriptor. Perspective components
     * store their property definitions as JSON schemas.
     */
    private Object extractSchema(Object descriptor) {
        // Try schema() first (Perspective's actual method), then fallbacks
        for (String methodName :
                new String[] {
                    "schema",
                    "getSchema",
                    "getPropsSchema",
                    "getPropertySchema",
                    "getPropertiesSchema",
                    "getPropertyTreeSchema"
                }) {
            try {
                Method getter = descriptor.getClass().getMethod(methodName);
                Object schema = getter.invoke(descriptor);
                if (schema != null) {
                    return convertToSerializable(schema);
                }
            } catch (NoSuchMethodException e) {
                // Try next
            } catch (Exception e) {
                logger.debug("Error calling {}: {}", methodName, e.getMessage());
            }
        }

        // Try getting the default props as a schema proxy
        for (String methodName :
                new String[] {"getDefaultProps", "getDefaultProperties", "getInitialProps"}) {
            try {
                Method getter = descriptor.getClass().getMethod(methodName);
                Object defaultProps = getter.invoke(descriptor);
                if (defaultProps != null) {
                    return convertToSerializable(defaultProps);
                }
            } catch (NoSuchMethodException e) {
                // Try next
            } catch (Exception e) {
                logger.debug("Error calling {}: {}", methodName, e.getMessage());
            }
        }

        return null;
    }

    /** Attempts to extract event descriptors from a component descriptor. */
    private Object extractEvents(Object descriptor) {
        for (String methodName :
                new String[] {"getEvents", "getEventDescriptors", "getEventDefinitions"}) {
            try {
                Method getter = descriptor.getClass().getMethod(methodName);
                Object events = getter.invoke(descriptor);
                if (events != null) {
                    return convertToSerializable(events);
                }
            } catch (NoSuchMethodException e) {
                // Try next
            } catch (Exception e) {
                logger.debug("Error calling {}: {}", methodName, e.getMessage());
            }
        }
        return null;
    }

    /**
     * Converts an object to a JSON-serializable form. Handles JsonElement, strings (that might be
     * JSON), and falls back to toString.
     */
    private Object convertToSerializable(Object obj) {
        if (obj == null) {
            return null;
        }

        // Already a Gson JsonElement
        if (obj instanceof JsonElement) {
            return obj;
        }

        // Try toString and check if it's JSON
        String str = obj.toString();
        if (str.startsWith("{") || str.startsWith("[")) {
            try {
                return JsonParser.parseString(str);
            } catch (Exception e) {
                // Not valid JSON
            }
        }

        // Try toJson() method
        try {
            Method toJson = obj.getClass().getMethod("toJson");
            Object jsonResult = toJson.invoke(obj);
            if (jsonResult instanceof String) {
                return JsonParser.parseString((String) jsonResult);
            }
            if (jsonResult instanceof JsonElement) {
                return jsonResult;
            }
        } catch (Exception e) {
            // No toJson method
        }

        // Return as string
        return str;
    }

    // --- Param helpers ---

    private JsonObject getParams(JsonRpcRequest request) {
        JsonElement paramsElement = request.getParams();
        if (paramsElement != null && paramsElement.isJsonObject()) {
            return paramsElement.getAsJsonObject();
        }
        return new JsonObject();
    }

    private String getStringParam(JsonObject params, String key) {
        if (params.has(key) && params.get(key).isJsonPrimitive()) {
            return params.get(key).getAsString();
        }
        return null;
    }
}
