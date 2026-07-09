package dev.bwdesigngroup.flint.gateway.auth;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import dev.bwdesigngroup.flint.common.FlintConstants;
import dev.bwdesigngroup.flint.common.auth.AuthUtil;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the Flint-generated Gateway API token used to authenticate headless HTTP clients on
 * Ignition 8.1 (and as a portable fallback on 8.3). Resolution order:
 *
 * <ol>
 *   <li>System property / environment variable {@code flint.gateway.apiToken} (operator-supplied;
 *       never persisted)
 *   <li>Existing token file in the gateway data dir
 *   <li>Freshly generated token, persisted with 0600 permissions
 * </ol>
 */
public class FlintTokenStore {

    private static final Logger logger = LoggerFactory.getLogger("flint.Gateway.Auth");
    private static final Gson GSON = new Gson();
    private static final int TOKEN_LENGTH = 48;

    private final String token;

    public FlintTokenStore(GatewayContext context) {
        this.token = resolveToken(context);
    }

    /** The active token. Never null. */
    public String getToken() {
        return token;
    }

    private static String resolveToken(GatewayContext context) {
        // 1. Operator override via system property or environment variable.
        String override = System.getProperty(FlintConstants.GATEWAY_API_TOKEN_PROPERTY);
        if (override == null || override.isEmpty()) {
            override = System.getenv("FLINT_GATEWAY_API_TOKEN");
        }
        if (override != null && !override.isEmpty()) {
            logger.info("Using operator-supplied Flint gateway API token");
            return override;
        }

        Path tokenFile = tokenFilePath(context);

        // 2. Load an existing persisted token.
        try {
            if (tokenFile != null && Files.exists(tokenFile)) {
                String contents = new String(Files.readAllBytes(tokenFile), StandardCharsets.UTF_8);
                JsonObject json = GSON.fromJson(contents, JsonObject.class);
                if (json != null && json.has("token")) {
                    String existing = json.get("token").getAsString();
                    if (existing != null && !existing.isEmpty()) {
                        return existing;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Could not read existing Flint gateway token file, regenerating", e);
        }

        // 3. Generate a fresh token and persist it.
        String generated = AuthUtil.generateHexSecret(TOKEN_LENGTH);
        persist(tokenFile, generated);
        return generated;
    }

    private static Path tokenFilePath(GatewayContext context) {
        try {
            File dataDir = context.getSystemManager().getDataDir();
            return dataDir.toPath()
                    .resolve("modules")
                    .resolve("flint")
                    .resolve("gateway")
                    .resolve("api-token.json");
        } catch (Exception e) {
            logger.warn("Could not resolve gateway data dir for token storage", e);
            return null;
        }
    }

    private static void persist(Path tokenFile, String token) {
        if (tokenFile == null) {
            logger.warn("No token file path available; token will not survive a restart");
            return;
        }
        try {
            Files.createDirectories(tokenFile.getParent());
            JsonObject json = new JsonObject();
            json.addProperty("token", token);
            Files.write(tokenFile, GSON.toJson(json).getBytes(StandardCharsets.UTF_8));
            try {
                Files.setPosixFilePermissions(
                        tokenFile, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // Non-POSIX filesystem (e.g. Windows) — rely on OS default user-only perms.
            }
            logger.info("Generated and persisted Flint gateway API token at {}", tokenFile);
        } catch (Exception e) {
            logger.error("Failed to persist Flint gateway token file", e);
        }
    }
}
