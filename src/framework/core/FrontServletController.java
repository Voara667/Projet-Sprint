package framework.core;

import framework.core.annotation.Controller;
import framework.core.annotation.RequestMapping;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FrontServletController extends HttpServlet {

    private Map<String, List<RouteInfo>> routes;
    private List<String> routeDescriptions;

    @Override
    public void init() throws ServletException {
        super.init();
        ServletContext ctx = getServletContext();
        String classesPath = ctx.getRealPath("/WEB-INF/classes");
        routes = new HashMap<>();
        routeDescriptions = new ArrayList<>();
        List<Class<?>> controllers = ClassScanner.findAnnotatedControllers(classesPath, getClass().getClassLoader());
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
                RouteInfo routeInfo = new RouteInfo(controller, method);
                routes.computeIfAbsent(url, k -> new ArrayList<>()).add(routeInfo);
                routeDescriptions.add(url + " -> " + controller.getName() + "." + method.getName());
            }
            if (!hasMappedMethod) {
                ctx.log("FrontServletController: Warning - " + controller.getName() + " est annoté @Controller mais sans méthode @RequestMapping, ignoré.");
            }
        }
        if (routes.isEmpty()) {
            ctx.log("FrontServletController: Warning - aucune route valide trouvée dans WEB-INF/classes.");
        } else {
            ctx.log("FrontServletController: " + routes.size() + " URL(s) enregistrées.");
            for (String route : routeDescriptions) {
                ctx.log("  " + route);
            }
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        processRequest(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        processRequest(req, resp);
    }

    private void processRequest(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getPathInfo();
        if (path == null) {
            path = "/";
        }
        List<RouteInfo> matches = routes.get(path);
        resp.setContentType("text/html;charset=UTF-8");
        if (matches == null || matches.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
        PrintWriter out = resp.getWriter();
        out.println("<!DOCTYPE html>");
        out.println("<html><head><meta charset=\"UTF-8\"><title>Front Controller</title></head><body>");
        out.println("<section>");
        if (matches != null && !matches.isEmpty()) {
            out.println("<h1>Chemin demande : " + escapeHtml(path) + "</h1>");
            for (RouteInfo route : matches) {
                out.println("<p>Route trouvee : " + escapeHtml(route.getControllerClass().getName()) + "." + escapeHtml(route.getAction().getName()) + "</p>");
            }
        } else {
            out.println("<h1>Aucune route configuree pour le chemin : " + escapeHtml(path) + "</h1>");
            out.println("</section>");
            out.println("<section>");
            out.println("<h2>Routes detectees au demarrage</h2>");
            if (routeDescriptions.isEmpty()) {
                out.println("<p>Aucune route detectee.</p>");
            } else {
                out.println("<ul>");
                for (String entry : routeDescriptions) {
                    out.println("<li>" + escapeHtml(entry) + "</li>");
                }
                out.println("</ul>");
            }
        }
        out.println("</section>");
        out.println("</body></html>");
        out.flush();
    }

    private String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
