package dev.bwdesigngroup.flint.designer.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import dev.bwdesigngroup.flint.common.FlintConstants;
import dev.bwdesigngroup.flint.common.protocol.ErrorCodes;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcRequest;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcResponse;
import dev.bwdesigngroup.flint.designer.server.FlintWebSocketHandler;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for icon.list and icon.search JSON-RPC methods. Discovers icon libraries from
 * Perspective's DesignerHook and fetches icon names from the gateway's icon endpoint.
 */
public class IconRegistryHandler implements MethodHandler {
    private static final Logger logger = LoggerFactory.getLogger("flint.Designer.IconRegistry");

    private final FlintWebSocketHandler handler;
    private final DesignerContext context;

    // Cached icon data: library name -> sorted set of icon names
    private Map<String, Set<String>> iconLibraries;
    private boolean iconsLoaded = false;

    public IconRegistryHandler(FlintWebSocketHandler handler) {
        this.handler = handler;
        this.context = handler.getContext();
    }

    @Override
    public JsonRpcResponse handle(JsonRpcRequest request) {
        String method = request.getMethod();
        Object id = request.getId();

        switch (method) {
            case FlintConstants.METHOD_ICON_LIST:
                return handleIconList(request);
            case FlintConstants.METHOD_ICON_SEARCH:
                return handleIconSearch(request);
            default:
                return JsonRpcResponse.error(
                        ErrorCodes.METHOD_NOT_FOUND, "Unknown icon method: " + method, id);
        }
    }

    /** icon.list - Lists all available icon libraries and their icons. */
    private JsonRpcResponse handleIconList(JsonRpcRequest request) {
        Object id = request.getId();
        JsonObject params = getParams(request);
        String libraryFilter = getStringParam(params, "library");

        Map<String, Set<String>> libraries = loadIconLibraries();
        if (libraries == null || libraries.isEmpty()) {
            return JsonRpcResponse.error(
                    ErrorCodes.INTERNAL_ERROR,
                    "Could not access icon registry. Perspective may not be loaded.",
                    id);
        }

        Map<String, Object> result = new HashMap<>();
        int totalCount = 0;

        if (libraryFilter != null) {
            Set<String> icons = libraries.get(libraryFilter);
            if (icons == null) {
                return JsonRpcResponse.error(
                        ErrorCodes.INVALID_PARAMS,
                        "Icon library not found: "
                                + libraryFilter
                                + ". Available libraries: "
                                + libraries.keySet(),
                        id);
            }
            Map<String, Object> libraryInfo = new HashMap<>();
            libraryInfo.put("icons", icons);
            libraryInfo.put("count", icons.size());
            Map<String, Object> librariesResult = new HashMap<>();
            librariesResult.put(libraryFilter, libraryInfo);
            result.put("libraries", librariesResult);
            totalCount = icons.size();
        } else {
            Map<String, Object> librariesResult = new HashMap<>();
            for (Map.Entry<String, Set<String>> entry : libraries.entrySet()) {
                Map<String, Object> libraryInfo = new HashMap<>();
                libraryInfo.put("icons", entry.getValue());
                libraryInfo.put("count", entry.getValue().size());
                librariesResult.put(entry.getKey(), libraryInfo);
                totalCount += entry.getValue().size();
            }
            result.put("libraries", librariesResult);
        }

        result.put("totalCount", totalCount);
        return JsonRpcResponse.success(result, id);
    }

    /** icon.search - Search icons by keyword. */
    private JsonRpcResponse handleIconSearch(JsonRpcRequest request) {
        Object id = request.getId();
        JsonObject params = getParams(request);

        String query = getStringParam(params, "query");
        if (query == null || query.isEmpty()) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Missing required parameter: query", id);
        }

        String libraryFilter = getStringParam(params, "library");
        String queryLower = query.toLowerCase();

        Map<String, Set<String>> libraries = loadIconLibraries();
        if (libraries == null || libraries.isEmpty()) {
            return JsonRpcResponse.error(
                    ErrorCodes.INTERNAL_ERROR,
                    "Could not access icon registry. Perspective may not be loaded.",
                    id);
        }

        List<String> matches = new ArrayList<>();

        for (Map.Entry<String, Set<String>> entry : libraries.entrySet()) {
            String library = entry.getKey();
            if (libraryFilter != null && !library.equals(libraryFilter)) {
                continue;
            }

            for (String iconName : entry.getValue()) {
                if (iconName.toLowerCase().contains(queryLower)) {
                    matches.add(library + "/" + iconName);
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("query", query);
        result.put("matches", matches);
        result.put("count", matches.size());
        return JsonRpcResponse.success(result, id);
    }

    // --- Icon discovery ---

    private Map<String, Set<String>> loadIconLibraries() {
        if (iconsLoaded) {
            return iconLibraries;
        }
        iconsLoaded = true;
        iconLibraries = new HashMap<>();

        try {
            // Step 1: Get library names from Perspective DesignerHook
            Set<String> libraryNames = getLibraryNamesFromHook();
            if (libraryNames == null || libraryNames.isEmpty()) {
                logger.warn("No icon libraries found from Perspective module");
                return iconLibraries;
            }
            logger.info("Found {} icon libraries: {}", libraryNames.size(), libraryNames);

            // Step 2: For each library, fetch icon names from the gateway
            String gatewayUrl = getGatewayBaseUrl();
            if (gatewayUrl != null) {
                for (String library : libraryNames) {
                    Set<String> icons = fetchIconsFromGateway(gatewayUrl, library);
                    if (icons != null && !icons.isEmpty()) {
                        iconLibraries.put(library, icons);
                        logger.debug("Loaded {} icons for library '{}'", icons.size(), library);
                    } else {
                        // Register the library with empty set so we at least know it exists
                        iconLibraries.put(library, new TreeSet<>());
                    }
                }
            }

            int total = iconLibraries.values().stream().mapToInt(Set::size).sum();
            logger.info("Discovered {} icons across {} libraries", total, iconLibraries.size());

        } catch (Exception e) {
            logger.error("Failed to discover icon libraries: {}", e.getMessage());
        }

        return iconLibraries;
    }

    /** Gets icon library names from the Perspective DesignerHook's getIconLibraries() method. */
    @SuppressWarnings("unchecked")
    private Set<String> getLibraryNamesFromHook() {
        try {
            Method getModule = context.getClass().getMethod("getModule", String.class);
            Object perspectiveHook =
                    getModule.invoke(context, "com.inductiveautomation.perspective");

            if (perspectiveHook == null) {
                logger.debug("Perspective module not found");
                return null;
            }

            Method getIconLibraries = perspectiveHook.getClass().getMethod("getIconLibraries");
            Object result = getIconLibraries.invoke(perspectiveHook);

            if (result instanceof Collection) {
                Set<String> names = new TreeSet<>();
                for (Object item : (Collection<?>) result) {
                    if (item instanceof String) {
                        names.add((String) item);
                    }
                }
                return names;
            }
        } catch (Exception e) {
            logger.debug("Failed to get icon libraries from hook: {}", e.getMessage());
        }
        return null;
    }

    /** Gets the gateway base URL via context.getLaunchContext().getGatewayAddress(). */
    private String getGatewayBaseUrl() {
        try {
            Method getLaunchContext = context.getClass().getMethod("getLaunchContext");
            Object launchContext = getLaunchContext.invoke(context);
            if (launchContext != null) {
                Method getGatewayAddress = launchContext.getClass().getMethod("getGatewayAddress");
                Object address = getGatewayAddress.invoke(launchContext);
                if (address != null) {
                    return address.toString();
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to get gateway URL: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Fetches icon names for a library from the gateway's SVG sprite endpoint. Perspective serves
     * icon SVG sprites at /data/perspective/icons/{library}.svg. Each icon is a {@code <g
     * class="icon" id="icon_name">} element in the SVG.
     */
    private Set<String> fetchIconsFromGateway(String gatewayUrl, String library) {
        String baseUrl = gatewayUrl;
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        String fullUrl = baseUrl + "/data/perspective/icons/" + library + ".svg";

        try {
            String content = httpGet(fullUrl);
            if (content != null && !content.isEmpty()) {
                return parseIconNamesFromSvg(content);
            }
        } catch (Exception e) {
            logger.debug("Failed to fetch icon SVG from {}: {}", fullUrl, e.getMessage());
        }

        return null;
    }

    /**
     * Parses icon names from an SVG sprite document. Material icons use {@code <g class="icon"
     * id="name">} elements. Symbol libraries use nested {@code <svg id="name">} elements.
     */
    private Set<String> parseIconNamesFromSvg(String svgContent) {
        Set<String> icons = new TreeSet<>();
        // Match <g ... id="name"> (material icons)
        Pattern gPattern = Pattern.compile("<g\\s[^>]*\\bid=\"([^\"]+)\"[^>]*>");
        Matcher gMatcher = gPattern.matcher(svgContent);
        while (gMatcher.find()) {
            icons.add(gMatcher.group(1));
        }
        // Match nested <svg ... id="name"> (symbol libraries)
        // Skip the root <svg> by requiring whitespace before <svg (indented)
        Pattern svgPattern = Pattern.compile("\\s<svg\\s[^>]*\\bid=\"([^\"]+)\"[^>]*>");
        Matcher svgMatcher = svgPattern.matcher(svgContent);
        while (svgMatcher.find()) {
            icons.add(svgMatcher.group(1));
        }
        return icons;
    }

    /** Simple HTTP GET with SSL trust for self-signed certs (common in dev). */
    private String httpGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn;

        if (urlStr.startsWith("https")) {
            // Trust all certs for local dev gateways
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(
                    null,
                    new TrustManager[] {
                        new X509TrustManager() {
                            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                                return null;
                            }

                            public void checkClientTrusted(
                                    java.security.cert.X509Certificate[] certs, String authType) {}

                            public void checkServerTrusted(
                                    java.security.cert.X509Certificate[] certs, String authType) {}
                        }
                    },
                    new java.security.SecureRandom());

            HttpsURLConnection httpsConn = (HttpsURLConnection) url.openConnection();
            httpsConn.setSSLSocketFactory(sslContext.getSocketFactory());
            httpsConn.setHostnameVerifier((hostname, session) -> true);
            conn = httpsConn;
        } else {
            conn = (HttpURLConnection) url.openConnection();
        }

        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        int status = conn.getResponseCode();
        if (status != 200) {
            return null;
        }

        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
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
