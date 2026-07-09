package dev.bwdesigngroup.flint.gateway.perspective;

import com.inductiveautomation.perspective.gateway.model.ComponentModel;
import com.inductiveautomation.perspective.gateway.model.ViewModel;
import com.inductiveautomation.perspective.gateway.property.PropertyTree;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.BindingProfile;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.ComponentProfile;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.ProfileWarning;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.ViewProfileResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Profiles a live Perspective view by walking the runtime ComponentModel tree. Extracts binding
 * states, property sizes, and component-level metrics. Delegates binding extraction to {@link
 * BindingStateExtractor}.
 */
class PerspectiveProfiler {
    private static final Logger logger =
            LoggerFactory.getLogger("flint.Gateway.PerspectiveProfiler");

    private final BindingStateExtractor bindingExtractor = new BindingStateExtractor();

    // Warning thresholds
    private static final int COMPONENT_COUNT_MEDIUM = 100;
    private static final int COMPONENT_COUNT_HIGH = 250;
    private static final int MAX_DEPTH_MEDIUM = 8;
    private static final int MAX_DEPTH_HIGH = 12;
    private static final int BINDING_COUNT_MEDIUM = 50;
    private static final int BINDING_COUNT_HIGH = 150;
    private static final int ERROR_BINDING_MEDIUM = 1;
    private static final int ERROR_BINDING_HIGH = 5;
    private static final int PENDING_BINDING_MEDIUM = 5;
    private static final int PENDING_BINDING_HIGH = 15;
    private static final long PROP_SIZE_MEDIUM = 10_000L;
    private static final long PROP_SIZE_HIGH = 50_000L;

    /** Profiles a live ViewModel and returns comprehensive metrics. */
    ViewProfileResult profileView(ViewModel view, String viewPath) {
        long startTime = System.currentTimeMillis();
        ViewProfileResult result = new ViewProfileResult();
        result.setViewPath(viewPath);
        result.setViewInstanceId(view.getId().toString());

        try {
            ComponentModel rootComponent = view.getRootContainer();
            if (rootComponent == null) {
                logger.warn("View has no root container: {}", viewPath);
                result.setProfilingDurationMs(System.currentTimeMillis() - startTime);
                return result;
            }

            // Walk the component tree
            ProfileAccumulator accumulator = new ProfileAccumulator();
            profileComponent(rootComponent, accumulator, 0);

            // Populate result from accumulator
            result.setTotalComponentCount(accumulator.componentCount);
            result.setMaxTreeDepth(accumulator.maxDepth);
            result.setTotalBindingCount(accumulator.totalBindingCount);
            result.setBindingsByType(accumulator.bindingsByType);
            result.setPendingBindingCount(accumulator.pendingBindingCount);
            result.setResolvedBindingCount(accumulator.resolvedBindingCount);
            result.setErrorBindingCount(accumulator.errorBindingCount);
            result.setTotalPropertySizeBytes(accumulator.totalPropertySizeBytes);
            result.setComponents(accumulator.components);

            // Generate warnings
            List<ProfileWarning> warnings = generateWarnings(accumulator);
            result.setWarnings(warnings);

        } catch (Exception e) {
            logger.error("Error profiling view: {}", viewPath, e);
        }

        result.setProfilingDurationMs(System.currentTimeMillis() - startTime);
        return result;
    }

    /** Recursively profiles a component and its children. */
    private void profileComponent(
            ComponentModel component, ProfileAccumulator accumulator, int depth) {
        accumulator.componentCount++;
        if (depth > accumulator.maxDepth) {
            accumulator.maxDepth = depth;
        }

        String path = getComponentPath(component);
        String type = component.getType();
        String name = component.getName();

        // Extract binding information via shared extractor
        List<BindingProfile> bindings = bindingExtractor.extractBindings(component);
        int bindingCount = bindings.size();
        accumulator.totalBindingCount += bindingCount;

        for (BindingProfile binding : bindings) {
            // Count by type
            String bindingType = binding.getBindingType();
            accumulator.bindingsByType.merge(bindingType, 1, Integer::sum);

            // Count by state
            String state = binding.getState();
            if ("good".equals(state)) {
                accumulator.resolvedBindingCount++;
            } else if ("pending".equals(state)) {
                accumulator.pendingBindingCount++;
            } else if ("bad".equals(state)) {
                accumulator.errorBindingCount++;
            }
        }

        // Measure property sizes
        long propsSizeBytes = measurePropertyTreeSize(component, "props");
        long customSizeBytes = measurePropertyTreeSize(component, "custom");
        accumulator.totalPropertySizeBytes += propsSizeBytes + customSizeBytes;

        // Count children
        Collection<? extends com.inductiveautomation.perspective.gateway.api.Component> children =
                component.getChildren();
        int childCount = children.size();

        // Create component profile
        ComponentProfile profile = new ComponentProfile();
        profile.setPath(path);
        profile.setType(type);
        profile.setName(name);
        profile.setBindingCount(bindingCount);
        profile.setBindings(bindings);
        profile.setPropsSizeBytes(propsSizeBytes);
        profile.setCustomSizeBytes(customSizeBytes);
        profile.setChildCount(childCount);
        accumulator.components.add(profile);

        // Recurse into children
        for (com.inductiveautomation.perspective.gateway.api.Component child : children) {
            if (child instanceof ComponentModel) {
                profileComponent((ComponentModel) child, accumulator, depth + 1);
            }
        }
    }

    /** Measures the approximate byte size of a PropertyTree. */
    private long measurePropertyTreeSize(ComponentModel component, String treeName) {
        try {
            PropertyTree tree = getPropertyTreeByName(component, treeName);
            if (tree == null) {
                return 0;
            }

            try {
                java.lang.reflect.Method toJson =
                        bindingExtractor.findMethod(tree.getClass(), "toJson", "toJsonObject");
                if (toJson != null) {
                    Object jsonObj = toJson.invoke(tree);
                    if (jsonObj != null) {
                        return jsonObj.toString().length();
                    }
                }
            } catch (Exception e) {
                // toJson not available
            }

            return estimatePropertyTreeSize(tree);

        } catch (Exception e) {
            logger.debug("Error measuring property tree size for {}: {}", treeName, e.getMessage());
            return 0;
        }
    }

    /** Estimates the size of a PropertyTree by walking its internal structure. */
    private long estimatePropertyTreeSize(PropertyTree tree) {
        try {
            java.lang.reflect.Field rootField = tree.getClass().getDeclaredField("root");
            rootField.setAccessible(true);
            Object rootNode = rootField.get(tree);
            if (rootNode == null) {
                return 0;
            }

            return estimateNodeSize(rootNode);
        } catch (Exception e) {
            return 0;
        }
    }

    /** Recursively estimates the size of a property tree node. */
    private long estimateNodeSize(Object node) {
        long size = 0;
        try {
            for (java.lang.reflect.Field field : node.getClass().getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(node);
                    if (value instanceof String) {
                        size += ((String) value).length() * 2L;
                    } else if (value instanceof Number) {
                        size += 8;
                    } else if (value instanceof Boolean) {
                        size += 1;
                    } else if (value instanceof Map) {
                        Map<?, ?> map = (Map<?, ?>) value;
                        for (Map.Entry<?, ?> entry : map.entrySet()) {
                            if (entry.getKey() instanceof String) {
                                size += ((String) entry.getKey()).length() * 2L;
                            }
                            if (entry.getValue() != null) {
                                size += estimateNodeSize(entry.getValue());
                            }
                        }
                    }
                } catch (Exception e) {
                    // Skip this field
                }
            }
        } catch (Exception e) {
            // Can't estimate
        }
        return size;
    }

    /** Gets a PropertyTree by name using reflection. */
    private PropertyTree getPropertyTreeByName(ComponentModel component, String typeName) {
        try {
            java.lang.reflect.Method getPropertyTreeOfMethod = null;
            Class<?> parameterType = null;

            for (java.lang.reflect.Method method : component.getClass().getMethods()) {
                if ("getPropertyTreeOf".equals(method.getName())
                        && method.getParameterCount() == 1) {
                    getPropertyTreeOfMethod = method;
                    parameterType = method.getParameterTypes()[0];
                    break;
                }
            }

            if (getPropertyTreeOfMethod != null
                    && parameterType != null
                    && parameterType.isEnum()) {
                Object[] enumConstants = parameterType.getEnumConstants();
                if (enumConstants != null) {
                    for (Object constant : enumConstants) {
                        if (constant.toString().equalsIgnoreCase(typeName)) {
                            Object result = getPropertyTreeOfMethod.invoke(component, constant);
                            if (result instanceof PropertyTree) {
                                return (PropertyTree) result;
                            }
                        }
                    }
                }
            }

            return null;
        } catch (Exception e) {
            logger.debug("Error getting property tree for {}: {}", typeName, e.getMessage());
            return null;
        }
    }

    /** Generates performance warnings from accumulated metrics. */
    private List<ProfileWarning> generateWarnings(ProfileAccumulator acc) {
        List<ProfileWarning> warnings = new ArrayList<>();

        if (acc.componentCount > COMPONENT_COUNT_HIGH) {
            warnings.add(
                    new ProfileWarning(
                            "high",
                            "structure",
                            String.format("Very high component count: %d", acc.componentCount),
                            null,
                            "Consider splitting this view into smaller embedded views or using virtualized containers"));
        } else if (acc.componentCount > COMPONENT_COUNT_MEDIUM) {
            warnings.add(
                    new ProfileWarning(
                            "medium",
                            "structure",
                            String.format("High component count: %d", acc.componentCount),
                            null,
                            "Monitor load times. Consider splitting complex sections into embedded views"));
        }

        if (acc.maxDepth > MAX_DEPTH_HIGH) {
            warnings.add(
                    new ProfileWarning(
                            "high",
                            "structure",
                            String.format("Very deep component tree: %d levels", acc.maxDepth),
                            null,
                            "Deeply nested trees cause slow rendering. Flatten the structure or use embedded views"));
        } else if (acc.maxDepth > MAX_DEPTH_MEDIUM) {
            warnings.add(
                    new ProfileWarning(
                            "medium",
                            "structure",
                            String.format("Deep component tree: %d levels", acc.maxDepth),
                            null,
                            "Consider reducing nesting depth for better performance"));
        }

        if (acc.totalBindingCount > BINDING_COUNT_HIGH) {
            warnings.add(
                    new ProfileWarning(
                            "high",
                            "binding",
                            String.format("Very high binding count: %d", acc.totalBindingCount),
                            null,
                            "Many bindings increase gateway load. Consolidate bindings or use expression bindings"));
        } else if (acc.totalBindingCount > BINDING_COUNT_MEDIUM) {
            warnings.add(
                    new ProfileWarning(
                            "medium",
                            "binding",
                            String.format("High binding count: %d", acc.totalBindingCount),
                            null,
                            "Monitor gateway performance. Consider consolidating similar bindings"));
        }

        if (acc.errorBindingCount > ERROR_BINDING_HIGH) {
            warnings.add(
                    new ProfileWarning(
                            "high",
                            "binding",
                            String.format("%d bindings in error state", acc.errorBindingCount),
                            null,
                            "Fix binding errors to improve performance and data accuracy"));
        } else if (acc.errorBindingCount > ERROR_BINDING_MEDIUM) {
            warnings.add(
                    new ProfileWarning(
                            "medium",
                            "binding",
                            String.format("%d binding(s) in error state", acc.errorBindingCount),
                            null,
                            "Check binding configurations for errors"));
        }

        if (acc.pendingBindingCount > PENDING_BINDING_HIGH) {
            warnings.add(
                    new ProfileWarning(
                            "high",
                            "binding",
                            String.format("%d bindings still pending", acc.pendingBindingCount),
                            null,
                            "Many pending bindings suggest slow data sources or excessive polling. Check tag paths and query performance"));
        } else if (acc.pendingBindingCount > PENDING_BINDING_MEDIUM) {
            warnings.add(
                    new ProfileWarning(
                            "medium",
                            "binding",
                            String.format("%d bindings still pending", acc.pendingBindingCount),
                            null,
                            "Some bindings are slow to resolve. Check data source performance"));
        }

        for (ComponentProfile comp : acc.components) {
            long totalSize = comp.getPropsSizeBytes() + comp.getCustomSizeBytes();
            if (totalSize > PROP_SIZE_HIGH) {
                warnings.add(
                        new ProfileWarning(
                                "high",
                                "data",
                                String.format(
                                        "Large property data on '%s': %s",
                                        comp.getName(), formatBytes(totalSize)),
                                comp.getPath(),
                                "Reduce data stored in component properties. Use tag bindings or named queries instead of large static data"));
            } else if (totalSize > PROP_SIZE_MEDIUM) {
                warnings.add(
                        new ProfileWarning(
                                "medium",
                                "data",
                                String.format(
                                        "Moderate property data on '%s': %s",
                                        comp.getName(), formatBytes(totalSize)),
                                comp.getPath(),
                                "Consider moving large data to external sources"));
            }
        }

        return warnings;
    }

    // ==================== Utility Methods ====================

    private String getComponentPath(ComponentModel component) {
        try {
            return component.getQualifiedPath();
        } catch (Exception e) {
            return component.getName();
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    /** Internal accumulator for collecting metrics during the tree walk. */
    private static class ProfileAccumulator {
        int componentCount = 0;
        int maxDepth = 0;
        int totalBindingCount = 0;
        Map<String, Integer> bindingsByType = new HashMap<>();
        int pendingBindingCount = 0;
        int resolvedBindingCount = 0;
        int errorBindingCount = 0;
        long totalPropertySizeBytes = 0;
        List<ComponentProfile> components = new ArrayList<>();
    }
}
