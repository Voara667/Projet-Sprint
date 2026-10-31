package framework.core;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionManager {
    public static final String CONTEXT_ATTRIBUTE = "framework.sessions";

    private SessionManager() {
    }

    public static void install(ServletContext ctx) {
        ctx.setAttribute(CONTEXT_ATTRIBUTE, new ConcurrentHashMap<String, Session>());
    }

    public static void uninstall(ServletContext ctx) {
        ctx.removeAttribute(CONTEXT_ATTRIBUTE);
    }

    public static Session obtain(HttpServletRequest req) {
        HttpSession httpSession = req.getSession();
        Map<String, Session> sessions = sessions(req.getServletContext());
        Session session = sessions.computeIfAbsent(httpSession.getId(), id -> new Session());
        session.loadFrom(httpSession);
        return session;
    }

    public static void saveAll(Object[] args, HttpServletRequest req) {
        Set<Session> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        HttpSession httpSession = null;
        for (Object arg : args) {
            if (!(arg instanceof Session) || !visited.add((Session) arg)) {
                continue;
            }
            Session session = (Session) arg;
            if (session.isInvalidated()) {
                forget(req.getServletContext(), session.getId());
                if (httpSession == null) {
                    httpSession = req.getSession(false);
                }
                if (httpSession != null) {
                    httpSession.invalidate();
                }
            } else {
                if (httpSession == null) {
                    httpSession = req.getSession(false);
                }
                session.saveTo(httpSession);
            }
        }
    }

    public static void forget(ServletContext ctx, String sessionId) {
        Object stored = ctx.getAttribute(CONTEXT_ATTRIBUTE);
        if (stored instanceof Map) {
            ((Map<?, ?>) stored).remove(sessionId);
        }
    }

    private static Map<String, Session> sessions(ServletContext ctx) {
        Object stored = ctx.getAttribute(CONTEXT_ATTRIBUTE);
        if (!(stored instanceof Map)) {
            throw new IllegalStateException("L'attribut " + CONTEXT_ATTRIBUTE
                    + " est absent ou invalide dans le ServletContext.");
        }
        @SuppressWarnings("unchecked")
        Map<String, Session> sessions = (Map<String, Session>) stored;
        return sessions;
    }
}
