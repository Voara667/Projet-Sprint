package framework.core;

import framework.core.annotation.RequestMapping;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FrontServletController extends HttpServlet {

    private Map<String, List<String>> urlControllerMap;
    private List<String[]> detectedControllers;

    @Override
    public void init() throws ServletException {
        super.init();
        ServletContext ctx = getServletContext();
        String classesPath = ctx.getRealPath("/WEB-INF/classes");
        urlControllerMap = new HashMap<>();
        detectedControllers = new ArrayList<>();
        List<Class<?>> controllers = ClassScanner.findAnnotatedControllers(classesPath, getClass().getClassLoader());
        for (Class<?> controller : controllers) {
            if (!controller.isAnnotationPresent(RequestMapping.class)) {
                ctx.log("FrontServletController: Warning - " + controller.getName() + " est annote @Controller mais sans @RequestMapping, ignoree.");
                continue;
            }
            RequestMapping mapping = controller.getAnnotation(RequestMapping.class);
            String url = mapping.value();
            urlControllerMap.computeIfAbsent(url, k -> new ArrayList<>()).add(controller.getName());
            detectedControllers.add(new String[]{url, controller.getName()});
        }
        if (urlControllerMap.isEmpty()) {
            ctx.log("FrontServletController: Warning - aucun controleur valide né trouvé dans WEB-INF/classes.");
        } else {
            ctx.log("FrontServletController: " + detectedControllers.size() + " controleur(s) enregistres.");
            for (String[] entry : detectedControllers) {
                ctx.log("  " + entry[0] + " -> " + entry[1]);
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
        List<String> controllers = urlControllerMap.get(path);
        resp.setContentType("text/html;charset=UTF-8");
        if (controllers == null || controllers.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
        PrintWriter out = resp.getWriter();
        out.println("<!DOCTYPE html>");
        out.println("<html><head><meta charset=\"UTF-8\"><title>Front Controller</title></head><body>");
        out.println("<section>");
        if (controllers != null && !controllers.isEmpty()) {
            out.println("<h1>Chemin demande : " + escapeHtml(path) + "</h1>");
            for (String controllerClass : controllers) {
                out.println("<p>Controleur trouve : " + escapeHtml(controllerClass) + "</p>");
            }
        } else {
            out.println("<h1>Aucun controleur configure pour le chemin : " + escapeHtml(path) + "</h1>");
            out.println("</section>");
            out.println("<section>");
            out.println("<h2>Controleurs detectes au demarrage</h2>");
            if (detectedControllers.isEmpty()) {
                out.println("<p>Aucun controleur detecte.</p>");
            } else {
                out.println("<ul>");
                for (String[] entry : detectedControllers) {
                    out.println("<li>" + escapeHtml(entry[0]) + "  ->  " + escapeHtml(entry[1]) + "</li>");
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
