package framework.core;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import java.util.Map;

public class ControllerScannerListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext ctx = sce.getServletContext();
        ctx.log("[FRAMEWORK] --- DÉBUT DU SCAN DES CONTROLEURS (T0) ---");
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
        ctx.log("[FRAMEWORK] framework.routes removed from ServletContext.");
    }
}
