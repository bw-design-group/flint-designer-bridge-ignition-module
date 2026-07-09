package dev.bwdesigngroup.flint.gateway.perspective;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.perspective.common.api.ComponentDescriptor;
import com.inductiveautomation.perspective.common.api.ComponentEventDescriptor;
import com.inductiveautomation.perspective.common.api.ComponentRegistry;
import com.inductiveautomation.perspective.gateway.api.IconManager;
import com.inductiveautomation.perspective.gateway.api.PerspectiveContext;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gateway-scope access to Perspective's component registry and icon libraries via {@link
 * PerspectiveContext} — the headless equivalent of the Designer's {@code
 * getDesignerComponentRegistry} / {@code getIconLibraries}. Returns null / empty when Perspective
 * isn't installed or running.
 *
 * <p>Perspective descriptor JSON uses Ignition's shaded gson; results are normalized to plain
 * {@code com.google.gson} elements (via {@code toString()} round-trip) so they serialize cleanly in
 * the JSON-RPC response.
 */
public class GatewayPerspectiveRegistry {

    private static final Logger logger =
            LoggerFactory.getLogger("flint.Gateway.Perspective.Registry");

    private final GatewayContext context;

    public GatewayPerspectiveRegistry(GatewayContext context) {
        this.context = context;
    }

    private PerspectiveContext perspective() {
        try {
            return PerspectiveContext.get(context);
        } catch (Throwable t) {
            // Perspective module not installed/loaded.
            return null;
        }
    }

    public boolean isAvailable() {
        return perspective() != null;
    }

    /** Lists all registered Perspective component types (id, name, category). */
    public Map<String, Object> listComponents() {
        PerspectiveContext pc = perspective();
        ComponentRegistry registry = pc.getComponentRegistry();

        List<Map<String, Object>> components = new ArrayList<>();
        for (ComponentDescriptor descriptor : registry.get().values()) {
            Map<String, Object> info = new TreeMap<>();
            info.put("id", descriptor.id());
            info.put("name", descriptor.name());
            info.put("category", descriptor.paletteCategory());
            info.put("deprecated", descriptor.deprecated());
            components.add(info);
        }
        components.sort(
                (a, b) -> String.valueOf(a.get("id")).compareTo(String.valueOf(b.get("id"))));

        Map<String, Object> result = new TreeMap<>();
        result.put("components", components);
        result.put("count", components.size());
        result.put("categories", new ArrayList<>(registry.getCategories()));
        return result;
    }

    /** Gets a single component's schema, default properties, and events. Null if not found. */
    public Map<String, Object> getComponentSchema(String componentId) {
        PerspectiveContext pc = perspective();
        ComponentRegistry registry = pc.getComponentRegistry();
        Optional<ComponentDescriptor> found = registry.find(componentId);
        if (!found.isPresent()) {
            return null;
        }
        ComponentDescriptor descriptor = found.get();

        Map<String, Object> result = new TreeMap<>();
        result.put("componentId", descriptor.id());
        result.put("name", descriptor.name());
        result.put("category", descriptor.paletteCategory());
        result.put("deprecated", descriptor.deprecated());
        result.put("defaultProperties", normalize(descriptor.defaultProperties()));
        result.put("schema", schemaToJson(descriptor.schema()));

        List<Map<String, Object>> events = new ArrayList<>();
        try {
            for (ComponentEventDescriptor event : descriptor.events()) {
                Map<String, Object> ev = new TreeMap<>();
                ev.put("name", event.getName());
                ev.put("description", event.getDescription());
                ev.put("schema", schemaToJson(event.getSchema()));
                events.add(ev);
            }
        } catch (Exception e) {
            logger.debug("Could not read events for {}: {}", componentId, e.getMessage());
        }
        result.put("events", events);
        return result;
    }

    /** Lists icon libraries and their icon names, optionally filtered to one library. */
    public Map<String, Object> listIcons(String libraryFilter) {
        IconManager iconManager = perspective().getIconManager();
        Map<String, List<String>> libraries = iconManager.getLibraries();

        List<Map<String, Object>> libs = new ArrayList<>();
        int total = 0;
        for (Map.Entry<String, List<String>> entry : new TreeMap<>(libraries).entrySet()) {
            if (libraryFilter != null && !libraryFilter.equals(entry.getKey())) {
                continue;
            }
            List<String> icons = resolveIconNames(iconManager, entry.getKey(), entry.getValue());
            Map<String, Object> lib = new TreeMap<>();
            lib.put("library", entry.getKey());
            lib.put("icons", icons);
            lib.put("count", icons.size());
            libs.add(lib);
            total += icons.size();
        }

        Map<String, Object> result = new TreeMap<>();
        result.put("libraries", libs);
        result.put("totalCount", total);
        return result;
    }

    /** Searches icon names across all libraries for a case-insensitive substring. */
    public Map<String, Object> searchIcons(String query) {
        IconManager iconManager = perspective().getIconManager();
        Map<String, List<String>> libraries = iconManager.getLibraries();
        String needle = query == null ? "" : query.toLowerCase();

        List<Map<String, Object>> matches = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : new TreeMap<>(libraries).entrySet()) {
            for (String icon : resolveIconNames(iconManager, entry.getKey(), entry.getValue())) {
                if (icon.toLowerCase().contains(needle)) {
                    Map<String, Object> match = new TreeMap<>();
                    match.put("library", entry.getKey());
                    match.put("name", icon);
                    match.put("path", entry.getKey() + "/" + icon);
                    matches.add(match);
                }
            }
        }

        Map<String, Object> result = new TreeMap<>();
        result.put("matches", matches);
        result.put("count", matches.size());
        return result;
    }

    private static final Pattern SVG_ICON_ID =
            Pattern.compile("<(?:g|symbol)\\b[^>]*\\bid=\"([^\"]+)\"");

    /**
     * Resolves icon names for a library. {@code getLibraries()} pre-populates names for custom
     * libraries but leaves built-ins (e.g. material) empty; for those, parse the SVG sprite from
     * {@code getLibrary(name)} for {@code <g|symbol id="...">} entries.
     */
    private List<String> resolveIconNames(
            IconManager iconManager, String library, List<String> known) {
        if (known != null && !known.isEmpty()) {
            return known;
        }
        try {
            Optional<String> svg = iconManager.getLibrary(library);
            if (svg.isPresent() && svg.get() != null) {
                TreeSet<String> names = new TreeSet<>();
                Matcher matcher = SVG_ICON_ID.matcher(svg.get());
                while (matcher.find()) {
                    names.add(matcher.group(1));
                }
                if (!names.isEmpty()) {
                    return new ArrayList<>(names);
                }
            }
        } catch (Exception e) {
            logger.debug("Could not parse icons for library {}: {}", library, e.getMessage());
        }
        return known != null ? known : new ArrayList<>();
    }

    /**
     * Serializes a Perspective {@code JsonSchema} to a plain com.google.gson element. The raw
     * schema node is exposed via the protected {@code getSchemaNode()} on BaseJsonValidator, so
     * reach it reflectively; fall back to {@code toString()}.
     */
    private JsonElement schemaToJson(Object jsonSchema) {
        if (jsonSchema == null) {
            return null;
        }
        Class<?> cls = jsonSchema.getClass();
        while (cls != null) {
            try {
                Method method = cls.getDeclaredMethod("getSchemaNode");
                method.setAccessible(true);
                Object node = method.invoke(jsonSchema);
                if (node != null) {
                    return normalize(node);
                }
                break;
            } catch (NoSuchMethodException e) {
                cls = cls.getSuperclass();
            } catch (Exception e) {
                logger.debug("Could not read schema node: {}", e.getMessage());
                break;
            }
        }
        return normalize(jsonSchema);
    }

    /**
     * Normalizes an Ignition shaded-gson element / JsonSchema to a plain com.google.gson element.
     */
    private JsonElement normalize(Object value) {
        if (value == null) {
            return null;
        }
        try {
            String json = value.toString();
            if (json != null && (json.startsWith("{") || json.startsWith("["))) {
                return JsonParser.parseString(json);
            }
        } catch (Exception e) {
            logger.debug("Could not normalize descriptor JSON: {}", e.getMessage());
        }
        return null;
    }
}
