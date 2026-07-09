package dev.bwdesigngroup.flint.gateway.perspective;

import com.inductiveautomation.perspective.gateway.model.ComponentModel;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.BindingProfile;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared reflection-based utilities for extracting binding state information from Perspective
 * ComponentModel instances.
 *
 * <p>The runtime binding objects are {@code ElementBindingHarness} instances stored in a {@code
 * BindingCollection} on each component. Key fields/methods:
 *
 * <ul>
 *   <li>{@code typeCode} (String) - binding type ("expr", "tag", "property", etc.)
 *   <li>{@code getLastValue()} - returns QualifiedValue with quality for state
 *   <li>{@code getTargetProperty()} - returns PropertyKey for the bound property path
 *   <li>{@code transforms} (Transform[]) - array of transforms
 * </ul>
 *
 * Used by both {@link PerspectiveProfiler} (point-in-time snapshots) and {@link BindingRecorder}
 * (temporal state tracking).
 */
class BindingStateExtractor {
    private static final Logger logger =
            LoggerFactory.getLogger("flint.Gateway.BindingStateExtractor");

    // ==================== Reflection Cache ====================

    /**
     * Cached reflection metadata for a component class. Since class structures don't change at
     * runtime, we discover fields/methods once and reuse them.
     */
    private static class ComponentReflection {
        final java.lang.reflect.Field bindingsField;

        ComponentReflection(java.lang.reflect.Field bindingsField) {
            this.bindingsField = bindingsField;
        }
    }

    /**
     * Cached reflection metadata for a BindingCollection class, including which extraction strategy
     * succeeded so we can skip the others.
     */
    private static class CollectionReflection {
        /** Which strategy (1-4) worked, or 0 if none worked yet */
        final int successfulStrategy;
        /** For strategy 1: which method name worked */
        final String successfulMethodName;
        /** For strategy 3: which field worked */
        final java.lang.reflect.Field successfulField;

        CollectionReflection(int strategy, String methodName, java.lang.reflect.Field field) {
            this.successfulStrategy = strategy;
            this.successfulMethodName = methodName;
            this.successfulField = field;
        }
    }

    /** Cached reflection metadata for a harness class (ElementBindingHarness). */
    private static class HarnessReflection {
        final java.lang.reflect.Method getTargetProperty;
        final java.lang.reflect.Method getLastValue;
        final java.lang.reflect.Field lastValueField;
        final java.lang.reflect.Field typeCodeField;
        final java.lang.reflect.Field transformsField;

        HarnessReflection(
                java.lang.reflect.Method getTargetProperty,
                java.lang.reflect.Method getLastValue,
                java.lang.reflect.Field lastValueField,
                java.lang.reflect.Field typeCodeField,
                java.lang.reflect.Field transformsField) {
            this.getTargetProperty = getTargetProperty;
            this.getLastValue = getLastValue;
            this.lastValueField = lastValueField;
            this.typeCodeField = typeCodeField;
            this.transformsField = transformsField;
        }
    }

    /** Cached reflection metadata for a QualifiedValue class. */
    private static class QualityReflection {
        final java.lang.reflect.Method getQuality;
        final java.lang.reflect.Method getValue;

        QualityReflection(java.lang.reflect.Method getQuality, java.lang.reflect.Method getValue) {
            this.getQuality = getQuality;
            this.getValue = getValue;
        }
    }

    /** Cached reflection metadata for a QualityCode class. */
    private static class QualityCodeReflection {
        final java.lang.reflect.Method isGood;
        final java.lang.reflect.Method isBad;
        final java.lang.reflect.Method isUncertain;
        final java.lang.reflect.Method getLevel;

        QualityCodeReflection(
                java.lang.reflect.Method isGood,
                java.lang.reflect.Method isBad,
                java.lang.reflect.Method isUncertain,
                java.lang.reflect.Method getLevel) {
            this.isGood = isGood;
            this.isBad = isBad;
            this.isUncertain = isUncertain;
            this.getLevel = getLevel;
        }
    }

    private final Map<Class<?>, ComponentReflection> componentCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, CollectionReflection> collectionCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, HarnessReflection> harnessCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, QualityReflection> qualityValueCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, QualityCodeReflection> qualityCodeCache = new ConcurrentHashMap<>();

    /**
     * Extracts binding profiles from a ComponentModel using reflection. Uses cached reflection
     * metadata to avoid repeated discovery after first call.
     */
    List<BindingProfile> extractBindings(ComponentModel component) {
        List<BindingProfile> bindings = new ArrayList<>();

        try {
            Class<?> componentClass = component.getClass();
            ComponentReflection cached = componentCache.get(componentClass);
            if (cached == null) {
                java.lang.reflect.Field bindingsField = findField(componentClass, "bindings");
                if (bindingsField != null) {
                    bindingsField.setAccessible(true);
                }
                cached = new ComponentReflection(bindingsField);
                componentCache.put(componentClass, cached);
            }

            if (cached.bindingsField != null) {
                Object bindingsObj = cached.bindingsField.get(component);
                if (bindingsObj != null) {
                    List<Object> harnesses = extractHarnessesFromCollection(bindingsObj);
                    for (Object harness : harnesses) {
                        BindingProfile profile = createProfileFromHarness(harness);
                        if (profile != null) {
                            bindings.add(profile);
                        }
                    }
                    if (!bindings.isEmpty()) {
                        return bindings;
                    }
                }
            }

        } catch (Exception e) {
            logger.debug(
                    "Error extracting bindings from component {}: {}",
                    component.getName(),
                    e.getMessage());
        }

        return bindings;
    }

    /**
     * Extracts ElementBindingHarness objects from a BindingCollection using reflection.
     * BindingCollection is an internal Perspective class that may not implement standard collection
     * interfaces. We try multiple strategies to find the harness objects. After the first
     * successful extraction, the winning strategy is cached.
     */
    private List<Object> extractHarnessesFromCollection(Object bindingsObj) {
        List<Object> harnesses = new ArrayList<>();
        Class<?> clazz = bindingsObj.getClass();

        // Check cache for previously successful strategy
        CollectionReflection cached = collectionCache.get(clazz);
        if (cached != null && cached.successfulStrategy > 0) {
            return extractWithCachedStrategy(bindingsObj, cached);
        }

        // Strategy 1: Try common collection/iterable methods
        for (String methodName :
                new String[] {"values", "getAll", "getBindings", "toList", "asList"}) {
            try {
                java.lang.reflect.Method method = findMethod(clazz, methodName);
                if (method != null) {
                    Object result = method.invoke(bindingsObj);
                    if (result instanceof Collection) {
                        harnesses.addAll((Collection<?>) result);
                        if (!harnesses.isEmpty()) {
                            collectionCache.put(
                                    clazz, new CollectionReflection(1, methodName, null));
                            return harnesses;
                        }
                    } else if (result instanceof Iterable) {
                        for (Object item : (Iterable<?>) result) {
                            harnesses.add(item);
                        }
                        if (!harnesses.isEmpty()) {
                            collectionCache.put(
                                    clazz, new CollectionReflection(1, methodName, null));
                            return harnesses;
                        }
                    }
                }
            } catch (Exception e) {
                // Try next method
            }
        }

        // Strategy 2: If BindingCollection itself is Iterable, try iterator
        if (bindingsObj instanceof Iterable) {
            for (Object item : (Iterable<?>) bindingsObj) {
                harnesses.add(item);
            }
            if (!harnesses.isEmpty()) {
                collectionCache.put(clazz, new CollectionReflection(2, null, null));
                return harnesses;
            }
        }

        // Strategy 3: Look for internal Map or Collection fields that hold the harnesses
        for (java.lang.reflect.Field field : getAllFields(clazz)) {
            try {
                field.setAccessible(true);
                Object fieldValue = field.get(bindingsObj);
                if (fieldValue == null) {
                    continue;
                }

                if (fieldValue instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) fieldValue;
                    for (Object value : map.values()) {
                        if (value != null && isBindingHarness(value)) {
                            harnesses.add(value);
                        }
                    }
                    if (!harnesses.isEmpty()) {
                        collectionCache.put(clazz, new CollectionReflection(3, null, field));
                        return harnesses;
                    }
                } else if (fieldValue instanceof Collection) {
                    for (Object item : (Collection<?>) fieldValue) {
                        if (item != null && isBindingHarness(item)) {
                            harnesses.add(item);
                        }
                    }
                    if (!harnesses.isEmpty()) {
                        collectionCache.put(clazz, new CollectionReflection(3, null, field));
                        return harnesses;
                    }
                } else if (fieldValue.getClass().isArray()) {
                    int len = Array.getLength(fieldValue);
                    for (int i = 0; i < len; i++) {
                        Object item = Array.get(fieldValue, i);
                        if (item != null && isBindingHarness(item)) {
                            harnesses.add(item);
                        }
                    }
                    if (!harnesses.isEmpty()) {
                        collectionCache.put(clazz, new CollectionReflection(3, null, field));
                        return harnesses;
                    }
                }
            } catch (Exception e) {
                // Try next field
            }
        }

        // Strategy 4: forEach method (common in custom collections)
        // Can't use directly, but try stream().collect()
        try {
            java.lang.reflect.Method stream = findMethod(clazz, "stream");
            if (stream != null) {
                Object streamObj = stream.invoke(bindingsObj);
                if (streamObj != null) {
                    // stream.toArray()
                    java.lang.reflect.Method toArray = findMethod(streamObj.getClass(), "toArray");
                    if (toArray != null) {
                        Object arr = toArray.invoke(streamObj);
                        if (arr != null && arr.getClass().isArray()) {
                            for (int i = 0; i < Array.getLength(arr); i++) {
                                harnesses.add(Array.get(arr, i));
                            }
                            if (!harnesses.isEmpty()) {
                                collectionCache.put(clazz, new CollectionReflection(4, null, null));
                                return harnesses;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Stream not available
        }

        logger.info(
                "Could not extract harnesses from BindingCollection (class={}). Fields: {}",
                clazz.getName(),
                describeFields(clazz));

        return harnesses;
    }

    /** Fast path: re-extracts harnesses using a previously successful strategy. */
    private List<Object> extractWithCachedStrategy(
            Object bindingsObj, CollectionReflection cached) {
        List<Object> harnesses = new ArrayList<>();
        try {
            switch (cached.successfulStrategy) {
                case 1:
                    {
                        java.lang.reflect.Method method =
                                findMethod(bindingsObj.getClass(), cached.successfulMethodName);
                        if (method != null) {
                            Object result = method.invoke(bindingsObj);
                            if (result instanceof Collection) {
                                harnesses.addAll((Collection<?>) result);
                            } else if (result instanceof Iterable) {
                                for (Object item : (Iterable<?>) result) {
                                    harnesses.add(item);
                                }
                            }
                        }
                        break;
                    }
                case 2:
                    {
                        if (bindingsObj instanceof Iterable) {
                            for (Object item : (Iterable<?>) bindingsObj) {
                                harnesses.add(item);
                            }
                        }
                        break;
                    }
                case 3:
                    {
                        if (cached.successfulField != null) {
                            Object fieldValue = cached.successfulField.get(bindingsObj);
                            if (fieldValue instanceof Map) {
                                harnesses.addAll(((Map<?, ?>) fieldValue).values());
                            } else if (fieldValue instanceof Collection) {
                                harnesses.addAll((Collection<?>) fieldValue);
                            } else if (fieldValue != null && fieldValue.getClass().isArray()) {
                                for (int i = 0; i < Array.getLength(fieldValue); i++) {
                                    harnesses.add(Array.get(fieldValue, i));
                                }
                            }
                        }
                        break;
                    }
                case 4:
                    {
                        java.lang.reflect.Method stream =
                                findMethod(bindingsObj.getClass(), "stream");
                        if (stream != null) {
                            Object streamObj = stream.invoke(bindingsObj);
                            if (streamObj != null) {
                                java.lang.reflect.Method toArray =
                                        findMethod(streamObj.getClass(), "toArray");
                                if (toArray != null) {
                                    Object arr = toArray.invoke(streamObj);
                                    if (arr != null && arr.getClass().isArray()) {
                                        for (int i = 0; i < Array.getLength(arr); i++) {
                                            harnesses.add(Array.get(arr, i));
                                        }
                                    }
                                }
                            }
                        }
                        break;
                    }
                default:
                    break;
            }
        } catch (Exception e) {
            logger.debug(
                    "Cached strategy {} failed, will rediscover: {}",
                    cached.successfulStrategy,
                    e.getMessage());
            collectionCache.remove(bindingsObj.getClass());
        }
        return harnesses;
    }

    /** Checks if an object looks like a binding harness (ElementBindingHarness or similar). */
    private boolean isBindingHarness(Object obj) {
        String className = obj.getClass().getName().toLowerCase();
        if (className.contains("harness") || className.contains("binding")) {
            return true;
        }
        // Also check for characteristic fields/methods
        return findField(obj.getClass(), "typeCode") != null
                || findMethod(obj.getClass(), "getTargetProperty") != null;
    }

    /** Gets all declared fields from a class and its superclasses. */
    private List<java.lang.reflect.Field> getAllFields(Class<?> clazz) {
        List<java.lang.reflect.Field> fields = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (java.lang.reflect.Field f : current.getDeclaredFields()) {
                fields.add(f);
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    /** Describes the fields on a class for diagnostic logging. */
    private String describeFields(Class<?> clazz) {
        StringBuilder sb = new StringBuilder();
        for (java.lang.reflect.Field f : getAllFields(clazz)) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(f.getName()).append(":").append(f.getType().getSimpleName());
        }
        return sb.toString();
    }

    /** Gets or creates cached reflection metadata for a harness class. */
    private HarnessReflection getHarnessReflection(Class<?> harnessClass) {
        return harnessCache.computeIfAbsent(
                harnessClass,
                clazz -> {
                    java.lang.reflect.Method getTargetProperty =
                            findMethod(clazz, "getTargetProperty");
                    java.lang.reflect.Method getLastValue = findMethod(clazz, "getLastValue");
                    java.lang.reflect.Field lastValueField = findField(clazz, "lastValue");
                    if (lastValueField != null) lastValueField.setAccessible(true);
                    java.lang.reflect.Field typeCodeField = findField(clazz, "typeCode");
                    if (typeCodeField != null) typeCodeField.setAccessible(true);
                    java.lang.reflect.Field transformsField = findField(clazz, "transforms");
                    if (transformsField != null) transformsField.setAccessible(true);
                    return new HarnessReflection(
                            getTargetProperty,
                            getLastValue,
                            lastValueField,
                            typeCodeField,
                            transformsField);
                });
    }

    /** Gets or creates cached reflection metadata for a QualifiedValue class. */
    private QualityReflection getQualityReflection(Class<?> qualifiedValueClass) {
        return qualityValueCache.computeIfAbsent(
                qualifiedValueClass,
                clazz -> {
                    java.lang.reflect.Method getQuality = findMethod(clazz, "getQuality");
                    java.lang.reflect.Method getValue = findMethod(clazz, "getValue");
                    return new QualityReflection(getQuality, getValue);
                });
    }

    /** Gets or creates cached reflection metadata for a QualityCode class. */
    private QualityCodeReflection getQualityCodeReflection(Class<?> qualityCodeClass) {
        return qualityCodeCache.computeIfAbsent(
                qualityCodeClass,
                clazz ->
                        new QualityCodeReflection(
                                findMethod(clazz, "isGood"),
                                findMethod(clazz, "isBad"),
                                findMethod(clazz, "isUncertain"),
                                findMethod(clazz, "getLevel")));
    }

    /**
     * Creates a BindingProfile from an ElementBindingHarness object using reflection. Uses cached
     * reflection metadata for hot-path performance.
     */
    private BindingProfile createProfileFromHarness(Object harness) {
        if (harness == null) {
            return null;
        }

        HarnessReflection hr = getHarnessReflection(harness.getClass());
        BindingProfile profile = new BindingProfile();

        // Extract property path from getTargetProperty() → PropertyKey → toString()
        String propertyPath = extractPropertyPath(harness, hr);
        profile.setPropertyPath(propertyPath != null ? propertyPath : "unknown");

        // Extract binding type from 'typeCode' field
        String typeCode = extractStringFieldCached(harness, hr.typeCodeField);
        profile.setBindingType(typeCode != null ? typeCode : "unknown");

        // Extract state from getLastValue() → QualifiedValue → quality
        String state = extractBindingState(harness, hr);
        profile.setState(state);

        // Extract value hash from getLastValue().getValue() for change detection
        String valueHash = extractValueHash(harness, hr);
        profile.setValueHash(valueHash);

        // Extract transform count from 'transforms' field (Transform[] array)
        int transformCount = extractTransformCount(harness, hr);
        profile.setTransformCount(transformCount);
        profile.setHasScriptTransform(checkHasScriptTransform(harness, hr));

        // Extract error from last value if state is bad
        if ("bad".equals(state)) {
            String error = extractLastError(harness, hr);
            profile.setLastError(error);
        }

        return profile;
    }

    /** Extracts the property path from an ElementBindingHarness using cached reflection. */
    private String extractPropertyPath(Object harness, HarnessReflection hr) {
        try {
            if (hr.getTargetProperty != null) {
                Object propertyKey = hr.getTargetProperty.invoke(harness);
                if (propertyKey != null) {
                    return propertyKey.toString();
                }
            }
        } catch (Exception e) {
            logger.debug("Could not extract property path: {}", e.getMessage());
        }
        return null;
    }

    /** Extracts a string value from a pre-cached field. */
    private String extractStringFieldCached(Object obj, java.lang.reflect.Field field) {
        if (field == null) return null;
        try {
            Object result = field.get(obj);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Gets the last value from a harness using cached reflection. */
    private Object getLastValue(Object harness, HarnessReflection hr) throws Exception {
        Object lastValue = null;
        if (hr.getLastValue != null) {
            lastValue = hr.getLastValue.invoke(harness);
        }
        if (lastValue == null && hr.lastValueField != null) {
            lastValue = hr.lastValueField.get(harness);
        }
        return lastValue;
    }

    /**
     * Extracts the binding state from an ElementBindingHarness using cached reflection. Returns a
     * normalized state string: "good", "pending", "bad", "stale", or "unknown".
     */
    String extractBindingState(Object harness) {
        HarnessReflection hr = getHarnessReflection(harness.getClass());
        return extractBindingState(harness, hr);
    }

    private String extractBindingState(Object harness, HarnessReflection hr) {
        try {
            Object lastValue = getLastValue(harness, hr);

            if (lastValue != null) {
                QualityReflection qr = getQualityReflection(lastValue.getClass());
                if (qr.getQuality != null) {
                    Object quality = qr.getQuality.invoke(lastValue);
                    if (quality != null) {
                        QualityCodeReflection qcr = getQualityCodeReflection(quality.getClass());

                        // Primary: use getLevel() which returns the Level enum directly
                        if (qcr.getLevel != null) {
                            Object level = qcr.getLevel.invoke(quality);
                            if (level != null) {
                                String levelStr = level.toString().toLowerCase();
                                if ("good".equals(levelStr)) return "good";
                                if ("bad".equals(levelStr)) return "bad";
                                if ("uncertain".equals(levelStr)) return "stale";
                            }
                        }

                        // Fallback: boolean methods
                        if (qcr.isGood != null) {
                            Boolean good = (Boolean) qcr.isGood.invoke(quality);
                            if (Boolean.TRUE.equals(good)) {
                                return "good";
                            }
                        }

                        if (qcr.isBad != null) {
                            Boolean bad = (Boolean) qcr.isBad.invoke(quality);
                            if (Boolean.TRUE.equals(bad)) {
                                return "bad";
                            }
                        }

                        if (qcr.isUncertain != null) {
                            Boolean uncertain = (Boolean) qcr.isUncertain.invoke(quality);
                            if (Boolean.TRUE.equals(uncertain)) {
                                return "stale";
                            }
                        }

                        // Fallback: try toString
                        String qualityStr = quality.toString().toLowerCase();
                        if (qualityStr.contains("good")) return "good";
                        if (qualityStr.contains("bad") || qualityStr.contains("error"))
                            return "bad";
                        if (qualityStr.contains("pending")) return "pending";
                        if (qualityStr.contains("stale") || qualityStr.contains("uncertain"))
                            return "stale";
                    }
                }
            }

            // If no lastValue, binding hasn't resolved yet
            java.lang.reflect.Field shutdownField = findField(harness.getClass(), "shutdown");
            if (shutdownField != null) {
                shutdownField.setAccessible(true);
                if (!shutdownField.getBoolean(harness)) {
                    return "pending";
                }
            }

        } catch (Exception e) {
            logger.debug("Could not extract binding state: {}", e.getMessage());
        }

        return "unknown";
    }

    /** Extracts a hash of the current value from a binding harness using cached reflection. */
    private String extractValueHash(Object harness, HarnessReflection hr) {
        try {
            Object lastValue = getLastValue(harness, hr);
            if (lastValue != null) {
                QualityReflection qr = getQualityReflection(lastValue.getClass());
                if (qr.getValue != null) {
                    Object value = qr.getValue.invoke(lastValue);
                    if (value != null) {
                        return String.valueOf(value.hashCode());
                    }
                }
            }
        } catch (Exception e) {
            // Value extraction failed
        }
        return null;
    }

    /** Extracts the error message from the last value of a binding using cached reflection. */
    private String extractLastError(Object harness, HarnessReflection hr) {
        try {
            Object lastValue = getLastValue(harness, hr);
            if (lastValue != null) {
                QualityReflection qr = getQualityReflection(lastValue.getClass());
                if (qr.getQuality != null) {
                    Object quality = qr.getQuality.invoke(lastValue);
                    if (quality != null) {
                        java.lang.reflect.Method getDiag =
                                findMethod(
                                        quality.getClass(), "getDiagnosticMessage", "getMessage");
                        if (getDiag != null) {
                            Object msg = getDiag.invoke(quality);
                            if (msg != null) {
                                return msg.toString();
                            }
                        }
                        return quality.toString();
                    }
                }
            }
        } catch (Exception e) {
            // Error extraction failed
        }
        return null;
    }

    /** Extracts the transform count using cached reflection. */
    private int extractTransformCount(Object harness, HarnessReflection hr) {
        try {
            if (hr.transformsField != null) {
                Object transforms = hr.transformsField.get(harness);
                if (transforms != null) {
                    if (transforms.getClass().isArray()) {
                        return Array.getLength(transforms);
                    } else if (transforms instanceof Collection) {
                        return ((Collection<?>) transforms).size();
                    }
                }
            }
        } catch (Exception e) {
            // Transform count not available
        }
        return 0;
    }

    /** Checks if any transform is a script transform using cached reflection. */
    private boolean checkHasScriptTransform(Object harness, HarnessReflection hr) {
        try {
            if (hr.transformsField != null) {
                Object transforms = hr.transformsField.get(harness);
                if (transforms != null) {
                    Iterable<?> items;
                    if (transforms.getClass().isArray()) {
                        List<Object> list = new ArrayList<>();
                        for (int i = 0; i < Array.getLength(transforms); i++) {
                            list.add(Array.get(transforms, i));
                        }
                        items = list;
                    } else if (transforms instanceof Iterable) {
                        items = (Iterable<?>) transforms;
                    } else {
                        return false;
                    }

                    for (Object transform : items) {
                        String type = extractStringField(transform, "type", "typeCode");
                        if ("script".equals(type)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Script transform check not available
        }
        return false;
    }

    /** Extracts a string value from a field on an object. */
    private String extractStringField(Object obj, String... fieldNames) {
        for (String name : fieldNames) {
            try {
                java.lang.reflect.Field field = findField(obj.getClass(), name);
                if (field != null) {
                    field.setAccessible(true);
                    Object result = field.get(obj);
                    if (result != null) {
                        return result.toString();
                    }
                }
            } catch (Exception e) {
                // Try next
            }
        }
        return null;
    }

    /**
     * Extracts a string property from an object using reflection, trying multiple method/field
     * names.
     */
    String extractStringProperty(Object obj, String... methodOrFieldNames) {
        for (String name : methodOrFieldNames) {
            // Try as method
            try {
                java.lang.reflect.Method method = obj.getClass().getMethod(name);
                Object result = method.invoke(obj);
                if (result != null) {
                    return result.toString();
                }
            } catch (Exception e) {
                // Try next
            }

            // Try as field
            try {
                java.lang.reflect.Field field = findField(obj.getClass(), name);
                if (field != null) {
                    field.setAccessible(true);
                    Object result = field.get(obj);
                    if (result != null) {
                        return result.toString();
                    }
                }
            } catch (Exception e) {
                // Try next
            }
        }
        return null;
    }

    /** Finds a field by name, searching up the class hierarchy. */
    java.lang.reflect.Field findField(Class<?> clazz, String... names) {
        for (String name : names) {
            Class<?> current = clazz;
            while (current != null && current != Object.class) {
                try {
                    return current.getDeclaredField(name);
                } catch (NoSuchFieldException e) {
                    current = current.getSuperclass();
                }
            }
        }
        return null;
    }

    /** Finds a zero-argument method by name. */
    java.lang.reflect.Method findMethod(Class<?> clazz, String... names) {
        for (String name : names) {
            try {
                for (java.lang.reflect.Method method : clazz.getMethods()) {
                    if (method.getName().equals(name) && method.getParameterCount() == 0) {
                        return method;
                    }
                }
            } catch (Exception e) {
                // Try next name
            }
        }
        return null;
    }
}
