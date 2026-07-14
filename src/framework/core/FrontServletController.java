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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FrontServletController extends HttpServlet {

    private Map<UrlMethod, RouteInfo> routes;
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
                routeDescriptions.add(url + " [" + httpMethod + "] -> " + controller.getName() + "." + method.getName());
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
        try {
            processRequest(req, resp);
        } catch (UrlNotFoundException e) {
            writeNotFound(resp, e.getMessage());
        } catch (HttpMethodNotSupportedException e) {
            writeMethodNotSupported(resp, e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            processRequest(req, resp);
        } catch (UrlNotFoundException e) {
            writeNotFound(resp, e.getMessage());
        } catch (HttpMethodNotSupportedException e) {
            writeMethodNotSupported(resp, e.getMessage());
        }
    }

    private void processRequest(HttpServletRequest req, HttpServletResponse resp) throws IOException, UrlNotFoundException, HttpMethodNotSupportedException {
        String path = req.getPathInfo();
        if (path == null) {
            path = "/";
        }
        HttpMethod reqMethod = null;
        try {
            reqMethod = HttpMethod.valueOf(req.getMethod());
        } catch (IllegalArgumentException ignored) {
        }
        UrlMethod lookup = reqMethod == null ? null : new UrlMethod(path, reqMethod);
        RouteInfo routeInfo = lookup == null ? null : routes.get(lookup);
        if (routeInfo == null) {
            boolean urlExists = false;
            List<String> supported = new ArrayList<>();
            for (UrlMethod um : routes.keySet()) {
                if (um.getUrl().equals(path)) {
                    urlExists = true;
                    supported.add(um.getMethod().name());
                }
            }
            if (urlExists) {
                String requested = reqMethod == null ? req.getMethod() : reqMethod.name();
                throw new HttpMethodNotSupportedException("Methode HTTP non supportee pour l'URL : " + path + "\nMethode demandee : " + requested + "\nMethodes disponibles : " + String.join(", ", supported));
            } else {
                throw new UrlNotFoundException("URL non supportee : " + path + "\nRoutes connues :\n" + buildKnownRoutesMessage());
            }
        }
        StringBuilder output = new StringBuilder();
        output.append("<!DOCTYPE html>");
        output.append("<html><head><meta charset=\"UTF-8\"><title>Front Controller</title></head><body>");
        output.append("<section>");
        output.append("<h1>Chemin demande : " + escapeHtml(path) + "</h1>");
        output.append("<p>Route trouvee : " + escapeHtml(routeInfo.getControllerClass().getName()) + "." + escapeHtml(routeInfo.getAction().getName()) + "</p>");
        output.append("</section>");
        output.append("</body></html>");

        try {
            Object controllerInstance = routeInfo.getControllerClass().getDeclaredConstructor().newInstance();
            routeInfo.getAction().invoke(controllerInstance);
            resp.setContentType("text/html;charset=UTF-8");
            PrintWriter out = resp.getWriter();
            out.println(output.toString());
            out.flush();
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            writeServerError(resp, "Erreur d'invocation : " + cause.getClass().getSimpleName() + " - " + cause.getMessage());
        } catch (Exception e) {
            writeServerError(resp, "Erreur d'invocation : " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    private String buildKnownRoutesMessage() {
        if (routeDescriptions.isEmpty()) {
            return "Aucune route detectee.";
        }
        StringBuilder builder = new StringBuilder();
        for (String route : routeDescriptions) {
            builder.append(route).append("\n");
        }
        return builder.toString();
    }

    private void writeNotFound(HttpServletResponse resp, String message) throws IOException {
        resp.setContentType("text/html;charset=UTF-8");
        resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        PrintWriter out = resp.getWriter();
        out.println("<!DOCTYPE html>");
        out.println("<html><head><meta charset=\"UTF-8\"><title>404 Not Found</title></head><body>");
        out.println("<section>");
        out.println("<h1>URL non supportee</h1>");
        out.println("<pre>" + escapeHtml(message) + "</pre>");
        out.println("</section>");
        out.println("</body></html>");
        out.flush();
    }

    private void writeMethodNotSupported(HttpServletResponse resp, String message) throws IOException {
        resp.setContentType("text/html;charset=UTF-8");
        resp.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        PrintWriter out = resp.getWriter();
        out.println("<!DOCTYPE html>");
        out.println("<html><head><meta charset=\"UTF-8\"><title>405 Method Not Allowed</title></head><body>");
        out.println("<section>");
        out.println("<h1>Methode HTTP non supportee</h1><p>" + escapeHtml(message) + "</p>");
        out.println("</section>");
        out.println("</body></html>");
        out.flush();
    }

    private void writeServerError(HttpServletResponse resp, String message) throws IOException {
        resp.setContentType("text/html;charset=UTF-8");
        resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        PrintWriter out = resp.getWriter();
        out.println("<!DOCTYPE html>");
        out.println("<html><head><meta charset=\"UTF-8\"><title>500 Internal Server Error</title></head><body>");
        out.println("<section>");
        out.println("<h1>Erreur interne du serveur</h1>");
        out.println("<pre>" + escapeHtml(message) + "</pre>");
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
