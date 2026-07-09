package dev.bwdesigngroup.flint.designer.handlers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcRequest;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcResponse;
import dev.bwdesigngroup.flint.common.protocol.methods.browser.BrowserCdpInfoResult;
import dev.bwdesigngroup.flint.common.protocol.methods.browser.CdpTarget;
import dev.bwdesigngroup.flint.designer.server.FlintWebSocketHandler;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for the browser.getCdpInfo method. Discovers CDP targets from JxBrowser's remote
 * debugging endpoint.
 */
public class BrowserCdpInfoHandler implements MethodHandler {
    private static final Logger logger = LoggerFactory.getLogger("flint.Designer.BrowserCdp");
    private static final int CDP_DEFAULT_PORT = 9222;
    private static final int HTTP_TIMEOUT_MS = 2000;

    private final FlintWebSocketHandler handler;
    private final Gson gson;

    public BrowserCdpInfoHandler(FlintWebSocketHandler handler) {
        this.handler = handler;
        this.gson = handler.getGson();
    }

    @Override
    public JsonRpcResponse handle(JsonRpcRequest request) {
        Object id = request.getId();

        try {
            int port = discoverCdpPort();
            if (port <= 0) {
                return JsonRpcResponse.success(BrowserCdpInfoResult.unavailable(), id);
            }

            List<CdpTarget> targets = fetchTargets(port);
            return JsonRpcResponse.success(BrowserCdpInfoResult.available(port, targets), id);
        } catch (Exception e) {
            logger.warn("Error getting CDP info: {}", e.getMessage());
            return JsonRpcResponse.success(BrowserCdpInfoResult.unavailable(), id);
        }
    }

    /**
     * Discovers the CDP port by first trying the JxBrowser Engine API via reflection, then falling
     * back to probing the default port.
     */
    private int discoverCdpPort() {
        // First, try to get the port from the Engine API via reflection
        int port = discoverPortFromEngine();
        if (port > 0) {
            return port;
        }

        // Fall back to probing the default CDP port
        if (probeCdpPort(CDP_DEFAULT_PORT)) {
            return CDP_DEFAULT_PORT;
        }

        return 0;
    }

    /**
     * Attempts to discover the CDP port from JxBrowser's Engine via reflection. Uses:
     * DesignerContext -> PerspectiveDesignerInterface -> workspace -> engine -> browsers ->
     * browser.devTools().remoteDebuggingUrl()
     */
    private int discoverPortFromEngine() {
        try {
            DesignerContext context = handler.getContext();

            // Try to access the Perspective workspace's JxBrowser Engine
            // This uses reflection because Perspective classes are not guaranteed to be available
            Object perspectiveModule = findPerspectiveModule(context);
            if (perspectiveModule == null) {
                logger.debug("Perspective module not found");
                return 0;
            }

            // Get the workspace from the Perspective designer interface
            Method getWorkspaceMethod = findMethod(perspectiveModule.getClass(), "getWorkspace");
            if (getWorkspaceMethod == null) {
                logger.debug("getWorkspace method not found on Perspective module");
                return 0;
            }

            Object workspace = getWorkspaceMethod.invoke(perspectiveModule);
            if (workspace == null) {
                logger.debug("Perspective workspace is null");
                return 0;
            }

            // Get the Engine from the workspace
            Method getEngineMethod = findMethod(workspace.getClass(), "getEngine");
            if (getEngineMethod == null) {
                logger.debug("getEngine method not found on workspace");
                return 0;
            }

            Object engine = getEngineMethod.invoke(workspace);
            if (engine == null) {
                logger.debug("JxBrowser Engine is null");
                return 0;
            }

            // Get browsers from the engine
            Method browsersMethod = findMethod(engine.getClass(), "browsers");
            if (browsersMethod == null) {
                logger.debug("browsers method not found on engine");
                return 0;
            }

            Object browsersList = browsersMethod.invoke(engine);
            if (browsersList instanceof List && !((List<?>) browsersList).isEmpty()) {
                Object firstBrowser = ((List<?>) browsersList).get(0);

                // Get devTools from browser
                Method devToolsMethod = findMethod(firstBrowser.getClass(), "devTools");
                if (devToolsMethod != null) {
                    Object devTools = devToolsMethod.invoke(firstBrowser);
                    if (devTools != null) {
                        Method remoteDebuggingUrlMethod =
                                findMethod(devTools.getClass(), "remoteDebuggingUrl");
                        if (remoteDebuggingUrlMethod != null) {
                            Object urlStr = remoteDebuggingUrlMethod.invoke(devTools);
                            if (urlStr != null) {
                                return parsePortFromUrl(urlStr.toString());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Could not discover CDP port from Engine API: {}", e.getMessage());
        }

        return 0;
    }

    /** Finds the Perspective designer module via reflection. */
    private Object findPerspectiveModule(DesignerContext context) {
        try {
            // Try to find PerspectiveDesignerInterface through the module manager
            Class<?> perspectiveClass =
                    Class.forName(
                            "com.inductiveautomation.perspective.designer.PerspectiveDesignerInterface");

            // Use the DesignerContext's getModule method if available
            Method getModuleMethod = findMethod(context.getClass(), "getModule", Class.class);
            if (getModuleMethod != null) {
                return getModuleMethod.invoke(context, perspectiveClass);
            }
        } catch (ClassNotFoundException e) {
            logger.debug("Perspective designer classes not available");
        } catch (Exception e) {
            logger.debug("Could not access Perspective module: {}", e.getMessage());
        }
        return null;
    }

    /** Probes whether a CDP endpoint is accessible on the given port. */
    private boolean probeCdpPort(int port) {
        try {
            URL url = new URL("http://localhost:" + port + "/json/version");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(HTTP_TIMEOUT_MS);
            conn.setReadTimeout(HTTP_TIMEOUT_MS);
            try {
                return conn.getResponseCode() == 200;
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            return false;
        }
    }

    /** Fetches CDP targets from the /json/list endpoint and classifies them. */
    private List<CdpTarget> fetchTargets(int port) {
        List<CdpTarget> targets = new ArrayList<>();

        try {
            URL url = new URL("http://localhost:" + port + "/json/list");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(HTTP_TIMEOUT_MS);
            conn.setReadTimeout(HTTP_TIMEOUT_MS);

            try {
                if (conn.getResponseCode() != 200) {
                    return targets;
                }

                StringBuilder response = new StringBuilder();
                try (BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }

                JsonArray jsonTargets = gson.fromJson(response.toString(), JsonArray.class);
                if (jsonTargets == null) {
                    return targets;
                }

                for (JsonElement element : jsonTargets) {
                    JsonObject obj = element.getAsJsonObject();
                    String targetType = getJsonString(obj, "type");

                    // Only include "page" type targets
                    if (!"page".equals(targetType)) {
                        continue;
                    }

                    String targetUrl = getJsonString(obj, "url");

                    // Skip devtools pages
                    if (targetUrl != null && targetUrl.startsWith("devtools://")) {
                        continue;
                    }

                    String classifiedType = classifyTarget(targetUrl);
                    String viewPath = extractViewPath(targetUrl);

                    CdpTarget target =
                            new CdpTarget(
                                    getJsonString(obj, "id"),
                                    getJsonString(obj, "title"),
                                    targetUrl,
                                    getJsonString(obj, "webSocketDebuggerUrl"),
                                    classifiedType,
                                    viewPath);

                    targets.add(target);
                }
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            logger.warn("Error fetching CDP targets: {}", e.getMessage());
        }

        return targets;
    }

    /** Classifies a CDP target by its URL pattern. */
    private String classifyTarget(String url) {
        if (url == null) {
            return "other";
        }
        if (url.contains("/data/perspective/design")) {
            return "perspective-design";
        }
        if (url.contains("/data/perspective/client")) {
            return "perspective-preview";
        }
        if (url.startsWith("devtools://")) {
            return "devtools";
        }
        return "other";
    }

    /**
     * Extracts the view path from a Perspective URL. e.g., ".../data/perspective/design/MyView" ->
     * "MyView"
     */
    private String extractViewPath(String url) {
        if (url == null) {
            return null;
        }

        String[] markers = {"/data/perspective/design/", "/data/perspective/client/"};
        for (String marker : markers) {
            int idx = url.indexOf(marker);
            if (idx >= 0) {
                String path = url.substring(idx + marker.length());
                // Remove query string if present
                int queryIdx = path.indexOf('?');
                if (queryIdx >= 0) {
                    path = path.substring(0, queryIdx);
                }
                // Remove fragment if present
                int fragIdx = path.indexOf('#');
                if (fragIdx >= 0) {
                    path = path.substring(0, fragIdx);
                }
                return path.isEmpty() ? null : path;
            }
        }

        return null;
    }

    /** Parses the port number from a URL string. */
    private int parsePortFromUrl(String urlStr) {
        try {
            URL url = new URL(urlStr);
            return url.getPort();
        } catch (Exception e) {
            // Try regex as fallback
            try {
                java.util.regex.Matcher m =
                        java.util.regex.Pattern.compile(":(\\d+)").matcher(urlStr);
                if (m.find()) {
                    return Integer.parseInt(m.group(1));
                }
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    /** Finds a method by name on a class, searching the class hierarchy. */
    private Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                Method method = current.getMethod(name, paramTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException e) {
                // Try declared methods
                try {
                    Method method = current.getDeclaredMethod(name, paramTypes);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException ignored) {
                }
            }
            current = current.getSuperclass();
        }

        // Also check interfaces
        for (Class<?> iface : clazz.getInterfaces()) {
            try {
                Method method = iface.getMethod(name, paramTypes);
                return method;
            } catch (NoSuchMethodException ignored) {
            }
        }

        return null;
    }

    private String getJsonString(JsonObject obj, String key) {
        JsonElement element = obj.get(key);
        return element != null && !element.isJsonNull() ? element.getAsString() : null;
    }
}
