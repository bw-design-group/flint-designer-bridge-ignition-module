package dev.bwdesigngroup.flint.designer.registry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.bwdesigngroup.flint.common.FlintConstants;
import dev.bwdesigngroup.flint.common.registry.DesignerRegistryInfo;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the Designer registry for VS Code discovery. Uses a separate lock file (.lock) for
 * liveness detection and a data file (.json) for readable connection info. This separation allows
 * external tools such as the VS Code extension to read the data file even on WSL where exclusive
 * file locks block all reads.
 */
public class DesignerRegistryManager {
    private static final Logger logger = LoggerFactory.getLogger("flint.Designer.Registry");
    private static final String HEX_CHARS = "0123456789abcdef";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final Gson gson;
    private final DesignerRegistryInfo registryInfo;
    private final Path registryFilePath;
    private final Path lockFilePath;
    private final long pid;

    private RandomAccessFile lockFile;
    private FileChannel lockChannel;
    private FileLock fileLock;
    private String secret;

    public DesignerRegistryManager() {
        this.pid = ProcessHandle.current().pid();
        this.registryFilePath = FlintConstants.getRegistryFilePath(pid);
        this.lockFilePath = FlintConstants.getLockFilePath(pid);
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.registryInfo = new DesignerRegistryInfo();
        this.registryInfo.setPid(pid);
        this.secret = generateSecret();
        this.registryInfo.setSecret(secret);
    }

    /**
     * Initializes the registry file with an exclusive lock.
     *
     * @return true if successful, false if lock could not be acquired
     */
    public boolean initialize() {
        try {
            // Ensure the registry directory exists
            Path registryDir = registryFilePath.getParent();
            if (!Files.exists(registryDir)) {
                Files.createDirectories(registryDir);
                logger.info("Created registry directory: {}", registryDir);
            }

            // Clean up any stale registry files from force-quit Designers
            cleanupStaleRegistryFiles(registryDir);

            // Create the lock file and acquire exclusive lock for liveness detection
            lockFile = new RandomAccessFile(lockFilePath.toFile(), "rw");
            lockChannel = lockFile.getChannel();
            fileLock = lockChannel.tryLock();

            if (fileLock == null) {
                logger.error("Could not acquire lock on lock file: {}", lockFilePath);
                cleanup();
                return false;
            }

            // Set file permissions to owner-only (0600 on Unix)
            setFilePermissions(lockFilePath);
            setFilePermissions(registryFilePath);

            logger.info("Lock file created: {}, registry file: {}", lockFilePath, registryFilePath);
            return true;

        } catch (IOException e) {
            logger.error("Failed to initialize registry", e);
            cleanup();
            return false;
        }
    }

    /**
     * Cleans up stale registry files from Designers that were force-quit. A file is considered
     * stale if we can acquire a lock on it (meaning the original process is no longer holding the
     * lock).
     */
    private void cleanupStaleRegistryFiles(Path registryDir) {
        try {
            Files.list(registryDir)
                    .filter(path -> path.getFileName().toString().startsWith("designer-"))
                    .filter(path -> path.getFileName().toString().endsWith(".lock"))
                    .forEach(this::tryCleanupStaleFile);
        } catch (IOException e) {
            logger.debug("Could not list registry directory for cleanup: {}", e.getMessage());
        }
    }

    /**
     * Attempts to clean up a potentially stale lock file and its corresponding data file. If we can
     * acquire the lock, the Designer is dead and both files should be deleted.
     */
    private void tryCleanupStaleFile(Path lockPath) {
        // Don't try to clean up our own file
        if (lockPath.equals(lockFilePath)) {
            return;
        }

        try (RandomAccessFile raf = new RandomAccessFile(lockPath.toFile(), "rw");
                FileChannel channel = raf.getChannel()) {

            // Try to acquire the lock - if successful, the original owner is gone
            FileLock lock = channel.tryLock();
            if (lock != null) {
                // We got the lock, meaning the original Designer is no longer running
                lock.release();
                Files.delete(lockPath);
                logger.info("Cleaned up stale lock file: {}", lockPath.getFileName());

                // Also delete the corresponding data file
                String lockName = lockPath.getFileName().toString();
                String dataName = lockName.replace(".lock", ".json");
                Path dataPath = lockPath.getParent().resolve(dataName);
                if (Files.exists(dataPath)) {
                    Files.delete(dataPath);
                    logger.info("Cleaned up stale data file: {}", dataName);
                }
            }
            // If lock is null, another Designer still holds it - leave it alone

        } catch (IOException e) {
            // File might be in use or have permission issues - skip it
            logger.debug(
                    "Could not check/cleanup lock file {}: {}",
                    lockPath.getFileName(),
                    e.getMessage());
        }
    }

    /** Updates the registry file with current information. */
    public void updateRegistry() {
        if (lockChannel == null || !lockChannel.isOpen()) {
            logger.warn("Cannot update registry - lock not held");
            return;
        }

        try {
            String json = gson.toJson(registryInfo);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

            // Write to a temp file and atomically rename for safe reads by external tools
            Path tempFile =
                    registryFilePath.getParent().resolve(registryFilePath.getFileName() + ".tmp");
            Files.write(tempFile, bytes);
            Files.move(
                    tempFile,
                    registryFilePath,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);

            logger.debug("Registry file updated");
        } catch (IOException e) {
            logger.error("Failed to update registry file", e);
        }
    }

    /** Releases the lock and deletes the registry file. */
    public void shutdown() {
        cleanup();
        try {
            if (Files.exists(lockFilePath)) {
                Files.delete(lockFilePath);
                logger.info("Lock file deleted: {}", lockFilePath);
            }
        } catch (IOException e) {
            logger.error("Failed to delete lock file", e);
        }
        try {
            if (Files.exists(registryFilePath)) {
                Files.delete(registryFilePath);
                logger.info("Registry file deleted: {}", registryFilePath);
            }
        } catch (IOException e) {
            logger.error("Failed to delete registry file", e);
        }
    }

    private void cleanup() {
        try {
            if (fileLock != null && fileLock.isValid()) {
                fileLock.release();
                fileLock = null;
            }
        } catch (IOException e) {
            logger.error("Error releasing file lock", e);
        }

        try {
            if (lockChannel != null && lockChannel.isOpen()) {
                lockChannel.close();
                lockChannel = null;
            }
        } catch (IOException e) {
            logger.error("Error closing lock channel", e);
        }

        try {
            if (lockFile != null) {
                lockFile.close();
                lockFile = null;
            }
        } catch (IOException e) {
            logger.error("Error closing lock file", e);
        }
    }

    private void setFilePermissions(Path path) {
        try {
            if (!Files.exists(path)) {
                return;
            }
            // Only works on Unix-like systems
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
            Files.setPosixFilePermissions(path, perms);
        } catch (UnsupportedOperationException e) {
            // Windows - permissions work differently, file is user-specific by default
            logger.debug("POSIX permissions not supported (Windows)");
        } catch (IOException e) {
            logger.warn("Could not set file permissions on {}", path, e);
        }
    }

    private String generateSecret() {
        StringBuilder sb = new StringBuilder(FlintConstants.SECRET_LENGTH);
        for (int i = 0; i < FlintConstants.SECRET_LENGTH; i++) {
            sb.append(HEX_CHARS.charAt(SECURE_RANDOM.nextInt(HEX_CHARS.length())));
        }
        return sb.toString();
    }

    // Setters for registry info
    public void setPort(int port) {
        registryInfo.setPort(port);
    }

    public void setGatewayInfo(String host, int port, boolean ssl, String name) {
        registryInfo.setGateway(new DesignerRegistryInfo.GatewayInfo(host, port, ssl, name));
    }

    public void setProjectInfo(String name, String title) {
        registryInfo.setProject(new DesignerRegistryInfo.ProjectInfo(name, title));
    }

    public void setUserInfo(String username) {
        registryInfo.setUser(new DesignerRegistryInfo.UserInfo(username));
    }

    public void setDesignerVersion(String version) {
        registryInfo.setDesignerVersion(version);
    }

    public void setModuleVersion(String version) {
        registryInfo.setModuleVersion(version);
    }

    public void setCapabilities(boolean scriptExecution, boolean gatewayScope) {
        registryInfo.setCapabilities(
                new DesignerRegistryInfo.Capabilities(scriptExecution, gatewayScope));
    }

    public void setCapabilities(boolean scriptExecution, boolean gatewayScope, int cdpPort) {
        registryInfo.setCapabilities(
                new DesignerRegistryInfo.Capabilities(scriptExecution, gatewayScope, cdpPort));
    }

    // Getters
    public String getSecret() {
        return secret;
    }

    public Path getRegistryFilePath() {
        return registryFilePath;
    }

    public long getPid() {
        return pid;
    }
}
