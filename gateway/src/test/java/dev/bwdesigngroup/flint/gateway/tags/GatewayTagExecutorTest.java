package dev.bwdesigngroup.flint.gateway.tags;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.inductiveautomation.ignition.common.browsing.Results;
import com.inductiveautomation.ignition.common.model.values.BasicQualifiedValue;
import com.inductiveautomation.ignition.common.model.values.QualifiedValue;
import com.inductiveautomation.ignition.common.model.values.QualityCode;
import com.inductiveautomation.ignition.common.sqltags.model.types.DataType;
import com.inductiveautomation.ignition.common.tags.browsing.NodeDescription;
import com.inductiveautomation.ignition.common.tags.config.TagConfigurationModel;
import com.inductiveautomation.ignition.common.tags.config.types.TagObjectType;
import com.inductiveautomation.ignition.common.tags.model.TagPath;
import com.inductiveautomation.ignition.common.tags.model.TagProvider;
import com.inductiveautomation.ignition.common.tags.paths.parser.TagPathParser;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.tags.model.GatewayTagManager;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagBrowseResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagCreateResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagDeleteResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagEditResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagGetConfigResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagProvidersResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagReadResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagWriteResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.UdtDefinitionResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.UdtListResult;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("GatewayTagExecutor")
@ExtendWith(MockitoExtension.class)
class GatewayTagExecutorTest {

    @Mock private GatewayContext context;

    @Mock private GatewayTagManager tagManager;

    private GatewayTagExecutor executor;

    @BeforeEach
    void setUp() {
        lenient().when(context.getTagManager()).thenReturn(tagManager);
        executor = new GatewayTagExecutor(context);
    }

    // ==================== browse() ====================

    @Nested
    @DisplayName("browse")
    class Browse {

        @Test
        @DisplayName("returns results from browseAsync")
        void returnsResults() throws Exception {
            NodeDescription node = mock(NodeDescription.class);
            when(node.getName()).thenReturn("TestTag");
            when(node.getFullPath()).thenReturn(TagPathParser.parse("[default]TestTag"));
            when(node.getObjectType()).thenReturn(TagObjectType.AtomicTag);
            when(node.getDataType()).thenReturn(DataType.Int4);
            when(node.hasChildren()).thenReturn(false);

            Results<NodeDescription> browseResults = mock(Results.class);
            Collection<NodeDescription> nodes = Collections.singletonList(node);
            when(browseResults.getResults()).thenReturn(nodes);

            when(tagManager.browseAsync(any(TagPath.class), any()))
                    .thenReturn(CompletableFuture.completedFuture(browseResults));

            TagBrowseResult result = executor.browse("default", "", null, null);

            assertNotNull(result);
            assertEquals(1, result.getResults().size());
            assertEquals("TestTag", result.getResults().get(0).getName());
            assertEquals("AtomicTag", result.getResults().get(0).getTagType());
        }

        @Test
        @DisplayName("returns empty results on empty browse")
        void returnsEmptyResults() throws Exception {
            Results<NodeDescription> browseResults = mock(Results.class);
            when(browseResults.getResults()).thenReturn(Collections.emptyList());

            when(tagManager.browseAsync(any(TagPath.class), any()))
                    .thenReturn(CompletableFuture.completedFuture(browseResults));

            TagBrowseResult result = executor.browse("default", "", null, null);

            assertNotNull(result);
            assertTrue(result.getResults().isEmpty());
        }

        @Test
        @DisplayName("returns empty result on exception")
        void returnsEmptyOnException() throws Exception {
            when(tagManager.browseAsync(any(TagPath.class), any()))
                    .thenReturn(
                            CompletableFuture.failedFuture(new RuntimeException("Browse failed")));

            TagBrowseResult result = executor.browse("default", "", null, null);

            assertNotNull(result);
            assertTrue(result.getResults().isEmpty());
        }

        @Test
        @DisplayName("defaults provider to 'default' when null")
        void defaultsProviderWhenNull() throws Exception {
            Results<NodeDescription> browseResults = mock(Results.class);
            when(browseResults.getResults()).thenReturn(Collections.emptyList());

            when(tagManager.browseAsync(any(TagPath.class), any()))
                    .thenReturn(CompletableFuture.completedFuture(browseResults));

            executor.browse(null, null, null, null);

            verify(tagManager)
                    .browseAsync(argThat(path -> path.toString().contains("default")), any());
        }

        @Test
        @DisplayName("preserves path that already starts with bracket")
        void preservesBracketPath() throws Exception {
            Results<NodeDescription> browseResults = mock(Results.class);
            when(browseResults.getResults()).thenReturn(Collections.emptyList());

            when(tagManager.browseAsync(any(TagPath.class), any()))
                    .thenReturn(CompletableFuture.completedFuture(browseResults));

            executor.browse("default", "[custom]Folder", null, null);

            // Path starting with [ should be preserved as-is
            verify(tagManager).browseAsync(any(TagPath.class), any());
        }

        @Test
        @DisplayName("handles node with null fullPath and objectType")
        void handlesNullNodeProperties() throws Exception {
            NodeDescription node = mock(NodeDescription.class);
            when(node.getName()).thenReturn("NullTag");
            when(node.getFullPath()).thenReturn(null);
            when(node.getObjectType()).thenReturn(null);
            when(node.getDataType()).thenReturn(null);
            when(node.hasChildren()).thenReturn(false);

            Results<NodeDescription> browseResults = mock(Results.class);
            when(browseResults.getResults()).thenReturn(Collections.singletonList(node));

            when(tagManager.browseAsync(any(TagPath.class), any()))
                    .thenReturn(CompletableFuture.completedFuture(browseResults));

            TagBrowseResult result = executor.browse("default", "", null, null);

            assertEquals(1, result.getResults().size());
            assertEquals("", result.getResults().get(0).getFullPath());
            assertEquals("", result.getResults().get(0).getTagType());
            assertNull(result.getResults().get(0).getDataType());
        }
    }

    // ==================== read() ====================

    @Nested
    @DisplayName("read")
    class Read {

        @Test
        @DisplayName("returns values from readAsync")
        void returnsValues() throws Exception {
            QualifiedValue qv = new BasicQualifiedValue(42);

            when(tagManager.readAsync(anyList()))
                    .thenReturn(CompletableFuture.completedFuture(Collections.singletonList(qv)));

            TagReadResult result = executor.read(Collections.singletonList("[default]TestTag"));

            assertNotNull(result);
            assertEquals(1, result.getResults().size());
            assertEquals("[default]TestTag", result.getResults().get(0).getPath());
            assertEquals(42, result.getResults().get(0).getValue());
            assertEquals("Good", result.getResults().get(0).getQuality());
        }

        @Test
        @DisplayName("handles null value in QualifiedValue")
        void handlesNullValue() throws Exception {
            QualifiedValue qv = new BasicQualifiedValue(null);

            when(tagManager.readAsync(anyList()))
                    .thenReturn(CompletableFuture.completedFuture(Collections.singletonList(qv)));

            TagReadResult result = executor.read(Collections.singletonList("[default]TestTag"));

            assertEquals(1, result.getResults().size());
            assertNull(result.getResults().get(0).getValue());
            assertEquals("null", result.getResults().get(0).getDataType());
        }

        @Test
        @DisplayName("converts Boolean value correctly")
        void convertsBooleanValue() throws Exception {
            QualifiedValue qv = new BasicQualifiedValue(true);

            when(tagManager.readAsync(anyList()))
                    .thenReturn(CompletableFuture.completedFuture(Collections.singletonList(qv)));

            TagReadResult result = executor.read(Collections.singletonList("[default]BoolTag"));

            assertEquals(true, result.getResults().get(0).getValue());
            assertEquals("Boolean", result.getResults().get(0).getDataType());
        }

        @Test
        @DisplayName("converts String value correctly")
        void convertsStringValue() throws Exception {
            QualifiedValue qv = new BasicQualifiedValue("hello");

            when(tagManager.readAsync(anyList()))
                    .thenReturn(CompletableFuture.completedFuture(Collections.singletonList(qv)));

            TagReadResult result = executor.read(Collections.singletonList("[default]StrTag"));

            assertEquals("hello", result.getResults().get(0).getValue());
            assertEquals("String", result.getResults().get(0).getDataType());
        }

        @Test
        @DisplayName("converts Date value to string")
        void convertsDateValue() throws Exception {
            Date date = new Date(1000000000000L);
            QualifiedValue qv = new BasicQualifiedValue(date);

            when(tagManager.readAsync(anyList()))
                    .thenReturn(CompletableFuture.completedFuture(Collections.singletonList(qv)));

            TagReadResult result = executor.read(Collections.singletonList("[default]DateTag"));

            assertNotNull(result.getResults().get(0).getValue());
            assertTrue(result.getResults().get(0).getValue() instanceof String);
        }

        @Test
        @DisplayName("returns empty result on exception")
        void returnsEmptyOnException() throws Exception {
            when(tagManager.readAsync(anyList()))
                    .thenReturn(
                            CompletableFuture.failedFuture(new RuntimeException("Read failed")));

            TagReadResult result = executor.read(Collections.singletonList("[default]TestTag"));

            assertNotNull(result);
            assertTrue(result.getResults().isEmpty());
        }

        @Test
        @DisplayName("reads multiple tags")
        void readsMultipleTags() throws Exception {
            QualifiedValue qv1 = new BasicQualifiedValue(10);
            QualifiedValue qv2 = new BasicQualifiedValue(20);

            when(tagManager.readAsync(anyList()))
                    .thenReturn(CompletableFuture.completedFuture(Arrays.asList(qv1, qv2)));

            List<String> paths = Arrays.asList("[default]Tag1", "[default]Tag2");
            TagReadResult result = executor.read(paths);

            assertEquals(2, result.getResults().size());
            assertEquals("[default]Tag1", result.getResults().get(0).getPath());
            assertEquals("[default]Tag2", result.getResults().get(1).getPath());
        }
    }

    // ==================== write() ====================

    @Nested
    @DisplayName("write")
    class Write {

        @Test
        @DisplayName("returns success statuses from writeAsync")
        void returnsSuccessStatuses() throws Exception {
            when(tagManager.writeAsync(anyList(), anyList()))
                    .thenReturn(
                            CompletableFuture.completedFuture(
                                    Collections.singletonList(QualityCode.Good)));

            TagWriteResult result =
                    executor.write(
                            Collections.singletonList("[default]TestTag"),
                            Collections.singletonList("42"),
                            Collections.singletonList("int4"));

            assertNotNull(result);
            assertEquals(1, result.getResults().size());
            assertTrue(result.getResults().get(0).isSuccess());
            assertEquals("Good", result.getResults().get(0).getQuality());
            assertNull(result.getResults().get(0).getError());
        }

        @Test
        @DisplayName("returns failure statuses for bad quality")
        void returnsFailureForBadQuality() throws Exception {
            QualityCode badQuality = QualityCode.Bad;

            when(tagManager.writeAsync(anyList(), anyList()))
                    .thenReturn(
                            CompletableFuture.completedFuture(
                                    Collections.singletonList(badQuality)));

            TagWriteResult result =
                    executor.write(
                            Collections.singletonList("[default]TestTag"),
                            Collections.singletonList("42"),
                            Collections.emptyList());

            assertEquals(1, result.getResults().size());
            assertFalse(result.getResults().get(0).isSuccess());
        }

        @Test
        @DisplayName("auto-detects boolean value when no dataType specified")
        void autoDetectsBoolean() throws Exception {
            when(tagManager.writeAsync(
                            anyList(),
                            argThat(
                                    values ->
                                            values.size() == 1
                                                    && values.get(0) instanceof Boolean
                                                    && (Boolean) values.get(0))))
                    .thenReturn(
                            CompletableFuture.completedFuture(
                                    Collections.singletonList(QualityCode.Good)));

            executor.write(
                    Collections.singletonList("[default]BoolTag"),
                    Collections.singletonList("true"),
                    Collections.emptyList());

            verify(tagManager)
                    .writeAsync(anyList(), argThat(values -> values.get(0) instanceof Boolean));
        }

        @Test
        @DisplayName("auto-detects integer value when no dataType specified")
        void autoDetectsInteger() throws Exception {
            when(tagManager.writeAsync(
                            anyList(),
                            argThat(values -> values.size() == 1 && values.get(0) instanceof Long)))
                    .thenReturn(
                            CompletableFuture.completedFuture(
                                    Collections.singletonList(QualityCode.Good)));

            executor.write(
                    Collections.singletonList("[default]IntTag"),
                    Collections.singletonList("42"),
                    Collections.emptyList());

            verify(tagManager)
                    .writeAsync(
                            anyList(),
                            argThat(
                                    values ->
                                            values.get(0) instanceof Long
                                                    && ((Long) values.get(0)) == 42L));
        }

        @Test
        @DisplayName("auto-detects double value when no dataType specified")
        void autoDetectsDouble() throws Exception {
            when(tagManager.writeAsync(anyList(), anyList()))
                    .thenReturn(
                            CompletableFuture.completedFuture(
                                    Collections.singletonList(QualityCode.Good)));

            executor.write(
                    Collections.singletonList("[default]FloatTag"),
                    Collections.singletonList("3.14"),
                    Collections.emptyList());

            verify(tagManager)
                    .writeAsync(
                            anyList(),
                            argThat(
                                    values ->
                                            values.get(0) instanceof Double
                                                    && ((Double) values.get(0)) == 3.14));
        }

        @Test
        @DisplayName("coerces value with explicit int4 dataType")
        void coercesInt4() throws Exception {
            when(tagManager.writeAsync(anyList(), anyList()))
                    .thenReturn(
                            CompletableFuture.completedFuture(
                                    Collections.singletonList(QualityCode.Good)));

            executor.write(
                    Collections.singletonList("[default]IntTag"),
                    Collections.singletonList("42"),
                    Collections.singletonList("int4"));

            verify(tagManager)
                    .writeAsync(
                            anyList(),
                            argThat(
                                    values ->
                                            values.get(0) instanceof Integer
                                                    && ((Integer) values.get(0)) == 42));
        }

        @Test
        @DisplayName("coerces value with explicit float8 dataType")
        void coercesFloat8() throws Exception {
            when(tagManager.writeAsync(anyList(), anyList()))
                    .thenReturn(
                            CompletableFuture.completedFuture(
                                    Collections.singletonList(QualityCode.Good)));

            executor.write(
                    Collections.singletonList("[default]DblTag"),
                    Collections.singletonList("2.718"),
                    Collections.singletonList("float8"));

            verify(tagManager)
                    .writeAsync(
                            anyList(),
                            argThat(
                                    values ->
                                            values.get(0) instanceof Double
                                                    && ((Double) values.get(0)) == 2.718));
        }

        @Test
        @DisplayName("coerces value with explicit boolean dataType")
        void coercesBoolean() throws Exception {
            when(tagManager.writeAsync(anyList(), anyList()))
                    .thenReturn(
                            CompletableFuture.completedFuture(
                                    Collections.singletonList(QualityCode.Good)));

            executor.write(
                    Collections.singletonList("[default]BoolTag"),
                    Collections.singletonList("false"),
                    Collections.singletonList("boolean"));

            verify(tagManager)
                    .writeAsync(
                            anyList(),
                            argThat(
                                    values ->
                                            values.get(0) instanceof Boolean
                                                    && !(Boolean) values.get(0)));
        }

        @Test
        @DisplayName("returns error statuses on exception")
        void returnsErrorOnException() throws Exception {
            when(tagManager.writeAsync(anyList(), anyList()))
                    .thenReturn(
                            CompletableFuture.failedFuture(new RuntimeException("Write failed")));

            TagWriteResult result =
                    executor.write(
                            Collections.singletonList("[default]TestTag"),
                            Collections.singletonList("42"),
                            Collections.emptyList());

            assertEquals(1, result.getResults().size());
            assertFalse(result.getResults().get(0).isSuccess());
            assertEquals("Bad", result.getResults().get(0).getQuality());
            assertNotNull(result.getResults().get(0).getError());
        }

        @Test
        @DisplayName("handles null value in coercion")
        void handlesNullValue() throws Exception {
            when(tagManager.writeAsync(anyList(), anyList()))
                    .thenReturn(
                            CompletableFuture.completedFuture(
                                    Collections.singletonList(QualityCode.Good)));

            executor.write(
                    Collections.singletonList("[default]NullTag"),
                    Collections.singletonList(null),
                    Collections.emptyList());

            verify(tagManager).writeAsync(anyList(), argThat(values -> values.get(0) == null));
        }

        @Test
        @DisplayName("falls back to string when value is not numeric or boolean")
        void fallsBackToString() throws Exception {
            when(tagManager.writeAsync(anyList(), anyList()))
                    .thenReturn(
                            CompletableFuture.completedFuture(
                                    Collections.singletonList(QualityCode.Good)));

            executor.write(
                    Collections.singletonList("[default]StrTag"),
                    Collections.singletonList("hello world"),
                    Collections.emptyList());

            verify(tagManager)
                    .writeAsync(anyList(), argThat(values -> "hello world".equals(values.get(0))));
        }
    }

    // ==================== getConfig() ====================

    @Nested
    @DisplayName("getConfig")
    class GetConfig {

        @Mock private TagProvider provider;

        @Test
        @DisplayName("returns config JSON from provider")
        void returnsConfigJson() throws Exception {
            when(tagManager.getTagProvider("default")).thenReturn(provider);

            TagConfigurationModel configModel = mock(TagConfigurationModel.class);
            when(configModel.getProperties()).thenReturn(Collections.emptySet());

            when(provider.getTagConfigsAsync(anyList(), eq(false), eq(true)))
                    .thenReturn(
                            CompletableFuture.completedFuture(
                                    Collections.singletonList(configModel)));

            TagGetConfigResult result = executor.getConfig("[default]TestTag");

            assertNotNull(result);
            assertEquals("[default]TestTag", result.getPath());
            assertNotNull(result.getConfig());
        }

        @Test
        @DisplayName("returns empty JSON on empty configs")
        void returnsEmptyJsonOnEmptyConfigs() throws Exception {
            when(tagManager.getTagProvider("default")).thenReturn(provider);

            when(provider.getTagConfigsAsync(anyList(), eq(false), eq(true)))
                    .thenReturn(CompletableFuture.completedFuture(Collections.emptyList()));

            TagGetConfigResult result = executor.getConfig("[default]TestTag");

            assertEquals("[default]TestTag", result.getPath());
            assertEquals("{}", result.getConfig());
        }

        @Test
        @DisplayName("returns empty JSON on exception")
        void returnsEmptyJsonOnException() throws Exception {
            when(tagManager.getTagProvider("default"))
                    .thenThrow(new RuntimeException("Provider error"));

            TagGetConfigResult result = executor.getConfig("[default]TestTag");

            assertEquals("{}", result.getConfig());
        }
    }

    // ==================== create() ====================

    @Nested
    @DisplayName("create")
    class Create {

        @Mock private TagProvider provider;

        @Test
        @DisplayName("creates tag and returns success status")
        void createsTagSuccessfully() throws Exception {
            when(tagManager.getTagProvider("default")).thenReturn(provider);
            when(provider.saveTagConfigsAsync(anyList(), any()))
                    .thenReturn(
                            CompletableFuture.completedFuture(
                                    Collections.singletonList(QualityCode.Good)));

            String tagsJson =
                    "[{\"name\":\"NewTag\",\"tagType\":\"AtomicTag\",\"dataType\":\"Int4\",\"value\":0}]";
            TagCreateResult result = executor.create("[default]Folder", tagsJson);

            assertNotNull(result);
            assertEquals(1, result.getResults().size());
            assertTrue(result.getResults().get(0).isSuccess());
            assertEquals("NewTag", result.getResults().get(0).getName());
        }

        @Test
        @DisplayName("handles partial failures in multi-tag create")
        void handlesPartialFailures() throws Exception {
            when(tagManager.getTagProvider("default")).thenReturn(provider);

            when(provider.saveTagConfigsAsync(anyList(), any()))
                    .thenReturn(
                            CompletableFuture.completedFuture(
                                    Collections.singletonList(QualityCode.Good)))
                    .thenReturn(
                            CompletableFuture.completedFuture(
                                    Collections.singletonList(QualityCode.Bad)));

            String tagsJson = "[{\"name\":\"Tag1\"},{\"name\":\"Tag2\"}]";
            TagCreateResult result = executor.create("[default]Folder", tagsJson);

            assertEquals(2, result.getResults().size());
            assertTrue(result.getResults().get(0).isSuccess());
            assertFalse(result.getResults().get(1).isSuccess());
        }

        @Test
        @DisplayName("defaults name to 'NewTag' when not specified")
        void defaultsName() throws Exception {
            when(tagManager.getTagProvider("default")).thenReturn(provider);
            when(provider.saveTagConfigsAsync(anyList(), any()))
                    .thenReturn(
                            CompletableFuture.completedFuture(
                                    Collections.singletonList(QualityCode.Good)));

            String tagsJson = "[{\"tagType\":\"AtomicTag\"}]";
            TagCreateResult result = executor.create("[default]Folder", tagsJson);

            assertEquals("NewTag", result.getResults().get(0).getName());
        }

        @Test
        @DisplayName("returns error on invalid JSON")
        void returnsErrorOnInvalidJson() {
            TagCreateResult result = executor.create("[default]Folder", "invalid json");

            assertNotNull(result);
            assertEquals(1, result.getResults().size());
            assertFalse(result.getResults().get(0).isSuccess());
        }

        @Test
        @DisplayName("returns error on provider exception")
        void returnsErrorOnProviderException() throws Exception {
            when(tagManager.getTagProvider("default"))
                    .thenThrow(new RuntimeException("No provider"));

            TagCreateResult result = executor.create("[default]Folder", "[{\"name\":\"Tag1\"}]");

            assertEquals(1, result.getResults().size());
            assertFalse(result.getResults().get(0).isSuccess());
        }
    }

    // ==================== edit() ====================

    @Nested
    @DisplayName("edit")
    class Edit {

        @Mock private TagProvider provider;

        @Test
        @DisplayName("returns success on good quality")
        void returnsSuccess() throws Exception {
            when(tagManager.getTagProvider("default")).thenReturn(provider);
            when(provider.saveTagConfigsAsync(anyList(), any()))
                    .thenReturn(
                            CompletableFuture.completedFuture(
                                    Collections.singletonList(QualityCode.Good)));

            TagEditResult result = executor.edit("[default]TestTag", "{\"tooltip\":\"updated\"}");

            assertTrue(result.isSuccess());
            assertNull(result.getError());
        }

        @Test
        @DisplayName("returns failure on bad quality")
        void returnsFailureOnBadQuality() throws Exception {
            when(tagManager.getTagProvider("default")).thenReturn(provider);
            when(provider.saveTagConfigsAsync(anyList(), any()))
                    .thenReturn(
                            CompletableFuture.completedFuture(
                                    Collections.singletonList(QualityCode.Bad)));

            TagEditResult result = executor.edit("[default]TestTag", "{\"tooltip\":\"updated\"}");

            assertFalse(result.isSuccess());
            assertNotNull(result.getError());
        }

        @Test
        @DisplayName("returns failure on empty results")
        void returnsFailureOnEmptyResults() throws Exception {
            when(tagManager.getTagProvider("default")).thenReturn(provider);
            when(provider.saveTagConfigsAsync(anyList(), any()))
                    .thenReturn(CompletableFuture.completedFuture(Collections.emptyList()));

            TagEditResult result = executor.edit("[default]TestTag", "{\"tooltip\":\"updated\"}");

            assertFalse(result.isSuccess());
            assertEquals("No result returned", result.getError());
        }

        @Test
        @DisplayName("returns failure on exception")
        void returnsFailureOnException() throws Exception {
            when(tagManager.getTagProvider("default"))
                    .thenThrow(new RuntimeException("Edit failed"));

            TagEditResult result = executor.edit("[default]TestTag", "{\"tooltip\":\"updated\"}");

            assertFalse(result.isSuccess());
            assertNotNull(result.getError());
        }
    }

    // ==================== delete() ====================

    @Nested
    @DisplayName("delete")
    class Delete {

        @Mock private TagProvider provider;

        @Test
        @DisplayName("returns success statuses from removeTagConfigsAsync")
        void returnsSuccessStatuses() throws Exception {
            when(tagManager.getTagProvider("default")).thenReturn(provider);
            when(provider.removeTagConfigsAsync(anyList()))
                    .thenReturn(
                            CompletableFuture.completedFuture(
                                    Collections.singletonList(QualityCode.Good)));

            TagDeleteResult result = executor.delete(Collections.singletonList("[default]TestTag"));

            assertEquals(1, result.getResults().size());
            assertTrue(result.getResults().get(0).isSuccess());
            assertEquals("[default]TestTag", result.getResults().get(0).getPath());
        }

        @Test
        @DisplayName("returns empty result for empty paths")
        void returnsEmptyForEmptyPaths() {
            TagDeleteResult result = executor.delete(Collections.emptyList());

            assertNotNull(result);
            assertTrue(result.getResults().isEmpty());
        }

        @Test
        @DisplayName("returns error statuses on exception")
        void returnsErrorOnException() throws Exception {
            when(tagManager.getTagProvider("default"))
                    .thenThrow(new RuntimeException("Delete failed"));

            List<String> paths = Arrays.asList("[default]Tag1", "[default]Tag2");
            TagDeleteResult result = executor.delete(paths);

            assertEquals(2, result.getResults().size());
            assertFalse(result.getResults().get(0).isSuccess());
            assertFalse(result.getResults().get(1).isSuccess());
        }

        @Test
        @DisplayName("handles failure quality code per tag")
        void handlesFailurePerTag() throws Exception {
            when(tagManager.getTagProvider("default")).thenReturn(provider);
            when(provider.removeTagConfigsAsync(anyList()))
                    .thenReturn(
                            CompletableFuture.completedFuture(
                                    Arrays.asList(QualityCode.Good, QualityCode.Bad)));

            List<String> paths = Arrays.asList("[default]Tag1", "[default]Tag2");
            TagDeleteResult result = executor.delete(paths);

            assertEquals(2, result.getResults().size());
            assertTrue(result.getResults().get(0).isSuccess());
            assertFalse(result.getResults().get(1).isSuccess());
        }
    }

    // ==================== getProviders() ====================

    @Nested
    @DisplayName("getProviders")
    class GetProviders {

        @Test
        @DisplayName("returns provider names from tag manager")
        void returnsProviderNames() {
            when(tagManager.getTagProviderNames())
                    .thenReturn(Arrays.asList("default", "System", "Custom"));

            TagProvidersResult result = executor.getProviders();

            assertNotNull(result);
            assertEquals(3, result.getProviders().size());
            assertEquals("default", result.getProviders().get(0).getName());
            assertEquals("System", result.getProviders().get(1).getName());
            assertEquals("Custom", result.getProviders().get(2).getName());
            assertEquals("standard", result.getProviders().get(0).getType());
        }

        @Test
        @DisplayName("returns empty list on exception")
        void returnsEmptyOnException() {
            when(tagManager.getTagProviderNames())
                    .thenThrow(new RuntimeException("Provider error"));

            TagProvidersResult result = executor.getProviders();

            assertNotNull(result);
            assertTrue(result.getProviders().isEmpty());
        }

        @Test
        @DisplayName("handles empty provider list")
        void handlesEmptyProviders() {
            when(tagManager.getTagProviderNames()).thenReturn(Collections.emptyList());

            TagProvidersResult result = executor.getProviders();

            assertNotNull(result);
            assertTrue(result.getProviders().isEmpty());
        }
    }

    // ==================== getDefinitions() ====================

    @Nested
    @DisplayName("getDefinitions")
    class GetDefinitions {

        @Test
        @DisplayName("returns UDT type nodes from _types_ browse")
        void returnsUdtTypes() throws Exception {
            NodeDescription node = mock(NodeDescription.class);
            when(node.getName()).thenReturn("Motor");
            when(node.getObjectType()).thenReturn(TagObjectType.UdtType);
            when(node.hasChildren()).thenReturn(true);

            Results<NodeDescription> browseResults = mock(Results.class);
            when(browseResults.getResults()).thenReturn(Collections.singletonList(node));

            when(tagManager.browseAsync(any(TagPath.class), any()))
                    .thenReturn(CompletableFuture.completedFuture(browseResults));

            UdtListResult result = executor.getDefinitions("default");

            assertNotNull(result);
            assertEquals(1, result.getDefinitions().size());
            assertEquals("Motor", result.getDefinitions().get(0).getName());
            assertTrue(result.getDefinitions().get(0).isHasMembers());
        }

        @Test
        @DisplayName("filters out non-UdtType nodes")
        void filtersNonUdtTypes() throws Exception {
            NodeDescription udtNode = mock(NodeDescription.class);
            when(udtNode.getName()).thenReturn("Motor");
            when(udtNode.getObjectType()).thenReturn(TagObjectType.UdtType);
            when(udtNode.hasChildren()).thenReturn(true);

            NodeDescription folderNode = mock(NodeDescription.class);
            when(folderNode.getObjectType()).thenReturn(TagObjectType.Folder);

            Results<NodeDescription> browseResults = mock(Results.class);
            when(browseResults.getResults()).thenReturn(Arrays.asList(udtNode, folderNode));

            when(tagManager.browseAsync(any(TagPath.class), any()))
                    .thenReturn(CompletableFuture.completedFuture(browseResults));

            UdtListResult result = executor.getDefinitions("default");

            assertEquals(1, result.getDefinitions().size());
            assertEquals("Motor", result.getDefinitions().get(0).getName());
        }

        @Test
        @DisplayName("returns empty result on exception")
        void returnsEmptyOnException() throws Exception {
            when(tagManager.browseAsync(any(TagPath.class), any()))
                    .thenReturn(
                            CompletableFuture.failedFuture(new RuntimeException("Browse failed")));

            UdtListResult result = executor.getDefinitions("default");

            assertNotNull(result);
            assertTrue(result.getDefinitions().isEmpty());
        }

        @Test
        @DisplayName("defaults provider to 'default' when null")
        void defaultsProvider() throws Exception {
            Results<NodeDescription> browseResults = mock(Results.class);
            when(browseResults.getResults()).thenReturn(Collections.emptyList());

            when(tagManager.browseAsync(any(TagPath.class), any()))
                    .thenReturn(CompletableFuture.completedFuture(browseResults));

            executor.getDefinitions(null);

            verify(tagManager)
                    .browseAsync(argThat(path -> path.toString().contains("default")), any());
        }
    }

    // ==================== getDefinition() ====================

    @Nested
    @DisplayName("getDefinition")
    class GetDefinition {

        @Mock private TagProvider provider;

        @Test
        @DisplayName("returns definition with members")
        void returnsDefinitionWithMembers() throws Exception {
            NodeDescription memberNode = mock(NodeDescription.class);
            when(memberNode.getName()).thenReturn("Speed");
            when(memberNode.getObjectType()).thenReturn(TagObjectType.AtomicTag);
            when(memberNode.getDataType()).thenReturn(DataType.Float4);

            Results<NodeDescription> browseResults = mock(Results.class);
            when(browseResults.getResults()).thenReturn(Collections.singletonList(memberNode));

            when(tagManager.browseAsync(any(TagPath.class), any()))
                    .thenReturn(CompletableFuture.completedFuture(browseResults));

            when(tagManager.getTagProvider("default")).thenReturn(provider);

            TagConfigurationModel memberConfig = mock(TagConfigurationModel.class);
            when(provider.getTagConfigsAsync(anyList(), eq(false), eq(true)))
                    .thenReturn(
                            CompletableFuture.completedFuture(
                                    Collections.singletonList(memberConfig)));

            UdtDefinitionResult result = executor.getDefinition("default", "Motor");

            assertNotNull(result);
            assertEquals("Motor", result.getName());
            assertEquals("Motor", result.getPath());
            assertEquals(1, result.getMembers().size());
            assertEquals("Speed", result.getMembers().get(0).getName());
            assertEquals("AtomicTag", result.getMembers().get(0).getTagType());
            assertEquals("Float4", result.getMembers().get(0).getDataType());
        }

        @Test
        @DisplayName("returns empty result on exception")
        void returnsEmptyOnException() throws Exception {
            when(tagManager.browseAsync(any(TagPath.class), any()))
                    .thenReturn(
                            CompletableFuture.failedFuture(new RuntimeException("Browse failed")));

            UdtDefinitionResult result = executor.getDefinition("default", "Motor");

            assertNotNull(result);
            assertTrue(result.getMembers().isEmpty());
        }

        @Test
        @DisplayName("handles nested type path")
        void handlesNestedTypePath() throws Exception {
            Results<NodeDescription> browseResults = mock(Results.class);
            when(browseResults.getResults()).thenReturn(Collections.emptyList());

            when(tagManager.browseAsync(any(TagPath.class), any()))
                    .thenReturn(CompletableFuture.completedFuture(browseResults));

            when(tagManager.getTagProvider("default")).thenReturn(provider);

            UdtDefinitionResult result = executor.getDefinition("default", "Equipment/Motor");

            assertEquals("Motor", result.getName());
            assertEquals("Equipment/Motor", result.getPath());
        }
    }

    // ==================== createDefinition() ====================

    @Nested
    @DisplayName("createDefinition")
    class CreateDefinition {

        @Mock private TagProvider provider;

        @Test
        @DisplayName("creates UDT definition successfully")
        void createsDefinitionSuccessfully() throws Exception {
            when(tagManager.getTagProvider("default")).thenReturn(provider);
            when(provider.saveTagConfigsAsync(anyList(), any()))
                    .thenReturn(
                            CompletableFuture.completedFuture(
                                    Collections.singletonList(QualityCode.Good)));

            TagCreateResult result = executor.createDefinition("default", "Motor", "", null);

            assertNotNull(result);
            assertEquals(1, result.getResults().size());
            assertTrue(result.getResults().get(0).isSuccess());
            assertEquals("Motor", result.getResults().get(0).getName());
        }

        @Test
        @DisplayName("creates definition with members")
        void createsWithMembers() throws Exception {
            when(tagManager.getTagProvider("default")).thenReturn(provider);
            when(provider.saveTagConfigsAsync(anyList(), any()))
                    .thenReturn(
                            CompletableFuture.completedFuture(
                                    Collections.singletonList(QualityCode.Good)));

            String membersJson =
                    "[{\"name\":\"Speed\",\"tagType\":\"AtomicTag\",\"dataType\":\"Float4\"},{\"name\":\"Running\",\"tagType\":\"AtomicTag\",\"dataType\":\"Boolean\"}]";
            TagCreateResult result = executor.createDefinition("default", "Motor", "", membersJson);

            assertNotNull(result);
            // 1 definition + 2 members = 3 statuses
            assertEquals(3, result.getResults().size());
            assertEquals("Motor", result.getResults().get(0).getName());
            assertEquals("Speed", result.getResults().get(1).getName());
            assertEquals("Running", result.getResults().get(2).getName());
        }

        @Test
        @DisplayName("returns error when definition creation fails")
        void returnsErrorOnDefFailure() throws Exception {
            when(tagManager.getTagProvider("default")).thenReturn(provider);
            when(provider.saveTagConfigsAsync(anyList(), any()))
                    .thenReturn(
                            CompletableFuture.completedFuture(
                                    Collections.singletonList(QualityCode.Bad)));

            TagCreateResult result = executor.createDefinition("default", "Motor", "", null);

            assertEquals(1, result.getResults().size());
            assertFalse(result.getResults().get(0).isSuccess());
        }

        @Test
        @DisplayName("handles nested parent type path")
        void handlesNestedParent() throws Exception {
            when(tagManager.getTagProvider("default")).thenReturn(provider);
            when(provider.saveTagConfigsAsync(anyList(), any()))
                    .thenReturn(
                            CompletableFuture.completedFuture(
                                    Collections.singletonList(QualityCode.Good)));

            TagCreateResult result =
                    executor.createDefinition("default", "Motor", "Equipment", null);

            assertTrue(result.getResults().get(0).isSuccess());
        }

        @Test
        @DisplayName("returns error on provider exception")
        void returnsErrorOnException() throws Exception {
            when(tagManager.getTagProvider("default"))
                    .thenThrow(new RuntimeException("Provider error"));

            TagCreateResult result = executor.createDefinition("default", "Motor", "", null);

            assertEquals(1, result.getResults().size());
            assertFalse(result.getResults().get(0).isSuccess());
        }
    }

    // ==================== createInstance() ====================

    @Nested
    @DisplayName("createInstance")
    class CreateInstance {

        @Mock private TagProvider provider;

        @Test
        @DisplayName("creates UDT instance with typeId set")
        void createsInstanceSuccessfully() throws Exception {
            when(tagManager.getTagProvider("default")).thenReturn(provider);
            when(provider.saveTagConfigsAsync(anyList(), any()))
                    .thenReturn(
                            CompletableFuture.completedFuture(
                                    Collections.singletonList(QualityCode.Good)));

            TagCreateResult result =
                    executor.createInstance("[default]Line1", "Motor1", "Motor", null);

            assertNotNull(result);
            assertEquals(1, result.getResults().size());
            assertTrue(result.getResults().get(0).isSuccess());
            assertEquals("Motor1", result.getResults().get(0).getName());
        }

        @Test
        @DisplayName("applies overrides when provided")
        void appliesOverrides() throws Exception {
            when(tagManager.getTagProvider("default")).thenReturn(provider);
            when(provider.saveTagConfigsAsync(anyList(), any()))
                    .thenReturn(
                            CompletableFuture.completedFuture(
                                    Collections.singletonList(QualityCode.Good)));

            TagCreateResult result =
                    executor.createInstance(
                            "[default]Line1", "Motor1", "Motor", "{\"tooltip\":\"Motor 1\"}");

            assertTrue(result.getResults().get(0).isSuccess());
        }

        @Test
        @DisplayName("returns error on provider exception")
        void returnsErrorOnException() throws Exception {
            when(tagManager.getTagProvider("default"))
                    .thenThrow(new RuntimeException("Provider error"));

            TagCreateResult result =
                    executor.createInstance("[default]Line1", "Motor1", "Motor", null);

            assertEquals(1, result.getResults().size());
            assertFalse(result.getResults().get(0).isSuccess());
        }
    }
}
