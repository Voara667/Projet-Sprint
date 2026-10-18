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
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

public class FrontServletController extends HttpServlet {

    private Map<UrlMethod, RouteInfo> routes;
    private List<String> routeDescriptions;
    private String viewPrefix;
    private String viewSuffix;

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
        // Read view prefix/suffix init params
        String vp = config.getInitParameter("framework.viewPrefix");
        if (vp == null || vp.isEmpty()) {
            vp = "/WEB-INF/views/";
            ctx.log("[FRAMEWORK] Aucun framework.viewPrefix defini, valeur par defaut utilisee : " + vp);
        }
        String vs = config.getInitParameter("framework.viewSuffix");
        if (vs == null || vs.isEmpty()) {
            vs = ".jsp";
            ctx.log("[FRAMEWORK] Aucun framework.viewSuffix defini, valeur par defaut utilisee : " + vs);
        }
        this.viewPrefix = vp;
        this.viewSuffix = vs;
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

    private void processRequest(HttpServletRequest req, HttpServletResponse resp) throws IOException, UrlNotFoundException, HttpMethodNotSupportedException, ServletException {
        String path = req.getPathInfo();
        if (path == null || path.isEmpty()) {
            path = req.getServletPath();
        }
        if (path == null || path.isEmpty()) {
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
                String requested = reqMethod == null ? req.getMethod() : reqMethod;
                throw new HttpMethodNotSupportedException("Methode HTTP non supportee pour l'URL : " + path + "\nMethode demandee : " + requested + "\nMethodes disponibles : " + String.join(", ", supported));
            } else {
                throw new UrlNotFoundException("URL non supportee : " + path + "\nRoutes connues :\n" + buildKnownRoutesMessage());
            }
        }

        String html = buildRouteFoundHtml(path, route);
        try {
            Object controllerInstance = route.getControllerClass().getDeclaredConstructor().newInstance();
            Method action = route.getAction();
            if (req.getCharacterEncoding() == null) {
                req.setCharacterEncoding("UTF-8");
            }
            MultipartSupport.prepare(req);
            Object[] args = buildActionArguments(action, req, resp);
            Object result = action.invoke(controllerInstance, args);
            if (route.isApi()) {
                writeApiResponse(resp, action, result);
            } else if (result instanceof ModelAndView) {
                ModelAndView mv = (ModelAndView) result;
                for (java.util.Map.Entry<String, Object> entry : mv.getAttributes().entrySet()) {
                    req.setAttribute(entry.getKey(), entry.getValue());
                }
                req.getRequestDispatcher(viewPrefix + mv.getUrl() + viewSuffix).forward(req, resp);
            } else {
                PrintWriter out = resp.getWriter();
                out.println(html);
                out.flush();
            }
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            String message = "Erreur d'invocation : " + cause.getClass().getSimpleName() + " - " + cause.getMessage();
            if (route.isApi()) {
                writeApiError(resp, message);
            } else {
                writeServerError(resp, message);
            }
        } catch (IllegalArgumentException e) {
            String message = "Argument invalide pour la méthode d'action : " + e.getMessage();
            if (route.isApi()) {
                writeApiError(resp, message);
            } else {
                writeServerError(resp, message);
            }
        } catch (ReflectiveOperationException e) {
            String message = "Erreur d'invocation : " + e.getClass().getSimpleName() + " - " + e.getMessage();
            if (route.isApi()) {
                writeApiError(resp, message);
            } else {
                writeServerError(resp, message);
            }
        } catch (ParameterBindingException e) {
            if (route.isApi()) {
                writeApiError(resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            } else {
                writeBadRequest(resp, e.getMessage());
            }
        } catch (UploadException e) {
            if (route.isApi()) {
                writeApiError(resp, e.getStatus(), e.getMessage());
            } else {
                writeUploadError(resp, e);
            }
        } catch (RuntimeException e) {
            if (route.isApi()) {
                writeApiError(resp, e.getMessage());
            } else {
                throw e;
            }
        }
    }

    private void writeApiResponse(HttpServletResponse resp, Method action, Object result) throws IOException {
        if (resp.isCommitted()) {
            return;
        }
        if (action.getReturnType() == void.class) {
            resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }
        String body;
        if (result instanceof String) {
            body = (String) result;
        } else if (result instanceof ModelAndView) {
            body = JsonSerializer.toJson(((ModelAndView) result).getAttributes());
        } else {
            body = JsonSerializer.toJson(result);
        }
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType("application/json;charset=UTF-8");
        PrintWriter out = resp.getWriter();
        out.print(body);
        out.flush();
    }

    private void writeApiError(HttpServletResponse resp, String message) throws IOException {
        writeApiError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message);
    }

    private void writeApiError(HttpServletResponse resp, int status, String message) throws IOException {
        if (resp.isCommitted()) {
            return;
        }
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        PrintWriter out = resp.getWriter();
        out.print("{\"status\":" + status + ",\"error\":" + JsonSerializer.quote(message) + "}");
        out.flush();
    }

    private void writeBadRequest(HttpServletResponse resp, String message) throws IOException {
        resp.setContentType("text/html;charset=UTF-8");
        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        PrintWriter out = resp.getWriter();
        out.println("<!DOCTYPE html>");
        out.println("<html><head><meta charset=\"UTF-8\"><title>400 Bad Request</title></head><body>");
        out.println("<section><h1>Requête invalide</h1><pre>" + escapeHtml(message) + "</pre></section>");
        out.println("</body></html>");
        out.flush();
    }

    private void writeUploadError(HttpServletResponse resp, UploadException exception) throws IOException {
        resp.setContentType("text/html;charset=UTF-8");
        resp.setStatus(exception.getStatus());
        PrintWriter out = resp.getWriter();
        out.println("<!DOCTYPE html>");
        out.println("<html><head><meta charset=\"UTF-8\"><title>" + exception.getStatus()
                + " Upload Error</title></head><body>");
        out.println("<section><h1>Erreur d'envoi</h1><pre>"
                + escapeHtml(exception.getMessage()) + "</pre></section>");
        out.println("</body></html>");
        out.flush();
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

    private Object[] buildActionArguments(Method action, HttpServletRequest req, HttpServletResponse resp)
            throws ParameterBindingException, IOException, ServletException {
        Class<?>[] parameterTypes = action.getParameterTypes();
        Parameter[] parameters = action.getParameters();
        Object[] args = new Object[parameterTypes.length];
        ApplicationContext applicationContext = null;
        boolean applicationContextResolved = false;
        Set<String> requestParameterNames = null;
        Map<String, UploadedFile> uploadedFiles = null;
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> type = parameterTypes[i];
            if (ApplicationContext.class.isAssignableFrom(type)) {
                if (!applicationContextResolved) {
                    applicationContext = getSpringApplicationContext();
                    applicationContextResolved = true;
                }
                args[i] = applicationContext;
            } else if (HttpServletRequest.class.isAssignableFrom(type)) {
                args[i] = req;
            } else if (HttpServletResponse.class.isAssignableFrom(type)) {
                args[i] = resp;
            } else if (type == UploadedFile.class || type == byte[].class
                    || isUploadedFilesMap(parameters[i])) {
                String name = parameterName(parameters[i]);
                if (uploadedFiles == null) {
                    uploadedFiles = MultipartSupport.readFiles(req);
                }
                UploadedFile file = uploadedFiles.get(name);
                if (isUploadedFilesMap(parameters[i])) {
                    args[i] = uploadedFiles;
                } else if (type == UploadedFile.class) {
                    args[i] = file;
                } else {
                    args[i] = file == null ? null : file.getBytes();
                }
            } else if (ParameterBinder.isSimpleType(type)) {
                if (requestParameterNames == null) {
                    requestParameterNames = collectParameterNames(req);
                }
                args[i] = ParameterBinder.bind(parameters[i], requestParameterNames, req);
            } else if (ObjectBinder.isBindableObject(type)) {
                if (requestParameterNames == null) {
                    requestParameterNames = collectParameterNames(req);
                }
                if (ObjectBinder.hasFileFields(type)) {
                    if (uploadedFiles == null) {
                        uploadedFiles = MultipartSupport.readFiles(req);
                    }
                } else if (uploadedFiles == null) {
                    uploadedFiles = java.util.Collections.emptyMap();
                }
                args[i] = ObjectBinder.bind(parameters[i], requestParameterNames, req,
                        uploadedFiles);
            } else {
                args[i] = null;
            }
        }
        return args;
    }

    private boolean isUploadedFilesMap(Parameter parameter) {
        if (!Map.class.equals(parameter.getType())
                || !(parameter.getParameterizedType() instanceof ParameterizedType)) {
            return false;
        }
        Type[] types = ((ParameterizedType) parameter.getParameterizedType()).getActualTypeArguments();
        return types.length == 2 && types[0] == String.class && types[1] == UploadedFile.class;
    }

    private String parameterName(Parameter parameter) {
        if (!parameter.isNamePresent()) {
            throw new IllegalArgumentException("Nom de paramètre indisponible pour "
                    + parameter.getType().getName()
                    + "; compiler les controllers avec javac -parameters.");
        }
        return parameter.getName();
    }

    private Set<String> collectParameterNames(HttpServletRequest req) {
        Set<String> names = new HashSet<>();
        java.util.Enumeration<String> parameterNames = req.getParameterNames();
        while (parameterNames != null && parameterNames.hasMoreElements()) {
            names.add(parameterNames.nextElement());
        }
        return names;
    }

    private ApplicationContext getSpringApplicationContext() {
        ServletContext servletContext = getServletContext();
        return WebApplicationContextUtils.getWebApplicationContext(servletContext);
    }

    private String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
