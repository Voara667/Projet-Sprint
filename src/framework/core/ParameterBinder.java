package framework.core;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;

public final class ParameterBinder {
    private ParameterBinder() {
    }

    public static boolean isSimpleType(Class<?> type) {
        return type == String.class
                || type == byte.class || type == Byte.class
                || type == short.class || type == Short.class
                || type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == float.class || type == Float.class
                || type == double.class || type == Double.class
                || type == boolean.class || type == Boolean.class
                || type == char.class || type == Character.class
                || type == BigDecimal.class || type == BigInteger.class
                || type == LocalDate.class;
    }

    public static Object bind(Parameter parameter, Set<String> requestParameterNames,
                              HttpServletRequest req) throws ParameterBindingException {
        if (!parameter.isNamePresent()) {
            throw new IllegalArgumentException("Nom de paramètre indisponible pour "
                    + parameter.getType().getName()
                    + "; compiler les controllers avec javac -parameters.");
        }
        String name = parameter.getName();
        String raw = requestParameterNames.contains(name) ? req.getParameter(name) : null;
        Class<?> type = parameter.getType();
        if (type == String.class) {
            return raw;
        }
        if (raw == null || raw.isEmpty()) {
            return primitiveDefault(type);
        }
        String value = raw.trim();
        try {
            if (type == byte.class || type == Byte.class) return Byte.valueOf(value);
            if (type == short.class || type == Short.class) return Short.valueOf(value);
            if (type == int.class || type == Integer.class) return Integer.valueOf(value);
            if (type == long.class || type == Long.class) return Long.valueOf(value);
            if (type == float.class || type == Float.class) return Float.valueOf(value);
            if (type == double.class || type == Double.class) return Double.valueOf(value);
            if (type == boolean.class || type == Boolean.class) return parseBoolean(value);
            if (type == char.class || type == Character.class) {
                if (value.length() != 1) throw new IllegalArgumentException("un caractère attendu");
                return value.charAt(0);
            }
            if (type == BigDecimal.class) return new BigDecimal(value);
            if (type == BigInteger.class) return new BigInteger(value);
            if (type == LocalDate.class) return LocalDate.parse(value);
        } catch (DateTimeParseException | IllegalArgumentException e) {
            throw conversionError(name, raw, type, e.getMessage());
        }
        return null;
    }

    private static Object parseBoolean(String value) {
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on")
                || value.equalsIgnoreCase("yes") || value.equals("1")) return true;
        if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("off")
                || value.equalsIgnoreCase("no") || value.equals("0")) return false;
        throw new IllegalArgumentException("valeur booléenne invalide");
    }

    private static Object primitiveDefault(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0f;
        if (type == double.class) return 0.0d;
        return null;
    }

    private static ParameterBindingException conversionError(String name, String value,
                                                             Class<?> type, String detail) {
        return new ParameterBindingException("Paramètre '" + name + "' de valeur '" + value
                + "' invalide : type attendu " + type.getName()
                + (detail == null ? "" : " (" + detail + ")"));
    }
}
