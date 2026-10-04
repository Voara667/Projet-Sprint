package framework.core;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.time.temporal.Temporal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.TreeMap;

public final class JsonSerializer {
    private static final int MAX_DEPTH = 64;

    private JsonSerializer() {
    }

    public static String toJson(Object o) {
        return serialize(o, new IdentityHashMap<>(), 0);
    }

    private static String serialize(Object value, IdentityHashMap<Object, Boolean> path, int depth) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String || value instanceof Character) {
            return quote(String.valueOf(value));
        }
        if (value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Number) {
            if (value instanceof Double && !Double.isFinite((Double) value)
                    || value instanceof Float && !Float.isFinite((Float) value)) {
                return "null";
            }
            return value.toString();
        }
        if (value instanceof Enum<?>) {
            return quote(((Enum<?>) value).name());
        }
        if (value instanceof Date) {
            return quote(((Date) value).toInstant().toString());
        }
        if (value instanceof Temporal || value.getClass().getPackageName().equals("java.time")) {
            return quote(value.toString());
        }
        if (depth >= MAX_DEPTH) {
            throw circularReference(value);
        }
        enter(value, path);
        try {
            if (value instanceof Map<?, ?>) {
                return serializeMap((Map<?, ?>) value, path, depth + 1);
            }
            if (value instanceof Iterable<?>) {
                return serializeIterable((Iterable<?>) value, path, depth + 1);
            }
            if (value.getClass().isArray()) {
                return serializeArray(value, path, depth + 1);
            }
            if (value.getClass().isRecord()) {
                return serializeRecord(value, path, depth + 1);
            }
            return serializePojo(value, path, depth + 1);
        } finally {
            path.remove(value);
        }
    }

    private static String serializeMap(Map<?, ?> map, IdentityHashMap<Object, Boolean> path, int depth) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append(quote(String.valueOf(entry.getKey())));
            json.append(':').append(serialize(entry.getValue(), path, depth));
        }
        return json.append('}').toString();
    }

    private static String serializeIterable(Iterable<?> values, IdentityHashMap<Object, Boolean> path,
                                            int depth) {
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (Object value : values) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append(serialize(value, path, depth));
        }
        return json.append(']').toString();
    }

    private static String serializeArray(Object array, IdentityHashMap<Object, Boolean> path, int depth) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < Array.getLength(array); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(serialize(Array.get(array, i), path, depth));
        }
        return json.append(']').toString();
    }

    private static String serializeRecord(Object value, IdentityHashMap<Object, Boolean> path, int depth) {
        StringBuilder json = new StringBuilder("{");
        RecordComponent[] components = value.getClass().getRecordComponents().clone();
        Arrays.sort(components, Comparator.comparing(RecordComponent::getName));
        for (int i = 0; i < components.length; i++) {
            if (i > 0) {
                json.append(',');
            }
            RecordComponent component = components[i];
            json.append(quote(component.getName())).append(':')
                    .append(invoke(component.getAccessor(), value, path, depth));
        }
        return json.append('}').toString();
    }

    private static String serializePojo(Object value, IdentityHashMap<Object, Boolean> path, int depth) {
        Map<String, Method> getters = new TreeMap<>();
        for (Method method : value.getClass().getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0
                    || method.getName().equals("getClass")) {
                continue;
            }
            String property = propertyName(method);
            if (property != null && (method.getName().startsWith("get")
                    || method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
                getters.put(property, method);
            }
        }
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Method> getter : getters.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append(quote(getter.getKey())).append(':')
                    .append(invoke(getter.getValue(), value, path, depth));
        }
        return json.append('}').toString();
    }

    private static String propertyName(Method method) {
        String name = method.getName();
        if (name.startsWith("get") && name.length() > 3) {
            return decapitalize(name.substring(3));
        }
        if (name.startsWith("is") && name.length() > 2
                && (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
            return decapitalize(name.substring(2));
        }
        return null;
    }

    private static String invoke(Method method, Object target, IdentityHashMap<Object, Boolean> path,
                                 int depth) {
        try {
            if (!Modifier.isPublic(target.getClass().getModifiers())) {
                method.setAccessible(true);
            }
            return serialize(method.invoke(target), path, depth);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Getter inaccessible : " + method.getName(), e);
        }
    }

    private static String decapitalize(String name) {
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private static void enter(Object value, IdentityHashMap<Object, Boolean> path) {
        if (path.put(value, Boolean.TRUE) != null) {
            throw circularReference(value);
        }
    }

    private static IllegalStateException circularReference(Object value) {
        return new IllegalStateException("Référence circulaire détectée : " + value.getClass().getName());
    }

    public static String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder escaped = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': escaped.append("\\\"");
                    break;
                case '\\': escaped.append("\\\\");
                    break;
                case '\n': escaped.append("\\n");
                    break;
                case '\r': escaped.append("\\r");
                    break;
                case '\t': escaped.append("\\t");
                    break;
                case '\b': escaped.append("\\b");
                    break;
                case '\f': escaped.append("\\f");
                    break;
                default:
                    if (c < 0x20 || c == '\u2028' || c == '\u2029') {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
            }
        }
        return escaped.append('"').toString();
    }
}
