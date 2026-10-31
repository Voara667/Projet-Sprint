package framework.core;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class AuthConfig {
    public static final String CONTEXT_PARAM = "framework.authSessionKey";
    public static final String CONTEXT_ATTRIBUTE = "framework.auth.sessionKey";
    public static final String ROLES_PARAM = "framework.rolesSessionKey";
    public static final String ROLES_ATTRIBUTE = "framework.auth.rolesKey";

    private AuthConfig() {
    }

    public static void install(ServletContext ctx, Map<UrlMethod, RouteInfo> routes) {
        List<String> protectedRoutes = new ArrayList<>();
        List<String> roleRoutes = new ArrayList<>();
        if (routes != null) {
            for (Map.Entry<UrlMethod, RouteInfo> entry : routes.entrySet()) {
                RouteInfo route = entry.getValue();
                if (route != null && route.isAuthenticated()) {
                    protectedRoutes.add(entry.getKey().getMethod() + " " + entry.getKey().getUrl());
                }
                if (route != null && route.isRoleRestricted()) {
                    String description = entry.getKey().getMethod() + " " + entry.getKey().getUrl();
                    roleRoutes.add(description);
                    for (String role : route.getRoles()) {
                        if (role == null || role.trim().isEmpty()) {
                            throw new IllegalStateException("Rôle vide dans @Roles pour la route " + description);
                        }
                    }
                    if (route.getRoles().length == 0) {
                        throw new IllegalStateException("@Roles vide pour la route " + description);
                    }
                }
            }
        }
        protectedRoutes.sort(String::compareTo);
        roleRoutes.sort(String::compareTo);

        String sessionKey = ctx.getInitParameter(CONTEXT_PARAM);
        if (sessionKey != null) {
            sessionKey = sessionKey.trim();
        }

        if (!protectedRoutes.isEmpty() && (sessionKey == null || sessionKey.isEmpty())) {
            throw new IllegalStateException("Paramètre de configuration absent ou vide : "
                    + CONTEXT_PARAM + ". Routes protégées : " + String.join(", ", protectedRoutes));
        }

        String rolesKey = ctx.getInitParameter(ROLES_PARAM);
        if (rolesKey != null) {
            rolesKey = rolesKey.trim();
        }
        if (!roleRoutes.isEmpty() && (rolesKey == null || rolesKey.isEmpty())) {
            throw new IllegalStateException("Paramètre de configuration absent ou vide : "
                    + ROLES_PARAM + ". Routes avec rôles : " + String.join(", ", roleRoutes));
        }

        if (protectedRoutes.isEmpty()) {
            ctx.removeAttribute(CONTEXT_ATTRIBUTE);
            ctx.removeAttribute(ROLES_ATTRIBUTE);
            return;
        }

        ctx.setAttribute(CONTEXT_ATTRIBUTE, sessionKey);
        if (roleRoutes.isEmpty()) {
            ctx.removeAttribute(ROLES_ATTRIBUTE);
        } else {
            ctx.setAttribute(ROLES_ATTRIBUTE, rolesKey);
        }
        ctx.log("[FRAMEWORK] Authentification configurée.");
    }

    public static void uninstall(ServletContext ctx) {
        ctx.removeAttribute(CONTEXT_ATTRIBUTE);
        ctx.removeAttribute(ROLES_ATTRIBUTE);
    }

    public static boolean isAuthenticated(HttpServletRequest req) {
        Object stored = req.getServletContext().getAttribute(CONTEXT_ATTRIBUTE);
        if (stored == null) {
            throw new IllegalStateException("L'attribut " + CONTEXT_ATTRIBUTE + " est absent dans le ServletContext.");
        }
        String sessionKey = String.valueOf(stored).trim();
        if (sessionKey.isEmpty()) {
            throw new IllegalStateException("La clé de session d'authentification est vide dans le ServletContext.");
        }
        HttpSession session = req.getSession(false);
        return session != null && session.getAttribute(sessionKey) != null;
    }

    public static boolean hasAnyRole(HttpServletRequest req, String[] requiredRoles) {
        Object storedKey = req.getServletContext().getAttribute(ROLES_ATTRIBUTE);
        if (storedKey == null) {
            throw new IllegalStateException("L'attribut " + ROLES_ATTRIBUTE + " est absent dans le ServletContext.");
        }
        String rolesKey = String.valueOf(storedKey).trim();
        if (rolesKey.isEmpty()) {
            throw new IllegalStateException("La clé de session des rôles est vide dans le ServletContext.");
        }
        HttpSession session = req.getSession(false);
        if (session == null) {
            return false;
        }
        Object value = session.getAttribute(rolesKey);
        if (value == null) {
            return false;
        }
        List<String> actualRoles = new ArrayList<>();
        if (value instanceof String) {
            actualRoles.add((String) value);
        } else if (value instanceof Collection<?>) {
            for (Object role : (Collection<?>) value) {
                if (role != null) {
                    actualRoles.add(String.valueOf(role));
                }
            }
        } else if (value.getClass().isArray() && !value.getClass().getComponentType().isPrimitive()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                Object role = Array.get(value, i);
                if (role != null) {
                    actualRoles.add(String.valueOf(role));
                }
            }
        } else {
            throw new IllegalStateException("Type de rôles non supporté : " + value.getClass().getName());
        }
        for (String requiredRole : requiredRoles) {
            if (actualRoles.contains(requiredRole)) {
                return true;
            }
        }
        return false;
    }
}
