package dev.bwdesigngroup.flint.common.protocol.methods.tags;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Tag Protocol Types")
class TagProtocolTypesTest {

    private Gson gson;

    @BeforeEach
    void setUp() {
        gson = new GsonBuilder().create();
    }

    // ==================== TagBrowseResult ====================

    @Nested
    @DisplayName("TagBrowseResult")
    class TagBrowseResultTest {

        @Test
        @DisplayName("serializes and deserializes with results")
        void roundTrip() {
            TagBrowseResult.TagNodeInfo node =
                    new TagBrowseResult.TagNodeInfo(
                            "TestTag", "[default]TestTag", "AtomicTag", "Int4", false, "memory");

            TagBrowseResult original = new TagBrowseResult(Collections.singletonList(node));
            String json = gson.toJson(original);
            TagBrowseResult deserialized = gson.fromJson(json, TagBrowseResult.class);

            assertEquals(1, deserialized.getResults().size());
            assertEquals("TestTag", deserialized.getResults().get(0).getName());
            assertEquals("[default]TestTag", deserialized.getResults().get(0).getFullPath());
            assertEquals("AtomicTag", deserialized.getResults().get(0).getTagType());
            assertEquals("Int4", deserialized.getResults().get(0).getDataType());
            assertFalse(deserialized.getResults().get(0).isHasChildren());
            assertEquals("memory", deserialized.getResults().get(0).getValueSource());
        }

        @Test
        @DisplayName("handles empty results")
        void emptyResults() {
            TagBrowseResult original = new TagBrowseResult();
            String json = gson.toJson(original);
            TagBrowseResult deserialized = gson.fromJson(json, TagBrowseResult.class);

            assertNotNull(deserialized.getResults());
            assertTrue(deserialized.getResults().isEmpty());
        }

        @Test
        @DisplayName("handles null results in constructor")
        void nullResults() {
            TagBrowseResult result = new TagBrowseResult(null);
            assertNotNull(result.getResults());
            assertTrue(result.getResults().isEmpty());
        }

        @Test
        @DisplayName("preserves hasChildren=true")
        void hasChildrenTrue() {
            TagBrowseResult.TagNodeInfo node =
                    new TagBrowseResult.TagNodeInfo(
                            "Folder", "[default]Folder", "Folder", null, true, null);

            String json = gson.toJson(node);
            TagBrowseResult.TagNodeInfo deserialized =
                    gson.fromJson(json, TagBrowseResult.TagNodeInfo.class);

            assertTrue(deserialized.isHasChildren());
            assertNull(deserialized.getDataType());
            assertNull(deserialized.getValueSource());
        }
    }

    // ==================== TagReadResult ====================

    @Nested
    @DisplayName("TagReadResult")
    class TagReadResultTest {

        @Test
        @DisplayName("serializes and deserializes with numeric value")
        void roundTripNumeric() {
            TagReadResult.TagValueInfo info =
                    new TagReadResult.TagValueInfo(
                            "[default]Tag1", 42, "Integer", "Good", "2024-01-01T00:00:00Z");

            TagReadResult original = new TagReadResult(Collections.singletonList(info));
            String json = gson.toJson(original);
            TagReadResult deserialized = gson.fromJson(json, TagReadResult.class);

            assertEquals(1, deserialized.getResults().size());
            assertEquals("[default]Tag1", deserialized.getResults().get(0).getPath());
            assertEquals("Good", deserialized.getResults().get(0).getQuality());
        }

        @Test
        @DisplayName("serializes and deserializes with null value")
        void roundTripNull() {
            TagReadResult.TagValueInfo info =
                    new TagReadResult.TagValueInfo("[default]Tag1", null, "null", "Good", "");

            TagReadResult original = new TagReadResult(Collections.singletonList(info));
            String json = gson.toJson(original);
            TagReadResult deserialized = gson.fromJson(json, TagReadResult.class);

            assertNull(deserialized.getResults().get(0).getValue());
        }

        @Test
        @DisplayName("handles empty results")
        void emptyResults() {
            TagReadResult result = new TagReadResult();
            String json = gson.toJson(result);
            TagReadResult deserialized = gson.fromJson(json, TagReadResult.class);

            assertNotNull(deserialized.getResults());
            assertTrue(deserialized.getResults().isEmpty());
        }
    }

    // ==================== TagWriteResult ====================

    @Nested
    @DisplayName("TagWriteResult")
    class TagWriteResultTest {

        @Test
        @DisplayName("serializes and deserializes success status")
        void roundTripSuccess() {
            TagWriteResult.TagWriteStatus status =
                    new TagWriteResult.TagWriteStatus("[default]Tag1", true, "Good", null);

            TagWriteResult original = new TagWriteResult(Collections.singletonList(status));
            String json = gson.toJson(original);
            TagWriteResult deserialized = gson.fromJson(json, TagWriteResult.class);

            assertEquals(1, deserialized.getResults().size());
            assertTrue(deserialized.getResults().get(0).isSuccess());
            assertEquals("Good", deserialized.getResults().get(0).getQuality());
            assertNull(deserialized.getResults().get(0).getError());
        }

        @Test
        @DisplayName("serializes and deserializes failure status")
        void roundTripFailure() {
            TagWriteResult.TagWriteStatus status =
                    new TagWriteResult.TagWriteStatus("[default]Tag1", false, "Bad", "Write error");

            TagWriteResult original = new TagWriteResult(Collections.singletonList(status));
            String json = gson.toJson(original);
            TagWriteResult deserialized = gson.fromJson(json, TagWriteResult.class);

            assertFalse(deserialized.getResults().get(0).isSuccess());
            assertEquals("Write error", deserialized.getResults().get(0).getError());
        }
    }

    // ==================== TagGetConfigResult ====================

    @Nested
    @DisplayName("TagGetConfigResult")
    class TagGetConfigResultTest {

        @Test
        @DisplayName("serializes and deserializes with config JSON")
        void roundTrip() {
            TagGetConfigResult original =
                    new TagGetConfigResult("[default]Tag1", "{\"dataType\":\"Int4\",\"value\":42}");

            String json = gson.toJson(original);
            TagGetConfigResult deserialized = gson.fromJson(json, TagGetConfigResult.class);

            assertEquals("[default]Tag1", deserialized.getPath());
            assertEquals("{\"dataType\":\"Int4\",\"value\":42}", deserialized.getConfig());
        }

        @Test
        @DisplayName("handles empty config")
        void emptyConfig() {
            TagGetConfigResult result = new TagGetConfigResult("[default]Tag1", "{}");
            String json = gson.toJson(result);
            TagGetConfigResult deserialized = gson.fromJson(json, TagGetConfigResult.class);

            assertEquals("{}", deserialized.getConfig());
        }
    }

    // ==================== TagCreateResult ====================

    @Nested
    @DisplayName("TagCreateResult")
    class TagCreateResultTest {

        @Test
        @DisplayName("serializes and deserializes success status")
        void roundTripSuccess() {
            TagCreateResult.TagCreateStatus status =
                    new TagCreateResult.TagCreateStatus("NewTag", true, null);

            TagCreateResult original = new TagCreateResult(Collections.singletonList(status));
            String json = gson.toJson(original);
            TagCreateResult deserialized = gson.fromJson(json, TagCreateResult.class);

            assertEquals(1, deserialized.getResults().size());
            assertEquals("NewTag", deserialized.getResults().get(0).getName());
            assertTrue(deserialized.getResults().get(0).isSuccess());
            assertNull(deserialized.getResults().get(0).getError());
        }

        @Test
        @DisplayName("serializes and deserializes failure status")
        void roundTripFailure() {
            TagCreateResult.TagCreateStatus status =
                    new TagCreateResult.TagCreateStatus("BadTag", false, "Tag already exists");

            TagCreateResult original = new TagCreateResult(Collections.singletonList(status));
            String json = gson.toJson(original);
            TagCreateResult deserialized = gson.fromJson(json, TagCreateResult.class);

            assertFalse(deserialized.getResults().get(0).isSuccess());
            assertEquals("Tag already exists", deserialized.getResults().get(0).getError());
        }

        @Test
        @DisplayName("handles multiple statuses")
        void multipleStatuses() {
            TagCreateResult original =
                    new TagCreateResult(
                            Arrays.asList(
                                    new TagCreateResult.TagCreateStatus("Tag1", true, null),
                                    new TagCreateResult.TagCreateStatus("Tag2", false, "error")));

            String json = gson.toJson(original);
            TagCreateResult deserialized = gson.fromJson(json, TagCreateResult.class);

            assertEquals(2, deserialized.getResults().size());
        }
    }

    // ==================== TagEditResult ====================

    @Nested
    @DisplayName("TagEditResult")
    class TagEditResultTest {

        @Test
        @DisplayName("serializes and deserializes success")
        void roundTripSuccess() {
            TagEditResult original = TagEditResult.success();
            String json = gson.toJson(original);
            TagEditResult deserialized = gson.fromJson(json, TagEditResult.class);

            assertTrue(deserialized.isSuccess());
            assertNull(deserialized.getError());
        }

        @Test
        @DisplayName("serializes and deserializes failure")
        void roundTripFailure() {
            TagEditResult original = TagEditResult.failure("Edit failed");
            String json = gson.toJson(original);
            TagEditResult deserialized = gson.fromJson(json, TagEditResult.class);

            assertFalse(deserialized.isSuccess());
            assertEquals("Edit failed", deserialized.getError());
        }

        @Test
        @DisplayName("factory methods produce correct state")
        void factoryMethods() {
            TagEditResult success = TagEditResult.success();
            assertTrue(success.isSuccess());
            assertNull(success.getError());

            TagEditResult failure = TagEditResult.failure("oops");
            assertFalse(failure.isSuccess());
            assertEquals("oops", failure.getError());
        }
    }

    // ==================== TagDeleteResult ====================

    @Nested
    @DisplayName("TagDeleteResult")
    class TagDeleteResultTest {

        @Test
        @DisplayName("serializes and deserializes success status")
        void roundTripSuccess() {
            TagDeleteResult.TagDeleteStatus status =
                    new TagDeleteResult.TagDeleteStatus("[default]Tag1", true, null);

            TagDeleteResult original = new TagDeleteResult(Collections.singletonList(status));
            String json = gson.toJson(original);
            TagDeleteResult deserialized = gson.fromJson(json, TagDeleteResult.class);

            assertEquals(1, deserialized.getResults().size());
            assertEquals("[default]Tag1", deserialized.getResults().get(0).getPath());
            assertTrue(deserialized.getResults().get(0).isSuccess());
        }

        @Test
        @DisplayName("serializes and deserializes failure status")
        void roundTripFailure() {
            TagDeleteResult.TagDeleteStatus status =
                    new TagDeleteResult.TagDeleteStatus("[default]Tag1", false, "Tag not found");

            TagDeleteResult original = new TagDeleteResult(Collections.singletonList(status));
            String json = gson.toJson(original);
            TagDeleteResult deserialized = gson.fromJson(json, TagDeleteResult.class);

            assertFalse(deserialized.getResults().get(0).isSuccess());
            assertEquals("Tag not found", deserialized.getResults().get(0).getError());
        }
    }

    // ==================== TagProvidersResult ====================

    @Nested
    @DisplayName("TagProvidersResult")
    class TagProvidersResultTest {

        @Test
        @DisplayName("serializes and deserializes provider info")
        void roundTrip() {
            TagProvidersResult original =
                    new TagProvidersResult(
                            Arrays.asList(
                                    new TagProvidersResult.TagProviderInfo("default", "standard"),
                                    new TagProvidersResult.TagProviderInfo("System", "internal")));

            String json = gson.toJson(original);
            TagProvidersResult deserialized = gson.fromJson(json, TagProvidersResult.class);

            assertEquals(2, deserialized.getProviders().size());
            assertEquals("default", deserialized.getProviders().get(0).getName());
            assertEquals("standard", deserialized.getProviders().get(0).getType());
            assertEquals("System", deserialized.getProviders().get(1).getName());
            assertEquals("internal", deserialized.getProviders().get(1).getType());
        }

        @Test
        @DisplayName("handles empty providers")
        void emptyProviders() {
            TagProvidersResult result = new TagProvidersResult();
            String json = gson.toJson(result);
            TagProvidersResult deserialized = gson.fromJson(json, TagProvidersResult.class);

            assertNotNull(deserialized.getProviders());
            assertTrue(deserialized.getProviders().isEmpty());
        }

        @Test
        @DisplayName("handles null providers in constructor")
        void nullProviders() {
            TagProvidersResult result = new TagProvidersResult(null);
            assertNotNull(result.getProviders());
            assertTrue(result.getProviders().isEmpty());
        }
    }

    // ==================== UdtListResult ====================

    @Nested
    @DisplayName("UdtListResult")
    class UdtListResultTest {

        @Test
        @DisplayName("serializes and deserializes with definitions")
        void roundTrip() {
            UdtListResult.UdtInfo info = new UdtListResult.UdtInfo("Motor", "Motor", true);

            UdtListResult original = new UdtListResult(Collections.singletonList(info));
            String json = gson.toJson(original);
            UdtListResult deserialized = gson.fromJson(json, UdtListResult.class);

            assertEquals(1, deserialized.getDefinitions().size());
            assertEquals("Motor", deserialized.getDefinitions().get(0).getName());
            assertEquals("Motor", deserialized.getDefinitions().get(0).getPath());
            assertTrue(deserialized.getDefinitions().get(0).isHasMembers());
        }

        @Test
        @DisplayName("handles empty definitions")
        void emptyDefinitions() {
            UdtListResult original = new UdtListResult();
            String json = gson.toJson(original);
            UdtListResult deserialized = gson.fromJson(json, UdtListResult.class);

            assertNotNull(deserialized.getDefinitions());
            assertTrue(deserialized.getDefinitions().isEmpty());
        }

        @Test
        @DisplayName("handles null definitions in constructor")
        void nullDefinitions() {
            UdtListResult result = new UdtListResult(null);
            assertNotNull(result.getDefinitions());
            assertTrue(result.getDefinitions().isEmpty());
        }

        @Test
        @DisplayName("handles multiple definitions")
        void multipleDefinitions() {
            UdtListResult original =
                    new UdtListResult(
                            Arrays.asList(
                                    new UdtListResult.UdtInfo("Motor", "Motor", true),
                                    new UdtListResult.UdtInfo("Valve", "Valve", false)));

            String json = gson.toJson(original);
            UdtListResult deserialized = gson.fromJson(json, UdtListResult.class);

            assertEquals(2, deserialized.getDefinitions().size());
            assertEquals("Valve", deserialized.getDefinitions().get(1).getName());
            assertFalse(deserialized.getDefinitions().get(1).isHasMembers());
        }
    }

    // ==================== UdtDefinitionResult ====================

    @Nested
    @DisplayName("UdtDefinitionResult")
    class UdtDefinitionResultTest {

        @Test
        @DisplayName("serializes and deserializes with members")
        void roundTrip() {
            UdtDefinitionResult.UdtMemberInfo member =
                    new UdtDefinitionResult.UdtMemberInfo(
                            "Speed", "AtomicTag", "Float4", 0.0, "memory", "Motor speed");

            UdtDefinitionResult original =
                    new UdtDefinitionResult("Motor", "Motor", Collections.singletonList(member));
            String json = gson.toJson(original);
            UdtDefinitionResult deserialized = gson.fromJson(json, UdtDefinitionResult.class);

            assertEquals("Motor", deserialized.getName());
            assertEquals("Motor", deserialized.getPath());
            assertEquals(1, deserialized.getMembers().size());
            assertEquals("Speed", deserialized.getMembers().get(0).getName());
            assertEquals("AtomicTag", deserialized.getMembers().get(0).getTagType());
            assertEquals("Float4", deserialized.getMembers().get(0).getDataType());
            assertEquals("memory", deserialized.getMembers().get(0).getValueSource());
            assertEquals("Motor speed", deserialized.getMembers().get(0).getTooltip());
        }

        @Test
        @DisplayName("handles empty members")
        void emptyMembers() {
            UdtDefinitionResult original = new UdtDefinitionResult();
            String json = gson.toJson(original);
            UdtDefinitionResult deserialized = gson.fromJson(json, UdtDefinitionResult.class);

            assertNotNull(deserialized.getMembers());
            assertTrue(deserialized.getMembers().isEmpty());
        }

        @Test
        @DisplayName("handles null members in constructor")
        void nullMembers() {
            UdtDefinitionResult result = new UdtDefinitionResult("Test", "Test", null);
            assertNotNull(result.getMembers());
            assertTrue(result.getMembers().isEmpty());
        }

        @Test
        @DisplayName("handles member with null optional fields")
        void memberWithNulls() {
            UdtDefinitionResult.UdtMemberInfo member =
                    new UdtDefinitionResult.UdtMemberInfo(
                            "Status", "AtomicTag", "Boolean", null, null, null);

            String json = gson.toJson(member);
            UdtDefinitionResult.UdtMemberInfo deserialized =
                    gson.fromJson(json, UdtDefinitionResult.UdtMemberInfo.class);

            assertEquals("Status", deserialized.getName());
            assertNull(deserialized.getValue());
            assertNull(deserialized.getValueSource());
            assertNull(deserialized.getTooltip());
        }
    }
}
