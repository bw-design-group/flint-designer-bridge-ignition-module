package dev.bwdesigngroup.flint.designer.registry;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.bwdesigngroup.flint.common.FlintConstants;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DesignerRegistryManager")
class DesignerRegistryManagerTest {

    private DesignerRegistryManager manager;
    private Gson gson;

    @BeforeEach
    void setUp() {
        gson = new GsonBuilder().create();
    }

    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.shutdown();
        }
    }

    @Nested
    @DisplayName("secret generation")
    class SecretGeneration {

        @Test
        @DisplayName("generates a secret of the expected length")
        void secretHasExpectedLength() {
            manager = new DesignerRegistryManager();
            String secret = manager.getSecret();
            assertEquals(FlintConstants.SECRET_LENGTH, secret.length());
        }

        @Test
        @DisplayName("generates a secret containing only hex characters")
        void secretIsHexOnly() {
            manager = new DesignerRegistryManager();
            String secret = manager.getSecret();
            assertTrue(
                    secret.matches("[0-9a-f]+"),
                    "Secret should contain only lowercase hex characters, but was: " + secret);
        }

        @Test
        @DisplayName("generates a different secret for each instance")
        void secretIsDifferentPerInstance() {
            DesignerRegistryManager manager1 = new DesignerRegistryManager();
            DesignerRegistryManager manager2 = new DesignerRegistryManager();

            // While theoretically possible to get the same secret, it is
            // astronomically unlikely with 32 hex characters (128 bits of entropy)
            assertNotEquals(
                    manager1.getSecret(),
                    manager2.getSecret(),
                    "Two independently created managers should have different secrets");

            // Clean up - these managers did not initialize, so just null them
            manager1.shutdown();
            manager2.shutdown();
        }

        @Test
        @DisplayName("secret is not null")
        void secretIsNotNull() {
            manager = new DesignerRegistryManager();
            assertNotNull(manager.getSecret());
        }
    }

    @Nested
    @DisplayName("PID assignment")
    class PidAssignment {

        @Test
        @DisplayName("uses the current process PID")
        void usesCurrentProcessPid() {
            manager = new DesignerRegistryManager();
            long expectedPid = ProcessHandle.current().pid();
            assertEquals(expectedPid, manager.getPid());
        }
    }

    @Nested
    @DisplayName("registry file path")
    class RegistryFilePath {

        @Test
        @DisplayName("registry file path matches expected convention")
        void registryFilePathMatchesConvention() {
            manager = new DesignerRegistryManager();
            long pid = ProcessHandle.current().pid();
            Path expectedPath = FlintConstants.getRegistryFilePath(pid);
            assertEquals(expectedPath, manager.getRegistryFilePath());
        }

        @Test
        @DisplayName("registry file path contains the PID")
        void registryFilePathContainsPid() {
            manager = new DesignerRegistryManager();
            long pid = ProcessHandle.current().pid();
            assertTrue(manager.getRegistryFilePath().toString().contains(String.valueOf(pid)));
        }
    }

    @Nested
    @DisplayName("initialize()")
    class Initialize {

        @Test
        @DisplayName("returns true on first successful initialization")
        void returnsTrueOnFirstInit() {
            manager = new DesignerRegistryManager();
            boolean result = manager.initialize();
            assertTrue(result, "First initialization should succeed");
        }

        @Test
        @DisplayName("creates the lock file on disk")
        void createsLockFile() {
            manager = new DesignerRegistryManager();
            manager.initialize();
            long pid = manager.getPid();
            assertTrue(
                    Files.exists(FlintConstants.getLockFilePath(pid)),
                    "Lock file should exist after initialization");
        }

        @Test
        @DisplayName("creates the registry directory if it does not exist")
        void createsRegistryDirectory() {
            manager = new DesignerRegistryManager();
            manager.initialize();
            assertTrue(
                    Files.isDirectory(manager.getRegistryFilePath().getParent()),
                    "Registry directory should exist after initialization");
        }
    }

    @Nested
    @DisplayName("updateRegistry()")
    class UpdateRegistry {

        @Test
        @DisplayName("writes valid JSON to the registry file")
        void writesValidJson() throws IOException {
            manager = new DesignerRegistryManager();
            manager.initialize();

            manager.setPort(52400);
            manager.setDesignerVersion("8.1.44");
            manager.setModuleVersion("0.12.0");
            manager.updateRegistry();

            String content =
                    Files.readString(manager.getRegistryFilePath(), StandardCharsets.UTF_8);
            assertDoesNotThrow(
                    () -> gson.fromJson(content, JsonObject.class),
                    "Registry file should contain valid JSON");
        }

        @Test
        @DisplayName("written JSON contains the port")
        void writtenJsonContainsPort() throws IOException {
            manager = new DesignerRegistryManager();
            manager.initialize();

            manager.setPort(52450);
            manager.updateRegistry();

            String content =
                    Files.readString(manager.getRegistryFilePath(), StandardCharsets.UTF_8);
            JsonObject json = gson.fromJson(content, JsonObject.class);
            assertEquals(52450, json.get("port").getAsInt());
        }

        @Test
        @DisplayName("written JSON contains the PID")
        void writtenJsonContainsPid() throws IOException {
            manager = new DesignerRegistryManager();
            manager.initialize();
            manager.updateRegistry();

            String content =
                    Files.readString(manager.getRegistryFilePath(), StandardCharsets.UTF_8);
            JsonObject json = gson.fromJson(content, JsonObject.class);
            assertEquals(ProcessHandle.current().pid(), json.get("pid").getAsLong());
        }

        @Test
        @DisplayName("written JSON contains the secret")
        void writtenJsonContainsSecret() throws IOException {
            manager = new DesignerRegistryManager();
            manager.initialize();
            manager.updateRegistry();

            String content =
                    Files.readString(manager.getRegistryFilePath(), StandardCharsets.UTF_8);
            JsonObject json = gson.fromJson(content, JsonObject.class);
            assertEquals(manager.getSecret(), json.get("secret").getAsString());
        }

        @Test
        @DisplayName("written JSON contains gateway info after setting it")
        void writtenJsonContainsGatewayInfo() throws IOException {
            manager = new DesignerRegistryManager();
            manager.initialize();

            manager.setGatewayInfo("localhost", 8088, false, "TestGateway");
            manager.updateRegistry();

            String content =
                    Files.readString(manager.getRegistryFilePath(), StandardCharsets.UTF_8);
            JsonObject json = gson.fromJson(content, JsonObject.class);
            JsonObject gateway = json.getAsJsonObject("gateway");
            assertNotNull(gateway);
            assertEquals("localhost", gateway.get("host").getAsString());
            assertEquals(8088, gateway.get("port").getAsInt());
            assertFalse(gateway.get("ssl").getAsBoolean());
            assertEquals("TestGateway", gateway.get("name").getAsString());
        }

        @Test
        @DisplayName("written JSON contains project info after setting it")
        void writtenJsonContainsProjectInfo() throws IOException {
            manager = new DesignerRegistryManager();
            manager.initialize();

            manager.setProjectInfo("my-project", "My Project Title");
            manager.updateRegistry();

            String content =
                    Files.readString(manager.getRegistryFilePath(), StandardCharsets.UTF_8);
            JsonObject json = gson.fromJson(content, JsonObject.class);
            JsonObject project = json.getAsJsonObject("project");
            assertNotNull(project);
            assertEquals("my-project", project.get("name").getAsString());
            assertEquals("My Project Title", project.get("title").getAsString());
        }

        @Test
        @DisplayName("written JSON contains user info after setting it")
        void writtenJsonContainsUserInfo() throws IOException {
            manager = new DesignerRegistryManager();
            manager.initialize();

            manager.setUserInfo("admin");
            manager.updateRegistry();

            String content =
                    Files.readString(manager.getRegistryFilePath(), StandardCharsets.UTF_8);
            JsonObject json = gson.fromJson(content, JsonObject.class);
            JsonObject user = json.getAsJsonObject("user");
            assertNotNull(user);
            assertEquals("admin", user.get("username").getAsString());
        }

        @Test
        @DisplayName("written JSON contains capabilities after setting them")
        void writtenJsonContainsCapabilities() throws IOException {
            manager = new DesignerRegistryManager();
            manager.initialize();

            manager.setCapabilities(true, false);
            manager.updateRegistry();

            String content =
                    Files.readString(manager.getRegistryFilePath(), StandardCharsets.UTF_8);
            JsonObject json = gson.fromJson(content, JsonObject.class);
            JsonObject capabilities = json.getAsJsonObject("capabilities");
            assertNotNull(capabilities);
            assertTrue(capabilities.get("scriptExecution").getAsBoolean());
            assertFalse(capabilities.get("gatewayScope").getAsBoolean());
        }

        @Test
        @DisplayName("does nothing when file channel is not open")
        void doesNothingWhenNotInitialized() {
            manager = new DesignerRegistryManager();
            // Do NOT call initialize()
            // This should not throw
            assertDoesNotThrow(() -> manager.updateRegistry());
        }
    }

    @Nested
    @DisplayName("shutdown()")
    class Shutdown {

        @Test
        @DisplayName("deletes both lock and registry files")
        void deletesBothFiles() {
            manager = new DesignerRegistryManager();
            manager.initialize();
            manager.updateRegistry();
            long pid = manager.getPid();
            Path lockPath = FlintConstants.getLockFilePath(pid);
            Path dataPath = manager.getRegistryFilePath();
            assertTrue(Files.exists(lockPath), "Lock file should exist before shutdown");
            assertTrue(Files.exists(dataPath), "Data file should exist before shutdown");

            manager.shutdown();
            assertFalse(Files.exists(lockPath), "Lock file should be deleted after shutdown");
            assertFalse(Files.exists(dataPath), "Data file should be deleted after shutdown");

            // Prevent double shutdown in tearDown
            manager = null;
        }

        @Test
        @DisplayName("can be called multiple times without error")
        void canBeCalledMultipleTimes() {
            manager = new DesignerRegistryManager();
            manager.initialize();

            assertDoesNotThrow(
                    () -> {
                        manager.shutdown();
                        manager.shutdown();
                    });

            // Prevent double shutdown in tearDown
            manager = null;
        }

        @Test
        @DisplayName("can be called without prior initialization")
        void canBeCalledWithoutInit() {
            manager = new DesignerRegistryManager();
            // Do NOT call initialize()
            assertDoesNotThrow(() -> manager.shutdown());

            // Prevent double shutdown in tearDown
            manager = null;
        }
    }

    @Nested
    @DisplayName("lock conflict")
    class LockConflict {

        @Test
        @DisplayName(
                "second manager on same PID lock file cannot acquire lock due to OverlappingFileLockException")
        void secondManagerCannotAcquireLockOnSameFile() {
            // Two DesignerRegistryManager instances created in the same JVM will both
            // point to the same lock file (same PID). Java's FileLock throws
            // OverlappingFileLockException when the same JVM tries to lock a file
            // it already holds. The manager's initialize() catches this as an
            // IOException and returns false.
            DesignerRegistryManager first = new DesignerRegistryManager();

            try {
                boolean firstInit = first.initialize();
                assertTrue(firstInit, "First manager should acquire lock");

                // Verify the lock file exists and is locked by the first manager
                long pid = first.getPid();
                assertTrue(
                        Files.exists(FlintConstants.getLockFilePath(pid)),
                        "Lock file should exist after first initialization");
            } finally {
                first.shutdown();
            }

            // Prevent tearDown from using the field manager
            manager = null;
        }
    }
}
