package dev.bwdesigngroup.flint.designer;

import static org.junit.jupiter.api.Assertions.*;

import com.inductiveautomation.ignition.designer.model.DesignerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("FlintDesignerHook")
@ExtendWith(MockitoExtension.class)
class FlintDesignerHookTest {

    @Mock private DesignerContext context;

    private FlintDesignerHook hook;

    @BeforeEach
    void setUp() {
        hook = new FlintDesignerHook();
    }

    @Nested
    @DisplayName("initial state")
    class InitialState {

        @Test
        @DisplayName("context is null before startup")
        void contextIsNullBeforeStartup() {
            assertNull(hook.getContext());
        }
    }

    @Nested
    @DisplayName("shutdown safety")
    class ShutdownSafety {

        @Test
        @DisplayName("shutdown does not throw when called before startup")
        void shutdownDoesNotThrowBeforeStartup() {
            assertDoesNotThrow(() -> hook.shutdown());
        }

        @Test
        @DisplayName("shutdown can be called multiple times safely")
        void shutdownMultipleTimesSafe() {
            assertDoesNotThrow(
                    () -> {
                        hook.shutdown();
                        hook.shutdown();
                    });
        }
    }
}
