package framework.core;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import java.util.Map;

public class ControllerScannerListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext ctx = sce.getServletContext();
        String mappingLocation = ctx.getInitParameter("mappingConfigLocation");
        if (mappingLocation == null || mappingLocation.isEmpty()) {
            mappingLocation = "/WEB-INF/mapping.xml";
        }
        String realPath = ctx.getRealPath(mappingLocation);
        Map<String, String> map = UrlMappingLoader.loadMappings(realPath);
        ctx.setAttribute(UrlMappingLoader.CONTEXT_ATTRIBUTE_NAME, map);
        ctx.log("ControllerScannerListener: loaded " + map.size() + " route(s) from " + mappingLocation);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        ServletContext ctx = sce.getServletContext();
        ctx.log("ControllerScannerListener: application context is being destroyed.");
    }
}
