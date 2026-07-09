package dev.bwdesigngroup.flint.gateway.tags;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.inductiveautomation.ignition.common.browsing.BrowseFilter;
import com.inductiveautomation.ignition.common.browsing.Results;
import com.inductiveautomation.ignition.common.config.Property;
import com.inductiveautomation.ignition.common.model.values.BasicQualifiedValue;
import com.inductiveautomation.ignition.common.model.values.QualifiedValue;
import com.inductiveautomation.ignition.common.model.values.QualityCode;
import com.inductiveautomation.ignition.common.sqltags.model.types.DataType;
import com.inductiveautomation.ignition.common.tags.browsing.NodeDescription;
import com.inductiveautomation.ignition.common.tags.config.BasicTagConfiguration;
import com.inductiveautomation.ignition.common.tags.config.CollisionPolicy;
import com.inductiveautomation.ignition.common.tags.config.TagConfiguration;
import com.inductiveautomation.ignition.common.tags.config.TagConfigurationModel;
import com.inductiveautomation.ignition.common.tags.config.properties.WellKnownTagProps;
import com.inductiveautomation.ignition.common.tags.config.types.TagObjectType;
import com.inductiveautomation.ignition.common.tags.model.TagPath;
import com.inductiveautomation.ignition.common.tags.model.TagProvider;
import com.inductiveautomation.ignition.common.tags.paths.parser.TagPathParser;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.tags.model.GatewayTagManager;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagBrowseResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagBrowseResult.TagNodeInfo;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagCreateResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagCreateResult.TagCreateStatus;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagDeleteResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagDeleteResult.TagDeleteStatus;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagEditResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagGetConfigResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagProvidersResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagProvidersResult.TagProviderInfo;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagReadResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagReadResult.TagValueInfo;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagWriteResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagWriteResult.TagWriteStatus;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.UdtDefinitionResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.UdtDefinitionResult.UdtMemberInfo;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.UdtListResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.UdtListResult.UdtInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Encapsulates all tag system operations via the Gateway TagManager API. */
public class GatewayTagExecutor {
    private static final Logger logger = LoggerFactory.getLogger("flint.Gateway.Tags");

    private final GatewayContext context;
    private final Gson gson;

    public GatewayTagExecutor(GatewayContext context) {
        this.context = context;
        this.gson = new GsonBuilder().create();
    }

    private GatewayTagManager getTagManager() {
        return context.getTagManager();
    }

    /** Browse tags at a specific level in the tag tree. */
    public TagBrowseResult browse(
            String provider, String parentPath, String typeFilter, String nameFilter) {
        try {
            String browsePath = buildBrowsePath(provider, parentPath);
            logger.debug("Browsing tags at path: {}", browsePath);

            TagPath tagPath = TagPathParser.parse(browsePath);

            BrowseFilter filter = new BrowseFilter();
            if (typeFilter != null && !typeFilter.isEmpty()) {
                filter.setAllowedTypes(new String[] {typeFilter});
            }
            if (nameFilter != null && !nameFilter.isEmpty()) {
                filter.addNameFilter(nameFilter);
            }

            Results<NodeDescription> browseResults =
                    getTagManager().browseAsync(tagPath, filter).get();
            Collection<NodeDescription> nodes = browseResults.getResults();

            List<TagNodeInfo> results = new ArrayList<>();
            for (NodeDescription node : nodes) {
                String name = node.getName();
                String fullPath = node.getFullPath() != null ? node.getFullPath().toString() : "";
                String tagType = node.getObjectType() != null ? node.getObjectType().name() : "";
                String dataType = node.getDataType() != null ? node.getDataType().toString() : null;
                boolean hasChildren = node.hasChildren();

                // Try to get valueSource from extended properties
                String valueSource = null;
                try {
                    valueSource = node.getOrElse(WellKnownTagProps.ValueSource, null);
                } catch (Exception ignored) {
                    // Not all nodes have valueSource
                }

                results.add(
                        new TagNodeInfo(
                                name, fullPath, tagType, dataType, hasChildren, valueSource));
            }

            return new TagBrowseResult(results);
        } catch (Exception e) {
            logger.error("Failed to browse tags", e);
            return new TagBrowseResult();
        }
    }

    /** Read tag values for the given paths. */
    public TagReadResult read(List<String> tagPaths) {
        try {
            List<TagPath> paths = new ArrayList<>();
            for (String p : tagPaths) {
                paths.add(TagPathParser.parse(p));
            }

            List<QualifiedValue> values = getTagManager().readAsync(paths).get();

            List<TagValueInfo> results = new ArrayList<>();
            for (int i = 0; i < values.size(); i++) {
                QualifiedValue qv = values.get(i);
                String path = tagPaths.get(i);
                Object value = qv.getValue();
                String dataType = value != null ? value.getClass().getSimpleName() : "null";
                String quality = qv.getQuality().isGood() ? "Good" : qv.getQuality().toString();
                String timestamp = qv.getTimestamp() != null ? qv.getTimestamp().toString() : "";

                results.add(
                        new TagValueInfo(path, convertValue(value), dataType, quality, timestamp));
            }

            return new TagReadResult(results);
        } catch (Exception e) {
            logger.error("Failed to read tags", e);
            return new TagReadResult();
        }
    }

    /** Write values to tags. */
    public TagWriteResult write(List<String> paths, List<String> values, List<String> dataTypes) {
        try {
            List<TagPath> tagPaths = new ArrayList<>();
            List<Object> writeValues = new ArrayList<>();

            for (int i = 0; i < paths.size(); i++) {
                tagPaths.add(TagPathParser.parse(paths.get(i)));
                writeValues.add(
                        coerceValue(values.get(i), i < dataTypes.size() ? dataTypes.get(i) : null));
            }

            List<QualityCode> results = getTagManager().writeAsync(tagPaths, writeValues).get();

            List<TagWriteStatus> statuses = new ArrayList<>();
            for (int i = 0; i < results.size(); i++) {
                QualityCode qc = results.get(i);
                statuses.add(
                        new TagWriteStatus(
                                paths.get(i),
                                qc.isGood(),
                                qc.isGood() ? "Good" : qc.toString(),
                                qc.isGood() ? null : qc.toString()));
            }

            return new TagWriteResult(statuses);
        } catch (Exception e) {
            logger.error("Failed to write tags", e);
            List<TagWriteStatus> statuses = new ArrayList<>();
            for (String path : paths) {
                statuses.add(new TagWriteStatus(path, false, "Bad", e.getMessage()));
            }
            return new TagWriteResult(statuses);
        }
    }

    /** Get full tag configuration as JSON. */
    public TagGetConfigResult getConfig(String tagPath) {
        try {
            TagPath path = TagPathParser.parse(tagPath);
            String providerName = path.getSource();
            TagProvider provider = getTagManager().getTagProvider(providerName);

            List<TagPath> paths = new ArrayList<>();
            paths.add(path);

            List<TagConfigurationModel> configs =
                    provider.getTagConfigsAsync(paths, false, true).get();

            if (configs.isEmpty()) {
                return new TagGetConfigResult(tagPath, "{}");
            }

            TagConfigurationModel config = configs.get(0);
            String configJson = tagConfigToJson(config);
            return new TagGetConfigResult(tagPath, configJson);
        } catch (Exception e) {
            logger.error("Failed to get tag config for: {}", tagPath, e);
            return new TagGetConfigResult(tagPath, "{}");
        }
    }

    /** Create tags from JSON configuration. */
    public TagCreateResult create(String parentPath, String tagsJson) {
        try {
            TagPath parent = TagPathParser.parse(parentPath);
            String providerName = parent.getSource();
            TagProvider provider = getTagManager().getTagProvider(providerName);

            JsonArray tagsArray = JsonParser.parseString(tagsJson).getAsJsonArray();

            List<TagCreateStatus> statuses = new ArrayList<>();

            for (JsonElement element : tagsArray) {
                JsonObject tagObj = element.getAsJsonObject();
                String name = tagObj.has("name") ? tagObj.get("name").getAsString() : "NewTag";

                try {
                    // Build the full tag path under the parent
                    String childPathStr = parentPath + "/" + name;
                    TagPath childPath = TagPathParser.parse(childPathStr);

                    TagConfiguration config = BasicTagConfiguration.createNew(childPath);
                    applyJsonToConfig(config, tagObj);

                    List<TagConfiguration> configs = new ArrayList<>();
                    configs.add(config);

                    List<QualityCode> results =
                            provider.saveTagConfigsAsync(configs, CollisionPolicy.Abort).get();

                    boolean success = !results.isEmpty() && results.get(0).isGood();
                    statuses.add(
                            new TagCreateStatus(
                                    name,
                                    success,
                                    success
                                            ? null
                                            : (results.isEmpty()
                                                    ? "Unknown error"
                                                    : results.get(0).toString())));
                } catch (Exception e) {
                    statuses.add(new TagCreateStatus(name, false, e.getMessage()));
                }
            }

            return new TagCreateResult(statuses);
        } catch (Exception e) {
            logger.error("Failed to create tags", e);
            List<TagCreateStatus> statuses = new ArrayList<>();
            statuses.add(new TagCreateStatus("unknown", false, e.getMessage()));
            return new TagCreateResult(statuses);
        }
    }

    /** Edit tag configuration with a partial config merge. */
    public TagEditResult edit(String tagPath, String configJson) {
        try {
            TagPath path = TagPathParser.parse(tagPath);
            String providerName = path.getSource();
            TagProvider provider = getTagManager().getTagProvider(providerName);

            JsonObject configObj = JsonParser.parseString(configJson).getAsJsonObject();

            TagConfiguration config = BasicTagConfiguration.createEdit(path);
            applyJsonToConfig(config, configObj);

            List<TagConfiguration> configs = new ArrayList<>();
            configs.add(config);

            List<QualityCode> results =
                    provider.saveTagConfigsAsync(configs, CollisionPolicy.MergeOverwrite).get();

            if (results.isEmpty()) {
                return TagEditResult.failure("No result returned");
            }

            QualityCode qc = results.get(0);
            return qc.isGood() ? TagEditResult.success() : TagEditResult.failure(qc.toString());
        } catch (Exception e) {
            logger.error("Failed to edit tag: {}", tagPath, e);
            return TagEditResult.failure(e.getMessage());
        }
    }

    /** Delete tags at the given paths. */
    public TagDeleteResult delete(List<String> tagPaths) {
        try {
            // Group by provider
            List<TagPath> paths = new ArrayList<>();
            for (String p : tagPaths) {
                paths.add(TagPathParser.parse(p));
            }

            // Use the first path's provider for the delete operation
            if (paths.isEmpty()) {
                return new TagDeleteResult();
            }

            String providerName = paths.get(0).getSource();
            TagProvider provider = getTagManager().getTagProvider(providerName);

            List<QualityCode> results = provider.removeTagConfigsAsync(paths).get();

            List<TagDeleteStatus> statuses = new ArrayList<>();
            for (int i = 0; i < results.size(); i++) {
                QualityCode qc = results.get(i);
                statuses.add(
                        new TagDeleteStatus(
                                tagPaths.get(i), qc.isGood(), qc.isGood() ? null : qc.toString()));
            }

            return new TagDeleteResult(statuses);
        } catch (Exception e) {
            logger.error("Failed to delete tags", e);
            List<TagDeleteStatus> statuses = new ArrayList<>();
            for (String path : tagPaths) {
                statuses.add(new TagDeleteStatus(path, false, e.getMessage()));
            }
            return new TagDeleteResult(statuses);
        }
    }

    /** List all tag providers. */
    public TagProvidersResult getProviders() {
        try {
            List<String> providerNames = getTagManager().getTagProviderNames();
            List<TagProviderInfo> providers = new ArrayList<>();

            for (String name : providerNames) {
                providers.add(new TagProviderInfo(name, "standard"));
            }

            return new TagProvidersResult(providers);
        } catch (Exception e) {
            logger.error("Failed to get tag providers", e);
            return new TagProvidersResult();
        }
    }

    // ==================== UDT Methods ====================

    /** List all UDT definitions on a tag provider. */
    public UdtListResult getDefinitions(String provider) {
        try {
            if (provider == null || provider.isEmpty()) {
                provider = "default";
            }
            String browsePath = "[" + provider + "]_types_";
            logger.debug("Browsing UDT definitions at path: {}", browsePath);

            TagPath tagPath = TagPathParser.parse(browsePath);

            BrowseFilter filter = new BrowseFilter();
            Results<NodeDescription> browseResults =
                    getTagManager().browseAsync(tagPath, filter).get();
            Collection<NodeDescription> nodes = browseResults.getResults();

            List<UdtInfo> definitions = new ArrayList<>();
            for (NodeDescription node : nodes) {
                if (node.getObjectType() == TagObjectType.UdtType) {
                    String name = node.getName();
                    String path = name;
                    boolean hasMembers = node.hasChildren();
                    definitions.add(new UdtInfo(name, path, hasMembers));
                }
            }

            return new UdtListResult(definitions);
        } catch (Exception e) {
            logger.error("Failed to get UDT definitions", e);
            return new UdtListResult();
        }
    }

    /** Get a UDT definition with its member structure. */
    public UdtDefinitionResult getDefinition(String provider, String typePath) {
        try {
            if (provider == null || provider.isEmpty()) {
                provider = "default";
            }
            String fullPath = "[" + provider + "]_types_/" + typePath;
            logger.debug("Getting UDT definition at path: {}", fullPath);

            TagPath tagPath = TagPathParser.parse(fullPath);

            // Browse the definition to get its member tags
            BrowseFilter filter = new BrowseFilter();
            Results<NodeDescription> browseResults =
                    getTagManager().browseAsync(tagPath, filter).get();
            Collection<NodeDescription> nodes = browseResults.getResults();

            List<UdtMemberInfo> members = new ArrayList<>();
            TagProvider tagProvider = getTagManager().getTagProvider(provider);

            for (NodeDescription node : nodes) {
                String memberName = node.getName();
                String tagType = node.getObjectType() != null ? node.getObjectType().name() : "";
                String dataType = node.getDataType() != null ? node.getDataType().toString() : null;

                // Get member config to retrieve value and other properties
                Object value = null;
                String valueSource = null;
                String tooltip = null;
                try {
                    TagPath memberPath = TagPathParser.parse(fullPath + "/" + memberName);
                    List<TagPath> memberPaths = new ArrayList<>();
                    memberPaths.add(memberPath);
                    List<TagConfigurationModel> configs =
                            tagProvider.getTagConfigsAsync(memberPaths, false, true).get();
                    if (!configs.isEmpty()) {
                        TagConfigurationModel config = configs.get(0);
                        try {
                            QualifiedValue qv = config.get(WellKnownTagProps.Value);
                            if (qv != null) {
                                value = convertValue(qv.getValue());
                            }
                        } catch (Exception ignored) {
                        }
                        try {
                            valueSource = config.get(WellKnownTagProps.ValueSource);
                        } catch (Exception ignored) {
                        }
                        try {
                            tooltip = config.get(WellKnownTagProps.Tooltip);
                        } catch (Exception ignored) {
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Failed to get config for member: {}", memberName, e);
                }

                members.add(
                        new UdtMemberInfo(
                                memberName, tagType, dataType, value, valueSource, tooltip));
            }

            // Extract the definition name from the typePath
            String name =
                    typePath.contains("/")
                            ? typePath.substring(typePath.lastIndexOf('/') + 1)
                            : typePath;
            return new UdtDefinitionResult(name, typePath, members);
        } catch (Exception e) {
            logger.error("Failed to get UDT definition: {}", typePath, e);
            return new UdtDefinitionResult();
        }
    }

    /** Create a new UDT definition with member tags. */
    public TagCreateResult createDefinition(
            String provider, String name, String parentTypePath, String membersJson) {
        try {
            if (provider == null || provider.isEmpty()) {
                provider = "default";
            }
            // Build the definition path under _types_
            String defPath;
            if (parentTypePath == null || parentTypePath.isEmpty()) {
                defPath = "[" + provider + "]_types_/" + name;
            } else {
                defPath = "[" + provider + "]_types_/" + parentTypePath + "/" + name;
            }
            logger.debug("Creating UDT definition at path: {}", defPath);

            TagPath definitionPath = TagPathParser.parse(defPath);
            TagProvider tagProvider = getTagManager().getTagProvider(provider);

            // Create the UDT definition tag
            TagConfiguration defConfig = BasicTagConfiguration.createNew(definitionPath);
            defConfig.setType(TagObjectType.UdtType);

            List<TagConfiguration> configs = new ArrayList<>();
            configs.add(defConfig);

            List<QualityCode> defResults =
                    tagProvider.saveTagConfigsAsync(configs, CollisionPolicy.Abort).get();
            boolean defSuccess = !defResults.isEmpty() && defResults.get(0).isGood();

            List<TagCreateStatus> statuses = new ArrayList<>();
            statuses.add(
                    new TagCreateStatus(
                            name,
                            defSuccess,
                            defSuccess
                                    ? null
                                    : (!defResults.isEmpty()
                                            ? defResults.get(0).toString()
                                            : "Unknown error")));

            if (!defSuccess) {
                return new TagCreateResult(statuses);
            }

            // Create member tags if provided
            if (membersJson != null && !membersJson.isEmpty()) {
                JsonArray membersArray = JsonParser.parseString(membersJson).getAsJsonArray();

                for (JsonElement element : membersArray) {
                    JsonObject memberObj = element.getAsJsonObject();
                    String memberName =
                            memberObj.has("name")
                                    ? memberObj.get("name").getAsString()
                                    : "NewMember";

                    try {
                        String memberPathStr = defPath + "/" + memberName;
                        TagPath memberPath = TagPathParser.parse(memberPathStr);

                        TagConfiguration memberConfig = BasicTagConfiguration.createNew(memberPath);
                        applyJsonToConfig(memberConfig, memberObj);

                        List<TagConfiguration> memberConfigs = new ArrayList<>();
                        memberConfigs.add(memberConfig);

                        List<QualityCode> memberResults =
                                tagProvider
                                        .saveTagConfigsAsync(memberConfigs, CollisionPolicy.Abort)
                                        .get();
                        boolean memberSuccess =
                                !memberResults.isEmpty() && memberResults.get(0).isGood();

                        statuses.add(
                                new TagCreateStatus(
                                        memberName,
                                        memberSuccess,
                                        memberSuccess
                                                ? null
                                                : (!memberResults.isEmpty()
                                                        ? memberResults.get(0).toString()
                                                        : "Unknown error")));
                    } catch (Exception e) {
                        statuses.add(new TagCreateStatus(memberName, false, e.getMessage()));
                    }
                }
            }

            return new TagCreateResult(statuses);
        } catch (Exception e) {
            logger.error("Failed to create UDT definition: {}", name, e);
            List<TagCreateStatus> statuses = new ArrayList<>();
            statuses.add(new TagCreateStatus(name, false, e.getMessage()));
            return new TagCreateResult(statuses);
        }
    }

    /** Create an instance of a UDT definition. */
    public TagCreateResult createInstance(
            String parentPath, String name, String typeId, String overridesJson) {
        try {
            String instancePathStr = parentPath + "/" + name;
            logger.debug(
                    "Creating UDT instance at path: {} with typeId: {}", instancePathStr, typeId);

            TagPath instancePath = TagPathParser.parse(instancePathStr);
            String providerName = instancePath.getSource();
            TagProvider provider = getTagManager().getTagProvider(providerName);

            TagConfiguration config = BasicTagConfiguration.createNew(instancePath);
            config.setType(TagObjectType.UdtInstance);
            config.set(WellKnownTagProps.TypeId, typeId);

            // Apply overrides if provided
            if (overridesJson != null && !overridesJson.isEmpty()) {
                JsonObject overridesObj = JsonParser.parseString(overridesJson).getAsJsonObject();
                applyJsonToConfig(config, overridesObj);
            }

            List<TagConfiguration> configs = new ArrayList<>();
            configs.add(config);

            List<QualityCode> results =
                    provider.saveTagConfigsAsync(configs, CollisionPolicy.Abort).get();
            boolean success = !results.isEmpty() && results.get(0).isGood();

            List<TagCreateStatus> statuses = new ArrayList<>();
            statuses.add(
                    new TagCreateStatus(
                            name,
                            success,
                            success
                                    ? null
                                    : (!results.isEmpty()
                                            ? results.get(0).toString()
                                            : "Unknown error")));

            return new TagCreateResult(statuses);
        } catch (Exception e) {
            logger.error("Failed to create UDT instance: {}", name, e);
            List<TagCreateStatus> statuses = new ArrayList<>();
            statuses.add(new TagCreateStatus(name, false, e.getMessage()));
            return new TagCreateResult(statuses);
        }
    }

    // --- Helper methods ---

    private String buildBrowsePath(String provider, String parentPath) {
        if (provider == null || provider.isEmpty()) {
            provider = "default";
        }
        if (parentPath == null || parentPath.isEmpty()) {
            return "[" + provider + "]";
        }
        if (parentPath.startsWith("[")) {
            return parentPath;
        }
        return "[" + provider + "]" + parentPath;
    }

    private Object convertValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof QualifiedValue) {
            return convertValue(((QualifiedValue) value).getValue());
        }
        if (value instanceof Date) {
            return value.toString();
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof String) {
            return value;
        }
        return value.toString();
    }

    private Object coerceValue(String value, String dataType) {
        if (value == null) {
            return null;
        }
        if (dataType == null || dataType.isEmpty()) {
            if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                return Boolean.parseBoolean(value);
            }
            try {
                if (value.contains(".")) {
                    return Double.parseDouble(value);
                }
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                return value;
            }
        }

        switch (dataType.toLowerCase()) {
            case "boolean":
                return Boolean.parseBoolean(value);
            case "int4":
            case "int2":
            case "int1":
                return Integer.parseInt(value);
            case "int8":
                return Long.parseLong(value);
            case "float4":
                return Float.parseFloat(value);
            case "float8":
                return Double.parseDouble(value);
            default:
                return value;
        }
    }

    private void applyJsonToConfig(TagConfiguration config, JsonObject configObj) {
        if (configObj.has("tagType")) {
            String tagType = configObj.get("tagType").getAsString();
            config.setType(TagObjectType.valueOf(tagType));
        }

        if (configObj.has("dataType")) {
            String dtStr = configObj.get("dataType").getAsString();
            DataType dt = DataType.valueOf(dtStr);
            config.set(WellKnownTagProps.DataType, dt);
        }

        if (configObj.has("value")) {
            JsonElement valElem = configObj.get("value");
            Object val = jsonElementToValue(valElem);
            config.set(WellKnownTagProps.Value, new BasicQualifiedValue(val));
        }

        if (configObj.has("valueSource")) {
            config.set(WellKnownTagProps.ValueSource, configObj.get("valueSource").getAsString());
        }

        if (configObj.has("tooltip")) {
            config.set(WellKnownTagProps.Tooltip, configObj.get("tooltip").getAsString());
        }

        if (configObj.has("documentation")) {
            config.set(
                    WellKnownTagProps.Documentation, configObj.get("documentation").getAsString());
        }

        if (configObj.has("enabled")) {
            config.set(WellKnownTagProps.Enabled, configObj.get("enabled").getAsBoolean());
        }

        if (configObj.has("formatString")) {
            config.set(WellKnownTagProps.FormatString, configObj.get("formatString").getAsString());
        }

        if (configObj.has("engUnit")) {
            config.set(WellKnownTagProps.EngUnit, configObj.get("engUnit").getAsString());
        }

        if (configObj.has("typeId")) {
            config.set(WellKnownTagProps.TypeId, configObj.get("typeId").getAsString());
        }
    }

    private Object jsonElementToValue(JsonElement elem) {
        if (elem == null || elem.isJsonNull()) {
            return null;
        }
        if (elem.isJsonPrimitive()) {
            if (elem.getAsJsonPrimitive().isBoolean()) {
                return elem.getAsBoolean();
            } else if (elem.getAsJsonPrimitive().isNumber()) {
                return elem.getAsNumber();
            } else {
                return elem.getAsString();
            }
        }
        return elem.toString();
    }

    private String tagConfigToJson(TagConfigurationModel config) {
        JsonObject json = new JsonObject();

        // Serialize all properties from the config
        try {
            for (Property<?> prop : config.getProperties()) {
                String propName = prop.getName();
                Object val = config.get(prop);
                if (val != null) {
                    if (val instanceof QualifiedValue) {
                        Object inner = ((QualifiedValue) val).getValue();
                        addJsonValue(json, propName, inner);
                    } else {
                        addJsonValue(json, propName, val);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Error serializing tag config", e);
        }

        return gson.toJson(json);
    }

    private void addJsonValue(JsonObject json, String key, Object val) {
        if (val == null) {
            return;
        }
        if (val instanceof Number) {
            json.addProperty(key, (Number) val);
        } else if (val instanceof Boolean) {
            json.addProperty(key, (Boolean) val);
        } else {
            json.addProperty(key, val.toString());
        }
    }
}
