package framework.core;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AuthConfig {
    public static final String CONTEXT_PARAM = "framework.authSessionKey";
    public static final String CONTEXT_ATTRIBUTE = "framework.auth.sessionKey";

    private AuthConfig() {
    }

    public static void install(ServletContext ctx, Map<UrlMethod, RouteInfo> routes) {
        List<String> protectedRoutes = new ArrayList<>();
        if (routes != null) {
            for (Map.Entry<UrlMethod, RouteInfo> entry : routes.entrySet()) {
                if (entry.getValue() != null && entry.getValue().isAuthenticated()) {
                    protectedRoutes.add(entry.getKey().getMethod() + " " + entry.getKey().getUrl());
                }
            }
        }
        protectedRoutes.sort(String::compareTo);

        String sessionKey = ctx.getInitParameter(CONTEXT_PARAM);
        if (sessionKey != null) {
            sessionKey = sessionKey.trim();
        }

        if (!protectedRoutes.isEmpty() && (sessionKey == null || sessionKey.isEmpty())) {
            throw new IllegalStateException("Paramètre de configuration absent ou vide : "
                    + CONTEXT_PARAM + ". Routes protégées : " + String.join(", ", protectedRoutes));
        }

        if (protectedRoutes.isEmpty()) {
            ctx.removeAttribute(CONTEXT_ATTRIBUTE);
            return;
        }

        ctx.setAttribute(CONTEXT_ATTRIBUTE, sessionKey);
        ctx.log("[FRAMEWORK] Authentification configurée.");
    }

    public static void uninstall(ServletContext ctx) {
        ctx.removeAttribute(CONTEXT_ATTRIBUTE);
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
}
