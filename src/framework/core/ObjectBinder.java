package framework.core;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.Set;

public final class ObjectBinder {
    private ObjectBinder() {
    }

    public static boolean isBindableObject(Class<?> type) {
        Package pkg = type.getPackage();
        String packageName = pkg == null ? "" : pkg.getName();
        return !type.isInterface() && !Modifier.isAbstract(type.getModifiers())
                && !type.isEnum() && !type.isArray() && !type.isAnnotation()
                && !type.isPrimitive() && !type.isRecord()
        && type != UploadedFile.class
                && !packageName.startsWith("java.")
                && !packageName.startsWith("javax.")
                && !packageName.startsWith("jakarta.")
                && !packageName.startsWith("org.springframework.");
    }

    public static Object bind(Parameter parameter, Set<String> requestParameterNames,
                              HttpServletRequest req) throws ParameterBindingException {
        return bind(parameter, requestParameterNames, req, java.util.Collections.emptyMap());
    }

    public static Object bind(Parameter parameter, Set<String> requestParameterNames,
                              HttpServletRequest req, Map<String, UploadedFile> uploadedFiles)
            throws ParameterBindingException {
        Class<?> type = parameter.getType();
        final Object instance;
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            instance = constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Impossible d'instancier " + type.getName()
                    + " : constructeur sans argument requis.", e);
        }

        for (Class<?> current = type; current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) {
                    continue;
                }
                String name = field.getName();
                if (field.getType() == UploadedFile.class || field.getType() == byte[].class) {
                    if (uploadedFiles.containsKey(name)) {
                        Object fileValue = field.getType() == UploadedFile.class
                                ? uploadedFiles.get(name) : uploadedFiles.get(name).getBytes();
                        setField(instance, field, fileValue, name);
                    }
                    continue;
                }
                if (!ParameterBinder.isSimpleType(field.getType())) {
                    continue;
                }
                if (!requestParameterNames.contains(name)) {
                    continue;
                }
                String raw = req.getParameter(name);
                if (raw == null || (!field.getType().equals(String.class) && raw.isEmpty())) {
                    continue;
                }
                Object value;
                try {
                    value = ParameterBinder.convert(type.getSimpleName() + "." + name,
                            raw, field.getType());
                } catch (ParameterBindingException e) {
                    throw e;
                }
                setField(instance, field, value, name);
            }
        }
        return instance;
    }

    public static boolean hasFileFields(Class<?> type) {
        for (Class<?> current = type; current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (!Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers)
                        && (field.getType() == UploadedFile.class || field.getType() == byte[].class)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void setField(Object instance, Field field, Object value, String name) {
        try {
            java.lang.reflect.Method setter = findSetter(instance.getClass(), name, field.getType());
            if (setter != null) {
                setter.invoke(instance, value);
            } else {
                field.setAccessible(true);
                field.set(instance, value);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Impossible de remplir "
                    + instance.getClass().getSimpleName() + "." + name + ".", e);
        }
    }

    private static java.lang.reflect.Method findSetter(Class<?> type, String fieldName, Class<?> fieldType) {
        String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        try {
            return type.getMethod(setterName, fieldType);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
