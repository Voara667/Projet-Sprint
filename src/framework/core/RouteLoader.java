package framework.core;

import framework.core.annotation.RequestMapping;
import jakarta.servlet.ServletContext;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RouteLoader {

    public static Map<UrlMethod, RouteInfo> loadRoutes(ServletContext ctx) throws Exception {
        String classesPath = ctx.getRealPath("/WEB-INF/classes");
        Map<UrlMethod, RouteInfo> routes = new HashMap<>();
        List<String> routeDescriptions = new ArrayList<>();
        List<Class<?>> controllers = ClassScanner.findAnnotatedControllers(classesPath, Thread.currentThread().getContextClassLoader());
        for (Class<?> controller : controllers) {
            Method[] methods = controller.getDeclaredMethods();
            boolean hasMappedMethod = false;
            for (Method method : methods) {
                if (!method.isAnnotationPresent(RequestMapping.class)) {
                    continue;
                }
                hasMappedMethod = true;
                RequestMapping mapping = method.getAnnotation(RequestMapping.class);
                String url = mapping.value();
                HttpMethod httpMethod = mapping.method();
                RouteInfo routeInfo = new RouteInfo(controller, method);
                UrlMethod key = new UrlMethod(url, httpMethod);
                if (routes.containsKey(key)) {
                    RouteInfo existing = routes.get(key);
                    String message = "Route en doublon : " + url + " [" + httpMethod + "] déjà déclarée par "
                            + existing.getControllerClass().getSimpleName() + "." + existing.getAction().getName() + "(), en conflit avec "
                            + controller.getSimpleName() + "." + method.getName() + "()";
                    throw new DuplicateRouteException(message);
                }
                routes.put(key, routeInfo);
                routeDescriptions.add(httpMethod + " " + url + " -> " + controller.getSimpleName() + "." + method.getName());
            }
            if (!hasMappedMethod) {
                ctx.log("FrontServletController: Warning - " + controller.getName() + " est annoté @Controller mais sans méthode @RequestMapping, ignoré.");
            }
        }
        for (String route : routeDescriptions) {
            ctx.log("[FRAMEWORK] Route détectée : " + route);
        }
        ctx.log("[FRAMEWORK] --- SCAN TERMINÉ : " + routes.size() + " ROUTES CHARGÉES ---");
        return routes;
    }
}
