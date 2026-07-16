package framework.core;

import framework.core.HttpMethod;
import framework.core.annotation.Controller;
import framework.core.annotation.RequestMapping;
import jakarta.servlet.ServletConfig;
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
import java.util.List;
import java.util.Map;

public class FrontServletController extends HttpServlet {

    private Map<UrlMethod, RouteInfo> routes;
    private List<String> routeDescriptions;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        ServletContext ctx = getServletContext();
        Object stored = ctx.getAttribute("framework.routes");
        if (!(stored instanceof Map)) {
            throw new ServletException("Attribut framework.routes manquant ou invalide dans le ServletContext.");
        }
        this.routes = (Map<UrlMethod, RouteInfo>) stored;
        this.routeDescriptions = buildRouteDescriptions(this.routes);
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
        String reqMethod = req.getMethod();
        HttpMethod httpMethod;
        try {
            httpMethod = HttpMethod.valueOf(reqMethod.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new HttpMethodNotSupportedException("Methode HTTP inconnue : " + reqMethod);
        }
        UrlMethod lookup = new UrlMethod(path, httpMethod);
        RouteInfo route = routes.get(lookup);
        if (route == null) {
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

        String html = buildRouteFoundHtml(path, route);
        try {
            Object controllerInstance = route.getControllerClass().getDeclaredConstructor().newInstance();
            route.getAction().invoke(controllerInstance);
            PrintWriter out = resp.getWriter();
            out.println(html);
            out.flush();
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            writeServerError(resp, "Erreur d'invocation : " + cause.getClass().getSimpleName() + " - " + cause.getMessage());
        } catch (ReflectiveOperationException e) {
            writeServerError(resp, "Erreur d'invocation : " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    private String buildRouteFoundHtml(String path, RouteInfo route) {
        StringBuilder builder = new StringBuilder();
        builder.append("<!DOCTYPE html>");
        builder.append("<html><head><meta charset=\"UTF-8\"><title>Front Controller</title></head><body>");
        builder.append("<section>");
        builder.append("<h1>Chemin demande : ").append(escapeHtml(path)).append("</h1>");
        builder.append("<p>Route trouvee : ").append(escapeHtml(route.getControllerClass().getName())).append(".").append(escapeHtml(route.getAction().getName())).append("</p>");
        builder.append("</section>");
        builder.append("</body></html>");
        return builder.toString();
    }

    private List<String> buildRouteDescriptions(Map<UrlMethod, RouteInfo> routes) {
        List<String> descriptions = new ArrayList<>();
        for (Map.Entry<UrlMethod, RouteInfo> entry : routes.entrySet()) {
            UrlMethod key = entry.getKey();
            RouteInfo routeInfo = entry.getValue();
            descriptions.add(key.getMethod() + " " + key.getUrl() + " -> " + routeInfo.getControllerClass().getName() + "." + routeInfo.getAction().getName());
        }
        return descriptions;
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

    private void writeServerError(HttpServletResponse resp, String message) throws IOException {
        resp.setContentType("text/html;charset=UTF-8");
        resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        PrintWriter out = resp.getWriter();
        out.println("<!DOCTYPE html>");
        out.println("<html><head><meta charset=\"UTF-8\"><title>500 Internal Server Error</title></head><body>");
        out.println("<section>");
        out.println("<h1>Erreur serveur</h1>");
        out.println("<pre>" + escapeHtml(message) + "</pre>");
        out.println("</section>");
        out.println("</body></html>");
        out.flush();
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
