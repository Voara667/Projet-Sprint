package framework.core;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import java.util.Map;

public class ControllerScannerListener implements ServletContextListener, HttpSessionListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext ctx = sce.getServletContext();
        ctx.log("[FRAMEWORK] --- DÉBUT DU SCAN DES CONTROLEURS (T0) ---");
        SessionManager.install(ctx);
        try {
            Map<UrlMethod, RouteInfo> routes = RouteLoader.loadRoutes(ctx);
            ctx.setAttribute("framework.routes", routes);
        } catch (Exception e) {
            throw new FrameworkStartupException("Échec du scan des controllers au démarrage.", e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        ServletContext ctx = sce.getServletContext();
        ctx.removeAttribute("framework.routes");
        SessionManager.uninstall(ctx);
        ctx.log("[FRAMEWORK] framework.routes removed from ServletContext.");
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        ServletContext ctx = event.getSession().getServletContext();
        SessionManager.forget(ctx, event.getSession().getId());
        Object stored = ctx.getAttribute(SessionManager.CONTEXT_ATTRIBUTE);
        int remaining = stored instanceof Map ? ((Map<?, ?>) stored).size() : 0;
        ctx.log("[FRAMEWORK] HTTP session destroyed; framework sessions remaining: " + remaining);
    }
}
