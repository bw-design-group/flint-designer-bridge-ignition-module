package dev.bwdesigngroup.flint.gateway;

import static org.junit.jupiter.api.Assertions.*;

import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("FlintGatewayHook")
@ExtendWith(MockitoExtension.class)
class FlintGatewayHookTest {

    @Mock private GatewayContext context;

    private FlintGatewayHook hook;

    @BeforeEach
    void setUp() {
        hook = new FlintGatewayHook();
    }

    @Nested
    @DisplayName("module properties")
    class ModuleProperties {

        @Test
        @DisplayName("is maker edition compatible")
        void isMakerEditionCompatible() {
            assertTrue(hook.isMakerEditionCompatible());
        }

        @Test
        @DisplayName("is free module")
        void isFreeModule() {
            assertTrue(hook.isFreeModule());
        }
    }

    @Nested
    @DisplayName("lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("setup stores context without throwing")
        void setupStoresContext() {
            assertDoesNotThrow(() -> hook.setup(context));
        }

        @Test
        @DisplayName("startup initializes all components")
        void startupInitializesComponents() {
            hook.setup(context);
            assertDoesNotThrow(() -> hook.startup(null));
        }

        @Test
        @DisplayName("shutdown does not throw after startup")
        void shutdownAfterStartup() {
            hook.setup(context);
            hook.startup(null);

            assertDoesNotThrow(() -> hook.shutdown());
        }

        @Test
        @DisplayName("shutdown does not throw before startup")
        void shutdownBeforeStartup() {
            assertDoesNotThrow(() -> hook.shutdown());
        }

        @Test
        @DisplayName("shutdown can be called multiple times")
        void shutdownMultipleTimes() {
            hook.setup(context);
            hook.startup(null);

            assertDoesNotThrow(
                    () -> {
                        hook.shutdown();
                        hook.shutdown();
                    });
        }
    }
}
